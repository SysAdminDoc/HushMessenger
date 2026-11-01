package app.hushmessenger.extension;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * A Save row in the menu you get by holding a voice message (#123). The patch hands every new menu's rows here, so a menu
 * for a voice message gets one more, and it asks here first about every tap. Saving runs Messenger's own voice download,
 * which finds the cached file or fetches it, and keeps the file where {@link VoiceMessages} keeps its copies. Android 9
 * lets no app write to shared folders without the storage permission, so the row doesn't show there.
 */
public final class SaveVoiceMenu {
    private SaveVoiceMenu() {}

    static final String KEY = "save_voice_menu";
    /** Spells "Hush". Messenger's own rows use small ids, so this one can't be mistaken for any of them. */
    static final int ROW_ID = 0x48757368;
    /** After Reply, Forward and Set a reminder, which take the bar along the bottom, so this one sits under More. */
    static final int POSITION = 3;
    private static final String ROW_MESSAGE = "rowMessage";

    /** What this Messenger's own classes do. Tests swap it. */
    interface Menu {
        Object row(CharSequence title, int id);
        int rowId(Object item);
        boolean isVoice(Object message);
        Object session(Object tap);
        Uri fetch(Object session, Object message);
    }

    static Menu menu = new Menu() {
        @Override public Object row(CharSequence title, int id) { return SaveVoiceMenu.row(title, id); }
        @Override public int rowId(Object item) { return SaveVoiceMenu.rowId(item); }
        @Override public boolean isVoice(Object message) { return SaveVoiceMenu.isVoice(message); }
        @Override public Object session(Object tap) { return SaveVoiceMenu.session(tap); }
        @Override public Uri fetch(Object session, Object message) { return SaveVoiceMenu.fetch(session, message); }
    };

    /** A new menu row with this title and id, using Messenger's Save video icon. The patch writes its body. */
    static Object row(CharSequence title, int id) { return null; }

    /** The menu row's id. The patch writes its body. */
    static int rowId(Object item) { return 0; }

    /** Whether Messenger classes this message as a voice message. The patch writes its body. */
    static boolean isVoice(Object message) { return false; }

    /** The account of the chat this tap belongs to. The patch writes its body. */
    static Object session(Object tap) { return null; }

    /** The voice message's file, from Messenger's cache or its download. Runs on a worker. The patch writes its body. */
    static Uri fetch(Object session, Object message) { return null; }

    /**
     * The rows a new menu is built from. For a voice message that's a copy with the Save row added. The builder's own list
     * stays as it was, and any other menu gets it back untouched.
     */
    public static List<Object> rows(List<Object> rows, Object extra) {
        try {
            if (rows == null || Build.VERSION.SDK_INT < 29 || !Settings.enabled(KEY)) return rows;
            Object message = message(extra);
            if (message == null || !menu.isVoice(message)) return rows;
            Object row = menu.row(label(), ROW_ID);
            if (row == null) return rows;
            List<Object> added = new ArrayList<>(rows);
            added.add(Math.min(POSITION, added.size()), row);
            return added;
        } catch (Throwable error) {
            Settings.hookFailed(KEY, "Can't add the voice message Save row", error);
            return rows;
        }
    }

    /** Messenger's menu asks this first. False for its own rows, which it then handles as before. True for ours. */
    public static boolean clicked(Object tap, Object item, Object extra) {
        try {
            if (item == null || menu.rowId(item) != ROW_ID) return false;
        } catch (Throwable error) {
            Settings.hookFailed(KEY, "Can't read a chat menu row", error);
            return false;
        }
        Context context = Settings.appContext;
        if (context == null) return true;
        try {
            Object message = message(extra), session = menu.session(tap);
            if (message == null || session == null) throw new IllegalStateException("The menu has no message or account");
            VoiceMessages.copies.execute(() -> save(context, session, message));
        } catch (Throwable error) {
            // Includes a full queue of copies, where nothing happened and the person should hear so.
            Settings.hookFailedPrivately(KEY, "Can't start saving a voice message", error);
            done(context, false);
        }
        return true;
    }

    /** Fetches the file and keeps it, then says how that went. A voice message that's already saved counts as saved. */
    static void save(Context context, Object session, Object message) {
        boolean kept = false;
        try {
            Uri audio = menu.fetch(session, message);
            // The download's own answer. Asked for one voice message, it gives back a file on the phone or nothing.
            kept = VoiceMessages.onPhone(audio) && VoiceMessages.keep(context, audio, true);
        } catch (Throwable error) {
            Settings.hookFailedPrivately(KEY, "Can't save a voice message", error);
        }
        done(context, kept);
    }

    private static void done(Context context, boolean kept) {
        new Handler(Looper.getMainLooper()).post(() -> {
            SettingsText text = new SettingsText(context);
            Toast.makeText(context, kept ? text.get("save_voice_menu_saved", VoiceMessages.SharedAudio.folder()) : text.get("save_voice_menu_failed"),
                Toast.LENGTH_SHORT).show();
        });
    }

    /** The message in the menu's Bundle, or null when this menu isn't for a message. */
    @SuppressWarnings("deprecation")
    private static Object message(Object extra) {
        return extra instanceof Bundle ? ((Bundle) extra).getParcelable(ROW_MESSAGE) : null;
    }

    private static String label() {
        Context context = Settings.appContext;
        return context == null ? SettingsText.ENGLISH.get("save_voice_menu_row") : new SettingsText(context).get("save_voice_menu_row");
    }
}
