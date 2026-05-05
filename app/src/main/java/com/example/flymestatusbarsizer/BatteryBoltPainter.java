package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class BatteryBoltPainter {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final float MIN_GROUP_GAP_RATIO = 0.04f;
    private static final float MAX_GROUP_WIDTH_RATIO = 0.92f;

    static {
        PAINT.setStyle(Paint.Style.FILL);
    }

    private BatteryBoltPainter() {
    }

    static float draw(Canvas canvas, RectF body, int color, boolean showLevelText,
            float widthRatio, float contentScale, float textWidth) {
        return drawInternal(canvas, body, showLevelText, widthRatio, contentScale, textWidth, PAINT, color);
    }

    static float drawCutout(Canvas canvas, RectF body, boolean showLevelText,
            float widthRatio, float contentScale, float textWidth, Paint paint) {
        return drawInternal(canvas, body, showLevelText, widthRatio, contentScale, textWidth, paint, 0);
    }

    private static float drawInternal(Canvas canvas, RectF body, boolean showLevelText,
            float widthRatio, float contentScale, float textWidth, Paint paint, int color) {
        if (canvas == null || body == null) {
            return 0f;
        }
        if (paint == null) {
            return 0f;
        }
        if (paint == PAINT) {
            PAINT.setColor(color);
        }
        float resolvedScale = normalizeContentScale(contentScale);
        float resolvedWidthRatio = Math.max(0.1f, widthRatio) * resolvedScale;
        float iconWidth = body.width() * resolvedWidthRatio;
        float iconHeight = body.height() * 0.56f * resolvedScale;
        float iconTop = body.centerY() - iconHeight / 2f;
        float iconLeft;
        float textCenterX;
        if (showLevelText) {
            float gap = Math.max(body.width(), body.height()) * MIN_GROUP_GAP_RATIO;
            float contentWidth = iconWidth + gap + Math.max(0f, textWidth);
            float maxContentWidth = body.width() * MAX_GROUP_WIDTH_RATIO;
            if (contentWidth > maxContentWidth) {
                gap = Math.max(0f, maxContentWidth - iconWidth - Math.max(0f, textWidth));
                contentWidth = iconWidth + gap + Math.max(0f, textWidth);
            }
            float contentLeft = body.centerX() - contentWidth / 2f;
            iconLeft = contentLeft;
            textCenterX = iconLeft + iconWidth + gap + Math.max(0f, textWidth) / 2f;
        } else {
            iconLeft = body.centerX() - iconWidth / 2f;
            textCenterX = body.centerX();
        }
        PATH.reset();
        PATH.moveTo(iconLeft + iconWidth * 0.48f, iconTop);
        PATH.lineTo(iconLeft + iconWidth * 0.10f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.48f, iconTop + iconHeight * 0.52f);
        PATH.lineTo(iconLeft + iconWidth * 0.24f, iconTop + iconHeight);
        PATH.lineTo(iconLeft + iconWidth * 0.90f, iconTop + iconHeight * 0.34f);
        PATH.lineTo(iconLeft + iconWidth * 0.62f, iconTop + iconHeight * 0.34f);
        PATH.close();
        canvas.drawPath(PATH, paint);
        return textCenterX;
    }

    private static float normalizeContentScale(float contentScale) {
        if (contentScale <= 0f) {
            return 1f;
        }
        return Math.max(0.5f, Math.min(2f, contentScale));
    }
}
