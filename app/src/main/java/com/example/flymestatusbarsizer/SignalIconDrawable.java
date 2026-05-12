package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import android.view.View;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

final class SignalIconDrawable extends Drawable {
    private static final Rect DRAW_BOUNDS = new Rect();
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
        Rect drawBounds = resolveCenteredDrawBounds(bounds);
        if (drawBounds.isEmpty()) {
            return;
        }
        updateDrawColor(getState());
        int color = SignalPreviewPainter.modulateColorAlpha(drawColor, alpha);
        View ownerView = ownerViewRef.get();
        ModuleConfig config = ModuleConfig.load(ownerView == null
                ? ModuleConfig.getSystemUiContext()
                : ownerView.getContext());
        float signalYOffsetPx = resolveSignalYOffsetPx(config, ownerView, mergedDual);
        float badgeYOffsetPx = resolveSignalBadgeYOffsetPx(config, ownerView);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(
                    canvas, drawBounds, color, colorFilter, mobileTypeBadge,
                    primarySignalLevel, secondarySignalLevel, signalYOffsetPx, badgeYOffsetPx);
        } else {
            SignalPreviewPainter.drawSingleSim(
                    canvas, drawBounds, color, colorFilter, mobileTypeBadge, primarySignalLevel,
                    signalYOffsetPx, badgeYOffsetPx);
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

    private static float resolveSignalYOffsetPx(ModuleConfig config, View ownerView, boolean mergedDual) {
        int offsetTenthDp = mergedDual
                ? (config == null
                ? SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10
                : SettingsStore.normalizeIconYOffsetTenthDp(config.signalDualYOffsetTenthDp))
                : (config == null
                ? SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10
                : SettingsStore.normalizeIconYOffsetTenthDp(config.signalSingleYOffsetTenthDp));
        return offsetDpToPx(ownerView, offsetTenthDp);
    }

    private static float resolveSignalBadgeYOffsetPx(ModuleConfig config, View ownerView) {
        int offsetTenthDp = config == null
                ? SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10
                : SettingsStore.normalizeIconYOffsetTenthDp(config.signalBadgeYOffsetTenthDp);
        return offsetDpToPx(ownerView, offsetTenthDp);
    }

    private static float offsetDpToPx(View ownerView, int offsetTenthDp) {
        if (ownerView == null) {
            android.content.Context context = ModuleConfig.getSystemUiContext();
            return SettingsStore.positionOffsetTenthDpToPx(context, offsetTenthDp);
        }
        return SettingsStore.positionOffsetTenthDpToPx(ownerView.getContext(), offsetTenthDp);
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

}
