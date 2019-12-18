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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.worker.SaveDraftWorker;
import rs.ltt.android.worker.SendEmailWorker;
import rs.ltt.jmap.common.entity.IdentifiableIdentity;

public class ComposeRepository extends LttrsRepository {

    public ComposeRepository(Application application, ListenableFuture<AccountWithCredentials> accountFuture) {
        super(application, accountFuture);
    }

    public LiveData<List<IdentityWithNameAndEmail>> getIdentities() {
        return Transformations.switchMap(this.databaseLiveData, database -> database.identityDao().getIdentitiesLiveData());
    }

    public void sendEmail(IdentifiableIdentity identity, String to, String subject, String body) {
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SendEmailWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(SendEmailWorker.data(requireAccount().id, identity.getId(), to, subject, body))
                .build();
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.enqueue(workRequest);
    }

    public void saveDraft(IdentifiableIdentity identity, String to, String subject, String body) {
        final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SaveDraftWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setInputData(SendEmailWorker.data(requireAccount().id, identity.getId(), to, subject, body))
                .build();
        final WorkManager workManager = WorkManager.getInstance(application);
        workManager.enqueue(workRequest);
    }
}
