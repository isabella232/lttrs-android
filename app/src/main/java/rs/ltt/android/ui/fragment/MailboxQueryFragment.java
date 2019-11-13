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


import android.os.Bundle;
import android.view.View;

import androidx.core.util.Preconditions;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import rs.ltt.android.MainNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.ThreadOverviewItem;

public class MailboxQueryFragment extends AbstractMailboxQueryFragment {
    @Override
    protected String getMailboxId() {
        final Bundle bundle = getArguments();
        return MailboxQueryFragmentArgs.fromBundle(bundle == null ? new Bundle() : bundle).getMailbox();
    }

    @Override
    public void onThreadClicked(String threadId, boolean everyHasSeen) {
        MailboxOverviewItem mailbox = mailboxQueryViewModel.getMailbox().getValue();
        final NavController navController = Navigation.findNavController(
                requireActivity(),
                R.id.nav_host_fragment
        );
        final String label = mailbox != null && mailbox.role == null ? mailbox.name : null;
        navController.navigate(MainNavigationDirections.actionToThread(
                threadId,
                label,
                !everyHasSeen
        ));
    }

    @Override
    protected void onQueryItemSwiped(final ThreadOverviewItem item) {
        //swipe should be disabled when mailbox is null.
        final MailboxOverviewItem mailbox = Preconditions.checkNotNull(
                mailboxQueryViewModel.getMailbox().getValue(),
                "MailboxQueryViewModel had no information about the mailbox we were viewing"
        );
        mailboxQueryViewModel.removeFromMailbox(item);
        final Snackbar snackbar = Snackbar.make(
                this.binding.getRoot(),getString(R.string.removed_from_x, mailbox.getName()),
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction(R.string.undo, v -> mailboxQueryViewModel.copyToMailbox(item));
        snackbar.show();
    }
}
