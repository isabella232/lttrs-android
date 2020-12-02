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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import rs.ltt.android.entity.AccountEntity;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.CredentialsEntity;
import rs.ltt.jmap.common.entity.Account;

@Dao
public abstract class AccountDao {

    @Query("select account.id as id, username,password,sessionResource,accountId from credentials join account on credentialsId = credentials.id where account.id=:id limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getAccountFuture(Long id);

    @Query("select account.id as id, username,password,sessionResource,accountId from credentials join account on credentialsId = credentials.id where account.id=:id limit 1")
    public abstract AccountWithCredentials getAccount(Long id);

    @Query("select account.id as id, username,password,sessionResource,accountId from credentials join account on credentialsId = credentials.id order by lastSelectedAt desc limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getMostRecentlySelectedAccountFuture();

    @Query("select id from account order by lastSelectedAt desc limit 1")
    public abstract Long getMostRecentlySelectedAccountId();

    @Insert
    abstract Long insert(CredentialsEntity entity);

    @Insert
    abstract Long insert(AccountEntity entity);

    @Query("select exists (select 1 from account)")
    public abstract boolean hasAccounts();

    @Transaction
    public List<AccountWithCredentials> insert(String username,
                                               String password,
                                               HttpUrl sessionResource,
                                               String primaryAccountId,
                                               Map<String, Account> accounts) {
        final ImmutableList.Builder<AccountWithCredentials> builder = ImmutableList.builder();
        final long now = System.currentTimeMillis();
        final Long credentialId = insert(new CredentialsEntity(
                username,
                password,
                sessionResource
        ));
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            final String accountId = entry.getKey();
            final String name = entry.getValue().getName();
            final Long id = insert(new AccountEntity(
                    credentialId,
                    accountId,
                    name,
                    accountId.equals(primaryAccountId) ? now + 1 : now
            ));
            builder.add(new AccountWithCredentials(
                    id,
                    accountId,
                    username,
                    password,
                    sessionResource
            ));
        }
        return builder.build();
    }
}
