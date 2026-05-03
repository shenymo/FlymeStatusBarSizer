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
            float widthRatio, float contentScale) {
        if (canvas == null || body == null) {
            return 0f;
        }
        PAINT.setColor(color);
        float resolvedScale = normalizeContentScale(contentScale);
        float resolvedWidthRatio = Math.max(0.1f, widthRatio) * resolvedScale;
        float iconLeft = showLevelText
                ? body.left + body.width() * (0.16f + (1f - resolvedScale) * 0.08f)
                : body.centerX() - body.width() * (resolvedWidthRatio * 0.41f);
        float iconTop = body.top + body.height() * (0.22f + (1f - resolvedScale) * 0.08f);
        float iconWidth = body.width() * resolvedWidthRatio;
        float iconHeight = body.height() * 0.56f * resolvedScale;
        PATH.reset();
        PATH.moveTo(iconLeft + iconWidth * 0.48f, iconTop);
        PATH.lineTo(iconLeft + iconWidth * 0.10f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.48f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.24f, iconTop + iconHeight);
        PATH.lineTo(iconLeft + iconWidth * 0.90f, iconTop + iconHeight * 0.34f);
        PATH.lineTo(iconLeft + iconWidth * 0.62f, iconTop + iconHeight * 0.34f);
        PATH.close();
        canvas.drawPath(PATH, PAINT);
        return showLevelText
                ? body.left + body.width() * (0.62f + (1f - resolvedScale) * 0.06f)
                : body.centerX();
    }

    private static float normalizeContentScale(float contentScale) {
        if (contentScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, contentScale));
    }
}
