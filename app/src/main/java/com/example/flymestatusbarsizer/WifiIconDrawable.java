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
    private static final float VIEWPORT_SIZE = 24f;
    private static final float CENTER_X = 12f;
    private static final float CENTER_Y = 21.5f;
    private static final float DOT_RADIUS = 4.1f;
    private static final float INNER_ARC_RADIUS = 10.1f;
    private static final float OUTER_ARC_RADIUS = 16.1f;
    private static final float ARC_START_ANGLE = 225f;
    private static final float ARC_SWEEP_ANGLE = 90f;
    private static final float ARC_STROKE_WIDTH = 2.6f;
    private static final float MOBILE_SIGNAL_VISIBLE_HEIGHT_RATIO =
            Math.min(0.88f / 1.5f, 0.74f) * 0.96f;
    private static final float WIFI_SOURCE_VISIBLE_HEIGHT_RATIO = OUTER_ARC_RADIUS / VIEWPORT_SIZE;
    private static final float WIFI_DRAW_SCALE =
            MOBILE_SIGNAL_VISIBLE_HEIGHT_RATIO / WIFI_SOURCE_VISIBLE_HEIGHT_RATIO;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path DOT_PATH = new Path();
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
        float width = bounds.width();
        float height = bounds.height();
        float side = Math.min(width, height);
        float unit = side * WIFI_DRAW_SCALE / VIEWPORT_SIZE;
        float drawWidth = VIEWPORT_SIZE * unit;
        float drawLeft = bounds.left + (width - drawWidth) / 2f;
        float drawTop = bounds.top;

        drawDot(canvas, drawLeft, drawTop, unit, resolveDotColor(activeColor, inactiveColor));
        drawArc(canvas, drawLeft, drawTop, unit,
                INNER_ARC_RADIUS, resolveInnerArcColor(activeColor, inactiveColor));
        drawArc(canvas, drawLeft, drawTop, unit,
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

    private void drawDot(Canvas canvas, float left, float top, float unit, int color) {
        float centerX = left + CENTER_X * unit;
        float centerY = top + CENTER_Y * unit;
        float radius = DOT_RADIUS * unit;
        DOT_PATH.reset();
        DOT_PATH.moveTo(centerX, centerY);
        DOT_PATH.lineTo(left + 9.1f * unit, top + 18.6f * unit);
        setOvalRect(centerX, centerY, radius);
        DOT_PATH.arcTo(OVAL_RECT, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false);
        DOT_PATH.close();
        FILL_PAINT.setColor(color);
        FILL_PAINT.setColorFilter(colorFilter);
        canvas.drawPath(DOT_PATH, FILL_PAINT);
        FILL_PAINT.setColorFilter(null);
    }

    private void drawArc(Canvas canvas, float left, float top, float unit, float radius, int color) {
        float strokeWidth = ARC_STROKE_WIDTH * unit;
        float centerX = left + CENTER_X * unit;
        float centerY = top + CENTER_Y * unit;
        setOvalRect(centerX, centerY, radius * unit);
        STROKE_PAINT.setStrokeWidth(strokeWidth);
        STROKE_PAINT.setColor(color);
        STROKE_PAINT.setColorFilter(colorFilter);
        canvas.drawArc(OVAL_RECT, ARC_START_ANGLE, ARC_SWEEP_ANGLE, false, STROKE_PAINT);
        STROKE_PAINT.setColorFilter(null);
    }

    private static void setOvalRect(float centerX, float centerY, float radius) {
        OVAL_RECT.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
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
