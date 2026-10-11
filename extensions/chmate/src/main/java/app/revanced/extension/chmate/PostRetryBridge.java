package app.revanced.extension.chmate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PostRetryBridge {
    private static final int MAX_RETRIES = 2;
    private static final ThreadLocal<PostContext> CURRENT = new ThreadLocal<>();

    private PostRetryBridge() {
    }

    public static void enter(Object client, Object board, Object postData, boolean mode,
            Object callback, Object transport) {
        PostContext existing = CURRENT.get();
        if (existing != null && existing.insideRetry) return;
        CURRENT.set(new PostContext(client, new Object[]{board, postData, mode, callback, transport}));
    }

    public static void enterThread(Object client, Object board, Object threadData, String subject,
            boolean mode, Object callback, Object transport) {
        PostContext existing = CURRENT.get();
        if (existing != null && existing.insideRetry) return;
        CURRENT.set(new PostContext(client,
                new Object[]{board, threadData, subject, mode, callback, transport}));
    }

    public static void clear() {
        PostContext context = CURRENT.get();
        if (context == null || !context.insideRetry) CURRENT.remove();
    }

    public static Object retryOrThrow(Throwable original) throws Throwable {
        PostContext context = CURRENT.get();
        if (context == null || context.insideRetry) throw original;
        if (RetryCooldown.delayMillis(original.getMessage()) == 0) {
            CURRENT.remove();
            throw original;
        }
        Method postingMethod = findPostingMethod(context);
        if (postingMethod == null) {
            CURRENT.remove();
            throw original;
        }
        context.insideRetry = true;
        Throwable lastFailure = original;
        try {
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                long delay = RetryCooldown.delayMillis(lastFailure.getMessage());
                if (delay == 0) throw lastFailure;
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
                try {
                    return postingMethod.invoke(context.client, context.arguments);
                } catch (InvocationTargetException invocation) {
                    lastFailure = invocation.getCause() == null ? invocation : invocation.getCause();
                }
            }
            throw lastFailure;
        } finally {
            CURRENT.remove();
        }
    }

    private static Method findPostingMethod(PostContext context) {
        Object[] arguments = context.arguments;
        Method match = null;
        for (Method method : context.client.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == Void.TYPE) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != arguments.length) continue;
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (arguments[i] instanceof Boolean) {
                    compatible &= parameters[i] == Boolean.TYPE;
                } else {
                    compatible &= accepts(parameters[i], arguments[i]);
                }
            }
            if (!compatible) continue;
            if (match != null) return null;
            match = method;
        }
        return match;
    }

    private static boolean accepts(Class<?> parameter, Object argument) {
        return argument == null ? !parameter.isPrimitive() : parameter.isInstance(argument);
    }

    private static final class PostContext {
        final Object client;
        final Object[] arguments;
        boolean insideRetry;

        PostContext(Object client, Object[] arguments) {
            this.client = client;
            this.arguments = arguments;
        }
    }
}
