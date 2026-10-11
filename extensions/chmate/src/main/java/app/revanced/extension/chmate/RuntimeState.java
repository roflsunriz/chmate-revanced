package app.revanced.extension.chmate;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RuntimeState {
    private static volatile Context applicationContext;
    private static final AtomicBoolean ACTIVITY_TRACKING = new AtomicBoolean();
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    private RuntimeState() {
    }

    public static void initialize(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
            if (applicationContext instanceof Application &&
                    ACTIVITY_TRACKING.compareAndSet(false, true)) {
                ((Application) applicationContext).registerActivityLifecycleCallbacks(
                        new Application.ActivityLifecycleCallbacks() {
                            @Override public void onActivityCreated(Activity activity, Bundle state) { }
                            @Override public void onActivityStarted(Activity activity) { }
                            @Override public void onActivityResumed(Activity activity) {
                                resumedActivity = new WeakReference<>(activity);
                                AdvancedNgViewCollapser.attach(activity);
                            }
                            @Override public void onActivityPaused(Activity activity) {
                                if (resumedActivity.get() == activity) {
                                    resumedActivity = new WeakReference<>(null);
                                }
                            }
                            @Override public void onActivityStopped(Activity activity) { }
                            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                            @Override public void onActivityDestroyed(Activity activity) {
                                AdvancedNgViewCollapser.detach(activity);
                            }
                        });
            }
        }
    }

    public static Context getContext() {
        return applicationContext;
    }

    static String currentThreadUrl() {
        Activity activity = resumedActivity.get();
        return activity == null || activity.getIntent() == null
                ? null : activity.getIntent().getDataString();
    }
}
