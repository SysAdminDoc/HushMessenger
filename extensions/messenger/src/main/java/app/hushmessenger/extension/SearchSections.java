package app.hushmessenger.extension;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search's own sections and rows (#204, #207). Every search source hands its finished sections to one result
 * constructor, and every section is built by one section constructor, so the patch passes both lists through here on
 * their way in. A section says what it is with a type of Messenger's own ("NULL_STATE_RECENTLY_VIEWED_REELS"), and a row
 * with an item type ("CONNECTED_AI_BOT"), so both are read by name off whatever enum the object holds. The class and
 * field names change with every build, so nothing here names them.
 *
 * Opening a chat goes through one launcher, which the patch asks first: with Block Meta AI chats on, a chat whose thread
 * type is one of Meta AI's never opens, from search, a link or the chat list. A group with an AI bot in it still opens,
 * since people are in it too.
 *
 * Off, Pause, safe mode and every failure leave the lists exactly as Messenger made them. Public, since the calls come
 * from Messenger's own search classes.
 */
public final class SearchSections {
    static final String SUGGESTED = "search_suggested";
    static final String REELS = "search_reels";
    static final String AI_CHATS = "ai_chat_block";
    private static final String META_AI = "meta_ai";

    /** The section types each switch drops. */
    static final Set<String> SUGGESTED_SECTIONS = Set.of("NULL_STATE_SUGGESTIONS");
    static final Set<String> REELS_SECTIONS = Set.of("NULL_STATE_RECENTLY_VIEWED_REELS");
    static final Set<String> AI_SECTIONS = Set.of("NULL_STATE_AI_BOTS", "NULL_STATE_SEE_ALL_AI_BOTS", "NULL_STATE_AI_AGENT_PROMPTS");
    /** The row types Hide Meta AI drops from every section, the people found by a typed search included. */
    static final Set<String> AI_ROWS = Set.of("CONNECTED_AI_BOT", "UNCONNECTED_AI_BOT", "USER_GENERATED_CHARACTER_BOT",
        "AI_AGENT_PROMPT", "META_AI_VOICE", "META_AI_SNIPPET", "META_AI_SNIPPET_PROMPT");
    /** The thread types Block Meta AI chats keeps closed. Groups with an AI bot in them aren't here. */
    static final Set<String> AI_THREADS = Set.of("AI_BOT", "BIZ_AI_AGENT", "AI_PROFILE", "AI_AGENT");
    private static final String META_AI_THREAD = "META_AI_";
    private static final String THREAD_KEY = "com.facebook.messaging.model.threadkey.ThreadKey";

    /** Each class's fields that can hold an enum or a thread key, found once. */
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private SearchSections() { }

    /** A search result's sections, without the ones a switch hides. The list itself when nothing goes. */
    public static List<?> sections(List<?> sections) {
        try {
            if (sections == null || sections.isEmpty()) return sections;
            boolean ai = Settings.wouldUse(META_AI), suggested = Settings.wouldUse(SUGGESTED), reels = Settings.wouldUse(REELS);
            if (!ai && !suggested && !reels) return sections;
            List<Object> kept = null;
            for (int i = 0; i < sections.size(); i++) {
                Object section = sections.get(i);
                String hiddenBy = hiddenBy(enumNames(section), ai, suggested, reels);
                if (hiddenBy != null) {
                    Settings.enabled(hiddenBy);
                    if (kept == null) kept = new ArrayList<>(sections.subList(0, i));
                } else if (kept != null) {
                    kept.add(section);
                }
            }
            return kept == null ? sections : kept;
        } catch (Throwable error) {
            Settings.hookFailedPrivately(META_AI, "Can't look over search's sections", error);
            return sections;
        }
    }

    private static String hiddenBy(List<String> types, boolean ai, boolean suggested, boolean reels) {
        for (String type : types) {
            if (ai && AI_SECTIONS.contains(type)) return META_AI;
            if (suggested && SUGGESTED_SECTIONS.contains(type)) return SUGGESTED;
            if (reels && REELS_SECTIONS.contains(type)) return REELS;
        }
        return null;
    }

    /** A section's rows, without Meta AI's with Hide Meta AI on. [type] is the section's own type. */
    public static List<?> items(Object type, List<?> items) {
        try {
            if (items == null || items.isEmpty() || !Settings.wouldUse(META_AI)) return items;
            if (type instanceof Enum && AI_SECTIONS.contains(((Enum<?>) type).name())) {
                Settings.enabled(META_AI);
                return Collections.emptyList();
            }
            List<Object> kept = null;
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                boolean ai = aiRow(item);
                if (ai) {
                    Settings.enabled(META_AI);
                    if (kept == null) kept = new ArrayList<>(items.subList(0, i));
                } else if (kept != null) {
                    kept.add(item);
                }
            }
            return kept == null ? items : kept;
        } catch (Throwable error) {
            Settings.hookFailedPrivately(META_AI, "Can't look over search's rows", error);
            return items;
        }
    }

    static boolean aiRow(Object item) throws IllegalAccessException {
        for (String name : enumNames(item)) if (AI_ROWS.contains(name) || aiThread(name)) return true;
        Object key = threadKey(item);
        return key != null && aiThreadKey(key);
    }

    /**
     * Whether the launcher should treat [params] as handled without opening it: true for one of Meta AI's chats while
     * Block Meta AI chats is on, which also says so on screen.
     */
    public static boolean blocksChat(Object params) {
        try {
            if (params == null || !Settings.wouldUse(AI_CHATS)) return false;
            Object key = threadKey(params);
            if (key == null || !aiThreadKey(key)) return false;
            Settings.enabled(AI_CHATS);
            say("ai_chat_blocked");
            return true;
        } catch (Throwable error) {
            Settings.hookFailedPrivately(AI_CHATS, "Can't check the chat being opened", error);
            return false;
        }
    }

    private static boolean aiThreadKey(Object key) throws IllegalAccessException {
        for (String name : enumNames(key)) if (aiThread(name)) return true;
        return false;
    }

    static boolean aiThread(String name) {
        return AI_THREADS.contains(name) || name.startsWith(META_AI_THREAD) || name.startsWith("OPTIMISTIC_" + META_AI_THREAD);
    }

    /** The names of the enums [owner] holds in its own fields. */
    static List<String> enumNames(Object owner) throws IllegalAccessException {
        if (owner == null) return Collections.emptyList();
        List<String> names = new ArrayList<>(2);
        for (Field field : fields(owner.getClass())) {
            Object value = field.get(owner);
            if (value instanceof Enum) names.add(((Enum<?>) value).name());
        }
        return names;
    }

    /** The thread key [owner] holds in one of its own fields, or null. */
    private static Object threadKey(Object owner) throws IllegalAccessException {
        for (Field field : fields(owner.getClass())) {
            if (!THREAD_KEY.equals(field.getType().getName())) continue;
            Object value = field.get(owner);
            if (value != null) return value;
        }
        return null;
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> known = FIELDS.get(type);
        if (known != null) return known;
        List<Field> found = new ArrayList<>();
        for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
            for (Field field : at.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> kind = field.getType();
                if (!kind.isEnum() && !THREAD_KEY.equals(kind.getName()) && kind != Object.class && kind != Enum.class) continue;
                try {
                    field.setAccessible(true);
                    found.add(field);
                } catch (RuntimeException refused) {
                    // A field this process can't open just isn't read.
                }
            }
        }
        List<Field> fixed = Collections.unmodifiableList(found);
        FIELDS.put(type, fixed);
        return fixed;
    }

    static void resetForTests() {
        FIELDS.clear();
    }

    private static void say(String message) {
        Context context = Settings.appContext;
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, new SettingsText(context).get(message), Toast.LENGTH_SHORT).show());
    }
}
