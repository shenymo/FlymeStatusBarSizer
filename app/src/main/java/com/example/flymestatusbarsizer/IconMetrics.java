package com.example.flymestatusbarsizer;

import android.graphics.Rect;
import android.graphics.RectF;

final class IconMetrics {
    private static final float VISUAL_MAX_WIDTH_RATIO = 1f;
    private static final float VISUAL_MAX_HEIGHT_RATIO = 0.56f;
    private static final float BASELINE_OFFSET_PX = 1f;

    private IconMetrics() {
    }

    static RectF resolveCenteredContentRect(Rect bounds, float sourceWidth, float sourceHeight,
                                            RectF outRect) {
        return resolveContentRect(bounds, sourceWidth, sourceHeight, false, outRect);
    }

    static RectF resolveStartContentRect(Rect bounds, float sourceWidth, float sourceHeight,
                                         RectF outRect) {
        return resolveContentRect(bounds, sourceWidth, sourceHeight, true, outRect);
    }

    static float resolveBaselineY(RectF contentRect) {
        if (contentRect == null || contentRect.isEmpty()) {
            return 0f;
        }
        return contentRect.bottom - BASELINE_OFFSET_PX;
    }

    private static RectF resolveContentRect(Rect bounds, float sourceWidth, float sourceHeight,
                                            boolean startAlignedSquare, RectF outRect) {
        RectF target = outRect == null ? new RectF() : outRect;
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            target.setEmpty();
            return target;
        }
        float safeSourceWidth = Math.max(0.0001f, sourceWidth);
        float safeSourceHeight = Math.max(0.0001f, sourceHeight);
        float side = Math.min(bounds.width(), bounds.height());
        float squareLeft = startAlignedSquare
                ? bounds.left
                : bounds.left + (bounds.width() - side) / 2f;
        float squareTop = bounds.top + (bounds.height() - side) / 2f;
        float maxWidth = side * VISUAL_MAX_WIDTH_RATIO;
        float maxHeight = side * VISUAL_MAX_HEIGHT_RATIO;
        float scale = Math.min(maxWidth / safeSourceWidth, maxHeight / safeSourceHeight);
        float width = safeSourceWidth * scale;
        float height = safeSourceHeight * scale;
        float left = squareLeft + (side - width) / 2f;
        float top = squareTop + (side - height) / 2f;
        target.set(left, top, left + width, top + height);
        return target;
    }
}
