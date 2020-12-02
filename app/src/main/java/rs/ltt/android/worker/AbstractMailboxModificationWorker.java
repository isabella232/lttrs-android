/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.EmailWithMailboxes;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.service.exception.PreexistingMailboxException;

public abstract class AbstractMailboxModificationWorker extends AbstractMuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMailboxModificationWorker.class);

    private static final String THREAD_ID_KEY = "threadId";

    public static final String EXCEPTION = "exception";
    public static final String PREEXISTING_MAILBOX_ID = "preexisting_mailbox_id";
    public static final String TARGET_ROLE = "role";

    protected final String threadId;

    AbstractMailboxModificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = getInputData();
        this.threadId = data.getString(THREAD_ID_KEY);
    }

    @NonNull
    @Override
    public Result doWork() {
        LttrsDatabase database = getDatabase();
        List<EmailWithMailboxes> emails = threadId == null ? Collections.emptyList() : database.threadAndEmailDao().getEmailsWithMailboxes(threadId);
        try {
            final boolean madeChanges = modify(emails).get();
            if (!madeChanges) {
                LOGGER.info("No changes were made to thread {}", threadId);
                database.overwriteDao().revertMailboxOverwrites(threadId);
            }
            return Result.success();
        } catch (final ExecutionException e) {
            LOGGER.warn(String.format("Unable to modify emails in thread %s", threadId), e);
            if (shouldRetry(e)) {
                return Result.retry();
            } else {
                database.overwriteDao().revertMailboxOverwrites(threadId);
                return Result.failure(exceptionToData(e.getCause()));
            }
        } catch (InterruptedException e) {
            return Result.retry();
        }
    }

    private static Data exceptionToData(final Throwable cause) {
        final Data.Builder dataBuilder =  new Data.Builder();
        if (cause == null) {
            return dataBuilder.build();
        }
        dataBuilder.putString(EXCEPTION, cause.getClass().getName());
        if (cause instanceof PreexistingMailboxException) {
            final IdentifiableMailboxWithRoleAndName preexistingMailbox = ((PreexistingMailboxException) cause).getPreexistingMailbox();
            final Role targetRole = ((PreexistingMailboxException) cause).getTargetRole();
            dataBuilder.putString(PREEXISTING_MAILBOX_ID, preexistingMailbox.getId());
            dataBuilder.putString(TARGET_ROLE, targetRole.toString());
        }
        return dataBuilder.build();
    }

    protected abstract ListenableFuture<Boolean> modify(List<EmailWithMailboxes> emails);

    public static Data data(Long account, String threadId) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(THREAD_ID_KEY, threadId)
                .build();
    }
}
