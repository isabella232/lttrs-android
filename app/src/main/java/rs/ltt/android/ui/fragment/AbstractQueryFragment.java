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

package rs.ltt.android.ui.fragment;


import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.paging.PagedList;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import rs.ltt.android.LttrsNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentThreadListBinding;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.ui.ActionModeMenuConfiguration;
import rs.ltt.android.ui.QueryItemTouchHelper;
import rs.ltt.android.ui.activity.ComposeActivity;
import rs.ltt.android.ui.adapter.OnFlaggedToggled;
import rs.ltt.android.ui.adapter.ThreadOverviewAdapter;
import rs.ltt.android.ui.adapter.ThreadOverviewItemDetailsLookup;
import rs.ltt.android.ui.adapter.ThreadOverviewItemKeyProvider;
import rs.ltt.android.ui.model.AbstractQueryViewModel;
import rs.ltt.android.util.Event;
import rs.ltt.android.worker.Failure;
import rs.ltt.jmap.mua.util.Label;


public abstract class AbstractQueryFragment extends AbstractLttrsFragment implements OnFlaggedToggled,
        ThreadOverviewAdapter.OnThreadClicked, QueryItemTouchHelper.OnQueryItemSwipe, ActionMode.Callback {

    private static final String SELECTION_ID = "thread-items";

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractQueryFragment.class);
    protected FragmentThreadListBinding binding;
    private ThreadOverviewAdapter threadOverviewAdapter;
    private ActionMode actionMode;
    private SelectionTracker<String> tracker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final AbstractQueryViewModel viewModel = getQueryViewModel();
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_thread_list, container, false);

        setupAdapter(viewModel.getImportant());
        setupSelectionTracker(savedInstanceState);
        observeThreadOverviewItems(viewModel.getThreadOverviewItems());

        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.compose.setOnClickListener((v) -> ComposeActivity.compose(this, getLttrsViewModel().getAccountId()));
        if (showComposeButton() && actionMode == null) {
            binding.compose.show();
        }

        binding.swipeToRefresh.setColorSchemeResources(R.color.colorAccent);
        binding.swipeToRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.colorSurface)
        );

        //TODO: do we want to get rid of flicker on changes
        //((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        viewModel.isRunningPagingRequest().observe(getViewLifecycleOwner(), threadOverviewAdapter::setLoading);

        viewModel.getEmailModificationWorkInfo().observe(getViewLifecycleOwner(), this::emailModification);

        final QueryItemTouchHelper queryItemTouchHelper = new QueryItemTouchHelper();

        queryItemTouchHelper.setOnQueryItemSwipeListener(this);

        new ItemTouchHelper(queryItemTouchHelper).attachToRecyclerView(binding.threadList);

        return binding.getRoot();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        LOGGER.info("on activity result code={}, result={}, intent={}", requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ComposeActivity.REQUEST_EDIT_DRAFT && resultCode == ComposeActivity.RESULT_OK) {
            final UUID uuid = (UUID) data.getSerializableExtra(ComposeActivity.EDITING_TASK_ID_EXTRA);
            if (uuid != null) {
                getLttrsViewModel().observeForFailure(uuid);
            }
        }
    }

    private void setupAdapter(final Future<MailboxWithRoleAndName> importantMailbox) {
        this.threadOverviewAdapter = new ThreadOverviewAdapter();
        this.binding.threadList.setAdapter(threadOverviewAdapter);
        this.threadOverviewAdapter.setOnFlaggedToggledListener(this);
        this.threadOverviewAdapter.setOnThreadClickedListener(this);
        this.threadOverviewAdapter.setImportantMailbox(importantMailbox);
    }

    private void setupSelectionTracker(final Bundle savedInstanceState) {
        tracker = new SelectionTracker.Builder<>(
                SELECTION_ID,
                binding.threadList,
                new ThreadOverviewItemKeyProvider(threadOverviewAdapter),
                new ThreadOverviewItemDetailsLookup(binding.threadList),
                StorageStrategy.createStringStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build();
        threadOverviewAdapter.setTracker(tracker);
        tracker.addObserver(new SelectionTracker.SelectionObserver<String>() {
            @Override
            public void onSelectionChanged() {
                toggleActionMode();
            }

            public void onSelectionRestored() {
                toggleActionMode();
            }
        });
        tracker.onRestoreInstanceState(savedInstanceState);
    }

    private void observeThreadOverviewItems(LiveData<PagedList<ThreadOverviewItem>> liveData) {
        final AtomicBoolean actionModeRefreshed = new AtomicBoolean(false);
        liveData.observe(getViewLifecycleOwner(), threadOverviewItems -> {
            final RecyclerView.LayoutManager layoutManager = binding.threadList.getLayoutManager();
            final boolean atTop;
            if (layoutManager instanceof LinearLayoutManager) {
                atTop = ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition() == 0;
            } else {
                atTop = false;
            }
            LOGGER.debug("submitting list");
            threadOverviewAdapter.submitList(threadOverviewItems, () -> {
                LOGGER.debug("list submitted");
                if (atTop && binding != null) {
                    binding.threadList.scrollToPosition(0);
                }
                if (actionMode != null && actionModeRefreshed.compareAndSet(false, true)) {
                    actionMode.invalidate();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        nullReferences();
        super.onDestroyView();
    }

    private void nullReferences() {
        this.binding.threadList.setAdapter(null);
        this.threadOverviewAdapter.setTracker(null);
        this.threadOverviewAdapter = null;
        this.tracker = null;
        this.binding = null;
    }

    private void toggleActionMode() {
        if (tracker.hasSelection()) {
            if (this.actionMode == null) {
                this.actionMode = requireLttrsActivity().beginActionMode(this);
            } else {
                this.actionMode.setTitle(String.valueOf(tracker.getSelection().size()));
                this.actionMode.invalidate();
            }
        } else if (actionMode != null) {
            requireLttrsActivity().endActionMode();
        }
    }

    private void emailModification(boolean allDone) {
        if (allDone) {
            getQueryViewModel().refreshInBackground();
        }
    }

    void onLabelOpened(final Label label) {
        getLttrsViewModel().setSelectedLabel(label);
        getLttrsViewModel().setActivityTitle(label.getName());
    }

    @Override
    public void onFlaggedToggled(String threadId, boolean target) {
        getThreadModifier().toggleFlagged(threadId, target);
    }


    protected void archive(ThreadOverviewItem item) {
        archive(ImmutableSet.of(item.threadId));
    }

    protected void archive(Collection<String> threadIds) {
        getThreadModifier().archive(threadIds);
    }

    protected abstract AbstractQueryViewModel getQueryViewModel();

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        if (tracker != null) {
            tracker.onSaveInstanceState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onThreadClicked(ThreadOverviewItem threadOverviewItem, boolean important) {
        getNavController().navigate(LttrsNavigationDirections.actionToThread(
                threadOverviewItem.threadId,
                null,
                threadOverviewItem.getSubject(),
                important
        ));
    }

    @Override
    public QueryItemTouchHelper.Swipable onQueryItemSwipe(int position) {
        final ThreadOverviewItem item = threadOverviewAdapter.getItem(position);
        if (item == null) {
            throw new IllegalStateException("Swipe Item not found");
        }
        return onQueryItemSwipe(item);
    }

    protected abstract QueryItemTouchHelper.Swipable onQueryItemSwipe(ThreadOverviewItem item);

    @Override
    public void onQueryItemSwiped(int position) {
        final ThreadOverviewItem item = threadOverviewAdapter.getItem(position);
        if (item == null) {
            throw new IllegalStateException("Swipe Item not found");
        }
        tracker.deselect(item.threadId);
        onQueryItemSwiped(item);
    }

    protected abstract void onQueryItemSwiped(ThreadOverviewItem item);

    protected abstract boolean showComposeButton();

    @Override
    public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
        LOGGER.debug("onCreateActionMode()");
        this.actionMode = mode;
        this.actionMode.getMenuInflater().inflate(R.menu.thread_item_action_mode, menu);
        this.actionMode.setTitle(String.valueOf(tracker.getSelection().size()));
        binding.compose.hide();
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        LOGGER.debug("prepare action mode for {} selected items", tracker.getSelection().size());
        final ActionModeMenuConfiguration.QueryType queryType = getQueryType();
        getQueryViewModel().getThreadOverviewItems().getValue();
        final ActionModeMenuConfiguration.SelectionInfo selectionInfo = ActionModeMenuConfiguration.SelectionInfo.vote(
                tracker.getSelection(),
                threadOverviewAdapter
        );
        final MenuItem archive = menu.findItem(R.id.action_archive);
        final MenuItem removeLabel = menu.findItem(R.id.action_remove_label);
        final MenuItem markRead = menu.findItem(R.id.action_mark_read);
        final MenuItem markUnRead = menu.findItem(R.id.action_mark_unread);
        final MenuItem moveToInbox = menu.findItem(R.id.action_move_to_inbox);
        final MenuItem markImportant = menu.findItem(R.id.action_mark_important);
        final MenuItem markNotImportant = menu.findItem(R.id.action_mark_not_important);
        final MenuItem addFlag = menu.findItem(R.id.action_add_flag);
        final MenuItem removeFlag = menu.findItem(R.id.action_remove_flag);

        if (queryType == ActionModeMenuConfiguration.QueryType.ARCHIVE) {
            archive.setVisible(false);
            removeLabel.setVisible(false);
        } else if (queryType == ActionModeMenuConfiguration.QueryType.SPECIAL) {
            archive.setVisible(false);
            removeLabel.setVisible(false);
            moveToInbox.setVisible(false);
        } else if (queryType == ActionModeMenuConfiguration.QueryType.INBOX) {
            removeLabel.setVisible(false);
            moveToInbox.setVisible(false);
        } else {
            archive.setVisible(false);
            moveToInbox.setVisible(false);
        }
        markRead.setVisible(!selectionInfo.read);
        markUnRead.setVisible(selectionInfo.read);
        markImportant.setVisible(queryType != ActionModeMenuConfiguration.QueryType.IMPORTANT && !selectionInfo.important);
        markNotImportant.setVisible(queryType != ActionModeMenuConfiguration.QueryType.IMPORTANT && selectionInfo.important);
        addFlag.setVisible(queryType != ActionModeMenuConfiguration.QueryType.FLAGGED && !selectionInfo.flagged);
        removeFlag.setVisible(queryType != ActionModeMenuConfiguration.QueryType.FLAGGED && selectionInfo.flagged);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        final Collection<String> threadIds = Sets.newHashSet(tracker.getSelection());
        switch (item.getItemId()) {
            case R.id.action_archive:
                archive(threadIds);
                tracker.clearSelection();
                return true;
            case R.id.action_remove_label:
                removeLabel(threadIds);
                tracker.clearSelection();
                return true;
            case R.id.action_mark_read:
                getThreadModifier().markRead(threadIds);
                return true;
            case R.id.action_mark_unread:
                getThreadModifier().markUnread(threadIds);
                return true;
            case R.id.action_move_to_inbox:
                getThreadModifier().moveToInbox(threadIds);
                tracker.clearSelection();
            case R.id.action_mark_important:
                getThreadModifier().markImportant(threadIds);
                return true;
            case R.id.action_mark_not_important:
                getThreadModifier().markNotImportant(threadIds);
                return true;
            case R.id.action_add_flag:
                getThreadModifier().addFlag(threadIds);
                return true;
            case R.id.action_remove_flag:
                getThreadModifier().removeFlag(threadIds);
                return true;
            case R.id.action_move_to_trash:
                getThreadModifier().moveToTrash(threadIds);
                tracker.clearSelection();
                return true;
            default:
                return onActionItemClicked(mode, item);
        }
    }

    abstract void removeLabel(Collection<String> threadIds);

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        tracker.clearSelection();
        if (showComposeButton()) {
            binding.compose.show();
        }
        this.actionMode = null;
    }

    protected abstract ActionModeMenuConfiguration.QueryType getQueryType();
}
