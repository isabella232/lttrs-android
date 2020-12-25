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
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import rs.ltt.android.R;
import rs.ltt.android.SetupNavigationDirections;
import rs.ltt.android.databinding.ActivitySetupBinding;
import rs.ltt.android.ui.model.SetupViewModel;
import rs.ltt.jmap.mua.util.MailToUri;

public class SetupActivity extends AppCompatActivity {

    public static String EXTRA_NEXT_ACTION = "rs.ltt.android.extras.next-action";

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
                        final Long[] ids = setupViewModel.getRecentlyAddedIds();
                        if (ids == null || ids.length < 1) {
                            throw new IllegalStateException("No recently added account ids for redirection event");
                        }
                        redirectToLttrs(ids[0]);
                        break;
                    case ENTER_PASSWORD:
                        navController.navigate(SetupNavigationDirections.enterPassword());
                        break;
                    case ENTER_URL:
                        navController.navigate(SetupNavigationDirections.enterSessionResource());
                        break;
                    default:
                        throw new IllegalStateException(String.format("Unable to navigate to target %s", target));

                }
            }
        });
        setupViewModel.getWarningMessage().observe(this, event -> {
            if (event.isConsumable()) {
                //TODO: Should we make this a dialog? ComposeActivity uses dialogs too
                Snackbar.make(binding.getRoot(), event.consume(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void redirectToLttrs(final Long accountId) {
        final Intent currentIntent = getIntent();
        final String uri = currentIntent == null ? null : currentIntent.getStringExtra(EXTRA_NEXT_ACTION);
        final MailToUri mailToUri = uri == null ? null : MailToUri.parse(uri);
        final Intent nextIntent;
        if (mailToUri != null) {
            nextIntent = new Intent(this, ComposeActivity.class);
            nextIntent.setAction(Intent.ACTION_VIEW);
            nextIntent.setData(Uri.parse(uri));
        } else {
            nextIntent = new Intent(this, LttrsActivity.class);
            nextIntent.putExtra(LttrsActivity.EXTRA_ACCOUNT_ID, accountId);
        }
        startActivity(nextIntent);
        finish();
    }
}
