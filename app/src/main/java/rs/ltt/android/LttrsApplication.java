/*
 * Copyright 2020 Daniel Gultsch
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

package rs.ltt.android;

import android.app.Activity;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;

import rs.ltt.android.database.AppDatabase;

public class LttrsApplication extends Application implements Configuration.Provider {

    private final Logger LOGGER = LoggerFactory.getLogger(LttrsApplication.class);

    private Long mostRecentlySelectedAccountId = null;

    private final static Object CACHE_LOCK = new Object();

    public boolean noAccountsConfigured() {
        return getMostRecentlySelectedAccountId() == null;
    }

    public Long getMostRecentlySelectedAccountId() {
        synchronized (CACHE_LOCK) {
            if (this.mostRecentlySelectedAccountId == null) {
                final Long id = AppDatabase.getInstance(this).accountDao().getMostRecentlySelectedAccountId();
                LOGGER.info("read most recently selected account id from database: {}", id);
                this.mostRecentlySelectedAccountId = id;
            }
            return mostRecentlySelectedAccountId;
        }
    }

    public void invalidateMostRecentlySelectedAccountId() {
        synchronized (CACHE_LOCK) {
            this.mostRecentlySelectedAccountId = null;
        }
    }

    public static LttrsApplication get(final Application application) {
        if (application instanceof LttrsApplication) {
            return (LttrsApplication) application;
        }
        throw new IllegalStateException("Application is not a " + LttrsApplication.class.getSimpleName());
    }

    public static LttrsApplication get(final Activity activity) {
        return get(activity.getApplication());
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        //jmap-mua methods regularly use ifInState parameters that require use to have an up to date
        //local cache. However at the same time this prevents us from launching two modifications at
        //the same time as the second call would fail with a state miss match.
        //Therefor all our email modifications (thus far the only thing happening in WorkManager)
        //are single threaded.
        LOGGER.info("Create single threaded WorkManager configuration");
        return new Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
    }
}
