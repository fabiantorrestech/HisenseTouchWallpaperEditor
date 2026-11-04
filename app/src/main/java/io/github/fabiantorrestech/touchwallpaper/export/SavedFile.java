package io.github.fabiantorrestech.touchwallpaper.export;

/** Where a saved PNG ended up. */
public final class SavedFile {
    public final String uri;
    /** The containing folder's document address, or null if it isn't known. */
    public final String folderUri;
    /** A location a person can read, such as "Internal storage/Pictures/TouchWallpaper/stars.png". */
    public final String path;

    public SavedFile(String uri, String folderUri, String path) {
        this.uri = uri;
        this.folderUri = folderUri;
        this.path = path;
    }
}
