package io.github.fabiantorrestech.touchwallpaper.model;

/** What the user wants for a band's text color: whatever the image produces, or a forced color. */
public enum TextColorChoice {
    AUTO(null),
    FORCE_BLACK(TextColor.BLACK),
    FORCE_WHITE(TextColor.WHITE);

    /** The forced color, or null for {@link #AUTO}. */
    public final TextColor target;

    TextColorChoice(TextColor target) {
        this.target = target;
    }
}
