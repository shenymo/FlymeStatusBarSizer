package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.lang.reflect.Method;

final class LauncherRecentsTransitionController {
    private static final String ABS_SWIPE_UP_HANDLER_CLASS =
            "com.android.quickstep.AbsSwipeUpHandler";
    private static final long BLANK_TAP_HOME_EXIT_DURATION_MS = 460L;
    private static final long GESTURE_STACK_RELEASE_DURATION_MS = 320L;
    private static final int APP_TO_RECENTS_STACK_ANCHOR_PAGE = 0;
    private static final float GESTURE_STACK_RELEASE_HANDOFF_START_PROGRESS = 0.42f;
    private static final float BLANK_TAP_HOME_EXIT_VIEW_FADE_START_PROGRESS = 0.78f;
    private static final long BLANK_TAP_HOME_EXIT_HOME_REVEAL_MS = 180L;
    private static final int BLANK_TAP_HOME_EXIT_REVEAL_SCRIM_ALPHA = 150;
    private static final LinearInterpolator BLANK_TAP_HOME_EXIT_INTERPOLATOR =
            new LinearInterpolator();
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
                    LauncherRecentsStateAnimationController.clearOverviewEntryState(recentsView);
                    LauncherRecentsAttachController.clearAppToRecentsEntrySession(
                            recentsView,
                            false);
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (shouldAnimateBlankTapHomeExit(recentsView)) {
                        prepareBlankTapHomeExitAnimation(recentsView);
                        Object result = chain.proceed();
                        return result;
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
                    if (isGestureRecentsStackReleaseHandoffPending(recentsView)) {
                        return result;
                    }
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
                applyForcedRecentsTranslation(resolveHandlerRecentsView(chain.getThisObject()));
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
        prepareBlankTapHomeExitAnimation(recentsView);
        startPreparedBlankTapHomeExitAnimation(recentsView);
    }

    static void prepareBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator =
                LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
            runningAnimator.cancel();
        }
        setPageAnimOffScreenStart(recentsView, false);
        if (!isBlankTapHomeExitActive(recentsView)
                || LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.isEmpty()) {
            LauncherRecentsLayoutEngine.captureBlankTapHomeExitTaskStates(recentsView);
        }
        markBlankTapHomeExitActive(recentsView, true);
        setBlankTapHomeExitProgress(recentsView, 0f);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        recentsView.invalidate();
    }

    private static void startPreparedBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator =
                LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            return;
        }
        setBlankTapHomeExitProgress(recentsView, 0f);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        recentsView.invalidate();
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
                    clearBlankTapHomeExitViewBlur(recentsView);
                    clearBlankTapHomeExitProgress(recentsView);
                    return;
                }
                setBlankTapHomeExitProgress(recentsView, 1f);
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                recentsView.postDelayed(
                        () -> clearBlankTapHomeExitProgress(recentsView, false),
                        80L);
            }
        });
        LauncherRecentsState.ACTIVE_HOME_EXIT_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void finishBlankTapHomeExit(View recentsView) {
        if (recentsView == null) {
            return;
        }
        recentsView.setAlpha(0f);
        startBlankTapHomeExitHome(recentsView);
        Runnable resetRunnable = () -> {
            recentsView.setAlpha(1f);
            clearBlankTapHomeExitViewBlur(recentsView);
            clearBlankTapHomeExitProgress(recentsView, false);
        };
        Handler handler = LauncherRecentsState.ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.postDelayed(resetRunnable, 80L);
        } else {
            recentsView.postDelayed(resetRunnable, 80L);
        }
    }

    private static void startBlankTapHomeExitHome(View recentsView) {
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "handleStartHome",
                new Class[]{boolean.class},
                false);
    }

    private static void applyBlankTapHomeExitViewBlur(View recentsView, float progress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && recentsView != null) {
            recentsView.setRenderEffect(null);
        }
    }

    private static void clearBlankTapHomeExitViewBlur(View recentsView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && recentsView != null) {
            recentsView.setRenderEffect(null);
        }
    }

    private static void startBlankTapHomeExitHomeReveal(View recentsView) {
        View rootView = recentsView != null ? recentsView.getRootView() : null;
        if (rootView == null || rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            return;
        }
        ColorDrawable scrim = new ColorDrawable(Color.BLACK);
        scrim.setBounds(0, 0, rootView.getWidth(), rootView.getHeight());
        scrim.setAlpha(BLANK_TAP_HOME_EXIT_REVEAL_SCRIM_ALPHA);
        rootView.getOverlay().add(scrim);
        ValueAnimator animator = ValueAnimator.ofInt(
                BLANK_TAP_HOME_EXIT_REVEAL_SCRIM_ALPHA,
                0);
        animator.setDuration(BLANK_TAP_HOME_EXIT_HOME_REVEAL_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.2f));
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            scrim.setAlpha(value instanceof Integer ? (Integer) value : 0);
            rootView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                rootView.getOverlay().remove(scrim);
            }
        });
        animator.start();
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
        clearBlankTapHomeExitProgress(recentsView, true);
    }

    private static void clearBlankTapHomeExitProgress(View recentsView, boolean reapplyLayout) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_PROGRESS.remove(recentsView);
        markBlankTapHomeExitActive(recentsView, false);
        LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.clear();
        setPageAnimOffScreenStart(recentsView, false);
        if (reapplyLayout) {
            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
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
            applyAppToRecentsStackAnchorScroll(
                    recentsView,
                    handoffStartScroll[0],
                    stackAnchorTargetScroll,
                    handoffProgress);
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
                    markGestureRecentsStackReleaseHandoffPending(recentsView, false);
                    clearGestureRecentsStackReleaseProgress(recentsView);
                    clearForcedRecentsTranslationX(recentsView);
                    clearForcedRecentsTranslationY(recentsView);
                    LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
                    return;
                }
                if (!handoffStarted[0]) {
                    handoffStartScroll[0] = resolvePrimaryScroll(recentsView);
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
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                LauncherRecentsTouchController.forceEnsureStackVisibleTaskData(recentsView, 15);
                LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
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

    private static void beginGestureRecentsStackReleaseHandoff(
            View recentsView,
            int stackAnchorStartScroll,
            int stackAnchorTargetScroll,
            boolean ensureRunningTaskScreenshot) {
        if (recentsView == null) {
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
        markGestureRecentsStackReleaseHandoffPending(recentsView, false);
        markPendingGestureRecentsStackRelease(recentsView, false);
        LauncherRecentsState.setAppToRecentsEntrySessionActive(recentsView, false);
        LauncherRecentsState.setAppToRecentsStackLayoutDeferred(recentsView, false);
        LauncherRecentsState.setAppToRecentsGestureReleased(recentsView, false);
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
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
        return LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView);
    }

    private static boolean shouldSuppressLiveTileForStack(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldDeferStackLayoutForAppToRecents(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView);
    }

    private static void markPendingGestureRecentsStackRelease(View recentsView, boolean active) {
        LauncherRecentsState.setPendingGestureRecentsStackRelease(recentsView, active);
    }

    static boolean isGestureRecentsStackReleaseHandoffPending(View recentsView) {
        return LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(recentsView);
    }

    private static void markGestureRecentsStackReleaseHandoffPending(
            View recentsView,
            boolean active) {
        LauncherRecentsState.setPendingGestureRecentsStackReleaseHandoff(recentsView, active);
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
