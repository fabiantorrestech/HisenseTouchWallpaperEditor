package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import io.github.fabiantorrestech.touchwallpaper.export.FastPngEncoder;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import java.io.IOException;

/**
 * The Hisense Touch Lite's e-ink keyguard: replaces the ROM's wallpaper file (ADR 4). Lossless PNG,
 * because forced text colors depend on exact pixels, written with the fast encoder because the
 * keyguard reads the file the moment power is pressed. Unlike a manual apply, no gallery copy is
 * saved. Used from the service's worker thread only.
 */
public final class HisenseRomFileTarget implements LockScreenTarget {
    private final WallpaperExporter exporter;
    private final FastPngEncoder encoder = new FastPngEncoder();

    public HisenseRomFileTarget(Context context) {
        exporter = new WallpaperExporter(context);
    }

    @Override
    public String problem() {
        if (!exporter.hasAllFilesAccess()) {
            return "All Files Access is off.";
        }
        if (!exporter.romFolderExists()) {
            return "The lock screen's wallpaper folder wasn't found.";
        }
        return null;
    }

    @Override
    public void apply(Bitmap wallpaper) throws IOException {
        long start = SystemClock.uptimeMillis();
        byte[] png = encoder.encode(wallpaper);
        long encoded = SystemClock.uptimeMillis();
        exporter.apply(png);
        Log.i(SeeThroughService.TAG, "encode " + (encoded - start) + " ms, write "
                + (SystemClock.uptimeMillis() - encoded) + " ms, " + png.length / 1024 + " KB");
    }

    @Override
    public void applyPng(byte[] png) throws IOException {
        exporter.apply(png);
    }
}
