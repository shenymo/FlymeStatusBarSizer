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
    private static final float STACK_LEFT_MOVE_RATIO = 0.72f;
    private static final float STACK_RIGHT_BASE_SPEEDUP_RATIO = 0.10f;
    private static final float STACK_RIGHT_SPEEDUP_RATIO = 0.32f;
    private static final float STACK_RELEASE_INITIAL_SPREAD_RATIO = 0.35f;
    private static final float STACK_RELEASE_SETTLED_PROGRESS_SHIFT = 0.70f;
    private static final float STACK_LEFT_REST_INSET_RATIO = -0.15f;
    private static final float STACK_LEFT_EDGE_REVEAL_SCROLL_RATIO = 0.30f;
    private static final float STACK_LEFT_RELEASE_START_PROGRESS = -1.25f;
    private static final float STACK_LEFT_RELEASE_END_PROGRESS = -2.10f;
    private static final float STACK_MIN_SCALE = 0.92f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final float BLANK_TAP_HOME_EXIT_SCALE_DELTA = 0.04f;
    private static final float BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO = 0.18f;
    private static final float STACK_CONTENT_BLUR_START_ALPHA = 0.85f;
    private static final int STACK_ENTRY_LIGHT_RADIUS = 3;
    private static final int STACK_STABLE_VISIBLE_RADIUS = 2;
    private static final int STACK_LAYOUT_RECOVERY_RADIUS_STEP = 4;
    private static final int STACK_SLOW_LOG_SCROLL_BUCKET_DIVISOR = 2;
    private static final long STACK_LAYOUT_DUPLICATE_WINDOW_NS = 16_000_000L; // 【方案三 3-A】扩大到 16ms，覆盖 120Hz 设备一帧内的多次重复触发

    private LauncherRecentsLayoutEngine() {
    }

    private static final class StackLayoutContext {
        final View recentsView;
        final int taskViewCount;
        final View runningTaskView;
        final int runningTaskChildIndex;
        final float referenceWidth;
        final float referenceHeight;
        final float pageSpan;
        final float[] rawOffsets;
        final float blankTapExitProgress;
        final float stackEntryProgress;
        final float stackVerticalProgress;
        final boolean gestureStackReleaseActive;
        final boolean overviewStateStackAnimationActive;
        final float overviewStateStackHandoffProgress;
        final float stackReleaseProgress;
        final float stackSettledShiftProgress;
        final int overScrollShift;
        final boolean appEntrySessionActive;
        final float maxTranslationZ;
        final float zStepPx;
        final boolean blankTapExitActive;
        final boolean stackContentBlurEnabled;

        StackLayoutContext(
                View recentsView,
                int taskViewCount,
                View runningTaskView,
                int runningTaskChildIndex,
                float referenceWidth,
                float referenceHeight,
                float pageSpan,
                float[] rawOffsets,
                float blankTapExitProgress,
                float stackEntryProgress,
                float stackVerticalProgress,
                boolean gestureStackReleaseActive,
                boolean overviewStateStackAnimationActive,
                float overviewStateStackHandoffProgress,
                float stackReleaseProgress,
                float stackSettledShiftProgress,
                int overScrollShift,
                boolean appEntrySessionActive,
                float maxTranslationZ,
                float zStepPx,
                boolean blankTapExitActive,
                boolean stackContentBlurEnabled) {
            this.recentsView = recentsView;
            this.taskViewCount = taskViewCount;
            this.runningTaskView = runningTaskView;
            this.runningTaskChildIndex = runningTaskChildIndex;
            this.referenceWidth = referenceWidth;
            this.referenceHeight = referenceHeight;
            this.pageSpan = pageSpan;
            this.rawOffsets = rawOffsets;
            this.blankTapExitProgress = blankTapExitProgress;
            this.stackEntryProgress = stackEntryProgress;
            this.stackVerticalProgress = stackVerticalProgress;
            this.gestureStackReleaseActive = gestureStackReleaseActive;
            this.overviewStateStackAnimationActive = overviewStateStackAnimationActive;
            this.overviewStateStackHandoffProgress = overviewStateStackHandoffProgress;
            this.stackReleaseProgress = stackReleaseProgress;
            this.stackSettledShiftProgress = stackSettledShiftProgress;
            this.overScrollShift = overScrollShift;
            this.appEntrySessionActive = appEntrySessionActive;
            this.maxTranslationZ = maxTranslationZ;
            this.zStepPx = zStepPx;
            this.blankTapExitActive = blankTapExitActive;
            this.stackContentBlurEnabled = stackContentBlurEnabled;
        }
    }

    private static final class StackTaskInput {
        final View taskView;
        final float rawOffset;
        final float layoutProgress;
        final float collapsedReferenceProgress;
        final float taskWidth;
        final float taskHeight;
        final float taskCenteredLeftPx;
        final float nativeDismissTranslationX;
        final LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState;
        final LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState;

        StackTaskInput(
                View taskView,
                float rawOffset,
                float layoutProgress,
                float collapsedReferenceProgress,
                float taskWidth,
                float taskHeight,
                float taskCenteredLeftPx,
                float nativeDismissTranslationX,
                LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState,
                LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState) {
            this.taskView = taskView;
            this.rawOffset = rawOffset;
            this.layoutProgress = layoutProgress;
            this.collapsedReferenceProgress = collapsedReferenceProgress;
            this.taskWidth = taskWidth;
            this.taskHeight = taskHeight;
            this.taskCenteredLeftPx = taskCenteredLeftPx;
            this.nativeDismissTranslationX = nativeDismissTranslationX;
            this.gestureReleaseTaskState = gestureReleaseTaskState;
            this.blankTapExitState = blankTapExitState;
        }
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewConstructors(module, loader);
        hookRecentsViewMethod(module, loader, "updatePageScales");
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewMethod(module, loader, "applyAttachAlpha");
        hookRecentsViewMethod(module, loader, "resetTaskVisuals");
        hookRecentsViewSetTaskIconVisible(module, loader);
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
                    scheduleStackLayout(
                            recentsView,
                            false,
                            "refreshTrackedViews",
                            true,
                            true);
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
                            LauncherRecentsPerf.hit("animationFrame:constructorPost", recentsView);
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
                        scheduleStackLayout(
                                recentsView,
                                false,
                                methodName + "_blankExitSuppress",
                                true,
                                false);
                        return null;
                    }
                    if (shouldSuppressGestureReleaseStockVisualMethod(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        LauncherRecentsTaskVisuals.forceRecentsTaskAlphaVisible(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            scheduleStackLayout(
                                    recentsView,
                                    false,
                                    methodName + "_gestureReleaseSuppress",
                                    true,
                                    true);
                        }
                        return null;
                    }
                    if (shouldSuppressStockPageScaleUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            scheduleStackLayout(
                                    recentsView,
                                    true,
                                    methodName + "_scaleSuppress",
                                    false,
                                    true);
                        }
                        return null;
                    }
                    if (shouldSuppressStockPageOffsetUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            scheduleStackLayout(
                                    recentsView,
                                    true,
                                    methodName + "_offsetSuppress",
                                    true,
                                    true);
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
                        scheduleStackLayout(
                                recentsView,
                                true,
                                methodName + "_after",
                                !"updatePageScales".equals(methodName),
                                true);
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
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onScrollChanged",
                    t);
        }
    }

    private static void hookRecentsViewSetTaskIconVisible(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setTaskIconVisible", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && LauncherRecentsTransitionController
                        .shouldSuppressGestureReleaseStockTaskVisuals((View) thisObject)) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    LauncherRecentsCompat.setBooleanField(recentsView, "mTaskIconVisible", true);
                    LauncherRecentsTaskVisuals.forceRecentsTaskAlphaVisible(recentsView);
                    recentsView.invalidate();
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setTaskIconVisible",
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
                    // 【方案三 3-B】手势释放动画运行期间，动画帧回调已经负责 apply layout，
                    // 此处跳过冗余触发，避免每帧重复计算 3~5 次堆叠布局
                    if (LauncherRecentsTransitionController
                            .isGestureRecentsStackReleaseAnimationActive(recentsView)) {
                        return result;
                    }
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
                    if (LauncherRecentsTransitionController
                            .shouldSuppressGestureReleaseStockTaskVisuals(recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        Object result = chain.proceed();
                        LauncherRecentsTaskVisuals.forceRecentsTaskAlphaVisible(recentsView);
                        if (shouldApplyDynamicStackLayout(recentsView)) {
                            scheduleStackLayout(
                                    recentsView,
                                    false,
                                    "contentAlpha_gestureReleaseSuppress",
                                    true,
                                    true);
                        }
                        recentsView.invalidate();
                        return result;
                    }
                    if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                            && arg0 instanceof Float
                            && (Float) arg0 < 1f) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        scheduleStackLayout(
                                recentsView,
                                false,
                                "contentAlpha_blankExit",
                                true,
                                false);
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
                        scheduleStackLayout(
                                recentsView,
                                true,
                                "contentAlpha_after",
                                true,
                                true);
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
        long perfStartNs = LauncherRecentsPerf.start(recentsView);
        if (!(recentsView instanceof ViewGroup)) {
            LauncherRecentsPerf.end("prepareRecentsView", perfStartNs);
            return;
        }
        try {
            ViewGroup group = (ViewGroup) recentsView;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            ViewParent parent = group.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).setClipChildren(false);
            }
        } finally {
            LauncherRecentsPerf.end("prepareRecentsView", perfStartNs);
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
        long perfStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            if (!LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                    recentsView)) {
                LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
                return;
            }
            if (LauncherRecentsState.isOverviewStateStackBaselineCaptured(recentsView)) {
                return;
            }
            LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
            LauncherRecentsState.setOverviewStateStackBaselineCaptured(recentsView, true);
        } finally {
            LauncherRecentsPerf.end("captureStockTaskStates", perfStartNs);
        }
    }

    static void captureBlankTapHomeExitTaskStates(View recentsView) {
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.clear();
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        int blankTapAnchorIndex = resolveStackLayoutAnchorIndex(
                recentsView,
                -1,
                taskViewCount,
                STACK_STABLE_VISIBLE_RADIUS);
        float anchorVisibleOffset = 0f;
        boolean hasVisibleAnchor = false;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            if (shouldHideStackLayoutTask(i, blankTapAnchorIndex, STACK_STABLE_VISIBLE_RADIUS)) {
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
            LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                    taskView,
                    new LauncherRecentsTaskVisuals.StackTaskVisualState(
                            taskWidth * 0.5f,
                            taskHeight * 0.5f,
                            0f,
                            taskOffsetX,
                            state.startTaskOffsetY,
                            state.startBoxTranslationY,
                            desiredScale,
                            state.startAttachAlpha,
                            desiredStableAlpha,
                            state.startActivityTitleAlpha * resolveBlankTapExitAlpha(
                                    clampedProgress),
                            state.startStackContentBlurProgress,
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "fullscreenProgress",
                                    0f),
                            taskView.getTranslationZ(),
                            true,
                            false));
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
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        View runningTaskView = runningTaskObject instanceof View
                ? (View) runningTaskObject
                : null;
        float pageSpacing = LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0);
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
            float startHorizontalOffsetX =
                    LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView);
            LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.put(
                    taskView,
                    new LauncherRecentsState.GestureReleaseTaskState(
                            startVisibleOffset,
                            targetVisibleOffset,
                            startHorizontalOffsetX));
        }
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

    private static boolean scheduleStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData,
            boolean dynamicOnly) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsState.PendingStackLayoutApplyState pendingState =
                LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.get(recentsView);
        if (pendingState != null) {
            pendingState.captureStockState |= captureStockState;
            pendingState.syncVisibleTaskData |= syncVisibleTaskData;
            pendingState.dynamicOnly &= dynamicOnly;
            pendingState.source = mergeScheduledStackLayoutSource(pendingState.source, source);
            return true;
        }
        LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.put(
                recentsView,
                new LauncherRecentsState.PendingStackLayoutApplyState(
                        captureStockState,
                        syncVisibleTaskData,
                        dynamicOnly,
                        source));
        recentsView.postOnAnimation(() -> runScheduledStackLayout(recentsView));
        return true;
    }

    private static String mergeScheduledStackLayoutSource(String currentSource, String nextSource) {
        if (currentSource == null) {
            return nextSource;
        }
        if (nextSource == null || currentSource.equals(nextSource)) {
            return currentSource;
        }
        return "scheduled";
    }

    private static void runScheduledStackLayout(View recentsView) {
        LauncherRecentsState.PendingStackLayoutApplyState pendingState =
                LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.remove(recentsView);
        if (recentsView == null || pendingState == null) {
            return;
        }
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (shouldBlockAppToRecentsStackApply(recentsView)) {
            return;
        }
        if (pendingState.dynamicOnly && !shouldApplyDynamicStackLayout(recentsView)) {
            return;
        }
        if (pendingState.captureStockState
                || (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                && !LauncherRecentsState.isOverviewStateStackBaselineCaptured(recentsView))) {
            captureStockTaskStatesForStackApply(recentsView);
        }
        applyStackLayout(
                recentsView,
                false,
                pendingState.source != null ? pendingState.source : "scheduled",
                pendingState.syncVisibleTaskData);
        recentsView.invalidate();
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
            LauncherRecentsPerf.hit("skipDuplicate:" + source, recentsView);
            return;
        }
        boolean layoutApplied = false;
        long totalStartNs = LauncherRecentsPerf.start(recentsView);
        long layoutStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            layoutApplied = applyStackLayoutMeasured(
                    recentsView,
                    captureStockState,
                    stackLayoutRadius);
        } finally {
            long layoutCostNs = LauncherRecentsPerf.end("layoutCompute:" + source, layoutStartNs);
            reportSlowApplyDynamicLayout(recentsView, source, stackLayoutRadius, layoutCostNs);
        }
        if (syncVisibleTaskData && layoutApplied) {
            long visibleDataStartNs = LauncherRecentsPerf.start(recentsView);
            try {
                LauncherRecentsTouchController.ensureStackVisibleTaskDataIfNeeded(recentsView, 15);
            } finally {
                LauncherRecentsPerf.end("visibleTaskDataSync:" + source, visibleDataStartNs);
            }
        }
        LauncherRecentsPerf.end("applyStackLayoutTotal:" + source, totalStartNs);
    }

    private static void reportSlowApplyDynamicLayout(
            View recentsView,
            String source,
            int stackLayoutRadius,
            long layoutCostNs) {
        if (!"applyDynamic".equals(source) || !LauncherRecentsPerf.isSlowCall(layoutCostNs)) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        int scrollBucket = resolveSlowLogScrollBucket(recentsView);
        boolean blankTapExitActive =
                LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
        boolean gestureReleaseActive =
                LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                        recentsView)
                        || LauncherRecentsTransitionController
                        .isGestureRecentsStackReleaseAnimationActive(recentsView);
        boolean overviewStateActive =
                LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                        recentsView);
        LauncherRecentsPerf.logSlowCall(
                "slowApplyDynamicLayout",
                recentsView,
                layoutCostNs,
                "taskCount=" + taskViewCount
                        + " layoutRadius=" + stackLayoutRadius
                        + " scrollBucket=" + scrollBucket
                        + " blankTapExit=" + blankTapExitActive
                        + " gestureRelease=" + gestureReleaseActive
                        + " overviewState=" + overviewStateActive);
    }

    private static int resolveSlowLogScrollBucket(View recentsView) {
        int width = recentsView != null ? recentsView.getWidth() : 0;
        if (width <= 0) {
            return Integer.MIN_VALUE;
        }
        return recentsView.getScrollX()
                / Math.max(1, width / STACK_SLOW_LOG_SCROLL_BUCKET_DIVISOR);
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
                && (!syncVisibleTaskData || lastState.syncedVisibleTaskData)
                && !LauncherRecentsTaskVisuals.hasAppliedTaskScaleMismatch(recentsView)) {
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
                || "scheduled".equals(source)
                || "onScrollChanged".equals(source)
                || "refreshTrackedViews".equals(source)
                || "contentAlpha_blankExit".equals(source)
                || "contentAlpha_after".equals(source)
                || "updatePageScales_after".equals(source)
                || "updatePageScales_blankExitSuppress".equals(source)
                || "updatePageOffsetsForFlyme_blankExitSuppress".equals(source)
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
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        key = mixStackLayoutApplyKey(
                key,
                config != null && config.launcherIosStackRecentsBlurEnabled ? 1 : 0);
        return key;
    }

    private static long mixStackLayoutApplyKey(long key, int value) {
        return key * 31L + value;
    }

    private static int quantizeStackLayoutFloat(float value) {
        return Math.round(value * 1000f);
    }

    private static boolean applyStackLayoutMeasured(
            View recentsView,
            boolean captureStockState,
            int stackLayoutRadius) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsState.LaunchHandoffState launchState =
                LauncherRecentsState.getActiveTaskLaunchHandoff(recentsView);
        if (launchState != null && launchState.frozen) {
            return false;
        }
        if (LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(recentsView)
                && !LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze()) {
            return false;
        }
        if (shouldBlockAppToRecentsStackApply(recentsView)) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            restoreTaskTransforms(recentsView, taskViewCount);
            return false;
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
        boolean blankTapExitActive =
                LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
        StackLayoutContext layoutContext = new StackLayoutContext(
                recentsView,
                taskViewCount,
                runningTaskView,
                runningTaskChildIndex,
                referenceWidth,
                referenceHeight,
                pageSpan,
                rawOffsets,
                blankTapExitProgress,
                stackEntryProgress,
                stackVerticalProgress,
                gestureStackReleaseActive,
                overviewStateStackAnimationActive,
                overviewStateStackHandoffProgress,
                stackReleaseProgress,
                stackSettledShiftProgress,
                overScrollShift,
                appEntrySessionActive,
                maxTranslationZ,
                zStepPx,
                blankTapExitActive,
                config.launcherIosStackRecentsBlurEnabled);

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
                LauncherRecentsTaskVisuals.clearStackContentBlurIfApplied(taskView);
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
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (captureStockState) {
                LauncherRecentsTaskVisuals.captureStockTaskState(taskView);
            }
            LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                    taskView,
                    buildStackTaskVisualState(
                            layoutContext,
                            buildStackTaskInput(layoutContext, taskView, i)));
        }
        if (launchState != null && launchState.handoffEnabled) {
            LauncherRecentsLaunchController.applyLaunchHandoffLayout(recentsView, launchState);
        }
        return true;
    }

    private static StackTaskInput buildStackTaskInput(
            StackLayoutContext context,
            View taskView,
            int index) {
        LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState =
                (context.gestureStackReleaseActive
                        || LauncherRecentsState.isGestureStackReleasedStable(context.recentsView))
                        ? LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.get(taskView)
                        : null;
        float rawOffset = context.rawOffsets[index];
        float nativeDismissTranslationX =
                LauncherRecentsCompat.readFloatField(taskView, "dismissTranslationX", 0f);
        float dismissTranslationX = LauncherRecentsTouchController
                .shouldSuppressNativeDismissTranslation(context.recentsView)
                ? 0f
                : nativeDismissTranslationX;
        // Keep the stock gap-closing animation, but remap its logical page position into
        // the compressed stack so sibling cards move into the dismissed slot instead of
        // adding a second full-page horizontal shift on top of it.
        float stackDismissLayoutOffset =
                LauncherRecentsTouchController.readStackDismissLayoutOffset(taskView);
        float layoutRawOffset = rawOffset - context.overScrollShift;
        float physicalRawOffset = layoutRawOffset + dismissTranslationX;
        float effectiveRawOffset = physicalRawOffset + stackDismissLayoutOffset;
        float progress = effectiveRawOffset / context.pageSpan;
        float layoutProgress = resolveStackReleaseSettledProgress(
                progress,
                context.stackSettledShiftProgress);
        float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : context.referenceWidth;
        float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : context.referenceHeight;
        float taskCenteredLeftPx =
                Math.max(0f, (context.recentsView.getWidth() - taskWidth) * 0.5f);
        float collapsedReferenceProgress = progress;
        if (context.appEntrySessionActive && context.runningTaskView != null) {
            collapsedReferenceProgress = resolveAppEntryCollapsedProgress(
                    taskView,
                    context.runningTaskView,
                    index,
                    context.runningTaskChildIndex,
                    context.taskViewCount);
        }
        return new StackTaskInput(
                taskView,
                rawOffset,
                layoutProgress,
                collapsedReferenceProgress,
                taskWidth,
                taskHeight,
                taskCenteredLeftPx,
                nativeDismissTranslationX,
                gestureReleaseTaskState,
                LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView));
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState buildStackTaskVisualState(
            StackLayoutContext context,
            StackTaskInput input) {
        View taskView = input.taskView;
        float stackEntryLiftPx = Math.min(
                input.taskHeight * STACK_ENTRY_LIFT_RATIO,
                FlymeStatusBarSizer.dp(context.recentsView.getContext(), 40));
        float finalVisibleOffset = resolveStackVisibleOffset(
                context.recentsView,
                input.layoutProgress,
                input.taskWidth,
                input.taskCenteredLeftPx);
        float finalTaskOffsetY = stackEntryLiftPx * (1.0f - context.stackVerticalProgress);
        float taskEntryProgress = resolveTaskStackEntryProgress(
                context.stackEntryProgress,
                input.collapsedReferenceProgress);
        float collapsedVisibleOffset = resolveStackVisibleOffset(
                context.recentsView,
                input.collapsedReferenceProgress,
                input.taskWidth,
                input.taskCenteredLeftPx) * STACK_ENTRY_INITIAL_SPREAD_RATIO;
        float desiredVisibleOffset = lerp(
                collapsedVisibleOffset,
                finalVisibleOffset,
                taskEntryProgress);
        if (context.gestureStackReleaseActive) {
            desiredVisibleOffset *= lerp(
                    STACK_RELEASE_INITIAL_SPREAD_RATIO,
                    1.0f,
                    smoothStep(context.stackReleaseProgress));
        }
        if (input.gestureReleaseTaskState != null) {
            desiredVisibleOffset = lerp(
                    input.gestureReleaseTaskState.startVisibleOffset,
                    input.gestureReleaseTaskState.targetVisibleOffset,
                    context.stackReleaseProgress);
        }
        float desiredLayerProgress = resolveStackLayerProgress(
                context.recentsView,
                input.taskCenteredLeftPx,
                input.taskWidth,
                desiredVisibleOffset);
        float desiredScale = lerp(STACK_MIN_SCALE, 1.0f, desiredLayerProgress);
        float desiredTaskOffsetY = lerp(
                0f,
                finalTaskOffsetY,
                Math.max(context.stackVerticalProgress, taskEntryProgress));
        float desiredBoxTranslationY = lerp(
                LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                Math.max(context.stackVerticalProgress, taskEntryProgress * 0.6f));
        if (context.gestureStackReleaseActive) {
            desiredBoxTranslationY = 0f;
        }
        float desiredTranslationZ = (context.maxTranslationZ + context.zStepPx)
                * desiredLayerProgress;
        float desiredStableAlpha = 1f;
        LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState =
                input.blankTapExitState;
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
        float activityTitleAlpha = desiredStableAlpha > 0.001f ? 1f : 0f;
        boolean blankTapExitTaskActive = context.blankTapExitActive
                && blankTapExitState != null;
        if (context.blankTapExitActive) {
            if (blankTapExitTaskActive) {
                float startVisibleOffset = blankTapExitState.startVisibleOffset;
                float centerVisibleOffset = blankTapExitState.centerVisibleOffset;
                float pathProgress = smoothStep(context.blankTapExitProgress);
                float controlVisibleOffset = centerVisibleOffset;
                float taskLeftPx = input.taskCenteredLeftPx + controlVisibleOffset;
                float exitTravelPx = Math.max(
                        taskLeftPx + input.taskWidth + FlymeStatusBarSizer.dp(
                                context.recentsView.getContext(),
                                64),
                        input.taskWidth * (1f + BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO));
                float exitVisibleOffset = controlVisibleOffset - exitTravelPx;
                desiredVisibleOffset = quadraticBezier(
                        startVisibleOffset,
                        controlVisibleOffset,
                        exitVisibleOffset,
                        pathProgress);
                desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * pathProgress);
                desiredStableAlpha *= resolveBlankTapExitAlpha(pathProgress);
                activityTitleAlpha = 1f;
            } else {
                desiredStableAlpha = 0f;
                activityTitleAlpha = 0f;
            }
        }
        float stackLeftClampAlpha = resolveStackLeftClampAlpha(
                context.recentsView,
                input.layoutProgress,
                input.taskWidth,
                input.taskCenteredLeftPx);
        // 在 app→recents 入场流程（含 release 动画）期间，scroll/layoutProgress 尚未收敛，
        // stackLeftClampAlpha 可能 < 1f，若此时用它压低 desiredStableAlpha，
        // 会导致 activityTitleAlpha 随手势持握时长变化（越短越透明）。
        // gestureReleasedStable=true 时 scroll 已由模块归位，可安全使用 clamp。
        boolean skipClampForEntry = (context.gestureStackReleaseActive
                || isAppToRecentsEntryInProgress(context.recentsView))
                && !LauncherRecentsState.isGestureStackReleasedStable(context.recentsView);
        if (!blankTapExitTaskActive && !skipClampForEntry) {
            desiredStableAlpha *= stackLeftClampAlpha;
        }

        float targetBlurProgress = 0f;
        if (context.stackContentBlurEnabled) {
            targetBlurProgress = resolveStackContentBlurProgress(
                    stackLeftClampAlpha,
                    taskEntryProgress);
            if (blankTapExitTaskActive) {
                targetBlurProgress = blankTapExitState.startStackContentBlurProgress;
            }
        }
        float translationCompensationX =
                desiredVisibleOffset - input.rawOffset - input.nativeDismissTranslationX;

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
        if (context.gestureStackReleaseActive) {
            if (input.gestureReleaseTaskState != null) {
                appliedHorizontalOffsetX = lerp(
                        input.gestureReleaseTaskState.startHorizontalOffsetX,
                        0f,
                        context.stackReleaseProgress);
                appliedTaskOffsetX = translationCompensationX - appliedHorizontalOffsetX;
            } else {
                appliedHorizontalOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                        appliedHorizontalOffsetX,
                        context.stackReleaseProgress);
                appliedTaskOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                        appliedTaskOffsetX,
                        context.stackReleaseProgress);
            }
            appliedTaskOffsetY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                    appliedTaskOffsetY,
                    context.stackReleaseProgress);
            appliedBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    appliedBoxTranslationY,
                    context.stackReleaseProgress);
            appliedScale = lerp(
                    LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                    appliedScale,
                    context.stackReleaseProgress);
            appliedFullscreenProgress = lerp(
                    LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                    appliedFullscreenProgress,
                    context.stackReleaseProgress);
            appliedTranslationZ = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                    appliedTranslationZ,
                    context.stackReleaseProgress);
            appliedAttachAlpha = lerp(
                    1f,
                    appliedAttachAlpha,
                    context.stackReleaseProgress);
            appliedStableAlpha = lerp(
                    1f,
                    appliedStableAlpha,
                    context.stackReleaseProgress);
            appliedBlurProgress = lerp(0f, appliedBlurProgress, context.stackReleaseProgress);
        }
        if (context.overviewStateStackAnimationActive) {
            appliedHorizontalOffsetX = lerp(
                    LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                    appliedHorizontalOffsetX,
                    context.overviewStateStackHandoffProgress);
            appliedTaskOffsetX = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                    appliedTaskOffsetX,
                    context.overviewStateStackHandoffProgress);
            appliedTaskOffsetY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                    appliedTaskOffsetY,
                    context.overviewStateStackHandoffProgress);
            appliedBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    appliedBoxTranslationY,
                    context.overviewStateStackHandoffProgress);
            appliedScale = lerp(
                    LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                    appliedScale,
                    context.overviewStateStackHandoffProgress);
            appliedFullscreenProgress = lerp(
                    LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                    appliedFullscreenProgress,
                    context.overviewStateStackHandoffProgress);
            appliedTranslationZ = lerp(
                    LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                    appliedTranslationZ,
                    context.overviewStateStackHandoffProgress);
            appliedAttachAlpha = lerp(
                    LauncherRecentsTaskVisuals.readLastStockAttachAlpha(taskView),
                    appliedAttachAlpha,
                    context.overviewStateStackHandoffProgress);
            appliedStableAlpha = lerp(
                    LauncherRecentsTaskVisuals.readLastStockStableAlpha(taskView),
                    appliedStableAlpha,
                    context.overviewStateStackHandoffProgress);
            appliedBlurProgress = lerp(
                    0f,
                    appliedBlurProgress,
                    context.overviewStateStackHandoffProgress);
        }
        // 头部内容不再二次衰减；实际透明度由 TaskView 自身 alpha 决定。
        boolean entryInProgress = (context.gestureStackReleaseActive
                || isAppToRecentsEntryInProgress(context.recentsView))
                && !LauncherRecentsState.isGestureStackReleasedStable(context.recentsView);
        float appliedActivityTitleAlpha;
        if (context.blankTapExitActive) {
            appliedActivityTitleAlpha = activityTitleAlpha;
        } else if (context.gestureStackReleaseActive) {
            appliedActivityTitleAlpha = 1f;
        } else if (entryInProgress) {
            appliedActivityTitleAlpha =
                    LauncherRecentsTaskVisuals.readActivityTitleAlpha(taskView);
        } else {
            appliedActivityTitleAlpha = appliedStableAlpha > 0.001f ? 1f : 0f;
        }
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                input.taskWidth * 0.5f,
                input.taskHeight * 0.5f,
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
                context.stackContentBlurEnabled,
                true);
    }

    static void cancelStackLayoutRecovery(View recentsView) {
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
    }

    static void startStackLayoutRecovery(View recentsView) {
        if (!shouldAllowStackLayoutRecovery(recentsView)) {
            return;
        }
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.put(
                recentsView,
                STACK_ENTRY_LIGHT_RADIUS + STACK_LAYOUT_RECOVERY_RADIUS_STEP);
        recentsView.postOnAnimation(() -> runStackLayoutRecoveryFrame(recentsView));
    }

    static boolean isStackLayoutRecoveryActive(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.containsKey(recentsView);
    }

    private static boolean shouldAllowStackLayoutRecovery(View recentsView) {
        return shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && (!LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(
                recentsView)
                || LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze())
                && !shouldBlockAppToRecentsStackApply(recentsView);
    }

    private static void runStackLayoutRecoveryFrame(View recentsView) {
        Integer radius = LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.get(recentsView);
        if (recentsView == null
                || radius == null
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || !shouldAllowStackLayoutRecovery(recentsView)) {
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (radius >= taskViewCount) {
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            applyStackLayout(recentsView, false, STACK_STABLE_VISIBLE_RADIUS, "recoveryFinal", true);
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
            return resolveNearestStackLayoutPage(recentsView, taskViewCount);
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
                && (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
                recentsView)
                // handoff 开始后 deferred 已清零但 progress 尚未建立，此帧也需压制
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView))
                && (!LauncherRecentsStateAnimationController.shouldKeepOverviewPeekStockLayout(
                recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || isStackLayoutRecoveryActive(recentsView));
    }

    private static boolean shouldSuppressStockPageScaleUpdate(
            String methodName,
            View recentsView) {
        return "updatePageScales".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && (!LauncherRecentsStateAnimationController.shouldKeepOverviewPeekStockLayout(
                recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || isStackLayoutRecoveryActive(recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
                recentsView))
                && (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                // handoff 开始后 deferred 已清零但 progress/stable 尚未建立，此帧也需压制。
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
                recentsView))
                && (!LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(
                recentsView)
                || LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze()
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

    private static boolean shouldSuppressGestureReleaseStockVisualMethod(
            String methodName,
            View recentsView) {
        return ("resetTaskVisuals".equals(methodName)
                || "applyAttachAlpha".equals(methodName))
                && LauncherRecentsTransitionController
                .shouldSuppressGestureReleaseStockTaskVisuals(recentsView);
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
        LauncherRecentsTaskVisuals.clearStackContentBlurIfApplied(taskView);
        LauncherRecentsTaskVisuals.setFullscreenProgress(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView));
        LauncherRecentsTaskVisuals.restoreTaskShadow(taskView);
        LauncherRecentsTaskVisuals.setTranslationZ(
                taskView,
                LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView));
        LauncherRecentsTaskVisuals.clearAppliedTaskState(taskView);
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
                recentsView)
                || isStackLayoutRecoveryActive(recentsView))
                && (!LauncherRecentsTouchController.isStackDismissPostRemoveAnimationActive(
                recentsView)
                || LauncherRecentsTouchController.shouldBypassStackDismissLayoutFreeze())
                && !shouldBlockAppToRecentsStackApply(recentsView);
    }

    static boolean shouldSuppressStockLayoutMutation(View recentsView) {
        return recentsView != null
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                || LauncherRecentsTouchController.shouldSuppressStackDismissPageMutation(
                recentsView));
    }

    static boolean applyDynamicStackLayoutIfNeeded(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsPerf.hit("animationFrame:applyDynamic", recentsView);
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (!shouldApplyDynamicStackLayout(recentsView)) {
            return false;
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                && !LauncherRecentsState.isOverviewStateStackBaselineCaptured(recentsView)) {
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
        // app→recents 入场流程的任何阶段（defer 中 / 手势已释放但 release 动画未稳定）
        // mAdjacentPageHorizontalOffset 尚未收敛，若此时读取系统实时值会导致
        // stackEntryProgress 偏低，进而拉低卡片名称/图标透明度（与触控时长成正比）
        if (isAppToRecentsEntryInProgress(recentsView)) {
            return 1f;
        }
        return resolveStockStackEntryProgress(recentsView);
    }

    static float resolveStackVerticalProgress(View recentsView) {
        if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
            return 1f;
        }
        if (isAppToRecentsEntryInProgress(recentsView)) {
            return 1f;
        }
        return resolveStockStackVerticalProgress(recentsView);
    }

    /**
     * 判断当前是否处于 app→recents 入场流程的任意阶段：
     * - 手势上滑进行中（layoutDeferred=true），mAdjacentPageHorizontalOffset 尚未从 1 收敛到 0
     * - 手势已松手但 release 动画仍在运行（gestureReleased=true 但未 stable）
     * 上述阶段读取系统 mAdjacentPageHorizontalOffset/mContentAlpha 会得到未收敛值，
     * 导致 stackEntryProgress 偏低，进而拉低名称/图标透明度。
     */
    private static boolean isAppToRecentsEntryInProgress(View recentsView) {
        return LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView);
    }

    private static boolean shouldBlockAppToRecentsStackApply(View recentsView) {
        return (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView))
                && !LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView);
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
        float startAdjacentOffset = Math.max(
                0.001f,
                LauncherRecentsState.readOverviewStateStackStartAdjacentOffset(
                        recentsView,
                        0.53f));
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
        return progress >= 0f ? visibleOffset : -(visibleOffset * STACK_LEFT_MOVE_RATIO);
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
        float stackSpreadProgress = stackDepth;
        if (progress > 0f) {
            stackSpreadProgress += (STACK_RIGHT_BASE_SPEEDUP_RATIO * stackDepth)
                    + (STACK_RIGHT_SPEEDUP_RATIO * stackDepth * stackDepth);
        }
        return stackRightOffsetPx * stackSpreadProgress;
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
        float currentOffset = resolveStackVisibleOffset(
                recentsView,
                progress,
                taskWidth,
                taskCenteredLeftPx);
        float frontOffset = resolveStackVisibleOffset(
                recentsView,
                progress + 1f,
                taskWidth,
                taskCenteredLeftPx);
        float distancePx = Math.abs(frontOffset - currentOffset);
        float opaqueDistancePx = Math.max(
                1f,
                taskWidth * 0.35f);
        return smoothStep(remapProgress(distancePx, 0f, opaqueDistancePx));
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
