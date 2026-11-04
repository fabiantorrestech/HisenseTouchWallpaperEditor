package io.github.fabiantorrestech.touchwallpaper.ui;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/** Runs an action once on tap, then repeatedly while the button is held. */
final class HoldRepeater {
    private static final long FIRST_REPEAT_MILLIS = 450;
    private static final long NEXT_REPEAT_MILLIS = 150;

    private HoldRepeater() {
    }

    @SuppressLint("ClickableViewAccessibility")
    static void attach(View button, Runnable action) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable repeat = new Runnable() {
            @Override
            public void run() {
                action.run();
                handler.postDelayed(this, NEXT_REPEAT_MILLIS);
            }
        };
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    action.run();
                    handler.postDelayed(repeat, FIRST_REPEAT_MILLIS);
                    return true;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    handler.removeCallbacks(repeat);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(repeat);
                    return true;
                default:
                    return true;
            }
        });
    }
}
