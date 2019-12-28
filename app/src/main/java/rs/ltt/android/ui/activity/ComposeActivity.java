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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import rs.ltt.android.R;
import rs.ltt.android.databinding.ActivityComposeBinding;
import rs.ltt.android.ui.ChipDrawableSpan;
import rs.ltt.android.ui.ComposeAction;
import rs.ltt.android.ui.model.ComposeViewModel;
import rs.ltt.android.ui.model.ComposeViewModelFactory;

//TODO handle save instance state
public class ComposeActivity extends AppCompatActivity {

    public static final int REQUEST_EDIT_DRAFT = 0x100;
    public static final String EDITING_TASK_ID_EXTRA = "work_request_id";
    public static final String DISCARDED_THREAD_EXTRA = "discarded_thread";
    private static final Logger LOGGER = LoggerFactory.getLogger(ComposeActivity.class);
    private static final String ACCOUNT_EXTRA = "account";
    private static final String COMPOSE_ACTION_EXTRA = "compose_action";
    private static final String EMAIL_ID_EXTRA = "email_id";
    private ActivityComposeBinding binding;
    private ComposeViewModel composeViewModel;

    public static void editDraft(final Fragment fragment, Long account, final String emailId) {
        launch(fragment, account, emailId, ComposeAction.EDIT_DRAFT);
    }

    public static void replyAll(final Fragment fragment, Long account, final String emailId) {
        launch(fragment, account, emailId, ComposeAction.REPLY_ALL);
    }

    private static void launch(final Fragment fragment,
                               final Long account,
                               final String emailId,
                               final ComposeAction action) {
        final Intent intent = new Intent(fragment.getContext(), ComposeActivity.class);
        intent.putExtra(ACCOUNT_EXTRA, account);
        intent.putExtra(COMPOSE_ACTION_EXTRA, action.toString());
        intent.putExtra(EMAIL_ID_EXTRA, emailId);
        fragment.startActivityForResult(intent, REQUEST_EDIT_DRAFT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_compose);
        setSupportActionBar(binding.toolbar);

        final ActionBar actionbar = requireActionBar();
        actionbar.setHomeButtonEnabled(true);
        actionbar.setDisplayHomeAsUpEnabled(true);

        final Intent i = getIntent();
        final Long account;
        if (i != null && i.hasExtra(ACCOUNT_EXTRA)) {
            account = i.getLongExtra(ACCOUNT_EXTRA, 0L);
        } else {
            account = null;
        }
        final ComposeAction action = ComposeAction.of(i == null ? null : i.getStringExtra(COMPOSE_ACTION_EXTRA));
        final String emailId = i == null ? null : i.getStringExtra(EMAIL_ID_EXTRA);
        final boolean freshStart = savedInstanceState == null || savedInstanceState.isEmpty();

        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                this,
                new ComposeViewModelFactory(
                        getApplication(),
                        account,
                        freshStart,
                        action,
                        emailId
                )
        );
        composeViewModel = viewModelProvider.get(ComposeViewModel.class);

        composeViewModel.getErrorMessage().observe(this, event -> {
            if (event.isConsumable()) {
                final String message = event.consume();
                new MaterialAlertDialogBuilder(this)
                        .setTitle(message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });

        binding.setComposeViewModel(composeViewModel);
        binding.setLifecycleOwner(this);

        binding.to.addTextChangedListener(new ChipTextWatcher(binding.to));
        binding.to.setOnFocusChangeListener(
                (v, hasFocus) -> ChipDrawableSpan.apply(this, binding.to.getEditableText(), hasFocus)
        );

        binding.cc.addTextChangedListener(new ChipTextWatcher(binding.cc));
        binding.cc.setOnFocusChangeListener(
                (v, hasFocus) -> ChipDrawableSpan.apply(this, binding.cc.getEditableText(), hasFocus)
        );

        binding.moreAddresses.setOnClickListener((v -> composeViewModel.showExtendedAddresses()));

        binding.subject.setOnFocusChangeListener(this::focusOnBodyOrSubject);
        binding.body.setOnFocusChangeListener(this::focusOnBodyOrSubject);

        binding.toLabel.setOnClickListener(v -> requestFocusAndOpenKeyboard(binding.to));
        binding.placeholder.setOnClickListener(v -> requestFocusAndOpenKeyboard(binding.body));

        //TODO once we handle instance state ourselves we need to call ChipDrawableSpan.reset() on `to`
    }

    private void focusOnBodyOrSubject(final View view, final boolean hasFocus) {
        if (hasFocus) {
            composeViewModel.suggestHideExtendedAddresses();
        }
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
            case R.id.action_send:
                if (composeViewModel.send()) {
                    finish();
                }
                break;
            case R.id.action_discard:
                discardDraft();
                break;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onBackPressed() {
        LOGGER.info("onBackPressed()");
        final UUID uuid = composeViewModel.saveDraft();
        if (uuid != null) {
            final Intent intent = new Intent();
            intent.putExtra(EDITING_TASK_ID_EXTRA, uuid);
            setResult(RESULT_OK, intent);
        }
        super.onBackPressed();
    }

    private void discardDraft() {
        final boolean isOnlyEmailInThread = composeViewModel.discard();
        Intent intent = new Intent();
        intent.putExtra(DISCARDED_THREAD_EXTRA, isOnlyEmailInThread);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onDestroy() {
        if (isFinishing()) {
            composeViewModel.saveDraft();
        }
        super.onDestroy();
    }

    private @NonNull
    ActionBar requireActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new IllegalStateException("No ActionBar found");
        }
        return actionBar;
    }

    private static class ChipTextWatcher implements TextWatcher {

        private final EditText editText;

        private ChipTextWatcher(EditText editText) {
            this.editText = editText;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            ChipDrawableSpan.apply(editText.getContext(), editable, editText.hasFocus());
        }
    }
}
