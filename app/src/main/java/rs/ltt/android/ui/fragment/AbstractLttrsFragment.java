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

package rs.ltt.android.ui.fragment;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.util.concurrent.ListenableFuture;

import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.ui.model.LttrsViewModel;

public abstract class AbstractLttrsFragment extends Fragment {

    protected LttrsViewModel getLttrsViewModel() {
        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                requireActivity(),
                getDefaultViewModelProviderFactory()
        );
        return viewModelProvider.get(LttrsViewModel.class);
    }

    protected AccountWithCredentials requireAccount() {
        final LttrsViewModel lttrsViewModel = getLttrsViewModel();
        ListenableFuture<AccountWithCredentials> future = lttrsViewModel.getAccount();
        if (future.isDone()) {
            try {
                return future.get();
            } catch (Exception e) {
                throw new IllegalStateException("Account information not yet available");
            }
        } else {
            throw new IllegalStateException("Account information not yet available");
        }
    }
}
