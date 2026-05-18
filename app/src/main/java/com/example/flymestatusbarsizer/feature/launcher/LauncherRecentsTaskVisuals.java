package com.example.flymestatusbarsizer.feature.launcher;

import android.animation.Animator;
import android.view.View;

final class LauncherRecentsTaskVisuals {
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

    static void captureStockTaskState(View taskView) {
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
    }

    static void setTaskOffsetTranslationX(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    static void setTaskOffsetTranslationY(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setTaskOffsetTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    static void setNonGridScale(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setNonGridScale",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    static void setBoxTranslationY(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setBoxTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    static void setStableAlpha(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setStableAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                LauncherRecentsLayoutEngine.clamp(value, 0f, 1f));
    }

    static void setFullscreenProgress(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setFullscreenProgress",
                LauncherRecentsCompat.FLOAT_ARG,
                LauncherRecentsLayoutEngine.clamp(value, 0f, 1f));
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

    static float readLastStockStableAlpha(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_STABLE_ALPHAS.get(taskView);
        return value != null ? value : 1f;
    }

    static float readLastStockTranslationZ(View taskView) {
        Float value = LauncherRecentsState.LAST_STOCK_TRANSLATION_ZS.get(taskView);
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
