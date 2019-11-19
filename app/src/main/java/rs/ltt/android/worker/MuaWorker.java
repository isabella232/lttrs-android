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
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.ExecutionException;

import rs.ltt.android.Credentials;
import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.jmap.client.api.MethodErrorResponseException;
import rs.ltt.jmap.client.session.SessionFileCache;
import rs.ltt.jmap.common.method.MethodErrorResponse;
import rs.ltt.jmap.common.method.error.StateMismatchMethodErrorResponse;
import rs.ltt.jmap.mua.Mua;

public abstract class MuaWorker extends Worker {

    public static final String TAG_EMAIL_MODIFICATION = "email_modification";

    protected final LttrsDatabase database;
    protected final Mua mua;

    MuaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.database = LttrsDatabase.getInstance(getApplicationContext(), Credentials.username);
        this.mua = Mua.builder()
                .password(Credentials.password)
                .username(Credentials.username)
                .accountId(Credentials.accountId)
                .cache(new DatabaseCache(this.database))
                .sessionCache(new SessionFileCache(getApplicationContext().getCacheDir()))
                .build();
    }

    static boolean shouldRetry(ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof MethodErrorResponseException) {
            MethodErrorResponse methodError = ((MethodErrorResponseException) cause).getMethodErrorResponse();
            return methodError instanceof StateMismatchMethodErrorResponse;
        }
        return false;
    }
}
