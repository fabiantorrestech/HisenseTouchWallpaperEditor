package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.Context;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.render.SourceImageLoader;
import io.github.fabiantorrestech.touchwallpaper.render.WallpaperRenderer;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import io.github.fabiantorrestech.touchwallpaper.storage.ZoneCatalog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.json.JSONException;

/**
 * The normal wallpaper used while a hidden app is open and when see-through is switched off.
 * {@code before.png} is the wallpaper in place when see-through was first turned on;
 * {@code fallback.png} is the one in use now. Screenshots are never stored here.
 */
public final class FallbackWallpaper {
    /** Settings value meaning the fallback is the wallpaper from before see-through. */
    public static final String BEFORE = "before";
    /** Settings value meaning the user chose no fallback: the last allowed screenshot stays. */
    public static final String NONE = "none";

    private final File folder;

    public FallbackWallpaper(Context context) {
        folder = new File(context.getApplicationContext().getFilesDir(), "seethrough");
    }

    public boolean hasBefore() {
        return before().exists();
    }

    public void saveBefore(byte[] png) throws IOException {
        write(before(), png);
    }

    public boolean exists() {
        return current().exists();
    }

    public byte[] read() throws IOException {
        return Files.readAllBytes(current().toPath());
    }

    public void save(byte[] png) throws IOException {
        write(current(), png);
    }

    public void clear() {
        current().delete();
    }

    public void useBefore() throws IOException {
        save(Files.readAllBytes(before().toPath()));
    }

    /** Renders a project the same way the editor's Apply does. Allocates a renderer; call off the UI thread. */
    public static byte[] renderProject(Context context, String projectId) throws IOException, JSONException {
        ProjectStore store = new ProjectStore(context);
        Project project = store.load(projectId);
        SourceImage source = SourceImageLoader.load(context, store.sourceFileOf(project));
        try {
            return ImageSaver.encodePng(new WallpaperRenderer()
                    .render(source, project, ZoneCatalog.loadZones(context)).bitmap);
        } finally {
            source.bitmap.recycle();
        }
    }

    private File before() {
        return new File(folder, "before.png");
    }

    private File current() {
        return new File(folder, "fallback.png");
    }

    private void write(File target, byte[] png) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Couldn't create the see-through folder.");
        }
        File temp = new File(folder, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(png);
            output.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Couldn't save the fallback wallpaper.");
        }
    }
}
