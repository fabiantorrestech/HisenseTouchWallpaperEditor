package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * A number setting with a slider, [-] and [+] buttons, and a value you can tap to type.
 * Only the number redraws while it changes. The new value is reported once changes stop, so a slow
 * panel isn't redrawn on every step.
 */
public final class StepperControl extends LinearLayout {
    private static final long COMMIT_DELAY_MILLIS = 600;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable commit = this::commitNow;
    private final TextView labelView;
    private final TextView valueView;
    private final SeekBar slider;

    private String label = "";
    private int min;
    private int max;
    private int step = 1;
    private int value;
    private int committedValue;
    private IntFunction<String> formatter = String::valueOf;
    private IntConsumer onCommit = newValue -> { };

    public StepperControl(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int gap = context.getResources().getDimensionPixelSize(R.dimen.gap_panel);
        int side = context.getResources().getDimensionPixelSize(R.dimen.gap_button);
        setPadding(side, gap, side, gap);
        LayoutInflater.from(context).inflate(R.layout.control_stepper, this, true);
        labelView = findViewById(R.id.stepper_label);
        valueView = findViewById(R.id.stepper_value);
        slider = findViewById(R.id.stepper_slider);
        valueView.setGravity(Gravity.CENTER);
        valueView.setOnClickListener(view -> askForNumber());
        HoldRepeater.attach(findViewById(R.id.stepper_minus), () -> changeBy(-step));
        HoldRepeater.attach(findViewById(R.id.stepper_plus), () -> changeBy(step));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    value = StepperControl.this.min + progress;
                    showValue();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(commit);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleCommit();
            }
        });
    }

    /** Sets everything at once, without reporting a change. */
    public StepperControl configure(String label, int min, int max, int step, int value,
                                    IntFunction<String> formatter, IntConsumer onCommit) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.formatter = formatter;
        this.onCommit = onCommit;
        labelView.setText(label);
        slider.setMax(max - min);
        setValue(value);
        return this;
    }

    public StepperControl configure(String label, int min, int max, int value, IntConsumer onCommit) {
        return configure(label, min, max, 1, value, String::valueOf, onCommit);
    }

    /** Updates the shown value without reporting a change. */
    public void setValue(int newValue) {
        handler.removeCallbacks(commit);
        value = clamp(newValue);
        committedValue = value;
        slider.setProgress(value - min);
        showValue();
    }

    /** Reports a pending change immediately. Call before leaving the screen. */
    public void commitNow() {
        handler.removeCallbacks(commit);
        if (value != committedValue) {
            committedValue = value;
            onCommit.accept(value);
        }
    }

    private void changeBy(int delta) {
        int changed = clamp(value + delta);
        if (changed != value) {
            value = changed;
            slider.setProgress(value - min);
            showValue();
        }
        scheduleCommit();
    }

    private void scheduleCommit() {
        handler.removeCallbacks(commit);
        handler.postDelayed(commit, COMMIT_DELAY_MILLIS);
    }

    private void showValue() {
        valueView.setText(formatter.apply(value));
    }

    private void askForNumber() {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.type_a_number, label, min, max))
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    try {
                        value = clamp(Integer.parseInt(input.getText().toString().trim()));
                        slider.setProgress(value - min);
                        showValue();
                        commitNow();
                    } catch (NumberFormatException e) {
                        showValue();
                    }
                })
                .show();
    }

    private int clamp(int number) {
        return Math.max(min, Math.min(max, number));
    }
}
