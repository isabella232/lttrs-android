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

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import okhttp3.HttpUrl;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.SearchSuggestionEntity;
import rs.ltt.android.util.SetupCache;
import rs.ltt.jmap.common.entity.Account;

public class MainRepository {

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final AppDatabase appDatabase;

    public MainRepository(Application application) {
        this.appDatabase = AppDatabase.getInstance(application);
    }

    public void insertSearchSuggestion(String term) {
        IO_EXECUTOR.execute(() -> appDatabase.searchSuggestionDao().insert(SearchSuggestionEntity.of(term)));
    }

    public ListenableFuture<Void> insertAccounts(final String username,
                                           final String password,
                                           final HttpUrl connectionUrl,
                                           final String primaryAccountId,
                                           final Map<String, Account> accounts) {
        final SettableFuture<Void> settableFuture = SettableFuture.create();
        IO_EXECUTOR.execute(() -> {
            appDatabase.accountDao().insert(
                    username,
                    password,
                    connectionUrl,
                    primaryAccountId,
                    accounts
            );
            SetupCache.invalidate();
            settableFuture.set(null);
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
}
