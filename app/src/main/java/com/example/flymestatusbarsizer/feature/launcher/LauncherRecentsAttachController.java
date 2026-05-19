package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.List;

final class LauncherRecentsAttachController {
    private static final String ABS_SWIPE_UP_HANDLER_CLASS =
            "com.android.quickstep.AbsSwipeUpHandler";
    private static final String DEFAULT_ANIMATION_FACTORY_CLASS =
            "com.android.quickstep.BaseActivityInterface$DefaultAnimationFactory";
    private static final String LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState";
    private static final int RECENTS_ATTACH_STATE_ELEMENT_INDEX = 4;

    private LauncherRecentsAttachController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewGestureAnimationStart(module, loader);
        hookRecentsViewMoveRunningTaskToExpectedPosition(module, loader);
        hookLauncherRecentsViewApplyLoadPlan(module, loader);
        hookAbsSwipeUpHandlerInitialRecentsAttach(module, loader);
        hookFlymeRecentsAttach(module, loader);
    }

    private static void hookRecentsViewGestureAnimationStart(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> groupedTaskInfoClass =
                    Class.forName("com.android.wm.shell.shared.GroupedTaskInfo", false, loader);
            Method method = clazz.getDeclaredMethod("onGestureAnimationStart", groupedTaskInfoClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject()
                        : null;
                if (recentsView == null
                        || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
                    return chain.proceed();
                }
                LauncherRecentsState.trackRecentsView(recentsView);
                LauncherRecentsState.setPendingInitialAppToRecentsReorder(recentsView, true);
                prepareRecentsForOverviewEntry(recentsView);
                LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                Object result = chain.proceed();
                applyImmediateStackEntryTakeover(recentsView, loader);
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onGestureAnimationStart",
                    t);
        }
    }

    private static void hookRecentsViewMoveRunningTaskToExpectedPosition(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("moveRunningTaskToExpectedPosition");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject()
                        : null;
                if (recentsView == null
                        || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                        || !LauncherRecentsState.hasPendingInitialAppToRecentsReorder(recentsView)) {
                    return chain.proceed();
                }
                if (moveRunningTaskToExpectedPositionWithoutReset(recentsView)) {
                    LauncherRecentsState.setPendingInitialAppToRecentsReorder(recentsView, false);
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.moveRunningTaskToExpectedPosition",
                    t);
        }
    }

    private static void hookLauncherRecentsViewApplyLoadPlan(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    LauncherRecentsCompat.LAUNCHER_RECENTS_VIEW_CLASS,
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("applyLoadPlan", List.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                View recentsView = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject()
                        : null;
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
                    LauncherRecentsState.trackRecentsView(recentsView);
                    applyImmediateStackEntryTakeover(recentsView, loader);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook LauncherRecentsView.applyLoadPlan",
                    t);
        }
    }

    private static void hookAbsSwipeUpHandlerInitialRecentsAttach(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(ABS_SWIPE_UP_HANDLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "maybeUpdateRecentsAttachedState",
                    boolean.class,
                    boolean.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                boolean animate = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                boolean updateRunningTaskAlpha =
                        chain.getArg(2) instanceof Boolean && (Boolean) chain.getArg(2);
                if (!shouldTakeOverInitialAppToRecentsAttach(thisObject, animate, loader)) {
                    return chain.proceed();
                }
                handleInitialAppToRecentsAttachTakeover(
                        thisObject,
                        updateRunningTaskAlpha,
                        loader);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook AbsSwipeUpHandler.maybeUpdateRecentsAttachedState",
                    t);
        }
    }

    private static void hookFlymeRecentsAttach(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(DEFAULT_ANIMATION_FACTORY_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "setRecentsAttachedToAppWindowForFlyme",
                    boolean.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                boolean attached = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                View recentsView = resolveRecentsView(thisObject);
                if (!shouldTakeOverAppToRecentsAttach(
                        thisObject,
                        recentsView,
                        attached,
                        loader)) {
                    return chain.proceed();
                }
                applyStackAttachTakeover(thisObject, recentsView, loader);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook BaseActivityInterface.DefaultAnimationFactory.setRecentsAttachedToAppWindowForFlyme",
                    t);
        }
    }

    private static boolean shouldTakeOverAppToRecentsAttach(
            Object factory,
            View recentsView,
            boolean attached,
            ClassLoader loader) {
        if (!attached
                || factory == null
                || recentsView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        Object targetState = resolveTargetState(factory);
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        return targetState == overviewState;
    }

    static void applyStackAttachTakeover(
            Object factory,
            View recentsView,
            ClassLoader loader) {
        LauncherRecentsState.setPendingInitialAppToRecentsReorder(recentsView, false);
        updateFactoryAttachState(factory, true);
        cancelAttachStateElementAnimation(factory);
        LauncherRecentsState.PENDING_GESTURE_RECENTS_STACK_RELEASES.put(
                recentsView,
                Boolean.TRUE);
        LauncherRecentsState.trackRecentsView(recentsView);
        prepareRecentsForOverviewEntry(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setPageAnimOffScreenStart",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.setStaticFloatPropertyCompat(
                LauncherRecentsCompat.RECENTS_VIEW_CLASS,
                "ADJACENT_PAGE_HORIZONTAL_OFFSET",
                loader,
                recentsView,
                0f);
        LauncherRecentsCompat.setStaticFloatPropertyCompat(
                LauncherRecentsCompat.RECENTS_VIEW_CLASS,
                "ADJACENT_PAGE_SCALE",
                loader,
                recentsView,
                0f);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setContentAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                1f);
        LauncherRecentsTransitionController.finishRunningTaskReleaseToStack(recentsView);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static boolean shouldTakeOverInitialAppToRecentsAttach(
            Object handler,
            boolean animate,
            ClassLoader loader) {
        if (animate || handler == null) {
            return false;
        }
        View recentsView = resolveHandlerRecentsView(handler);
        if (recentsView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        Object animationFactory = LauncherRecentsCompat.getFieldCompat(handler, "mAnimationFactory");
        if (!isOverviewActivityAttachFactory(animationFactory, loader)) {
            return false;
        }
        Object gestureState = LauncherRecentsCompat.getFieldCompat(handler, "mGestureState");
        Object endTarget = LauncherRecentsCompat.invokeCompat(gestureState, "getEndTarget");
        if (endTarget != null) {
            return false;
        }
        if (isReachedToForceHideOverviewFactor(handler)) {
            return false;
        }
        return shouldAttachRecentsBeforeEndTarget(handler, recentsView);
    }

    private static void handleInitialAppToRecentsAttachTakeover(
            Object handler,
            boolean updateRunningTaskAlpha,
            ClassLoader loader) {
        View recentsView = resolveHandlerRecentsView(handler);
        Object animationFactory = LauncherRecentsCompat.getFieldCompat(handler, "mAnimationFactory");
        if (recentsView == null
                || animationFactory == null
                || !shouldAttachRecentsBeforeEndTarget(handler, recentsView)) {
            return;
        }
        LauncherRecentsCompat.writeField(
                handler,
                "mDeferredSetRecentsAttachedToAppWindow",
                Boolean.TRUE);
        boolean hasEverAttached =
                LauncherRecentsCompat.invokeBoolean(
                        animationFactory,
                        "hasRecentsEverAttachedToAppWindow",
                        false);
        prepareRecentsForOverviewEntry(recentsView);
        if (updateRunningTaskAlpha && !hasEverAttached) {
            LauncherRecentsState.setPendingInitialAppToRecentsReorder(recentsView, true);
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "moveRunningTaskToExpectedPosition",
                    LauncherRecentsCompat.NO_ARGS);
        }
        applyStackAttachTakeover(animationFactory, recentsView, loader);
        LauncherRecentsCompat.invokeCompat(
                handler,
                "applyScrollAndTransform",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
    }

    private static boolean shouldAttachRecentsBeforeEndTarget(
            Object handler,
            View recentsView) {
        if (handler == null || recentsView == null) {
            return false;
        }
        boolean continuingLastGesture =
                LauncherRecentsCompat.readBooleanField(handler, "mContinuingLastGesture", false);
        if (continuingLastGesture) {
            int runningTaskIndex = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getRunningTaskIndex",
                    -1);
            int nextPage = LauncherRecentsCompat.invokeInt(recentsView, "getNextPage", -1);
            if (runningTaskIndex != nextPage) {
                Object gestureState = LauncherRecentsCompat.getFieldCompat(handler, "mGestureState");
                boolean parallelGesture =
                        LauncherRecentsCompat.invokeBoolean(
                                gestureState,
                                "isParallelAnimGesture",
                                false);
                return !parallelGesture;
            }
        }
        Object topTarget = resolveTopRunningTaskTarget(handler);
        if (isRemoteTargetNotInRecents(topTarget)) {
            return true;
        }
        boolean hasMotionEverBeenPaused =
                LauncherRecentsCompat.readBooleanField(handler, "mHasMotionEverBeenPaused", false);
        boolean likelyToStartNewTask =
                LauncherRecentsCompat.readBooleanField(handler, "mIsLikelyToStartNewTask", false);
        boolean deferredAttach =
                LauncherRecentsCompat.readBooleanField(
                        handler,
                        "mDeferredSetRecentsAttachedToAppWindow",
                        false);
        return !hasMotionEverBeenPaused && !likelyToStartNewTask && deferredAttach;
    }

    private static boolean moveRunningTaskToExpectedPositionWithoutReset(View recentsView) {
        if (!(recentsView instanceof ViewGroup)) {
            return false;
        }
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        if (!(runningTaskObject instanceof View)) {
            return false;
        }
        View runningTaskView = (View) runningTaskObject;
        int currentPage = LauncherRecentsCompat.readIntField(recentsView, "mCurrentPage", -1);
        ViewGroup group = (ViewGroup) recentsView;
        int currentIndex = group.indexOfChild(runningTaskView);
        int expectedIndex = resolveRunningTaskExpectedIndex(recentsView, runningTaskView);
        if (currentPage != currentIndex || currentPage == expectedIndex || expectedIndex < 0) {
            return false;
        }
        Object pagedOrientationHandler =
                LauncherRecentsCompat.invokeCompat(recentsView, "getPagedOrientationHandler");
        int primaryScroll = LauncherRecentsCompat.invokeInt(
                pagedOrientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                0,
                recentsView);
        int currentPageScroll = LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                0,
                currentPage);
        LauncherRecentsCompat.writeField(
                recentsView,
                "mCurrentPageScrollDiff",
                Integer.valueOf(primaryScroll - currentPageScroll));
        LauncherRecentsCompat.writeField(recentsView, "mMovingTaskView", runningTaskView);
        group.removeView(runningTaskView);
        LauncherRecentsCompat.writeField(recentsView, "mMovingTaskView", null);
        group.addView(runningTaskView, expectedIndex);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setCurrentPage",
                LauncherRecentsCompat.INT_ARG,
                expectedIndex);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "updateTaskSize",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        recentsView.requestLayout();
        recentsView.invalidate();
        return true;
    }

    private static int resolveRunningTaskExpectedIndex(View recentsView, View runningTaskView) {
        Object utils = LauncherRecentsCompat.getFieldCompat(recentsView, "mUtils");
        if (utils == null || runningTaskView == null) {
            return -1;
        }
        Class<?> taskViewClass;
        try {
            taskViewClass = Class.forName(
                    LauncherRecentsCompat.TASK_VIEW_CLASS,
                    false,
                    recentsView.getClass().getClassLoader());
        } catch (Throwable ignored) {
            taskViewClass = runningTaskView.getClass();
        }
        return LauncherRecentsCompat.invokeInt(
                utils,
                "getRunningTaskExpectedIndex",
                new Class<?>[]{taskViewClass},
                -1,
                runningTaskView);
    }

    private static void applyImmediateStackEntryTakeover(
            View recentsView,
            ClassLoader loader) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setPageAnimOffScreenStart",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.setStaticFloatPropertyCompat(
                LauncherRecentsCompat.RECENTS_VIEW_CLASS,
                "ADJACENT_PAGE_HORIZONTAL_OFFSET",
                loader,
                recentsView,
                0f);
        LauncherRecentsCompat.setStaticFloatPropertyCompat(
                LauncherRecentsCompat.RECENTS_VIEW_CLASS,
                "ADJACENT_PAGE_SCALE",
                loader,
                recentsView,
                0f);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static void prepareRecentsForOverviewEntry(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "snapToPageImmediately",
                LauncherRecentsCompat.INT_ARG,
                0);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setOverviewStateEnabled",
                LauncherRecentsCompat.BOOLEAN_ARG,
                true);
    }

    private static View resolveRecentsView(Object factory) {
        Object activity = LauncherRecentsCompat.getFieldCompat(factory, "mActivity");
        Object overviewPanel = LauncherRecentsCompat.invokeCompat(activity, "getOverviewPanel");
        return overviewPanel instanceof View ? (View) overviewPanel : null;
    }

    private static View resolveHandlerRecentsView(Object handler) {
        Object value = LauncherRecentsCompat.getFieldCompat(handler, "mRecentsView");
        return value instanceof View ? (View) value : null;
    }

    private static Object resolveTargetState(Object factory) {
        Object outer = LauncherRecentsCompat.getFieldCompat(factory, "this$0");
        return LauncherRecentsCompat.getFieldCompat(outer, "mTargetState");
    }

    private static boolean isOverviewActivityAttachFactory(Object factory, ClassLoader loader) {
        if (factory == null || loader == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(DEFAULT_ANIMATION_FACTORY_CLASS, false, loader);
            if (!clazz.isInstance(factory)) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
        Object targetState = resolveTargetState(factory);
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        return targetState == overviewState;
    }

    private static Object resolveTopRunningTaskTarget(Object handler) {
        Object targets = LauncherRecentsCompat.getFieldCompat(handler, "mRecentsAnimationTargets");
        if (targets == null) {
            return null;
        }
        Object gestureState = LauncherRecentsCompat.getFieldCompat(handler, "mGestureState");
        int topRunningTaskId =
                LauncherRecentsCompat.invokeInt(gestureState, "getTopRunningTaskId", -1);
        if (topRunningTaskId == -1) {
            return null;
        }
        return LauncherRecentsCompat.invokeCompat(
                targets,
                "findTask",
                LauncherRecentsCompat.INT_ARG,
                topRunningTaskId);
    }

    private static boolean isRemoteTargetNotInRecents(Object target) {
        if (target == null) {
            return false;
        }
        if (LauncherRecentsCompat.readBooleanField(target, "isNotInRecents", false)) {
            return true;
        }
        Object windowConfiguration = LauncherRecentsCompat.getFieldCompat(target, "windowConfiguration");
        int activityType =
                LauncherRecentsCompat.invokeInt(windowConfiguration, "getActivityType", 0);
        return activityType == 2;
    }

    private static boolean isReachedToForceHideOverviewFactor(Object handler) {
        Object currentShift = LauncherRecentsCompat.getFieldCompat(handler, "mCurrentShift");
        float shiftValue = LauncherRecentsCompat.readFloatField(currentShift, "value", 0f);
        float forceHideFactor =
                LauncherRecentsCompat.readFloatField(handler, "mForceHideOverViewFactor", 0f);
        return shiftValue >= forceHideFactor;
    }

    private static void updateFactoryAttachState(Object factory, boolean attached) {
        if (factory == null) {
            return;
        }
        LauncherRecentsCompat.writeField(factory, "mIsAttachedToWindow", attached);
        if (attached) {
            LauncherRecentsCompat.writeField(factory, "mHasEverAttachedToWindow", Boolean.TRUE);
        }
    }

    private static void cancelAttachStateElementAnimation(Object factory) {
        Object activity = LauncherRecentsCompat.getFieldCompat(factory, "mActivity");
        Object stateManager = LauncherRecentsCompat.invokeCompat(activity, "getStateManager");
        LauncherRecentsCompat.invokeCompat(
                stateManager,
                "cancelStateElementAnimation",
                LauncherRecentsCompat.INT_ARG,
                RECENTS_ATTACH_STATE_ELEMENT_INDEX);
    }
}
