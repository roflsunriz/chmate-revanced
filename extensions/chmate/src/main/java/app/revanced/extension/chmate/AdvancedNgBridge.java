package app.revanced.extension.chmate;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdvancedNgBridge {
    private static final int NG_WORD = 16;
    private static final int TRANSPARENT = 4096;
    private static final ConcurrentHashMap<Class<?>, Accessor> ACCESSORS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = new ThreadLocal<>();
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final Map<String, Set<Integer>> TRANSPARENT_MATCHES =
            Collections.synchronizedMap(new LinkedHashMap<String, Set<Integer>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Set<Integer>> eldest) {
                    return size() > 8;
                }
            });
    private static volatile List<AdvancedNgRule> cachedRules;

    private AdvancedNgBridge() { }

    public static void enter(Object response, Object evaluator) {
        if (response == null || evaluator == null || rules().isEmpty()) return;
        ArrayDeque<Frame> stack = FRAMES.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            FRAMES.set(stack);
        }
        Frame oldest = stack.peekFirst();
        if (stack.size() >= 64 || (oldest != null &&
                System.currentTimeMillis() - oldest.createdAt > 30_000)) {
            stack.clear();
        }
        stack.addLast(new Frame(response, evaluator));
    }

    public static int finish(int originalFlags, String fieldConfiguration, boolean active) {
        ArrayDeque<Frame> stack = FRAMES.get();
        if (stack == null || stack.isEmpty()) return originalFlags;
        Frame frame = stack.removeLast();
        if (stack.isEmpty()) FRAMES.remove();
        return active ? apply(originalFlags, frame.response, fieldConfiguration, frame.evaluator)
                : originalFlags;
    }

    public static int apply(int originalFlags, Object response, String fieldConfiguration,
            Object evaluator) {
        if (response == null || evaluator == null) return originalFlags;
        List<AdvancedNgRule> rules = rules();
        if (rules.isEmpty()) return originalFlags;
        try {
            Accessor accessor = ACCESSORS.get(response.getClass());
            if (accessor == null) {
                accessor = new Accessor(response.getClass(), fieldConfiguration);
                ACCESSORS.put(response.getClass(), accessor);
            }
            List<?> thread = findThread(evaluator, response.getClass());
            String title = thread == null ? "" : threadTitle(thread);
            String url = RuntimeState.currentThreadUrl();
            Needs needs = new Needs(rules);
            AdvancedNgMatcher.Context context = context(accessor, response, thread, title, url, needs);
            boolean transparentMatch = false;
            for (AdvancedNgRule rule : rules) {
                if (!rule.enabled) continue;
                if (AdvancedNgMatcher.matches(rule, context) ||
                        (rule.chain && chainMatches(rule, accessor, response, thread,
                                title, url, needs, new HashSet<>(), 0))) {
                    originalFlags |= NG_WORD;
                    if (rule.transparent) {
                        originalFlags |= TRANSPARENT;
                        transparentMatch = true;
                    }
                }
            }
            if (url != null && !url.isEmpty()) {
                synchronized (TRANSPARENT_MATCHES) {
                    Set<Integer> numbers = TRANSPARENT_MATCHES.get(url);
                    if (transparentMatch) {
                        if (numbers == null) {
                            numbers = new HashSet<>();
                            TRANSPARENT_MATCHES.put(url, numbers);
                        }
                        numbers.add(context.number);
                    } else if (numbers != null) {
                        numbers.remove(context.number);
                    }
                }
            }
        } catch (Throwable error) {
            if (REPORTED_FAILURE.compareAndSet(false, true)) {
                Log.w("ChMateReVancedNg", "Advanced NG evaluation failed", error);
            }
        }
        return originalFlags;
    }

    static synchronized List<AdvancedNgRule> loadForEditor(Context context) throws JSONException {
        String saved = UserAgentOverride.preferences(context)
                .getString(AdvancedNgRule.STORAGE_KEY, "");
        return AdvancedNgRule.deserialize(saved);
    }

    static synchronized boolean saveFromEditor(Context context, List<AdvancedNgRule> rules)
            throws JSONException {
        if (rules.size() > 500) throw new JSONException("Too many advanced NG rules");
        String json = AdvancedNgRule.serialize(rules);
        boolean saved = UserAgentOverride.preferences(context).edit()
                .putString(AdvancedNgRule.STORAGE_KEY, json).commit();
        if (saved) {
            cachedRules = new ArrayList<>(rules);
            synchronized (TRANSPARENT_MATCHES) { TRANSPARENT_MATCHES.clear(); }
        }
        return saved;
    }

    static boolean isTransparentMatch(String threadUrl, int number) {
        if (threadUrl == null) return false;
        synchronized (TRANSPARENT_MATCHES) {
            Set<Integer> numbers = TRANSPARENT_MATCHES.get(threadUrl);
            return numbers != null && numbers.contains(number);
        }
    }

    static boolean hasTransparentRules() {
        for (AdvancedNgRule rule : rules()) {
            if (rule.enabled && rule.transparent) return true;
        }
        return false;
    }

    private static List<AdvancedNgRule> rules() {
        List<AdvancedNgRule> existing = cachedRules;
        if (existing != null) return existing;
        Context context = RuntimeState.getContext();
        if (context == null) return new ArrayList<>();
        synchronized (AdvancedNgBridge.class) {
            if (cachedRules == null) {
                try {
                    cachedRules = loadForEditor(context);
                } catch (JSONException ignored) {
                    cachedRules = new ArrayList<>();
                }
            }
            return cachedRules;
        }
    }

    private static AdvancedNgMatcher.Context context(Accessor accessor, Object response,
            List<?> thread, String title, String url, Needs needs) throws IllegalAccessException {
        AdvancedNgMatcher.Context result = new AdvancedNgMatcher.Context();
        result.number = accessor.number.getInt(response);
        result.name = string(accessor.name.get(response));
        result.mail = string(accessor.mail.get(response));
        result.date = string(accessor.date.get(response));
        result.body = string(accessor.body.get(response));
        result.id = string(accessor.id.get(response));
        result.threadTitle = title;
        result.threadUrl = url == null ? "" : url;
        result.timestampMillis = AdvancedNgMatcher.parseTimestamp(result.date);
        int[] references = (int[]) accessor.anchors.get(response);
        result.replyCount = references == null ? 0 : references.length;
        if (thread != null) {
            result.threadResCount = thread.size();
            if (needs.idCount || needs.repliedCount) {
                for (Object other : thread) {
                    if (!response.getClass().isInstance(other)) continue;
                    if (needs.idCount && !result.id.isEmpty() &&
                            result.id.equals(string(accessor.id.get(other)))) {
                        result.idCount++;
                    }
                    if (needs.repliedCount) {
                        int[] otherReferences = (int[]) accessor.anchors.get(other);
                        if (otherReferences != null) {
                            for (int reference : otherReferences) {
                                if (reference == result.number) result.repliedCount++;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean chainMatches(AdvancedNgRule rule, Accessor accessor, Object response,
            List<?> thread, String title, String url, Needs needs, Set<Integer> visited, int depth)
            throws IllegalAccessException {
        if (thread == null || depth >= 32) return false;
        int number = accessor.number.getInt(response);
        if (!visited.add(number)) return false;
        int[] references = (int[]) accessor.anchors.get(response);
        if (references == null) return false;
        for (int reference : references) {
            Object parent = findResponse(thread, accessor, reference);
            if (parent == null) continue;
            AdvancedNgMatcher.Context parentContext = context(accessor, parent, thread, title, url, needs);
            if (AdvancedNgMatcher.matches(rule, parentContext) ||
                    chainMatches(rule, accessor, parent, thread, title, url, needs,
                            visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static Object findResponse(List<?> thread, Accessor accessor, int number)
            throws IllegalAccessException {
        if (number > 0 && number <= thread.size()) {
            Object candidate = thread.get(number - 1);
            if (candidate != null && candidate.getClass() == accessor.number.getDeclaringClass()
                    && accessor.number.getInt(candidate) == number) return candidate;
        }
        for (Object candidate : thread) {
            if (candidate != null && candidate.getClass() == accessor.number.getDeclaringClass()
                    && accessor.number.getInt(candidate) == number) return candidate;
        }
        return null;
    }

    private static List<?> findThread(Object evaluator, Class<?> responseClass)
            throws IllegalAccessException {
        Class<?> type = evaluator.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(evaluator);
                if (!(value instanceof List)) continue;
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && responseClass.isInstance(list.get(0))) return list;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String threadTitle(List<?> thread) {
        try {
            Method method = thread.getClass().getMethod("getTitle");
            return string(method.invoke(thread));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class Accessor {
        final Field number;
        final Field name;
        final Field mail;
        final Field date;
        final Field body;
        final Field id;
        final Field anchors;

        Accessor(Class<?> type, String configuration) throws NoSuchFieldException {
            String[] names = configuration.split("\\|");
            if (names.length != 7) throw new NoSuchFieldException("Invalid response field map");
            number = field(type, names[0]);
            name = field(type, names[1]);
            mail = field(type, names[2]);
            date = field(type, names[3]);
            body = field(type, names[4]);
            id = field(type, names[5]);
            anchors = field(type, names[6]);
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }
    }

    private static final class Frame {
        final Object response;
        final Object evaluator;
        final long createdAt = System.currentTimeMillis();

        Frame(Object response, Object evaluator) {
            this.response = response;
            this.evaluator = evaluator;
        }
    }

    private static final class Needs {
        final boolean idCount;
        final boolean repliedCount;

        Needs(List<AdvancedNgRule> rules) {
            boolean id = false;
            boolean replied = false;
            for (AdvancedNgRule rule : rules) {
                if (!rule.enabled) continue;
                for (AdvancedNgRule.Condition condition : rule.conditions) {
                    id |= condition.type.equals("numeric") && condition.field.equals("idCount");
                    replied |= condition.type.equals("numeric") &&
                            condition.field.equals("repliedCount");
                }
            }
            idCount = id;
            repliedCount = replied;
        }
    }
}
