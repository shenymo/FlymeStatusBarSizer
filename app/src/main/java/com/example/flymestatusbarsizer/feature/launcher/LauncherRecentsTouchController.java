package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class LauncherRecentsTouchController {
    private static final String TASK_VIEW_DISMISS_TOUCH_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewDismissTouchController";
    private static final String TASK_VIEW_TOUCH_CONTROLLER_DEPRECATED_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchControllerDeprecated";
    private static final long STACK_DISMISS_SUCCESS_ANIM_MS = 180L;
    private static final long STACK_DISMISS_CANCEL_ANIM_MS = 320L;
    private static final float STACK_DISMISS_SECONDARY_DOMINANCE = 1.2f;
    private static final float STACK_DISMISS_MIN_FLING_VELOCITY = -1200f;
    private static final float STACK_VISIBLE_DATA_RIGHT_MARGIN_RATIO = 1.40f;
    private static final int STACK_VISIBLE_DATA_SCROLL_BUCKET_DIVISOR = 2;
    private static final long STACK_VISIBLE_DATA_SYNC_RETRY_DELAY_MS = 64L;
    private static final long STACK_VISIBLE_DATA_CLEANUP_IDLE_DELAY_MS = 450L;
    private static final long STACK_VISIBLE_DATA_CLEANUP_RETRY_DELAY_MS = 250L;
    private static final float STACK_LEFT_RELEASE_ALPHA_THRESHOLD = 0.05f;
    private static final int STACK_APP_FLOW_LIGHT_RADIUS = 3;
    private static final String STACK_APP_FLOW_HIDDEN = "<stack-hidden>";
    private static final ThreadLocal<Boolean> TASK_DISMISS_VISIBILITY_BYPASS =
            new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE =
            new ThreadLocal<>();
    private static final WeakHashMap<View, StackDismissGestureState> STACK_DISMISS_GESTURES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> STACK_DISMISS_LAYOUT_OFFSETS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> STACK_DISMISS_VISIBLE_OFFSETS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ArrayList<Integer>> STACK_VISIBLE_TASK_IDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, StackVisibleTaskDataSyncState>
            STACK_VISIBLE_TASK_DATA_SYNC_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SILENT_NATIVE_DISMISS_RECENTS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, SilentNativeDismissAnchor> SILENT_NATIVE_DISMISS_ANCHORS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> STACK_DISMISS_POST_REMOVE_RECENTS =
            new WeakHashMap<>();
    private static final ThreadLocal<Boolean> STACK_DISMISS_LAYOUT_FREEZE_BYPASS =
            new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS =
            new ThreadLocal<>();

    private LauncherRecentsTouchController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookPagedViewOnInterceptTouchEvent(module, loader);
        hookPagedViewOnTouchEvent(module, loader);
        hookRecentsViewNotifyHandleActionUp(module, loader);
        hookRecentsViewFreeScrollSettling(module, loader);
        hookPagedViewSnapToDestination(module, loader);
        hookTaskDismissControllerTouch(
                module,
                loader,
                TASK_VIEW_TOUCH_CONTROLLER_DEPRECATED_CLASS);
        hookTaskDismissControllerTouch(
                module,
                loader,
                TASK_VIEW_DISMISS_TOUCH_CONTROLLER_CLASS);
        hookRecentsViewTaskVisibilityForDismiss(module, loader);
        hookRecentsViewLoadVisibleTaskDataForStack(module, loader);
        hookTaskViewListVisibilityForStack(module, loader);
        hookTaskViewAppFlowVisibilityForStack(module, loader);
        hookRecentsViewResetTaskVisualsForSilentDismiss(module, loader);
        hookRecentsViewUpdateTaskSizeForSilentDismiss(module, loader);
        hookTaskViewNativeDismissTransformsForSilentDismiss(module, loader);
        hookRecentsViewDismissAnimationEnds(module, loader);
    }

    private static void hookPagedViewOnInterceptTouchEvent(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onInterceptTouchEvent", MotionEvent.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                if (LauncherRecentsCompat.isRecentsViewObject(thisObject)
                        && thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    keepAppToRecentsEntryHeadsVisibleOnTouchDown(recentsView, motionEvent);
                    if (isStackDismissPostRemoveAnimationActive(recentsView)) {
                        if (shouldConsumeStackDismissPostRemoveTouch(recentsView, motionEvent)) {
                            logStackFlow("touch:intercept:consumePostRemove",
                                    recentsView, motionEvent, null);
                            return true;
                        }
                    }
                    if (handleMovingStackBlankTapHomeExit(recentsView, motionEvent)) {
                        logStackFlow("touch:intercept:movingBlankTapHome",
                                recentsView, motionEvent, null);
                        return true;
                    }
                    if (shouldKeepStackDismissGestureAwayFromPagedView(
                            recentsView,
                            motionEvent)) {
                        logStackFlow("touch:intercept:releaseToDismiss",
                                recentsView, motionEvent, null);
                        releasePagedTouchForStackDismiss(recentsView);
                        return false;
                    }
                    return chain.proceed();
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.onInterceptTouchEvent",
                    t);
        }
    }

    private static void hookPagedViewOnTouchEvent(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                if (LauncherRecentsCompat.isRecentsViewObject(thisObject)
                        && thisObject instanceof View
                        && motionEvent != null) {
                    View recentsView = (View) thisObject;
                    keepAppToRecentsEntryHeadsVisibleOnTouchDown(recentsView, motionEvent);
                    boolean entryTakeover =
                            takeOverAppToRecentsEntryOnHorizontalMove(recentsView, motionEvent);
                    boolean overviewTakeover = !entryTakeover
                            && takeOverOverviewStateOnHorizontalMove(recentsView, motionEvent);
                    clearGestureReleaseTaskStatesOnUserMove(recentsView, motionEvent);
                    if (isStackDismissPostRemoveAnimationActive(recentsView)) {
                        if (shouldConsumeStackDismissPostRemoveTouch(recentsView, motionEvent)) {
                            logStackFlow("touch:event:consumePostRemove",
                                    recentsView, motionEvent, null);
                            return true;
                        }
                    }
                    if (handleMovingStackBlankTapHomeExit(recentsView, motionEvent)) {
                        logStackFlow("touch:event:movingBlankTapHome",
                                recentsView, motionEvent, null);
                        return true;
                    }
                    if (shouldKeepStackDismissGestureAwayFromPagedView(
                            recentsView,
                            motionEvent)) {
                        logStackFlow("touch:event:releaseToDismiss",
                                recentsView, motionEvent, null);
                        releasePagedTouchForStackDismiss(recentsView);
                        return false;
                    }
                    if (shouldSkipBlankTapPagedRelease(recentsView, motionEvent)) {
                        logStackFlow("touch:event:blankTapRelease",
                                recentsView, motionEvent, null);
                        if (!LauncherRecentsTransitionController.isBlankTapHomeExitActive(
                                recentsView)) {
                            LauncherRecentsCompat.invokeCompat(
                                    recentsView,
                                    "startHome",
                                    LauncherRecentsCompat.BOOLEAN_ARG,
                                    true);
                        }
                        LauncherRecentsCompat.setBooleanField(
                                recentsView,
                                "mTouchDownToStartHome",
                                false);
                        releasePagedEdgeEffects(recentsView, motionEvent);
                        LauncherRecentsCompat.invokeCompat(
                                recentsView,
                                "resetTouchState",
                                LauncherRecentsCompat.NO_ARGS);
                        return true;
                    }
                    if (LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                            && shouldSuppressPagedRelease(recentsView, motionEvent)) {
                        logStackFlow("touch:event:suppressPagedRelease",
                                recentsView, motionEvent, null);
                        LauncherRecentsState.trackRecentsView(recentsView);
                        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                        suppressPagedRelease(recentsView, motionEvent);
                        return true;
                    }
                    Object result = chain.proceed();
                    if (entryTakeover) {
                        keepAppToRecentsEntryTakeoverDataReady(recentsView);
                    }
                    if (overviewTakeover) {
                        keepOverviewStateTakeoverLayoutReady(recentsView);
                    }
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.onTouchEvent",
                    t);
        }
    }

    private static void hookRecentsViewNotifyHandleActionUp(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("notifyHandleActionUp", taskViewClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (thisObject instanceof View && arg0 instanceof View) {
                    View recentsView = (View) thisObject;
                    View taskView = (View) arg0;
                    if (LauncherRecentsLaunchController.shouldSuppressTaskHandleActionUp(
                            recentsView,
                            taskView)) {
                        clearRecentsDeferredSnap(recentsView);
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.notifyHandleActionUp",
                    t);
        }
    }

    private static void hookRecentsViewFreeScrollSettling(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onNotSnappingToPageInFreeScroll");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView)) {
                        logStackFlow("freeScroll:settle:applied", recentsView, null, null);
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

    private static void hookPagedViewSnapToDestination(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("snapToDestination");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (LauncherRecentsCompat.isRecentsViewObject(thisObject)
                        && thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView)) {
                        logStackFlow("snapToDestination:applied", recentsView, null, null);
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

    private static void hookTaskDismissControllerTouch(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Method interceptMethod = clazz.getDeclaredMethod(
                    "onControllerInterceptTouchEvent",
                    MotionEvent.class);
            interceptMethod.setAccessible(true);
            module.intercept(interceptMethod, chain -> {
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                Boolean handled = handleStackDismissControllerTouch(
                        chain.getThisObject(),
                        motionEvent,
                        "intercept");
                if (handled != null) {
                    return handled;
                }
                Boolean previous = TASK_DISMISS_VISIBILITY_BYPASS.get();
                TASK_DISMISS_VISIBILITY_BYPASS.set(Boolean.TRUE);
                try {
                    return chain.proceed();
                } finally {
                    if (previous == null) {
                        TASK_DISMISS_VISIBILITY_BYPASS.remove();
                    } else {
                        TASK_DISMISS_VISIBILITY_BYPASS.set(previous);
                    }
                }
            });
            Method touchMethod = clazz.getDeclaredMethod(
                    "onControllerTouchEvent",
                    MotionEvent.class);
            touchMethod.setAccessible(true);
            module.intercept(touchMethod, chain -> {
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                Boolean handled = handleStackDismissControllerTouch(
                        chain.getThisObject(),
                        motionEvent,
                        "touch");
                if (handled != null) {
                    return handled;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook " + className + " task dismiss touch",
                    t);
        }
    }

    private static void keepAppToRecentsEntryHeadsVisibleOnTouchDown(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_DOWN
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || !isAppToRecentsEntryTouchTakeoverNeeded(recentsView)) {
            return;
        }
        logStackFlow("touch:entryDownHeads", recentsView, motionEvent, null);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        recentsView.invalidate();
    }

    private static void clearGestureReleaseTaskStatesOnUserMove(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                || LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.isEmpty()) {
            return;
        }
        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
    }

    private static boolean takeOverAppToRecentsEntryOnHorizontalMove(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || !isAppToRecentsEntryTouchTakeoverNeeded(recentsView)) {
            return false;
        }
        float downX = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionX",
                motionEvent.getX());
        float downY = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionY",
                motionEvent.getY());
        float dx = motionEvent.getX() - downX;
        float dy = motionEvent.getY() - downY;
        int touchSlop = ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop();
        if (Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(dy)) {
            return false;
        }
        logStackFlow("touch:entryTakeover",
                recentsView,
                motionEvent,
                "dx=" + Math.round(dx) + " dy=" + Math.round(dy));
        LauncherRecentsTaskVisuals.captureCurrentTaskStatesAsBaseline(recentsView);
        int currentScroll = resolvePrimaryScroll(recentsView);
        LauncherRecentsTransitionController.cancelGestureRecentsStackReleaseAnimation(
                recentsView,
                true);
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        LauncherRecentsAttachController.endAppToRecentsEntrySessionWithoutLayout(recentsView);
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, false);
        LauncherRecentsLayoutEngine.captureGestureStackReleaseTaskStates(
                recentsView,
                currentScroll,
                currentScroll);
        LauncherRecentsTransitionController.setGestureRecentsStackReleaseProgress(
                recentsView,
                0f);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setEnableDrawingLiveTile",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskViewShowScreenshot",
                LauncherRecentsCompat.BOOLEAN_ARG,
                true);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "setRunningTaskHidden",
                LauncherRecentsCompat.BOOLEAN_ARG,
                false);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "overviewTouchTakeover",
                false,
                false);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.requestLayout();
        recentsView.invalidate();
        recentsView.postOnAnimation(() -> finishAppToRecentsEntryTouchTakeover(recentsView));
        return true;
    }

    private static void keepAppToRecentsEntryTakeoverDataReady(View recentsView) {
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.invalidate();
    }

    private static void finishAppToRecentsEntryTouchTakeover(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        LauncherRecentsTransitionController.setGestureRecentsStackReleaseProgress(
                recentsView,
                1f);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "overviewTakeoverReady",
                false,
                false);
        forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.requestLayout();
        recentsView.invalidate();
        recentsView.postDelayed(() -> clearAppToRecentsEntryTouchTakeover(recentsView), 80L);
    }

    private static void clearAppToRecentsEntryTouchTakeover(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        LauncherRecentsTransitionController.clearGestureRecentsStackReleaseProgress(recentsView);
        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
        LauncherRecentsState.setGestureStackReleasedStable(recentsView, true);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "entryTouchTakeoverClear",
                false,
                false);
        forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static boolean takeOverOverviewStateOnHorizontalMove(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || !LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(
                recentsView)) {
            return false;
        }
        float downX = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionX",
                motionEvent.getX());
        float downY = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionY",
                motionEvent.getY());
        float dx = motionEvent.getX() - downX;
        float dy = motionEvent.getY() - downY;
        int touchSlop = ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop();
        if (Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(dy)) {
            return false;
        }
        logStackFlow("touch:overviewTakeover",
                recentsView,
                motionEvent,
                "dx=" + Math.round(dx) + " dy=" + Math.round(dy));
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        LauncherRecentsStateAnimationController.finishOverviewStateStackAnimationForTouchTakeover(
                recentsView);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "entryTouchTakeover",
                false,
                false);
        recentsView.requestLayout();
        recentsView.invalidate();
        return true;
    }

    private static void keepOverviewStateTakeoverLayoutReady(View recentsView) {
        LauncherRecentsTransitionController.forceRecentsTranslationZero(recentsView);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsLayoutEngine.requestStackLayout(
                recentsView,
                "entryTouchTakeoverFinish",
                false,
                false);
        LauncherRecentsTaskVisuals.forceRecentsTaskHeadsVisible(recentsView);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static boolean isAppToRecentsEntryTouchTakeoverNeeded(View recentsView) {
        return LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(
                recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView);
    }

    private static void clearGestureReleaseTaskStatesForStackDismiss(View recentsView) {
        if (recentsView == null
                || !LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                || LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.isEmpty()) {
            return;
        }
        LauncherRecentsState.GESTURE_STACK_RELEASE_TASK_STATES.clear();
    }

    private static void hookRecentsViewTaskVisibilityForDismiss(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("isTaskViewVisible", taskViewClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (Boolean.TRUE.equals(result)) {
                    return result;
                }
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (thisObject instanceof View
                        && arg0 instanceof View
                        && shouldExposeStackTaskForDismissVisibility(
                        (View) thisObject,
                        (View) arg0)) {
                    return true;
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.isTaskViewVisible",
                    t);
        }
    }

    private static void hookRecentsViewLoadVisibleTaskDataForStack(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("loadVisibleTaskData", int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (!(thisObject instanceof View)
                        || !LauncherRecentsLayoutEngine.shouldUseStackLayout((View) thisObject)) {
                    return chain.proceed();
                }
                Boolean previous = STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE.get();
                STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE.set(Boolean.TRUE);
                Object result;
                try {
                    result = chain.proceed();
                } finally {
                    if (previous == null) {
                        STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE.remove();
                    } else {
                        STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE.set(previous);
                    }
                }
                if (thisObject instanceof View
                        && !isStackDismissReflowActive((View) thisObject)) {
                    Object arg0 = chain.getArg(0);
                    int changes = arg0 instanceof Integer ? (Integer) arg0 : 15;
                    forceEnsureStackVisibleTaskData((View) thisObject, changes);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.loadVisibleTaskData",
                    t);
        }
    }

    private static void hookTaskViewListVisibilityForStack(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onTaskListVisibilityChanged",
                    boolean.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                Object arg1 = chain.getArg(1);
                if (thisObject instanceof View
                        && arg0 instanceof Boolean
                        && arg1 instanceof Integer
                        && shouldThrottleStackTaskListVisibility(
                        (View) thisObject,
                        (Boolean) arg0,
                        (Integer) arg1)) {
                    return null;
                }
                if (thisObject instanceof View
                        && Boolean.FALSE.equals(arg0)
                        && arg1 instanceof Integer
                        && shouldSuppressStackTaskDataUnload((View) thisObject, (Integer) arg1)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView.onTaskListVisibilityChanged",
                    t);
        }
    }

    private static void hookRecentsViewResetTaskVisualsForSilentDismiss(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("resetTaskVisuals");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && isSilentNativeDismissActive((View) thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.resetTaskVisuals",
                    t);
        }
    }

    private static void hookRecentsViewDismissAnimationEnds(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onDismissAnimationEnds");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (isSilentNativeDismissActive(recentsView)) {
                        clearNativeDismissTransforms(recentsView);
                        clearStackDismissLayoutOffsets();
                        recentsView.invalidate();
                        scheduleSilentNativeDismissFinish(recentsView);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onDismissAnimationEnds",
                    t);
        }
    }

    private static void hookRecentsViewUpdateTaskSizeForSilentDismiss(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateTaskSize");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && isSilentNativeDismissActive((View) thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.updateTaskSize",
                    t);
        }
    }

    private static void hookTaskViewNativeDismissTransformsForSilentDismiss(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method translationMethod = clazz.getDeclaredMethod("setDismissTranslationX", float.class);
            translationMethod.setAccessible(true);
            module.intercept(translationMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && shouldSuppressNativeTaskDismissTransform((View) thisObject)) {
                    resetNativeDismissTranslationX((View) thisObject);
                    return null;
                }
                return chain.proceed();
            });
            Method scaleMethod = clazz.getDeclaredMethod("setDismissScale", float.class);
            scaleMethod.setAccessible(true);
            module.intercept(scaleMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && shouldSuppressNativeTaskDismissTransform((View) thisObject)) {
                    resetNativeDismissScale((View) thisObject);
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView native dismiss transforms",
                    t);
        }
    }

    static float readStackDismissLayoutOffset(View taskView) {
        Float value = STACK_DISMISS_LAYOUT_OFFSETS.get(taskView);
        return value != null ? value : 0f;
    }

    static Float readStackDismissVisibleOffset(View taskView) {
        return STACK_DISMISS_VISIBLE_OFFSETS.get(taskView);
    }

    static boolean hasStackDismissLayoutOffset(View taskView) {
        return STACK_DISMISS_LAYOUT_OFFSETS.containsKey(taskView);
    }

    private static boolean hasActiveStackDismissLayoutOffset(View taskView) {
        return Math.abs(readStackDismissLayoutOffset(taskView)) > 0.5f;
    }

    private static boolean hasActiveStackDismissLayoutOffsets() {
        for (Float value : STACK_DISMISS_LAYOUT_OFFSETS.values()) {
            if (value != null && Math.abs(value) > 0.5f) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStackDismissReflowActive(View recentsView) {
        return recentsView != null
                && (STACK_DISMISS_GESTURES.containsKey(recentsView)
                || hasActiveStackDismissLayoutOffsets());
    }

    private static boolean isSilentNativeDismissActive(View recentsView) {
        return Boolean.TRUE.equals(SILENT_NATIVE_DISMISS_RECENTS.get(recentsView))
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView);
    }

    static boolean shouldSuppressNativeDismissTranslation(View recentsView) {
        return isSilentNativeDismissActive(recentsView)
                || isStackDismissPostRemoveAnimationActive(recentsView);
    }

    static boolean isStackDismissPostRemoveAnimationActive(View recentsView) {
        return Boolean.TRUE.equals(STACK_DISMISS_POST_REMOVE_RECENTS.get(recentsView));
    }

    static boolean shouldBypassStackDismissLayoutFreeze() {
        return Boolean.TRUE.equals(STACK_DISMISS_LAYOUT_FREEZE_BYPASS.get());
    }

    private static boolean shouldSuppressNativeTaskDismissTransform(View taskView) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        return isSilentNativeDismissActive(recentsView)
                || isStackDismissPostRemoveAnimationActive(recentsView);
    }

    static boolean shouldBypassStackDismissScrollSuppression() {
        return Boolean.TRUE.equals(STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS.get());
    }

    static boolean shouldSuppressStackDismissPageMutation(Object thisObject) {
        return thisObject instanceof View
                && LauncherRecentsCompat.isRecentsViewObject(thisObject)
                && (isSilentNativeDismissActive((View) thisObject)
                || isStackDismissPostRemoveAnimationActive((View) thisObject));
    }

    static boolean handleStackDismissSetCurrentPage(Object thisObject) {
        if (!shouldSuppressStackDismissPageMutation(thisObject)) {
            return false;
        }
        View recentsView = (View) thisObject;
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (pageCount > 0) {
            SilentNativeDismissAnchor anchor = SILENT_NATIVE_DISMISS_ANCHORS.get(recentsView);
            int page = anchor != null
                    ? Math.min(anchor.targetPage, pageCount - 1)
                    : resolveNearestStackDismissPageForScroll(recentsView, pageCount);
            if (isStackDismissPostRemoveAnimationActive(recentsView)
                    && shouldBypassStackDismissLayoutFreeze()) {
                setStackDismissCurrentPageSnapped(recentsView, page);
            } else {
                setStackDismissCurrentPageKeepingScroll(recentsView, page);
            }
        }
        clearRecentsDeferredSnap(recentsView);
        return true;
    }

    private static Boolean handleStackDismissControllerTouch(
            Object controller,
            MotionEvent motionEvent,
            String source) {
        View recentsView = resolveControllerRecentsView(controller);
        if (recentsView == null || motionEvent == null) {
            return null;
        }
        logStackFlow("dismissController:" + source, recentsView, motionEvent,
                controller != null ? controller.getClass().getSimpleName() : null);
        if (!LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            logStackFlow("dismissController:" + source + ":notStack",
                    recentsView, motionEvent, null);
            clearStackDismissGesture(recentsView, true);
            return null;
        }
        return handleStackDismissTouch(recentsView, motionEvent);
    }

    private static View resolveControllerRecentsView(Object controller) {
        Object value = LauncherRecentsCompat.getFieldCompat(controller, "recentsView");
        if (value instanceof View) {
            return (View) value;
        }
        value = LauncherRecentsCompat.getFieldCompat(controller, "mRecentsView");
        return value instanceof View ? (View) value : null;
    }

    private static void logStackFlow(
            String name,
            View recentsView,
            MotionEvent motionEvent,
            String details) {
        String message = "";
        if (motionEvent != null) {
            message = "action=" + MotionEvent.actionToString(motionEvent.getActionMasked())
                    + " x=" + Math.round(motionEvent.getX())
                    + " y=" + Math.round(motionEvent.getY());
        }
        if (details != null && !details.isEmpty()) {
            message = message.isEmpty() ? details : message + " " + details;
        }
        LauncherRecentsPerf.flow(name, recentsView, message);
    }

    private static String taskDetails(View recentsView, View taskView) {
        if (taskView == null) {
            return "task=null";
        }
        return "taskIndex=" + findTaskViewIndex(recentsView, taskView)
                + " taskAlpha=" + taskView.getAlpha()
                + " taskShown=" + taskView.isShown();
    }

    private static Boolean handleStackDismissTouch(View recentsView, MotionEvent motionEvent) {
        int action = motionEvent.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            clearStackDismissGesture(recentsView, true);
            View taskView = findStackTaskUnderRawPoint(
                    recentsView,
                    motionEvent.getRawX(),
                    motionEvent.getRawY());
            logStackFlow("dismiss:down", recentsView, motionEvent,
                    taskDetails(recentsView, taskView));
            if (!isStackDismissTaskCandidate(recentsView, taskView)) {
                logStackFlow("dismiss:down:noCandidate",
                        recentsView, motionEvent, taskDetails(recentsView, taskView));
                return null;
            }
            StackDismissGestureState state =
                    new StackDismissGestureState(recentsView, taskView, motionEvent);
            STACK_DISMISS_GESTURES.put(recentsView, state);
            return false;
        }

        StackDismissGestureState state = STACK_DISMISS_GESTURES.get(recentsView);
        if (state == null) {
            return null;
        }
        if (!isStackDismissTaskCandidate(recentsView, state.taskView)) {
            logStackFlow("dismiss:clearInvalidTask",
                    recentsView, motionEvent, taskDetails(recentsView, state.taskView));
            clearStackDismissGesture(recentsView, true);
            return null;
        }
        state.addMovement(motionEvent);

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = motionEvent.getRawX() - state.downRawX;
            float dy = motionEvent.getRawY() - state.downRawY;
            int touchSlop = ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop();
            if (!state.dragging) {
                if (isStackDismissDragStart(state, dx, dy, touchSlop)) {
                    beginStackDismissDrag(state, motionEvent);
                } else if (isStackDismissPrimaryGesture(state, dx, dy, touchSlop)) {
                    logStackFlow("dismiss:primaryGesture",
                            recentsView,
                            motionEvent,
                            "dx=" + Math.round(dx) + " dy=" + Math.round(dy));
                    clearStackDismissGesture(recentsView, true);
                    return null;
                } else {
                    return false;
                }
            }
            updateStackDismissDrag(state, motionEvent);
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (state.dragging) {
                finishStackDismissGesture(state, action == MotionEvent.ACTION_CANCEL);
                return true;
            }
            logStackFlow("dismiss:end:notDragging",
                    recentsView, motionEvent, taskDetails(recentsView, state.taskView));
            clearStackDismissGesture(recentsView, false);
            return false;
        }

        return state.dragging ? true : false;
    }

    private static boolean isStackDismissTaskCandidate(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || LauncherRecentsCompat.isDesktopTask(taskView)
                || taskView.getVisibility() != View.VISIBLE
                || taskView.getAlpha() <= 0.01f
                || taskView.getWidth() <= 0
                || taskView.getHeight() <= 0) {
            return false;
        }
        return !(recentsView instanceof ViewGroup)
                || ((ViewGroup) recentsView).indexOfChild(taskView) >= 0;
    }

    private static boolean isStackDismissReflowTaskCandidate(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || LauncherRecentsCompat.isDesktopTask(taskView)
                || taskView.getVisibility() != View.VISIBLE
                || taskView.getWidth() <= 0
                || taskView.getHeight() <= 0) {
            return false;
        }
        return !(recentsView instanceof ViewGroup)
                || ((ViewGroup) recentsView).indexOfChild(taskView) >= 0;
    }

    private static View findStackTaskUnderRawPoint(View recentsView, float rawX, float rawY) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        View bestTaskView = null;
        float bestZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (!isStackDismissTaskCandidate(recentsView, taskView)
                    || !isRawPointInTransformedView(taskView, rawX, rawY)) {
                continue;
            }
            float z = taskView.getTranslationZ();
            if (bestTaskView == null || z >= bestZ) {
                bestTaskView = taskView;
                bestZ = z;
            }
        }
        return bestTaskView;
    }

    private static boolean isRawPointInTransformedView(View view, float rawX, float rawY) {
        if (view == null) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float scaleX = Math.max(0.01f, Math.abs(view.getScaleX()));
        float scaleY = Math.max(0.01f, Math.abs(view.getScaleY()));
        float pivotX = view.getPivotX();
        float pivotY = view.getPivotY();
        float left = location[0] + pivotX - (pivotX * scaleX);
        float top = location[1] + pivotY - (pivotY * scaleY);
        float right = left + (view.getWidth() * scaleX);
        float bottom = top + (view.getHeight() * scaleY);
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom;
    }

    private static boolean shouldKeepStackDismissGestureAwayFromPagedView(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || findStackTaskUnderPoint(
                recentsView,
                motionEvent.getX(),
                motionEvent.getY()) == null) {
            return false;
        }
        float downX = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionX",
                motionEvent.getX());
        float downY = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionY",
                motionEvent.getY());
        float dx = motionEvent.getX() - downX;
        float dy = motionEvent.getY() - downY;
        int touchSlop = LauncherRecentsCompat.readIntField(
                recentsView,
                "mTouchSlop",
                ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop());
        float primaryDelta = resolveGesturePrimaryDelta(recentsView, dx, dy);
        float secondaryDelta = resolveGestureSecondaryDelta(recentsView, dx, dy);
        float absPrimary = Math.abs(primaryDelta);
        float absSecondary = Math.abs(secondaryDelta);
        return resolveStackDismissDirectionSign(recentsView) * secondaryDelta > 0f
                && absSecondary > touchSlop
                && absSecondary >= absPrimary * 0.8f;
    }

    private static void releasePagedTouchForStackDismiss(View recentsView) {
        clearRecentsDeferredSnap(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "resetTouchState",
                LauncherRecentsCompat.NO_ARGS);
        ViewParent parent = recentsView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(false);
        }
    }

    private static View findStackTaskUnderPoint(View recentsView, float x, float y) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        View bestTaskView = null;
        float bestZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null
                    || LauncherRecentsCompat.isDesktopTask(taskView)
                    || taskView.getVisibility() != View.VISIBLE
                    || taskView.getAlpha() <= 0.01f
                    || taskView.getWidth() <= 0
                    || taskView.getHeight() <= 0
                    || !isPointInTransformedView(recentsView, taskView, x, y)) {
                continue;
            }
            float z = taskView.getTranslationZ();
            if (bestTaskView == null || z >= bestZ) {
                bestTaskView = taskView;
                bestZ = z;
            }
        }
        return bestTaskView;
    }

    private static boolean isPointInTransformedView(
            View parent,
            View view,
            float x,
            float y) {
        float scaleX = Math.max(0.01f, Math.abs(view.getScaleX()));
        float scaleY = Math.max(0.01f, Math.abs(view.getScaleY()));
        float pivotX = view.getPivotX();
        float pivotY = view.getPivotY();
        float left = view.getX() - parent.getScrollX() + pivotX - (pivotX * scaleX);
        float top = view.getY() - parent.getScrollY() + pivotY - (pivotY * scaleY);
        float right = left + (view.getWidth() * scaleX);
        float bottom = top + (view.getHeight() * scaleY);
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private static boolean isStackDismissDragStart(
            StackDismissGestureState state,
            float dx,
            float dy,
            int touchSlop) {
        float primaryDelta = resolveGesturePrimaryDelta(state.recentsView, dx, dy);
        float secondaryDelta = resolveGestureSecondaryDelta(state.recentsView, dx, dy);
        float absPrimary = Math.abs(primaryDelta);
        float absSecondary = Math.abs(secondaryDelta);
        return isStackDismissGestureTowardDismiss(state, secondaryDelta)
                && absSecondary > touchSlop
                && absSecondary >= absPrimary * STACK_DISMISS_SECONDARY_DOMINANCE;
    }

    private static boolean isStackDismissPrimaryGesture(
            StackDismissGestureState state,
            float dx,
            float dy,
            int touchSlop) {
        float absPrimary = Math.abs(resolveGesturePrimaryDelta(state.recentsView, dx, dy));
        float absSecondary = Math.abs(resolveGestureSecondaryDelta(state.recentsView, dx, dy));
        return absPrimary > touchSlop && absPrimary > absSecondary;
    }

    private static boolean isStackDismissGoingUp(View recentsView, float secondaryDelta) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "isGoingUp",
                new Class<?>[]{float.class, boolean.class},
                secondaryDelta,
                recentsView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return secondaryDelta < 0f;
    }

    private static float resolveStackDismissDirectionSign(View recentsView) {
        float nativeSign = isStackDismissGoingUp(recentsView, 1f) ? 1f : -1f;
        return isPrimaryScrollHorizontal(recentsView) ? nativeSign : -nativeSign;
    }

    private static boolean isStackDismissGestureTowardDismiss(
            StackDismissGestureState state,
            float secondaryDelta) {
        return state.dismissDirectionSign * secondaryDelta > 0f;
    }

    private static void beginStackDismissDrag(
            StackDismissGestureState state,
            MotionEvent motionEvent) {
        logStackFlow("dismiss:beginDrag",
                state.recentsView,
                motionEvent,
                taskDetails(state.recentsView, state.taskView));
        state.dragging = true;
        state.cancelAnimator();
        state.taskView.animate().cancel();
        LauncherRecentsState.trackRecentsView(state.recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(state.recentsView);
        clearGestureReleaseTaskStatesForStackDismiss(state.recentsView);
        settleStackDismissLayoutState(state.recentsView);
        clearRecentsDeferredSnap(state.recentsView);
        LauncherRecentsCompat.invokeCompat(
                state.recentsView,
                "abortScrollerAnimation",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsCompat.invokeCompat(
                state.recentsView,
                "resetTouchState",
                LauncherRecentsCompat.NO_ARGS);
        releasePagedEdgeEffects(state.recentsView, motionEvent);
        requestParentDisallowIntercept(state.recentsView, true);
        LauncherRecentsTaskVisuals.setTranslationZ(
                state.taskView,
                state.originalTranslationZ);
        prepareStackDismissSiblingMoves(state);
        applyStackDismissProgress(state, state.currentDismissTranslation);
        preheatStackDismissVisibleTaskData(state.recentsView);
    }

    private static void updateStackDismissDrag(
            StackDismissGestureState state,
            MotionEvent motionEvent) {
        float dx = motionEvent.getRawX() - state.downRawX;
        float dy = motionEvent.getRawY() - state.downRawY;
        float delta = resolveGestureSecondaryDelta(state.recentsView, dx, dy);
        float translation = state.startDismissTranslation
                + (isStackDismissGestureTowardDismiss(state, delta) ? delta : delta * 0.18f);
        state.currentDismissTranslation =
                state.dismissDirectionSign * translation
                        >= state.dismissDirectionSign * state.startDismissTranslation
                        ? translation
                        : state.startDismissTranslation;
        applyStackDismissProgress(state, state.currentDismissTranslation);
    }

    private static void finishStackDismissGesture(
            StackDismissGestureState state,
            boolean canceled) {
        STACK_DISMISS_GESTURES.remove(state.recentsView);
        requestParentDisallowIntercept(state.recentsView, false);
        if (canceled) {
            logStackFlow("dismiss:finish:canceled",
                    state.recentsView, null, taskDetails(state.recentsView, state.taskView));
            animateStackDismissCancel(state);
            state.recycleVelocityTracker();
            return;
        }
        float velocity = state.computeSecondaryVelocity();
        boolean dismiss =
                state.dismissDirectionSign * state.currentDismissTranslation
                        >= resolveStackDismissThreshold(state)
                        || state.dismissDirectionSign * velocity
                        >= -STACK_DISMISS_MIN_FLING_VELOCITY;
        logStackFlow("dismiss:finish",
                state.recentsView,
                null,
                "dismiss=" + dismiss
                        + " translation=" + Math.round(state.currentDismissTranslation)
                        + " velocity=" + Math.round(velocity)
                        + " " + taskDetails(state.recentsView, state.taskView));
        if (dismiss) {
            animateStackDismissSuccess(state);
        } else {
            animateStackDismissCancel(state);
        }
        state.recycleVelocityTracker();
    }

    private static void animateStackDismissSuccess(StackDismissGestureState state) {
        logStackFlow("dismiss:animateSuccess:start",
                state.recentsView, null, taskDetails(state.recentsView, state.taskView));
        float start = state.currentDismissTranslation;
        float end = state.dismissDirectionSign * resolveStackDismissDistance(state);
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        state.animator = animator;
        animator.setDuration(STACK_DISMISS_SUCCESS_ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslation = value;
            LauncherRecentsPerf.hit("animationFrame:dismissSuccess", state.recentsView);
            applyStackDismissSuccessProgress(
                    state,
                    value,
                    resolveStackDismissReflowProgress(state, value));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                logStackFlow("dismiss:animateSuccess:end",
                        state.recentsView, null, taskDetails(state.recentsView, state.taskView));
                applyStackDismissSuccessProgress(state, end, 1f);
                finishStackDismissAfterReflow(state);
            }
        });
        animator.start();
    }

    private static void finishStackDismissAfterReflow(StackDismissGestureState state) {
        boolean dismissed = invokeNativeDismissTaskView(state.recentsView, state.taskView);
        logStackFlow("dismiss:afterReflow",
                state.recentsView,
                null,
                "dismissed=" + dismissed + " " + taskDetails(state.recentsView, state.taskView));
        if (!dismissed) {
            resetStackDismissVisuals(state);
        }
    }

    private static void animateStackDismissCancel(StackDismissGestureState state) {
        logStackFlow("dismiss:animateCancel:start",
                state.recentsView, null, taskDetails(state.recentsView, state.taskView));
        float start = state.currentDismissTranslation;
        ValueAnimator animator = ValueAnimator.ofFloat(start, state.startDismissTranslation);
        state.animator = animator;
        animator.setDuration(STACK_DISMISS_CANCEL_ANIM_MS);
        animator.setInterpolator(new OvershootInterpolator(0.85f));
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslation = value;
            LauncherRecentsPerf.hit("animationFrame:dismissCancel", state.recentsView);
            applyStackDismissProgress(state, value);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                logStackFlow("dismiss:animateCancel:end",
                        state.recentsView, null, taskDetails(state.recentsView, state.taskView));
                resetStackDismissVisuals(state);
            }
        });
        animator.start();
    }

    private static void applyStackDismissProgress(
            StackDismissGestureState state,
            float dismissTranslation) {
        setStackDismissTranslation(state, dismissTranslation);
        applyStackDismissReflowProgress(
                state,
                resolveStackDismissReflowProgress(state, dismissTranslation));
    }

    private static void applyStackDismissSuccessProgress(
            StackDismissGestureState state,
            float dismissTranslation,
            float reflowProgress) {
        setStackDismissTranslation(state, dismissTranslation);
        applyStackDismissReflowProgress(state, LauncherRecentsLayoutEngine.clamp(
                reflowProgress,
                0f,
                1f));
    }

    private static void applyStackDismissReflowProgress(
            StackDismissGestureState state,
            float progress) {
        float clampedProgress = LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f);
        for (int i = 0; i < state.siblingMoves.size(); i++) {
            StackDismissSiblingMove move = state.siblingMoves.get(i);
            STACK_DISMISS_LAYOUT_OFFSETS.put(
                    move.taskView,
                    move.targetOffsetPx * clampedProgress);
            float visibleOffset = LauncherRecentsLayoutEngine.lerp(
                    move.startVisibleOffsetPx,
                    move.targetVisibleOffsetPx,
                    clampedProgress);
            STACK_DISMISS_VISIBLE_OFFSETS.put(move.taskView, visibleOffset);
            applyStackDismissSiblingVisibleOffset(state, move, visibleOffset);
        }
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        state.recentsView.invalidate();
    }

    private static void applyStackDismissSiblingVisibleOffset(
            StackDismissGestureState state,
            StackDismissSiblingMove move,
            float visibleOffset) {
        if (state == null
                || move == null
                || !isStackDismissReflowTaskCandidate(state.recentsView, move.taskView)) {
            return;
        }
        boolean primaryScrollHorizontal = !state.secondaryDismissHorizontal;
        float taskOffsetPrimary = visibleOffset
                - move.startRawOffsetPx
                - move.startDismissTranslationPrimaryPx;
        if (primaryScrollHorizontal) {
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(
                    move.taskView,
                    taskOffsetPrimary - move.startHorizontalOffsetX);
        } else {
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(
                    move.taskView,
                    taskOffsetPrimary);
        }
    }

    private static void preheatStackDismissVisibleTaskData(View recentsView) {
        forceEnsureStackVisibleTaskData(recentsView, 15);
    }

    private static float resolveStackDismissReflowProgress(
            StackDismissGestureState state,
            float dismissTranslation) {
        float moved = state.dismissDirectionSign
                * (dismissTranslation - state.startDismissTranslation);
        return LauncherRecentsLayoutEngine.clamp(
                moved / Math.max(1f, resolveStackDismissDistance(state)),
                0f,
                1f);
    }

    private static void prepareStackDismissSiblingMoves(StackDismissGestureState state) {
        state.siblingMoves.clear();
        clearStackDismissLayoutOffsets();
        int dismissedIndex = findTaskViewIndex(state.recentsView, state.taskView);
        if (dismissedIndex < 0) {
            return;
        }
        float dismissedRawOffset = resolveStackDismissRawOffset(
                state.recentsView,
                dismissedIndex);
        int targetPage = resolveSilentNativeDismissAnchorPage(state.recentsView, state.taskView);
        int targetScrollX = resolveStackDismissScrollForPage(state.recentsView, targetPage);
        int taskViewCount =
                LauncherRecentsCompat.invokeInt(state.recentsView, "getTaskViewCount", 0);
        float[] visibleOffsets = resolveStackDismissCurrentVisibleOffsets(
                state.recentsView,
                taskViewCount);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(state.recentsView, i);
            if (!isStackDismissReflowTaskCandidate(state.recentsView, taskView)
                    || taskView == state.taskView) {
                continue;
            }
            int projectedIndex = i > dismissedIndex ? i - 1 : i;
            float currentRawOffset = resolveStackDismissRawOffset(state.recentsView, i);
            float targetRawOffset = resolveStackDismissRawOffsetAtScroll(
                    state.recentsView,
                    projectedIndex,
                    targetScrollX);
            if (currentRawOffset < dismissedRawOffset && targetRawOffset > dismissedRawOffset) {
                targetRawOffset = dismissedRawOffset - 1f;
            } else if (currentRawOffset > dismissedRawOffset
                    && targetRawOffset < dismissedRawOffset) {
                targetRawOffset = dismissedRawOffset + 1f;
            }
            float targetOffsetPx = targetRawOffset - currentRawOffset;
            if (Math.abs(targetOffsetPx) <= 0.5f) {
                targetOffsetPx = resolveStackDismissBoundarySiblingOffset(
                        state,
                        i,
                        dismissedIndex,
                        taskViewCount,
                        currentRawOffset,
                        dismissedRawOffset,
                        taskView);
            }
            if (Math.abs(targetOffsetPx) > 0.5f) {
                int targetVisibleIndex = resolveStackDismissBoundaryTargetVisibleIndex(
                        i,
                        dismissedIndex,
                        taskViewCount);
                state.siblingMoves.add(new StackDismissSiblingMove(
                        taskView,
                        targetOffsetPx,
                        currentRawOffset,
                        LauncherRecentsCompat.readFloatField(
                                taskView,
                                isPrimaryScrollHorizontal(state.recentsView)
                                        ? "dismissTranslationX"
                                        : "dismissTranslationY",
                                0f),
                        LauncherRecentsCompat.readFloatField(
                                taskView,
                                "horizontalOffsetTranslationX",
                                0f),
                        visibleOffsets[i],
                        targetVisibleIndex >= 0
                                ? visibleOffsets[targetVisibleIndex]
                                : LauncherRecentsLayoutEngine.resolveStackDismissTargetVisibleOffset(
                                        state.recentsView,
                                        taskView,
                                        targetRawOffset)));
            }
        }
    }

    private static float[] resolveStackDismissCurrentVisibleOffsets(
            View recentsView,
            int taskViewCount) {
        float[] offsets = new float[taskViewCount];
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            offsets[i] = LauncherRecentsLayoutEngine.resolveStackTaskCurrentVisibleOffset(
                    recentsView,
                    taskView,
                    i);
        }
        return offsets;
    }

    private static int resolveStackDismissBoundaryTargetVisibleIndex(
            int taskIndex,
            int dismissedIndex,
            int taskViewCount) {
        if (dismissedIndex == 0 && taskIndex > dismissedIndex) {
            return taskIndex - 1;
        }
        if (dismissedIndex == taskViewCount - 1 && taskIndex < dismissedIndex) {
            return taskIndex + 1;
        }
        return -1;
    }

    private static float resolveStackDismissRawOffset(View recentsView, int taskIndex) {
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getUnclampedScrollOffset",
                LauncherRecentsCompat.INT_ARG,
                LauncherRecentsCompat.invokeInt(
                        recentsView,
                        "getScrollOffset",
                        LauncherRecentsCompat.INT_ARG,
                        0,
                        taskIndex),
                taskIndex);
    }

    private static float resolveStackDismissRawOffsetAtScroll(
            View recentsView,
            int taskIndex,
            int primaryScroll) {
        return resolveStackDismissRawOffset(recentsView, taskIndex)
                + resolvePrimaryScroll(recentsView)
                - primaryScroll;
    }

    private static float resolveStackDismissBoundarySiblingOffset(
            StackDismissGestureState state,
            int taskIndex,
            int dismissedIndex,
            int taskViewCount,
            float currentRawOffset,
            float dismissedRawOffset,
            View taskView) {
        boolean rightSideOfFirst = dismissedIndex == 0 && taskIndex > dismissedIndex;
        boolean leftSideOfLast =
                dismissedIndex == taskViewCount - 1 && taskIndex < dismissedIndex;
        if (!rightSideOfFirst && !leftSideOfLast) {
            return 0f;
        }
        boolean adjacent = Math.abs(taskIndex - dismissedIndex) == 1;
        if (adjacent) {
            float offsetToDismissed = dismissedRawOffset - currentRawOffset;
            if (Math.abs(offsetToDismissed) > 0.5f) {
                return offsetToDismissed;
            }
        }
        float pageSpan = resolveStackDismissPageSpan(state.recentsView, taskView);
        return rightSideOfFirst ? -pageSpan : pageSpan;
    }

    private static float resolveStackDismissPageSpan(View recentsView, View taskView) {
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float taskSize = taskView != null
                ? resolvePrimarySize(taskView, primaryScrollHorizontal)
                : 0f;
        if (taskSize <= 0f) {
            taskSize = resolvePrimarySize(recentsView, primaryScrollHorizontal);
        }
        return Math.max(
                1f,
                taskSize + LauncherRecentsCompat.readIntField(recentsView, "mPageSpacing", 0));
    }

    private static int findTaskViewIndex(View recentsView, View targetTaskView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            if (LauncherRecentsCompat.getTaskViewAt(recentsView, i) == targetTaskView) {
                return i;
            }
        }
        return -1;
    }

    private static void resetStackDismissVisuals(StackDismissGestureState state) {
        state.cancelAnimator();
        clearStackDismissLayoutOffsets();
        setStackDismissTranslation(state, state.startDismissTranslation);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        LauncherRecentsTaskVisuals.setTranslationZ(state.taskView, state.originalTranslationZ);
        LauncherRecentsLayoutEngine.requestStackLayout(
                state.recentsView,
                "dismissReset",
                false,
                false);
    }

    private static void clearStackDismissLayoutOffsets() {
        STACK_DISMISS_LAYOUT_OFFSETS.clear();
        STACK_DISMISS_VISIBLE_OFFSETS.clear();
    }

    private static void clearStackDismissGesture(View recentsView, boolean resetVisuals) {
        StackDismissGestureState state = STACK_DISMISS_GESTURES.remove(recentsView);
        if (state == null) {
            return;
        }
        requestParentDisallowIntercept(recentsView, false);
        state.recycleVelocityTracker();
        if (resetVisuals) {
            resetStackDismissVisuals(state);
        } else {
            state.cancelAnimator();
        }
    }

    private static float resolveStackDismissThreshold(StackDismissGestureState state) {
        float fallback = FlymeStatusBarSizer.dp(state.recentsView.getContext(), 120);
        float secondarySize = resolvePrimarySize(
                state.taskView,
                state.secondaryDismissHorizontal);
        if (secondarySize <= 0f) {
            secondarySize = fallback;
        }
        return LauncherRecentsLayoutEngine.clamp(
                secondarySize * 0.24f,
                FlymeStatusBarSizer.dp(state.recentsView.getContext(), 96),
                FlymeStatusBarSizer.dp(state.recentsView.getContext(), 220));
    }

    private static float resolveStackDismissDistance(StackDismissGestureState state) {
        float taskStart = Math.max(
                0f,
                (state.secondaryDismissHorizontal ? state.taskView.getX() : state.taskView.getY())
                        - (state.secondaryDismissHorizontal
                        ? state.recentsView.getScrollX()
                        : state.recentsView.getScrollY()));
        float taskSize = Math.max(
                1f,
                state.secondaryDismissHorizontal
                        ? state.taskView.getWidth()
                        : state.taskView.getHeight());
        float recentsSize = Math.max(
                1f,
                state.secondaryDismissHorizontal
                        ? state.recentsView.getWidth()
                        : state.recentsView.getHeight());
        float margin = FlymeStatusBarSizer.dp(state.recentsView.getContext(), 48);
        float distance = state.dismissDirectionSign < 0f
                ? taskStart + taskSize + margin
                : recentsSize - taskStart + margin;
        return Math.max(distance, recentsSize * 0.72f)
                - Math.min(0f, state.dismissDirectionSign * state.currentDismissTranslation);
    }

    private static void setStackDismissTranslation(StackDismissGestureState state, float value) {
        if (state.secondaryDismissHorizontal) {
            setStackDismissTranslationX(state.taskView, value);
        } else {
            setStackDismissTranslationY(state.taskView, value);
        }
    }

    private static void setStackDismissTranslationX(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setDismissTranslationX",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    private static void setStackDismissTranslationY(View taskView, float value) {
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "setDismissTranslationY",
                LauncherRecentsCompat.FLOAT_ARG,
                value);
    }

    private static void clearNativeDismissTransforms(View recentsView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            resetNativeDismissTranslationX(taskView);
            LauncherRecentsCompat.invokeCompat(
                    taskView,
                    "setDismissTranslationY",
                    LauncherRecentsCompat.FLOAT_ARG,
                    0f);
            resetNativeDismissScale(taskView);
        }
    }

    private static void resetNativeDismissTranslationX(View taskView) {
        LauncherRecentsCompat.writeField(taskView, "dismissTranslationX", 0f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "applyTranslationX",
                LauncherRecentsCompat.NO_ARGS);
    }

    private static void resetNativeDismissScale(View taskView) {
        LauncherRecentsCompat.writeField(taskView, "dismissScale", 1f);
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "applyScale",
                LauncherRecentsCompat.NO_ARGS);
    }

    private static boolean invokeNativeDismissTaskView(
            View recentsView,
            View taskView) {
        logStackFlow("dismiss:native:start",
                recentsView, null, taskDetails(recentsView, taskView));
        Class<?> taskViewClass = resolveTaskViewBaseClass(taskView);
        if (!(recentsView instanceof ViewGroup) || taskViewClass == null) {
            logStackFlow("dismiss:native:missingClass",
                    recentsView, null, taskDetails(recentsView, taskView));
            return false;
        }
        int targetPage = resolveSilentNativeDismissAnchorPage(recentsView, taskView);
        rememberSilentNativeDismissAnchor(recentsView, targetPage);
        if (LauncherRecentsCompat.invokeBoolean(taskView, "isRunningTask", false)
                && LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "finishRecentsAnimation",
                new Class<?>[]{boolean.class, boolean.class, Runnable.class},
                true,
                false,
                (Runnable) () -> commitManualNativeDismiss(
                        recentsView,
                        taskView,
                        taskViewClass,
                        targetPage))) {
            return true;
        }
        return commitManualNativeDismiss(
                recentsView,
                taskView,
                taskViewClass,
                targetPage);
    }

    private static boolean commitManualNativeDismiss(
            View recentsView,
            View taskView,
            Class<?> taskViewClass,
            int targetPage) {
        logStackFlow("dismiss:native:commit",
                recentsView,
                null,
                "targetPage=" + targetPage + " " + taskDetails(recentsView, taskView));
        setStackDismissPostRemoveAnimationActive(recentsView, true);
        boolean removedTask = LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "removeTaskInternal",
                new Class<?>[]{taskViewClass},
                taskView);
        if (!removedTask) {
            logStackFlow("dismiss:native:removeFailed",
                    recentsView, null, taskDetails(recentsView, taskView));
            finishSilentNativeDismiss(recentsView);
            return false;
        }
        removeDismissedTaskFromGridState(recentsView, taskView);
        ((ViewGroup) recentsView).removeViewInLayout(taskView);
        clearStackDismissLayoutOffsets();
        LauncherRecentsCompat.writeField(recentsView, "mPendingAnimation", null);
        clearTaskViewsDismissPrimaryTranslations(recentsView);
        clearNativeDismissTransforms(recentsView);
        applyStackDismissFinalLayout(recentsView);
        recentsView.invalidate();
        scheduleStackDismissPostRemoveCallbacks(recentsView);
        return true;
    }

    private static void scheduleStackDismissPostRemoveCallbacks(View recentsView) {
        if (recentsView == null) {
            return;
        }
        recentsView.postOnAnimation(() -> {
            LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "dispatchScrollChanged",
                    LauncherRecentsCompat.NO_ARGS);
            LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "updateActionsViewFocusedScroll",
                    LauncherRecentsCompat.NO_ARGS);
            LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "updateCurrentTaskActionsVisibility",
                    LauncherRecentsCompat.NO_ARGS);
            boolean dispatchedDismissEnd = LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "onDismissAnimationEnds",
                    LauncherRecentsCompat.NO_ARGS);
            if (!dispatchedDismissEnd) {
                scheduleSilentNativeDismissFinish(recentsView);
            }
            recentsView.invalidate();
        });
    }

    private static void rememberSilentNativeDismissAnchor(
            View recentsView,
            int targetPage) {
        if (recentsView == null) {
            return;
        }
        settleStackDismissLayoutState(recentsView);
        SILENT_NATIVE_DISMISS_RECENTS.put(recentsView, Boolean.TRUE);
        SILENT_NATIVE_DISMISS_ANCHORS.put(recentsView, new SilentNativeDismissAnchor(targetPage));
    }

    private static int resolveSilentNativeDismissAnchorPage(View recentsView, View dismissedTaskView) {
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (pageCount <= 1) {
            return 0;
        }
        int nearestPage = resolveNearestStackDismissPageForScroll(recentsView, pageCount);
        int dismissedIndex = findTaskViewIndex(recentsView, dismissedTaskView);
        if (dismissedIndex >= 0 && dismissedIndex < nearestPage) {
            nearestPage--;
        }
        return Math.max(0, Math.min(nearestPage, pageCount - 2));
    }

    private static void settleStackDismissLayoutState(View recentsView) {
        LauncherRecentsAttachController.endAppToRecentsEntrySessionWithoutLayout(recentsView);
    }

    private static void scheduleSilentNativeDismissFinish(View recentsView) {
        if (recentsView == null) {
            return;
        }
        logStackFlow("dismiss:native:scheduleFinish", recentsView, null, null);
        recentsView.postOnAnimation(() -> {
            if (isSilentNativeDismissActive(recentsView)) {
                clearNativeDismissTransforms(recentsView);
                clearStackDismissLayoutOffsets();
                recentsView.invalidate();
            }
            recentsView.postOnAnimation(() -> {
                finishSilentNativeDismiss(recentsView);
            });
        });
    }

    private static void finishSilentNativeDismiss(View recentsView) {
        if (!isSilentNativeDismissActive(recentsView)) {
            logStackFlow("dismiss:native:finishInactive", recentsView, null, null);
            SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
            SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
            setStackDismissPostRemoveAnimationActive(recentsView, false);
            LauncherRecentsLayoutEngine.requestStackLayout(
                    recentsView,
                    "nativeDismissFinishInactive",
                    false,
                    false);
            if (recentsView != null) {
                recentsView.invalidate();
            }
            return;
        }
        logStackFlow("dismiss:native:finish", recentsView, null, null);
        clearNativeDismissTransforms(recentsView);
        clearStackDismissLayoutOffsets();
        SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
        SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
        setStackDismissPostRemoveAnimationActive(recentsView, false);
        recentsView.invalidate();
    }

    private static boolean shouldConsumeStackDismissPostRemoveTouch(
            View recentsView,
            MotionEvent motionEvent) {
        if (motionEvent != null
                && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
            logStackFlow("dismiss:postRemove:newDown", recentsView, motionEvent, null);
            releasePagedTouchForStackDismiss(recentsView);
            finishSilentNativeDismiss(recentsView);
            return false;
        }
        releasePagedTouchForStackDismiss(recentsView);
        if (motionEvent != null
                && (motionEvent.getActionMasked() == MotionEvent.ACTION_UP
                || motionEvent.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            logStackFlow("dismiss:postRemove:end", recentsView, motionEvent, null);
            finishSilentNativeDismiss(recentsView);
        }
        return true;
    }

    private static void setStackDismissPostRemoveAnimationActive(
            View recentsView,
            boolean active) {
        if (recentsView == null) {
            return;
        }
        if (active) {
            STACK_DISMISS_POST_REMOVE_RECENTS.put(recentsView, Boolean.TRUE);
        } else {
            STACK_DISMISS_POST_REMOVE_RECENTS.remove(recentsView);
        }
    }

    private static void applyStackDismissFinalLayout(View recentsView) {
        Boolean previousLayoutBypass = STACK_DISMISS_LAYOUT_FREEZE_BYPASS.get();
        Boolean previousScrollBypass = STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS.get();
        STACK_DISMISS_LAYOUT_FREEZE_BYPASS.set(Boolean.TRUE);
        STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS.set(Boolean.TRUE);
        try {
            runStackDismissPendingLayout(recentsView);
            syncStackDismissSnappedPage(recentsView);
            LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
            LauncherRecentsLayoutEngine.applyStackLayout(
                    recentsView,
                    false,
                    "dismissFinalLayout",
                    false);
            forceEnsureStackVisibleTaskData(recentsView, 15);
        } finally {
            if (previousLayoutBypass == null) {
                STACK_DISMISS_LAYOUT_FREEZE_BYPASS.remove();
            } else {
                STACK_DISMISS_LAYOUT_FREEZE_BYPASS.set(previousLayoutBypass);
            }
            if (previousScrollBypass == null) {
                STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS.remove();
            } else {
                STACK_DISMISS_SCROLL_SUPPRESSION_BYPASS.set(previousScrollBypass);
            }
        }
    }

    private static void runStackDismissPendingLayout(View recentsView) {
        if (recentsView == null
                || recentsView.getWidth() <= 0
                || recentsView.getHeight() <= 0) {
            return;
        }
        LauncherRecentsCompat.setBooleanField(recentsView, "mPendingLayoutRequested", true);
        LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "onLayout",
                new Class<?>[]{
                        boolean.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                false,
                recentsView.getLeft(),
                recentsView.getTop(),
                recentsView.getRight(),
                recentsView.getBottom());
    }

    private static void syncStackDismissSnappedPage(View recentsView) {
        if (recentsView == null) {
            return;
        }
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (pageCount <= 0) {
            return;
        }
        SilentNativeDismissAnchor anchor = SILENT_NATIVE_DISMISS_ANCHORS.get(recentsView);
        int page = anchor != null
                ? Math.min(anchor.targetPage, pageCount - 1)
                : resolveNearestStackDismissPageForScroll(recentsView, pageCount);
        setStackDismissCurrentPageSnapped(recentsView, page);
    }

    private static void removeDismissedTaskFromGridState(View recentsView, View taskView) {
        int taskViewId = LauncherRecentsCompat.invokeInt(taskView, "getTaskViewId", -1);
        if (taskViewId == -1) {
            return;
        }
        Object topRowIdSet = LauncherRecentsCompat.getFieldCompat(recentsView, "mTopRowIdSet");
        LauncherRecentsCompat.invokeCompat(
                topRowIdSet,
                "remove",
                LauncherRecentsCompat.INT_ARG,
                taskViewId);
    }

    private static void clearTaskViewsDismissPrimaryTranslations(View recentsView) {
        Object value =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mTaskViewsDismissPrimaryTranslations");
        if (value instanceof Map) {
            ((Map<?, ?>) value).clear();
        }
    }

    private static void setStackDismissCurrentPageKeepingScroll(View recentsView, int page) {
        int pageScroll = resolveStackDismissScrollForPage(recentsView, page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentScrollOverPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", page);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mCurrentPageScrollDiff",
                resolvePrimaryScroll(recentsView) - pageScroll);
    }

    private static void setStackDismissCurrentPageSnapped(View recentsView, int page) {
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentScrollOverPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPageScrollDiff", 0);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "updateCurrentPageScroll",
                LauncherRecentsCompat.NO_ARGS);
    }

    private static int resolveStackDismissScrollForPage(View recentsView, int page) {
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                resolvePrimaryScroll(recentsView),
                page);
    }

    private static int resolveNearestStackDismissPageForScroll(View recentsView, int pageCount) {
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int nearestPage = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < pageCount; i++) {
            int distance = Math.abs(resolveStackDismissScrollForPage(recentsView, i) - primaryScroll);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPage = i;
            }
        }
        return nearestPage;
    }

    private static Class<?> resolveTaskViewBaseClass(View taskView) {
        if (taskView == null) {
            return null;
        }
        Class<?> clazz = taskView.getClass();
        while (clazz != null) {
            if (LauncherRecentsCompat.TASK_VIEW_CLASS.equals(clazz.getName())) {
                return clazz;
            }
            clazz = clazz.getSuperclass();
        }
        try {
            return Class.forName(
                    LauncherRecentsCompat.TASK_VIEW_CLASS,
                    false,
                    taskView.getClass().getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void requestParentDisallowIntercept(View view, boolean disallow) {
        ViewParent parent = view != null ? view.getParent() : null;
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static final class StackDismissGestureState {
        final View recentsView;
        final View taskView;
        final ArrayList<StackDismissSiblingMove> siblingMoves = new ArrayList<>();
        final float downRawX;
        final float downRawY;
        final boolean secondaryDismissHorizontal;
        final float dismissDirectionSign;
        final float startDismissTranslation;
        final float originalStableAlpha;
        final float originalTranslationZ;
        VelocityTracker velocityTracker;
        ValueAnimator animator;
        boolean dragging;
        float currentDismissTranslation;

        StackDismissGestureState(View recentsView, View taskView, MotionEvent motionEvent) {
            this.recentsView = recentsView;
            this.taskView = taskView;
            this.downRawX = motionEvent.getRawX();
            this.downRawY = motionEvent.getRawY();
            this.secondaryDismissHorizontal = !isPrimaryScrollHorizontal(recentsView);
            this.dismissDirectionSign = resolveStackDismissDirectionSign(recentsView);
            this.startDismissTranslation = LauncherRecentsCompat.readFloatField(
                    taskView,
                    this.secondaryDismissHorizontal ? "dismissTranslationX" : "dismissTranslationY",
                    0f);
            this.currentDismissTranslation = this.startDismissTranslation;
            this.originalStableAlpha = LauncherRecentsTaskVisuals.readStableAlpha(taskView);
            this.originalTranslationZ = taskView.getTranslationZ();
            this.velocityTracker = VelocityTracker.obtain();
            this.velocityTracker.addMovement(motionEvent);
        }

        void addMovement(MotionEvent motionEvent) {
            if (velocityTracker != null && motionEvent != null) {
                velocityTracker.addMovement(motionEvent);
            }
        }

        float computeSecondaryVelocity() {
            if (velocityTracker == null) {
                return 0f;
            }
            velocityTracker.computeCurrentVelocity(1000);
            return secondaryDismissHorizontal
                    ? velocityTracker.getXVelocity()
                    : velocityTracker.getYVelocity();
        }

        void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }

        void cancelAnimator() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }
    }

    private static final class StackDismissSiblingMove {
        final View taskView;
        final float targetOffsetPx;
        final float startRawOffsetPx;
        final float startDismissTranslationPrimaryPx;
        final float startHorizontalOffsetX;
        final float startVisibleOffsetPx;
        final float targetVisibleOffsetPx;

        StackDismissSiblingMove(
                View taskView,
                float targetOffsetPx,
                float startRawOffsetPx,
                float startDismissTranslationPrimaryPx,
                float startHorizontalOffsetX,
                float startVisibleOffsetPx,
                float targetVisibleOffsetPx) {
            this.taskView = taskView;
            this.targetOffsetPx = targetOffsetPx;
            this.startRawOffsetPx = startRawOffsetPx;
            this.startDismissTranslationPrimaryPx = startDismissTranslationPrimaryPx;
            this.startHorizontalOffsetX = startHorizontalOffsetX;
            this.startVisibleOffsetPx = startVisibleOffsetPx;
            this.targetVisibleOffsetPx = targetVisibleOffsetPx;
        }
    }

    private static final class SilentNativeDismissAnchor {
        final int targetPage;

        SilentNativeDismissAnchor(int targetPage) {
            this.targetPage = targetPage;
        }
    }

    private static boolean shouldExposeStackTaskForDismissVisibility(
            View recentsView,
            View taskView) {
        return shouldExposeStackTaskForDismissVisibility(recentsView, taskView, false);
    }

    private static boolean shouldExposeStackTaskForDismissVisibility(
            View recentsView,
            View taskView,
            boolean knownChild) {
        if (recentsView == null
                || taskView == null
                || LauncherRecentsCompat.isDesktopTask(taskView)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        if (!knownChild
                && (!(recentsView instanceof ViewGroup)
                || ((ViewGroup) recentsView).indexOfChild(taskView) < 0)) {
            return false;
        }
        if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)) {
            LauncherRecentsState.BlankTapHomeExitTaskState state =
                    LauncherRecentsState.BLANK_TAP_HOME_EXIT_TASK_STATES.get(taskView);
            return state != null
                    && taskView.getVisibility() == View.VISIBLE
                    && taskView.getWidth() > 0
                    && taskView.getHeight() > 0;
        }
        return taskView.getVisibility() == View.VISIBLE
                && (readStackTaskDataAlpha(taskView) > STACK_LEFT_RELEASE_ALPHA_THRESHOLD
                || hasStackDismissLayoutOffset(taskView))
                && taskView.getWidth() > 0
                && taskView.getHeight() > 0
                && isStackTaskWithinVisibleDataBounds(recentsView, taskView);
    }

    private static boolean isStackTaskDataVisible(
            View recentsView,
            View taskView,
            boolean knownChild) {
        if (recentsView == null
                || taskView == null
                || LauncherRecentsCompat.isDesktopTask(taskView)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || taskView.getVisibility() != View.VISIBLE
                || taskView.getWidth() <= 0
                || taskView.getHeight() <= 0
                || !(recentsView instanceof ViewGroup)) {
            return false;
        }
        ViewGroup parent = (ViewGroup) recentsView;
        int taskIndex = parent.indexOfChild(taskView);
        if (!knownChild && taskIndex < 0) {
            return false;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (taskIndex < 0 || taskIndex >= taskViewCount) {
            return false;
        }
        int anchorIndex = resolveStackVisibleTaskDataAnchorIndex(recentsView, taskViewCount);
        int radius = resolveStackVisibleTaskDataRadius(recentsView);
        return isStackVisibleTaskDataIndexVisible(
                recentsView,
                taskIndex,
                taskViewCount,
                anchorIndex,
                radius)
                && isStackTaskWithinVisibleDataBounds(recentsView, taskView);
    }

    private static float readStackTaskDataAlpha(View taskView) {
        return Math.min(taskView.getAlpha(), LauncherRecentsTaskVisuals.readStableAlpha(taskView));
    }

    private static boolean isStackTaskWithinVisibleDataBounds(View recentsView, View taskView) {
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float taskStart = (primaryScrollHorizontal ? taskView.getX() : taskView.getY())
                + readStackDismissLayoutOffset(taskView)
                - resolvePrimaryScroll(recentsView);
        float taskSize = resolvePrimarySize(taskView, primaryScrollHorizontal);
        float viewportStart = -taskSize;
        float viewportEnd = resolvePrimarySize(recentsView, primaryScrollHorizontal)
                * (1f + STACK_VISIBLE_DATA_RIGHT_MARGIN_RATIO);
        return hasStackDismissLayoutOffset(taskView)
                || (taskStart + taskSize > viewportStart && taskStart < viewportEnd);
    }

    private static boolean shouldSuppressStackTaskDataUnload(View taskView, int changes) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)) {
            return shouldExposeStackTaskForDismissVisibility(recentsView, taskView);
        }
        if ((changes & 2) != 2) {
            return false;
        }
        return shouldExposeStackTaskForDismissVisibility(recentsView, taskView);
    }

    private static boolean isTransitionAnimationActive(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        return LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isOverviewStateStackAnimationActive(recentsView)
                || LauncherRecentsState.isOverviewPeekStockAnimationActive(recentsView)
                || LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseHandoffPending(recentsView);
    }

    static void ensureStackVisibleTaskDataIfNeeded(View recentsView, int changes) {
        if (recentsView == null || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return;
        }
        if (isTransitionAnimationActive(recentsView)) {
            logStackFlow("visibleData:ensureIfNeeded:skipTransition",
                    recentsView, null, "changes=" + changes);
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        if (recentsView.getWidth() <= 0 || recentsView.getHeight() <= 0) {
            logStackFlow("visibleData:ensureIfNeeded:forceNoSize",
                    recentsView, null, "changes=" + changes);
            forceEnsureStackVisibleTaskData(recentsView, changes);
            return;
        }
        int bucket = resolveStackVisibleTaskDataBucket(recentsView);
        StackVisibleTaskDataSyncState state = findStackVisibleTaskDataSyncState(recentsView);
        if (shouldSkipStackVisibleTaskDataSync(
                recentsView,
                state,
                taskViewCount,
                currentPage,
                bucket,
                false)) {
            logStackFlow("visibleData:ensureIfNeeded:skipDuplicate",
                    recentsView,
                    null,
                    "changes=" + changes
                            + " bucket=" + bucket
                            + " taskCount=" + taskViewCount
                            + " currentPage=" + currentPage);
            markStackVisibleTaskDataTouched(state, false);
            return;
        }
        logStackFlow("visibleData:ensureIfNeeded:force",
                recentsView,
                null,
                "changes=" + changes
                        + " bucket=" + bucket
                        + " taskCount=" + taskViewCount
                        + " currentPage=" + currentPage);
        forceEnsureStackVisibleTaskData(recentsView, changes);
    }

    static void forceEnsureStackVisibleTaskData(View recentsView, int changes) {
        forceEnsureStackVisibleTaskData(recentsView, changes, false);
    }

    static void forceEnsureStackVisibleTaskData(View recentsView, int changes, boolean forceRelease) {
        forceEnsureStackVisibleTaskData(recentsView, changes, forceRelease, true);
    }

    private static void forceEnsureStackVisibleTaskData(
            View recentsView,
            int changes,
            boolean forceRelease,
            boolean allowDelay) {
        if (recentsView == null) {
            return;
        }
        if (isTransitionAnimationActive(recentsView) && !forceRelease) {
            logStackFlow("visibleData:force:skipTransition",
                    recentsView, null, "changes=" + changes);
            return;
        }
        logStackFlow("visibleData:force:start",
                recentsView, null, "changes=" + changes + " forceRelease=" + forceRelease);
        long perfStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            StackVisibleTaskDataSyncState state = ensureStackVisibleTaskDataSyncState(recentsView);
            int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
            int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
            int scrollBucket = resolveStackVisibleTaskDataBucket(recentsView);
            if (shouldSkipStackVisibleTaskDataSync(
                    recentsView,
                    state,
                    taskViewCount,
                    currentPage,
                    scrollBucket,
                    forceRelease)) {
                logStackFlow("visibleData:force:skipDuplicate",
                        recentsView,
                        null,
                        "changes=" + changes
                                + " bucket=" + scrollBucket
                                + " taskCount=" + taskViewCount
                                + " currentPage=" + currentPage
                                + " forceRelease=" + forceRelease);
                markStackVisibleTaskDataTouched(state, forceRelease);
                return;
            }
            if (shouldDelayStackVisibleTaskDataSync(recentsView, forceRelease, allowDelay)) {
                logStackFlow("visibleData:force:delayGestureBackground",
                        recentsView,
                        null,
                        "changes=" + changes
                                + " bucket=" + scrollBucket
                                + " taskCount=" + taskViewCount
                                + " currentPage=" + currentPage);
                scheduleDeferredStackVisibleTaskDataSync(recentsView, state, changes);
                return;
            }
            state.taskViewCount = taskViewCount;
            state.currentPage = currentPage;
            state.scrollBucket = scrollBucket;
            logStackFlow("visibleData:force:run",
                    recentsView,
                    null,
                    "changes=" + changes
                            + " bucket=" + scrollBucket
                            + " taskCount=" + taskViewCount
                            + " currentPage=" + currentPage
                            + " forceRelease=" + forceRelease);
            ensureStackVisibleTaskData(recentsView, changes, forceRelease);
        } finally {
            LauncherRecentsPerf.end("visibleTaskDataSync:force", perfStartNs);
        }
    }

    private static boolean shouldDelayStackVisibleTaskDataSync(
            View recentsView,
            boolean forceRelease,
            boolean allowDelay) {
        return allowDelay
                && !forceRelease
                && !isLastStackVisibleTaskIdsEmpty(recentsView)
                && isGestureRecentsBackground(recentsView);
    }

    private static void scheduleDeferredStackVisibleTaskDataSync(
            final View recentsView,
            final StackVisibleTaskDataSyncState state,
            int changes) {
        state.pendingSyncChanges |= changes;
        if (state.pendingSyncRunnable != null) {
            logStackFlow("visibleData:force:delayAlreadyPending",
                    recentsView,
                    null,
                    "changes=" + state.pendingSyncChanges);
            return;
        }
        state.pendingSyncRunnable = new Runnable() {
            @Override
            public void run() {
                state.pendingSyncRunnable = null;
                int syncChanges = state.pendingSyncChanges;
                state.pendingSyncChanges = 0;
                forceEnsureStackVisibleTaskData(recentsView, syncChanges, false, false);
            }
        };
        recentsView.postDelayed(state.pendingSyncRunnable, STACK_VISIBLE_DATA_SYNC_RETRY_DELAY_MS);
    }

    private static boolean isGestureRecentsBackground(View recentsView) {
        return LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                && isLauncherStateBackground(recentsView);
    }

    private static boolean isLauncherStateBackground(View recentsView) {
        Object stateManager = LauncherRecentsCompat.invokeCompat(recentsView, "getStateManager");
        Object state = LauncherRecentsCompat.invokeCompat(stateManager, "getState");
        return state != null && "Background".equals(String.valueOf(state));
    }

    private static StackVisibleTaskDataSyncState findStackVisibleTaskDataSyncState(
            View recentsView) {
        return STACK_VISIBLE_TASK_DATA_SYNC_STATES.get(recentsView);
    }

    private static StackVisibleTaskDataSyncState ensureStackVisibleTaskDataSyncState(
            View recentsView) {
        StackVisibleTaskDataSyncState state = STACK_VISIBLE_TASK_DATA_SYNC_STATES.get(recentsView);
        if (state == null) {
            state = new StackVisibleTaskDataSyncState();
            STACK_VISIBLE_TASK_DATA_SYNC_STATES.put(recentsView, state);
        }
        return state;
    }

    private static boolean shouldSkipStackVisibleTaskDataSync(
            View recentsView,
            StackVisibleTaskDataSyncState state,
            int taskViewCount,
            int currentPage,
            int scrollBucket,
            boolean forceRelease) {
        if (state == null || forceRelease || isLastStackVisibleTaskIdsEmpty(recentsView)) {
            return false;
        }
        if (hasActiveStackDismissLayoutOffsets()) {
            return false;
        }
        return state.taskViewCount == taskViewCount
                && state.currentPage == currentPage
                && state.scrollBucket == scrollBucket;
    }

    private static void markStackVisibleTaskDataTouched(
            StackVisibleTaskDataSyncState state,
            boolean forceRelease) {
        if (state != null && !forceRelease) {
            state.lastLoadUptimeMs = SystemClock.uptimeMillis();
        }
    }

    private static int resolveStackVisibleTaskDataBucket(View recentsView) {
        if (recentsView == null) {
            return Integer.MIN_VALUE;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        int primarySize = Math.round(resolvePrimarySize(recentsView, primaryScrollHorizontal));
        if (primarySize <= 0) {
            return Integer.MIN_VALUE;
        }
        return resolvePrimaryScroll(recentsView)
                / Math.max(1, primarySize / STACK_VISIBLE_DATA_SCROLL_BUCKET_DIVISOR);
    }

    private static boolean isLastStackVisibleTaskIdsEmpty(View recentsView) {
        ArrayList<Integer> lastVisibleTaskIds = STACK_VISIBLE_TASK_IDS.get(recentsView);
        return lastVisibleTaskIds == null || lastVisibleTaskIds.isEmpty();
    }

    static void ensureStackVisibleTaskData(View recentsView, int changes) {
        ensureStackVisibleTaskData(recentsView, changes, false);
    }

    static void ensureStackVisibleTaskData(final View recentsView, final int changes, boolean forceRelease) {
        if (recentsView == null || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return;
        }
        long readStateStartNs = LauncherRecentsPerf.start(recentsView);
        Object visibleTaskData;
        SparseBooleanArray visibleIds;
        int taskViewCount;
        try {
            visibleTaskData = LauncherRecentsCompat.getFieldCompat(
                    recentsView,
                    "mHasVisibleTaskData");
            visibleIds = visibleTaskData instanceof SparseBooleanArray
                    ? (SparseBooleanArray) visibleTaskData
                    : null;
            taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        } finally {
            LauncherRecentsPerf.end("visibleTaskDataSync:readState", readStateStartNs);
        }
        StackVisibleTaskDataSyncState syncState =
                STACK_VISIBLE_TASK_DATA_SYNC_STATES.get(recentsView);
        markStackVisibleTaskDataTouched(syncState, forceRelease);
        ArrayList<Integer> visibleTaskIds = new ArrayList<>();
        boolean hasPendingReleases = false;

        long scanStartNs = LauncherRecentsPerf.start(recentsView);
        try {
            int anchorIndex = resolveStackVisibleTaskDataAnchorIndex(recentsView, taskViewCount);
            int radius = resolveStackVisibleTaskDataRadius(recentsView);
            ArrayList<Integer> visibleTaskIndices = resolveStackVisibleTaskDataIndices(
                    recentsView,
                    taskViewCount,
                    anchorIndex,
                    radius);
            for (int index = 0; index < visibleTaskIndices.size(); index++) {
                int i = visibleTaskIndices.get(index);
                View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
                if (!isStackTaskDataVisible(recentsView, taskView, true)) {
                    continue;
                }
                boolean needsUpdate = visibleIds == null;
                ArrayList<Integer> taskIds = resolveStackTaskIds(taskView);
                for (int j = 0; j < taskIds.size(); j++) {
                    int taskId = taskIds.get(j);
                    visibleTaskIds.add(taskId);
                    if (visibleIds == null) {
                        needsUpdate = true;
                        continue;
                    }
                    if (!visibleIds.get(taskId)) {
                        needsUpdate = true;
                    }
                    visibleIds.put(taskId, true);
                }
                if (needsUpdate) {
                    LauncherRecentsCompat.invokeCompat(
                            taskView,
                            "onTaskListVisibilityChanged",
                            new Class<?>[]{boolean.class, int.class},
                            true,
                            changes);
                }
            }
        } finally {
            LauncherRecentsPerf.end("visibleTaskDataSync:scanAndLoad", scanStartNs);
        }
        ArrayList<Integer> lastVisibleTaskIds = STACK_VISIBLE_TASK_IDS.get(recentsView);
        boolean visibleTaskIdsChanged = lastVisibleTaskIds == null
                || !lastVisibleTaskIds.equals(visibleTaskIds);
        ArrayList<Integer> removedTaskIds = resolveRemovedStackTaskIds(
                lastVisibleTaskIds,
                visibleTaskIds);
        logStackFlow("visibleData:sync",
                recentsView,
                null,
                "changes=" + changes
                        + " forceRelease=" + forceRelease
                        + " visibleIds=" + visibleTaskIds
                        + " removedIds=" + removedTaskIds);
        if (visibleTaskIds.isEmpty()) {
            STACK_VISIBLE_TASK_IDS.remove(recentsView);
        } else if (visibleTaskIdsChanged) {
            STACK_VISIBLE_TASK_IDS.put(recentsView, new ArrayList<>(visibleTaskIds));
        }
        Object viewModel = LauncherRecentsCompat.getFieldCompat(recentsView, "mRecentsViewModel");
        if (viewModel != null && !visibleTaskIds.isEmpty() && visibleTaskIdsChanged) {
            long updateModelStartNs = LauncherRecentsPerf.start(recentsView);
            try {
                LauncherRecentsCompat.invokeCompat(
                        viewModel,
                        "updateVisibleTasks",
                        new Class<?>[]{List.class},
                        visibleTaskIds);
            } finally {
                LauncherRecentsPerf.end(
                        "visibleTaskDataSync:updateVisibleTasks",
                        updateModelStartNs);
            }
        }

        StackVisibleTaskDataSyncState state = STACK_VISIBLE_TASK_DATA_SYNC_STATES.get(recentsView);
        if (state != null) {
            hasPendingReleases = !removedTaskIds.isEmpty();
            if (forceRelease) {
                state.pendingCleanupRunnable = null;
                state.pendingCleanupChanges = 0;
                state.pendingReleaseTaskIds.clear();
                releaseStackTaskDataForIds(recentsView, visibleIds, removedTaskIds, changes);
            } else if (hasPendingReleases) {
                scheduleDeferredCleanup(recentsView, state, changes, removedTaskIds);
            }
        }
    }

    private static void scheduleDeferredCleanup(
            final View recentsView,
            final StackVisibleTaskDataSyncState state,
            final int changes,
            ArrayList<Integer> taskIds) {
        appendUniqueTaskIds(state.pendingReleaseTaskIds, taskIds);
        if (state.pendingCleanupRunnable != null) {
            state.pendingCleanupChanges |= changes;
            logStackFlow("visibleData:cleanup:alreadyPending",
                    recentsView,
                    null,
                    "changes=" + state.pendingCleanupChanges
                            + " pendingIds=" + state.pendingReleaseTaskIds);
            LauncherRecentsPerf.hit("visibleTaskDataSync:cleanupAlreadyPending", recentsView);
            return;
        }
        state.pendingCleanupChanges = changes;
        state.pendingCleanupRunnable = new Runnable() {
            @Override
            public void run() {
                state.pendingCleanupRunnable = null;
                int cleanupChanges = state.pendingCleanupChanges;
                ArrayList<Integer> cleanupTaskIds = new ArrayList<>(state.pendingReleaseTaskIds);
                state.pendingReleaseTaskIds.clear();
                if (shouldDelayStackVisibleTaskDataCleanup(recentsView, state)) {
                    logStackFlow("visibleData:cleanup:retryActive",
                            recentsView, null, "pendingIds=" + cleanupTaskIds);
                    scheduleDeferredCleanup(recentsView, state, cleanupChanges, cleanupTaskIds);
                    return;
                }
                if (!isStackVisibleTaskDataCleanupIdle(state)) {
                    logStackFlow("visibleData:cleanup:retryBusy",
                            recentsView, null, "pendingIds=" + cleanupTaskIds);
                    scheduleDeferredCleanup(recentsView, state, cleanupChanges, cleanupTaskIds);
                    return;
                }
                logStackFlow("visibleData:cleanup:run",
                        recentsView,
                        null,
                        "changes=" + cleanupChanges + " pendingIds=" + cleanupTaskIds);
                long startNs = LauncherRecentsPerf.start(recentsView);
                try {
                    Object visibleTaskData = LauncherRecentsCompat.getFieldCompat(
                            recentsView,
                            "mHasVisibleTaskData");
                    SparseBooleanArray visibleIds = visibleTaskData instanceof SparseBooleanArray
                            ? (SparseBooleanArray) visibleTaskData
                            : null;
                    releaseStackTaskDataForIds(
                            recentsView,
                            visibleIds,
                            cleanupTaskIds,
                            cleanupChanges);
                } finally {
                    LauncherRecentsPerf.end("visibleTaskDataSync:deferredCleanup", startNs);
                }
            }
        };
        recentsView.postDelayed(
                state.pendingCleanupRunnable,
                shouldDelayStackVisibleTaskDataCleanup(recentsView, state)
                        ? STACK_VISIBLE_DATA_CLEANUP_RETRY_DELAY_MS
                        : STACK_VISIBLE_DATA_CLEANUP_IDLE_DELAY_MS);
        logStackFlow("visibleData:cleanup:schedule",
                recentsView,
                null,
                "changes=" + changes + " pendingIds=" + state.pendingReleaseTaskIds);
    }

    private static boolean shouldDelayStackVisibleTaskDataCleanup(
            View recentsView,
            StackVisibleTaskDataSyncState state) {
        if (isTransitionAnimationActive(recentsView) || isRecentsScrollerActive(recentsView)) {
            return true;
        }
        if (state != null
                && !state.delayedGestureBackgroundCleanup
                && isGestureRecentsBackground(recentsView)) {
            state.delayedGestureBackgroundCleanup = true;
            return true;
        }
        return false;
    }

    private static boolean isRecentsScrollerActive(View recentsView) {
        Object scroller = LauncherRecentsCompat.getFieldCompat(recentsView, "mScroller");
        if (isScrollerActive(scroller)) {
            return true;
        }
        Object activeScroller = LauncherRecentsCompat.getFieldCompat(scroller, "usingScroller");
        return activeScroller != scroller && isScrollerActive(activeScroller);
    }

    private static boolean isScrollerActive(Object scroller) {
        Object value = LauncherRecentsCompat.invokeCompat(
                scroller,
                "isFinished",
                LauncherRecentsCompat.NO_ARGS);
        return value instanceof Boolean && !((Boolean) value);
    }

    private static boolean isStackVisibleTaskDataCleanupIdle(
            StackVisibleTaskDataSyncState state) {
        return state == null
                || state.lastLoadUptimeMs <= 0L
                || SystemClock.uptimeMillis() - state.lastLoadUptimeMs
                >= STACK_VISIBLE_DATA_CLEANUP_IDLE_DELAY_MS;
    }

    private static ArrayList<Integer> resolveRemovedStackTaskIds(
            ArrayList<Integer> oldIds,
            ArrayList<Integer> newIds) {
        ArrayList<Integer> removed = new ArrayList<>();
        if (oldIds == null || oldIds.isEmpty()) {
            return removed;
        }
        for (int i = 0; i < oldIds.size(); i++) {
            int taskId = oldIds.get(i);
            if (!newIds.contains(taskId)) {
                removed.add(taskId);
            }
        }
        return removed;
    }

    private static void appendUniqueTaskIds(ArrayList<Integer> target, ArrayList<Integer> source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            int taskId = source.get(i);
            if (!target.contains(taskId)) {
                target.add(taskId);
            }
        }
    }

    private static void releaseStackTaskDataForIds(
            View recentsView,
            SparseBooleanArray visibleIds,
            ArrayList<Integer> taskIdsToRelease,
            int changes) {
        if (recentsView == null
                || visibleIds == null
                || taskIdsToRelease == null
                || taskIdsToRelease.isEmpty()) {
            return;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (isStackTaskDataVisible(recentsView, taskView, true)) {
                continue;
            }
            boolean hadVisibleData = false;
            ArrayList<Integer> taskIds = resolveStackTaskIds(taskView);
            for (int j = 0; j < taskIds.size(); j++) {
                int taskId = taskIds.get(j);
                if (taskIdsToRelease.contains(taskId) && visibleIds.get(taskId)) {
                    visibleIds.delete(taskId);
                    hadVisibleData = true;
                }
            }
            if (hadVisibleData) {
                logStackFlow("visibleData:releaseTask",
                        recentsView,
                        null,
                        "changes=" + changes + " " + taskDetails(recentsView, taskView));
                long releaseStartNs = LauncherRecentsPerf.start(recentsView);
                try {
                    LauncherRecentsCompat.invokeCompat(
                            taskView,
                            "onTaskListVisibilityChanged",
                            new Class<?>[]{boolean.class, int.class},
                            false,
                            changes);
                } finally {
                    LauncherRecentsPerf.end("visibleTaskDataSync:releaseTask", releaseStartNs);
                }
            }
        }
    }

    private static int resolveStackVisibleTaskDataAnchorIndex(View recentsView, int taskViewCount) {
        if (taskViewCount <= 0) {
            return 0;
        }
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        if (runningTaskObject instanceof View && recentsView instanceof ViewGroup) {
            int runningIndex = ((ViewGroup) recentsView).indexOfChild((View) runningTaskObject);
            if (runningIndex >= 0 && isTransitionAnimationActive(recentsView)) {
                return runningIndex;
            }
        }
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                || LauncherRecentsState.isOverviewStateStackSettled(recentsView)) {
            return resolveNearestStackVisibleTaskDataPage(recentsView, taskViewCount, currentPage);
        }
        return Math.max(0, Math.min(currentPage, taskViewCount - 1));
    }

    private static int resolveNearestStackVisibleTaskDataPage(
            View recentsView,
            int taskViewCount,
            int currentPage) {
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int nearestPage = Math.max(0, Math.min(currentPage, taskViewCount - 1));
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < taskViewCount; i++) {
            int pageScroll = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getScrollForPage",
                    LauncherRecentsCompat.INT_ARG,
                    primaryScroll,
                    i);
            int distance = Math.abs(pageScroll - primaryScroll);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPage = i;
            }
        }
        return nearestPage;
    }

    private static int resolveStackVisibleTaskDataRadius(View recentsView) {
        return 3;
    }

    private static ArrayList<Integer> resolveStackVisibleTaskDataIndices(
            View recentsView,
            int taskViewCount,
            int anchorIndex,
            int radius) {
        ArrayList<Integer> indices = new ArrayList<>();
        if (taskViewCount <= 0 || radius < 0) {
            return indices;
        }
        anchorIndex = Math.max(0, Math.min(anchorIndex, taskViewCount - 1));
        if (shouldUseStackEntryVisibleTaskDataWindow(recentsView)) {
            int targetCount = resolveStackVisibleTaskDataFillBoundaryTargetCount(
                    recentsView,
                    taskViewCount,
                    radius);
            appendStackVisibleTaskDataIndex(indices, anchorIndex, taskViewCount);
            if (targetCount > 0) {
                if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
                    appendStableStackVisibleTaskDataIndices(
                            indices,
                            anchorIndex,
                            taskViewCount,
                            targetCount);
                    return appendStackDismissVisibleTaskDataIndices(
                            recentsView,
                            indices,
                            taskViewCount);
                }
                for (int i = 1; indices.size() < targetCount; i++) {
                    appendStackVisibleTaskDataIndex(indices, anchorIndex - i, taskViewCount);
                    if (indices.size() >= targetCount) {
                        break;
                    }
                    appendStackVisibleTaskDataIndex(indices, anchorIndex + i, taskViewCount);
                }
                return appendStackDismissVisibleTaskDataIndices(
                        recentsView,
                        indices,
                        taskViewCount);
            }
            for (int i = 1; i <= radius; i++) {
                appendStackVisibleTaskDataIndex(indices, anchorIndex - i, taskViewCount);
            }
            for (int i = 1; i <= radius; i++) {
                appendStackVisibleTaskDataIndex(indices, anchorIndex + i, taskViewCount);
            }
            return appendStackDismissVisibleTaskDataIndices(
                    recentsView,
                    indices,
                    taskViewCount);
        }
        int start = Math.max(0, anchorIndex - radius);
        int end = Math.min(taskViewCount - 1, anchorIndex + radius);
        for (int i = start; i <= end; i++) {
            indices.add(i);
        }
        return appendStackDismissVisibleTaskDataIndices(recentsView, indices, taskViewCount);
    }

    private static void appendStableStackVisibleTaskDataIndices(
            ArrayList<Integer> target,
            int anchorIndex,
            int taskViewCount,
            int targetCount) {
        appendStackVisibleTaskDataIndex(target, anchorIndex - 1, taskViewCount);
        for (int i = 1; target.size() < targetCount; i++) {
            appendStackVisibleTaskDataIndex(target, anchorIndex + i, taskViewCount);
            if (target.size() >= targetCount) {
                break;
            }
            if (i > 1) {
                appendStackVisibleTaskDataIndex(target, anchorIndex - i, taskViewCount);
            }
        }
    }

    private static boolean isStackVisibleTaskDataIndexVisible(
            View recentsView,
            int taskIndex,
            int taskViewCount,
            int anchorIndex,
            int radius) {
        return resolveStackVisibleTaskDataIndices(
                recentsView,
                taskViewCount,
                anchorIndex,
                radius).contains(taskIndex);
    }

    private static void appendStackVisibleTaskDataIndex(
            ArrayList<Integer> target,
            int index,
            int taskViewCount) {
        if (index >= 0 && index < taskViewCount && !target.contains(index)) {
            target.add(index);
        }
    }

    private static ArrayList<Integer> appendStackDismissVisibleTaskDataIndices(
            View recentsView,
            ArrayList<Integer> target,
            int taskViewCount) {
        for (int i = 0; i < taskViewCount; i++) {
            if (hasStackDismissLayoutOffset(
                    LauncherRecentsCompat.getTaskViewAt(recentsView, i))) {
                appendStackVisibleTaskDataIndex(target, i, taskViewCount);
            }
        }
        return target;
    }

    private static boolean shouldUseStackEntryVisibleTaskDataWindow(View recentsView) {
        return LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                || LauncherRecentsState.isGestureStackReleasedStable(recentsView);
    }

    private static int resolveStackVisibleTaskDataFillBoundaryTargetCount(
            View recentsView,
            int taskViewCount,
            int radius) {
        if (taskViewCount <= 0) {
            return 0;
        }
        if (LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
            return Math.min(taskViewCount, (radius * 2) + 2);
        }
        if (LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)) {
            return Math.min(taskViewCount, (radius * 2) + 1);
        }
        return 0;
    }

    private static final int KEY_TASK_IDS_CACHE = 0x7f999999;

    private static final class TaskIdsCache {
        final Object taskInstance;
        final ArrayList<Integer> taskIds;

        TaskIdsCache(Object taskInstance, ArrayList<Integer> taskIds) {
            this.taskInstance = taskInstance;
            this.taskIds = taskIds;
        }
    }

    private static ArrayList<Integer> resolveStackTaskIds(View taskView) {
        if (taskView == null) {
            return new ArrayList<>();
        }
        Object currentTask = LauncherRecentsCompat.getFieldCompat(taskView, "mTask");
        if (currentTask == null) {
            currentTask = LauncherRecentsCompat.invokeCompat(taskView, "getTask");
        }
        if (currentTask != null) {
            Object cached = taskView.getTag(KEY_TASK_IDS_CACHE);
            if (cached instanceof TaskIdsCache) {
                TaskIdsCache cache = (TaskIdsCache) cached;
                if (cache.taskInstance == currentTask) {
                    return cache.taskIds;
                }
            }
        }
        ArrayList<Integer> taskIds = new ArrayList<>();
        Object containersObject =
                LauncherRecentsCompat.invokeCompat(taskView, "getTaskContainers");
        if (containersObject instanceof List) {
            List<?> taskContainers = (List<?>) containersObject;
            for (int i = 0; i < taskContainers.size(); i++) {
                addStackTaskIdFromTask(
                        taskIds,
                        LauncherRecentsCompat.invokeCompat(taskContainers.get(i), "getTask"));
            }
            if (!taskIds.isEmpty()) {
                if (currentTask != null) {
                    taskView.setTag(KEY_TASK_IDS_CACHE, new TaskIdsCache(currentTask, taskIds));
                }
                return taskIds;
            }
        }
        Object attributeContainersObject =
                LauncherRecentsCompat.invokeCompat(taskView, "getTaskIdAttributeContainers");
        if (attributeContainersObject instanceof Object[]) {
            Object[] attributeContainers = (Object[]) attributeContainersObject;
            for (int i = 0; i < attributeContainers.length; i++) {
                addStackTaskIdFromTask(
                        taskIds,
                        LauncherRecentsCompat.invokeCompat(attributeContainers[i], "getTask"));
            }
            if (!taskIds.isEmpty()) {
                if (currentTask != null) {
                    taskView.setTag(KEY_TASK_IDS_CACHE, new TaskIdsCache(currentTask, taskIds));
                }
                return taskIds;
            }
        }
        addStackTaskIdFromTask(
                taskIds,
                LauncherRecentsCompat.invokeCompat(taskView, "getTask"));
        if (!taskIds.isEmpty()) {
            if (currentTask != null) {
                taskView.setTag(KEY_TASK_IDS_CACHE, new TaskIdsCache(currentTask, taskIds));
            }
            return taskIds;
        }
        addStackTaskIdFromTask(
                taskIds,
                LauncherRecentsCompat.getFieldCompat(taskView, "mTask"));
        if (!taskIds.isEmpty()) {
            if (currentTask != null) {
                taskView.setTag(KEY_TASK_IDS_CACHE, new TaskIdsCache(currentTask, taskIds));
            }
            return taskIds;
        }
        Object taskIdsObject =
                LauncherRecentsCompat.invokeCompat(taskView, "getTaskIds");
        if (taskIdsObject instanceof int[]) {
            int[] ids = (int[]) taskIdsObject;
            for (int i = 0; i < ids.length; i++) {
                addStackTaskId(taskIds, ids[i]);
            }
        }
        if (currentTask != null && !taskIds.isEmpty()) {
            taskView.setTag(KEY_TASK_IDS_CACHE, new TaskIdsCache(currentTask, taskIds));
        }
        return taskIds;
    }

    private static void addStackTaskIdFromTask(ArrayList<Integer> taskIds, Object task) {
        Object key = LauncherRecentsCompat.getFieldCompat(task, "key");
        addStackTaskId(taskIds, LauncherRecentsCompat.readIntField(key, "id", -1));
    }

    private static void addStackTaskId(ArrayList<Integer> taskIds, int taskId) {
        if (taskId == -1 || taskIds.contains(taskId)) {
            return;
        }
        taskIds.add(taskId);
    }

    private static boolean shouldSuppressPagedRelease(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(recentsView)) {
            return false;
        }
        int action = motionEvent.getActionMasked();
        return (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && LauncherRecentsCompat.invokeBoolean(recentsView, "isHandlingTouch", false);
    }

    private static boolean shouldSkipBlankTapPagedRelease(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        int action = motionEvent.getActionMasked();
        if ((action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        if (LauncherRecentsCompat.readBooleanField(
                recentsView,
                "mTouchDownToStartHome",
                false)) {
            return true;
        }
        return isAppToRecentsReleaseInterruptible(recentsView)
                && isBlankTapOnStack(recentsView, motionEvent);
    }

    private static boolean handleMovingStackBlankTapHomeExit(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView)) {
            finishMovingStackBlankTapTouch(recentsView, motionEvent);
            return true;
        }
        if (!LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                || LauncherRecentsCompat.invokeBoolean(recentsView, "isScrollerFinished", true)
                || findStackTaskUnderPoint(recentsView, motionEvent.getX(), motionEvent.getY())
                != null) {
            return false;
        }
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsTransitionController.prepareBlankTapHomeExitAnimation(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "startHome",
                LauncherRecentsCompat.BOOLEAN_ARG,
                true);
        finishMovingStackBlankTapTouch(recentsView, motionEvent);
        return true;
    }

    private static void finishMovingStackBlankTapTouch(
            View recentsView,
            MotionEvent motionEvent) {
        LauncherRecentsCompat.setBooleanField(
                recentsView,
                "mTouchDownToStartHome",
                false);
        releasePagedEdgeEffects(recentsView, motionEvent);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "resetTouchState",
                LauncherRecentsCompat.NO_ARGS);
    }

    private static boolean isAppToRecentsReleaseInterruptible(View recentsView) {
        return recentsView != null
                && !LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                && (LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                || LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(recentsView)
                || LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView));
    }

    private static boolean isBlankTapOnStack(View recentsView, MotionEvent motionEvent) {
        float downX = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionX",
                motionEvent.getX());
        float downY = LauncherRecentsCompat.readFloatField(
                recentsView,
                "mDownMotionY",
                motionEvent.getY());
        return findStackTaskUnderPoint(recentsView, downX, downY) == null
                && findStackTaskUnderPoint(
                recentsView,
                motionEvent.getX(),
                motionEvent.getY()) == null;
    }

    private static void suppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        logStackFlow("pagedRelease:suppress", recentsView, motionEvent, null);
        clearRecentsDeferredSnap(recentsView);
        if (motionEvent != null && motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
            startUnsnappedFlingIfNeeded(recentsView, motionEvent);
        } else {
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "abortScrollerAnimation",
                    LauncherRecentsCompat.NO_ARGS);
        }
        releasePagedEdgeEffects(recentsView, motionEvent);
        LauncherRecentsCompat.invokeCompat(recentsView, "resetTouchState", LauncherRecentsCompat.NO_ARGS);
        if (LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView)) {
            recentsView.invalidate();
        }
    }

    private static void startUnsnappedFlingIfNeeded(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return;
        }
        Object velocityTrackerValue =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mVelocityTracker");
        if (!(velocityTrackerValue instanceof VelocityTracker)) {
            logStackFlow("pagedRelease:fling:noVelocityTracker",
                    recentsView, motionEvent, null);
            return;
        }
        VelocityTracker velocityTracker = (VelocityTracker) velocityTrackerValue;
        velocityTracker.addMovement(motionEvent);
        int maximumVelocity =
                LauncherRecentsCompat.invokeInt(recentsView, "getMaximumVelocity", Integer.MAX_VALUE);
        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
        int activePointerId =
                LauncherRecentsCompat.readIntField(recentsView, "mActivePointerId", -1);
        int primaryVelocity = Math.round(
                resolvePrimaryVelocity(recentsView, velocityTracker, activePointerId));
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int minScroll =
                LauncherRecentsCompat.readIntField(recentsView, "mMinScroll", primaryScroll);
        int maxScroll =
                LauncherRecentsCompat.readIntField(recentsView, "mMaxScroll", primaryScroll);

        if (primaryScroll < minScroll || primaryScroll > maxScroll) {
            logStackFlow("pagedRelease:fling:springBack",
                    recentsView,
                    motionEvent,
                    "velocity=" + primaryVelocity
                            + " primaryScroll=" + primaryScroll
                            + " min=" + minScroll
                            + " max=" + maxScroll);
            startPagedSpringBack(recentsView, primaryScroll, minScroll, maxScroll);
            return;
        }
        if (!shouldKeepFreeScrollFling(recentsView, primaryVelocity)) {
            logStackFlow("pagedRelease:fling:skipVelocity",
                    recentsView, motionEvent, "velocity=" + primaryVelocity);
            return;
        }
        Object scroller = LauncherRecentsCompat.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            logStackFlow("pagedRelease:fling:noScroller",
                    recentsView, motionEvent, "velocity=" + primaryVelocity);
            return;
        }
        setScrollerFriction(scroller, 0.01f);
        if (!startScrollerFling(
                recentsView,
                scroller,
                primaryScroll,
                primaryVelocity,
                minScroll,
                maxScroll)) {
            logStackFlow("pagedRelease:fling:startFailed",
                    recentsView, motionEvent, "velocity=" + primaryVelocity);
            return;
        }
        logStackFlow("pagedRelease:fling:start",
                recentsView,
                motionEvent,
                "velocity=" + primaryVelocity
                        + " primaryScroll=" + primaryScroll
                        + " min=" + minScroll
                        + " max=" + maxScroll);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mNextPage",
                LauncherRecentsCompat.readIntField(recentsView, "mCurrentPage", -1));
    }

    private static boolean shouldKeepFreeScrollFling(View recentsView, int primaryVelocity) {
        Object value = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "shouldFlingForVelocity",
                LauncherRecentsCompat.INT_ARG,
                primaryVelocity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static float resolvePrimaryVelocity(
            View recentsView,
            VelocityTracker velocityTracker,
            int activePointerId) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryVelocity",
                new Class<?>[]{VelocityTracker.class, int.class},
                velocityTracker,
                activePointerId);
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return activePointerId >= 0
                ? velocityTracker.getXVelocity(activePointerId)
                : velocityTracker.getXVelocity();
    }

    private static int resolvePrimaryScroll(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                recentsView);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return isPrimaryScrollHorizontal(recentsView)
                ? recentsView.getScrollX()
                : recentsView.getScrollY();
    }

    private static boolean isPrimaryScrollHorizontal(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryValue",
                new Class<?>[]{int.class, int.class},
                1,
                0);
        return !(value instanceof Integer) || (Integer) value == 1;
    }

    private static float resolvePrimarySize(View view, boolean primaryScrollHorizontal) {
        return primaryScrollHorizontal ? view.getWidth() : view.getHeight();
    }

    private static float resolveGesturePrimaryDelta(View recentsView, float dx, float dy) {
        return isPrimaryScrollHorizontal(recentsView) ? dx : dy;
    }

    private static float resolveGestureSecondaryDelta(View recentsView, float dx, float dy) {
        return isPrimaryScrollHorizontal(recentsView) ? dy : dx;
    }

    private static void startPagedSpringBack(
            View recentsView,
            int primaryScroll,
            int minScroll,
            int maxScroll) {
        Object scroller = LauncherRecentsCompat.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        invokeScrollerSpringBack(scroller, primaryScroll, minScroll, maxScroll);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mNextPage",
                LauncherRecentsCompat.readIntField(recentsView, "mCurrentPage", -1));
    }

    private static boolean startScrollerFling(
            View recentsView,
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll) {
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        int overX = Math.round(
                resolvePrimarySize(recentsView, primaryScrollHorizontal) * 0.5f * 0.07f);
        invokeScrollerFling10(scroller, primaryScroll, primaryVelocity, minScroll, maxScroll, overX);
        int afterFinalX = readScrollerFinalX(scroller, primaryScroll);
        if (afterFinalX != primaryScroll) {
            return true;
        }
        invokeScrollerFling8(scroller, primaryScroll, primaryVelocity, minScroll, maxScroll);
        return readScrollerFinalX(scroller, primaryScroll) != primaryScroll;
    }

    private static void setScrollerFriction(Object scroller, float friction) {
        invokeScrollerMethod(scroller, "setFriction", LauncherRecentsCompat.FLOAT_ARG, friction);
    }

    private static void invokeScrollerSpringBack(
            Object scroller,
            int primaryScroll,
            int minScroll,
            int maxScroll) {
        invokeScrollerMethod(
                scroller,
                "springBack",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                minScroll,
                maxScroll,
                0,
                0);
    }

    private static void invokeScrollerFling10(
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll,
            int overX) {
        invokeScrollerMethod(
                scroller,
                "fling",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                -primaryVelocity,
                0,
                minScroll,
                maxScroll,
                0,
                0,
                overX,
                0);
    }

    private static void invokeScrollerFling8(
            Object scroller,
            int primaryScroll,
            int primaryVelocity,
            int minScroll,
            int maxScroll) {
        invokeScrollerMethod(
                scroller,
                "fling",
                new Class<?>[]{
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class
                },
                primaryScroll,
                0,
                -primaryVelocity,
                0,
                minScroll,
                maxScroll,
                0,
                0);
    }

    private static void invokeScrollerMethod(
            Object scroller,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (scroller == null) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(scroller, methodName, parameterTypes, args);
        Object activeScroller = LauncherRecentsCompat.getFieldCompat(scroller, "usingScroller");
        if (activeScroller != null && activeScroller != scroller) {
            LauncherRecentsCompat.invokeCompat(activeScroller, methodName, parameterTypes, args);
        }
    }

    private static int readScrollerFinalX(Object scroller, int fallback) {
        if (scroller == null) {
            return fallback;
        }
        Object value = LauncherRecentsCompat.invokeCompat(scroller, "getFinalX", LauncherRecentsCompat.NO_ARGS);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        Object activeScroller = LauncherRecentsCompat.getFieldCompat(scroller, "usingScroller");
        Object activeValue =
                LauncherRecentsCompat.invokeCompat(activeScroller, "getFinalX", LauncherRecentsCompat.NO_ARGS);
        return activeValue instanceof Integer ? (Integer) activeValue : fallback;
    }

    private static void releasePagedEdgeEffects(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        releaseEdgeEffect(
                LauncherRecentsCompat.getFieldCompat(recentsView, "mEdgeGlowLeft"),
                motionEvent);
        releaseEdgeEffect(
                LauncherRecentsCompat.getFieldCompat(recentsView, "mEdgeGlowRight"),
                motionEvent);
    }

    private static void releaseEdgeEffect(Object edgeEffect, MotionEvent motionEvent) {
        if (edgeEffect == null) {
            return;
        }
        if (motionEvent != null) {
            LauncherRecentsCompat.invokeCompat(
                    edgeEffect,
                    "onRelease",
                    new Class<?>[]{MotionEvent.class},
                    motionEvent);
        }
        LauncherRecentsCompat.invokeCompat(edgeEffect, "onRelease", LauncherRecentsCompat.NO_ARGS);
    }

    private static void hookTaskViewAppFlowVisibilityForStack(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setAppFlowViewVisible", String.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (!(thisObject instanceof View) || !(arg0 instanceof String)) {
                    return chain.proceed();
                }
                View taskView = (View) thisObject;
                String pkgName = (String) arg0;
                if (pkgName.isEmpty()) {
                    hideStackAppFlowIfNeeded(taskView);
                    return null;
                }
                if (!shouldThrottleStackAppFlow(taskView, pkgName)) {
                    Object result = chain.proceed();
                    LauncherRecentsState.LAST_STACK_APP_FLOW_PACKAGES.put(taskView, pkgName);
                    return result;
                }
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView.setAppFlowViewVisible",
                    t);
        }
    }

    static void clearStackAppFlowVisibilityCache() {
        LauncherRecentsState.LAST_STACK_APP_FLOW_PACKAGES.clear();
        LauncherRecentsState.LAST_STACK_TASK_LIST_VISIBILITY_CHANGES.clear();
    }

    private static boolean shouldThrottleStackTaskListVisibility(
            View taskView,
            boolean visible,
            int changes) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        if (recentsView == null || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        if (!visible) {
            boolean suppressUnload = shouldSuppressStackTaskDataUnload(taskView, changes);
            if (!suppressUnload) {
                LauncherRecentsState.LAST_STACK_TASK_LIST_VISIBILITY_CHANGES.remove(taskView);
            }
            return suppressUnload;
        }
        Integer lastChanges =
                LauncherRecentsState.LAST_STACK_TASK_LIST_VISIBILITY_CHANGES.get(taskView);
        if (lastChanges != null && lastChanges == changes) {
            return true;
        }
        LauncherRecentsState.LAST_STACK_TASK_LIST_VISIBILITY_CHANGES.put(taskView, changes);
        return false;
    }

    private static boolean shouldThrottleStackAppFlow(View taskView, String pkgName) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        if (recentsView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        int taskIndex = findTaskViewIndex(recentsView, taskView);
        if (taskIndex < 0) {
            return false;
        }
        String lastPkg = LauncherRecentsState.LAST_STACK_APP_FLOW_PACKAGES.get(taskView);
        if (pkgName.isEmpty()) {
            hideStackAppFlowIfNeeded(taskView);
            return true;
        }
        Object castDevicesObject = LauncherRecentsCompat.invokeCompat(recentsView, "getCastDevices");
        if (!(castDevicesObject instanceof List) || ((List<?>) castDevicesObject).size() <= 1) {
            hideStackAppFlowIfNeeded(taskView);
            return true;
        }
        if (LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                && !LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                && !LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                && Math.abs(taskIndex - resolveStackAppFlowAnchorIndex(recentsView))
                > STACK_APP_FLOW_LIGHT_RADIUS) {
            hideStackAppFlowIfNeeded(taskView);
            return true;
        }
        return pkgName.equals(lastPkg);
    }

    private static void hideStackAppFlowIfNeeded(View taskView) {
        String lastPkg = LauncherRecentsState.LAST_STACK_APP_FLOW_PACKAGES.get(taskView);
        if (STACK_APP_FLOW_HIDDEN.equals(lastPkg)) {
            return;
        }
        LauncherRecentsCompat.invokeCompat(taskView, "hideFlowViews");
        LauncherRecentsState.LAST_STACK_APP_FLOW_PACKAGES.put(taskView, STACK_APP_FLOW_HIDDEN);
    }

    private static int resolveStackAppFlowAnchorIndex(View recentsView) {
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        if (runningTaskObject instanceof View) {
            int runningIndex = findTaskViewIndex(recentsView, (View) runningTaskObject);
            if (runningIndex >= 0) {
                return runningIndex;
            }
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        return Math.max(0, taskViewCount - 1);
    }

    private static final class StackVisibleTaskDataSyncState {
        int taskViewCount;
        int currentPage;
        int scrollBucket;
        Runnable pendingSyncRunnable;
        int pendingSyncChanges;
        Runnable pendingCleanupRunnable;
        int pendingCleanupChanges;
        boolean delayedGestureBackgroundCleanup;
        final ArrayList<Integer> pendingReleaseTaskIds = new ArrayList<>();
        long lastLoadUptimeMs;
    }

    static void clearRecentsDeferredSnap(View recentsView) {
        logStackFlow("deferredSnap:clear", recentsView, null, null);
        Object handlerValue = LauncherRecentsCompat.getFieldCompat(
                recentsView,
                "mMainHandlerForAbortScrollAndCheckSnap");
        Object timeoutValue =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mTimeoutToCheckSnap");
        Object abortRunnerValue = LauncherRecentsCompat.getFieldCompat(
                recentsView,
                "mAbortRecentsViewScrollAnimRunner");
        if (handlerValue instanceof Handler) {
            Handler handler = (Handler) handlerValue;
            if (timeoutValue instanceof Runnable) {
                handler.removeCallbacks((Runnable) timeoutValue);
            }
            if (abortRunnerValue instanceof Runnable) {
                handler.removeCallbacks((Runnable) abortRunnerValue);
            }
        }
        LauncherRecentsCompat.setBooleanField(recentsView, "mNeedCheckSnapToDestination", false);
        LauncherRecentsCompat.setIntField(recentsView, "mLastHandleActionUpChildIndex", -1);
    }
}
