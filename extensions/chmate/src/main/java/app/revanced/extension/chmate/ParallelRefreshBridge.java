package app.revanced.extension.chmate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ParallelRefreshBridge {
    private static final int MAX_PARALLELISM = 4;
    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();

    private ParallelRefreshBridge() {
    }

    public static Object run(Object work, Object client, List<?> items, boolean download)
            throws Exception {
        if (Boolean.TRUE.equals(BYPASS.get()) || work == null || client == null
                || items == null || items.size() < 2) return null;
        List<List<?>> partitions = partition(items, MAX_PARALLELISM);
        if (partitions.size() < 2) return null;
        Method original = findOriginal(work.getClass(), client.getClass(), 3, 1);
        return runPartitions(work, original, partitions, client, null, download);
    }

    public static Object runLegacy(Object work, Object client, Object helper, List<?> items,
            boolean download) throws Exception {
        if (Boolean.TRUE.equals(BYPASS.get()) || work == null || client == null || helper == null
                || items == null || items.size() < 2) return null;
        List<List<?>> partitions = partition(items, MAX_PARALLELISM);
        if (partitions.size() < 2) return null;
        Method original = findOriginal(work.getClass(), client.getClass(), 4, 2);
        return runPartitions(work, original, partitions, client, helper, download);
    }

    private static Object runPartitions(Object work, Method original, List<List<?>> partitions,
            Object client, Object helper, boolean download) throws Exception {
        original.setAccessible(true);
        ExecutorService executor = Executors.newFixedThreadPool(partitions.size(), runnable -> {
            Thread thread = new Thread(runnable, "ChMate parallel thread refresh");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (List<?> partition : partitions) {
                futures.add(executor.submit(invokeOriginal(
                        work, original, client, helper, partition, download)));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException error) {
                    for (Future<Object> pending : futures) pending.cancel(true);
                    Throwable cause = error.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw new RuntimeException(cause);
                }
            }
            return combine(results);
        } finally {
            executor.shutdownNow();
        }
    }

    static List<List<?>> partition(List<?> items, int maximum) {
        LinkedHashMap<String, List<Object>> groups = new LinkedHashMap<>();
        for (Object item : items) {
            String key = boardKey(item);
            List<Object> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(item);
        }
        int count = Math.min(Math.max(1, maximum), items.size());
        List<List<Object>> buckets = new ArrayList<>();
        for (int i = 0; i < Math.min(count, Math.max(1, groups.size())); i++) {
            buckets.add(new ArrayList<>());
        }
        if (groups.size() == 1 && count > 1) {
            buckets.clear();
            for (int i = 0; i < count; i++) buckets.add(new ArrayList<>());
            int index = 0;
            for (Object item : items) buckets.get(index++ % buckets.size()).add(item);
        } else {
            List<List<Object>> ordered = new ArrayList<>(groups.values());
            Collections.sort(ordered, new Comparator<List<Object>>() {
                @Override
                public int compare(List<Object> first, List<Object> second) {
                    return Integer.compare(second.size(), first.size());
                }
            });
            for (List<Object> group : ordered) {
                List<Object> smallest = buckets.get(0);
                for (List<Object> bucket : buckets) {
                    if (bucket.size() < smallest.size()) smallest = bucket;
                }
                smallest.addAll(group);
            }
        }
        List<List<?>> result = new ArrayList<>();
        for (List<Object> bucket : buckets) if (!bucket.isEmpty()) result.add(bucket);
        return result;
    }

    private static Callable<Object> invokeOriginal(Object work, Method method, Object client,
            Object helper, List<?> items, boolean download) {
        return () -> {
            BYPASS.set(Boolean.TRUE);
            try {
                return helper == null
                        ? method.invoke(work, client, items, download)
                        : method.invoke(work, client, helper, items, download);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new RuntimeException(cause);
            } finally {
                BYPASS.remove();
            }
        };
    }

    private static Method findOriginal(Class<?> workType, Class<?> clientType,
            int parameterCount, int listParameter) {
        Method found = null;
        for (Method method : workType.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || parameters.length != parameterCount
                    || !parameters[0].isAssignableFrom(clientType)
                    || !List.class.isAssignableFrom(parameters[listParameter])
                    || parameters[parameterCount - 1] != boolean.class
                    || method.getReturnType() == void.class) continue;
            if (found != null) throw new IllegalStateException("Ambiguous ChMate refresh method");
            found = method;
        }
        if (found == null) throw new IllegalStateException("ChMate refresh method not found");
        return found;
    }

    private static Object combine(List<Object> results) throws Exception {
        Object sample = null;
        int errors = 0;
        String firstSummary = null;
        for (Object result : results) {
            if (result == null) continue;
            if (sample == null) sample = result;
            if (result instanceof Number) {
                errors += ((Number) result).intValue();
                continue;
            }
            for (Field field : result.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == int.class) errors += field.getInt(result);
                else if (field.getType() == String.class && firstSummary == null) {
                    firstSummary = (String) field.get(result);
                }
            }
        }
        if (sample == null) throw new IllegalStateException("ChMate refresh returned no result");
        if (sample instanceof Number) return Integer.valueOf(errors);
        for (Constructor<?> constructor : sample.getClass().getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == int.class && parameters[1] == String.class) {
                constructor.setAccessible(true);
                return constructor.newInstance(errors, firstSummary);
            }
        }
        throw new IllegalStateException("ChMate refresh result constructor not found");
    }

    private static String boardKey(Object item) {
        if (item == null) return "null";
        for (Field field : item.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            try {
                field.setAccessible(true);
                String value = (String) field.get(item);
                if (value != null && value.indexOf('/') > 0 && value.indexOf(' ') < 0) return value;
            } catch (ReflectiveOperationException ignored) {
                // Try the next String field from this version's bookmark record.
            }
        }
        return item.getClass().getName();
    }
}
