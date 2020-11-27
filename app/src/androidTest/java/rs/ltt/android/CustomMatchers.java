package rs.ltt.android;

import android.view.View;

import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class CustomMatchers {

    public static Matcher<View> withError(final String expectedError) {
        return new TypeSafeMatcher<View>() {

            @Override
            public boolean matchesSafely(final View view) {
                if (view instanceof TextInputLayout) {
                    final CharSequence error = ((TextInputLayout) view).getError();
                    if (error == null) {
                        return false;
                    }
                    final String hint = error.toString();
                    return expectedError.equals(hint);
                }
                return false;

            }

            @Override
            public void describeTo(Description description) {
            }
        };


    }
}
