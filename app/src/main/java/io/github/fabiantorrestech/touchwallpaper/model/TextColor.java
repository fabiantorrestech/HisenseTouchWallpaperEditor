package io.github.fabiantorrestech.touchwallpaper.model;

/** A color the lock screen can use for its text. */
public enum TextColor {
    BLACK("black", 0xFF000000),
    WHITE("white", 0xFFFFFFFF);

    public final String label;
    public final int argb;

    TextColor(String label, int argb) {
        this.label = label;
        this.argb = argb;
    }
}
