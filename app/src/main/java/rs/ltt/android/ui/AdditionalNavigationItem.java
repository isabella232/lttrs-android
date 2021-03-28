package rs.ltt.android.ui;

import rs.ltt.jmap.mua.util.Navigable;

public class AdditionalNavigationItem implements Navigable {

    public final Type type;

    public AdditionalNavigationItem(final Type type) {
        this.type = type;
    }


    public enum Type {
        MANAGE_ACCOUNT
    }

}
