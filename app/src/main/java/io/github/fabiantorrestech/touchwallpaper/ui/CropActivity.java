package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.button.MaterialButton;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.export.SavedFile;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.render.RenderResult;
import io.github.fabiantorrestech.touchwallpaper.render.SourceImageLoader;
import io.github.fabiantorrestech.touchwallpaper.render.WallpaperRenderer;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectHistory;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectJson;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import io.github.fabiantorrestech.touchwallpaper.storage.ZoneCatalog;
import java.io.IOException;
import java.util.List;
import org.json.JSONException;

/** Step 1: place the image. Changing the crop never touches Step 2's work. */
public final class CropActivity extends BaseActivity implements CropCanvasView.Listener, PngSaveFlow.Listener {
    private static final int GRAY_PER_STEP = 17;
    private static final int MAX_GRAY_STEP = 15;
    private static final int ZOOM_STEP = 5;
    private static final int[] NUDGE_SIZES = {1, 10, 50};
    private static final long NUDGE_SAVE_DELAY_MILLIS = 600;

    private final PngSaveFlow saveFlow = new PngSaveFlow(this, this);
    private final ActivityResultLauncher<String[]> imagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::changeImage);
    private final Runnable commitNudges = this::commit;

    private ProjectStore store;
    private CropCanvasView canvas;
    private StepperControl zoomStepper;
    private StepperControl backgroundStepper;
    private MaterialButton undoButton;
    private MaterialButton redoButton;
    private Project project;
    private SourceImage source;
    private List<ZoneDefinition> zones;
    private ProjectHistory history;
    /** Created on first use and only touched on the worker thread. */
    private WallpaperRenderer renderer;
    private int nudgeSizeIndex = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);
        setUpTopBar(getString(R.string.crop_title));
        store = new ProjectStore(this);
        canvas = findViewById(R.id.canvas);
        undoButton = findViewById(R.id.undo);
        redoButton = findViewById(R.id.redo);
        String projectId = getIntent().getStringExtra(Extras.PROJECT_ID);
        Background.run(() -> loadProject(projectId));
    }

    private void loadProject(String projectId) {
        try {
            Project loaded = store.load(projectId);
            SourceImage image = SourceImageLoader.load(this, store.sourceFileOf(loaded));
            List<ZoneDefinition> definitions = ZoneCatalog.loadZones(this);
            runOnUiThread(() -> onLoaded(loaded, image, definitions));
        } catch (IOException | JSONException | RuntimeException e) {
            runOnUiThread(() -> showErrorAndClose(e));
        }
    }

    private void onLoaded(Project loaded, SourceImage image, List<ZoneDefinition> definitions) {
        project = loaded;
        source = image;
        zones = definitions;
        canvas.bind(source, project.crop, zones, this);
        try {
            history = new ProjectHistory(project);
        } catch (JSONException e) {
            showErrorAndClose(e);
            return;
        }
        bindControls();
        updateUndoButtons();
    }

    private void bindControls() {
        zoomStepper = new StepperControl(this).configure(getString(R.string.zoom),
                CropCanvasView.MIN_ZOOM_PERCENT, CropCanvasView.MAX_ZOOM_PERCENT, ZOOM_STEP, canvas.zoomPercent(),
                value -> getString(R.string.percent, value),
                value -> {
                    canvas.setZoomPercent(value);
                    commit();
                });
        ((FrameLayout) findViewById(R.id.zoom_holder)).addView(zoomStepper);
        backgroundStepper = new StepperControl(this).configure(getString(R.string.background_gray),
                0, MAX_GRAY_STEP, project.crop.backgroundGray / GRAY_PER_STEP,
                value -> {
                    project.crop.backgroundGray = value * GRAY_PER_STEP;
                    canvas.invalidate();
                    commit();
                });
        ((FrameLayout) findViewById(R.id.background_holder)).addView(backgroundStepper);

        findViewById(R.id.tab_position).setOnClickListener(view -> showPage(true));
        findViewById(R.id.tab_more).setOnClickListener(view -> showPage(false));
        HoldRepeater.attach(findViewById(R.id.nudge_left), () -> nudge(-1, 0));
        HoldRepeater.attach(findViewById(R.id.nudge_right), () -> nudge(1, 0));
        HoldRepeater.attach(findViewById(R.id.nudge_up), () -> nudge(0, -1));
        HoldRepeater.attach(findViewById(R.id.nudge_down), () -> nudge(0, 1));
        findViewById(R.id.nudge_step).setOnClickListener(view -> {
            nudgeSizeIndex = (nudgeSizeIndex + 1) % NUDGE_SIZES.length;
            showNudgeSize();
        });
        showNudgeSize();

        CheckBox showZones = findViewById(R.id.show_zones);
        showZones.setOnCheckedChangeListener((button, checked) -> canvas.setShowZones(checked));
        findViewById(R.id.rotate).setOnClickListener(view -> {
            project.crop.rotateClockwise(source);
            afterCropButton();
        });
        findViewById(R.id.reset).setOnClickListener(view -> {
            project.crop.quarterTurns = 0;
            project.crop.resetToCover(source);
            afterCropButton();
        });
        findViewById(R.id.change_image).setOnClickListener(view -> imagePicker.launch(new String[]{"image/*"}));
        undoButton.setOnClickListener(view -> undo());
        redoButton.setOnClickListener(view -> redo());
        findViewById(R.id.save_png).setOnClickListener(view -> savePng());
        findViewById(R.id.next).setOnClickListener(view -> {
            flushPending();
            startActivity(new Intent(this, ZoneEditorActivity.class).putExtra(Extras.PROJECT_ID, project.id));
            finish();
        });
    }

    private void showPage(boolean position) {
        flushPending();
        findViewById(R.id.page_position).setVisibility(position ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_more).setVisibility(position ? View.GONE : View.VISIBLE);
    }

    private void showNudgeSize() {
        ((MaterialButton) findViewById(R.id.nudge_step))
                .setText(getString(R.string.move_step, NUDGE_SIZES[nudgeSizeIndex]));
    }

    private void nudge(int directionX, int directionY) {
        int size = NUDGE_SIZES[nudgeSizeIndex];
        canvas.nudge(directionX * size, directionY * size);
        canvas.removeCallbacks(commitNudges);
        canvas.postDelayed(commitNudges, NUDGE_SAVE_DELAY_MILLIS);
    }

    private void afterCropButton() {
        canvas.invalidate();
        zoomStepper.setValue(canvas.zoomPercent());
        commit();
    }

    @Override
    public void onCropGestureFinished() {
        zoomStepper.setValue(canvas.zoomPercent());
        commit();
    }

    /** Saves and adds an undo step. Does nothing if nothing changed. */
    private void commit() {
        try {
            history.record(project);
            store.save(project);
            updateUndoButtons();
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private void updateUndoButtons() {
        undoButton.setEnabled(history.canUndo());
        redoButton.setEnabled(history.canRedo());
    }

    private void undo() {
        flushPending();
        if (history.canUndo()) {
            try {
                showRestored(history.undo(project));
            } catch (JSONException e) {
                showError(e);
            }
        }
    }

    private void redo() {
        flushPending();
        if (history.canRedo()) {
            try {
                showRestored(history.redo(project));
            } catch (JSONException e) {
                showError(e);
            }
        }
    }

    private void showRestored(Project restored) {
        project = restored;
        canvas.bind(source, project.crop, zones, this);
        zoomStepper.setValue(canvas.zoomPercent());
        backgroundStepper.setValue(project.crop.backgroundGray / GRAY_PER_STEP);
        try {
            store.save(project);
        } catch (IOException | JSONException e) {
            showError(e);
        }
        updateUndoButtons();
    }

    private void changeImage(Uri uri) {
        if (uri == null) {
            return;
        }
        flushPending();
        Project target = project;
        Background.run(() -> {
            try {
                SourceImage image = store.replaceSource(target, uri);
                runOnUiThread(() -> onImageChanged(image));
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    /** Earlier undo steps are measured against the old image, so they no longer apply. */
    private void onImageChanged(SourceImage image) {
        source = image;
        canvas.bind(source, project.crop, zones, this);
        zoomStepper.setValue(canvas.zoomPercent());
        try {
            history.clear(project);
        } catch (JSONException e) {
            showError(e);
        }
        updateUndoButtons();
    }

    private void savePng() {
        flushPending();
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        SourceImage image = source;
        saveFlow.start(project.name, () -> {
            try {
                Project snapshot = ProjectJson.fromCurrentJson(json);
                return ImageSaver.encodePng(renderer().renderCropOnly(image, snapshot).bitmap);
            } catch (JSONException e) {
                throw new IOException(e.getMessage(), e);
            }
        });
    }

    @Override
    public void onPngSaved(SavedFile file) {
        project.lastSavedUri = file.uri;
        project.lastSavedFolderUri = file.folderUri;
        project.lastSavedPath = file.path;
        try {
            store.save(project);
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private WallpaperRenderer renderer() {
        if (renderer == null) {
            renderer = new WallpaperRenderer();
        }
        return renderer;
    }

    private String currentJsonOrNull() {
        try {
            return ProjectJson.toJson(project);
        } catch (JSONException e) {
            showError(e);
            return null;
        }
    }

    private void flushPending() {
        canvas.removeCallbacks(commitNudges);
        if (zoomStepper != null) {
            zoomStepper.commitNow();
            backgroundStepper.commitNow();
        }
        if (history != null) {
            commit();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (project != null && history != null) {
            flushPending();
            saveThumbnailInBackground();
        }
    }

    private void saveThumbnailInBackground() {
        String json = currentJsonOrNull();
        SourceImage image = source;
        if (json == null) {
            return;
        }
        Background.run(() -> {
            try {
                Project snapshot = ProjectJson.fromCurrentJson(json);
                RenderResult result = renderer().render(image, snapshot, zones);
                store.saveThumbnail(snapshot, result.bitmap);
            } catch (IOException | JSONException e) {
                android.util.Log.w("CropActivity", "Couldn't save the thumbnail", e);
            }
        });
    }
}
