package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

final class OneUiBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = 0xff00cd55;
    private static final int LOW_BATTERY_RED = Color.rgb(255, 59, 48);
    private static final int LOW_BATTERY_ORANGE = Color.rgb(255, 149, 0);
    private static final int RENDER_ALPHA = 224;
    private static final float BOLT_WIDTH_RATIO = 0.22f;
    private static final Paint BODY_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CUTOUT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF BODY_CONTENT = new RectF();
    private static final RectF FILL = new RectF();
    static {
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        CUTOUT_TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private OneUiBatteryPainter() {
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
        float visualHeight = visualWidth / 1.72f;
        float left = bounds.left + (bounds.width() - visualWidth) / 2f;
        float top = bounds.top + (bounds.height() - visualHeight) / 2f;
        float radius = visualHeight * 0.5f;

        BODY.set(left, top, left + visualWidth, top + visualHeight);
        BODY_CONTENT.set(BODY);
        float contentRadius = radius;
        boolean showBolt = charging || pluggedIn;
        float normalizedTextScale = normalizeTextScale(textScale);
        String levelText = Integer.toString(clampedLevel);
        float textSize = BODY.height() * 0.62f * normalizedTextScale;
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
            drawHollowBattery(canvas, contentRadius, renderedBodyColor, renderedFillColor,
                    clampedLevel, levelText, textSize,
                    showLevelText, showBolt, normalizedTextScale, textWidth,
                    hollowFillFollowsLevel);
            return;
        }

        drawBodyRange(canvas, contentRadius, renderedFillColor, 0f, clampedLevel);
        drawBodyRange(canvas, contentRadius, renderedBodyColor, clampedLevel, 100f);

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

    private static void drawHollowBattery(Canvas canvas, float contentRadius, int emptyColor, int fillColor,
            int level, String levelText, float textSize, boolean showLevelText, boolean showBolt,
            float contentScale, float textWidth, boolean fillFollowsLevel) {
        int layer = canvas.saveLayer(BODY.left, BODY.top, BODY.right, BODY.bottom, null);
        if (fillFollowsLevel) {
            drawBodyRange(canvas, contentRadius, fillColor, 0f, level);
            drawBodyRange(canvas, contentRadius, emptyColor, level, 100f);
        } else {
            drawBodyRange(canvas, contentRadius, fillColor, 0f, 100f);
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

    private static void drawBodyRange(Canvas canvas, float contentRadius, int color,
            float startPercent, float endPercent) {
        if (BODY_CONTENT.width() <= 0f || BODY_CONTENT.height() <= 0f) {
            return;
        }
        float clampedStart = Math.max(0f, Math.min(100f, startPercent));
        float clampedEnd = Math.max(0f, Math.min(100f, endPercent));
        if (clampedEnd <= clampedStart) {
            return;
        }
        BODY_PAINT.setStyle(Paint.Style.FILL);
        BODY_PAINT.setColor(color);
        float fillLeft = BODY_CONTENT.left + BODY_CONTENT.width() * clampedStart / 100f;
        float fillRight = BODY_CONTENT.left + BODY_CONTENT.width() * clampedEnd / 100f;
        if (fillRight > fillLeft) {
            FILL.set(fillLeft, BODY_CONTENT.top, fillRight, BODY_CONTENT.bottom);
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY_CONTENT, contentRadius, contentRadius, BODY_PAINT);
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
