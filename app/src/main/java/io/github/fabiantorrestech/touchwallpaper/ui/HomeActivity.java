package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfiles;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.seethrough.SeeThrough;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.IOException;
import org.json.JSONException;

/** The app's front door. Every other screen has a Home button that comes back here. */
public final class HomeActivity extends BaseActivity {
    private WallpaperExporter exporter;
    private TextView status;
    private View restoreButton;

    private final ActivityResultLauncher<String[]> imagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::createProject);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        exporter = new WallpaperExporter(this);
        status = findViewById(R.id.status);
        restoreButton = findViewById(R.id.restore_original);
        findViewById(R.id.new_wallpaper).setOnClickListener(view -> chooseWallpaperType());
        findViewById(R.id.projects).setOnClickListener(view -> startActivity(new Intent(this, ProjectListActivity.class)));
        findViewById(R.id.settings).setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.help).setOnClickListener(view -> startActivity(new Intent(this, WelcomeActivity.class)));
        restoreButton.setOnClickListener(view -> confirmRestore());
        findViewById(R.id.device_warning).setVisibility(DeviceProfiles.HISENSE_TOUCH_LITE_MODEL.equals(Build.MODEL) ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.device_warning)).setText(R.string.other_device_warning);
        if (!settings.welcomeShown()) {
            settings.setWelcomeShown();
            startActivity(new Intent(this, WelcomeActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        restoreButton.setVisibility(exporter.hasOriginalBackup() ? View.VISIBLE : View.GONE);
    }

    private void updateStatus() {
        int message;
        if (settings.seeThroughEnabled()) {
            // Every capture rewrites the wallpaper, so the apply status below would only flicker.
            status.setText(SeeThrough.isServiceOn(this)
                    ? R.string.status_see_through_on
                    : R.string.status_see_through_needs_service);
            status.setVisibility(View.VISIBLE);
            return;
        }
        switch (exporter.loadStatus()) {
            case WAITING_FOR_LOCK:
                message = R.string.status_waiting;
                break;
            case LOADED:
                message = R.string.status_loaded;
                break;
            case REPLACED_BY_ANOTHER_APP:
                message = R.string.status_replaced;
                break;
            default:
                status.setVisibility(View.GONE);
                return;
        }
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    /** A picture goes through Step 1; see-through has nothing to crop, so it opens Step 2 directly. */
    private void chooseWallpaperType() {
        String[] types = {getString(R.string.new_from_picture), getString(R.string.new_see_through)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_wallpaper)
                .setItems(types, (dialog, which) -> {
                    if (which == 0) {
                        imagePicker.launch(new String[]{"image/*"});
                    } else {
                        createSeeThroughProject();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createSeeThroughProject() {
        ProjectStore store = new ProjectStore(this);
        String name = getString(R.string.see_through);
        Background.run(() -> {
            try {
                Project project = store.createSeeThrough(name);
                runOnUiThread(() -> startActivity(new Intent(this, ZoneEditorActivity.class).putExtra(Extras.PROJECT_ID, project.id)));
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void createProject(Uri uri) {
        if (uri == null) {
            return;
        }
        ProjectStore store = new ProjectStore(this);
        Background.run(() -> {
            try {
                Project project = store.createFromImage(uri);
                runOnUiThread(() -> startActivity(new Intent(this, CropActivity.class).putExtra(Extras.PROJECT_ID, project.id)));
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (dialog, which) -> restore())
                .show();
    }

    private void restore() {
        Background.run(() -> {
            try {
                // A restored picture would be replaced by the next screenshot.
                settings.setSeeThroughEnabled(false);
                exporter.restoreOriginal();
                runOnUiThread(() -> {
                    updateStatus();
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.restored)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }
}
