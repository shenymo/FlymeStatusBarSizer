package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.List;

final class LauncherRecentsTaskVisuals {
    private static final float MODULE_APPLIED_EPSILON = 0.01f;
    private static final int STACK_CONTENT_MAX_BLUR_DP = 18;
    private static final float STACK_CONTENT_BLUR_STEP_PX = 0.5f;
    private static final String ACTIVITY_TITLE_FIELD = "mActivityTitle";
    private static final String TASK_HEAD_FIELD = "mTaskHead";
    private static final ViewOutlineProvider STACK_TASK_NO_SHADOW_OUTLINE_PROVIDER =
            new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setEmpty();
                }
            };

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
        final boolean stackContentBlurEnabled;
        final boolean clearShadow;

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
                boolean stackContentBlurEnabled,
                boolean clearShadow) {
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
            this.blurProgress = blurProgress;
            this.fullscreenProgress = fullscreenProgress;
            this.translationZ = translationZ;
            this.stackContentBlurEnabled = stackContentBlurEnabled;
            this.clearShadow = clearShadow;
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
                    && stackContentBlurEnabled == other.stackContentBlurEnabled
                    && clearShadow == other.clearShadow;
        }

        StackTaskVisualState lerpTo(StackTaskVisualState target, float progress) {
            if (target == null) {
                return this;
            }
            return new StackTaskVisualState(
                    target.pivotX,
                    target.pivotY,
                    LauncherRecentsLayoutEngine.lerp(horizontalOffsetX, target.horizontalOffsetX, progress),
                    LauncherRecentsLayoutEngine.lerp(taskOffsetX, target.taskOffsetX, progress),
                    LauncherRecentsLayoutEngine.lerp(taskOffsetY, target.taskOffsetY, progress),
                    LauncherRecentsLayoutEngine.lerp(boxTranslationY, target.boxTranslationY, progress),
                    LauncherRecentsLayoutEngine.lerp(scale, target.scale, progress),
                    LauncherRecentsLayoutEngine.lerp(attachAlpha, target.attachAlpha, progress),
                    LauncherRecentsLayoutEngine.lerp(stableAlpha, target.stableAlpha, progress),
                    LauncherRecentsLayoutEngine.lerp(activityTitleAlpha, target.activityTitleAlpha, progress),
                    LauncherRecentsLayoutEngine.lerp(blurProgress, target.blurProgress, progress),
                    LauncherRecentsLayoutEngine.lerp(fullscreenProgress, target.fullscreenProgress, progress),
                    LauncherRecentsLayoutEngine.lerp(translationZ, target.translationZ, progress),
                    target.stackContentBlurEnabled,
                    target.clearShadow);
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
        setTaskHeadContentAlpha(taskView, state.activityTitleAlpha);
        if (state.stackContentBlurEnabled) {
            setStackContentBlurProgress(taskView, state.blurProgress);
        } else {
            clearStackContentBlurIfApplied(taskView);
        }
        setFullscreenProgress(taskView, state.fullscreenProgress);
        if (state.clearShadow) {
            clearStackShadow(taskView);
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
        LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.put(taskView, state);
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
                && isTaskHeadContentAlphaApplied(taskView, state.activityTitleAlpha)
                && approximatelyEqual(
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                state.fullscreenProgress)
                && (!state.stackContentBlurEnabled
                || approximatelyEqual(readStackContentBlurProgress(taskView), state.blurProgress))
                && approximatelyEqual(taskView.getTranslationZ(), state.translationZ)
                && (!state.clearShadow || isStackShadowCleared(taskView));
    }

    private static boolean isStackShadowCleared(View taskView) {
        return taskView != null
                && taskView.getOutlineProvider() == STACK_TASK_NO_SHADOW_OUTLINE_PROVIDER
                && approximatelyEqual(taskView.getElevation(), 0f);
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
    }

    static void setTaskHeadContentAlpha(View taskView, float value) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        Float lastAppliedValue =
                LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.get(taskView);
        if (shouldSkipAppliedFloat(lastAppliedValue, clampedValue)
                && isTaskHeadContentAlphaApplied(taskView, clampedValue)) {
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
            forceTaskHeadVisible(taskView);
        }
    }

    static void setStackContentBlurProgress(View taskView, float blurProgress) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        float blurPx = FlymeStatusBarSizer.dp(
                taskView.getContext(),
                STACK_CONTENT_MAX_BLUR_DP) * LauncherRecentsLayoutEngine.clamp(
                blurProgress,
                0f,
                1f);
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
    }

    static float readStackContentBlurProgress(View taskView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || taskView == null) {
            return 0f;
        }
        float maxBlurPx = FlymeStatusBarSizer.dp(taskView.getContext(), STACK_CONTENT_MAX_BLUR_DP);
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

    static void clearStackShadow(View taskView) {
        if (taskView == null) {
            return;
        }
        markStackTaskVisualStateDirty(taskView);
        Float lastAppliedElevation =
                LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.get(taskView);
        if (lastAppliedElevation != null
                && approximatelyEqual(lastAppliedElevation, 0f)
                && taskView.getOutlineProvider() == STACK_TASK_NO_SHADOW_OUTLINE_PROVIDER
                && approximatelyEqual(taskView.getElevation(), 0f)) {
            return;
        }
        rememberOriginalTaskState(taskView);
        taskView.setOutlineProvider(STACK_TASK_NO_SHADOW_OUTLINE_PROVIDER);
        taskView.setElevation(0f);
        taskView.invalidateOutline();
        LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.put(taskView, 0f);
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
        if (!approximatelyEqual(readActivityTitleAlpha(taskView), clampedValue)
                || !approximatelyEqual(readTaskHeadAlpha(taskView), clampedValue)) {
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
        LauncherRecentsState.LAST_APPLIED_TASK_SHADOW_ELEVATIONS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STACK_CONTENT_BLURS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.remove(taskView);
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
        float appliedBlurPx = quantizeStackContentBlurPx(blurPx);
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

    private static float quantizeStackContentBlurPx(float blurPx) {
        if (blurPx <= MODULE_APPLIED_EPSILON) {
            return 0f;
        }
        return Math.round(blurPx / STACK_CONTENT_BLUR_STEP_PX) * STACK_CONTENT_BLUR_STEP_PX;
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
        if (view == null || readAppliedStackContentBlurPx(view) <= MODULE_APPLIED_EPSILON) {
            return;
        }
        restoreStackIconClip(view);
        applyStackContentBlur(view, 0f);
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
        LauncherRecentsState.StackIconBlurState state =
                LauncherRecentsState.STACK_ICON_BLUR_STATES.get(view);
        if (state == null
                || state.iconWidth != iconWidth
                || state.iconHeight != iconHeight
                || state.viewWidth != view.getWidth()
                || state.viewHeight != view.getHeight()) {
            ViewOutlineProvider outlineProvider =
                    createStackIconBlurOutlineProvider(iconWidth, iconHeight);
            view.setOutlineProvider(outlineProvider);
            view.setClipToOutline(true);
            view.invalidateOutline();
            LauncherRecentsState.STACK_ICON_BLUR_STATES.put(
                    view,
                    new LauncherRecentsState.StackIconBlurState(
                            iconWidth,
                            iconHeight,
                            view.getWidth(),
                            view.getHeight(),
                            outlineProvider));
        } else if (view.getOutlineProvider() != state.outlineProvider || !view.getClipToOutline()) {
            view.setOutlineProvider(state.outlineProvider);
            view.setClipToOutline(true);
            view.invalidateOutline();
        }
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
        LauncherRecentsState.STACK_ICON_BLUR_STATES.remove(view);
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
        markStackTaskVisualStateDirty(taskView);
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
}
