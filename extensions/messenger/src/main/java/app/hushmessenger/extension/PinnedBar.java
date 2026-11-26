package app.hushmessenger.extension;

/**
 * Messenger's own pinned message bar under a chat's name (#213). Messenger gathers a chat's banners in one place and
 * only adds the pinned messages one for some chats, after asking the chat's thread key. The patch passes that answer
 * through here, and with Show pinned messages on it comes back yes, so every chat gets the bar Messenger already draws
 * for the chats it picked. A chat shows one banner at a time, so the pinned bar's rank comes through here too.
 *
 * Off, Pause, safe mode and every failure hand the answer back as it came. A chat Messenger already shows the bar in
 * keeps it without counting as a use. Public, since the call comes from Messenger's own banner code.
 */
public final class PinnedBar {
    static final String KEY = "pinned_bar";

    private PinnedBar() { }

    /** Whether the chat's banners take the pinned messages bar. */
    public static boolean show(boolean stock) {
        try {
            return stock || Settings.enabled(KEY);
        } catch (Throwable error) {
            Settings.hookFailedPrivately(KEY, "Can't look over the pinned message bar", error);
            return stock;
        }
    }

    /**
     * The rank Messenger gives a banner when a chat has more than one to show under its name, lowest first. A chat
     * shows one at a time, and the pinned messages bar ranks behind most of the others (the reminder about deleted
     * messages took the spot in one-to-one chats). With the switch on the pinned bar goes ahead of all of them.
     * {@code pinned} is non-zero for the pinned messages banner. Every other banner keeps Messenger's rank.
     */
    public static int bannerRank(int pinned, int rank) {
        if (pinned == 0) return rank;
        try {
            return Settings.enabled(KEY) ? Integer.MIN_VALUE : rank;
        } catch (Throwable error) {
            Settings.hookFailedPrivately(KEY, "Can't put the pinned message bar first", error);
            return rank;
        }
    }
}
