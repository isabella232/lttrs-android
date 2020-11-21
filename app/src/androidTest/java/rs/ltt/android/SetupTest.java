package rs.ltt.android;

import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import okhttp3.mockwebserver.MockWebServer;
import rs.ltt.android.ui.activity.LttrsActivity;
import rs.ltt.android.ui.activity.SetupActivity;
import rs.ltt.jmap.mock.server.JmapDispatcher;
import rs.ltt.jmap.mock.server.StubMailServer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class SetupTest {

    @Rule
    public ActivityScenarioRule<SetupActivity> activityRule = new ActivityScenarioRule<>(SetupActivity.class);


    private final MockWebServer mockWebServer = new MockWebServer();

    @Before
    public void startServer() throws IOException {
        mockWebServer.setDispatcher(new StubMailServer());
        mockWebServer.start();
        Intents.init();
    }

    @Test
    public void setup() throws InterruptedException {
        onView(withId(R.id.email_address)).perform(typeText(JmapDispatcher.USERNAME));
        onView(withId(R.id.next)).perform(click());

        Thread.sleep(2000);

        onView(withId(R.id.header)).check(matches(withText("Info required")));

        onView(withId(R.id.url)).perform(typeText(mockWebServer.url(JmapDispatcher.WELL_KNOWN_PATH).toString()));

        onView(withId(R.id.next)).perform(click());

        Thread.sleep(2000);

        onView(withId(R.id.password)).perform(typeText(JmapDispatcher.PASSWORD));

        onView(withId(R.id.next)).perform(click());

        Thread.sleep(2000);

        intended(hasComponent(LttrsActivity.class.getName()));

    }

    @Test
    public void invalidEmailAddress() throws InterruptedException {
        onView(withId(R.id.email_address)).perform(typeText("incomplete"));
        onView(withId(R.id.next)).perform(click());
        Thread.sleep(3000);
    }

    @After
    public void stopServer() throws IOException {
        mockWebServer.shutdown();
        Intents.release();
    }

}