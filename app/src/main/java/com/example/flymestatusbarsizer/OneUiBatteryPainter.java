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
    private static final int CHARGING_FILL_COLOR = 0xff00cd55;
    private static final int LOW_BATTERY_RED = Color.rgb(255, 59, 48);
    private static final int LOW_BATTERY_ORANGE = Color.rgb(255, 149, 0);
    private static final int EMPTY_BACKGROUND_ALPHA = 0x4D;
    private static final int RENDER_ALPHA = 224;
    private static final float VISUAL_ASPECT_RATIO = 1.72f;
    private static final float BOLT_WIDTH_RATIO = 0.56f;
    private static final float BOLT_GAP_RATIO = 0.05f;
    private static final float BOLT_TRAILING_PADDING_RATIO = 0.03f;
    private static final Paint BODY_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CUTOUT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF BODY = new RectF();
    private static final RectF BOLT = new RectF();
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

    static int getRequiredWidth(int squareSize, boolean showBolt, int bodyWidthPercent) {
        if (squareSize <= 0) {
            return 0;
        }
        float side = squareSize * bodyWidthPercent / 100f;
        if (!showBolt) {
            return Math.round(Math.max(squareSize, side));
        }
        float boltGap = Math.max(1f, side * BOLT_GAP_RATIO);
        float boltWidth = Math.max(1f, side * BOLT_WIDTH_RATIO);
        float trailingPadding = Math.max(1f, side * BOLT_TRAILING_PADDING_RATIO);
        return Math.round(side + boltGap + boltWidth + trailingPadding);
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            boolean quickCharging,
            int fillColor, int textColor, boolean showLevelText, float textScale, Typeface typeface,
            float bodyYOffsetPx, float textYOffsetPx, float boltYOffsetPx,
            boolean hollow, boolean hollowFillFollowsLevel, int bodyWidthPercent,
            int bodyHeightPercent, int cornerRadiusPercent) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        int effectiveFillColor = resolveLevelFillColor(clampedLevel, charging, fillColor);
        IconMetrics.resolveStartVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        if (VISUAL_CANVAS.isEmpty()) {
            return;
        }
        RectF visualBounds = VISUAL_CANVAS.rect;
        float visualWidth = visualBounds.width() * bodyWidthPercent / 100f;
        float visualHeight = visualBounds.height() * bodyHeightPercent / 100f;
        float left = visualBounds.centerX() - visualWidth / 2f;
        float top = visualBounds.centerY() - visualHeight / 2f;
        float bottom = top + visualHeight;
        float radius = Math.min(visualHeight / 2f,
                visualHeight * 0.5f * cornerRadiusPercent / 100f);

        BODY.set(left, top, left + visualWidth, bottom);
        BOLT.setEmpty();
        BODY_CONTENT.set(BODY);
        float contentRadius = radius;
        boolean showBolt = charging || pluggedIn;
        boolean useQuickBolt = charging && quickCharging;
        float normalizedTextScale = normalizeTextScale(textScale);
        String levelText = Integer.toString(clampedLevel);
        float textSize = BODY.height() * 0.62f * normalizedTextScale;
        applyTextTypeface(typeface);
        if (showLevelText) {
            TEXT_PAINT.setTextSize(textSize);
        }
        int renderedBodyColor = withFixedAlpha(fillColor, EMPTY_BACKGROUND_ALPHA);
        int renderedFillColor = withFixedAlpha(charging ? CHARGING_FILL_COLOR : effectiveFillColor, RENDER_ALPHA);
        int renderedTextColor = withFixedAlpha(textColor, RENDER_ALPHA);
        int renderedBoltColor = withFixedAlpha(resolveBoltColor(pluggedIn, charging, fillColor), RENDER_ALPHA);
        if (showBolt) {
            float boltLeft = BODY.right + Math.max(1f, visualWidth * BOLT_GAP_RATIO);
            BOLT.set(boltLeft, BODY.top, bounds.right, BODY.bottom);
        }
        applyBodyVerticalOffset(bodyYOffsetPx);
        if (showBolt && boltYOffsetPx != 0) {
            BOLT.offset(0f, -boltYOffsetPx);
        }
        if (hollow) {
            drawHollowBattery(canvas, contentRadius, renderedBodyColor, renderedFillColor,
                    clampedLevel, levelText, textSize,
                    textYOffsetPx, showLevelText, showBolt, renderedBoltColor, useQuickBolt,
                    hollowFillFollowsLevel);
            return;
        }

        drawBodyRange(canvas, contentRadius, renderedFillColor, 0f, clampedLevel);
        drawBodyRange(canvas, contentRadius, renderedBodyColor, clampedLevel, 100f);

        if (showBolt) {
            BatteryBoltPainter.draw(canvas, BOLT, BODY.width(), BODY.height(),
                    renderedBoltColor, BOLT_WIDTH_RATIO, 1f, useQuickBolt);
        }

        if (showLevelText) {
            TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY() - (TEXT_PAINT.descent() + TEXT_PAINT.ascent()) / 2f
                    - textYOffsetPx;
            TEXT_PAINT.setColor(renderedTextColor);
            canvas.drawText(levelText, BODY.centerX(), textBaseline, TEXT_PAINT);
        }
    }

    private static void drawHollowBattery(Canvas canvas, float contentRadius, int emptyColor, int fillColor,
            int level, String levelText, float textSize, float textYOffsetPx,
            boolean showLevelText, boolean showBolt,
            int boltColor, boolean quickCharging, boolean fillFollowsLevel) {
        if (!showLevelText) {
            if (fillFollowsLevel) {
                drawBodyRange(canvas, contentRadius, fillColor, 0f, level);
                drawBodyRange(canvas, contentRadius, emptyColor, level, 100f);
            } else {
                drawBodyRange(canvas, contentRadius, fillColor, 0f, 100f);
            }
            if (showBolt) {
                BatteryBoltPainter.draw(canvas, BOLT, BODY.width(), BODY.height(),
                        boltColor, BOLT_WIDTH_RATIO, 1f, quickCharging);
            }
            return;
        }
        int layer = canvas.saveLayer(BODY.left, BODY.top, Math.max(BODY.right, BOLT.right), BODY.bottom, null);
        if (fillFollowsLevel) {
            drawBodyRange(canvas, contentRadius, fillColor, 0f, level);
            drawBodyRange(canvas, contentRadius, emptyColor, level, 100f);
        } else {
            drawBodyRange(canvas, contentRadius, fillColor, 0f, 100f);
        }
        if (showBolt) {
            BatteryBoltPainter.draw(canvas, BOLT, BODY.width(), BODY.height(),
                    boltColor, BOLT_WIDTH_RATIO, 1f, quickCharging);
        }
        if (showLevelText) {
            CUTOUT_TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY()
                    - (CUTOUT_TEXT_PAINT.descent() + CUTOUT_TEXT_PAINT.ascent()) / 2f
                    - textYOffsetPx;
            canvas.drawText(levelText, BODY.centerX(), textBaseline, CUTOUT_TEXT_PAINT);
        }
        canvas.restoreToCount(layer);
    }

    private static void applyBodyVerticalOffset(float bodyYOffsetPx) {
        if (bodyYOffsetPx == 0f) {
            return;
        }
        float offsetY = -bodyYOffsetPx;
        BODY.offset(0f, offsetY);
        BODY_CONTENT.offset(0f, offsetY);
        if (!BOLT.isEmpty()) {
            BOLT.offset(0f, offsetY);
        }
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

    private static int resolveBoltColor(boolean pluggedIn, boolean charging, int fillColor) {
        if (charging) {
            return CHARGING_FILL_COLOR;
        }
        if (pluggedIn) {
            return fillColor;
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
