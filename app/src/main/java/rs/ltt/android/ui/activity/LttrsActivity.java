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

package rs.ltt.android.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.work.WorkInfo;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import rs.ltt.android.LttrsNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.ActivityLttrsBinding;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.ui.OnLabelOpened;
import rs.ltt.android.ui.Theme;
import rs.ltt.android.ui.ThreadModifier;
import rs.ltt.android.ui.adapter.LabelListAdapter;
import rs.ltt.android.ui.fragment.SearchQueryFragment;
import rs.ltt.android.ui.model.LttrsViewModel;
import rs.ltt.android.util.MainThreadExecutor;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.KeywordLabel;
import rs.ltt.jmap.mua.util.Label;

public class LttrsActivity extends AppCompatActivity implements OnLabelOpened, ThreadModifier, SearchQueryFragment.OnTermSearched, NavController.OnDestinationChangedListener, MenuItem.OnActionExpandListener, DrawerLayout.DrawerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsActivity.class);

    private static final int NUM_TOOLBAR_ICON = 1;
    private static final List<Integer> MAIN_DESTINATIONS = Arrays.asList(
            R.id.inbox,
            R.id.mailbox,
            R.id.keyword
    );
    final LabelListAdapter labelListAdapter = new LabelListAdapter();
    private ActivityLttrsBinding binding;
    private LttrsViewModel lttrsViewModel;
    private MenuItem mSearchItem;
    private SearchView mSearchView;
    private ActionMode actionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_lttrs);

        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                this,
                getDefaultViewModelProviderFactory()
        );
        lttrsViewModel = viewModelProvider.get(LttrsViewModel.class);
        lttrsViewModel.getHasAccounts().observe(this, this::onHasAccountsChanged);
        setSupportActionBar(binding.toolbar);

        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );

        binding.drawerLayout.addDrawerListener(this);

        labelListAdapter.setOnMailboxOverviewItemSelectedListener((label, currentlySelected) -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            if (currentlySelected) {
                return;
            }
            final boolean navigateToInbox = label.getRole() == Role.INBOX;
            if (navigateToInbox) {
                navController.navigate(LttrsNavigationDirections.actionToInbox());
            } else if (label instanceof MailboxOverviewItem) {
                final MailboxOverviewItem mailbox = (MailboxOverviewItem) label;
                navController.navigate(LttrsNavigationDirections.actionToMailbox(mailbox.id));
            } else if (label instanceof KeywordLabel) {
                final KeywordLabel keyword = (KeywordLabel) label;
                navController.navigate(LttrsNavigationDirections.actionToKeyword(keyword));
            } else {
                throw new IllegalStateException(String.format("%s is an unsupported label", label.getClass()));
            }
            if (mSearchItem != null) {
                mSearchItem.collapseActionView();
            }
            //currently unused should remain here in case we bring scrollable toolbar back
            binding.appBarLayout.setExpanded(true, false);
        });
        binding.mailboxList.setAdapter(labelListAdapter);
        lttrsViewModel.getNavigatableLabels().observe(this, labelListAdapter::submitList);
    }

    private void onHasAccountsChanged(Boolean hasAccounts) {
        if (!hasAccounts) {
            startActivity(new Intent(LttrsActivity.this, SetupActivity.class));
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        final int currentDestination = getCurrentDestinationId();
        final boolean showSearch = MAIN_DESTINATIONS.contains(currentDestination) || currentDestination == R.id.search;

        getMenuInflater().inflate(R.menu.activity_main, menu);

        mSearchItem = menu.findItem(R.id.action_search);

        mSearchItem.setVisible(showSearch);

        if (showSearch) {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView) mSearchItem.getActionView();
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            if (currentDestination == R.id.search) {
                setSearchToolbarColors();
                mSearchItem.expandActionView();
                mSearchView.setQuery(lttrsViewModel.getCurrentSearchTerm(), false);
                mSearchView.clearFocus();
            }
            mSearchItem.setOnActionExpandListener(this);
        } else {
            mSearchItem = null;
            mSearchView = null;
            resetToolbarColors();
        }

        return super.onCreateOptionsMenu(menu);
    }

    private int getCurrentDestinationId() {
        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );
        final NavDestination currentDestination = navController.getCurrentDestination();
        return currentDestination == null ? 0 : currentDestination.getId();
    }

    @Override
    public void onStart() {
        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );
        navController.addOnDestinationChangedListener(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );
        navController.removeOnDestinationChangedListener(this);
    }

    private void configureActionBarForDestination(NavDestination destination) {
        final ActionBar actionbar = getSupportActionBar();
        if (actionbar == null) {
            return;
        }
        final int destinationId = destination.getId();
        final boolean showMenu = MAIN_DESTINATIONS.contains(destinationId);
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(showMenu ? R.drawable.ic_menu_black_24dp : R.drawable.ic_arrow_back_white_24dp);
        actionbar.setDisplayShowTitleEnabled(destinationId != R.id.thread);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                final NavController navController = Navigation.findNavController(
                        this,
                        R.id.nav_host_fragment
                );
                final NavDestination currentDestination = navController.getCurrentDestination();
                if (currentDestination != null && MAIN_DESTINATIONS.contains(currentDestination.getId())) {
                    binding.drawerLayout.openDrawer(GravityCompat.START);
                    return true;
                } else {
                    onBackPressed();
                }
        }
        return super.onOptionsItemSelected(item);

    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);

            if (mSearchView != null) {
                mSearchView.setQuery(query, false);
                mSearchView.clearFocus(); //this does not work on all phones / android versions; therefor we have this followed by a requestFocus() on the list
            }
            binding.mailboxList.requestFocus();

            lttrsViewModel.insertSearchSuggestion(query);
            final NavController navController = Navigation.findNavController(
                    this,
                    R.id.nav_host_fragment
            );
            navController.navigate(LttrsNavigationDirections.actionSearch(query));
        }

    }

    @Override
    public void archive(final String threadId) {
        archive(ImmutableSet.of(threadId));
    }

    public void archive(Collection<String> threadIds) {
        final int count = threadIds.size();
        lttrsViewModel.archive(threadIds);
        final Snackbar snackbar = Snackbar.make(
                this.binding.getRoot(),
                getResources().getQuantityString(R.plurals.n_archived, count, count),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.moveToInbox(threadIds));
        snackbar.show();
    }

    @Override
    public void moveToInbox(final String threadId) {
        moveToInbox(ImmutableSet.of(threadId));
    }

    public void moveToInbox(final Collection<String> threadIds) {
        final int count = threadIds.size();
        final Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                getResources().getQuantityString(R.plurals.n_moved_to_inbox, count, count),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.archive(threadIds));
        snackbar.show();
        lttrsViewModel.moveToInbox(threadIds);
    }

    @Override
    public void moveToTrash(final String threadId) {
        moveToTrash(ImmutableSet.of(threadId));
    }

    public void moveToTrash(final Collection<String> threadIds) {
        final int count = threadIds.size();
        final Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                getResources().getQuantityString(R.plurals.n_deleted, count, count),
                Snackbar.LENGTH_LONG
        );
        final ListenableFuture<LiveData<WorkInfo>> future = lttrsViewModel.moveToTrash(threadIds);
        snackbar.setAction(R.string.undo, v -> {
            try {
                final LiveData<WorkInfo> workInfoLiveData = future.get();
                final WorkInfo workInfo = workInfoLiveData.getValue();
                lttrsViewModel.cancelMoveToTrash(workInfo, threadIds);
            } catch (Exception e) {
                LOGGER.warn("Unable to cancel moveToTrash operation", e);
            }
        });
        snackbar.show();
        future.addListener(() -> {
            try {
                future.get().observe(this, workInfo -> {
                    if (workInfo != null && workInfo.getState() != WorkInfo.State.ENQUEUED && snackbar.isShown()) {
                        snackbar.dismiss();
                    }
                });
            } catch (Exception e) {
                LOGGER.warn("Unable to observe moveToTrash operation", e);
            }
        }, MainThreadExecutor.getInstance());
    }

    @Override
    public void removeFromMailbox(String threadId, MailboxWithRoleAndName mailbox) {
        removeFromMailbox(ImmutableSet.of(threadId), mailbox);
    }

    public void removeFromMailbox(Collection<String> threadIds, MailboxWithRoleAndName mailbox) {
        final int count = threadIds.size();
        final Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                getResources().getQuantityString(R.plurals.n_removed_from_x, count, count, mailbox.name),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.copyToMailbox(threadIds, mailbox));
        snackbar.show();
        lttrsViewModel.removeFromMailbox(threadIds, mailbox);
    }

    public void removeFromKeyword(Collection<String> threadIds, final String keyword) {
        final int count = threadIds.size();
        final Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                getResources().getQuantityString(
                        R.plurals.n_removed_from_x,
                        count,
                        count,
                        KeywordLabel.of(keyword).getName()
                ),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.addKeyword(threadIds, keyword));
        snackbar.show();
        lttrsViewModel.removeKeyword(threadIds, keyword);
    }

    @Override
    public void onLabelOpened(Label label) {
        setTitle(label.getName());
        labelListAdapter.setSelectedLabel(label);
    }

    @Override
    public void onBackPressed() {
        if (binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestinationChanged(@NonNull NavController controller, @NonNull NavDestination destination, @Nullable Bundle arguments) {
        LOGGER.debug("onDestinationChanged({})", destination.getLabel());
        if (destination.getId() == R.id.thread) {
            endActionMode();
        }
        configureActionBarForDestination(destination);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        animateShowSearchToolbar();
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        animateCloseSearchToolbar();
        if (getCurrentDestinationId() == R.id.search) {
            final NavController navController = Navigation.findNavController(
                    this,
                    R.id.nav_host_fragment
            );
            navController.navigateUp();
        }
        return true;
    }

    public void animateShowSearchToolbar() {
        setSearchToolbarColors();
        final int toolbarIconWidth = getResources().getDimensionPixelSize(R.dimen.toolbar_icon_width);
        final int width = binding.toolbar.getWidth() - ((toolbarIconWidth * NUM_TOOLBAR_ICON) / 2);
        Animator createCircularReveal = ViewAnimationUtils.createCircularReveal(binding.toolbar, Theme.isRtl(this) ? binding.toolbar.getWidth() - width : width, binding.toolbar.getHeight() / 2, 0.0f, (float) width);
        createCircularReveal.setDuration(250);
        createCircularReveal.start();
    }

    public void animateCloseSearchToolbar() {
        final int toolbarIconWidth = getResources().getDimensionPixelSize(R.dimen.toolbar_icon_width);
        final int width = binding.toolbar.getWidth() - ((toolbarIconWidth * NUM_TOOLBAR_ICON) / 2);
        Animator createCircularReveal = ViewAnimationUtils.createCircularReveal(binding.toolbar, Theme.isRtl(this) ? binding.toolbar.getWidth() - width : width, binding.toolbar.getHeight() / 2, (float) width, 0.0f);
        createCircularReveal.setDuration(250);
        createCircularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                resetToolbarColors();
            }
        });
        createCircularReveal.start();
    }

    private void resetToolbarColors() {
        binding.toolbar.setBackgroundColor(Theme.getColor(LttrsActivity.this, R.attr.colorPrimary));
        binding.drawerLayout.setStatusBarBackgroundColor(Theme.getColor(LttrsActivity.this, R.attr.colorPrimaryDark));
    }

    private void setSearchToolbarColors() {
        binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.colorSurface));
        binding.drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.colorStatusBarSearch));
    }

    @Override
    public void onTermSearched(final String term) {
        lttrsViewModel.setCurrentSearchTerm(term);
        labelListAdapter.setSelectedLabel(null);
    }

    public ActionMode beginActionMode(final ActionMode.Callback callback) {
        this.actionMode = startSupportActionMode(callback);
        return this.actionMode;
    }

    public void endActionMode() {
        if (this.actionMode != null) {
            this.actionMode.finish();
        }
        this.actionMode = null;
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        endActionMode();
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {

    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {

    }

    @Override
    public void onDrawerStateChanged(int newState) {

    }
}
