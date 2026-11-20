package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.Context;
import android.net.Uri;
import io.github.fabiantorrestech.touchwallpaper.export.ImageSaver;
import io.github.fabiantorrestech.touchwallpaper.export.WallpaperExporter;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.render.SourceImageLoader;
import io.github.fabiantorrestech.touchwallpaper.render.WallpaperRenderer;
import io.github.fabiantorrestech.touchwallpaper.storage.ProjectStore;
import io.github.fabiantorrestech.touchwallpaper.storage.ZoneCatalog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import org.json.JSONException;

/**
 * The normal wallpaper shown while a hidden or uncapturable app is open, and put back when
 * see-through is switched off. Which one is set by {@code AppSettings.seeThroughFallbackId()}:
 * <ul>
 * <li>null (Automatic): the last picture wallpaper applied, else the original.</li>
 * <li>{@link #ORIGINAL}: the wallpaper from before this app first changed it.</li>
 * <li>a project id or {@link #IMAGE}: a PNG rendered when it was chosen.</li>
 * </ul>
 * Screenshots are never stored here.
 */
public final class FallbackWallpaper {
    public static final String ORIGINAL = "original";
    public static final String IMAGE = "image";

    private final Context context;
    private final File folder;

    public FallbackWallpaper(Context context) {
        this.context = context.getApplicationContext();
        folder = new File(this.context.getFilesDir(), "seethrough");
    }

    /** The wallpaper in place when see-through was first turned on, if the app has none older. */
    public boolean hasBefore() {
        return before().exists();
    }

    public void saveBefore(byte[] png) throws IOException {
        write(before(), png);
    }

    /** Called on every picture apply, for the Automatic choice. */
    public void saveLastNormal(byte[] png) throws IOException {
        write(lastNormal(), png);
    }

    public void saveChosen(byte[] png) throws IOException {
        write(chosen(), png);
    }

    public boolean hasOriginal() {
        return new WallpaperExporter(context).hasOriginalBackup() || hasBefore();
    }

    /** The PNG to show for this setting, or null if there's nothing to show. Reads files; call off the UI thread. */
    public byte[] resolve(String fallbackId) throws IOException {
        if (ORIGINAL.equals(fallbackId)) {
            return original();
        }
        if (fallbackId != null && chosen().exists()) {
            return read(chosen());
        }
        if (lastNormal().exists()) {
            return read(lastNormal());
        }
        return original();
    }

    private byte[] original() throws IOException {
        byte[] backup = new WallpaperExporter(context).readOriginalBackup();
        if (backup != null) {
            return backup;
        }
        return hasBefore() ? read(before()) : null;
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

    /** Fills the screen with a picked image, centered, through the same renderer. */
    public static byte[] renderImage(Context context, Uri image) throws IOException, JSONException {
        File temp = new File(context.getCacheDir(), "fallback-pick");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(image);
                 OutputStream output = new FileOutputStream(temp)) {
                if (input == null) {
                    throw new IOException("Couldn't open that image.");
                }
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
            SourceImage source = SourceImageLoader.load(context, temp);
            try {
                Project project = new Project();
                project.crop.resetToCover(source);
                return ImageSaver.encodePng(new WallpaperRenderer()
                        .render(source, project, ZoneCatalog.loadZones(context)).bitmap);
            } finally {
                source.bitmap.recycle();
            }
        } finally {
            temp.delete();
        }
    }

    private File before() {
        return new File(folder, "before.png");
    }

    private File lastNormal() {
        return new File(folder, "last_normal.png");
    }

    private File chosen() {
        return new File(folder, "fallback.png");
    }

    private static byte[] read(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
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
