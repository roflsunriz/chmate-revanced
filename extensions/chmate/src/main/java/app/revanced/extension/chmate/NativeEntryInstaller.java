package app.revanced.extension.chmate;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class NativeEntryInstaller {
    private static final Set<Activity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());

    private NativeEntryInstaller() {
    }

    static void install(Activity activity, String label, View.OnClickListener listener) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.post(() -> {
            synchronized (INSTALLED) {
                if (INSTALLED.contains(activity) || activity.isFinishing()) return;
                INSTALLED.add(activity);
            }
            if (containsText(content, label)) return;

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
            Button entry = new Button(activity);
            entry.setText(label);
            entry.setAllCaps(false);
            entry.setOnClickListener(listener);
            column.addView(entry, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(column, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        });
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof android.widget.TextView
                && text.contentEquals(((android.widget.TextView) view).getText())) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
