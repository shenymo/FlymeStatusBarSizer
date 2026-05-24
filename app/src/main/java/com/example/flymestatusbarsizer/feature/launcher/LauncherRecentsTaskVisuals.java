package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.List;

final class LauncherRecentsTaskVisuals {
    private static final float MODULE_APPLIED_EPSILON = 0.01f;
    private static final int STACK_CONTENT_MAX_BLUR_DP = 18;
    private static final String ACTIVITY_TITLE_FIELD = "mActivityTitle";
    private static final ViewOutlineProvider STACK_TASK_SHADOW_OUTLINE_PROVIDER =
            new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    Rect bounds = new Rect();
                    LauncherRecentsCompat.invokeCompat(
                            view,
                            "getThumbnailBounds",
                            new Class<?>[]{Rect.class, boolean.class},
                            bounds,
                            false);
                    if (bounds.isEmpty()) {
                        bounds.set(0, 0, view.getWidth(), view.getHeight());
                    }
                    outline.setRoundRect(
                            bounds,
                            FlymeStatusBarSizer.dp(view.getContext(), 22));
                }
            };

    private LauncherRecentsTaskVisuals() {
    }

    static void captureStockTaskStates(View recentsView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            captureStockTaskState(taskView);
        }
    }

    static void captureCurrentTaskStatesAsBaseline(View recentsView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            captureCurrentTaskStateAsBaseline(taskView);
        }
    }

    static void captureStockTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        if (LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)) {
            return;
        }
        if (isCurrentTaskStateModuleApplied(taskView)) {
            return;
        }
        rememberOriginalTaskState(taskView);
        captureCurrentTaskStateAsBaseline(taskView);
    }

    private static void captureCurrentTaskStateAsBaseline(View taskView) {
        if (taskView == null) {
            return;
        }
        rememberOriginalTaskState(taskView);
        LauncherRecentsState.LAST_STOCK_TASK_OFFSET_XS.put(
                taskView,
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f));
        LauncherRecentsState.LAST_STOCK_TASK_OFFSET_YS.put(
                taskView,
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f));
        LauncherRecentsState.LAST_STOCK_HORIZONTAL_OFFSET_XS.put(
                taskView,
                LauncherRecentsCompat.readFloatField(taskView, "horizontalOffsetTranslationX", 0f));
        LauncherRecentsState.LAST_STOCK_NON_GRID_SCALES.put(
                taskView,
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f));
        LauncherRecentsState.LAST_STOCK_BOX_TRANSLATION_YS.put(
                taskView,
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        readOriginalBoxTranslationY(taskView)));
        LauncherRecentsState.LAST_STOCK_ATTACH_ALPHAS.put(taskView, readAttachAlpha(taskView));
        LauncherRecentsState.LAST_STOCK_STABLE_ALPHAS.put(taskView, readStableAlpha(taskView));
        LauncherRecentsState.LAST_STOCK_TRANSLATION_ZS.put(taskView, taskView.getTranslationZ());
        LauncherRecentsState.LAST_STOCK_FULLSCREEN_PROGRESSES.put(
                taskView,
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f));
    }

    static void setHorizontalOffsetTranslationX(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setHorizontalOffsetTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.put(taskView, value);
    }

    static void setTaskOffsetTranslationX(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.put(taskView, value);
    }

    static void setTaskOffsetTranslationY(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.put(taskView, value);
    }

    static void setNonGridScale(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setNonGridScale",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.put(taskView, value);
    }

    static void setBoxTranslationY(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setBoxTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.put(taskView, value);
    }

    static void setAttachAlpha(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setAttachAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.put(taskView, clampedValue);
    }

    static void setStableAlpha(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setStableAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.put(taskView, clampedValue);
    }

    static void setActivityTitleAlpha(View taskView, float value) {
        View titleView = resolveActivityTitleView(taskView);
        if (titleView != null) {
            titleView.setAlpha(LauncherRecentsLayoutEngine.clamp(value, 0f, 1f));
        }
    }

    static void setStackContentBlur(View taskView, float stableAlpha) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return;
        }
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        float alpha = LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)
                ? 1f
                : LauncherRecentsLayoutEngine.clamp(stableAlpha, 0f, 1f);
        float blurPx = FlymeStatusBarSizer.dp(
                taskView.getContext(),
                STACK_CONTENT_MAX_BLUR_DP) * (1f - alpha);
        Object containersObject = LauncherRecentsCompat.invokeCompat(taskView, "getTaskContainers");
        if (!(containersObject instanceof List)) {
            return;
        }
        List<?> taskContainers = (List<?>) containersObject;
        for (int i = 0; i < taskContainers.size(); i++) {
            Object taskContainer = taskContainers.get(i);
            Object snapshotView = LauncherRecentsCompat.invokeCompat(taskContainer, "getSnapshotView");
            applyStackContentBlur(snapshotView instanceof View ? (View) snapshotView : null, blurPx);
            Object iconView = LauncherRecentsCompat.invokeCompat(taskContainer, "getIconView");
            Object iconAsView = LauncherRecentsCompat.invokeCompat(iconView, "asView");
            applyStackIconBlur(
                    iconView,
                    iconAsView instanceof View ? (View) iconAsView : null,
                    blurPx);
        }
    }

    static void clearStackContentBlur(View taskView) {
        setStackContentBlur(taskView, 1f);
    }

    static void setFullscreenProgress(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setFullscreenProgress",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.put(taskView, clampedValue);
    }

    static void setTranslationZ(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        taskView.setTranslationZ(value);
        LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.put(taskView, value);
    }

    static void setStackShadowElevation(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        rememberOriginalTaskState(taskView);
        taskView.setOutlineProvider(STACK_TASK_SHADOW_OUTLINE_PROVIDER);
        taskView.setElevation(value);
        taskView.invalidateOutline();
        LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.put(taskView, value);
    }

    static void rememberOriginalTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        if (!LauncherRecentsState.ORIGINAL_NON_GRID_SCALES.containsKey(taskView)) {
            LauncherRecentsState.ORIGINAL_NON_GRID_SCALES.put(
                    taskView,
                    LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f));
        }
        if (!LauncherRecentsState.ORIGINAL_BOX_TRANSLATION_YS.containsKey(taskView)) {
            LauncherRecentsState.ORIGINAL_BOX_TRANSLATION_YS.put(
                    taskView,
                    LauncherRecentsCompat.readFloatField(taskView, "boxTranslationY", 0f));
        }
        if (!LauncherRecentsState.ORIGINAL_TASK_ELEVATIONS.containsKey(taskView)) {
            LauncherRecentsState.ORIGINAL_TASK_ELEVATIONS.put(taskView, taskView.getElevation());
        }
        if (!LauncherRecentsState.ORIGINAL_TASK_OUTLINE_PROVIDERS.containsKey(taskView)) {
            LauncherRecentsState.ORIGINAL_TASK_OUTLINE_PROVIDERS.put(
                    taskView,
                    taskView.getOutlineProvider());
        }
    }

    static float readOriginalNonGridScale(View taskView) {
        Float value = LauncherRecentsState.ORIGINAL_NON_GRID_SCALES.get(taskView);
        return value != null ? value : 1f;
    }

    static float readOriginalBoxTranslationY(View taskView) {
        Float value = LauncherRecentsState.ORIGINAL_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readLastStockTaskOffsetX(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_TASK_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readLastStockTaskOffsetY(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_TASK_OFFSET_YS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readLastStockHorizontalOffsetX(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_HORIZONTAL_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readLastStockNonGridScale(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_NON_GRID_SCALES.get(taskView);
        return value != null ? value : readOriginalNonGridScale(taskView);
    }

    static float readLastStockBoxTranslationY(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : readOriginalBoxTranslationY(taskView);
    }

    static float readLastStockAttachAlpha(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_ATTACH_ALPHAS.get(taskView);
        return value != null ? value : 1f;
    }

    static float readLastStockStableAlpha(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_STABLE_ALPHAS.get(taskView);
        return value != null ? value : 1f;
    }

    static float readLastStockTranslationZ(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_TRANSLATION_ZS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readOriginalTaskElevation(View taskView) {
        Float value = LauncherRecentsState.ORIGINAL_TASK_ELEVATIONS.get(taskView);
        return value != null ? value : 0f;
    }

    static float readLastStockFullscreenProgress(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_FULLSCREEN_PROGRESSES.get(taskView);
        return value != null ? value : 0f;
    }

    static float readStableAlpha(View taskView) {
        Object value = LauncherRecentsCompat.invokeCompat(taskView, "getStableAlpha");
        if (value instanceof Float) {
            return (Float) value;
        }
        return taskView.getAlpha();
    }

    private static View resolveActivityTitleView(View taskView) {
        Object value = LauncherRecentsCompat.getFieldCompat(taskView, ACTIVITY_TITLE_FIELD);
        return value instanceof View ? (View) value : null;
    }

    static float readAttachAlpha(View taskView) {
        Object value = LauncherRecentsCompat.invokeCompat(taskView, "getAttachAlpha");
        if (value instanceof Float) {
            return (Float) value;
        }
        return 1f;
    }

    static void clearAppliedTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.remove(taskView);
    }

    private static void applyStackContentBlur(View view, float blurPx) {
        if (view == null) {
            return;
        }
        float appliedBlurPx = blurPx > MODULE_APPLIED_EPSILON ? blurPx : 0f;
        Float lastAppliedBlurPx = LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.get(view);
        if (lastAppliedBlurPx != null
                && Math.abs(lastAppliedBlurPx - appliedBlurPx) < MODULE_APPLIED_EPSILON) {
            return;
        }
        if (appliedBlurPx == 0f) {
            view.setRenderEffect(null);
        } else {
            view.setRenderEffect(RenderEffect.createBlurEffect(
                    appliedBlurPx,
                    appliedBlurPx,
                    Shader.TileMode.CLAMP));
        }
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.put(view, appliedBlurPx);
    }

    private static void applyStackIconBlur(Object iconView, View view, float blurPx) {
        if (view == null) {
            return;
        }
        float appliedBlurPx = blurPx > MODULE_APPLIED_EPSILON ? blurPx : 0f;
        if (appliedBlurPx == 0f) {
            restoreStackIconClip(view);
            applyStackContentBlur(view, 0f);
            return;
        }
        rememberStackIconClip(view);
        int iconWidth = readIconSize(iconView, "getDrawableWidth", view.getWidth());
        int iconHeight = readIconSize(iconView, "getDrawableHeight", view.getHeight());
        view.setOutlineProvider(createStackIconBlurOutlineProvider(iconWidth, iconHeight));
        view.setClipToOutline(true);
        view.invalidateOutline();
        applyStackContentBlur(view, appliedBlurPx);
    }

    private static void rememberStackIconClip(View view) {
        if (!LauncherRecentsState.ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS.containsKey(view)) {
            LauncherRecentsState.ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS.put(
                    view,
                    view.getOutlineProvider());
        }
        if (!LauncherRecentsState.ORIGINAL_STACK_ICON_CLIP_TO_OUTLINES.containsKey(view)) {
            LauncherRecentsState.ORIGINAL_STACK_ICON_CLIP_TO_OUTLINES.put(
                    view,
                    view.getClipToOutline());
        }
    }

    private static void restoreStackIconClip(View view) {
        if (!LauncherRecentsState.ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS.containsKey(view)) {
            return;
        }
        view.setOutlineProvider(LauncherRecentsState.ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS.remove(
                view));
        Boolean clipToOutline =
                LauncherRecentsState.ORIGINAL_STACK_ICON_CLIP_TO_OUTLINES.remove(view);
        view.setClipToOutline(clipToOutline != null && clipToOutline);
        view.invalidateOutline();
    }

    private static ViewOutlineProvider createStackIconBlurOutlineProvider(
            int iconWidth,
            int iconHeight) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int width = iconWidth > 0 ? Math.min(iconWidth, view.getWidth()) : view.getWidth();
                int height = iconHeight > 0 ? Math.min(iconHeight, view.getHeight()) : view.getHeight();
                if (width <= 0 || height <= 0) {
                    outline.setEmpty();
                    return;
                }
                int left = Math.max(0, (view.getWidth() - width) / 2);
                int top = Math.max(0, (view.getHeight() - height) / 2);
                float radius = Math.min(width, height) * 0.22f;
                outline.setRoundRect(left, top, left + width, top + height, radius);
            }
        };
    }

    private static int readIconSize(Object iconView, String methodName, int fallback) {
        Object value = LauncherRecentsCompat.invokeCompat(iconView, methodName);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static void restoreTaskShadow(View taskView) {
        if (taskView == null) {
            return;
        }
        if (!LauncherRecentsState.ORIGINAL_TASK_ELEVATIONS.containsKey(taskView)) {
            return;
        }
        taskView.setElevation(readOriginalTaskElevation(taskView));
        taskView.setOutlineProvider(LauncherRecentsState.ORIGINAL_TASK_OUTLINE_PROVIDERS.get(
                taskView));
        taskView.invalidateOutline();
        LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.remove(taskView);
    }

    private static boolean isCurrentTaskStateModuleApplied(View taskView) {
        Float appliedTaskOffsetX = LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.get(taskView);
        Float appliedTaskOffsetY = LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.get(taskView);
        Float appliedHorizontalOffsetX =
                LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.get(taskView);
        Float appliedNonGridScale =
                LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.get(taskView);
        Float appliedBoxTranslationY =
                LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.get(taskView);
        Float appliedAttachAlpha =
                LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.get(taskView);
        Float appliedStableAlpha = LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.get(taskView);
        Float appliedTranslationZ =
                LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.get(taskView);
        Float appliedFullscreenProgress =
                LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.get(taskView);
        if (appliedTaskOffsetX == null
                || appliedTaskOffsetY == null
                || appliedHorizontalOffsetX == null
                || appliedNonGridScale == null
                || appliedBoxTranslationY == null
                || appliedAttachAlpha == null
                || appliedStableAlpha == null
                || appliedTranslationZ == null
                || appliedFullscreenProgress == null) {
            return false;
        }
        return approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                appliedTaskOffsetX)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                appliedTaskOffsetY)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f),
                appliedHorizontalOffsetX)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                appliedNonGridScale)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        readOriginalBoxTranslationY(taskView)),
                appliedBoxTranslationY)
                && approximatelyEqual(readAttachAlpha(taskView), appliedAttachAlpha)
                && approximatelyEqual(readStableAlpha(taskView), appliedStableAlpha)
                && approximatelyEqual(taskView.getTranslationZ(), appliedTranslationZ)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                appliedFullscreenProgress);
    }

    private static boolean approximatelyEqual(float a, float b) {
        return Math.abs(a - b) <= MODULE_APPLIED_EPSILON;
    }

    static void resetTaskTouchScale(View taskView) {
        if (taskView == null) {
            return;
        }
        Object animator = LauncherRecentsCompat.getFieldCompat(taskView, "mTaskThumbScaleAnimator");
        if (animator instanceof Animator) {
            Animator taskScaleAnimator = (Animator) animator;
            if (taskScaleAnimator.isStarted() || taskScaleAnimator.isRunning()) {
                taskScaleAnimator.cancel();
            }
        }
        Object scaleUpRunnable = LauncherRecentsCompat.getFieldCompat(taskView, "mScaleUpRunnable");
        if (scaleUpRunnable instanceof Runnable) {
            taskView.removeCallbacks((Runnable) scaleUpRunnable);
        }
        LauncherRecentsCompat.writeField(taskView, "mTaskThumbScaleAnimator", null);
        setNonGridScale(taskView, LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f));
    }
}
