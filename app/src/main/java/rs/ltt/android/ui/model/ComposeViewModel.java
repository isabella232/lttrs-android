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
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import rs.ltt.android.R;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.EditableEmail;
import rs.ltt.android.entity.EmailAddressType;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.repository.ComposeRepository;
import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.ui.ComposeAction;
import rs.ltt.android.util.Event;
import rs.ltt.jmap.common.entity.EmailAddress;
import rs.ltt.jmap.mua.util.EmailAddressUtil;
import rs.ltt.jmap.mua.util.EmailUtil;

public class ComposeViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComposeViewModel.class);

    private final ComposeRepository repository;
    private final ComposeAction composeAction;
    private final ListenableFuture<EditableEmail> email;

    private final MutableLiveData<Event<String>> errorMessage = new MutableLiveData<>();

    private final MutableLiveData<Integer> selectedIdentityPosition = new MutableLiveData<>();
    private final MutableLiveData<Boolean> extendedAddresses = new MutableLiveData<>();
    private final MutableLiveData<String> to = new MutableLiveData<>();
    private final MutableLiveData<String> cc = new MutableLiveData<>();
    private final MutableLiveData<String> subject = new MutableLiveData<>();
    private final MutableLiveData<String> body = new MutableLiveData<>();
    private final LiveData<List<IdentityWithNameAndEmail>> identities;

    private boolean draftHasBeenHandled = false;

    ComposeViewModel(@NonNull final Application application,
                     final Long id,
                     final boolean freshStart,
                     final ComposeAction composeAction,
                     final String emailId) {
        super(application);
        this.composeAction = composeAction;
        final MainRepository mainRepository = new MainRepository(application);
        final ListenableFuture<AccountWithCredentials> account = mainRepository.getAccount(id);
        this.repository = new ComposeRepository(application, account);
        this.identities = this.repository.getIdentities();
        if (composeAction == ComposeAction.NEW) {
            this.email = null;
        } else {
            this.email = this.repository.getEditableEmail(emailId);
        }
        if (freshStart && this.email != null) {
            initializeWithEmail();
        }
    }

    public LiveData<Event<String>> getErrorMessage() {
        return this.errorMessage;
    }

    public MutableLiveData<String> getTo() {
        return this.to;
    }

    public MutableLiveData<String> getCc() {
        return this.cc;
    }

    public LiveData<Boolean> getExtendedAddresses() {
        return this.extendedAddresses;
    }

    public MutableLiveData<String> getBody() {
        return this.body;
    }

    public MutableLiveData<String> getSubject() {
        return this.subject;
    }

    public LiveData<List<IdentityWithNameAndEmail>> getIdentities() {
        return this.identities;
    }

    public MutableLiveData<Integer> getSelectedIdentityPosition() {
        return this.selectedIdentityPosition;
    }

    public void showExtendedAddresses() {
        this.extendedAddresses.postValue(true);
    }

    public void suggestHideExtendedAddresses() {
        final String cc = Strings.nullToEmpty(this.cc.getValue());
        if (cc.isEmpty()) {
            this.extendedAddresses.postValue(false);
        }
    }

    public boolean discard() {
        final EditableEmail email = getEmail();
        final boolean isOnlyEmailInThread = email == null || repository.discard(email);
        this.draftHasBeenHandled = true;
        return isOnlyEmailInThread;
    }

    public boolean send() {
        final IdentityWithNameAndEmail identity = getIdentity();
        if (identity == null) {
            postErrorMessage(R.string.select_sender);
            return false;
        }
        final Draft currentDraft = getCurrentDraft();
        if (currentDraft.to.size() <= 0) {
            postErrorMessage(R.string.add_at_least_one_recipient);
            return false;
        }
        for (EmailAddress emailAddress : currentDraft.to) {
            if (EmailAddressUtil.isValid(emailAddress)) {
                continue;
            }
            postErrorMessage(R.string.the_address_x_is_invalid, emailAddress.getEmail());
            return false;
        }
        LOGGER.info("sending with identity {}", identity.getId());
        final EditableEmail editableEmail = getEmail();
        if (this.composeAction == ComposeAction.EDIT_DRAFT
                && editableEmail != null
                && currentDraft.unedited(Draft.edit(editableEmail))) {
            LOGGER.info("draft remains unedited. submitting...");
            this.repository.submitEmail(identity, editableEmail);
        } else {
            final Collection<String> inReplyTo = inReplyTo(editableEmail, composeAction);
            this.repository.sendEmail(identity, currentDraft, inReplyTo);
        }
        this.draftHasBeenHandled = true;
        return true;
    }

    private static Collection<String> inReplyTo(@Nullable EditableEmail editableEmail, ComposeAction action) {
        if (editableEmail == null) {
            return Collections.emptyList();
        }
        if (action == ComposeAction.EDIT_DRAFT) {
            return editableEmail.inReplyTo;
        }
        if (action == ComposeAction.REPLY_ALL) {
            return editableEmail.messageId;
        }
        return Collections.emptyList();
    }

    public UUID saveDraft() {
        if (this.draftHasBeenHandled) {
            LOGGER.info("Not storing as draft. Email has already been handled.");
            return null;
        }
        final IdentityWithNameAndEmail identity = getIdentity();
        if (identity == null) {
            LOGGER.info("Not storing draft. No identity has been selected");
            return null;
        }
        final Draft currentDraft = getCurrentDraft();
        if (currentDraft.isEmpty()) {
            LOGGER.info("not storing draft. To, subject and body are empty.");
            return null;
        }
        final EditableEmail editableEmail = getEmail();
        final Draft originalDraft = Draft.with(this.composeAction, editableEmail);
        if (originalDraft != null && currentDraft.unedited(originalDraft)) {
            LOGGER.info("Not storing draft. Nothing has been changed");
            draftHasBeenHandled = true;
            return null;
        }
        LOGGER.info("Saving draft");
        final EditableEmail discard;
        if (this.composeAction == ComposeAction.EDIT_DRAFT) {
            discard = editableEmail;
            LOGGER.info("Requesting to delete previous draft={}", discard == null ? null : discard.id);
        } else {
            discard = null;
        }
        final Collection<String> inReplyTo = inReplyTo(editableEmail, composeAction);
        final UUID uuid = this.repository.saveDraft(identity, currentDraft, inReplyTo, discard);
        this.draftHasBeenHandled = true;
        return uuid;
    }

    private IdentityWithNameAndEmail getIdentity() {
        final List<IdentityWithNameAndEmail> identities = this.identities.getValue();
        final Integer selectedIdentity = this.selectedIdentityPosition.getValue();
        if (identities != null && selectedIdentity != null && selectedIdentity < identities.size()) {
            return identities.get(selectedIdentity);
        }
        return null;
    }

    private void postErrorMessage(@StringRes final int res, final Object... objects) {
        this.errorMessage.postValue(
                new Event<>(getApplication().getString(res, objects))
        );
    }

    private EditableEmail getEmail() {
        if (this.email != null && this.email.isDone()) {
            try {
                return this.email.get();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void initializeWithEmail() {
        Futures.addCallback(this.email, new FutureCallback<EditableEmail>() {
            @Override
            public void onSuccess(@NullableDecl EditableEmail result) {
                initializeWithEmail(result);
            }

            @Override
            public void onFailure(Throwable t) {

            }
        }, MoreExecutors.directExecutor());
    }

    private void initializeWithEmail(final EditableEmail email) {
        final Draft draft = Draft.with(composeAction, email);
        to.postValue(EmailAddressUtil.toHeaderValue(draft.to));
        cc.postValue(EmailAddressUtil.toHeaderValue(draft.cc));
        if (draft.cc.size() > 0) {
            extendedAddresses.postValue(true);
        }
        subject.postValue(draft.subject);
        body.postValue(draft.body);
    }

    private Draft getCurrentDraft() {
        return Draft.of(this.to, this.cc, this.subject, this.body);
    }

    public static class Draft {
        private final Collection<EmailAddress> to;
        private final Collection<EmailAddress> cc;
        private final String subject;
        private final String body;

        private Draft(Collection<EmailAddress> to, Collection<EmailAddress> cc, String subject, String body) {
            this.to = to;
            this.cc = cc;
            this.subject = subject;
            this.body = body;
        }

        public static Draft of(LiveData<String> to, LiveData<String> cc, LiveData<String> subject, LiveData<String> body) {
            return new Draft(
                    EmailAddressUtil.parse(Strings.nullToEmpty(to.getValue())),
                    EmailAddressUtil.parse(Strings.nullToEmpty(cc.getValue())),
                    Strings.nullToEmpty(subject.getValue()),
                    Strings.nullToEmpty(body.getValue())
            );
        }

        private static Draft edit(EditableEmail email) {
            return new Draft(
                    email.getTo(),
                    email.getCc(),
                    email.subject,
                    email.getText()
            );
        }

        private static Draft replyAll(EditableEmail email) {
            EmailUtil.ReplyAddresses replyAddresses = EmailUtil.replyAll(email);
            return new Draft(
                    replyAddresses.getTo(),
                    replyAddresses.getCc(),
                    EmailUtil.getResponseSubject(email),
                    ""
            );
        }

        public static Draft with(final ComposeAction action, EditableEmail editableEmail) {
            switch (action) {
                case NEW:
                    return null;
                case EDIT_DRAFT:
                    return edit(editableEmail);
                case REPLY_ALL:
                    return replyAll(editableEmail);
                default:
                    throw new IllegalStateException(String.format("Unknown action %s", action));
            }
        }

        public boolean isEmpty() {
            return to.isEmpty() && subject.trim().isEmpty() && body.trim().isEmpty();
        }

        public Collection<EmailAddress> getTo() {
            return to;
        }

        public Collection<EmailAddress> getCc() {
            return cc;
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }

        public boolean unedited(final Draft draft) {
            return draft != null
                    && EmailAddressUtil.equalCollections(draft.getTo(), to)
                    && subject.equals(draft.subject)
                    && body.equals(draft.body);
        }
    }
}
