package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Trace;
import android.view.View;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.HashMap;

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
    private static final String HOME_TO_OVERVIEW_TOUCH_CONTROLLER_CLASS =
            "com.meizu.flyme.launcher.uioverrides.HomeToOverviewTouchController";
    private static final String NO_BUTTON_NAVBAR_TO_OVERVIEW_TOUCH_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.NoButtonNavbarToOverviewTouchController";
    private static final String NO_BUTTON_QUICK_SWITCH_TOUCH_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.NoButtonQuickSwitchTouchController";
    private static final long OVERVIEW_STATE_STACK_ANIMATION_FALLBACK_CLEAR_DELAY_MS = 350L;

    private LauncherRecentsStateAnimationController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewAnimatedStateMethod(
                module,
                loader,
                "setStateWithAnimation",
                "Failed to hook RecentsViewStateController.setStateWithAnimation");
        hookRecentsViewAnimatedStateMethod(
                module,
                loader,
                "setStateWithAnimationInternalForFlyme",
                "Failed to hook RecentsViewStateController.setStateWithAnimationInternalForFlyme");
        hookRecentsViewStateImmediate(module, loader);
        hookHomeToOverviewGoOverview(module, loader);
        hookNoButtonNavbarOverviewMotionPause(module, loader);
        hookNoButtonNavbarOverviewDragEnd(module, loader);
        hookNoButtonQuickSwitchDragStart(module, loader);
        hookAdjacentPageHorizontalOffsetProperty(module, loader, "com.android.quickstep.views.RecentsView$4");
        hookAdjacentPageHorizontalOffsetProperty(module, loader, "com.android.quickstep.views.RecentsView$5");
    }

    private static void hookAdjacentPageHorizontalOffsetProperty(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Class<?> recentsViewClass = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS,
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("setValue", recentsViewClass, float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object target = chain.getArg(0);
                float value = chain.getArg(1) instanceof Float ? (Float) chain.getArg(1) : 0f;
                if (target instanceof View
                        && shouldFreezePreReleaseAdjacentOffset((View) target, value)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook " + className + ".setValue",
                    t);
        }
    }

    private static boolean shouldFreezePreReleaseAdjacentOffset(View recentsView, float value) {
        if (recentsView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsState.isLauncherQuickSwitchStockMode(recentsView)
                || LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                || LauncherRecentsState.isOverviewStateStackReleaseRequested(recentsView)
                || LauncherRecentsState.isOverviewStateStackAnimationActive(recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)) {
            return false;
        }
        float current = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mAdjacentPageHorizontalOffset",
                value);
        return current > 0.45f && value < 0.45f;
    }

    private static void hookNoButtonNavbarOverviewMotionPause(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    NO_BUTTON_NAVBAR_TO_OVERVIEW_TOUCH_CONTROLLER_CLASS,
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("onMotionPauseDetected");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = resolveNoButtonNavbarRecentsView(chain.getThisObject());
                if (recentsView == null) {
                    return chain.proceed();
                }
                LauncherRecentsState.setLauncherQuickSwitchStockMode(recentsView, false);
                if (!LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
                    return chain.proceed();
                }
                LauncherRecentsLayoutEngine.cancelStackLayoutRecovery(recentsView);
                LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, true);
                LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, false);
                markOverviewStateStackAnimation(recentsView, false);
                markOverviewPeekStockAnimation(recentsView, true);
                LauncherRecentsCompat.writeField(chain.getThisObject(), "mStartedOverview", true);
                LauncherRecentsCompat.writeField(chain.getThisObject(), "mReachedOverview", false);
                LauncherRecentsCompat.writeField(
                        chain.getThisObject(),
                        "mNormalToHintOverviewScrimAnimator",
                        null);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook NoButtonNavbarToOverviewTouchController.onMotionPauseDetected",
                    t);
        }
    }

    private static void hookNoButtonNavbarOverviewDragEnd(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    NO_BUTTON_NAVBAR_TO_OVERVIEW_TOUCH_CONTROLLER_CLASS,
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("onDragEnd", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = resolveNoButtonNavbarRecentsView(chain.getThisObject());
                if (recentsView == null
                        || !LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)) {
                    return chain.proceed();
                }
                LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, false);
                LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, true);
                Object launcher =
                        LauncherRecentsCompat.getFieldCompat(chain.getThisObject(), "mLauncher");
                Object stateManager = LauncherRecentsCompat.invokeCompat(launcher, "getStateManager");
                Object overviewState = LauncherRecentsCompat.readStaticFieldCompat(
                        LAUNCHER_STATE_CLASS,
                        "OVERVIEW",
                        loader);
                LauncherRecentsCompat.invokeCompat(
                        stateManager,
                        "goToState",
                        new Class<?>[]{Class.forName(LAUNCHER_STATE_CLASS, false, loader),
                                boolean.class},
                        overviewState,
                        true);
                Object motionPauseDetector =
                        LauncherRecentsCompat.getFieldCompat(chain.getThisObject(),
                                "mMotionPauseDetector");
                LauncherRecentsCompat.invokeCompat(motionPauseDetector, "clear");
                LauncherRecentsCompat.writeField(chain.getThisObject(), "mIsTrackpadSwipe", false);
                LauncherRecentsCompat.writeField(
                        chain.getThisObject(),
                        "mNormalToHintOverviewScrimAnimator",
                        null);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook NoButtonNavbarToOverviewTouchController.onDragEnd",
                    t);
        }
    }

    private static View resolveNoButtonNavbarRecentsView(Object controller) {
        Object recentsView = LauncherRecentsCompat.getFieldCompat(controller, "mRecentsView");
        return recentsView instanceof View ? (View) recentsView : null;
    }

    private static void hookHomeToOverviewGoOverview(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(HOME_TO_OVERVIEW_TOUCH_CONTROLLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("goOverview");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = resolveHomeToOverviewRecentsView(chain.getThisObject());
                if (recentsView != null) {
                    LauncherRecentsState.setLauncherQuickSwitchStockMode(recentsView, false);
                }
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
                    LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, false);
                    LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, true);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook HomeToOverviewTouchController.goOverview",
                    t);
        }
    }

    private static View resolveHomeToOverviewRecentsView(Object controller) {
        Object launcher = LauncherRecentsCompat.getFieldCompat(controller, "mLauncher");
        Object recentsView = LauncherRecentsCompat.invokeCompat(launcher, "getOverviewPanel");
        return recentsView instanceof View ? (View) recentsView : null;
    }

    private static void hookRecentsViewAnimatedStateMethod(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            String methodName,
            String failureMessage) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_STATE_CONTROLLER_CLASS, false, loader);
            Class<?> launcherStateClass = Class.forName(LAUNCHER_STATE_CLASS, false, loader);
            Class<?> stateAnimationConfigClass =
                    Class.forName(STATE_ANIMATION_CONFIG_CLASS, false, loader);
            Class<?> pendingAnimationClass =
                    Class.forName(PENDING_ANIMATION_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    methodName,
                    launcherStateClass,
                    stateAnimationConfigClass,
                    pendingAnimationClass);
            method.setAccessible(true);
            module.intercept(method, createAnimatedStateHook(loader));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(failureMessage, t);
        }
    }

    private static XposedInterface.Hooker createAnimatedStateHook(
            ClassLoader loader) {
        return chain -> {
            Object thisObject = chain.getThisObject();
            Object toState = chain.getArg(0);
            Object pendingAnimation = chain.getArg(2);
            View recentsView = resolveControllerRecentsView(thisObject);
            boolean shouldTakeOver =
                    shouldTakeOverOverviewPeekToOverview(recentsView, toState, loader);
            if (shouldTakeOver) {
                beginOverviewStateStackAnimation(recentsView, pendingAnimation);
            } else {
                updateOverviewPeekStockAnimation(recentsView, toState, loader);
            }
            prepareHomeExitFromRecentsIfNeeded(recentsView, toState, pendingAnimation, loader);
            Object result = chain.proceed();
            if (shouldAttachBlankTapHomeExitToSystemAnimation(recentsView, toState, loader)) {
                attachBlankTapHomeExitSystemCallbacks(recentsView, pendingAnimation, loader);
            }
            return result;
        };
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
                        shouldTakeOverOverviewPeekToOverview(recentsView, toState, loader);
                if (shouldTakeOver) {
                    beginOverviewStateStackAnimation(recentsView, null);
                } else {
                    updateOverviewPeekStockAnimation(recentsView, toState, loader);
                }
                Object result = chain.proceed();
                if (shouldTakeOver) {
                    LauncherRecentsLayoutEngine.applyStackLayout(
                            recentsView,
                            false,
                            "stateImmediate");
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
                    Trace.beginSection("FSBS/desktop/frame");
                    try {
                        if (isOverviewStateStackAnimationActive(recentsView)) {
                            Trace.beginSection("FSBS/desktop/cached");
                            boolean applied;
                            try {
                                applied = LauncherRecentsLayoutEngine
                                        .applyCachedOverviewStateFrame(recentsView);
                            } finally {
                                Trace.endSection();
                            }
                            if (!applied) {
                                Trace.beginSection("FSBS/desktop/fallback");
                                try {
                                    applied = LauncherRecentsLayoutEngine.applyStackLayout(
                                            recentsView,
                                            false,
                                            "overviewStateFrameFallback");
                                } finally {
                                    Trace.endSection();
                                }
                            }
                        }
                    } finally {
                        Trace.endSection();
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
        LauncherRecentsLayoutEngine.applyBlankTapHomeExitFrame(recentsView, 1f);
        LauncherRecentsTransitionController.clearBlankTapHomeExitProgressWithoutLayout(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setContentAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                0f);
        LauncherRecentsLayoutEngine.clearBlankTapHomeExitPendingLayout(recentsView);
    }

    private static void beginOverviewStateStackAnimation(
            View recentsView,
            Object pendingAnimation) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.setPositionOwner(
                recentsView,
                LauncherRecentsState.POSITION_OWNER_OVERVIEW);
        LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, false);
        LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, false);
        LauncherRecentsLayoutEngine.cancelStackLayoutRecovery(recentsView);
        markOverviewPeekStockAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackStartAdjacentOffset(
                recentsView,
                LauncherRecentsCompat.readFloatField(
                        recentsView,
                        "mAdjacentPageHorizontalOffset",
                        0.53f));
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> startVisualStates =
                LauncherRecentsLayoutEngine.captureCurrentStackTaskVisualStates(recentsView);
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        LauncherRecentsState.setOverviewStateStackBaselineCaptured(recentsView, true);
        markOverviewStateStackAnimation(recentsView, true);
        LauncherRecentsLayoutEngine.captureOverviewStateStackEntryTaskStates(
                recentsView,
                startVisualStates);
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
            View recentsView,
            Object toState,
            ClassLoader loader) {
        if (recentsView == null
                || toState == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        Object overviewState =
                LauncherRecentsCompat.readStaticFieldCompat(LAUNCHER_STATE_CLASS, "OVERVIEW", loader);
        if (toState != overviewState) {
            return false;
        }
        if (!LauncherRecentsState.isOverviewStateStackReleaseRequested(recentsView)) {
            return false;
        }
        return true;
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
        return recentsView != null
                && (LauncherRecentsState.isOverviewPreReleaseStockMode(recentsView)
                || isOverviewPeekStockAnimationActive(recentsView)
                || isSystemOverviewPeekStateActive(recentsView));
    }

    private static boolean isSystemOverviewPeekStateActive(View recentsView) {
        Object container = LauncherRecentsCompat.getFieldCompat(recentsView, "mContainer");
        Object stateManager = LauncherRecentsCompat.invokeCompat(container, "getStateManager");
        return isOverviewPeekStateObject(LauncherRecentsCompat.invokeCompat(stateManager, "getState"))
                || isOverviewPeekStateObject(
                LauncherRecentsCompat.invokeCompat(stateManager, "getCurrentStableState"))
                || isOverviewPeekStateObject(
                LauncherRecentsCompat.invokeCompat(stateManager, "getTargetState"));
    }

    private static boolean isOverviewPeekStateObject(Object state) {
        return state != null && state.getClass().getName().contains("OverviewPeekState");
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
            beginOverviewPeekStockAnimation(recentsView);
        } else if (toState == overviewState) {
            markOverviewPeekStockAnimation(recentsView, false);
        } else {
            clearOverviewEntryState(recentsView);
        }
    }

    private static void beginOverviewPeekStockAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsLayoutEngine.cancelStackLayoutRecovery(recentsView);
        markOverviewStateStackAnimation(recentsView, false);
        LauncherRecentsState.setOverviewStateStackSettled(recentsView, false);
        LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, true);
        LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, false);
        markOverviewPeekStockAnimation(recentsView, true);
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

    static void finishOverviewStateStackAnimationForTouchTakeover(View recentsView) {
        if (recentsView == null || !isOverviewStateStackAnimationActive(recentsView)) {
            return;
        }
        LauncherRecentsState.clearPositionOwner(
                recentsView,
                LauncherRecentsState.POSITION_OWNER_OVERVIEW);
        LauncherRecentsState.clearOverviewStackAnimationState(recentsView, true);
    }

    private static void clearOverviewStateStackAnimation(View recentsView) {
        if (recentsView == null || !isOverviewStateStackAnimationActive(recentsView)) {
            return;
        }
        LauncherRecentsState.clearPositionOwner(
                recentsView,
                LauncherRecentsState.POSITION_OWNER_OVERVIEW);
        LauncherRecentsState.clearOverviewStackAnimationState(recentsView, true);
        LauncherRecentsLayoutEngine.restoreStackLayout(recentsView, "overviewStateClearRestore");
    }

    private static void hookNoButtonQuickSwitchDragStart(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    NO_BUTTON_QUICK_SWITCH_TOUCH_CONTROLLER_CLASS,
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("onDragStart", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object recentsObject = LauncherRecentsCompat.getFieldCompat(
                        chain.getThisObject(),
                        "mRecentsView");
                if (recentsObject instanceof View) {
                    LauncherRecentsState.setLauncherQuickSwitchStockMode(
                            (View) recentsObject,
                            true);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook NoButtonQuickSwitchTouchController.onDragStart",
                    t);
        }
    }

    static void clearOverviewEntryState(View recentsView) {
        LauncherRecentsState.clearPositionOwner(
                recentsView,
                LauncherRecentsState.POSITION_OWNER_OVERVIEW);
        LauncherRecentsState.clearOverviewStackAnimationState(recentsView, false);
        LauncherRecentsState.setOverviewPreReleaseStockMode(recentsView, false);
        LauncherRecentsState.setOverviewStateStackReleaseRequested(recentsView, false);
    }
}
