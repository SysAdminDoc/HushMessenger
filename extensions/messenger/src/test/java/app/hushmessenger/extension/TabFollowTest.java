package app.hushmessenger.extension;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
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
    /** With the floating tab bar: the view around the holder, the holder of the tabs' pages, and each tab's page. */
    private FrameLayout level;
    private FrameLayout holder;
    private final java.util.Map<TabSwipeTest.Tab, FrameLayout> tabPages = new java.util.EnumMap<>(TabSwipeTest.Tab.class);
    /** Every place the holder was put, with the tab open at that moment. */
    private final List<Object[]> holderMoves = new ArrayList<>();

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

    /**
     * With the floating tab bar every tab's page sits in one holder, and Messenger builds a tab's page the first time it
     * opens, here while the old page is off the side of the screen. Litho draws only what it saw on screen the last time
     * its own view moved, so the new page came in blank until something moved it (#194).
     */
    @Test public void aPageMessengerBuildsWhileTheOldOneIsOffTheScreenIsDrawnOnceItSlidesIn() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = floating(home, TabSwipeTest.Tab.INBOX);

        down(frame);
        move(frame, 825);
        move(frame, 600);
        assertNull("People has no page yet, so nothing comes along beside", frame.following().beside());
        assertTrue(level.getClipChildren());
        up(frame, 600);
        settle();
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);
        FrameLayout people = tabPages.get(TabSwipeTest.Tab.PEOPLE);
        assertNotNull("Messenger built the page while the old one was carried off", people);
        assertEquals(0f, onScreenX(people), 0.01f);
        assertEquals("and it was drawn where it came to rest, all of it", 0f, litho(people).seenAt, 0.01f);
        assertEquals(0f, header.getTranslationX(), 0.01f);
        assertTrue(level.getClipChildren());
    }

    /**
     * A tab opened before keeps its page in the holder, hidden. That page comes along beside the moving one while the
     * finger drags, and once the swipe commits it's the page Messenger shows, so it stays where it came to.
     */
    @Test public void aTabOpenedBeforeComesAlongBesideThePageAndStaysWhenTheSwipeCommits() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = visitedPeople(home);
        FrameLayout people = tabPages.get(TabSwipeTest.Tab.PEOPLE);
        FrameLayout chats = tabPages.get(TabSwipeTest.Tab.INBOX);

        down(frame);
        move(frame, 825);
        move(frame, 600);
        assertSame(people, frame.following().beside());
        assertEquals(View.VISIBLE, people.getVisibility());
        assertEquals("People sits just right of the page, the side it opens from", 780f, onScreenX(people), 0.01f);
        assertFalse("and isn't cut off at the holder's edge", level.getClipChildren());
        assertTrue("Litho was told it's partly on screen", litho(people).seenAt > 0);

        holderMoves.clear();
        up(frame, 600);
        settle();
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);
        assertEquals(View.GONE, chats.getVisibility());
        assertEquals(View.VISIBLE, people.getVisibility());
        assertEquals(0f, people.getTranslationX(), 0.01f);
        assertEquals(0f, holder.getTranslationX(), 0.01f);
        for (Object[] move : holderMoves) {
            if (move[1] == TabSwipeTest.Tab.PEOPLE) assertFalse("The page that came along never jumped back off the screen", (Float) move[0] > 0);
        }
        assertEquals(0f, litho(people).seenAt, 0.01f);
        assertEquals("The title slid in after it", 0f, header.getTranslationX(), 0.01f);
        assertTrue("The holder's parent clips again", level.getClipChildren());
        assertNull(frame.following().beside());
    }

    @Test public void aSwipeThatSpringsBackOrTurnsAroundHidesThePageBesideAgain() {
        on(true);
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = visitedPeople(home);
        FrameLayout people = tabPages.get(TabSwipeTest.Tab.PEOPLE);
        FrameLayout chats = tabPages.get(TabSwipeTest.Tab.INBOX);

        down(frame);
        move(frame, 825);
        move(frame, 750);
        assertSame(people, frame.following().beside());
        up(frame, 750);
        settle();
        assertSame("Too short to change the tab", TabSwipeTest.Tab.INBOX, home.open);
        assertEquals(View.GONE, people.getVisibility());
        assertEquals(0f, people.getTranslationX(), 0.01f);
        assertEquals(View.VISIBLE, chats.getVisibility());
        assertEquals(0f, holder.getTranslationX(), 0.01f);
        assertTrue(level.getClipChildren());

        // Back the other way, where Chats has no tab before it, People goes again at once.
        down(frame);
        move(frame, 825);
        move(frame, 750);
        assertSame(people, frame.following().beside());
        move(frame, 1000);
        assertNull(frame.following().beside());
        assertEquals(View.GONE, people.getVisibility());
        assertTrue(level.getClipChildren());
        up(frame, 1000);
        settle();
        assertSame(TabSwipeTest.Tab.INBOX, home.open);
        assertEquals(View.VISIBLE, chats.getVisibility());
    }

    /** A floating home screen that has opened People once and come back to Chats, so People's page is kept, hidden. */
    private TabSwipe.SwipeFrame visitedPeople(TabSwipeTest.Home home) {
        home.open = TabSwipeTest.Tab.PEOPLE;
        TabSwipe.SwipeFrame frame = floating(home, TabSwipeTest.Tab.INBOX, TabSwipeTest.Tab.PEOPLE);
        swipe(frame, 300);
        settle();
        assertSame(TabSwipeTest.Tab.INBOX, home.open);
        assertEquals(View.GONE, tabPages.get(TabSwipeTest.Tab.PEOPLE).getVisibility());
        assertEquals(View.VISIBLE, tabPages.get(TabSwipeTest.Tab.INBOX).getVisibility());
        assertEquals(0f, holder.getTranslationX(), 0.01f);
        return frame;
    }

    /**
     * The home screen with the floating tab bar: the holder of the tabs' pages, with a page for each of [built] and only
     * the open tab's shown, an empty layer, the title along the top and the bar along the bottom. Opening a tab shows its
     * page, building it the first time, a moment later, the way a fragment change runs.
     */
    private TabSwipe.SwipeFrame floating(TabSwipeTest.Home home, TabSwipeTest.Tab... built) {
        android.content.Context context = RuntimeEnvironment.getApplication();
        shown = home;
        tabPages.clear();
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(1080, 1920));
        level = new FrameLayout(context);
        root.addView(level, fill());
        holder = new FrameLayout(context) {
            @Override public void setTranslationX(float x) {
                super.setTranslationX(x);
                holderMoves.add(new Object[] {x, shown.open});
            }
        };
        level.addView(holder, fill());
        for (TabSwipeTest.Tab tab : built) {
            FrameLayout page = tabPage(context);
            tabPages.put(tab, page);
            holder.addView(page, fill());
        }
        level.addView(new FrameLayout(context), fill());
        header = new View(context);
        level.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 170, Gravity.TOP));
        bar = new View(context);
        level.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180, Gravity.BOTTOM));
        View wrapped = TabSwipe.wrap(root, home, ROUTE);
        assertTrue(wrapped instanceof TabSwipe.SwipeFrame);
        TabSwipe.SwipeFrame frame = (TabSwipe.SwipeFrame) wrapped;
        layOut(frame);
        // Hidden pages keep where they were last laid out, the way a fragment's view does once it's hidden.
        for (java.util.Map.Entry<TabSwipeTest.Tab, FrameLayout> page : tabPages.entrySet()) {
            page.getValue().setVisibility(page.getKey() == home.open ? View.VISIBLE : View.GONE);
        }
        home.selected = tab -> new Handler(Looper.getMainLooper()).post(() -> {
            FrameLayout page = tabPages.get(tab);
            if (page == null) {
                page = tabPage(context);
                tabPages.put(tab, page);
                holder.addView(page, fill());
            }
            for (FrameLayout each : tabPages.values()) each.setVisibility(each == page ? View.VISIBLE : View.GONE);
            layOut(frame);
        });
        return frame;
    }

    private static FrameLayout tabPage(android.content.Context context) {
        FrameLayout page = new FrameLayout(context);
        page.addView(new Litho(context), fill());
        return page;
    }

    private static Litho litho(FrameLayout page) {
        return (Litho) page.getChildAt(0);
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static void layOut(View frame) {
        frame.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, 1080, 1920);
    }

    /** Where [view] sits across the screen, counting every view around it that moved. */
    private static float onScreenX(View view) {
        float x = 0;
        for (View at = view; at != null; at = at.getParent() instanceof View ? (View) at.getParent() : null) {
            x += at.getLeft() + at.getTranslationX();
        }
        return x;
    }

    /**
     * Stands in for a Litho view: it looks at where it is on screen only when it moves itself, the way Litho's mounting
     * view does, and moving by nothing counts.
     */
    static final class Litho extends FrameLayout {
        /** Where it was across the screen the last time it looked, or NaN if it never did. */
        float seenAt = Float.NaN;

        Litho(android.content.Context context) {
            super(context);
        }

        @Override public void offsetLeftAndRight(int offset) {
            super.offsetLeftAndRight(offset);
            seenAt = onScreenX(this);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            return true;
        }
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
