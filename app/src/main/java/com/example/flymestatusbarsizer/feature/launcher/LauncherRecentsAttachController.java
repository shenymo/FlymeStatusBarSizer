package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.view.View;

import java.lang.reflect.Method;
import java.util.List;

final class LauncherRecentsAttachController {
    private static final String ABS_SWIPE_UP_HANDLER_CLASS =
            "com.android.quickstep.AbsSwipeUpHandler";
    private static final String DEFAULT_ANIMATION_FACTORY_CLASS =
            "com.android.quickstep.BaseActivityInterface$DefaultAnimationFactory";
    private static final String LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState";
    private static final String FLYME_LAUNCHER_STATE_CLASS =
            "com.meizu.flyme.launcher.FlymeLauncherState";

    private LauncherRecentsAttachController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewGestureAnimationStart(module, loader);
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
                LauncherRecentsPerf.flow("attach:gestureAnimationStart", recentsView);
                LauncherRecentsPerf.startSpan("enterRecents", recentsView);
                LauncherRecentsLaunchController.clearTaskLaunchFrozenForNewGesture(recentsView);
                LauncherRecentsTransitionController.cancelGestureRecentsStackReleaseAnimation(
                        recentsView,
                        true);
                LauncherRecentsStateAnimationController.clearOverviewEntryState(recentsView);
                LauncherRecentsLayoutEngine.hideStackClearAllButton(recentsView);
                LauncherRecentsState.clearAppToRecentsGestureState(recentsView);
                LauncherRecentsState.setSwipeUpGestureActive(recentsView, true);
                LauncherRecentsState.setPositionOwner(
                        recentsView,
                        LauncherRecentsState.POSITION_OWNER_ENTER);
                LauncherRecentsTouchController.clearStackAppFlowVisibilityCache();
                LauncherRecentsState.trackRecentsView(recentsView);
                long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                try {
                    return chain.proceed();
                } finally {
                    LauncherRecentsPerf.end("native:onGestureAnimationStart", nativeStartNs);
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onGestureAnimationStart",
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
                View recentsView = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject()
                        : null;
                long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                Object result;
                try {
                    result = chain.proceed();
                } finally {
                    LauncherRecentsPerf.end("native:applyLoadPlan", nativeStartNs);
                }
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
                    LauncherRecentsPerf.flow("attach:applyLoadPlan",
                            recentsView,
                            "deferred=" + LauncherRecentsState
                                    .isAppToRecentsStackLayoutDeferred(recentsView));
                    LauncherRecentsTouchController.clearStackAppFlowVisibilityCache();
                    LauncherRecentsState.trackRecentsView(recentsView);
                    if (!LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)) {
                        LauncherRecentsPerf.flow("attach:applyLoadPlan:applyDynamic",
                                recentsView);
                        LauncherRecentsLayoutEngine.requestStackLayout(
                                recentsView,
                                "applyLoadPlan",
                                true);
                    }
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
                View recentsView = resolveHandlerRecentsView(thisObject);
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                        && LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)) {
                    LauncherRecentsPerf.flow("attach:maybeUpdateAttachedState",
                            recentsView,
                            "arg0=" + chain.getArg(0)
                                    + " arg1=" + chain.getArg(1)
                                    + " arg2=" + chain.getArg(2));
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                }
                return chain.proceed();
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
                LauncherRecentsPerf.flow("attach:setRecentsAttached",
                        recentsView,
                        "attached=" + attached + " animate=" + chain.getArg(1));
                if (!attached) {
                    if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
                        LauncherRecentsPerf.flow("attach:setRecentsAttached:skipSwipeUp",
                                recentsView);
                        return chain.proceed();
                    }
                    // Release 结束后系统还会 detach，需保留 stable 状态避免下一帧读到未收敛偏移。
                    boolean shouldKeepDeferred =
                            LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                                    || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                                    || LauncherRecentsTransitionController
                                    .isGestureRecentsStackReleaseAnimationActive(recentsView)
                                    || LauncherRecentsState.isAppToRecentsStackSettled(recentsView);
                    if (shouldKeepDeferred) {
                        LauncherRecentsPerf.flow("attach:setRecentsAttached:keepDeferred",
                                recentsView);
                        LauncherRecentsState.trackRecentsView(recentsView);
                        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                        return chain.proceed();
                    }
                    LauncherRecentsPerf.flow("attach:setRecentsAttached:clearEntry",
                            recentsView);
                    clearAppToRecentsEntrySession(recentsView, false);
                    return chain.proceed();
                }

                if (!shouldAugmentAppToRecentsAttach(
                        thisObject,
                        recentsView,
                        attached,
                        loader)) {
                    LauncherRecentsPerf.flow("attach:setRecentsAttached:stock",
                            recentsView);
                    return chain.proceed();
                }
                if (LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)) {
                    LauncherRecentsPerf.flow("attach:setRecentsAttached:skipOverviewPeek",
                            recentsView);
                    LauncherRecentsCompat.writeField(thisObject, "mIsAttachedToWindow", true);
                    LauncherRecentsCompat.writeField(thisObject, "mHasEverAttachedToWindow", true);
                    return null;
                }
                if (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)) {
                    LauncherRecentsPerf.flow("attach:setRecentsAttached:prepareDeferred",
                            recentsView);
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook BaseActivityInterface.DefaultAnimationFactory.setRecentsAttachedToAppWindowForFlyme",
                    t);
        }
    }

    private static boolean shouldAugmentAppToRecentsAttach(
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
        return isOverviewState(targetState, loader)
                || isOverviewPeekStateActive(resolveFactoryActivity(factory), loader);
    }

    static void clearAppToRecentsEntrySession(View recentsView, boolean keepExpanded) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("attach:entrySession:clear",
                recentsView, "keepExpanded=" + keepExpanded);
        LauncherRecentsTransitionController.cancelGestureRecentsStackReleaseAnimation(
                recentsView,
                true);
        if (!keepExpanded) {
            LauncherRecentsState.setAppToRecentsStackSettled(recentsView, false);
        }
        endAppToRecentsEntrySessionWithoutLayout(recentsView);
        LauncherRecentsLayoutEngine.restoreStackLayout(recentsView, "appEntryClearRestore");
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    static void endAppToRecentsEntrySessionWithoutLayout(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("attach:entrySession:endWithoutLayout", recentsView);
        LauncherRecentsState.clearAppToRecentsEntryState(recentsView);
        LauncherRecentsTouchController.clearStackAppFlowVisibilityCache();
    }

    private static View resolveRecentsView(Object factory) {
        Object activity = resolveFactoryActivity(factory);
        Object overviewPanel = LauncherRecentsCompat.invokeCompat(activity, "getOverviewPanel");
        return overviewPanel instanceof View ? (View) overviewPanel : null;
    }

    private static Object resolveFactoryActivity(Object factory) {
        return LauncherRecentsCompat.getFieldCompat(factory, "mActivity");
    }

    private static View resolveHandlerRecentsView(Object handler) {
        Object value = LauncherRecentsCompat.getFieldCompat(handler, "mRecentsView");
        return value instanceof View ? (View) value : null;
    }

    private static Object resolveTargetState(Object factory) {
        Object outer = LauncherRecentsCompat.getFieldCompat(factory, "this$0");
        return LauncherRecentsCompat.getFieldCompat(outer, "mTargetState");
    }

    private static boolean isOverviewState(Object value, ClassLoader loader) {
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        return overviewState != null && value == overviewState;
    }

    private static boolean isOverviewPeekState(Object value, ClassLoader loader) {
        Object overviewPeekState = LauncherRecentsCompat.readStaticFieldCompat(
                FLYME_LAUNCHER_STATE_CLASS,
                "OVERVIEW_PEEK",
                loader);
        return overviewPeekState != null && value == overviewPeekState;
    }

    private static boolean isOverviewPeekStateActive(Object activity, ClassLoader loader) {
        if (activity == null || loader == null) {
            return false;
        }
        Object stateManager = LauncherRecentsCompat.invokeCompat(activity, "getStateManager");
        Object currentState = LauncherRecentsCompat.invokeCompat(stateManager, "getState");
        Object stableState = LauncherRecentsCompat.invokeCompat(stateManager, "getCurrentStableState");
        Object targetState = LauncherRecentsCompat.invokeCompat(stateManager, "getTargetState");
        return isOverviewPeekState(currentState, loader)
                || isOverviewPeekState(stableState, loader)
                || isOverviewPeekState(targetState, loader);
    }

}
