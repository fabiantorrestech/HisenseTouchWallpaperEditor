package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Rect;

/** A named area a zone can cover, such as the quote's language options. */
public final class ZonePreset {
    public final String id;
    public final String label;
    public final String note;
    public final Rect rect;

    public ZonePreset(String id, String label, String note, Rect rect) {
        this.id = id;
        this.label = label;
        this.note = note;
        this.rect = rect;
    }
}
