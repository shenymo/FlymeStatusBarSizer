package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Method;

final class LauncherRecentsTransitionController {
    private static final String ABS_SWIPE_UP_HANDLER_CLASS =
            "com.android.quickstep.AbsSwipeUpHandler";
    private static final long GESTURE_STACK_RELEASE_DURATION_MS = 320L;
    private static final float GESTURE_STACK_RELEASE_HANDOFF_START_PROGRESS = 0f;
    private static final DecelerateInterpolator GESTURE_STACK_RELEASE_INTERPOLATOR =
            new DecelerateInterpolator(1.35f);

    private LauncherRecentsTransitionController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewStartHome(module, loader);
        hookRecentsViewPrepareGestureEndAnimation(module, loader);
        hookRecentsViewSwitchToScreenshot(module, loader);
        hookRecentsViewEnableDrawingLiveTile(module, loader);
        hookRecentsViewGestureAnimationEnd(module, loader);
        hookAbsSwipeUpHandlerGestureEnded(module, loader);
        hookAbsSwipeUpHandlerCalculateEndTarget(module, loader);
        hookAbsSwipeUpHandlerLauncherTransitionProgress(module, loader);
    }

    private static void hookRecentsViewStartHome(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("startHome", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsPerf.flow("leave:startHome", recentsView,
                            "animated=" + chain.getArg(0));
                    if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
                        LauncherRecentsPerf.flow("leave:startHome:skipSwipeUp", recentsView);
                        return chain.proceed();
                    }
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (shouldAnimateBlankTapHomeExit(recentsView)) {
                        LauncherRecentsPerf.flow("leave:startHome:prepareBlankTap",
                                recentsView);
                        prepareBlankTapHomeExitAnimation(recentsView);
                        Object result = chain.proceed();
                        return result;
                    }
                    LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
                    LauncherRecentsStateAnimationController.clearOverviewEntryState(recentsView);
                    LauncherRecentsAttachController.clearAppToRecentsEntrySession(
                            recentsView,
                            false);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.startHome",
                    t);
        }
    }

    private static void hookRecentsViewPrepareGestureEndAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> gestureEndTargetClass =
                    Class.forName("com.android.quickstep.GestureState$GestureEndTarget", false, loader);
            Class<?> remoteTargetHandleArrayClass =
                    Class.forName(
                            "[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;",
                            false,
                            loader);
            Method method = clazz.getDeclaredMethod(
                    "onPrepareGestureEndAnimation",
                    AnimatorSet.class,
                    gestureEndTargetClass,
                    remoteTargetHandleArrayClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject()
                        : null;
                Object endTarget = chain.getArg(1);
                boolean shouldPrepareGestureRelease =
                        shouldUsePendingGestureRecentsStackRelease(recentsView, endTarget);
                LauncherRecentsPerf.flow("enter:prepareGestureEnd",
                        recentsView,
                        "endTarget=" + endTarget
                                + " shouldPrepareRelease=" + shouldPrepareGestureRelease);
                if (shouldPrepareGestureRelease) {
                    LauncherRecentsState.setSwipeUpGestureActive(recentsView, false);
                    markPendingGestureRecentsStackRelease(recentsView, true);
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (chain.getArg(0) instanceof AnimatorSet) {
                        LauncherRecentsPerf.flow("enter:prepareGestureEnd:startReleaseAnimation",
                                recentsView);
                        startGestureRecentsStackReleaseAnimation(
                                recentsView,
                                (AnimatorSet) chain.getArg(0),
                                true);
                    }
                    return null;
                } else {
                    markPendingGestureRecentsStackRelease(recentsView, false);
                }
                Object result = chain.proceed();
                if (recentsView != null
                        && isRecentsGestureEndTarget(endTarget)
                        && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onPrepareGestureEndAnimation",
                    t);
        }
    }

    private static void hookRecentsViewSwitchToScreenshot(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("switchToScreenshot", Runnable.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    LauncherRecentsPerf.flow("enter:switchToScreenshot", recentsView);
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.switchToScreenshot",
                    t);
        }
    }

    private static void hookRecentsViewEnableDrawingLiveTile(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setEnableDrawingLiveTile", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                boolean enableLiveTile =
                        chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (enableLiveTile && shouldSuppressLiveTileForStack(recentsView)) {
                        LauncherRecentsPerf.flow("enter:liveTile:suppress",
                                recentsView, "enable=" + enableLiveTile);
                        LauncherRecentsCompat.writeField(recentsView, "mEnableDrawingLiveTile", false);
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setEnableDrawingLiveTile",
                    t);
        }
    }

    private static void hookRecentsViewGestureAnimationEnd(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onGestureAnimationEnd");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                View recentsView = thisObject instanceof View ? (View) thisObject : null;
                Object endTarget = LauncherRecentsCompat.getFieldCompat(
                        thisObject,
                        "mCurrentGestureEndTarget");
                boolean shouldPrepareGestureRelease =
                        shouldUsePendingGestureRecentsStackRelease(recentsView, endTarget);
                LauncherRecentsPerf.flow("enter:gestureAnimationEnd",
                        recentsView,
                        "endTarget=" + endTarget
                                + " shouldPrepareRelease=" + shouldPrepareGestureRelease);
                if (shouldPrepareGestureRelease) {
                    LauncherRecentsState.setSwipeUpGestureActive(recentsView, false);
                    markPendingGestureRecentsStackRelease(recentsView, true);
                }
                if ((!LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                        || isRecentsGestureEndTarget(endTarget))
                        && shouldTakeOverAppToRecentsGestureEnd(
                        recentsView,
                        shouldPrepareGestureRelease)) {
                    LauncherRecentsPerf.flow("enter:gestureAnimationEnd:takeOver",
                            recentsView);
                    finishAppToRecentsGestureEnd(recentsView);
                    return null;
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
                        LauncherRecentsPerf.flow("enter:gestureAnimationEnd:clearSwipeUp",
                                recentsView);
                        clearNonRecentsGestureEndState(recentsView);
                        return result;
                    }
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    boolean gestureReleased =
                            LauncherRecentsState.isAppToRecentsGestureReleased(recentsView);
                    boolean releaseAnimationActive =
                            isGestureRecentsStackReleaseAnimationActive(recentsView);
                    boolean releaseAnimationFinished =
                            LauncherRecentsState.isGestureStackReleasedStable(recentsView);
                    if (isGestureRecentsStackReleaseHandoffPending(recentsView)) {
                        LauncherRecentsPerf.flow("enter:gestureAnimationEnd:waitHandoff",
                                recentsView);
                        return result;
                    }
                    if (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                            && !gestureReleased
                            && !releaseAnimationActive
                            && !releaseAnimationFinished) {
                        LauncherRecentsPerf.flow("enter:gestureAnimationEnd:waitDeferred",
                                recentsView);
                        return result;
                    } else if (shouldPrepareGestureRelease) {
                        if (gestureReleased || releaseAnimationActive || releaseAnimationFinished) {
                            LauncherRecentsPerf.flow("enter:gestureAnimationEnd:applyRelease",
                                    recentsView,
                                    "gestureReleased=" + gestureReleased
                                            + " releaseActive=" + releaseAnimationActive
                                            + " releaseFinished=" + releaseAnimationFinished);
                            LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, false);
                            LauncherRecentsState.setAppToRecentsStackLayoutDeferred(recentsView, false);
                            markPendingGestureRecentsStackRelease(recentsView, false);
                            LauncherRecentsLayoutEngine.requestStackLayout(
                                    recentsView,
                                    "gestureAnimationEndRelease",
                                    false,
                                    false);
                        }
                    } else {
                        // 有待处理释放时继续等下一帧，避免提前恢复平铺布局。
                        if (!isPendingGestureRecentsStackRelease(recentsView)) {
                            LauncherRecentsPerf.flow("enter:gestureAnimationEnd:clearNonRecents",
                                    recentsView);
                            clearNonRecentsGestureEndState(recentsView);
                        }
                    }
                    if (!shouldPrepareGestureRelease
                            || gestureReleased
                            || releaseAnimationActive
                            || releaseAnimationFinished) {
                        markPendingGestureRecentsStackRelease(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onGestureAnimationEnd",
                    t);
        }
    }

    private static boolean shouldTakeOverAppToRecentsGestureEnd(
            View recentsView,
            boolean shouldPrepareGestureRelease) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && (shouldPrepareGestureRelease
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || isGestureRecentsStackReleaseHandoffPending(recentsView)
                || isGestureRecentsStackReleaseAnimationActive(recentsView)
                || LauncherRecentsState.isGestureStackReleasedStable(recentsView));
    }

    private static void clearNonRecentsGestureEndState(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:clearNonRecentsState", recentsView);
        cancelGestureRecentsStackReleaseAnimation(recentsView, true);
        LauncherRecentsState.clearAppToRecentsGestureState(recentsView);
        LauncherRecentsTouchController.clearStackAppFlowVisibilityCache();
        LauncherRecentsStateAnimationController.clearOverviewEntryState(recentsView);
        LauncherRecentsLayoutEngine.hideStackClearAllButton(recentsView);
    }

    private static void finishAppToRecentsGestureEnd(View recentsView) {
        LauncherRecentsPerf.flow("enter:finishGestureEnd:start", recentsView);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsCompat.writeField(recentsView, "mActiveGestureGroupedTaskInfo", null);
        Object orientationState = LauncherRecentsCompat.getFieldCompat(
                recentsView,
                "mOrientationState");
        Object gestureChanged = LauncherRecentsCompat.invokeCompat(
                orientationState,
                "setGestureActive",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        if (gestureChanged instanceof Boolean && (Boolean) gestureChanged) {
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "updateOrientationHandler",
                    LauncherRecentsCompat.BOOLEAN_ARG,
                    false);
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setEnableFreeScroll",
                new Class[]{boolean.class, boolean.class},
                true,
                true);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setEnableDrawingLiveTile",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskViewShowScreenshot",
                LauncherRecentsCompat.BOOLEAN_ARG,
                true);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskHidden",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.writeField(recentsView, "mCurrentGestureEndTarget", null);
        LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, false);
        LauncherRecentsState.setAppToRecentsStackLayoutDeferred(recentsView, false);
        LauncherRecentsState.setSwipeUpGestureActive(recentsView, false);
        markPendingGestureRecentsStackRelease(recentsView, false);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        LauncherRecentsLayoutEngine.ensureStackClearAllButtonReady(recentsView);
        LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.invalidate();
        LauncherRecentsPerf.flow("enter:finishGestureEnd:end", recentsView);
    }

    private static void hookAbsSwipeUpHandlerGestureEnded(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(ABS_SWIPE_UP_HANDLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onGestureEnded",
                    float.class,
                    PointF.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                View recentsView = resolveHandlerRecentsView(thisObject);
                Object result = chain.proceed();
                Object gestureState = LauncherRecentsCompat.getFieldCompat(thisObject, "mGestureState");
                Object endTarget = LauncherRecentsCompat.invokeCompat(gestureState, "getEndTarget");
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                        && isRecentsGestureEndTarget(endTarget)) {
                    LauncherRecentsPerf.flow("enter:absGestureEnded",
                            recentsView, "endTarget=" + endTarget);
                    LauncherRecentsState.setSwipeUpGestureActive(recentsView, false);
                    LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, true);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook AbsSwipeUpHandler.onGestureEnded",
                    t);
        }
    }

    private static void hookAbsSwipeUpHandlerCalculateEndTarget(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(ABS_SWIPE_UP_HANDLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "calculateEndTarget",
                    PointF.class,
                    float.class,
                    boolean.class,
                    boolean.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                View recentsView = resolveHandlerRecentsView(chain.getThisObject());
                int targetPage = resolveStackQuickSwitchTargetPage(recentsView);
                if (targetPage >= 0 && isLastTaskGestureEndTarget(result)) {
                    LauncherRecentsPerf.flow("enter:calculateEndTarget:quickSwitch",
                            recentsView,
                            "oldTarget=" + result + " targetPage=" + targetPage);
                    LauncherRecentsCompat.setIntField(recentsView, "mNextPage", targetPage);
                    Object newTaskTarget = LauncherRecentsCompat.readStaticFieldCompat(
                            "com.android.quickstep.GestureState$GestureEndTarget",
                            "NEW_TASK",
                            loader);
                    if (newTaskTarget != null) {
                        return newTaskTarget;
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook AbsSwipeUpHandler.calculateEndTarget",
                    t);
        }
    }

    private static int resolveStackQuickSwitchTargetPage(View recentsView) {
        if (recentsView == null || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return -1;
        }
        int runningTaskIndex = LauncherRecentsCompat.invokeInt(
                recentsView,
                "getRunningTaskIndex",
                -1);
        if (runningTaskIndex < 0) {
            return -1;
        }
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        int targetPage = LauncherRecentsCompat.invokeInt(recentsView, "getDestinationPage", -1);
        if (targetPage < 0) {
            targetPage = resolveNearestQuickSwitchPageForScroll(recentsView, pageCount);
        }
        if (targetPage < 0 || targetPage >= pageCount || targetPage == runningTaskIndex) {
            return -1;
        }
        Object taskView = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getTaskViewAt",
                LauncherRecentsCompat.INT_ARG,
                targetPage);
        return taskView instanceof View ? targetPage : -1;
    }

    private static int resolveNearestQuickSwitchPageForScroll(View recentsView, int pageCount) {
        if (recentsView == null || pageCount <= 0) {
            return -1;
        }
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int nearestPage = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < pageCount; i++) {
            int pageScroll = resolveScrollForPage(recentsView, i, primaryScroll);
            int distance = Math.abs(pageScroll - primaryScroll);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPage = i;
            }
        }
        return nearestPage;
    }

    private static boolean isLastTaskGestureEndTarget(Object value) {
        return value instanceof Enum && "LAST_TASK".equals(((Enum<?>) value).name());
    }

    private static void hookAbsSwipeUpHandlerLauncherTransitionProgress(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(ABS_SWIPE_UP_HANDLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateLauncherTransitionProgressForFlyme");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                View recentsView = resolveHandlerRecentsView(chain.getThisObject());
                applyForcedRecentsTranslation(recentsView);
                if (isBlankTapHomeExitActive(recentsView)) {
                    LauncherRecentsLayoutEngine.applyBlankTapHomeExitRecentsFrame(
                            recentsView,
                            readBlankTapHomeExitProgress(recentsView));
                }
                if (hasGestureRecentsStackReleaseProgress(recentsView)) {
                    recentsView.invalidate();
                } else if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
                    LauncherRecentsPerf.flow("enter:launcherTransitionProgress",
                            recentsView);
                    LauncherRecentsLayoutEngine.requestStackLayout(
                            recentsView,
                            "launcherTransitionProgress",
                            false,
                            false);
                    recentsView.invalidate();
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook AbsSwipeUpHandler.updateLauncherTransitionProgressForFlyme",
                    t);
        }
    }

    static boolean shouldAnimateBlankTapHomeExit(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && (LauncherRecentsCompat.readBooleanField(
                        recentsView,
                        "mTouchDownToStartHome",
                        false)
                || isRecentsVisibleState(recentsView));
    }

    static boolean shouldPrepareHomeExitFromRecents(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && isRecentsVisibleState(recentsView);
    }

    private static boolean isRecentsVisibleState(View recentsView) {
        Object container = LauncherRecentsCompat.getFieldCompat(recentsView, "mContainer");
        Object stateManager = LauncherRecentsCompat.invokeCompat(container, "getStateManager");
        return isRecentsVisibleStateObject(
                LauncherRecentsCompat.invokeCompat(stateManager, "getState"))
                || isRecentsVisibleStateObject(
                        LauncherRecentsCompat.invokeCompat(
                                stateManager,
                                "getCurrentStableState"));
    }

    private static boolean isRecentsVisibleStateObject(Object state) {
        return LauncherRecentsCompat.readBooleanField(state, "isRecentsViewVisible", false);
    }

    static void prepareBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("leave:blankTap:prepare", recentsView);
        if (isBlankTapHomeExitActive(recentsView)
                && !LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.isEmpty()) {
            LauncherRecentsPerf.flow("leave:blankTap:prepare:alreadyActive", recentsView);
            return;
        }
        setPageAnimOffScreenStart(recentsView, false);
        boolean shouldCaptureBlankTapState = !isBlankTapHomeExitActive(recentsView)
                || LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.isEmpty();
        if (shouldCaptureBlankTapState) {
            LauncherRecentsLayoutEngine.captureBlankTapHomeExitTaskStates(recentsView);
            LauncherRecentsLayoutEngine.captureBlankTapHomeExitRecentsState(recentsView);
        }
        clearEntryStateForBlankTapHomeExit(recentsView, true);
        markBlankTapHomeExitActive(recentsView, true);
        setBlankTapHomeExitProgress(recentsView, 0f);
        LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "blankTapPrepare",
                false,
                false);
        recentsView.invalidate();
    }

    static void cancelBlankTapHomeExitAnimation(View recentsView, boolean resetTransform) {
        if (resetTransform) {
            clearBlankTapHomeExitProgress(recentsView);
        }
    }

    static void clearBlankTapHomeExitProgress(View recentsView) {
        clearBlankTapHomeExitProgress(recentsView, true);
    }

    private static void clearBlankTapHomeExitProgress(View recentsView, boolean reapplyLayout) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("leave:blankTap:clear",
                recentsView, "reapplyLayout=" + reapplyLayout);
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.remove(recentsView);
        markBlankTapHomeExitActive(recentsView, false);
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.clear();
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_RECENTS_STATES.clear();
        setPageAnimOffScreenStart(recentsView, false);
        clearEntryStateForBlankTapHomeExit(recentsView, false);
        if (reapplyLayout) {
            LauncherRecentsLayoutEngine.requestStackLayout(
                    recentsView,
                    "blankTapClear",
                    false,
                    false);
        }
        recentsView.invalidate();
    }

    static void setBlankTapHomeExitProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.put(
                recentsView,
                LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f));
    }

    static void clearBlankTapHomeExitProgressWithoutLayout(View recentsView) {
        clearBlankTapHomeExitProgress(recentsView, false);
    }

    static float readBlankTapHomeExitProgress(View recentsView) {
        Float value = LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.get(recentsView);
        return value != null ? value : 0f;
    }

    static boolean isBlankTapHomeExitActive(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.ACTIVE_BLANK_TAP_HOME_EXITS.containsKey(recentsView);
    }

    private static void markBlankTapHomeExitActive(View recentsView, boolean active) {
        if (recentsView == null) {
            return;
        }
        if (active) {
            LauncherRecentsState.ACTIVE_BLANK_TAP_HOME_EXITS.put(recentsView, Boolean.TRUE);
        } else {
            LauncherRecentsState.ACTIVE_BLANK_TAP_HOME_EXITS.remove(recentsView);
        }
    }

    static boolean hasGestureRecentsStackReleaseProgress(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.containsKey(recentsView);
    }

    static boolean isGestureRecentsStackReleaseAnimationActive(View recentsView) {
        return recentsView != null
                && (LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.containsKey(
                recentsView)
                || hasGestureRecentsStackReleaseProgress(recentsView));
    }

    static boolean shouldSuppressGestureReleaseStockTaskVisuals(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && (LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || isGestureRecentsStackReleaseHandoffPending(recentsView)
                || isGestureRecentsStackReleaseAnimationActive(recentsView));
    }

    static float readGestureRecentsStackReleaseProgress(View recentsView) {
        Float value = LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.get(recentsView);
        return value != null ? value : 1f;
    }

    static void cancelGestureRecentsStackReleaseAnimation(View recentsView, boolean clearProgress) {
        ValueAnimator animator =
                LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.remove(recentsView);
        if (animator != null) {
            LauncherRecentsPerf.flow("enter:gestureRelease:cancelRequest",
                    recentsView, "clearProgress=" + clearProgress);
            animator.cancel();
        }
        markGestureRecentsStackReleaseHandoffPending(recentsView, false);
        if (clearProgress) {
            LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
            clearGestureRecentsStackReleaseProgress(recentsView);
            clearForcedRecentsTranslationX(recentsView);
            clearForcedRecentsTranslationY(recentsView);
            LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
        }
    }

    static boolean isRecentsGestureEndTarget(Object value) {
        return value instanceof Enum && "RECENTS".equals(((Enum<?>) value).name());
    }

    private static void startGestureRecentsStackReleaseAnimation(
            View recentsView,
            AnimatorSet animatorSet,
            boolean ensureRunningTaskScreenshot) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator =
                LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            LauncherRecentsPerf.flow("enter:gestureRelease:start:alreadyRunning",
                    recentsView);
            return;
        }
        final float releaseStartTranslationX = recentsView.getTranslationX();
        final float releaseStartTranslationY = recentsView.getTranslationY();
        final int stackAnchorPage = resolveAppToRecentsStackAnchorPage(recentsView);
        final int stackAnchorTargetScroll = resolveScrollForPage(
                recentsView,
                stackAnchorPage,
                resolvePrimaryScroll(recentsView));
        final boolean[] handoffStarted = new boolean[]{false};
        final float[] handoffStartTranslationX = new float[]{releaseStartTranslationX};
        final float[] handoffStartTranslationY = new float[]{releaseStartTranslationY};
        final int[] handoffStartScroll = new int[]{resolvePrimaryScroll(recentsView)};
        LauncherRecentsPerf.flow("enter:gestureRelease:start",
                recentsView,
                "anchorPage=" + stackAnchorPage
                        + " targetScroll=" + stackAnchorTargetScroll
                        + " ensureScreenshot=" + ensureRunningTaskScreenshot);
        markGestureRecentsStackReleaseHandoffPending(recentsView, true);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(GESTURE_STACK_RELEASE_DURATION_MS);
        animator.setInterpolator(GESTURE_STACK_RELEASE_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            float progress = value instanceof Float ? (Float) value : 1f;
            if (!handoffStarted[0]
                    && progress < GESTURE_STACK_RELEASE_HANDOFF_START_PROGRESS) {
                recentsView.invalidate();
                return;
            }
            if (!handoffStarted[0]) {
                handoffStartTranslationX[0] = recentsView.getTranslationX();
                handoffStartTranslationY[0] = recentsView.getTranslationY();
                handoffStartScroll[0] = resolvePrimaryScroll(recentsView);
                beginGestureRecentsStackReleaseHandoff(
                        recentsView,
                        handoffStartScroll[0],
                        stackAnchorTargetScroll,
                        ensureRunningTaskScreenshot);
                handoffStarted[0] = true;
            }
            float handoffProgress = LauncherRecentsLayoutEngine.smoothStep(
                    LauncherRecentsLayoutEngine.remapProgress(
                            progress,
                            GESTURE_STACK_RELEASE_HANDOFF_START_PROGRESS,
                            1f));
            setForcedRecentsTranslationX(recentsView, LauncherRecentsLayoutEngine.lerp(
                    handoffStartTranslationX[0],
                    0f,
                    handoffProgress));
            setForcedRecentsTranslationY(recentsView, LauncherRecentsLayoutEngine.lerp(
                    handoffStartTranslationY[0],
                    0f,
                    handoffProgress));
            setGestureRecentsStackReleaseProgress(recentsView, handoffProgress);
            LauncherRecentsPerf.flow("enter:gestureRelease:frame",
                    recentsView,
                    "progress=" + progress
                            + " handoffProgress=" + handoffProgress);
            applyAppToRecentsStackAnchorScroll(
                    recentsView,
                    handoffStartScroll[0],
                    stackAnchorTargetScroll,
                    handoffProgress);
            LauncherRecentsPerf.hit("animationFrame:gestureRelease", recentsView);
            LauncherRecentsLayoutEngine.requestStackLayout(
                    recentsView,
                    "gestureReleaseFrame",
                    false,
                    false);
            recentsView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                LauncherRecentsPerf.flow("enter:gestureRelease:cancel", recentsView);
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.remove(recentsView);
                if (cancelled) {
                    LauncherRecentsPerf.flow("enter:gestureRelease:endCancelled",
                            recentsView);
                    markGestureRecentsStackReleaseHandoffPending(recentsView, false);
                    clearGestureRecentsStackReleaseProgress(recentsView);
                    clearForcedRecentsTranslationX(recentsView);
                    clearForcedRecentsTranslationY(recentsView);
                    LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
                    return;
                }
                if (!handoffStarted[0]) {
                    handoffStartScroll[0] = resolvePrimaryScroll(recentsView);
                    LauncherRecentsPerf.flow("enter:gestureRelease:forceHandoffAtEnd",
                            recentsView);
                    beginGestureRecentsStackReleaseHandoff(
                            recentsView,
                            handoffStartScroll[0],
                            stackAnchorTargetScroll,
                            ensureRunningTaskScreenshot);
                    handoffStarted[0] = true;
                }
                markGestureRecentsStackReleaseHandoffPending(recentsView, false);
                setForcedRecentsTranslationX(recentsView, 0f);
                setForcedRecentsTranslationY(recentsView, 0f);
                setGestureRecentsStackReleaseProgress(recentsView, 1f);
                normalizeAppToRecentsStackAnchor(recentsView, stackAnchorPage);
                LauncherRecentsState.setGestureStackReleasedStable(recentsView, true);
                clearGestureRecentsStackReleaseProgress(recentsView);
                LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15, true);
                recentsView.invalidate();
                LauncherRecentsPerf.flow("enter:gestureRelease:end", recentsView);
            }
        });
        LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.put(recentsView, animator);
        if (animatorSet != null) {
            animatorSet.play(animator);
        } else {
            animator.start();
        }
    }

    private static void clearEntryStateForBlankTapHomeExit(
            View recentsView,
            boolean cancelGestureRelease) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("leave:blankTap:clearEntryState",
                recentsView, "cancelGestureRelease=" + cancelGestureRelease);
        if (cancelGestureRelease) {
            cancelGestureRecentsStackReleaseAnimation(recentsView, true);
        }
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
        LauncherRecentsState.clearAppToRecentsEntryState(recentsView);
        clearForcedRecentsTranslationX(recentsView);
        clearForcedRecentsTranslationY(recentsView);
        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
        LauncherRecentsTouchController.clearStackAppFlowVisibilityCache();
    }

    private static void beginGestureRecentsStackReleaseHandoff(
            View recentsView,
            int stackAnchorStartScroll,
            int stackAnchorTargetScroll,
            boolean ensureRunningTaskScreenshot) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:handoffBegin",
                recentsView,
                "startScroll=" + stackAnchorStartScroll
                        + " targetScroll=" + stackAnchorTargetScroll
                        + " ensureScreenshot=" + ensureRunningTaskScreenshot);
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        if (ensureRunningTaskScreenshot) {
            prepareRunningTaskScreenshotForStackRelease(recentsView);
        } else {
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "setEnableDrawingLiveTile",
                    LauncherRecentsCompat.BOOLEAN_ARG,
                    false);
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskHidden",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "forceFinishScroller",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsState.clearAppToRecentsEntryState(recentsView);
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
        LauncherRecentsLayoutEngine.captureGestureStackReleaseTaskStates(
                recentsView,
                stackAnchorStartScroll,
                stackAnchorTargetScroll);
        setGestureRecentsStackReleaseProgress(recentsView, 0f);
        LauncherRecentsLayoutEngine.applyStackLayout(
                recentsView,
                false,
                "gestureReleaseHandoffBegin",
                true);
        recentsView.invalidate();
    }

    private static void prepareRunningTaskScreenshotForStackRelease(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:prepareScreenshot", recentsView);
        Runnable finishRunnable = () -> {
            LauncherRecentsPerf.flow("enter:gestureRelease:screenshotReady",
                    recentsView);
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "setEnableDrawingLiveTile",
                    LauncherRecentsCompat.BOOLEAN_ARG,
                    false);
            finishRunningTaskRecentsAnimation(recentsView);
        };
        if (!LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "switchToScreenshot",
                new Class<?>[]{Runnable.class},
                finishRunnable)) {
            LauncherRecentsPerf.flow("enter:gestureRelease:screenshotFallback",
                    recentsView);
            LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "setRunningTaskViewShowScreenshot",
                    LauncherRecentsCompat.BOOLEAN_ARG,
                    true);
            finishRunnable.run();
        }
    }

    private static int resolveAppToRecentsStackAnchorPage(View recentsView) {
        if (recentsView == null) {
            return -1;
        }
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (pageCount <= 0) {
            return -1;
        }
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        if (runningTaskObject instanceof View && recentsView instanceof ViewGroup) {
            int runningTaskPage = ((ViewGroup) recentsView).indexOfChild((View) runningTaskObject);
            if (runningTaskPage > 0) {
                return runningTaskPage - 1;
            }
            if (runningTaskPage == 0) {
                return 0;
            }
        }
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        return Math.max(0, Math.min(currentPage, pageCount - 1));
    }

    private static void normalizeAppToRecentsStackAnchor(View recentsView, int anchorPage) {
        if (recentsView == null || anchorPage < 0) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:normalizeAnchor",
                recentsView, "anchorPage=" + anchorPage);
        setPrimaryScroll(recentsView, resolveScrollForPage(
                recentsView,
                anchorPage,
                resolvePrimaryScroll(recentsView)));
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPage", anchorPage);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentScrollOverPage", anchorPage);
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", anchorPage);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPageScrollDiff", 0);
    }

    private static void applyAppToRecentsStackAnchorScroll(
            View recentsView,
            int startScroll,
            int targetScroll,
            float progress) {
        if (recentsView == null || startScroll == targetScroll) {
            return;
        }
        int primaryScroll = Math.round(LauncherRecentsLayoutEngine.lerp(
                startScroll,
                targetScroll,
                progress));
        LauncherRecentsPerf.flow("enter:gestureRelease:anchorScroll",
                recentsView,
                "startScroll=" + startScroll
                        + " targetScroll=" + targetScroll
                        + " progress=" + progress
                        + " primaryScroll=" + primaryScroll);
        setPrimaryScroll(recentsView, primaryScroll);
    }

    private static int resolveScrollForPage(View recentsView, int page, int fallback) {
        if (recentsView == null || page < 0) {
            return fallback;
        }
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                fallback,
                page);
    }

    private static int resolvePrimaryScroll(View recentsView) {
        if (recentsView == null) {
            return 0;
        }
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                recentsView);
        return value instanceof Integer ? (Integer) value : recentsView.getScrollX();
    }

    private static void setPrimaryScroll(View recentsView, int primaryScroll) {
        if (recentsView == null) {
            return;
        }
        if (isPrimaryScrollHorizontal(recentsView)) {
            recentsView.scrollTo(primaryScroll, recentsView.getScrollY());
        } else {
            recentsView.scrollTo(recentsView.getScrollX(), primaryScroll);
        }
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

    static void finishRunningTaskRecentsAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:finishRunningTaskAnimation",
                recentsView);
        LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "finishRecentsAnimation",
                new Class<?>[]{boolean.class, boolean.class, Runnable.class},
                true,
                false,
                null);
    }

    private static void setPageAnimOffScreenStart(View recentsView, boolean value) {
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setPageAnimOffScreenStart",
                LauncherRecentsCompat.BOOLEAN_ARG,
                value);
    }

    private static boolean shouldUsePendingGestureRecentsStackRelease(
            View recentsView,
            Object endTarget) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && isRecentsGestureEndTarget(endTarget);
    }

    private static boolean isPendingGestureRecentsStackRelease(View recentsView) {
        return LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView);
    }

    private static boolean shouldSuppressLiveTileForStack(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
    }

    private static void markPendingGestureRecentsStackRelease(View recentsView, boolean active) {
        LauncherRecentsPerf.flow("enter:pendingGestureRelease",
                recentsView, "active=" + active);
        LauncherRecentsState.setPendingGestureRecentsStackRelease(recentsView, active);
    }

    static boolean isGestureRecentsStackReleaseHandoffPending(View recentsView) {
        return LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(recentsView);
    }

    private static void markGestureRecentsStackReleaseHandoffPending(
            View recentsView,
            boolean active) {
        LauncherRecentsPerf.flow("enter:gestureReleaseHandoffPending",
                recentsView, "active=" + active);
        LauncherRecentsState.setPendingGestureRecentsStackReleaseHandoff(recentsView, active);
    }

    private static View resolveHandlerRecentsView(Object handler) {
        Object value = LauncherRecentsCompat.getFieldCompat(handler, "mRecentsView");
        return value instanceof View ? (View) value : null;
    }

    static void setGestureRecentsStackReleaseProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:setProgress",
                recentsView, "progress=" + progress);
        LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.put(
                recentsView,
                LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f));
    }

    static void clearGestureRecentsStackReleaseProgress(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("enter:gestureRelease:clearProgress", recentsView);
        LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.remove(recentsView);
    }

    private static void setForcedRecentsTranslationY(View recentsView, float translationY) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.put(recentsView, translationY);
        recentsView.setTranslationY(translationY);
    }

    private static void setForcedRecentsTranslationX(View recentsView, float translationX) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_XS.put(recentsView, translationX);
        recentsView.setTranslationX(translationX);
    }

    private static void clearForcedRecentsTranslationY(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.remove(recentsView);
    }

    private static void clearForcedRecentsTranslationX(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_XS.remove(recentsView);
    }

    static void forceRecentsTranslationZero(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_XS.put(recentsView, 0f);
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.put(recentsView, 0f);
        recentsView.setTranslationX(0f);
        recentsView.setTranslationY(0f);
    }

    private static void applyForcedRecentsTranslation(View recentsView) {
        Float translationX = recentsView != null
                ? LauncherRecentsState.FORCED_RECENTS_TRANSLATION_XS.get(recentsView)
                : null;
        if (translationX != null) {
            recentsView.setTranslationX(translationX);
        }
        Float translationY = recentsView != null
                ? LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.get(recentsView)
                : null;
        if (translationY != null) {
            recentsView.setTranslationY(translationY);
        }
    }
}
