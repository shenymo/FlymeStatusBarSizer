package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class IosBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = Color.rgb(0, 205, 85);
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF FILL = new RectF();
    private static final Rect TEXT_BOUNDS = new Rect();

    private IosBatteryPainter() {
    }

    static void draw(Drawable drawable, Canvas canvas, int level, boolean pluggedIn, boolean charging,
            boolean showPercent) {
        draw(canvas, drawable.getBounds(), level, pluggedIn, charging, showPercent,
                SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE,
                SettingsStore.DEFAULT_IOS_BATTERY_TEXT_WEIGHT, Color.BLACK, Color.WHITE);
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            boolean showPercent, int textSizePercent, int textWeightPercent, int fillColor, int textColor) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        float width = bounds.width();
        float height = bounds.height();
        float capWidth = Math.max(1.2f, width * 0.08f);
        float gap = Math.max(0.8f, width * 0.025f);
        float bodyWidth = width - capWidth - gap;
        float bodyHeight = height * 0.72f;
        float left = bounds.left;
        float top = bounds.exactCenterY() - bodyHeight / 2f;
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
            PAINT.setColor(charging ? CHARGING_FILL_COLOR : fillColor);
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY, radius, radius, PAINT);
            canvas.restore();
        }

        if (charging) {
            drawPercentSplit(canvas, BODY, FILL, fillRight > BODY.left, clampedLevel, textSizePercent,
                    textWeightPercent, textColor, readableTintTextColor(CHARGING_FILL_COLOR),
                    contrastTextColor(CHARGING_FILL_COLOR));
        } else {
            drawPercentSplit(canvas, BODY, FILL, fillRight > BODY.left, clampedLevel, textSizePercent,
                    textWeightPercent, textColor, readableTintTextColor(fillColor),
                    contrastTextColor(fillColor));
        }
    }

    private static void drawPercentSplit(Canvas canvas, RectF body, RectF fill, boolean hasFill,
            int level, int textSizePercent, int textWeightPercent,
            int baseTextColor, int filledTextColor, int strokeColor) {
        drawPercent(canvas, body, level, textSizePercent, textWeightPercent, baseTextColor, strokeColor);
        if (!hasFill) {
            return;
        }
        canvas.save();
        canvas.clipRect(fill);
        drawPercent(canvas, body, level, textSizePercent, textWeightPercent, filledTextColor, strokeColor);
        canvas.restore();
    }

    private static void drawPercent(Canvas canvas, RectF body, int level, int textSizePercent,
            int textWeightPercent, int textColor, int strokeColor) {
        String text = Integer.toString(level);
        float textSize = body.height() * Math.max(40, Math.min(100, textSizePercent)) / 100f;
        float strokeWidth = textSize * Math.max(0f, Math.min(1.6f, (textWeightPercent - 100f) / 250f));
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTextSize(textSize);
        PAINT.setFakeBoldText(true);
        PAINT.setColor(textColor);
        PAINT.getTextBounds(text, 0, text.length(), TEXT_BOUNDS);
        float y = body.centerY() - (PAINT.descent() + PAINT.ascent()) / 2f;
        if (strokeWidth > 0f) {
            PAINT.setStyle(Paint.Style.STROKE);
            PAINT.setStrokeWidth(strokeWidth);
            PAINT.setStrokeJoin(Paint.Join.ROUND);
            PAINT.setStrokeMiter(10f);
            PAINT.setColor(applyAlpha(strokeColor, 215));
            canvas.drawText(text, body.centerX(), y, PAINT);
        }
        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setStrokeWidth(0f);
        PAINT.setColor(textColor);
        canvas.drawText(text, body.centerX(), y, PAINT);
        PAINT.setFakeBoldText(false);
    }

    private static int contrastTextColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return (r * 299 + g * 587 + b * 114) / 1000 < 128 ? Color.WHITE : Color.BLACK;
    }

    private static int readableTintTextColor(int color) {
        int alpha = Color.alpha(color);
        boolean dark = contrastTextColor(color) == Color.WHITE;
        float mix = dark ? 0.78f : 0.72f;
        int target = dark ? Color.WHITE : Color.BLACK;
        return Color.argb(alpha == 0 ? 255 : alpha,
                blendChannel(Color.red(color), Color.red(target), mix),
                blendChannel(Color.green(color), Color.green(target), mix),
                blendChannel(Color.blue(color), Color.blue(target), mix));
    }

    private static int blendChannel(int start, int end, float mix) {
        return Math.max(0, Math.min(255, Math.round(start + (end - start) * mix)));
    }

    private static int applyAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

}
