package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

final class FlymeCapsuleBatteryPainter {
    private static final int CHARGING_FILL_COLOR = Color.rgb(0, 205, 85);
    private static final int LOW_BATTERY_RED = Color.rgb(255, 59, 48);
    private static final int LOW_BATTERY_ORANGE = Color.rgb(255, 149, 0);
    private static final int SHELL_ALPHA = 0x73;
    private static final int FILL_ALPHA = 224;
    private static final int TEXT_OUTLINE_ALPHA = 160;
    private static final float TEXT_STROKE_WIDTH_RATIO = 0.10f;
    private static final float VISUAL_ASPECT_RATIO = 2.1f;
    private static final float BOLT_WIDTH_RATIO = 0.54f;
    private static final float BOLT_GAP_RATIO = 0.05f;
    private static final float BOLT_TRAILING_PADDING_RATIO = 0.03f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CUTOUT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF BODY = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF CAP_ARC = new RectF();
    private static final RectF INNER = new RectF();
    private static final RectF FILL = new RectF();
    private static final RectF BOLT = new RectF();
    private static final Path CAP_PATH = new Path();

    static {
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        CUTOUT_TEXT_PAINT.setFakeBoldText(true);
        CUTOUT_TEXT_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private FlymeCapsuleBatteryPainter() {
    }

    static int getRequiredWidth(int squareSize, boolean showBolt) {
        if (squareSize <= 0) {
            return 0;
        }
        float visualWidth = squareSize * VISUAL_ASPECT_RATIO / 1.8f;
        if (!showBolt) {
            return Math.round(visualWidth);
        }
        float boltGap = Math.max(1f, squareSize * BOLT_GAP_RATIO);
        float boltWidth = Math.max(1f, squareSize * BOLT_WIDTH_RATIO);
        float trailingPadding = Math.max(1f, squareSize * BOLT_TRAILING_PADDING_RATIO);
        return Math.round(visualWidth + boltGap + boltWidth + trailingPadding);
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            boolean quickCharging,
            int fillColor, int textColor, boolean showLevelText, float textScale, Typeface typeface,
            float bodyYOffsetPx, float textYOffsetPx, float boltYOffsetPx,
            boolean hollow, boolean hollowFillFollowsLevel) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        IconMetrics.resolveStartVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        if (VISUAL_CANVAS.isEmpty()) {
            return;
        }
        RectF visualBounds = VISUAL_CANVAS.rect;
        float height = visualBounds.height();
        float capWidth = Math.max(1f, height * 0.15f);
        float gap = Math.max(1f, visualBounds.width() * 0.055f);
        float strokeWidth = Math.max(1f, height * 0.13f);
        float bodyWidth = visualBounds.width() - capWidth - gap;

        BODY.set(visualBounds.left, visualBounds.top, visualBounds.left + bodyWidth,
                VISUAL_CANVAS.baselineY);
        CAP.set(BODY.right + gap, BODY.top + BODY.height() * 0.35f,
                BODY.right + gap + capWidth, BODY.bottom - BODY.height() * 0.35f);
        INNER.set(BODY);
        INNER.inset(strokeWidth * 1.25f, strokeWidth * 1.25f);
        BOLT.setEmpty();

        boolean showBolt = charging || pluggedIn;
        boolean useQuickBolt = charging && quickCharging;
        if (showBolt) {
            float boltLeft = CAP.right + Math.max(1f, visualBounds.width() * BOLT_GAP_RATIO);
            BOLT.set(boltLeft, BODY.top, bounds.right, BODY.bottom);
        }
        applyBodyVerticalOffset(bodyYOffsetPx);
        if (showBolt && boltYOffsetPx != 0f) {
            BOLT.offset(0f, -boltYOffsetPx);
        }

        int shellColor = withFixedAlpha(fillColor, SHELL_ALPHA);
        int batteryFillColor = withFixedAlpha(resolveLevelFillColor(clampedLevel, charging, fillColor),
                FILL_ALPHA);
        float radius = BODY.height() * 0.38f;
        boolean cutoutText = hollow && showLevelText;
        float fillPercent = cutoutText && !hollowFillFollowsLevel ? 100f : clampedLevel;
        float normalizedTextScale = normalizeTextScale(textScale);
        float textSize = BODY.height() * 0.58f * normalizedTextScale;
        TEXT_PAINT.setTypeface(typeface);
        CUTOUT_TEXT_PAINT.setTypeface(typeface);

        if (cutoutText) {
            int layer = saveBatteryLayer(canvas, strokeWidth);
            drawBody(canvas, shellColor, batteryFillColor, strokeWidth, radius, fillPercent);
            drawBoltIfNeeded(canvas, showBolt, useQuickBolt, pluggedIn, charging, fillColor);
            drawCutoutOutlinedLevelText(canvas, clampedLevel, textSize, textYOffsetPx, fillColor);
            canvas.restoreToCount(layer);
            return;
        }

        drawBody(canvas, shellColor, batteryFillColor, strokeWidth, radius, fillPercent);
        drawBoltIfNeeded(canvas, showBolt, useQuickBolt, pluggedIn, charging, fillColor);

        if (showLevelText) {
            drawOutlinedLevelText(canvas, clampedLevel, textSize, textYOffsetPx,
                    fillColor, TEXT_OUTLINE_ALPHA, textColor);
        }
    }

    private static void drawBody(Canvas canvas, int shellColor, int batteryFillColor,
            float strokeWidth, float radius, float fillPercent) {
        PAINT.setStyle(Paint.Style.STROKE);
        PAINT.setStrokeWidth(strokeWidth);
        PAINT.setColor(shellColor);
        canvas.drawRoundRect(BODY, radius, radius, PAINT);

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(shellColor);
        drawDShapedCap(canvas);

        float innerRadius = INNER.height() * 0.34f;
        drawInnerRange(canvas, batteryFillColor, innerRadius, 0f, fillPercent);
    }

    private static void drawInnerRange(Canvas canvas, int color, float innerRadius,
            float startPercent, float endPercent) {
        float clampedStart = Math.max(0f, Math.min(100f, startPercent));
        float clampedEnd = Math.max(0f, Math.min(100f, endPercent));
        if (clampedEnd <= clampedStart) {
            return;
        }
        FILL.set(INNER.left + INNER.width() * clampedStart / 100f, INNER.top,
                INNER.left + INNER.width() * clampedEnd / 100f, INNER.bottom);
        PAINT.setColor(color);
        canvas.save();
        canvas.clipRect(FILL);
        canvas.drawRoundRect(INNER, innerRadius, innerRadius, PAINT);
        canvas.restore();
    }

    private static void drawBoltIfNeeded(Canvas canvas, boolean showBolt, boolean quickCharging,
            boolean pluggedIn, boolean charging, int fillColor) {
        if (!showBolt) {
            return;
        }
        BatteryBoltPainter.draw(canvas, BOLT, BODY.width(), BODY.height(),
                withFixedAlpha(resolveBoltColor(pluggedIn, charging, fillColor), FILL_ALPHA),
                BOLT_WIDTH_RATIO, 1f, quickCharging);
    }

    private static void drawLevelText(Canvas canvas, Paint paint, int level, float textSize,
            float textYOffsetPx) {
        paint.setTextSize(textSize);
        float textBaseline = BODY.centerY() - (paint.descent() + paint.ascent()) / 2f
                - textYOffsetPx;
        canvas.drawText(Integer.toString(level), BODY.centerX(), textBaseline, paint);
    }

    private static void drawOutlinedLevelText(Canvas canvas, int level, float textSize,
            float textYOffsetPx, int outlineColor, int outlineAlpha, int textColor) {
        TEXT_PAINT.setTextSize(textSize);
        float textBaseline = BODY.centerY() - (TEXT_PAINT.descent() + TEXT_PAINT.ascent()) / 2f
                - textYOffsetPx;
        String text = Integer.toString(level);

        TEXT_PAINT.setStyle(Paint.Style.STROKE);
        TEXT_PAINT.setStrokeWidth(Math.max(1f, textSize * TEXT_STROKE_WIDTH_RATIO));
        TEXT_PAINT.setColor(withFixedAlpha(outlineColor, outlineAlpha));
        canvas.drawText(text, BODY.centerX(), textBaseline, TEXT_PAINT);

        TEXT_PAINT.setStyle(Paint.Style.FILL);
        TEXT_PAINT.setColor(withFixedAlpha(textColor, FILL_ALPHA));
        canvas.drawText(text, BODY.centerX(), textBaseline, TEXT_PAINT);
    }

    private static void drawCutoutOutlinedLevelText(Canvas canvas, int level, float textSize,
            float textYOffsetPx, int outlineColor) {
        drawOutlinedLevelText(canvas, level, textSize, textYOffsetPx,
                outlineColor, FILL_ALPHA, outlineColor);
        drawLevelText(canvas, CUTOUT_TEXT_PAINT, level, textSize, textYOffsetPx);
    }

    private static int saveBatteryLayer(Canvas canvas, float strokeWidth) {
        float padding = Math.max(2f, strokeWidth);
        float left = Math.min(BODY.left, CAP.left) - padding;
        float top = Math.min(BODY.top, CAP.top) - padding;
        float right = Math.max(BODY.right, CAP.right) + padding;
        float bottom = Math.max(BODY.bottom, CAP.bottom) + padding;
        if (!BOLT.isEmpty()) {
            left = Math.min(left, BOLT.left - padding);
            top = Math.min(top, BOLT.top - padding);
            right = Math.max(right, BOLT.right + padding);
            bottom = Math.max(bottom, BOLT.bottom + padding);
        }
        return canvas.saveLayer(left, top, right, bottom, null);
    }

    private static void applyBodyVerticalOffset(float bodyYOffsetPx) {
        if (bodyYOffsetPx == 0f) {
            return;
        }
        float offsetY = -bodyYOffsetPx;
        BODY.offset(0f, offsetY);
        CAP.offset(0f, offsetY);
        INNER.offset(0f, offsetY);
        if (!BOLT.isEmpty()) {
            BOLT.offset(0f, offsetY);
        }
    }

    private static void drawDShapedCap(Canvas canvas) {
        float radius = CAP.height() * 0.5f;
        CAP_ARC.set(CAP.left - radius, CAP.top, CAP.left + radius, CAP.bottom);
        CAP_PATH.reset();
        CAP_PATH.moveTo(CAP.left, CAP.top);
        CAP_PATH.arcTo(CAP_ARC, -90f, 180f);
        CAP_PATH.lineTo(CAP.left, CAP.top);
        CAP_PATH.close();
        canvas.drawPath(CAP_PATH, PAINT);
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
        return fillColor;
    }

    private static float normalizeTextScale(float textScale) {
        if (textScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, textScale));
    }
}
