package app.revanced.extension.chmate;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserAgentOverride {
    public static final String PREFERENCES_NAME = "chmate_revanced_settings";
    public static final String USER_AGENT_KEY = "user_agent";

    private UserAgentOverride() {
    }

    public static String resolve(String original) {
        Context context = RuntimeState.getContext();
        if (context == null) {
            return original;
        }

        String configured = preferences(context).getString(USER_AGENT_KEY, "");
        return configured == null || configured.isEmpty() ? original : configured;
    }

    public static String overrideHeader(String name, String value) {
        return name != null && "user-agent".equalsIgnoreCase(name.trim()) ? resolve(value) : value;
    }

    public static String getSystemProperty(String name) {
        String value = System.getProperty(name);
        return "http.agent".equals(name) ? resolve(value) : value;
    }

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
