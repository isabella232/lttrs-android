package rs.ltt.android.ui.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.SelectableMailbox;
import rs.ltt.android.repository.MailboxRepository;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.LabelUtil;

public class ChooseLabelsViewModel extends LttrsViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChooseLabelsViewModel.class);

    private final String[] threadIds;
    private final MailboxRepository mailboxRepository;
    private final LiveData<List<MailboxWithRoleAndName>> existingLabels;
    private final LiveData<List<String>> mailboxes;
    private final LiveData<List<MailboxOverwriteEntity>> mailboxOverwrites;
    private final HashMap<Selection, Boolean> selectionOverwrites = new HashMap<>();
    private final MediatorLiveData<List<SelectableMailbox>> labels = new MediatorLiveData<>();

    public ChooseLabelsViewModel(@NonNull final Application application,
                                 @NonNull final Long accountId,
                                 @NonNull final String[] threadIds) {
        super(application, accountId);
        Preconditions.checkNotNull(threadIds);
        Preconditions.checkArgument(threadIds.length > 0);
        this.threadIds = threadIds;
        this.mailboxRepository = new MailboxRepository(application, accountId);
        this.existingLabels = this.mailboxRepository.getLabels();
        this.mailboxes = this.mailboxRepository.getMailboxIdsForThreadsLiveData(threadIds);
        this.mailboxOverwrites = this.mailboxRepository.getMailboxOverwrites(threadIds);
        this.labels.addSource(existingLabels, existingMailboxes -> {
            final List<String> mailboxes = this.mailboxes.getValue();
            final List<MailboxOverwriteEntity> mailboxOverwrites = this.mailboxOverwrites.getValue();
            if (mailboxes == null || mailboxOverwrites == null) {
                return;
            }
            updateSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes);
        });
        this.labels.addSource(mailboxes, mailboxes -> {
            final List<MailboxWithRoleAndName> existingMailboxes = this.existingLabels.getValue();
            final List<MailboxOverwriteEntity> mailboxOverwrites = this.mailboxOverwrites.getValue();
            if (existingMailboxes == null || mailboxOverwrites == null) {
                return;
            }
            updateSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes);
        });
        this.labels.addSource(mailboxOverwrites, mailboxOverwrites -> {
            final List<String> mailboxes = this.mailboxes.getValue();
            final List<MailboxWithRoleAndName> existingMailboxes = this.existingLabels.getValue();
            if (mailboxes == null || existingMailboxes == null) {
                return;
            }
            updateSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes);
        });
    }

    public void applyChanges() {
        final List<String> mailboxes = Objects.requireNonNull(this.mailboxes.getValue());
        final List<MailboxOverwriteEntity> mailboxOverwrites = Objects.requireNonNull(this.mailboxOverwrites.getValue());
        final List<MailboxWithRoleAndName> existingMailboxes = Objects.requireNonNull(this.existingLabels.getValue());
        final ImmutableList.Builder<IdentifiableMailboxWithRoleAndName> addBuilder = new ImmutableList.Builder<>();
        final ImmutableList.Builder<IdentifiableMailboxWithRoleAndName> removeBuilder = new ImmutableList.Builder<>();
        for(final SelectableMailbox selectableMailbox : getSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes)) {
            if (selectableMailbox.isSelected()) {
                addBuilder.add(selectableMailbox.toMailboxWithRoleAndName());
            } else if (isInMailbox(selectableMailbox,mailboxes,mailboxOverwrites)) {
                removeBuilder.add(selectableMailbox.toMailboxWithRoleAndName());
            }
        }
        final List<IdentifiableMailboxWithRoleAndName> add = addBuilder.build();
        final List<IdentifiableMailboxWithRoleAndName> remove = removeBuilder.build();
        LOGGER.debug("add: {}, remove: {}", add, remove);
    }

    private List<SelectableMailbox> getSelectableMailboxes(final List<String> mailboxes,
                                                           final List<MailboxOverwriteEntity> mailboxOverwrites,
                                                           final List<MailboxWithRoleAndName> existingMailboxes) {
        final ArrayList<SelectableMailbox> selectableMailboxes = new ArrayList<>();
        for (final MailboxWithRoleAndName mailbox : existingMailboxes) {
            final Boolean overwrite = getSelectionOverwrite(mailbox);
            if (overwrite != null) {
                selectableMailboxes.add(SelectableMailbox.of(mailbox, overwrite));
            } else {
                selectableMailboxes.add(SelectableMailbox.of(mailbox, isInMailbox(mailbox, mailboxes, mailboxOverwrites)));
            }
        }
        for (final Map.Entry<Selection, Boolean> selectionOverwrite : this.selectionOverwrites.entrySet()) {
            final Selection selection = selectionOverwrite.getKey();
            if (selection.id != null || selection.matchesAny(existingMailboxes)) {
                continue;
            }
            selectableMailboxes.add(SelectableMailbox.of(selection.name, selectionOverwrite.getValue()));
        }
        Collections.sort(selectableMailboxes, LabelUtil.COMPARATOR);
        return selectableMailboxes;
    }

    private boolean isInMailbox(final IdentifiableMailboxWithRoleAndName mailbox) {
        final List<String> mailboxes = Objects.requireNonNull(this.mailboxes.getValue());
        final List<MailboxOverwriteEntity> mailboxOverwrites = Objects.requireNonNull(this.mailboxOverwrites.getValue());
        return isInMailbox(mailbox, mailboxes, mailboxOverwrites);
    }

    private static boolean isInMailbox(final IdentifiableMailboxWithRoleAndName mailbox, final List<String> mailboxes, final List<MailboxOverwriteEntity> mailboxOverwrites) {
        final Boolean overwrite = MailboxOverwriteEntity.getOverwrite(mailboxOverwrites, mailbox);
        if (overwrite != null) {
            return overwrite;
        } else {
            return mailbox.getId() != null && mailboxes.contains(mailbox.getId());
        }
    }

    private void updateSelectableMailboxes() {
        final List<String> mailboxes = Objects.requireNonNull(this.mailboxes.getValue());
        final List<MailboxWithRoleAndName> existingMailboxes = Objects.requireNonNull(this.existingLabels.getValue());
        final List<MailboxOverwriteEntity> mailboxOverwrites = Objects.requireNonNull(this.mailboxOverwrites.getValue());
        updateSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes);
    }

    private void updateSelectableMailboxes(final List<String> mailboxes,
                                           final List<MailboxOverwriteEntity> mailboxOverwrites,
                                           final List<MailboxWithRoleAndName> existingMailboxes) {
        this.labels.postValue(getSelectableMailboxes(mailboxes, mailboxOverwrites, existingMailboxes));
    }

    public LiveData<List<SelectableMailbox>> getSelectableMailboxesLiveData() {
        return this.labels;
    }

    private static class Selection {
        private final String id;
        private final String name;
        private final Role role;

        private Selection(String id, String name, Role role) {
            this.id = id;
            this.name = name;
            this.role = role;
        }

        private boolean matches(final IdentifiableMailboxWithRoleAndName mailbox) {
            if (id == null) {
                return Objects.equals(name, mailbox.getName()) && Objects.equals(role, mailbox.getRole());
            } else {
                return id.equals(mailbox.getId());
            }
        }

        private boolean matchesAny(final Collection<? extends IdentifiableMailboxWithRoleAndName> mailboxes) {
            for (IdentifiableMailboxWithRoleAndName mailbox : mailboxes) {
                if (matches(mailbox)) {
                    return true;
                }
            }
            return false;
        }
    }

    private Boolean getSelectionOverwrite(final IdentifiableMailboxWithRoleAndName mailbox) {
        synchronized (this.selectionOverwrites) {
            for (final Map.Entry<Selection, Boolean> selectionOverwrite : this.selectionOverwrites.entrySet()) {
                final Selection selection = selectionOverwrite.getKey();
                if (selection.matches(mailbox)) {
                    return selectionOverwrite.getValue();
                }
            }
        }
        return null;
    }

    public void setSelectionOverwrite(final IdentifiableMailboxWithRoleAndName mailbox, boolean selected) {
        synchronized (this.selectionOverwrites) {
            final Iterator<Map.Entry<Selection, Boolean>> iterator = this.selectionOverwrites.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<Selection, Boolean> selectionOverwrite = iterator.next();
                final Selection selection = selectionOverwrite.getKey();
                if (selection.matches(mailbox)) {
                    if (selected == isInMailbox(mailbox)) {
                        LOGGER.debug("remove selection overwrite for {}", mailbox.getName());
                        iterator.remove();
                    } else {
                        selectionOverwrite.setValue(selected);
                    }
                    updateSelectableMailboxes();
                    return;
                }
            }
            selectionOverwrites.put(new Selection(mailbox.getId(), mailbox.getName(), mailbox.getRole()), selected);
            updateSelectableMailboxes();
        }
    }

    public void createLabel(final String name) {
        setSelectionOverwrite(new StubLabel(name), true);
    }

    private static class StubLabel implements IdentifiableMailboxWithRoleAndName {
        private final String name;

        private StubLabel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Role getRole() {
            return null;
        }

        @Override
        public String getId() {
            return null;
        }
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final Application application;
        private final long accountId;
        private final String[] threadIds;

        public Factory(final Application application,
                       final long accountId,
                       final String[] threadIds) {
            this.application = application;
            this.accountId = accountId;
            this.threadIds = threadIds;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return Objects.requireNonNull(modelClass.cast(new ChooseLabelsViewModel(application, accountId, threadIds)));
        }
    }

}
