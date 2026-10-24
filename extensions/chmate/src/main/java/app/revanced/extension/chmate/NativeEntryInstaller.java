package app.revanced.extension.chmate;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class NativeEntryInstaller {
    private static final Map<Activity, Map<String, Button>> BUTTONS = new WeakHashMap<>();
    private static final Map<Activity, LinearLayout> ACTIONS = new WeakHashMap<>();

    private NativeEntryInstaller() {
    }

    static void install(Activity activity, String label, View.OnClickListener listener) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.post(() -> {
            synchronized (BUTTONS) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Map<String, Button> activityButtons = BUTTONS.get(activity);
                if (activityButtons == null) {
                    activityButtons = new HashMap<>();
                    BUTTONS.put(activity, activityButtons);
                }
                Button existing = activityButtons.get(label);
                if (existing != null) {
                    existing.setOnClickListener(listener);
                    return;
                }
                LinearLayout actions = ACTIONS.get(activity);
                if (actions == null) {
                    FrameLayout original = new FrameLayout(activity);
                    while (content.getChildCount() > 0) {
                        View child = content.getChildAt(0);
                        content.removeViewAt(0);
                        original.addView(child);
                    }
                    LinearLayout column = new LinearLayout(activity);
                    column.setOrientation(LinearLayout.VERTICAL);
                    column.addView(original, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                    actions = new LinearLayout(activity);
                    actions.setOrientation(LinearLayout.VERTICAL);
                    column.addView(actions, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    content.addView(column, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    ACTIONS.put(activity, actions);
                }
                Button entry = new Button(activity);
                entry.setText(label);
                entry.setAllCaps(false);
                entry.setOnClickListener(listener);
                actions.addView(entry, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                activityButtons.put(label, entry);
            }
        });
    }

}
