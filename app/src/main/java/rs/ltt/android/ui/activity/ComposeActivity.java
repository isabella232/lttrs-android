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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import rs.ltt.android.R;
import rs.ltt.android.databinding.ActivityComposeBinding;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.ui.ChipDrawableSpan;
import rs.ltt.android.ui.model.ComposeViewModel;
import rs.ltt.android.ui.model.LttrsViewModel;

public class ComposeActivity extends AppCompatActivity {

    private ActivityComposeBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_compose);
        setSupportActionBar(binding.toolbar);

        final ActionBar actionbar = requireActionBar();
        actionbar.setHomeButtonEnabled(true);
        actionbar.setDisplayHomeAsUpEnabled(true);

        final ViewModelProvider viewModelProvider = new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        final ComposeViewModel viewModel = viewModelProvider.get(ComposeViewModel.class);

        binding.to.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                ChipDrawableSpan.apply(ComposeActivity.this, editable, binding.to.hasFocus());
            }
        });
        binding.to.setOnFocusChangeListener(
                (v, hasFocus) -> ChipDrawableSpan.apply(this, binding.to.getEditableText(), hasFocus)
        );

        binding.toLabel.setOnClickListener(v -> requestFocusAndOpenKeyboard(binding.to));
        binding.placeholder.setOnClickListener(v -> requestFocusAndOpenKeyboard(binding.body));

        final ArrayAdapter<String> fromAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        fromAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);


        viewModel.getIdentities().observe(this, identityWithNameAndEmails -> {
            fromAdapter.clear();
            for (IdentityWithNameAndEmail identity : identityWithNameAndEmails) {
                fromAdapter.add(identity.email);
            }
            fromAdapter.notifyDataSetChanged();
        });
        binding.from.setAdapter(fromAdapter);
    }

    private void requestFocusAndOpenKeyboard(AppCompatEditText editText) {
        editText.requestFocus();
        final InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_compose, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);

    }

    private @NonNull
    ActionBar requireActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new IllegalStateException("No ActionBar found");
        }
        return actionBar;
    }
}
