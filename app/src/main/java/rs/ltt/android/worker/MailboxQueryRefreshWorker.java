package rs.ltt.android.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.util.StandardQueries;

public class MailboxQueryRefreshWorker extends AbstractQueryRefreshWorker {

    private final String mailboxId;

    public MailboxQueryRefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.mailboxId = data.getString(MAILBOX_ID_KEY);
    }

    public static Data data(final Long account, final String mailboxId) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(MAILBOX_ID_KEY, mailboxId)
                .build();
    }

    @Override
    EmailQuery getEmailQuery() {
        final IdentifiableMailboxWithRole mailbox;
        if (mailboxId != null) {
            mailbox = getDatabase().mailboxDao().getMailbox(mailboxId);
        } else {
            mailbox = getDatabase().mailboxDao().getMailbox(Role.INBOX);
        }
        return StandardQueries.mailbox(mailbox);
    }
}
