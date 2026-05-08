package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

final class SignalPreviewPainter {
    static final int MOBILE_TYPE_BADGE_NONE = 0;
    static final int MOBILE_TYPE_BADGE_5G = 1;
    static final int MOBILE_TYPE_BADGE_5GA = 2;
    private static final int SIGNAL_DRAW_ALPHA = 224;
    private static final float SIGNAL_ASPECT_RATIO = 1.5f;
    private static final float BASELINE_OFFSET_PX = 1f;
    private static final float CORE_BOX_RATIO = 24f / 24f;
    private static final float MOBILE_TYPE_GAP_RATIO = 0.07f;
    private static final float MOBILE_TYPE_5GA_TRAILING_PADDING_RATIO = 0.08f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BADGE_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BADGE_SUBSCRIPT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BAR = new RectF();
    private static final RectF DOT = new RectF();
    private static final Rect SIGNAL_BOX = new Rect();
    private static final Rect BADGE_MAIN_TEXT_BOUNDS = new Rect();
    private static final Rect BADGE_SUB_TEXT_BOUNDS = new Rect();
    private static final RectF MOBILE_TYPE_BOX = new RectF();

    static {
        BADGE_TEXT_PAINT.setStyle(Paint.Style.FILL);
        BADGE_TEXT_PAINT.setTextAlign(Paint.Align.LEFT);
        BADGE_SUBSCRIPT_TEXT_PAINT.setStyle(Paint.Style.FILL);
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextAlign(Paint.Align.LEFT);
    }

    private SignalPreviewPainter() {
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color) {
        drawSingleSim(canvas, bounds, color, null);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        drawSingleSim(canvas, bounds, color, colorFilter, MOBILE_TYPE_BADGE_NONE);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                              int mobileTypeBadge) {
        drawSignal(canvas, bounds, false, color, colorFilter, mobileTypeBadge);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color) {
        drawMergedDualSim(canvas, bounds, color, null);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        drawMergedDualSim(canvas, bounds, color, colorFilter, MOBILE_TYPE_BADGE_NONE);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                                  int mobileTypeBadge) {
        drawSignal(canvas, bounds, true, color, colorFilter, mobileTypeBadge);
    }

    static int resolveIntrinsicWidth(int heightPx) {
        return resolveIntrinsicWidth(heightPx, MOBILE_TYPE_BADGE_NONE);
    }

    static int resolveIntrinsicWidth(int heightPx, int mobileTypeBadge) {
        int boxSize = Math.max(1, heightPx);
        if (mobileTypeBadge == MOBILE_TYPE_BADGE_NONE) {
            return boxSize;
        }
        MobileTypeTextLayout layout = createMobileTypeTextLayout(
                boxSize,
                mobileTypeBadge,
                0,
                null);
        float totalWidth = boxSize + boxSize * MOBILE_TYPE_GAP_RATIO + layout.badgeWidth;
        return Math.max(1, Math.round(totalWidth));
    }

    static int resolveIntrinsicHeight(int heightPx) {
        return Math.max(1, heightPx);
    }

    static int withFixedAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static void drawSignal(Canvas canvas, Rect bounds, boolean mergedDual, int color,
                                   ColorFilter colorFilter, int mobileTypeBadge) {
        int drawColor = withFixedAlpha(color, SIGNAL_DRAW_ALPHA);
        if (mobileTypeBadge == MOBILE_TYPE_BADGE_NONE) {
            SignalGeometry geometry = buildGeometry(bounds, mergedDual);
            drawBars(canvas, geometry, drawColor, colorFilter);
            if (mergedDual) {
                drawDots(canvas, geometry, drawColor, colorFilter);
            }
            return;
        }
        float boxSize = Math.min(bounds.height(), bounds.width());
        if (boxSize <= 0f) {
            return;
        }
        MobileTypeTextLayout layout = createMobileTypeTextLayout(
                boxSize,
                mobileTypeBadge,
                drawColor,
                colorFilter);
        float contentWidth = boxSize + boxSize * MOBILE_TYPE_GAP_RATIO + layout.badgeWidth;
        float left = bounds.left + (bounds.width() - contentWidth) / 2f;
        float top = bounds.top + (bounds.height() - boxSize) / 2f;
        SIGNAL_BOX.set(
                Math.round(left),
                Math.round(top),
                Math.round(left + boxSize),
                Math.round(top + boxSize));
        SignalGeometry geometry = buildGeometry(SIGNAL_BOX, mergedDual);
        drawBars(canvas, geometry, drawColor, colorFilter);
        if (mergedDual) {
            drawDots(canvas, geometry, drawColor, colorFilter);
        }
        float badgeLeft = left + boxSize * (1f + MOBILE_TYPE_GAP_RATIO);
        MOBILE_TYPE_BOX.set(badgeLeft, top, badgeLeft + layout.badgeWidth, top + boxSize);
        drawMobileTypeBadge(canvas, MOBILE_TYPE_BOX, layout);
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

    private static void drawMobileTypeBadge(Canvas canvas, RectF badgeBounds,
                                            MobileTypeTextLayout layout) {
        if (badgeBounds.isEmpty() || layout == null || layout.badgeType == MOBILE_TYPE_BADGE_NONE) {
            return;
        }
        if (layout.badgeType == MOBILE_TYPE_BADGE_5GA) {
            drawMobileType5gaText(canvas, badgeBounds, layout);
        } else {
            drawMobileType5gText(canvas, badgeBounds, layout);
        }
    }

    private static void drawMobileType5gText(Canvas canvas, RectF badgeBounds,
                                             MobileTypeTextLayout layout) {
        float mainBaseline = resolveMobileTypeMainBaseline(badgeBounds);
        float startX = badgeBounds.left + layout.sidePadding;
        canvas.drawText("5G", startX, mainBaseline, BADGE_TEXT_PAINT);
        BADGE_TEXT_PAINT.setColorFilter(null);
    }

    private static void drawMobileType5gaText(Canvas canvas, RectF badgeBounds,
                                              MobileTypeTextLayout layout) {
        float startX = badgeBounds.left + layout.sidePadding;
        float mainBaseline = resolveMobileTypeMainBaseline(badgeBounds);
        float commonBottom = mainBaseline + BADGE_MAIN_TEXT_BOUNDS.bottom;
        float subBaseline = commonBottom - BADGE_SUB_TEXT_BOUNDS.bottom;

        canvas.drawText("5G", startX, mainBaseline, BADGE_TEXT_PAINT);
        canvas.drawText("A", startX + layout.mainWidth + layout.textGap,
                subBaseline, BADGE_SUBSCRIPT_TEXT_PAINT);
        BADGE_TEXT_PAINT.setColorFilter(null);
        BADGE_SUBSCRIPT_TEXT_PAINT.setColorFilter(null);
    }

    private static float resolveMobileTypeTextGap(float signalBoxSize) {
        return signalBoxSize * 0.04f;
    }

    private static float configureMobileTypeMainPaint(float signalBoxSize, int color,
                                                      ColorFilter colorFilter) {
        float textSize = signalBoxSize * 0.60f;
        applyMobileTypeTextPaintWeight(BADGE_TEXT_PAINT);
        BADGE_TEXT_PAINT.setColor(color);
        BADGE_TEXT_PAINT.setColorFilter(colorFilter);
        BADGE_TEXT_PAINT.setTextSize(textSize);
        BADGE_TEXT_PAINT.getTextBounds("5G", 0, 2, BADGE_MAIN_TEXT_BOUNDS);
        return BADGE_TEXT_PAINT.measureText("5G");
    }

    private static float configureMobileTypeSubPaint(int mainHeight, int color,
                                                     ColorFilter colorFilter) {
        float subTextSize = BADGE_TEXT_PAINT.getTextSize() * 0.5f;
        applyMobileTypeTextPaintWeight(BADGE_SUBSCRIPT_TEXT_PAINT);
        BADGE_SUBSCRIPT_TEXT_PAINT.setColor(color);
        BADGE_SUBSCRIPT_TEXT_PAINT.setColorFilter(colorFilter);
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextSize(subTextSize);
        BADGE_SUBSCRIPT_TEXT_PAINT.getTextBounds("A", 0, 1, BADGE_SUB_TEXT_BOUNDS);
        int subHeight = Math.max(1, BADGE_SUB_TEXT_BOUNDS.height());
        float targetSubHeight = mainHeight * 0.618f;
        subTextSize *= targetSubHeight / subHeight;
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextSize(subTextSize);
        BADGE_SUBSCRIPT_TEXT_PAINT.getTextBounds("A", 0, 1, BADGE_SUB_TEXT_BOUNDS);
        return BADGE_SUBSCRIPT_TEXT_PAINT.measureText("A");
    }

    private static float resolveMobileTypeMainBaseline(RectF badgeBounds) {
        return badgeBounds.centerY()
                - (BADGE_MAIN_TEXT_BOUNDS.top + BADGE_MAIN_TEXT_BOUNDS.bottom) / 2f;
    }

    private static MobileTypeTextLayout createMobileTypeTextLayout(float signalBoxSize,
                                                                   int badgeType,
                                                                   int color,
                                                                   ColorFilter colorFilter) {
        MobileTypeTextLayout layout = new MobileTypeTextLayout();
        layout.badgeType = badgeType;
        layout.textGap = resolveMobileTypeTextGap(signalBoxSize);
        layout.mainWidth = configureMobileTypeMainPaint(signalBoxSize, color, colorFilter);
        layout.mainHeight = Math.max(1, BADGE_MAIN_TEXT_BOUNDS.height());
        layout.subWidth = configureMobileTypeSubPaint(layout.mainHeight, color, colorFilter);
        float contentWidthFor5ga = layout.mainWidth + layout.textGap + layout.subWidth;
        layout.sidePadding = Math.max(0f, (signalBoxSize - contentWidthFor5ga) / 2f);
        if (badgeType == MOBILE_TYPE_BADGE_5GA) {
            layout.badgeWidth = signalBoxSize
                    + signalBoxSize * MOBILE_TYPE_5GA_TRAILING_PADDING_RATIO;
        } else {
            layout.badgeWidth = layout.mainWidth + layout.sidePadding * 2f;
        }
        return layout;
    }

    private static void applyMobileTypeTextPaintWeight(Paint paint) {
        if (paint == null) {
            return;
        }
        int fontWeight = FlymeStatusBarSizer.resolveSignalMobileTypeBadgeFontWeight();
        Typeface typeface;
        try {
            typeface = Typeface.create(Typeface.SANS_SERIF, fontWeight, false);
        } catch (Throwable ignored) {
            typeface = Typeface.defaultFromStyle(fontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (typeface != null) {
            paint.setTypeface(typeface);
        }
        paint.setFakeBoldText(fontWeight >= 600);
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

    private static final class MobileTypeTextLayout {
        int badgeType;
        int mainHeight;
        float mainWidth;
        float subWidth;
        float textGap;
        float sidePadding;
        float badgeWidth;
    }
}
