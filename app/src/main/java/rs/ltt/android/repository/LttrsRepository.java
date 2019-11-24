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

import androidx.annotation.NonNull;
import androidx.core.util.Preconditions;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import rs.ltt.android.worker.CopyToMailboxWorker;
import rs.ltt.android.worker.MarkImportantWorker;
import rs.ltt.android.worker.ModifyKeywordWorker;
import rs.ltt.android.worker.MoveToInboxWorker;
import rs.ltt.android.worker.MoveToTrashWorker;
import rs.ltt.android.worker.MuaWorker;
import rs.ltt.android.worker.RemoveFromMailboxWorker;
import rs.ltt.jmap.client.session.FileSessionCache;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Keyword;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.Mua;

public class LttrsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsRepository.class);

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final long INITIAL_DELAY_DURATION = 4;
    private static final TimeUnit INITIAL_DELAY_TIME_UNIT = TimeUnit.SECONDS;
    private static final Constraints CONNECTED_CONSTRAINT = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

    protected final LttrsDatabase database;
    protected final Application application;
    protected final Mua mua;


    LttrsRepository(final Application application) {
        this.application = application;
        this.database = LttrsDatabase.getInstance(application, Credentials.username);
        this.mua = Mua.builder()
                .password(Credentials.password)
                .username(Credentials.username)
                .accountId(Credentials.accountId)
                .cache(new DatabaseCache(this.database))
                .sessionCache(new FileSessionCache(application.getCacheDir()))
                .queryPageSize(20L)
                .build();
    }

    public LiveData<List<MailboxOverviewItem>> getMailboxes() {
        return database.mailboxDao().getMailboxes();
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
                        true),
                QueryItemOverwriteEntity.Type.MAILBOX
        );
    }

    private void insertQueryItemOverwrite(final String threadId, final String keyword) {
        insertQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .hasKeyword(keyword)
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.KEYWORD
        );
    }

    private void insertQueryItemOverwrite(final String threadId, final EmailQuery emailQuery, final QueryItemOverwriteEntity.Type type) {
        final String queryString = emailQuery.toQueryString();
        final QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().insert(new QueryItemOverwriteEntity(queryEntity.id, threadId, type));
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
                        true),
                QueryItemOverwriteEntity.Type.MAILBOX
        );
    }

    private void deleteQueryItemOverwrite(final String threadId, final String keyword) {
        deleteQueryItemOverwrite(threadId,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .hasKeyword(keyword)
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.KEYWORD
        );
    }

    private void deleteQueryItemOverwrite(final String threadId, final EmailQuery emailQuery, QueryItemOverwriteEntity.Type type) {
        final String queryString = emailQuery.toQueryString();
        QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().delete(new QueryItemOverwriteEntity(queryEntity.id, threadId, type));
        }
    }

    public void removeFromMailbox(final String threadId, final IdentifiableMailboxWithRole mailbox) {
        IO_EXECUTOR.execute(() -> {
            if (mailbox.getRole() == Role.IMPORTANT) {
                markNotImportant(threadId, mailbox);
                return;
            }
            insertQueryItemOverwrite(threadId, mailbox);
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(RemoveFromMailboxWorker.data(threadId, mailbox))
                    .setInitialDelay(INITIAL_DELAY_DURATION, INITIAL_DELAY_TIME_UNIT)
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    RemoveFromMailboxWorker.uniqueName(threadId, mailbox),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void copyToMailbox(@NonNull final String threadId, @NonNull final IdentifiableMailboxWithRole mailbox) {
        IO_EXECUTOR.execute(() -> {
            if (mailbox.getRole() == Role.IMPORTANT) {
                markImportantNow(threadId);
                return;
            }
            deleteQueryItemOverwrite(threadId, mailbox);
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CopyToMailboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(CopyToMailboxWorker.data(threadId, mailbox))
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    CopyToMailboxWorker.uniqueName(threadId, mailbox),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public void archive(final String threadId) {
        IO_EXECUTOR.execute(() -> {
            insertQueryItemOverwrite(threadId, Role.INBOX);
            deleteQueryItemOverwrite(threadId, Role.ARCHIVE);
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, false));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.ARCHIVE, true));

            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ArchiveWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(ArchiveWorker.data(threadId))
                    .setInitialDelay(INITIAL_DELAY_DURATION, INITIAL_DELAY_TIME_UNIT)
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
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
                    .setInitialDelay(INITIAL_DELAY_DURATION, INITIAL_DELAY_TIME_UNIT)
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    MoveToInboxWorker.uniqueName(threadId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
        });
    }

    public ListenableFuture<LiveData<WorkInfo>> moveToTrash(final String threadId) {
        final SettableFuture<LiveData<WorkInfo>> future = SettableFuture.create();
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
                    .setInitialDelay(INITIAL_DELAY_DURATION, TimeUnit.SECONDS)
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueueUniqueWork(
                    MoveToTrashWorker.uniqueName(threadId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );
            future.set(workManager.getWorkInfoByIdLiveData(workRequest.getId()));
        });
        return future;
    }

    public void cancelMoveToTrash(final WorkInfo workInfo, final String threadId) {
        Preconditions.checkNotNull(workInfo,"Unable to cancel moveToTrash operation.");
        WorkManager.getInstance(application).cancelWorkById(workInfo.getId());
        IO_EXECUTOR.execute(() -> database.overwriteDao().revertMailboxOverwrites(threadId));
    }

    public void markImportant(final String threadId) {
        IO_EXECUTOR.execute(() -> markImportantNow(threadId));
    }

    private void markImportantNow(final String threadId) {
        database.overwriteDao().insert(
                MailboxOverwriteEntity.of(threadId, Role.IMPORTANT, true)
        );
        deleteQueryItemOverwrite(threadId, Role.IMPORTANT);
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MarkImportantWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(MarkImportantWorker.data(threadId))
                .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                .build();
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.enqueueUniqueWork(
                MarkImportantWorker.uniqueName(threadId),
                ExistingWorkPolicy.REPLACE,
                workRequest
        );
    }

    public void markNotImportant(final String threadId) {
        IO_EXECUTOR.execute(() -> {
            final MailboxWithRoleAndName mailbox = Preconditions.checkNotNull(
                    database.mailboxDao().getMailbox(Role.IMPORTANT),
                    "No mailbox with role=IMPORTANT found in cache"
            );
            markNotImportant(threadId, mailbox);
        });
    }

    private void markNotImportant(String threadId, IdentifiableMailboxWithRole mailbox) {
        Preconditions.checkArgument(mailbox.getRole() == Role.IMPORTANT);
        insertQueryItemOverwrite(threadId, mailbox);
        database.overwriteDao().insert(
                MailboxOverwriteEntity.of(threadId, Role.IMPORTANT, false)
        );

        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(RemoveFromMailboxWorker.data(threadId, mailbox))
                .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                .setInitialDelay(INITIAL_DELAY_DURATION, INITIAL_DELAY_TIME_UNIT)
                .build();
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.enqueueUniqueWork(
                MarkImportantWorker.uniqueName(threadId),
                ExistingWorkPolicy.REPLACE,
                workRequest
        );
    }

    public void toggleFlagged(final String threadId, final boolean targetState) {
        toggleKeyword(threadId, Keyword.FLAGGED, targetState);
    }

    public void removeKeyword(final String threadId, final String keyword) {
        toggleKeyword(threadId, keyword, false);
    }

    public void addKeyword(final String threadId, final String keyword) {
        toggleKeyword(threadId, keyword, true);
    }

    private void toggleKeyword(final String threadId, final String keyword, final boolean targetState) {
        Preconditions.checkNotNull(threadId);
        Preconditions.checkNotNull(keyword);
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
                    .addTag(MuaWorker.TAG_EMAIL_MODIFICATION)
                    .setInitialDelay(targetState ? 0 : INITIAL_DELAY_DURATION, INITIAL_DELAY_TIME_UNIT)
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
