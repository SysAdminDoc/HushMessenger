package app.hushmessenger.extension;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Page follows your finger (#194): while a swipe between tabs is under way, the home screen's page and title move with
 * the finger and its tab bar stays put. A swipe that commits carries the page off that side before the tab changes, and
 * the new page slides in from the other side. Everything ends back in place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class TabFollowTest {
    private static final String HOME = "Lapp/hushmessenger/extension/TabSwipeTest$Home;";
    private static final String BAR = "Lapp/hushmessenger/extension/TabSwipeTest$Bar;";
    private static final String TAB = "Lapp/hushmessenger/extension/TabSwipeTest$Tab;";
    private static final String ROUTE = HOME + "->current()" + TAB + "|" + HOME + "->bar:" + BAR + "|"
        + BAR + "->tabs:Ljava/util/List;|" + HOME + "->select(" + TAB + ")V";

    private View header;
    private View page;
    private View layer;
    private View bar;
    /** The home screen the last frame was built for. */
    private TabSwipeTest.Home shown;
    /** Every place the page was put since the last swipe began, with the tab open at that moment. */
    private final List<Object[]> moves = new ArrayList<>();
    private boolean barMoved;

    @Test public void thePageAndTitleFollowTheFingerAndTheTabBarStays() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, true);

        down(frame);
        move(frame, 825);
        move(frame, 750);
        assertNotNull("The swipe took the page", frame.following());
        assertEquals(-150f, page.getTranslationX(), 0.01f);
        assertEquals(-150f, header.getTranslationX(), 0.01f);
        assertEquals(-150f, layer.getTranslationX(), 0.01f);
        assertEquals("The tab bar never moves", 0f, bar.getTranslationX(), 0.01f);

        // Chats is the first tab, so towards the start the page only gives a little.
        move(frame, 1000);
        assertEquals(100 * TabFollow.EDGE_PULL, page.getTranslationX(), 0.01f);
        up(frame, 1000);
        settle();
        assertNull(home.picked);
        assertEquals("And springs back", 0f, page.getTranslationX(), 0.01f);
        assertEquals(0f, header.getTranslationX(), 0.01f);
        assertTrue(Settings.lastActive(TabFollow.KEY) > 0);
    }

    @Test public void aSwipeThatCommitsCarriesThePageOffThenTheNextTabSlidesIn() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, true);

        swipe(frame, -300);
        assertNull("The tab waits for the page to leave", home.picked);
        assertTrue(frame.following().sliding());
        float[] went = watch(TabSwipeTest.Tab.PEOPLE);
        assertTrue("The page left to the left, the way the finger went", went[0] < -540);
        assertTrue("and the new one came in from the right, the side the next tab is on", went[3] > 540);
        assertEquals(0f, page.getTranslationX(), 0.01f);
        assertEquals(0f, header.getTranslationX(), 0.01f);
        assertFalse("The tab bar never moved", barMoved);

        // And back the other way, in from the left.
        swipe(frame, 300);
        went = watch(TabSwipeTest.Tab.INBOX);
        assertTrue(went[1] > 540);
        assertTrue(went[2] < -540);
        assertEquals(0f, page.getTranslationX(), 0.01f);
        assertFalse(barMoved);
    }

    /**
     * Lets the slides run and checks [tab] opened. Gives back how far left and right the page went while the old tab
     * still showed, then the same once [tab] had opened.
     */
    private float[] watch(TabSwipeTest.Tab tab) {
        settle();
        assertSame(tab, shown.picked);
        float[] went = new float[4];
        for (Object[] move : moves) {
            int at = move[1] == tab ? 2 : 0;
            went[at] = Math.min(went[at], (Float) move[0]);
            went[at + 1] = Math.max(went[at + 1], (Float) move[0]);
        }
        return went;
    }

    @Test public void aShortSwipeOrOneWithNoTabThatWaySpringsBack() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, true);

        swipe(frame, -40);
        settle();
        assertNull(home.picked);
        assertEquals(0f, page.getTranslationX(), 0.01f);

        // Far enough to commit, but there's no tab before the first one.
        swipe(frame, 300);
        settle();
        assertNull(home.picked);
        assertEquals(0f, page.getTranslationX(), 0.01f);

        // A swipe the screen takes away puts the page back too.
        down(frame);
        move(frame, 825);
        move(frame, 700);
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 30, MotionEvent.ACTION_CANCEL, 700, 500, 0));
        settle();
        assertNull(home.picked);
        assertEquals(0f, page.getTranslationX(), 0.01f);
    }

    @Test public void aNewTouchDuringTheSlideFinishesItWithTheTabChanged() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, true);

        swipe(frame, -300);
        assertNull(home.picked);
        down(frame);
        assertSame("The tab the swipe was going to", TabSwipeTest.Tab.PEOPLE, home.picked);
        assertNull(frame.following());
        assertEquals(0f, page.getTranslationX(), 0.01f);
        settle();
        assertEquals("Nothing slides in after it was finished", 0f, page.getTranslationX(), 0.01f);
        up(frame, 900);
    }

    @Test public void offOrOnAScreenWithNoBarAlongTheBottomTheSwipeJustChangesTheTab() {
        on(false);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, true);
        down(frame);
        move(frame, 825);
        move(frame, 700);
        assertNull(frame.following());
        assertEquals(0f, page.getTranslationX(), 0.01f);
        up(frame, 600);
        assertSame("At once, the way it always did", TabSwipeTest.Tab.PEOPLE, home.picked);

        on(true);
        TabSwipeTest.Home other = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame noBar = frame(other, false);
        down(noBar);
        move(noBar, 825);
        move(noBar, 700);
        assertNull("A layout Hush doesn't know isn't moved", noBar.following());
        assertEquals(0f, page.getTranslationX(), 0.01f);
        up(noBar, 600);
        assertSame(TabSwipeTest.Tab.PEOPLE, other.picked);

        // It rides on Swipe between tabs, so that going off stops it as well.
        Settings.preferences.edit().putBoolean(TabSwipe.KEY, false).commit();
        assertFalse(TabFollow.on());
    }

    private void on(boolean follow) {
        Settings.preferences.edit().putBoolean(TabSwipe.KEY, true).putBoolean(TabFollow.KEY, follow).commit();
    }

    /**
     * The home screen the way Messenger lays it out: a column of one view inside another, then the title along the
     * top, the page under it, an empty layer over the page, and the tab bar along the bottom.
     */
    private TabSwipe.SwipeFrame frame(TabSwipeTest.Home home, boolean withBar) {
        android.content.Context context = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(1080, 1920));
        FrameLayout level = new FrameLayout(context);
        root.addView(level, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header = new View(context);
        level.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 170, Gravity.TOP));
        shown = home;
        page = new View(context) {
            @Override public boolean onTouchEvent(MotionEvent event) { return true; }

            @Override public void setTranslationX(float x) {
                super.setTranslationX(x);
                moves.add(new Object[] {x, shown.picked});
            }
        };
        FrameLayout.LayoutParams under = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        under.topMargin = 170;
        level.addView(page, under);
        layer = new FrameLayout(context);
        level.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar = new View(context) {
            @Override public void setTranslationX(float x) {
                super.setTranslationX(x);
                if (x != 0) barMoved = true;
            }
        };
        if (withBar) level.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180, Gravity.BOTTOM));
        View wrapped = TabSwipe.wrap(root, home, ROUTE);
        assertTrue(wrapped instanceof TabSwipe.SwipeFrame);
        wrapped.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        wrapped.layout(0, 0, 1080, 1920);
        return (TabSwipe.SwipeFrame) wrapped;
    }

    private void swipe(TabSwipe.SwipeFrame frame, int sideways) {
        moves.clear();
        down(frame);
        for (int step = 1; step <= 4; step++) move(frame, 900 + sideways * step / 4f);
        up(frame, 900 + sideways);
    }

    private static void down(TabSwipe.SwipeFrame frame) {
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 900, 500, 0));
    }

    private static void move(TabSwipe.SwipeFrame frame, float x) {
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, x, 500, 0));
    }

    private static void up(TabSwipe.SwipeFrame frame, float x) {
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, x, 500, 0));
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    @Before public void reset() {
        Settings.initialize(RuntimeEnvironment.getApplication());
        Settings.preferences.edit().clear().commit();
        Settings.activeAt.clear();
        Settings.hookErrors.clear();
        CrashGuard.resetForTests();
        restart();
    }

    @After public void restart() {
        Settings.tabSwiping = null;
        Settings.bottomTabs.clear();
    }
}
