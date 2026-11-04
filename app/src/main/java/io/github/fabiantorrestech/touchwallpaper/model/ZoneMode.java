package io.github.fabiantorrestech.touchwallpaper.model;

/** How an enabled zone is drawn onto the wallpaper. Whether it's on at all is {@link ZoneStyle#enabled}. */
public enum ZoneMode {
    OUTLINE(false, true),
    FILL(true, false),
    BOTH(true, true);

    public final boolean drawsFill;
    public final boolean drawsOutline;

    ZoneMode(boolean drawsFill, boolean drawsOutline) {
        this.drawsFill = drawsFill;
        this.drawsOutline = drawsOutline;
    }
}
