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

package rs.ltt.android.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.Collection;
import java.util.Collections;

import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.EmailAddress;
import rs.ltt.jmap.common.entity.EmailBodyPart;
import rs.ltt.jmap.common.entity.EmailBodyValue;
import rs.ltt.jmap.mua.util.EmailAddressUtil;

public abstract class AbstractCreateEmailWorker extends AbstractMuaWorker {

    private static String IDENTITY_KEY = "identity";
    private static String TO_KEY = "to";
    private static String SUBJECT_KEY = "subject";
    private static String BODY_KEY = "body";

    private final String identity;
    private final Collection<EmailAddress> to;
    private final String subject;
    private final String body;


    AbstractCreateEmailWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.identity = data.getString(IDENTITY_KEY);
        final String to = data.getString(TO_KEY);
        this.to = to == null ? Collections.emptyList() : EmailAddressUtil.parse(to);
        this.subject = data.getString(SUBJECT_KEY);
        this.body = data.getString(BODY_KEY);
    }

    public static Data data(final Long account,
                            final String identity,
                            final String to,
                            final String subject,
                            final String body) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(IDENTITY_KEY, identity)
                .putString(TO_KEY, to)
                .putString(SUBJECT_KEY, subject)
                .putString(BODY_KEY, body)
                .build();
    }

    Email buildEmail(final IdentityWithNameAndEmail identity) {
        final EmailBodyValue emailBodyValue = EmailBodyValue.builder()
                .value(this.body)
                .build();
        final String partId = "0";
        final EmailBodyPart emailBodyPart = EmailBodyPart.builder()
                .partId(partId)
                .type("text/plain")
                .build();
        return Email.builder()
                .from(identity.getEmailAddress())
                .to(this.to)
                .subject(this.subject)
                .bodyValue(partId, emailBodyValue)
                .textBody(emailBodyPart)
                .build();
    }

    IdentityWithNameAndEmail getIdentity() {
        return getDatabase().identityDao().get(this.identity);
    }
}
