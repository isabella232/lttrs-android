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

package rs.ltt.android.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.jmap.client.api.MethodErrorResponseException;
import rs.ltt.jmap.client.session.FileSessionCache;
import rs.ltt.jmap.common.method.MethodErrorResponse;
import rs.ltt.jmap.common.method.error.StateMismatchMethodErrorResponse;
import rs.ltt.jmap.mua.Mua;

public abstract class MuaWorker extends Worker {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuaWorker.class);

    public static final String TAG_EMAIL_MODIFICATION = "email_modification";

    protected static final String ACCOUNT_KEY = "account";

    private final Long account;

    MuaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = getInputData();
        if (data.hasKeyWithValueOfType(ACCOUNT_KEY, Long.class)) {
            this.account = data.getLong(ACCOUNT_KEY, 0L);
        } else {
            throw new IllegalStateException("Missing required account");
        }
    }

    static boolean shouldRetry(ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof MethodErrorResponseException) {
            MethodErrorResponse methodError = ((MethodErrorResponseException) cause).getMethodErrorResponse();
            return methodError instanceof StateMismatchMethodErrorResponse;
        }
        return false;
    }

    public LttrsDatabase getDatabase() {
        return LttrsDatabase.getInstance(getApplicationContext(), this.account);
    }

    public Mua getMua() {
        final AccountWithCredentials account = AppDatabase.getInstance(getApplicationContext()).accountDao().getAccount(this.account);
        return Mua.builder()
                .username(account.username)
                .password(account.password)
                .accountId(account.accountId)
                .sessionResource(account.sessionResource)
                .cache(new DatabaseCache(getDatabase()))
                .sessionCache(new FileSessionCache(getApplicationContext().getCacheDir()))
                .build();
    }
}
