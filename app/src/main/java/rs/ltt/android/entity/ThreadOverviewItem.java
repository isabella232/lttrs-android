package rs.ltt.android.entity;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.collect.Iterables;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.BindingAdapter;
import androidx.room.Relation;
import rs.ltt.android.R;
import rs.ltt.jmap.common.entity.Keyword;

public class ThreadOverviewItem {

    public long position;

    public String threadId;

    @Relation(parentColumn = "threadId", entityColumn = "threadId", entity = EmailEntity.class)
    public List<Email> emails;

    //TODO grap threadItem to get hold of position
    @Relation(parentColumn = "threadId", entityColumn = "threadId")
    public List<ThreadItemEntity> threadItemEntities;


    public String getPreview() {
        final Email email = Iterables.getLast(emails, null);
        return email == null ? "(no preview)" : email.preview;
    }

    public String getSubject() {
        final Email email = Iterables.getFirst(emails, null);
        return email == null ? "(no subject)" : email.subject;
    }

    public Date getReceivedAt() {
        final Email email = Iterables.getLast(emails, null);
        return email == null ? null : email.receivedAt;
    }

    public boolean everyHasSeenKeyword() {
        for (Email email : emails) {
            if (!email.keywords.contains(Keyword.SEEN)) {
                return false;
            }
        }
        return true;
    }

    public boolean isFlagged() {
        for(Email email : emails) {
            if (email.keywords.contains(Keyword.FLAGGED)) {
                return true;
            }
        }
        return false;
    }

    public Integer getCount() {
        final int count = threadItemEntities.size();
        return count <= 1 ? null : count;
    }

    public Map.Entry<String, From> getFrom() {
        return Iterables.getLast(getFromMap().entrySet(), null);
    }

    private Map<String, From> getFromMap() {
        LinkedHashMap<String, From> froms = new LinkedHashMap<>();
        for (Email email : emails) {
            final boolean seen = email.keywords.contains(Keyword.SEEN);
            for (EmailAddress emailAddress : email.emailAddresses) {
                if (emailAddress.type == EmailAddressType.FROM) {
                    From from = froms.get(emailAddress.email);
                    if (from == null) {
                        final String name = emailAddress.name == null ? emailAddress.email.split("@")[0] : emailAddress.name;
                        from = new From(name, seen);
                        froms.put(emailAddress.email, from);
                    } else {
                        from.seen &= seen;
                    }
                }
            }
        }
        return froms;
    }

    public From[] getFroms() {
        return getFromMap().values().toArray(new From[0]);
    }

    public static class Email {

        public String id;
        public String preview;
        public String threadId;
        public String subject;
        public Date receivedAt;

        @Relation(entity = EmailKeywordEntity.class, parentColumn = "id", entityColumn = "emailId", projection = {"keyword"})
        public Set<String> keywords;

        @Relation(entity = EmailEmailAddressEntity.class, parentColumn = "id", entityColumn = "emailId", projection = {"email", "name", "type"})
        public List<EmailAddress> emailAddresses;

    }

    public static class From {
        public final String name;
        public boolean seen;

        public From(String name, boolean seen) {
            this.name = name;
            this.seen = seen;
        }
    }

    public static class EmailAddress {
        public EmailAddressType type;
        public String email;
        public String name;
    }

    public class EmailPosition {
        public String emailId;
        public Long position;
    }

    @BindingAdapter("android:text")
    public static void setFroms(final TextView textView, final From[] froms) {
        final boolean shorten = froms.length > 1;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < froms.length; ++i) {
            From from = froms[i];
            if (builder.length() != 0) {
                builder.append(", ");
            }
            int start = builder.length();
            builder.append(shorten ? from.name.split("\\s")[0] : from.name);
            if (!from.seen) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (froms.length > 3) {
                if (i < froms.length - 3) {
                    builder.append(" … "); //TODO small?
                    i = froms.length - 3;
                }
            }
        }
        textView.setText(builder);
    }

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd");

    private static boolean isToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar specifiedDate = Calendar.getInstance();
        specifiedDate.setTime(date);

        return today.get(Calendar.DAY_OF_MONTH) == specifiedDate.get(Calendar.DAY_OF_MONTH)
                && today.get(Calendar.MONTH) == specifiedDate.get(Calendar.MONTH)
                && today.get(Calendar.YEAR) == specifiedDate.get(Calendar.YEAR);
    }

    @BindingAdapter("android:text")
    public static void setInteger(TextView textView, Date receivedAt) {
        if (receivedAt == null || receivedAt.getTime() <= 0) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            if (isToday(receivedAt)) {
                textView.setText(TIME_FORMAT.format(receivedAt));
            } else {
                textView.setText(DATE_FORMAT.format(receivedAt));
            }
        }
    }

    @BindingAdapter("android:typeface")
    public static void setTypeface(TextView v, String style) {
        switch (style) {
            case "bold":
                v.setTypeface(null, Typeface.BOLD);
                break;
            default:
                v.setTypeface(null, Typeface.NORMAL);
                break;
        }
    }

    @BindingAdapter("isFlagged")
    public static void setIsFlagged(final ImageView imageView, final boolean isFlagged) {
        if (isFlagged) {
            imageView.setImageResource(R.drawable.ic_star_black_24dp);
            ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(ContextCompat.getColor(imageView.getContext(), R.color.colorPrimary)));
        } else {
            imageView.setImageResource(R.drawable.ic_star_border_black_24dp);
            ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(ContextCompat.getColor(imageView.getContext(), R.color.black54)));
        }
    }
}
