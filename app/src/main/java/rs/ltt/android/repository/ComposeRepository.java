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
import androidx.lifecycle.Transformations;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.IdentityWithNameAndEmail;

public class ComposeRepository extends LttrsRepository {

    public ComposeRepository(Application application, ListenableFuture<AccountWithCredentials> accountFuture) {
        super(application, accountFuture);
    }

    public LiveData<List<IdentityWithNameAndEmail>> getIdentities() {
        return Transformations.switchMap(this.databaseLiveData, database -> database.identityDao().getIdentitiesLiveData());
    }
}
