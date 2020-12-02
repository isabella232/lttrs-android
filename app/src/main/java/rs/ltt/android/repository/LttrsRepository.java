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

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.KeywordOverwriteEntity;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.QueryEntity;
import rs.ltt.android.entity.QueryItemOverwriteEntity;
import rs.ltt.android.worker.AbstractMuaWorker;
import rs.ltt.android.worker.ArchiveWorker;
import rs.ltt.android.worker.CopyToMailboxWorker;
import rs.ltt.android.worker.MarkImportantWorker;
import rs.ltt.android.worker.ModifyKeywordWorker;
import rs.ltt.android.worker.MoveToInboxWorker;
import rs.ltt.android.worker.MoveToTrashWorker;
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

    static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();
    static final Constraints CONNECTED_CONSTRAINT = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsRepository.class);
    protected final Application application;
    protected final long accountId;
    protected final LttrsDatabase database;
    protected final ListenableFuture<Mua> mua;

    LttrsRepository(final Application application, final long accountId) {
        this.application = application;
        this.accountId = accountId;
        LOGGER.debug("creating instance of {}", getClass().getSimpleName());
        this.database = LttrsDatabase.getInstance(application, accountId);
        this.mua = Futures.transform(getAccount(), account -> Mua.builder()
                .username(account.username)
                .password(account.password)
                .accountId(account.accountId)
                .sessionResource(account.sessionResource)
                .cache(new DatabaseCache(database))
                .sessionCache(new FileSessionCache(application.getCacheDir()))
                .queryPageSize(20L)
                .build(), MoreExecutors.directExecutor());
    }

    public ListenableFuture<AccountWithCredentials> getAccount() {
        return AppDatabase.getInstance(application).accountDao().getAccountFuture(accountId);
    }

    public LiveData<List<MailboxOverviewItem>> getMailboxes() {
        return database.mailboxDao().getMailboxes();
    }

    private void insert(final Collection<KeywordOverwriteEntity> keywordOverwriteEntities) {
        database.overwriteDao().insertKeywordOverwrites(keywordOverwriteEntities);
    }

    protected void insertQueryItemOverwrite(final String threadId, final Role role) {
        insertQueryItemOverwrite(ImmutableSet.of(threadId), role);
    }

    protected void insertQueryItemOverwrite(final Collection<String> threadIds, final Role role) {
        MailboxOverviewItem mailbox = database.mailboxDao().getMailboxOverviewItem(role);
        if (mailbox != null) {
            insertQueryItemOverwrite(threadIds, mailbox);
        }
    }

    private void insertQueryItemOverwrite(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        insertQueryItemOverwrite(threadIds,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .inMailbox(mailbox.getId())
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.MAILBOX
        );
    }

    protected void insertQueryItemOverwrite(final String threadId, final String keyword) {
        insertQueryItemOverwrite(ImmutableSet.of(threadId), keyword);
    }

    protected void insertQueryItemOverwrite(final Collection<String> threadIds, final String keyword) {
        insertQueryItemOverwrite(threadIds,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .inMailboxOtherThan(
                                        database.mailboxDao().getMailboxes(Role.TRASH, Role.JUNK)
                                )
                                .hasKeyword(keyword)
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.KEYWORD
        );
    }

    private void insertQueryItemOverwrite(final Collection<String> threadIds,
                                          final EmailQuery emailQuery,
                                          final QueryItemOverwriteEntity.Type type) {
        final String queryString = emailQuery.toQueryString();
        final QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().insertQueryOverwrites(
                    Collections2.transform(
                            threadIds,
                            threadId -> new QueryItemOverwriteEntity(queryEntity.id, threadId, type)
                    )
            );
        }
    }

    private void deleteQueryItemOverwrite(final Collection<String> threadIds, final Role role) {
        MailboxOverviewItem mailbox = database.mailboxDao().getMailboxOverviewItem(role);
        if (mailbox != null) {
            deleteQueryItemOverwrite(threadIds, mailbox);
        }
    }

    private void deleteQueryItemOverwrite(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        deleteQueryItemOverwrite(threadIds,
                EmailQuery.of(
                        EmailFilterCondition.builder().
                                inMailbox(mailbox.getId())
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.MAILBOX
        );
    }

    private void deleteQueryItemOverwrite(final Collection<String> threadIds, final String keyword) {
        deleteQueryItemOverwrite(threadIds,
                EmailQuery.of(
                        EmailFilterCondition.builder()
                                .hasKeyword(keyword)
                                .build(),
                        true),
                QueryItemOverwriteEntity.Type.KEYWORD
        );
    }

    private void deleteQueryItemOverwrite(final Collection<String> threadIds, final EmailQuery emailQuery, QueryItemOverwriteEntity.Type type) {
        final String queryString = emailQuery.toQueryString();
        QueryEntity queryEntity = database.queryDao().get(queryString);
        if (queryEntity != null) {
            database.overwriteDao().deleteQueryOverwrites(
                    Collections2.transform(
                            threadIds,
                            threadId -> new QueryItemOverwriteEntity(queryEntity.id, threadId, type)
                    )
            );
        }
    }

    public void removeFromMailbox(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        IO_EXECUTOR.execute(() -> {
            if (mailbox.getRole() == Role.IMPORTANT) {
                markNotImportant(threadIds, mailbox);
                return;
            }
            insertQueryItemOverwrite(threadIds, mailbox);
            final WorkManager workManager = WorkManager.getInstance(application);
            for (final String threadId : threadIds) {
                final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                        .setConstraints(CONNECTED_CONSTRAINT)
                        .setInputData(RemoveFromMailboxWorker.data(accountId, threadId, mailbox))
                        .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                        .build();
                workManager.enqueueUniqueWork(
                        RemoveFromMailboxWorker.uniqueName(accountId),
                        ExistingWorkPolicy.APPEND,
                        workRequest
                );
            }
        });
    }

    public void copyToMailbox(@NonNull final Collection<String> threadIds, @NonNull final IdentifiableMailboxWithRole mailbox) {
        IO_EXECUTOR.execute(() -> {
            if (mailbox.getRole() == Role.IMPORTANT) {
                markImportantNow(threadIds);
                return;
            }
            deleteQueryItemOverwrite(threadIds, mailbox);
            final WorkManager workManager = WorkManager.getInstance(application);
            for (final String threadId : threadIds) {
                final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CopyToMailboxWorker.class)
                        .setConstraints(CONNECTED_CONSTRAINT)
                        .setInputData(CopyToMailboxWorker.data(accountId, threadId, mailbox))
                        .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                        .build();
                workManager.enqueueUniqueWork(
                        CopyToMailboxWorker.uniqueName(accountId),
                        ExistingWorkPolicy.APPEND,
                        workRequest
                );
            }
        });
    }

    public void archive(final Collection<String> threadIds) {
        IO_EXECUTOR.execute(() -> {
            insertQueryItemOverwrite(threadIds, Role.INBOX);
            deleteQueryItemOverwrite(threadIds, Role.ARCHIVE);
            database.overwriteDao().insertMailboxOverwrites(MailboxOverwriteEntity.of(threadIds, Role.INBOX, false));
            database.overwriteDao().insertMailboxOverwrites(MailboxOverwriteEntity.of(threadIds, Role.ARCHIVE, true));
            final WorkManager workManager = WorkManager.getInstance(application);
            for (final String threadId : threadIds) {
                final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ArchiveWorker.class)
                        .setConstraints(CONNECTED_CONSTRAINT)
                        .setInputData(ArchiveWorker.data(accountId, threadId))
                        .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                        .build();

                workManager.enqueueUniqueWork(
                        ArchiveWorker.uniqueName(accountId),
                        ExistingWorkPolicy.APPEND,
                        workRequest
                );
            }
        });
    }

    public void moveToInbox(final Collection<String> threadIds) {
        IO_EXECUTOR.execute(() -> {
            insertQueryItemOverwrite(threadIds, Role.ARCHIVE);
            insertQueryItemOverwrite(threadIds, Role.TRASH);
            deleteQueryItemOverwrite(threadIds, Role.INBOX);

            database.overwriteDao().insertMailboxOverwrites(
                    MailboxOverwriteEntity.of(threadIds, Role.INBOX, true)
            );
            database.overwriteDao().insertMailboxOverwrites(
                    MailboxOverwriteEntity.of(threadIds, Role.ARCHIVE, false)
            );
            database.overwriteDao().insertMailboxOverwrites(
                    MailboxOverwriteEntity.of(threadIds, Role.TRASH, false)
            );
            final WorkManager workManager = WorkManager.getInstance(application);
            for (final String threadId : threadIds) {
                final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToInboxWorker.class)
                        .setConstraints(CONNECTED_CONSTRAINT)
                        .setInputData(MoveToInboxWorker.data(accountId, threadId))
                        .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                        .build();
                workManager.enqueueUniqueWork(
                        MoveToInboxWorker.uniqueName(accountId),
                        ExistingWorkPolicy.APPEND,
                        workRequest
                );
            }
        });
    }

    public ListenableFuture<LiveData<WorkInfo>> moveToTrash(final Collection<String> threadIds) {
        final SettableFuture<LiveData<WorkInfo>> future = SettableFuture.create();
        IO_EXECUTOR.execute(() -> {
            for (MailboxWithRoleAndName mailbox : database.mailboxDao().getMailboxesForThreads(threadIds)) {
                if (mailbox.role != Role.TRASH) {
                    insertQueryItemOverwrite(threadIds, mailbox);
                }
            }
            for (String keyword : KeywordUtil.KEYWORD_ROLE.keySet()) {
                insertQueryItemOverwrite(threadIds, keyword);
            }
            database.overwriteDao().insertMailboxOverwrites(
                    MailboxOverwriteEntity.of(threadIds, Role.INBOX, false)
            );
            database.overwriteDao().insertMailboxOverwrites(
                    MailboxOverwriteEntity.of(threadIds, Role.TRASH, true)
            );
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MoveToTrashWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(MoveToTrashWorker.data(accountId, threadIds))
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            final WorkManager workManager = WorkManager.getInstance(application);
            workManager.enqueue(workRequest);
            future.set(workManager.getWorkInfoByIdLiveData(workRequest.getId()));
        });
        return future;
    }

    public void cancelMoveToTrash(final WorkInfo workInfo, final Collection<String> threadIds) {
        Preconditions.checkNotNull(workInfo, "Unable to cancel moveToTrash operation.");
        WorkManager.getInstance(application).cancelWorkById(workInfo.getId());
        IO_EXECUTOR.execute(() -> {
            database.overwriteDao().revertMoveToTrashOverwrites(threadIds);
        });
    }

    public void markImportant(final Collection<String> threadIds) {
        IO_EXECUTOR.execute(() -> markImportantNow(threadIds));
    }

    private void markImportantNow(final Collection<String> threadIds) {
        database.overwriteDao().insertMailboxOverwrites(
                MailboxOverwriteEntity.of(threadIds, Role.IMPORTANT, true)
        );
        deleteQueryItemOverwrite(threadIds, Role.IMPORTANT);
        final WorkManager workManager = WorkManager.getInstance(application);
        for (final String threadId : threadIds) {
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MarkImportantWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(MarkImportantWorker.data(accountId, threadId))
                    .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            workManager.enqueueUniqueWork(
                    MarkImportantWorker.uniqueName(accountId),
                    ExistingWorkPolicy.APPEND,
                    workRequest
            );
        }
    }

    public void markNotImportant(final Collection<String> threadIds) {
        IO_EXECUTOR.execute(() -> {
            final MailboxWithRoleAndName mailbox = Preconditions.checkNotNull(
                    database.mailboxDao().getMailbox(Role.IMPORTANT),
                    "No mailbox with role=IMPORTANT found in cache"
            );
            markNotImportant(threadIds, mailbox);
        });
    }

    private void markNotImportant(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        Preconditions.checkArgument(mailbox.getRole() == Role.IMPORTANT);
        insertQueryItemOverwrite(threadIds, mailbox);
        database.overwriteDao().insertMailboxOverwrites(
                MailboxOverwriteEntity.of(threadIds, Role.IMPORTANT, false)
        );
        final WorkManager workManager = WorkManager.getInstance(application);
        for (final String threadId : threadIds) {
            final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RemoveFromMailboxWorker.class)
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .setInputData(RemoveFromMailboxWorker.data(accountId, threadId, mailbox))
                    .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                    .build();
            workManager.enqueueUniqueWork(
                    MarkImportantWorker.uniqueName(accountId),
                    ExistingWorkPolicy.APPEND,
                    workRequest
            );
        }
    }

    public void toggleFlagged(final Collection<String> threadIds, final boolean targetState) {
        toggleKeyword(threadIds, Keyword.FLAGGED, targetState);
    }

    public void removeKeyword(final Collection<String> threadIds, final String keyword) {
        toggleKeyword(threadIds, keyword, false);
    }

    public void addKeyword(final Collection<String> threadIds, final String keyword) {
        toggleKeyword(threadIds, keyword, true);
    }

    private void toggleKeyword(final Collection<String> threadIds, final String keyword, final boolean targetState) {
        Preconditions.checkNotNull(threadIds);
        Preconditions.checkNotNull(keyword);
        LOGGER.info("toggle keyword {} for threads {}", keyword, threadIds);
        IO_EXECUTOR.execute(() -> {
            final Collection<KeywordOverwriteEntity> entities = Collections2.transform(
                    threadIds,
                    threadId -> new KeywordOverwriteEntity(threadId, keyword, targetState)
            );
            insert(entities);
            if (targetState) {
                deleteQueryItemOverwrite(threadIds, keyword);
            } else {
                insertQueryItemOverwrite(threadIds, keyword);
            }
            final WorkManager workManager = WorkManager.getInstance(application);
            for (final String threadId : threadIds) {
                final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ModifyKeywordWorker.class)
                        .setConstraints(CONNECTED_CONSTRAINT)
                        .setInputData(ModifyKeywordWorker.data(accountId, threadId, keyword, targetState))
                        .addTag(AbstractMuaWorker.TAG_EMAIL_MODIFICATION)
                        .build();
                workManager.enqueueUniqueWork(
                        ModifyKeywordWorker.uniqueName(accountId),
                        ExistingWorkPolicy.APPEND,
                        workRequest
                );
            }
        });
    }

    public void markRead(final Collection<String> threadIds) {
        toggleKeyword(threadIds, Keyword.SEEN, true);
    }

    public void markUnRead(final Collection<String> threadIds) {
        toggleKeyword(threadIds, Keyword.SEEN, false);
    }
}
