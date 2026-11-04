package io.github.fabiantorrestech.touchwallpaper.storage;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Saves all projects to a single .zip and restores them. Uninstalling the app erases its private
 * storage, so this is how projects survive a reinstall.
 */
public final class ProjectBackup {
    private static final int BUFFER_SIZE = 65536;

    private final File root;
    private final ContentResolver resolver;

    public ProjectBackup(File root, ContentResolver resolver) {
        this.root = root;
        this.resolver = resolver;
    }

    /** Returns how many projects were saved. */
    public int backUpTo(Uri target) throws IOException {
        File[] folders = root.listFiles(File::isDirectory);
        int count = 0;
        try (OutputStream raw = resolver.openOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            if (folders == null) {
                return 0;
            }
            for (File folder : folders) {
                addFolder(zip, folder);
                count++;
            }
        }
        return count;
    }

    private void addFolder(ZipOutputStream zip, File folder) throws IOException {
        File[] files = folder.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        for (File file : files) {
            zip.putNextEntry(new ZipEntry(folder.getName() + "/" + file.getName()));
            try (InputStream input = new FileInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
        }
    }

    /**
     * Restores projects that aren't already on the device. Existing ones are left alone, so
     * restoring can't overwrite newer work. Returns how many projects were restored.
     */
    public int restoreFrom(Uri source) throws IOException {
        int restored = 0;
        String currentFolder = null;
        boolean restoring = false;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream raw = resolver.openInputStream(source);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String[] parts = entry.getName().split("/");
                if (entry.isDirectory() || parts.length != 2 || !isSafeName(parts[0]) || !isSafeName(parts[1])) {
                    throw new IOException("This isn't a Touch Wallpaper backup.");
                }
                if (!parts[0].equals(currentFolder)) {
                    currentFolder = parts[0];
                    File folder = new File(root, currentFolder);
                    restoring = !folder.exists();
                    if (restoring) {
                        if (!folder.mkdirs()) {
                            throw new IOException("Couldn't create the project folder.");
                        }
                        restored++;
                    }
                }
                if (restoring) {
                    writeEntry(zip, new File(new File(root, currentFolder), parts[1]), buffer);
                }
            }
        }
        return restored;
    }

    private static void writeEntry(ZipInputStream zip, File target, byte[] buffer) throws IOException {
        try (OutputStream output = new FileOutputStream(target)) {
            int read;
            while ((read = zip.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
    }

    /** Blocks paths that could write outside the projects folder. */
    private static boolean isSafeName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..") && !name.contains("\\");
    }
}
