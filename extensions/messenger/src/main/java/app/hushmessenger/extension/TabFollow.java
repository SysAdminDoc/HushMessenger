package app.hushmessenger.extension;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The page following the finger while you swipe between tabs (#194). What the home screen shows over its tab bar moves
 * with the finger. Messenger keeps the page of every tab it has opened beside the open one, hidden, so when the tab the
 * finger heads for has one, that page comes along beside the moving one, and a swipe that commits leaves it in place as
 * the tab changes. A tab Messenger hasn't opened yet has no page until it's opened, so then the page is carried off, the
 * tab changes and the new page slides in from the other side. One that doesn't commit springs back. The tab bar never
 * moves.
 */
final class TabFollow {
    static final String KEY = "tab_swipe_follow";
    /** How much of the finger's travel the page follows towards a side with no tab left. */
    static final float EDGE_PULL = 0.3f;
    static final long OUT_MS = 120;
    static final long IN_MS = 220;
    static final long BACK_MS = 180;
    /** A child of the home screen counts as its tab bar when it's no taller than this share of the screen... */
    private static final float BAR_HEIGHT = 0.2f;
    /** ...and starts this far down it. */
    private static final float BAR_FROM = 0.7f;
    /** The view holding the tabs' pages is at least this share of the screen's height... */
    private static final float HOLDER_HEIGHT = 0.5f;
    /** ...and the page it shows at least this share of its own. */
    private static final float PAGE_HEIGHT = 0.75f;

    private TabFollow() {}

    /** Its own switch under Swipe between tabs, read on every swipe. */
    static boolean on() {
        return Settings.wouldUse(TabSwipe.KEY) && Settings.preferences != null && Settings.preferences.getBoolean(KEY, false);
    }

    /**
     * What moves: every shown view beside the tab bar, at the first level of the home screen that holds more than one.
     * Empty when that level has no bar along its bottom, so a layout Hush doesn't know keeps the plain swipe.
     */
    static List<View> pages(ViewGroup frame) {
        List<View> moving = new ArrayList<>();
        if (frame.getWidth() <= 0 || frame.getHeight() <= 0) return moving;
        ViewGroup level = frame;
        while (true) {
            List<View> shown = new ArrayList<>();
            for (int i = 0; i < level.getChildCount(); i++) {
                View child = level.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) shown.add(child);
            }
            if (shown.size() == 1 && shown.get(0) instanceof ViewGroup) {
                level = (ViewGroup) shown.get(0);
                continue;
            }
            if (shown.size() < 2) return moving;
            boolean bar = false;
            for (View child : shown) {
                if (isBar(frame, child)) bar = true;
                else moving.add(child);
            }
            if (!bar) moving.clear();
            return moving;
        }
    }

    /** Measured inside the frame, from the layout itself, so a page already moved sideways doesn't count. */
    private static boolean isBar(ViewGroup frame, View child) {
        Rect box = new Rect(0, 0, child.getWidth(), child.getHeight());
        frame.offsetDescendantRectToMyCoords(child, box);
        int height = frame.getHeight();
        return box.height() > 0 && box.height() <= height * BAR_HEIGHT && box.top >= height * BAR_FROM;
    }

    /**
     * The view among [moving] that holds the tabs' pages: about as tall as the screen, showing one page that fills it,
     * with the pages of tabs opened before kept hidden beside it. Null when there's none.
     */
    static ViewGroup holder(ViewGroup frame, List<View> moving) {
        for (View view : moving) {
            if (!(view instanceof ViewGroup) || view.getHeight() < frame.getHeight() * HOLDER_HEIGHT) continue;
            View shown = shownPage((ViewGroup) view);
            if (shown != null && shown.getHeight() >= view.getHeight() * PAGE_HEIGHT) return (ViewGroup) view;
        }
        return null;
    }

    /** The one page [holder] shows, or null when it shows none or several. */
    private static View shownPage(ViewGroup holder) {
        View shown = null;
        for (int i = 0; i < holder.getChildCount(); i++) {
            View child = holder.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (shown != null) return null;
            shown = child;
        }
        return shown;
    }

    /** The hidden page in [holder] that [built] says belongs to [tab], or null when Messenger hasn't built one. */
    private static View hiddenPage(ViewGroup holder, Map<View, Object> built, Object tab) {
        if (holder == null || tab == null) return null;
        for (int i = 0; i < holder.getChildCount(); i++) {
            View child = holder.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE && tab.equals(built.get(child))) return child;
        }
        return null;
    }

    /**
     * Has every Litho view under [view] look again at how much of it is on screen. Litho draws only the part of a view
     * it last saw on screen and looks again only when that view itself moves, never when a view around it does, so a
     * page Messenger built while the page was carried off stayed blank once it slid back (#194). Moving a view by
     * nothing is what makes Litho look, and does nothing to any other view.
     */
    static void remount(View view) {
        if (view.getVisibility() != View.VISIBLE || !(view instanceof ViewGroup)) return;
        view.offsetLeftAndRight(0);
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) remount(group.getChildAt(i));
    }

    /** One swipe's page: the views it moves, the page brought along beside them, and the slide carrying them, if any. */
    static final class Drag {
        private final ViewGroup frame;
        private List<View> moving;
        private ValueAnimator running;
        /** The tab change a slide off the screen makes when it gets there. */
        private Runnable change;
        /** Moves on whenever the swipe is ended early, so a slide posted before that never starts. */
        private int round;
        /** What holds the tabs' pages, and the page it showed as the swipe began. Null when there's none. */
        private final ViewGroup holder;
        private final View open;
        /** The pages Messenger has already built for the tabs before and after the open one, or null. */
        private final View before;
        private final View after;
        /** The page shown beside the moving ones now, and what it had before it was. */
        private View beside;
        private int besideVisibility;
        private float besideX;
        /** The view around the holder that stops clipping it while a page is beside it, and whether it clipped. */
        private ViewGroup unclipped;
        private boolean clipped;

        /**
         * [built] remembers which tab each of Messenger's pages belongs to, learnt as each one is the page on screen with
         * [opened] open. [previous] and [next] are the tabs either side, or null at an end.
         */
        Drag(ViewGroup frame, List<View> moving, Map<View, Object> built, Object opened, Object previous, Object next) {
            this.frame = frame;
            this.moving = moving;
            ViewGroup found = holder(frame, moving);
            holder = found;
            open = found == null ? null : shownPage(found);
            if (open != null && opened != null) built.put(open, opened);
            before = hiddenPage(found, built, previous);
            after = hiddenPage(found, built, next);
        }

        /** Follows the finger [dx] from where it went down, pulling less towards a side with no tab. */
        void follow(float dx, boolean tabThatWay) {
            stop();
            bring(dx < 0 ? after : dx > 0 ? before : null, dx < 0 ? frame.getWidth() : -frame.getWidth());
            place(tabThatWay ? dx : dx * EDGE_PULL);
            Settings.activeAt.put(KEY, System.currentTimeMillis());
        }

        /**
         * The swipe committed towards [direction] (1 is the next tab, the finger going left). The page leaves that
         * way and [opens] changes the tab. A page beside it is then the one Messenger shows, so it stays where it came to;
         * otherwise the new page comes in from the other side once Messenger has put it there.
         */
        void commit(int direction, Runnable opens) {
            float out = direction > 0 ? -frame.getWidth() : frame.getWidth();
            bring(direction > 0 ? after : before, -out);
            change = opens;
            slide(out, OUT_MS, () -> {
                Runnable now = change;
                change = null;
                if (now != null) now.run();
                int was = round;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (round != was) return;
                    if (landed()) {
                        // Messenger shows the page that came along now. It stays put and the rest slides in after it.
                        View came = beside;
                        beside = null;
                        came.setTranslationX(besideX);
                        clip();
                        moving = new ArrayList<>(moving);
                        moving.remove(holder);
                        holder.setTranslationX(0);
                        remount(holder);
                    } else {
                        let();
                        List<View> fresh = pages(frame);
                        if (!fresh.isEmpty()) {
                            for (View view : moving) if (!fresh.contains(view)) view.setTranslationX(0);
                            moving = fresh;
                        }
                    }
                    place(-out);
                    slide(0, IN_MS, null);
                });
            });
        }

        /** The swipe didn't commit, so the page springs back and the one beside it goes. */
        void back() {
            slide(0, BACK_MS, this::let);
        }

        /**
         * Ends it at once with every view back in place. With [changeTab] the tab changes too if a slide was on its way
         * to changing it, the way a new touch ends it. A home screen leaving the window keeps its tab.
         */
        void finish(boolean changeTab) {
            round++;
            Runnable now = change;
            change = null;
            stop();
            try {
                if (now != null && changeTab) now.run();
            } finally {
                let();
                place(0);
            }
        }

        /** Where the page is now. */
        float at() {
            return moving.isEmpty() ? 0 : moving.get(0).getTranslationX();
        }

        /** A slide running right now. Tests read it. */
        boolean sliding() {
            return running != null;
        }

        /** The page beside the moving ones now, or null. Tests read it. */
        View beside() {
            return beside;
        }

        private void place(float x) {
            for (View view : moving) view.setTranslationX(x);
            for (View view : moving) remount(view);
        }

        /** Shows [page], hidden until now, [offset] to the side of the open one, in place of any other beside it. */
        private void bring(View page, float offset) {
            if (page == beside) {
                if (page != null) page.setTranslationX(offset);
                return;
            }
            let();
            if (page == null || holder == null || page.getParent() != holder) return;
            beside = page;
            besideVisibility = page.getVisibility();
            besideX = page.getTranslationX();
            page.setTranslationX(offset);
            page.setVisibility(View.VISIBLE);
            ViewParent around = holder.getParent();
            if (around instanceof ViewGroup) {
                unclipped = (ViewGroup) around;
                clipped = unclipped.getClipChildren();
                // The page beside sits past the holder's edge, which its parent would otherwise cut off.
                unclipped.setClipChildren(false);
            }
        }

        /**
         * Hides the page beside the moving ones again, unless the tab it belongs to has opened since, which makes it
         * Messenger's page on screen.
         */
        private void let() {
            View page = beside;
            beside = null;
            if (page == null) return;
            page.setTranslationX(besideX);
            if (page.getVisibility() == View.VISIBLE && (open == null || open.getVisibility() == View.VISIBLE)) {
                page.setVisibility(besideVisibility);
            }
            clip();
        }

        /** Gives the holder's parent back the clipping it had. */
        private void clip() {
            if (unclipped != null) unclipped.setClipChildren(clipped);
            unclipped = null;
        }

        /** True once the tab change has shown the page beside the moving ones and hidden the one that was open. */
        private boolean landed() {
            return beside != null && holder != null && beside.getParent() == holder && beside.getVisibility() == View.VISIBLE
                && open != null && open.getVisibility() != View.VISIBLE;
        }

        private void stop() {
            ValueAnimator was = running;
            running = null;
            if (was != null) was.cancel();
        }

        private void slide(float target, long ms, Runnable done) {
            stop();
            ValueAnimator slide = ValueAnimator.ofFloat(at(), target);
            slide.setDuration(ms);
            slide.setInterpolator(new DecelerateInterpolator());
            slide.addUpdateListener(step -> place((Float) step.getAnimatedValue()));
            slide.addListener(new AnimatorListenerAdapter() {
                private boolean stopped;

                @Override public void onAnimationCancel(Animator animation) {
                    stopped = true;
                }

                @Override public void onAnimationEnd(Animator animation) {
                    if (running == animation) running = null;
                    if (stopped) return;
                    place(target);
                    if (done != null) done.run();
                }
            });
            running = slide;
            slide.start();
        }
    }
}
