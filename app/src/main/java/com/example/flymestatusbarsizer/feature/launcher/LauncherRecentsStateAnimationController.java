package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;

import java.lang.reflect.Method;

final class LauncherRecentsStateAnimationController {
    private static final String RECENTS_VIEW_STATE_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.RecentsViewStateController";
    private static final String LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState";
    private static final String STATE_ANIMATION_CONFIG_CLASS =
            "com.android.launcher3.states.StateAnimationConfig";
    private static final String PENDING_ANIMATION_CLASS =
            "com.android.launcher3.anim.PendingAnimation";
    private static final String FLYME_LAUNCHER_STATE_CLASS =
            "com.meizu.flyme.launcher.FlymeLauncherState";
    private static final long OVERVIEW_STATE_STACK_ANIMATION_FALLBACK_CLEAR_DELAY_MS = 350L;

    private LauncherRecentsStateAnimationController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewStateWithAnimation(module, loader);
        hookRecentsViewStateAnimationInternal(module, loader);
        hookRecentsViewStateImmediate(module, loader);
    }

    private static void hookRecentsViewStateWithAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_STATE_CONTROLLER_CLASS, false, loader);
            Class<?> launcherStateClass = Class.forName(LAUNCHER_STATE_CLASS, false, loader);
            Class<?> stateAnimationConfigClass =
                    Class.forName(STATE_ANIMATION_CONFIG_CLASS, false, loader);
            Class<?> pendingAnimationClass =
                    Class.forName(PENDING_ANIMATION_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "setStateWithAnimation",
                    launcherStateClass,
                    stateAnimationConfigClass,
                    pendingAnimationClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object toState = chain.getArg(0);
                Object pendingAnimation = chain.getArg(2);
                View recentsView = resolveControllerRecentsView(thisObject);
                boolean shouldTakeOver =
                        shouldTakeOverOverviewPeekToOverview(thisObject, recentsView, toState, loader);
                LauncherRecentsPerf.flow("state:setWithAnimation",
                        recentsView,
                        "toState=" + toState
                                + " takeOver=" + shouldTakeOver
                                + " pendingAnimation=" + (pendingAnimation != null));
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, pendingAnimation);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                prepareHomeExitFromRecentsIfNeeded(recentsView, toState, pendingAnimation, loader);
                Object result = chain.proceed();
                if (shouldAttachBlankTapHomeExitToSystemAnimation(recentsView, toState, loader)) {
                    LauncherRecentsPerf.flow("state:setWithAnimation:attachBlankTapSystem",
                            recentsView, "toState=" + toState);
                    attachBlankTapHomeExitSystemCallbacks(recentsView, pendingAnimation, loader);
                }
                if (shouldTakeOver) {
                    LauncherRecentsPerf.flow("state:setWithAnimation:applyDynamic",
                            recentsView);
                    LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsViewStateController.setStateWithAnimation",
                    t);
        }
    }

    private static void hookRecentsViewStateAnimationInternal(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_STATE_CONTROLLER_CLASS, false, loader);
            Class<?> launcherStateClass = Class.forName(LAUNCHER_STATE_CLASS, false, loader);
            Class<?> stateAnimationConfigClass =
                    Class.forName(STATE_ANIMATION_CONFIG_CLASS, false, loader);
            Class<?> pendingAnimationClass =
                    Class.forName(PENDING_ANIMATION_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "setStateWithAnimationInternalForFlyme",
                    launcherStateClass,
                    stateAnimationConfigClass,
                    pendingAnimationClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object toState = chain.getArg(0);
                Object pendingAnimation = chain.getArg(2);
                View recentsView = resolveControllerRecentsView(thisObject);
                boolean shouldTakeOver =
                        shouldTakeOverOverviewPeekToOverview(thisObject, recentsView, toState, loader);
                LauncherRecentsPerf.flow("state:setWithAnimationInternal",
                        recentsView,
                        "toState=" + toState
                                + " takeOver=" + shouldTakeOver
                                + " pendingAnimation=" + (pendingAnimation != null));
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, pendingAnimation);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                prepareHomeExitFromRecentsIfNeeded(recentsView, toState, pendingAnimation, loader);
                Object result = chain.proceed();
                if (shouldAttachBlankTapHomeExitToSystemAnimation(recentsView, toState, loader)) {
                    LauncherRecentsPerf.flow("state:setWithAnimationInternal:attachBlankTapSystem",
                            recentsView, "toState=" + toState);
                    attachBlankTapHomeExitSystemCallbacks(recentsView, pendingAnimation, loader);
                }
                if (shouldTakeOver) {
                    LauncherRecentsPerf.flow("state:setWithAnimationInternal:applyDynamic",
                            recentsView);
                    LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsViewStateController.setStateWithAnimationInternalForFlyme",
                    t);
        }
    }

    private static void hookRecentsViewStateImmediate(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_STATE_CONTROLLER_CLASS, false, loader);
            Class<?> launcherStateClass = Class.forName(LAUNCHER_STATE_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setState", launcherStateClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object toState = chain.getArg(0);
                View recentsView = resolveControllerRecentsView(thisObject);
                boolean shouldTakeOver =
                        shouldTakeOverOverviewPeekToOverview(thisObject, recentsView, toState, loader);
                LauncherRecentsPerf.flow("state:setImmediate",
                        recentsView,
                        "toState=" + toState + " takeOver=" + shouldTakeOver);
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, null);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                Object result = chain.proceed();
                if (shouldTakeOver) {
                    LauncherRecentsPerf.flow("state:setImmediate:applyAndClear",
                            recentsView);
                    LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                    clearOverviewStateStackAnimation(recentsView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsViewStateController.setState",
                    t);
        }
    }

    private static void attachOverviewStateAnimationCallbacks(View recentsView, Object pendingAnimation) {
        if (recentsView == null || pendingAnimation == null) {
            return;
        }
        LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addOnFrameCallback",
                new Class<?>[]{Runnable.class},
                (Runnable) () -> {
                    if (isOverviewStateStackAnimationActive(recentsView)) {
                        LauncherRecentsPerf.flow("state:overview:frame", recentsView);
                        LauncherRecentsPerf.hit("animationFrame:overviewState", recentsView);
                        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                    }
                });
        LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addListener",
                new Class<?>[]{Animator.AnimatorListener.class},
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        LauncherRecentsPerf.flow("state:overview:cancel", recentsView);
                        clearOverviewStateStackAnimation(recentsView);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        LauncherRecentsPerf.flow("state:overview:end", recentsView);
                        clearOverviewStateStackAnimation(recentsView);
                    }
                });
    }

    private static void attachBlankTapHomeExitSystemCallbacks(
            View recentsView,
            Object pendingAnimation,
            ClassLoader loader) {
        if (recentsView == null || pendingAnimation == null) {
            return;
        }
        LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addOnFrameListener",
                new Class<?>[]{ValueAnimator.AnimatorUpdateListener.class},
                (ValueAnimator.AnimatorUpdateListener) animation -> {
                    Object value = animation.getAnimatedValue();
                    float progress = value instanceof Float
                            ? (Float) value
                            : animation.getAnimatedFraction();
                    LauncherRecentsTransitionController.setBlankTapHomeExitProgress(
                            recentsView,
                            progress);
                    LauncherRecentsPerf.flow("leave:blankTapSystem:frame",
                            recentsView, "progress=" + progress);
                    LauncherRecentsPerf.hit("animationFrame:blankTapSystem", recentsView);
                    LauncherRecentsLayoutEngine.applyBlankTapHomeExitFrame(recentsView, progress);
                    recentsView.invalidate();
                });
        LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addListener",
                new Class<?>[]{Animator.AnimatorListener.class},
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        LauncherRecentsPerf.flow("leave:blankTapSystem:cancel",
                                recentsView);
                        finishBlankTapHomeExitSystemAnimation(recentsView);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        LauncherRecentsPerf.flow("leave:blankTapSystem:end",
                                recentsView);
                        finishBlankTapHomeExitSystemAnimation(recentsView);
                    }
                });
    }

    private static void finishBlankTapHomeExitSystemAnimation(View recentsView) {
        LauncherRecentsPerf.flow("leave:blankTapSystem:finish", recentsView);
        LauncherRecentsTransitionController.setBlankTapHomeExitProgress(recentsView, 1f);
        LauncherRecentsLayoutEngine.applyBlankTapHomeExitFrame(recentsView, 1f);
        LauncherRecentsTransitionController.clearBlankTapHomeExitProgressWithoutLayout(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setContentAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                0f);
    }

    private static void beginOverviewStateStackAnimation(
            View recentsView,
            Object pendingAnimation) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("state:overview:begin",
                recentsView, "pendingAnimation=" + (pendingAnimation != null));
        LauncherRecentsLayoutEngine.cancelStackLayoutRecovery(recentsView);
        markOverviewPeekStockAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackStartAdjacentOffset(
                recentsView,
                LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        0.53f));
        long perfStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
            LauncherRecentsState.setOverviewStateStackBaselineCaptured(recentsView, true);
        } finally {
            LauncherRecentsPerf.end("captureStockTaskStates:overviewBegin", perfStartNs);
        }
        markOverviewStateStackAnimation(recentsView, true);
        attachOverviewStateAnimationCallbacks(recentsView, pendingAnimation);
        if (pendingAnimation == null) {
            LauncherRecentsPerf.flow("state:overview:scheduleFallbackClear",
                    recentsView,
                    "delayMs=" + OVERVIEW_STATE_STACK_ANIMATION_FALLBACK_CLEAR_DELAY_MS);
            recentsView.postDelayed(() -> {
                if (isOverviewStateStackAnimationActive(recentsView)) {
                    LauncherRecentsPerf.flow("state:overview:runFallbackClear",
                            recentsView);
                    clearOverviewStateStackAnimation(recentsView);
                }
            }, OVERVIEW_STATE_STACK_ANIMATION_FALLBACK_CLEAR_DELAY_MS);
        }
    }

    private static boolean shouldTakeOverOverviewPeekToOverview(
            Object controller,
            View recentsView,
            Object toState,
            ClassLoader loader) {
        if (controller == null
                || recentsView == null
                || toState == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)) {
            return false;
        }
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        if (toState != overviewState) {
            return false;
        }
        Object overviewPeekState = LauncherRecentsCompat.readStaticFieldCompat(
                FLYME_LAUNCHER_STATE_CLASS,
                "OVERVIEW_PEEK",
                loader);
        if (overviewPeekState == null) {
            return false;
        }
        Object launcher = LauncherRecentsCompat.getFieldCompat(controller, "launcher");
        Object stateManager = LauncherRecentsCompat.invokeCompat(launcher, "getStateManager");
        Object currentState = LauncherRecentsCompat.invokeCompat(stateManager, "getState");
        Object stableState = LauncherRecentsCompat.invokeCompat(stateManager, "getCurrentStableState");
        Object targetState = LauncherRecentsCompat.invokeCompat(stateManager, "getTargetState");
        return isOverviewPeekStockAnimationActive(recentsView)
                || currentState == overviewPeekState
                || stableState == overviewPeekState
                || targetState == overviewPeekState;
    }

    private static boolean shouldAttachBlankTapHomeExitToSystemAnimation(
            View recentsView,
            Object toState,
            ClassLoader loader) {
        if (recentsView == null
                || toState == null
                || !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)) {
            return false;
        }
        Object normalState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "NORMAL", loader);
        return toState == normalState;
    }

    private static void prepareHomeExitFromRecentsIfNeeded(
            View recentsView,
            Object toState,
            Object pendingAnimation,
            ClassLoader loader) {
        if (pendingAnimation == null
                || recentsView == null
                || toState == null
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)) {
            return;
        }
        Object normalState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "NORMAL", loader);
        if (toState == normalState
                && LauncherRecentsTransitionController.shouldPrepareHomeExitFromRecents(
                recentsView)) {
            LauncherRecentsPerf.flow("leave:prepareHomeExitFromState",
                    recentsView, "toState=" + toState);
            LauncherRecentsTransitionController.prepareBlankTapHomeExitAnimation(recentsView);
        }
    }

    private static View resolveControllerRecentsView(Object controller) {
        Object value = LauncherRecentsCompat.getFieldCompat(controller, "recentsView");
        return value instanceof View ? (View) value : null;
    }

    static boolean isOverviewStateStackAnimationActive(View recentsView) {
        return LauncherRecentsState.isOverviewStateStackAnimationActive(recentsView);
    }

    static boolean isOverviewPeekStockAnimationActive(View recentsView) {
        return LauncherRecentsState.isOverviewPeekStockAnimationActive(recentsView);
    }

    static boolean shouldKeepOverviewPeekStockLayout(View recentsView) {
        return recentsView != null && isOverviewPeekStockAnimationActive(recentsView);
    }

    private static void updateOverviewPeekStockAnimation(
            View recentsView,
            Object toState,
            ClassLoader loader) {
        if (recentsView == null || toState == null) {
            return;
        }
        Object overviewPeekState = LauncherRecentsCompat.readStaticFieldCompat(
                FLYME_LAUNCHER_STATE_CLASS,
                "OVERVIEW_PEEK",
                loader);
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        if (toState == overviewPeekState) {
            LauncherRecentsPerf.flow("state:overviewPeek:markStock", recentsView);
            markOverviewPeekStockAnimation(recentsView, true);
        } else if (toState == overviewState) {
            LauncherRecentsPerf.flow("state:overviewPeek:clearStock", recentsView);
            markOverviewPeekStockAnimation(recentsView, false);
        } else {
            LauncherRecentsPerf.flow("state:overviewPeek:clearEntry",
                    recentsView, "toState=" + toState);
            clearOverviewEntryState(recentsView);
        }
    }

    private static void markOverviewStateStackAnimation(View recentsView, boolean active) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.setOverviewStateStackAnimationActive(recentsView, active);
    }

    private static void markOverviewPeekStockAnimation(View recentsView, boolean active) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.setOverviewPeekStockAnimationActive(recentsView, active);
    }

    private static void clearOverviewStateStackAnimation(View recentsView) {
        LauncherRecentsPerf.flow("state:overview:clear", recentsView);
        markOverviewPeekStockAnimation(recentsView, false);
        markOverviewStateStackAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackSettled(recentsView, true);
        LauncherRecentsLayoutEngine.startStackLayoutRecovery(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(
                recentsView,
                false,
                "overviewStateClearRestore",
                true);
        LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15);
    }

    static void clearOverviewEntryState(View recentsView) {
        LauncherRecentsPerf.flow("state:overview:clearEntry", recentsView);
        markOverviewPeekStockAnimation(recentsView, false);
        markOverviewStateStackAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackSettled(recentsView, false);
    }
}
