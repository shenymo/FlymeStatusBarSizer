package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import android.view.View;

import java.lang.ref.WeakReference;

final class WifiIconDrawable extends Drawable {
    private static final int MAX_LEVEL = 4;
    private static final int DRAW_ALPHA = 224;
    private static final float INACTIVE_ALPHA_RATIO = 0.3f;
    private static final float SOURCE_WIDTH = 50.617f - 4.318f;
    private static final float SOURCE_HEIGHT = 34.332f - 1.117f;
    private static final float VISUAL_ASPECT_RATIO = SOURCE_WIDTH / SOURCE_HEIGHT;
    private static final float ARC_START_ANGLE = 225f;
    private static final float ARC_SWEEP_ANGLE = 90f;
    private static final float INTER_BAND_GAP_TO_THICKNESS_RATIO = 0.8f;
    private static final float SQRT_TWO = (float) Math.sqrt(2d);

    private static final Paint ARC_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SECTOR_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF DRAW_BOUNDS = new RectF();
    private static final RectF ARC_OVAL = new RectF();

    static {
        ARC_PAINT.setStyle(Paint.Style.STROKE);
        ARC_PAINT.setStrokeCap(Paint.Cap.BUTT);
        SECTOR_PAINT.setStyle(Paint.Style.FILL);
    }

    private final WeakReference<View> ownerViewRef;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final int visualBandHeight;

    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;
    private int level;

    WifiIconDrawable(View ownerView, int intrinsicWidth, int intrinsicHeight,
            int visualBandHeight, int level) {
        this.ownerViewRef = new WeakReference<>(ownerView);
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.visualBandHeight = Math.max(1, visualBandHeight);
        this.level = sanitizeLevel(level);
    }

    boolean matchesGeometry(int intrinsicWidth, int intrinsicHeight, int visualBandHeight) {
        return this.intrinsicWidth == Math.max(1, intrinsicWidth)
                && this.intrinsicHeight == Math.max(1, intrinsicHeight)
                && this.visualBandHeight == Math.max(1, visualBandHeight);
    }

    boolean setLevelValue(int level) {
        int sanitized = sanitizeLevel(level);
        if (this.level == sanitized) {
            return false;
        }
        this.level = sanitized;
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        updateDrawColor(getState());
        int baseColor = SignalPreviewPainter.modulateColorAlpha(drawColor, alpha);
        int activeColor = SignalPreviewPainter.modulateColorAlpha(baseColor, DRAW_ALPHA);
        int inactiveColor = scaleAlpha(activeColor, INACTIVE_ALPHA_RATIO);
        IconMetrics.resolveCenteredVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        if (VISUAL_CANVAS.isEmpty()) {
            return;
        }

        DRAW_BOUNDS.set(VISUAL_CANVAS.rect.left, VISUAL_CANVAS.rect.top,
                VISUAL_CANVAS.rect.right, VISUAL_CANVAS.baselineY);
        if (DRAW_BOUNDS.isEmpty()) {
            return;
        }

        float maxOuterBoundary = Math.max(0f, Math.min(
                DRAW_BOUNDS.height(),
                DRAW_BOUNDS.width() * SQRT_TWO / 2f) - 0.01f);
        if (maxOuterBoundary <= 0f) {
            return;
        }

        float thickness = Math.max(0.75f,
                maxOuterBoundary / (3f + 2f * INTER_BAND_GAP_TO_THICKNESS_RATIO));
        float gap = thickness * INTER_BAND_GAP_TO_THICKNESS_RATIO;
        float cx = DRAW_BOUNDS.centerX();
        float cy = DRAW_BOUNDS.bottom;
        float sectorRadius = thickness;
        float innerRadius = sectorRadius + gap + thickness / 2f;
        float outerRadius = innerRadius + gap + thickness;

        drawConcentricArc(canvas, cx, cy, outerRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveOuterBandColor(activeColor, inactiveColor), thickness);
        drawConcentricArc(canvas, cx, cy, innerRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveInnerBandColor(activeColor, inactiveColor), thickness);
        drawSolidSector(canvas, cx, cy, sectorRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveSectorColor(activeColor, inactiveColor));
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(alpha, 255));
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setTintList(ColorStateList tint) {
        tintList = tint;
        updateDrawColor(getState());
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return updateDrawColor(state);
    }

    private boolean updateDrawColor(int[] state) {
        int fallbackColor = tintList == null
                ? Color.WHITE
                : tintList.getColorForState(state == null ? StateSet.NOTHING : state, tintList.getDefaultColor());
        int resolvedColor = FlymeStatusBarSizer.resolveSignalLinkedTintColor(
                ownerViewRef.get(),
                tintList,
                state,
                fallbackColor);
        if (drawColor == resolvedColor) {
            return false;
        }
        drawColor = resolvedColor;
        invalidateSelf();
        return true;
    }

    private void drawConcentricArc(Canvas canvas, float cx, float cy, float radius,
            float startAngle, float sweepAngle, int color, float strokeWidth) {
        if (radius <= 0f) {
            return;
        }
        ARC_OVAL.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ARC_PAINT.setStrokeWidth(strokeWidth);
        ARC_PAINT.setColor(color);
        ARC_PAINT.setColorFilter(colorFilter);
        canvas.drawArc(ARC_OVAL, startAngle, sweepAngle, false, ARC_PAINT);
        ARC_PAINT.setColorFilter(null);
    }

    private void drawSolidSector(Canvas canvas, float cx, float cy, float radius,
            float startAngle, float sweepAngle, int color) {
        if (radius <= 0f) {
            return;
        }
        ARC_OVAL.set(cx - radius, cy - radius, cx + radius, cy + radius);
        SECTOR_PAINT.setColor(color);
        SECTOR_PAINT.setColorFilter(colorFilter);
        canvas.drawArc(ARC_OVAL, startAngle, sweepAngle, true, SECTOR_PAINT);
        SECTOR_PAINT.setColorFilter(null);
    }

    private int resolveSectorColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 1 ? activeColor : inactiveColor;
    }

    private int resolveInnerBandColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 2 ? activeColor : inactiveColor;
    }

    private int resolveOuterBandColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 3 ? activeColor : inactiveColor;
    }

    private int resolveVisibleBars() {
        if (level <= 0) {
            return 1;
        }
        if (level == 1) {
            return 2;
        }
        return 3;
    }

    private static int sanitizeLevel(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, MAX_LEVEL);
    }

    private static int scaleAlpha(int color, float ratio) {
        int alpha = (color >>> 24) & 0xff;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * ratio)));
        return SignalPreviewPainter.withFixedAlpha(color, scaledAlpha);
    }
}
