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
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import rs.ltt.android.R;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.android.repository.ComposeRepository;
import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.util.Event;
import rs.ltt.jmap.common.entity.EmailAddress;
import rs.ltt.jmap.mua.util.EmailAddressUtil;

public class ComposeViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComposeViewModel.class);

    private final ComposeRepository repository;
    private final MainRepository mainRepository;

    private final MutableLiveData<Event<String>> errorMessage = new MutableLiveData<>();

    private final MutableLiveData<Integer> selectedIdentityPosition = new MutableLiveData<>();
    private final MutableLiveData<String> to = new MutableLiveData<>();
    private final MutableLiveData<String> subject = new MutableLiveData<>();
    private final MutableLiveData<String> body = new MutableLiveData<>();
    private final LiveData<List<IdentityWithNameAndEmail>> identities;

    private boolean emailHasBeenStored = false;

    public ComposeViewModel(@NonNull Application application) {
        super(application);
        this.mainRepository = new MainRepository(application);
        ListenableFuture<AccountWithCredentials> account = this.mainRepository.getAccount(null);
        this.repository = new ComposeRepository(application, account);
        this.identities = this.repository.getIdentities();
    }

    public LiveData<Event<String>> getErrorMessage() {
        return this.errorMessage;
    }

    public MutableLiveData<String> getTo() {
        return this.to;
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

    public boolean send() {
        final IdentityWithNameAndEmail identity = getIdentity();
        if (identity == null) {
            postErrorMessage(R.string.select_sender);
            return false;
        }
        final Collection<EmailAddress> toEmailAddresses = EmailAddressUtil.parse(
                Strings.nullToEmpty(to.getValue())
        );
        if (toEmailAddresses.size() <= 0) {
            postErrorMessage(R.string.add_at_least_one_recipient);
            return false;
        }
        for (EmailAddress emailAddress : toEmailAddresses) {
            if (EmailAddressUtil.isValid(emailAddress)) {
                continue;
            }
            postErrorMessage(R.string.the_address_x_is_invalid, emailAddress.getEmail());
            return false;
        }
        LOGGER.info("sending with identity {}", identity.getId());
        this.repository.sendEmail(identity, to.getValue(), subject.getValue(), body.getValue());
        this.emailHasBeenStored = true;
        return true;
    }

    public void saveDraft() {
        if (this.emailHasBeenStored) {
            LOGGER.info("Not storing as draft. Email has already been stored.");
        }
        final IdentityWithNameAndEmail identity = getIdentity();
        if (identity == null) {
            LOGGER.info("Not storing draft. No identity has been selected");
            return;
        }
        final String to = Strings.nullToEmpty(this.to.getValue());
        final String subject = Strings.nullToEmpty(this.subject.getValue());
        final String body = Strings.nullToEmpty(this.body.getValue());
        if (to.trim().isEmpty() && subject.trim().isEmpty() && body.trim().isEmpty()) {
            LOGGER.info("not storing draft. To, subject and body are empty.");
            return;
        }
        LOGGER.info("Saving draft");
        this.repository.saveDraft(identity, to, subject, body);
        this.emailHasBeenStored = false;
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
}
