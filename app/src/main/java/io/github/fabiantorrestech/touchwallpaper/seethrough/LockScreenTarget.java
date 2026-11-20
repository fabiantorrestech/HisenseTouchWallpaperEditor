package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.graphics.Bitmap;
import java.io.IOException;

/** Where a lock screen image goes on a given device (ADR 7). Implementations may block; call off the UI thread. */
public interface LockScreenTarget {
    /** A short, user-facing reason this target can't be used right now, or null when it's ready. */
    String problem();

    /** Applies a finished wallpaper. The caller keeps ownership of the bitmap. */
    void apply(Bitmap wallpaper) throws IOException;

    /** Applies an already encoded PNG, such as the fallback wallpaper. */
    void applyPng(byte[] png) throws IOException;
}
