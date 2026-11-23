package io.github.fabiantorrestech.touchwallpaper.export;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A lossless PNG writer tuned for speed, for the see-through lock, which must write a new lock
 * image before the user presses power. Compared with {@code Bitmap.compress(PNG)} it uses the
 * fastest compression level and drops the alpha channel (screenshots are opaque). Every pixel is
 * still stored exactly, so forced text colors are unaffected.
 *
 * <p>Keeps its row buffers between calls, so it isn't thread safe: use one instance per thread.
 */
public final class FastPngEncoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int BYTES_PER_PIXEL = 3;
    private static final int COLOR_TYPE_RGB = 2;
    private static final int BIT_DEPTH = 8;
    /** PNG row filter "Up": each byte minus the byte above it. Suits pages with vertical repeats. */
    private static final int FILTER_UP = 2;
    private static final int DEFLATE_CHUNK = 64 * 1024;

    private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    private final CRC32 crc = new CRC32();
    private final byte[] deflated = new byte[DEFLATE_CHUNK];
    private int[] pixels = new int[0];
    private byte[] current = new byte[0];
    private byte[] previous = new byte[0];
    private byte[] filtered = new byte[0];

    public byte[] encode(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ensureBuffers(width);
        Arrays.fill(previous, (byte) 0);

        ByteArrayOutputStream imageData = new ByteArrayOutputStream(width * height);
        deflater.reset();
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            toRgb(width);
            filterUp(width);
            deflater.setInput(filtered, 0, 1 + width * BYTES_PER_PIXEL);
            while (!deflater.needsInput()) {
                imageData.write(deflated, 0, deflater.deflate(deflated));
            }
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
        deflater.finish();
        while (!deflater.finished()) {
            imageData.write(deflated, 0, deflater.deflate(deflated));
        }

        byte[] compressed = imageData.toByteArray();
        ByteArrayOutputStream png = new ByteArrayOutputStream(compressed.length + 64);
        png.write(SIGNATURE, 0, SIGNATURE.length);
        writeChunk(png, "IHDR", header(width, height));
        writeChunk(png, "IDAT", compressed);
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private void ensureBuffers(int width) {
        if (pixels.length != width) {
            int rowBytes = width * BYTES_PER_PIXEL;
            pixels = new int[width];
            current = new byte[rowBytes];
            previous = new byte[rowBytes];
            filtered = new byte[1 + rowBytes];
        }
    }

    private void toRgb(int width) {
        for (int x = 0, i = 0; x < width; x++) {
            int pixel = pixels[x];
            current[i++] = (byte) (pixel >> 16);
            current[i++] = (byte) (pixel >> 8);
            current[i++] = (byte) pixel;
        }
    }

    private void filterUp(int width) {
        filtered[0] = FILTER_UP;
        int rowBytes = width * BYTES_PER_PIXEL;
        for (int i = 0; i < rowBytes; i++) {
            filtered[i + 1] = (byte) (current[i] - previous[i]);
        }
    }

    private static byte[] header(int width, int height) {
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = BIT_DEPTH;
        header[9] = COLOR_TYPE_RGB;
        // Bytes 10 to 12 stay 0: deflate compression, adaptive filtering, no interlace.
        return header;
    }

    private void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] number = new byte[4];
        putInt(number, 0, data.length);
        out.write(number, 0, 4);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        crc.reset();
        crc.update(typeBytes);
        crc.update(data);
        putInt(number, 0, (int) crc.getValue());
        out.write(number, 0, 4);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
