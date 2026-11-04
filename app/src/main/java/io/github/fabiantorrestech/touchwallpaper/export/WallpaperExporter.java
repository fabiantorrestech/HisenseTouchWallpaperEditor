package io.github.fabiantorrestech.touchwallpaper.export;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Applies a wallpaper by replacing the file the ROM's lock screen reads (ADR 4). */
public final class WallpaperExporter {
    /** The lock screen's outcome once the device has slept. */
    public enum LoadStatus {
        NOTHING_APPLIED,
        WAITING_FOR_LOCK,
        LOADED,
        REPLACED_BY_ANOTHER_APP
    }

    private static final String ROM_FOLDER = "Pictures/.lockscreen_wallpaper";
    private static final String ROM_FILE = "lockscreen_wallpaper_custom.png";
    private static final String THEME_FILE =
            "Download/.hmcttheme/com.hmct.theme/lock_screen_wallpaper/lockscreen_wallpaper_fixed.png";
    private static final String ROM_LOADED_TIME_SETTING = "fw_paper_modifed_time";
    private static final String BACKUP_FILE = "original_lock_wallpaper.png";
    private static final String PREFERENCES = "export";
    private static final String KEY_APPLIED_TIME = "applied_time";
    private static final int COPY_BUFFER_SIZE = 65536;

    private final Context context;
    private final File romFile;

    public WallpaperExporter(Context context) {
        this.context = context.getApplicationContext();
        File storage = Environment.getExternalStorageDirectory();
        romFile = new File(new File(storage, ROM_FOLDER), ROM_FILE);
    }

    public boolean hasAllFilesAccess() {
        return Environment.isExternalStorageManager();
    }

    /** Whether this device has the wallpaper folder the app writes to. Needs All Files Access to check. */
    public boolean romFolderExists() {
        return romFile.getParentFile().isDirectory();
    }

    public void apply(byte[] png) throws IOException {
        keepOriginalOnce();
        replaceRomFile(png);
    }

    public boolean hasOriginalBackup() {
        return backupFile().exists();
    }

    /** Puts back the wallpaper that was in place before this app first changed it. */
    public void restoreOriginal() throws IOException {
        replaceRomFile(readAll(backupFile()));
    }

    /** Whether the lock screen has picked up the file this app last wrote. */
    public LoadStatus loadStatus() {
        long appliedTime = preferences().getLong(KEY_APPLIED_TIME, 0);
        if (appliedTime == 0) {
            return LoadStatus.NOTHING_APPLIED;
        }
        if (hasAllFilesAccess() && (romFile.lastModified() != appliedTime || isThemeFileNewerThan(appliedTime))) {
            return LoadStatus.REPLACED_BY_ANOTHER_APP;
        }
        long loadedTime = Settings.Global.getLong(context.getContentResolver(), ROM_LOADED_TIME_SETTING, 0);
        return loadedTime == appliedTime ? LoadStatus.LOADED : LoadStatus.WAITING_FOR_LOCK;
    }

    private boolean isThemeFileNewerThan(long time) {
        File theme = new File(Environment.getExternalStorageDirectory(), THEME_FILE);
        return theme.exists() && theme.lastModified() > time;
    }

    private void keepOriginalOnce() throws IOException {
        File backup = backupFile();
        if (backup.exists() || !romFile.exists()) {
            return;
        }
        File temp = new File(backup.getParentFile(), BACKUP_FILE + ".tmp");
        try (OutputStream output = new FileOutputStream(temp)) {
            output.write(readAll(romFile));
        }
        if (!temp.renameTo(backup)) {
            throw new IOException("Couldn't back up the current wallpaper.");
        }
    }

    /** Writes a temp file and renames it, because opening the ROM's file directly empties it first. */
    private void replaceRomFile(byte[] png) throws IOException {
        File folder = romFile.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("This device's lock screen folder wasn't found. A firmware update may have moved it. You can still save a PNG.");
        }
        File temp = new File(folder, ROM_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(png);
            output.getFD().sync();
        }
        if (!temp.renameTo(romFile)) {
            temp.delete();
            throw new IOException("Couldn't replace the lock screen's wallpaper. You can still save a PNG.");
        }
        // The lock screen only reloads when the modified time changes.
        romFile.setLastModified(System.currentTimeMillis());
        preferences().edit().putLong(KEY_APPLIED_TIME, romFile.lastModified()).apply();
    }

    private File backupFile() {
        return new File(context.getFilesDir(), BACKUP_FILE);
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }
}
