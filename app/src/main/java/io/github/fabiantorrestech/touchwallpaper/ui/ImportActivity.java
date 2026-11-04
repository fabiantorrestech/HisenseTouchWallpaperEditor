package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.IOException;
import org.json.JSONException;

/** Receives a photo from Android's share menu and opens it as a new project. Has no screen of its own. */
public final class ImportActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri image = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (image == null) {
            finish();
            return;
        }
        ProjectStore store = new ProjectStore(this);
        Background.run(() -> {
            try {
                Project project = store.createFromImage(image);
                runOnUiThread(() -> {
                    startActivity(new Intent(this, CropActivity.class).putExtra(Extras.PROJECT_ID, project.id));
                    finish();
                });
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showErrorAndClose(e));
            }
        });
    }
}
