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

package rs.ltt.android.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.work.WorkInfo;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import rs.ltt.android.MainNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.ActivityMainBinding;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.ui.adapter.LabelListAdapter;
import rs.ltt.android.ui.fragment.SearchQueryFragment;
import rs.ltt.android.ui.model.MainViewModel;
import rs.ltt.android.util.MainThreadExecutor;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.KeywordLabel;
import rs.ltt.jmap.mua.util.Label;

public class MainActivity extends AppCompatActivity implements OnLabelOpened, ThreadModifier, SearchQueryFragment.OnTermSearched, NavController.OnDestinationChangedListener, MenuItem.OnActionExpandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);

    private static final int NUM_TOOLBAR_ICON = 1;
    private static final List<Integer> MAIN_DESTINATIONS = Arrays.asList(
            R.id.inbox,
            R.id.mailbox,
            R.id.keyword
    );
    final LabelListAdapter labelListAdapter = new LabelListAdapter();
    private ActivityMainBinding binding;
    private MainViewModel mainViewModel;
    private MenuItem mSearchItem;
    private SearchView mSearchView;
    //TODO: move to viewmodel
    private String currentSearchTerm = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                getViewModelStore(),
                getDefaultViewModelProviderFactory()
        );
        mainViewModel = viewModelProvider.get(MainViewModel.class);
        setSupportActionBar(binding.toolbar);

        final NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment
        );

        labelListAdapter.setOnMailboxOverviewItemSelectedListener((label, currentlySelected) -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            if (currentlySelected) {
                return;
            }
            final boolean navigateToInbox = label.getRole() == Role.INBOX;
            if (navigateToInbox) {
                navController.navigate(MainNavigationDirections.actionToInbox());
            } else if (label instanceof MailboxOverviewItem) {
                final MailboxOverviewItem mailbox = (MailboxOverviewItem) label;
                navController.navigate(MainNavigationDirections.actionToMailbox(mailbox.id));
            } else if (label instanceof KeywordLabel) {
                final KeywordLabel keyword = (KeywordLabel) label;
                navController.navigate(MainNavigationDirections.actionToKeyword(keyword));
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
        mainViewModel.getNavigatableLabels().observe(this, labelListAdapter::submitList);
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
                mSearchView.setQuery(currentSearchTerm, false);
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

            mainViewModel.insertSearchSuggestion(query);
            final NavController navController = Navigation.findNavController(
                    this,
                    R.id.nav_host_fragment
            );
            navController.navigate(MainNavigationDirections.actionSearch(query));
        }

    }

    @Override
    public void archive(final String threadId) {
        final Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.archived, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> mainViewModel.moveToInbox(threadId));
        snackbar.show();
        mainViewModel.archive(threadId);
    }

    @Override
    public void moveToInbox(final String threadId) {
        final Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.moved_to_inbox, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> mainViewModel.archive(threadId));
        snackbar.show();
        mainViewModel.moveToInbox(threadId);
    }

    @Override
    public void moveToTrash(final String threadId) {
        final Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.deleted, Snackbar.LENGTH_LONG);
        final ListenableFuture<LiveData<WorkInfo>> future = mainViewModel.moveToTrash(threadId);
        snackbar.setAction(R.string.undo, v -> {
            try {
                final LiveData<WorkInfo> workInfoLiveData = future.get();
                final WorkInfo workInfo = workInfoLiveData.getValue();
                mainViewModel.cancelMoveToTrash(workInfo, threadId);
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
        final Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                getString(R.string.removed_from_x, mailbox.name),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> mainViewModel.copyToMailbox(threadId, mailbox));
        snackbar.show();
        mainViewModel.removeFromMailbox(threadId, mailbox);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            animateShowSearchToolbarLollipop();
        } else {
            animateShowSearchToolbarLegacy();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void animateShowSearchToolbarLollipop() {
        final int toolbarIconWidth = getResources().getDimensionPixelSize(R.dimen.toolbar_icon_width);
        final int width = binding.toolbar.getWidth() - ((toolbarIconWidth * NUM_TOOLBAR_ICON) / 2);
        Animator createCircularReveal = ViewAnimationUtils.createCircularReveal(binding.toolbar, Theme.isRtl(this) ? binding.toolbar.getWidth() - width : width, binding.toolbar.getHeight() / 2, 0.0f, (float) width);
        createCircularReveal.setDuration(250);
        createCircularReveal.start();
    }

    private void animateShowSearchToolbarLegacy() {
        TranslateAnimation translateAnimation = new TranslateAnimation(0.0f, 0.0f, (float) (-binding.toolbar.getHeight()), 0.0f);
        translateAnimation.setDuration(220);
        binding.toolbar.clearAnimation();
        binding.toolbar.startAnimation(translateAnimation);
    }

    public void animateCloseSearchToolbar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            animateCloseSearchToolbarLollipop();
        } else {
            animateCloseSearchToolbarLegacy();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void animateCloseSearchToolbarLollipop() {
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

    private void animateCloseSearchToolbarLegacy() {
        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
        Animation translateAnimation = new TranslateAnimation(0.0f, 0.0f, 0.0f, (float) (-binding.toolbar.getHeight()));
        AnimationSet animationSet = new AnimationSet(true);
        animationSet.addAnimation(alphaAnimation);
        animationSet.addAnimation(translateAnimation);
        animationSet.setDuration(220);
        animationSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                resetToolbarColors();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        binding.toolbar.startAnimation(animationSet);
    }

    private void resetToolbarColors() {
        binding.toolbar.setBackgroundColor(Theme.getColor(MainActivity.this, R.attr.colorPrimary));
        binding.drawerLayout.setStatusBarBackgroundColor(Theme.getColor(MainActivity.this, R.attr.colorPrimaryDark));
    }

    private void setSearchToolbarColors() {
        binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        binding.drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.quantum_grey_600));
    }

    @Override
    public void onTermSearched(String term) {
        this.currentSearchTerm = term;
        Log.d("lttrs", "on term searched " + term);
        labelListAdapter.setSelectedLabel(null);
    }
}
