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
    private static final float STACK_ENTRY_LIFT_RATIO = 0.05f;
    private static final float STACK_ENTRY_INITIAL_SPREAD_RATIO = 0.32f;
    private static final float STACK_LEFT_EDGE_INSET_RATIO = -0.10f;
    private static final float STACK_RIGHT_VISIBLE_RATIO = 0.80f;
    private static final float STACK_SPREAD_RIGHT_BIAS = 1.00f;
    private static final float STACK_LEFT_OFFSCREEN_STEP_RATIO = 0.18f;
    private static final float STACK_RIGHT_OFFSCREEN_STEP_RATIO = 0.85f;
    private static final float STACK_MIN_SCALE = 0.80f;
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
        hookRecentsViewMethod(module, loader, "updatePageScales");
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewMethod(module, loader, "applyAttachAlpha");
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
                if (shouldApplyDynamicStackLayout(recentsView)) {
                    applyStackLayout(recentsView, false);
                } else if (!shouldUseStackLayout(recentsView)) {
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
                            applyDynamicStackLayoutIfNeeded(recentsView);
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
                    if (shouldSuppressStockPageScaleUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                            applyStackLayout(recentsView, false);
                        }
                        return null;
                    }
                    if (shouldSuppressStockPageOffsetUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                            applyStackLayout(recentsView, false);
                        }
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

    static void resetTaskPageViewScales(View recentsView) {
        if (!(recentsView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recentsView;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) {
                continue;
            }
            child.setScaleX(1f);
            child.setScaleY(1f);
            if (child.getWidth() > 0 && child.getHeight() > 0) {
                child.setPivotX(child.getWidth() * 0.5f);
                child.setPivotY(child.getHeight() * 0.5f);
            }
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
        if (LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(recentsView)) {
            return;
        }
        if (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)) {
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
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        View runningTaskView = runningTaskObject instanceof View
                ? (View) runningTaskObject
                : null;
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
        boolean gestureStackReleaseActive =
                LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                        recentsView);
        float gestureStackReleaseProgress =
                LauncherRecentsTransitionController.readGestureRecentsStackReleaseProgress(
                        recentsView);
        if (gestureStackReleaseActive) {
            stackEntryProgress = 1f;
            stackVerticalProgress = 1f;
        }
        float maxTranslationZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 24);
        float zStepPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 8);
        boolean appEntrySessionActive =
                LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
        int runningTaskChildIndex = -1;
        if (runningTaskView != null && recentsView instanceof ViewGroup) {
            runningTaskChildIndex = ((ViewGroup) recentsView).indexOfChild(runningTaskView);
        }

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (LauncherRecentsCompat.isDesktopTask(taskView)) {
                restoreTaskTransform(taskView);
                continue;
            }
            if (taskView != runningTaskView && sharesRunningTaskIds(taskView, runningTaskView)) {
                restoreTaskTransform(taskView);
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (appEntrySessionActive && taskView == runningTaskView) {
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (captureStockState) {
                LauncherRecentsTaskVisuals.captureStockTaskState(taskView);
            }
            float rawOffset = rawOffsets[i];
            float nativeDismissTranslationX =
                    LauncherRecentsCompat.readFloatField(taskView, "dismissTranslationX", 0f);
            float dismissTranslationX = LauncherRecentsTouchController
                    .shouldSuppressNativeDismissTranslation(recentsView)
                    ? 0f
                    : nativeDismissTranslationX;
            // Keep the stock gap-closing animation, but remap its logical page position into
            // the compressed stack so sibling cards move into the dismissed slot instead of
            // adding a second full-page horizontal shift on top of it.
            float stackDismissLayoutOffset =
                    LauncherRecentsTouchController.readStackDismissLayoutOffset(taskView);
            float physicalRawOffset = rawOffset + dismissTranslationX;
            float effectiveRawOffset = physicalRawOffset + stackDismissLayoutOffset;
            float progress = effectiveRawOffset / pageSpan;
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : referenceHeight;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float collapsedReferenceProgress = progress;
            if (appEntrySessionActive && runningTaskView != null) {
                collapsedReferenceProgress = resolveAppEntryCollapsedProgress(
                        taskView,
                        runningTaskView,
                        i,
                        runningTaskChildIndex);
            }
            float clampedStackProgress = clamp(progress, -MAX_STACK_LAYERS, MAX_STACK_LAYERS);
            float stackRightProgress = remapProgress(
                    clampedStackProgress,
                    -MAX_STACK_LAYERS,
                    MAX_STACK_LAYERS);
            float stackEntryLiftPx = Math.min(
                    taskHeight * STACK_ENTRY_LIFT_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 40));
            float blankTapExitTravelPx = Math.max(
                    taskWidth * BLANK_TAP_HOME_EXIT_TRAVEL_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 220));
            float finalVisibleOffset;
            float finalScale;
            float finalTranslationZ;
            float finalTaskOffsetY;

            float stackSpreadBias = clamp(STACK_SPREAD_RIGHT_BIAS, 0f, 1f);
            float stackSpreadProgress = (float) Math.pow(
                    stackRightProgress,
                    1.0f + (stackSpreadBias * 2.5f));
            float stackLeftOffsetPx =
                    -taskCenteredLeftPx + (taskWidth * STACK_LEFT_EDGE_INSET_RATIO);
            float stackRightOffsetPx = Math.max(
                    stackLeftOffsetPx,
                    recentsView.getWidth()
                            - (taskWidth * STACK_RIGHT_VISIBLE_RATIO)
                            - taskCenteredLeftPx);
            finalVisibleOffset = lerp(
                    stackLeftOffsetPx,
                    stackRightOffsetPx,
                    stackSpreadProgress);
            if (progress < -MAX_STACK_LAYERS) {
                finalVisibleOffset = stackLeftOffsetPx
                        + ((progress + MAX_STACK_LAYERS)
                        * taskWidth
                        * STACK_LEFT_OFFSCREEN_STEP_RATIO);
            } else if (progress > MAX_STACK_LAYERS) {
                finalVisibleOffset = stackRightOffsetPx
                        + ((progress - MAX_STACK_LAYERS)
                        * taskWidth
                        * STACK_RIGHT_OFFSCREEN_STEP_RATIO);
            }
            finalScale = lerp(STACK_MIN_SCALE, 1.0f, stackRightProgress);
            finalTranslationZ = lerp(0f, maxTranslationZ + zStepPx, stackRightProgress);
            finalTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            float taskEntryProgress = resolveTaskStackEntryProgress(
                    stackEntryProgress,
                    collapsedReferenceProgress);
            float collapsedRightProgress = remapProgress(
                    clamp(collapsedReferenceProgress, -MAX_STACK_LAYERS, MAX_STACK_LAYERS),
                    -MAX_STACK_LAYERS,
                    MAX_STACK_LAYERS);
            float collapsedVisibleOffset = finalVisibleOffset * STACK_ENTRY_INITIAL_SPREAD_RATIO;
            float collapsedScale = lerp(STACK_MIN_SCALE, 1.0f, collapsedRightProgress);
            float collapsedTranslationZ =
                    lerp(0f, maxTranslationZ + zStepPx, collapsedRightProgress);
            float collapsedTaskOffsetY = 0f;
            float transformEntryProgress = Math.max(
                    taskEntryProgress,
                    stackVerticalProgress * 0.55f);
            float desiredVisibleOffset = lerp(
                    collapsedVisibleOffset,
                    finalVisibleOffset,
                    taskEntryProgress);
            float desiredScale = lerp(
                    collapsedScale,
                    finalScale,
                    transformEntryProgress);
            float desiredTaskOffsetY = lerp(
                    collapsedTaskOffsetY,
                    finalTaskOffsetY,
                    Math.max(stackVerticalProgress, taskEntryProgress));
            float desiredBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                    Math.max(stackVerticalProgress, taskEntryProgress * 0.6f));
            float desiredTranslationZ = lerp(
                    collapsedTranslationZ,
                    finalTranslationZ,
                    transformEntryProgress);
            float desiredStableAlpha = 1f;
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
            float translationCompensationX =
                    desiredVisibleOffset - rawOffset - nativeDismissTranslationX;

            taskView.setPivotX(taskWidth * 0.5f);
            taskView.setPivotY(taskHeight * 0.5f);
            float appliedHorizontalOffsetX = 0f;
            float appliedTaskOffsetX = translationCompensationX;
            float appliedTaskOffsetY = desiredTaskOffsetY;
            float appliedBoxTranslationY = desiredBoxTranslationY;
            float appliedScale = desiredScale;
            float appliedAttachAlpha = 1f;
            float appliedStableAlpha = desiredStableAlpha;
            float appliedFullscreenProgress =
                    LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView);
            float appliedTranslationZ = desiredTranslationZ;
            if (gestureStackReleaseActive) {
                float releaseProgress = clamp(gestureStackReleaseProgress, 0f, 1f);
                appliedHorizontalOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                        appliedHorizontalOffsetX,
                        releaseProgress);
                appliedTaskOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                        appliedTaskOffsetX,
                        releaseProgress);
                appliedTaskOffsetY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                        appliedTaskOffsetY,
                        releaseProgress);
                appliedBoxTranslationY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                        appliedBoxTranslationY,
                        releaseProgress);
                appliedScale = lerp(
                        LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                        appliedScale,
                        releaseProgress);
                appliedFullscreenProgress = lerp(
                        LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                        appliedFullscreenProgress,
                        releaseProgress);
                appliedTranslationZ = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                        appliedTranslationZ,
                        releaseProgress);
            }
            LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(
                    taskView,
                    appliedHorizontalOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, appliedTaskOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, appliedTaskOffsetY);
            LauncherRecentsTaskVisuals.setBoxTranslationY(taskView, appliedBoxTranslationY);
            LauncherRecentsTaskVisuals.setNonGridScale(taskView, appliedScale);
            LauncherRecentsTaskVisuals.setAttachAlpha(
                    taskView,
                    appliedAttachAlpha);
            LauncherRecentsTaskVisuals.setStableAlpha(taskView, appliedStableAlpha);
            LauncherRecentsTaskVisuals.setFullscreenProgress(
                    taskView,
                    appliedFullscreenProgress);
            LauncherRecentsTaskVisuals.setTranslationZ(taskView, appliedTranslationZ);
        }
        if (launchState != null && launchState.handoffEnabled) {
            LauncherRecentsLaunchController.applyLaunchHandoffLayout(recentsView, launchState);
        }
        LauncherRecentsTouchController.ensureStackVisibleTaskData(recentsView, 15);
    }

    private static boolean shouldSuppressStockPageOffsetUpdate(
            String methodName,
            View recentsView) {
        return "updatePageOffsetsForFlyme".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView);
    }

    private static boolean shouldSuppressStockPageScaleUpdate(
            String methodName,
            View recentsView) {
        return "updatePageScales".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && (LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView));
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
        LauncherRecentsTaskVisuals.setAttachAlpha(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockAttachAlpha(taskView));
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

    static boolean shouldDeferStackLayoutForAppToRecents(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        return config != null
                && config.enabled
                && config.launcherIosStackRecentsEnabled
                && !LauncherRecentsCompat.invokeBoolean(recentsView, "showAsGrid", false)
                && !LauncherRecentsCompat.invokeBoolean(
                        recentsView,
                        "isSplitSelectionActive",
                        false);
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
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && !LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(
                recentsView)
                && (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView));
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
        return resolveStockStackEntryProgress(recentsView);
    }

    static float resolveStackVerticalProgress(View recentsView) {
        return resolveStockStackVerticalProgress(recentsView);
    }

    static float resolveStockStackEntryProgress(View recentsView) {
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

    static float resolveStockStackVerticalProgress(View recentsView) {
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

    private static float resolveAppEntryCollapsedProgress(
            View taskView,
            View runningTaskView,
            int childIndex,
            int runningTaskChildIndex) {
        if (taskView == null || runningTaskView == null) {
            return 0f;
        }
        if (taskView == runningTaskView) {
            return 0f;
        }
        int referenceIndex = runningTaskChildIndex >= 0 ? runningTaskChildIndex : childIndex;
        int orderDistance = Math.abs(childIndex - referenceIndex);
        return -Math.max(1f, (float) orderDistance);
    }

    private static boolean sharesRunningTaskIds(View taskView, View runningTaskView) {
        if (taskView == null || runningTaskView == null) {
            return false;
        }
        Object taskIdsObject = LauncherRecentsCompat.invokeCompat(
                runningTaskView,
                "getTaskIds",
                LauncherRecentsCompat.NO_ARGS);
        if (!(taskIdsObject instanceof int[])) {
            return false;
        }
        int[] runningTaskIds = (int[]) taskIdsObject;
        for (int taskId : runningTaskIds) {
            Object contains = LauncherRecentsCompat.invokeCompat(
                    taskView,
                    "containsTaskId",
                    LauncherRecentsCompat.INT_ARG,
                    taskId);
            if (contains instanceof Boolean && (Boolean) contains) {
                return true;
            }
        }
        return false;
    }

    private static float resolveTaskStackEntryProgress(
            float stackEntryProgress,
            float pageProgress) {
        if (Math.abs(pageProgress) < 0.5f) {
            return smoothStep(stackEntryProgress);
        }
        float layerDepth = clamp(Math.abs(pageProgress), 0f, MAX_STACK_LAYERS);
        float revealStart = Math.min(0.42f, layerDepth * 0.10f);
        float revealEnd = 1.0f - Math.min(0.18f, layerDepth * 0.04f);
        return smoothStep(remapProgress(stackEntryProgress, revealStart, revealEnd));
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
