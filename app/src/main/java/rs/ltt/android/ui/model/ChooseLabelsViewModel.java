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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.SelectableMailbox;
import rs.ltt.android.repository.MailboxRepository;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;

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

    }

    private List<SelectableMailbox> getSelectableMailboxes(final List<String> mailboxes,
                                                           final List<MailboxOverwriteEntity> mailboxOverwrites,
                                                           final List<MailboxWithRoleAndName> existingMailboxes) {
        ImmutableList.Builder<SelectableMailbox> builder = new ImmutableList.Builder<>();
        for (final MailboxWithRoleAndName mailbox : existingMailboxes) {
            final Boolean overwrite = getSelectionOverwrite(mailbox);
            if (overwrite != null) {
                LOGGER.debug("creating {} with overwrite {}", mailbox.getName(), overwrite);
                builder.add(SelectableMailbox.of(mailbox, overwrite));
            } else {
                LOGGER.debug("creating {} without overwrite", mailbox.getName());
                builder.add(SelectableMailbox.of(mailbox, isInMailbox(mailbox, mailboxes, mailboxOverwrites)));
            }
        }
        return builder.build();
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
            for (final Map.Entry<Selection, Boolean> selectionOverwrite : this.selectionOverwrites.entrySet()) {
                final Selection selection = selectionOverwrite.getKey();
                if (selection.matches(mailbox)) {
                    selectionOverwrite.setValue(selected);
                    break;
                }
            }
            selectionOverwrites.put(new Selection(mailbox.getId(), mailbox.getName(), mailbox.getRole()), selected);
        }
        updateSelectableMailboxes();
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
