package rs.ltt.android.worker;

import androidx.work.Data;

import com.google.common.base.Strings;

import rs.ltt.jmap.common.entity.IdentifiableMailboxWithRoleAndName;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.service.exception.PreexistingMailboxException;

public class Failure {

    private static final String EXCEPTION = "exception";
    private static final String MESSAGE = "message";
    private static final String PREEXISTING_MAILBOX_ID = "preexisting_mailbox_id";
    private static final String TARGET_ROLE = "role";


    private final String exception;
    private final String message;

    private Failure(final String exception, final String message) {
        this.exception = exception;
        this.message = message;
    }


    public static Failure of(final Data data) {
        final String exception = data.getString(EXCEPTION);
        final Class<?> clazz;
        try {
            clazz = Class.forName(Strings.nullToEmpty(exception));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        if (clazz == PreexistingMailboxException.class) {
            return new PreExistingMailbox(
                    data.getString(EXCEPTION),
                    data.getString(MESSAGE),
                    data.getString(PREEXISTING_MAILBOX_ID),
                    Role.valueOf(data.getString(TARGET_ROLE))
            );
        } else {
            return new Failure(
                    data.getString(EXCEPTION),
                    data.getString(MESSAGE)
            );
        }
    }

    static Data of(final Throwable cause) {
        final Data.Builder dataBuilder = new Data.Builder();
        if (cause == null) {
            return dataBuilder.build();
        }
        dataBuilder.putString(EXCEPTION, cause.getClass().getName());
        final String message = cause.getMessage();
        if (!Strings.isNullOrEmpty(message)) {
            dataBuilder.putString(MESSAGE, message);
        }
        if (cause instanceof PreexistingMailboxException) {
            final IdentifiableMailboxWithRoleAndName preexistingMailbox = ((PreexistingMailboxException) cause).getPreexistingMailbox();
            final Role targetRole = ((PreexistingMailboxException) cause).getTargetRole();
            dataBuilder.putString(PREEXISTING_MAILBOX_ID, preexistingMailbox.getId());
            dataBuilder.putString(TARGET_ROLE, targetRole.toString());
        }
        return dataBuilder.build();
    }

    public String getException() {
        return exception;
    }

    public String getMessage() {
        return message;
    }

    public static class PreExistingMailbox extends Failure {


        private final String mailboxId;
        private final Role role;

        private PreExistingMailbox(String exception, String message, final String mailboxId, final Role role) {
            super(exception, message);
            this.mailboxId = mailboxId;
            this.role = role;
        }
    }
}
