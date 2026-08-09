package app.revanced.extension.chmate;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class AdViewCollapser implements Application.ActivityLifecycleCallbacks {
    private static final Set<String> AD_RESOURCE_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ad", "ads", "ad_view", "adview", "ad_container", "adcontainer",
            "ad_banner", "adbanner", "banner_ad", "bannerad", "advertisement"
    )));

    private static final Map<Application, AdViewCollapser> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> listeners =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AdViewCollapser() {
    }

    public static void install(Application application) {
        synchronized (INSTANCES) {
            if (INSTANCES.containsKey(application)) {
                return;
            }
            AdViewCollapser collapser = new AdViewCollapser();
            INSTANCES.put(application, collapser);
            application.registerActivityLifecycleCallbacks(collapser);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        View root = activity.getWindow().getDecorView();
        collapseRecursively(root);

        synchronized (listeners) {
            if (listeners.containsKey(activity)) {
                return;
            }
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> collapseRecursively(root);
            listeners.put(activity, listener);
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener = listeners.remove(activity);
        if (listener == null) {
            return;
        }
        View root = activity.getWindow().getDecorView();
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void collapseRecursively(View view) {
        if (isAdvertisementView(view)) {
            collapse(view);
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collapseRecursively(group.getChildAt(index));
            }
        }
    }

    private static boolean isAdvertisementView(View view) {
        String className = view.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains(".google.android.gms.ads.")
                || className.contains(".amazon.device.ads.")
                || className.contains(".applovin.")
                || className.contains(".unity3d.ads.")
                || className.endsWith(".adview")
                || className.contains("bannerad")) {
            return true;
        }

        if (view.getId() == View.NO_ID) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);
            return AD_RESOURCE_NAMES.contains(name);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void collapse(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != 0) {
            params.height = 0;
            view.setLayoutParams(params);
        }
        view.setMinimumHeight(0);
        if (view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
