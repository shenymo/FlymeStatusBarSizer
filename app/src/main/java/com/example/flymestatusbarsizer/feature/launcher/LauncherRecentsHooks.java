package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class LauncherRecentsHooks {
    private static final Class<?>[] NO_ARGS = new Class[0];
    private static final Class<?>[] INT_ARG = new Class[]{int.class};
    private static final Class<?>[] FLOAT_ARG = new Class[]{float.class};
    private static final Class<?>[] BOOLEAN_ARG = new Class[]{boolean.class};
    private static final String LAUNCHER_RECENTS_VIEW_CLASS =
            "com.android.quickstep.views.LauncherRecentsView";
    private static final String PAGED_VIEW_CLASS = "com.android.launcher3.PagedView";
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView";
    private static final long BLANK_TAP_HOME_EXIT_DURATION_MS = 360L;
    private static final float BLANK_TAP_HOME_EXIT_SCALE_DELTA = 0.07f;
    private static final float BLANK_TAP_HOME_EXIT_TRAVEL_RATIO = 0.90f;
    private static final float STACK_DEPTH_CURVE_POWER = 0.82f;
    private static final float STACK_FRONT_VISIBLE_RATIO = 0.50f;
    private static final float STACK_FRONT_SHIFT_START_PROGRESS = 0.12f;
    private static final float STACK_FRONT_REVEAL_CURVE_POWER = 0.72f;
    private static final float STACK_ENTRY_LIFT_RATIO = 0.05f;
    private static final float STACK_BACK_SPREAD_RATIO = 0.14f;
    private static final float STACK_MIN_OVERLAP_RATIO = 0.20f;
    private static final float STACK_SCALE_STEP = 0.065f;
    private static final float STACK_MIN_SCALE = 0.80f;
    private static final float STACK_LEFT_INSET_RATIO = 0.05f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final DecelerateInterpolator BLANK_TAP_HOME_EXIT_INTERPOLATOR =
            new DecelerateInterpolator(1.6f);
    private static final WeakHashMap<View, Boolean> TRACKED_RECENTS_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, ValueAnimator> ACTIVE_HOME_EXIT_ANIMATORS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> BLANK_TAP_HOME_EXIT_PROGRESS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_XS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_YS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_HORIZONTAL_OFFSET_XS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_NON_GRID_SCALES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_BOX_TRANSLATION_YS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_STABLE_ALPHAS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TRANSLATION_ZS =
            new WeakHashMap<>();
    private static volatile Handler mainHandler;

    private LauncherRecentsHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewConstructors(module, loader);
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewMethod(module, loader, "updatePageScales");
        hookRecentsViewOnLayout(module, loader);
        hookRecentsViewOnScrollChanged(module, loader);
        hookRecentsViewContentAlpha(module, loader);
        hookRecentsViewDraw(module, loader);
        hookRecentsViewStartHome(module, loader);
        hookRecentsViewFreeScrollSettling(module, loader);
        hookRecentsViewPrepareGestureEndAnimation(module, loader);
        hookRecentsViewGestureAnimationEnd(module, loader);
        hookPagedViewOnTouchEvent(module, loader);
        hookPagedViewSnapToDestination(module, loader);
        hookTaskViewPressScale(module, loader);
    }

    public static void refreshTrackedViews() {
        Runnable refreshRunnable = () -> {
            ArrayList<View> views = new ArrayList<>(TRACKED_RECENTS_VIEWS.keySet());
            for (View recentsView : views) {
                if (recentsView == null) {
                    continue;
                }
                prepareRecentsView(recentsView);
                if (shouldUseStackLayout(recentsView)) {
                    applyStackLayout(recentsView, false);
                } else {
                    reapplyOriginalTransforms(recentsView);
                }
            }
        };
        Handler handler = ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(refreshRunnable);
        } else {
            refreshRunnable.run();
        }
    }

    private static void hookRecentsViewConstructors(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LAUNCHER_RECENTS_VIEW_CLASS, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof View) {
                        View recentsView = (View) thisObject;
                        trackRecentsView(recentsView);
                        recentsView.post(() -> {
                            prepareRecentsView(recentsView);
                            captureStockTaskStates(recentsView);
                            applyStackLayout(recentsView, false);
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
            FlymeStatusBarSizer module, ClassLoader loader, String methodName) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    captureStockTaskStates(recentsView);
                    applyStackLayout(recentsView, false);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView." + methodName,
                    t);
        }
    }

    private static void hookRecentsViewOnLayout(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onLayout",
                    boolean.class,
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
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    captureStockTaskStates(recentsView);
                    applyStackLayout(recentsView, false);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onLayout",
                    t);
        }
    }

    private static void hookRecentsViewOnScrollChanged(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onScrollChanged", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    captureStockTaskStates(recentsView);
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
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

    private static void hookRecentsViewContentAlpha(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setContentAlpha", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    captureStockTaskStates(recentsView);
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setContentAlpha",
                    t);
        }
    }

    private static void hookRecentsViewDraw(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("draw", Canvas.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.draw",
                    t);
        }
    }

    private static void hookRecentsViewStartHome(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("startHome", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldAnimateBlankTapHomeExit(recentsView)) {
                        if (invokeBoolean(recentsView, "canStartHomeSafely", false)) {
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

    private static void hookRecentsViewFreeScrollSettling(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onNotSnappingToPageInFreeScroll");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
                        recentsView.invalidate();
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onNotSnappingToPageInFreeScroll",
                    t);
        }
    }

    private static void hookRecentsViewPrepareGestureEndAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> gestureEndTargetClass =
                    Class.forName("com.android.quickstep.GestureState$GestureEndTarget", false, loader);
            Class<?> remoteTargetHandleArrayClass =
                    Class.forName("[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;", false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onPrepareGestureEndAnimation",
                    AnimatorSet.class,
                    gestureEndTargetClass,
                    remoteTargetHandleArrayClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object endTarget = chain.getArg(1);
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)
                            && isRecentsGestureEndTarget(endTarget)) {
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

    private static void hookRecentsViewGestureAnimationEnd(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onGestureAnimationEnd");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object endTarget = FlymeStatusBarSizer.getFieldCompat(thisObject, "mCurrentGestureEndTarget");
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)
                            && isRecentsGestureEndTarget(endTarget)) {
                        finishRunningTaskReleaseToStack(recentsView);
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

    private static void hookPagedViewOnTouchEvent(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                if (isRecentsViewObject(thisObject)
                        && thisObject instanceof View
                        && motionEvent != null) {
                    View recentsView = (View) thisObject;
                    if (shouldUseStackLayout(recentsView)
                            && shouldSuppressPagedRelease(recentsView, motionEvent)) {
                        trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        suppressPagedRelease(recentsView, motionEvent);
                        return true;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.onTouchEvent",
                    t);
        }
    }

    private static void hookPagedViewSnapToDestination(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("snapToDestination");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (isRecentsViewObject(thisObject) && thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
                        recentsView.invalidate();
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.snapToDestination",
                    t);
        }
    }

    private static void hookTaskViewPressScale(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_CLASS, false, loader);

            Method scaleDownMethod = clazz.getDeclaredMethod("scaleDown");
            scaleDownMethod.setAccessible(true);
            module.intercept(scaleDownMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (shouldSuppressTaskPressScale(taskView)) {
                        resetTaskTouchScale(taskView);
                        return null;
                    }
                }
                return chain.proceed();
            });

            Method scaleUpMethod = clazz.getDeclaredMethod("scaleUp", boolean.class);
            scaleUpMethod.setAccessible(true);
            module.intercept(scaleUpMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (shouldSuppressTaskPressScale(taskView)) {
                        resetTaskTouchScale(taskView);
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView press scale",
                    t);
        }
    }

    private static void trackRecentsView(View recentsView) {
        if (recentsView == null) {
            return;
        }
        TRACKED_RECENTS_VIEWS.put(recentsView, Boolean.TRUE);
    }

    private static void prepareRecentsView(View recentsView) {
        if (!(recentsView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recentsView;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        ViewParent parent = group.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).setClipChildren(false);
        }
    }

    private static void applyStackLayout(View recentsView, boolean captureStockState) {
        if (recentsView == null) {
            return;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            restoreTaskTransforms(recentsView, taskViewCount);
            return;
        }

        float pageSpacing = readIntField(recentsView, "mPageSpacing", 0);
        float referenceWidth = 0f;
        float referenceHeight = 0f;
        float pageSpan = 0f;
        float[] rawOffsets = new float[taskViewCount];

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            rememberOriginalTaskState(taskView);
            rawOffsets[i] = invokeInt(
                    recentsView,
                    "getUnclampedScrollOffset",
                    INT_ARG,
                    invokeInt(recentsView, "getScrollOffset", INT_ARG, 0, i),
                    i);
            if (taskView.getWidth() > 0) {
                referenceWidth = Math.max(referenceWidth, taskView.getWidth());
                pageSpan = Math.max(pageSpan, taskView.getWidth() + pageSpacing);
            }
            if (taskView.getHeight() > 0) {
                referenceHeight = Math.max(referenceHeight, taskView.getHeight());
            }
        }

        if (referenceWidth <= 0f) {
            referenceWidth = Math.max(1, recentsView.getWidth());
        }
        if (referenceHeight <= 0f) {
            referenceHeight = Math.max(1, recentsView.getHeight());
        }
        if (pageSpan <= 1f) {
            pageSpan = referenceWidth + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referenceWidth);
        }

        float blankTapExitProgress = readBlankTapHomeExitProgress(recentsView);
        float stackEntryProgress = resolveStackEntryProgress(recentsView);
        float stackVerticalProgress = resolveStackVerticalProgress(recentsView);
        boolean isTouchHandling = invokeBoolean(recentsView, "isHandlingTouch", false);
        float maxTranslationZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 24);
        float zStepPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 8);

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (isDesktopTask(taskView)) {
                restoreTaskTransform(taskView);
                continue;
            }
            if (captureStockState) {
                captureStockTaskState(taskView);
            }
            float rawOffset = rawOffsets[i];
            float dismissTranslationX = readFloatField(taskView, "dismissTranslationX", 0f);
            // Keep the stock gap-closing animation, but remap its logical page position into
            // the compressed stack so sibling cards move into the dismissed slot instead of
            // adding a second full-page horizontal shift on top of it.
            float effectiveRawOffset = rawOffset + dismissTranslationX;
            float progress = effectiveRawOffset / pageSpan;
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : referenceHeight;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float stackBaseOffsetPx =
                    -taskCenteredLeftPx + (taskWidth * STACK_LEFT_INSET_RATIO);
            float stackFrontLeftPx = recentsView.getWidth() - (taskWidth * STACK_FRONT_VISIBLE_RATIO);
            float screenFrontOffsetPx = stackFrontLeftPx - taskCenteredLeftPx;
            float maxFrontOffsetPx = stackBaseOffsetPx
                    + (taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO));
            float stackFrontOffsetPx = Math.min(screenFrontOffsetPx, maxFrontOffsetPx);
            float stackBackSpreadPx = Math.min(
                    taskWidth * STACK_BACK_SPREAD_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 96));
            float stackEntryLiftPx = Math.min(
                    taskHeight * STACK_ENTRY_LIFT_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 40));
            float blankTapExitTravelPx = Math.max(
                    taskWidth * BLANK_TAP_HOME_EXIT_TRAVEL_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 220));
            float stockVisibleOffset = effectiveRawOffset
                    + readLastStockTaskOffsetX(taskView)
                    + readLastStockHorizontalOffsetX(taskView);
            boolean shouldHoldLeadCardCentered = isTouchHandling
                    && stackVerticalProgress < 0.999f
                    && progress >= 0f
                    && progress < 1.0f;
            float horizontalEntryProgress = shouldHoldLeadCardCentered
                    ? 0f
                    : stackEntryProgress;
            float frontShiftProgress = remapProgress(
                    horizontalEntryProgress,
                    STACK_FRONT_SHIFT_START_PROGRESS,
                    1.0f);
            float frontBaseOffset = lerp(stackBaseOffsetPx, stackFrontOffsetPx, frontShiftProgress);
            float desiredVisibleOffset;
            float desiredScale;
            float desiredTranslationZ;
            float desiredTaskOffsetY;
            float desiredBoxTranslationY;

            if (progress >= 0f) {
                float positiveProgress = Math.max(0f, progress);
                int rightLayer = (int) Math.floor(positiveProgress);
                float localProgress = positiveProgress - rightLayer;
                float handoffProgress = smoothStep((float) Math.pow(
                        localProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                float maxLeadSeparationPx = taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO);
                desiredVisibleOffset = frontBaseOffset
                        + (rightLayer * maxLeadSeparationPx)
                        + (handoffProgress * maxLeadSeparationPx);
                desiredScale = 1.0f;
                desiredTranslationZ = maxTranslationZ
                        + zStepPx
                        + (Math.min(progress, MAX_STACK_LAYERS) / MAX_STACK_LAYERS * maxTranslationZ);
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            } else {
                float stackDepth = clamp(-progress, 0f, MAX_STACK_LAYERS);
                float revealCurve = (float) Math.pow(
                        clamp(stackDepth / MAX_STACK_LAYERS, 0f, 1f),
                        STACK_DEPTH_CURVE_POWER);
                float visualStackDepth = revealCurve * MAX_STACK_LAYERS;
                float backgroundSpreadProgress = clamp(
                        (stackDepth - 1.0f) / Math.max(1.0f, MAX_STACK_LAYERS - 1.0f),
                        0f,
                        1f);
                float backgroundSpreadCurve = (float) Math.pow(
                        backgroundSpreadProgress,
                        STACK_DEPTH_CURVE_POWER);
                float backgroundStackOffset = stackBaseOffsetPx
                        - (stackBackSpreadPx * backgroundSpreadCurve);
                float incomingProgress = remapProgress(progress, -1.0f, 0.0f);
                float frontRevealProgress = smoothStep((float) Math.pow(
                        incomingProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                desiredVisibleOffset = lerp(
                        backgroundStackOffset,
                        frontBaseOffset,
                        frontRevealProgress);
                desiredScale = Math.max(
                        STACK_MIN_SCALE,
                        1.0f - (STACK_SCALE_STEP * visualStackDepth));
                desiredTranslationZ = Math.max(0f, maxTranslationZ - (revealCurve * maxTranslationZ));
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            }
            desiredVisibleOffset = lerp(stockVisibleOffset, desiredVisibleOffset, horizontalEntryProgress);
            desiredScale = lerp(readLastStockNonGridScale(taskView), desiredScale, stackVerticalProgress);
            desiredTaskOffsetY = lerp(
                    readLastStockTaskOffsetY(taskView),
                    desiredTaskOffsetY,
                    stackVerticalProgress);
            desiredBoxTranslationY = lerp(
                    readLastStockBoxTranslationY(taskView),
                    readOriginalBoxTranslationY(taskView),
                    stackVerticalProgress);
            desiredTranslationZ = lerp(
                    readLastStockTranslationZ(taskView),
                    desiredTranslationZ,
                    stackVerticalProgress);
            float desiredStableAlpha = readLastStockStableAlpha(taskView);
            if (blankTapExitProgress > 0f) {
                if (isTaskVisibleInViewport(
                        recentsView,
                        taskCenteredLeftPx,
                        taskWidth,
                        desiredVisibleOffset,
                        desiredScale)) {
                    desiredVisibleOffset -= blankTapExitTravelPx * blankTapExitProgress;
                    desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * blankTapExitProgress);
                    desiredStableAlpha *= 1.0f - blankTapExitProgress;
                } else {
                    desiredStableAlpha = 0f;
                }
            }
            float translationCompensationX = desiredVisibleOffset - effectiveRawOffset;

            taskView.setPivotX(taskWidth * 0.5f);
            taskView.setPivotY(taskHeight * 0.5f);
            setHorizontalOffsetTranslationX(taskView, 0f);
            setTaskOffsetTranslationX(taskView, translationCompensationX);
            setTaskOffsetTranslationY(taskView, desiredTaskOffsetY);
            setBoxTranslationY(taskView, desiredBoxTranslationY);
            setNonGridScale(taskView, desiredScale);
            setStableAlpha(taskView, desiredStableAlpha);
            taskView.setTranslationZ(desiredTranslationZ);
        }
    }

    private static void restoreTaskTransforms(View recentsView, int taskViewCount) {
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            restoreTaskTransform(taskView);
        }
    }

    private static void restoreTaskTransform(View taskView) {
        setHorizontalOffsetTranslationX(taskView, 0f);
        setTaskOffsetTranslationX(taskView, 0f);
        setTaskOffsetTranslationY(taskView, 0f);
        setBoxTranslationY(taskView, readOriginalBoxTranslationY(taskView));
        setNonGridScale(taskView, readOriginalNonGridScale(taskView));
        setStableAlpha(taskView, readLastStockStableAlpha(taskView));
        taskView.setTranslationZ(readLastStockTranslationZ(taskView));
    }

    private static boolean shouldUseStackLayout(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        return shouldUseStackLayout(config, recentsView, taskViewCount);
    }

    private static boolean shouldUseStackLayout(
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config,
            View recentsView,
            int taskViewCount) {
        return config != null
                && config.enabled
                && config.launcherIosStackRecentsEnabled
                && taskViewCount > 0
                && !invokeBoolean(recentsView, "showAsGrid", false)
                && !invokeBoolean(recentsView, "isSplitSelectionActive", false);
    }

    private static void reapplyOriginalTransforms(View recentsView) {
        cancelBlankTapHomeExitAnimation(recentsView, true);
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        restoreTaskTransforms(recentsView, taskViewCount);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageScales", NO_ARGS);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageOffsetsForFlyme", NO_ARGS);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static boolean shouldAnimateBlankTapHomeExit(View recentsView) {
        return recentsView != null
                && shouldUseStackLayout(recentsView)
                && readBooleanField(recentsView, "mTouchDownToStartHome", false);
    }

    private static void startBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator = ACTIVE_HOME_EXIT_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            if (runningAnimator.isStarted() || runningAnimator.isRunning()) {
                return;
            }
            ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
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
                ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
                if (cancelled) {
                    clearBlankTapHomeExitProgress(recentsView);
                    return;
                }
                finishBlankTapHomeExit(recentsView);
            }
        });
        ACTIVE_HOME_EXIT_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void finishBlankTapHomeExit(View recentsView) {
        if (recentsView == null) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "handleStartHome",
                new Class[]{boolean.class},
                false);
        Runnable resetRunnable = () -> clearBlankTapHomeExitProgress(recentsView);
        Handler handler = ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(resetRunnable);
        } else {
            recentsView.post(resetRunnable);
        }
    }

    private static void cancelBlankTapHomeExitAnimation(View recentsView, boolean resetTransform) {
        ValueAnimator animator = ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (resetTransform) {
            clearBlankTapHomeExitProgress(recentsView);
        }
    }

    private static void clearBlankTapHomeExitProgress(View recentsView) {
        if (recentsView == null) {
            return;
        }
        BLANK_TAP_HOME_EXIT_PROGRESS.remove(recentsView);
        setPageAnimOffScreenStart(recentsView, false);
        recentsView.invalidate();
    }

    private static void setBlankTapHomeExitProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        BLANK_TAP_HOME_EXIT_PROGRESS.put(recentsView, clamp(progress, 0f, 1f));
    }

    private static float readBlankTapHomeExitProgress(View recentsView) {
        Float value = BLANK_TAP_HOME_EXIT_PROGRESS.get(recentsView);
        return value != null ? value : 0f;
    }

    private static View getTaskViewAt(View recentsView, int index) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(recentsView, "getTaskViewAt", INT_ARG, index);
        return value instanceof View ? (View) value : null;
    }

    private static boolean shouldSuppressTaskPressScale(View taskView) {
        View recentsView = resolveOwningRecentsView(taskView);
        return shouldUseStackLayout(recentsView);
    }

    private static boolean isRecentsGestureEndTarget(Object value) {
        return value instanceof Enum && "RECENTS".equals(((Enum<?>) value).name());
    }

    private static void switchRunningTaskToScreenshot(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable applyRunnable = () -> finishRunningTaskReleaseToStack(recentsView);
        if (!invokeMethodReflectively(
                recentsView,
                "switchToScreenshot",
                new Class<?>[]{Runnable.class},
                applyRunnable)) {
            applyRunnable.run();
        }
    }

    private static void finishRunningTaskReleaseToStack(View recentsView) {
        if (recentsView == null) {
            return;
        }
        invokeMethodReflectively(
                recentsView,
                "setRunningTaskViewShowScreenshot",
                BOOLEAN_ARG,
                true);
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setEnableDrawingLiveTile",
                BOOLEAN_ARG,
                false);
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setRunningTaskHidden",
                BOOLEAN_ARG,
                false);
        captureStockTaskStates(recentsView);
        applyStackLayout(recentsView, false);
        recentsView.invalidate();
    }

    private static View resolveOwningRecentsView(View taskView) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(taskView, "getRecentsView", NO_ARGS);
        if (value instanceof View) {
            return (View) value;
        }
        ViewParent parent = taskView != null ? taskView.getParent() : null;
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (isRecentsViewObject(parentView)) {
                return parentView;
            }
            parent = parentView.getParent();
        }
        return null;
    }

    private static void resetTaskTouchScale(View taskView) {
        if (taskView == null) {
            return;
        }
        Object animator = FlymeStatusBarSizer.getFieldCompat(taskView, "mTaskThumbScaleAnimator");
        if (animator instanceof Animator) {
            Animator taskScaleAnimator = (Animator) animator;
            if (taskScaleAnimator.isStarted() || taskScaleAnimator.isRunning()) {
                taskScaleAnimator.cancel();
            }
        }
        Object scaleUpRunnable = FlymeStatusBarSizer.getFieldCompat(taskView, "mScaleUpRunnable");
        if (scaleUpRunnable instanceof Runnable) {
            taskView.removeCallbacks((Runnable) scaleUpRunnable);
        }
        writeField(taskView, "mTaskThumbScaleAnimator", null);
        setNonGridScale(taskView, readFloatField(taskView, "nonGridScale", 1f));
    }

    private static boolean shouldSuppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        int action = motionEvent.getActionMasked();
        return (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && invokeBoolean(recentsView, "isHandlingTouch", false);
    }

    private static void suppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        clearRecentsDeferredSnap(recentsView);
        if (motionEvent != null && motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
            startUnsnappedFlingIfNeeded(recentsView, motionEvent);
        } else {
            FlymeStatusBarSizer.invokeMethodCompat(recentsView, "abortScrollerAnimation", NO_ARGS);
        }
        releasePagedEdgeEffects(recentsView, motionEvent);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "resetTouchState", NO_ARGS);
        captureStockTaskStates(recentsView);
        applyStackLayout(recentsView, false);
        recentsView.invalidate();
    }

    private static void startUnsnappedFlingIfNeeded(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return;
        }
        Object velocityTrackerValue = FlymeStatusBarSizer.getFieldCompat(recentsView, "mVelocityTracker");
        if (!(velocityTrackerValue instanceof VelocityTracker)) {
            return;
        }
        VelocityTracker velocityTracker = (VelocityTracker) velocityTrackerValue;
        velocityTracker.addMovement(motionEvent);
        int maximumVelocity = invokeInt(recentsView, "getMaximumVelocity", Integer.MAX_VALUE);
        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
        int activePointerId = readIntField(recentsView, "mActivePointerId", -1);
        int primaryVelocity = Math.round(resolvePrimaryVelocity(recentsView, velocityTracker, activePointerId));
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int minScroll = readIntField(recentsView, "mMinScroll", primaryScroll);
        int maxScroll = readIntField(recentsView, "mMaxScroll", primaryScroll);

        if (primaryScroll < minScroll || primaryScroll > maxScroll) {
            startPagedSpringBack(recentsView, primaryScroll, minScroll, maxScroll);
            return;
        }
        if (!shouldKeepFreeScrollFling(recentsView, primaryVelocity)) {
            return;
        }
        Object scroller = FlymeStatusBarSizer.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        setScrollerFriction(scroller, 0.03f);
        if (!startScrollerFling(recentsView, scroller, primaryScroll, primaryVelocity, minScroll, maxScroll)) {
            return;
        }
        setIntField(recentsView, "mNextPage", readIntField(recentsView, "mCurrentPage", -1));
    }

    private static boolean shouldKeepFreeScrollFling(View recentsView, int primaryVelocity) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "shouldFlingForVelocity",
                INT_ARG,
                primaryVelocity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static float resolvePrimaryVelocity(
            View recentsView,
            VelocityTracker velocityTracker,
            int activePointerId) {
        Object orientationHandler = FlymeStatusBarSizer.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
                orientationHandler,
                "getPrimaryVelocity",
                new Class<?>[]{VelocityTracker.class, int.class},
                velocityTracker,
                activePointerId);
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return activePointerId >= 0
                ? velocityTracker.getXVelocity(activePointerId)
                : velocityTracker.getXVelocity();
    }

    private static int resolvePrimaryScroll(View recentsView) {
        Object orientationHandler = FlymeStatusBarSizer.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
                orientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                recentsView);
        return value instanceof Integer ? (Integer) value : recentsView.getScrollX();
    }

    private static void startPagedSpringBack(
            View recentsView,
            int primaryScroll,
            int minScroll,
            int maxScroll) {
        Object scroller = FlymeStatusBarSizer.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        invokeScrollerSpringBack(scroller, primaryScroll, minScroll, maxScroll);
        setIntField(recentsView, "mNextPage", readIntField(recentsView, "mCurrentPage", -1));
    }

    private static boolean startScrollerFling(
            View recentsView,
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll) {
        int overX = Math.round(recentsView.getWidth() * 0.5f * 0.07f);
        invokeScrollerFling10(scroller, primaryScroll, primaryVelocity, minScroll, maxScroll, overX);
        int afterFinalX = readScrollerFinalX(scroller, primaryScroll);
        if (afterFinalX != primaryScroll) {
            return true;
        }
        invokeScrollerFling8(scroller, primaryScroll, primaryVelocity, minScroll, maxScroll);
        return readScrollerFinalX(scroller, primaryScroll) != primaryScroll;
    }

    private static void setScrollerFriction(Object scroller, float friction) {
        invokeScrollerMethod(scroller, "setFriction", FLOAT_ARG, friction);
    }

    private static void invokeScrollerSpringBack(
            Object scroller,
            int primaryScroll,
            int minScroll,
            int maxScroll) {
        invokeScrollerMethod(
                scroller,
                "springBack",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                minScroll,
                maxScroll,
                0,
                0);
    }

    private static void invokeScrollerFling10(
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll,
            int overX) {
        invokeScrollerMethod(
                scroller,
                "fling",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                -primaryVelocity,
                0,
                minScroll,
                maxScroll,
                0,
                0,
                overX,
                0);
    }

    private static void invokeScrollerFling8(
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll) {
        invokeScrollerMethod(
                scroller,
                "fling",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                -primaryVelocity,
                0,
                minScroll,
                maxScroll,
                0,
                0);
    }

    private static void invokeScrollerMethod(
            Object scroller,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (scroller == null) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(scroller, methodName, parameterTypes, args);
        Object activeScroller = FlymeStatusBarSizer.getFieldCompat(scroller, "usingScroller");
        if (activeScroller != null && activeScroller != scroller) {
            FlymeStatusBarSizer.invokeMethodCompat(activeScroller, methodName, parameterTypes, args);
        }
    }

    private static int readScrollerFinalX(Object scroller, int fallback) {
        if (scroller == null) {
            return fallback;
        }
        Object value = FlymeStatusBarSizer.invokeMethodCompat(scroller, "getFinalX", NO_ARGS);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        Object activeScroller = FlymeStatusBarSizer.getFieldCompat(scroller, "usingScroller");
        Object activeValue = FlymeStatusBarSizer.invokeMethodCompat(activeScroller, "getFinalX", NO_ARGS);
        return activeValue instanceof Integer ? (Integer) activeValue : fallback;
    }

    private static void releasePagedEdgeEffects(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        releaseEdgeEffect(FlymeStatusBarSizer.getFieldCompat(recentsView, "mEdgeGlowLeft"), motionEvent);
        releaseEdgeEffect(FlymeStatusBarSizer.getFieldCompat(recentsView, "mEdgeGlowRight"), motionEvent);
    }

    private static void releaseEdgeEffect(Object edgeEffect, MotionEvent motionEvent) {
        if (edgeEffect == null) {
            return;
        }
        if (motionEvent != null) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    edgeEffect,
                    "onRelease",
                    new Class<?>[]{MotionEvent.class},
                    motionEvent);
        }
        FlymeStatusBarSizer.invokeMethodCompat(edgeEffect, "onRelease", NO_ARGS);
    }

    private static void clearRecentsDeferredSnap(View recentsView) {
        Object handlerValue = FlymeStatusBarSizer.getFieldCompat(
                recentsView,
                "mMainHandlerForAbortScrollAndCheckSnap");
        Object timeoutValue = FlymeStatusBarSizer.getFieldCompat(recentsView, "mTimeoutToCheckSnap");
        Object abortRunnerValue = FlymeStatusBarSizer.getFieldCompat(
                recentsView,
                "mAbortRecentsViewScrollAnimRunner");
        if (handlerValue instanceof Handler) {
            Handler handler = (Handler) handlerValue;
            if (timeoutValue instanceof Runnable) {
                handler.removeCallbacks((Runnable) timeoutValue);
            }
            if (abortRunnerValue instanceof Runnable) {
                handler.removeCallbacks((Runnable) abortRunnerValue);
            }
        }
        setBooleanField(recentsView, "mNeedCheckSnapToDestination", false);
        setIntField(recentsView, "mLastHandleActionUpChildIndex", -1);
    }

    private static void captureStockTaskStates(View recentsView) {
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null || isDesktopTask(taskView)) {
                continue;
            }
            captureStockTaskState(taskView);
        }
    }

    private static void captureStockTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        rememberOriginalTaskState(taskView);
        LAST_STOCK_TASK_OFFSET_XS.put(taskView, readFloatField(taskView, "taskOffsetTranslationX", 0f));
        LAST_STOCK_TASK_OFFSET_YS.put(taskView, readFloatField(taskView, "taskOffsetTranslationY", 0f));
        LAST_STOCK_HORIZONTAL_OFFSET_XS.put(
                taskView,
                readFloatField(taskView, "horizontalOffsetTranslationX", 0f));
        LAST_STOCK_NON_GRID_SCALES.put(taskView, readFloatField(taskView, "nonGridScale", 1f));
        LAST_STOCK_BOX_TRANSLATION_YS.put(
                taskView,
                readFloatField(taskView, "boxTranslationY", readOriginalBoxTranslationY(taskView)));
        LAST_STOCK_STABLE_ALPHAS.put(taskView, readStableAlpha(taskView));
        LAST_STOCK_TRANSLATION_ZS.put(taskView, taskView.getTranslationZ());
    }

    private static void setHorizontalOffsetTranslationX(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setHorizontalOffsetTranslationX",
                FLOAT_ARG,
                value);
    }

    private static void setTaskOffsetTranslationX(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setTaskOffsetTranslationX",
                FLOAT_ARG,
                value);
    }

    private static void setTaskOffsetTranslationY(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setTaskOffsetTranslationY",
                FLOAT_ARG,
                value);
    }

    private static void setNonGridScale(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setNonGridScale",
                FLOAT_ARG,
                value);
    }

    private static void setBoxTranslationY(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setBoxTranslationY",
                FLOAT_ARG,
                value);
    }

    private static void setStableAlpha(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setStableAlpha",
                FLOAT_ARG,
                clamp(value, 0f, 1f));
    }

    private static boolean isDesktopTask(View taskView) {
        return taskView != null
                && taskView.getClass().getName().contains("DesktopTaskView");
    }

    private static boolean isRecentsViewObject(Object value) {
        if (!(value instanceof View)) {
            return false;
        }
        String className = value.getClass().getName();
        return RECENTS_VIEW_CLASS.equals(className)
                || LAUNCHER_RECENTS_VIEW_CLASS.equals(className)
                || className.endsWith(".RecentsView")
                || className.endsWith("LauncherRecentsView");
    }

    private static void rememberOriginalTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        if (!ORIGINAL_NON_GRID_SCALES.containsKey(taskView)) {
            ORIGINAL_NON_GRID_SCALES.put(taskView, readFloatField(taskView, "nonGridScale", 1f));
        }
        if (!ORIGINAL_BOX_TRANSLATION_YS.containsKey(taskView)) {
            ORIGINAL_BOX_TRANSLATION_YS.put(taskView, readFloatField(taskView, "boxTranslationY", 0f));
        }
    }

    private static float readOriginalNonGridScale(View taskView) {
        Float value = ORIGINAL_NON_GRID_SCALES.get(taskView);
        return value != null ? value : 1f;
    }

    private static float readOriginalBoxTranslationY(View taskView) {
        Float value = ORIGINAL_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockTaskOffsetX(View taskView) {
        Float value = LAST_STOCK_TASK_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockTaskOffsetY(View taskView) {
        Float value = LAST_STOCK_TASK_OFFSET_YS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockHorizontalOffsetX(View taskView) {
        Float value = LAST_STOCK_HORIZONTAL_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockNonGridScale(View taskView) {
        Float value = LAST_STOCK_NON_GRID_SCALES.get(taskView);
        return value != null ? value : readOriginalNonGridScale(taskView);
    }

    private static float readLastStockBoxTranslationY(View taskView) {
        Float value = LAST_STOCK_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : readOriginalBoxTranslationY(taskView);
    }

    private static float readLastStockStableAlpha(View taskView) {
        Float value = LAST_STOCK_STABLE_ALPHAS.get(taskView);
        return value != null ? value : 1f;
    }

    private static float readLastStockTranslationZ(View taskView) {
        Float value = LAST_STOCK_TRANSLATION_ZS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readStableAlpha(View taskView) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(taskView, "getStableAlpha", NO_ARGS);
        if (value instanceof Float) {
            return (Float) value;
        }
        return taskView.getAlpha();
    }

    private static int invokeInt(Object target, String methodName, int fallback) {
        return invokeInt(target, methodName, NO_ARGS, fallback);
    }

    private static int invokeInt(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            int fallback,
            Object... args) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, methodName, parameterTypes, args);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static boolean invokeBoolean(Object target, String methodName, boolean fallback) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, methodName, NO_ARGS);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int readIntField(Object target, String name, int fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static float readFloatField(Object target, String name, float fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Float ? (Float) value : fallback;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        writeField(target, name, value);
    }

    private static void setIntField(Object target, String name, int value) {
        writeField(target, name, value);
    }

    private static void writeField(Object target, String name, Object value) {
        if (target == null || name == null) {
            return;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static boolean invokeMethodReflectively(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null) {
            return false;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static float resolveStackEntryProgress(View recentsView) {
        float adjacentOffset = clamp(
                readFloatField(recentsView, "mAdjacentPageHorizontalOffset", 0f),
                0f,
                1f);
        float fullscreenProgress = clamp(
                readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        float collapsedProgress = Math.max(adjacentOffset, fullscreenProgress);
        return clamp((1.0f - collapsedProgress) * contentAlpha, 0f, 1f);
    }

    private static float resolveStackVerticalProgress(View recentsView) {
        float fullscreenProgress = clamp(
                readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        return clamp((1.0f - fullscreenProgress) * contentAlpha, 0f, 1f);
    }

    private static boolean isTaskVisibleInViewport(
            View recentsView,
            float centeredLeftPx,
            float taskWidth,
            float desiredVisibleOffset,
            float desiredScale) {
        float clampedScale = Math.max(0.5f, desiredScale);
        float translatedLeftPx = centeredLeftPx + desiredVisibleOffset;
        float actualLeftPx = translatedLeftPx + ((1.0f - clampedScale) * taskWidth * 0.5f);
        float actualRightPx = actualLeftPx + (taskWidth * clampedScale);
        return actualRightPx > 0f && actualLeftPx < recentsView.getWidth();
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * clamp(progress, 0f, 1f));
    }

    private static float remapProgress(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1f : 0f;
        }
        return clamp((value - start) / (end - start), 0f, 1f);
    }

    private static float smoothStep(float value) {
        float clamped = clamp(value, 0f, 1f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    private static void setPageAnimOffScreenStart(View recentsView, boolean value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setPageAnimOffScreenStart",
                BOOLEAN_ARG,
                value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Handler ensureMainHandler() {
        Handler handler = mainHandler;
        if (handler != null) {
            return handler;
        }
        Looper looper = Looper.getMainLooper();
        if (looper == null) {
            return null;
        }
        Handler created = new Handler(looper);
        mainHandler = created;
        return created;
    }
}
