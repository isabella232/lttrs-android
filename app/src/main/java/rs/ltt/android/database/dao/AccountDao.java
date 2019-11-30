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

package rs.ltt.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;

import okhttp3.HttpUrl;
import rs.ltt.android.entity.AccountEntity;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.CredentialsEntity;
import rs.ltt.jmap.common.entity.Account;

@Dao
public abstract class AccountDao {

    @Query("select account.id as id, username,password,connectionUrl,accountId from credentials join account on credentialsId = credentials.id where account.id=:id limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getAccountFuture(Long id);

    @Query("select account.id as id, username,password,connectionUrl,accountId from credentials join account on credentialsId = credentials.id where account.id=:id limit 1")
    public abstract AccountWithCredentials getAccount(Long id);

    @Query("select account.id as id, username,password,connectionUrl,accountId from credentials join account on credentialsId = credentials.id order by lastSelectedAt desc limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getMostRecentlySelectedAccountFuture();

    @Insert
    abstract Long insert(CredentialsEntity entity);

    @Insert
    abstract void insert(AccountEntity entity);

    @Query("select exists (select 1 from account)")
    public abstract LiveData<Boolean> hasAccountsLiveData();

    @Query("select exists (select 1 from account)")
    public abstract boolean hasAccounts();

    @Transaction
    public void insert(String username,
                       String password,
                       HttpUrl connectionUrl,
                       String primaryAccountId,
                       Map<String, Account> accounts) {
        final long now = System.currentTimeMillis();
        final Long credentialId = insert(new CredentialsEntity(
                username,
                password,
                connectionUrl
        ));
        for(Map.Entry<String, Account> entry : accounts.entrySet()) {
            insert(new AccountEntity(
                    credentialId,
                    entry.getKey(),
                    entry.getValue().getName(),
                    entry.getKey().equals(primaryAccountId) ? now + 1 : now
            ));
        }
    }
}
