package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.eink.EinkRefresher;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.export.SavedFile;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.render.LockScreenOverlay;
import io.github.fabiantorrestech.touchwallpaper.render.ReadableDesigner;
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

/**
 * Step 2, built around preview > edit > preview. One screen with swappable panels, so its fixed
 * image buffers are shared instead of reallocated when moving between views.
 */
public final class ZoneEditorActivity extends BaseActivity implements ZoneCanvasView.Listener,
        ZonesListPanel.Host, EditZonePanel.Host, PngSaveFlow.Listener {

    private enum View_ {
        PREVIEW, FULL, ZONES, EDIT, RESIZE
    }

    private enum PreviewText {
        PREDICTED(R.string.preview_predicted),
        WHITE(R.string.preview_white),
        BLACK(R.string.preview_black),
        HIDDEN(R.string.preview_none);

        @StringRes final int label;

        PreviewText(@StringRes int label) {
            this.label = label;
        }

        PreviewText next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final PngSaveFlow saveFlow = new PngSaveFlow(this, this);
    private final LockScreenOverlay overlay = new LockScreenOverlay();

    private ProjectStore store;
    private WallpaperExporter exporter;
    private ReadabilityAdvisor advisor;
    private ZoneCanvasView canvas;
    private ZonesListPanel zonesPanel;
    private EditZonePanel editPanel;
    private TextView previewAdvice;
    private TextView editAdvice;
    private MaterialButton applyButton;
    private MaterialButton previewTextButton;

    private Project project;
    private SourceImage source;
    private List<ZoneDefinition> zones;
    private ProjectHistory history;
    private RenderResult lastResult;
    private View_ currentView = View_.PREVIEW;
    private PreviewText previewText = PreviewText.PREDICTED;
    private int quoteIndex;

    /** Lets a newer render cancel an older one before it starts. */
    private volatile int renderGeneration;

    // Worker thread only. Fixed buffers, so editing doesn't churn memory.
    private WallpaperRenderer renderer;
    private Bitmap previewBufferA;
    private Bitmap previewBufferB;
    private boolean drawIntoA = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zone_editor);
        setUpTopBar(getString(R.string.zones_title));
        store = new ProjectStore(this);
        exporter = new WallpaperExporter(this);
        advisor = new ReadabilityAdvisor(this, settings);
        canvas = findViewById(R.id.canvas);
        previewAdvice = findViewById(R.id.advice_preview);
        editAdvice = findViewById(R.id.advice_edit);
        applyButton = findViewById(R.id.apply);
        previewTextButton = findViewById(R.id.preview_text);
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
        try {
            history = new ProjectHistory(project);
        } catch (JSONException e) {
            showErrorAndClose(e);
            return;
        }
        canvas.setListener(this);
        zonesPanel = new ZonesListPanel(this, this);
        editPanel = new EditZonePanel(this, this);
        bindButtons();
        showView(View_.PREVIEW);
        updateUndoButtons();
        requestRender();
    }

    private void bindButtons() {
        findViewById(R.id.open_zones).setOnClickListener(view -> showView(View_.ZONES));
        findViewById(R.id.make_readable).setOnClickListener(view -> makeReadable());
        findViewById(R.id.back_to_crop).setOnClickListener(view -> {
            flushAndCommit();
            startActivity(new Intent(this, CropActivity.class).putExtra(Extras.PROJECT_ID, project.id));
            finish();
        });
        findViewById(R.id.undo).setOnClickListener(view -> undo());
        findViewById(R.id.redo).setOnClickListener(view -> redo());
        findViewById(R.id.edit_undo).setOnClickListener(view -> undo());
        findViewById(R.id.edit_redo).setOnClickListener(view -> redo());
        findViewById(R.id.full_view).setOnClickListener(view -> showView(View_.FULL));
        findViewById(R.id.full_back).setOnClickListener(view -> showView(View_.PREVIEW));
        findViewById(R.id.save_png).setOnClickListener(view -> savePng());
        applyButton.setOnClickListener(view -> applyToLockScreen());
        previewTextButton.setText(previewText.label);
        previewTextButton.setOnClickListener(view -> {
            previewText = previewText.next();
            previewTextButton.setText(previewText.label);
            requestRender();
        });
        findViewById(R.id.next_quote).setOnClickListener(view -> {
            quoteIndex++;
            requestRender();
        });
        findViewById(R.id.zones_done).setOnClickListener(view -> showView(View_.PREVIEW));
        findViewById(R.id.edit_done).setOnClickListener(view -> showView(View_.PREVIEW));
        findViewById(R.id.resize_done).setOnClickListener(view -> showView(View_.EDIT));
    }

    private void showView(View_ next) {
        View_ previous = currentView;
        if (editPanel != null) {
            editPanel.flush();
        }
        currentView = next;
        setPanelVisible(R.id.panel_preview, next == View_.PREVIEW);
        setPanelVisible(R.id.panel_full, next == View_.FULL);
        setPanelVisible(R.id.panel_zones, next == View_.ZONES);
        setPanelVisible(R.id.panel_edit, next == View_.EDIT);
        setPanelVisible(R.id.panel_resize, next == View_.RESIZE);
        if (next == View_.ZONES) {
            zonesPanel.show();
        }
        if (next == View_.EDIT) {
            editPanel.rebuild();
        }
        showSelection();
        showAdvice();
        boolean previewBefore = previous == View_.PREVIEW || previous == View_.FULL;
        boolean previewNow = next == View_.PREVIEW || next == View_.FULL;
        if (previewBefore != previewNow && settings.refreshOnSwitch()) {
            canvas.post(() -> EinkRefresher.refresh(this));
        }
    }

    private void setPanelVisible(int id, boolean visible) {
        findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showSelection() {
        boolean editing = currentView == View_.EDIT || currentView == View_.RESIZE;
        ZoneDefinition zone = editPanel.zone();
        canvas.setSelectedRect(editing && zone != null ? project.styleFor(zone.id).effectiveRect(zone) : null);
    }

    @Override
    public void onBackPressed() {
        if (currentView == View_.RESIZE) {
            showView(View_.EDIT);
        } else if (currentView == View_.PREVIEW || history == null) {
            super.onBackPressed();
        } else {
            showView(View_.PREVIEW);
        }
    }

    // ZonesListPanel.Host and EditZonePanel.Host

    @Override
    public Project project() {
        return project;
    }

    @Override
    public List<ZoneDefinition> zones() {
        return zones;
    }

    @Override
    public void onSettingsChanged() {
        commit();
        showSelection();
        requestRender();
    }

    @Override
    public void onEditZone(ZoneDefinition zone) {
        openZone(zone);
    }

    @Override
    public void onStartResize() {
        showView(View_.RESIZE);
    }

    private void openZone(ZoneDefinition zone) {
        editPanel.show(zone);
        showView(View_.EDIT);
    }

    // ZoneCanvasView.Listener

    @Override
    public void onZoneRectChanged(Rect rect) {
        project.styleFor(editPanel.zone().id).customRect = rect;
        project.styleFor(editPanel.zone().id).enabled = true;
        onSettingsChanged();
    }

    /** Tapping a zone opens it. The smallest match wins, so nested zones stay reachable. */
    @Override
    public void onCanvasTapped(int wallpaperX, int wallpaperY) {
        if (currentView == View_.RESIZE || zones == null) {
            return;
        }
        ZoneDefinition best = null;
        int bestArea = Integer.MAX_VALUE;
        for (ZoneDefinition zone : zones) {
            Rect rect = project.styleFor(zone.id).effectiveRect(zone);
            int area = rect.width() * rect.height();
            if (rect.contains(wallpaperX, wallpaperY) && area < bestArea) {
                best = zone;
                bestArea = area;
            }
        }
        if (best != null) {
            openZone(best);
        }
    }

    // Undo and saving

    /** Saves and adds an undo step. Does nothing to history if nothing changed. */
    private void commit() {
        try {
            history.record(project);
            store.save(project);
            updateUndoButtons();
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private void flushAndCommit() {
        if (editPanel != null) {
            editPanel.flush();
        }
        if (history != null) {
            commit();
        }
    }

    private void updateUndoButtons() {
        boolean canUndo = history.canUndo();
        boolean canRedo = history.canRedo();
        findViewById(R.id.undo).setEnabled(canUndo);
        findViewById(R.id.edit_undo).setEnabled(canUndo);
        findViewById(R.id.redo).setEnabled(canRedo);
        findViewById(R.id.edit_redo).setEnabled(canRedo);
    }

    private void undo() {
        editPanel.flush();
        if (history.canUndo()) {
            try {
                showRestored(history.undo(project));
            } catch (JSONException e) {
                showError(e);
            }
        }
    }

    private void redo() {
        editPanel.flush();
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
        try {
            store.save(project);
        } catch (IOException | JSONException e) {
            showError(e);
        }
        updateUndoButtons();
        if (currentView == View_.EDIT) {
            editPanel.rebuild();
        } else if (currentView == View_.ZONES) {
            zonesPanel.show();
        }
        showSelection();
        requestRender();
    }

    // Rendering

    private void requestRender() {
        int generation = ++renderGeneration;
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        PreviewText text = previewText;
        int quote = quoteIndex;
        Background.run(() -> {
            if (generation != renderGeneration) {
                return;
            }
            try {
                Project snapshot = ProjectJson.fromCurrentJson(json);
                RenderResult result = renderer().render(source, snapshot, zones);
                Bitmap shown = composePreview(result, text, quote);
                runOnUiThread(() -> onRendered(generation, result, shown));
            } catch (IOException | JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void onRendered(int generation, RenderResult result, Bitmap shown) {
        if (generation != renderGeneration || isDestroyed()) {
            return;
        }
        canvas.setImage(shown);
        lastResult = result;
        advisor.noteRender(result);
        showAdvice();
    }

    private void showAdvice() {
        if (lastResult == null) {
            return;
        }
        previewAdvice.setText(advisor.adviceForPreview(lastResult, project, zones));
        ZoneDefinition zone = editPanel.zone();
        editAdvice.setText(zone == null ? "" : advisor.adviceForZone(lastResult, project, zone, zones));
    }

    private WallpaperRenderer renderer() {
        if (renderer == null) {
            renderer = new WallpaperRenderer();
        }
        return renderer;
    }

    /**
     * Draws into whichever buffer isn't on screen, then swaps. 16-bit is plenty for a preview on a
     * gray panel and halves the memory. The exact pixels only matter in the export.
     */
    private Bitmap composePreview(RenderResult result, PreviewText text, int quote) throws IOException, JSONException {
        if (previewBufferA == null) {
            previewBufferA = Bitmap.createBitmap(Screen.WIDTH, Screen.HEIGHT, Bitmap.Config.RGB_565);
            previewBufferB = Bitmap.createBitmap(Screen.WIDTH, Screen.HEIGHT, Bitmap.Config.RGB_565);
        }
        Bitmap target = drawIntoA ? previewBufferA : previewBufferB;
        drawIntoA = !drawIntoA;
        Canvas targetCanvas = new Canvas(target);
        targetCanvas.drawBitmap(result.bitmap, 0, 0, null);
        if (text != PreviewText.HIDDEN) {
            List<String> quotes = ZoneCatalog.loadQuotes(this);
            overlay.draw(targetCanvas, topColor(result, text), bottomColor(result, text), quotes.get(quote % quotes.size()));
        }
        return target;
    }

    private static TextColor topColor(RenderResult result, PreviewText text) {
        return text == PreviewText.PREDICTED ? result.predictedTextColor(Band.TOP) : fixedColor(text);
    }

    private static TextColor bottomColor(RenderResult result, PreviewText text) {
        return text == PreviewText.PREDICTED ? result.predictedTextColor(Band.BOTTOM) : fixedColor(text);
    }

    private static TextColor fixedColor(PreviewText text) {
        return text == PreviewText.WHITE ? TextColor.WHITE : TextColor.BLACK;
    }

    private String currentJsonOrNull() {
        try {
            return ProjectJson.toJson(project);
        } catch (JSONException e) {
            showError(e);
            return null;
        }
    }

    // Actions

    private void makeReadable() {
        flushAndCommit();
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        Background.run(() -> {
            try {
                Project designed = ProjectJson.fromCurrentJson(json);
                ReadableDesigner.apply(designed, source, zones, renderer());
                String designedJson = ProjectJson.toJson(designed);
                runOnUiThread(() -> onDesignReady(designedJson));
            } catch (JSONException | RuntimeException e) {
                runOnUiThread(() -> showError(e));
            }
        });
    }

    private void onDesignReady(String designedJson) {
        try {
            project = ProjectJson.fromCurrentJson(designedJson);
        } catch (JSONException e) {
            showError(e);
            return;
        }
        onSettingsChanged();
    }

    private void savePng() {
        flushAndCommit();
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        saveFlow.start(project.name, () -> encodeFinished(json));
    }

    private byte[] encodeFinished(String json) throws IOException {
        try {
            Project snapshot = ProjectJson.fromCurrentJson(json);
            return ImageSaver.encodePng(renderer().render(source, snapshot, zones).bitmap);
        } catch (JSONException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void onPngSaved(SavedFile file) {
        rememberSavedFile(file);
    }

    private void rememberSavedFile(SavedFile file) {
        project.lastSavedUri = file.uri;
        project.lastSavedFolderUri = file.folderUri;
        project.lastSavedPath = file.path;
        try {
            store.save(project);
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private void applyToLockScreen() {
        if (!exporter.hasAllFilesAccess()) {
            askForAllFilesAccess();
            return;
        }
        flushAndCommit();
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        String name = project.name;
        applyButton.setEnabled(false);
        applyButton.setText(R.string.applying);
        Background.run(() -> {
            try {
                byte[] png = encodeFinished(json);
                exporter.apply(png);
                saveCopyAfterApply(png, name);
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    finishApplyButton();
                    showError(e);
                });
            }
        });
    }

    /** The wallpaper is already applied here, so failing to save a copy mustn't look like failing to apply. */
    private void saveCopyAfterApply(byte[] png, String name) {
        try {
            SavedFile copy = new ImageSaver(this).saveToDefaultLocation(png, name);
            runOnUiThread(() -> {
                finishApplyButton();
                rememberSavedFile(copy);
                SavedFileDialog.show(this, getString(R.string.applied_title), getString(R.string.applied_message),
                        copy.uri, copy.folderUri, copy.path);
            });
        } catch (IOException | RuntimeException e) {
            runOnUiThread(() -> {
                finishApplyButton();
                new AlertDialog.Builder(this)
                        .setTitle(R.string.applied_title)
                        .setMessage(getString(R.string.applied_no_copy, e.getMessage()))
                        .setPositiveButton(R.string.ok, null)
                        .show();
            });
        }
    }

    private void finishApplyButton() {
        applyButton.setEnabled(true);
        applyButton.setText(R.string.apply);
    }

    private void askForAllFilesAccess() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> startActivity(
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()))))
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (history != null) {
            flushAndCommit();
            saveThumbnailInBackground();
        }
    }

    private void saveThumbnailInBackground() {
        String json = currentJsonOrNull();
        if (json == null) {
            return;
        }
        Background.run(() -> {
            try {
                Project snapshot = ProjectJson.fromCurrentJson(json);
                store.saveThumbnail(snapshot, renderer().render(source, snapshot, zones).bitmap);
            } catch (IOException | JSONException | RuntimeException e) {
                android.util.Log.w("ZoneEditorActivity", "Couldn't save the thumbnail", e);
            }
        });
    }
}
