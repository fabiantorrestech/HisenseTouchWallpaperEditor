package io.github.fabiantorrestech.touchwallpaper.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** One editable wallpaper: the source image plus the settings from both steps. */
public final class Project {
    public String id;
    public String name;
    public long updatedAtMillis;
    /** File name of the private source image copy inside the project's folder. */
    public String sourceFileName;

    public CropState crop = new CropState();
    public final Map<String, ZoneStyle> zoneStyles = new HashMap<>();
    public final Map<Band, TextColorChoice> textColorChoices = new EnumMap<>(Band.class);

    /** The last PNG saved from this project, or nulls if there isn't one. */
    public String lastSavedUri;
    public String lastSavedFolderUri;
    public String lastSavedPath;

    public Project() {
        for (Band band : Band.values()) {
            textColorChoices.put(band, TextColorChoice.AUTO);
        }
    }

    /** Returns the zone's style, creating the default the first time it's requested. */
    public ZoneStyle styleFor(String zoneId) {
        ZoneStyle style = zoneStyles.get(zoneId);
        if (style == null) {
            style = new ZoneStyle();
            zoneStyles.put(zoneId, style);
        }
        return style;
    }
}
