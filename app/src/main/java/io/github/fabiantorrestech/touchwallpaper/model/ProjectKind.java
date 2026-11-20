package io.github.fabiantorrestech.touchwallpaper.model;

/** What a project's wallpaper is drawn over. */
public enum ProjectKind {
    /** The project's own picture, placed in Step 1. */
    IMAGE,
    /**
     * Whatever is on screen when the device locks (ADR 6). The project's image is only a preview
     * for designing zones; it's never applied.
     */
    SEE_THROUGH
}
