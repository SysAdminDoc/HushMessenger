package app.hushmessenger.extension;

import android.content.Context;
import android.view.View;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Keep taps in the chat preview (#247). The preview a long press on a chat opens lays a see-through layer over the
 * chat, and any tap on it opens the chat, the chat's own jump-to-the-bottom arrow included. With this switch on the
 * layer lets every touch through to the chat, and an Open chat row at the top of the menu under it opens the chat
 * through the layer's own tap, the same way Messenger does.
 */
public final class PreviewTaps {
    static final String KEY = "chat_preview_taps";
    /** Our chat menu row's id. Messenger hands it back on the tap, so it can't be one of Messenger's own. */
    static final int ROW_ID = 0x48550004;
    private static final String ROW_NAME = "hush_open_chat";
    /** The newest preview's tap layer, which a tap on Open chat clicks. */
    private static WeakReference<View> layer = new WeakReference<>(null);
    /** Whether Messenger showed its preview since it started, with or without Preview chats on long press. */
    private static boolean previewSeen;

    private PreviewTaps() { }

    static void resetForTests() {
        layer = new WeakReference<>(null);
        previewSeen = false;
    }

    /**
     * ChatPreviewSize.tapLayer hands over each preview's tap layer as it's built. With the switch on, the layer stops
     * taking touches, so they reach the chat under it, and TalkBack skips it for the Open chat row. True when it did.
     */
    static boolean adopt(View tapLayer) {
        layer = new WeakReference<>(tapLayer);
        previewSeen = true;
        if (!Settings.enabled(KEY)) return false;
        tapLayer.setClickable(false);
        tapLayer.setLongClickable(false);
        tapLayer.setFocusable(false);
        tapLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return true;
    }

    /** Adds Open chat to the top of the menu a long press opens, when the switch is on and that menu sits in a preview. */
    static void menuRow(ChatHiding.Route target, ArrayList<Object> rows) {
        try {
            Context context = Settings.appContext;
            if (context == null || !Settings.wouldUse(KEY) || !(previewSeen || Settings.wouldUse("chat_preview"))) return;
            Object row = target.row(ROW_ID, ROW_NAME, new SettingsText(context).get("chat_preview_taps_open"), "MESSAGE", "APP_MESSENGER");
            if (row == null) return;
            // Messenger pads the list with empty slots up to the count it expects, so one goes to make room at the top.
            int slot = rows.indexOf(null);
            if (slot >= 0) rows.remove(slot);
            rows.add(0, row);
            Settings.enabled(KEY);
        } catch (Throwable error) {
            Settings.hookFailed(KEY, "Can't add Open chat to the chat menu", error);
        }
    }

    /** Opens the chat whose preview is showing, through the layer Messenger gave the job. */
    static void tapped() {
        try {
            View held = layer.get();
            if (held != null && held.isAttachedToWindow()) {
                held.performClick();
                return;
            }
            Context context = Settings.appContext;
            if (context != null) Toast.makeText(context, new SettingsText(context).get("chat_preview_taps_gone"), Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Settings.hookFailed(KEY, "Can't open the chat", error);
        }
    }
}
