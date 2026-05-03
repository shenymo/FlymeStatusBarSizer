package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class IosBatteryPainter {
    private static final int BODY_COLOR = Color.rgb(150, 150, 150);
    private static final int CHARGING_FILL_COLOR = Color.rgb(0, 205, 85);
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BODY = new RectF();
    private static final RectF CAP = new RectF();
    private static final RectF FILL = new RectF();

    private IosBatteryPainter() {
    }

    static void draw(Canvas canvas, Rect bounds, int level, boolean pluggedIn, boolean charging,
            int fillColor) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        int clampedLevel = Math.max(0, Math.min(100, level));
        float side = Math.min(bounds.width(), bounds.height());
        float visualWidth = side * (20f / 24f);
        float visualHeight = visualWidth / 1.7f;
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
            PAINT.setColor(charging ? CHARGING_FILL_COLOR : fillColor);
            canvas.save();
            canvas.clipRect(FILL);
            canvas.drawRoundRect(BODY, radius, radius, PAINT);
            canvas.restore();
        }
    }

}
