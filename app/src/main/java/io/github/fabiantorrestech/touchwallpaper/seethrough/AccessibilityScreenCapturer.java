package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;
import java.util.concurrent.Executor;

/**
 * Android 11's accessibility screenshot: any app, one-time consent, about one per second at most.
 * The frame arrives as a hardware buffer, which is copied to a software bitmap so the renderer can
 * read its pixels, then closed straight away.
 */
final class AccessibilityScreenCapturer implements ScreenCapturer {
    private final AccessibilityService service;
    private final Executor executor;

    AccessibilityScreenCapturer(AccessibilityService service, Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    @Override
    public void capture(Callback callback) {
        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, new AccessibilityService.TakeScreenshotCallback() {
            @Override
            public void onSuccess(AccessibilityService.ScreenshotResult result) {
                Bitmap frame = null;
                HardwareBuffer buffer = result.getHardwareBuffer();
                try {
                    Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware != null) {
                        frame = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        hardware.recycle();
                    }
                } catch (RuntimeException e) {
                    callback.onFailed("couldn't read the screenshot: " + e, false);
                    return;
                } finally {
                    buffer.close();
                }
                if (frame == null) {
                    callback.onFailed("couldn't read the screenshot", false);
                } else {
                    callback.onCaptured(frame);
                }
            }

            @Override
            public void onFailure(int errorCode) {
                callback.onFailed("screenshot error " + errorCode,
                        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT);
            }
        });
    }
}
