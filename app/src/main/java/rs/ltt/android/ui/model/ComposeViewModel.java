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

package rs.ltt.android.ui.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.repository.ComposeRepository;
import rs.ltt.android.repository.MainRepository;

public class ComposeViewModel extends AndroidViewModel {

    private final ComposeRepository repository;
    private final MainRepository mainRepository;

    private LiveData<List<IdentityWithNameAndEmail>> identities;

    public ComposeViewModel(@NonNull Application application) {
        super(application);
        this.mainRepository = new MainRepository(application);
        ListenableFuture<AccountWithCredentials> account = this.mainRepository.getAccount(null);
        this.repository = new ComposeRepository(application, account);
        this.identities = this.repository.getIdentities();
    }

    public LiveData<List<IdentityWithNameAndEmail>> getIdentities() {
        return this.identities;
    }
}
