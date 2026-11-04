package io.github.fabiantorrestech.touchwallpaper.render;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import io.github.fabiantorrestech.touchwallpaper.font.FontColorRule;
import io.github.fabiantorrestech.touchwallpaper.font.TextColorForcer;
import io.github.fabiantorrestech.touchwallpaper.model.Band;
import io.github.fabiantorrestech.touchwallpaper.model.Project;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.TextColorChoice;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneStyle;
import java.util.List;

/**
 * The only code that draws a wallpaper. Preview and export both use it, so they can't disagree.
 * Keeps one output bitmap and one pixel buffer for its lifetime, so editing doesn't churn memory.
 * Not thread safe: use it from the shared worker thread only.
 */
public final class WallpaperRenderer {
    private final Bitmap output = Bitmap.createBitmap(Screen.WIDTH, Screen.HEIGHT, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(output);
    private final int[] pixels = new int[Screen.WIDTH * Screen.HEIGHT];
    private final Matrix matrix = new Matrix();
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public WallpaperRenderer() {
        outlinePaint.setStyle(Paint.Style.STROKE);
    }

    /** The finished wallpaper: image, enabled zones and text color choices. */
    public RenderResult render(SourceImage source, Project project, List<ZoneDefinition> zones) {
        drawImage(source, project);
        for (ZoneDefinition zone : zones) {
            ZoneStyle style = project.zoneStyles.get(zone.id);
            if (style != null && style.enabled) {
                drawZone(zone, style);
            }
        }
        return applyTextColorChoices(project);
    }

    /** Step 1's output: only the placed image. Text colors are reported but not forced. */
    public RenderResult renderCropOnly(SourceImage source, Project project) {
        drawImage(source, project);
        output.getPixels(pixels, 0, Screen.WIDTH, 0, 0, Screen.WIDTH, Screen.HEIGHT);
        return resultFromPixels();
    }

    private void drawImage(SourceImage source, Project project) {
        int gray = project.crop.backgroundGray;
        canvas.drawColor(Color.rgb(gray, gray, gray));
        project.crop.writeMatrix(matrix, source);
        canvas.drawBitmap(source.bitmap, matrix, imagePaint);
    }

    private void drawZone(ZoneDefinition zone, ZoneStyle style) {
        Rect rect = style.effectiveRect(zone);
        bounds.set(rect.left - style.padding, rect.top - style.padding, rect.right + style.padding, rect.bottom + style.padding);
        if (style.mode.drawsFill) {
            int alpha = Math.round(style.fillOpacityPercent * 255 / 100f);
            fillPaint.setColor(Color.argb(alpha, style.fillGray, style.fillGray, style.fillGray));
            fillPaint.setMaskFilter(style.softEdge > 0
                    ? new BlurMaskFilter(style.softEdge, BlurMaskFilter.Blur.NORMAL)
                    : null);
            canvas.drawRoundRect(bounds, style.cornerRadius, style.cornerRadius, fillPaint);
        }
        if (style.mode.drawsOutline) {
            outlinePaint.setStrokeWidth(style.outlineWidth);
            outlinePaint.setColor(Color.rgb(style.outlineGray, style.outlineGray, style.outlineGray));
            canvas.drawRoundRect(bounds, style.cornerRadius, style.cornerRadius, outlinePaint);
        }
    }

    private RenderResult applyTextColorChoices(Project project) {
        output.getPixels(pixels, 0, Screen.WIDTH, 0, 0, Screen.WIDTH, Screen.HEIGHT);
        boolean changed = false;
        for (Band band : Band.values()) {
            TextColorChoice choice = project.textColorChoices.get(band);
            if (choice.target != null) {
                changed |= TextColorForcer.apply(pixels, Screen.WIDTH, band, choice.target) > 0;
            }
        }
        if (changed) {
            output.setPixels(pixels, 0, Screen.WIDTH, 0, 0, Screen.WIDTH, Screen.HEIGHT);
        }
        return resultFromPixels();
    }

    private RenderResult resultFromPixels() {
        RenderResult result = new RenderResult(output);
        for (Band band : Band.values()) {
            result.setVote(band, FontColorRule.vote(pixels, Screen.WIDTH, band));
        }
        return result;
    }
}
