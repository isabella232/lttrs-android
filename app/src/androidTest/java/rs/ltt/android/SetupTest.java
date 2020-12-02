package rs.ltt.android;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import okhttp3.mockwebserver.MockWebServer;
import rs.ltt.android.ui.activity.LttrsActivity;
import rs.ltt.android.ui.activity.SetupActivity;
import rs.ltt.jmap.client.api.HttpJmapApiClient;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mock.server.JmapDispatcher;
import rs.ltt.jmap.mock.server.MockMailServer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static rs.ltt.android.CustomMatchers.withError;

@RunWith(AndroidJUnit4.class)
public class SetupTest {

    @Rule
    public ActivityScenarioRule<SetupActivity> activityRule = new ActivityScenarioRule<>(SetupActivity.class);


    private final MockWebServer mockWebServer = new MockWebServer();
    private final MockMailServer mockMailServer = new MockMailServer(128) {
        @Override
        protected List<MailboxInfo> generateMailboxes() {
            return Arrays.asList(
                    new MailboxInfo(UUID.randomUUID().toString(), "Inbox", Role.INBOX),
                    new MailboxInfo(UUID.randomUUID().toString(), "Archive", null)
            );
        }
    };
    private final OkHttp3IdlingResource okHttp3IdlingResource = OkHttp3IdlingResource.create("OkHttp", HttpJmapApiClient.OK_HTTP_CLIENT);

    @Before
    public void startServer() throws IOException {
        mockWebServer.setDispatcher(mockMailServer);
        mockWebServer.start();
        Intents.init();
    }

    @Before
    public void registerIdlingResources() {
        IdlingRegistry.getInstance().register(okHttp3IdlingResource);
    }

    @Test
    public void setupAndRefresh() throws InterruptedException {
        onView(withId(R.id.email_address)).perform(typeText(JmapDispatcher.USERNAME));
        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.header)).check(matches(withText("Info required")));

        onView(withId(R.id.url)).perform(typeText(mockWebServer.url(JmapDispatcher.WELL_KNOWN_PATH).toString()));
        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.password)).perform(typeText(JmapDispatcher.PASSWORD));
        onView(withId(R.id.next)).perform(click());

        Thread.sleep(3000);

        intended(hasComponent(LttrsActivity.class.getName()));


        mockMailServer.generateEmailOnTop();

        onView(withId(R.id.swipe_to_refresh)).perform(swipeDown());

        Thread.sleep(3000);

        onView(withId(R.id.thread_list)).perform(RecyclerViewActions.actionOnItemAtPosition(0, swipeRight()));

        Thread.sleep(5000);

    }

    @Test
    public void setupAndSwipePreexistingArchive() throws InterruptedException {
        onView(withId(R.id.email_address)).perform(typeText(JmapDispatcher.USERNAME));
        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.header)).check(matches(withText("Info required")));

        onView(withId(R.id.url)).perform(typeText(mockWebServer.url(JmapDispatcher.WELL_KNOWN_PATH).toString()));
        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.password)).perform(typeText(JmapDispatcher.PASSWORD));
        onView(withId(R.id.next)).perform(click());

        Thread.sleep(2000);

        intended(hasComponent(LttrsActivity.class.getName()));

        onView(withId(R.id.thread_list)).perform(RecyclerViewActions.actionOnItemAtPosition(0, swipeRight()));

        Thread.sleep(5000);

    }

    @Test
    public void invalidManuallyEnteredEndpoint() {
        onView(withId(R.id.email_address)).perform(typeText(JmapDispatcher.USERNAME));
        onView(withId(R.id.next)).perform(click());
        onView(withId(R.id.url)).perform(typeText(mockWebServer.url("not-found").toString()));
        onView(withId(R.id.next)).perform(click());
        onView(withId(R.id.url_input_layout)).check(matches(withError(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.endpoint_not_found)
        )));


    }

    @Test
    public void invalidEmailAddress() {
        onView(withId(R.id.email_address)).perform(typeText("incomplete"));
        onView(withId(R.id.next)).perform(click());
        onView(withId(R.id.email_address_input_layout)).check(matches(withError(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.enter_a_valid_email_address)
        )));
    }

    @Test
    public void wrongPassword() {
        onView(withId(R.id.email_address)).perform(typeText(JmapDispatcher.USERNAME));
        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.url)).perform(typeText(mockWebServer.url(JmapDispatcher.WELL_KNOWN_PATH).toString()));

        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.password)).perform(typeText("wrong!"));

        onView(withId(R.id.next)).perform(click());

        onView(withId(R.id.password_input_layout)).check(matches(withError(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.wrong_password)
        )));
    }

    @After
    public void stopServer() throws IOException {
        mockWebServer.shutdown();
        Intents.release();
    }

    @After
    public void unregisterIdlingResources() {
        IdlingRegistry.getInstance().unregister(okHttp3IdlingResource);
    }

}