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
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.Status;

public class QueryRepository extends LttrsRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryRepository.class);


    private final Set<String> runningQueries = new HashSet<>();
    private final Set<String> runningPagingRequests = new HashSet<>();

    private MutableLiveData<Set<String>> runningQueriesLiveData = new MutableLiveData<>(runningQueries);
    private MutableLiveData<Set<String>> runningPagingRequestsLiveData = new MutableLiveData<>(runningPagingRequests);


    public QueryRepository(Application application, ListenableFuture<AccountWithCredentials> account) {
        super(application, account);
    }

    public LiveData<PagedList<ThreadOverviewItem>> getThreadOverviewItems(final EmailQuery query) {
        return Transformations.switchMap(databaseLiveData, new Function<LttrsDatabase, LiveData<PagedList<ThreadOverviewItem>>>() {
            @Override
            public LiveData<PagedList<ThreadOverviewItem>> apply(LttrsDatabase database) {
                return new LivePagedListBuilder<>(database.queryDao().getThreadOverviewItems(query.toQueryString()), 30)
                        .setBoundaryCallback(new PagedList.BoundaryCallback<ThreadOverviewItem>() {
                            @Override
                            public void onZeroItemsLoaded() {
                                Log.d("lttrs", "onZeroItemsLoaded");
                                requestNextPage(query, null); //conceptually in terms of loading indicators this is more of a page request
                                super.onZeroItemsLoaded();
                            }

                            @Override
                            public void onItemAtEndLoaded(@NonNull ThreadOverviewItem itemAtEnd) {
                                Log.d("lttrs", "onItemAtEndLoaded(" + itemAtEnd.emailId + ")");
                                requestNextPage(query, itemAtEnd.emailId);
                                super.onItemAtEndLoaded(itemAtEnd);
                            }
                        })
                        .build();
            }
        });
    }

    public ListenableFuture<MailboxWithRoleAndName> getInbox() {
        return Futures.transformAsync(database, database -> database.mailboxDao().getMailboxFuture(Role.INBOX), MoreExecutors.directExecutor());
    }

    public ListenableFuture<MailboxWithRoleAndName> getImportant() {
        return Futures.transformAsync(database, database -> database.mailboxDao().getMailboxFuture(Role.IMPORTANT), MoreExecutors.directExecutor());
    }

    public LiveData<Boolean> isRunningQueryFor(final EmailQuery query) {
        return Transformations.map(runningQueriesLiveData, queryStrings -> queryStrings.contains(query.toQueryString()));
    }

    public LiveData<Boolean> isRunningPagingRequestFor(final EmailQuery query) {
        return Transformations.map(runningPagingRequestsLiveData, queryStrings -> queryStrings.contains(query.toQueryString()));
    }

    public void refresh(final EmailQuery emailQuery) {
        final String queryString = emailQuery.toQueryString();
        synchronized (this) {
            if (!runningQueries.add(queryString)) {
                Log.d("lttrs", "skipping refresh since already running");
                return;
            }
            if (runningPagingRequests.contains(queryString)) {
                //even though this refresh call is only implicit through the pageRequest we want to display something nice for the user
                runningQueries.add(queryString);
                runningQueriesLiveData.postValue(runningQueries);
                Log.d("lttrs", "skipping refresh since we are running a page request");
                return;
            }

        }
        final ListenableFuture<Status> statusFuture = Futures.transformAsync(mua, mua -> mua.query(emailQuery), MoreExecutors.directExecutor());
        statusFuture.addListener(() -> {
            synchronized (runningQueries) {
                runningQueries.remove(queryString);
            }
            runningQueriesLiveData.postValue(runningQueries);
            try {
                Status status = statusFuture.get();
            } catch (Exception e) {
                Log.d("lttrs", "unable to refresh", e);
            }
        }, MoreExecutors.directExecutor());
    }

    public void refreshInBackground(final EmailQuery emailQuery) {
        final String queryString = emailQuery.toQueryString();
        synchronized (this) {
            if (runningQueries.contains(queryString) || runningPagingRequests.contains(queryString)) {
                LOGGER.debug("skipping background refresh");
                return;
            }
            LOGGER.info("started background refresh");
            Futures.transformAsync(mua, mua -> mua.query(emailQuery), MoreExecutors.directExecutor());
        }
    }


    private void requestNextPage(final EmailQuery emailQuery, String afterEmailId) {
        final String queryString = emailQuery.toQueryString();
        synchronized (this) {
            if (!runningPagingRequests.add(queryString)) {
                Log.d("lttrs", "skipping paging request since already running");
                return;
            }
            runningPagingRequestsLiveData.postValue(runningPagingRequests);
        }
        final ListenableFuture<Status> hadResults;
        if (afterEmailId == null) {
            hadResults = Futures.transformAsync(mua, mua -> mua.query(emailQuery), MoreExecutors.directExecutor());
        } else {
            hadResults = Futures.transformAsync(mua, mua -> mua.query(emailQuery, afterEmailId), MoreExecutors.directExecutor());
        }
        hadResults.addListener(() -> {
            final boolean modifiedImplicitRefresh;
            synchronized (this) {
                runningPagingRequests.remove(queryString);
                modifiedImplicitRefresh = runningQueries.remove(queryString);
            }
            runningPagingRequestsLiveData.postValue(runningPagingRequests);
            if (modifiedImplicitRefresh) {
                runningQueriesLiveData.postValue(runningQueries);
            }
            try {
                Log.d("lttrs", "requestNextPageResult=" + hadResults.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                Log.d("lttrs", "error retrieving the next page", cause);
            } catch (Exception e) {
                Log.d("lttrs", "error paging ", e);
            }
        }, MoreExecutors.directExecutor());
    }

    public LiveData<MailboxOverviewItem> getMailboxOverviewItem(final String mailboxId) {
        if (mailboxId == null) {
            return Transformations.switchMap(databaseLiveData, database -> database.mailboxDao().getMailboxOverviewItemLiveData(Role.INBOX));
        } else {
            return Transformations.switchMap(databaseLiveData, database -> database.mailboxDao().getMailboxOverviewItemLiveData(mailboxId));
        }
    }

    public LiveData<String[]> getTrashAndJunk() {
        return Transformations.switchMap(databaseLiveData, database -> database.mailboxDao().getMailboxesLiveData(Role.TRASH, Role.JUNK));
    }
}
