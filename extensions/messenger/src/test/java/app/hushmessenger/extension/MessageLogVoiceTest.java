package app.hushmessenger.extension;

import android.net.Uri;
import com.facebook.messaging.model.attachment.Attachment;
import com.facebook.messaging.model.attachment.AudioData;
import com.facebook.messaging.model.attachment.VideoData;
import com.facebook.messaging.model.messages.Message;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Keeping a voice message's clip: finding its address on the message, fetching it, and pointing the entry at it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class MessageLogVoiceTest {
    private static final String ADDRESS = "https://cdn.example.com/audio.mp4?oe=6A1B2C3D";
    private static final String THREAD = "thread-7";
    private static final String ID = "mid.100";
    /** Stands in for the fetch, so no test reaches a network. */
    private static final byte[] CLIP = "ftypM4A clip bytes".getBytes(StandardCharsets.UTF_8);

    private java.util.function.Function<String, byte[]> download;
    private java.util.function.LongConsumer retryLater;
    private final List<String> asked = new java.util.ArrayList<>();

    @Before public void reset() {
        Settings.initialize(RuntimeEnvironment.getApplication());
        Settings.preferences.edit().clear().commit();
        Settings.activeAt.clear();
        CrashGuard.resetForTests();
        CrashGuard.onProcessStart(RuntimeEnvironment.getApplication());
        retryLater = MessageLog.retryLater;
        MessageLog.retryLater = delay -> { };
        MessageLog.retry();
        MessageLog.forgetCaptures();
        Settings.hookErrors.clear();
        MessageLog.vault = new MessageLogTest.SoftwareVault();
        logFile().delete();
        MessageLogMedia.deleteAll(Settings.appContext);
        download = MessageLogVoice.download;
        asked.clear();
        MessageLogVoice.download = address -> {
            asked.add(address);
            return CLIP;
        };
        Settings.preferences.edit().putBoolean(MessageLog.KEY, true).putBoolean(MessageLog.KEEP_VOICE, true).commit();
    }

    @After public void tidy() {
        MessageLogVoice.download = download;
        MessageLog.retryLater = retryLater;
        logFile().delete();
        MessageLogMedia.deleteAll(Settings.appContext);
        MessageLog.vault = null;
    }

    private File logFile() {
        return new File(Settings.appContext.getFilesDir(), MessageLog.FILE);
    }

    private static Message voiceMessage() {
        return Message.with(Attachment.audio(new AudioData(Uri.parse(ADDRESS))));
    }

    @Test public void theAddressIsFoundThroughTheAttachmentsOwnTypes() {
        assertEquals(ADDRESS, MessageLogVoice.address(voiceMessage()));
    }

    @Test public void anotherKindOfAttachmentHasNoAddressToFind() {
        assertNull(MessageLogVoice.address(Message.with(Attachment.video(new VideoData(Uri.parse(ADDRESS))))));
        assertNull(MessageLogVoice.address(Message.with()));
        assertNull(MessageLogVoice.address(Message.with(Attachment.audio(new AudioData(null)))));
        assertNull(MessageLogVoice.address(new Object()));
        assertNull(MessageLogVoice.address(null));
    }

    /** Only the signed address the message came with. A file already on the phone is Messenger's to hand out, not ours. */
    @Test public void anAddressThatIsntTheMessagesOwnIsLeftAlone() {
        for (String address : new String[] {"file:///data/data/com.facebook.orca/clip.mp4", "content://media/external/audio/1",
            "http://cdn.example.com/audio.mp4"}) {
            assertNull(address, MessageLogVoice.address(Message.with(Attachment.audio(new AudioData(Uri.parse(address))))));
        }
    }

    @Test public void nothingIsReadWithTheSwitchOff() {
        Settings.preferences.edit().putBoolean(MessageLog.KEEP_VOICE, false).commit();
        assertNull(MessageLogVoice.address(voiceMessage()));
        Settings.preferences.edit().putBoolean(MessageLog.KEEP_VOICE, true).putBoolean(MessageLog.KEY, false).commit();
        assertNull(MessageLogVoice.address(voiceMessage()));
        Settings.preferences.edit().putBoolean(MessageLog.KEY, true).putBoolean("paused", true).commit();
        assertNull(MessageLogVoice.address(voiceMessage()));
    }

    /** The hook hands the message over before its text, and the address waits there for the log's own call. */
    @Test public void theHookHandsTheAddressOverWithTheMessage() {
        Settings.incomingMessageMedia(voiceMessage());
        assertEquals(ADDRESS, Settings.takeIncomingMessageVoice());
        assertNull(Settings.takeIncomingMessageVoice());
        Settings.incomingMessageMedia(voiceMessage());
        Settings.incomingMessageMedia(Message.with());
        assertNull("a message with no clip clears what the one before it left", Settings.takeIncomingMessageVoice());
    }

    /** The message stored, then its clip fetched and sealed where the log's screen plays it from. */
    @Test public void aClipIsKeptWithItsMessage() throws Exception {
        MessageLog.storeNow(null, THREAD, ID);
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        assertEquals(List.of(ADDRESS), asked);
        List<MessageLog.Entry> entries = MessageLog.entries();
        assertEquals(1, entries.size());
        MessageLog.Entry kept = entries.get(0);
        assertTrue(kept.attachment);
        assertEquals(MessageLogVoice.KIND, kept.mediaType);
        assertEquals(MessageLogVoice.name(THREAD, ID), kept.media);
        assertArrayEquals(CLIP, MessageLogMedia.open(Settings.appContext, kept.media));
        assertTrue(Settings.hookErrors.isEmpty());
    }

    /** The clip can land while the message is still waiting to be written, and then the one write carries both. */
    @Test public void aClipThatArrivesBeforeTheWriteGoesInWithIt() throws Exception {
        MessageLog.queue(null, THREAD, ID);
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        MessageLog.drain();
        List<MessageLog.Entry> entries = MessageLog.entries();
        assertEquals(1, entries.size());
        assertEquals(MessageLogVoice.KIND, entries.get(0).mediaType);
        assertArrayEquals(CLIP, MessageLogMedia.open(Settings.appContext, entries.get(0).media));
    }

    /** Messenger builds one message's notification twice, and the second fetch is the one that isn't needed. */
    @Test public void theSameClipIsFetchedOnce() {
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        assertEquals(List.of(ADDRESS), asked);
    }

    @Test public void aMessageStaysLoggedWhenItsClipCantBeFetched() {
        MessageLogVoice.download = address -> {
            asked.add(address);
            return null;
        };
        MessageLog.storeNow(null, THREAD, ID);
        assertEquals("", MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        List<MessageLog.Entry> entries = MessageLog.entries();
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).media.isEmpty());
        assertEquals("", entries.get(0).mediaType);
        assertFalse(new File(Settings.appContext.getFilesDir(), MessageLogMedia.DIR).isDirectory());
    }

    /** A picture off the notification is what the log already had for this message, so it keeps it. */
    @Test public void aPictureAlreadyKeptIsntReplaced() throws Exception {
        MessageLog.storeNow(null, THREAD, ID);
        String picture = MessageLog.digest(THREAD + '\u0000' + ID);
        File dir = new File(Settings.appContext.getFilesDir(), MessageLogMedia.DIR);
        dir.mkdirs();
        MessageLog.sealInto(dir, picture, "a picture".getBytes(StandardCharsets.UTF_8));
        assertTrue(MessageLog.attachNow(THREAD, ID, picture, ""));
        assertFalse(MessageLog.attachNow(THREAD, ID, MessageLogVoice.name(THREAD, ID), MessageLogVoice.KIND));
        List<MessageLog.Entry> entries = MessageLog.entries();
        assertEquals(picture, entries.get(0).media);
        assertEquals("", entries.get(0).mediaType);
    }

    /** A clip whose message isn't in the log has nothing to belong to, and the next write drops the file. */
    @Test public void aClipWithNoMessageIsntKept() {
        assertFalse(MessageLog.attachNow(THREAD, ID, MessageLogVoice.name(THREAD, ID), MessageLogVoice.KIND));
    }

    /** The kind is a column of its own, and a log written before it still reads the way it did. */
    @Test public void theKindSurvivesAWriteAndAnOlderLogStillReads() throws Exception {
        MessageLog.write(Settings.appContext, List.of(
            new MessageLog.Entry(1_700_000_000_000L, THREAD, "", "Ana", "", ID, false, true, "a".repeat(64), MessageLogVoice.KIND),
            new MessageLog.Entry(1_700_000_001_000L, THREAD, "", "Ana", "", "mid.101", false, true, "b".repeat(64))));
        List<MessageLog.Entry> back = MessageLog.read(logFile());
        assertEquals(2, back.size());
        assertEquals(MessageLogVoice.KIND, back.get(0).mediaType);
        assertEquals("a".repeat(64), back.get(0).media);
        assertEquals("", back.get(1).mediaType);
        assertEquals("b".repeat(64), back.get(1).media);
    }

    /** A kind the log doesn't know reads as a picture, which is what every entry before this was. */
    @Test public void anUnknownKindReadsAsAPicture() throws Exception {
        MessageLog.sealInto(Settings.appContext.getFilesDir(), MessageLog.FILE,
            ("1700000000000\t" + THREAD + "\t\tAna\t\t" + ID + "\t\t" + "a".repeat(64) + "\tsomething\n").getBytes(StandardCharsets.UTF_8));
        List<MessageLog.Entry> back = MessageLog.read(logFile());
        assertEquals(1, back.size());
        assertEquals("", back.get(0).mediaType);
    }

    /** An unsend marks the clip's message like any other, and the clip stays with it. */
    @Test public void anUnsentVoiceMessageKeepsItsClip() {
        MessageLog.storeNow(null, THREAD, ID);
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        assertTrue(MessageLog.markNow(ID, THREAD, ""));
        MessageLog.Entry marked = MessageLog.entries().get(0);
        assertTrue(marked.unsent);
        assertEquals(MessageLogVoice.KIND, marked.mediaType);
        assertEquals(MessageLogVoice.name(THREAD, ID), marked.media);
    }

    /** Clear takes the clips with the messages, and Undo puts both back. */
    @Test public void clearAndUndoTakeTheClipAlong() throws Exception {
        MessageLog.storeNow(null, THREAD, ID);
        assertEquals(MessageLogVoice.name(THREAD, ID), MessageLogVoice.keepNow(THREAD, ID, ADDRESS));
        MessageLog.Cleared cleared = MessageLog.clearForUndo();
        assertTrue(cleared.cleared);
        assertTrue(MessageLog.entries().isEmpty());
        assertTrue(MessageLog.restore(cleared));
        MessageLog.Entry back = MessageLog.entries().get(0);
        assertEquals(MessageLogVoice.KIND, back.mediaType);
        assertArrayEquals(CLIP, MessageLogMedia.open(Settings.appContext, back.media));
    }
}
