package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    private static final Path BOLT = new Path();

    private IosBatteryPainter() {
    }

    static void draw(Drawable drawable, Canvas canvas, int level, boolean pluggedIn, boolean charging,
            boolean showPercent) {
        draw(canvas, drawable.getBounds(), level, pluggedIn, charging, showPercent,
                SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE, Color.BLACK, Color.WHITE, 0, 0);
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            boolean showPercent, int textSizePercent, int fillColor, int textColor,
            int chargingBoltWidth, int chargingBoltExtraHeight) {
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
            drawPercent(canvas, BODY, clampedLevel, textSizePercent, contrastTextColor(CHARGING_FILL_COLOR));
            drawChargingBolt(canvas, bounds.right, chargingBoltWidth, chargingBoltExtraHeight, fillColor);
        } else {
            drawPercent(canvas, BODY, clampedLevel, textSizePercent, textColor);
        }
    }

    private static void drawPercent(Canvas canvas, RectF body, int level, int textSizePercent, int textColor) {
        String text = Integer.toString(level);
        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setFakeBoldText(true);
        PAINT.setTextAlign(Paint.Align.CENTER);
        PAINT.setTextSize(body.height() * Math.max(40, Math.min(100, textSizePercent)) / 100f);
        PAINT.setColor(textColor);
        PAINT.getTextBounds(text, 0, text.length(), TEXT_BOUNDS);
        float y = body.centerY() - (PAINT.descent() + PAINT.ascent()) / 2f;
        canvas.drawText(text, body.centerX(), y, PAINT);
        PAINT.setFakeBoldText(false);
    }

    private static void drawChargingBolt(Canvas canvas, float left, int width, int extraHeight, int color) {
        if (width <= 0) {
            return;
        }
        float boltHeight = BODY.height() + extraHeight;
        float top = BODY.centerY() - boltHeight / 2f;
        float bottom = BODY.centerY() + boltHeight / 2f;
        float centerY = BODY.centerY();
        float boltLeft = left + width * 0.18f;
        float boltRight = left + width * 0.82f;
        float midLeft = left + width * 0.39f;
        float midRight = left + width * 0.65f;
        BOLT.reset();
        BOLT.moveTo(midRight, top);
        BOLT.lineTo(boltLeft, centerY + boltHeight * 0.08f);
        BOLT.lineTo(midLeft, centerY + boltHeight * 0.08f);
        BOLT.lineTo(midLeft - width * 0.12f, bottom);
        BOLT.lineTo(boltRight, centerY - boltHeight * 0.1f);
        BOLT.lineTo(midRight, centerY - boltHeight * 0.1f);
        BOLT.close();
        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        canvas.drawPath(BOLT, PAINT);
    }

    private static int contrastTextColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return (r * 299 + g * 587 + b * 114) / 1000 < 128 ? Color.WHITE : Color.BLACK;
    }

}
