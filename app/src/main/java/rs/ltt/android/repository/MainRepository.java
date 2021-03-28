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

import androidx.lifecycle.LiveData;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import rs.ltt.android.LttrsApplication;
import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountName;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.SearchSuggestionEntity;
import rs.ltt.jmap.client.session.FileSessionCache;
import rs.ltt.jmap.common.entity.Account;
import rs.ltt.jmap.mua.Mua;
import rs.ltt.jmap.mua.Status;

public class MainRepository {

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final AppDatabase appDatabase;
    private final Application application;

    public MainRepository(final Application application) {
        this.application = application;
        this.appDatabase = AppDatabase.getInstance(application);
    }

    public void insertSearchSuggestion(String term) {
        IO_EXECUTOR.execute(() -> appDatabase.searchSuggestionDao().insert(SearchSuggestionEntity.of(term)));
    }

    public ListenableFuture<Long[]> insertAccountsRefreshMailboxes(final String username,
                                                                   final String password,
                                                                   final HttpUrl sessionResource,
                                                                   final String primaryAccountId,
                                                                   final Map<String, Account> accounts) {
        final SettableFuture<Long[]> settableFuture = SettableFuture.create();
        IO_EXECUTOR.execute(() -> {
            try {
                final List<AccountWithCredentials> credentials = appDatabase.accountDao().insert(
                        username,
                        password,
                        sessionResource,
                        primaryAccountId,
                        accounts
                );
                final Long[] ids = Lists.transform(credentials, c -> c.id).toArray(new Long[0]);
                LttrsApplication.get(application).invalidateMostRecentlySelectedAccountId();
                final Collection<ListenableFuture<Status>> mailboxRefreshes = Collections2.transform(credentials, this::retrieveMailboxes);
                settableFuture.setFuture(Futures.whenAllComplete(mailboxRefreshes).call(() -> ids, MoreExecutors.directExecutor()));
            } catch (Exception e) {
                settableFuture.setException(e);
            }
        });
        return settableFuture;
    }

    private ListenableFuture<Status> retrieveMailboxes(final AccountWithCredentials account) {
        final Mua mua = Mua.builder()
                .accountId(account.accountId)
                .username(account.username)
                .password(account.password)
                .sessionResource(account.sessionResource)
                .cache(new DatabaseCache(LttrsDatabase.getInstance(this.application, account.id)))
                .sessionCache(new FileSessionCache(application.getCacheDir()))
                .build();
        mua.refreshIdentities();
        return mua.refreshMailboxes();
    }


    public LiveData<AccountName> getAccountName(final Long id) {
        return this.appDatabase.accountDao().getAccountName(id);
    }

    public LiveData<List<AccountName>> getAccountNames() {
        return this.appDatabase.accountDao().getAccountNames();
    }
}
