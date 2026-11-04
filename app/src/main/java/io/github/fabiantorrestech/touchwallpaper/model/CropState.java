package io.github.fabiantorrestech.touchwallpaper.model;

import android.graphics.Matrix;

/**
 * Step 1: where the source image sits on the wallpaper. Independent of the zone settings.
 * Measured against the original image, so it stays correct whatever size the image is loaded at.
 */
public final class CropState {
    private static final int FULL_TURN = 4;
    private static final float DEGREES_PER_TURN = 90f;

    /** Wallpaper pixels per original image pixel. */
    public float scale = 1f;
    /** The image center's position, in wallpaper pixels. */
    public float centerX = Screen.WIDTH / 2f;
    public float centerY = Screen.HEIGHT / 2f;
    public int quarterTurns = 0;
    /** Shown wherever the image doesn't cover the wallpaper. */
    public int backgroundGray = 255;

    /** Writes the mapping from loaded image pixels to wallpaper pixels into {@code matrix}. */
    public void writeMatrix(Matrix matrix, SourceImage image) {
        float loadedScale = scale * image.originalPixelsPerLoadedPixel();
        matrix.reset();
        matrix.postTranslate(-image.bitmap.getWidth() / 2f, -image.bitmap.getHeight() / 2f);
        matrix.postRotate(quarterTurns * DEGREES_PER_TURN);
        matrix.postScale(loadedScale, loadedScale);
        matrix.postTranslate(centerX, centerY);
    }

    /** Centers the image and scales it to just cover the wallpaper, keeping the current rotation. */
    public void resetToCover(SourceImage image) {
        scale = coverScale(image);
        centerX = Screen.WIDTH / 2f;
        centerY = Screen.HEIGHT / 2f;
    }

    public void rotateClockwise(SourceImage image) {
        quarterTurns = (quarterTurns + 1) % FULL_TURN;
        resetToCover(image);
    }

    public float coverScale(SourceImage image) {
        boolean sideways = quarterTurns % 2 != 0;
        int rotatedWidth = sideways ? image.originalHeight : image.originalWidth;
        int rotatedHeight = sideways ? image.originalWidth : image.originalHeight;
        return Math.max((float) Screen.WIDTH / rotatedWidth, (float) Screen.HEIGHT / rotatedHeight);
    }
}
