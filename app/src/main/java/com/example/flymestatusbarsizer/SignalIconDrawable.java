package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import java.lang.ref.WeakReference;

final class SignalIconDrawable extends Drawable {
    private static final int SIGNAL_DRAW_ALPHA = 224;
    private final boolean mergedDual;
    private final WeakReference<android.view.View> ownerViewRef;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final int mobileTypeBadge;
    private int signalLevel;
    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;

    SignalIconDrawable(android.view.View ownerView, boolean mergedDual, int intrinsicWidth,
                       int intrinsicHeight, int mobileTypeBadge, int signalLevel) {
        this.ownerViewRef = new WeakReference<>(ownerView);
        this.mergedDual = mergedDual;
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.mobileTypeBadge = mobileTypeBadge;
        this.signalLevel = sanitizeSignalLevel(signalLevel);
    }

    boolean isMergedDual() {
        return mergedDual;
    }

    boolean matchesGeometry(boolean mergedDual, int intrinsicWidth, int intrinsicHeight,
                            int mobileTypeBadge) {
        return this.mergedDual == mergedDual
                && this.intrinsicWidth == Math.max(1, intrinsicWidth)
                && this.intrinsicHeight == Math.max(1, intrinsicHeight)
                && this.mobileTypeBadge == mobileTypeBadge;
    }

    boolean setSignalLevel(int signalLevel) {
        int sanitized = sanitizeSignalLevel(signalLevel);
        if (this.signalLevel == sanitized) {
            return false;
        }
        this.signalLevel = sanitized;
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
        int color = SignalPreviewPainter.withFixedAlpha(drawColor, SIGNAL_DRAW_ALPHA);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(
                    canvas, bounds, color, colorFilter, mobileTypeBadge, signalLevel);
        } else {
            SignalPreviewPainter.drawSingleSim(
                    canvas, bounds, color, colorFilter, mobileTypeBadge, signalLevel);
        }
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

    private static int sanitizeSignalLevel(int signalLevel) {
        if (signalLevel < 0) {
            return 0;
        }
        return Math.min(signalLevel, 4);
    }
}
