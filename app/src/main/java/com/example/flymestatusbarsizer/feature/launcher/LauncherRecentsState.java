package com.example.flymestatusbarsizer.feature.launcher;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

final class LauncherRecentsState {
    static final WeakHashMap<View, Boolean> TRACKED_RECENTS_VIEWS = new WeakHashMap<>();
    static final WeakHashMap<View, ValueAnimator> ACTIVE_HOME_EXIT_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, ValueAnimator> ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> BLANK_TAP_HOME_EXIT_PROGRESS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> PENDING_GESTURE_RECENTS_STACK_RELEASES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> PENDING_INITIAL_APP_TO_RECENTS_REORDERS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> ACTIVE_OVERVIEW_STATE_STACK_ANIMATIONS =
            new WeakHashMap<>();
    static final WeakHashMap<View, LaunchHandoffState> ACTIVE_TASK_LAUNCH_HANDOFFS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> BYPASS_TASK_CLICK_INTERCEPTION =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> TASK_LAUNCH_REQUEST_STARTED =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_XS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_YS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_HORIZONTAL_OFFSET_XS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_NON_GRID_SCALES = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_BOX_TRANSLATION_YS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_STABLE_ALPHAS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_TRANSLATION_ZS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_STOCK_FULLSCREEN_PROGRESSES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_TASK_OFFSET_XS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_TASK_OFFSET_YS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_HORIZONTAL_OFFSET_XS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_NON_GRID_SCALES = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_BOX_TRANSLATION_YS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_STABLE_ALPHAS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_TRANSLATION_ZS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_FULLSCREEN_PROGRESSES =
            new WeakHashMap<>();
    static final ThreadLocal<TaskLaunchSimulatorTranslationContext>
            ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION = new ThreadLocal<>();
    static final ThreadLocal<View> ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS =
            new ThreadLocal<>();

    private static volatile Handler mainHandler;

    static final class LaunchHandoffState {
        final View targetTaskView;
        final int targetIndex;
        final boolean promoteRearCard;
        final boolean handoffEnabled;
        float progress;
        boolean frozen;

        LaunchHandoffState(
                View targetTaskView,
                int targetIndex,
                boolean promoteRearCard,
                boolean handoffEnabled) {
            this.targetTaskView = targetTaskView;
            this.targetIndex = targetIndex;
            this.promoteRearCard = promoteRearCard;
            this.handoffEnabled = handoffEnabled;
        }
    }

    static final class TaskLaunchSimulatorTranslationContext {
        final View recentsView;
        final View taskView;

        TaskLaunchSimulatorTranslationContext(View recentsView, View taskView) {
            this.recentsView = recentsView;
            this.taskView = taskView;
        }
    }

    static final class TaskLaunchTaskRectTranslation {
        final int translationX;
        final int translationY;

        TaskLaunchTaskRectTranslation(int translationX, int translationY) {
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }

    private LauncherRecentsState() {
    }

    static void trackRecentsView(View recentsView) {
        if (recentsView == null) {
            return;
        }
        TRACKED_RECENTS_VIEWS.put(recentsView, Boolean.TRUE);
    }

    static ArrayList<View> snapshotTrackedRecentsViews() {
        return new ArrayList<>(TRACKED_RECENTS_VIEWS.keySet());
    }

    static boolean consumeTaskClickBypass(View taskView) {
        Boolean value = BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        return value != null && value;
    }

    static boolean consumeTaskLaunchRequestStarted(View recentsView) {
        Boolean value = TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        return value != null && value;
    }

    static void setPendingInitialAppToRecentsReorder(View recentsView, boolean pending) {
        if (recentsView == null) {
            return;
        }
        if (pending) {
            PENDING_INITIAL_APP_TO_RECENTS_REORDERS.put(recentsView, Boolean.TRUE);
        } else {
            PENDING_INITIAL_APP_TO_RECENTS_REORDERS.remove(recentsView);
        }
    }

    static boolean consumePendingInitialAppToRecentsReorder(View recentsView) {
        Boolean value = PENDING_INITIAL_APP_TO_RECENTS_REORDERS.remove(recentsView);
        return value != null && value;
    }

    static boolean hasPendingInitialAppToRecentsReorder(View recentsView) {
        Boolean value = recentsView != null
                ? PENDING_INITIAL_APP_TO_RECENTS_REORDERS.get(recentsView)
                : null;
        return value != null && value;
    }

    static boolean isTaskLaunchLayoutFrozen(View recentsView) {
        LaunchHandoffState state = ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        return state != null && state.frozen;
    }

    static Handler ensureMainHandler() {
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
