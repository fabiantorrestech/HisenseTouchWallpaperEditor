package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfile;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfiles;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;
import java.io.IOException;

/** Turning see-through on and off from the UI. The service does the capturing. */
public final class SeeThrough {
    private SeeThrough() {
    }

    /** Whether the user has switched on this app's accessibility service in system settings. */
    public static boolean isServiceOn(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName ours = new ComponentName(context, SeeThroughService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String entry : splitter) {
            if (ours.equals(ComponentName.unflattenFromString(entry))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns see-through on. The first time on a device with a ROM wallpaper file, keeps the current
     * wallpaper as "before see-through" and makes it the fallback. Blocks; call off the UI thread.
     */
    public static void turnOn(Context context, AppSettings settings) throws IOException {
        DeviceProfile profile = DeviceProfiles.detect(context);
        FallbackWallpaper fallback = new FallbackWallpaper(context);
        if (profile.kind == DeviceProfile.Kind.HISENSE_TOUCH_LITE && !fallback.hasBefore()) {
            byte[] current = new WallpaperExporter(context).readCurrent();
            if (current != null) {
                fallback.saveBefore(current);
            }
        }
        if (settings.seeThroughFallbackId() == null && fallback.hasBefore()) {
            fallback.useBefore();
            settings.setSeeThroughFallbackId(FallbackWallpaper.BEFORE);
        }
        settings.setSeeThroughEnabled(true);
    }

    /**
     * Turns see-through off and puts the fallback wallpaper back, so the lock screen stops showing
     * the last screenshot. Returns false if there was no fallback to put back. Blocks.
     */
    public static boolean turnOff(Context context, AppSettings settings) throws IOException {
        settings.setSeeThroughEnabled(false);
        FallbackWallpaper fallback = new FallbackWallpaper(context);
        if (!fallback.exists()) {
            return false;
        }
        LockScreenTarget target = DeviceProfiles.detect(context).createTarget(context);
        String problem = target.problem();
        if (problem != null) {
            throw new IOException(problem);
        }
        target.applyPng(fallback.read());
        return true;
    }
}
