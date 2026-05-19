package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Method;

final class LauncherRecentsTransitionController {
    private static final long BLANK_TAP_HOME_EXIT_DURATION_MS = 360L;
    private static final DecelerateInterpolator BLANK_TAP_HOME_EXIT_INTERPOLATOR =
            new DecelerateInterpolator(1.6f);

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
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (shouldAnimateBlankTapHomeExit(recentsView)) {
                        if (LauncherRecentsCompat.invokeBoolean(
                                recentsView,
                                "canStartHomeSafely",
                                false)) {
                            startBlankTapHomeExitAnimation(recentsView);
                            return null;
                        }
                    }
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
                if (shouldPrepareGestureRelease) {
                    markPendingGestureRecentsStackRelease(recentsView, true);
                } else {
                    markPendingGestureRecentsStackRelease(recentsView, false);
                }
                Object result = chain.proceed();
                if (recentsView != null) {
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (shouldPrepareGestureRelease) {
                        switchRunningTaskToScreenshot(recentsView);
                    }
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
                    if (isPendingGestureRecentsStackRelease(recentsView)) {
                        applyGestureRecentsStackRelease(recentsView, false);
                    }
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
                if (shouldPrepareGestureRelease) {
                    markPendingGestureRecentsStackRelease(recentsView, true);
                    applyGestureRecentsStackRelease(recentsView, true);
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (shouldPrepareGestureRelease) {
                        finishRunningTaskReleaseToStack(recentsView);
                    }
                    markPendingGestureRecentsStackRelease(recentsView, false);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onGestureAnimationEnd",
                    t);
        }
    }

    static boolean shouldAnimateBlankTapHomeExit(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && LauncherRecentsCompat.readBooleanField(
                        recentsView,
                        "mTouchDownToStartHome",
                        false);
    }

    static void startBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator =
                LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            if (runningAnimator.isStarted() || runningAnimator.isRunning()) {
                return;
            }
            LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
        }
        setPageAnimOffScreenStart(recentsView, true);
        setBlankTapHomeExitProgress(recentsView, 0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(BLANK_TAP_HOME_EXIT_DURATION_MS);
        animator.setInterpolator(BLANK_TAP_HOME_EXIT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            float progress = value instanceof Float ? (Float) value : 0f;
            setBlankTapHomeExitProgress(recentsView, progress);
            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
            recentsView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
                if (cancelled) {
                    clearBlankTapHomeExitProgress(recentsView);
                    return;
                }
                finishBlankTapHomeExit(recentsView);
            }
        });
        LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void finishBlankTapHomeExit(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "handleStartHome",
                new Class[]{boolean.class},
                false);
        Runnable resetRunnable = () -> clearBlankTapHomeExitProgress(recentsView);
        Handler handler = LauncherRecentsState.ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(resetRunnable);
        } else {
            recentsView.post(resetRunnable);
        }
    }

    static void cancelBlankTapHomeExitAnimation(View recentsView, boolean resetTransform) {
        ValueAnimator animator = LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (resetTransform) {
            clearBlankTapHomeExitProgress(recentsView);
        }
    }

    static void clearBlankTapHomeExitProgress(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.remove(recentsView);
        setPageAnimOffScreenStart(recentsView, false);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        recentsView.invalidate();
    }

    private static void setBlankTapHomeExitProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.put(
                recentsView,
                LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f));
    }

    static float readBlankTapHomeExitProgress(View recentsView) {
        Float value = LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.get(recentsView);
        return value != null ? value : 0f;
    }

    static boolean isRecentsGestureEndTarget(Object value) {
        return value instanceof Enum && "RECENTS".equals(((Enum<?>) value).name());
    }

    static void switchRunningTaskToScreenshot(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable applyRunnable = () -> {
            finishRunningTaskRecentsAnimation(recentsView);
            finishRunningTaskReleaseToStack(recentsView);
        };
        if (!LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "switchToScreenshot",
                new Class<?>[]{Runnable.class},
                applyRunnable)) {
            applyRunnable.run();
        }
    }

    static void finishRunningTaskReleaseToStack(View recentsView) {
        applyGestureRecentsStackRelease(recentsView, true);
    }

    private static void applyGestureRecentsStackRelease(
            View recentsView,
            boolean ensureRunningTaskScreenshot) {
        if (recentsView == null) {
            return;
        }
        if (ensureRunningTaskScreenshot) {
            LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "setRunningTaskViewShowScreenshot",
                    LauncherRecentsCompat.BOOLEAN_ARG,
                    true);
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setEnableDrawingLiveTile",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskHidden",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        recentsView.invalidate();
    }

    static void finishRunningTaskRecentsAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
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
        Boolean value = LauncherRecentsState.PENDING_GESTURE_RECENTS_STACK_RELEASES.get(recentsView);
        return value != null && value;
    }

    private static boolean shouldSuppressLiveTileForStack(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView);
    }

    private static void markPendingGestureRecentsStackRelease(View recentsView, boolean active) {
        if (recentsView == null) {
            return;
        }
        if (active) {
            LauncherRecentsState.PENDING_GESTURE_RECENTS_STACK_RELEASES.put(
                    recentsView,
                    Boolean.TRUE);
        } else {
            LauncherRecentsState.PENDING_GESTURE_RECENTS_STACK_RELEASES.remove(recentsView);
        }
    }
}
