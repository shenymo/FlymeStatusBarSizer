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
    private static final float STACK_ENTRY_INITIAL_SPREAD_RATIO = 0.8f;
    private static final float STACK_LEFT_EDGE_INSET_RATIO = -0.05f;
    private static final float STACK_RIGHT_VISIBLE_RATIO = 0.80f;
    private static final float STACK_SPREAD_POWER = 1.0f;
    private static final float STACK_LEFT_DAMPING_START_DEPTH = 0.75f;
    private static final float STACK_LEFT_DAMPING_POWER = 1.55f;
    private static final float STACK_LEFT_DAMPING_MIN_WEIGHT = 0.18f;
    private static final float STACK_RELEASE_INITIAL_SPREAD_RATIO = 0.35f;
    private static final float STACK_RELEASE_SETTLED_PROGRESS_SHIFT = 0.70f;
    private static final float STACK_LEFT_REST_INSET_RATIO = -0.15f;
    private static final float STACK_LEFT_EDGE_REVEAL_SCROLL_RATIO = 0.30f;
    private static final float STACK_LEFT_RELEASE_START_PROGRESS = -1.25f;
    private static final float STACK_LEFT_RELEASE_END_PROGRESS = -2.10f;
    private static final float STACK_MIN_SCALE = 0.85f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final float BLANK_TAP_HOME_EXIT_SCALE_DELTA = 0.04f;
    private static final float BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO = 0.18f;
    private static final float STACK_TITLE_FADE_END_CARD_ALPHA = 0.42f;
    private static final float STACK_CONTENT_BLUR_START_ALPHA = 0.85f;
    private static final int STACK_ENTRY_LIGHT_RADIUS = 3;
    private static final int STACK_STABLE_VISIBLE_RADIUS = -1;
    private static final int STACK_LAYOUT_RECOVERY_RADIUS_STEP = 4;
    private static final long STACK_LAYOUT_DUPLICATE_WINDOW_NS = 12_000_000L;

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
        hookRecentsViewDispatchScrollChanged(module, loader);
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
                    applyStackLayout(recentsView, false, "refreshTrackedViews", true);
                    LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15);
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
                            captureStockTaskStatesForStackApply(recentsView);
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
                    if (shouldSuppressBlankTapHomeExitStockTransformMethod(
                            methodName,
                            recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        applyStackLayout(recentsView, false, methodName + "_blankExitSuppress", true);
                        return null;
                    }
                    if (shouldSuppressStockPageScaleUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            captureStockTaskStatesForStackApply(recentsView);
                            applyStackLayout(recentsView, false, methodName + "_scaleSuppress", false);
                        }
                        return null;
                    }
                    if (shouldSuppressStockPageOffsetUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            captureStockTaskStatesForStackApply(recentsView);
                            applyStackLayout(recentsView, false, methodName + "_offsetSuppress", true);
                        }
                        return null;
                    }
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayoutOnSystemFrame(recentsView)) {
                        captureStockTaskStatesForStackApply(recentsView);
                        applyStackLayout(
                                recentsView,
                                false,
                                methodName + "_after",
                                !"updatePageScales".equals(methodName));
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
                        captureStockTaskStatesForStackApply(recentsView);
                        applyStackLayout(recentsView, false, "onScrollChanged", false);
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

    private static void hookRecentsViewDispatchScrollChanged(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("dispatchScrollChanged");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (applyDynamicStackLayoutIfNeeded(recentsView)) {
                        recentsView.invalidate();
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.dispatchScrollChanged",
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
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    Object arg0 = chain.getArg(0);
                    if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                            && arg0 instanceof Float
                            && (Float) arg0 < 1f) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        applyStackLayout(recentsView, false, "contentAlpha_blankExit", true);
                        recentsView.invalidate();
                        return null;
                    }
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        captureStockTaskStatesForStackApply(recentsView);
                        applyStackLayout(recentsView, false, "contentAlpha_after", true);
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

    private static void captureStockTaskStatesForStackApply(View recentsView) {
        if (!LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)) {
            LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
            return;
        }
        if (LauncherRecentsState.OVERVIEW_STATE_STACK_BASELINES_CAPTURED.containsKey(
                recentsView)) {
            return;
        }
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        LauncherRecentsState.OVERVIEW_STATE_STACK_BASELINES_CAPTURED.put(
                recentsView,
                Boolean.TRUE);
    }

    static void captureBlankTapHomeExitTaskStates(View recentsView) {
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.clear();
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        float anchorVisibleOffset = 0f;
        boolean hasVisibleAnchor = false;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            int rawOffset = LauncherRecentsCompat.invokeInt(
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
            float dismissTranslationX = LauncherRecentsCompat.readFloatField(
                    taskView,
                    "dismissTranslationX",
                    0f);
            float visibleOffset =
                    rawOffset
                            + dismissTranslationX
                            + LauncherRecentsCompat.readFloatField(
                            taskView,
                            "taskOffsetTranslationX",
                            0f)
                            + LauncherRecentsCompat.readFloatField(
                            taskView,
                            "horizontalOffsetTranslationX",
                            0f);
            float taskWidth = taskView.getWidth() > 0
                    ? taskView.getWidth()
                    : Math.max(1f, recentsView.getWidth());
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float taskScale = LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f);
            if (taskView.getVisibility() != View.VISIBLE
                    || taskView.getWidth() <= 0
                    || taskView.getHeight() <= 0
                    || !isTaskVisibleInViewport(
                    recentsView,
                    taskCenteredLeftPx,
                    taskWidth,
                    visibleOffset,
                    taskScale)) {
                continue;
            }
            LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.put(
                    taskView,
                    new LauncherRecentsState.BlankTapHomeExitTaskState(
                            rawOffset,
                            dismissTranslationX,
                            visibleOffset,
                            taskScale,
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "taskOffsetTranslationY",
                                    0f),
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "boxTranslationY",
                            LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(
                                            taskView)),
                            LauncherRecentsTaskVisuals.readAttachAlpha(taskView),
                            LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                            LauncherRecentsTaskVisuals.readActivityTitleAlpha(taskView),
                            LauncherRecentsTaskVisuals.readStackContentBlurProgress(taskView),
                            taskView.getTranslationZ()));
            if (!hasVisibleAnchor || visibleOffset > anchorVisibleOffset) {
                anchorVisibleOffset = visibleOffset;
                hasVisibleAnchor = true;
            }
        }
        if (hasVisibleAnchor) {
            for (LauncherRecentsState.BlankTapHomeExitTaskState state
                    : LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.values()) {
                state.centerVisibleOffset = state.startVisibleOffset - anchorVisibleOffset;
            }
            normalizeBlankTapHomeExitSiblingAlpha(anchorVisibleOffset);
        }
    }

    static void applyBlankTapHomeExitFrame(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        float clampedProgress = clamp(progress, 0f, 1f);
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            LauncherRecentsState.BlankTapHomeExitTaskState state =
                    LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView);
            if (taskView == null || state == null) {
                continue;
            }
            float taskWidth = taskView.getWidth() > 0
                    ? taskView.getWidth()
                    : Math.max(1f, recentsView.getWidth());
            float taskHeight = taskView.getHeight() > 0
                    ? taskView.getHeight()
                    : Math.max(1f, recentsView.getHeight());
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float desiredVisibleOffset = state.startVisibleOffset;
            float desiredScale = state.startScale;
            float desiredStableAlpha = state.startStableAlpha;
            if (state.startStableAlpha > 0f) {
                float pathProgress = smoothStep(clampedProgress);
                float controlVisibleOffset = state.centerVisibleOffset;
                float taskLeftPx = taskCenteredLeftPx + controlVisibleOffset;
                float exitTravelPx = Math.max(
                        taskLeftPx + taskWidth + FlymeStatusBarSizer.dp(
                                recentsView.getContext(),
                                64),
                        taskWidth * (1f + BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO));
                float exitVisibleOffset = controlVisibleOffset - exitTravelPx;
                desiredVisibleOffset = quadraticBezier(
                        state.startVisibleOffset,
                        controlVisibleOffset,
                        exitVisibleOffset,
                        pathProgress);
                desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * pathProgress);
                desiredStableAlpha *= resolveBlankTapExitAlpha(pathProgress);
            } else {
                desiredStableAlpha = 0f;
            }
            float taskOffsetX =
                    desiredVisibleOffset - state.startRawOffset - state.startDismissTranslationX;
            taskView.setPivotX(taskWidth * 0.5f);
            taskView.setPivotY(taskHeight * 0.5f);
            LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(taskView, 0f);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, taskOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(
                    taskView,
                    state.startTaskOffsetY);
            LauncherRecentsTaskVisuals.setBoxTranslationY(taskView, state.startBoxTranslationY);
            LauncherRecentsTaskVisuals.setNonGridScale(taskView, desiredScale);
            LauncherRecentsTaskVisuals.setAttachAlpha(taskView, state.startAttachAlpha);
            LauncherRecentsTaskVisuals.setStableAlpha(taskView, desiredStableAlpha);
            LauncherRecentsTaskVisuals.setActivityTitleAlpha(
                    taskView,
                    state.startActivityTitleAlpha * resolveBlankTapExitAlpha(clampedProgress));
            LauncherRecentsTaskVisuals.setStackContentBlurProgress(
                    taskView,
                    state.startStackContentBlurProgress);
        }
    }

    static void captureGestureStackReleaseTaskStates(
            View recentsView,
            int startScroll,
            int targetScroll) {
        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (taskViewCount <= 0) {
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
        float pageSpan = 0f;
        float[] rawOffsets = new float[taskViewCount];
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
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
        }
        if (referenceWidth <= 0f) {
            referenceWidth = Math.max(1, recentsView.getWidth());
        }
        if (pageSpan <= 1f) {
            pageSpan = referenceWidth + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referenceWidth);
        }
        float scrollDelta = startScroll - targetScroll;
        float targetLeftEdgeRevealProgress = resolveLeftEdgeRevealProgress(
                recentsView,
                targetScroll);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null
                    || LauncherRecentsCompat.isDesktopTask(taskView)
                    || (taskView != runningTaskView
                    && sharesRunningTaskIds(taskView, runningTaskView))) {
                continue;
            }
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float startRawOffset = rawOffsets[i];
            float targetRawOffset = startRawOffset + scrollDelta;
            float targetProgress = targetRawOffset / pageSpan;
            float targetVisibleOffset = resolveStackVisibleOffset(
                    recentsView,
                    resolveStackReleaseSettledProgress(targetProgress, 1f),
                    taskWidth,
                    taskCenteredLeftPx,
                    targetLeftEdgeRevealProgress);
            float startVisibleOffset =
                    startRawOffset
                            + LauncherRecentsCompat.readFloatField(
                            taskView,
                            "dismissTranslationX",
                            0f)
                            + LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView)
                            + LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView);
            LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.put(
                    taskView,
                    new LauncherRecentsState.GestureReleaseTaskState(
                            startVisibleOffset,
                            targetVisibleOffset));
        }
    }

    static void applyStackLayout(View recentsView, boolean captureStockState) {
        applyStackLayout(recentsView, captureStockState, "external");
    }

    static void applyStackLayout(View recentsView, boolean captureStockState, String source) {
        applyStackLayout(recentsView, captureStockState, source, true);
    }

    static void applyStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData) {
        applyStackLayout(
                recentsView,
                captureStockState,
                resolveStackLayoutRadius(recentsView),
                source,
                syncVisibleTaskData);
    }

    private static void applyStackLayout(
            View recentsView,
            boolean captureStockState,
            int stackLayoutRadius) {
        applyStackLayout(recentsView, captureStockState, stackLayoutRadius, "internal", true);
    }

    private static void applyStackLayout(
            View recentsView,
            boolean captureStockState,
            int stackLayoutRadius,
            String source,
            boolean syncVisibleTaskData) {
        if (shouldSkipDuplicateStackLayout(
                recentsView,
                stackLayoutRadius,
                source,
                syncVisibleTaskData)) {
            return;
        }
        long perfStartNs = LauncherRecentsPerf.start();
        try {
            applyStackLayoutMeasured(
                    recentsView,
                    captureStockState,
                    stackLayoutRadius,
                    syncVisibleTaskData);
        } finally {
            LauncherRecentsPerf.end("applyStackLayout:" + source, perfStartNs);
        }
    }

    private static boolean shouldSkipDuplicateStackLayout(
            View recentsView,
            int stackLayoutRadius,
            String source,
            boolean syncVisibleTaskData) {
        if (recentsView == null || !shouldCoalesceStackLayoutSource(source)) {
            return false;
        }
        long key = resolveStackLayoutApplyKey(recentsView, stackLayoutRadius);
        long nowNs = System.nanoTime();
        LauncherRecentsState.StackLayoutApplyState lastState =
                LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.get(recentsView);
        if (lastState != null
                && lastState.key == key
                && nowNs - lastState.timeNs <= STACK_LAYOUT_DUPLICATE_WINDOW_NS
                && (!syncVisibleTaskData || lastState.syncedVisibleTaskData)) {
            return true;
        }
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.put(
                recentsView,
                new LauncherRecentsState.StackLayoutApplyState(
                        key,
                        nowNs,
                        syncVisibleTaskData));
        return false;
    }

    private static boolean shouldCoalesceStackLayoutSource(String source) {
        return "applyDynamic".equals(source)
                || "onScrollChanged".equals(source)
                || "updatePageOffsetsForFlyme_offsetSuppress".equals(source)
                || "updatePageOffsetsForFlyme_after".equals(source)
                || "updatePageScales_scaleSuppress".equals(source);
    }

    private static long resolveStackLayoutApplyKey(View recentsView, int stackLayoutRadius) {
        long key = 17L;
        key = mixStackLayoutApplyKey(key, stackLayoutRadius);
        key = mixStackLayoutApplyKey(key, recentsView.getScrollX());
        key = mixStackLayoutApplyKey(key, recentsView.getScrollY());
        key = mixStackLayoutApplyKey(key, recentsView.getWidth());
        key = mixStackLayoutApplyKey(key, recentsView.getHeight());
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0));
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0));
        key = mixStackLayoutApplyKey(
                key,
                quantizeStackLayoutFloat(LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        0f)));
        key = mixStackLayoutApplyKey(
                key,
                quantizeStackLayoutFloat(
                        LauncherRecentsTransitionController.readBlankTapHomeExitProgress(
                                recentsView)));
        key = mixStackLayoutApplyKey(
                key,
                quantizeStackLayoutFloat(
                        LauncherRecentsTransitionController.readGestureRecentsStackReleaseProgress(
                                recentsView)));
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                        recentsView) ? 1 : 0);
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView) ? 1 : 0);
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsState.isGestureStackReleasedStable(recentsView) ? 1 : 0);
        return key;
    }

    private static long mixStackLayoutApplyKey(long key, int value) {
        return key * 31L + value;
    }

    private static int quantizeStackLayoutFloat(float value) {
        return Math.round(value * 1000f);
    }

    private static void applyStackLayoutMeasured(
            View recentsView,
            boolean captureStockState,
            int stackLayoutRadius,
            boolean syncVisibleTaskData) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.LaunchHandoffState launchState =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        if (launchState != null && launchState.frozen) {
            return;
        }
        if (LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(recentsView)
                && !LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze()) {
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
        int runningTaskChildIndex = -1;
        if (runningTaskView != null && recentsView instanceof ViewGroup) {
            runningTaskChildIndex = ((ViewGroup) recentsView).indexOfChild(runningTaskView);
        }
        int lightAnchorIndex = resolveStackLayoutAnchorIndex(
                recentsView,
                runningTaskChildIndex,
                taskViewCount,
                stackLayoutRadius);
        float referenceWidth = 0f;
        float referenceHeight = 0f;
        float pageSpan = 0f;
        float[] rawOffsets = new float[taskViewCount];

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (shouldHideStackLayoutTask(i, lightAnchorIndex, stackLayoutRadius)) {
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
        boolean overviewStateStackAnimationActive =
                LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                        recentsView);
        float gestureStackReleaseProgress =
                LauncherRecentsTransitionController.readGestureRecentsStackReleaseProgress(
                        recentsView);
        float overviewStateStackHandoffProgress = overviewStateStackAnimationActive
                ? smoothStep(resolveOverviewPeekToOverviewProgress(recentsView))
                : 1f;
        float stackReleaseProgress = gestureStackReleaseActive
                ? clamp(gestureStackReleaseProgress, 0f, 1f)
                : 1f;
        float stackSettledShiftProgress = LauncherRecentsState.isGestureStackReleasedStable(
                recentsView)
                ? 1f
                : 0f;
        if (gestureStackReleaseActive) {
            stackEntryProgress = 1f;
            stackVerticalProgress = 1f;
            stackSettledShiftProgress = smoothStep(stackReleaseProgress);
        }
        int overScrollShift = LauncherRecentsCompat.invokeInt(
                recentsView,
                "getOverScrollShift",
                0);
        boolean appEntrySessionActive =
                LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
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
            if (taskView != runningTaskView && sharesRunningTaskIds(taskView, runningTaskView)) {
                restoreTaskTransform(taskView);
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setActivityTitleAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setStackContentBlurProgress(taskView, 0f);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (shouldHideStackLayoutTask(i, lightAnchorIndex, stackLayoutRadius)) {
                hideLightStackTask(taskView);
                continue;
            }
            if (appEntrySessionActive && taskView == runningTaskView) {
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 1f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 1f);
                LauncherRecentsTaskVisuals.setActivityTitleAlpha(taskView, 1f);
                LauncherRecentsTaskVisuals.setStackContentBlurProgress(taskView, 0f);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (captureStockState) {
                LauncherRecentsTaskVisuals.captureStockTaskState(taskView);
            }
            LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState =
                    gestureStackReleaseActive
                            ? LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.get(taskView)
                            : null;
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
            float layoutRawOffset = rawOffset - overScrollShift;
            float physicalRawOffset = layoutRawOffset + dismissTranslationX;
            float effectiveRawOffset = physicalRawOffset + stackDismissLayoutOffset;
            float progress = effectiveRawOffset / pageSpan;
            float layoutProgress = resolveStackReleaseSettledProgress(
                    progress,
                    stackSettledShiftProgress);
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : referenceHeight;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float collapsedReferenceProgress = progress;
            if (appEntrySessionActive && runningTaskView != null) {
                collapsedReferenceProgress = resolveAppEntryCollapsedProgress(
                        taskView,
                        runningTaskView,
                        i,
                        runningTaskChildIndex,
                        taskViewCount);
            }
            float stackEntryLiftPx = Math.min(
                    taskHeight * STACK_ENTRY_LIFT_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 40));
            float finalVisibleOffset;
            float finalTaskOffsetY;

            finalVisibleOffset = resolveStackVisibleOffset(
                    recentsView,
                    layoutProgress,
                    taskWidth,
                    taskCenteredLeftPx);
            finalTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            float taskEntryProgress = resolveTaskStackEntryProgress(
                    stackEntryProgress,
                    collapsedReferenceProgress);
            float collapsedVisibleOffset = resolveStackVisibleOffset(
                    recentsView,
                    collapsedReferenceProgress,
                    taskWidth,
                    taskCenteredLeftPx) * STACK_ENTRY_INITIAL_SPREAD_RATIO;
            float collapsedTaskOffsetY = 0f;
            float desiredVisibleOffset = lerp(
                    collapsedVisibleOffset,
                    finalVisibleOffset,
                    taskEntryProgress);
            if (gestureStackReleaseActive) {
                desiredVisibleOffset *= lerp(
                        STACK_RELEASE_INITIAL_SPREAD_RATIO,
                        1.0f,
                        smoothStep(stackReleaseProgress));
            }
            if (gestureReleaseTaskState != null) {
                desiredVisibleOffset = lerp(
                        gestureReleaseTaskState.startVisibleOffset,
                        gestureReleaseTaskState.targetVisibleOffset,
                        stackReleaseProgress);
            }
            float desiredLayerProgress = resolveStackLayerProgress(
                    recentsView,
                    taskCenteredLeftPx,
                    taskWidth,
                    desiredVisibleOffset);
            float desiredScale = lerp(STACK_MIN_SCALE, 1.0f, desiredLayerProgress);
            float desiredTaskOffsetY = lerp(
                    collapsedTaskOffsetY,
                    finalTaskOffsetY,
                    Math.max(stackVerticalProgress, taskEntryProgress));
            float desiredBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                    Math.max(stackVerticalProgress, taskEntryProgress * 0.6f));
            if (gestureStackReleaseActive) {
                desiredBoxTranslationY = 0f;
            }
            float desiredTranslationZ = (maxTranslationZ + zStepPx) * desiredLayerProgress;
            float desiredStableAlpha = 1f;
            LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState =
                    LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView);
            if (blankTapExitState != null) {
                desiredVisibleOffset = blankTapExitState.startVisibleOffset;
                desiredScale = blankTapExitState.startScale;
                desiredTaskOffsetY = blankTapExitState.startTaskOffsetY;
                desiredBoxTranslationY = blankTapExitState.startBoxTranslationY;
                desiredStableAlpha = blankTapExitState.startStableAlpha;
                desiredTranslationZ = blankTapExitState.startTranslationZ;
            }
            float desiredAttachAlpha = blankTapExitState != null
                    ? blankTapExitState.startAttachAlpha
                    : 1f;
            float activityTitleAlpha = resolveStackTitleAlpha(desiredStableAlpha);
            boolean blankTapExitActive =
                    LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
            boolean blankTapExitTaskActive = blankTapExitActive
                    && blankTapExitState != null
                    && blankTapExitState.startStableAlpha > 0f;
            if (blankTapExitActive) {
                if (blankTapExitTaskActive) {
                    float startVisibleOffset = blankTapExitState.startVisibleOffset;
                    float centerVisibleOffset = blankTapExitState.centerVisibleOffset;
                    float pathProgress = smoothStep(blankTapExitProgress);
                    float controlVisibleOffset = centerVisibleOffset;
                    float taskLeftPx = taskCenteredLeftPx + controlVisibleOffset;
                    float exitTravelPx = Math.max(
                            taskLeftPx + taskWidth + FlymeStatusBarSizer.dp(
                                    recentsView.getContext(),
                                    64),
                            taskWidth * (1f + BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO));
                    float exitVisibleOffset = controlVisibleOffset - exitTravelPx;
                    desiredVisibleOffset = quadraticBezier(
                            startVisibleOffset,
                            controlVisibleOffset,
                            exitVisibleOffset,
                            pathProgress);
                    desiredScale *= 1.0f
                            - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * pathProgress);
                    desiredStableAlpha *= resolveBlankTapExitAlpha(pathProgress);
                    activityTitleAlpha = blankTapExitState.startActivityTitleAlpha
                            * resolveBlankTapExitAlpha(pathProgress);
                } else {
                    desiredStableAlpha = 0f;
                    activityTitleAlpha = 0f;
                }
            }
            float stackLeftClampAlpha = resolveStackLeftClampAlpha(
                    recentsView,
                    layoutProgress,
                    taskWidth,
                    taskCenteredLeftPx);
            if (!blankTapExitTaskActive) {
                desiredStableAlpha *= stackLeftClampAlpha;
            }
            float targetBlurProgress = resolveStackContentBlurProgress(
                    stackLeftClampAlpha,
                    taskEntryProgress);
            if (blankTapExitTaskActive) {
                targetBlurProgress = blankTapExitState.startStackContentBlurProgress;
            }
            float translationCompensationX =
                    desiredVisibleOffset - rawOffset - nativeDismissTranslationX;

            float appliedHorizontalOffsetX = 0f;
            float appliedTaskOffsetX = translationCompensationX;
            float appliedTaskOffsetY = desiredTaskOffsetY;
            float appliedBoxTranslationY = desiredBoxTranslationY;
            float appliedScale = desiredScale;
            float appliedAttachAlpha = desiredAttachAlpha;
            float appliedStableAlpha = desiredStableAlpha;
            float appliedBlurProgress = targetBlurProgress;
            float appliedFullscreenProgress =
                    LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView);
            float appliedTranslationZ = desiredTranslationZ;
            if (gestureStackReleaseActive) {
                if (gestureReleaseTaskState != null) {
                    appliedHorizontalOffsetX = 0f;
                } else {
                    appliedHorizontalOffsetX = lerp(
                            LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                            appliedHorizontalOffsetX,
                            stackReleaseProgress);
                    appliedTaskOffsetX = lerp(
                            LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                            appliedTaskOffsetX,
                            stackReleaseProgress);
                }
                appliedTaskOffsetY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                        appliedTaskOffsetY,
                        stackReleaseProgress);
                appliedBoxTranslationY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                        appliedBoxTranslationY,
                        stackReleaseProgress);
                appliedScale = lerp(
                        LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                        appliedScale,
                        stackReleaseProgress);
                appliedFullscreenProgress = lerp(
                        LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                        appliedFullscreenProgress,
                        stackReleaseProgress);
                appliedTranslationZ = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                        appliedTranslationZ,
                        stackReleaseProgress);
                appliedAttachAlpha = lerp(
                        LauncherRecentsTaskVisuals.readLastStockAttachAlpha(taskView),
                        appliedAttachAlpha,
                        stackReleaseProgress);
                appliedStableAlpha = lerp(
                        LauncherRecentsTaskVisuals.readLastStockStableAlpha(taskView),
                        appliedStableAlpha,
                        stackReleaseProgress);
                appliedBlurProgress = lerp(0f, appliedBlurProgress, stackReleaseProgress);
            }
            if (overviewStateStackAnimationActive) {
                appliedHorizontalOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                        appliedHorizontalOffsetX,
                        overviewStateStackHandoffProgress);
                appliedTaskOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                        appliedTaskOffsetX,
                        overviewStateStackHandoffProgress);
                appliedTaskOffsetY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                        appliedTaskOffsetY,
                        overviewStateStackHandoffProgress);
                appliedBoxTranslationY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                        appliedBoxTranslationY,
                        overviewStateStackHandoffProgress);
                appliedScale = lerp(
                        LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                        appliedScale,
                        overviewStateStackHandoffProgress);
                appliedFullscreenProgress = lerp(
                        LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                        appliedFullscreenProgress,
                        overviewStateStackHandoffProgress);
                appliedTranslationZ = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                        appliedTranslationZ,
                        overviewStateStackHandoffProgress);
                appliedAttachAlpha = lerp(
                        LauncherRecentsTaskVisuals.readLastStockAttachAlpha(taskView),
                        appliedAttachAlpha,
                        overviewStateStackHandoffProgress);
                appliedStableAlpha = lerp(
                        LauncherRecentsTaskVisuals.readLastStockStableAlpha(taskView),
                        appliedStableAlpha,
                        overviewStateStackHandoffProgress);
                appliedBlurProgress = lerp(0f, appliedBlurProgress, overviewStateStackHandoffProgress);
            }
            float appliedActivityTitleAlpha =
                    blankTapExitActive
                            ? activityTitleAlpha
                            : resolveStackTitleAlpha(appliedStableAlpha);
            LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                    taskView,
                    new LauncherRecentsTaskVisuals.StackTaskVisualState(
                            taskWidth * 0.5f,
                            taskHeight * 0.5f,
                            appliedHorizontalOffsetX,
                            appliedTaskOffsetX,
                            appliedTaskOffsetY,
                            appliedBoxTranslationY,
                            appliedScale,
                            appliedAttachAlpha,
                            appliedStableAlpha,
                            appliedActivityTitleAlpha,
                            appliedBlurProgress,
                            appliedFullscreenProgress,
                            appliedTranslationZ,
                            true));
        }
        if (launchState != null && launchState.handoffEnabled) {
            LauncherRecentsLaunchController.applyLaunchHandoffLayout(recentsView, launchState);
        }
        if (syncVisibleTaskData) {
            LauncherRecentsTouchController.ensureStackVisibleTaskDataIfNeeded(recentsView, 15);
        }
    }

    static void cancelStackLayoutRecovery(View recentsView) {
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
    }

    static void startStackLayoutRecovery(View recentsView) {
        if (recentsView == null || !shouldApplyDynamicStackLayout(recentsView)) {
            return;
        }
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.put(
                recentsView,
                STACK_ENTRY_LIGHT_RADIUS + STACK_LAYOUT_RECOVERY_RADIUS_STEP);
        recentsView.postOnAnimation(() -> runStackLayoutRecoveryFrame(recentsView));
    }

    private static void runStackLayoutRecoveryFrame(View recentsView) {
        Integer radius = LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.get(recentsView);
        if (recentsView == null
                || radius == null
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || !shouldApplyDynamicStackLayout(recentsView)) {
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (radius >= taskViewCount) {
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            applyStackLayout(recentsView, false, -1, "recoveryFinal", true);
            return;
        }
        applyStackLayout(recentsView, false, radius, "recoveryFrame", true);
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.put(
                recentsView,
                radius + STACK_LAYOUT_RECOVERY_RADIUS_STEP);
        recentsView.postOnAnimation(() -> runStackLayoutRecoveryFrame(recentsView));
    }

    private static int resolveStackLayoutRadius(View recentsView) {
        Integer recoveryRadius = LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.get(recentsView);
        if (recoveryRadius != null) {
            return recoveryRadius;
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)) {
            return STACK_ENTRY_LIGHT_RADIUS;
        }
        return STACK_STABLE_VISIBLE_RADIUS;
    }

    private static int resolveStackLayoutAnchorIndex(
            View recentsView,
            int runningTaskChildIndex,
            int taskViewCount,
            int stackLayoutRadius) {
        if (stackLayoutRadius == STACK_STABLE_VISIBLE_RADIUS) {
            int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
            return Math.max(0, Math.min(currentPage, Math.max(0, taskViewCount - 1)));
        }
        if (runningTaskChildIndex >= 0) {
            return runningTaskChildIndex;
        }
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        return Math.max(0, Math.min(currentPage, Math.max(0, taskViewCount - 1)));
    }

    private static int resolveNearestStackLayoutPage(View recentsView, int taskViewCount) {
        int scrollX = recentsView.getScrollX();
        int nearestPage = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < taskViewCount; i++) {
            int pageScroll = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getScrollForPage",
                    LauncherRecentsCompat.INT_ARG,
                    scrollX,
                    i);
            int distance = Math.abs(pageScroll - scrollX);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPage = i;
            }
        }
        return nearestPage;
    }

    private static boolean shouldHideStackLayoutTask(int index, int anchorIndex, int radius) {
        return radius >= 0 && Math.abs(index - anchorIndex) > radius;
    }

    private static void hideLightStackTask(View taskView) {
        if (isLightStackTaskHidden(taskView)) {
            return;
        }
        LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
        LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
        LauncherRecentsTaskVisuals.setActivityTitleAlpha(taskView, 0f);
        LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
    }

    private static boolean isLightStackTaskHidden(View taskView) {
        return isAppliedZero(LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.get(taskView))
                && isAppliedZero(LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.get(taskView))
                && isAppliedZero(
                LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.get(taskView))
                && isAppliedZero(LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.get(taskView));
    }

    private static boolean isAppliedZero(Float value) {
        return value != null && Math.abs(value) < 0.001f;
    }

    private static boolean shouldSuppressStockPageOffsetUpdate(
            String methodName,
            View recentsView) {
        return "updatePageOffsetsForFlyme".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsStateAnimationController.shouldKeepOverviewPeekStockLayout(
                recentsView)
                && !LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView);
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
                || LauncherRecentsTouchController.shouldSuppressStackDismissPageMutation(
                recentsView));
    }

    private static boolean shouldSuppressBlankTapHomeExitStockTransformMethod(
            String methodName,
            View recentsView) {
        return ("updatePageOffsetsForFlyme".equals(methodName)
                || "updatePageScales".equals(methodName))
                && shouldUseStackLayout(recentsView)
                && LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
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
        LauncherRecentsTaskVisuals.setActivityTitleAlpha(taskView, 1f);
        LauncherRecentsTaskVisuals.clearStackContentBlur(taskView);
        LauncherRecentsTaskVisuals.setFullscreenProgress(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView));
        LauncherRecentsTaskVisuals.restoreTaskShadow(taskView);
        LauncherRecentsTaskVisuals.setTranslationZ(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView));
        LauncherRecentsTaskVisuals.clearAppliedTaskState(taskView);
    }

    private static float resolveStackTitleAlpha(float taskAlpha) {
        return remapProgress(taskAlpha, STACK_TITLE_FADE_END_CARD_ALPHA, 1f);
    }

    private static float resolveStackContentBlurProgress(
            float stackLeftClampAlpha,
            float stackEntryProgress) {
        float alphaFadeProgress = remapProgress(
                1f - stackLeftClampAlpha,
                1f - STACK_CONTENT_BLUR_START_ALPHA,
                1f);
        return clamp(alphaFadeProgress * stackEntryProgress, 0f, 1f);
    }

    private static void normalizeBlankTapHomeExitSiblingAlpha(float anchorVisibleOffset) {
        LauncherRecentsState.BlankTapHomeExitTaskState siblingState = null;
        for (LauncherRecentsState.BlankTapHomeExitTaskState state
                : LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.values()) {
            if (state.startStableAlpha <= 0f || state.startVisibleOffset >= anchorVisibleOffset) {
                continue;
            }
            if (siblingState == null || state.startVisibleOffset > siblingState.startVisibleOffset) {
                siblingState = state;
            }
        }
        if (siblingState != null) {
            siblingState.startStableAlpha = 1f;
        }
    }

    private static float resolveBlankTapExitAlpha(float progress) {
        if (progress < 0.88f) {
            return 1f;
        }
        return lerp(1f, 0f, smoothStep(remapProgress(progress, 0.88f, 1f)));
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
                && taskViewCount > 1
                && !LauncherRecentsCompat.invokeBoolean(recentsView, "showAsGrid", false)
                && !LauncherRecentsCompat.invokeBoolean(
                        recentsView,
                        "isSplitSelectionActive",
                        false);
    }

    static boolean shouldApplyDynamicStackLayout(View recentsView) {
        return shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && (!LauncherRecentsStateAnimationController.shouldKeepOverviewPeekStockLayout(
                recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView))
                && (!LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(
                recentsView)
                || LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze())
                && (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView));
    }

    private static boolean shouldApplyDynamicStackLayoutOnSystemFrame(View recentsView) {
        if (!shouldApplyDynamicStackLayout(recentsView)) {
            return false;
        }
        if (!LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)) {
            return true;
        }
        return true;
    }

    static boolean applyDynamicStackLayoutIfNeeded(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (!shouldApplyDynamicStackLayoutOnSystemFrame(recentsView)) {
            return false;
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                && !LauncherRecentsState.OVERVIEW_STATE_STACK_BASELINES_CAPTURED.containsKey(
                recentsView)) {
            captureStockTaskStatesForStackApply(recentsView);
        }
        applyStackLayout(recentsView, false, "applyDynamic", false);
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
        if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
            return 1f;
        }
        return resolveStockStackEntryProgress(recentsView);
    }

    static float resolveStackVerticalProgress(View recentsView) {
        if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
            return 1f;
        }
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

    private static float resolveOverviewPeekToOverviewProgress(View recentsView) {
        Float startValue = LauncherRecentsState.OVERVIEW_STATE_STACK_START_ADJACENT_OFFSETS.get(
                recentsView);
        float startAdjacentOffset = startValue != null
                ? Math.max(0.001f, startValue)
                : 0.53f;
        float currentAdjacentOffset = clamp(
                LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        startAdjacentOffset),
                0f,
                1f);
        return clamp((startAdjacentOffset - currentAdjacentOffset) / startAdjacentOffset, 0f, 1f);
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

    private static float resolveStackVisibleOffset(
            View recentsView,
            float progress,
            float taskWidth,
            float taskCenteredLeftPx) {
        return resolveStackVisibleOffset(
                recentsView,
                progress,
                taskWidth,
                taskCenteredLeftPx,
                resolveLeftEdgeRevealProgress(recentsView));
    }

    private static float resolveStackVisibleOffset(
            View recentsView,
            float progress,
            float taskWidth,
            float taskCenteredLeftPx,
            float leftEdgeRevealProgress) {
        float leftBoundOffsetPx = resolveStackLeftBoundOffset(
                taskWidth,
                taskCenteredLeftPx,
                progress < 0f ? 1f : leftEdgeRevealProgress);
        float visibleOffset = resolveStackUnclampedVisibleOffset(
                recentsView,
                progress,
                taskWidth,
                taskCenteredLeftPx);
        return Math.max(leftBoundOffsetPx, visibleOffset);
    }

    private static float resolveStackUnclampedVisibleOffset(
            View recentsView,
            float progress,
            float taskWidth,
            float taskCenteredLeftPx) {
        float visibleOffset = resolveStackVirtualVisibleOffset(
                recentsView,
                progress,
                taskWidth,
                taskCenteredLeftPx);
        if (progress >= 0f) {
            return visibleOffset;
        }
        return -resolveLeftDampedVisibleOffset(
                visibleOffset,
                taskWidth,
                taskCenteredLeftPx,
                Math.abs(progress));
    }

    private static float resolveStackVirtualVisibleOffset(
            View recentsView,
            float progress,
            float taskWidth,
            float taskCenteredLeftPx) {
        float stackRightOffsetPx = Math.max(
                0f,
                recentsView.getWidth()
                        - (taskWidth * STACK_RIGHT_VISIBLE_RATIO)
                        - taskCenteredLeftPx);
        float stackDepth = Math.abs(progress);
        if (stackDepth <= 0.001f) {
            return 0f;
        }
        float stackSpreadProgress = (float) Math.pow(
                remapProgress(stackDepth, 0f, 1f),
                STACK_SPREAD_POWER);
        float visibleOffset = stackRightOffsetPx * stackSpreadProgress;
        if (stackDepth > 1f) {
            visibleOffset += (stackDepth - 1f) * taskWidth * STACK_RIGHT_VISIBLE_RATIO;
        }
        return visibleOffset;
    }

    private static float resolveLeftDampedVisibleOffset(
            float virtualOffset,
            float taskWidth,
            float taskCenteredLeftPx,
            float stackDepth) {
        float dampingStartPx = Math.max(
                1f,
                Math.max(taskCenteredLeftPx, taskWidth * STACK_LEFT_DAMPING_START_DEPTH));
        if (stackDepth <= STACK_LEFT_DAMPING_START_DEPTH) {
            return virtualOffset;
        }
        if (virtualOffset <= dampingStartPx) {
            return virtualOffset;
        }
        float stackRangePx = Math.max(1f, taskWidth * STACK_RIGHT_VISIBLE_RATIO);
        float overflowPx = virtualOffset - dampingStartPx;
        float dampingProgress = overflowPx / stackRangePx;
        float dampedOverflowPx = stackRangePx
                * STACK_LEFT_DAMPING_MIN_WEIGHT
                * (1.0f - (float) Math.pow(
                STACK_LEFT_DAMPING_MIN_WEIGHT / (dampingProgress + STACK_LEFT_DAMPING_MIN_WEIGHT),
                STACK_LEFT_DAMPING_POWER));
        return dampingStartPx + dampedOverflowPx;
    }

    private static float resolveStackLeftBoundOffset(
            float taskWidth,
            float taskCenteredLeftPx,
            float leftEdgeRevealProgress) {
        float stackLeftOffsetPx =
                -taskCenteredLeftPx + (taskWidth * STACK_LEFT_EDGE_INSET_RATIO);
        float stackLeftRestOffsetPx =
                -taskCenteredLeftPx + (taskWidth * STACK_LEFT_REST_INSET_RATIO);
        return lerp(
                stackLeftRestOffsetPx,
                stackLeftOffsetPx,
                leftEdgeRevealProgress);
    }

    private static float resolveStackLeftClampAlpha(
            View recentsView,
            float progress,
            float taskWidth,
            float taskCenteredLeftPx) {
        if (progress >= 0f) {
            return 1f;
        }
        float leftBoundOffsetPx = resolveStackLeftBoundOffset(
                taskWidth,
                taskCenteredLeftPx,
                resolveLeftEdgeRevealProgress(recentsView));
        float visibleOffset = -resolveStackVirtualVisibleOffset(
                recentsView,
                progress,
                taskWidth,
                taskCenteredLeftPx);
        float overflowPx = leftBoundOffsetPx - visibleOffset;
        if (overflowPx <= 0f) {
            return 1f;
        }
        float fadeStartOverflowPx = 0f;
        float fadeEndOverflowPx = Math.max(
                1f,
                (Math.abs(STACK_LEFT_RELEASE_END_PROGRESS)
                        - Math.abs(STACK_LEFT_RELEASE_START_PROGRESS))
                        * taskWidth
                        * STACK_RIGHT_VISIBLE_RATIO);
        return 1f - remapProgress(overflowPx, fadeStartOverflowPx, fadeEndOverflowPx);
    }

    private static float resolveStackLayerProgress(
            View recentsView,
            float taskCenteredLeftPx,
            float taskWidth,
            float visibleOffset) {
        float taskCenterX = taskCenteredLeftPx + visibleOffset + (taskWidth * 0.5f);
        return remapProgress(taskCenterX, 0f, recentsView.getWidth());
    }

    private static float resolveStackReleaseSettledProgress(
            float progress,
            float stackSettledShiftProgress) {
        if (stackSettledShiftProgress <= 0f) {
            return progress;
        }
        return progress + (STACK_RELEASE_SETTLED_PROGRESS_SHIFT * stackSettledShiftProgress);
    }

    private static float resolveLeftEdgeRevealProgress(View recentsView) {
        return resolveLeftEdgeRevealProgress(recentsView, recentsView.getScrollX());
    }

    private static float resolveLeftEdgeRevealProgress(View recentsView, int primaryScroll) {
        int minScroll = LauncherRecentsCompat.readIntField(
                recentsView,
                "mMinScroll",
                recentsView.getScrollX());
        float revealRange = Math.max(
                1f,
                recentsView.getWidth() * STACK_LEFT_EDGE_REVEAL_SCROLL_RATIO);
        return 1.0f - remapProgress(primaryScroll - minScroll, 0f, revealRange);
    }

    private static float resolveAppEntryCollapsedProgress(
            View taskView,
            View runningTaskView,
            int childIndex,
            int runningTaskChildIndex,
            int taskViewCount) {
        if (taskView == null || runningTaskView == null) {
            return 0f;
        }
        if (taskView == runningTaskView) {
            return 0f;
        }
        int visibleCount = Math.max(1, taskViewCount);
        int visibleIndex = childIndex;
        if (runningTaskChildIndex >= 0) {
            visibleCount = Math.max(1, taskViewCount - 1);
            if (childIndex > runningTaskChildIndex) {
                visibleIndex--;
            }
        }
        if (visibleCount <= 1) {
            return 0f;
        }
        float position = clamp(visibleIndex / (float) (visibleCount - 1), 0f, 1f);
        return lerp(-MAX_STACK_LAYERS, MAX_STACK_LAYERS, position);
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

    private static float quadraticBezier(float start, float control, float end, float progress) {
        float p = clamp(progress, 0f, 1f);
        float oneMinusP = 1f - p;
        return (oneMinusP * oneMinusP * start)
                + (2f * oneMinusP * p * control)
                + (p * p * end);
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
