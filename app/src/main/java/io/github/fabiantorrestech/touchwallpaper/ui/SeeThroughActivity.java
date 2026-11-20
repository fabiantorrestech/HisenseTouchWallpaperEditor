package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfile;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfiles;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.seethrough.FallbackWallpaper;
import io.github.fabiantorrestech.touchwallpaper.seethrough.InstalledApps;
import io.github.fabiantorrestech.touchwallpaper.seethrough.SeeThrough;
import io.github.fabiantorrestech.touchwallpaper.seethrough.SensitiveAppRules;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.json.JSONException;

/**
 * See-through lock settings. Two pages instead of scrolling: Setup (switch, permissions, style and
 * fallback pickers) and Hidden apps (a paged checklist with sensitive apps suggested first).
 */
public final class SeeThroughActivity extends BaseActivity {
    private static final int APPS_PER_PAGE = 8;
    /** Like the steppers: the label changes at once, the choice is saved once tapping stops. */
    private static final long COMMIT_DELAY_MILLIS = 600;
    private static final int APP_ROW_HEIGHT_DP = 48;

    private static final class Choice {
        final String id;
        final String label;

        Choice(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable commitStyle = this::commitStyle;
    private final Runnable commitFallback = this::commitFallback;

    private DeviceProfile profile;
    private WallpaperExporter exporter;
    private FallbackWallpaper fallback;
    private CheckBox enabledBox;
    private boolean binding;

    private final List<Choice> styleChoices = new ArrayList<>();
    private final List<Choice> fallbackChoices = new ArrayList<>();
    private int styleIndex;
    private int fallbackIndex;

    private List<InstalledApps.App> apps;
    private Set<String> hiddenApps;
    private LinearLayout appList;
    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_see_through);
        setUpTopBar(getString(R.string.see_through));
        profile = DeviceProfiles.detect(this);
        exporter = new WallpaperExporter(this);
        fallback = new FallbackWallpaper(this);
        appList = findViewById(R.id.app_list);
        hiddenApps = new HashSet<>(settings.seeThroughHiddenApps());

        findViewById(R.id.tab_setup).setOnClickListener(view -> showTab(true));
        findViewById(R.id.tab_apps).setOnClickListener(view -> showTab(false));

        enabledBox = findViewById(R.id.see_through_enabled);
        enabledBox.setOnCheckedChangeListener((box, checked) -> {
            if (!binding) {
                onSwitchChanged(checked);
            }
        });
        findViewById(R.id.open_accessibility).setOnClickListener(view -> openAccessibilitySettings());
        View allFiles = findViewById(R.id.allow_all_files);
        allFiles.setVisibility(profile.needsAllFilesAccess() ? View.VISIBLE : View.GONE);
        allFiles.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()))));

        findViewById(R.id.style_previous).setOnClickListener(view -> stepStyle(-1));
        findViewById(R.id.style_next).setOnClickListener(view -> stepStyle(1));
        findViewById(R.id.fallback_previous).setOnClickListener(view -> stepFallback(-1));
        findViewById(R.id.fallback_next).setOnClickListener(view -> stepFallback(1));

        bindCheck(R.id.see_through_on_screen_off, settings.seeThroughOnScreenOff(), settings::setSeeThroughOnScreenOff);
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        findViewById(R.id.see_through_probe).setVisibility(debuggable ? View.VISIBLE : View.GONE);
        bindCheck(R.id.see_through_probe, settings.seeThroughProbe(), settings::setSeeThroughProbe);

        findViewById(R.id.hide_suggested).setOnClickListener(view -> hideAllSuggested());
        findViewById(R.id.page_previous).setOnClickListener(view -> showAppPage(page - 1));
        findViewById(R.id.page_next).setOnClickListener(view -> showAppPage(page + 1));
        showTab(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding = true;
        enabledBox.setChecked(settings.seeThroughEnabled());
        binding = false;
        refreshStatus();
        loadChoices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save a choice still waiting for its delay rather than lose it.
        if (handler.hasCallbacks(commitStyle)) {
            handler.removeCallbacks(commitStyle);
            commitStyle();
        }
        if (handler.hasCallbacks(commitFallback)) {
            handler.removeCallbacks(commitFallback);
            commitFallback();
        }
    }

    private void bindCheck(@IdRes int id, boolean checked, Consumer<Boolean> onChange) {
        CheckBox box = findViewById(id);
        box.setChecked(checked);
        box.setOnCheckedChangeListener((button, isChecked) -> onChange.accept(isChecked));
    }

    private void showTab(boolean setup) {
        findViewById(R.id.setup_page).setVisibility(setup ? View.VISIBLE : View.GONE);
        findViewById(R.id.apps_page).setVisibility(setup ? View.GONE : View.VISIBLE);
        findViewById(R.id.tab_setup).setEnabled(!setup);
        findViewById(R.id.tab_apps).setEnabled(setup);
        if (!setup && apps == null) {
            loadApps();
        }
    }

    // Setup page

    private void refreshStatus() {
        List<String> lines = new ArrayList<>();
        String device = profile.hasMeasuredZones()
                ? getString(R.string.see_through_device_measured, profile.label)
                : getString(R.string.see_through_device_generic, profile.label);
        lines.add(getString(R.string.see_through_device, device));
        boolean serviceOn = SeeThrough.isServiceOn(this);
        lines.add(getString(serviceOn ? R.string.see_through_service_on : R.string.see_through_service_off));
        boolean filesOk = !profile.needsAllFilesAccess() || exporter.hasAllFilesAccess();
        if (profile.needsAllFilesAccess()) {
            lines.add(getString(filesOk ? R.string.see_through_files_on : R.string.see_through_files_off));
        }
        if (settings.seeThroughEnabled() && serviceOn && filesOk) {
            lines.add(getString(R.string.see_through_working));
        }
        ((TextView) findViewById(R.id.see_through_status)).setText(TextUtils.join("\n", lines));
    }

    private void onSwitchChanged(boolean on) {
        if (on && profile.needsAllFilesAccess() && !exporter.hasAllFilesAccess()) {
            binding = true;
            enabledBox.setChecked(false);
            binding = false;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_title)
                    .setMessage(R.string.permission_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.open_settings, (dialog, which) -> startActivity(
                            new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName()))))
                    .show();
            return;
        }
        enabledBox.setEnabled(false);
        Background.run(() -> {
            try {
                if (on) {
                    SeeThrough.turnOn(this, settings);
                    runOnUiThread(this::onTurnedOn);
                } else {
                    boolean restored = SeeThrough.turnOff(this, settings);
                    runOnUiThread(() -> onTurnedOff(restored));
                }
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    enabledBox.setEnabled(true);
                    refreshStatus();
                    showError(e);
                });
            }
        });
    }

    private void onTurnedOn() {
        enabledBox.setEnabled(true);
        refreshStatus();
        loadChoices();
        if (!SeeThrough.isServiceOn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.see_through_service_title)
                    .setMessage(R.string.see_through_service_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.open_settings, (dialog, which) -> openAccessibilitySettings())
                    .show();
        }
    }

    private void onTurnedOff(boolean restored) {
        enabledBox.setEnabled(true);
        refreshStatus();
        new AlertDialog.Builder(this)
                .setMessage(restored ? R.string.see_through_turned_off : R.string.see_through_turned_off_no_fallback)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void loadChoices() {
        ProjectStore store = new ProjectStore(this);
        Background.run(() -> {
            List<Project> projects = store.list();
            runOnUiThread(() -> showChoices(projects));
        });
    }

    private void showChoices(List<Project> projects) {
        styleChoices.clear();
        styleChoices.add(new Choice(null, getString(R.string.see_through_style_none)));
        if (profile.hasMeasuredZones()) {
            for (Project project : projects) {
                styleChoices.add(new Choice(project.id, project.name));
            }
        }
        styleIndex = indexOf(styleChoices, settings.seeThroughStyleProjectId());

        fallbackChoices.clear();
        fallbackChoices.add(new Choice(FallbackWallpaper.NONE, getString(R.string.see_through_fallback_none)));
        if (fallback.hasBefore()) {
            fallbackChoices.add(new Choice(FallbackWallpaper.BEFORE, getString(R.string.see_through_fallback_before)));
        }
        for (Project project : projects) {
            fallbackChoices.add(new Choice(project.id, project.name));
        }
        fallbackIndex = indexOf(fallbackChoices, settings.seeThroughFallbackId());
        showStyle();
        showFallback();
    }

    private static int indexOf(List<Choice> choices, String id) {
        for (int i = 0; i < choices.size(); i++) {
            if (Objects.equals(choices.get(i).id, id)) {
                return i;
            }
        }
        return 0;
    }

    private void showStyle() {
        boolean canChoose = styleChoices.size() > 1;
        findViewById(R.id.style_previous).setEnabled(canChoose);
        findViewById(R.id.style_next).setEnabled(canChoose);
        TextView name = findViewById(R.id.style_name);
        name.setText(profile.hasMeasuredZones()
                ? styleChoices.get(styleIndex).label
                : getString(R.string.see_through_style_unmeasured));
    }

    private void showFallback() {
        boolean canChoose = fallbackChoices.size() > 1;
        findViewById(R.id.fallback_previous).setEnabled(canChoose);
        findViewById(R.id.fallback_next).setEnabled(canChoose);
        ((TextView) findViewById(R.id.fallback_name)).setText(fallbackChoices.get(fallbackIndex).label);
    }

    private void stepStyle(int delta) {
        if (styleChoices.isEmpty()) {
            return;
        }
        styleIndex = Math.floorMod(styleIndex + delta, styleChoices.size());
        showStyle();
        handler.removeCallbacks(commitStyle);
        handler.postDelayed(commitStyle, COMMIT_DELAY_MILLIS);
    }

    private void stepFallback(int delta) {
        if (fallbackChoices.isEmpty()) {
            return;
        }
        fallbackIndex = Math.floorMod(fallbackIndex + delta, fallbackChoices.size());
        showFallback();
        handler.removeCallbacks(commitFallback);
        handler.postDelayed(commitFallback, COMMIT_DELAY_MILLIS);
    }

    private void commitStyle() {
        if (styleIndex < styleChoices.size()) {
            settings.setSeeThroughStyleProjectId(styleChoices.get(styleIndex).id);
        }
    }

    /** Renders the chosen project once, so the service only ever copies a finished PNG. */
    private void commitFallback() {
        if (fallbackIndex >= fallbackChoices.size()) {
            return;
        }
        String id = fallbackChoices.get(fallbackIndex).id;
        Background.run(() -> {
            try {
                if (FallbackWallpaper.NONE.equals(id)) {
                    fallback.clear();
                } else if (FallbackWallpaper.BEFORE.equals(id)) {
                    fallback.useBefore();
                } else {
                    fallback.save(FallbackWallpaper.renderProject(this, id));
                }
                settings.setSeeThroughFallbackId(id);
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    // Hidden apps page

    private void loadApps() {
        ((TextView) findViewById(R.id.apps_intro)).setText(R.string.loading_apps);
        Background.run(() -> {
            List<InstalledApps.App> loaded = InstalledApps.load(this);
            runOnUiThread(() -> {
                apps = loaded;
                showAppsIntro();
                showAppPage(0);
            });
        });
    }

    private void showAppsIntro() {
        int suggested = 0;
        for (InstalledApps.App app : apps) {
            if (app.suggestion != null) {
                suggested++;
            }
        }
        String intro = getString(R.string.hidden_apps_intro);
        if (suggested > 0) {
            intro += "\n" + getString(R.string.hidden_apps_suggested, suggested);
        }
        ((TextView) findViewById(R.id.apps_intro)).setText(intro);
        findViewById(R.id.hide_suggested).setEnabled(suggested > 0);
    }

    private int pageCount() {
        return Math.max(1, (apps.size() + APPS_PER_PAGE - 1) / APPS_PER_PAGE);
    }

    private void showAppPage(int requested) {
        if (apps == null) {
            return;
        }
        page = Math.max(0, Math.min(pageCount() - 1, requested));
        appList.removeAllViews();
        int rowHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, APP_ROW_HEIGHT_DP,
                getResources().getDisplayMetrics()));
        int end = Math.min(apps.size(), (page + 1) * APPS_PER_PAGE);
        for (int i = page * APPS_PER_PAGE; i < end; i++) {
            appList.addView(createAppRow(apps.get(i)), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));
        }
        findViewById(R.id.pager).setVisibility(pageCount() > 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_previous).setEnabled(page > 0);
        findViewById(R.id.page_next).setEnabled(page < pageCount() - 1);
        ((TextView) findViewById(R.id.page_label)).setText(getString(R.string.page_of, page + 1, pageCount()));
    }

    private View createAppRow(InstalledApps.App app) {
        CheckBox row = new CheckBox(this);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setText(app.suggestion == null
                ? app.label
                : getString(R.string.app_row_suggested, app.label, getString(reasonLabel(app.suggestion))));
        row.setChecked(hiddenApps.contains(app.packageName));
        row.setOnCheckedChangeListener((box, hidden) -> {
            if (hidden) {
                hiddenApps.add(app.packageName);
            } else {
                hiddenApps.remove(app.packageName);
            }
            settings.setSeeThroughHiddenApps(hiddenApps);
        });
        return row;
    }

    private void hideAllSuggested() {
        for (InstalledApps.App app : apps) {
            if (app.suggestion != null) {
                hiddenApps.add(app.packageName);
            }
        }
        settings.setSeeThroughHiddenApps(hiddenApps);
        showAppPage(page);
    }

    @StringRes
    private static int reasonLabel(SensitiveAppRules.Reason reason) {
        switch (reason) {
            case MONEY:
                return R.string.reason_money;
            case MESSAGES:
                return R.string.reason_messages;
            case EMAIL:
                return R.string.reason_email;
            case PASSWORDS:
                return R.string.reason_passwords;
            default:
                return R.string.reason_social;
        }
    }
}
