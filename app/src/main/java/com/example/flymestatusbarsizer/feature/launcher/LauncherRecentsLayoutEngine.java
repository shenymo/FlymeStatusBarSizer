package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

final class LauncherRecentsLayoutEngine {
    private static final String LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState";
    private static final float STACK_ENTRY_LIFT_RATIO = 0.05f;
    private static final float STACK_ENTRY_INITIAL_SPREAD_RATIO = 0.8f;
    private static final float STACK_LEFT_EDGE_INSET_RATIO = -0.05f;
    private static final float STACK_RIGHT_VISIBLE_RATIO = 0.80f;
    private static final float STACK_LEFT_MOVE_RATIO = 0.45f;
    private static final float STACK_RIGHT_BASE_SPEEDUP_RATIO = 0.16f;
    private static final float STACK_RIGHT_SPEEDUP_RATIO = 0.40f;
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
    private static final int STACK_ENTRY_LIGHT_RADIUS = 1;
    private static final int STACK_STABLE_VISIBLE_RADIUS = 2;
    private static final int STACK_GESTURE_RELEASE_CORE_RADIUS = 2;
    private static final int STACK_LAYOUT_RECOVERY_RADIUS_STEP = 4;
    private static final int STACK_SLOW_LOG_SCROLL_BUCKET_DIVISOR = 2;
    private static final long STACK_LAYOUT_DUPLICATE_WINDOW_NS = 16_000_000L;

    private LauncherRecentsLayoutEngine() {
    }

    private static final class StackLayoutContext {
        final View recentsView;
        final int taskViewCount;
        final View runningTaskView;
        final int runningTaskChildIndex;
        final float referenceWidth;
        final float referenceHeight;
        final float referencePrimarySize;
        final float pageSpan;
        final boolean primaryScrollHorizontal;
        final int primaryScroll;
        final float leftEdgeRevealProgress;
        final float blankTapExitProgress;
        final float stackEntryProgress;
        final float stackVerticalProgress;
        final boolean gestureStackReleaseActive;
        final boolean overviewStateStackAnimationActive;
        final boolean desktopOverviewEntryWindow;
        final float overviewStateStackHandoffProgress;
        final float stackReleaseProgress;
        final float stackSettledShiftProgress;
        final int edgeScrollCorrection;
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
                float referencePrimarySize,
                float pageSpan,
                boolean primaryScrollHorizontal,
                int primaryScroll,
                float leftEdgeRevealProgress,
                float blankTapExitProgress,
                float stackEntryProgress,
                float stackVerticalProgress,
                boolean gestureStackReleaseActive,
                boolean overviewStateStackAnimationActive,
                boolean desktopOverviewEntryWindow,
                float overviewStateStackHandoffProgress,
                float stackReleaseProgress,
                float stackSettledShiftProgress,
                int edgeScrollCorrection,
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
            this.referencePrimarySize = referencePrimarySize;
            this.pageSpan = pageSpan;
            this.primaryScrollHorizontal = primaryScrollHorizontal;
            this.primaryScroll = primaryScroll;
            this.leftEdgeRevealProgress = leftEdgeRevealProgress;
            this.blankTapExitProgress = blankTapExitProgress;
            this.stackEntryProgress = stackEntryProgress;
            this.stackVerticalProgress = stackVerticalProgress;
            this.gestureStackReleaseActive = gestureStackReleaseActive;
            this.overviewStateStackAnimationActive = overviewStateStackAnimationActive;
            this.desktopOverviewEntryWindow = desktopOverviewEntryWindow;
            this.overviewStateStackHandoffProgress = overviewStateStackHandoffProgress;
            this.stackReleaseProgress = stackReleaseProgress;
            this.stackSettledShiftProgress = stackSettledShiftProgress;
            this.edgeScrollCorrection = edgeScrollCorrection;
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
        final float taskPrimarySize;
        final float taskCenteredPrimaryStartPx;
        final float nativeDismissTranslationPrimary;
        final LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState;
        final LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState;

        StackTaskInput(
                View taskView,
                float rawOffset,
                float layoutProgress,
                float collapsedReferenceProgress,
                float taskWidth,
                float taskHeight,
                float taskPrimarySize,
                float taskCenteredPrimaryStartPx,
                float nativeDismissTranslationPrimary,
                LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState,
                LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState) {
            this.taskView = taskView;
            this.rawOffset = rawOffset;
            this.layoutProgress = layoutProgress;
            this.collapsedReferenceProgress = collapsedReferenceProgress;
            this.taskWidth = taskWidth;
            this.taskHeight = taskHeight;
            this.taskPrimarySize = taskPrimarySize;
            this.taskCenteredPrimaryStartPx = taskCenteredPrimaryStartPx;
            this.nativeDismissTranslationPrimary = nativeDismissTranslationPrimary;
            this.gestureReleaseTaskState = gestureReleaseTaskState;
            this.blankTapExitState = blankTapExitState;
        }
    }

    private static final class ComputedStackLayout {
        final ArrayList<Integer> activeIndices;
        final ArrayList<Integer> processIndices;
        final View runningTaskView;
        final boolean appEntrySessionActive;
        final int[] runningTaskIds;
        final HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> visualStates =
                new HashMap<>();
        final ArrayList<View> coreOnlyTaskViews = new ArrayList<>();

        ComputedStackLayout(
                ArrayList<Integer> activeIndices,
                ArrayList<Integer> processIndices,
                View runningTaskView,
                boolean appEntrySessionActive,
                int[] runningTaskIds) {
            this.activeIndices = activeIndices;
            this.processIndices = processIndices;
            this.runningTaskView = runningTaskView;
            this.appEntrySessionActive = appEntrySessionActive;
            this.runningTaskIds = runningTaskIds;
        }
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewConstructors(module, loader);
        hookRecentsViewMethod(module, loader, "updatePageScales");
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewMethod(module, loader, "updateHorizontalOffset");
        hookRecentsViewMethod(module, loader, "updateTaskViewsSnapshotRadius");
        hookRecentsViewMethod(module, loader, "updateCurveProperties");
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
                            LauncherRecentsPerf.hit("animationFrame:constructorPost", recentsView);
                            requestStackLayout(recentsView, "constructorPost", true, false);
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
                    if (shouldSuppressStackAlphaVisualMethod(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        LauncherRecentsTaskVisuals.forceRecentsTaskAlphaVisible(recentsView);
                        scheduleStackLayoutFromHook(
                                recentsView,
                                false,
                                methodName + "_gestureReleaseSuppress",
                                true,
                                true);
                        return null;
                    }
                    if (shouldSuppressStockPageScaleUpdate(methodName, recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        applyStackLayoutFromScaleSuppress(
                                recentsView,
                                methodName + "_scaleSuppress");
                        return null;
                    }
                    if (shouldSuppressStockPageOffsetUpdate(methodName, recentsView)
                            || shouldSuppressStockHorizontalOffsetUpdate(
                            methodName,
                            recentsView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        scheduleStackLayoutFromHook(
                                recentsView,
                                false,
                                methodName + "_offsetSuppress",
                                true,
                                true);
                        return null;
                    }
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (shouldSkipIdleCurvePropertiesRelayout(methodName, recentsView)) {
                        return result;
                    }
                    LauncherRecentsState.trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    scheduleStackLayoutFromHook(
                            recentsView,
                            false,
                            methodName + "_after",
                            !"updatePageScales".equals(methodName),
                            true);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView." + methodName,
                    t);
        }
    }

    private static boolean shouldSkipIdleCurvePropertiesRelayout(
            String methodName,
            View recentsView) {
        return "updateCurveProperties".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && LauncherRecentsCompat.invokeBoolean(recentsView, "isScrollerFinished", true)
                && !LauncherRecentsCompat.invokeBoolean(recentsView, "isHandlingTouch", false)
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && !LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                && !LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                && !LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                && !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                && !LauncherRecentsTouchController.isStackDismissRelayoutAnimationActive(
                recentsView)
                && !LauncherRecentsState.hasActiveTaskLaunchTransitionGeometry(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView);
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
                    if (isStackScrollerActive(recentsView)) {
                        applyDynamicStackLayoutImmediatelyForScroll(recentsView);
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
                    // 动画帧已负责刷新布局，此处只跳过重复触发。
                    if (LauncherRecentsTransitionController
                            .isGestureRecentsStackReleaseAnimationActive(recentsView)
                            || LauncherRecentsTouchController
                            .isStackDismissRelayoutAnimationActive(recentsView)) {
                        return result;
                    }
                    if (!LauncherRecentsCompat.invokeBoolean(
                            recentsView,
                            "isScrollerFinished",
                            true)) {
                        applyDynamicStackLayoutIfNeeded(recentsView);
                        return result;
                    }
                    requestStackLayout(recentsView, "dispatchScrollChanged", false, false);
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
                    if (shouldOwnStackTaskAlpha(recentsView)
                            && arg0 instanceof Float
                            && (Float) arg0 < 1f) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        LauncherRecentsCompat.writeField(recentsView, "mContentAlpha", 1f);
                        LauncherRecentsTaskVisuals.forceRecentsTaskAlphaVisible(recentsView);
                        scheduleStackLayoutFromHook(
                                recentsView,
                                false,
                                "contentAlpha_gestureReleaseSuppress",
                                true,
                                true);
                        recentsView.invalidate();
                        return null;
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
                    scheduleStackLayoutFromHook(
                            recentsView,
                            false,
                            "contentAlpha_after",
                            true,
                            true);
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
        if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
            return;
        }
        long perfStartNs = LauncherRecentsPerf.start(recentsView);
        if (!(recentsView instanceof ViewGroup)) {
            LauncherRecentsPerf.end("prepareRecentsView", perfStartNs);
            return;
        }
        try {
            ViewGroup group = (ViewGroup) recentsView;
            LauncherRecentsState.PrepareRecentsViewState state =
                    prepareRecentsViewState(recentsView);
            if (!state.recentsClipsReady
                    || group.getClipChildren()
                    || group.getClipToPadding()) {
                group.setClipChildren(false);
                group.setClipToPadding(false);
                state.recentsClipsReady = true;
            }
            ViewParent parent = group.getParent();
            if (parent instanceof ViewGroup && parent != state.clipParent) {
                ((ViewGroup) parent).setClipChildren(false);
                state.clipParent = parent;
            }
            ensureStackClearAllButtonReady(recentsView);
        } finally {
            LauncherRecentsPerf.end("prepareRecentsView", perfStartNs);
        }
    }

    static void ensureStackClearAllButtonReady(View recentsView) {
        syncStackClearAllButton(recentsView, false);
    }

    private static void syncStackClearAllButton(View recentsView, boolean forceHide) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.PrepareRecentsViewState state =
                prepareRecentsViewState(recentsView);
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            releaseStackClearAllButton(state, taskViewCount > 1);
            return;
        }
        Object value = LauncherRecentsCompat.invokeCompat(recentsView, "getClearAllButton");
        if (!(value instanceof View)) {
            clearStackClearAllButtonState(state);
            return;
        }
        View clearAllButton = (View) value;
        boolean enabled = config.launcherIosStackRecentsClearAllButtonEnabled;
        boolean allowed = enabled
                && !forceHide
                && !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                && isStackClearAllButtonAllowed(recentsView);
        if (!enabled || !allowed) {
            hideStackClearAllButtonView(state, clearAllButton, enabled);
            return;
        }
        if (isStackClearAllButtonShown(state, clearAllButton)) {
            return;
        }
        state.clearAllReady = true;
        state.clearAllEnabled = true;
        state.clearAllAllowed = true;
        state.clearAllButton = clearAllButton;
        LauncherRecentsCompat.invokeCompat(
                clearAllButton,
                "setScrollAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsCompat.invokeCompat(
                clearAllButton,
                "setContentAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsCompat.invokeCompat(
                clearAllButton,
                "setDismissAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "trySetClearAllBtnAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsCompat.invokeCompat(
                clearAllButton,
                "setVisibilityAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsCompat.invokeCompat(clearAllButton, "updateAlphaPublic");
        clearAllButton.setVisibility(View.VISIBLE);
        clearAllButton.setEnabled(true);
        clearAllButton.setClickable(true);
    }

    private static LauncherRecentsState.PrepareRecentsViewState prepareRecentsViewState(
            View recentsView) {
        LauncherRecentsState.PrepareRecentsViewState state =
                LauncherRecentsState.PREPARE_RECENTS_VIEW_STATES.get(recentsView);
        if (state == null) {
            state = new LauncherRecentsState.PrepareRecentsViewState();
            LauncherRecentsState.PREPARE_RECENTS_VIEW_STATES.put(recentsView, state);
        }
        return state;
    }

    private static boolean isStackClearAllButtonAllowed(View recentsView) {
        if (LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                || LauncherRecentsState.isAppToRecentsStackSettled(recentsView)
                || LauncherRecentsState.isOverviewStateStackSettled(recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)) {
            return true;
        }
        Object stateManager = LauncherRecentsCompat.invokeCompat(recentsView, "getStateManager");
        return isClearAllButtonState(LauncherRecentsCompat.invokeCompat(stateManager, "getState"))
                || isClearAllButtonState(
                LauncherRecentsCompat.invokeCompat(stateManager, "getCurrentStableState"))
                || isClearAllButtonState(
                LauncherRecentsCompat.invokeCompat(stateManager, "getTargetState"));
    }

    private static boolean isClearAllButtonState(Object state) {
        if (state == null) {
            return false;
        }
        ClassLoader loader = state.getClass().getClassLoader();
        Object overview = LauncherRecentsCompat.readStaticFieldCompat(
                LAUNCHER_STATE_CLASS,
                "OVERVIEW",
                loader);
        return state == overview;
    }

    static void hideStackClearAllButton(View recentsView) {
        syncStackClearAllButton(recentsView, true);
    }

    private static void hideStackClearAllButtonView(
            LauncherRecentsState.PrepareRecentsViewState state,
            View clearAllButton,
            boolean enabled) {
        if (isStackClearAllButtonHidden(state, clearAllButton, enabled)) {
            return;
        }
        state.clearAllReady = true;
        state.clearAllEnabled = enabled;
        state.clearAllAllowed = false;
        state.clearAllButton = clearAllButton;
        LauncherRecentsCompat.invokeCompat(
                clearAllButton,
                "setVisibilityAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                0f);
        LauncherRecentsCompat.invokeCompat(clearAllButton, "updateAlphaPublic");
        clearAllButton.setEnabled(false);
        clearAllButton.setClickable(false);
        clearAllButton.setVisibility(View.INVISIBLE);
    }

    private static void releaseStackClearAllButton(
            LauncherRecentsState.PrepareRecentsViewState state,
            boolean showButton) {
        if (isStackClearAllButtonStateEmpty(state)) {
            return;
        }
        View clearAllButton = state.clearAllButton;
        if (clearAllButton != null) {
            LauncherRecentsCompat.invokeCompat(
                    clearAllButton,
                    "setVisibilityAlpha",
                    LauncherRecentsCompat.FLOAT_ARG,
                    1f);
            LauncherRecentsCompat.invokeCompat(clearAllButton, "updateAlphaPublic");
            clearAllButton.setEnabled(true);
            clearAllButton.setClickable(true);
            if (showButton) {
                clearAllButton.setVisibility(View.VISIBLE);
            }
        }
        clearStackClearAllButtonState(state);
    }

    private static boolean isStackClearAllButtonShown(
            LauncherRecentsState.PrepareRecentsViewState state,
            View clearAllButton) {
        return state.clearAllReady
                && state.clearAllEnabled
                && state.clearAllAllowed
                && state.clearAllButton == clearAllButton
                && clearAllButton.getAlpha() >= 0.99f
                && clearAllButton.getVisibility() == View.VISIBLE
                && clearAllButton.isEnabled()
                && clearAllButton.isClickable();
    }

    private static boolean isStackClearAllButtonHidden(
            LauncherRecentsState.PrepareRecentsViewState state,
            View clearAllButton,
            boolean enabled) {
        return state.clearAllReady
                && state.clearAllEnabled == enabled
                && !state.clearAllAllowed
                && state.clearAllButton == clearAllButton
                && clearAllButton.getVisibility() == View.INVISIBLE
                && !clearAllButton.isEnabled()
                && !clearAllButton.isClickable();
    }

    private static boolean isStackClearAllButtonStateEmpty(
            LauncherRecentsState.PrepareRecentsViewState state) {
        return !state.clearAllReady
                && !state.clearAllEnabled
                && !state.clearAllAllowed
                && state.clearAllButton == null;
    }

    private static void clearStackClearAllButtonState(
            LauncherRecentsState.PrepareRecentsViewState state) {
        state.clearAllReady = false;
        state.clearAllEnabled = false;
        state.clearAllAllowed = false;
        state.clearAllButton = null;
    }

    private static boolean isPrimaryScrollHorizontal(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryValue",
                new Class<?>[]{int.class, int.class},
                1,
                0);
        return !(value instanceof Integer) || (Integer) value == 1;
    }

    private static int resolvePrimaryScroll(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                recentsView);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return isPrimaryScrollHorizontal(recentsView)
                ? recentsView.getScrollX()
                : recentsView.getScrollY();
    }

    private static boolean isSeascapeOrientation(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getRotation",
                LauncherRecentsCompat.NO_ARGS);
        return value instanceof Integer && (Integer) value == 3;
    }

    private static float resolvePrimarySize(View view, boolean primaryScrollHorizontal) {
        return primaryScrollHorizontal ? view.getWidth() : view.getHeight();
    }

    private static float resolveTaskPrimarySize(
            View taskView,
            float fallback,
            boolean primaryScrollHorizontal) {
        float size = resolvePrimarySize(taskView, primaryScrollHorizontal);
        return size > 0f ? size : fallback;
    }

    private static float resolveTaskCenteredPrimaryStartPx(
            View recentsView,
            float taskPrimarySize,
            boolean primaryScrollHorizontal) {
        return Math.max(
                0f,
                (resolvePrimarySize(recentsView, primaryScrollHorizontal) - taskPrimarySize)
                        * 0.5f);
    }

    private static float readTaskPrimaryOffset(View taskView, boolean primaryScrollHorizontal) {
        return primaryScrollHorizontal
                ? LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView)
                : LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView);
    }

    private static float readTaskPrimaryOffsetField(View taskView, boolean primaryScrollHorizontal) {
        return LauncherRecentsCompat.readFloatField(
                taskView,
                primaryScrollHorizontal ? "taskOffsetTranslationX" : "taskOffsetTranslationY",
                0f);
    }

    private static float readTaskPrimaryDismissTranslation(
            View taskView,
            boolean primaryScrollHorizontal) {
        return LauncherRecentsCompat.readFloatField(
                taskView,
                primaryScrollHorizontal ? "dismissTranslationX" : "dismissTranslationY",
                0f);
    }

    private static float readTaskPrimaryHorizontalOffset(
            View taskView,
            boolean primaryScrollHorizontal) {
        return primaryScrollHorizontal
                ? LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView)
                : 0f;
    }

    private static float readTaskPrimaryHorizontalOffsetField(
            View taskView,
            boolean primaryScrollHorizontal) {
        return primaryScrollHorizontal
                ? LauncherRecentsCompat.readFloatField(
                taskView,
                "horizontalOffsetTranslationX",
                0f)
                : 0f;
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

    private static boolean shouldCaptureStockTaskStatesForStackApply(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)) {
            return !LauncherRecentsState.isOverviewStateStackBaselineCaptured(recentsView);
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        Integer lastTaskViewCount =
                LauncherRecentsState.LAST_STACK_STOCK_CAPTURE_TASK_COUNTS.get(recentsView);
        return lastTaskViewCount == null || lastTaskViewCount != taskViewCount;
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
                STACK_STABLE_VISIBLE_RADIUS,
                false);
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
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
            float dismissTranslationPrimary = readTaskPrimaryDismissTranslation(
                    taskView,
                    primaryScrollHorizontal);
            float visibleOffset =
                    rawOffset
                            + dismissTranslationPrimary
                            + readTaskPrimaryOffsetField(taskView, primaryScrollHorizontal)
                            + readTaskPrimaryHorizontalOffsetField(
                            taskView,
                            primaryScrollHorizontal);
            float taskPrimarySize = resolveTaskPrimarySize(
                    taskView,
                    Math.max(1f, resolvePrimarySize(recentsView, primaryScrollHorizontal)),
                    primaryScrollHorizontal);
            float taskCenteredPrimaryStartPx = resolveTaskCenteredPrimaryStartPx(
                    recentsView,
                    taskPrimarySize,
                    primaryScrollHorizontal);
            float taskScale = LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f);
            if (taskView.getVisibility() != View.VISIBLE
                    || taskView.getWidth() <= 0
                    || taskView.getHeight() <= 0
                    || !isTaskVisibleInViewport(
                    recentsView,
                    taskCenteredPrimaryStartPx,
                    taskPrimarySize,
                    visibleOffset,
                    taskScale,
                    primaryScrollHorizontal)) {
                continue;
            }
            LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.put(
                    taskView,
                    new LauncherRecentsState.BlankTapHomeExitTaskState(
                            rawOffset,
                            dismissTranslationPrimary,
                            visibleOffset,
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "horizontalOffsetTranslationX",
                                    0f),
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "taskOffsetTranslationX",
                                    0f),
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "taskOffsetTranslationY",
                                    0f),
                            taskScale,
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "boxTranslationY",
                            LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(
                                            taskView)),
                            LauncherRecentsTaskVisuals.readAttachAlpha(taskView),
                            LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                            LauncherRecentsTaskVisuals.readActivityTitleAlpha(taskView),
                            LauncherRecentsTaskVisuals.readStackContentBlurProgress(taskView),
                            LauncherRecentsCompat.readFloatField(
                                    taskView,
                                    "fullscreenProgress",
                                    0f),
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

    static void captureBlankTapHomeExitRecentsState(View recentsView) {
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_RECENTS_STATES.clear();
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_RECENTS_STATES.put(
                recentsView,
                new LauncherRecentsState.BlankTapHomeExitRecentsState(
                        recentsView.getTranslationX(),
                        recentsView.getTranslationY()));
    }

    static void applyBlankTapHomeExitRecentsFrame(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BlankTapHomeExitRecentsState state =
                LauncherRecentsState.BLANK_TAP_HOME_EXIT_RECENTS_STATES.get(recentsView);
        if (state == null) {
            return;
        }
        float pathProgress = smoothStep(clamp(progress, 0f, 1f));
        recentsView.setTranslationX(lerp(state.startTranslationX, 0f, pathProgress));
        recentsView.setTranslationY(lerp(state.startTranslationY, 0f, pathProgress));
    }

    static void applyBlankTapHomeExitFrame(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        float clampedProgress = clamp(progress, 0f, 1f);
        applyBlankTapHomeExitRecentsFrame(recentsView, clampedProgress);
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        float pathProgress = smoothStep(clampedProgress);
        float fallbackWidth = Math.max(1f, recentsView.getWidth());
        float fallbackHeight = Math.max(1f, recentsView.getHeight());
        float exitMarginPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 64);
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            LauncherRecentsState.BlankTapHomeExitTaskState state =
                    LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView);
            if (state == null) {
                hideLightStackTask(taskView);
                if (LauncherRecentsState.STACK_CONTENT_TARGETS.containsKey(taskView)) {
                    LauncherRecentsTaskVisuals.clearStackContentBlurIfApplied(taskView);
                }
                continue;
            }
            float taskWidth = taskView.getWidth() > 0
                    ? taskView.getWidth()
                    : fallbackWidth;
            float taskHeight = taskView.getHeight() > 0
                    ? taskView.getHeight()
                    : fallbackHeight;
            float taskPrimarySize = primaryScrollHorizontal ? taskWidth : taskHeight;
            float taskCenteredPrimaryStartPx = resolveTaskCenteredPrimaryStartPx(
                    recentsView,
                    taskPrimarySize,
                    primaryScrollHorizontal);
            float desiredVisibleOffset = state.startVisibleOffset;
            float desiredScale = state.startScale;
            float desiredStableAlpha = state.startStableAlpha;
            float desiredAttachAlpha = state.startAttachAlpha;
            float exitAlpha = resolveBlankTapExitAlpha(pathProgress, state.centerVisibleOffset);
            if (state.startStableAlpha > 0f) {
                float controlVisibleOffset = state.centerVisibleOffset;
                float taskStartPx = taskCenteredPrimaryStartPx + controlVisibleOffset;
                float exitTravelPx = Math.max(
                        taskStartPx + taskPrimarySize + exitMarginPx,
                        taskPrimarySize * (1f + BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO));
                float exitVisibleOffset = controlVisibleOffset - exitTravelPx;
                desiredVisibleOffset = quadraticBezier(
                        state.startVisibleOffset,
                        controlVisibleOffset,
                        exitVisibleOffset,
                        pathProgress);
                desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * pathProgress);
                desiredStableAlpha *= exitAlpha;
                desiredAttachAlpha *= exitAlpha;
            } else {
                desiredStableAlpha = 0f;
                desiredAttachAlpha = 0f;
            }
            float taskOffsetPrimary =
                    desiredVisibleOffset - state.startRawOffset - state.startDismissTranslationX;
            float horizontalOffsetX = lerp(state.startHorizontalOffsetX, 0f, pathProgress);
            float taskOffsetX = primaryScrollHorizontal
                    ? taskOffsetPrimary - horizontalOffsetX
                    : lerp(state.startTaskOffsetX, 0f, pathProgress);
            float taskOffsetY = primaryScrollHorizontal ? state.startTaskOffsetY : taskOffsetPrimary;
            LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                    taskView,
                    new LauncherRecentsTaskVisuals.StackTaskVisualState(
                            taskWidth * 0.5f,
                            taskHeight * 0.5f,
                            horizontalOffsetX,
                            taskOffsetX,
                            taskOffsetY,
                            state.startBoxTranslationY,
                            desiredScale,
                            desiredAttachAlpha,
                            desiredStableAlpha,
                            state.startActivityTitleAlpha * exitAlpha,
                            state.startStackContentBlurProgress,
                            state.startFullscreenProgress,
                            state.startTranslationZ,
                            true,
                            false));
        }
    }

    static void clearBlankTapHomeExitPendingLayout(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        recentsView.invalidate();
    }

    static void captureGestureStackReleaseTaskStates(
            View recentsView,
            int startScroll,
            int targetScroll) {
        captureGestureStackReleaseTaskStates(recentsView, startScroll, targetScroll, null);
    }

    static void captureGestureStackReleaseTaskStates(
            View recentsView,
            int startScroll,
            int targetScroll,
            HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> startVisualStates) {
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
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        if (!primaryScrollHorizontal) {
            HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> targetVisualStates =
                    computeGestureReleaseTargetVisualStates(recentsView, targetScroll);
            if (!targetVisualStates.isEmpty()) {
                for (View taskView : targetVisualStates.keySet()) {
                    if (taskView == null
                            || LauncherRecentsCompat.isDesktopTask(taskView)
                            || (taskView != runningTaskView
                            && sharesRunningTaskIds(taskView, runningTaskView))) {
                        continue;
                    }
                    LauncherRecentsTaskVisuals.StackTaskVisualState startVisualState =
                            startVisualStates != null ? startVisualStates.get(taskView) : null;
                    if (startVisualState == null) {
                        startVisualState = captureCurrentStackTaskVisualState(taskView);
                    }
                    LauncherRecentsTaskVisuals.StackTaskVisualState targetVisualState =
                            targetVisualStates.get(taskView);
                    targetVisualState = compensateGestureReleaseTargetStateForFrozenScroll(
                            targetVisualState,
                            startScroll,
                            targetScroll,
                            primaryScrollHorizontal);
                    if (taskView == runningTaskView) {
                        startVisualState = ensureVisibleGestureReleaseStartState(
                                startVisualState,
                                targetVisualState);
                    }
                    if (startVisualState != null && targetVisualState != null) {
                        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.put(
                                taskView,
                                new LauncherRecentsState.GestureReleaseTaskState(
                                        startVisualState,
                                        targetVisualState));
                    }
                }
                return;
            }
        }
        float pageSpacing = LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0);
        float referencePrimarySize = 0f;
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
            float taskPrimarySize = resolvePrimarySize(taskView, primaryScrollHorizontal);
            if (taskPrimarySize > 0) {
                referencePrimarySize = Math.max(referencePrimarySize, taskPrimarySize);
                pageSpan = Math.max(pageSpan, taskPrimarySize + pageSpacing);
            }
        }
        if (referencePrimarySize <= 0f) {
            referencePrimarySize = Math.max(
                    1f,
                    resolvePrimarySize(recentsView, primaryScrollHorizontal));
        }
        if (pageSpan <= 1f) {
            pageSpan = referencePrimarySize + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referencePrimarySize);
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
            float taskPrimarySize = resolveTaskPrimarySize(
                    taskView,
                    referencePrimarySize,
                    primaryScrollHorizontal);
            float taskCenteredPrimaryStartPx = resolveTaskCenteredPrimaryStartPx(
                    recentsView,
                    taskPrimarySize,
                    primaryScrollHorizontal);
            float startRawOffset = rawOffsets[i];
            float targetRawOffset = startRawOffset + scrollDelta;
            float targetProgress = targetRawOffset / pageSpan;
            float targetVisibleOffset = resolveStackVisibleOffset(
                    recentsView,
                    resolveStackReleaseSettledProgress(targetProgress, 1f),
                    taskPrimarySize,
                    taskCenteredPrimaryStartPx,
                    targetLeftEdgeRevealProgress,
                    primaryScrollHorizontal);
            float startVisibleOffset =
                    startRawOffset
                            + readTaskPrimaryDismissTranslation(
                            taskView,
                            primaryScrollHorizontal)
                            + readTaskPrimaryOffset(taskView, primaryScrollHorizontal)
                            + readTaskPrimaryHorizontalOffset(taskView, primaryScrollHorizontal);
            float startHorizontalOffsetX = readTaskPrimaryHorizontalOffset(
                    taskView,
                    primaryScrollHorizontal);
            LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.put(
                    taskView,
                    new LauncherRecentsState.GestureReleaseTaskState(
                            startVisibleOffset,
                            targetVisibleOffset,
                            startHorizontalOffsetX));
        }
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState
            compensateGestureReleaseTargetStateForFrozenScroll(
            LauncherRecentsTaskVisuals.StackTaskVisualState state,
            int startScroll,
            int targetScroll,
            boolean primaryScrollHorizontal) {
        if (state == null || startScroll == targetScroll) {
            return state;
        }
        float scrollDelta = targetScroll - startScroll;
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                state.pivotX,
                state.pivotY,
                state.horizontalOffsetX,
                primaryScrollHorizontal ? state.taskOffsetX - scrollDelta : state.taskOffsetX,
                primaryScrollHorizontal ? state.taskOffsetY : state.taskOffsetY - scrollDelta,
                state.boxTranslationY,
                state.scale,
                state.attachAlpha,
                state.stableAlpha,
                state.activityTitleAlpha,
                state.blurProgress,
                state.fullscreenProgress,
                state.translationZ,
                state.stackContentBlurEnabled,
                state.clearShadow);
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState
            ensureVisibleGestureReleaseStartState(
            LauncherRecentsTaskVisuals.StackTaskVisualState startState,
            LauncherRecentsTaskVisuals.StackTaskVisualState targetState) {
        if (startState == null || targetState == null) {
            return startState;
        }
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                startState.pivotX,
                startState.pivotY,
                startState.horizontalOffsetX,
                startState.taskOffsetX,
                startState.taskOffsetY,
                startState.boxTranslationY,
                startState.scale,
                Math.max(startState.attachAlpha, targetState.attachAlpha),
                Math.max(startState.stableAlpha, targetState.stableAlpha),
                Math.max(startState.activityTitleAlpha, targetState.activityTitleAlpha),
                startState.blurProgress,
                startState.fullscreenProgress,
                startState.translationZ,
                startState.stackContentBlurEnabled,
                startState.clearShadow);
    }

    static HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState>
            captureCurrentStackTaskVisualStates(View recentsView) {
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> states = new HashMap<>();
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            states.put(taskView, captureCurrentStackTaskVisualState(taskView));
        }
        return states;
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState
            captureCurrentStackTaskVisualState(View taskView) {
        if (taskView == null) {
            return null;
        }
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                taskView.getPivotX(),
                taskView.getPivotY(),
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView)),
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                LauncherRecentsTaskVisuals.readAttachAlpha(taskView),
                LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                LauncherRecentsTaskVisuals.readActivityTitleAlpha(taskView),
                LauncherRecentsTaskVisuals.readStackContentBlurProgress(taskView),
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                taskView.getTranslationZ(),
                true,
                true);
    }

    private static HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState>
            computeGestureReleaseTargetVisualStates(View recentsView, int targetScroll) {
        boolean wasSettled = LauncherRecentsState.isAppToRecentsStackSettled(recentsView);
        LauncherRecentsState.setAppToRecentsStackSettled(recentsView, true);
        try {
            return computeStackLayout(
                    recentsView,
                    STACK_STABLE_VISIBLE_RADIUS,
                    null,
                    targetScroll);
        } finally {
            LauncherRecentsState.setAppToRecentsStackSettled(recentsView, wasSettled);
        }
    }

    static boolean applyStackLayout(View recentsView, boolean captureStockState, String source) {
        return applyStackLayout(recentsView, captureStockState, source, true);
    }

    static boolean applyStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData) {
        return applyStackLayout(
                recentsView,
                captureStockState,
                resolveStackLayoutRadius(recentsView),
                source,
                syncVisibleTaskData);
    }

    static boolean applyStableStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData) {
        return applyStackLayout(
                recentsView,
                captureStockState,
                STACK_STABLE_VISIBLE_RADIUS,
                source,
                syncVisibleTaskData);
    }

    static void prepareStackDismissRelayoutCapture(View recentsView) {
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (taskViewCount <= 0) {
            return;
        }
        prepareRecentsView(recentsView);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        applyStackLayout(
                recentsView,
                false,
                Math.max(taskViewCount, STACK_STABLE_VISIBLE_RADIUS),
                "dismissRelayoutPreCapture",
                false);
    }

    static boolean requestStackLayout(
            View recentsView,
            String source,
            boolean captureStockState,
            boolean syncVisibleTaskData) {
        if (recentsView == null || !shouldApplyDynamicStackLayout(recentsView)) {
            return false;
        }
        return scheduleStackLayout(
                recentsView,
                captureStockState,
                source,
                syncVisibleTaskData,
                true);
    }

    private static boolean applyDynamicStackLayoutImmediatelyForScroll(View recentsView) {
        if (recentsView == null || !shouldApplyDynamicStackLayout(recentsView)) {
            return false;
        }
        if (shouldBlockStackDismissLayout(recentsView, "onScrollChangedSync")) {
            LauncherRecentsPerf.flow("layout:scrollSync:skipDismiss", recentsView);
            return false;
        }
        if (shouldCaptureStockTaskStatesForStackApply(recentsView)) {
            captureStockTaskStatesForStackApply(recentsView);
        }
        boolean layoutApplied = applyStackLayout(
                recentsView,
                false,
                "onScrollChangedSync",
                false);
        if (layoutApplied) {
            recentsView.invalidate();
        }
        return layoutApplied;
    }

    static boolean applyDynamicStackLayoutIfNeeded(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        if (shouldBlockStackDismissLayout(recentsView, "applyDynamic")) {
            LauncherRecentsPerf.flow("layout:dynamic:skipDismiss", recentsView);
            return false;
        }
        LauncherRecentsPerf.flow("layout:dynamic:start", recentsView);
        LauncherRecentsPerf.hit("animationFrame:applyDynamic", recentsView);
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (!shouldApplyDynamicStackLayout(recentsView)) {
            LauncherRecentsPerf.flow("layout:dynamic:skip", recentsView);
            return false;
        }
        boolean captureStockState = shouldCaptureStockTaskStatesForStackApply(recentsView);
        if (captureStockState) {
            LauncherRecentsPerf.hit("animationFrame:applyDynamicCapture", recentsView);
        }
        boolean scheduled = scheduleStackLayout(
                recentsView,
                captureStockState,
                "applyDynamic",
                LauncherRecentsState.isAppToRecentsStackSettled(recentsView),
                true);
        LauncherRecentsPerf.flow(scheduled
                ? "layout:dynamic:scheduled"
                : "layout:dynamic:skipSchedule", recentsView);
        return scheduled;
    }

    private static boolean scheduleStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData,
            boolean dynamicOnly) {
        return scheduleStackLayout(
                recentsView,
                captureStockState,
                source,
                syncVisibleTaskData,
                dynamicOnly,
                false);
    }

    private static boolean scheduleStackLayoutBeforeDraw(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData,
            boolean dynamicOnly) {
        return scheduleStackLayout(
                recentsView,
                captureStockState,
                source,
                syncVisibleTaskData,
                dynamicOnly,
                true);
    }

    private static boolean scheduleStackLayout(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData,
            boolean dynamicOnly,
            boolean beforeDraw) {
        if (recentsView == null) {
            return false;
        }
        if (shouldBlockStackDismissLayout(recentsView, source)) {
            LauncherRecentsPerf.flow("layout:schedule:skipDismiss",
                    recentsView, "source=" + source);
            return false;
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
            LauncherRecentsPerf.flow("layout:schedule:skipSwipeUp",
                    recentsView, "source=" + source);
            return false;
        }
        if (LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)) {
            LauncherRecentsPerf.flow("layout:schedule:skipOverviewPreReleaseStock",
                    recentsView, "source=" + source);
            return false;
        }
        LauncherRecentsState.PendingStackLayoutApplyState pendingState =
                LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.get(recentsView);
        if (pendingState != null) {
            pendingState.captureStockState |= captureStockState;
            pendingState.syncVisibleTaskData |= syncVisibleTaskData;
            pendingState.dynamicOnly &= dynamicOnly;
            pendingState.source = mergeScheduledStackLayoutSource(pendingState.source, source);
            if (beforeDraw && !pendingState.preDrawScheduled) {
                pendingState.preDrawScheduled = true;
                postStackLayoutBeforeDraw(recentsView);
            }
            LauncherRecentsPerf.flow("layout:schedule:merge",
                    recentsView,
                    "source=" + pendingState.source
                            + " capture=" + pendingState.captureStockState
                            + " syncVisible=" + pendingState.syncVisibleTaskData
                            + " dynamicOnly=" + pendingState.dynamicOnly);
            return true;
        }
        LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.put(
                recentsView,
                new LauncherRecentsState.PendingStackLayoutApplyState(
                        captureStockState,
                        syncVisibleTaskData,
                        dynamicOnly,
                        source));
        if (beforeDraw) {
            LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.get(recentsView)
                    .preDrawScheduled = true;
        }
        LauncherRecentsPerf.flow("layout:schedule",
                recentsView,
                "source=" + source
                        + " capture=" + captureStockState
                        + " syncVisible=" + syncVisibleTaskData
                        + " dynamicOnly=" + dynamicOnly);
        if (beforeDraw) {
            postStackLayoutBeforeDraw(recentsView);
        } else {
            recentsView.postOnAnimation(() -> runScheduledStackLayout(recentsView));
        }
        return true;
    }

    private static void postStackLayoutBeforeDraw(View recentsView) {
        ViewTreeObserver observer = recentsView.getViewTreeObserver();
        if (!observer.isAlive()) {
            recentsView.postOnAnimation(() -> runScheduledStackLayout(recentsView));
            return;
        }
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver currentObserver = recentsView.getViewTreeObserver();
                if (currentObserver.isAlive()) {
                    currentObserver.removeOnPreDrawListener(this);
                }
                runScheduledStackLayout(recentsView);
                return true;
            }
        });
        recentsView.invalidate();
    }

    private static void scheduleStackLayoutFromHook(
            View recentsView,
            boolean captureStockState,
            String source,
            boolean syncVisibleTaskData,
            boolean dynamicOnly) {
        if (!shouldApplyDynamicStackLayout(recentsView)) {
            return;
        }
        if (isAppToRecentsEntryInProgress(recentsView)) {
            return;
        }
        scheduleStackLayout(
                recentsView,
                captureStockState,
                source,
                syncVisibleTaskData,
                dynamicOnly);
    }

    private static void applyStackLayoutFromScaleSuppress(View recentsView, String source) {
        if (!shouldApplyDynamicStackLayout(recentsView)
                || shouldBlockAppToRecentsStackApply(recentsView)) {
            return;
        }
        applyStackLayout(
                recentsView,
                false,
                source,
                false);
        recentsView.invalidate();
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

    private static boolean shouldDelayScheduledStackLayoutForHomeExit(
            View recentsView,
            String source) {
        return LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                && !"blankTapPrepare".equals(source)
                && !"contentAlpha_blankExit".equals(source);
    }

    private static boolean shouldBlockStackDismissLayout(View recentsView, String source) {
        return LauncherRecentsTouchController.isStackDismissInteractionActive(recentsView)
                && !isStackDismissOwnedLayoutSource(source);
    }

    private static boolean isStackDismissOwnedLayoutSource(String source) {
        return "dismissEntryTakeover".equals(source)
                || "dismissRelayoutPreCapture".equals(source)
                || "dismissRelayoutEnd".equals(source)
                || "dismissRelayoutNoStart".equals(source)
                || "dismissRelayoutNoTarget".equals(source);
    }

    private static void runScheduledStackLayout(View recentsView) {
        LauncherRecentsState.PendingStackLayoutApplyState pendingState =
                LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.remove(recentsView);
        if (recentsView == null || pendingState == null) {
            return;
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
            LauncherRecentsPerf.flow("layout:runScheduled:skipSwipeUp",
                    recentsView, "source=" + pendingState.source);
            return;
        }
        if (LauncherRecentsState.hasActiveTaskLaunchTransitionGeometry(recentsView)) {
            LauncherRecentsPerf.flow("layout:runScheduled:skipTaskLaunch",
                    recentsView, "source=" + pendingState.source);
            return;
        }
        if (shouldBlockStackDismissLayout(recentsView, pendingState.source)) {
            LauncherRecentsPerf.flow("layout:runScheduled:skipDismiss",
                    recentsView, "source=" + pendingState.source);
            return;
        }
        if (shouldDelayScheduledStackLayoutForHomeExit(recentsView, pendingState.source)) {
            LauncherRecentsState.PENDING_STACK_LAYOUT_APPLIES.put(recentsView, pendingState);
            LauncherRecentsPerf.flow("layout:runScheduled:delayHomeExit",
                    recentsView, "source=" + pendingState.source);
            recentsView.postDelayed(() -> runScheduledStackLayout(recentsView), 32L);
            return;
        }
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        if (shouldBlockAppToRecentsStackApply(recentsView)) {
            LauncherRecentsPerf.flow("layout:runScheduled:blocked",
                    recentsView, "source=" + pendingState.source);
            return;
        }
        if (pendingState.dynamicOnly && !shouldApplyDynamicStackLayout(recentsView)) {
            LauncherRecentsPerf.flow("layout:runScheduled:skipDynamic",
                    recentsView, "source=" + pendingState.source);
            return;
        }
        if (shouldCaptureStockTaskStatesForStackApply(recentsView)) {
            captureStockTaskStatesForStackApply(recentsView);
        }
        boolean layoutApplied = applyStackLayout(
                recentsView,
                false,
                pendingState.source != null ? pendingState.source : "scheduled",
                pendingState.syncVisibleTaskData);
        LauncherRecentsPerf.flow("layout:runScheduled:applied",
                recentsView, "source=" + pendingState.source);
        if (layoutApplied) {
            recentsView.invalidate();
        }
    }

    private static boolean applyStackLayout(
            View recentsView,
            boolean captureStockState,
            int stackLayoutRadius,
            String source,
            boolean syncVisibleTaskData) {
        if (recentsView == null) {
            return false;
        }
        if (shouldBlockStackDismissLayout(recentsView, source)) {
            LauncherRecentsPerf.flow("layout:apply:skipDismiss",
                    recentsView, "source=" + source);
            return false;
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
            LauncherRecentsPerf.flow("layout:apply:skipSwipeUp",
                    recentsView, "source=" + source);
            return false;
        }
        if (LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)) {
            LauncherRecentsPerf.flow("layout:apply:skipOverviewPreReleaseStock",
                    recentsView, "source=" + source);
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (shouldSkipDuplicateStackLayout(
                recentsView,
                taskViewCount,
                stackLayoutRadius,
                source,
                syncVisibleTaskData,
                config)) {
            LauncherRecentsPerf.hit("skipDuplicate:" + source, recentsView);
            LauncherRecentsPerf.flow("layout:apply:skipDuplicate",
                    recentsView,
                    "source=" + source
                            + " radius=" + stackLayoutRadius
                            + " syncVisible=" + syncVisibleTaskData);
            return false;
        }
        boolean logApply = !"updatePageScales_scaleSuppress".equals(source);
        if (logApply) {
            LauncherRecentsPerf.flow("layout:apply:start",
                    recentsView,
                    "source=" + source
                            + " radius=" + stackLayoutRadius
                            + " syncVisible=" + syncVisibleTaskData);
        }
        boolean layoutApplied = false;
        long totalStartNs = LauncherRecentsPerf.start(recentsView);
        long layoutStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            layoutApplied = applyStackLayoutMeasured(
                    recentsView,
                    captureStockState,
                    taskViewCount,
                    stackLayoutRadius,
                    config);
        } finally {
            long layoutCostNs = LauncherRecentsPerf.end("layoutCompute:" + source, layoutStartNs);
            reportSlowApplyDynamicLayout(recentsView, source, stackLayoutRadius, layoutCostNs);
        }
        if (syncVisibleTaskData
                && layoutApplied
                && shouldRunVisibleTaskDataSync(recentsView, source)) {
            long visibleDataStartNs = LauncherRecentsPerf.start(recentsView);
            try {
                LauncherRecentsTouchController.ensureStackVisibleTaskDataIfNeeded(recentsView, 15);
            } finally {
                LauncherRecentsPerf.end("visibleTaskDataSync:" + source, visibleDataStartNs);
            }
        }
        if (logApply) {
            LauncherRecentsPerf.flow("layout:apply:end",
                    recentsView,
                    "source=" + source
                            + " applied=" + layoutApplied
                            + " syncVisible=" + syncVisibleTaskData);
        }
        LauncherRecentsPerf.end("applyStackLayoutTotal:" + source, totalStartNs);
        return layoutApplied;
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
        if (recentsView == null) {
            return Integer.MIN_VALUE;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        int primarySize = Math.round(resolvePrimarySize(recentsView, primaryScrollHorizontal));
        if (primarySize <= 0) {
            return Integer.MIN_VALUE;
        }
        return resolvePrimaryScroll(recentsView)
                / Math.max(1, primarySize / STACK_SLOW_LOG_SCROLL_BUCKET_DIVISOR);
    }

    private static boolean shouldRunVisibleTaskDataSync(View recentsView, String source) {
        return !shouldCoalesceStackLayoutSource(source)
                || shouldSyncVisibleTaskDataForCurrentBucket(recentsView);
    }

    private static boolean shouldSyncVisibleTaskDataForCurrentBucket(View recentsView) {
        LauncherRecentsState.PrepareRecentsViewState state =
                prepareRecentsViewState(recentsView);
        int page = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        int bucket = resolveSlowLogScrollBucket(recentsView);
        if (state.visibleTaskDataPage == page && state.visibleTaskDataBucket == bucket) {
            return false;
        }
        state.visibleTaskDataPage = page;
        state.visibleTaskDataBucket = bucket;
        return true;
    }

    private static boolean shouldSkipDuplicateStackLayout(
            View recentsView,
            int taskViewCount,
            int stackLayoutRadius,
            String source,
            boolean syncVisibleTaskData,
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config) {
        if (recentsView == null || !shouldCoalesceStackLayoutSource(source)) {
            return false;
        }
        if (shouldOwnStackTaskAlpha(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackSettled(recentsView)) {
            return false;
        }
        long key = resolveStackLayoutApplyKey(recentsView, taskViewCount, stackLayoutRadius, config);
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
                || "overviewStateClearRestore".equals(source)
                || "contentAlpha_blankExit".equals(source)
                || "contentAlpha_after".equals(source)
                || "updatePageScales_after".equals(source)
                || "updatePageScales_blankExitSuppress".equals(source)
                || "updatePageOffsetsForFlyme_blankExitSuppress".equals(source)
                || "updatePageOffsetsForFlyme_offsetSuppress".equals(source)
                || "updatePageOffsetsForFlyme_after".equals(source)
                || "updatePageScales_scaleSuppress".equals(source);
    }

    private static long resolveStackLayoutApplyKey(
            View recentsView,
            int taskViewCount,
            int stackLayoutRadius,
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config) {
        long key = 17L;
        key = mixStackLayoutApplyKey(key, stackLayoutRadius);
        key = mixStackLayoutApplyKey(key, recentsView.getScrollX());
        key = mixStackLayoutApplyKey(key, recentsView.getScrollY());
        key = mixStackLayoutApplyKey(key, recentsView.getWidth());
        key = mixStackLayoutApplyKey(key, recentsView.getHeight());
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0));
        Integer fixedAnchorPage = LauncherRecentsState.getStackScrollFixedAnchorPage(recentsView);
        key = mixStackLayoutApplyKey(key, fixedAnchorPage != null ? fixedAnchorPage : -1);
        key = mixStackLayoutApplyKey(
                key,
                taskViewCount);
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
                LauncherRecentsState.isAppToRecentsStackSettled(recentsView) ? 1 : 0);
        key = mixStackLayoutApplyKey(
                key,
                LauncherRecentsState.isOverviewStateStackSettled(recentsView) ? 1 : 0);
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
            int taskViewCount,
            int stackLayoutRadius,
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config) {
        if (recentsView == null) {
            return false;
        }
        LauncherRecentsState.LaunchTransitionGeometryState launchState =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (launchState != null && launchState.frozen) {
            LauncherRecentsLaunchController.applyFrozenTaskLaunchLayout(recentsView);
            return false;
        }
        if (shouldBlockAppToRecentsStackApply(recentsView)) {
            return false;
        }
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            LauncherRecentsState.LAST_STACK_LAYOUT_ACTIVE_INDICES.remove(recentsView);
            restoreTaskTransforms(recentsView, taskViewCount);
            return false;
        }

        boolean blankTapExitActive =
                LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
        if (blankTapExitActive) {
            applyBlankTapHomeExitRecentsFrame(
                    recentsView,
                    LauncherRecentsTransitionController.readBlankTapHomeExitProgress(recentsView));
        }
        ComputedStackLayout layout = computeStackLayoutInternal(
                recentsView,
                taskViewCount,
                stackLayoutRadius,
                config,
                null,
                null);
        LauncherRecentsState.LAST_STACK_LAYOUT_ACTIVE_INDICES.put(
                recentsView,
                new ArrayList<>(layout.activeIndices));

        for (int index = 0; index < layout.processIndices.size(); index++) {
            int i = layout.processIndices.get(index);
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (LauncherRecentsCompat.isDesktopTask(taskView)) {
                restoreTaskTransform(taskView);
                continue;
            }
            if (taskView != layout.runningTaskView
                    && sharesTaskIds(taskView, layout.runningTaskIds)) {
                restoreTaskTransform(taskView);
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.setTaskHeadContentAlpha(taskView, 0f);
                LauncherRecentsTaskVisuals.clearStackContentBlurIfApplied(taskView);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            if (!layout.activeIndices.contains(i)) {
                hideLightStackTask(taskView);
                continue;
            }
            if (layout.appEntrySessionActive && taskView == layout.runningTaskView) {
                LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 1f);
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 1f);
                LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
                continue;
            }
            LauncherRecentsTaskVisuals.StackTaskVisualState visualState =
                    layout.visualStates.get(taskView);
            if (visualState == null) {
                continue;
            }
            if (captureStockState) {
                LauncherRecentsTaskVisuals.captureStockTaskState(taskView);
            }
            if (layout.coreOnlyTaskViews.contains(taskView)) {
                LauncherRecentsTaskVisuals.applyStackTaskCoreVisualState(taskView, visualState);
            } else {
                LauncherRecentsTaskVisuals.applyStackTaskVisualState(taskView, visualState);
            }
        }
        return true;
    }

    static HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> computeStackLayout(
            View recentsView,
            View excludedTaskView,
            Integer targetScroll) {
        if (recentsView == null) {
            return new HashMap<>();
        }
        return computeStackLayout(
                recentsView,
                resolveStackLayoutRadius(recentsView),
                excludedTaskView,
                targetScroll);
    }

    static HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> computeStackLayout(
            View recentsView,
            int stackLayoutRadius,
            View excludedTaskView,
            Integer targetScroll) {
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> states = new HashMap<>();
        if (recentsView == null) {
            return states;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = resolveProjectedTaskViewCount(recentsView, excludedTaskView);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            return states;
        }
        return computeStackLayoutInternal(
                recentsView,
                taskViewCount,
                stackLayoutRadius,
                config,
                excludedTaskView,
                targetScroll).visualStates;
    }

    private static ComputedStackLayout computeStackLayoutInternal(
            View recentsView,
            int taskViewCount,
            int stackLayoutRadius,
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config,
            View excludedTaskView,
            Integer targetScroll) {
        float pageSpacing = LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0);
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        View runningTaskView = runningTaskObject instanceof View
                && runningTaskObject != excludedTaskView
                ? (View) runningTaskObject
                : null;
        int runningTaskChildIndex = findProjectedTaskIndex(
                recentsView,
                runningTaskView,
                excludedTaskView);
        boolean gestureStackReleaseActive =
                LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                        recentsView);
        boolean overviewStateStackAnimationActive =
                LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                        recentsView);
        boolean appEntrySessionActive =
                LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
        boolean entryLightWindow = stackLayoutRadius == STACK_ENTRY_LIGHT_RADIUS
                && (gestureStackReleaseActive
                || overviewStateStackAnimationActive
                || appEntrySessionActive);
        boolean appEntryLightWindow = stackLayoutRadius == STACK_ENTRY_LIGHT_RADIUS
                && (gestureStackReleaseActive || appEntrySessionActive);
        boolean desktopOverviewEntryWindow = stackLayoutRadius == STACK_ENTRY_LIGHT_RADIUS
                && overviewStateStackAnimationActive
                && !gestureStackReleaseActive
                && !appEntrySessionActive
                && runningTaskChildIndex < 0;
        boolean stableFillWindow = LauncherRecentsState.isAppToRecentsStackSettled(recentsView);
        int fillBoundaryTargetCount = resolveStackLayoutFillBoundaryTargetCount(
                recentsView,
                taskViewCount,
                stackLayoutRadius,
                appEntryLightWindow);
        int lightAnchorIndex = resolveStackLayoutAnchorIndex(
                recentsView,
                runningTaskChildIndex,
                taskViewCount,
                stackLayoutRadius,
                entryLightWindow,
                targetScroll);
        if (desktopOverviewEntryWindow) {
            fillBoundaryTargetCount = Math.min(taskViewCount, 3);
            lightAnchorIndex = 0;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        boolean seascapeEntryWindow = entryLightWindow
                && !stableFillWindow
                && fillBoundaryTargetCount > 0
                && runningTaskChildIndex >= 0
                && !primaryScrollHorizontal
                && isSeascapeOrientation(recentsView);
        ArrayList<Integer> activeIndices = resolveStackLayoutActiveIndices(
                taskViewCount,
                lightAnchorIndex,
                stackLayoutRadius,
                fillBoundaryTargetCount,
                stableFillWindow,
                seascapeEntryWindow);
        addFixedStackScrollActiveIndices(
                recentsView,
                taskViewCount,
                stackLayoutRadius,
                fillBoundaryTargetCount,
                stableFillWindow,
                seascapeEntryWindow,
                entryLightWindow,
                targetScroll,
                activeIndices);
        ArrayList<Integer> processIndices = resolveStackLayoutProcessIndices(
                taskViewCount,
                activeIndices,
                LauncherRecentsState.LAST_STACK_LAYOUT_ACTIVE_INDICES.get(recentsView));
        ComputedStackLayout result = new ComputedStackLayout(
                activeIndices,
                processIndices,
                runningTaskView,
                appEntrySessionActive,
                resolveTaskIds(runningTaskView));
        int primaryScroll = targetScroll != null ? targetScroll : resolvePrimaryScroll(recentsView);
        float referenceWidth = 0f;
        float referenceHeight = 0f;
        float referencePrimarySize = 0f;
        float pageSpan = 0f;

        for (int index = 0; index < activeIndices.size(); index++) {
            View taskView = getProjectedTaskViewAt(
                    recentsView,
                    activeIndices.get(index),
                    excludedTaskView);
            if (taskView == null) {
                continue;
            }
            LauncherRecentsTaskVisuals.rememberOriginalTaskState(taskView);
            if (taskView.getWidth() > 0) {
                referenceWidth = Math.max(referenceWidth, taskView.getWidth());
            }
            if (taskView.getHeight() > 0) {
                referenceHeight = Math.max(referenceHeight, taskView.getHeight());
            }
            float taskPrimarySize = resolvePrimarySize(taskView, primaryScrollHorizontal);
            if (taskPrimarySize > 0f) {
                referencePrimarySize = Math.max(referencePrimarySize, taskPrimarySize);
                pageSpan = Math.max(pageSpan, taskPrimarySize + pageSpacing);
            }
        }

        if (referenceWidth <= 0f) {
            referenceWidth = Math.max(1, recentsView.getWidth());
        }
        if (referenceHeight <= 0f) {
            referenceHeight = Math.max(1, recentsView.getHeight());
        }
        if (referencePrimarySize <= 0f) {
            referencePrimarySize = Math.max(
                    1f,
                    resolvePrimarySize(recentsView, primaryScrollHorizontal));
        }
        if (pageSpan <= 1f) {
            pageSpan = referencePrimarySize + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referencePrimarySize);
        }

        float blankTapExitProgress =
                LauncherRecentsTransitionController.readBlankTapHomeExitProgress(recentsView);
        float stackEntryProgress = resolveStackEntryProgress(recentsView);
        float stackVerticalProgress = resolveStackVerticalProgress(recentsView);
        float gestureStackReleaseProgress =
                LauncherRecentsTransitionController.readGestureRecentsStackReleaseProgress(
                        recentsView);
        float overviewStateStackHandoffProgress = overviewStateStackAnimationActive
                ? smoothStep(resolveOverviewPeekToOverviewProgress(recentsView))
                : 1f;
        float stackReleaseProgress = gestureStackReleaseActive
                ? clamp(gestureStackReleaseProgress, 0f, 1f)
                : 1f;
        float stackSettledShiftProgress = LauncherRecentsState.isAppToRecentsStackSettled(
                recentsView)
                ? 1f
                : 0f;
        if (gestureStackReleaseActive) {
            stackEntryProgress = 1f;
            stackVerticalProgress = 1f;
            stackSettledShiftProgress = smoothStep(stackReleaseProgress);
        }
        StackLayoutContext layoutContext = new StackLayoutContext(
                recentsView,
                taskViewCount,
                runningTaskView,
                runningTaskChildIndex,
                referenceWidth,
                referenceHeight,
                referencePrimarySize,
                pageSpan,
                primaryScrollHorizontal,
                primaryScroll,
                resolveLeftEdgeRevealProgress(recentsView, primaryScroll),
                blankTapExitProgress,
                stackEntryProgress,
                stackVerticalProgress,
                gestureStackReleaseActive,
                overviewStateStackAnimationActive,
                desktopOverviewEntryWindow,
                overviewStateStackHandoffProgress,
                stackReleaseProgress,
                stackSettledShiftProgress,
                resolveEdgeScrollCorrection(recentsView, primaryScroll),
                appEntrySessionActive,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 24),
                FlymeStatusBarSizer.dp(recentsView.getContext(), 8),
                LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView),
                config.launcherIosStackRecentsBlurEnabled);

        for (int index = 0; index < processIndices.size(); index++) {
            int i = processIndices.get(index);
            View taskView = getProjectedTaskViewAt(recentsView, i, excludedTaskView);
            if (taskView == null
                    || LauncherRecentsCompat.isDesktopTask(taskView)
                    || (taskView != runningTaskView
                    && sharesTaskIds(taskView, result.runningTaskIds))
                    || !activeIndices.contains(i)
                    || (appEntrySessionActive && taskView == runningTaskView)) {
                continue;
            }
            boolean coreOnly = shouldApplyCoreOnlyDuringGestureRelease(
                    gestureStackReleaseActive,
                    i,
                    lightAnchorIndex);
            LauncherRecentsTaskVisuals.StackTaskVisualState visualState =
                    buildStackTaskVisualState(
                            layoutContext,
                            buildStackTaskInput(layoutContext, taskView, i),
                            coreOnly);
            if (coreOnly) {
                result.coreOnlyTaskViews.add(taskView);
            }
            result.visualStates.put(taskView, visualState);
        }
        return result;
    }

    private static boolean shouldApplyCoreOnlyDuringGestureRelease(
            boolean gestureStackReleaseActive,
            int index,
            int anchorIndex) {
        return gestureStackReleaseActive
                && Math.abs(index - anchorIndex) > STACK_GESTURE_RELEASE_CORE_RADIUS;
    }

    private static void addFixedStackScrollActiveIndices(
            View recentsView,
            int taskViewCount,
            int stackLayoutRadius,
            int fillBoundaryTargetCount,
            boolean stableFillWindow,
            boolean seascapeEntryWindow,
            boolean entryWindow,
            Integer targetScroll,
            ArrayList<Integer> activeIndices) {
        if (entryWindow
                || stackLayoutRadius != STACK_STABLE_VISIBLE_RADIUS
                || LauncherRecentsState.getStackScrollFixedAnchorPage(recentsView) == null) {
            return;
        }
        int fixedAnchorIndex = Math.max(
                0,
                Math.min(
                        LauncherRecentsState.getStackScrollFixedAnchorPage(recentsView),
                        Math.max(0, taskViewCount - 1)));
        ArrayList<Integer> fixedIndices = resolveStackLayoutActiveIndices(
                taskViewCount,
                fixedAnchorIndex,
                stackLayoutRadius,
                fillBoundaryTargetCount,
                stableFillWindow,
                seascapeEntryWindow);
        appendStackLayoutIndices(activeIndices, fixedIndices, taskViewCount);
    }

    private static ArrayList<Integer> resolveStackLayoutActiveIndices(
            int taskViewCount,
            int anchorIndex,
            int radius,
            int fillBoundaryTargetCount,
            boolean stableFillWindow,
            boolean seascapeEntryWindow) {
        ArrayList<Integer> indices = new ArrayList<>();
        if (taskViewCount <= 0 || radius < 0) {
            return indices;
        }
        anchorIndex = Math.max(0, Math.min(anchorIndex, taskViewCount - 1));
        if (fillBoundaryTargetCount > 0) {
            int targetCount = Math.min(taskViewCount, fillBoundaryTargetCount);
            if (seascapeEntryWindow) {
                for (int i = targetCount - 1; i >= 0; i--) {
                    appendStackLayoutIndex(indices, anchorIndex + i, taskViewCount);
                }
                return indices;
            }
            if (stableFillWindow) {
                appendStableStackLayoutIndices(indices, anchorIndex, taskViewCount, targetCount);
                return indices;
            }
            indices.add(anchorIndex);
            for (int i = 1; indices.size() < targetCount; i++) {
                appendStackLayoutIndex(indices, anchorIndex - i, taskViewCount);
                if (indices.size() >= targetCount) {
                    break;
                }
                appendStackLayoutIndex(indices, anchorIndex + i, taskViewCount);
            }
            return indices;
        }
        int start = Math.max(0, anchorIndex - radius);
        int end = Math.min(taskViewCount - 1, anchorIndex + radius);
        for (int i = start; i <= end; i++) {
            indices.add(i);
        }
        return indices;
    }

    private static void appendStableStackLayoutIndices(
            ArrayList<Integer> target,
            int anchorIndex,
            int taskViewCount,
            int targetCount) {
        appendStackLayoutIndex(target, anchorIndex, taskViewCount);
        appendStackLayoutIndex(target, anchorIndex - 1, taskViewCount);
        for (int i = 1; target.size() < targetCount; i++) {
            appendStackLayoutIndex(target, anchorIndex + i, taskViewCount);
            if (target.size() >= targetCount) {
                break;
            }
            if (i > 1) {
                appendStackLayoutIndex(target, anchorIndex - i, taskViewCount);
            }
        }
    }

    private static int resolveStackLayoutFillBoundaryTargetCount(
            View recentsView,
            int taskViewCount,
            int radius,
            boolean appEntryLightWindow) {
        if (taskViewCount <= 0) {
            return 0;
        }
        if (LauncherRecentsState.isAppToRecentsStackSettled(recentsView)) {
            return Math.min(taskViewCount, (radius * 2) + 2);
        }
        if (appEntryLightWindow) {
            return Math.min(taskViewCount, (radius * 2) + 1);
        }
        return 0;
    }

    private static void appendStackLayoutIndex(
            ArrayList<Integer> target,
            int index,
            int taskViewCount) {
        if (index >= 0 && index < taskViewCount && !target.contains(index)) {
            target.add(index);
        }
    }

    private static ArrayList<Integer> resolveStackLayoutProcessIndices(
            int taskViewCount,
            ArrayList<Integer> activeIndices,
            ArrayList<Integer> lastActiveIndices) {
        ArrayList<Integer> indices = new ArrayList<>();
        if (lastActiveIndices == null) {
            appendStackLayoutIndices(indices, activeIndices, taskViewCount);
            return indices;
        }
        appendStackLayoutIndices(indices, activeIndices, taskViewCount);
        appendStackLayoutIndices(indices, lastActiveIndices, taskViewCount);
        return indices;
    }

    private static void appendStackLayoutIndices(
            ArrayList<Integer> target,
            ArrayList<Integer> source,
            int taskViewCount) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            int index = source.get(i);
            if (index >= 0 && index < taskViewCount && !target.contains(index)) {
                target.add(index);
            }
        }
    }

    private static int resolveProjectedTaskViewCount(View recentsView, View excludedTaskView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (excludedTaskView == null) {
            return taskViewCount;
        }
        return findProjectedTaskIndex(recentsView, excludedTaskView, null) >= 0
                ? Math.max(0, taskViewCount - 1)
                : taskViewCount;
    }

    private static View getProjectedTaskViewAt(
            View recentsView,
            int projectedIndex,
            View excludedTaskView) {
        if (excludedTaskView == null) {
            return LauncherRecentsCompat.getTaskViewAt(recentsView, projectedIndex);
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        int projected = 0;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == excludedTaskView) {
                continue;
            }
            if (projected == projectedIndex) {
                return taskView;
            }
            projected++;
        }
        return null;
    }

    private static int findProjectedTaskIndex(
            View recentsView,
            View targetTaskView,
            View excludedTaskView) {
        if (targetTaskView == null) {
            return -1;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        int projected = 0;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == excludedTaskView) {
                continue;
            }
            if (taskView == targetTaskView) {
                return projected;
            }
            projected++;
        }
        return -1;
    }

    private static int resolveTaskRawOffset(View recentsView, int index) {
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getUnclampedScrollOffset",
                LauncherRecentsCompat.INT_ARG,
                LauncherRecentsCompat.invokeInt(
                        recentsView,
                        "getScrollOffset",
                        LauncherRecentsCompat.INT_ARG,
                        0,
                        index),
                index);
    }

    static float resolveTaskRawOffset(View recentsView, int index, int primaryScroll) {
        return resolveTaskRawOffset(recentsView, index)
                + resolvePrimaryScroll(recentsView)
                - primaryScroll;
    }

    static float resolveStackDismissProjectedVisibleOffset(
            View recentsView,
            View taskView,
            int projectedIndex,
            int targetScroll) {
        if (recentsView == null || taskView == null) {
            return 0f;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float taskPrimarySize = resolveTaskPrimarySize(taskView, 1f, primaryScrollHorizontal);
        float pageSpan = Math.max(
                1f,
                taskPrimarySize + LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0));
        float rawOffset = resolveTaskRawOffset(recentsView, projectedIndex)
                + resolvePrimaryScroll(recentsView)
                - targetScroll;
        float layoutProgress = resolveStackReleaseSettledProgress(
                (rawOffset + resolveEdgeScrollCorrection(recentsView)) / pageSpan,
                LauncherRecentsState.isAppToRecentsStackSettled(recentsView) ? 1f : 0f);
        return resolveStackVisibleOffset(
                recentsView,
                layoutProgress,
                taskPrimarySize,
                resolveTaskCenteredPrimaryStartPx(
                        recentsView,
                        taskPrimarySize,
                        primaryScrollHorizontal),
                primaryScrollHorizontal);
    }

    static float resolveStackScaleForVisibleOffset(
            View recentsView,
            View taskView,
            float visibleOffset) {
        if (recentsView == null || taskView == null) {
            return 1f;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float taskPrimarySize = resolvePrimarySize(taskView, primaryScrollHorizontal);
        if (taskPrimarySize <= 0f) {
            taskPrimarySize = Math.max(
                    1f,
                    resolvePrimarySize(recentsView, primaryScrollHorizontal));
        }
        float layerProgress = resolveStackLayerProgress(
                recentsView,
                resolveTaskCenteredPrimaryStartPx(
                        recentsView,
                        taskPrimarySize,
                        primaryScrollHorizontal),
                taskPrimarySize,
                visibleOffset,
                primaryScrollHorizontal);
        return lerp(STACK_MIN_SCALE, 1f, layerProgress);
    }

    private static StackTaskInput buildStackTaskInput(
            StackLayoutContext context,
            View taskView,
            int index) {
        LauncherRecentsState.GestureReleaseTaskState gestureReleaseTaskState =
                (context.gestureStackReleaseActive
                        || LauncherRecentsState.isAppToRecentsStackSettled(context.recentsView))
                        ? LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.get(taskView)
                        : null;
        float rawOffset = resolveTaskRawOffset(context.recentsView, index, context.primaryScroll);
        float nativeDismissTranslationPrimary = readTaskPrimaryDismissTranslation(
                taskView,
                context.primaryScrollHorizontal);
        float layoutRawOffset = rawOffset + context.edgeScrollCorrection;
        float physicalRawOffset = layoutRawOffset + nativeDismissTranslationPrimary;
        float effectiveRawOffset = physicalRawOffset;
        float progress = effectiveRawOffset / context.pageSpan;
        if (context.desktopOverviewEntryWindow) {
            progress = -index;
        }
        float layoutProgress = resolveStackReleaseSettledProgress(
                progress,
                context.stackSettledShiftProgress);
        float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : context.referenceWidth;
        float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : context.referenceHeight;
        float taskPrimarySize = resolveTaskPrimarySize(
                taskView,
                context.referencePrimarySize,
                context.primaryScrollHorizontal);
        float taskCenteredPrimaryStartPx = resolveTaskCenteredPrimaryStartPx(
                context.recentsView,
                taskPrimarySize,
                context.primaryScrollHorizontal);
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
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                nativeDismissTranslationPrimary,
                gestureReleaseTaskState,
                LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView));
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState buildStackTaskVisualState(
            StackLayoutContext context,
            StackTaskInput input,
            boolean coreOnly) {
        View taskView = input.taskView;
        if (input.gestureReleaseTaskState != null
                && input.gestureReleaseTaskState.startVisualState != null
                && input.gestureReleaseTaskState.targetVisualState != null) {
            return input.gestureReleaseTaskState.startVisualState.lerpTo(
                    input.gestureReleaseTaskState.targetVisualState,
                    context.stackReleaseProgress);
        }
        float stackEntryLiftPx = Math.min(
                input.taskHeight * STACK_ENTRY_LIFT_RATIO,
                FlymeStatusBarSizer.dp(context.recentsView.getContext(), 40));
        float finalVisibleOffset = resolveStackVisibleOffset(
                context.recentsView,
                input.layoutProgress,
                input.taskPrimarySize,
                input.taskCenteredPrimaryStartPx,
                context.leftEdgeRevealProgress,
                context.primaryScrollHorizontal);
        float finalTaskOffsetY = stackEntryLiftPx * (1.0f - context.stackVerticalProgress);
        float taskEntryProgress = resolveTaskStackEntryProgress(
                context.stackEntryProgress,
                input.collapsedReferenceProgress);
        float collapsedVisibleOffset = resolveStackVisibleOffset(
                context.recentsView,
                input.collapsedReferenceProgress,
                input.taskPrimarySize,
                input.taskCenteredPrimaryStartPx,
                context.leftEdgeRevealProgress,
                context.primaryScrollHorizontal) * STACK_ENTRY_INITIAL_SPREAD_RATIO;
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
                input.taskCenteredPrimaryStartPx,
                input.taskPrimarySize,
                desiredVisibleOffset,
                context.primaryScrollHorizontal);
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
        float desiredTranslationZ = coreOnly
                ? 0f
                : (context.maxTranslationZ + context.zStepPx) * desiredLayerProgress;
        float desiredStableAlpha = 1f;
        LauncherRecentsState.BlankTapHomeExitTaskState blankTapExitState =
                input.blankTapExitState;
        if (blankTapExitState != null) {
            desiredVisibleOffset = blankTapExitState.startVisibleOffset;
            desiredScale = blankTapExitState.startScale;
            desiredTaskOffsetY = context.primaryScrollHorizontal
                    ? blankTapExitState.startTaskOffsetY
                    : 0f;
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
                float taskStartPx = input.taskCenteredPrimaryStartPx + controlVisibleOffset;
                float exitTravelPx = Math.max(
                        taskStartPx + input.taskPrimarySize + FlymeStatusBarSizer.dp(
                                context.recentsView.getContext(),
                                64),
                        input.taskPrimarySize * (1f + BLANK_TAP_HOME_EXIT_EXTRA_TRAVEL_RATIO));
                float exitVisibleOffset = controlVisibleOffset - exitTravelPx;
                desiredVisibleOffset = quadraticBezier(
                        startVisibleOffset,
                        controlVisibleOffset,
                        exitVisibleOffset,
                        pathProgress);
                desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * pathProgress);
                float exitAlpha = resolveBlankTapExitAlpha(
                        pathProgress,
                        blankTapExitState.centerVisibleOffset);
                desiredStableAlpha *= exitAlpha;
                desiredAttachAlpha *= exitAlpha;
                activityTitleAlpha = 1f;
            } else {
                desiredStableAlpha = 0f;
                desiredAttachAlpha = 0f;
                activityTitleAlpha = 0f;
            }
        }
        float stackLeftClampAlpha = resolveStackLeftClampAlpha(
                context.recentsView,
                input.layoutProgress,
                input.taskPrimarySize,
                input.taskCenteredPrimaryStartPx,
                context.leftEdgeRevealProgress,
                context.primaryScrollHorizontal);
        if (!blankTapExitTaskActive) {
            desiredStableAlpha *= stackLeftClampAlpha;
            activityTitleAlpha = desiredStableAlpha > 0.001f ? stackLeftClampAlpha : 0f;
        }

        float targetBlurProgress = 0f;
        if (!coreOnly && context.stackContentBlurEnabled) {
            targetBlurProgress = resolveStackContentBlurProgress(
                    stackLeftClampAlpha,
                    taskEntryProgress);
            if (blankTapExitTaskActive) {
                targetBlurProgress = blankTapExitState.startStackContentBlurProgress;
            }
        }
        float translationCompensationPrimary =
                desiredVisibleOffset - input.rawOffset - input.nativeDismissTranslationPrimary;

        float appliedHorizontalOffsetX = 0f;
        float appliedTaskOffsetX = context.primaryScrollHorizontal
                ? translationCompensationPrimary
                : 0f;
        float appliedTaskOffsetY = context.primaryScrollHorizontal
                ? desiredTaskOffsetY
                : translationCompensationPrimary + desiredTaskOffsetY;
        float appliedBoxTranslationY = desiredBoxTranslationY;
        float appliedScale = desiredScale;
        float appliedAttachAlpha = desiredAttachAlpha;
        float appliedStableAlpha = desiredStableAlpha;
        float appliedBlurProgress = targetBlurProgress;
        float appliedFullscreenProgress = coreOnly
                ? 0f
                : LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView);
        float appliedTranslationZ = desiredTranslationZ;
        if (blankTapExitTaskActive) {
            float pathProgress = smoothStep(context.blankTapExitProgress);
            appliedHorizontalOffsetX = lerp(
                    blankTapExitState.startHorizontalOffsetX,
                    0f,
                    pathProgress);
            if (context.primaryScrollHorizontal) {
                appliedTaskOffsetX = translationCompensationPrimary - appliedHorizontalOffsetX;
                appliedTaskOffsetY = blankTapExitState.startTaskOffsetY;
            } else {
                appliedTaskOffsetX = lerp(
                        blankTapExitState.startTaskOffsetX,
                        0f,
                        pathProgress);
                appliedTaskOffsetY = translationCompensationPrimary;
            }
            appliedFullscreenProgress = blankTapExitState.startFullscreenProgress;
            appliedTranslationZ = blankTapExitState.startTranslationZ;
        }
        if (context.gestureStackReleaseActive) {
            if (input.gestureReleaseTaskState != null) {
                float appliedPrimaryHorizontalOffset = lerp(
                        input.gestureReleaseTaskState.startHorizontalOffsetX,
                        0f,
                        context.stackReleaseProgress);
                appliedHorizontalOffsetX = context.primaryScrollHorizontal
                        ? appliedPrimaryHorizontalOffset
                        : 0f;
                float appliedPrimaryTaskOffset =
                        translationCompensationPrimary - appliedPrimaryHorizontalOffset;
                appliedTaskOffsetX = context.primaryScrollHorizontal ? appliedPrimaryTaskOffset : 0f;
                appliedTaskOffsetY = context.primaryScrollHorizontal
                        ? appliedTaskOffsetY
                        : appliedPrimaryTaskOffset + desiredTaskOffsetY;
            } else {
                appliedHorizontalOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockHorizontalOffsetX(taskView),
                        appliedHorizontalOffsetX,
                        context.stackReleaseProgress);
                if (context.primaryScrollHorizontal) {
                    appliedTaskOffsetX = lerp(
                            LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                            appliedTaskOffsetX,
                            context.stackReleaseProgress);
                } else {
                    appliedTaskOffsetY = lerp(
                            LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                            appliedTaskOffsetY,
                            context.stackReleaseProgress);
                }
            }
            if (context.primaryScrollHorizontal) {
                appliedTaskOffsetY = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetY(taskView),
                        appliedTaskOffsetY,
                        context.stackReleaseProgress);
            } else {
                appliedTaskOffsetX = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTaskOffsetX(taskView),
                        appliedTaskOffsetX,
                        context.stackReleaseProgress);
            }
            appliedBoxTranslationY = lerp(
                    LauncherRecentsTaskVisuals.readLastStockBoxTranslationY(taskView),
                    appliedBoxTranslationY,
                    context.stackReleaseProgress);
            appliedScale = lerp(
                    LauncherRecentsTaskVisuals.readLastStockNonGridScale(taskView),
                    appliedScale,
                    context.stackReleaseProgress);
            if (!coreOnly) {
                appliedFullscreenProgress = lerp(
                        LauncherRecentsTaskVisuals.readLastStockFullscreenProgress(taskView),
                        appliedFullscreenProgress,
                        context.stackReleaseProgress);
                appliedTranslationZ = lerp(
                        LauncherRecentsTaskVisuals.readLastStockTranslationZ(taskView),
                        appliedTranslationZ,
                        context.stackReleaseProgress);
            }
            appliedAttachAlpha = lerp(
                    1f,
                    appliedAttachAlpha,
                    context.stackReleaseProgress);
            appliedStableAlpha = lerp(
                    1f,
                    appliedStableAlpha,
                    context.stackReleaseProgress);
            if (!coreOnly) {
                appliedBlurProgress = lerp(0f, appliedBlurProgress, context.stackReleaseProgress);
            }
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
                && !LauncherRecentsState.isAppToRecentsStackSettled(context.recentsView);
        float appliedActivityTitleAlpha;
        if (context.blankTapExitActive) {
            appliedActivityTitleAlpha = activityTitleAlpha;
        } else if (context.gestureStackReleaseActive) {
            appliedActivityTitleAlpha = lerp(1f, activityTitleAlpha, context.stackReleaseProgress);
        } else if (entryInProgress) {
            appliedActivityTitleAlpha = activityTitleAlpha;
        } else {
            appliedActivityTitleAlpha = appliedStableAlpha > 0.001f ? activityTitleAlpha : 0f;
        }
        appliedActivityTitleAlpha = Math.min(appliedActivityTitleAlpha, appliedStableAlpha);
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
        LauncherRecentsPerf.flow("layout:recovery:cancel", recentsView);
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
    }

    static void startStackLayoutRecovery(View recentsView) {
        if (!shouldAllowStackLayoutRecovery(recentsView)) {
            LauncherRecentsPerf.flow("layout:recovery:skipStart", recentsView);
            return;
        }
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.put(
                recentsView,
                STACK_ENTRY_LIGHT_RADIUS + STACK_LAYOUT_RECOVERY_RADIUS_STEP);
        LauncherRecentsPerf.flow("layout:recovery:schedule",
                recentsView,
                "radius=" + (STACK_ENTRY_LIGHT_RADIUS + STACK_LAYOUT_RECOVERY_RADIUS_STEP));
        recentsView.postOnAnimation(() -> runStackLayoutRecoveryFrame(recentsView));
    }

    static void restoreStackLayout(View recentsView, String source) {
        startStackLayoutRecovery(recentsView);
        applyStackLayout(recentsView, false, source, true);
    }

    static boolean isStackLayoutRecoveryActive(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.containsKey(recentsView);
    }

    private static boolean shouldAllowStackLayoutRecovery(View recentsView) {
        return shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                && !shouldBlockAppToRecentsStackApply(recentsView);
    }

    private static void runStackLayoutRecoveryFrame(View recentsView) {
        Integer radius = LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.get(recentsView);
        if (recentsView == null
                || radius == null
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || !shouldAllowStackLayoutRecovery(recentsView)) {
            LauncherRecentsPerf.flow("layout:recovery:clear",
                    recentsView, "radius=" + radius);
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (radius >= taskViewCount) {
            LauncherRecentsPerf.flow("layout:recovery:final",
                    recentsView,
                    "radius=" + radius + " taskCount=" + taskViewCount);
            LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.remove(recentsView);
            applyStackLayout(recentsView, false, STACK_STABLE_VISIBLE_RADIUS, "recoveryFinal", true);
            return;
        }
        LauncherRecentsPerf.flow("layout:recovery:frame",
                recentsView,
                "radius=" + radius + " taskCount=" + taskViewCount);
        applyStackLayout(recentsView, false, radius, "recoveryFrame", true);
        LauncherRecentsState.STACK_LAYOUT_RECOVERY_RADII.put(
                recentsView,
                radius + STACK_LAYOUT_RECOVERY_RADIUS_STEP);
        LauncherRecentsPerf.flow("layout:recovery:scheduleNext",
                recentsView,
                "radius=" + (radius + STACK_LAYOUT_RECOVERY_RADIUS_STEP));
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
            int stackLayoutRadius,
            boolean entryWindow) {
        return resolveStackLayoutAnchorIndex(
                recentsView,
                runningTaskChildIndex,
                taskViewCount,
                stackLayoutRadius,
                entryWindow,
                null);
    }

    private static int resolveStackLayoutAnchorIndex(
            View recentsView,
            int runningTaskChildIndex,
            int taskViewCount,
            int stackLayoutRadius,
            boolean entryWindow,
            Integer targetScroll) {
        if (entryWindow && runningTaskChildIndex >= 0) {
            return runningTaskChildIndex;
        }
        if (!entryWindow && stackLayoutRadius == STACK_STABLE_VISIBLE_RADIUS) {
            return resolveNearestStackLayoutPage(recentsView, taskViewCount, targetScroll);
        }
        if (runningTaskChildIndex >= 0) {
            return runningTaskChildIndex;
        }
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        return Math.max(0, Math.min(currentPage, Math.max(0, taskViewCount - 1)));
    }

    private static int resolveNearestStackLayoutPage(View recentsView, int taskViewCount) {
        return resolveNearestStackLayoutPage(recentsView, taskViewCount, null);
    }

    private static int resolveNearestStackLayoutPage(
            View recentsView,
            int taskViewCount,
            Integer targetScroll) {
        int primaryScroll = targetScroll != null ? targetScroll : resolvePrimaryScroll(recentsView);
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        int scrollOverPage = LauncherRecentsCompat.readIntField(
                recentsView,
                "mCurrentScrollOverPage",
                currentPage);
        int centerPage = scrollOverPage >= 0 && scrollOverPage < taskViewCount
                ? scrollOverPage
                : currentPage;
        centerPage = Math.max(0, Math.min(centerPage, Math.max(0, taskViewCount - 1)));
        int searchRadius = STACK_STABLE_VISIBLE_RADIUS + 2;
        int nearestPage = resolveNearestStackLayoutPageInRange(
                recentsView,
                primaryScroll,
                Math.max(0, centerPage - searchRadius),
                Math.min(taskViewCount - 1, centerPage + searchRadius));
        int nearestDistance = Math.abs(resolveStackLayoutScrollForPage(
                recentsView,
                primaryScroll,
                nearestPage) - primaryScroll);
        float pageSpan = Math.max(
                1f,
                resolvePrimarySize(recentsView, isPrimaryScrollHorizontal(recentsView))
                        + LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0));
        if (nearestDistance <= pageSpan) {
            return nearestPage;
        }
        return resolveNearestStackLayoutPageInRange(
                recentsView,
                primaryScroll,
                0,
                taskViewCount - 1);
    }

    private static int resolveNearestStackLayoutPageInRange(
            View recentsView,
            int primaryScroll,
            int startPage,
            int endPage) {
        int nearestPage = startPage;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = startPage; i <= endPage; i++) {
            int pageScroll = resolveStackLayoutScrollForPage(recentsView, primaryScroll, i);
            int distance = Math.abs(pageScroll - primaryScroll);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPage = i;
            }
        }
        return nearestPage;
    }

    private static int resolveStackLayoutScrollForPage(
            View recentsView,
            int fallbackScroll,
            int page) {
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                fallbackScroll,
                page);
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
        LauncherRecentsTaskVisuals.setTaskHeadContentAlpha(taskView, 0f);
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
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && (isStackScrollerActive(recentsView)
                || shouldSuppressStockPageOffsetUpdateForTransition(recentsView));
    }

    private static boolean shouldSuppressStockHorizontalOffsetUpdate(
            String methodName,
            View recentsView) {
        return "updateHorizontalOffset".equals(methodName)
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && (isStackScrollerActive(recentsView)
                || shouldSuppressStockPageOffsetUpdateForTransition(recentsView));
    }

    private static boolean isStackScrollerActive(View recentsView) {
        Object scroller = LauncherRecentsCompat.getFieldCompat(recentsView, "mScroller");
        if (isScrollerActive(scroller)) {
            return true;
        }
        Object activeScroller = LauncherRecentsCompat.getFieldCompat(scroller, "usingScroller");
        return activeScroller != scroller && isScrollerActive(activeScroller);
    }

    private static boolean isScrollerActive(Object scroller) {
        Object value = LauncherRecentsCompat.invokeCompat(
                scroller,
                "isFinished",
                LauncherRecentsCompat.NO_ARGS);
        return value instanceof Boolean && !((Boolean) value);
    }

    private static boolean shouldSuppressStockPageOffsetUpdateForTransition(View recentsView) {
        return (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                        recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
                        recentsView)
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
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
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
                // handoff 开始后 deferred 已清零但 progress 尚未建立，此帧也需压制。
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
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

    private static boolean shouldSuppressStackAlphaVisualMethod(
            String methodName,
            View recentsView) {
        return ("resetTaskVisuals".equals(methodName)
                || "applyAttachAlpha".equals(methodName))
                && shouldOwnStackTaskAlpha(recentsView);
    }

    private static boolean shouldOwnStackTaskAlpha(View recentsView) {
        return recentsView != null
                && shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                && (LauncherRecentsTransitionController
                .shouldSuppressGestureReleaseStockTaskVisuals(recentsView)
                || LauncherRecentsState.isAppToRecentsStackSettled(recentsView)
                || LauncherRecentsState.isOverviewStateStackSettled(recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || isStackLayoutRecoveryActive(recentsView));
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
        LauncherRecentsTaskVisuals.setTaskHeadContentAlpha(taskView, 1f);
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

    private static float resolveBlankTapExitAlpha(float progress, float centerVisibleOffset) {
        if (Math.abs(centerVisibleOffset) < 1f) {
            return resolveBlankTapExitAlpha(progress);
        }
        if (progress < 0.28f) {
            return 1f;
        }
        return lerp(1f, 0f, smoothStep(remapProgress(progress, 0.28f, 0.62f)));
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
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && !LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)
                && !LauncherRecentsState.hasActiveTaskLaunchTransitionGeometry(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && (!LauncherRecentsStateAnimationController.shouldKeepOverviewPeekStockLayout(
                recentsView)
                || LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                || isStackLayoutRecoveryActive(recentsView))
                && !shouldBlockAppToRecentsStackApply(recentsView);
    }

    static boolean shouldSuppressStockLayoutMutation(View recentsView) {
        return recentsView != null
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView));
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
        if (LauncherRecentsState.isAppToRecentsStackSettled(recentsView)
                || LauncherRecentsState.isOverviewStateStackSettled(recentsView)) {
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
        if (LauncherRecentsState.isAppToRecentsStackSettled(recentsView)
                || LauncherRecentsState.isOverviewStateStackSettled(recentsView)) {
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
     * - 手势已松手但 release 动画仍在运行
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
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            boolean primaryScrollHorizontal) {
        return resolveStackVisibleOffset(
                recentsView,
                progress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                resolveLeftEdgeRevealProgress(recentsView),
                primaryScrollHorizontal);
    }

    private static float resolveStackVisibleOffset(
            View recentsView,
            float progress,
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            float leftEdgeRevealProgress,
            boolean primaryScrollHorizontal) {
        int primaryAxisSign = resolveStackPrimaryAxisSign(recentsView, primaryScrollHorizontal);
        float visualProgress = progress * primaryAxisSign;
        float leftBoundOffsetPx = resolveStackLeftBoundOffset(
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                visualProgress < 0f ? 1f : leftEdgeRevealProgress);
        float visibleOffset = resolveStackUnclampedVisibleOffset(
                recentsView,
                visualProgress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                primaryScrollHorizontal);
        float offset = Math.max(leftBoundOffsetPx, visibleOffset);
        return offset * primaryAxisSign;
    }

    private static int resolveStackPrimaryAxisSign(
            View recentsView,
            boolean primaryScrollHorizontal) {
        return !primaryScrollHorizontal && isSeascapeOrientation(recentsView) ? -1 : 1;
    }

    private static float resolveStackUnclampedVisibleOffset(
            View recentsView,
            float progress,
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            boolean primaryScrollHorizontal) {
        float visibleOffset = resolveStackVirtualVisibleOffset(
                recentsView,
                progress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                primaryScrollHorizontal);
        return progress >= 0f ? visibleOffset : -(visibleOffset * STACK_LEFT_MOVE_RATIO);
    }

    private static float resolveStackVirtualVisibleOffset(
            View recentsView,
            float progress,
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            boolean primaryScrollHorizontal) {
        float stackRightOffsetPx = Math.max(
                0f,
                resolvePrimarySize(recentsView, primaryScrollHorizontal)
                        - (taskPrimarySize * STACK_RIGHT_VISIBLE_RATIO)
                        - taskCenteredPrimaryStartPx);
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
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            float leftEdgeRevealProgress) {
        float stackLeftOffsetPx =
                -taskCenteredPrimaryStartPx + (taskPrimarySize * STACK_LEFT_EDGE_INSET_RATIO);
        float stackLeftRestOffsetPx =
                -taskCenteredPrimaryStartPx + (taskPrimarySize * STACK_LEFT_REST_INSET_RATIO);
        return lerp(
                stackLeftRestOffsetPx,
                stackLeftOffsetPx,
                leftEdgeRevealProgress);
    }

    private static float resolveStackLeftClampAlpha(
            View recentsView,
            float progress,
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            boolean primaryScrollHorizontal) {
        return resolveStackLeftClampAlpha(
                recentsView,
                progress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                resolveLeftEdgeRevealProgress(recentsView),
                primaryScrollHorizontal);
    }

    private static float resolveStackLeftClampAlpha(
            View recentsView,
            float progress,
            float taskPrimarySize,
            float taskCenteredPrimaryStartPx,
            float leftEdgeRevealProgress,
            boolean primaryScrollHorizontal) {
        int primaryAxisSign = resolveStackPrimaryAxisSign(recentsView, primaryScrollHorizontal);
        float visualProgress = progress * primaryAxisSign;
        if (visualProgress >= 0f) {
            return 1f;
        }
        float currentOffset = resolveStackVisibleOffset(
                recentsView,
                progress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                leftEdgeRevealProgress,
                primaryScrollHorizontal);
        float frontProgress = (visualProgress + 1f) * primaryAxisSign;
        float frontOffset = resolveStackVisibleOffset(
                recentsView,
                frontProgress,
                taskPrimarySize,
                taskCenteredPrimaryStartPx,
                leftEdgeRevealProgress,
                primaryScrollHorizontal);
        float distancePx = Math.abs(frontOffset - currentOffset);
        float opaqueDistancePx = Math.max(
                1f,
                taskPrimarySize * 0.24f);
        return smoothStep(remapProgress(distancePx, 0f, opaqueDistancePx));
    }

    private static float resolveStackLayerProgress(
            View recentsView,
            float taskCenteredPrimaryStartPx,
            float taskPrimarySize,
            float visibleOffset,
            boolean primaryScrollHorizontal) {
        float taskCenterPrimary =
                taskCenteredPrimaryStartPx + visibleOffset + (taskPrimarySize * 0.5f);
        float primarySize = resolvePrimarySize(recentsView, primaryScrollHorizontal);
        if (resolveStackPrimaryAxisSign(recentsView, primaryScrollHorizontal) < 0) {
            taskCenterPrimary = primarySize - taskCenterPrimary;
        }
        return remapProgress(
                taskCenterPrimary,
                0f,
                primarySize);
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
        return resolveLeftEdgeRevealProgress(recentsView, resolvePrimaryScroll(recentsView));
    }

    private static float resolveLeftEdgeRevealProgress(View recentsView, int primaryScroll) {
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        int minScroll = LauncherRecentsCompat.readIntField(
                recentsView,
                "mMinScroll",
                resolvePrimaryScroll(recentsView));
        float revealRange = Math.max(
                1f,
                resolvePrimarySize(recentsView, primaryScrollHorizontal)
                        * STACK_LEFT_EDGE_REVEAL_SCROLL_RATIO);
        return 1.0f - remapProgress(primaryScroll - minScroll, 0f, revealRange);
    }

    private static int resolveEdgeScrollCorrection(View recentsView) {
        return resolveEdgeScrollCorrection(recentsView, resolvePrimaryScroll(recentsView));
    }

    private static int resolveEdgeScrollCorrection(View recentsView, int primaryScroll) {
        int minScroll = LauncherRecentsCompat.readIntField(recentsView, "mMinScroll", primaryScroll);
        int maxScroll = LauncherRecentsCompat.readIntField(recentsView, "mMaxScroll", primaryScroll);
        int clampedScroll = Math.max(minScroll, Math.min(primaryScroll, maxScroll));
        return primaryScroll - clampedScroll;
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
        return sharesTaskIds(taskView, resolveTaskIds(runningTaskView));
    }

    private static int[] resolveTaskIds(View taskView) {
        Object taskIdsObject = LauncherRecentsCompat.invokeCompat(
                taskView,
                "getTaskIds",
                LauncherRecentsCompat.NO_ARGS);
        if (!(taskIdsObject instanceof int[])) {
            return null;
        }
        return (int[]) taskIdsObject;
    }

    private static boolean sharesTaskIds(View taskView, int[] taskIds) {
        if (taskView == null || taskIds == null) {
            return false;
        }
        for (int taskId : taskIds) {
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
            float centeredPrimaryStartPx,
            float taskPrimarySize,
            float desiredVisibleOffset,
            float desiredScale,
            boolean primaryScrollHorizontal) {
        float clampedScale = Math.max(0.5f, desiredScale);
        float translatedStartPx = centeredPrimaryStartPx + desiredVisibleOffset;
        float actualStartPx =
                translatedStartPx + ((1.0f - clampedScale) * taskPrimarySize * 0.5f);
        float actualEndPx = actualStartPx + (taskPrimarySize * clampedScale);
        return actualEndPx > 0f
                && actualStartPx < resolvePrimarySize(recentsView, primaryScrollHorizontal);
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
