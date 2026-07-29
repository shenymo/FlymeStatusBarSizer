package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import java.util.List;

final class LauncherRecentsTaskVisuals {
    private static final float MODULE_APPLIED_EPSILON = 0.01f;
    private static final int STACK_CONTENT_MAX_BLUR_DP = 18;
    private static final String ACTIVITY_TITLE_FIELD = "mActivityTitle";
    private static final String TASK_HEAD_FIELD = "mTaskHead";
    private LauncherRecentsTaskVisuals() {
    }

    static final class StackTaskVisualState {
        final float pivotX;
        final float pivotY;
        final float horizontalOffsetX;
        final float taskOffsetX;
        final float taskOffsetY;
        final float boxTranslationY;
        final float scale;
        final float attachAlpha;
        final float stableAlpha;
        final float activityTitleAlpha;
        final float blurProgress;
        final float fullscreenProgress;
        final float translationZ;
        final int shadowElevationDp;
        final boolean stackContentBlurEnabled;
        final boolean shadowEnabled;

        StackTaskVisualState(
                float pivotX,
                float pivotY,
                float horizontalOffsetX,
                float taskOffsetX,
                float taskOffsetY,
                float boxTranslationY,
                float scale,
                float attachAlpha,
                float stableAlpha,
                float activityTitleAlpha,
                float blurProgress,
                float fullscreenProgress,
                float translationZ,
                int shadowElevationDp,
                boolean stackContentBlurEnabled,
                boolean shadowEnabled) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.horizontalOffsetX = horizontalOffsetX;
            this.taskOffsetX = taskOffsetX;
            this.taskOffsetY = taskOffsetY;
            this.boxTranslationY = boxTranslationY;
            this.scale = scale;
            this.attachAlpha = attachAlpha;
            this.stableAlpha = stableAlpha;
            this.activityTitleAlpha = activityTitleAlpha;
            this.blurProgress = LauncherRecentsLayoutEngine.clamp(blurProgress, 0f, 1f);
            this.fullscreenProgress = fullscreenProgress;
            this.translationZ = translationZ;
            this.shadowElevationDp = shadowElevationDp;
            this.stackContentBlurEnabled = stackContentBlurEnabled;
            this.shadowEnabled = shadowEnabled;
        }

        boolean approximatelyEquals(StackTaskVisualState other) {
            return other != null
                    && approximatelyEqual(pivotX, other.pivotX)
                    && approximatelyEqual(pivotY, other.pivotY)
                    && approximatelyEqual(horizontalOffsetX, other.horizontalOffsetX)
                    && approximatelyEqual(taskOffsetX, other.taskOffsetX)
                    && approximatelyEqual(taskOffsetY, other.taskOffsetY)
                    && approximatelyEqual(boxTranslationY, other.boxTranslationY)
                    && approximatelyEqual(scale, other.scale)
                    && approximatelyEqual(attachAlpha, other.attachAlpha)
                    && approximatelyEqual(stableAlpha, other.stableAlpha)
                    && approximatelyEqual(activityTitleAlpha, other.activityTitleAlpha)
                    && approximatelyEqual(blurProgress, other.blurProgress)
                    && approximatelyEqual(fullscreenProgress, other.fullscreenProgress)
                    && approximatelyEqual(translationZ, other.translationZ)
                    && shadowElevationDp == other.shadowElevationDp
                    && stackContentBlurEnabled == other.stackContentBlurEnabled
                    && shadowEnabled == other.shadowEnabled;
        }

        StackTaskVisualState lerpTo(StackTaskVisualState target, float progress) {
            if (target == null) {
                return this;
            }
            float clampedProgress = LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f);
            return new StackTaskVisualState(
                    target.pivotX,
                    target.pivotY,
                    LauncherRecentsLayoutEngine.lerp(horizontalOffsetX, target.horizontalOffsetX, progress),
                    LauncherRecentsLayoutEngine.lerp(taskOffsetX, target.taskOffsetX, progress),
                    LauncherRecentsLayoutEngine.lerp(taskOffsetY, target.taskOffsetY, progress),
                    LauncherRecentsLayoutEngine.lerp(boxTranslationY, target.boxTranslationY, progress),
                    LauncherRecentsLayoutEngine.lerp(scale, target.scale, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(attachAlpha, target.attachAlpha, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(stableAlpha, target.stableAlpha, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(activityTitleAlpha, target.activityTitleAlpha, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(blurProgress, target.blurProgress, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(fullscreenProgress, target.fullscreenProgress, clampedProgress),
                    LauncherRecentsLayoutEngine.lerp(translationZ, target.translationZ, clampedProgress),
                    target.shadowElevationDp,
                    target.stackContentBlurEnabled,
                    target.shadowEnabled);
        }

        StackTaskVisualState withActivityTitleAlpha(float value) {
            return new StackTaskVisualState(
                    pivotX,
                    pivotY,
                    horizontalOffsetX,
                    taskOffsetX,
                    taskOffsetY,
                    boxTranslationY,
                    scale,
                    attachAlpha,
                    stableAlpha,
                    value,
                    blurProgress,
                    fullscreenProgress,
                    translationZ,
                    shadowElevationDp,
                    stackContentBlurEnabled,
                    shadowEnabled);
        }
    }

    static void applyStackTaskVisualState(View taskView, StackTaskVisualState state) {
        if (taskView == null || state == null) {
            return;
        }
        StackTaskVisualState lastState =
                LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.get(taskView);
        if (lastState != null
                && lastState.approximatelyEquals(state)
                && isCurrentStackTaskVisualStateApplied(taskView, state)) {
            return;
        }
        if (!approximatelyEqual(taskView.getPivotX(), state.pivotX)) {
            taskView.setPivotX(state.pivotX);
        }
        if (!approximatelyEqual(taskView.getPivotY(), state.pivotY)) {
            taskView.setPivotY(state.pivotY);
        }
        setHorizontalOffsetTranslationX(taskView, state.horizontalOffsetX);
        setTaskOffsetTranslationX(taskView, state.taskOffsetX);
        setTaskOffsetTranslationY(taskView, state.taskOffsetY);
        setBoxTranslationY(taskView, state.boxTranslationY);
        setNonGridScale(taskView, state.scale);
        setAttachAlpha(taskView, state.attachAlpha);
        setStableAlpha(taskView, state.stableAlpha);
        setTaskHeadContentAlpha(taskView, 1f);
        setActivityTitleAlpha(taskView, state.activityTitleAlpha);
        if (state.stackContentBlurEnabled) {
            setStackContentBlurProgress(taskView, state.blurProgress);
        } else {
            clearStackContentBlurIfApplied(taskView);
        }
        setFullscreenProgress(taskView, state.fullscreenProgress);
        if (state.shadowEnabled) {
            applyStackShadow(taskView, state.shadowElevationDp);
        } else {
            restoreStackShadow(taskView);
        }
        setTranslationZ(taskView, state.translationZ);
        LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.put(taskView, state);
    }

    static void applyStackTaskCoreVisualState(View taskView, StackTaskVisualState state) {
        if (taskView == null || state == null) {
            return;
        }
        if (!approximatelyEqual(taskView.getPivotX(), state.pivotX)) {
            taskView.setPivotX(state.pivotX);
        }
        if (!approximatelyEqual(taskView.getPivotY(), state.pivotY)) {
            taskView.setPivotY(state.pivotY);
        }
        setHorizontalOffsetTranslationX(taskView, state.horizontalOffsetX);
        setTaskOffsetTranslationX(taskView, state.taskOffsetX);
        setTaskOffsetTranslationY(taskView, state.taskOffsetY);
        setBoxTranslationY(taskView, state.boxTranslationY);
        setNonGridScale(taskView, state.scale);
        setAttachAlpha(taskView, state.attachAlpha);
        setStableAlpha(taskView, state.stableAlpha);
        setTranslationZ(taskView, state.translationZ);
        LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.put(taskView, state);
    }

    static void applyStackTaskEntryVisualState(View taskView, StackTaskVisualState state) {
        applyStackTaskCoreVisualState(taskView, state);
        if (taskView == null || state == null) {
            return;
        }
        if (state.stackContentBlurEnabled) {
            setStackContentBlurProgress(taskView, state.blurProgress);
        } else {
            clearStackContentBlurIfApplied(taskView);
        }
        LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.put(taskView, state);
    }

    static void applyStackTaskEntryAnimationFrame(
            View taskView,
            StackTaskVisualState start,
            StackTaskVisualState target,
            float progress) {
        if (taskView == null || start == null || target == null) {
            return;
        }
        float value = LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f);
        if (!approximatelyEqual(taskView.getPivotX(), target.pivotX)) {
            taskView.setPivotX(target.pivotX);
        }
        if (!approximatelyEqual(taskView.getPivotY(), target.pivotY)) {
            taskView.setPivotY(target.pivotY);
        }
        setHorizontalOffsetTranslationX(taskView, LauncherRecentsLayoutEngine.lerp(
                start.horizontalOffsetX, target.horizontalOffsetX, value));
        setTaskOffsetTranslationX(taskView, LauncherRecentsLayoutEngine.lerp(
                start.taskOffsetX, target.taskOffsetX, value));
        setTaskOffsetTranslationY(taskView, LauncherRecentsLayoutEngine.lerp(
                start.taskOffsetY, target.taskOffsetY, value));
        setBoxTranslationY(taskView, LauncherRecentsLayoutEngine.lerp(
                start.boxTranslationY, target.boxTranslationY, value));
        setNonGridScale(taskView, LauncherRecentsLayoutEngine.lerp(
                start.scale, target.scale, value));
        setAttachAlpha(taskView, LauncherRecentsLayoutEngine.lerp(
                start.attachAlpha, target.attachAlpha, value));
        setStableAlpha(taskView, LauncherRecentsLayoutEngine.lerp(
                start.stableAlpha, target.stableAlpha, value));
        setTaskHeadContentAlpha(taskView, 1f);
        setActivityTitleAlpha(taskView, target.activityTitleAlpha);
        setTranslationZ(taskView, LauncherRecentsLayoutEngine.lerp(
                start.translationZ, target.translationZ, value));
        if (target.stackContentBlurEnabled) {
            setStackContentBlurProgress(taskView, LauncherRecentsLayoutEngine.lerp(
                    start.blurProgress, target.blurProgress, value));
        } else {
            clearStackContentBlurIfApplied(taskView);
        }
        if (value >= 1f) {
            LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.put(taskView, target);
        }
    }

    static void applyStackTaskExitAnimationFrame(
            View taskView,
            float horizontalOffsetX,
            float taskOffsetX,
            float taskOffsetY,
            float scale,
            float attachAlpha,
            float stableAlpha,
            float activityTitleAlpha) {
        if (taskView == null) {
            return;
        }
        setHorizontalOffsetTranslationX(taskView, horizontalOffsetX);
        setTaskOffsetTranslationX(taskView, taskOffsetX);
        setTaskOffsetTranslationY(taskView, taskOffsetY);
        setNonGridScale(taskView, scale);
        setAttachAlpha(taskView, attachAlpha);
        setStableAlpha(taskView, stableAlpha);
        setTaskHeadContentAlpha(taskView, 1f);
        setActivityTitleAlpha(taskView, activityTitleAlpha);
    }

    private static boolean isCurrentStackTaskVisualStateApplied(
            View taskView,
            StackTaskVisualState state) {
        return approximatelyEqual(taskView.getPivotX(), state.pivotX)
                && approximatelyEqual(taskView.getPivotY(), state.pivotY)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f),
                state.horizontalOffsetX)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                state.taskOffsetX)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                state.taskOffsetY)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        readOriginalBoxTranslationY(taskView)),
                state.boxTranslationY)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                state.scale)
                && isTaskViewScaleApplied(taskView, state.scale)
                && approximatelyEqual(readAttachAlpha(taskView), state.attachAlpha)
                && approximatelyEqual(readStableAlpha(taskView), state.stableAlpha)
                && isTaskHeadAlphaApplied(taskView, 1f)
                && approximatelyEqual(
                readActivityTitleAlpha(taskView),
                state.activityTitleAlpha)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                state.fullscreenProgress)
                && (!state.stackContentBlurEnabled
                || approximatelyEqual(readStackContentBlurProgress(taskView), state.blurProgress))
                && approximatelyEqual(taskView.getTranslationZ(), state.translationZ)
                && (!state.shadowEnabled
                || isStackShadowApplied(taskView, state.shadowElevationDp));
    }

    private static boolean isStackShadowApplied(View taskView, int elevationDp) {
        LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
        if (targets == null || targets.snapshotViews.length == 0) {
            return false;
        }
        float elevation = FlymeStatusBarSizer.dp(
                taskView.getContext(), elevationDp);
        boolean foundSnapshot = false;
        for (View snapshotView : targets.snapshotViews) {
            if (snapshotView == null) {
                continue;
            }
            foundSnapshot = true;
            if (!approximatelyEqual(snapshotView.getElevation(), elevation)) {
                return false;
            }
        }
        return foundSnapshot;
    }

    private static void markStackTaskVisualStateDirty(View taskView) {
        if (taskView != null) {
            LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.remove(taskView);
        }
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
        if (recentsView != null) {
            LauncherRecentsState.LAST_STACK_STOCK_CAPTURE_TASK_COUNTS.put(
                    recentsView,
                    taskViewCount);
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
        if (recentsView != null) {
            LauncherRecentsState.LAST_STACK_STOCK_CAPTURE_TASK_COUNTS.put(
                    recentsView,
                    taskViewCount);
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
                readStockFloatWithoutApplied(
                        taskView,
                        "taskOffsetTranslationX",
                        0f,
                        LauncherRecentsState.LAST_STOCK_TASK_OFFSET_XS,
                        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS));
        LauncherRecentsState.LAST_STOCK_TASK_OFFSET_YS.put(
                taskView,
                readStockFloatWithoutApplied(
                        taskView,
                        "taskOffsetTranslationY",
                        0f,
                        LauncherRecentsState.LAST_STOCK_TASK_OFFSET_YS,
                        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS));
        LauncherRecentsState.LAST_STOCK_HORIZONTAL_OFFSET_XS.put(
                taskView,
                readStockFloatWithoutApplied(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f,
                        LauncherRecentsState.LAST_STOCK_HORIZONTAL_OFFSET_XS,
                        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS));
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

    private static float readStockFloatWithoutApplied(
            View taskView,
            String fieldName,
            float fallback,
            java.util.WeakHashMap<View, Float> stockValues,
            java.util.WeakHashMap<View, Float> appliedValues) {
        float currentValue = LauncherRecentsCompat.readFloatField(taskView, fieldName, fallback);
        Float appliedValue = appliedValues.get(taskView);
        if (appliedValue != null && approximatelyEqual(currentValue, appliedValue)) {
            Float stockValue = stockValues.get(taskView);
            return stockValue != null ? stockValue : fallback;
        }
        return currentValue;
    }

    static void setHorizontalOffsetTranslationX(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, value)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f),
                value) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setHorizontalOffsetTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.put(taskView, value);
    }

    static void setTaskOffsetTranslationX(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedValue = LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, value)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                value) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.put(taskView, value);
    }

    static void setTaskOffsetTranslationY(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedValue = LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, value)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                value) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.put(taskView, value);
    }

    static void setNonGridScale(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedValue = LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, value)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                value) == 0
                && isTaskViewScaleApplied(taskView, value)) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setNonGridScale",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.put(taskView, value);
    }

    static void setBoxTranslationY(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, value)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        readOriginalBoxTranslationY(taskView)),
                value) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setBoxTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
        LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.put(taskView, value);
    }

    static void setAttachAlpha(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue = LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && Float.compare(readAttachAlpha(taskView), clampedValue) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setAttachAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.put(taskView, clampedValue);
    }

    static void setStableAlpha(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue = LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && Float.compare(readStableAlpha(taskView), clampedValue) == 0) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setStableAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.put(taskView, clampedValue);
    }

    static void setActivityTitleAlpha(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && Float.compare(readActivityTitleAlpha(taskView), clampedValue) == 0) {
            return;
        }
        View titleView = resolveActivityTitleView(taskView);
        if (titleView != null) {
            titleView.setAlpha(clampedValue);
            LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.put(taskView, clampedValue);
        }
    }

    static void forceTaskHeadVisible(View taskView) {
        setTaskHeadContentAlpha(taskView, 1f);
        setActivityTitleAlpha(taskView, 1f);
    }

    static void setTaskHeadContentAlpha(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_TASK_HEAD_ALPHAS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && isTaskHeadAlphaApplied(taskView, clampedValue)) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setIconVisibleForGesture",
                LauncherRecentsCompat.BOOLEAN_ARG,
                clampedValue > MODULE_APPLIED_EPSILON);
        View taskHead = resolveTaskHeadView(taskView);
        if (taskHead != null) {
            taskHead.setAlpha(clampedValue);
        }
        View titleView = resolveActivityTitleView(taskView);
        if (titleView != null) {
            titleView.setAlpha(clampedValue);
        }
        LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
        if (targets != null) {
            for (int i = 0; i < targets.iconViews.length; i++) {
                Object iconView = targets.iconViews[i];
                LauncherRecentsCompat.invokeCompat(
                        iconView,
                        "setContentAlpha",
                        LauncherRecentsCompat.FLOAT_ARG,
                        clampedValue);
                LauncherRecentsCompat.invokeCompat(
                        iconView,
                        "setModalAlpha",
                        LauncherRecentsCompat.FLOAT_ARG,
                        clampedValue);
                LauncherRecentsCompat.invokeCompat(
                        iconView,
                        "setFlexSplitAlpha",
                        LauncherRecentsCompat.FLOAT_ARG,
                        clampedValue);
                View iconAsView = targets.iconAsViews[i];
                if (iconAsView != null) {
                    iconAsView.setAlpha(clampedValue);
                }
            }
        }
        LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.put(taskView, clampedValue);
        LauncherRecentsState.LAST_APPLIED_TASK_HEAD_ALPHAS.put(taskView, clampedValue);
    }

    static void forceRecentsTaskHeadsVisible(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setTaskIconVisible",
                LauncherRecentsCompat.BOOLEAN_ARG,
                true);
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            forceTaskHeadVisible(LauncherRecentsCompat.getTaskViewAt(recentsView, i));
        }
    }

    static void forceRecentsTaskAlphaVisible(View recentsView) {
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            setAttachAlpha(taskView, 1f);
            setStableAlpha(taskView, 1f);
            setTaskHeadContentAlpha(taskView, 1f);
        }
    }

    static void setStackContentBlurProgress(View taskView, float blurProgress) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return;
        }
        float clampedProgress = LauncherRecentsLayoutEngine.clamp(blurProgress, 0f, 1f);
        LauncherRecentsPerf.measure("blur:applyProgress", taskView, () -> {
            markStackTaskVisualStateDirty(taskView);
            float blurPx = FlymeStatusBarSizer.dp(
                    taskView.getContext(),
                    stackContentMaxBlurDp(taskView)) * clampedProgress;
            LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
            if (targets == null) {
                return;
            }
            for (int i = 0; i < targets.snapshotViews.length; i++) {
                applyStackContentBlur(targets.snapshotViews[i], blurPx);
                applyStackIconBlur(
                        targets.iconViews[i],
                        targets.iconAsViews[i],
                        blurPx);
            }
        });
    }

    static float readStackContentBlurProgress(View taskView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return 0f;
        }
        float maxBlurPx = FlymeStatusBarSizer.dp(taskView.getContext(), stackContentMaxBlurDp(taskView));
        if (maxBlurPx <= MODULE_APPLIED_EPSILON) {
            return 0f;
        }
        LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
        if (targets == null) {
            return 0f;
        }
        float blurPx = 0f;
        for (int i = 0; i < targets.snapshotViews.length; i++) {
            blurPx = Math.max(blurPx, readAppliedStackContentBlurPx(targets.snapshotViews[i]));
            blurPx = Math.max(blurPx, readAppliedStackContentBlurPx(targets.iconAsViews[i]));
        }
        return LauncherRecentsLayoutEngine.clamp(blurPx / maxBlurPx, 0f, 1f);
    }

    static void clearStackContentBlurIfApplied(View taskView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return;
        }
        LauncherRecentsPerf.measure("blur:clearProgress", taskView, () -> {
            LauncherRecentsState.StackContentTargets targets =
                    LauncherRecentsState.STACK_CONTENT_TARGETS.get(taskView);
            if (targets == null) {
                return;
            }
            for (int i = 0; i < targets.snapshotViews.length; i++) {
                clearStackContentTargetBlurIfApplied(targets.snapshotViews[i]);
                clearStackIconBlurIfApplied(targets.iconAsViews[i]);
            }
            LauncherRecentsState.STACK_CONTENT_TARGETS.remove(taskView);
        });
    }

    static void clearRecentsStackContentBlur(View recentsView) {
        if (recentsView == null) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            clearStackContentBlurIfApplied(LauncherRecentsCompat.getTaskViewAt(recentsView, i));
        }
    }

    static boolean hasAppliedTaskScaleMismatch(View recentsView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            Float appliedScale = LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.get(taskView);
            if (appliedScale != null && !isTaskViewScaleApplied(taskView, appliedScale)) {
                return true;
            }
        }
        return false;
    }

    static void setFullscreenProgress(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && Float.compare(
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                clampedValue) == 0) {
            return;
        }
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
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedTranslationZ =
                LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.get(taskView);
        if (lastAppliedTranslationZ != null
                && approximatelyEqual(lastAppliedTranslationZ, value)
                && approximatelyEqual(taskView.getTranslationZ(), value)) {
            return;
        }
        taskView.setTranslationZ(value);
        LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.put(taskView, value);
    }

    static void applyStackShadow(View taskView, int elevationDp) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
        if (targets == null) {
            return;
        }
        float elevation = FlymeStatusBarSizer.dp(
                taskView.getContext(), elevationDp);
        for (View snapshotView : targets.snapshotViews) {
            if (snapshotView == null) {
                continue;
            }
            if (!LauncherRecentsState.ORIGINAL_STACK_SHADOW_ELEVATIONS.containsKey(snapshotView)) {
                LauncherRecentsState.ORIGINAL_STACK_SHADOW_ELEVATIONS.put(
                        snapshotView, snapshotView.getElevation());
            }
            if (!approximatelyEqual(snapshotView.getElevation(), elevation)) {
                snapshotView.setElevation(elevation);
                snapshotView.invalidateOutline();
            }
        }
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

    private static View resolveTaskHeadView(View taskView) {
        Object value = LauncherRecentsCompat.getFieldCompat(taskView, TASK_HEAD_FIELD);
        return value instanceof View ? (View) value : null;
    }

    private static boolean isTaskHeadContentAlphaApplied(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        return approximatelyEqual(readActivityTitleAlpha(taskView), clampedValue)
                && isTaskHeadAlphaApplied(taskView, clampedValue);
    }

    private static boolean isTaskHeadAlphaApplied(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        if (!approximatelyEqual(readTaskHeadAlpha(taskView), clampedValue)) {
            return false;
        }
        LauncherRecentsState.StackContentTargets targets =
                LauncherRecentsState.STACK_CONTENT_TARGETS.get(taskView);
        if (targets == null) {
            return true;
        }
        for (View iconAsView : targets.iconAsViews) {
            if (iconAsView != null && !approximatelyEqual(iconAsView.getAlpha(), clampedValue)) {
                return false;
            }
        }
        return true;
    }

    private static float readTaskHeadAlpha(View taskView) {
        View taskHead = resolveTaskHeadView(taskView);
        return taskHead != null ? taskHead.getAlpha() : 1f;
    }

    static float readActivityTitleAlpha(View taskView) {
        View titleView = resolveActivityTitleView(taskView);
        return titleView != null ? titleView.getAlpha() : 1f;
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
        LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_ATTACH_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_HEAD_ALPHAS.remove(taskView);
        LauncherRecentsState.STACK_CONTENT_TARGETS.remove(taskView);
    }

    private static LauncherRecentsState.StackContentTargets resolveStackContentTargets(
            View taskView) {
        LauncherRecentsState.StackContentTargets cachedTargets =
                LauncherRecentsState.STACK_CONTENT_TARGETS.get(taskView);
        Object containersObject = LauncherRecentsCompat.invokeCompat(taskView, "getTaskContainers");
        if (!(containersObject instanceof List)) {
            LauncherRecentsState.STACK_CONTENT_TARGETS.remove(taskView);
            return null;
        }
        List<?> taskContainers = (List<?>) containersObject;
        if (cachedTargets != null
                && cachedTargets.containersObject == containersObject
                && cachedTargets.snapshotViews.length == taskContainers.size()) {
            return cachedTargets;
        }
        View[] snapshotViews = new View[taskContainers.size()];
        Object[] iconViews = new Object[taskContainers.size()];
        View[] iconAsViews = new View[taskContainers.size()];
        for (int i = 0; i < taskContainers.size(); i++) {
            Object taskContainer = taskContainers.get(i);
            Object snapshotView = LauncherRecentsCompat.invokeCompat(
                    taskContainer,
                    "getSnapshotView");
            snapshotViews[i] = snapshotView instanceof View ? (View) snapshotView : null;
            Object iconView = LauncherRecentsCompat.invokeCompat(taskContainer, "getIconView");
            iconViews[i] = iconView;
            Object iconAsView = LauncherRecentsCompat.invokeCompat(iconView, "asView");
            iconAsViews[i] = iconAsView instanceof View ? (View) iconAsView : null;
        }
        LauncherRecentsState.StackContentTargets targets =
                new LauncherRecentsState.StackContentTargets(
                        containersObject,
                        snapshotViews,
                        iconViews,
                        iconAsViews);
        LauncherRecentsState.STACK_CONTENT_TARGETS.put(taskView, targets);
        return targets;
    }

    private static void applyStackContentBlur(View view, float blurPx) {
        if (view == null) {
            return;
        }
        if (LauncherRecentsCompat.invokeBoolean(view, "getSecretive", false)) {
            LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.remove(view);
            return;
        }
        float appliedBlurPx = Math.max(0f, blurPx);
        Float lastAppliedBlurPx = LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.get(view);
        if (lastAppliedBlurPx != null
                && Math.abs(lastAppliedBlurPx - appliedBlurPx) < MODULE_APPLIED_EPSILON) {
            return;
        }
        if (appliedBlurPx == 0f) {
            view.setRenderEffect(null);
        } else {
            view.setRenderEffect(RenderEffect.createBlurEffect(
                    appliedBlurPx, appliedBlurPx, Shader.TileMode.CLAMP));
        }
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.put(view, appliedBlurPx);
    }

    private static float readAppliedStackContentBlurPx(View view) {
        if (view == null) {
            return 0f;
        }
        Float value = LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.get(view);
        return value != null ? value : 0f;
    }

    private static void clearStackContentTargetBlurIfApplied(View view) {
        if (view == null || readAppliedStackContentBlurPx(view) <= MODULE_APPLIED_EPSILON) {
            return;
        }
        applyStackContentBlur(view, 0f);
    }

    private static void clearStackIconBlurIfApplied(View view) {
        if (view == null) {
            return;
        }
        LauncherRecentsState.STACK_ICON_BLUR_STATES.remove(view);
        if (readAppliedStackContentBlurPx(view) > MODULE_APPLIED_EPSILON) {
            applyStackContentBlur(view, 0f);
        }
    }

    private static void applyStackIconBlur(Object iconView, View view, float blurPx) {
        if (view == null) {
            return;
        }
        float appliedBlurPx = blurPx > MODULE_APPLIED_EPSILON ? blurPx : 0f;
        if (appliedBlurPx == 0f) {
            LauncherRecentsState.STACK_ICON_BLUR_STATES.remove(view);
            applyStackContentBlur(view, 0f);
            return;
        }
        int iconWidth = readIconSize(iconView, "getDrawableWidth", view.getWidth());
        int iconHeight = readIconSize(iconView, "getDrawableHeight", view.getHeight());
        Object drawableObject = LauncherRecentsCompat.invokeCompat(iconView, "getDrawable");
        LauncherRecentsState.StackIconBlurState state =
                LauncherRecentsState.STACK_ICON_BLUR_STATES.get(view);
        if (state == null
                || state.iconWidth != iconWidth
                || state.iconHeight != iconHeight
                || state.viewWidth != view.getWidth()
                || state.viewHeight != view.getHeight()
                || state.drawable != drawableObject) {
            RenderEffect maskEffect = createStackIconMaskEffect(
                    drawableObject instanceof Drawable ? (Drawable) drawableObject : null,
                    iconWidth,
                    iconHeight,
                    view.getWidth(),
                    view.getHeight());
            if (maskEffect == null) {
                LauncherRecentsState.STACK_ICON_BLUR_STATES.remove(view);
                applyStackContentBlur(view, 0f);
                return;
            }
            state = new LauncherRecentsState.StackIconBlurState(
                    iconWidth,
                    iconHeight,
                    view.getWidth(),
                    view.getHeight(),
                    drawableObject,
                    maskEffect);
            LauncherRecentsState.STACK_ICON_BLUR_STATES.put(view, state);
            LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.remove(view);
        }
        applyStackMaskedBlur(view, appliedBlurPx, (RenderEffect) state.maskEffect);
    }

    private static void applyStackMaskedBlur(
            View view,
            float blurPx,
            RenderEffect maskEffect) {
        Float lastAppliedBlurPx = LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.get(view);
        if (lastAppliedBlurPx != null
                && Math.abs(lastAppliedBlurPx - blurPx) < MODULE_APPLIED_EPSILON) {
            return;
        }
        RenderEffect blurEffect = RenderEffect.createBlurEffect(
                blurPx, blurPx, Shader.TileMode.CLAMP);
        view.setRenderEffect(RenderEffect.createBlendModeEffect(
                blurEffect, maskEffect, BlendMode.DST_IN));
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.put(view, blurPx);
    }

    private static RenderEffect createStackIconMaskEffect(
            Drawable drawable,
            int iconWidth,
            int iconHeight,
            int viewWidth,
            int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return null;
        }
        int width = iconWidth > 0 ? Math.min(iconWidth, viewWidth) : viewWidth;
        int height = iconHeight > 0 ? Math.min(iconHeight, viewHeight) : viewHeight;
        int left = Math.max(0, (viewWidth - width) / 2);
        int top = Math.max(0, (viewHeight - height) / 2);
        Bitmap maskBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(maskBitmap);
        if (drawable != null && !drawable.getBounds().isEmpty()) {
            drawable.draw(canvas);
        } else {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            float radius = Math.min(width, height) * 0.22f;
            canvas.drawRoundRect(
                    left,
                    top,
                    left + width,
                    top + height,
                    radius,
                    radius,
                    paint);
        }
        return RenderEffect.createShaderEffect(new BitmapShader(
                maskBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP));
    }

    private static int readIconSize(Object iconView, String methodName, int fallback) {
        Object value = LauncherRecentsCompat.invokeCompat(iconView, methodName);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static void restoreTaskShadow(View taskView) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        if (LauncherRecentsState.ORIGINAL_TASK_ELEVATIONS.containsKey(taskView)) {
            taskView.setElevation(readOriginalTaskElevation(taskView));
            taskView.setOutlineProvider(LauncherRecentsState.ORIGINAL_TASK_OUTLINE_PROVIDERS.get(
                    taskView));
            taskView.invalidateOutline();
        }
        restoreStackShadow(taskView);
    }

    private static void restoreStackShadow(View taskView) {
        LauncherRecentsState.StackContentTargets targets = resolveStackContentTargets(taskView);
        if (targets == null) {
            return;
        }
        for (View snapshotView : targets.snapshotViews) {
            Float elevation = snapshotView == null
                    ? null
                    : LauncherRecentsState.ORIGINAL_STACK_SHADOW_ELEVATIONS.remove(snapshotView);
            if (elevation != null) {
                snapshotView.setElevation(elevation);
                snapshotView.invalidateOutline();
            }
        }
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
                && isTaskViewScaleApplied(taskView, appliedNonGridScale)
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

    private static boolean shouldSkipAppliedFloat(Float lastAppliedValue, float value) {
        return lastAppliedValue != null
                && Float.compare(lastAppliedValue, value) == 0;
    }

    private static boolean isTaskViewScaleApplied(View taskView, float nonGridScale) {
        if (taskView == null) {
            return false;
        }
        float expectedScale = resolveExpectedTaskViewScale(taskView, nonGridScale);
        return approximatelyEqual(taskView.getScaleX(), expectedScale)
                && approximatelyEqual(taskView.getScaleY(), expectedScale);
    }

    private static float resolveExpectedTaskViewScale(View taskView, float nonGridScale) {
        float gridProgress = LauncherRecentsCompat.readFloatField(taskView, "gridProgress", 0f);
        float dismissScale = LauncherRecentsCompat.readFloatField(taskView, "dismissScale", 1f);
        float modalness = LauncherRecentsCompat.readFloatField(taskView, "modalness", 0f);
        float modalScale = LauncherRecentsCompat.readFloatField(taskView, "modalScale", 1f);
        float persistentScale = nonGridScale + ((1f - nonGridScale) * gridProgress);
        float modalMappedScale = 1f + ((modalScale - 1f) * modalness);
        return persistentScale * dismissScale * modalMappedScale;
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

    private static int stackContentMaxBlurDp(View view) {
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                LauncherRecentsLayoutEngine.stackConfig(view);
        return config == null ? STACK_CONTENT_MAX_BLUR_DP : config.stackContentMaxBlurDp;
    }

}
