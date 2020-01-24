/*
 * Copyright 2020 Daniel Gultsch
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

package rs.ltt.android.ui.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagedList;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import rs.ltt.android.entity.ThreadOverviewItem;

public class ThreadOverviewItemKeyProvider extends ItemKeyProvider<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadOverviewItemKeyProvider.class);

    private final ThreadOverviewAdapter threadOverviewAdapter;

    public ThreadOverviewItemKeyProvider(ThreadOverviewAdapter adapter) {
        super(ItemKeyProvider.SCOPE_MAPPED);
        this.threadOverviewAdapter = adapter;
    }

    @Nullable
    @Override
    public String getKey(final int position) {
        LOGGER.info("attempting to get key for position {}", position);
        final ThreadOverviewItem item = threadOverviewAdapter.getItem(position);
        if (item != null) {
            LOGGER.info("email id "+item.emailId);
        }
        return item == null ? null : item.emailId;
    }

    @Override
    public int getPosition(@NonNull String key) {
        final PagedList<ThreadOverviewItem> currentList = threadOverviewAdapter.getCurrentList();
        if (currentList == null) {
            return RecyclerView.NO_POSITION;
        }
        final int offset = currentList.getPositionOffset();
        final List<ThreadOverviewItem> snapshot = currentList.snapshot();
        int i = 0;
        for(final ThreadOverviewItem item : snapshot) {
            if (item != null && key.equals(item.emailId)) {
                return offset + i;
            }
            ++i;
        }
        return RecyclerView.NO_POSITION;
    }
}
