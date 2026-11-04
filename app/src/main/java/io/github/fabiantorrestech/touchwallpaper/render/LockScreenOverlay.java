package io.github.fabiantorrestech.touchwallpaper.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import io.github.fabiantorrestech.touchwallpaper.model.Screen;
import io.github.fabiantorrestech.touchwallpaper.model.TextColor;

/**
 * Draws a preview of the lock screen's text on top of a wallpaper. It's only a demo and is never
 * exported. Quote settings come from the ROM's MottoText_style; other text sizes are approximate.
 */
public final class LockScreenOverlay {
    private static final float QUOTE_BOX_TOP = 1065f;
    private static final float QUOTE_BOX_BOTTOM = 1289f;
    private static final int QUOTE_SIDE_MARGIN = 56;
    private static final int QUOTE_MAX_LINES = 5;
    private static final float QUOTE_TEXT_SIZE = 28f;
    private static final float QUOTE_LINE_SPACING = 10f;
    private static final float BOTTOM_ROW_BASELINE = 1406f;

    public void draw(Canvas canvas, TextColor topColor, TextColor bottomColor, String quote) {
        drawTopText(canvas, topColor.argb);
        drawQuote(canvas, bottomColor.argb, quote);
        drawBottomRow(canvas, bottomColor.argb);
    }

    private void drawTopText(Canvas canvas, int color) {
        canvas.drawText("10:49", 60f, 250f, textPaint(color, 96f, Typeface.create("sans-serif-light", Typeface.NORMAL)));
        canvas.drawText("AM", 347f, 262f, textPaint(color, 28f, Typeface.DEFAULT));
        canvas.drawText("Wed, Nov 4", 60f, 324f, textPaint(color, 28f, Typeface.DEFAULT));
    }

    private void drawQuote(Canvas canvas, int color, String quote) {
        TextPaint paint = textPaint(color, QUOTE_TEXT_SIZE, Typeface.DEFAULT);
        int width = Screen.WIDTH - 2 * QUOTE_SIDE_MARGIN;
        StaticLayout layout = StaticLayout.Builder.obtain(quote, 0, quote.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(QUOTE_LINE_SPACING, 1f)
                .setMaxLines(QUOTE_MAX_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        canvas.save();
        canvas.translate(QUOTE_SIDE_MARGIN, QUOTE_BOX_BOTTOM - layout.getHeight());
        canvas.clipRect(new RectF(0, QUOTE_BOX_TOP - QUOTE_BOX_BOTTOM + layout.getHeight(), width, layout.getHeight()));
        layout.draw(canvas);
        canvas.restore();
    }

    private void drawBottomRow(Canvas canvas, int color) {
        canvas.drawText("100%", 20f, BOTTOM_ROW_BASELINE, textPaint(color, 28f, Typeface.DEFAULT));
        TextPaint hint = textPaint(color, 32f, Typeface.DEFAULT);
        hint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Press power button to unlock", Screen.WIDTH / 2f, BOTTOM_ROW_BASELINE, hint);
        Paint ringer = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringer.setColor(color);
        ringer.setStyle(Paint.Style.STROKE);
        ringer.setStrokeWidth(3f);
        canvas.drawRoundRect(new RectF(676f, 1380f, 694f, 1410f), 4f, 4f, ringer);
    }

    private TextPaint textPaint(int color, float size, Typeface typeface) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(typeface);
        return paint;
    }
}
