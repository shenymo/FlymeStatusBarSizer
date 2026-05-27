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
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, pendingAnimation);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                Object result = chain.proceed();
                if (shouldAttachBlankTapHomeExitToSystemAnimation(recentsView, toState, loader)) {
                    attachBlankTapHomeExitSystemCallbacks(recentsView, pendingAnimation, loader);
                }
                if (shouldTakeOver) {
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
                View recentsView = resolveControllerRecentsView(thisObject);
                boolean shouldTakeOver =
                        shouldTakeOverOverviewPeekToOverview(thisObject, recentsView, toState, loader);
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, null);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                Object result = chain.proceed();
                if (shouldTakeOver) {
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
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, null);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                Object result = chain.proceed();
                if (shouldTakeOver) {
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
                        clearOverviewStateStackAnimation(recentsView);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
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
                    LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                    recentsView.invalidate();
                });
        LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addListener",
                new Class<?>[]{Animator.AnimatorListener.class},
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        finishBlankTapHomeExitSystemAnimation(recentsView);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finishBlankTapHomeExitSystemAnimation(recentsView);
                    }
                });
    }

    private static void finishBlankTapHomeExitSystemAnimation(View recentsView) {
        LauncherRecentsTransitionController.setBlankTapHomeExitProgress(recentsView, 1f);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
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
        LauncherRecentsLayoutEngine.cancelStackLayoutRecovery(recentsView);
        markOverviewPeekStockAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackStartAdjacentOffset(
                recentsView,
                LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        0.53f));
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        LauncherRecentsState.setOverviewStateStackBaselineCaptured(recentsView, true);
        markOverviewStateStackAnimation(recentsView, true);
        attachOverviewStateAnimationCallbacks(recentsView, pendingAnimation);
        if (pendingAnimation == null) {
            recentsView.postDelayed(() -> {
                if (isOverviewStateStackAnimationActive(recentsView)) {
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
        return currentState == overviewPeekState
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
        if (recentsView == null) {
            return false;
        }
        if (isOverviewPeekStockAnimationActive(recentsView)) {
            return true;
        }
        Object container = LauncherRecentsCompat.getFieldCompat(recentsView, "mContainer");
        Object stateManager = LauncherRecentsCompat.invokeCompat(container, "getStateManager");
        return isOverviewPeekStateObject(LauncherRecentsCompat.invokeCompat(stateManager, "getState"))
                || isOverviewPeekStateObject(
                LauncherRecentsCompat.invokeCompat(stateManager, "getCurrentStableState"))
                || isOverviewPeekStateObject(
                LauncherRecentsCompat.invokeCompat(stateManager, "getTargetState"));
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
            markOverviewPeekStockAnimation(recentsView, true);
        } else if (toState != overviewState) {
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

    private static boolean isOverviewPeekStateObject(Object value) {
        return value != null && value.getClass().getName().endsWith("OverviewPeekState");
    }

    private static void clearOverviewStateStackAnimation(View recentsView) {
        markOverviewPeekStockAnimation(recentsView, false);
        markOverviewStateStackAnimation(recentsView, false);
        LauncherRecentsLayoutEngine.startStackLayoutRecovery(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(
                recentsView,
                false,
                "overviewStateClearRestore",
                true);
        LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15);
    }

    static void clearOverviewEntryState(View recentsView) {
        markOverviewPeekStockAnimation(recentsView, false);
        markOverviewStateStackAnimation(recentsView, false);
    }
}
