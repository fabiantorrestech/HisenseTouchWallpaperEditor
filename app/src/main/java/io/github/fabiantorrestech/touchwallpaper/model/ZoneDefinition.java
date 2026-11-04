package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Rect;
import java.util.Collections;
import java.util.List;

/** A lock screen component's measured location, loaded from assets/zones.json. */
public final class ZoneDefinition {
    public final String id;
    public final String label;
    public final Band band;
    public final Rect rect;
    public final String evidence;
    /** Whether the band's text color rule is known to apply to this component. */
    public final boolean hasTextColor;
    /** Whether "Make readable" puts a panel behind this component. */
    public final boolean includedInMakeReadable;
    public final List<ZonePreset> presets;

    public ZoneDefinition(String id, String label, Band band, Rect rect, String evidence,
                          boolean hasTextColor, boolean includedInMakeReadable, List<ZonePreset> presets) {
        this.id = id;
        this.label = label;
        this.band = band;
        this.rect = rect;
        this.evidence = evidence;
        this.hasTextColor = hasTextColor;
        this.includedInMakeReadable = includedInMakeReadable;
        this.presets = Collections.unmodifiableList(presets);
    }

    /** Returns the matching preset's area, or the measured area when there's no match. */
    public Rect rectForPreset(String presetId) {
        for (ZonePreset preset : presets) {
            if (preset.id.equals(presetId)) {
                return preset.rect;
            }
        }
        return rect;
    }
}
