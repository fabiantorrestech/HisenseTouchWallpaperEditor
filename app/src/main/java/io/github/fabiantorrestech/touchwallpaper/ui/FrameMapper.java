package io.github.fabiantorrestech.touchwallpaper.ui;

import android.graphics.RectF;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;

/** Fits the 1:2 wallpaper into a view and converts between view and wallpaper coordinates. */
final class FrameMapper {
    final RectF frame = new RectF();
    private float viewPixelsPerWallpaperPixel = 1f;

    void layout(int viewWidth, int viewHeight) {
        viewPixelsPerWallpaperPixel = Math.min(
                (float) viewWidth / Screen.WIDTH,
                (float) viewHeight / Screen.HEIGHT);
        float width = Screen.WIDTH * viewPixelsPerWallpaperPixel;
        float height = Screen.HEIGHT * viewPixelsPerWallpaperPixel;
        float left = (viewWidth - width) / 2f;
        float top = (viewHeight - height) / 2f;
        frame.set(left, top, left + width, top + height);
    }

    float scale() {
        return viewPixelsPerWallpaperPixel;
    }

    float toWallpaperX(float viewX) {
        return (viewX - frame.left) / viewPixelsPerWallpaperPixel;
    }

    float toWallpaperY(float viewY) {
        return (viewY - frame.top) / viewPixelsPerWallpaperPixel;
    }

    float toViewX(float wallpaperX) {
        return frame.left + wallpaperX * viewPixelsPerWallpaperPixel;
    }

    float toViewY(float wallpaperY) {
        return frame.top + wallpaperY * viewPixelsPerWallpaperPixel;
    }

    float toViewLength(float wallpaperLength) {
        return wallpaperLength * viewPixelsPerWallpaperPixel;
    }

    float toWallpaperLength(float viewLength) {
        return viewLength / viewPixelsPerWallpaperPixel;
    }
}
