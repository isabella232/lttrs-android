package rs.ltt.android.ui;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

public class ItemAnimators {

    public static void disableChangeAnimation(final RecyclerView.ItemAnimator itemAnimator) {
        if (itemAnimator instanceof SimpleItemAnimator) {
            final SimpleItemAnimator simpleItemAnimator = (SimpleItemAnimator) itemAnimator;
            simpleItemAnimator.setSupportsChangeAnimations(false);
        }
    }

    public static RecyclerView.ItemAnimator createDefault() {
        final DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        disableChangeAnimation(defaultItemAnimator);
        return defaultItemAnimator;
    }
}
