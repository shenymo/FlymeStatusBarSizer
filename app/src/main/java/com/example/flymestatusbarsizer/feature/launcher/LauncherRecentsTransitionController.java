package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Method;

final class LauncherRecentsTransitionController {
    private static final String ABS_SWIPE_UP_HANDLER_CLASS =
            "com.android.quickstep.AbsSwipeUpHandler";
    private static final long BLANK_TAP_HOME_EXIT_DURATION_MS = 360L;
    private static final long GESTURE_STACK_RELEASE_DURATION_MS = 320L;
    private static final int APP_TO_RECENTS_STACK_ANCHOR_PAGE = 0;
    private static final DecelerateInterpolator BLANK_TAP_HOME_EXIT_INTERPOLATOR =
            new DecelerateInterpolator(1.6f);
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
                    LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
                    LauncherRecentsAttachController.clearAppToRecentsEntrySession(
                            recentsView,
                            false);
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
                AnimatorSet animatorSet = chain.getArg(0) instanceof AnimatorSet
                        ? (AnimatorSet) chain.getArg(0)
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
                    if (shouldPrepareGestureRelease
                            && LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                            && !isGestureRecentsStackReleaseAnimationActive(recentsView)) {
                        startGestureRecentsStackReleaseAnimation(recentsView, animatorSet, true);
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
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    boolean gestureReleased =
                            LauncherRecentsState.isAppToRecentsGestureReleased(recentsView);
                    boolean releaseAnimationActive =
                            isGestureRecentsStackReleaseAnimationActive(recentsView);
                    boolean releaseAnimationFinished =
                            LauncherRecentsState.isGestureStackReleasedStable(recentsView);
                    if (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                            && !gestureReleased
                            && !releaseAnimationActive
                            && !releaseAnimationFinished) {
                        return result;
                    } else if (shouldPrepareGestureRelease) {
                        if (gestureReleased
                                && !releaseAnimationActive
                                && !releaseAnimationFinished) {
                            applyGestureRecentsStackRelease(recentsView, true);
                        }
                        if (gestureReleased || releaseAnimationActive || releaseAnimationFinished) {
                            LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, false);
                            LauncherRecentsState.setAppToRecentsStackLayoutDeferred(recentsView, false);
                            markPendingGestureRecentsStackRelease(recentsView, false);
                            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                        }
                    } else {
                        LauncherRecentsAttachController.clearAppToRecentsEntrySession(
                                recentsView,
                                false);
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
                if (recentsView != null
                        && LauncherRecentsLayoutEngine.shouldDeferStackLayoutForAppToRecents(
                        recentsView)) {
                    LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, true);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook AbsSwipeUpHandler.onGestureEnded",
                    t);
        }
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
                applyForcedRecentsTranslationY(resolveHandlerRecentsView(chain.getThisObject()));
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

    static float readGestureRecentsStackReleaseProgress(View recentsView) {
        Float value = LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.get(recentsView);
        return value != null ? value : 1f;
    }

    static void cancelGestureRecentsStackReleaseAnimation(View recentsView, boolean clearProgress) {
        ValueAnimator animator =
                LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (clearProgress) {
            LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
            clearGestureRecentsStackReleaseProgress(recentsView);
            clearForcedRecentsTranslationY(recentsView);
        }
    }

    static boolean isRecentsGestureEndTarget(Object value) {
        return value instanceof Enum && "RECENTS".equals(((Enum<?>) value).name());
    }

    static void switchRunningTaskToScreenshot(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable applyRunnable = () -> {
            if (!isGestureRecentsStackReleaseAnimationActive(recentsView)) {
                finishRunningTaskReleaseToStack(recentsView);
            }
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
        startGestureRecentsStackReleaseAnimation(recentsView, null, ensureRunningTaskScreenshot);
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
            return;
        }
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
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "forceFinishScroller",
                LauncherRecentsCompat.NO_ARGS);
        final float releaseStartTranslationY = recentsView.getTranslationY();
        final int stackAnchorPage = resolveAppToRecentsStackAnchorPage(recentsView);
        final int stackAnchorStartScroll = resolvePrimaryScroll(recentsView);
        final int stackAnchorTargetScroll = resolveScrollForPage(
                recentsView,
                stackAnchorPage,
                stackAnchorStartScroll);
        LauncherRecentsState.setAppToRecentsEntrySessionActive(recentsView, false);
        LauncherRecentsState.setAppToRecentsStackLayoutDeferred(recentsView, false);
        LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, false);
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        setGestureRecentsStackReleaseProgress(recentsView, 0f);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        recentsView.invalidate();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(GESTURE_STACK_RELEASE_DURATION_MS);
        animator.setInterpolator(GESTURE_STACK_RELEASE_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            float progress = value instanceof Float ? (Float) value : 1f;
            setForcedRecentsTranslationY(recentsView, LauncherRecentsLayoutEngine.lerp(
                    releaseStartTranslationY,
                    0f,
                    progress));
            setGestureRecentsStackReleaseProgress(recentsView, progress);
            applyAppToRecentsStackAnchorScroll(
                    recentsView,
                    stackAnchorStartScroll,
                    stackAnchorTargetScroll,
                    progress);
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
                LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.remove(recentsView);
                if (cancelled) {
                    clearGestureRecentsStackReleaseProgress(recentsView);
                    clearForcedRecentsTranslationY(recentsView);
                    return;
                }
                setForcedRecentsTranslationY(recentsView, 0f);
                setGestureRecentsStackReleaseProgress(recentsView, 1f);
                normalizeAppToRecentsStackAnchor(recentsView, stackAnchorPage);
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                LauncherRecentsState.setGestureStackReleasedStable(recentsView, true);
                clearGestureRecentsStackReleaseProgress(recentsView);
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                recentsView.invalidate();
            }
        });
        LauncherRecentsState.ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS.put(recentsView, animator);
        if (animatorSet != null) {
            animatorSet.play(animator);
        } else {
            animator.start();
        }
    }

    private static void prepareRunningTaskScreenshotForStackRelease(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable finishRunnable = () -> {
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
        return Math.min(APP_TO_RECENTS_STACK_ANCHOR_PAGE, pageCount - 1);
    }

    private static void normalizeAppToRecentsStackAnchor(View recentsView, int anchorPage) {
        if (recentsView == null || anchorPage < 0) {
            return;
        }
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
                && LauncherRecentsLayoutEngine.shouldDeferStackLayoutForAppToRecents(recentsView)
                && isRecentsGestureEndTarget(endTarget);
    }

    private static boolean isPendingGestureRecentsStackRelease(View recentsView) {
        Boolean value = LauncherRecentsState.PENDING_GESTURE_RECENTS_STACK_RELEASES.get(recentsView);
        return value != null && value;
    }

    private static boolean shouldSuppressLiveTileForStack(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldDeferStackLayoutForAppToRecents(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
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

    private static View resolveHandlerRecentsView(Object handler) {
        Object value = LauncherRecentsCompat.getFieldCompat(handler, "mRecentsView");
        return value instanceof View ? (View) value : null;
    }

    private static void setGestureRecentsStackReleaseProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.put(
                recentsView,
                LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f));
    }

    private static void clearGestureRecentsStackReleaseProgress(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.GESTURE_STACK_RELEASE_PROGRESS.remove(recentsView);
    }

    private static void setForcedRecentsTranslationY(View recentsView, float translationY) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.put(recentsView, translationY);
        recentsView.setTranslationY(translationY);
    }

    private static void clearForcedRecentsTranslationY(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.remove(recentsView);
    }

    private static void applyForcedRecentsTranslationY(View recentsView) {
        Float value = recentsView != null
                ? LauncherRecentsState.FORCED_RECENTS_TRANSLATION_YS.get(recentsView)
                : null;
        if (value != null) {
            recentsView.setTranslationY(value);
        }
    }
}
