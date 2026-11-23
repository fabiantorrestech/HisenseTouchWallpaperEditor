package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Color;

/**
 * See-through projects: fades the screenshot toward black or white before the zones are drawn,
 * so the lock screen looks locked and its text stands out. Drawn by the renderer only.
 */
public final class BackgroundTone {
    public enum Mode { OFF, DARKEN, LIGHTEN }

    public static final int MIN_PERCENT = 5;
    public static final int MAX_PERCENT = 90;
    public static final int STEP_PERCENT = 5;
    /** White page 255 → 153: clearly dimmed on e-ink, black text still sharp. Reasoning only; check by eye. */
    public static final int DEFAULT_PERCENT = 40;

    public Mode mode = Mode.OFF;
    public int percent = DEFAULT_PERCENT;

    public boolean isOff() {
        return mode == Mode.OFF;
    }

    /** The translucent color laid over the image. Only meaningful when not {@link #isOff()}. */
    public int color() {
        int alpha = Math.round(clampedPercent() * 255 / 100f);
        int level = mode == Mode.LIGHTEN ? 255 : 0;
        return Color.argb(alpha, level, level, level);
    }

    public int clampedPercent() {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }
}
