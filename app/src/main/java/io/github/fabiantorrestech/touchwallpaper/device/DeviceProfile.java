package io.github.fabiantorrestech.touchwallpaper.device;

import android.content.Context;
import io.github.fabiantorrestech.touchwallpaper.seethrough.HisenseRomFileTarget;
import io.github.fabiantorrestech.touchwallpaper.seethrough.LockScreenTarget;
import io.github.fabiantorrestech.touchwallpaper.seethrough.WallpaperManagerLockTarget;

/**
 * What the app knows about the device it runs on: its screen, how a lock screen image is applied,
 * and whether lock screen zones were measured for it (ADR 7).
 */
public final class DeviceProfile {
    public enum Kind {
        /** Measured in research/: ROM wallpaper file, zones and the font color rule. */
        HISENSE_TOUCH_LITE,
        /** Any other Android 11+ device: the standard lock wallpaper, no zones. */
        GENERIC
    }

    public final Kind kind;
    public final String label;
    /** Portrait screen size in pixels. */
    public final int width;
    public final int height;

    DeviceProfile(Kind kind, String label, int width, int height) {
        this.kind = kind;
        this.label = label;
        this.width = width;
        this.height = height;
    }

    /** Whether zones.json and the font color rule apply here. Without them, screenshots are used as they are. */
    public boolean hasMeasuredZones() {
        return kind == Kind.HISENSE_TOUCH_LITE;
    }

    /** Whether applying needs All Files Access (ADR 4). */
    public boolean needsAllFilesAccess() {
        return kind == Kind.HISENSE_TOUCH_LITE;
    }

    public LockScreenTarget createTarget(Context context) {
        return kind == Kind.HISENSE_TOUCH_LITE
                ? new HisenseRomFileTarget(context)
                : new WallpaperManagerLockTarget(context);
    }
}
