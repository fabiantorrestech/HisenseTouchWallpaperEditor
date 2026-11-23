package io.github.fabiantorrestech.touchwallpaper.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.model.BackgroundTone;
import io.github.fabiantorrestech.touchwallpaper.model.Project;

/**
 * See-through projects: Off, Darken or Lighten, and how much, for the screenshot under the zones.
 * Every change goes through the host, so it's saved and becomes one undo step.
 */
final class BackgroundTonePanel {
    interface Host {
        Project project();

        void onSettingsChanged();
    }

    private final Host host;
    private final RadioGroup modeGroup;
    private final RadioButton off;
    private final RadioButton darken;
    private final RadioButton lighten;
    private final StepperControl amount;
    /** True while the controls are being set from the project, so they don't report a change. */
    private boolean binding;

    BackgroundTonePanel(Activity activity, Host host) {
        this.host = host;
        modeGroup = activity.findViewById(R.id.tone_mode);
        off = activity.findViewById(R.id.tone_off);
        darken = activity.findViewById(R.id.tone_darken);
        lighten = activity.findViewById(R.id.tone_lighten);
        amount = new StepperControl(activity).configure(activity.getString(R.string.tone_amount),
                BackgroundTone.MIN_PERCENT, BackgroundTone.MAX_PERCENT, BackgroundTone.STEP_PERCENT,
                BackgroundTone.DEFAULT_PERCENT, value -> value + "%", this::setPercent);
        ((ViewGroup) activity.findViewById(R.id.tone_amount_holder)).addView(amount);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!binding) {
                setMode(modeFor(checkedId));
            }
        });
    }

    /** Shows the project's current tone. Call when the panel opens and after undo or redo. */
    void show() {
        BackgroundTone tone = host.project().tone;
        binding = true;
        modeGroup.check(radioFor(tone.mode).getId());
        amount.setValue(tone.clampedPercent());
        binding = false;
        showAmount(tone.mode);
    }

    /** Reports an amount change that's still waiting for its delay. Call before leaving the panel. */
    void flush() {
        amount.commitNow();
    }

    private void setMode(BackgroundTone.Mode mode) {
        flush();
        host.project().tone.mode = mode;
        showAmount(mode);
        host.onSettingsChanged();
    }

    private void setPercent(int percent) {
        host.project().tone.percent = percent;
        host.onSettingsChanged();
    }

    /** No amount to set while the tone is off. */
    private void showAmount(BackgroundTone.Mode mode) {
        amount.setVisibility(mode == BackgroundTone.Mode.OFF ? View.GONE : View.VISIBLE);
    }

    private BackgroundTone.Mode modeFor(int radioId) {
        if (radioId == darken.getId()) {
            return BackgroundTone.Mode.DARKEN;
        }
        if (radioId == lighten.getId()) {
            return BackgroundTone.Mode.LIGHTEN;
        }
        return BackgroundTone.Mode.OFF;
    }

    private RadioButton radioFor(BackgroundTone.Mode mode) {
        switch (mode) {
            case DARKEN:
                return darken;
            case LIGHTEN:
                return lighten;
            default:
                return off;
        }
    }
}
