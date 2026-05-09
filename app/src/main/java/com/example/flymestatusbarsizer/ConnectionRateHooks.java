package com.example.flymestatusbarsizer;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

final class ConnectionRateHooks {
    private static final WeakHashMap<View, ConnectionRateViewState> CONNECTION_RATE_VIEW_STATES =
            new WeakHashMap<>();

    private ConnectionRateHooks() {
    }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookConnectionRateView(module, loader);
    }

    static void refreshTrackedViews() {
        FlymeStatusBarSizer.postToMainHandler(() -> {
            ArrayList<View> connectionRateViews =
                    new ArrayList<>(CONNECTION_RATE_VIEW_STATES.keySet());
            for (View view : connectionRateViews) {
                if (view == null) {
                    continue;
                }
                applyConnectionRateTextScale(view);
                applyConnectionRateThresholdVisibility(view);
            }
        });
    }

    private static void hookConnectionRateView(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.statusbar.connectionRateView.ConnectionRateView",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"onAttachedToWindow".equals(name)
                        && !"onConnectionRateChange".equals(name)
                        && !"onConfigurationChanged".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object thisObject = chain.getThisObject();
                    Object result = chain.proceed();
                    if (thisObject instanceof View) {
                        View view = (View) thisObject;
                        trackConnectionRateView(view);
                        FlymeStatusBarSizer.ensureConfigRefreshObserver(view.getContext());
                        if ("onAttachedToWindow".equals(name)
                                || "onConfigurationChanged".equals(name)) {
                            rememberConnectionRateBaseState(view, true);
                            applyConnectionRateTextScale(view);
                        }
                        if ("onConnectionRateChange".equals(name) && chain.getArgs().size() == 2
                                && chain.getArg(0) instanceof Boolean) {
                            Object rateArg = chain.getArg(1);
                            double rate = rateArg instanceof Number
                                    ? ((Number) rateArg).doubleValue()
                                    : ReflectUtils.getDoubleField(view, "mCurrentRate", 0d);
                            applyConnectionRateThresholdVisibility(
                                    view,
                                    (Boolean) chain.getArg(0),
                                    rate);
                        } else {
                            applyConnectionRateThresholdVisibility(view);
                            view.postDelayed(() -> applyConnectionRateThresholdVisibility(view), 500);
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logConnectionRateWarning(
                    "Failed to hook ConnectionRateView",
                    t);
        }
    }

    private static void trackConnectionRateView(View view) {
        if (view == null) {
            return;
        }
        rememberConnectionRateViewState(view);
    }

    private static void rememberConnectionRateBaseState(View view, boolean overwrite) {
        rememberConnectionRateBaseState(
                view,
                resolveConnectionRateUnitView(view),
                rememberConnectionRateViewState(view),
                overwrite);
    }

    private static void rememberConnectionRateBaseState(
            View view, TextView unitView, ConnectionRateViewState state, boolean overwrite) {
        if (view == null || state == null) {
            return;
        }
        int textSize = ReflectUtils.getIntField(view, "mTextSize", 0);
        if (textSize > 0 && (overwrite || state.originalTextSize <= 0)) {
            state.originalTextSize = textSize;
        }
        int maxWidth = ReflectUtils.getIntField(view, "mMaxWidth", 0);
        if (maxWidth > 0 && (overwrite || state.originalMaxWidth <= 0)) {
            state.originalMaxWidth = maxWidth;
        }
        if (overwrite || Float.isNaN(state.originalRotation)) {
            state.originalRotation = view.getRotation();
        }
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            if (overwrite || state.originalOrientation == Integer.MIN_VALUE) {
                state.originalOrientation = group.getOrientation();
            }
        }
        if (unitView == null) {
            if (overwrite) {
                state.hasAppliedTextScaleSignature = false;
            }
            return;
        }
        if (overwrite || Float.isNaN(state.originalUnitTextSize)) {
            state.originalUnitTextSize = unitView.getTextSize();
        }
        if (overwrite || state.originalUnitMinWidth < 0) {
            state.originalUnitMinWidth = unitView.getMinWidth();
        }
        if (overwrite || state.originalUnitMaxLines == Integer.MIN_VALUE) {
            state.originalUnitMaxLines = unitView.getMaxLines();
        }
        if (overwrite || Float.isNaN(state.originalUnitRotation)) {
            state.originalUnitRotation = unitView.getRotation();
        }
        if (overwrite || state.originalUnitPadding == null) {
            state.originalUnitPadding = new int[]{
                    unitView.getPaddingLeft(),
                    unitView.getPaddingTop(),
                    unitView.getPaddingRight(),
                    unitView.getPaddingBottom()
            };
        }
        if (overwrite) {
            state.hasAppliedTextScaleSignature = false;
        }
    }

    private static TextView resolveConnectionRateUnitView(View view) {
        if (view == null) {
            return null;
        }
        Object unit = ReflectUtils.getField(view, "mUnitView");
        if (unit instanceof TextView) {
            return (TextView) unit;
        }
        if (view instanceof ViewGroup) {
            View child = FlymeStatusBarSizer.findSystemUiChildCompat(view, "unit");
            if (child instanceof TextView) {
                return (TextView) child;
            }
        }
        return null;
    }

    private static boolean applyConnectionRateTextScale(View view) {
        if (view == null) {
            return false;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        TextView unitView = resolveConnectionRateUnitView(view);
        ConnectionRateViewState state = rememberConnectionRateViewState(view);
        long signature = getConnectionRateTextScaleSignature(config, unitView != null);
        if (state.hasAppliedTextScaleSignature && state.appliedTextScaleSignature == signature) {
            return false;
        }
        rememberConnectionRateBaseState(view, unitView, state, false);
        boolean enabled = config != null && config.enabled;
        float scale = enabled ? resolveClockAndCarrierTextScale(config) : 1f;
        boolean changed = false;

        if (state.originalTextSize > 0) {
            int targetTextSize = scaleSize(state.originalTextSize, scale);
            int currentTextSize = ReflectUtils.getIntField(view, "mTextSize", 0);
            if (currentTextSize != targetTextSize) {
                ReflectUtils.setIntField(view, "mTextSize", targetTextSize);
                changed = true;
            }
        }

        if (state.originalMaxWidth > 0) {
            int targetMaxWidth = scaleSize(state.originalMaxWidth, scale);
            int currentMaxWidth = ReflectUtils.getIntField(view, "mMaxWidth", 0);
            if (currentMaxWidth != targetMaxWidth) {
                ReflectUtils.setIntField(view, "mMaxWidth", targetMaxWidth);
                changed = true;
            }
        }

        if (applyConnectionRateHorizontalState(view, state, enabled)) {
            changed = true;
        }

        if (unitView != null) {
            if (!Float.isNaN(state.originalUnitTextSize) && state.originalUnitTextSize > 0f) {
                float targetUnitTextSize = state.originalUnitTextSize * scale;
                if (Math.abs(unitView.getTextSize() - targetUnitTextSize) > 0.5f) {
                    unitView.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetUnitTextSize);
                    changed = true;
                }
            }

            if (state.originalUnitMinWidth > 0) {
                int targetMinWidth = scaleSize(state.originalUnitMinWidth, scale);
                if (unitView.getMinWidth() != targetMinWidth) {
                    unitView.setMinWidth(targetMinWidth);
                    changed = true;
                }
            }

            int[] originalPadding = state.originalUnitPadding;
            if (originalPadding != null) {
                int left = scaleInsetSize(originalPadding[0], scale);
                int top = scaleInsetSize(originalPadding[1], scale);
                int right = scaleInsetSize(originalPadding[2], scale);
                int bottom = scaleInsetSize(originalPadding[3], scale);
                if (unitView.getPaddingLeft() != left || unitView.getPaddingTop() != top
                        || unitView.getPaddingRight() != right
                        || unitView.getPaddingBottom() != bottom) {
                    unitView.setPadding(left, top, right, bottom);
                    changed = true;
                }
            }

            if (applyConnectionRateUnitHorizontalState(unitView, state, enabled)) {
                changed = true;
            }
        }

        if (changed) {
            FlymeStatusBarSizer.disableAncestorClipping(view, 4);
            if (unitView != null) {
                unitView.requestLayout();
                unitView.invalidate();
            }
            view.requestLayout();
            view.invalidate();
        }
        state.appliedTextScaleSignature = signature;
        state.hasAppliedTextScaleSignature = true;
        return changed;
    }

    private static long getConnectionRateTextScaleSignature(
            ModuleConfig config, boolean hasUnitView) {
        boolean enabled = config != null && config.enabled;
        int effectivePercent = enabled
                ? SettingsStore.normalizeScalePercent(config.clockAndCarrierTextSizePercent)
                : SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT;
        long signature = 17L;
        signature = signature * 31L + (enabled ? 1L : 0L);
        signature = signature * 31L + effectivePercent;
        signature = signature * 31L + (hasUnitView ? 1L : 0L);
        return signature;
    }

    private static boolean applyConnectionRateHorizontalState(
            View view, ConnectionRateViewState state, boolean enabled) {
        if (view == null || state == null) {
            return false;
        }
        boolean changed = false;
        float targetRotation = enabled
                ? 0f
                : (Float.isNaN(state.originalRotation) ? view.getRotation() : state.originalRotation);
        if (Math.abs(view.getRotation() - targetRotation) > 0.5f) {
            view.setRotation(targetRotation);
            changed = true;
        }
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            int targetOrientation = enabled
                    ? LinearLayout.HORIZONTAL
                    : (state.originalOrientation == Integer.MIN_VALUE
                    ? group.getOrientation()
                    : state.originalOrientation);
            if (group.getOrientation() != targetOrientation) {
                group.setOrientation(targetOrientation);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyConnectionRateUnitHorizontalState(
            TextView unitView, ConnectionRateViewState state, boolean enabled) {
        if (unitView == null || state == null) {
            return false;
        }
        boolean changed = false;
        float targetRotation = enabled
                ? 0f
                : (Float.isNaN(state.originalUnitRotation)
                ? unitView.getRotation()
                : state.originalUnitRotation);
        if (Math.abs(unitView.getRotation() - targetRotation) > 0.5f) {
            unitView.setRotation(targetRotation);
            changed = true;
        }

        int targetMaxLines = enabled
                ? 1
                : (state.originalUnitMaxLines == Integer.MIN_VALUE
                ? unitView.getMaxLines()
                : state.originalUnitMaxLines);
        if (enabled) {
            if (unitView.getMaxLines() != 1) {
                unitView.setSingleLine(true);
                changed = true;
            }
        } else if (state.originalUnitMaxLines != Integer.MIN_VALUE) {
            if (targetMaxLines == 1) {
                if (unitView.getMaxLines() != 1) {
                    unitView.setSingleLine(true);
                    changed = true;
                }
            } else {
                if (unitView.getMaxLines() == 1) {
                    unitView.setSingleLine(false);
                    changed = true;
                }
                if (unitView.getMaxLines() != targetMaxLines) {
                    unitView.setMaxLines(targetMaxLines);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static ConnectionRateViewState rememberConnectionRateViewState(View view) {
        ConnectionRateViewState state = CONNECTION_RATE_VIEW_STATES.get(view);
        if (state == null) {
            state = new ConnectionRateViewState();
            CONNECTION_RATE_VIEW_STATES.put(view, state);
        }
        return state;
    }

    private static void applyConnectionRateThresholdVisibility(View view) {
        if (view == null) {
            return;
        }
        ConnectionRateViewState state = rememberConnectionRateViewState(view);
        applyConnectionRateThresholdVisibility(view, state, ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(
            View view, boolean baseShow, double rate) {
        if (view == null) {
            return;
        }
        applyConnectionRateThresholdVisibility(
                view,
                baseShow,
                rate,
                rememberConnectionRateViewState(view),
                ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(
            View view, ConnectionRateViewState state, ModuleConfig config) {
        if (view == null || state == null || config == null) {
            return;
        }
        boolean thresholdEnabled = config.enabled && config.connectionRateThresholdEnabled;
        if (state.featureEnabled != thresholdEnabled) {
            state.featureEnabled = thresholdEnabled;
            state.resetCounters();
            if (!thresholdEnabled) {
                state.thresholdVisible = state.lastBaseShow;
            } else {
                state.thresholdVisible = false;
            }
        }
        if (!thresholdEnabled) {
            if (!state.hasBaseShow) {
                return;
            }
            ReflectUtils.setBooleanField(view, "mShow", state.lastBaseShow);
            applyConnectionRateVisibleState(
                    view,
                    state.lastBaseShow,
                    ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        ReflectUtils.setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(
                view,
                state.thresholdVisible,
                ReflectUtils.getBooleanField(view, "mEnable", true),
                ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
    }

    private static void applyConnectionRateThresholdVisibility(
            View view, boolean baseShow, double rate,
            ConnectionRateViewState state, ModuleConfig config) {
        if (view == null || state == null || config == null) {
            return;
        }
        state.lastBaseShow = baseShow;
        state.hasBaseShow = true;
        state.lastRate = rate;
        boolean featureEnabled = config.enabled && config.connectionRateThresholdEnabled;
        long configSignature = getConnectionRateThresholdSignature(config);
        if (!featureEnabled) {
            state.featureEnabled = false;
            state.thresholdVisible = baseShow;
            state.resetCounters();
            state.lastConfigSignature = configSignature;
            ReflectUtils.setBooleanField(view, "mShow", baseShow);
            applyConnectionRateVisibleState(
                    view,
                    baseShow,
                    ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        if (!state.featureEnabled) {
            state.reset();
        }
        state.featureEnabled = true;
        if (state.lastConfigSignature != configSignature) {
            state.resetCounters();
            state.lastConfigSignature = configSignature;
        }
        double safeRate = sanitizeConnectionRate(rate);
        int showThreshold = Math.max(
                config.connectionRateShowThresholdKb,
                config.connectionRateHideThresholdKb);
        int hideThreshold = Math.min(
                config.connectionRateShowThresholdKb,
                config.connectionRateHideThresholdKb);
        int showSamples = Math.max(1, config.connectionRateShowSampleCount);
        int hideSamples = Math.max(1, config.connectionRateHideSampleCount);
        if (!baseShow) {
            state.resetCounters();
            state.thresholdVisible = false;
            ReflectUtils.setBooleanField(view, "mShow", false);
            applyConnectionRateVisibleState(
                    view,
                    false,
                    ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        if (state.thresholdVisible) {
            if (safeRate < hideThreshold) {
                state.belowCount++;
                state.aboveCount = 0;
                if (state.belowCount >= hideSamples) {
                    state.thresholdVisible = false;
                    state.belowCount = 0;
                }
            } else {
                state.belowCount = 0;
            }
        } else if (safeRate >= showThreshold) {
            state.aboveCount++;
            state.belowCount = 0;
            if (state.aboveCount >= showSamples) {
                state.thresholdVisible = true;
                state.aboveCount = 0;
            }
        } else {
            state.aboveCount = 0;
        }
        ReflectUtils.setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(
                view,
                state.thresholdVisible,
                ReflectUtils.getBooleanField(view, "mEnable", true),
                ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
    }

    private static void applyConnectionRateVisibleState(
            View view, boolean thresholdVisible, boolean enable, boolean isDemoMode) {
        if (view == null) {
            return;
        }
        int visibility = thresholdVisible && enable ? View.VISIBLE : View.GONE;
        if (!isDemoMode && view.getVisibility() != visibility) {
            view.setVisibility(visibility);
        }
    }

    private static double sanitizeConnectionRate(double rate) {
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate < 0d) {
            return 0d;
        }
        return rate;
    }

    private static long getConnectionRateThresholdSignature(ModuleConfig config) {
        if (config == null) {
            return 0L;
        }
        int showThreshold = Math.max(
                config.connectionRateShowThresholdKb,
                config.connectionRateHideThresholdKb);
        int hideThreshold = Math.min(
                config.connectionRateShowThresholdKb,
                config.connectionRateHideThresholdKb);
        int showSamples = Math.max(1, config.connectionRateShowSampleCount);
        int hideSamples = Math.max(1, config.connectionRateHideSampleCount);
        long signature = 17L;
        signature = signature * 31L
                + (config.enabled && config.connectionRateThresholdEnabled ? 1L : 0L);
        signature = signature * 31L + showThreshold;
        signature = signature * 31L + hideThreshold;
        signature = signature * 31L + showSamples;
        signature = signature * 31L + hideSamples;
        return signature;
    }

    private static float resolveClockAndCarrierTextScale(ModuleConfig config) {
        int percent = config == null
                ? SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT
                : SettingsStore.normalizeScalePercent(config.clockAndCarrierTextSizePercent);
        return percent / 100f;
    }

    private static int scaleSize(int original, float scale) {
        if (original == 0) {
            return 0;
        }
        if (original < 0) {
            return original;
        }
        return Math.max(1, Math.round(original * scale));
    }

    private static int scaleInsetSize(int original, float scale) {
        if (original == 0) {
            return 0;
        }
        return Math.round(original * scale);
    }

    private static final class ConnectionRateViewState {
        int originalTextSize = -1;
        int originalMaxWidth = -1;
        int originalOrientation = Integer.MIN_VALUE;
        float originalRotation = Float.NaN;
        float originalUnitTextSize = Float.NaN;
        int originalUnitMinWidth = -1;
        int originalUnitMaxLines = Integer.MIN_VALUE;
        float originalUnitRotation = Float.NaN;
        int[] originalUnitPadding;
        long appliedTextScaleSignature;
        boolean hasAppliedTextScaleSignature;
        boolean featureEnabled;
        boolean thresholdVisible;
        boolean lastBaseShow;
        boolean hasBaseShow;
        double lastRate;
        long lastConfigSignature;
        int aboveCount;
        int belowCount;

        void reset() {
            thresholdVisible = false;
            resetCounters();
        }

        void resetCounters() {
            aboveCount = 0;
            belowCount = 0;
        }
    }
}
