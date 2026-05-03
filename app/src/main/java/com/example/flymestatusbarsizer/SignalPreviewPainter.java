package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class SignalPreviewPainter {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BAR = new RectF();
    private static final RectF DOT = new RectF();

    private SignalPreviewPainter() {
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color) {
        drawBars(canvas, buildGeometry(bounds, false), color);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color) {
        SignalGeometry geometry = buildGeometry(bounds, true);
        drawBars(canvas, geometry, color);
        drawDots(canvas, geometry, color);
    }

    private static void drawBars(Canvas canvas, SignalGeometry geometry, int color) {
        if (geometry == null) {
            return;
        }
        float radius = Math.min(geometry.barWidth, geometry.unitY * 3.2f) * 0.52f;

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        for (int i = 0; i < geometry.heights.length; i++) {
            float barLeft = geometry.startLeft + i * (geometry.barWidth + geometry.gap);
            float barTop = geometry.baseBottom - geometry.heights[i];
            BAR.set(barLeft, barTop, barLeft + geometry.barWidth, geometry.baseBottom);
            canvas.drawRoundRect(BAR, radius, radius, PAINT);
        }
    }

    private static void drawDots(Canvas canvas, SignalGeometry geometry, int color) {
        if (geometry == null) {
            return;
        }
        float radius = geometry.barWidth / 2f;
        float centerY = geometry.dotCenterY;

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        for (int i = 0; i < 4; i++) {
            float centerX = geometry.startLeft + i * (geometry.barWidth + geometry.gap)
                    + geometry.barWidth / 2f;
            DOT.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawOval(DOT, PAINT);
        }
    }

    private static SignalGeometry buildGeometry(Rect bounds, boolean mergedDual) {
        float side = Math.min(bounds.width(), bounds.height());
        float visualWidth = side * 0.94f;
        float visualHeight = visualWidth / 1.5f;
        float left = bounds.left + (bounds.width() - visualWidth) / 2f;
        float top = bounds.top + (bounds.height() - visualHeight) / 2f;
        float unitX = visualWidth / 24f;
        float unitY = visualHeight / 16f;

        SignalGeometry geometry = new SignalGeometry();
        geometry.unitX = unitX;
        geometry.unitY = unitY;
        geometry.baseBottom = top + unitY * (mergedDual ? 9.8f : 12.0f);
        geometry.dotCenterY = top + unitY * 11.5f;
        geometry.barWidth = unitX * 2.7f;
        geometry.gap = unitX * 1.15f;
        geometry.startLeft = left + unitX * 3.15f;
        if (mergedDual) {
            geometry.heights = new float[]{unitY * 3.0f, unitY * 4.8f, unitY * 6.6f, unitY * 8.4f};
        } else {
            geometry.heights = new float[]{unitY * 4.2f, unitY * 6.6f, unitY * 9.1f, unitY * 11.6f};
        }
        return geometry;
    }

    private static final class SignalGeometry {
        float unitX;
        float unitY;
        float baseBottom;
        float dotCenterY;
        float barWidth;
        float gap;
        float startLeft;
        float[] heights;
    }
}
