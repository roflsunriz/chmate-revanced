package app.revanced.extension.chmate;

import android.app.Activity;
import android.content.Intent;

public final class AdvancedNgIntegration {
    private AdvancedNgIntegration() {
    }

    public static void attach(Activity activity) {
        IntegrationStrings strings = IntegrationStrings.current();
        NativeEntryInstaller.install(activity, strings.advancedNg,
                view -> activity.startActivity(new Intent(activity, AdvancedNgActivity.class)));
    }
}
