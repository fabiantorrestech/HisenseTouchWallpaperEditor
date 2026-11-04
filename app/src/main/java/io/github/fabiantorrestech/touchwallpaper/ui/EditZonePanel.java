package io.github.fabiantorrestech.touchwallpaper.ui;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.material.button.MaterialButton;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.TextColorChoice;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneMode;
import io.github.fabiantorrestech.touchwallpaper.model.ZonePreset;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Settings for one zone, showing only what applies to it: outline controls only when it has an
 * outline, coverage only for the quote, and text color only where the rule is known to apply.
 * Text color is stored per band, so zones sharing a band always show the same choice.
 */
final class EditZonePanel {
    interface Host {
        Project project();

        List<ZoneDefinition> zones();

        void onSettingsChanged();

        void onStartResize();
    }

    private enum Tab {
        STYLE, SIZE, TEXT
    }

    private static final int GRAY_PER_STEP = 17;
    private static final int MAX_GRAY_STEP = 15;
    private static final int MAX_OPACITY = 100;
    private static final int MAX_OUTLINE = 24;
    private static final int MAX_CORNERS = 60;
    private static final int MAX_PADDING = 48;
    private static final int MAX_SOFT_EDGE = 40;
    private static final int BLACK_GRAY = 0;
    private static final int WHITE_GRAY = 255;
    private static final int ROW_HEIGHT_DP = 48;

    private final Activity activity;
    private final Host host;
    private final LinearLayout pageContainer;
    private final CheckBox enabledBox;
    private final View textTab;
    private final View previousPage;
    private final View nextPage;
    private final List<StepperControl> activeSteppers = new ArrayList<>();
    private final List<Runnable> pages = new ArrayList<>();

    private ZoneDefinition zone;
    private Tab tab = Tab.STYLE;
    private int pageIndex;
    private boolean binding;

    EditZonePanel(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        pageContainer = activity.findViewById(R.id.edit_page);
        enabledBox = activity.findViewById(R.id.edit_enabled);
        textTab = activity.findViewById(R.id.tab_text);
        previousPage = activity.findViewById(R.id.edit_previous);
        nextPage = activity.findViewById(R.id.edit_next);
        activity.findViewById(R.id.tab_style).setOnClickListener(view -> showTab(Tab.STYLE));
        activity.findViewById(R.id.tab_size).setOnClickListener(view -> showTab(Tab.SIZE));
        textTab.setOnClickListener(view -> showTab(Tab.TEXT));
        previousPage.setOnClickListener(view -> showPage(pageIndex - 1));
        nextPage.setOnClickListener(view -> showPage(pageIndex + 1));
        enabledBox.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                style().enabled = checked;
                host.onSettingsChanged();
            }
        });
    }

    void show(ZoneDefinition newZone) {
        flush();
        zone = newZone;
        pageIndex = 0;
        tab = Tab.STYLE;
        rebuild();
    }

    ZoneDefinition zone() {
        return zone;
    }

    /** Reports any change still waiting on the stepper delay. Call before leaving this panel. */
    void flush() {
        for (StepperControl stepper : activeSteppers) {
            stepper.commitNow();
        }
    }

    /** Redraws from the current project, such as after an undo. */
    void rebuild() {
        binding = true;
        enabledBox.setText(zone.label);
        enabledBox.setChecked(style().enabled);
        binding = false;
        textTab.setVisibility(zone.hasTextColor ? View.VISIBLE : View.GONE);
        buildPages();
        showPage(pageIndex);
    }

    private ZoneStyle style() {
        return host.project().styleFor(zone.id);
    }

    /** Changing a zone's look turns it on, so the change is visible instead of seeming to do nothing. */
    private void onStyleEdited() {
        style().enabled = true;
        binding = true;
        enabledBox.setChecked(true);
        binding = false;
        host.onSettingsChanged();
    }

    private void showTab(Tab newTab) {
        flush();
        tab = newTab;
        pageIndex = 0;
        buildPages();
        showPage(0);
    }

    private void showPage(int requested) {
        pageIndex = Math.max(0, Math.min(pages.size() - 1, requested));
        flush();
        activeSteppers.clear();
        pageContainer.removeAllViews();
        pages.get(pageIndex).run();
        previousPage.setEnabled(pageIndex > 0);
        nextPage.setEnabled(pageIndex < pages.size() - 1);
    }

    private void buildPages() {
        pages.clear();
        if (tab == Tab.STYLE) {
            buildStylePages();
        } else if (tab == Tab.SIZE) {
            buildSizePages();
        } else {
            pages.add(this::addTextColorControls);
        }
    }

    private void buildStylePages() {
        ZoneMode mode = style().mode;
        if (mode == ZoneMode.OUTLINE) {
            pages.add(() -> {
                addModePicker();
                addOutlineGray();
            });
            pages.add(this::addOutlineWidth);
            return;
        }
        pages.add(() -> {
            addModePicker();
            addFillShortcuts();
            addFillGray();
        });
        if (mode == ZoneMode.BOTH) {
            pages.add(() -> {
                addOpacity();
                addOutlineGray();
            });
            pages.add(this::addOutlineWidth);
        } else {
            pages.add(this::addOpacity);
        }
    }

    private void buildSizePages() {
        if (!zone.presets.isEmpty()) {
            pages.add(this::addPresetPicker);
        }
        pages.add(() -> {
            addStepper(R.string.corners, MAX_CORNERS, style().cornerRadius, value -> style().cornerRadius = value);
            addStepper(R.string.padding, MAX_PADDING, style().padding, value -> style().padding = value);
        });
        pages.add(() -> {
            if (style().mode.drawsFill) {
                addStepper(R.string.soft_edge, MAX_SOFT_EDGE, style().softEdge, value -> style().softEdge = value);
            }
            addMoveAndReset();
        });
    }

    private void addModePicker() {
        RadioGroup group = horizontalGroup();
        addRadio(group, R.string.mode_outline, style().mode == ZoneMode.OUTLINE, () -> setMode(ZoneMode.OUTLINE));
        addRadio(group, R.string.mode_fill, style().mode == ZoneMode.FILL, () -> setMode(ZoneMode.FILL));
        addRadio(group, R.string.mode_both, style().mode == ZoneMode.BOTH, () -> setMode(ZoneMode.BOTH));
        pageContainer.addView(group);
    }

    private void setMode(ZoneMode mode) {
        if (style().mode == mode) {
            return;
        }
        style().mode = mode;
        onStyleEdited();
        pageContainer.post(() -> {
            pageIndex = 0;
            buildPages();
            showPage(0);
        });
    }

    private void addFillShortcuts() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.addView(shortcut(R.string.black, BLACK_GRAY), rowButtonParams());
        row.addView(shortcut(R.string.white, WHITE_GRAY), rowButtonParams());
        pageContainer.addView(row);
    }

    private View shortcut(@StringRes int label, int gray) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setOnClickListener(view -> {
            style().fillGray = gray;
            onStyleEdited();
            showPage(pageIndex);
        });
        return button;
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Math.round(ROW_HEIGHT_DP * density), 1f);
        int gap = activity.getResources().getDimensionPixelSize(R.dimen.gap_button);
        params.setMargins(gap, gap, gap, gap);
        return params;
    }

    private void addFillGray() {
        addStepper(R.string.fill_gray, MAX_GRAY_STEP, style().fillGray / GRAY_PER_STEP,
                value -> style().fillGray = value * GRAY_PER_STEP);
    }

    private void addOpacity() {
        addStepper(R.string.opacity, MAX_OPACITY, style().fillOpacityPercent, value -> style().fillOpacityPercent = value);
    }

    private void addOutlineGray() {
        addStepper(R.string.outline_gray, MAX_GRAY_STEP, style().outlineGray / GRAY_PER_STEP,
                value -> style().outlineGray = value * GRAY_PER_STEP);
    }

    private void addOutlineWidth() {
        addStepper(R.string.outline_width, MAX_OUTLINE, style().outlineWidth, value -> style().outlineWidth = value);
    }

    private void addStepper(@StringRes int label, int max, int value, IntConsumer apply) {
        StepperControl stepper = new StepperControl(activity).configure(activity.getString(label), 0, max, value, newValue -> {
            apply.accept(newValue);
            onStyleEdited();
        });
        activeSteppers.add(stepper);
        pageContainer.addView(stepper);
    }

    private void addPresetPicker() {
        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.VERTICAL);
        TextView note = new TextView(activity);
        ZonePreset current = currentPreset();
        for (ZonePreset preset : zone.presets) {
            addPresetRadio(group, preset, preset == current, note);
        }
        note.setText(current.note);
        pageContainer.addView(group);
        pageContainer.addView(note);
    }

    private ZonePreset currentPreset() {
        for (ZonePreset preset : zone.presets) {
            if (preset.id.equals(style().presetId)) {
                return preset;
            }
        }
        return zone.presets.get(0);
    }

    private void addPresetRadio(RadioGroup group, ZonePreset preset, boolean checked, TextView note) {
        RadioButton radio = new RadioButton(activity);
        radio.setId(View.generateViewId());
        radio.setText(preset.label);
        group.addView(radio);
        radio.setChecked(checked);
        radio.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                style().presetId = preset.id;
                style().customRect = null;
                note.setText(preset.note);
                onStyleEdited();
            }
        });
    }

    private void addMoveAndReset() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        MaterialButton move = new MaterialButton(activity);
        move.setText(R.string.move_or_resize);
        move.setOnClickListener(view -> host.onStartResize());
        MaterialButton reset = new MaterialButton(activity);
        reset.setText(R.string.reset_zone);
        reset.setOnClickListener(view -> {
            flush();
            boolean wasEnabled = style().enabled;
            host.project().zoneStyles.remove(zone.id);
            style().enabled = wasEnabled;
            host.onSettingsChanged();
            pageIndex = 0;
            rebuild();
        });
        row.addView(move, rowButtonParams());
        row.addView(reset, rowButtonParams());
        pageContainer.addView(row);
    }

    private void addTextColorControls() {
        Project project = host.project();
        TextColorChoice current = project.textColorChoices.get(zone.band);
        RadioGroup group = horizontalGroup();
        addRadio(group, R.string.choice_auto, current == TextColorChoice.AUTO, () -> setTextColor(TextColorChoice.AUTO));
        addRadio(group, R.string.black, current == TextColorChoice.FORCE_BLACK, () -> setTextColor(TextColorChoice.FORCE_BLACK));
        addRadio(group, R.string.white, current == TextColorChoice.FORCE_WHITE, () -> setTextColor(TextColorChoice.FORCE_WHITE));
        pageContainer.addView(group);

        TextView shared = new TextView(activity);
        String others = otherZonesInBand();
        shared.setText(others.isEmpty()
                ? activity.getString(R.string.text_not_shared)
                : activity.getString(R.string.text_shared_with, others));
        pageContainer.addView(shared);
    }

    private void setTextColor(TextColorChoice choice) {
        host.project().textColorChoices.put(zone.band, choice);
        host.onSettingsChanged();
    }

    private String otherZonesInBand() {
        List<String> names = new ArrayList<>();
        for (ZoneDefinition other : host.zones()) {
            if (other != zone && other.band == zone.band && other.hasTextColor) {
                names.add(other.label);
            }
        }
        return String.join(", ", names);
    }

    private RadioGroup horizontalGroup() {
        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.HORIZONTAL);
        return group;
    }

    private void addRadio(RadioGroup group, @StringRes int label, boolean checked, Runnable onChosen) {
        RadioButton radio = new RadioButton(activity);
        radio.setId(View.generateViewId());
        radio.setText(label);
        group.addView(radio, new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        radio.setChecked(checked);
        radio.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                onChosen.run();
            }
        });
    }
}
