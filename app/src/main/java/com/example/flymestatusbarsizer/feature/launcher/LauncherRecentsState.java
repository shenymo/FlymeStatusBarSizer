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
    static final WeakHashMap<View, ValueAnimator> ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> BLANK_TAP_HOME_EXIT_PROGRESS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> GESTURE_STACK_RELEASE_PROGRESS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> GESTURE_STACK_RELEASED_STABLE =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> PENDING_GESTURE_RECENTS_STACK_RELEASES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> APP_TO_RECENTS_GESTURE_RELEASED =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> DEFERRED_APP_TO_RECENTS_STACK_LAYOUTS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> ACTIVE_OVERVIEW_STATE_STACK_ANIMATIONS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> ACTIVE_APP_TO_RECENTS_ENTRY_SESSIONS =
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
    static final WeakHashMap<View, Float> LAST_STOCK_ATTACH_ALPHAS = new WeakHashMap<>();
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
    static final WeakHashMap<View, Float> LAST_APPLIED_ATTACH_ALPHAS = new WeakHashMap<>();
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

    static void setAppToRecentsEntrySessionActive(View recentsView, boolean active) {
        if (recentsView == null) {
            return;
        }
        if (active) {
            ACTIVE_APP_TO_RECENTS_ENTRY_SESSIONS.put(recentsView, Boolean.TRUE);
        } else {
            ACTIVE_APP_TO_RECENTS_ENTRY_SESSIONS.remove(recentsView);
        }
    }

    static boolean isAppToRecentsEntrySessionActive(View recentsView) {
        Boolean value = recentsView != null
                ? ACTIVE_APP_TO_RECENTS_ENTRY_SESSIONS.get(recentsView)
                : null;
        return value != null && value;
    }

    static void setAppToRecentsStackLayoutDeferred(View recentsView, boolean deferred) {
        if (recentsView == null) {
            return;
        }
        if (deferred) {
            DEFERRED_APP_TO_RECENTS_STACK_LAYOUTS.put(recentsView, Boolean.TRUE);
        } else {
            DEFERRED_APP_TO_RECENTS_STACK_LAYOUTS.remove(recentsView);
        }
    }

    static boolean isAppToRecentsStackLayoutDeferred(View recentsView) {
        Boolean value = recentsView != null
                ? DEFERRED_APP_TO_RECENTS_STACK_LAYOUTS.get(recentsView)
                : null;
        return value != null && value;
    }

    static void setAppToRecentsGestureReleased(View recentsView, boolean released) {
        if (recentsView == null) {
            return;
        }
        if (released) {
            APP_TO_RECENTS_GESTURE_RELEASED.put(recentsView, Boolean.TRUE);
        } else {
            APP_TO_RECENTS_GESTURE_RELEASED.remove(recentsView);
        }
    }

    static boolean isAppToRecentsGestureReleased(View recentsView) {
        Boolean value = recentsView != null
                ? APP_TO_RECENTS_GESTURE_RELEASED.get(recentsView)
                : null;
        return value != null && value;
    }

    static void setGestureStackReleasedStable(View recentsView, boolean stable) {
        if (recentsView == null) {
            return;
        }
        if (stable) {
            GESTURE_STACK_RELEASED_STABLE.put(recentsView, Boolean.TRUE);
        } else {
            GESTURE_STACK_RELEASED_STABLE.remove(recentsView);
        }
    }

    static boolean isGestureStackReleasedStable(View recentsView) {
        Boolean value = recentsView != null
                ? GESTURE_STACK_RELEASED_STABLE.get(recentsView)
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
