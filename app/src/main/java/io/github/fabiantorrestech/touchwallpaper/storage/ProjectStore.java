package io.github.fabiantorrestech.touchwallpaper.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.render.SourceImageLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.json.JSONException;

/** Keeps each project in its own private folder: project.json, the source image and a thumbnail (ADR 3). */
public final class ProjectStore {
    private static final String PROJECT_FILE = "project.json";
    private static final String THUMBNAIL_FILE = "thumbnail.png";
    private static final String SOURCE_FILE = "source";
    private static final int THUMBNAIL_WIDTH = 180;
    private static final int THUMBNAIL_HEIGHT = 360;
    private static final int COPY_BUFFER_SIZE = 65536;

    private final File root;
    private final Context context;
    private final ContentResolver resolver;

    public ProjectStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "projects");
        resolver = this.context.getContentResolver();
    }

    public File rootFolder() {
        return root;
    }

    /** Copies the image in, so the project keeps working if the original moves or is deleted. */
    public Project createFromImage(Uri imageUri) throws IOException, JSONException {
        Project project = new Project();
        project.id = UUID.randomUUID().toString();
        project.name = stripExtension(displayNameOf(imageUri));
        project.sourceFileName = SOURCE_FILE;
        File folder = folderOf(project.id);
        if (!folder.mkdirs()) {
            throw new IOException("Couldn't create the project folder.");
        }
        try {
            copyUriTo(imageUri, new File(folder, project.sourceFileName));
            SourceImage source = SourceImageLoader.load(context, sourceFileOf(project));
            project.crop.resetToCover(source);
            source.bitmap.recycle();
            save(project);
        } catch (IOException | JSONException | RuntimeException e) {
            deleteFolder(folder);
            throw e;
        }
        return project;
    }

    /** Swaps a project's image, keeping its zones. The old image stays if the new one can't be read. */
    public SourceImage replaceSource(Project project, Uri imageUri) throws IOException, JSONException {
        File current = sourceFileOf(project);
        File incoming = new File(current.getParentFile(), current.getName() + ".new");
        try {
            copyUriTo(imageUri, incoming);
            SourceImage image = SourceImageLoader.load(context, incoming);
            if (!incoming.renameTo(current)) {
                throw new IOException("Couldn't replace the image.");
            }
            project.crop.resetToCover(image);
            save(project);
            return image;
        } finally {
            incoming.delete();
        }
    }

    public void save(Project project) throws IOException, JSONException {
        project.updatedAtMillis = System.currentTimeMillis();
        writeAtomically(new File(folderOf(project.id), PROJECT_FILE),
                ProjectJson.toJson(project).getBytes(StandardCharsets.UTF_8));
    }

    public void saveThumbnail(Project project, Bitmap wallpaper) throws IOException {
        Bitmap scaled = Bitmap.createScaledBitmap(wallpaper, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true);
        File target = thumbnailOf(project.id);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (OutputStream output = new FileOutputStream(temp)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            scaled.recycle();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Couldn't save the thumbnail.");
        }
    }

    public Project load(String projectId) throws IOException, JSONException {
        File folder = folderOf(projectId);
        byte[] bytes = readAll(new File(folder, PROJECT_FILE));
        return ProjectJson.fromJson(new String(bytes, StandardCharsets.UTF_8), () -> legacySampleSizeOf(folder));
    }

    private static int legacySampleSizeOf(File folder) {
        try {
            return SourceImageLoader.legacySampleSize(new File(folder, SOURCE_FILE));
        } catch (IOException e) {
            return 1;
        }
    }

    /** Newest first. A project that can't be read is left out rather than failing the whole list. */
    public List<Project> list() {
        List<Project> projects = new ArrayList<>();
        File[] folders = root.listFiles(File::isDirectory);
        if (folders == null) {
            return projects;
        }
        for (File folder : folders) {
            try {
                projects.add(load(folder.getName()));
            } catch (IOException | JSONException e) {
                android.util.Log.w("ProjectStore", "Skipping unreadable project " + folder.getName(), e);
            }
        }
        projects.sort(Comparator.comparingLong((Project project) -> project.updatedAtMillis).reversed());
        return projects;
    }

    public void rename(String projectId, String newName) throws IOException, JSONException {
        Project project = load(projectId);
        project.name = newName;
        save(project);
    }

    public Project duplicate(String projectId) throws IOException, JSONException {
        Project copy = load(projectId);
        copy.id = UUID.randomUUID().toString();
        copy.name = copy.name + " copy";
        copy.lastSavedUri = null;
        copy.lastSavedFolderUri = null;
        copy.lastSavedPath = null;
        File from = folderOf(projectId);
        File to = folderOf(copy.id);
        if (!to.mkdirs()) {
            throw new IOException("Couldn't create the project folder.");
        }
        copyFile(new File(from, copy.sourceFileName), new File(to, copy.sourceFileName));
        File thumbnail = thumbnailOf(projectId);
        if (thumbnail.exists()) {
            copyFile(thumbnail, thumbnailOf(copy.id));
        }
        save(copy);
        return copy;
    }

    public void delete(String projectId) {
        deleteFolder(folderOf(projectId));
    }

    public File sourceFileOf(Project project) {
        return new File(folderOf(project.id), project.sourceFileName);
    }

    public File thumbnailOf(String projectId) {
        return new File(folderOf(projectId), THUMBNAIL_FILE);
    }

    private File folderOf(String projectId) {
        return new File(root, projectId);
    }

    private void copyUriTo(Uri uri, File target) throws IOException {
        try (InputStream input = resolver.openInputStream(uri);
             OutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                throw new IOException("Couldn't open that image.");
            }
            copy(input, output);
        }
    }

    private String displayNameOf(Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ProjectStore", "Couldn't read the image's name", e);
        }
        return "Wallpaper";
    }

    private static String stripExtension(String name) {
        String stripped = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        return stripped.isEmpty() ? name : stripped;
    }

    private static void writeAtomically(File target, byte[] bytes) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            throw new IOException("Couldn't save " + target.getName());
        }
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new IOException("Unexpected end of " + file.getName());
                }
                offset += read;
            }
        }
        return bytes;
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream input = new FileInputStream(from); OutputStream output = new FileOutputStream(to)) {
            copy(input, output);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
    }

    private static void deleteFolder(File folder) {
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                child.delete();
            }
        }
        folder.delete();
    }
}
