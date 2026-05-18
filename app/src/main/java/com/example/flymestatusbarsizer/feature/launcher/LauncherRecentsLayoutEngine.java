package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class LauncherRecentsLayoutEngine {
    private static final float STACK_DEPTH_CURVE_POWER = 0.82f;
    private static final float STACK_FRONT_VISIBLE_RATIO = 0.50f;
    private static final float STACK_FRONT_SHIFT_START_PROGRESS = 0.12f;
    private static final float STACK_FRONT_REVEAL_CURVE_POWER = 0.72f;
    private static final float STACK_ENTRY_LIFT_RATIO = 0.05f;
    private static final float STACK_BACK_SPREAD_RATIO = 0.14f;
    private static final float STACK_MIN_OVERLAP_RATIO = 0.20f;
    private static final float STACK_SCALE_STEP = 0.065f;
    private static final float STACK_MIN_SCALE = 0.80f;
    private static final float STACK_LEFT_INSET_RATIO = 0.05f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final float BLANK_TAP_HOME_EXIT_SCALE_DELTA = 0.07f;
    private static final float BLANK_TAP_HOME_EXIT_TRAVEL_RATIO = 0.90f;

    private LauncherRecentsLayoutEngine() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewConstructors(module, loader);
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewOnScrollChanged(module, loader);
        hookRecentsViewContentAlpha(module, loader);
    }

    static void refreshTrackedViews() {
        Runnable refreshRunnable = () -> {
            ArrayList<View> views = LauncherRecentsState.snapshotTrackedRecentsViews();
            for (View recentsView : views) {
                if (recentsView == null) {
                    continue;
                }
                if (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                    continue;
                }
                prepareRecentsView(recentsView);
                if (shouldUseStackLayout(recentsView)) {
                    applyStackLayout(recentsView, false);
                } else {
                    reapplyOriginalTransforms(recentsView);
                }
            }
        };
        Handler handler = LauncherRecentsState.ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(refreshRunnable);
        } else {
            refreshRunnable.run();
        }
    }

    private static void hookRecentsViewConstructors(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    LauncherRecentsCompat.LAUNCHER_RECENTS_VIEW_CLASS,
                    false,
                    loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof View) {
                        View recentsView = (View) thisObject;
                        LauncherRecentsState.trackRecentsView(recentsView);
                        recentsView.post(() -> {
                            prepareRecentsView(recentsView);
                            LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                            applyStackLayout(recentsView, false);
                        });
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook LauncherRecentsView constructors",
                    t);
        }
    }

    private static void hookRecentsViewMethod(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            String methodName) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (LauncherRecentsLaunchController.shouldSuppressStockTaskLaunchTransformMethod(
                            recentsView,
                            methodName)) {
                        return null;
                    }
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView." + methodName,
                    t);
        }
    }

    private static void hookRecentsViewOnScrollChanged(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onScrollChanged",
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onScrollChanged",
                    t);
        }
    }

    private static void hookRecentsViewContentAlpha(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setContentAlpha", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setContentAlpha",
                    t);
        }
    }

    static void prepareRecentsView(View recentsView) {
        if (!(recentsView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recentsView;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        ViewParent parent = group.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).setClipChildren(false);
        }
    }

    static void applyStackLayout(View recentsView, boolean captureStockState) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.LaunchHandoffState launchState =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        if (launchState != null && launchState.frozen) {
            return;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            restoreTaskTransforms(recentsView, taskViewCount);
            return;
        }

        float pageSpacing = LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0);
        float referenceWidth = 0f;
        float referenceHeight = 0f;
        float pageSpan = 0f;
        float[] rawOffsets = new float[taskViewCount];

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            LauncherRecentsTaskVisuals.rememberOriginalTaskState(taskView);
            rawOffsets[i] = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getUnclampedScrollOffset",
                    LauncherRecentsCompat.INT_ARG,
                    LauncherRecentsCompat.invokeInt(
                            recentsView,
                            "getScrollOffset",
                            LauncherRecentsCompat.INT_ARG,
                            0,
                            i),
                    i);
            if (taskView.getWidth() > 0) {
                referenceWidth = Math.max(referenceWidth, taskView.getWidth());
                pageSpan = Math.max(pageSpan, taskView.getWidth() + pageSpacing);
            }
            if (taskView.getHeight() > 0) {
                referenceHeight = Math.max(referenceHeight, taskView.getHeight());
            }
        }

        if (referenceWidth <= 0f) {
            referenceWidth = Math.max(1, recentsView.getWidth());
        }
        if (referenceHeight <= 0f) {
            referenceHeight = Math.max(1, recentsView.getHeight());
        }
        if (pageSpan <= 1f) {
            pageSpan = referenceWidth + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referenceWidth);
        }

        float blankTapExitProgress =
                LauncherRecentsTransitionController.readBlankTapHomeExitProgress(recentsView);
        float stackEntryProgress = resolveStackEntryProgress(recentsView);
        float stackVerticalProgress = resolveStackVerticalProgress(recentsView);
        boolean isTouchHandling =
                LauncherRecentsCompat.invokeBoolean(recentsView, "isHandlingTouch", false);
        float maxTranslationZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 24);
        float zStepPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 8);

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (LauncherRecentsCompat.isDesktopTask(taskView)) {
                restoreTaskTransform(taskView);
                continue;
            }
            if (captureStockState) {
                LauncherRecentsTaskVisuals.captureStockTaskState(taskView);
            }
            float rawOffset = rawOffsets[i];
            float dismissTranslationX =
                    LauncherRecentsCompat.readFloatField(taskView, "dismissTranslationX", 0f);
            // Keep the stock gap-closing animation, but remap its logical page position into
            // the compressed stack so sibling cards move into the dismissed slot instead of
            // adding a second full-page horizontal shift on top of it.
            float effectiveRawOffset = rawOffset + dismissTranslationX;
            float progress = effectiveRawOffset / pageSpan;
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : referenceHeight;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float stackBaseOffsetPx =
                    -taskCenteredLeftPx + (taskWidth * STACK_LEFT_INSET_RATIO);
            float stackFrontLeftPx =
                    recentsView.getWidth() - (taskWidth * STACK_FRONT_VISIBLE_RATIO);
            float screenFrontOffsetPx = stackFrontLeftPx - taskCenteredLeftPx;
            float maxFrontOffsetPx = stackBaseOffsetPx
                    + (taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO));
            float stackFrontOffsetPx = Math.min(screenFrontOffsetPx, maxFrontOffsetPx);
            float stackBackSpreadPx = Math.min(
                    taskWidth * STACK_BACK_SPREAD_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 96));
            float stackEntryLiftPx = Math.min(
                    taskHeight * STACK_ENTRY_LIFT_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 40));
            float blankTapExitTravelPx = Math.max(
                    taskWidth * BLANK_TAP_HOME_EXIT_TRAVEL_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 220));
            float stockVisibleOffset = effectiveRawOffset
                    + LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView)
                    + LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView);
            boolean shouldHoldLeadCardCentered = isTouchHandling
                    && stackVerticalProgress < 0.999f
                    && progress >= 0f
                    && progress < 1.0f;
            float horizontalEntryProgress = shouldHoldLeadCardCentered
                    ? 0f
                    : stackEntryProgress;
            float frontShiftProgress = remapProgress(
                    horizontalEntryProgress,
                    STACK_FRONT_SHIFT_START_PROGRESS,
                    1.0f);
            float frontBaseOffset = lerp(stackBaseOffsetPx, stackFrontOffsetPx, frontShiftProgress);
            float desiredVisibleOffset;
            float desiredScale;
            float desiredTranslationZ;
            float desiredTaskOffsetY;
            float desiredBoxTranslationY;

            if (progress >= 0f) {
                float positiveProgress = Math.max(0f, progress);
                int rightLayer = (int) Math.floor(positiveProgress);
                float localProgress = positiveProgress - rightLayer;
                float handoffProgress = smoothStep((float) Math.pow(
                        localProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                float maxLeadSeparationPx = taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO);
                desiredVisibleOffset = frontBaseOffset
                        + (rightLayer * maxLeadSeparationPx)
                        + (handoffProgress * maxLeadSeparationPx);
                desiredScale = 1.0f;
                desiredTranslationZ = maxTranslationZ
                        + zStepPx
                        + (Math.min(progress, MAX_STACK_LAYERS) / MAX_STACK_LAYERS * maxTranslationZ);
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            } else {
                float stackDepth = clamp(-progress, 0f, MAX_STACK_LAYERS);
                float revealCurve = (float) Math.pow(
                        clamp(stackDepth / MAX_STACK_LAYERS, 0f, 1f),
                        STACK_DEPTH_CURVE_POWER);
                float visualStackDepth = revealCurve * MAX_STACK_LAYERS;
                float backgroundSpreadProgress = clamp(
                        (stackDepth - 1.0f) / Math.max(1.0f, MAX_STACK_LAYERS - 1.0f),
                        0f,
                        1f);
                float backgroundSpreadCurve = (float) Math.pow(
                        backgroundSpreadProgress,
                        STACK_DEPTH_CURVE_POWER);
                float backgroundStackOffset = stackBaseOffsetPx
                        - (stackBackSpreadPx * backgroundSpreadCurve);
                float incomingProgress = remapProgress(progress, -1.0f, 0.0f);
                float frontRevealProgress = smoothStep((float) Math.pow(
                        incomingProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                desiredVisibleOffset = lerp(
                        backgroundStackOffset,
                        frontBaseOffset,
                        frontRevealProgress);
                desiredScale = Math.max(
                        STACK_MIN_SCALE,
                        1.0f - (STACK_SCALE_STEP * visualStackDepth));
                desiredTranslationZ =
                        Math.max(0f, maxTranslationZ - (revealCurve * maxTranslationZ));
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            }
            desiredVisibleOffset = lerp(
                    stockVisibleOffset,
                    desiredVisibleOffset,
                    horizontalEntryProgress);
            desiredScale = lerp(
                    LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                    desiredScale,
                    stackVerticalProgress);
            desiredTaskOffsetY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                    desiredTaskOffsetY,
                    stackVerticalProgress);
            desiredBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                    stackVerticalProgress);
            desiredTranslationZ = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                    desiredTranslationZ,
                    stackVerticalProgress);
            float desiredStableAlpha =
                    LauncherRecentsTaskVisuals.readLastStockStableAlpha(taskView);
            if (blankTapExitProgress > 0f) {
                if (isTaskVisibleInViewport(
                        recentsView,
                        taskCenteredLeftPx,
                        taskWidth,
                        desiredVisibleOffset,
                        desiredScale)) {
                    desiredVisibleOffset -= blankTapExitTravelPx * blankTapExitProgress;
                    desiredScale *= 1.0f
                            - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * blankTapExitProgress);
                    desiredStableAlpha *= 1.0f - blankTapExitProgress;
                } else {
                    desiredStableAlpha = 0f;
                }
            }
            float translationCompensationX = desiredVisibleOffset - effectiveRawOffset;

            taskView.setPivotX(taskWidth * 0.5f);
            taskView.setPivotY(taskHeight * 0.5f);
            LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(taskView, 0f);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, translationCompensationX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, desiredTaskOffsetY);
            LauncherRecentsTaskVisuals.setBoxTranslationY(taskView, desiredBoxTranslationY);
            LauncherRecentsTaskVisuals.setNonGridScale(taskView, desiredScale);
            LauncherRecentsTaskVisuals.setStableAlpha(taskView, desiredStableAlpha);
            LauncherRecentsTaskVisuals.setFullscreenProgress(
                    taskView,
                    LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView));
            LauncherRecentsTaskVisuals.setTranslationZ(taskView, desiredTranslationZ);
        }
        if (launchState != null && launchState.handoffEnabled) {
            LauncherRecentsLaunchController.applyLaunchHandoffLayout(recentsView, launchState);
        }
    }

    static void restoreTaskTransforms(View recentsView, int taskViewCount) {
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            restoreTaskTransform(taskView);
        }
    }

    static void restoreTaskTransform(View taskView) {
        LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(taskView, 0f);
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, 0f);
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, 0f);
        LauncherRecentsTaskVisuals.setBoxTranslationY(
                taskView,
                LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView));
        LauncherRecentsTaskVisuals.setNonGridScale(
                taskView,
                LauncherRecentsTaskVisuals.readOriginalNonGridScale(taskView));
        LauncherRecentsTaskVisuals.setStableAlpha(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockStableAlpha(taskView));
        LauncherRecentsTaskVisuals.setFullscreenProgress(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView));
        LauncherRecentsTaskVisuals.setTranslationZ(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView));
        LauncherRecentsTaskVisuals.clearAppliedTaskState(taskView);
    }

    static boolean shouldUseStackLayout(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        return shouldUseStackLayout(config, recentsView, taskViewCount);
    }

    static boolean shouldUseStackLayout(
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config,
            View recentsView,
            int taskViewCount) {
        return config != null
                && config.enabled
                && config.launcherIosStackRecentsEnabled
                && taskViewCount > 0
                && !LauncherRecentsCompat.invokeBoolean(recentsView, "showAsGrid", false)
                && !LauncherRecentsCompat.invokeBoolean(
                        recentsView,
                        "isSplitSelectionActive",
                        false);
    }

    static boolean shouldApplyDynamicStackLayout(View recentsView) {
        return shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView);
    }

    static boolean applyDynamicStackLayoutIfNeeded(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (!shouldApplyDynamicStackLayout(recentsView)) {
            return false;
        }
        applyStackLayout(recentsView, false);
        return true;
    }

    static void reapplyOriginalTransforms(View recentsView) {
        LauncherRecentsTransitionController.cancelBlankTapHomeExitAnimation(recentsView, true);
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        restoreTaskTransforms(recentsView, taskViewCount);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "updatePageScales",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "updatePageOffsetsForFlyme",
                LauncherRecentsCompat.NO_ARGS);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    static float resolveStackEntryProgress(View recentsView) {
        float adjacentOffset = clamp(
                LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        0f),
                0f,
                1f);
        float fullscreenProgress = clamp(
                LauncherRecentsCompat.readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                LauncherRecentsCompat.readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        float collapsedProgress = Math.max(adjacentOffset, fullscreenProgress);
        return clamp((1.0f - collapsedProgress) * contentAlpha, 0f, 1f);
    }

    static float resolveStackVerticalProgress(View recentsView) {
        float fullscreenProgress = clamp(
                LauncherRecentsCompat.readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                LauncherRecentsCompat.readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        return clamp((1.0f - fullscreenProgress) * contentAlpha, 0f, 1f);
    }

    static boolean isTaskVisibleInViewport(
            View recentsView,
            float centeredLeftPx,
            float taskWidth,
            float desiredVisibleOffset,
            float desiredScale) {
        float clampedScale = Math.max(0.5f, desiredScale);
        float translatedLeftPx = centeredLeftPx + desiredVisibleOffset;
        float actualLeftPx = translatedLeftPx + ((1.0f - clampedScale) * taskWidth * 0.5f);
        float actualRightPx = actualLeftPx + (taskWidth * clampedScale);
        return actualRightPx > 0f && actualLeftPx < recentsView.getWidth();
    }

    static float lerp(float start, float end, float progress) {
        return start + ((end - start) * clamp(progress, 0f, 1f));
    }

    static float remapProgress(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1f : 0f;
        }
        return clamp((value - start) / (end - start), 0f, 1f);
    }

    static float smoothStep(float value) {
        float clamped = clamp(value, 0f, 1f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
