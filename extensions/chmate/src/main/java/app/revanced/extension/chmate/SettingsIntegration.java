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
        NativeEntryInstaller.install(activity, strings.imageBoardSources,
                view -> activity.startActivity(new Intent(activity, ImageBoardSourcesActivity.class)));
        NativeEntryInstaller.install(activity, strings.advancedNg,
                view -> activity.startActivity(new Intent(activity, AdvancedNgActivity.class)));
        NativeEntryInstaller.install(activity, strings.revancedSettings,
                view -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
    }
}
