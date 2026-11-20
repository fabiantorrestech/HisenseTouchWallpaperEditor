package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Any Android device whose lock screen shows the standard lock wallpaper. Best effort: many e-ink
 * ROMs draw their own sleep screen and ignore it, as the Hisense one does (ADR 4).
 */
public final class WallpaperManagerLockTarget implements LockScreenTarget {
    private final WallpaperManager manager;

    public WallpaperManagerLockTarget(Context context) {
        manager = WallpaperManager.getInstance(context.getApplicationContext());
    }

    @Override
    public String problem() {
        if (!manager.isWallpaperSupported() || !manager.isSetWallpaperAllowed()) {
            return "This device doesn't let apps change the lock screen wallpaper.";
        }
        return null;
    }

    @Override
    public void apply(Bitmap wallpaper) throws IOException {
        manager.setBitmap(wallpaper, null, true, WallpaperManager.FLAG_LOCK);
    }

    @Override
    public void applyPng(byte[] png) throws IOException {
        manager.setStream(new ByteArrayInputStream(png), null, true, WallpaperManager.FLAG_LOCK);
    }
}
