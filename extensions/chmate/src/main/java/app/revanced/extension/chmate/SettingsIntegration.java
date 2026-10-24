package app.revanced.extension.chmate;

import android.app.Activity;
import android.content.Intent;

public final class SettingsIntegration {
    private SettingsIntegration() {
    }

    public static void attach(Activity activity) {
        IntegrationStrings strings = IntegrationStrings.current();
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.post(() -> {
            boolean boardSettings = NativeEntryInstaller.containsText(content, "掲示板設定")
                    || NativeEntryInstaller.containsText(content, "BBSMENU");
            if (boardSettings) {
                NativeEntryInstaller.install(activity, strings.imageBoardSources,
                        view -> activity.startActivity(
                                new Intent(activity, ImageBoardSourcesActivity.class)));
            } else {
                NativeEntryInstaller.install(activity, strings.revancedSettings,
                        view -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
            }
        });
    }
}
