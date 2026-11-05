package app.hushmessenger.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * #129: words or a picture of your own at the top of the chat list, in place of Messenger's "messenger" wordmark. The
 * wordmark's component hands its drawable to {@link #dress} right after loading it, and scales whatever comes back to
 * its slot the way it scales the wordmark. So the words or picture keep the wordmark's own height, and inside it take
 * the height and place of its letters. Words take the wordmark's colors as drawn, its gradient and its dark mode tint
 * included. A picture keeps its own colors and takes the place of the words when both are
 * there. Off, Pause, safe mode, blank words with no picture and every failure keep the wordmark.
 */
public final class HomeHeader {
    static final String KEY = "home_header";
    /** The words as typed. {@link #clean} decides what shows. */
    static final String TEXT = "home_header_text";
    /** The most characters the header takes. */
    static final int MAX_LENGTH = 30;
    /** The copy of the picked picture, in Messenger's files. */
    static final String PICTURE = "hushmessenger-home-header.png";
    /** The words' height when the wordmark gives none, in dp. */
    static final int FALLBACK_HEIGHT_DP = 24;
    /** The widest the header gets, as a share of the screen's width. Wider words get smaller. */
    static final float MAX_WIDTH_SHARE = 0.55f;
    /** Messenger's blue, for words when the wordmark has no color to take. */
    static final int MESSENGER_BLUE = 0xff0866ff;

    /** The mark made for each wordmark, kept while its settings stay the same, so a render hands back the same drawable. */
    private static final Map<Drawable, Drawable> marks = new WeakHashMap<>();
    private static final Map<Drawable, String> signatures = new WeakHashMap<>();
    /** The picture read last, with the file's time and size when it was read. */
    private static Bitmap picture;
    private static long pictureModified = -1, pictureLength = -1;
    private static final java.util.concurrent.atomic.AtomicBoolean WARMING = new java.util.concurrent.atomic.AtomicBoolean();

    private HomeHeader() { }

    /** The wordmark Messenger just loaded, or your words or picture in its place. Never throws. */
    public static Drawable dress(Drawable wordmark) {
        if (wordmark == null) return null;
        try {
            if (!Settings.wouldUse(KEY)) return wordmark;
            Context context = Settings.appContext;
            File copy = context == null ? null : file(context);
            String signature = signature(copy);
            if (signature == null) return wordmark;
            synchronized (marks) {
                Drawable mark = marks.get(wordmark);
                if (mark == null || !signature.equals(signatures.get(wordmark))) {
                    mark = make(wordmark, copy, signature);
                    if (mark == null) return wordmark;
                    marks.put(wordmark, mark);
                    signatures.put(wordmark, signature);
                }
                Settings.activeAt.put(KEY, System.currentTimeMillis());
                return mark;
            }
        } catch (RuntimeException | OutOfMemoryError error) {
            Settings.hookFailed(KEY, "Can't put your home header in", error);
            return wordmark;
        }
    }

    /**
     * What the header shows, as one line for the restart row and the mark cache: the picture's time and size, else the
     * cleaned words. Null when there's neither, which keeps the wordmark.
     */
    static String signature(File copy) {
        if (copy != null && copy.isFile()) return "picture\n" + copy.lastModified() + "\n" + copy.length();
        SharedPreferences prefs = Settings.preferences;
        String words = clean(prefs == null ? null : prefs.getString(TEXT, ""));
        return words.isEmpty() ? null : "text\n" + words;
    }

    /** What a restart would show, for the restart row: nothing while the switch is off, paused or in safe mode. */
    static String inUse(Context context) {
        if (!Settings.wouldUse(KEY)) return "";
        String signature = signature(file(context));
        return signature == null ? "" : signature;
    }

    static File file(Context context) {
        return new File(context.getFilesDir(), PICTURE);
    }

    private static Drawable make(Drawable wordmark, File copy, String signature) {
        int height = wordmark.getIntrinsicHeight();
        if (height <= 0) height = Math.round(FALLBACK_HEIGHT_DP * Resources.getSystem().getDisplayMetrics().density);
        int maxWidth = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels * MAX_WIDTH_SHARE);
        if (signature.startsWith("picture\n")) {
            Bitmap shown = load(copy);
            // A copy that won't decode leaves the words, if there are any.
            if (shown != null) return new PictureMark(shown, height, maxWidth, look(wordmark));
            SharedPreferences prefs = Settings.preferences;
            String words = clean(prefs == null ? null : prefs.getString(TEXT, ""));
            return words.isEmpty() ? null : new TextMark(words, height, maxWidth, look(wordmark));
        }
        return new TextMark(signature.substring(5), height, maxWidth, look(wordmark));
    }

    /** The saved picture, read again only when the file changed. Null when it's gone or won't decode. Holds marks' lock. */
    private static Bitmap load(File copy) {
        long modified = copy.lastModified(), length = copy.length();
        if (picture != null && modified == pictureModified && length == pictureLength) return picture;
        Bitmap read = decode(copy);
        if (read == null) {
            android.util.Log.w("HushMessenger", "The home header picture won't decode, so the words or wordmark stay");
            return null;
        }
        picture = read;
        pictureModified = modified;
        pictureLength = length;
        return read;
    }

    /** The picture in [copy], or null. The bytes are read and the file closed first, so removing it never waits on a decoder. */
    private static Bitmap decode(File copy) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(copy.toPath());
            Bitmap read = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            return read == null || read.getWidth() <= 0 || read.getHeight() <= 0 ? null : read;
        } catch (java.io.IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Decodes the saved picture on a worker, so the chat list's first header doesn't wait on it. HostScreens starts it
     * once CrashGuard has run. A picture that won't decode is left for the render, which says so and keeps the words.
     */
    static void warmUp() {
        if (!WARMING.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                warm();
            } catch (RuntimeException | OutOfMemoryError | LinkageError error) {
                android.util.Log.w("HushMessenger", "home_header: couldn't read the picture ahead of time: " + error.getClass().getSimpleName());
            }
        }, "HushMessengerHeader");
        worker.setDaemon(true);
        worker.start();
    }

    /** The warm-up's read, only while the switch is in effect. A pick or a remove while it reads wins. */
    static void warm() {
        Context context = Settings.appContext;
        if (context == null || !Settings.wouldUse(KEY)) return;
        File copy = file(context);
        long modified = copy.lastModified(), length = copy.length();
        if (!copy.isFile()) return;
        Bitmap read = decode(copy);
        if (read == null) return;
        synchronized (marks) {
            if (picture != null || copy.lastModified() != modified || copy.length() != length) return;
            picture = read;
            pictureModified = modified;
            pictureLength = length;
        }
    }

    /** The copy changed or went. Marks made from it go too, so the next render reads the file again. */
    static void forget() {
        synchronized (marks) {
            picture = null;
            pictureModified = pictureLength = -1;
            marks.clear();
            signatures.clear();
        }
    }

    /**
     * The words the header shows for [typed]: one line, without control or invisible formatting characters, trimmed, and
     * at most {@link #MAX_LENGTH} characters. Empty keeps the wordmark.
     */
    static String clean(String typed) {
        if (typed == null) return "";
        StringBuilder line = new StringBuilder();
        boolean space = false;
        int count = 0;
        for (int at = 0; at < typed.length() && count < MAX_LENGTH; ) {
            int point = typed.codePointAt(at);
            at += Character.charCount(point);
            int type = Character.getType(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                space = line.length() > 0;
                continue;
            }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
                    || type == Character.PRIVATE_USE || type == Character.SURROGATE) continue;
            if (space) {
                // A space needs room for the character after it, so the words never end on one.
                if (count + 1 >= MAX_LENGTH) break;
                line.append(' ');
                count++;
                space = false;
            }
            line.appendCodePoint(point);
            count++;
        }
        return line.toString();
    }

    /**
     * How the wordmark looks where it's drawn: its colors, and the band of its height its letters fill. Messenger's
     * wordmark leaves room above and below its letters, so words or a picture sized to the whole drawable come out far
     * bigger than the wordmark ever looks.
     */
    static final class Look {
        static final Look WHOLE = new Look(new int[] {MESSENGER_BLUE}, 0f, 1f, 0f);
        final int[] colors;
        /** The top and bottom of the letters, as shares of the wordmark's height. */
        final float top, bottom;
        /** The space before the letters, in the wordmark's own pixels. */
        final float start;

        Look(int[] colors, float top, float bottom, float start) {
            this.colors = colors;
            this.top = top;
            this.bottom = bottom;
            this.start = start;
        }
    }

    /**
     * The wordmark's look, drawn small from a copy of it so the one on screen is never touched. The copy takes the
     * wordmark's color filter, the one Messenger tints it with in dark mode, so the colors are the ones on screen. Colors
     * come from its left, middle and right thirds: one when the thirds match, three for a gradient when they don't,
     * Messenger's blue when nothing in it is solid enough to read, which also leaves the whole height as the band.
     */
    static Look look(Drawable wordmark) {
        int width = wordmark.getIntrinsicWidth(), height = wordmark.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return Look.WHOLE;
        float scale = Math.min(1f, 48f / height);
        int w = Math.max(3, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
        Drawable.ConstantState state = wordmark.getConstantState();
        Drawable copy = state == null ? null : state.newDrawable();
        Drawable drawn = copy != null ? copy.mutate() : wordmark;
        if (drawn != wordmark) drawn.setColorFilter(wordmark.getColorFilter());
        Rect kept = new Rect(drawn.getBounds());
        Bitmap sample = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            drawn.setBounds(0, 0, w, h);
            drawn.draw(new Canvas(sample));
            int[] pixels = new int[w * h];
            sample.getPixels(pixels, 0, w, 0, 0, w, h);
            long[][] sums = new long[3][4];
            int top = h, bottom = -1, start = w;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = pixels[y * w + x];
                    if (Color.alpha(pixel) < 160) continue;
                    long[] third = sums[Math.min(2, x * 3 / w)];
                    third[0] += Color.red(pixel);
                    third[1] += Color.green(pixel);
                    third[2] += Color.blue(pixel);
                    third[3]++;
                    top = Math.min(top, y);
                    bottom = y;
                    start = Math.min(start, x);
                }
            }
            long[] all = new long[4];
            for (long[] third : sums) for (int i = 0; i < 4; i++) all[i] += third[i];
            if (all[3] == 0) return Look.WHOLE;
            int whole = average(all, all);
            int left = average(sums[0], all), middle = average(sums[1], all), right = average(sums[2], all);
            int[] colors = apart(left, right) || apart(left, middle) || apart(middle, right) ? new int[] {left, middle, right} : new int[] {whole};
            return new Look(colors, top / (float) h, (bottom + 1) / (float) h, start * width / (float) w);
        } finally {
            if (drawn == wordmark) wordmark.setBounds(kept);
            sample.recycle();
        }
    }

    /** The opaque average of [sum], or of [fallback] when [sum] counted no pixels. */
    private static int average(long[] sum, long[] fallback) {
        long[] used = sum[3] == 0 ? fallback : sum;
        return Color.rgb((int) (used[0] / used[3]), (int) (used[1] / used[3]), (int) (used[2] / used[3]));
    }

    /** Whether two colors differ enough to read as a gradient, not as the same color drawn smooth. */
    static boolean apart(int one, int two) {
        return Math.abs(Color.red(one) - Color.red(two)) + Math.abs(Color.green(one) - Color.green(two))
            + Math.abs(Color.blue(one) - Color.blue(two)) > 48;
    }

    /**
     * Where a mark sits in bounds the header gives it: its whole slot scaled to fit, centered. Both marks keep the
     * wordmark's height as their own, so the header scales them the way it scales the wordmark.
     */
    private static RectF fitted(Rect bounds, int width, int height, RectF into) {
        float scale = Math.min(bounds.width() / (float) width, bounds.height() / (float) height);
        float halfWidth = width * scale / 2f, halfHeight = height * scale / 2f;
        into.set(bounds.exactCenterX() - halfWidth, bounds.exactCenterY() - halfHeight,
            bounds.exactCenterX() + halfWidth, bounds.exactCenterY() + halfHeight);
        return into;
    }

    /**
     * The picture as tall as the wordmark's letters and where they sit, its shape kept, in a slot as tall as the
     * wordmark. One wider than {@link #MAX_WIDTH_SHARE} of the screen gets smaller. It keeps its own colors, so a filter
     * or tint meant for the wordmark doesn't reach it.
     */
    static final class PictureMark extends Drawable {
        final Bitmap picture;
        private final int width, height;
        /** The picture's place in the slot, before the header scales it. */
        final RectF shown;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF slot = new RectF(), place = new RectF();

        PictureMark(Bitmap picture, int height, int maxWidth, Look look) {
            this.picture = picture;
            this.height = height;
            float start = look.start;
            float high = Math.max(1f, (look.bottom - look.top) * height);
            float wide = picture.getWidth() * high / picture.getHeight();
            float room = Math.max(1f, maxWidth - start);
            if (maxWidth > 0 && wide > room) {
                high = high * room / wide;
                wide = room;
            }
            float middle = (look.top + look.bottom) / 2f * height;
            shown = new RectF(start, middle - high / 2f, start + wide, middle + high / 2f);
            width = Math.max(1, Math.round(start + wide));
        }

        @Override public void draw(Canvas canvas) {
            fitted(getBounds(), width, height, slot);
            float scale = slot.height() / height;
            place.set(slot.left + shown.left * scale, slot.top + shown.top * scale,
                slot.left + shown.right * scale, slot.top + shown.bottom * scale);
            canvas.drawBitmap(picture, null, place, paint);
        }

        @Override public int getIntrinsicWidth() { return width; }

        @Override public int getIntrinsicHeight() { return height; }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public int getAlpha() { return paint.getAlpha(); }

        /** A filter meant for the wordmark would paint the whole picture one color, so it's left out. */
        @Override public void setColorFilter(ColorFilter filter) { }

        @Override public void setTintList(android.content.res.ColorStateList tint) { }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * The words in bold sans as tall as the wordmark's letters and where they sit, in a slot as tall as the wordmark, in
     * its color or its gradient from start to end. The words and a capital H together set the size, so short words
     * without capitals or with descenders still fill the letters' height the way the wordmark does.
     */
    static final class TextMark extends Drawable {
        final String text;
        final int[] colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF slot = new RectF();
        private final int width, height;
        private final float start, x, y;
        private int alpha = 255;

        TextMark(String text, int height, int maxWidth, Look look) {
            this.text = text;
            this.height = height;
            this.colors = look.colors;
            start = look.start;
            float band = Math.max(1f, (look.bottom - look.top) * height);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setColor(colors[0]);
            paint.setTextSize(100f);
            Rect reach = reach();
            float size = reach.height() > 0 ? 100f * band / reach.height() : band;
            paint.setTextSize(size);
            Rect ink = new Rect();
            paint.getTextBounds(text, 0, text.length(), ink);
            float room = Math.max(1f, maxWidth - start);
            if (maxWidth > 0 && ink.width() > room) {
                paint.setTextSize(size * room / ink.width());
                paint.getTextBounds(text, 0, text.length(), ink);
            }
            Rect fitted = reach();
            width = Math.max(1, Math.round(start + ink.width()));
            x = start - ink.left;
            y = (look.top + look.bottom) / 2f * height - (fitted.top + fitted.bottom) / 2f;
            // The gradient runs the words' width, start to end, in the slot's own pixels.
            if (colors.length > 1) paint.setShader(new LinearGradient(start, 0, width, 0, colors, null, Shader.TileMode.CLAMP));
        }

        /** The words' ink with a capital H's, at the paint's size. */
        private Rect reach() {
            Rect union = new Rect();
            paint.getTextBounds(text, 0, text.length(), union);
            Rect capital = new Rect();
            paint.getTextBounds("H", 0, 1, capital);
            union.union(capital);
            return union;
        }

        @Override public void draw(Canvas canvas) {
            fitted(getBounds(), width, height, slot);
            int saved = canvas.save();
            canvas.translate(slot.left, slot.top);
            float scale = slot.height() / height;
            canvas.scale(scale, scale);
            canvas.drawText(text, x, y, paint);
            canvas.restoreToCount(saved);
        }

        @Override public int getIntrinsicWidth() { return width; }

        @Override public int getIntrinsicHeight() { return height; }

        @Override public void setAlpha(int alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public int getAlpha() { return alpha; }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public ColorFilter getColorFilter() { return paint.getColorFilter(); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
