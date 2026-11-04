package io.github.fabiantorrestech.touchwallpaper.font;

import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;

/**
 * The ROM's text color rule (Util.isWhiteAreaOfView), confirmed on the device and panel.
 * Every 4th pixel in a band votes on its red channel alone. See research/lockscreen-zones.md.
 */
public final class FontColorRule {
    static final int SAMPLE_STEP = 4;
    /** A sample votes "white" only when its red value is above this. */
    static final int RED_THRESHOLD = 192;

    private FontColorRule() {
    }

    /** Positive means white wins. The range is about ±15,931 for a full band. */
    public static int vote(int[] pixels, int width, Band band) {
        int count = 0;
        for (int y = firstSampleRow(band); y < band.bottom; y += SAMPLE_STEP) {
            for (int x = SAMPLE_STEP; x < width; x += SAMPLE_STEP) {
                count += votesWhite(pixels[y * width + x]) ? 1 : -1;
            }
        }
        return count;
    }

    /** A band that votes white gets black text. Ties get white text. */
    public static TextColor textColorFor(int vote) {
        return vote > 0 ? TextColor.BLACK : TextColor.WHITE;
    }

    static boolean votesWhite(int argb) {
        return red(argb) > RED_THRESHOLD;
    }

    static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    /** The first sampled row strictly inside the band. */
    static int firstSampleRow(Band band) {
        return (band.top / SAMPLE_STEP + 1) * SAMPLE_STEP;
    }
}
