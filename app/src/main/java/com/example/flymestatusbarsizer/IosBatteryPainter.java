package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class IosBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = Color.rgb(0, 205, 85);
    private static final int NORMAL_FILL_ALPHA = 224;
    private static final int CHARGING_FILL_ALPHA = 242;
    private static final float BOLT_WIDTH_RATIO = 0.26f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_EDGE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF FILL = new RectF();

    static {
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setFakeBoldText(true);
        TEXT_EDGE_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_EDGE_PAINT.setFakeBoldText(true);
        TEXT_EDGE_PAINT.setStyle(Paint.Style.STROKE);
        TEXT_EDGE_PAINT.setStrokeJoin(Paint.Join.ROUND);
    }

    private IosBatteryPainter() {
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            int fillColor, int textColor, boolean showLevelText, float textScale) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        float side = Math.min(bounds.width(), bounds.height());
        float visualWidth = side * (24f / 24f);
        float visualHeight = visualWidth / 1.8f;
        float capWidth = Math.max(1.2f, visualWidth * 0.08f);
        float gap = Math.max(0.8f, visualWidth * 0.025f);
        float bodyWidth = visualWidth - capWidth - gap;
        float bodyHeight = visualHeight;
        float left = bounds.left + (bounds.width() - visualWidth) / 2f;
        float top = bounds.top + (bounds.height() - visualHeight) / 2f;
        float radius = bodyHeight * 0.28f;

        BODY.set(left, top, left + bodyWidth, top + bodyHeight);
        CAP.set(BODY.right + gap, BODY.top + bodyHeight * 0.28f,
                BODY.right + gap + capWidth, BODY.bottom - bodyHeight * 0.28f);

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(BODY_COLOR);
        PAINT.setAlpha(255);
        canvas.drawRoundRect(BODY, radius, radius, PAINT);
        canvas.drawRoundRect(CAP, capWidth * 0.45f, capWidth * 0.45f, PAINT);

        float fillRight = BODY.left + BODY.width() * clampedLevel / 100f;
        if (fillRight > BODY.left) {
            FILL.set(BODY.left, BODY.top, fillRight, BODY.bottom);
            PAINT.setColor(charging
                    ? withMaxAlpha(CHARGING_FILL_COLOR, CHARGING_FILL_ALPHA)
                    : withMaxAlpha(fillColor, NORMAL_FILL_ALPHA));
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY, radius, radius, PAINT);
            canvas.restore();
        }

        boolean showBolt = charging || pluggedIn;
        float textCenterX = BODY.centerX();
        float normalizedTextScale = normalizeTextScale(textScale);
        if (showBolt) {
            textCenterX = BatteryBoltPainter.draw(
                    canvas, BODY, textColor, showLevelText, BOLT_WIDTH_RATIO, normalizedTextScale);
        }

        if (showLevelText) {
            float textSize = bodyHeight * 0.62f * normalizedTextScale;
            TEXT_EDGE_PAINT.setColor(resolveTextEdgeColor(textColor));
            TEXT_EDGE_PAINT.setTextSize(textSize);
            TEXT_EDGE_PAINT.setStrokeWidth(bodyHeight * 0.08f);
            TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY() - (TEXT_PAINT.descent() + TEXT_PAINT.ascent()) / 2f;
            canvas.drawText(Integer.toString(clampedLevel), textCenterX, textBaseline, TEXT_EDGE_PAINT);
            TEXT_PAINT.setColor(textColor);
            canvas.drawText(Integer.toString(clampedLevel), textCenterX, textBaseline, TEXT_PAINT);
        }
    }

    private static int withMaxAlpha(int color, int maxAlpha) {
        int alpha = Color.alpha(color);
        if (alpha == 0) {
            alpha = 255;
        }
        return Color.argb(Math.min(alpha, maxAlpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int resolveTextEdgeColor(int textColor) {
        return Color.red(textColor) + Color.green(textColor) + Color.blue(textColor) >= 384
                ? Color.argb(92, 0, 0, 0)
                : Color.argb(88, 255, 255, 255);
    }

    private static float normalizeTextScale(float textScale) {
        if (textScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, textScale));
    }

}
