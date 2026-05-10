package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.HashMap;

final class SignalPreviewPainter {
    static final int MOBILE_TYPE_BADGE_NONE = 0;
    static final int MOBILE_TYPE_BADGE_5G = 1;
    static final int MOBILE_TYPE_BADGE_5GA = 2;
    private static final int DEFAULT_SIGNAL_LEVEL = 4;
    private static final int SIGNAL_DRAW_ALPHA = 224;
    private static final float INACTIVE_SIGNAL_ALPHA_RATIO = 0.3f;
    private static final float SIGNAL_ASPECT_RATIO = 1.5f;
    private static final float MOBILE_TYPE_GAP_RATIO = 0.07f;
    private static final float MOBILE_TYPE_5GA_TRAILING_PADDING_RATIO = 0.08f;
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BADGE_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BADGE_SUBSCRIPT_TEXT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BAR = new RectF();
    private static final RectF DOT = new RectF();
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final Rect SIGNAL_BOX = new Rect();
    private static final Rect BADGE_MAIN_TEXT_BOUNDS = new Rect();
    private static final Rect BADGE_SUB_TEXT_BOUNDS = new Rect();
    private static final RectF MOBILE_TYPE_BOX = new RectF();
    private static final HashMap<Long, MobileTypeTextLayoutMetrics> MOBILE_TYPE_LAYOUT_CACHE =
            new HashMap<>();
    private static final HashMap<Integer, Typeface> MOBILE_TYPE_TYPEFACE_CACHE = new HashMap<>();

    static {
        BADGE_TEXT_PAINT.setStyle(Paint.Style.FILL);
        BADGE_TEXT_PAINT.setTextAlign(Paint.Align.LEFT);
        BADGE_SUBSCRIPT_TEXT_PAINT.setStyle(Paint.Style.FILL);
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextAlign(Paint.Align.LEFT);
    }

    private SignalPreviewPainter() {
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color) {
        drawSingleSim(canvas, bounds, color, null, MOBILE_TYPE_BADGE_NONE, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        drawSingleSim(canvas, bounds, color, colorFilter, MOBILE_TYPE_BADGE_NONE, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                              int mobileTypeBadge) {
        drawSingleSim(canvas, bounds, color, colorFilter, mobileTypeBadge, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawSingleSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                              int mobileTypeBadge, int signalLevel) {
        drawSignal(canvas, bounds, false, color, colorFilter, mobileTypeBadge,
                signalLevel, signalLevel);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color) {
        drawMergedDualSim(canvas, bounds, color, null, MOBILE_TYPE_BADGE_NONE, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color,
                                  int primarySignalLevel, int secondarySignalLevel) {
        drawMergedDualSim(canvas, bounds, color, null, MOBILE_TYPE_BADGE_NONE,
                primarySignalLevel, secondarySignalLevel);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter) {
        drawMergedDualSim(canvas, bounds, color, colorFilter, MOBILE_TYPE_BADGE_NONE, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                                  int mobileTypeBadge) {
        drawMergedDualSim(canvas, bounds, color, colorFilter, mobileTypeBadge, DEFAULT_SIGNAL_LEVEL);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                                  int mobileTypeBadge, int signalLevel) {
        drawMergedDualSim(canvas, bounds, color, colorFilter, mobileTypeBadge,
                signalLevel, signalLevel);
    }

    static void drawMergedDualSim(Canvas canvas, Rect bounds, int color, ColorFilter colorFilter,
                                  int mobileTypeBadge, int primarySignalLevel,
                                  int secondarySignalLevel) {
        drawSignal(canvas, bounds, true, color, colorFilter, mobileTypeBadge,
                primarySignalLevel, secondarySignalLevel);
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

    static int modulateAlpha(int baseAlpha, int appliedAlpha) {
        int clampedBase = Math.max(0, Math.min(baseAlpha, 255));
        int clampedApplied = Math.max(0, Math.min(appliedAlpha, 255));
        if (clampedBase == 0 || clampedApplied == 0) {
            return 0;
        }
        if (clampedBase == 255) {
            return clampedApplied;
        }
        if (clampedApplied == 255) {
            return clampedBase;
        }
        return (clampedBase * clampedApplied + 127) / 255;
    }

    static int modulateColorAlpha(int color, int appliedAlpha) {
        return withFixedAlpha(color, modulateAlpha((color >>> 24) & 0xff, appliedAlpha));
    }

    private static void drawSignal(Canvas canvas, Rect bounds, boolean mergedDual, int color,
                                   ColorFilter colorFilter, int mobileTypeBadge,
                                   int primarySignalLevel, int secondarySignalLevel) {
        int drawColor = modulateColorAlpha(color, SIGNAL_DRAW_ALPHA);
        int inactiveColor = scaleAlpha(drawColor, INACTIVE_SIGNAL_ALPHA_RATIO);
        if (mobileTypeBadge == MOBILE_TYPE_BADGE_NONE) {
            SignalGeometry geometry = buildGeometry(bounds, mergedDual);
            drawBars(canvas, geometry, drawColor, inactiveColor, colorFilter, primarySignalLevel);
            if (mergedDual) {
                drawDots(canvas, geometry, drawColor, inactiveColor, colorFilter, secondarySignalLevel);
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
        drawBars(canvas, geometry, drawColor, inactiveColor, colorFilter, primarySignalLevel);
        if (mergedDual) {
            drawDots(canvas, geometry, drawColor, inactiveColor, colorFilter, secondarySignalLevel);
        }
        float badgeLeft = left + boxSize * (1f + MOBILE_TYPE_GAP_RATIO);
        MOBILE_TYPE_BOX.set(badgeLeft, top, badgeLeft + layout.badgeWidth, top + boxSize);
        drawMobileTypeBadge(canvas, MOBILE_TYPE_BOX, layout);
    }

    private static void drawBars(Canvas canvas, SignalGeometry geometry, int activeColor,
                                 int inactiveColor, ColorFilter colorFilter, int signalLevel) {
        if (geometry == null) {
            return;
        }
        float radius = Math.min(geometry.barWidth, geometry.unitY * 3.2f) * 0.52f;
        int activeCount = clampSignalLevel(signalLevel);

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColorFilter(colorFilter);
        for (int i = 0; i < geometry.heights.length; i++) {
            PAINT.setColor(i < activeCount ? activeColor : inactiveColor);
            float barLeft = geometry.startLeft + i * (geometry.barWidth + geometry.gap);
            float barTop = geometry.baseBottom - geometry.heights[i];
            BAR.set(barLeft, barTop, barLeft + geometry.barWidth, geometry.baseBottom);
            canvas.drawRoundRect(BAR, radius, radius, PAINT);
        }
        PAINT.setColorFilter(null);
    }

    private static void drawDots(Canvas canvas, SignalGeometry geometry, int activeColor,
                                 int inactiveColor, ColorFilter colorFilter, int signalLevel) {
        if (geometry == null) {
            return;
        }
        float radius = geometry.barWidth / 2f;
        float centerY = geometry.dotCenterY;
        int activeCount = clampSignalLevel(signalLevel);

        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColorFilter(colorFilter);
        for (int i = 0; i < 4; i++) {
            PAINT.setColor(i < activeCount ? activeColor : inactiveColor);
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
        float commonBottom = mainBaseline + layout.mainBoundsBottom;
        float subBaseline = commonBottom - layout.subBoundsBottom;

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
        MobileTypeTextLayoutMetrics metrics = resolveMobileTypeTextLayoutMetrics(
                signalBoxSize,
                MOBILE_TYPE_BADGE_5G);
        BADGE_TEXT_PAINT.setColor(color);
        BADGE_TEXT_PAINT.setColorFilter(colorFilter);
        BADGE_TEXT_PAINT.setTextSize(metrics.mainTextSize);
        applyMobileTypeTextPaintWeight(BADGE_TEXT_PAINT, metrics.fontWeight);
        BADGE_MAIN_TEXT_BOUNDS.set(
                metrics.mainBoundsLeft,
                metrics.mainBoundsTop,
                metrics.mainBoundsRight,
                metrics.mainBoundsBottom);
        return metrics.mainWidth;
    }

    private static float configureMobileTypeSubPaint(int mainHeight, int color,
                                                     ColorFilter colorFilter) {
        float signalBoxSize = BADGE_TEXT_PAINT.getTextSize() / 0.60f;
        MobileTypeTextLayoutMetrics metrics = resolveMobileTypeTextLayoutMetrics(
                signalBoxSize,
                MOBILE_TYPE_BADGE_5GA);
        BADGE_SUBSCRIPT_TEXT_PAINT.setColor(color);
        BADGE_SUBSCRIPT_TEXT_PAINT.setColorFilter(colorFilter);
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextSize(metrics.subTextSize);
        applyMobileTypeTextPaintWeight(BADGE_SUBSCRIPT_TEXT_PAINT, metrics.fontWeight);
        BADGE_SUB_TEXT_BOUNDS.set(
                metrics.subBoundsLeft,
                metrics.subBoundsTop,
                metrics.subBoundsRight,
                metrics.subBoundsBottom);
        return metrics.subWidth;
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
        MobileTypeTextLayoutMetrics metrics = resolveMobileTypeTextLayoutMetrics(
                signalBoxSize,
                badgeType);
        layout.badgeType = badgeType;
        layout.textGap = metrics.textGap;
        layout.mainWidth = configureMobileTypeMainPaint(signalBoxSize, color, colorFilter);
        layout.mainHeight = metrics.mainHeight;
        layout.subWidth = badgeType == MOBILE_TYPE_BADGE_5GA
                ? configureMobileTypeSubPaint(layout.mainHeight, color, colorFilter)
                : metrics.subWidth;
        layout.mainBoundsBottom = metrics.mainBoundsBottom;
        layout.subBoundsBottom = metrics.subBoundsBottom;
        float contentWidthFor5ga = layout.mainWidth + layout.textGap + layout.subWidth;
        layout.sidePadding = metrics.sidePadding;
        if (badgeType == MOBILE_TYPE_BADGE_5GA) {
            layout.badgeWidth = metrics.badgeWidth;
        } else {
            layout.badgeWidth = metrics.badgeWidth;
        }
        return layout;
    }

    private static int clampSignalLevel(int signalLevel) {
        if (signalLevel <= 0) {
            return 0;
        }
        return Math.min(signalLevel, 4);
    }

    private static int scaleAlpha(int color, float ratio) {
        int alpha = (color >>> 24) & 0xff;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * ratio)));
        return (color & 0x00ffffff) | (scaledAlpha << 24);
    }

    private static void applyMobileTypeTextPaintWeight(Paint paint, int fontWeight) {
        if (paint == null) {
            return;
        }
        Typeface typeface = MOBILE_TYPE_TYPEFACE_CACHE.get(fontWeight);
        if (typeface == null) {
            try {
                typeface = Typeface.create(Typeface.SANS_SERIF, fontWeight, false);
            } catch (Throwable ignored) {
                typeface = Typeface.defaultFromStyle(
                        fontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (typeface != null) {
                MOBILE_TYPE_TYPEFACE_CACHE.put(fontWeight, typeface);
            }
        }
        if (typeface != null) {
            paint.setTypeface(typeface);
        }
        paint.setFakeBoldText(fontWeight >= 600);
    }

    private static MobileTypeTextLayoutMetrics resolveMobileTypeTextLayoutMetrics(
            float signalBoxSize, int badgeType) {
        int boxSize = Math.max(1, Math.round(signalBoxSize));
        int fontWeight = FlymeStatusBarSizer.resolveSignalMobileTypeBadgeFontWeight();
        long key = (((long) boxSize) << 32)
                | (((long) badgeType & 0xffffL) << 16)
                | (fontWeight & 0xffffL);
        MobileTypeTextLayoutMetrics cached = MOBILE_TYPE_LAYOUT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        MobileTypeTextLayoutMetrics metrics = buildMobileTypeTextLayoutMetrics(
                boxSize,
                badgeType,
                fontWeight);
        MOBILE_TYPE_LAYOUT_CACHE.put(key, metrics);
        return metrics;
    }

    private static MobileTypeTextLayoutMetrics buildMobileTypeTextLayoutMetrics(
            int boxSize, int badgeType, int fontWeight) {
        MobileTypeTextLayoutMetrics metrics = new MobileTypeTextLayoutMetrics();
        metrics.fontWeight = fontWeight;
        metrics.textGap = resolveMobileTypeTextGap(boxSize);

        Rect mainBounds = new Rect();
        Rect subBounds = new Rect();

        float mainTextSize = boxSize * 0.60f;
        BADGE_TEXT_PAINT.setTextSize(mainTextSize);
        applyMobileTypeTextPaintWeight(BADGE_TEXT_PAINT, fontWeight);
        BADGE_TEXT_PAINT.getTextBounds("5G", 0, 2, mainBounds);
        metrics.mainTextSize = mainTextSize;
        metrics.mainWidth = BADGE_TEXT_PAINT.measureText("5G");
        metrics.mainHeight = Math.max(1, mainBounds.height());
        metrics.mainBoundsLeft = mainBounds.left;
        metrics.mainBoundsTop = mainBounds.top;
        metrics.mainBoundsRight = mainBounds.right;
        metrics.mainBoundsBottom = mainBounds.bottom;

        float subTextSize = mainTextSize * 0.5f;
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextSize(subTextSize);
        applyMobileTypeTextPaintWeight(BADGE_SUBSCRIPT_TEXT_PAINT, fontWeight);
        BADGE_SUBSCRIPT_TEXT_PAINT.getTextBounds("A", 0, 1, subBounds);
        int subHeight = Math.max(1, subBounds.height());
        float targetSubHeight = metrics.mainHeight * 0.618f;
        subTextSize *= targetSubHeight / subHeight;
        BADGE_SUBSCRIPT_TEXT_PAINT.setTextSize(subTextSize);
        BADGE_SUBSCRIPT_TEXT_PAINT.getTextBounds("A", 0, 1, subBounds);
        metrics.subTextSize = subTextSize;
        metrics.subWidth = BADGE_SUBSCRIPT_TEXT_PAINT.measureText("A");
        metrics.subBoundsLeft = subBounds.left;
        metrics.subBoundsTop = subBounds.top;
        metrics.subBoundsRight = subBounds.right;
        metrics.subBoundsBottom = subBounds.bottom;

        float contentWidthFor5ga = metrics.mainWidth + metrics.textGap + metrics.subWidth;
        metrics.sidePadding = Math.max(0f, (boxSize - contentWidthFor5ga) / 2f);
        metrics.badgeWidth = badgeType == MOBILE_TYPE_BADGE_5GA
                ? boxSize + boxSize * MOBILE_TYPE_5GA_TRAILING_PADDING_RATIO
                : metrics.mainWidth + metrics.sidePadding * 2f;
        return metrics;
    }

    private static SignalGeometry buildGeometry(Rect bounds, boolean mergedDual) {
        IconMetrics.resolveCenteredVisualCanvas(bounds, SIGNAL_ASPECT_RATIO, VISUAL_CANVAS);
        if (VISUAL_CANVAS.isEmpty()) {
            return null;
        }
        RectF visualBounds = VISUAL_CANVAS.rect;
        float visualWidth = visualBounds.width();
        float visualHeight = visualBounds.height();
        float visualLeft = visualBounds.left;
        float visualTop = visualBounds.top;
        float baselineY = VISUAL_CANVAS.baselineY;

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
        int mainBoundsBottom;
        int subBoundsBottom;
    }

    private static final class MobileTypeTextLayoutMetrics {
        int fontWeight;
        int mainHeight;
        int mainBoundsLeft;
        int mainBoundsTop;
        int mainBoundsRight;
        int mainBoundsBottom;
        int subBoundsLeft;
        int subBoundsTop;
        int subBoundsRight;
        int subBoundsBottom;
        float mainTextSize;
        float subTextSize;
        float mainWidth;
        float subWidth;
        float textGap;
        float sidePadding;
        float badgeWidth;
    }
}
