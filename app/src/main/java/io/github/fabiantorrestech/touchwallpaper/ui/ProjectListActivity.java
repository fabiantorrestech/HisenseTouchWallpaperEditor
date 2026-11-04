package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

/** The Projects screen: saved wallpapers in pages. Only the visible page's thumbnails are loaded. */
public final class ProjectListActivity extends BaseActivity {
    private static final int PROJECTS_PER_PAGE = 2;

    private ProjectStore store;
    private LinearLayout list;
    private List<Project> projects = new ArrayList<>();
    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);
        setUpTopBar(getString(R.string.projects));
        store = new ProjectStore(this);
        list = findViewById(R.id.project_list);
        findViewById(R.id.page_previous).setOnClickListener(view -> showPage(page - 1));
        findViewById(R.id.page_next).setOnClickListener(view -> showPage(page + 1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        projects = store.list();
        findViewById(R.id.empty).setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
        showPage(page);
    }

    private int pageCount() {
        return Math.max(1, (projects.size() + PROJECTS_PER_PAGE - 1) / PROJECTS_PER_PAGE);
    }

    private void showPage(int requested) {
        page = Math.max(0, Math.min(pageCount() - 1, requested));
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int end = Math.min(projects.size(), (page + 1) * PROJECTS_PER_PAGE);
        for (int i = page * PROJECTS_PER_PAGE; i < end; i++) {
            list.addView(createRow(inflater, projects.get(i)));
        }
        findViewById(R.id.pager).setVisibility(pageCount() > 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.page_previous).setEnabled(page > 0);
        findViewById(R.id.page_next).setEnabled(page < pageCount() - 1);
        ((TextView) findViewById(R.id.page_label)).setText(getString(R.string.page_of, page + 1, pageCount()));
    }

    private View createRow(LayoutInflater inflater, Project project) {
        View row = inflater.inflate(R.layout.row_project, list, false);
        ((TextView) row.findViewById(R.id.name)).setText(project.name);
        File thumbnail = store.thumbnailOf(project.id);
        if (thumbnail.exists()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            ((ImageView) row.findViewById(R.id.thumbnail))
                    .setImageBitmap(BitmapFactory.decodeFile(thumbnail.getPath(), options));
        }
        row.findViewById(R.id.open_crop).setOnClickListener(view -> open(CropActivity.class, project.id));
        row.findViewById(R.id.open_zones).setOnClickListener(view -> open(ZoneEditorActivity.class, project.id));
        row.findViewById(R.id.rename).setOnClickListener(view -> askToRename(project));
        row.findViewById(R.id.duplicate).setOnClickListener(view -> duplicate(project));
        row.findViewById(R.id.delete).setOnClickListener(view -> confirmDelete(project));
        View showSaved = row.findViewById(R.id.show_saved);
        showSaved.setVisibility(project.lastSavedUri != null ? View.VISIBLE : View.INVISIBLE);
        showSaved.setOnClickListener(view -> showSavedFile(project));
        return row;
    }

    private void open(Class<?> screen, String projectId) {
        startActivity(new Intent(this, screen).putExtra(Extras.PROJECT_ID, projectId));
    }

    private void askToRename(Project project) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(project.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> rename(project, input.getText().toString().trim()))
                .show();
    }

    private void rename(Project project, String name) {
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            store.rename(project.id, name);
            reload();
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private void duplicate(Project project) {
        try {
            store.duplicate(project.id);
            reload();
        } catch (IOException | JSONException e) {
            showError(e);
        }
    }

    private void confirmDelete(Project project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, project.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    store.delete(project.id);
                    reload();
                })
                .show();
    }

    private void showSavedFile(Project project) {
        if (!new ImageSaver(this).exists(project.lastSavedUri)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.file_missing_title)
                    .setMessage(getString(R.string.file_missing_message, project.lastSavedPath))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        SavedFileDialog.show(this, project.name, null, project.lastSavedUri, project.lastSavedFolderUri, project.lastSavedPath);
    }
}
