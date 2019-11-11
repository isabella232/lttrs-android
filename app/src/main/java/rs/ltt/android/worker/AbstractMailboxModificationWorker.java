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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rs.ltt.android.entity.EmailWithMailboxes;

public abstract class AbstractMailboxModificationWorker extends MuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMailboxModificationWorker.class);

    private static final String THREAD_ID_KEY = "threadId";

    protected final String threadId;

    public AbstractMailboxModificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = getInputData();
        this.threadId = data.getString(THREAD_ID_KEY);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<EmailWithMailboxes> emails = threadId == null ? Collections.emptyList() : database.threadAndEmailDao().getEmailsWithMailboxes(threadId);
        try {
            final boolean madeChanges = modify(emails).get();
            if (!madeChanges) {
                LOGGER.info("No changes were made to thread {}", threadId);
                database.overwriteDao().deleteOverwritesForMailboxModification(threadId);
            }
            return Result.success();
        } catch (ExecutionException e) {
            LOGGER.warn(String.format("Unable to modify emails in thread %s", threadId), e);
            if (shouldRetry(e)) {
                return Result.retry();
            } else {
                database.overwriteDao().deleteOverwritesForMailboxModification(threadId);
                return Result.failure();
            }
        } catch (InterruptedException e) {
            return Result.retry();
        }
    }

    protected abstract ListenableFuture<Boolean> modify(List<EmailWithMailboxes> emails);

    public static String uniqueName(String threadId) {
        return "mailbox-modification-" + threadId;
    }

    public static Data data(String threadId) {
        return new Data.Builder()
                .putString(THREAD_ID_KEY, threadId)
                .build();
    }
}
