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

package rs.ltt.android.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;

import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.ExpandedPosition;
import rs.ltt.android.entity.FullEmail;
import rs.ltt.android.entity.KeywordOverwriteEntity;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.ThreadHeader;

public class ThreadRepository extends LttrsRepository {

    public ThreadRepository(Application application, ListenableFuture<AccountWithCredentials> account) {
        super(application, account);
    }

    public LiveData<PagedList<FullEmail>> getEmails(String threadId) {
        return Transformations.switchMap(databaseLiveData, database -> new LivePagedListBuilder<>(database.threadAndEmailDao().getEmails(threadId), 30).build());
    }

    public LiveData<ThreadHeader> getThreadHeader(String threadId) {
        return Transformations.switchMap(databaseLiveData, database -> database.threadAndEmailDao().getThreadHeader(threadId));
    }

    public LiveData<List<MailboxWithRoleAndName>> getMailboxes(String threadId) {
        return Transformations.switchMap(databaseLiveData, database ->database.mailboxDao().getMailboxesForThreadLiveData(threadId));
    }


    public LiveData<List<MailboxOverwriteEntity>> getMailboxOverwrites(String threadId) {
        return Transformations.switchMap(databaseLiveData, database ->database.overwriteDao().getMailboxOverwrites(threadId));
    }

    public ListenableFuture<List<ExpandedPosition>> getExpandedPositions(String threadId) {
        ListenableFuture<KeywordOverwriteEntity> overwriteFuture = Futures.transformAsync(this.database, database -> database.overwriteDao().getKeywordOverwrite(threadId), MoreExecutors.directExecutor());
        return Futures.transformAsync(overwriteFuture, input -> {
            if (input != null) {
                if (input.value) {
                    return requireDatabase().threadAndEmailDao().getMaxPosition(threadId);
                } else {
                    return requireDatabase().threadAndEmailDao().getAllPositions(threadId);
                }
            } else {
                ListenableFuture<List<ExpandedPosition>> unseen = requireDatabase().threadAndEmailDao().getUnseenPositions(threadId);
                return Futures.transformAsync(unseen, input1 -> {
                    if (input1 == null || input1.size() == 0) {
                        return requireDatabase().threadAndEmailDao().getMaxPosition(threadId);
                    } else {
                        return Futures.immediateFuture(input1);
                    }
                }, MoreExecutors.directExecutor());
            }
        }, MoreExecutors.directExecutor());
    }
}
