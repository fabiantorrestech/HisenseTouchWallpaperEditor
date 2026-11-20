package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.graphics.Bitmap;

/** Takes a picture of what's on screen. One implementation per capture method (ADR 6). */
public interface ScreenCapturer {
    interface Callback {
        /** A software ARGB_8888 frame. The receiver owns it and must recycle it. */
        void onCaptured(Bitmap frame);

        /** {@code retrySoon} is true when trying again after the system's rate limit should work. */
        void onFailed(String reason, boolean retrySoon);
    }

    void capture(Callback callback);
}
