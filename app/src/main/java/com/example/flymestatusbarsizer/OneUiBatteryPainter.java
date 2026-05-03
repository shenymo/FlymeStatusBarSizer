package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

final class OneUiBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = 0xff00cd55;
    private static final int LOW_BATTERY_RED = Color.rgb(255, 59, 48);
    private static final int LOW_BATTERY_ORANGE = Color.rgb(255, 149, 0);
    private static final int NORMAL_FILL_ALPHA = 224;
    private static final int CHARGING_FILL_ALPHA = 242;
    private static final float BOLT_WIDTH_RATIO = 0.22f;
    private static final Paint BODY_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF FILL = new RectF();

    static {
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setFakeBoldText(true);
    }

    private OneUiBatteryPainter() {
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            int fillColor, int textColor, boolean showLevelText, float textScale, Typeface typeface) {
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
        BODY_PAINT.setStyle(Paint.Style.FILL);
        BODY_PAINT.setColor(BODY_COLOR);
        canvas.drawRoundRect(BODY, radius, radius, BODY_PAINT);

        float fillRight = BODY.left + BODY.width() * clampedLevel / 100f;
        if (fillRight > BODY.left) {
            FILL.set(BODY.left, BODY.top, fillRight, BODY.bottom);
            BODY_PAINT.setColor(charging
                    ? withMaxAlpha(CHARGING_FILL_COLOR, CHARGING_FILL_ALPHA)
                    : withMaxAlpha(effectiveFillColor, NORMAL_FILL_ALPHA));
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY, radius, radius, BODY_PAINT);
            canvas.restore();
        }

        boolean showBolt = charging || pluggedIn;
        float textCenterX = BODY.centerX();
        float normalizedTextScale = normalizeTextScale(textScale);
        String levelText = Integer.toString(clampedLevel);
        float textSize = BODY.height() * 0.62f * normalizedTextScale;
        applyTextTypeface(typeface);
        if (showBolt) {
            float textWidth = 0f;
            if (showLevelText) {
                TEXT_PAINT.setTextSize(textSize);
                textWidth = TEXT_PAINT.measureText(levelText);
            }
            textCenterX = BatteryBoltPainter.draw(
                    canvas, BODY, textColor, showLevelText, BOLT_WIDTH_RATIO, normalizedTextScale, textWidth);
        }

        if (showLevelText) {
            TEXT_PAINT.setTextSize(textSize);
            float textBaseline = BODY.centerY() - (TEXT_PAINT.descent() + TEXT_PAINT.ascent()) / 2f;
            TEXT_PAINT.setColor(textColor);
            canvas.drawText(levelText, textCenterX, textBaseline, TEXT_PAINT);
        }
    }

    private static int withMaxAlpha(int color, int maxAlpha) {
        int alpha = Color.alpha(color);
        if (alpha == 0) {
            alpha = 255;
        }
        return Color.argb(Math.min(alpha, maxAlpha), Color.red(color), Color.green(color), Color.blue(color));
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
    }
}
