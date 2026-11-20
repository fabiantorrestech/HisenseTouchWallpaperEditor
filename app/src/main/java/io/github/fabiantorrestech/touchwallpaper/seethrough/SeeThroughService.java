package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfile;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfiles;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.json.JSONException;

/**
 * See-through lock (ADR 6). A normal app can't catch the power button, so this keeps the lock
 * screen image current instead: push events (accessibility events, touches, keys) mark the screen
 * dirty, and once it settles the screen is captured, drawn by the renderer and applied. Pressing
 * power then shows a fresh image without waiting for anything. Nothing here polls.
 *
 * <p>Threads: accessibility callbacks arrive on the main thread and only post to one worker
 * thread, which owns all other state. Logs under {@link #TAG} are the measurements that
 * research/see-through-spike.md asks for.
 */
public final class SeeThroughService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String TAG = "SeeThrough";

    /** How long the screen must stay still before it's captured. Tune from the spike's timings. */
    private static final long QUIET_MILLIS = 1200;
    /** Screens that never settle, such as animations, are captured at most this often. */
    private static final long MAX_WAIT_MILLIS = 10000;
    /** Android 11 allows one accessibility screenshot per second; a little slack avoids refusals. */
    private static final long MIN_INTERVAL_MILLIS = 1100;
    private static final String SYSTEM_UI = "com.android.systemui";
    /** Every 4th pixel of every 4th row is enough to spot a black frame. */
    private static final int SAMPLE_STEP = 4;
    private static final int DARK_LEVEL = 16;
    private static final int BLACK_FRAME_PERCENT = 99;

    private final CaptureScheduler scheduler = new CaptureScheduler(QUIET_MILLIS, MAX_WAIT_MILLIS, MIN_INTERVAL_MILLIS);
    private final Runnable normalCapture = () -> captureIfDue(false);
    private final Runnable urgentCapture = () -> captureIfDue(true);
    private final Runnable markActivity = this::onScreenActivity;

    /** Read on the main thread to drop events early; written on the worker. */
    private volatile boolean enabled;

    private final Handler main = new Handler(Looper.getMainLooper());
    private AppSettings settings;
    private HandlerThread thread;
    private Handler worker;
    private BroadcastReceiver screenReceiver;
    private View touchWatcher;

    // Worker thread only.
    private DeviceProfile profile;
    private LockScreenTarget target;
    private ScreenCapturer capturer;
    private SeeThroughComposer composer;
    private FallbackWallpaper fallback;
    private Project style;
    private Set<String> hiddenApps = Collections.emptySet();
    private boolean captureOnScreenOff;
    private boolean probe;
    private boolean screenOn = true;
    private boolean showingFallback;
    private long lastFrameHash;
    private long frameHash;
    private int[] row;

    @Override
    protected void onServiceConnected() {
        settings = new AppSettings(this);
        thread = new HandlerThread("see-through");
        thread.start();
        worker = new Handler(thread.getLooper());
        profile = DeviceProfiles.detect(this);
        target = profile.createTarget(this);
        capturer = new AccessibilityScreenCapturer(this, worker::post);
        composer = new SeeThroughComposer(this, profile);
        fallback = new FallbackWallpaper(this);
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    onScreenOff();
                } else {
                    onScreenOn();
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // Delivered on the worker, so screen-off work doesn't wait behind the main thread.
        registerReceiver(screenReceiver, filter, null, worker);
        settings.registerListener(this);
        Log.i(TAG, "connected on " + profile.label);
        worker.post(this::reloadSettings);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key != null && key.startsWith("see_through")) {
            worker.post(this::reloadSettings);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!enabled) {
            return;
        }
        // The status bar clock changes every minute; it alone isn't worth a new lock screen.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getPackageName() != null && SYSTEM_UI.contentEquals(event.getPackageName())) {
            return;
        }
        worker.post(markActivity);
    }

    /** Observes keys such as page turns. Never consumes one. */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
            // Spike S1: expected never to appear, because the system handles power first.
            Log.i(TAG, "saw the power key, action " + event.getAction());
        }
        if (enabled && event.getAction() == KeyEvent.ACTION_UP) {
            worker.post(markActivity);
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (settings != null) {
            settings.unregisterListener(this);
        }
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        showTouchWatcher(false);
        if (thread != null) {
            thread.quitSafely();
        }
        super.onDestroy();
    }

    // Worker thread from here on, except the touch watcher.

    private void reloadSettings() {
        boolean wasEnabled = enabled;
        enabled = settings.seeThroughEnabled();
        hiddenApps = settings.seeThroughHiddenApps();
        captureOnScreenOff = settings.seeThroughOnScreenOff();
        probe = settings.seeThroughProbe() && (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        style = loadStyle(settings.seeThroughStyleProjectId());
        // The style may have changed, so the next frame is written even if the screen hasn't.
        lastFrameHash = 0;
        boolean on = enabled;
        main.post(() -> showTouchWatcher(on));
        if (!enabled) {
            worker.removeCallbacks(normalCapture);
            worker.removeCallbacks(urgentCapture);
            scheduler.clear();
            composer.release();
        } else if (!wasEnabled) {
            onScreenActivity();
        }
        Log.i(TAG, "settings: enabled " + enabled + ", style " + (style != null ? style.name : "none")
                + ", hidden apps " + hiddenApps.size() + ", on screen off " + captureOnScreenOff + ", probe " + probe);
    }

    private Project loadStyle(String projectId) {
        if (projectId == null || !profile.hasMeasuredZones()) {
            return null;
        }
        try {
            return new ProjectStore(this).load(projectId);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Style project unavailable, using the plain screenshot", e);
            return null;
        }
    }

    private void onScreenActivity() {
        // While probing, only the solid test images may reach the lock screen.
        if (!enabled || !screenOn || probe) {
            return;
        }
        schedule(normalCapture, scheduler.onActivity(SystemClock.uptimeMillis()));
    }

    private void schedule(Runnable capture, long delay) {
        worker.removeCallbacks(capture);
        if (delay != CaptureScheduler.NOT_DUE) {
            worker.postDelayed(capture, delay);
        }
    }

    private void onScreenOff() {
        Log.i(TAG, "screen off, dirty " + scheduler.isDirty());
        screenOn = false;
        worker.removeCallbacks(normalCapture);
        if (!enabled) {
            return;
        }
        if (probe) {
            writeProbe(Color.BLACK);
        } else if (captureOnScreenOff && scheduler.isDirty()) {
            captureIfDue(true);
        } else {
            scheduler.clear();
        }
    }

    private void onScreenOn() {
        Log.i(TAG, "screen on");
        screenOn = true;
        worker.removeCallbacks(urgentCapture);
        scheduler.clear();
        if (enabled && probe) {
            writeProbe(Color.WHITE);
        }
    }

    private void captureIfDue(boolean urgent) {
        if (!enabled || (!screenOn && !urgent)) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!scheduler.tryStart(now, urgent)) {
            schedule(urgent ? urgentCapture : normalCapture,
                    urgent ? scheduler.delayUntilUrgentDue(now) : scheduler.delayUntilDue(now));
            return;
        }
        String problem = target.problem();
        if (problem != null) {
            Log.w(TAG, "Can't apply: " + problem);
            finishCapture(false);
            return;
        }
        String app = foregroundPackage();
        if (SYSTEM_UI.equals(app)) {
            // The notification shade or keyguard: keep the last frame of the app behind it.
            Log.i(TAG, "skipped: system UI in front");
            finishCapture(false);
            return;
        }
        if (app != null && hiddenApps.contains(app)) {
            showFallbackOnce(app);
            finishCapture(false);
            return;
        }
        long requestedAt = SystemClock.uptimeMillis();
        capturer.capture(new ScreenCapturer.Callback() {
            @Override
            public void onCaptured(Bitmap frame) {
                handleFrame(frame, app, requestedAt, urgent);
                finishCapture(false);
            }

            @Override
            public void onFailed(String reason, boolean retrySoon) {
                Log.w(TAG, "capture failed: " + reason);
                finishCapture(retrySoon);
            }
        });
    }

    private void finishCapture(boolean retry) {
        long delay = scheduler.onFinished(SystemClock.uptimeMillis(), retry);
        if (screenOn) {
            schedule(normalCapture, delay);
        } else if (retry && captureOnScreenOff) {
            schedule(urgentCapture, scheduler.delayUntilUrgentDue(SystemClock.uptimeMillis()));
        }
    }

    private void handleFrame(Bitmap frame, String app, long requestedAt, boolean urgent) {
        try {
            long captured = SystemClock.uptimeMillis();
            if (frame.getWidth() > frame.getHeight()) {
                Log.i(TAG, "skipped: landscape frame");
                return;
            }
            int darkPercent = scanFrame(frame);
            long hash = frameHash;
            if (darkPercent >= BLACK_FRAME_PERCENT) {
                if (urgent) {
                    // The display was already off: writing this would put a black lock screen up.
                    Log.i(TAG, "skipped: black frame after screen off");
                } else {
                    // Some apps, such as KOReader on this device, come back black: their screen
                    // isn't visible to Android screenshots. Treat them like a hidden app.
                    Log.i(TAG, "black frame from " + app);
                    showFallbackOnce(app);
                }
                return;
            }
            if (hash == lastFrameHash && !showingFallback) {
                Log.i(TAG, "skipped: screen unchanged");
                return;
            }
            // Switched off while capturing: don't overwrite the fallback the UI just put back.
            if (!enabled) {
                return;
            }
            long scanned = SystemClock.uptimeMillis();
            Bitmap wallpaper = composer.compose(frame, style != null ? style : new Project());
            long composed = SystemClock.uptimeMillis();
            target.apply(wallpaper);
            lastFrameHash = hash;
            showingFallback = false;
            Log.i(TAG, (urgent ? "screen-off " : "") + "applied: capture " + (captured - requestedAt)
                    + " ms, scan " + (scanned - captured) + " ms, compose " + (composed - scanned)
                    + " ms, apply " + (SystemClock.uptimeMillis() - composed) + " ms, dark " + darkPercent + "%");
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Couldn't apply the screenshot", e);
        } finally {
            frame.recycle();
        }
    }

    /**
     * Hashes the whole frame into {@link #frameHash}, so an unchanged screen isn't written again,
     * and returns the share of dark samples, which tells a black frame (spike S2, S7) from a real one.
     */
    private int scanFrame(Bitmap frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (row == null || row.length < width) {
            row = new int[width];
        }
        long hash = 1125899906842597L;
        int samples = 0;
        int dark = 0;
        for (int y = 0; y < height; y++) {
            frame.getPixels(row, 0, width, 0, y, width, 1);
            boolean sampleRow = y % SAMPLE_STEP == 0;
            for (int x = 0; x < width; x++) {
                int pixel = row[x];
                hash = 31 * hash + pixel;
                if (sampleRow && x % SAMPLE_STEP == 0) {
                    samples++;
                    if (Color.red(pixel) < DARK_LEVEL && Color.green(pixel) < DARK_LEVEL && Color.blue(pixel) < DARK_LEVEL) {
                        dark++;
                    }
                }
            }
        }
        frameHash = hash == 0 ? 1 : hash;
        return samples == 0 ? 0 : dark * 100 / samples;
    }

    /** The package of the window the user is using, or null if it can't be told. */
    private String foregroundPackage() {
        List<AccessibilityWindowInfo> windows = getWindows();
        String result = null;
        for (AccessibilityWindowInfo window : windows) {
            if (result == null && window.isActive()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    CharSequence name = root.getPackageName();
                    result = name != null ? name.toString() : null;
                    root.recycle();
                }
            }
            window.recycle();
        }
        return result;
    }

    private void showFallbackOnce(String app) {
        if (showingFallback) {
            return;
        }
        try {
            byte[] png = fallback.resolve(settings.seeThroughFallbackId());
            if (png == null) {
                Log.i(TAG, app + " not shown, no fallback: keeping the last image");
                return;
            }
            target.applyPng(png);
            showingFallback = true;
            lastFrameHash = 0;
            Log.i(TAG, app + " not shown: fallback applied");
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Couldn't apply the fallback", e);
        }
    }

    /** Spike S3: a solid image whose keyguard vote shows whether this write beat the lock screen. */
    private void writeProbe(int color) {
        Bitmap solid = Bitmap.createBitmap(profile.width, profile.height, Bitmap.Config.ARGB_8888);
        try {
            solid.eraseColor(color);
            long start = SystemClock.uptimeMillis();
            target.apply(solid);
            Log.i(TAG, "probe wrote " + (color == Color.BLACK ? "black" : "white") + " in "
                    + (SystemClock.uptimeMillis() - start) + " ms");
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Probe write failed", e);
        } finally {
            solid.recycle();
        }
    }

    /**
     * A 1×1 invisible window that hears about every touch elsewhere (as ACTION_OUTSIDE), for apps
     * that draw on a canvas and send no accessibility events, such as many e-readers.
     */
    private void showTouchWatcher(boolean show) {
        WindowManager windows = getSystemService(WindowManager.class);
        if (show && touchWatcher == null) {
            View view = new View(this);
            view.setOnTouchListener((touched, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE && enabled) {
                    worker.post(markActivity);
                }
                return false;
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSPARENT);
            params.gravity = Gravity.TOP | Gravity.START;
            try {
                windows.addView(view, params);
                touchWatcher = view;
            } catch (RuntimeException e) {
                Log.w(TAG, "Touch watcher unavailable; relying on accessibility events", e);
            }
        } else if (!show && touchWatcher != null) {
            windows.removeView(touchWatcher);
            touchWatcher = null;
        }
    }
}
