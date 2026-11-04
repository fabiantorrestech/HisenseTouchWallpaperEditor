package io.github.fabiantorrestech.touchwallpaper.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import io.github.fabiantorrestech.touchwallpaper.R;

/** Tells the user where a file is, and takes them there when the device can. */
final class SavedFileDialog {
    private SavedFileDialog() {
    }

    static void show(Activity activity, String title, String message, String uri, String folderUri, String path) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message == null ? path : message + "\n\n" + path)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.copy_path, (dialog, which) -> copyPath(activity, path))
                .setNegativeButton(R.string.open_location, (dialog, which) -> openLocation(activity, uri, folderUri))
                .show();
    }

    /**
     * Shows the folder in the system's file picker. Its browse screen isn't used because it crashes
     * on this ROM ("Launcher activity not found") after appearing to open. Falls back to viewing
     * the file; the path stays visible if neither works.
     */
    private static void openLocation(Activity activity, String uri, String folderUri) {
        if (folderUri != null && tryStart(activity, new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(folderUri)))) {
            return;
        }
        boolean opened = tryStart(activity, new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), "image/png")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        if (!opened) {
            Toast.makeText(activity, R.string.cant_open_location, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean tryStart(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private static void copyPath(Activity activity, String path) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.saved_location), path));
        Toast.makeText(activity, R.string.path_copied, Toast.LENGTH_SHORT).show();
    }
}
