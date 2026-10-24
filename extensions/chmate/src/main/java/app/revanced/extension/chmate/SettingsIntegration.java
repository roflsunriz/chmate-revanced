package app.revanced.extension.chmate;

import android.app.Activity;
import android.content.Intent;

public final class SettingsIntegration {
    private SettingsIntegration() {
    }

    public static void attach(Activity activity) {
        IntegrationStrings strings = IntegrationStrings.current();
        NativeEntryInstaller.install(activity, strings.revancedSettings,
                view -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
    }
}
