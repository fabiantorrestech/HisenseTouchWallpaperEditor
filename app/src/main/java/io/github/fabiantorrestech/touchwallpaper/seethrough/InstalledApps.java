package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The launchable apps, each with a hide suggestion. Slow (loads labels): call off the UI thread. */
public final class InstalledApps {
    public static final class App {
        public final String packageName;
        public final String label;
        /** Why hiding it is suggested, or null. */
        public final SensitiveAppRules.Reason suggestion;

        App(String packageName, String label, SensitiveAppRules.Reason suggestion) {
            this.packageName = packageName;
            this.label = label;
            this.suggestion = suggestion;
        }
    }

    private InstalledApps() {
    }

    /** Suggested apps first, then by name. This app is left out. */
    public static List<App> load(Context context) {
        PackageManager manager = context.getPackageManager();
        Map<String, SensitiveAppRules.Reason> roles = systemRoles(context, manager);
        List<ResolveInfo> launchable = manager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Set<String> seen = new HashSet<>();
        List<App> apps = new ArrayList<>();
        for (ResolveInfo info : launchable) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName()) || !seen.add(packageName)) {
                continue;
            }
            String label = String.valueOf(info.loadLabel(manager));
            boolean social = info.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_SOCIAL;
            apps.add(new App(packageName, label,
                    SensitiveAppRules.reasonFor(packageName, label, roles.get(packageName), social)));
        }
        apps.sort(Comparator.comparing((App app) -> app.suggestion == null)
                .thenComparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return apps;
    }

    /** Apps the system itself names as the SMS, phone or email app. */
    private static Map<String, SensitiveAppRules.Reason> systemRoles(Context context, PackageManager manager) {
        Map<String, SensitiveAppRules.Reason> roles = new HashMap<>();
        List<ResolveInfo> mail = manager.queryIntentActivities(
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0);
        for (ResolveInfo info : mail) {
            roles.put(info.activityInfo.packageName, SensitiveAppRules.Reason.EMAIL);
        }
        String sms = Telephony.Sms.getDefaultSmsPackage(context);
        if (sms != null) {
            roles.put(sms, SensitiveAppRules.Reason.MESSAGES);
        }
        TelecomManager telecom = context.getSystemService(TelecomManager.class);
        String dialer = telecom != null ? telecom.getDefaultDialerPackage() : null;
        if (dialer != null) {
            roles.put(dialer, SensitiveAppRules.Reason.MESSAGES);
        }
        return roles;
    }
}
