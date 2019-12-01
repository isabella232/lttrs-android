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

package rs.ltt.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import rs.ltt.android.R;
import rs.ltt.android.SetupNavigationDirections;
import rs.ltt.android.databinding.ActivitySetupBinding;
import rs.ltt.android.ui.model.SetupViewModel;

public class SetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySetupBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_setup);
        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );
        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                this,
                getDefaultViewModelProviderFactory()
        );
        final SetupViewModel setupViewModel = viewModelProvider.get(SetupViewModel.class);
        setupViewModel.getRedirection().observe(this, targetEvent -> {
            if (targetEvent.isConsumable()) {
                final SetupViewModel.Target target = targetEvent.consume();
                switch (target) {
                    case LTTRS:
                        startActivity(new Intent(SetupActivity.this, LttrsActivity.class));
                        finish();
                        break;
                    case ENTER_PASSWORD:
                        navController.navigate(SetupNavigationDirections.enterPassword());
                        break;
                    case ENTER_URL:
                        navController.navigate(SetupNavigationDirections.enterConnectionUrl());
                        break;
                    default:
                        throw new IllegalStateException(String.format("Unable to navigate to target %s", target));

                }
            }
        });
    }
}
