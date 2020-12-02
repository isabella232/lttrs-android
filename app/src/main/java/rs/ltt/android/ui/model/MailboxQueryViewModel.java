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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.query.EmailQuery;

public class MailboxQueryViewModel extends AbstractQueryViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxQueryViewModel.class);


    private final LiveData<MailboxOverviewItem> mailbox;

    private final LiveData<EmailQuery> emailQueryLiveData;

    MailboxQueryViewModel(final Application application, final long accountId, final String mailboxId) {
        super(application, accountId);
        this.mailbox = this.queryRepository.getMailboxOverviewItem(mailboxId);
        this.emailQueryLiveData = Transformations.map(mailbox, input -> {
            if (input == null) {
                return EmailQuery.unfiltered(true);
            } else {
                return EmailQuery.of(EmailFilterCondition.builder().inMailbox(input.id).build(), true);
            }
        });
        init();
    }

    public void removeFromMailbox(ThreadOverviewItem item) {
        final MailboxOverviewItem mailbox = this.mailbox.getValue();
        if (mailbox == null) {
            throw new IllegalStateException("No mailbox associated with MailboxQueryViewModel");
        }
        LOGGER.debug("remove {} from {}", item.emailId, mailbox.name);

        queryRepository.removeFromMailbox(ImmutableSet.of(item.threadId), mailbox);
    }

    public void copyToMailbox(ThreadOverviewItem item) {
        final IdentifiableMailboxWithRole mailbox = this.mailbox.getValue();
        if (mailbox == null) {
            throw new IllegalStateException("No mailbox associated with MailboxQueryViewModel");
        }
        queryRepository.copyToMailbox(ImmutableSet.of(item.threadId), mailbox);
    }

    public LiveData<MailboxOverviewItem> getMailbox() {
        return mailbox;
    }

    @Override
    protected LiveData<EmailQuery> getQuery() {
        return emailQueryLiveData;
    }
}
