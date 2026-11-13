package com.example.flymestatusbarsizer.feature.battery;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import com.example.flymestatusbarsizer.feature.statusbar.IconMetrics;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;

public final class BatteryIconPainter {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final PorterDuffXfermode CLEAR = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    private static final RectF CLIP = new RectF();

    private BatteryIconPainter() { }

    public static int measureWidth(BatteryIconPack pack, int boxHeight, boolean showBolt) {
        return Math.max(1, (int) Math.ceil(IconMetrics.resolveVisualBandHeight(boxHeight)
                * pack.layoutBounds(showBolt).width() / pack.visualBounds.height()));
    }

    public static void draw(BatteryIconPack pack, Canvas canvas, Rect bounds, int level,
            boolean pluggedIn, boolean charging, boolean quickCharging, int tint, int textColor,
            boolean showText, float textScale, float bodyOffset,
            float boltOffset, boolean hollow, boolean fillFollowsLevel) {
        if (bounds.isEmpty()) return;
        RectF layout = pack.layoutBounds(charging || pluggedIn);
        float visualHeight = IconMetrics.resolveVisualBandHeight(bounds.height());
        float scale = Math.min(visualHeight / pack.visualBounds.height(),
                bounds.width() / layout.width());
        float progress = Math.max(0, Math.min(100, level)) / 100f;
        if (hollow && !fillFollowsLevel) progress = 1;
        int fillColor = charging ? Color.rgb(0, 205, 85)
                : level <= 10 ? Color.rgb(255, 59, 48)
                : level <= 20 ? Color.rgb(255, 149, 0) : tint;
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top + (bounds.height() - pack.visualBounds.height() * scale) / 2f - bodyOffset);
        canvas.scale(scale, scale);
        canvas.translate(-layout.left, -pack.visualBounds.top);
        int layer = hollow && showText ? canvas.saveLayer(null, null) : -1;
        drawRange(pack, pack.background, canvas, tint, progress, 1);
        drawRange(pack, pack.fill, canvas, fillColor, 0, progress);
        pack.shell.drawRaw(canvas, PAINT, tint);
        RingerVectorXml bolt = quickCharging && charging ? pack.quickCharging : pack.charging;
        if ((charging || pluggedIn) && bolt != null) {
            int boltSave = canvas.save();
            canvas.translate(0, -boltOffset / scale);
            bolt.drawRaw(canvas, PAINT, charging ? Color.rgb(0, 205, 85) : tint);
            canvas.restoreToCount(boltSave);
        }
        if (showText) {
            String label = Integer.toString(Math.max(0, Math.min(100, level)));
            TEXT.setFakeBoldText(true);
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setTextSize(pack.textSize * textScale);
            float x = pack.textBounds.centerX();
            float y = pack.textBounds.centerY() - (TEXT.ascent() + TEXT.descent()) / 2f;
            TEXT.setXfermode(null);
            if (pack.textOutline > 0) {
                TEXT.setStyle(Paint.Style.STROKE);
                TEXT.setStrokeWidth(pack.textOutline * textScale);
                TEXT.setColor(tint);
                TEXT.setAlpha(160);
                canvas.drawText(label, x, y, TEXT);
            }
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setColor(textColor);
            TEXT.setAlpha(hollow ? 255 : Math.round(255 * pack.textAlpha));
            TEXT.setXfermode(hollow ? CLEAR : null);
            canvas.drawText(label, x, y, TEXT);
            TEXT.setXfermode(null);
        }
        if (layer != -1) canvas.restoreToCount(layer);
        canvas.restoreToCount(saved);
    }

    private static void drawRange(BatteryIconPack pack, RingerVectorXml vector, Canvas canvas,
            int color, float start, float end) {
        if (vector == null || end <= start) return;
        CLIP.set(pack.fillBounds);
        switch (pack.direction) {
            case LEFT_TO_RIGHT -> {
                CLIP.left += pack.fillBounds.width() * start;
                CLIP.right = pack.fillBounds.left + pack.fillBounds.width() * end;
            }
            case RIGHT_TO_LEFT -> {
                CLIP.left = pack.fillBounds.right - pack.fillBounds.width() * end;
                CLIP.right -= pack.fillBounds.width() * start;
            }
            case BOTTOM_TO_TOP -> {
                CLIP.top = pack.fillBounds.bottom - pack.fillBounds.height() * end;
                CLIP.bottom -= pack.fillBounds.height() * start;
            }
            case TOP_TO_BOTTOM -> {
                CLIP.top += pack.fillBounds.height() * start;
                CLIP.bottom = pack.fillBounds.top + pack.fillBounds.height() * end;
            }
        }
        int saved = canvas.save();
        canvas.clipRect(CLIP);
        vector.drawRaw(canvas, PAINT, color);
        canvas.restoreToCount(saved);
    }
}
