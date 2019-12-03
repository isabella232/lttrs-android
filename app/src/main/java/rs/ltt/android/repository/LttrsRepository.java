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
import androidx.lifecycle.Transformations;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.KeywordOverwriteEntity;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.QueryEntity;
import rs.ltt.android.entity.QueryItemOverwriteEntity;
import rs.ltt.android.util.FutureLiveData;
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
import rs.ltt.jmap.mua.util.KeywordUtil;

public class LttrsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsRepository.class);

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final long INITIAL_DELAY_DURATION = 4;
    private static final TimeUnit INITIAL_DELAY_TIME_UNIT = TimeUnit.SECONDS;
    private static final Constraints CONNECTED_CONSTRAINT = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

    protected final Application application;
    protected final ListenableFuture<AccountWithCredentials> account;
    protected final ListenableFuture<LttrsDatabase> database;
    protected final LiveData<LttrsDatabase> databaseLiveData;
    protected final ListenableFuture<Mua> mua;


    LttrsRepository(final Application application,
                    final ListenableFuture<AccountWithCredentials> accountFuture) {
        this.application = application;
        this.account = accountFuture;
        LOGGER.debug("creating instance of {}", getClass().getSimpleName());
        this.database = Futures.transform(accountFuture, new Function<AccountWithCredentials, LttrsDatabase>() {
            @NullableDecl
            @Override
            public LttrsDatabase apply(@NullableDecl AccountWithCredentials account) {
                Preconditions.checkNotNull(account);
                return LttrsDatabase.getInstance(application, account.id);
            }
        }, IO_EXECUTOR);
        this.databaseLiveData = new FutureLiveData<>(this.database);
        this.mua = Futures.transform(this.database, new Function<LttrsDatabase, Mua>() {
            @NullableDecl
            @Override
            public Mua apply(@NullableDecl LttrsDatabase database) {
                final AccountWithCredentials account = requireAccount();
                return Mua.builder()
                        .username(account.username)
                        .password(account.password)
                        .accountId(account.accountId)
                        .sessionResource(account.sessionResource)
                        .cache(new DatabaseCache(database))
                        .sessionCache(new FileSessionCache(application.getCacheDir()))
                        .queryPageSize(20L)
                        .build();
            }
        }, MoreExecutors.directExecutor());
    }

    @NonNull
    LttrsDatabase requireDatabase() {
        try {
            return database.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Unable to acquire Lttrs database", e);
            throw new IllegalStateException(e);
        }
    }

    private AccountWithCredentials requireAccount() {
        try {
            return this.account.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Unable to acquire account credentials");
            throw new IllegalStateException(e);
        }
    }

    public LiveData<List<MailboxOverviewItem>> getMailboxes() {
        return Transformations.switchMap(this.databaseLiveData, database -> database.mailboxDao().getMailboxes());
    }

    private void insert(final KeywordOverwriteEntity keywordOverwriteEntity) {
        Log.d("lttrs", "db insert keyword overwrite " + keywordOverwriteEntity.value);
        requireDatabase().overwriteDao().insert(keywordOverwriteEntity);
    }

    private void insertQueryItemOverwrite(final String threadId, final Role role) {
        MailboxOverviewItem mailbox = requireDatabase().mailboxDao().getMailboxOverviewItem(role);
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
                                .inMailboxOtherThan(
                                        requireDatabase().mailboxDao().getMailboxes(Role.TRASH, Role.JUNK)
                                )
                                .hasKeyword(keyword)
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.KEYWORD
        );
    }

    private void insertQueryItemOverwrite(final String threadId,
                                          final EmailQuery emailQuery,
                                          final QueryItemOverwriteEntity.Type type) {
        final String queryString = emailQuery.toQueryString();
        final QueryEntity queryEntity = requireDatabase().queryDao().get(queryString);
        if (queryEntity != null) {
            requireDatabase().overwriteDao().insert(new QueryItemOverwriteEntity(queryEntity.id, threadId, type));
        }
    }

    private void deleteQueryItemOverwrite(final String threadId, final Role role) {
        MailboxOverviewItem mailbox = requireDatabase().mailboxDao().getMailboxOverviewItem(role);
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
        QueryEntity queryEntity = requireDatabase().queryDao().get(queryString);
        if (queryEntity != null) {
            requireDatabase().overwriteDao().delete(new QueryItemOverwriteEntity(queryEntity.id, threadId, type));
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
                    .setInputData(RemoveFromMailboxWorker.data(requireAccount().id, threadId, mailbox))
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
                    .setInputData(CopyToMailboxWorker.data(requireAccount().id, threadId, mailbox))
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
            requireDatabase().overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, false));
            requireDatabase().overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.ARCHIVE, true));

            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ArchiveWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(ArchiveWorker.data(requireAccount().id, threadId))
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

            requireDatabase().overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, true));
            requireDatabase().overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.ARCHIVE, false));
            requireDatabase().overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.TRASH, false));

            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToInboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(MoveToInboxWorker.data(requireAccount().id, threadId))
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
            final LttrsDatabase database = requireDatabase();
            for (MailboxWithRoleAndName mailbox : database.mailboxDao().getMailboxesForThread(threadId)) {
                if (mailbox.role != Role.TRASH) {
                    insertQueryItemOverwrite(threadId, mailbox);
                }
            }
            for (String keyword : KeywordUtil.KEYWORD_ROLE.keySet()) {
                insertQueryItemOverwrite(threadId, keyword);
            }
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.INBOX, false));
            database.overwriteDao().insert(MailboxOverwriteEntity.of(threadId, Role.TRASH, true));
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToTrashWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(MoveToTrashWorker.data(requireAccount().id, threadId))
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
        Preconditions.checkNotNull(workInfo, "Unable to cancel moveToTrash operation.");
        WorkManager.getInstance(application).cancelWorkById(workInfo.getId());
        IO_EXECUTOR.execute(() -> {
            requireDatabase().overwriteDao().revertMailboxOverwrites(threadId);
            requireDatabase().overwriteDao().deleteQueryOverwritesByThread(threadId, QueryItemOverwriteEntity.Type.KEYWORD);
        });
    }

    public void markImportant(final String threadId) {
        IO_EXECUTOR.execute(() -> markImportantNow(threadId));
    }

    private void markImportantNow(final String threadId) {
        requireDatabase().overwriteDao().insert(
                MailboxOverwriteEntity.of(threadId, Role.IMPORTANT, true)
        );
        deleteQueryItemOverwrite(threadId, Role.IMPORTANT);
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MarkImportantWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(MarkImportantWorker.data(requireAccount().id, threadId))
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
                    requireDatabase().mailboxDao().getMailbox(Role.IMPORTANT),
                    "No mailbox with role=IMPORTANT found in cache"
            );
            markNotImportant(threadId, mailbox);
        });
    }

    private void markNotImportant(String threadId, IdentifiableMailboxWithRole mailbox) {
        Preconditions.checkArgument(mailbox.getRole() == Role.IMPORTANT);
        insertQueryItemOverwrite(threadId, mailbox);
        requireDatabase().overwriteDao().insert(
                MailboxOverwriteEntity.of(threadId, Role.IMPORTANT, false)
        );

        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(RemoveFromMailboxWorker.data(requireAccount().id, threadId, mailbox))
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
                    .setInputData(ModifyKeywordWorker.data(requireAccount().id, threadId, keyword, targetState))
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
