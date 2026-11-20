package io.github.fabiantorrestech.touchwallpaper.seethrough;

import android.content.Context;
import android.graphics.Bitmap;
import io.github.fabiantorrestech.touchwallpaper.device.DeviceProfile;
import io.github.fabiantorrestech.touchwallpaper.model.CropState;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.render.WallpaperRenderer;
import io.github.fabiantorrestech.touchwallpaper.storage.ZoneCatalog;
import java.io.IOException;
import java.util.List;
import org.json.JSONException;

/**
 * Turns a screenshot into a lock screen image. On a measured device it goes through
 * {@link WallpaperRenderer}, the app's only drawing code, so the style project's zones and forced
 * text colors look exactly as they do in the editor. Elsewhere the screenshot is used unchanged.
 * Not thread safe: the service's worker thread only.
 */
final class SeeThroughComposer {
    private final Context context;
    private final DeviceProfile profile;
    private WallpaperRenderer renderer;
    private List<ZoneDefinition> zones;

    SeeThroughComposer(Context context, DeviceProfile profile) {
        this.context = context.getApplicationContext();
        this.profile = profile;
    }

    /**
     * Returns the frame itself or the renderer's reused output, so use the result before the next
     * call. {@code style} is changed: its crop is set to fill the screen with the frame.
     */
    Bitmap compose(Bitmap frame, Project style) throws IOException, JSONException {
        if (!profile.hasMeasuredZones()) {
            return frame;
        }
        if (renderer == null) {
            zones = ZoneCatalog.loadZones(context);
            renderer = new WallpaperRenderer();
        }
        SourceImage source = new SourceImage(frame, frame.getWidth(), frame.getHeight());
        style.crop = new CropState();
        style.crop.resetToCover(source);
        return renderer.render(source, style, zones).bitmap;
    }

    /** Frees the renderer's buffers while the feature is off. */
    void release() {
        renderer = null;
    }
}
