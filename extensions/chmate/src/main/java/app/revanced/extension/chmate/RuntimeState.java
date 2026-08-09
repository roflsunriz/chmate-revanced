package app.revanced.extension.chmate;

import android.content.Context;

public final class RuntimeState {
    private static volatile Context applicationContext;

    private RuntimeState() {
    }

    public static void initialize(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public static Context getContext() {
        return applicationContext;
    }
}
