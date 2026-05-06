package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

final class SignalPreviewPainter {
    private static final int SIGNAL_DRAW_ALPHA = 224;
    private static final float SIGNAL_ASPECT_RATIO = 1.5f;
    private static final float BASELINE_OFFSET_PX = 1f;
    private static final float CORE_BOX_RATIO = 20f / 24f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BAR = new RectF();
    private static final RectF DOT = new RectF();

    private SignalPreviewPainter() {
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color) {
        drawSingleSim(canvas, bounds, color, null);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        drawBars(canvas, buildGeometry(bounds, false), withFixedAlpha(color, SIGNAL_DRAW_ALPHA), colorFilter);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color) {
        drawMergedDualSim(canvas, bounds, color, null);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        SignalGeometry geometry = buildGeometry(bounds, true);
        int drawColor = withFixedAlpha(color, SIGNAL_DRAW_ALPHA);
        drawBars(canvas, geometry, drawColor, colorFilter);
        drawDots(canvas, geometry, drawColor, colorFilter);
    }

    static int resolveIntrinsicWidth(int heightPx) {
        return Math.max(1, heightPx);
    }

    static int resolveIntrinsicHeight(int heightPx) {
        return Math.max(1, heightPx);
    }

    static int withFixedAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static void drawBars(Canvas canvas, SignalGeometry geometry, int color, ColorFilter colorFilter) {
        if (geometry == null) {
            return;
        }
        float radius = Math.min(geometry.barWidth, geometry.unitY * 3.2f) * 0.52f;

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        PAINT.setColorFilter(colorFilter);
        for (int i = 0; i < geometry.heights.length; i++) {
            float barLeft = geometry.startLeft + i * (geometry.barWidth + geometry.gap);
            float barTop = geometry.baseBottom - geometry.heights[i];
            BAR.set(barLeft, barTop, barLeft + geometry.barWidth, geometry.baseBottom);
            canvas.drawRoundRect(BAR, radius, radius, PAINT);
        }
        PAINT.setColorFilter(null);
    }

    private static void drawDots(Canvas canvas, SignalGeometry geometry, int color, ColorFilter colorFilter) {
        if (geometry == null) {
            return;
        }
        float radius = geometry.barWidth / 2f;
        float centerY = geometry.dotCenterY;

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
        PAINT.setColorFilter(colorFilter);
        for (int i = 0; i < 4; i++) {
            float centerX = geometry.startLeft + i * (geometry.barWidth + geometry.gap)
                    + geometry.barWidth / 2f;
            DOT.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawOval(DOT, PAINT);
        }
        PAINT.setColorFilter(null);
    }

    private static SignalGeometry buildGeometry(Rect bounds, boolean mergedDual) {
        float side = Math.min(bounds.width(), bounds.height());
        float coreSide = side * CORE_BOX_RATIO;
        float coreLeft = bounds.left + (bounds.width() - coreSide) / 2f;
        float coreTop = bounds.top + (bounds.height() - coreSide) / 2f;
        float maxVisualWidth = coreSide * (mergedDual ? 0.9f : 0.88f);
        float maxVisualHeight = coreSide * (mergedDual ? 0.78f : 0.74f);
        float visualWidth = Math.min(maxVisualWidth, maxVisualHeight * SIGNAL_ASPECT_RATIO);
        float visualHeight = visualWidth / SIGNAL_ASPECT_RATIO;
        float visualLeft = coreLeft + (coreSide - visualWidth) / 2f;
        float visualTop = coreTop + (coreSide - visualHeight) / 2f;
        float baselineY = visualTop + visualHeight - BASELINE_OFFSET_PX;

        SignalGeometry geometry = new SignalGeometry();
        geometry.unitX = visualWidth;
        geometry.unitY = visualHeight;
        geometry.gap = visualWidth * (mergedDual ? 0.07f : 0.08f);
        geometry.barWidth = (visualWidth - geometry.gap * 3f) / 4f;
        geometry.startLeft = visualLeft;
        if (mergedDual) {
            float dotRadius = geometry.barWidth / 2f;
            geometry.dotCenterY = baselineY - dotRadius;
            geometry.baseBottom = baselineY - dotRadius * 2f - visualHeight * 0.08f;
            float barAreaHeight = Math.max(1f, geometry.baseBottom - visualTop);
            geometry.heights = new float[]{
                    barAreaHeight * 0.36f,
                    barAreaHeight * 0.56f,
                    barAreaHeight * 0.76f,
                    barAreaHeight * 0.96f
            };
        } else {
            geometry.baseBottom = baselineY;
            geometry.dotCenterY = baselineY;
            geometry.heights = new float[]{
                    visualHeight * 0.36f,
                    visualHeight * 0.56f,
                    visualHeight * 0.76f,
                    visualHeight * 0.96f
            };
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
