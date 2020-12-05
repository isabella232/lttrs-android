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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.repository.LttrsRepository;
import rs.ltt.android.util.Event;
import rs.ltt.android.worker.Failure;
import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRole;
import rs.ltt.jmap.common.entity.Keyword;
import rs.ltt.jmap.mua.util.Label;
import rs.ltt.jmap.mua.util.LabelUtil;

public class LttrsViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsViewModel.class);

    private final LiveData<List<Label>> navigableLabels;
    private final MutableLiveData<Label> selectedLabel = new MutableLiveData<>();
    private final MutableLiveData<String> activityTitle = new MutableLiveData<>();
    private final MainRepository mainRepository;
    private final long accountId;
    private final LttrsRepository lttrsRepository;
    private String currentSearchTerm;

    public LttrsViewModel(@NonNull Application application, final long accountId) {
        super(application);
        LOGGER.debug("creating instance of LttrsViewModel");
        this.mainRepository = new MainRepository(application);
        this.accountId = accountId;
        this.lttrsRepository = new LttrsRepository(application, accountId);
        this.navigableLabels = Transformations.map(
                this.lttrsRepository.getMailboxes(),
                LabelUtil::fillUpAndSort
        );
    }

    public LiveData<Event<Failure>> getFailureEvent() {
        return this.lttrsRepository.getFailureEvent();
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

    public void setActivityTitle(final String title) {
        this.activityTitle.postValue(title);
    }

    public LiveData<String> getActivityTitle() {
        return this.activityTitle;
    }

    public void setSelectedLabel(final Label label) {
        this.selectedLabel.postValue(label);
    }

    public LiveData<Label> getSelectedLabel() {
        return this.selectedLabel;
    }

    public LiveData<List<Label>> getNavigableLabels() {
        return this.navigableLabels;
    }


    public void insertSearchSuggestion(String term) {
        this.mainRepository.insertSearchSuggestion(term);
    }

    public ListenableFuture<LiveData<WorkInfo>> moveToTrash(final Collection<String> threadIds) {
        return this.lttrsRepository.moveToTrash(threadIds);
    }

    public void cancelMoveToTrash(final WorkInfo workInfo, final Collection<String> threadIds) {
        this.lttrsRepository.cancelMoveToTrash(workInfo, threadIds);
    }

    public void archive(Collection<String> threadIds) {
        this.lttrsRepository.archive(threadIds);
    }

    public void moveToInbox(Collection<String> threadIds) {
        this.lttrsRepository.moveToInbox(threadIds);
    }

    public void removeFromMailbox(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        this.lttrsRepository.removeFromMailbox(threadIds, mailbox);
    }

    public void copyToMailbox(final Collection<String> threadIds, final IdentifiableMailboxWithRole mailbox) {
        this.lttrsRepository.copyToMailbox(threadIds, mailbox);
    }

    public void removeKeyword(final Collection<String> threadIds, final String keyword) {
        this.lttrsRepository.removeKeyword(threadIds, keyword);
    }

    public void addKeyword(final Collection<String> threadIds, final String keyword) {
        this.lttrsRepository.addKeyword(threadIds, keyword);
    }

    public void toggleFlagged(String threadId, boolean target) {
        this.lttrsRepository.toggleFlagged(ImmutableSet.of(threadId), target);
    }

    public void markRead(final Collection<String> threadIds) {
        this.lttrsRepository.markRead(threadIds);
    }

    public void markUnread(final Collection<String> threadIds) {
        this.lttrsRepository.markUnRead(threadIds);
    }

    public void markImportant(final Collection<String> threadIds) {
        this.lttrsRepository.markImportant(threadIds);
    }

    public void markNotImportant(final Collection<String> threadIds) {
        this.lttrsRepository.markNotImportant(threadIds);
    }

    public void addFlag(final Collection<String> threadIds) {
        this.addKeyword(threadIds, Keyword.FLAGGED);
    }

    public void removeFlag(final Collection<String> threadIds) {
        this.removeKeyword(threadIds, Keyword.FLAGGED);
    }

    public void observeForFailure(final UUID id) {
        this.lttrsRepository.observeForFailure(id);
    }
}
