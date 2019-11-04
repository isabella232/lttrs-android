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

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.ui.OnLabelOpened;
import rs.ltt.android.ui.QueryItemTouchHelper;
import rs.ltt.android.ui.model.AbstractQueryViewModel;
import rs.ltt.android.ui.model.KeywordQueryViewModel;
import rs.ltt.android.ui.model.KeywordQueryViewModelFactory;
import rs.ltt.jmap.mua.util.KeywordLabel;

public class KeywordQueryFragment extends AbstractQueryFragment {

    private KeywordQueryViewModel keywordQueryViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Bundle bundle = getArguments();
        final KeywordLabel keyword = KeywordQueryFragmentArgs.fromBundle(bundle == null ? new Bundle() : bundle).getKeyword();
        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                getViewModelStore(),
                new KeywordQueryViewModelFactory(requireActivity().getApplication(), keyword.getKeyword())
        );
        this.keywordQueryViewModel = viewModelProvider.get(KeywordQueryViewModel.class);
        onLabelOpened(keyword);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected AbstractQueryViewModel getQueryViewModel() {
        return keywordQueryViewModel;
    }

    @Override
    protected QueryItemTouchHelper.Swipable onQueryItemSwipe(ThreadOverviewItem item) {
        return null;
    }

    @Override
    protected void onQueryItemSwiped(ThreadOverviewItem item) {

    }
}
