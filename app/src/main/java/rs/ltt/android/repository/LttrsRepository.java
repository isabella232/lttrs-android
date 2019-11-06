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

package rs.ltt.android.repository;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import rs.ltt.android.Credentials;
import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.KeywordOverwriteEntity;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.QueryEntity;
import rs.ltt.android.entity.QueryItemOverwriteEntity;
import rs.ltt.android.worker.AbstractMailboxModificationWorker;
import rs.ltt.android.worker.ArchiveWorker;
import rs.ltt.android.worker.ModifyKeywordWorker;
import rs.ltt.android.worker.MoveToInboxWorker;
import rs.ltt.android.worker.MoveToTrashWorker;
import rs.ltt.android.worker.MuaWorker;
import rs.ltt.android.worker.RemoveFromMailboxWorker;
import rs.ltt.jmap.client.session.SessionFileCache;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Keyword;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.Mua;

public abstract class LttrsRepository {

    private static final Constraints CONNECTED_CONSTRAINT = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

    protected final LttrsDatabase database;

    protected final Application application;

    protected final Mua mua;

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();


    LttrsRepository(final Application application) {
        this.application = application;
        this.database = LttrsDatabase.getInstance(application, Credentials.username);
        this.mua = Mua.builder()
                .password(Credentials.password)
                .username(Credentials.username)
                .cache(new DatabaseCache(this.database))
                .sessionCache(new SessionFileCache(application.getCacheDir()))
                .queryPageSize(20L)
                .build();
    }

    private void insert(final KeywordOverwriteEntity keywordOverwriteEntity) {
        Log.d("lttrs", "db insert keyword overwrite " + keywordOverwriteEntity.value);
        database.overwriteDao().insert(keywordOverwriteEntity);
    }

    private void insertQueryItemOverwrite(final String threadId, final Role role) {
        MailboxOverviewItem mailbox = database.mailboxDao().getMailboxOverviewItem(role);
        if (mailbox != null) {
            insertQueryItemOverwrite(threadId, mailbox);
        }
    }

    private void insertQueryItemOverwrite(final String threadId, final IdentifiableMailboxWithRole mailbox) {
        insertQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .inMailbox(mailbox.getId())
                                .build(),
                        true));
    }

    private void insertQueryItemOverwrite(final String threadId, final String keyword) {
        insertQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .hasKeyword(keyword)
                                .build(),
                        true)
        );
    }

    private void insertQueryItemOverwrite(final String threadId, final EmailQuery emailQuery) {
        final String queryString = emailQuery.toQueryString();
        final QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().insert(new QueryItemOverwriteEntity(queryEntity.id, threadId));
        } else {
            Log.d("lttrs", "do not enter overwrite");
        }
    }

    private void deleteQueryItemOverwrite(final String threadId, final Role role) {
        MailboxOverviewItem mailbox = database.mailboxDao().getMailboxOverviewItem(role);
        if (mailbox != null) {
            deleteQueryItemOverwrite(threadId, mailbox);
        }
    }

    private void deleteQueryItemOverwrite(final String threadId, final IdentifiableMailboxWithRole mailbox) {
        deleteQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder().
                                inMailbox(mailbox.getId())
                                .build(),
                        true)
        );
    }

    private void deleteQueryItemOverwrite(final String threadId, final String keyword) {
        deleteQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .hasKeyword(keyword)
                                .build(),
                        true)
        );
    }

    private void deleteQueryItemOverwrite(final String threadId, final EmailQuery emailQuery) {
        final String queryString = emailQuery.toQueryString();
        QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().delete(new QueryItemOverwriteEntity(queryEntity.id, threadId));
        }
    }

    public void removeFromMailbox(final String threadId, final IdentifiableMailboxWithRole mailbox) {
        IO_EXECUTOR.execute(() -> {
            insertQueryItemOverwrite(threadId, mailbox);
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(RemoveFromMailboxWorker.data(threadId, mailbox))
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    MuaWorker.SYNC_LABELS,
                    ExistingWorkPolicy.APPEND,
                    workRequest
            );
        });
    }

    public void archive(final String threadId) {
        IO_EXECUTOR.execute(() -> {
            Log.d("lttrs","archiving "+threadId);
            insertQueryItemOverwrite(threadId, Role.INBOX);
            deleteQueryItemOverwrite(threadId, Role.ARCHIVE);
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, false));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.ARCHIVE, true));
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ArchiveWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(ArchiveWorker.data(threadId))
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    ArchiveWorker.uniqueName(threadId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void moveToInbox(final String threadId) {
        IO_EXECUTOR.execute(() -> {
            insertQueryItemOverwrite(threadId, Role.ARCHIVE);
            insertQueryItemOverwrite(threadId, Role.TRASH);
            deleteQueryItemOverwrite(threadId, Role.INBOX);

            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, true));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.ARCHIVE, false));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.TRASH, false));

            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToInboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(AbstractMailboxModificationWorker.data(threadId))
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    MoveToInboxWorker.uniqueName(threadId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void moveToTrash(final String threadId) {
        IO_EXECUTOR.execute(() -> {
            for (MailboxWithRoleAndName mailbox : database.mailboxDao().getMailboxesForThread(threadId)) {
                if (mailbox.role != Role.TRASH) {
                    insertQueryItemOverwrite(threadId, mailbox);
                }
            }
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, false));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.TRASH, true));
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToTrashWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(MoveToTrashWorker.data(threadId))
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    MoveToTrashWorker.uniqueName(threadId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void toggleFlagged(final String threadId, final boolean targetState) {
        toggleKeyword(threadId, Keyword.FLAGGED, targetState);
    }

    public void removeKeyword(final String threadId, final String keyword) {
        toggleKeyword(threadId, keyword, false);
    }

    private void toggleKeyword(final String threadId, final String keyword, final boolean targetState) {
        IO_EXECUTOR.execute(() -> {
            final KeywordOverwriteEntity keywordOverwriteEntity = new KeywordOverwriteEntity(threadId, keyword, targetState);
            insert(keywordOverwriteEntity);
            if (targetState) {
                deleteQueryItemOverwrite(threadId, keyword);
            } else {
                insertQueryItemOverwrite(threadId, keyword);
            }
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ModifyKeywordWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(ModifyKeywordWorker.data(threadId, keyword, targetState))
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    ModifyKeywordWorker.uniqueName(threadId, keyword),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void markRead(String threadId) {
        toggleKeyword(threadId, Keyword.SEEN, true);
    }

    public void markUnRead(String threadId) {
        toggleKeyword(threadId, Keyword.SEEN, false);
    }
}
