package rs.ltt.android.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.UUID;

import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.worker.SetMailboxRoleWorker;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Role;

public class MailboxRepository extends AbstractMuaRepository {

    public MailboxRepository(Application application, long accountId) {
        super(application, accountId);
    }

    public LiveData<MailboxWithRoleAndName> getMailbox(final String id) {
        return database.mailboxDao().getMailboxLiveData(id);
    }

    public LiveData<List<MailboxWithRoleAndName>> getLabels() {
        return database.mailboxDao().getLabels();
    }

    public LiveData<List<String>> getMailboxIdsForThreadsLiveData(final String[] threadIds) {
        return database.mailboxDao().getMailboxIdsForThreadsLiveData(threadIds);
    }

    public LiveData<List<MailboxOverwriteEntity>> getMailboxOverwrites(String[] threadIds) {
        return database.overwriteDao().getMailboxOverwrites(threadIds);
    }

    public UUID setRole(final IdentifiableMailboxWithRole mailbox, final Role role) {
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SetMailboxRoleWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(SetMailboxRoleWorker.data(accountId, mailbox.getId(), role))
                .build();
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.enqueueUniqueWork(
                SetMailboxRoleWorker.uniqueName(accountId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
        );
        return workRequest.getId();
    }
}
