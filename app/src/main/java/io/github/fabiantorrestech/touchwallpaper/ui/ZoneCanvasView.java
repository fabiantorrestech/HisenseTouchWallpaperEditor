package io.github.fabiantorrestech.touchwallpaper.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;

/**
 * Step 2 canvas: shows the rendered wallpaper and lets the selected zone be moved or resized.
 * During a drag only the rectangle redraws; the wallpaper re-renders on finger lift, to limit ghosting.
 */
public final class ZoneCanvasView extends View {
    public interface Listener {
        void onZoneRectChanged(Rect rect);

        /** A tap away from the selected zone, in wallpaper coordinates. */
        void onCanvasTapped(int wallpaperX, int wallpaperY);
    }

    private enum DragKind {
        NONE, MOVE, RESIZE
    }

    private static final int MIN_ZONE_SIZE = 16;
    private static final float HANDLE_TOUCH_DP = 18f;
    private static final float TAP_SLOP_DP = 8f;

    private final FrameMapper mapper = new FrameMapper();
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint();
    private final float handleTouchPixels;
    private final float tapSlopPixels;

    private Bitmap image;
    private Rect selected;
    private Listener listener;

    private DragKind dragKind = DragKind.NONE;
    private boolean dragLeft;
    private boolean dragTop;
    private boolean dragRight;
    private boolean dragBottom;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean movedPastSlop;

    public ZoneCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        float density = context.getResources().getDisplayMetrics().density;
        handleTouchPixels = HANDLE_TOUCH_DP * density;
        tapSlopPixels = TAP_SLOP_DP * density;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setImage(Bitmap image) {
        this.image = image;
        invalidate();
    }

    /** Sets the zone being edited. The view works on its own copy until the finger lifts. */
    public void setSelectedRect(Rect rect) {
        selected = rect == null ? null : new Rect(rect);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        mapper.layout(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        if (image != null) {
            canvas.drawBitmap(image, null, mapper.frame, imagePaint);
        }
        if (selected != null) {
            drawSelection(canvas);
        }
    }

    private void drawSelection(Canvas canvas) {
        float left = mapper.toViewX(selected.left);
        float top = mapper.toViewY(selected.top);
        float right = mapper.toViewX(selected.right);
        float bottom = mapper.toViewY(selected.bottom);
        // White under black keeps the selection visible over any wallpaper.
        linePaint.setStrokeWidth(5f);
        linePaint.setColor(Color.WHITE);
        canvas.drawRect(left, top, right, bottom, linePaint);
        linePaint.setStrokeWidth(2f);
        linePaint.setColor(Color.BLACK);
        canvas.drawRect(left, top, right, bottom, linePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                movedPastSlop = false;
                beginDrag(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                movedPastSlop |= Math.hypot(x - downX, y - downY) > tapSlopPixels;
                if (movedPastSlop) {
                    continueDrag(x, y);
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                finishGesture(x, y);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragKind = DragKind.NONE;
                return true;
            default:
                return true;
        }
    }

    private void beginDrag(float x, float y) {
        dragKind = DragKind.NONE;
        if (selected == null) {
            return;
        }
        float left = mapper.toViewX(selected.left);
        float top = mapper.toViewY(selected.top);
        float right = mapper.toViewX(selected.right);
        float bottom = mapper.toViewY(selected.bottom);
        boolean nearVertically = y > top - handleTouchPixels && y < bottom + handleTouchPixels;
        boolean nearHorizontally = x > left - handleTouchPixels && x < right + handleTouchPixels;
        dragLeft = nearVertically && Math.abs(x - left) < handleTouchPixels;
        dragRight = nearVertically && Math.abs(x - right) < handleTouchPixels;
        dragTop = nearHorizontally && Math.abs(y - top) < handleTouchPixels;
        dragBottom = nearHorizontally && Math.abs(y - bottom) < handleTouchPixels;
        if (dragLeft || dragRight || dragTop || dragBottom) {
            dragKind = DragKind.RESIZE;
        } else if (x > left && x < right && y > top && y < bottom) {
            dragKind = DragKind.MOVE;
        }
    }

    private void continueDrag(float x, float y) {
        int deltaX = Math.round(mapper.toWallpaperLength(x - lastX));
        int deltaY = Math.round(mapper.toWallpaperLength(y - lastY));
        if (dragKind == DragKind.MOVE) {
            selected.offset(deltaX, deltaY);
            keepInsideScreen();
        } else if (dragKind == DragKind.RESIZE) {
            resize(deltaX, deltaY);
        } else {
            return;
        }
        invalidate();
    }

    private void resize(int deltaX, int deltaY) {
        if (dragLeft) {
            selected.left = clamp(selected.left + deltaX, 0, selected.right - MIN_ZONE_SIZE);
        }
        if (dragRight) {
            selected.right = clamp(selected.right + deltaX, selected.left + MIN_ZONE_SIZE, Screen.WIDTH);
        }
        if (dragTop) {
            selected.top = clamp(selected.top + deltaY, 0, selected.bottom - MIN_ZONE_SIZE);
        }
        if (dragBottom) {
            selected.bottom = clamp(selected.bottom + deltaY, selected.top + MIN_ZONE_SIZE, Screen.HEIGHT);
        }
    }

    private void keepInsideScreen() {
        selected.offset(
                Math.max(0, -selected.left) - Math.max(0, selected.right - Screen.WIDTH),
                Math.max(0, -selected.top) - Math.max(0, selected.bottom - Screen.HEIGHT));
    }

    private void finishGesture(float x, float y) {
        if (listener == null) {
            dragKind = DragKind.NONE;
            return;
        }
        if (!movedPastSlop) {
            listener.onCanvasTapped(Math.round(mapper.toWallpaperX(x)), Math.round(mapper.toWallpaperY(y)));
        } else if (dragKind != DragKind.NONE) {
            listener.onZoneRectChanged(new Rect(selected));
        }
        dragKind = DragKind.NONE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
