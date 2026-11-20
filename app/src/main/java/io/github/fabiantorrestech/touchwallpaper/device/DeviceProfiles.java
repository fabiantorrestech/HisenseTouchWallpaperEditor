package io.github.fabiantorrestech.touchwallpaper.device;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;

/** Picks the profile for this device. Add a measured device here once its lock screen is researched. */
public final class DeviceProfiles {
    public static final String HISENSE_TOUCH_LITE_MODEL = "HITV205N";

    private DeviceProfiles() {
    }

    public static DeviceProfile detect(Context context) {
        if (HISENSE_TOUCH_LITE_MODEL.equals(Build.MODEL)) {
            return new DeviceProfile(DeviceProfile.Kind.HISENSE_TOUCH_LITE, "Hisense Touch Lite", Screen.WIDTH, Screen.HEIGHT);
        }
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealMetrics(metrics);
        int shortSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        int longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        return new DeviceProfile(DeviceProfile.Kind.GENERIC, Build.MANUFACTURER + " " + Build.MODEL, shortSide, longSide);
    }
}
