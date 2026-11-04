package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectBackup;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.IOException;
import java.util.function.Consumer;

/** E-ink options, where files are saved, and project backup. */
public final class SettingsActivity extends BaseActivity {
    private static final String BACKUP_FILE_NAME = "touch-wallpaper-projects.zip";
    private static final String ZIP_TYPE = "application/zip";

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onFolderChosen);
    private final ActivityResultLauncher<String> backupTarget =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(ZIP_TYPE), this::onBackupTargetChosen);
    private final ActivityResultLauncher<String[]> backupSource =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBackupSourceChosen);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setUpTopBar(getString(R.string.settings));
        bindCheck(R.id.eink_look, settings.einkLook(), settings::setEinkLook);
        bindCheck(R.id.no_animations, settings.noAnimations(), settings::setNoAnimations);
        bindCheck(R.id.refresh_on_switch, settings.refreshOnSwitch(), settings::setRefreshOnSwitch);
        bindCheck(R.id.ask_each_time, settings.askEachTime(), settings::setAskEachTime);
        bindCheck(R.id.technical_details, settings.technicalDetails(), settings::setTechnicalDetails);
        findViewById(R.id.choose_folder).setOnClickListener(view -> folderPicker.launch(null));
        findViewById(R.id.use_default_folder).setOnClickListener(view -> {
            settings.setExportFolder(null, null);
            showExportFolder();
        });
        findViewById(R.id.back_up).setOnClickListener(view -> backupTarget.launch(BACKUP_FILE_NAME));
        findViewById(R.id.restore_projects).setOnClickListener(view -> backupSource.launch(new String[]{ZIP_TYPE}));
        showExportFolder();
    }

    private void bindCheck(@IdRes int id, boolean checked, Consumer<Boolean> onChange) {
        CheckBox box = findViewById(id);
        box.setChecked(checked);
        box.setOnCheckedChangeListener((button, isChecked) -> onChange.accept(isChecked));
    }

    private void showExportFolder() {
        String label = settings.exportFolderLabel();
        ((TextView) findViewById(R.id.export_folder)).setText(getString(R.string.export_folder_now,
                label != null ? label : getString(R.string.default_export_folder)));
    }

    private void onFolderChosen(Uri tree) {
        if (tree == null) {
            return;
        }
        getContentResolver().takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        settings.setExportFolder(tree.toString(), ImageSaver.labelForTree(tree));
        showExportFolder();
    }

    private ProjectBackup backup() {
        return new ProjectBackup(new ProjectStore(this).rootFolder(), getContentResolver());
    }

    private void onBackupTargetChosen(Uri target) {
        if (target == null) {
            return;
        }
        Background.run(() -> {
            try {
                int count = backup().backUpTo(target);
                runOnUiThread(() -> showMessage(getString(R.string.backed_up, count)));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void onBackupSourceChosen(Uri source) {
        if (source == null) {
            return;
        }
        Background.run(() -> {
            try {
                int count = backup().restoreFrom(source);
                runOnUiThread(() -> showMessage(getString(R.string.restored_projects, count)));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
