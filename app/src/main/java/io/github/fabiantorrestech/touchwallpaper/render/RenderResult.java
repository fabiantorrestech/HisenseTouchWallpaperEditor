package io.github.fabiantorrestech.touchwallpaper.render;

import android.graphics.Bitmap;
import io.github.fabiantorrestech.touchwallpaper.font.FontColorRule;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;
import java.util.EnumMap;
import java.util.Map;

/**
 * A finished wallpaper and what the lock screen's text color rule will decide for it.
 * The bitmap belongs to the renderer and is overwritten by its next render, so use it on the worker thread.
 */
public final class RenderResult {
    public final Bitmap bitmap;
    private final Map<Band, Integer> votes = new EnumMap<>(Band.class);

    RenderResult(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    void setVote(Band band, int vote) {
        votes.put(band, vote);
    }

    public int voteFor(Band band) {
        return votes.get(band);
    }

    public TextColor predictedTextColor(Band band) {
        return FontColorRule.textColorFor(voteFor(band));
    }
}
