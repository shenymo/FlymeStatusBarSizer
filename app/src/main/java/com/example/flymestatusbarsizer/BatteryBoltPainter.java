package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class BatteryBoltPainter {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final float MAX_ICON_AREA_FILL_RATIO = 0.92f;

    static {
        PAINT.setStyle(Paint.Style.FILL);
    }

    private BatteryBoltPainter() {
    }

    static void draw(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            int color, float widthRatio, float contentScale) {
        drawInternal(canvas, area, bodyWidth, bodyHeight, widthRatio, contentScale, PAINT, color);
    }

    static void drawCutout(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            float widthRatio, float contentScale, Paint paint) {
        drawInternal(canvas, area, bodyWidth, bodyHeight, widthRatio, contentScale, paint, 0);
    }

    private static void drawInternal(Canvas canvas, RectF area, float bodyWidth, float bodyHeight,
            float widthRatio, float contentScale, Paint paint, int color) {
        if (canvas == null || area == null) {
            return;
        }
        if (paint == null) {
            return;
        }
        if (paint == PAINT) {
            PAINT.setColor(color);
        }
        float resolvedScale = normalizeContentScale(contentScale);
        float desiredWidth = Math.max(0f, bodyWidth) * Math.max(0.1f, widthRatio) * resolvedScale;
        float desiredHeight = Math.max(0f, bodyHeight) * 0.56f * resolvedScale;
        float iconWidth = Math.min(area.width() * MAX_ICON_AREA_FILL_RATIO, desiredWidth);
        float iconHeight = Math.min(area.height() * MAX_ICON_AREA_FILL_RATIO, desiredHeight);
        if (iconWidth <= 0f || iconHeight <= 0f) {
            return;
        }
        float iconLeft = area.centerX() - iconWidth / 2f;
        float iconTop = area.centerY() - iconHeight / 2f;
        PATH.reset();
        PATH.moveTo(iconLeft + iconWidth * 0.48f, iconTop);
        PATH.lineTo(iconLeft + iconWidth * 0.10f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.48f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.24f, iconTop + iconHeight);
        PATH.lineTo(iconLeft + iconWidth * 0.90f, iconTop + iconHeight * 0.34f);
        PATH.lineTo(iconLeft + iconWidth * 0.62f, iconTop + iconHeight * 0.34f);
        PATH.close();
        canvas.drawPath(PATH, paint);
    }

    private static float normalizeContentScale(float contentScale) {
        if (contentScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, contentScale));
    }
}
