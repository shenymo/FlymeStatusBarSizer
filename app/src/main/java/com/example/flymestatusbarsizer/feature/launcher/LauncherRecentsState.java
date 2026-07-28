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
    static final int POSITION_OWNER_NONE = 0;
    static final int POSITION_OWNER_SCROLL = 1;
    static final int POSITION_OWNER_ENTER = 2;
    static final int POSITION_OWNER_OVERVIEW = 3;
    static final int POSITION_OWNER_HOME_EXIT = 4;
    static final int POSITION_OWNER_DISMISS = 5;
    static final int POSITION_OWNER_TASK_LAUNCH = 6;
    private static final WeakHashMap<View, RecentsViewState> RECENTS_VIEW_STATES =
            new WeakHashMap<>();

    // App-to-recents entry animation.
    static final WeakHashMap<View, ValueAnimator> ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, ValueAnimator> ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS =
            new WeakHashMap<>();
    static final WeakHashMap<View, GestureReleaseTaskState> GESTURE_STACK_RELEASE_TASK_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, GestureReleaseTaskState> OVERVIEW_STATE_STACK_ENTRY_TASK_STATES =
            new WeakHashMap<>();

    // Home exit animation.
    static final WeakHashMap<View, BlankTapHomeExitTaskState> BLANK_TAP_HOME_EXIT_TASK_STATES =
            new WeakHashMap<>();

    // Stack layout cache.
    static final WeakHashMap<View, PrepareRecentsViewState> PREPARE_RECENTS_VIEW_STATES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Integer> LAST_STACK_STOCK_CAPTURE_TASK_COUNTS =
            new WeakHashMap<>();

    // Visible task data and click guards.
    static final WeakHashMap<View, String> LAST_STACK_APP_FLOW_PACKAGES =
            new WeakHashMap<>();
    static final WeakHashMap<View, Boolean> BYPASS_TASK_CLICK_INTERCEPTION =
            new WeakHashMap<>();

    // Task visual state.
    static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_TASK_ELEVATIONS = new WeakHashMap<>();
    static final WeakHashMap<View, ViewOutlineProvider> ORIGINAL_TASK_OUTLINE_PROVIDERS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> ORIGINAL_STACK_SHADOW_ELEVATIONS =
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
    static final WeakHashMap<View, Float> LAST_APPLIED_STACK_CONTENT_BLURS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_ACTIVITY_TITLE_ALPHAS =
            new WeakHashMap<>();
    static final WeakHashMap<View, Float> LAST_APPLIED_TASK_HEAD_ALPHAS = new WeakHashMap<>();
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

    // Task launch transition.
    static final ThreadLocal<TaskLaunchTransitionGeometryContext>
            ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY = new ThreadLocal<>();
    static final ThreadLocal<View> ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS =
            new ThreadLocal<>();

    private static volatile Handler mainHandler;

    static final class RecentsViewState {
        boolean appToRecentsEntrySessionActive;
        boolean appToRecentsGestureReleased;
        boolean appToRecentsStackSettled;
        boolean pendingGestureRecentsStackRelease;
        boolean pendingGestureRecentsStackReleaseHandoff;
        boolean overviewStateStackAnimationActive;
        boolean overviewPeekStockAnimationActive;
        boolean overviewStateStackBaselineCaptured;
        boolean overviewStateStackSettled;
        boolean overviewPreReleaseStockMode;
        boolean overviewStateStackReleaseRequested;
        boolean launcherQuickSwitchStockMode;
        boolean taskLaunchRequestStarted;
        boolean swipeUpGestureActive;
        boolean blankTapHomeExitActive;
        boolean stackLayoutFramePosted;
        int positionOwner;
        Float gestureStackReleaseProgress;
        Float forcedRecentsTranslationX;
        Float forcedRecentsTranslationY;
        Float blankTapHomeExitProgress;
        Integer stackLayoutRecoveryRadius;
        BlankTapHomeExitRecentsState blankTapHomeExitRecentsState;
        StackLayoutApplyState lastStackLayoutApply;
        PendingStackLayoutApplyState pendingStackLayoutApply;
        ArrayList<Integer> lastStackLayoutActiveIndices;
        Float overviewStateStackStartAdjacentOffset;
        LaunchTransitionGeometryState activeTaskLaunchTransitionGeometry;
    }

    static final class LaunchTransitionGeometryState {
        final View targetTaskView;
        final int targetIndex;
        final Rect startBounds = new Rect();
        final ArrayList<TaskLaunchFrozenTaskState> frozenTaskStates = new ArrayList<>();
        float siblingExitProgress;
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
        final boolean target;
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
                boolean target,
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
            this.target = target;
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
        final LauncherRecentsTaskVisuals.StackTaskVisualState startVisualState;
        final LauncherRecentsTaskVisuals.StackTaskVisualState targetVisualState;

        GestureReleaseTaskState(
                LauncherRecentsTaskVisuals.StackTaskVisualState startVisualState,
                LauncherRecentsTaskVisuals.StackTaskVisualState targetVisualState) {
            this.startVisualState = startVisualState;
            this.targetVisualState = targetVisualState;
        }
    }

    static final class BlankTapHomeExitTaskState {
        final float startRawOffset;
        final float startDismissTranslationX;
        final float startVisibleOffset;
        final float startHorizontalOffsetX;
        final float startTaskOffsetX;
        final float startTaskOffsetY;
        final float startScale;
        final float startBoxTranslationY;
        final float startAttachAlpha;
        float startStableAlpha;
        final float startActivityTitleAlpha;
        final float startStackContentBlurProgress;
        final float startFullscreenProgress;
        final float startTranslationZ;
        final float taskWidth;
        final float taskHeight;
        final float taskPrimarySize;
        final float taskCenteredPrimaryStartPx;
        final boolean primaryScrollHorizontal;
        float centerVisibleOffset;
        float exitVisibleOffset;

        BlankTapHomeExitTaskState(
                float startRawOffset,
                float startDismissTranslationX,
                float startVisibleOffset,
                float startHorizontalOffsetX,
                float startTaskOffsetX,
                float startTaskOffsetY,
                float startScale,
                float startBoxTranslationY,
                float startAttachAlpha,
                float startStableAlpha,
                float startActivityTitleAlpha,
                float startStackContentBlurProgress,
                float startFullscreenProgress,
                float startTranslationZ,
                float taskWidth,
                float taskHeight,
                float taskPrimarySize,
                float taskCenteredPrimaryStartPx,
                boolean primaryScrollHorizontal) {
            this.startRawOffset = startRawOffset;
            this.startDismissTranslationX = startDismissTranslationX;
            this.startVisibleOffset = startVisibleOffset;
            this.startHorizontalOffsetX = startHorizontalOffsetX;
            this.startTaskOffsetX = startTaskOffsetX;
            this.startTaskOffsetY = startTaskOffsetY;
            this.startScale = startScale;
            this.startBoxTranslationY = startBoxTranslationY;
            this.startAttachAlpha = startAttachAlpha;
            this.startStableAlpha = startStableAlpha;
            this.startActivityTitleAlpha = startActivityTitleAlpha;
            this.startStackContentBlurProgress = startStackContentBlurProgress;
            this.startFullscreenProgress = startFullscreenProgress;
            this.startTranslationZ = startTranslationZ;
            this.taskWidth = taskWidth;
            this.taskHeight = taskHeight;
            this.taskPrimarySize = taskPrimarySize;
            this.taskCenteredPrimaryStartPx = taskCenteredPrimaryStartPx;
            this.primaryScrollHorizontal = primaryScrollHorizontal;
            this.centerVisibleOffset = startVisibleOffset;
            this.exitVisibleOffset = startVisibleOffset;
        }
    }

    static final class BlankTapHomeExitRecentsState {
        final float startTranslationX;
        final float startTranslationY;

        BlankTapHomeExitRecentsState(float startTranslationX, float startTranslationY) {
            this.startTranslationX = startTranslationX;
            this.startTranslationY = startTranslationY;
        }
    }

    static final class StackLayoutApplyState {
        final long key;
        final long timeNs;

        StackLayoutApplyState(long key, long timeNs) {
            this.key = key;
            this.timeNs = timeNs;
        }
    }

    static final class PendingStackLayoutApplyState {
        boolean captureStockState;
        boolean dynamicOnly;
        String source;

        PendingStackLayoutApplyState(
                boolean captureStockState,
                boolean dynamicOnly,
                String source) {
            this.captureStockState = captureStockState;
            this.dynamicOnly = dynamicOnly;
            this.source = source;
        }
    }

    static final class PrepareRecentsViewState {
        boolean recentsClipsReady;
        Object clipParent;
        long clearAllLastSyncNs;
        boolean clearAllLastForceHide;
        boolean clearAllLastBlankTapExitActive;
        boolean clearAllButtonReady;
        boolean clearAllReady;
        boolean clearAllEnabled;
        boolean clearAllAllowed;
        View clearAllButton;
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

    static void clearAppToRecentsEntryState(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state == null) {
            return;
        }
        state.appToRecentsEntrySessionActive = false;
        state.appToRecentsGestureReleased = false;
        state.pendingGestureRecentsStackRelease = false;
        state.pendingGestureRecentsStackReleaseHandoff = false;
    }

    static void clearAppToRecentsGestureState(View recentsView) {
        clearAppToRecentsEntryState(recentsView);
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state == null) {
            return;
        }
        state.appToRecentsStackSettled = false;
        state.swipeUpGestureActive = false;
        if (state.positionOwner == POSITION_OWNER_ENTER) {
            state.positionOwner = POSITION_OWNER_NONE;
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

    static void setAppToRecentsStackSettled(View recentsView, boolean settled) {
        RecentsViewState state = settled
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.appToRecentsStackSettled = settled;
        }
    }

    static boolean isAppToRecentsStackSettled(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.appToRecentsStackSettled;
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

    static void clearOverviewStackAnimationState(View recentsView, boolean settled) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state == null) {
            return;
        }
        state.overviewPeekStockAnimationActive = false;
        state.overviewStateStackAnimationActive = false;
        state.overviewStateStackSettled = settled;
        state.overviewStateStackStartAdjacentOffset = null;
        state.overviewStateStackBaselineCaptured = false;
        OVERVIEW_STATE_STACK_ENTRY_TASK_STATES.clear();
    }

    static void setOverviewStateStackAnimationActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state == null) {
            return;
        }
        state.overviewStateStackAnimationActive = active;
        state.overviewStateStackSettled = false;
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

    static void setOverviewPreReleaseStockMode(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.overviewPreReleaseStockMode = active;
        }
    }

    static boolean isOverviewPreReleaseStockMode(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewPreReleaseStockMode;
    }

    static void setOverviewStateStackReleaseRequested(View recentsView, boolean requested) {
        RecentsViewState state = requested
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.overviewStateStackReleaseRequested = requested;
        }
    }

    static boolean isOverviewStateStackReleaseRequested(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewStateStackReleaseRequested;
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

    static void setOverviewStateStackSettled(View recentsView, boolean settled) {
        RecentsViewState state = settled
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.overviewStateStackSettled = settled;
        }
    }

    static boolean isOverviewStateStackSettled(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.overviewStateStackSettled;
    }

    static void setLauncherQuickSwitchStockMode(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.launcherQuickSwitchStockMode = active;
        }
    }

    static boolean isLauncherQuickSwitchStockMode(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.launcherQuickSwitchStockMode;
    }

    static int getPositionOwner(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.positionOwner : POSITION_OWNER_NONE;
    }

    static void setPositionOwner(View recentsView, int owner) {
        RecentsViewState state = owner != POSITION_OWNER_NONE
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.positionOwner = owner;
        }
    }

    static void clearPositionOwner(View recentsView, int owner) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state != null && state.positionOwner == owner) {
            state.positionOwner = POSITION_OWNER_NONE;
        }
    }

    static Float getGestureStackReleaseProgress(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.gestureStackReleaseProgress : null;
    }

    static void setGestureStackReleaseProgress(View recentsView, Float progress) {
        RecentsViewState state = progress != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.gestureStackReleaseProgress = progress;
        }
    }

    static Float getForcedRecentsTranslationX(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.forcedRecentsTranslationX : null;
    }

    static void setForcedRecentsTranslationX(View recentsView, Float translation) {
        RecentsViewState state = translation != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.forcedRecentsTranslationX = translation;
        }
    }

    static Float getForcedRecentsTranslationY(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.forcedRecentsTranslationY : null;
    }

    static void setForcedRecentsTranslationY(View recentsView, Float translation) {
        RecentsViewState state = translation != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.forcedRecentsTranslationY = translation;
        }
    }

    static boolean isBlankTapHomeExitActive(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null && state.blankTapHomeExitActive;
    }

    static void setBlankTapHomeExitActive(View recentsView, boolean active) {
        RecentsViewState state = active
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.blankTapHomeExitActive = active;
        }
    }

    static Float getBlankTapHomeExitProgress(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.blankTapHomeExitProgress : null;
    }

    static void setBlankTapHomeExitProgress(View recentsView, Float progress) {
        RecentsViewState state = progress != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.blankTapHomeExitProgress = progress;
        }
    }

    static BlankTapHomeExitRecentsState getBlankTapHomeExitRecentsState(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.blankTapHomeExitRecentsState : null;
    }

    static void setBlankTapHomeExitRecentsState(
            View recentsView,
            BlankTapHomeExitRecentsState blankTapState) {
        RecentsViewState state = blankTapState != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.blankTapHomeExitRecentsState = blankTapState;
        }
    }

    static StackLayoutApplyState getLastStackLayoutApply(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.lastStackLayoutApply : null;
    }

    static void setLastStackLayoutApply(View recentsView, StackLayoutApplyState applyState) {
        RecentsViewState state = applyState != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.lastStackLayoutApply = applyState;
        }
    }

    static PendingStackLayoutApplyState getPendingStackLayoutApply(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.pendingStackLayoutApply : null;
    }

    static void setPendingStackLayoutApply(
            View recentsView,
            PendingStackLayoutApplyState pendingState) {
        RecentsViewState state = pendingState != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.pendingStackLayoutApply = pendingState;
        }
    }

    static PendingStackLayoutApplyState takePendingStackLayoutApply(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        if (state == null) {
            return null;
        }
        PendingStackLayoutApplyState pendingState = state.pendingStackLayoutApply;
        state.pendingStackLayoutApply = null;
        state.stackLayoutFramePosted = false;
        return pendingState;
    }

    static boolean markStackLayoutFramePosted(View recentsView) {
        RecentsViewState state = ensureRecentsViewState(recentsView);
        if (state == null || state.stackLayoutFramePosted) {
            return false;
        }
        state.stackLayoutFramePosted = true;
        return true;
    }

    static ArrayList<Integer> getLastStackLayoutActiveIndices(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.lastStackLayoutActiveIndices : null;
    }

    static void setLastStackLayoutActiveIndices(
            View recentsView,
            ArrayList<Integer> indices) {
        RecentsViewState state = indices != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.lastStackLayoutActiveIndices = indices;
        }
    }

    static Integer getStackLayoutRecoveryRadius(View recentsView) {
        RecentsViewState state = findRecentsViewState(recentsView);
        return state != null ? state.stackLayoutRecoveryRadius : null;
    }

    static void setStackLayoutRecoveryRadius(View recentsView, Integer radius) {
        RecentsViewState state = radius != null
                ? ensureRecentsViewState(recentsView)
                : findRecentsViewState(recentsView);
        if (state != null) {
            state.stackLayoutRecoveryRadius = radius;
        }
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

    static void clearRecentsViewState(View recentsView) {
        if (recentsView == null) {
            return;
        }
        cancelAndRemove(ACTIVE_GESTURE_STACK_RELEASE_ANIMATORS, recentsView);
        cancelAndRemove(ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS, recentsView);
        RECENTS_VIEW_STATES.remove(recentsView);
        PREPARE_RECENTS_VIEW_STATES.remove(recentsView);
        LAST_STACK_STOCK_CAPTURE_TASK_COUNTS.remove(recentsView);
    }

    static void clearTaskViewState(View taskView) {
        if (taskView == null) {
            return;
        }
        GESTURE_STACK_RELEASE_TASK_STATES.remove(taskView);
        OVERVIEW_STATE_STACK_ENTRY_TASK_STATES.remove(taskView);
        BLANK_TAP_HOME_EXIT_TASK_STATES.remove(taskView);
        LAST_STACK_APP_FLOW_PACKAGES.remove(taskView);
        BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        ORIGINAL_NON_GRID_SCALES.remove(taskView);
        ORIGINAL_BOX_TRANSLATION_YS.remove(taskView);
        ORIGINAL_TASK_ELEVATIONS.remove(taskView);
        ORIGINAL_TASK_OUTLINE_PROVIDERS.remove(taskView);
        LAST_STOCK_TASK_OFFSET_XS.remove(taskView);
        LAST_STOCK_TASK_OFFSET_YS.remove(taskView);
        LAST_STOCK_HORIZONTAL_OFFSET_XS.remove(taskView);
        LAST_STOCK_NON_GRID_SCALES.remove(taskView);
        LAST_STOCK_BOX_TRANSLATION_YS.remove(taskView);
        LAST_STOCK_ATTACH_ALPHAS.remove(taskView);
        LAST_STOCK_STABLE_ALPHAS.remove(taskView);
        LAST_STOCK_TRANSLATION_ZS.remove(taskView);
        LAST_STOCK_FULLSCREEN_PROGRESSES.remove(taskView);
        LAST_APPLIED_TASK_OFFSET_XS.remove(taskView);
        LAST_APPLIED_TASK_OFFSET_YS.remove(taskView);
        LAST_APPLIED_HORIZONTAL_OFFSET_XS.remove(taskView);
        LAST_APPLIED_NON_GRID_SCALES.remove(taskView);
        LAST_APPLIED_BOX_TRANSLATION_YS.remove(taskView);
        LAST_APPLIED_ATTACH_ALPHAS.remove(taskView);
        LAST_APPLIED_STABLE_ALPHAS.remove(taskView);
        LAST_APPLIED_TRANSLATION_ZS.remove(taskView);
        LAST_APPLIED_FULLSCREEN_PROGRESSES.remove(taskView);
        LAST_APPLIED_STACK_CONTENT_BLURS.remove(taskView);
        LAST_APPLIED_ACTIVITY_TITLE_ALPHAS.remove(taskView);
        LAST_APPLIED_TASK_HEAD_ALPHAS.remove(taskView);
        LAST_APPLIED_STACK_TASK_VISUAL_STATES.remove(taskView);
        STACK_CONTENT_TARGETS.remove(taskView);
        STACK_ICON_BLUR_STATES.remove(taskView);
        ORIGINAL_STACK_ICON_OUTLINE_PROVIDERS.remove(taskView);
        ORIGINAL_STACK_ICON_CLIP_TO_OUTLINES.remove(taskView);
    }

    private static void cancelAndRemove(
            WeakHashMap<View, ValueAnimator> animators,
            View view) {
        ValueAnimator animator = animators.remove(view);
        if (animator != null) {
            animator.cancel();
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
