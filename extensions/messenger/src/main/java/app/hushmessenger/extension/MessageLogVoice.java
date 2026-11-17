package app.hushmessenger.extension;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The voice clips the message log keeps. A voice message arrives with the address of its own audio on it, and Messenger
 * only fetches that when the clip is played, so an unsend before the first play takes the clip with it. With
 * {@link MessageLog#KEEP_VOICE} on, the clip is fetched as the message is logged and sealed next to the log's pictures,
 * so it stays playable in the log's screen afterwards.
 *
 * The address is read off the message object the notification hook hands over, by type rather than by field name: the
 * message carries attachments, an attachment carries its audio, and that audio has exactly one address. Those three type
 * names are the same in every build, while the field names are not, so nothing here has to be recorded per build.
 */
final class MessageLogVoice {
    private MessageLogVoice() {}

    private static final String ATTACHMENT = "com.facebook.messaging.model.attachment.Attachment";
    private static final String AUDIO_DATA = "com.facebook.messaging.model.attachment.AudioData";
    private static final String URI = "android.net.Uri";
    /** What an entry's media is when the kept file is a voice clip rather than a picture. */
    static final String KIND = "audio";
    /** A clip bigger than this isn't kept. A minute of Messenger's voice audio is a few hundred kilobytes. */
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int TIMEOUT_MS = 20_000;
    /** Clips waiting to be fetched. Past it the oldest waiting is dropped, and its message is still logged. */
    private static final int MAX_WAITING = 16;

    /**
     * The address field of each class that has been looked at, so the walk runs once per build and not once per message.
     * Weak on the class, since a key held strongly would pin Messenger's class loader.
     */
    private static final Map<Class<?>, Field[]> FIELDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Field[] NONE = new Field[0];

    private static final ThreadPoolExecutor FETCHER = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(MAX_WAITING), runnable -> {
            Thread thread = new Thread(runnable, "HushMessageLogVoice");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());

    /** Takes the address over from the download, so a test can answer without a network. */
    static java.util.function.Function<String, byte[]> download = MessageLogVoice::get;

    /**
     * The address of [message]'s voice clip, or null when it carries none, the switch is off, or the message object isn't
     * shaped the way this build's was. Runs on Messenger's own thread, so it only reads fields and never fetches.
     */
    static String address(Object message) {
        if (message == null || !kept()) return null;
        try {
            for (Field field : fields(message.getClass())) {
                Object attachments = field.get(message);
                if (!(attachments instanceof List)) continue;
                for (Object attachment : (List<?>) attachments) {
                    String address = addressOf(attachment);
                    if (address != null) return address;
                }
            }
        } catch (Throwable unreadable) {
            // The message is still logged, without its clip.
            Settings.hookFailedPrivately(MessageLog.KEY, "Can't read a voice message's address", unreadable);
        }
        return null;
    }

    /** Whether voice clips are being kept: the log itself on and usable, and the switch under it on. */
    static boolean kept() {
        android.content.SharedPreferences prefs = Settings.preferences;
        return prefs != null && prefs.getBoolean(MessageLog.KEEP_VOICE, false) && Settings.wouldUse(MessageLog.KEY);
    }

    /** The list fields of [type], which is where attachments sit. Every list is looked at, since none is named here. */
    private static Field[] fields(Class<?> type) {
        Field[] known = FIELDS.get(type);
        if (known != null) return known;
        java.util.ArrayList<Field> lists = new java.util.ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
            } catch (RuntimeException sealed) {
                continue;
            }
            lists.add(field);
        }
        Field[] found = lists.isEmpty() ? NONE : lists.toArray(NONE);
        FIELDS.put(type, found);
        return found;
    }

    /** [attachment]'s audio address, or null when it isn't an attachment, carries no audio, or the audio has no address. */
    private static String addressOf(Object attachment) throws Exception {
        if (attachment == null || !ATTACHMENT.equals(attachment.getClass().getName())) return null;
        Object audio = only(attachment, AUDIO_DATA);
        if (audio == null) return null;
        Object address = only(audio, URI);
        if (address == null) return null;
        String url = address.toString();
        // Only the signed address the message came with. Anything else, a file on the phone included, is left alone.
        return url.startsWith("https://") ? url : null;
    }

    /** The value of [holder]'s one field declared as [type], or null when it has none, several, or that one is null. */
    private static Object only(Object holder, String type) throws Exception {
        Object found = null;
        for (Field field : holder.getClass().getDeclaredFields()) {
            if (!type.equals(field.getType().getName())) continue;
            if (found != null) return null;
            field.setAccessible(true);
            found = field.get(holder);
            if (found == null) return null;
        }
        return found;
    }

    /**
     * Fetches the clip at [address] for message [id] in [thread] and seals it, then points the message's entry at it. The
     * fetch waits its turn behind any other, and a failure leaves the message logged without its clip.
     */
    static void keep(String thread, String id, String address) {
        if (thread == null || id == null || id.isEmpty() || address == null) return;
        try {
            FETCHER.execute(() -> keepNow(thread, id, address));
        } catch (RuntimeException rejected) {
            Settings.hookFailedPrivately(MessageLog.KEY, "Can't set up a voice clip's download", rejected);
        }
    }

    /** {@link #keep} on the calling thread. Returns the name it sealed the clip under, or "". */
    static String keepNow(String thread, String id, String address) {
        Context context = Settings.appContext;
        if (context == null || !kept()) return "";
        String name = name(thread, id);
        try {
            // Messenger builds one message's notification twice, from the push and again from the sync, and the second
            // reaches here with the same name. One fetch is enough.
            File kept = MessageLogMedia.file(context, name);
            if (kept != null && kept.isFile()) return name;
            byte[] clip = download.apply(address);
            if (clip == null || clip.length == 0) return "";
            File dir = new File(context.getFilesDir(), MessageLogMedia.DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't make " + MessageLogMedia.DIR);
            MessageLog.sealInto(dir, name, clip);
            // An entry that already has a picture keeps it, and the clip just sealed is dropped by the next tidy.
            MessageLog.attachNow(thread, id, name, KIND);
            return name;
        } catch (Throwable failure) {
            Settings.hookFailedPrivately(MessageLog.KEY, "Can't keep a voice message in the log", failure);
            return "";
        }
    }

    /** A clip's name, kept apart from the name a picture for the same message would take. */
    static String name(String thread, String id) {
        return MessageLog.digest(thread + '\u0000' + id + '\u0000' + KIND);
    }

    /** The bytes at [address], or null when the answer isn't one clip this size. */
    private static byte[] get(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            if (connection.getContentLength() > MAX_BYTES) return null;
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toByteArray();
            }
        } catch (Exception unreachable) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
