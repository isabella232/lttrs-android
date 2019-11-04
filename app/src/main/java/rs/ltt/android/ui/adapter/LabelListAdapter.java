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

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import rs.ltt.android.R;
import rs.ltt.android.databinding.MailboxListHeaderBinding;
import rs.ltt.android.databinding.MailboxListItemBinding;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.jmap.mua.util.KeywordLabel;
import rs.ltt.jmap.mua.util.Label;

public class LabelListAdapter extends RecyclerView.Adapter<LabelListAdapter.AbstractMailboxViewHolder> {

    private static final int ITEM_VIEW_TYPE = 1;
    private static final int HEADER_VIEW_TYPE = 2;

    private static final DiffUtil.ItemCallback<Label> ITEM_CALLBACK = new DiffUtil.ItemCallback<Label>() {
        @Override
        public boolean areItemsTheSame(@NonNull Label oldItem, @NonNull Label newItem) {
            return LabelListAdapter.same(oldItem, newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Label oldItem, @NonNull Label newItem) {
            return oldItem.equals(newItem);
        }
    };

    private final AsyncListDiffer<Label> mDiffer = new AsyncListDiffer<>(new OffsetListUpdateCallback<>(this, 1), new AsyncDifferConfig.Builder<>(ITEM_CALLBACK).build());

    private Label selectedLabel = null;

    private OnLabelSelected onLabelSelected = null;

    public LabelListAdapter() {
        super();
    }


    @NonNull
    @Override
    public AbstractMailboxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ITEM_VIEW_TYPE) {
            MailboxListItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.mailbox_list_item, parent, false);
            return new MailboxViewHolder(binding);
        } else {
            MailboxListHeaderBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.mailbox_list_header, parent, false);
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
        final Label label = getItem(position);
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
            ImageViewCompat.setImageTintList(holder.binding.icon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.black54)));
        }
    }

    public void setSelectedLabel(final Label label) {
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

    private int getPosition(final Label label) {
        if (label == null) {
            return RecyclerView.NO_POSITION;
        }
        final List<Label> items = mDiffer.getCurrentList();
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

    private Label getItem(int position) {
        return this.mDiffer.getCurrentList().get(position - 1);
    }

    public void submitList(List<Label> items) {
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

    class AbstractMailboxViewHolder extends RecyclerView.ViewHolder {

        AbstractMailboxViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    class MailboxViewHolder extends AbstractMailboxViewHolder {

        private final MailboxListItemBinding binding;

        MailboxViewHolder(@NonNull MailboxListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    class MailboxHeaderViewHolder extends AbstractMailboxViewHolder {

        MailboxHeaderViewHolder(@NonNull MailboxListHeaderBinding binding) {
            super(binding.getRoot());
        }
    }
}
