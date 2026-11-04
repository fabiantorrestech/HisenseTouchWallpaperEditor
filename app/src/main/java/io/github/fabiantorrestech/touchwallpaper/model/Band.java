package io.github.fabiantorrestech.touchwallpaper.model;

/**
 * The two parts of the wallpaper the lock screen checks to pick its text color.
 * Bounds are exclusive on both ends, matching the ROM's check. See research/lockscreen-zones.md.
 */
public enum Band {
    TOP("Top", 0, 360),
    BOTTOM("Bottom", 1080, 1440);

    public final String label;
    public final int top;
    public final int bottom;

    Band(String label, int top, int bottom) {
        this.label = label;
        this.top = top;
        this.bottom = bottom;
    }
}
