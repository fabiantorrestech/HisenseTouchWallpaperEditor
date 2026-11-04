package io.github.fabiantorrestech.touchwallpaper.eink;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;

/**
 * Clears e-ink ghosting. Uses the ROM's forceClear() when this app is allowed to call it, the same
 * way SystemUI does (ADR 5). Otherwise it flashes the screen black, then white, then back. If the
 * ROM refuses the call once, it isn't tried again.
 */
public final class EinkRefresher {
    private static final String TAG = "EinkRefresher";
    /** Long enough for the slowest panel update to finish before the next color. */
    private static final long FLASH_STEP_MILLIS = 180;

    private static volatile boolean romCallUnavailable = false;

    private EinkRefresher() {
    }

    public static void refresh(Activity activity) {
        if (romCallUnavailable) {
            flash(activity);
            return;
        }
        new Thread(() -> {
            boolean cleared = tryRomForceClear();
            if (!cleared) {
                romCallUnavailable = true;
                activity.runOnUiThread(() -> flash(activity));
            }
        }, "eink-refresh").start();
    }

    private static boolean tryRomForceClear() {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "epd");
            if (binder == null) {
                Log.i(TAG, "No e-ink service on this device, using a screen flash");
                return false;
            }
            Class<?> stub = Class.forName("com.hmct.epd.IEpdManager$Stub");
            Object manager = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Object result = manager.getClass().getMethod("forceClear").invoke(manager);
            Log.i(TAG, "ROM refresh returned " + result);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.i(TAG, "ROM refresh unavailable, using a screen flash: " + e);
            return false;
        }
    }

    private static void flash(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ViewGroup root = activity.findViewById(android.R.id.content);
        View cover = new View(activity);
        cover.setBackgroundColor(Color.BLACK);
        cover.setClickable(true);
        root.addView(cover, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> cover.setBackgroundColor(Color.WHITE), FLASH_STEP_MILLIS);
        handler.postDelayed(() -> root.removeView(cover), 2 * FLASH_STEP_MILLIS);
    }
}
