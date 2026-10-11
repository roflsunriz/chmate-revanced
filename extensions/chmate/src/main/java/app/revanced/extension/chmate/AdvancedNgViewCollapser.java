package app.revanced.extension.chmate;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AdvancedNgViewCollapser {
    private static final Pattern NG_HEADER = Pattern.compile(
            "^\\s*(\\d+)(?:\\s*\\(\\d+\\))?\\s+あぼーん\\s*\\[NGWord\\]");
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, OriginalViewState> CHANGED = new WeakHashMap<>();

    private AdvancedNgViewCollapser() { }

    static void attach(Activity activity) {
        if (!AdvancedNgBridge.hasTransparentRules()) {
            scan(activity);
            return;
        }
        View root = activity.getWindow().getDecorView();
        if (root == null || !root.getViewTreeObserver().isAlive()) return;
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            WeakReference<Activity> reference = new WeakReference<>(activity);
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
                Activity current = reference.get();
                if (current != null && !current.isFinishing()) scan(current);
            };
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
        scan(activity);
    }

    static void detach(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) { listener = OBSERVERS.remove(activity); }
        if (listener == null) return;
        ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
    }

    private static void scan(Activity activity) {
        String url = activity.getIntent() == null ? null : activity.getIntent().getDataString();
        if (url == null || url.isEmpty()) return;
        View root = activity.getWindow().getDecorView();
        if (root instanceof ViewGroup) scanGroup((ViewGroup) root, url);
    }

    private static void scanGroup(ViewGroup group, String threadUrl) {
        if (isRecyclerView(group)) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                Integer number = ngNumber(child);
                if (number == null) {
                    restore(child);
                    continue;
                }
                if (AdvancedNgBridge.isTransparentMatch(threadUrl, number)) collapse(child);
                else restore(child);
            }
            return;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof ViewGroup) scanGroup((ViewGroup) child, threadUrl);
        }
    }

    private static boolean isRecyclerView(ViewGroup group) {
        Class<?> type = group.getClass();
        while (type != null) {
            if (type.getName().equals("androidx.recyclerview.widget.RecyclerView")) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static Integer ngNumber(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) {
                Matcher match = NG_HEADER.matcher(text);
                if (match.find()) {
                    try { return Integer.parseInt(match.group(1)); }
                    catch (NumberFormatException ignored) { return null; }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Integer number = ngNumber(group.getChildAt(index));
                if (number != null) return number;
            }
        }
        return null;
    }

    private static void collapse(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;
        if (!CHANGED.containsKey(view)) {
            CHANGED.put(view, new OriginalViewState(params.height, view.getVisibility()));
        }
        if (params.height != 0 || view.getVisibility() != View.GONE) {
            params.height = 0;
            view.setLayoutParams(params);
            view.setVisibility(View.GONE);
        }
    }

    private static void restore(View view) {
        OriginalViewState state = CHANGED.remove(view);
        if (state == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.height = state.height;
            view.setLayoutParams(params);
        }
        view.setVisibility(state.visibility);
    }

    private static final class OriginalViewState {
        final int height;
        final int visibility;

        OriginalViewState(int height, int visibility) {
            this.height = height;
            this.visibility = visibility;
        }
    }
}
