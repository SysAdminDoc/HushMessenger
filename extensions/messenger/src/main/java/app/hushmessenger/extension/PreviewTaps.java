package app.hushmessenger.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Keep taps in the chat preview (#247). The preview a long press on a chat opens lays a see-through layer over the
 * chat, and any tap on it opens the chat, the chat's own jump-to-the-bottom arrow included. With this switch on the
 * layer lets every touch through to the chat, and an Open chat row at the top of the menu under it opens the chat.
 */
public final class PreviewTaps {
    static final String KEY = "chat_preview_taps";
    /** Our chat menu row's id. Messenger hands it back on the tap, so it can't be one of Messenger's own. */
    static final int ROW_ID = 0x48550004;
    private static final String ROW_NAME = "hush_open_chat";
    /** The newest preview's tap layer, whose screen Open chat starts the chat from. */
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

    /**
     * Opens the chat whose menu was tapped, on Messenger's chat screen for its key. A click on the preview's layer opened
     * nothing from here on the S22: closing the preview clears the preview's own way into the chat, and the preview
     * closes with its menu. The preview's screen starts the chat when it's still there, so Back comes back to the list.
     */
    static void tapped(ChatHiding.Route target, Object menu) {
        try {
            Context context = Settings.appContext;
            if (context == null) return;
            Object chat = target != null && target.menu.isInstance(menu) ? target.menuSummary.get(menu) : null;
            Object key = chat != null && target.summary.isInstance(chat) ? target.key.get(chat) : null;
            if (key == null) {
                Toast.makeText(context, new SettingsText(context).get("chat_preview_taps_gone"), Toast.LENGTH_SHORT).show();
                return;
            }
            View held = layer.get();
            Activity screen = held == null ? null : MenuBlur.activity(held.getContext());
            if (screen != null && screen.isFinishing()) screen = null;
            Intent open = LocalSearchActivity.chatScreen(screen != null ? screen : context, key);
            if (screen == null) open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            (screen != null ? screen : context).startActivity(open);
        } catch (Throwable error) {
            Settings.hookFailed(KEY, "Can't open the chat", error);
        }
    }
}
