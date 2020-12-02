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
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.repository.ThreadRepository;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.mua.util.Label;
import rs.ltt.jmap.mua.util.LabelUtil;

public class LttrsViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsViewModel.class);

    private final LiveData<List<Label>> navigatableLabels;
    private final MainRepository mainRepository;
    private final long accountId;
    private final ThreadRepository threadRepository;
    private String currentSearchTerm;

    public LttrsViewModel(@NonNull Application application, final long accountId) {
        super(application);
        LOGGER.debug("creating instance of LttrsViewModel");
        this.mainRepository = new MainRepository(application);
        this.accountId = accountId;
        this.threadRepository = new ThreadRepository(application, accountId);
        this.navigatableLabels = Transformations.map(
                this.threadRepository.getMailboxes(),
                LabelUtil::fillUpAndSort
        );
    }

    public long getAccountId() {
        return this.accountId;
    }

    public String getCurrentSearchTerm() {
        return currentSearchTerm;
    }

    public void setCurrentSearchTerm(String currentSearchTerm) {
        this.currentSearchTerm = currentSearchTerm;
    }

    public LiveData<List<Label>> getNavigatableLabels() {
        return this.navigatableLabels;
    }


    public void insertSearchSuggestion(String term) {
        this.mainRepository.insertSearchSuggestion(term);
    }

    public ListenableFuture<LiveData<WorkInfo>> moveToTrash(final Collection<String> threadIds) {
        return this.threadRepository.moveToTrash(threadIds);
    }

    public void cancelMoveToTrash(final WorkInfo workInfo, final Collection<String> threadIds) {
        this.threadRepository.cancelMoveToTrash(workInfo, threadIds);
    }

    public void archive(Collection<String> threadIds) {
        this.threadRepository.archive(threadIds);
    }

    public void moveToInbox(Collection<String> threadIds) {
        this.threadRepository.moveToInbox(threadIds);
    }

    public void removeFromMailbox(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        this.threadRepository.removeFromMailbox(threadIds, mailbox);
    }

    public void copyToMailbox(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        this.threadRepository.copyToMailbox(threadIds, mailbox);
    }

    public void removeKeyword(final Collection<String> threadIds, final String keyword) {
        this.threadRepository.removeKeyword(threadIds, keyword);
    }

    public void addKeyword(final Collection<String> threadIds, final String keyword) {
        this.threadRepository.addKeyword(threadIds, keyword);
    }

}
