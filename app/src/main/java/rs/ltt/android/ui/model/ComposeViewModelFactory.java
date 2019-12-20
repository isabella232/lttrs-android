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
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import rs.ltt.android.ui.ComposeAction;

public class ComposeViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final Long account;
    private final boolean freshStart;
    private final ComposeAction action;
    private final String emailId;

    public ComposeViewModelFactory(@NonNull Application application,
                                   @Nullable Long account,
                                   boolean freshStart,
                                   @NonNull ComposeAction action,
                                   @Nullable String emailId) {
        this.application = application;
        this.account = account;
        this.freshStart = freshStart;
        this.action = action;
        this.emailId = emailId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return modelClass.cast(new ComposeViewModel(
                application,
                account,
                freshStart,
                action,
                emailId
        ));
    }
}
