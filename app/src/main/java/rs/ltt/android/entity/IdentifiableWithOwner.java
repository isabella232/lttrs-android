package rs.ltt.android.entity;

import java.util.Objects;

public interface IdentifiableWithOwner {

    String getId();

    Long getAccountId();

    static void checkSameOwner(IdentifiableWithOwner a, IdentifiableWithOwner b) {
        if (Objects.equals(a.getAccountId(), b.getAccountId())) {
            return;
        }
        throw new OwnerMismatchException(
                String.format(
                        "%s and %s do not belong to the same account",
                        a.getClass().getSimpleName(),
                        b.getClass().getSimpleName()
                )
        );
    }

    class OwnerMismatchException extends RuntimeException {
        private OwnerMismatchException(String message) {
            super(message);
        }
    }
}
