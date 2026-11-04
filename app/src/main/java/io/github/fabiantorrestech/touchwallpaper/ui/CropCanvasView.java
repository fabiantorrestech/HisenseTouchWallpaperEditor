package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import io.github.fabiantorrestech.touchwallpaper.model.CropState;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;
import io.github.fabiantorrestech.touchwallpaper.model.SourceImage;
import io.github.fabiantorrestech.touchwallpaper.model.ZoneDefinition;
import java.util.Collections;
import java.util.List;

/** Step 1 canvas: pinch to zoom and drag to place the image inside the wallpaper's frame. */
public final class CropCanvasView extends View {
    public interface Listener {
        /** Called when a gesture ends, so the crop isn't saved on every finger movement. */
        void onCropGestureFinished();
    }

    public static final int MIN_ZOOM_PERCENT = 10;
    public static final int MAX_ZOOM_PERCENT = 1000;
    private static final float PERCENT = 100f;

    private final FrameMapper mapper = new FrameMapper();
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint solidPaint = new Paint();
    private final Paint zonePaint = new Paint();
    private final Matrix drawMatrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;

    private SourceImage source;
    private CropState crop;
    private List<ZoneDefinition> zones = Collections.emptyList();
    private boolean showZones = true;
    private Listener listener;
    private float lastTouchX;
    private float lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    public CropCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(2f);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomAround(mapper.toWallpaperX(detector.getFocusX()), mapper.toWallpaperY(detector.getFocusY()),
                        crop.scale * detector.getScaleFactor());
                return true;
            }
        });
    }

    public void bind(SourceImage source, CropState crop, List<ZoneDefinition> zones, Listener listener) {
        this.source = source;
        this.crop = crop;
        this.zones = zones;
        this.listener = listener;
        invalidate();
    }

    public void setShowZones(boolean showZones) {
        this.showZones = showZones;
        invalidate();
    }

    /** Zoom relative to just covering the wallpaper: 100% means no empty space and no extra zoom. */
    public int zoomPercent() {
        return Math.round(crop.scale / crop.coverScale(source) * PERCENT);
    }

    public void setZoomPercent(int percent) {
        zoomAround(Screen.WIDTH / 2f, Screen.HEIGHT / 2f, crop.coverScale(source) * percent / PERCENT);
    }

    public void nudge(int wallpaperPixelsX, int wallpaperPixelsY) {
        crop.centerX += wallpaperPixelsX;
        crop.centerY += wallpaperPixelsY;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        mapper.layout(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        if (source == null || crop == null) {
            return;
        }
        canvas.save();
        canvas.clipRect(mapper.frame);
        solidPaint.setColor(Color.rgb(crop.backgroundGray, crop.backgroundGray, crop.backgroundGray));
        canvas.drawRect(mapper.frame, solidPaint);
        crop.writeMatrix(drawMatrix, source);
        drawMatrix.postScale(mapper.scale(), mapper.scale());
        drawMatrix.postTranslate(mapper.frame.left, mapper.frame.top);
        canvas.drawBitmap(source.bitmap, drawMatrix, imagePaint);
        if (showZones) {
            drawZones(canvas);
        }
        canvas.restore();
    }

    private void drawZones(Canvas canvas) {
        for (ZoneDefinition zone : zones) {
            Rect rect = zone.rect;
            float left = mapper.toViewX(rect.left);
            float top = mapper.toViewY(rect.top);
            float right = mapper.toViewX(rect.right);
            float bottom = mapper.toViewY(rect.bottom);
            // White under black keeps the zone readable over any image.
            zonePaint.setColor(Color.WHITE);
            canvas.drawRect(left - 2f, top - 2f, right + 2f, bottom + 2f, zonePaint);
            zonePaint.setColor(Color.BLACK);
            canvas.drawRect(left, top, right, bottom, zonePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (source == null || crop == null) {
            return false;
        }
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startDrag(event, 0);
                return true;
            case MotionEvent.ACTION_MOVE:
                continueDrag(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                if (listener != null) {
                    listener.onCropGestureFinished();
                }
                return true;
            default:
                return true;
        }
    }

    private void startDrag(MotionEvent event, int pointerIndex) {
        activePointerId = event.getPointerId(pointerIndex);
        lastTouchX = event.getX(pointerIndex);
        lastTouchY = event.getY(pointerIndex);
    }

    private void continueDrag(MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        if (index < 0) {
            return;
        }
        float x = event.getX(index);
        float y = event.getY(index);
        if (!scaleDetector.isInProgress()) {
            crop.centerX += mapper.toWallpaperLength(x - lastTouchX);
            crop.centerY += mapper.toWallpaperLength(y - lastTouchY);
            invalidate();
        }
        lastTouchX = x;
        lastTouchY = y;
    }

    private void handlePointerUp(MotionEvent event) {
        int liftedIndex = event.getActionIndex();
        if (event.getPointerId(liftedIndex) == activePointerId) {
            startDrag(event, liftedIndex == 0 ? 1 : 0);
        }
    }

    private void zoomAround(float focusX, float focusY, float targetScale) {
        float coverScale = crop.coverScale(source);
        float newScale = Math.max(coverScale * MIN_ZOOM_PERCENT / PERCENT,
                Math.min(coverScale * MAX_ZOOM_PERCENT / PERCENT, targetScale));
        float applied = newScale / crop.scale;
        crop.centerX = focusX + (crop.centerX - focusX) * applied;
        crop.centerY = focusY + (crop.centerY - focusY) * applied;
        crop.scale = newScale;
        invalidate();
    }
}
