package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.StateSet;
import android.view.View;
import android.view.ViewParent;

import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

final class SignalIconDrawable extends Drawable {
    private static final String DEBUG_TAG = "FlymeStatusBarSizer";
    private static final Rect DRAW_BOUNDS = new Rect();
    private static final Paint DEBUG_VIEW_BOTTOM_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint DEBUG_WRAPPER_BOTTOM_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final WeakHashMap<View, String> LAST_DEBUG_SIGNATURES = new WeakHashMap<>();
    private final boolean mergedDual;
    private final WeakReference<android.view.View> ownerViewRef;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final int mobileTypeBadge;
    private int primarySignalLevel;
    private int secondarySignalLevel;
    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;

    SignalIconDrawable(android.view.View ownerView, boolean mergedDual, int intrinsicWidth,
                       int intrinsicHeight, int mobileTypeBadge, int primarySignalLevel,
                       int secondarySignalLevel) {
        this.ownerViewRef = new WeakReference<>(ownerView);
        this.mergedDual = mergedDual;
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.mobileTypeBadge = mobileTypeBadge;
        this.primarySignalLevel = sanitizeSignalLevel(primarySignalLevel);
        this.secondarySignalLevel = sanitizeSignalLevel(secondarySignalLevel);
    }

    static {
        DEBUG_VIEW_BOTTOM_PAINT.setStyle(Paint.Style.STROKE);
        DEBUG_VIEW_BOTTOM_PAINT.setStrokeWidth(1.5f);
        DEBUG_VIEW_BOTTOM_PAINT.setColor(Color.BLUE);
        DEBUG_WRAPPER_BOTTOM_PAINT.setStyle(Paint.Style.STROKE);
        DEBUG_WRAPPER_BOTTOM_PAINT.setStrokeWidth(1.5f);
        DEBUG_WRAPPER_BOTTOM_PAINT.setColor(Color.YELLOW);
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
        return setSignalLevels(signalLevel, signalLevel);
    }

    boolean setSignalLevels(int primarySignalLevel, int secondarySignalLevel) {
        int sanitizedPrimary = sanitizeSignalLevel(primarySignalLevel);
        int sanitizedSecondary = sanitizeSignalLevel(secondarySignalLevel);
        if (this.primarySignalLevel == sanitizedPrimary
                && this.secondarySignalLevel == sanitizedSecondary) {
            return false;
        }
        this.primarySignalLevel = sanitizedPrimary;
        this.secondarySignalLevel = sanitizedSecondary;
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        View ownerView = ownerViewRef.get();
        Rect drawBounds = resolveCenteredDrawBounds(bounds);
        if (drawBounds.isEmpty()) {
            return;
        }
        updateDrawColor(getState());
        int color = SignalPreviewPainter.modulateColorAlpha(drawColor, alpha);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(
                    canvas, drawBounds, color, colorFilter, mobileTypeBadge,
                    primarySignalLevel, secondarySignalLevel);
        } else {
            SignalPreviewPainter.drawSingleSim(
                    canvas, drawBounds, color, colorFilter, mobileTypeBadge, primarySignalLevel);
        }
        drawOwnerDebugLines(canvas, ownerView);
        logDebugMetrics(bounds, drawBounds, ownerView);
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

    private Rect resolveCenteredDrawBounds(Rect bounds) {
        View ownerView = ownerViewRef.get();
        if (bounds == null || bounds.isEmpty()) {
            DRAW_BOUNDS.setEmpty();
            return DRAW_BOUNDS;
        }
        int drawWidth = bounds.width();
        int drawHeight = bounds.height();
        if (ownerView != null && drawHeight > 0) {
            View hostView = resolveSignalHostView(ownerView);
            int hostHeight = hostView == null ? ownerView.getHeight() : hostView.getHeight();
            int ownerTopInHost = hostView == null ? 0 : resolveOwnerTopInHost(ownerView, hostView);
            if (hostHeight > 0) {
                int top = Math.round((hostHeight - drawHeight) / 2f) - ownerTopInHost;
                DRAW_BOUNDS.set(bounds.left, top, bounds.left + drawWidth, top + drawHeight);
                return DRAW_BOUNDS;
            }
        }
        DRAW_BOUNDS.set(bounds);
        return DRAW_BOUNDS;
    }

    private void drawOwnerDebugLines(Canvas canvas, View ownerView) {
        if (canvas == null || ownerView == null) {
            return;
        }
        float viewBottomY = ownerView.getHeight() - 1f;
        canvas.drawLine(0f, viewBottomY, ownerView.getWidth(), viewBottomY, DEBUG_VIEW_BOTTOM_PAINT);
        View wrapper = resolveWrapperView(ownerView);
        if (wrapper == null) {
            return;
        }
        float wrapperBottomY = wrapper.getHeight() - ownerView.getTop() - 1f;
        canvas.drawLine(0f, wrapperBottomY, ownerView.getWidth(), wrapperBottomY, DEBUG_WRAPPER_BOTTOM_PAINT);
    }

    private void logDebugMetrics(Rect bounds, Rect drawBounds, View ownerView) {
        if (ownerView == null || bounds == null || drawBounds == null) {
            return;
        }
        View wrapper = resolveWrapperView(ownerView);
        int wrapperHeight = wrapper == null ? -1 : wrapper.getHeight();
        View hostView = resolveSignalHostView(ownerView);
        int hostHeight = hostView == null ? -1 : hostView.getHeight();
        int ownerTopInHost = hostView == null ? 0 : resolveOwnerTopInHost(ownerView, hostView);
        int ownerTopInWrapper = ownerView.getTop();
        int ownerBottomInWrapper = ownerView.getBottom();
        String signature = "bounds=" + formatRect(bounds)
                + " drawBounds=" + formatRect(drawBounds)
                + " viewH=" + ownerView.getHeight()
                + " viewW=" + ownerView.getWidth()
                + " wrapperH=" + wrapperHeight
                + " hostH=" + hostHeight
                + " viewTopInWrapper=" + ownerTopInWrapper
                + " viewBottomInWrapper=" + ownerBottomInWrapper
                + " ownerTopInHost=" + ownerTopInHost
                + " scaleType=" + (ownerView instanceof android.widget.ImageView
                ? ((android.widget.ImageView) ownerView).getScaleType() : "n/a");
        String previous = LAST_DEBUG_SIGNATURES.get(ownerView);
        if (signature.equals(previous)) {
            return;
        }
        LAST_DEBUG_SIGNATURES.put(ownerView, signature);
        Log.d(DEBUG_TAG, "[FSBS_SIGNAL_DEBUG] " + signature);
    }

    private static View resolveWrapperView(View ownerView) {
        if (ownerView == null) {
            return null;
        }
        ViewParent parent = ownerView.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    private static View resolveSignalHostView(View ownerView) {
        if (ownerView == null) {
            return null;
        }
        View candidate = null;
        View current = ownerView;
        while (true) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            View parentView = (View) parent;
            String idName = resolveIdName(parentView);
            String className = parentView.getClass().getName();
            if ("mobile_group".equals(idName)
                    || "mobile_combo".equals(idName)
                    || "com.flyme.systemui.statusbar.net.mobile.ui.view.FlymeModernStatusBarMobileView".equals(className)
                    || "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView".equals(className)
                    || "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView".equals(className)) {
                candidate = parentView;
            }
            current = parentView;
        }
        return candidate;
    }

    private static int resolveOwnerTopInHost(View ownerView, View hostView) {
        if (ownerView == null || hostView == null) {
            return 0;
        }
        int top = 0;
        View current = ownerView;
        while (current != null && current != hostView) {
            top += current.getTop();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return top;
    }

    private static String resolveIdName(View view) {
        if (view == null) {
            return "";
        }
        int id = view.getId();
        if (id == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatRect(Rect rect) {
        if (rect == null) {
            return "null";
        }
        return "[" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + "]";
    }
}
