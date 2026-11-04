package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Rect;

/** The user's settings for one zone. Pixel values are in wallpaper pixels. */
public final class ZoneStyle {
    /** Toggling this off keeps every other setting, so turning a zone back on restores its look. */
    public boolean enabled = false;
    public ZoneMode mode = ZoneMode.FILL;
    public int fillGray = 255;
    public int fillOpacityPercent = 100;
    public int outlineGray = 0;
    public int outlineWidth = 3;
    public int cornerRadius = 12;
    public int padding = 8;
    public int softEdge = 0;

    /** The chosen coverage for zones that offer presets, or null for the default. */
    public String presetId = null;

    /** The user's dragged rectangle, or null to use the preset or measured one. */
    public Rect customRect = null;

    /** Whether this is still the untouched default, so merely looking at a zone isn't an edit. */
    public boolean isDefault() {
        ZoneStyle untouched = new ZoneStyle();
        return enabled == untouched.enabled
                && mode == untouched.mode
                && fillGray == untouched.fillGray
                && fillOpacityPercent == untouched.fillOpacityPercent
                && outlineGray == untouched.outlineGray
                && outlineWidth == untouched.outlineWidth
                && cornerRadius == untouched.cornerRadius
                && padding == untouched.padding
                && softEdge == untouched.softEdge
                && presetId == null
                && customRect == null;
    }

    public Rect effectiveRect(ZoneDefinition definition) {
        return customRect != null ? customRect : definition.rectForPreset(presetId);
    }
}
