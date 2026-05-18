package com.example.flymestatusbarsizer.feature.launcher;

import android.animation.Animator;
import android.view.View;

final class LauncherRecentsTaskVisuals {
    private static final float MODULE_APPLIED_EPSILON = 0.01f;

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
        if (isCurrentTaskStateModuleApplied(taskView)) {
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

    static void setStableAlpha(View taskView, float value) {
        float clampedValue = LauncherRecentsLayoutEngine.clamp(value, 0f, 1f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setStableAlpha",
                LauncherRecentsCompat.FLOAT_ARG,
                clampedValue);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.put(taskView, clampedValue);
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

    static void clearAppliedTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TASK_OFFSET_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_HORIZONTAL_OFFSET_XS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_NON_GRID_SCALES.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_BOX_TRANSLATION_YS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_STABLE_ALPHAS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_TRANSLATION_ZS.remove(taskView);
        LauncherRecentsState.LAST_APPLIED_FULLSCREEN_PROGRESSES.remove(taskView);
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
