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

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class ThreadOverviewItemDetailsLookup extends ItemDetailsLookup<String> {

    private final RecyclerView recyclerView;

    public ThreadOverviewItemDetailsLookup(final RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<String> getItemDetails(@NonNull MotionEvent e) {
        final View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
        final RecyclerView.ViewHolder viewHolder = view == null ? null : recyclerView.getChildViewHolder(view);
        if (viewHolder instanceof ThreadOverviewAdapter.ThreadOverviewViewHolder) {
            return ((ThreadOverviewAdapter.ThreadOverviewViewHolder) viewHolder).getItemDetails();
        }
        return null;
    }
}
