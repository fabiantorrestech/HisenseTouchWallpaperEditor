package io.github.fabiantorrestech.touchwallpaper.storage;

import io.github.fabiantorrestech.touchwallpaper.model.Project;
import java.util.ArrayDeque;
import java.util.Deque;
import org.json.JSONException;

/** Undo and redo for one editing session, kept as small project snapshots. */
public final class ProjectHistory {
    private static final int MAX_STEPS = 30;

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String current;

    public ProjectHistory(Project project) throws JSONException {
        current = snapshot(project);
    }

    /** Records the project's state as one undo step. Does nothing if it hasn't changed. */
    public void record(Project project) throws JSONException {
        String latest = snapshot(project);
        if (latest.equals(current)) {
            return;
        }
        undo.push(current);
        if (undo.size() > MAX_STEPS) {
            undo.removeLast();
        }
        redo.clear();
        current = latest;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public Project undo(Project live) throws JSONException {
        redo.push(current);
        current = undo.pop();
        return restore(live);
    }

    public Project redo(Project live) throws JSONException {
        undo.push(current);
        current = redo.pop();
        return restore(live);
    }

    /** Forgets all steps. Used when the image itself changes, which can't be undone. */
    public void clear(Project project) throws JSONException {
        undo.clear();
        redo.clear();
        current = snapshot(project);
    }

    /** Undo covers edits only, so a save location or rename made since stays. */
    private Project restore(Project live) throws JSONException {
        Project restored = ProjectJson.fromCurrentJson(current);
        restored.name = live.name;
        restored.lastSavedUri = live.lastSavedUri;
        restored.lastSavedFolderUri = live.lastSavedFolderUri;
        restored.lastSavedPath = live.lastSavedPath;
        return restored;
    }

    /** Leaves out what changes without an edit, so identical settings compare as equal. */
    private static String snapshot(Project project) throws JSONException {
        long updatedAt = project.updatedAtMillis;
        String savedUri = project.lastSavedUri;
        String savedFolder = project.lastSavedFolderUri;
        String savedPath = project.lastSavedPath;
        project.updatedAtMillis = 0;
        project.lastSavedUri = null;
        project.lastSavedFolderUri = null;
        project.lastSavedPath = null;
        String json = ProjectJson.toJson(project);
        project.updatedAtMillis = updatedAt;
        project.lastSavedUri = savedUri;
        project.lastSavedFolderUri = savedFolder;
        project.lastSavedPath = savedPath;
        return json;
    }
}
