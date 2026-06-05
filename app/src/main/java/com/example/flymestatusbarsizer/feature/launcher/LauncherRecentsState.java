package com.example.flymestatusbarsizer.feature.launcher;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.ArrayList;
import java.util.WeakHashMap;

final class LauncherRecentsState {
    private static final WeakHashMap<View, RecentsViewState> RECENTS_VIEW_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, ValueAnimator> ACTIVE_HOME_EXIT_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, ValueAnimator> ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> BLANK_TAP_HOME_EXIT_PROGRESS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> ACTIVE_BLANK_TAP_HOME_EXITS =
            new WeakHashMap<>();
    static final WeakHashMap<View, BlankTapHomeExitTaskState> BLANK_TAP_HOME_EXIT_TASK_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> GESTURE_STACK_RELEASE_PROGRESS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> FORCED_RECENTS_TRANSLATION_XS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> FORCED_RECENTS_TRANSLATION_YS =
            new WeakHashMap<>();
    static final WeakHashMap<View, GestureReleaseTaskState> GESTURE_STACK_RELEASE_TASK_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Integer> STACK_LAYOUT_RECOVERY_RADII =
            new WeakHashMap<>();
    static final WeakHashMap<View, StackLayoutApplyState> LAST_STACK_LAYOUT_APPLIES =
            new WeakHashMap<>();
    static final WeakHashMap<View, PendingStackLayoutApplyState> PENDING_STACK_LAYOUT_APPLIES =
            new WeakHashMap<>();
    static final WeakHashMap<View, ArrayList<Integer>> LAST_STACK_LAYOUT_ACTIVE_INDICES =
            new WeakHashMap<>();
    static final WeakHashMap<View, String> LAST_STACK_APP_FLOW_PACKAGES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Integer> LAST_STACK_TASK_LIST_VISIBILITY_CHANGES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> BYPASS_TASK_CLICK_INTERCEPTION =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_TASK_ELEVATIONS = new WeakHashMap<>();
    static final WeakHashMap<View, ViewOutlineProvider> ORIGINAL_TASK_OUTLINE_PROVIDERS =
            new WeakHashMap<>();
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
    static final WeakHashMap<View, Float> LAST_APPLIED_TASK_SHADOW_ELEVATIONS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_STACK_CONTENT_BLURS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_ACTIVITY_TITLE_ALPHAS =
            new WeakHashMap<>();
    static final WeakHashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState>
            LAST_APPLIED_STACK_TASK_VISUAL_STATES = new WeakHashMap<>();
    static final WeakHashMap<View, StackContentTargets> STACK_CONTENT_TARGETS =
            new WeakHashMap<>();
    static final WeakHashMap<View, StackIconBlurState> STACK_ICON_BLUR_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, ViewOutlineProvider> ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> ORIGINAL_STACK_ICON_CLIP_TO_OUTLINES =
            new WeakHashMap<>();
    static final ThreadLocal<TaskLaunchTransitionGeometryContext>
            ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY = new ThreadLocal<>();
    static final ThreadLocal<View> ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS =
            new ThreadLocal<>();

    private static volatile Handler mainHandler;

    static final class RecentsViewState {
        boolean appToRecentsEntrySessionActive;
        boolean appToRecentsStackLayoutDeferred;
        boolean appToRecentsGestureReleased;
        boolean gestureStackReleasedStable;
        boolean pendingGestureRecentsStackRelease;
        boolean pendingGestureRecentsStackReleaseHandoff;
        boolean overviewStateStackAnimationActive;
        boolean overviewPeekStockAnimationActive;
        boolean overviewStateStackBaselineCaptured;
        boolean taskLaunchRequestStarted;
        boolean swipeUpGestureActive;
        Float overviewStateStackStartAdjacentOffset;
        LaunchTransitionGeometryState activeTaskLaunchTransitionGeometry;
    }

    static final class LaunchTransitionGeometryState {
        final View targetTaskView;
        final int targetIndex;
        final Rect startBounds = new Rect();
        final ArrayList<TaskLaunchFrozenTaskState> frozenTaskStates = new ArrayList<>();
        boolean frozen;

        LaunchTransitionGeometryState(
                View targetTaskView,
                int targetIndex) {
            this.targetTaskView = targetTaskView;
            this.targetIndex = targetIndex;
        }
    }

    static final class TaskLaunchFrozenTaskState {
        final View taskView;
        final int visibility;
        final float x;
        final float y;
        final float pivotX;
        final float pivotY;
        final float scaleX;
        final float scaleY;
        final float alpha;
        final float translationZ;
        final LauncherRecentsTaskVisuals.StackTaskVisualState stackVisualState;

        TaskLaunchFrozenTaskState(
                View taskView,
                int visibility,
                float x,
                float y,
                float pivotX,
                float pivotY,
                float scaleX,
                float scaleY,
                float alpha,
                float translationZ,
                LauncherRecentsTaskVisuals.StackTaskVisualState stackVisualState) {
            this.taskView = taskView;
            this.visibility = visibility;
            this.x = x;
            this.y = y;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.alpha = alpha;
            this.translationZ = translationZ;
            this.stackVisualState = stackVisualState;
        }
    }

    static final class GestureReleaseTaskState {
        final float startVisibleOffset;
        final float targetVisibleOffset;
        final float startHorizontalOffsetX;

        GestureReleaseTaskState(
                float startVisibleOffset,
                float targetVisibleOffset,
                float startHorizontalOffsetX) {
            this.startVisibleOffset = startVisibleOffset;
            this.targetVisibleOffset = targetVisibleOffset;
            this.startHorizontalOffsetX = startHorizontalOffsetX;
        }
    }

    static final class BlankTapHomeExitTaskState {
        final float startRawOffset;
        final float startDismissTranslationX;
        final float startVisibleOffset;
        final float startScale;
        final float startTaskOffsetY;
        final float startBoxTranslationY;
        final float startAttachAlpha;
        float startStableAlpha;
        final float startActivityTitleAlpha;
        final float startStackContentBlurProgress;
        final float startTranslationZ;
        float centerVisibleOffset;

        BlankTapHomeExitTaskState(
                float startRawOffset,
                float startDismissTranslationX,
                float startVisibleOffset,
                float startScale,
                float startTaskOffsetY,
                float startBoxTranslationY,
                float startAttachAlpha,
                float startStableAlpha,
                float startActivityTitleAlpha,
                float startStackContentBlurProgress,
                float startTranslationZ) {
            this.startRawOffset = startRawOffset;
            this.startDismissTranslationX = startDismissTranslationX;
            this.startVisibleOffset = startVisibleOffset;
            this.startScale = startScale;
            this.startTaskOffsetY = startTaskOffsetY;
            this.startBoxTranslationY = startBoxTranslationY;
            this.startAttachAlpha = startAttachAlpha;
            this.startStableAlpha = startStableAlpha;
            this.startActivityTitleAlpha = startActivityTitleAlpha;
            this.startStackContentBlurProgress = startStackContentBlurProgress;
            this.startTranslationZ = startTranslationZ;
            this.centerVisibleOffset = startVisibleOffset;
        }
    }

    static final class StackLayoutApplyState {
        final long key;
        final long timeNs;
        final boolean syncedVisibleTaskData;

        StackLayoutApplyState(long key, long timeNs, boolean syncedVisibleTaskData) {
            this.key = key;
            this.timeNs = timeNs;
            this.syncedVisibleTaskData = syncedVisibleTaskData;
        }
    }

    static final class PendingStackLayoutApplyState {
        boolean captureStockState;
        boolean syncVisibleTaskData;
        boolean dynamicOnly;
        String source;

        PendingStackLayoutApplyState(
                boolean captureStockState,
                boolean syncVisibleTaskData,
                boolean dynamicOnly,
                String source) {
            this.captureStockState = captureStockState;
            this.syncVisibleTaskData = syncVisibleTaskData;
            this.dynamicOnly = dynamicOnly;
            this.source = source;
        }
    }

    static final class TaskLaunchTransitionGeometryContext {
        final View recentsView;
        final View taskView;

        TaskLaunchTransitionGeometryContext(View recentsView, View taskView) {
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

    static final class StackContentTargets {
        final Object containersObject;
        final View[] snapshotViews;
        final Object[] iconViews;
        final View[] iconAsViews;

        StackContentTargets(
                Object containersObject,
                View[] snapshotViews,
                Object[] iconViews,
                View[] iconAsViews) {
            this.containersObject = containersObject;
            this.snapshotViews = snapshotViews;
            this.iconViews = iconViews;
            this.iconAsViews = iconAsViews;
        }
    }

    static final class StackIconBlurState {
        final int iconWidth;
        final int iconHeight;
        final int viewWidth;
        final int viewHeight;
        final ViewOutlineProvider outlineProvider;

        StackIconBlurState(
                int iconWidth,
                int iconHeight,
                int viewWidth,
                int viewHeight,
                ViewOutlineProvider outlineProvider) {
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.viewWidth = viewWidth;
            this.viewHeight = viewHeight;
            this.outlineProvider = outlineProvider;
        }
    }

    private LauncherRecentsState() {
    }

    private static RecentsViewState recentsViewState(View recentsView, boolean create) {
        if (recentsView == null) {
            return null;
        }
        RecentsViewState state = RECENTS_VIEW_STATES.get(recentsView);
        if (state == null && create) {
            state = new RecentsViewState();
            RECENTS_VIEW_STATES.put(recentsView, state);
        }
        return state;
    }

    private static RecentsViewState ensureRecentsViewState(View recentsView) {
        return recentsViewState(recentsView, true);
    }

    private static RecentsViewState findRecentsViewState(View recentsView) {
        return recentsViewState(recentsView, false);
    }

    static void trackRecentsView(View recentsView) {
        ensureRecentsViewState(recentsView);
    }

    static ArrayList<View> snapshotTrackedRecentsViews() {
        return new ArrayList<>(RECENTS_VIEW_STATES.keySet());
    }

    static boolean consumeTaskClickBypass(View taskView) {
        Boolean value = BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        return value != null && value;
    }

    static boolean consumeTaskLaunchRequestStarted(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state == null || !state.taskLaunchRequestStarted) {
            return false;
        }
        state.taskLaunchRequestStarted = false;
        return true;
    }

    static void setTaskLaunchRequestStarted(View recentsView, boolean started) {
        RecentsViewState state = started
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.taskLaunchRequestStarted = started;
        }
    }

    static void setAppToRecentsEntrySessionActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.appToRecentsEntrySessionActive = active;
        }
    }

    static boolean isAppToRecentsEntrySessionActive(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.appToRecentsEntrySessionActive;
    }

    static void setAppToRecentsStackLayoutDeferred(View recentsView, boolean deferred) {
        RecentsViewState state = deferred
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.appToRecentsStackLayoutDeferred = deferred;
        }
    }

    static boolean isAppToRecentsStackLayoutDeferred(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.appToRecentsStackLayoutDeferred;
    }

    static void setAppToRecentsGestureReleased(View recentsView, boolean released) {
        RecentsViewState state = released
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.appToRecentsGestureReleased = released;
        }
    }

    static boolean isAppToRecentsGestureReleased(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.appToRecentsGestureReleased;
    }

    static void setGestureStackReleasedStable(View recentsView, boolean stable) {
        RecentsViewState state = stable
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.gestureStackReleasedStable = stable;
        }
    }

    static boolean isGestureStackReleasedStable(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.gestureStackReleasedStable;
    }

    static void setPendingGestureRecentsStackRelease(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.pendingGestureRecentsStackRelease = active;
        }
    }

    static boolean isPendingGestureRecentsStackRelease(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.pendingGestureRecentsStackRelease;
    }

    static void setPendingGestureRecentsStackReleaseHandoff(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.pendingGestureRecentsStackReleaseHandoff = active;
        }
    }

    static boolean isPendingGestureRecentsStackReleaseHandoff(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.pendingGestureRecentsStackReleaseHandoff;
    }

    static void setSwipeUpGestureActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.swipeUpGestureActive = active;
        }
    }

    static boolean isSwipeUpGestureActive(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.swipeUpGestureActive;
    }

    static void setOverviewStateStackAnimationActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state == null) {
            return;
        }
        state.overviewStateStackAnimationActive = active;
        if (!active) {
            state.overviewStateStackStartAdjacentOffset = null;
            state.overviewStateStackBaselineCaptured = false;
        }
    }

    static boolean isOverviewStateStackAnimationActive(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewStateStackAnimationActive;
    }

    static void setOverviewPeekStockAnimationActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.overviewPeekStockAnimationActive = active;
        }
    }

    static boolean isOverviewPeekStockAnimationActive(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewPeekStockAnimationActive;
    }

    static void setOverviewStateStackStartAdjacentOffset(View recentsView, float value) {
        RecentsViewState state = ensureRecentsViewState(recentsView);
        if (state != null) {
            state.overviewStateStackStartAdjacentOffset = value;
        }
    }

    static float readOverviewStateStackStartAdjacentOffset(View recentsView, float fallback) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewStateStackStartAdjacentOffset != null
                ? state.overviewStateStackStartAdjacentOffset
                : fallback;
    }

    static void setOverviewStateStackBaselineCaptured(View recentsView, boolean captured) {
        RecentsViewState state = captured
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.overviewStateStackBaselineCaptured = captured;
        }
    }

    static boolean isOverviewStateStackBaselineCaptured(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewStateStackBaselineCaptured;
    }

    static LaunchTransitionGeometryState getActiveTaskLaunchTransitionGeometry(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.activeTaskLaunchTransitionGeometry : null;
    }

    static void setActiveTaskLaunchTransitionGeometry(
            View recentsView,
            LaunchTransitionGeometryState state) {
        RecentsViewState recentsState = state != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (recentsState != null) {
            recentsState.activeTaskLaunchTransitionGeometry = state;
        }
    }

    static void clearActiveTaskLaunchTransitionGeometry(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state != null) {
            state.activeTaskLaunchTransitionGeometry = null;
        }
    }

    static boolean hasActiveTaskLaunchTransitionGeometry(View recentsView) {
        return getActiveTaskLaunchTransitionGeometry(recentsView) != null;
    }

    static boolean isTaskLaunchLayoutFrozen(View recentsView) {
        LaunchTransitionGeometryState state =
                getActiveTaskLaunchTransitionGeometry(recentsView);
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
