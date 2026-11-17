package app.hushmessenger.extension;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaDataSource;
import android.media.MediaPlayer;
import java.io.IOException;

/**
 * Plays a voice clip the message log kept ({@link MessageLogVoice}). The clip is opened from its sealed file into memory
 * and played from there, so nothing is written out in the clear. One clip plays at a time: starting another stops the one
 * before it, and so does closing the log's sheet.
 */
final class MessageLogClip {
    private MessageLogClip() {}

    /** The clip playing now and the name it was kept under, or null when none is. Guarded by the class. */
    private static MediaPlayer playing;
    private static String playingName;
    /** Told when playback stops, whether it ended on its own or something else started. Guarded by the class. */
    private static Runnable onStop;

    /** Whether the clip kept under [name] is the one playing. */
    static synchronized boolean playing(String name) {
        return playing != null && name != null && name.equals(playingName);
    }

    /**
     * Opens the clip kept under [name] and plays it, after stopping whatever was playing. Runs on the caller's thread,
     * never the main one, since it opens the file. [stopped] is called on that same thread when the clip ends, and the
     * answer is whether it started at all.
     */
    static boolean play(Context context, String name, Runnable stopped) {
        byte[] clip;
        try {
            clip = MessageLogMedia.open(context, name);
        } catch (Exception unreadable) {
            Settings.hookFailedPrivately(MessageLog.KEY, "Can't open a voice message in the log", unreadable);
            return false;
        }
        if (clip == null || clip.length == 0) return false;
        stop();
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(new Bytes(clip));
            player.setOnCompletionListener(done -> stop());
            player.setOnErrorListener((failed, what, extra) -> {
                stop();
                return true;
            });
            player.prepare();
            synchronized (MessageLogClip.class) {
                playing = player;
                playingName = name;
                onStop = stopped;
            }
            player.start();
            return true;
        } catch (Throwable unplayable) {
            player.release();
            Settings.hookFailedPrivately(MessageLog.KEY, "Can't play a voice message in the log", unplayable);
            return false;
        }
    }

    /** Stops whatever is playing and tells whoever started it. Safe to call when nothing is. */
    static void stop() {
        MediaPlayer player;
        Runnable told;
        synchronized (MessageLogClip.class) {
            player = playing;
            told = onStop;
            playing = null;
            playingName = null;
            onStop = null;
        }
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException alreadyStopped) {
                // Nothing to stop.
            }
            player.release();
        }
        if (told != null) told.run();
    }

    /** The clip in memory, as a source the player can read. */
    private static final class Bytes extends MediaDataSource {
        private final byte[] clip;
        Bytes(byte[] clip) {
            this.clip = clip;
        }
        @Override public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            if (position < 0 || position >= clip.length) return -1;
            int read = (int) Math.min(size, clip.length - position);
            System.arraycopy(clip, (int) position, buffer, offset, read);
            return read;
        }
        @Override public long getSize() {
            return clip.length;
        }
        @Override public void close() {}
    }
}
