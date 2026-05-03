package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class BatteryBoltPainter {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();

    static {
        PAINT.setStyle(Paint.Style.FILL);
    }

    private BatteryBoltPainter() {
    }

    static float draw(Canvas canvas, RectF body, int color, boolean showLevelText,
            float widthRatio) {
        if (canvas == null || body == null) {
            return 0f;
        }
        PAINT.setColor(color);
        float resolvedWidthRatio = Math.max(0.1f, widthRatio);
        float iconLeft = showLevelText
                ? body.left + body.width() * 0.16f
                : body.centerX() - body.width() * (resolvedWidthRatio * 0.41f);
        float iconTop = body.top + body.height() * 0.22f;
        float iconWidth = body.width() * resolvedWidthRatio;
        float iconHeight = body.height() * 0.56f;
        PATH.reset();
        PATH.moveTo(iconLeft + iconWidth * 0.48f, iconTop);
        PATH.lineTo(iconLeft + iconWidth * 0.10f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.48f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.24f, iconTop + iconHeight);
        PATH.lineTo(iconLeft + iconWidth * 0.90f, iconTop + iconHeight * 0.34f);
        PATH.lineTo(iconLeft + iconWidth * 0.62f, iconTop + iconHeight * 0.34f);
        PATH.close();
        canvas.drawPath(PATH, PAINT);
        return showLevelText ? body.left + body.width() * 0.62f : body.centerX();
    }
}
