package app.hushmessenger.extension;

import android.os.Looper;
import com.facebook.messaging.model.threadkey.ThreadKey;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class SearchSectionsTest {
    /** Messenger's section types, by the names its enum gives them. */
    enum Type { NULL_STATE_SUGGESTIONS, NULL_STATE_RECENTLY_VIEWED_REELS, NULL_STATE_AI_BOTS, NULL_STATE_SEE_ALL_AI_BOTS, NULL_STATE_CONTACTS }

    enum Item { CONTACT, PAGE, CONNECTED_AI_BOT, USER_GENERATED_CHARACTER_BOT, META_AI_VOICE }

    enum ThreadType { ONE_TO_ONE, GROUP, AI_BOT, BIZ_AI_AGENT, META_AI_VOICE_THREAD, OPTIMISTIC_META_AI_THREAD }

    /** A section as the section constructor leaves it: its type in an obfuscated field, next to things that aren't. */
    static final class Section {
        final String A02 = "header";
        final Type A00;
        Section(Type type) { A00 = type; }
        @Override public String toString() { return A00.name(); }
    }

    static final class Row {
        final Object A00;
        final ThreadKey A01;
        Row(Item item, ThreadKey key) { A00 = item; A01 = key; }
    }

    static final class Params {
        final int A00 = 3;
        final ThreadKey A01;
        Params(ThreadKey key) { A01 = key; }
    }

    private static ThreadKey key(ThreadType type) {
        return new ThreadKey(type, 42L);
    }

    private static final Section SUGGESTED = new Section(Type.NULL_STATE_SUGGESTIONS);
    private static final Section REELS = new Section(Type.NULL_STATE_RECENTLY_VIEWED_REELS);
    private static final Section AI = new Section(Type.NULL_STATE_AI_BOTS);
    private static final Section SEE_ALL_AI = new Section(Type.NULL_STATE_SEE_ALL_AI_BOTS);
    private static final Section CONTACTS = new Section(Type.NULL_STATE_CONTACTS);
    private static final List<Section> ALL = Arrays.asList(SUGGESTED, CONTACTS, REELS, AI, SEE_ALL_AI);

    @Before public void reset() {
        Settings.initialize(RuntimeEnvironment.getApplication());
        Settings.preferences.edit().clear().commit();
        Settings.activeAt.clear();
        CrashGuard.resetForTests();
        SearchSections.resetForTests();
    }

    private static void turnOn(String... keys) {
        android.content.SharedPreferences.Editor edit = Settings.preferences.edit();
        for (String key : keys) edit.putBoolean(key, true);
        edit.commit();
    }

    @Test public void withEverySwitchOffTheListsComeBackAsTheyWere() {
        assertSame(ALL, SearchSections.sections(ALL));
        List<Row> rows = Arrays.asList(new Row(Item.CONNECTED_AI_BOT, null), new Row(Item.CONTACT, null));
        assertSame(rows, SearchSections.items(Type.NULL_STATE_AI_BOTS, rows));
        assertFalse(SearchSections.blocksChat(new Params(key(ThreadType.AI_BOT))));
        assertTrue(Settings.activeAt.isEmpty());
    }

    @Test public void eachSwitchDropsOnlyItsOwnSection() {
        turnOn(SearchSections.SUGGESTED);
        assertEquals(Arrays.asList(CONTACTS, REELS, AI, SEE_ALL_AI), SearchSections.sections(ALL));
        assertTrue(Settings.lastActive(SearchSections.SUGGESTED) > 0);
        assertEquals(0, Settings.lastActive(SearchSections.REELS));

        reset();
        turnOn(SearchSections.REELS);
        assertEquals(Arrays.asList(SUGGESTED, CONTACTS, AI, SEE_ALL_AI), SearchSections.sections(ALL));
        assertTrue(Settings.lastActive(SearchSections.REELS) > 0);

        reset();
        turnOn("meta_ai");
        assertEquals("Hide Meta AI drops Meta AI's own sections", Arrays.asList(SUGGESTED, CONTACTS, REELS), SearchSections.sections(ALL));
        assertTrue(Settings.lastActive("meta_ai") > 0);

        reset();
        turnOn(SearchSections.SUGGESTED, SearchSections.REELS, "meta_ai");
        assertEquals(Collections.singletonList(CONTACTS), SearchSections.sections(ALL));
    }

    @Test public void aListWithNothingToDropIsTheSameList() {
        turnOn(SearchSections.SUGGESTED, SearchSections.REELS, "meta_ai");
        List<Section> plain = Arrays.asList(CONTACTS, CONTACTS);
        assertSame(plain, SearchSections.sections(plain));
        assertNull(SearchSections.sections(null));
        assertEquals(0, Settings.lastActive(SearchSections.SUGGESTED));
    }

    @Test public void hideMetaAiDropsItsRowsFromEverySection() {
        turnOn("meta_ai");
        Row contact = new Row(Item.CONTACT, key(ThreadType.ONE_TO_ONE));
        Row page = new Row(Item.PAGE, null);
        Row group = new Row(Item.CONTACT, key(ThreadType.GROUP));
        List<Row> rows = Arrays.asList(contact, new Row(Item.CONNECTED_AI_BOT, null), page,
            new Row(Item.USER_GENERATED_CHARACTER_BOT, null), new Row(Item.META_AI_VOICE, null),
            new Row(Item.CONTACT, key(ThreadType.AI_BOT)), new Row(Item.CONTACT, key(ThreadType.META_AI_VOICE_THREAD)), group);
        assertEquals("Typed-search people stay, a group with a bot in it too", Arrays.asList(contact, page, group),
            SearchSections.items(Type.NULL_STATE_CONTACTS, rows));
        assertEquals(Collections.emptyList(), SearchSections.items(Type.NULL_STATE_AI_BOTS, Collections.singletonList(contact)));
        assertTrue(Settings.lastActive("meta_ai") > 0);
    }

    @Test public void searchSwitchesAloneLeaveTheRowsAlone() {
        turnOn(SearchSections.SUGGESTED, SearchSections.REELS);
        List<Row> rows = Collections.singletonList(new Row(Item.CONNECTED_AI_BOT, null));
        assertSame(rows, SearchSections.items(Type.NULL_STATE_CONTACTS, rows));
    }

    @Test public void blockMetaAiChatsKeepsOnlyAiChatsClosedAndSaysSo() {
        turnOn(SearchSections.AI_CHATS);
        for (ThreadType type : new ThreadType[] {ThreadType.AI_BOT, ThreadType.BIZ_AI_AGENT, ThreadType.META_AI_VOICE_THREAD, ThreadType.OPTIMISTIC_META_AI_THREAD})
            assertTrue(type.name(), SearchSections.blocksChat(new Params(key(type))));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Meta AI chats are blocked in HushMessenger settings.", ShadowToast.getTextOfLatestToast());
        assertTrue(Settings.lastActive(SearchSections.AI_CHATS) > 0);

        Settings.activeAt.clear();
        assertFalse(SearchSections.blocksChat(new Params(key(ThreadType.ONE_TO_ONE))));
        assertFalse("A group with an AI in it still opens", SearchSections.blocksChat(new Params(key(ThreadType.GROUP))));
        assertFalse(SearchSections.blocksChat(new Params(null)));
        assertFalse(SearchSections.blocksChat(null));
        assertEquals(0, Settings.lastActive(SearchSections.AI_CHATS));
    }

    @Test public void hideMetaAiAloneStillOpensItsChats() {
        turnOn("meta_ai");
        assertFalse(SearchSections.blocksChat(new Params(key(ThreadType.AI_BOT))));
    }

    @Test public void pauseAndSafeModeOpenEveryChatAndKeepEverySection() {
        for (String state : new String[] {"paused", "safe"}) {
            Settings.initialize(RuntimeEnvironment.getApplication());
            Settings.preferences.edit().clear().putBoolean(SearchSections.AI_CHATS, true).putBoolean(SearchSections.SUGGESTED, true)
                .putBoolean("meta_ai", true).putBoolean("paused", state.equals("paused")).putBoolean("safe_mode", state.equals("safe")).commit();
            CrashGuard.resetForTests();
            CrashGuard.onProcessStart(RuntimeEnvironment.getApplication());
            Settings.activeAt.clear();
            assertFalse(state, SearchSections.blocksChat(new Params(key(ThreadType.AI_BOT))));
            assertSame(state, ALL, SearchSections.sections(ALL));
            assertTrue(state, Settings.activeAt.isEmpty());
        }
    }

    @Test public void aListThatFailsComesBackUntouched() {
        turnOn(SearchSections.SUGGESTED, "meta_ai");
        List<Object> broken = new AbstractList<Object>() {
            @Override public Object get(int index) { throw new IllegalStateException("gone"); }
            @Override public int size() { return 2; }
        };
        assertSame(broken, SearchSections.sections(broken));
        assertSame(broken, SearchSections.items(Type.NULL_STATE_CONTACTS, broken));
    }

    @Test public void theSwitchesSitWithSearchAndMetaAiAndStartOff() {
        String[][] expected = {
            {SearchSections.SUGGESTED, "Hide Suggested in search", "inbox"},
            {SearchSections.REELS, "Hide recent reels in search", "inbox"},
            {SearchSections.AI_CHATS, "Block Meta AI chats", "navigation"},
        };
        for (String[] want : expected) {
            String[] spec = null;
            for (String[] control : SettingsActivity.CONTROLS) if (control[0].equals(want[0])) spec = control;
            assertNotNull(want[0], spec);
            assertEquals(want[1], spec[1]);
            assertEquals(want[2], spec[3]);
            assertTrue(want[0], Settings.installed.contains(want[0]));
            assertFalse(want[0], Settings.preferences.getBoolean(want[0], false));
        }
    }
}
