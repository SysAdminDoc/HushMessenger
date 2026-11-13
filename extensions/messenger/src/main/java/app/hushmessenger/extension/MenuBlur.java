package app.hushmessenger.extension;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Predicate;

/**
 * Blur behind menus (#162). Every sheet, menu and dialog Messenger opens from a DialogFragment comes through shown()
 * right after it's shown, the long-press menu on a chat included. When its window dims the screen behind it, the switch
 * blurs that screen instead, the way an iPhone does, and keeps a light dim so the sheet still stands out.
 * Window blur needs Android 12, and the phone can turn it off (battery saver, or a phone that doesn't do it), so then
 * the window keeps Messenger's own dim. Public, since the call comes from androidx's DialogFragment in another package.
 */
public final class MenuBlur {
    static final String KEY = "menu_blur";
    /** How far the screen behind blurs, in dp. */
    static final int RADIUS_DP = 24;
    /** The most dim left over the blur. */
    static final float DIM = 0.25f;

    /** Whether this window can blur what's behind it right now. Tests swap it. */
    static Predicate<Window> available = MenuBlur::crossWindowBlur;

    private MenuBlur() {}

    /** Blurs the screen behind a dialog that dims it. Anything else, and Off, Pause or safe mode, leave the window as it is. */
    public static void shown(Dialog dialog) {
        try {
            if (dialog == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !dialog.isShowing()) return;
            Window window = dialog.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams attributes = window.getAttributes();
            if ((attributes.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0 || attributes.dimAmount <= 0f) return;
            if (!Settings.enabled(KEY) || !available.test(window)) return;
            blur(window, attributes);
        } catch (Throwable error) {
            Settings.hookFailedPrivately(KEY, "Couldn't blur behind a menu", error);
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static void blur(Window window, WindowManager.LayoutParams attributes) {
        WindowManager.LayoutParams changed = new WindowManager.LayoutParams();
        changed.copyFrom(attributes);
        changed.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        changed.setBlurBehindRadius(Math.round(RADIUS_DP * window.getContext().getResources().getDisplayMetrics().density));
        changed.dimAmount = Math.min(attributes.dimAmount, DIM);
        window.setAttributes(changed);
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static boolean crossWindowBlur(Window window) {
        WindowManager manager = window.getWindowManager();
        return manager != null && manager.isCrossWindowBlurEnabled();
    }
}
