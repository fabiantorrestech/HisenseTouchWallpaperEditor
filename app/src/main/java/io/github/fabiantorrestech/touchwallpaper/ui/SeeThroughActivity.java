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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
 * See-through lock options, opened from Settings. The see-through wallpaper itself is a project
 * (Home > New wallpaper > See-through), applied from the editor. Two pages instead of scrolling:
 * Setup (switch, permissions, hidden-app wallpaper) and Hidden apps (a paged checklist with
 * sensitive apps suggested first).
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
    private final Runnable commitFallback = this::commitFallback;

    private DeviceProfile profile;
    private WallpaperExporter exporter;
    private FallbackWallpaper fallback;
    private CheckBox enabledBox;
    private boolean binding;

    private final ActivityResultLauncher<String[]> fallbackImagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFallbackImagePicked);
    private final List<Choice> fallbackChoices = new ArrayList<>();
    private int fallbackIndex;
    /** The see-through project last applied, or null if it's gone. */
    private Project styleProject;

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

        findViewById(R.id.fallback_previous).setOnClickListener(view -> stepFallback(-1));
        findViewById(R.id.fallback_next).setOnClickListener(view -> stepFallback(1));
        findViewById(R.id.fallback_pick_image).setOnClickListener(view -> fallbackImagePicker.launch(new String[]{"image/*"}));

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
        if (on && styleProject == null) {
            binding = true;
            enabledBox.setChecked(false);
            binding = false;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.see_through_needs_project)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        String styleId = on ? styleProject.id : null;
        enabledBox.setEnabled(false);
        Background.run(() -> {
            try {
                if (on) {
                    SeeThrough.turnOn(this, settings, styleId);
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
        styleProject = null;
        String styleId = settings.seeThroughStyleProjectId();
        for (Project project : projects) {
            if (project.isSeeThrough() && project.id.equals(styleId)) {
                styleProject = project;
            }
        }
        ((TextView) findViewById(R.id.style_name)).setText(styleProject != null
                ? getString(R.string.see_through_using, styleProject.name)
                : getString(R.string.see_through_needs_project));

        fallbackChoices.clear();
        fallbackChoices.add(new Choice(null, getString(R.string.see_through_fallback_auto)));
        if (fallback.hasOriginal()) {
            fallbackChoices.add(new Choice(FallbackWallpaper.ORIGINAL, getString(R.string.see_through_fallback_original)));
        }
        for (Project project : projects) {
            if (!project.isSeeThrough()) {
                fallbackChoices.add(new Choice(project.id, project.name));
            }
        }
        if (FallbackWallpaper.IMAGE.equals(settings.seeThroughFallbackId())) {
            fallbackChoices.add(new Choice(FallbackWallpaper.IMAGE, getString(R.string.see_through_fallback_image)));
        }
        fallbackIndex = indexOf(fallbackChoices, settings.seeThroughFallbackId());
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

    private void showFallback() {
        boolean canChoose = fallbackChoices.size() > 1;
        findViewById(R.id.fallback_previous).setEnabled(canChoose);
        findViewById(R.id.fallback_next).setEnabled(canChoose);
        ((TextView) findViewById(R.id.fallback_name)).setText(fallbackChoices.get(fallbackIndex).label);
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

    /** A project is rendered once when chosen, so the service only ever copies a finished PNG. */
    private void commitFallback() {
        if (fallbackIndex >= fallbackChoices.size()) {
            return;
        }
        String id = fallbackChoices.get(fallbackIndex).id;
        if (id == null || FallbackWallpaper.ORIGINAL.equals(id) || FallbackWallpaper.IMAGE.equals(id)) {
            // Automatic and Original are read when needed; a picked image is already saved.
            settings.setSeeThroughFallbackId(id);
            return;
        }
        Background.run(() -> {
            try {
                fallback.saveChosen(FallbackWallpaper.renderProject(this, id));
                settings.setSeeThroughFallbackId(id);
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void onFallbackImagePicked(Uri image) {
        if (image == null) {
            return;
        }
        handler.removeCallbacks(commitFallback);
        Background.run(() -> {
            try {
                fallback.saveChosen(FallbackWallpaper.renderImage(this, image));
                settings.setSeeThroughFallbackId(FallbackWallpaper.IMAGE);
                runOnUiThread(this::loadChoices);
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
