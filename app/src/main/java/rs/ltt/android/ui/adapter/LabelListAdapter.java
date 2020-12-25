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

package rs.ltt.android.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import rs.ltt.android.R;
import rs.ltt.android.databinding.ItemMailboxBinding;
import rs.ltt.android.databinding.ItemMailboxHeaderBinding;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.jmap.mua.util.KeywordLabel;
import rs.ltt.jmap.mua.util.Label;
import rs.ltt.jmap.mua.util.LabelWithCount;

public class LabelListAdapter extends RecyclerView.Adapter<LabelListAdapter.AbstractMailboxViewHolder> {

    private static final int ITEM_VIEW_TYPE = 1;
    private static final int HEADER_VIEW_TYPE = 2;

    private static final DiffUtil.ItemCallback<LabelWithCount> ITEM_CALLBACK = new DiffUtil.ItemCallback<LabelWithCount>() {
        @Override
        public boolean areItemsTheSame(@NonNull LabelWithCount oldItem, @NonNull LabelWithCount newItem) {
            return LabelListAdapter.same(oldItem, newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull LabelWithCount oldItem, @NonNull LabelWithCount newItem) {
            return oldItem.equals(newItem);
        }
    };

    private final AsyncListDiffer<LabelWithCount> mDiffer = new AsyncListDiffer<>(new OffsetListUpdateCallback<>(this, 1), new AsyncDifferConfig.Builder<>(ITEM_CALLBACK).build());

    private LabelWithCount selectedLabel = null;

    private OnLabelSelected onLabelSelected = null;

    public LabelListAdapter() {
        super();
    }


    @NonNull
    @Override
    public AbstractMailboxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ITEM_VIEW_TYPE) {
            ItemMailboxBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.item_mailbox, parent, false);
            return new MailboxViewHolder(binding);
        } else {
            ItemMailboxHeaderBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.item_mailbox_header, parent, false);
            return new MailboxHeaderViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull AbstractMailboxViewHolder abstractHolder, final int position) {
        if (abstractHolder instanceof MailboxHeaderViewHolder) {
            return;
        }
        MailboxViewHolder holder = (MailboxViewHolder) abstractHolder;
        final Context context = holder.binding.getRoot().getContext();
        final LabelWithCount label = getItem(position);
        holder.binding.setLabel(label);
        holder.binding.item.setOnClickListener(v -> {
            if (onLabelSelected != null) {
                onLabelSelected.onLabelSelected(label, same(label, this.selectedLabel));
            }
        });
        if (same(label, this.selectedLabel)) {
            holder.binding.item.setBackgroundColor(ContextCompat.getColor(context, R.color.primary12));
            ImageViewCompat.setImageTintList(holder.binding.icon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorPrimary)));
        } else {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            holder.binding.item.setBackgroundResource(outValue.resourceId);
            ImageViewCompat.setImageTintList(holder.binding.icon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorSecondaryOnSurface)));
        }
    }

    public void setSelectedLabel(final LabelWithCount label) {
        if ((label == null && this.selectedLabel == null) || (same(label, this.selectedLabel))) {
            return;
        }
        final int previous = getPosition(this.selectedLabel);
        final int position = getPosition(label);
        this.selectedLabel = label;
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous);
        }
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position);
        }
    }

    private int getPosition(final LabelWithCount label) {
        if (label == null) {
            return RecyclerView.NO_POSITION;
        }
        final List<LabelWithCount> items = mDiffer.getCurrentList();
        for (int i = 0; i < items.size(); ++i) {
            if (same(label, items.get(i))) {
                return i + 1;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? HEADER_VIEW_TYPE : ITEM_VIEW_TYPE;
    }

    private LabelWithCount getItem(int position) {
        return this.mDiffer.getCurrentList().get(position - 1);
    }

    public void submitList(List<LabelWithCount> items) {
        this.mDiffer.submitList(items);
    }

    public void setOnMailboxOverviewItemSelectedListener(OnLabelSelected listener) {
        this.onLabelSelected = listener;
    }

    private static boolean same(Label a, Label b) {
        if (a instanceof MailboxOverviewItem && b instanceof MailboxOverviewItem) {
            return ((MailboxOverviewItem) a).getId().equals(((MailboxOverviewItem) b).getId());
        }
        if (a instanceof KeywordLabel && b instanceof KeywordLabel) {
            return a.getRole() != null && a.getRole().equals(b.getRole());
        }
        return false;
    }

    public interface OnLabelSelected {
        void onLabelSelected(final Label label, boolean currentlySelected);
    }

    static class AbstractMailboxViewHolder extends RecyclerView.ViewHolder {

        AbstractMailboxViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class MailboxViewHolder extends AbstractMailboxViewHolder {

        private final ItemMailboxBinding binding;

        MailboxViewHolder(@NonNull ItemMailboxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class MailboxHeaderViewHolder extends AbstractMailboxViewHolder {

        MailboxHeaderViewHolder(@NonNull ItemMailboxHeaderBinding binding) {
            super(binding.getRoot());
        }
    }
}
