package app.revanced.extension.chmate;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Process;

import java.util.List;

final class RestartController {
    private static final String MAIN_ACTIVITY_METADATA = "app.revanced.extension.chmate.MAIN_ACTIVITY";

    private RestartController() {
    }

    static boolean restart(Activity activity) {
        String mainActivity = readMainActivity(activity);
        if (mainActivity == null) {
            return false;
        }

        ComponentName component = new ComponentName(activity.getPackageName(), normalizeClassName(activity, mainActivity));
        Intent launchIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component);

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        ActivityManager manager = activity.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (activity.getPackageName().equals(process.processName) && process.pid != Process.myPid()) {
                    Process.killProcess(process.pid);
                }
            }
        }

        activity.startActivity(launchIntent);
        activity.finish();
        return true;
    }

    private static String readMainActivity(Activity activity) {
        try {
            ActivityInfo info = activity.getPackageManager()
                    .getActivityInfo(activity.getComponentName(), PackageManager.GET_META_DATA);
            return info.metaData == null ? null : info.metaData.getString(MAIN_ACTIVITY_METADATA);
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }

    private static String normalizeClassName(Activity activity, String className) {
        if (className.startsWith(".")) {
            return activity.getPackageName() + className;
        }
        if (className.indexOf('.') < 0) {
            return activity.getPackageName() + "." + className;
        }
        return className;
    }
}
