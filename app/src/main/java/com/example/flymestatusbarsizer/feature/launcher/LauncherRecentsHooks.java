package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class LauncherRecentsHooks {
    private static final Class<?>[] NO_ARGS = new Class[0];
    private static final Class<?>[] INT_ARG = new Class[]{int.class};
    private static final Class<?>[] FLOAT_ARG = new Class[]{float.class};
    private static final String LAUNCHER_RECENTS_VIEW_CLASS =
            "com.android.quickstep.views.LauncherRecentsView";
    private static final String PAGED_VIEW_CLASS = "com.android.launcher3.PagedView";
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final float STACK_BACK_REVEAL_DECAY = 0.80f;
    private static final float STACK_HORIZONTAL_STEP_RATIO = 0.22f;
    private static final float STACK_OUTGOING_TRAVEL_RATIO = 0.70f;
    private static final float STACK_SCALE_STEP = 0.055f;
    private static final float STACK_MIN_SCALE = 0.80f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final WeakHashMap<View, Boolean> TRACKED_RECENTS_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
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
        hookRecentsViewDraw(module, loader);
        hookRecentsViewFreeScrollSettling(module, loader);
        hookPagedViewSnapToDestination(module, loader);
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
                    applyStackLayout(recentsView);
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
                            applyStackLayout(recentsView);
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
                    applyStackLayout(recentsView);
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
                    applyStackLayout(recentsView);
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
                    if (shouldUseStackLayout(recentsView)) {
                        applyStackLayout(recentsView);
                        if (invokeBoolean(recentsView, "isScrollerFinished", false)
                                && !invokeBoolean(recentsView, "isHandlingTouch", false)) {
                            syncCurrentPageToNearestIfNeeded(recentsView);
                        }
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
                        applyStackLayout(recentsView);
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
                        applyStackLayout(recentsView);
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
                        syncCurrentPageToNearestIfNeeded(recentsView);
                        applyStackLayout(recentsView);
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

    private static void applyStackLayout(View recentsView) {
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

        float stackLeftMarginPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 16);
        float stackLeftShiftPx = -Math.max(0f, (recentsView.getWidth() - referenceWidth) * 0.5f)
                + stackLeftMarginPx;
        float horizontalStepPx = Math.min(referenceWidth * STACK_HORIZONTAL_STEP_RATIO,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 92));
        float outgoingTravelPx = Math.max(referenceWidth * STACK_OUTGOING_TRAVEL_RATIO,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 220));
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
            float rawOffset = rawOffsets[i];
            float dismissTranslationX = readFloatField(taskView, "dismissTranslationX", 0f);
            // Keep the stock gap-closing animation, but remap its logical page position into
            // the compressed stack so sibling cards move into the dismissed slot instead of
            // adding a second full-page horizontal shift on top of it.
            float effectiveRawOffset = rawOffset + dismissTranslationX;
            float progress = effectiveRawOffset / pageSpan;
            float desiredVisibleOffset;
            float desiredScale;
            float desiredTranslationZ;

            if (progress >= 0f) {
                float outgoingProgress = clamp(progress, 0f, MAX_STACK_LAYERS);
                desiredVisibleOffset = stackLeftShiftPx + horizontalStepPx
                        + (outgoingProgress * outgoingTravelPx);
                desiredScale = Math.max(0.92f, 1.0f - (0.03f * outgoingProgress));
                desiredTranslationZ = maxTranslationZ + (outgoingProgress * zStepPx);
            } else {
                float stackDepth = Math.max(-progress, 0f);
                float revealCurve = (float) Math.exp(-STACK_BACK_REVEAL_DECAY * stackDepth);
                float visualStackDepth = clamp(stackDepth, 0f, MAX_STACK_LAYERS);
                desiredVisibleOffset = stackLeftShiftPx + (horizontalStepPx * revealCurve);
                desiredScale = Math.max(
                        STACK_MIN_SCALE,
                        1.0f - (STACK_SCALE_STEP * visualStackDepth));
                desiredTranslationZ = Math.max(0f, maxTranslationZ - (visualStackDepth * zStepPx));
            }
            float translationCompensationX = desiredVisibleOffset - effectiveRawOffset;

            taskView.setPivotX(0f);
            taskView.setPivotY(taskView.getHeight() * 0.5f);
            setHorizontalOffsetTranslationX(taskView, 0f);
            setTaskOffsetTranslationX(taskView, translationCompensationX);
            setTaskOffsetTranslationY(taskView, 0f);
            setBoxTranslationY(taskView, readOriginalBoxTranslationY(taskView));
            setNonGridScale(taskView, desiredScale);
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
        taskView.setTranslationZ(0f);
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
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        restoreTaskTransforms(recentsView, taskViewCount);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageScales", NO_ARGS);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageOffsetsForFlyme", NO_ARGS);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static View getTaskViewAt(View recentsView, int index) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(recentsView, "getTaskViewAt", INT_ARG, index);
        return value instanceof View ? (View) value : null;
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

    private static void syncCurrentPageToNearestIfNeeded(View recentsView) {
        int page = findNearestTaskIndexByRawOffset(recentsView);
        int currentPage = invokeInt(recentsView, "getCurrentPage", -1);
        if (page >= 0 && page != currentPage) {
            FlymeStatusBarSizer.invokeMethodCompat(recentsView, "setCurrentPage", INT_ARG, page);
        }
    }

    private static int findNearestTaskIndexByRawOffset(View recentsView) {
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < taskViewCount; i++) {
            float offset = invokeInt(
                    recentsView,
                    "getUnclampedScrollOffset",
                    INT_ARG,
                    invokeInt(recentsView, "getScrollOffset", INT_ARG, 0, i),
                    i);
            float distance = Math.abs(offset);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
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
