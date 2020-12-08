package rs.ltt.android.ui.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.repository.MailboxRepository;
import rs.ltt.jmap.common.entity.Mailbox;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.MailboxUtil;

public class ReassignRoleViewModel extends AndroidViewModel {

    private final MailboxRepository mailboxRepository;
    private final Long accountId;

    private final LiveData<MailboxWithRoleAndName> mailbox;
    private final LiveData<Boolean> isReassignment;
    private final Role role;

    public ReassignRoleViewModel(@NonNull final Application application,
                                 @NonNull final Long accountId,
                                 @NonNull final String mailboxId,
                                 @NonNull final Role role) {
        super(application);
        this.accountId = accountId;
        this.mailboxRepository = new MailboxRepository(application, accountId);
        this.mailbox = this.mailboxRepository.getMailbox(mailboxId);
        this.isReassignment = Transformations.map(this.mailbox, m-> m.getRole() != null);
        this.role = role;
    }

    public LiveData<Boolean> isReassignment() {
        return this.isReassignment;
    }

    public LiveData<MailboxWithRoleAndName> getMailbox() {
        return mailbox;
    }

    public String getHumanReadableRole() {
        //TODO replace with translated version
        return MailboxUtil.humanReadable(this.role);
    }

    public Role getRole() {
        return this.role;
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final Application application;
        private final Long accountId;
        private final String mailboxId;
        private final Role role;

        public Factory(Application application, Long accountId, String mailboxId, Role role) {
            this.application = application;
            this.accountId = accountId;
            this.mailboxId = mailboxId;
            this.role = role;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return modelClass.cast(new ReassignRoleViewModel(
                    application,
                    accountId,
                    mailboxId,
                    role
            ));
        }
    }
}
