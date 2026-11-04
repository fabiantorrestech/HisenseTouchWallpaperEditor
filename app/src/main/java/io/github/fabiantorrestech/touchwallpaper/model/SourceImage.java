package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Bitmap;

/** A project's image as loaded for editing, plus the full size of the original upright image. */
public final class SourceImage {
    public final Bitmap bitmap;
    public final int originalWidth;
    public final int originalHeight;

    public SourceImage(Bitmap bitmap, int originalWidth, int originalHeight) {
        this.bitmap = bitmap;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
    }

    public float originalPixelsPerLoadedPixel() {
        return (float) originalWidth / bitmap.getWidth();
    }
}
