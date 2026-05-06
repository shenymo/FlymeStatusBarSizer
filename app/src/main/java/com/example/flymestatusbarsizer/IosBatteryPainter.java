package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

final class IosBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = Color.rgb(0, 205, 85);
    private static final int LOW_BATTERY_RED = Color.rgb(255, 59, 48);
    private static final int LOW_BATTERY_ORANGE = Color.rgb(255, 149, 0);
    private static final int RENDER_ALPHA = 224;
    private static final float BOLT_WIDTH_RATIO = 0.26f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CUTOUT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF BODY_CONTENT = new RectF();
    private static final RectF CAP_CONTENT = new RectF();
    private static final RectF FILL = new RectF();

    static {
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        CUTOUT_TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private IosBatteryPainter() {
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            int fillColor, int textColor, boolean showLevelText, float textScale, Typeface typeface,
            boolean hollow, boolean hollowFillFollowsLevel) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        int effectiveFillColor = resolveLevelFillColor(clampedLevel, charging, fillColor);
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
        float capRadius = capWidth * 0.45f;

        BODY.set(left, top, left + bodyWidth, top + bodyHeight);
        CAP.set(BODY.right + gap, BODY.top + bodyHeight * 0.28f,
                BODY.right + gap + capWidth, BODY.bottom - bodyHeight * 0.28f);
        BODY_CONTENT.set(BODY);
        CAP_CONTENT.set(CAP);
        float contentRadius = radius;
        float capContentRadius = capRadius;

        boolean showBolt = charging || pluggedIn;
        float normalizedTextScale = normalizeTextScale(textScale);
        String levelText = Integer.toString(clampedLevel);
        float textSize = bodyHeight * 0.62f * normalizedTextScale;
        applyTextTypeface(typeface);
        float textWidth = 0f;
        if (showLevelText) {
            TEXT_PAINT.setTextSize(textSize);
            textWidth = TEXT_PAINT.measureText(levelText);
        }
        int renderedBodyColor = withFixedAlpha(BODY_COLOR, RENDER_ALPHA);
        int renderedFillColor = withFixedAlpha(charging ? CHARGING_FILL_COLOR : effectiveFillColor, RENDER_ALPHA);
        int renderedTextColor = withFixedAlpha(textColor, RENDER_ALPHA);
        if (hollow) {
            drawHollowBattery(canvas, contentRadius, capContentRadius, renderedBodyColor, renderedFillColor,
                    clampedLevel, levelText,
                    textSize, showLevelText, showBolt, normalizedTextScale, textWidth,
                    hollowFillFollowsLevel);
            return;
        }

        drawBodyAndCapRange(canvas, contentRadius, capContentRadius, renderedFillColor, 0f, clampedLevel);
        drawBodyAndCapRange(canvas, contentRadius, capContentRadius, renderedBodyColor, clampedLevel, 100f);

        float textCenterX = BODY.centerX();
        if (showBolt) {
            textCenterX = BatteryBoltPainter.draw(
                    canvas, BODY, renderedTextColor, showLevelText, BOLT_WIDTH_RATIO, normalizedTextScale, textWidth);
        }

        if (showLevelText) {
            TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY() - (TEXT_PAINT.descent() + TEXT_PAINT.ascent()) / 2f;
            TEXT_PAINT.setColor(renderedTextColor);
            canvas.drawText(levelText, textCenterX, textBaseline, TEXT_PAINT);
        }
    }

    private static void drawHollowBattery(Canvas canvas, float contentRadius, float capContentRadius,
            int emptyColor, int fillColor, int level, String levelText, float textSize,
            boolean showLevelText, boolean showBolt, float contentScale, float textWidth,
            boolean fillFollowsLevel) {
        int layer = canvas.saveLayer(BODY.left, BODY.top, CAP.right, BODY.bottom, null);
        if (fillFollowsLevel) {
            drawBodyAndCapRange(canvas, contentRadius, capContentRadius, fillColor, 0f, level);
            drawBodyAndCapRange(canvas, contentRadius, capContentRadius, emptyColor, level, 100f);
        } else {
            drawBodyAndCapRange(canvas, contentRadius, capContentRadius, fillColor, 0f, 100f);
        }
        float textCenterX = BODY.centerX();
        if (showBolt) {
            textCenterX = BatteryBoltPainter.drawCutout(
                    canvas, BODY, showLevelText, BOLT_WIDTH_RATIO, contentScale, textWidth, CUTOUT_TEXT_PAINT);
        }
        if (showLevelText) {
            CUTOUT_TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY() - (CUTOUT_TEXT_PAINT.descent() + CUTOUT_TEXT_PAINT.ascent()) / 2f;
            canvas.drawText(levelText, textCenterX, textBaseline, CUTOUT_TEXT_PAINT);
        }
        canvas.restoreToCount(layer);
    }

    private static void drawBodyAndCapRange(Canvas canvas, float contentRadius, float capContentRadius,
            int color, float startPercent, float endPercent) {
        if (BODY_CONTENT.width() <= 0f || BODY_CONTENT.height() <= 0f) {
            return;
        }
        float clampedStart = Math.max(0f, Math.min(100f, startPercent));
        float clampedEnd = Math.max(0f, Math.min(100f, endPercent));
        if (clampedEnd <= clampedStart) {
            return;
        }
        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        float totalFillWidth = BODY_CONTENT.width() + Math.max(0f, CAP_CONTENT.width());
        float startWidth = totalFillWidth * clampedStart / 100f;
        float endWidth = totalFillWidth * clampedEnd / 100f;

        float bodyStart = Math.min(BODY_CONTENT.width(), Math.max(0f, startWidth));
        float bodyEnd = Math.min(BODY_CONTENT.width(), Math.max(0f, endWidth));
        if (bodyEnd > bodyStart) {
            FILL.set(BODY_CONTENT.left + bodyStart, BODY_CONTENT.top,
                    BODY_CONTENT.left + bodyEnd, BODY_CONTENT.bottom);
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY_CONTENT, contentRadius, contentRadius, PAINT);
            canvas.restore();
        }

        if (CAP_CONTENT.width() <= 0f || CAP_CONTENT.height() <= 0f) {
            return;
        }
        float capStart = Math.min(CAP_CONTENT.width(), Math.max(0f, startWidth - BODY_CONTENT.width()));
        float capEnd = Math.min(CAP_CONTENT.width(), Math.max(0f, endWidth - BODY_CONTENT.width()));
        if (capEnd > capStart) {
            FILL.set(CAP_CONTENT.left + capStart, CAP_CONTENT.top,
                    CAP_CONTENT.left + capEnd, CAP_CONTENT.bottom);
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(CAP_CONTENT, capContentRadius, capContentRadius, PAINT);
            canvas.restore();
        }
    }

    private static int withFixedAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int resolveLevelFillColor(int level, boolean charging, int fillColor) {
        if (charging) {
            return CHARGING_FILL_COLOR;
        }
        if (level <= 10) {
            return LOW_BATTERY_RED;
        }
        if (level <= 20) {
            return LOW_BATTERY_ORANGE;
        }
        return fillColor;
    }

    private static float normalizeTextScale(float textScale) {
        if (textScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, textScale));
    }

    private static void applyTextTypeface(Typeface typeface) {
        TEXT_PAINT.setTypeface(typeface);
        CUTOUT_TEXT_PAINT.setTypeface(typeface);
    }
}
