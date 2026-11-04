package io.github.fabiantorrestech.touchwallpaper.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import io.github.fabiantorrestech.touchwallpaper.settings.AppSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Saves PNGs where the user chose. PNG only, because forced text colors depend on exact pixels. */
public final class ImageSaver {
    private static final String DEFAULT_FOLDER = "Pictures/TouchWallpaper";
    private static final String STORAGE_AUTHORITY = "com.android.externalstorage.documents";
    private static final String PRIMARY_STORAGE_PREFIX = "primary:";
    private static final String STORAGE_LABEL = "Internal storage/";
    private static final String PNG_TYPE = "image/png";

    private final Context context;
    private final ContentResolver resolver;
    private final AppSettings settings;

    public ImageSaver(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        settings = new AppSettings(this.context);
    }

    public static byte[] encodePng(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
            throw new IOException("Couldn't create the image.");
        }
        return buffer.toByteArray();
    }

    /** Saves to the chosen default folder, or Pictures/TouchWallpaper if none is set. */
    public SavedFile saveToDefaultLocation(byte[] png, String name) throws IOException {
        String folder = settings.exportFolderUri();
        String fileName = fileNameFor(name);
        return folder == null ? saveToPictures(png, fileName) : saveToFolder(png, fileName, Uri.parse(folder));
    }

    /** Writes to a file the user just created through the system's save screen. */
    public SavedFile saveToCreatedDocument(byte[] png, Uri document) throws IOException {
        write(document, png);
        return describeDocument(document);
    }

    /** Whether a previously saved file is still there. */
    public boolean exists(String uri) {
        try (android.os.ParcelFileDescriptor descriptor = resolver.openFileDescriptor(Uri.parse(uri), "r")) {
            return descriptor != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static String fileNameFor(String name) {
        String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) {
            safe = "Wallpaper";
        }
        return safe.toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? safe : safe + ".png";
    }

    /** A readable form of a folder chosen through the system's folder picker. */
    public static String labelForTree(Uri tree) {
        return readablePath(DocumentsContract.getTreeDocumentId(tree));
    }

    private SavedFile saveToPictures(byte[] png, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, PNG_TYPE);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, DEFAULT_FOLDER);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Couldn't save to " + DEFAULT_FOLDER + ".");
        }
        write(uri, png);
        // The system may have added a number to avoid replacing a file with the same name.
        String savedName = queryDisplayName(uri, fileName);
        Uri folder = DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, PRIMARY_STORAGE_PREFIX + DEFAULT_FOLDER);
        return new SavedFile(uri.toString(), folder.toString(), STORAGE_LABEL + DEFAULT_FOLDER + "/" + savedName);
    }

    private SavedFile saveToFolder(byte[] png, String fileName, Uri tree) throws IOException {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri document;
        try {
            document = DocumentsContract.createDocument(resolver, parent, PNG_TYPE, fileName);
        } catch (RuntimeException | java.io.FileNotFoundException e) {
            throw new IOException("Couldn't open your export folder. Choose it again in Settings.", e);
        }
        if (document == null) {
            throw new IOException("Couldn't open your export folder. Choose it again in Settings.");
        }
        write(document, png);
        return describeDocument(document, tree);
    }

    private void write(Uri uri, byte[] png) throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Couldn't write the file.");
            }
            output.write(png);
        }
    }

    private SavedFile describeDocument(Uri document) {
        String name = queryDisplayName(document, "wallpaper.png");
        if (!DocumentsContract.isDocumentUri(context, document)) {
            return new SavedFile(document.toString(), null, name);
        }
        String documentId = DocumentsContract.getDocumentId(document);
        return new SavedFile(document.toString(), parentFolderOf(document, documentId), readablePath(documentId));
    }

    private SavedFile describeDocument(Uri document, Uri tree) {
        String documentId = DocumentsContract.getDocumentId(document);
        String folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)).toString();
        return new SavedFile(document.toString(), folder, readablePath(documentId));
    }

    private static String parentFolderOf(Uri document, String documentId) {
        int slash = documentId.lastIndexOf('/');
        if (slash < 0 || !documentId.contains(":")) {
            return null;
        }
        return DocumentsContract.buildDocumentUri(document.getAuthority(), documentId.substring(0, slash)).toString();
    }

    private String queryDisplayName(Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ImageSaver", "Couldn't read the saved file's name", e);
        }
        return fallback;
    }

    private static String readablePath(String documentId) {
        if (documentId.startsWith(PRIMARY_STORAGE_PREFIX)) {
            return STORAGE_LABEL + documentId.substring(PRIMARY_STORAGE_PREFIX.length());
        }
        return documentId.replaceFirst(":", "/");
    }
}
