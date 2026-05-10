package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
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
    // Keep the three WIFI bands on an equal spacing grid so the gaps read as uniform.
    private static final float CENTER_X = 12f;
    private static final float CENTER_Y = 21.5f;
    private static final float DOT_RADIUS = 4.1f;
    private static final float ARC_STROKE_WIDTH = 3.2f;
    private static final float BAND_GAP = 3.45f;
    private static final float INNER_ARC_RADIUS = DOT_RADIUS + BAND_GAP + ARC_STROKE_WIDTH * 0.5f;
    private static final float OUTER_ARC_RADIUS = INNER_ARC_RADIUS + BAND_GAP + ARC_STROKE_WIDTH;
    private static final float ARC_START_ANGLE = 225f;
    private static final float ARC_SWEEP_ANGLE = 90f;
    private static final float DIAGONAL_PROJECTION = 0.70710677f;
    private static final float DOT_EDGE_X = 9.1f;
    private static final float DOT_EDGE_Y = 18.6f;
    // Normalize against the actual painted bounds, not the arc centerline bounds. This keeps
    // WIFI's visible top/bottom within the same visual height as the battery glyph after scaling.
    private static final float PAINTED_HALF_STROKE = ARC_STROKE_WIDTH * 0.5f;
    private static final float PAINTED_LEFT =
            CENTER_X - (OUTER_ARC_RADIUS + PAINTED_HALF_STROKE) * DIAGONAL_PROJECTION;
    private static final float PAINTED_TOP = CENTER_Y - OUTER_ARC_RADIUS - PAINTED_HALF_STROKE;
    private static final float PAINTED_RIGHT =
            CENTER_X + (OUTER_ARC_RADIUS + PAINTED_HALF_STROKE) * DIAGONAL_PROJECTION;
    private static final float PAINTED_BOTTOM = CENTER_Y + DOT_RADIUS;
    private static final float PAINTED_WIDTH = PAINTED_RIGHT - PAINTED_LEFT;
    private static final float PAINTED_HEIGHT = PAINTED_BOTTOM - PAINTED_TOP;
    private static final float HORIZONTAL_SCALE = 0.84f;
    private static final float VISUAL_ASPECT_RATIO =
            (PAINTED_WIDTH * HORIZONTAL_SCALE) / PAINTED_HEIGHT;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path DOT_PATH = new Path();
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF OVAL_RECT = new RectF();

    private final WeakReference<View> ownerViewRef;
    private final int intrinsicWidth;
    private final int intrinsicHeight;

    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;
    private int level;

    WifiIconDrawable(View ownerView, int intrinsicWidth, int intrinsicHeight, int level) {
        this.ownerViewRef = new WeakReference<>(ownerView);
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.level = sanitizeLevel(level);
        STROKE_PAINT.setStyle(Paint.Style.STROKE);
        STROKE_PAINT.setStrokeCap(Paint.Cap.BUTT);
        FILL_PAINT.setStyle(Paint.Style.FILL);
    }

    boolean matchesGeometry(int intrinsicWidth, int intrinsicHeight) {
        return this.intrinsicWidth == Math.max(1, intrinsicWidth)
                && this.intrinsicHeight == Math.max(1, intrinsicHeight);
    }

    static int resolveIntrinsicWidth(int intrinsicHeight) {
        int safeHeight = Math.max(1, intrinsicHeight);
        return Math.max(1, Math.round(safeHeight * VISUAL_ASPECT_RATIO));
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
        float unitY = VISUAL_CANVAS.rect.height() / PAINTED_HEIGHT;
        float unitX = unitY * HORIZONTAL_SCALE;
        float drawLeft = VISUAL_CANVAS.rect.left - PAINTED_LEFT * unitX;
        float drawTop = VISUAL_CANVAS.rect.top - PAINTED_TOP * unitY;

        drawDot(canvas, drawLeft, drawTop, unitX, unitY, resolveDotColor(activeColor, inactiveColor));
        drawArc(canvas, drawLeft, drawTop, unitX, unitY,
                INNER_ARC_RADIUS, resolveInnerArcColor(activeColor, inactiveColor));
        drawArc(canvas, drawLeft, drawTop, unitX, unitY,
                OUTER_ARC_RADIUS, resolveOuterArcColor(activeColor, inactiveColor));
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

    private void drawDot(Canvas canvas, float left, float top, float unitX, float unitY, int color) {
        float centerX = left + CENTER_X * unitX;
        float centerY = top + CENTER_Y * unitY;
        float radiusX = DOT_RADIUS * unitX;
        float radiusY = DOT_RADIUS * unitY;
        DOT_PATH.reset();
        DOT_PATH.moveTo(centerX, centerY);
        DOT_PATH.lineTo(left + DOT_EDGE_X * unitX, top + DOT_EDGE_Y * unitY);
        setOvalRect(centerX, centerY, radiusX, radiusY);
        DOT_PATH.arcTo(OVAL_RECT, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false);
        DOT_PATH.close();
        FILL_PAINT.setColor(color);
        FILL_PAINT.setColorFilter(colorFilter);
        canvas.drawPath(DOT_PATH, FILL_PAINT);
        FILL_PAINT.setColorFilter(null);
    }

    private void drawArc(Canvas canvas, float left, float top, float unitX, float unitY,
            float radius, int color) {
        float strokeWidth = ARC_STROKE_WIDTH * unitY;
        float centerX = left + CENTER_X * unitX;
        float centerY = top + CENTER_Y * unitY;
        setOvalRect(centerX, centerY, radius * unitX, radius * unitY);
        STROKE_PAINT.setStrokeWidth(strokeWidth);
        STROKE_PAINT.setColor(color);
        STROKE_PAINT.setColorFilter(colorFilter);
        canvas.drawArc(OVAL_RECT, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false, STROKE_PAINT);
        STROKE_PAINT.setColorFilter(null);
    }

    private static void setOvalRect(float centerX, float centerY, float radiusX, float radiusY) {
        OVAL_RECT.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);
    }

    private int resolveDotColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 1 ? activeColor : inactiveColor;
    }

    private int resolveInnerArcColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 2 ? activeColor : inactiveColor;
    }

    private int resolveOuterArcColor(int activeColor, int inactiveColor) {
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
