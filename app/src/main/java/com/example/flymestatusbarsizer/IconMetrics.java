package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

final class IconMetrics {
    private static final float VISUAL_MAX_WIDTH_RATIO = 1f;
    private static final float VISUAL_MAX_HEIGHT_RATIO = 0.56f;
    private static final float BASELINE_OFFSET_PX = 1f;
    private static final float SIGNAL_BOX_ASPECT_RATIO = 1f;
    private static final float WIFI_BOX_ASPECT_RATIO = 1f;
    private static final float SHARED_ICON_BOX_DP = 22f;
    private static final float BATTERY_MARGIN_START_DP = 1f;
    private static final float BATTERY_MARGIN_END_DP = 0f;

    private IconMetrics() {
    }

    static int resolveSharedIconBoxHeight(Context context, float scale) {
        int baseHeight = dp(context, SHARED_ICON_BOX_DP);
        return scaleSize(baseHeight, scale);
    }

    static int resolveSignalBoxHeight(Context context, float scale) {
        return Math.max(1, resolveSharedIconBoxHeight(context, scale));
    }

    static int resolveSignalBoxWidth(Context context, float scale, float aspectRatio) {
        return resolveBoxWidth(resolveSignalBoxHeight(context, scale), aspectRatio);
    }

    static int resolveWifiBoxSize(Context context, float scale) {
        return resolveBoxWidth(resolveSignalBoxHeight(context, scale), WIFI_BOX_ASPECT_RATIO);
    }

    static int resolveBatteryBoxHeight(Context context, float scale) {
        return Math.max(1, resolveSharedIconBoxHeight(context, scale));
    }

    static int resolveBatteryMarginStart(Context context, float scale) {
        return dp(context, BATTERY_MARGIN_START_DP * scale);
    }

    static int resolveBatteryMarginEnd(Context context, float scale) {
        return dp(context, BATTERY_MARGIN_END_DP * scale);
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

    private static int resolveBoxWidth(int height, float aspectRatio) {
        float safeAspectRatio = aspectRatio <= 0f ? SIGNAL_BOX_ASPECT_RATIO : aspectRatio;
        return Math.max(1, Math.round(height * safeAspectRatio));
    }

    private static int scaleSize(int original, float scale) {
        if (original <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(original * scale));
    }

    private static int dp(Context context, float value) {
        if (context == null) {
            return Math.max(1, Math.round(value));
        }
        return Math.max(0, Math.round(value * context.getResources().getDisplayMetrics().density));
    }
}
