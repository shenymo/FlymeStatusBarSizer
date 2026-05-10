package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

final class IconMetrics {
    // Keep a single shared visual band inside the 22dp icon box. The height ratio is derived
    // from the widest current normalized battery canvas so all icon types share exact
    // top/bottom/baseline metrics without per-icon vertical patches.
    private static final float VISUAL_CANVAS_HEIGHT_RATIO = 1f / 1.8f;
    private static final float BASELINE_OFFSET_PX = 0f;
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

    static VisualCanvas resolveCenteredVisualCanvas(Rect bounds, float aspectRatio,
                                                    VisualCanvas outCanvas) {
        return resolveVisualCanvas(bounds, aspectRatio, HorizontalAlignment.CENTER, outCanvas);
    }

    static VisualCanvas resolveStartVisualCanvas(Rect bounds, float aspectRatio,
                                                 VisualCanvas outCanvas) {
        return resolveVisualCanvas(bounds, aspectRatio, HorizontalAlignment.START, outCanvas);
    }

    private static VisualCanvas resolveVisualCanvas(Rect bounds, float aspectRatio,
                                                    HorizontalAlignment horizontalAlignment,
                                                    VisualCanvas outCanvas) {
        VisualCanvas target = outCanvas == null ? new VisualCanvas() : outCanvas;
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            target.setEmpty();
            return target;
        }
        float safeAspectRatio = aspectRatio <= 0f ? SIGNAL_BOX_ASPECT_RATIO : aspectRatio;
        float side = Math.min(bounds.width(), bounds.height());
        float squareLeft = horizontalAlignment == HorizontalAlignment.START
                ? bounds.left
                : bounds.left + (bounds.width() - side) / 2f;
        float squareTop = bounds.top + (bounds.height() - side) / 2f;
        float canvasHeight = side * VISUAL_CANVAS_HEIGHT_RATIO;
        float canvasWidth = canvasHeight * safeAspectRatio;
        float left = horizontalAlignment == HorizontalAlignment.START
                ? squareLeft
                : squareLeft + (side - canvasWidth) / 2f;
        float top = squareTop + (side - canvasHeight) / 2f;
        target.rect.set(left, top, left + canvasWidth, top + canvasHeight);
        target.baselineY = target.rect.bottom - BASELINE_OFFSET_PX;
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

    enum HorizontalAlignment {
        CENTER,
        START
    }

    static final class VisualCanvas {
        final RectF rect = new RectF();
        float baselineY;

        boolean isEmpty() {
            return rect.isEmpty();
        }

        void setEmpty() {
            rect.setEmpty();
            baselineY = 0f;
        }
    }
}
