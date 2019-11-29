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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentSignInBinding;
import rs.ltt.android.ui.model.SetupViewModel;
import rs.ltt.android.util.MainThreadExecutor;

public class SignInFragment extends AbstractSetupFragment {

    private FragmentSignInBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_sign_in, container, false);
        binding.setSetupViewModel(setupViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.next.setOnClickListener(this::onNextButtonClicked);
        return binding.getRoot();
    }

    private void onNextButtonClicked(View view) {
        final ListenableFuture<SetupViewModel.Target> future = setupViewModel.enterEmailAddress();
        future.addListener(() -> {
            try {
                final SetupViewModel.Target target = future.get();
                if (target == SetupViewModel.Target.NOP) {
                    return;
                }
                final NavController navController = Navigation.findNavController(
                        requireActivity(),
                        R.id.nav_host_fragment
                );
                switch (target) {
                    case ENTER_PASSWORD:
                        navController.navigate(R.id.signIn_to_password);
                        break;
                    case ENTER_URL:
                        break;
                    default:
                        throw new IllegalStateException(
                                String.format("Navigating to %s not implemented", target.toString())
                        );
                }
            } catch (ExecutionException | InterruptedException e) {

            }
        }, MainThreadExecutor.getInstance());
    }

}
