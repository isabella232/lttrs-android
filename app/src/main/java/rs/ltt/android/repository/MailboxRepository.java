package rs.ltt.android.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import rs.ltt.android.entity.MailboxWithRoleAndName;

public class MailboxRepository extends AbstractMuaRepository {

    public MailboxRepository(Application application, long accountId) {
        super(application, accountId);
    }

    public LiveData<MailboxWithRoleAndName> getMailbox(final String id) {
        return database.mailboxDao().getMailboxLiveData(id);
    }
}
