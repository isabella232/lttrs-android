package rs.ltt.android.entity;

import rs.ltt.jmap.mua.util.Navigable;

public class AccountName implements Navigable {

    public Long id;
    public String name;

    public String getName() {
        return this.name;
    }
}
