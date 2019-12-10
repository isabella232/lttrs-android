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

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.SearchSuggestionEntity;
import rs.ltt.android.util.SetupCache;
import rs.ltt.jmap.client.session.FileSessionCache;
import rs.ltt.jmap.common.entity.Account;
import rs.ltt.jmap.mua.Mua;
import rs.ltt.jmap.mua.Status;

public class MainRepository {

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final AppDatabase appDatabase;
    private final Application application;

    public MainRepository(Application application) {
        this.application = application;
        this.appDatabase = AppDatabase.getInstance(application);
    }

    public void insertSearchSuggestion(String term) {
        IO_EXECUTOR.execute(() -> appDatabase.searchSuggestionDao().insert(SearchSuggestionEntity.of(term)));
    }

    public ListenableFuture<Void> insertAccountsRefreshMailboxes(final String username,
                                                                 final String password,
                                                                 final HttpUrl sessionResource,
                                                                 final String primaryAccountId,
                                                                 final Map<String, Account> accounts) {
        final SettableFuture<Void> settableFuture = SettableFuture.create();
        IO_EXECUTOR.execute(() -> {
            try {
                List<AccountWithCredentials> credentials = appDatabase.accountDao().insert(
                        username,
                        password,
                        sessionResource,
                        primaryAccountId,
                        accounts
                );
                SetupCache.invalidate();
                final Collection<ListenableFuture<Status>> mailboxRefreshes = Collections2.transform(
                        credentials,
                        new Function<AccountWithCredentials, ListenableFuture<Status>>() {
                            @NullableDecl
                            @Override
                            public ListenableFuture<Status> apply(@NullableDecl AccountWithCredentials account) {
                                return retrieveMailboxes(account);
                            }
                        });
                settableFuture.setFuture(Futures.whenAllComplete(mailboxRefreshes).call(() -> null, MoreExecutors.directExecutor()));
            } catch (Exception e) {
                settableFuture.setException(e);
            }
        });
        return settableFuture;
    }

    public LiveData<Boolean> hasAccounts() {
        return Transformations.distinctUntilChanged(appDatabase.accountDao().hasAccountsLiveData());
    }

    public ListenableFuture<AccountWithCredentials> getAccount(@Nullable final Long id) {
        if (id == null) {
            return appDatabase.accountDao().getMostRecentlySelectedAccountFuture();
        } else {
            return appDatabase.accountDao().getAccountFuture(id);
        }
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
}
