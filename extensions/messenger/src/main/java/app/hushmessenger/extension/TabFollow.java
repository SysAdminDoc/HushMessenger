package app.hushmessenger.extension;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import java.util.ArrayList;
import java.util.List;

/**
 * The page following the finger while you swipe between tabs (#194). Messenger swaps one tab's page in and out of the
 * same fragment, so there's no next page to pull in beside it. What the home screen shows over its tab bar moves with
 * the finger instead. A swipe that commits carries it off that side, the tab changes and the new page slides in from the
 * other side, and one that doesn't springs back. The tab bar never moves.
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

    /** One swipe's page: the views it moves, and the slide that's carrying them, if one is. */
    static final class Drag {
        private final ViewGroup frame;
        private List<View> moving;
        private ValueAnimator running;
        /** The tab change a slide off the screen makes when it gets there. */
        private Runnable change;
        /** Moves on whenever the swipe is ended early, so a slide posted before that never starts. */
        private int round;

        Drag(ViewGroup frame, List<View> moving) {
            this.frame = frame;
            this.moving = moving;
        }

        /** Follows the finger [dx] from where it went down, pulling less towards a side with no tab. */
        void follow(float dx, boolean tabThatWay) {
            stop();
            place(tabThatWay ? dx : dx * EDGE_PULL);
            Settings.activeAt.put(KEY, System.currentTimeMillis());
        }

        /**
         * The swipe committed towards [direction] (1 is the next tab, the finger going left). The page leaves that
         * way, [opens] changes the tab, and the new page comes in from the other side once Messenger has put it there.
         */
        void commit(int direction, Runnable opens) {
            float out = direction > 0 ? -frame.getWidth() : frame.getWidth();
            change = opens;
            slide(out, OUT_MS, () -> {
                Runnable now = change;
                change = null;
                if (now != null) now.run();
                int was = round;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (round != was) return;
                    List<View> fresh = pages(frame);
                    if (!fresh.isEmpty()) {
                        for (View view : moving) if (!fresh.contains(view)) view.setTranslationX(0);
                        moving = fresh;
                    }
                    place(-out);
                    slide(0, IN_MS, null);
                });
            });
        }

        /** The swipe didn't commit, so the page springs back. */
        void back() {
            slide(0, BACK_MS, null);
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

        private void place(float x) {
            for (View view : moving) view.setTranslationX(x);
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
