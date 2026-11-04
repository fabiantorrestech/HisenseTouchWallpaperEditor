package io.github.fabiantorrestech.touchwallpaper.render;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import java.io.File;
import java.io.IOException;

/** Loads a project's image upright, at a size the device can comfortably edit. The original file isn't changed. */
public final class SourceImageLoader {
    private static final int MAX_LONG_SIDE = 2880;
    private static final int MAX_LONG_SIDE_LOW_MEMORY = 1440;
    private static final long LOW_MEMORY_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    /** The largest image the first app version would load. Needed to migrate its saved crops. */
    private static final int LEGACY_MAX_LONG_SIDE = 4096;

    private SourceImageLoader() {
    }

    public static SourceImage load(Context context, File file) throws IOException {
        BitmapFactory.Options bounds = readBounds(file);
        boolean lowMemory = isLowMemory(context);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(
                Math.max(bounds.outWidth, bounds.outHeight),
                lowMemory ? MAX_LONG_SIDE_LOW_MEMORY : MAX_LONG_SIDE);
        // Half the memory per pixel. The small loss doesn't show on a 16-level gray panel.
        options.inPreferredConfig = lowMemory ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (bitmap == null) {
            throw new IOException("This file isn't a readable image.");
        }
        return upright(bitmap, bounds, file);
    }

    /** How much the first app version shrank this file when loading it. */
    public static int legacySampleSize(File file) throws IOException {
        BitmapFactory.Options bounds = readBounds(file);
        return sampleSizeFor(Math.max(bounds.outWidth, bounds.outHeight), LEGACY_MAX_LONG_SIDE);
    }

    private static BitmapFactory.Options readBounds(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("This file isn't a readable image.");
        }
        return bounds;
    }

    private static boolean isLowMemory(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return manager.isLowRamDevice() || info.totalMem <= LOW_MEMORY_TOTAL_BYTES;
    }

    private static int sampleSizeFor(int longSide, int maxLongSide) {
        int sampleSize = 1;
        while (longSide / sampleSize > maxLongSide) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static SourceImage upright(Bitmap bitmap, BitmapFactory.Options bounds, File file) throws IOException {
        int orientation = new ExifInterface(file.getPath())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        boolean sideways = false;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                sideways = true;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                sideways = true;
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            default:
                return new SourceImage(bitmap, bounds.outWidth, bounds.outHeight);
        }
        Bitmap turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (turned != bitmap) {
            bitmap.recycle();
        }
        return sideways
                ? new SourceImage(turned, bounds.outHeight, bounds.outWidth)
                : new SourceImage(turned, bounds.outWidth, bounds.outHeight);
    }
}
