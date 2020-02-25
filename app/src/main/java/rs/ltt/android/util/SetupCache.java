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

package rs.ltt.android.util;

import android.app.Activity;

import rs.ltt.android.database.AppDatabase;

public class SetupCache {

    private static Boolean hasAccounts = null;

    public static synchronized boolean hasAccounts(final Activity activity) {
        if (hasAccounts == null) {
            hasAccounts = AppDatabase.getInstance(activity.getApplicationContext()).accountDao().hasAccounts();
        }
        return hasAccounts;
    }

    public static synchronized void invalidate() {
        hasAccounts = null;
    }

}
