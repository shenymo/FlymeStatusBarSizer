package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class LauncherRecentsTouchController {
    private static final String TASK_VIEW_DISMISS_TOUCH_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewDismissTouchController";
    private static final String TASK_VIEW_TOUCH_CONTROLLER_DEPRECATED_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchControllerDeprecated";
    private static final long STACK_DISMISS_SUCCESS_ANIM_MS = 180L;
    private static final long STACK_DISMISS_CANCEL_ANIM_MS = 320L;
    private static final long STACK_DISMISS_RELAYOUT_ANIM_MS = STACK_DISMISS_CANCEL_ANIM_MS;
    private static final float STACK_DISMISS_SECONDARY_DOMINANCE = 1.2f;
    private static final float STACK_DISMISS_MIN_FLING_VELOCITY = -1200f;
    private static final float STACK_VISIBLE_DATA_RIGHT_MARGIN_RATIO = 1.40f;
    private static final int STACK_VISIBLE_DATA_SCROLL_BUCKET_DIVISOR = 2;
    private static final long STACK_VISIBLE_DATA_SYNC_RETRY_DELAY_MS = 64L;
    private static final long STACK_VISIBLE_DATA_CLEANUP_IDLE_DELAY_MS = 450L;
    private static final long STACK_VISIBLE_DATA_CLEANUP_RETRY_DELAY_MS = 250L;
    private static final float STACK_LEFT_RELEASE_ALPHA_THRESHOLD = 0.05f;
    private static final float STACK_SCROLL_TOUCH_SLOP_RATIO = 0.5f;
    private static final float STACK_SCROLL_FLING_DISTANCE_MS = 0.25f;
    private static final int STACK_APP_FLOW_LIGHT_RADIUS = 3;
    private static final String STACK_APP_FLOW_HIDDEN = "<stack-hidden>";
    private static final ThreadLocal<Boolean> TASK_DISMISS_VISIBILITY_BYPASS =
            new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STACK_LOAD_VISIBLE_TASK_DATA_ACTIVE =
            new ThreadLocal<>();
    private static final WeakHashMap<View, StackDismissGestureState> STACK_DISMISS_GESTURES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ArrayList<Integer>> STACK_VISIBLE_TASK_IDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, StackVisibleTaskDataSyncState>
            STACK_VISIBLE_TASK_DATA_SYNC_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, StackScrollGestureState> STACK_SCROLL_GESTURES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ValueAnimator> STACK_SCROLL_ANIMATORS =
            new WeakHashMap<>();

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
        hookRecentsViewClearAllDismissAnimationForStack(module, loader);
        hookRecentsViewLoadVisibleTaskDataForStack(module, loader);
        hookTaskViewListVisibilityForStack(module, loader);
        hookTaskViewAppFlowVisibilityForStack(module, loader);
    }

    private static void hookRecentsViewClearAllDismissAnimationForStack(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> recentsViewClass =
                    Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Class<?> pendingAnimationClass =
                    Class.forName("com.android.launcher3.anim.PendingAnimation", false, loader);
            Constructor<?> pendingAnimationConstructor =
                    pendingAnimationClass.getDeclaredConstructor(long.class);
            pendingAnimationConstructor.setAccessible(true);
            Method method = recentsViewClass.getDeclaredMethod(
                    "createAllTasksDismissAnimationMz",
                    long.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (!(thisObject instanceof View)
                        || !LauncherRecentsLayoutEngine.shouldUseStackLayout((View) thisObject)) {
                    return chain.proceed();
                }
                long durationMs = chain.getArg(0) instanceof Long
                        ? (Long) chain.getArg(0)
                        : 300L;
                if (!runStackClearAllDismissAnimation(
                        (View) thisObject,
                        pendingAnimationConstructor,
                        pendingAnimationClass,
                        taskViewClass,
                        durationMs)) {
                    return chain.proceed();
                }
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.createAllTasksDismissAnimationMz",
                    t);
        }
    }

    private static boolean runStackClearAllDismissAnimation(
            View recentsView,
            Constructor<?> pendingAnimationConstructor,
            Class<?> pendingAnimationClass,
            Class<?> taskViewClass,
            long durationMs) {
        Object pendingAnimation = LauncherRecentsCompat.createPendingAnimationInstance(
                pendingAnimationConstructor,
                durationMs);
        if (pendingAnimation == null) {
            return false;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (taskViewCount <= 0) {
            return false;
        }
        Object runningTaskObject = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getRunningTaskView");
        View runningTaskView = runningTaskObject instanceof View ? (View) runningTaskObject : null;
        if (runningTaskView != null) {
            LauncherRecentsCompat.invokeCompat(runningTaskView, "dismissGuidePopupWindow");
        }

        ArrayList<View> visibleTasks = new ArrayList<>();
        ArrayList<Integer> visibleTaskIndices = new ArrayList<>();
        ArrayList<View> hiddenTasks = new ArrayList<>();
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (isStackClearAllTaskVisible(recentsView, taskView)) {
                visibleTasks.add(taskView);
                visibleTaskIndices.add(i);
            } else {
                hiddenTasks.add(taskView);
            }
        }
        if (visibleTasks.isEmpty()) {
            return false;
        }

        boolean runningTaskAnimated = false;
        for (int i = 0; i < visibleTasks.size(); i++) {
            View taskView = visibleTasks.get(i);
            boolean isRunningTask = taskView == runningTaskView;
            runningTaskAnimated |= isRunningTask;
            if (!LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "addDismissedTaskAnimationsMz",
                    new Class<?>[]{
                            taskViewClass,
                            pendingAnimationClass,
                            long.class,
                            boolean.class
                    },
                    taskView,
                    pendingAnimation,
                    visibleTaskIndices.get(i) * 30L,
                    isRunningTask)) {
                return false;
            }
        }
        boolean finalRunningTaskAnimated = runningTaskAnimated;
        Consumer<Boolean> endListener = success -> LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "lambda$createAllTasksDismissAnimationMz$71",
                new Class<?>[]{taskViewClass, boolean.class, Boolean.class},
                runningTaskView,
                finalRunningTaskAnimated,
                Boolean.TRUE.equals(success));
        if (!LauncherRecentsCompat.invokeMethodReflectively(
                pendingAnimation,
                "addEndListener",
                new Class<?>[]{Consumer.class},
                endListener)) {
            return false;
        }
        Object animation = LauncherRecentsCompat.invokeCompat(pendingAnimation, "buildAnim");
        if (!(animation instanceof Animator)) {
            return false;
        }
        for (int i = 0; i < hiddenTasks.size(); i++) {
            hiddenTasks.get(i).setAlpha(0f);
        }
        LauncherRecentsCompat.writeField(recentsView, "mPendingAnimation", pendingAnimation);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play((Animator) animation);
        animatorSet.start();
        return true;
    }

    private static boolean isStackClearAllTaskVisible(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || taskView.getVisibility() != View.VISIBLE
                || taskView.getWidth() <= 0
                || taskView.getHeight() <= 0
                || taskView.getAlpha() <= 0f) {
            return false;
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float primarySize = primaryScrollHorizontal ? taskView.getWidth() : taskView.getHeight();
        float primaryScale = Math.max(
                0.01f,
                primaryScrollHorizontal ? taskView.getScaleX() : taskView.getScaleY());
        float primaryPivot = primaryScrollHorizontal ? taskView.getPivotX() : taskView.getPivotY();
        float primaryStart = primaryScrollHorizontal ? taskView.getX() : taskView.getY();
        int primaryScroll =
                primaryScrollHorizontal ? recentsView.getScrollX() : recentsView.getScrollY();
        float scaledStart = primaryStart + primaryPivot - (primaryPivot * primaryScale)
                - primaryScroll;
        float scaledEnd = scaledStart + (primarySize * primaryScale);
        float viewportSize =
                primaryScrollHorizontal ? recentsView.getWidth() : recentsView.getHeight();
        return scaledEnd > 0f && scaledStart < viewportSize;
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
                    ensureClearAllButtonReadyOnTouchDown(recentsView, motionEvent);
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
                    long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                    try {
                        return chain.proceed();
                    } finally {
                        LauncherRecentsPerf.end("native:onInterceptTouchEvent", nativeStartNs);
                    }
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
                    ensureClearAllButtonReadyOnTouchDown(recentsView, motionEvent);
                    boolean entryTakeover =
                            takeOverAppToRecentsEntryOnHorizontalMove(recentsView, motionEvent);
                    boolean overviewTakeover = !entryTakeover
                            && takeOverOverviewStateOnHorizontalMove(recentsView, motionEvent);
                    clearGestureReleaseTaskStatesOnUserMove(recentsView, motionEvent);
                    startStackScrollEarlier(recentsView, motionEvent);
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
                    Boolean stackScrollHandled =
                            handleStackPagedScrollTouch(recentsView, motionEvent);
                    if (stackScrollHandled != null) {
                        return stackScrollHandled;
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
                    long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                    Object result;
                    try {
                        result = chain.proceed();
                    } finally {
                        LauncherRecentsPerf.end("native:onTouchEvent", nativeStartNs);
                    }
                    applyStackLayoutAfterPagedMove(recentsView, motionEvent);
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

    private static void ensureClearAllButtonReadyOnTouchDown(
            View recentsView,
            MotionEvent motionEvent) {
        if (motionEvent == null || motionEvent.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return;
        }
        LauncherRecentsLayoutEngine.ensureStackClearAllButtonReady(recentsView);
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

    private static void startStackScrollEarlier(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || !LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                || LauncherRecentsCompat.invokeBoolean(recentsView, "isHandlingTouch", false)) {
            return;
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
        float primaryDelta = resolveGesturePrimaryDelta(recentsView, dx, dy);
        float secondaryDelta = resolveGestureSecondaryDelta(recentsView, dx, dy);
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        float downPrimary = primaryScrollHorizontal ? downX : downY;
        int touchSlop = ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop();
        if (Math.abs(primaryDelta) <= touchSlop * STACK_SCROLL_TOUCH_SLOP_RATIO
                || Math.abs(primaryDelta) <= Math.abs(secondaryDelta)) {
            return;
        }
        LauncherRecentsCompat.setBooleanField(recentsView, "mIsBeingDragged", true);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mLastMotion",
                Math.round(downPrimary));
        LauncherRecentsCompat.invokeCompat(recentsView, "pageBeginTransition");
        ViewParent parent = recentsView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
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
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> startVisualStates =
                LauncherRecentsLayoutEngine.captureCurrentStackTaskVisualStates(recentsView);
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
                currentScroll,
                startVisualStates);
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
                        && !isStackDismissDragActive((View) thisObject)) {
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

    private static boolean isStackDismissDragActive(View recentsView) {
        return recentsView != null && STACK_DISMISS_GESTURES.containsKey(recentsView);
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
            LauncherRecentsPerf.startSpan("dismissTask", recentsView);
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
            LauncherRecentsPerf.endSpan("dismissTask", recentsView);
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
                    LauncherRecentsPerf.endSpan("dismissTask", recentsView);
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
            LauncherRecentsPerf.endSpan("dismissTask", recentsView);
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
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        if (!primaryScrollHorizontal && isSeascapeOrientation(recentsView)) {
            return nativeSign;
        }
        return primaryScrollHorizontal ? nativeSign : -nativeSign;
    }

    private static boolean isSeascapeOrientation(View recentsView) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getRotation",
                LauncherRecentsCompat.NO_ARGS);
        return value instanceof Integer && (Integer) value == 3;
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
        animator.addUpdateListener(animation -> LauncherRecentsPerf.measure(
                "frameCost:dismissSuccess",
                state.recentsView,
                () -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslation = value;
            LauncherRecentsPerf.hit("animationFrame:dismissSuccess", state.recentsView);
            applyStackDismissProgress(state, value);
        }));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                logStackFlow("dismiss:animateSuccess:end",
                        state.recentsView, null, taskDetails(state.recentsView, state.taskView));
                applyStackDismissProgress(state, end);
                finishStackDismissAfterSlideOut(state);
            }
        });
        animator.start();
    }

    private static void finishStackDismissAfterSlideOut(StackDismissGestureState state) {
        boolean dismissed = commitStackDismiss(state);
        logStackFlow("dismiss:afterSlideOut",
                state.recentsView,
                null,
                "dismissed=" + dismissed + " " + taskDetails(state.recentsView, state.taskView));
        LauncherRecentsPerf.endSpan("dismissTask", state.recentsView);
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
        animator.addUpdateListener(animation -> LauncherRecentsPerf.measure(
                "frameCost:dismissCancel",
                state.recentsView,
                () -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslation = value;
            LauncherRecentsPerf.hit("animationFrame:dismissCancel", state.recentsView);
            applyStackDismissProgress(state, value);
        }));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                logStackFlow("dismiss:animateCancel:end",
                        state.recentsView, null, taskDetails(state.recentsView, state.taskView));
                resetStackDismissVisuals(state);
                LauncherRecentsPerf.endSpan("dismissTask", state.recentsView);
            }
        });
        animator.start();
    }

    private static void applyStackDismissProgress(
            StackDismissGestureState state,
            float dismissTranslation) {
        setStackDismissTranslation(state, dismissTranslation);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        state.recentsView.invalidate();
    }

    private static void preheatStackDismissVisibleTaskData(View recentsView) {
        forceEnsureStackVisibleTaskData(recentsView, 15);
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
        setStackDismissTranslation(state, state.startDismissTranslation);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        LauncherRecentsTaskVisuals.setTranslationZ(state.taskView, state.originalTranslationZ);
        state.recentsView.invalidate();
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

    private static boolean commitStackDismiss(StackDismissGestureState state) {
        View recentsView = state != null ? state.recentsView : null;
        View taskView = state != null ? state.taskView : null;
        Class<?> taskViewClass = resolveTaskViewBaseClass(taskView);
        if (!(recentsView instanceof ViewGroup) || taskViewClass == null) {
            logStackFlow("dismiss:commit:missingClass",
                    recentsView, null, taskDetails(recentsView, taskView));
            return false;
        }
        int dismissedIndex = findTaskViewIndex(recentsView, taskView);
        LauncherRecentsLayoutEngine.prepareStackDismissRelayoutCapture(recentsView);
        HashMap<View, StackDismissRelayoutStartState> startStates =
                captureStackDismissRelayoutStartStates(recentsView);
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        boolean fillFromAfter = shouldFillStackDismissFromAfter(dismissedIndex, startStates);
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        boolean snapToPageAfterRelayout = !fillFromAfter;
        int startScroll = resolvePrimaryScroll(recentsView);
        int[] targetPageAndScroll = snapToPageAfterRelayout
                ? resolveNearestStackDismissPageAndScroll(
                        recentsView,
                        startScroll,
                        Math.max(0, taskViewCount - 1))
                : null;
        int targetScroll = targetPageAndScroll != null
                ? targetPageAndScroll[1]
                : startScroll;
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> targetStates =
                dismissedIndex >= 0
                        ? LauncherRecentsLayoutEngine.computeStackLayout(
                                recentsView,
                                taskView,
                                targetScroll)
                        : new HashMap<>();
        adjustStackDismissRelayoutTargets(
                recentsView,
                dismissedIndex,
                startStates,
                targetStates,
                primaryScrollHorizontal,
                targetScroll);
        boolean removedTask = LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "removeTaskInternal",
                new Class<?>[]{taskViewClass},
                taskView);
        if (!removedTask) {
            logStackFlow("dismiss:commit:removeFailed",
                    recentsView, null, taskDetails(recentsView, taskView));
            return false;
        }
        ((ViewGroup) recentsView).removeViewInLayout(taskView);
        animateStackDismissRelayout(
                recentsView,
                dismissedIndex,
                startStates,
                targetStates,
                primaryScrollHorizontal,
                startScroll,
                targetScroll,
                snapToPageAfterRelayout);
        recentsView.invalidate();
        return true;
    }

    private static HashMap<View, StackDismissRelayoutStartState>
    captureStackDismissRelayoutStartStates(View recentsView) {
        HashMap<View, StackDismissRelayoutStartState> states = new HashMap<>();
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null
                    || LauncherRecentsCompat.isDesktopTask(taskView)
                    || taskView.getVisibility() != View.VISIBLE
                    || taskView.getWidth() <= 0
                    || taskView.getHeight() <= 0
                    || (recentsView instanceof ViewGroup
                    && ((ViewGroup) recentsView).indexOfChild(taskView) < 0)) {
                continue;
            }
            LauncherRecentsTaskVisuals.StackTaskVisualState visualState =
                    LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.get(taskView);
            if (visualState == null) {
                visualState = createStackDismissCurrentStartState(taskView);
            }
            states.put(
                    taskView,
                    new StackDismissRelayoutStartState(
                            i,
                            visualState));
        }
        return states;
    }

    private static boolean shouldFillStackDismissFromAfter(
            int dismissedIndex,
            HashMap<View, StackDismissRelayoutStartState> startStates) {
        int beforeCount = 0;
        int afterCount = 0;
        if (startStates == null) {
            return false;
        }
        for (StackDismissRelayoutStartState state : startStates.values()) {
            if (state.index > dismissedIndex) {
                afterCount++;
            } else if (state.index < dismissedIndex) {
                beforeCount++;
            }
        }
        return afterCount > 0 && afterCount >= beforeCount;
    }

    private static void adjustStackDismissRelayoutTargets(
            View recentsView,
            int dismissedIndex,
            HashMap<View, StackDismissRelayoutStartState> startStates,
            HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> targetStates,
            boolean primaryScrollHorizontal,
            int targetScroll) {
        if (dismissedIndex < 0 || startStates == null || targetStates == null) {
            return;
        }
        for (View taskView : new ArrayList<>(targetStates.keySet())) {
            StackDismissRelayoutStartState startState = startStates.get(taskView);
            LauncherRecentsTaskVisuals.StackTaskVisualState targetState =
                    targetStates.get(taskView);
            if (startState == null || targetState == null || startState.index <= dismissedIndex) {
                continue;
            }
            int projectedIndex = startState.index - 1;
            float oldRawOffset = LauncherRecentsLayoutEngine.resolveTaskRawOffset(
                    recentsView,
                    startState.index,
                    targetScroll);
            float newRawOffset = LauncherRecentsLayoutEngine.resolveTaskRawOffset(
                    recentsView,
                    projectedIndex,
                    targetScroll);
            float delta = newRawOffset - oldRawOffset;
            if (Math.abs(delta) <= 0.01f) {
                continue;
            }
            targetStates.put(
                    taskView,
                    offsetStackDismissRelayoutTarget(
                            targetState,
                            primaryScrollHorizontal,
                            delta));
        }
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState
    offsetStackDismissRelayoutTarget(
            LauncherRecentsTaskVisuals.StackTaskVisualState state,
            boolean primaryScrollHorizontal,
            float primaryDelta) {
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                state.pivotX,
                state.pivotY,
                state.horizontalOffsetX,
                primaryScrollHorizontal ? state.taskOffsetX + primaryDelta : state.taskOffsetX,
                primaryScrollHorizontal ? state.taskOffsetY : state.taskOffsetY + primaryDelta,
                state.boxTranslationY,
                state.scale,
                state.attachAlpha,
                state.stableAlpha,
                state.activityTitleAlpha,
                state.blurProgress,
                state.fullscreenProgress,
                state.translationZ,
                state.stackContentBlurEnabled,
                state.clearShadow);
    }

    private static void animateStackDismissRelayout(
            View recentsView,
            int dismissedIndex,
            HashMap<View, StackDismissRelayoutStartState> startStates,
            HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> targetStates,
            boolean primaryScrollHorizontal,
            int startScroll,
            int targetScroll,
            boolean snapToPageAfterRelayout) {
        if (dismissedIndex < 0 || startStates == null || startStates.isEmpty()) {
            finishStackDismissRelayout(recentsView, "dismissRelayoutNoStart", false);
            return;
        }
        HashMap<View, LauncherRecentsTaskVisuals.StackTaskVisualState> animationStartStates =
                new HashMap<>();
        final boolean animatePageScroll = snapToPageAfterRelayout && targetScroll != startScroll;
        if (targetStates == null || targetStates.isEmpty()) {
            finishStackDismissRelayout(recentsView, "dismissRelayoutNoTarget", false);
            return;
        }
        for (View taskView : targetStates.keySet()) {
            StackDismissRelayoutStartState startState = startStates.get(taskView);
            animationStartStates.put(
                    taskView,
                    startState != null
                            ? startState.visualState
                            : createStackDismissCurrentStartState(taskView));
        }
        ValueAnimator runningAnimator =
                LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS.remove(recentsView);
        if (runningAnimator != null) {
            runningAnimator.cancel();
        }
        for (View taskView : targetStates.keySet()) {
            LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                    taskView,
                    animationStartStates.get(taskView));
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(STACK_DISMISS_RELAYOUT_ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            if (animatePageScroll) {
                int scroll = Math.round(LauncherRecentsLayoutEngine.lerp(
                        startScroll,
                        targetScroll,
                        progress));
                scrollStackDismissTo(recentsView, primaryScrollHorizontal, scroll);
            }
            for (View taskView : targetStates.keySet()) {
                LauncherRecentsTaskVisuals.StackTaskVisualState startState =
                        animationStartStates.get(taskView);
                LauncherRecentsTaskVisuals.StackTaskVisualState targetState =
                        targetStates.get(taskView);
                if (startState != null && targetState != null) {
                    LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                            taskView,
                            startState.lerpTo(targetState, progress));
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS.get(recentsView)
                        == animation) {
                    LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS.remove(recentsView);
                    if (animatePageScroll) {
                        scrollStackDismissTo(recentsView, primaryScrollHorizontal, targetScroll);
                    }
                    finishStackDismissRelayout(
                            recentsView,
                            "dismissRelayoutEnd",
                            snapToPageAfterRelayout);
                }
            }
        });
        LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void finishStackDismissRelayout(
            View recentsView,
            String source,
            boolean snapToPage) {
        if (recentsView == null) {
            return;
        }
        syncStackDismissPageFields(recentsView, snapToPage);
        LauncherRecentsState.LAST_STACK_LAYOUT_APPLIES.remove(recentsView);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false, source, true);
        hideUnmanagedStackDismissTasks(recentsView);
        forceEnsureStackVisibleTaskData(recentsView, 15, true);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static void hideUnmanagedStackDismissTasks(View recentsView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null
                    || LauncherRecentsCompat.isDesktopTask(taskView)
                    || LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES
                    .containsKey(taskView)
                    || taskView.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (taskView.getAlpha() <= 0.01f
                    && LauncherRecentsTaskVisuals.readStableAlpha(taskView) <= 0.01f) {
                continue;
            }
            LauncherRecentsTaskVisuals.setAttachAlpha(taskView, 0f);
            LauncherRecentsTaskVisuals.setStableAlpha(taskView, 0f);
            LauncherRecentsTaskVisuals.setTaskHeadContentAlpha(taskView, 0f);
            LauncherRecentsTaskVisuals.clearStackContentBlurIfApplied(taskView);
            LauncherRecentsTaskVisuals.setTranslationZ(taskView, 0f);
        }
    }

    private static void syncStackDismissPageFields(View recentsView, boolean snapToPage) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        if (recentsView == null || taskViewCount <= 0) {
            return;
        }
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int[] pageAndScroll =
                resolveNearestStackDismissPageAndScroll(recentsView, primaryScroll, taskViewCount);
        int nearestPage = pageAndScroll[0];
        int nearestScroll = pageAndScroll[1];
        if (snapToPage) {
            setStackDismissPageFields(recentsView, nearestPage, nearestPage, 0);
            return;
        }
        setStackDismissPageFields(recentsView, nearestPage, -1, primaryScroll - nearestScroll);
    }

    private static int[] resolveNearestStackDismissPageAndScroll(
            View recentsView,
            int primaryScroll,
            int taskViewCount) {
        int nearestPage = 0;
        int nearestScroll = LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                primaryScroll,
                0);
        int nearestDistance = Math.abs(nearestScroll - primaryScroll);
        for (int i = 1; i < taskViewCount; i++) {
            int pageScroll = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getScrollForPage",
                    LauncherRecentsCompat.INT_ARG,
                    primaryScroll,
                    i);
            int distance = Math.abs(pageScroll - primaryScroll);
            if (distance < nearestDistance) {
                nearestPage = i;
                nearestScroll = pageScroll;
                nearestDistance = distance;
            }
        }
        return new int[]{nearestPage, nearestScroll};
    }

    private static void setStackDismissPageFields(
            View recentsView,
            int page,
            int nextPage,
            int currentPageScrollDiff) {
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentScrollOverPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", nextPage);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mCurrentPageScrollDiff",
                currentPageScrollDiff);
    }

    private static void applyStackLayoutAfterPagedMove(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null
                || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || !LauncherRecentsState.isGestureStackReleasedStable(recentsView)) {
            return;
        }
        cancelStackDismissRelayoutAnimation(recentsView);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
    }

    private static void cancelStackDismissRelayoutAnimation(View recentsView) {
        ValueAnimator animator =
                LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
    }

    static boolean isStackDismissRelayoutAnimationActive(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.ACTIVE_STACK_DISMISS_RELAYOUT_ANIMATORS
                .containsKey(recentsView);
    }

    private static LauncherRecentsTaskVisuals.StackTaskVisualState
    createStackDismissCurrentStartState(View taskView) {
        float blurProgress = LauncherRecentsTaskVisuals.readStackContentBlurProgress(taskView);
        return new LauncherRecentsTaskVisuals.StackTaskVisualState(
                taskView.getPivotX(),
                taskView.getPivotY(),
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "horizontalOffsetTranslationX",
                        0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView)),
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                LauncherRecentsTaskVisuals.readAttachAlpha(taskView),
                LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                LauncherRecentsTaskVisuals.readActivityTitleAlpha(taskView),
                blurProgress,
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f),
                taskView.getTranslationZ(),
                blurProgress > 0.001f,
                false);
    }

    private static void settleStackDismissLayoutState(View recentsView) {
        LauncherRecentsState.clearAppToRecentsEntryState(recentsView);
        clearStackAppFlowVisibilityCache();
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

    private static final class StackDismissRelayoutStartState {
        final int index;
        final LauncherRecentsTaskVisuals.StackTaskVisualState visualState;

        StackDismissRelayoutStartState(
                int index,
                LauncherRecentsTaskVisuals.StackTaskVisualState visualState) {
            this.index = index;
            this.visualState = visualState;
        }
    }

    private static final class StackScrollGestureState {
        final View recentsView;
        final float downPrimary;
        final float downSecondary;
        final int activePointerId;
        VelocityTracker velocityTracker;
        boolean dragging;
        int startScroll;
        float startPrimary;

        StackScrollGestureState(View recentsView, MotionEvent motionEvent) {
            this.recentsView = recentsView;
            this.downPrimary = resolvePrimaryDirection(recentsView, motionEvent);
            this.downSecondary = resolveSecondaryDirection(recentsView, motionEvent);
            this.activePointerId = motionEvent.getPointerId(0);
            this.startScroll = resolvePrimaryScroll(recentsView);
            this.startPrimary = this.downPrimary;
            this.velocityTracker = VelocityTracker.obtain();
            this.velocityTracker.addMovement(motionEvent);
        }

        void addMovement(MotionEvent motionEvent) {
            if (velocityTracker != null && motionEvent != null) {
                velocityTracker.addMovement(motionEvent);
            }
        }

        float computePrimaryVelocity() {
            if (velocityTracker == null) {
                return 0f;
            }
            velocityTracker.computeCurrentVelocity(1000);
            return resolvePrimaryVelocity(recentsView, velocityTracker, activePointerId);
        }

        void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
    }

    private static final class StackDismissGestureState {
        final View recentsView;
        final View taskView;
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
                && readStackTaskDataAlpha(taskView) > STACK_LEFT_RELEASE_ALPHA_THRESHOLD
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
                - resolvePrimaryScroll(recentsView);
        float taskSize = resolvePrimarySize(taskView, primaryScrollHorizontal);
        float viewportStart = -taskSize;
        float viewportEnd = resolvePrimarySize(recentsView, primaryScrollHorizontal)
                * (1f + STACK_VISIBLE_DATA_RIGHT_MARGIN_RATIO);
        return taskStart + taskSize > viewportStart && taskStart < viewportEnd;
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
                || isStackDismissRelayoutAnimationActive(recentsView)
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
                    return indices;
                }
                for (int i = 1; indices.size() < targetCount; i++) {
                    appendStackVisibleTaskDataIndex(indices, anchorIndex - i, taskViewCount);
                    if (indices.size() >= targetCount) {
                        break;
                    }
                    appendStackVisibleTaskDataIndex(indices, anchorIndex + i, taskViewCount);
                }
                return indices;
            }
            for (int i = 1; i <= radius; i++) {
                appendStackVisibleTaskDataIndex(indices, anchorIndex - i, taskViewCount);
            }
            for (int i = 1; i <= radius; i++) {
                appendStackVisibleTaskDataIndex(indices, anchorIndex + i, taskViewCount);
            }
            return indices;
        }
        int start = Math.max(0, anchorIndex - radius);
        int end = Math.min(taskViewCount - 1, anchorIndex + radius);
        for (int i = start; i <= end; i++) {
            indices.add(i);
        }
        return indices;
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

    private static Boolean handleStackPagedScrollTouch(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return null;
        }
        int action = motionEvent.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelStackScrollAnimation(recentsView);
            STACK_SCROLL_GESTURES.remove(recentsView);
            if (shouldTakeOverStackPagedScroll(recentsView)) {
                STACK_SCROLL_GESTURES.put(
                        recentsView,
                        new StackScrollGestureState(recentsView, motionEvent));
            }
            return null;
        }

        StackScrollGestureState state = STACK_SCROLL_GESTURES.get(recentsView);
        if (state == null) {
            return null;
        }
        state.addMovement(motionEvent);
        if (!shouldTakeOverStackPagedScroll(recentsView)) {
            clearStackScrollGesture(recentsView);
            LauncherRecentsState.clearStackScrollFixedAnchorPage(recentsView);
            return null;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (!state.dragging && !shouldStartStackPagedScroll(state, motionEvent)) {
                return null;
            }
            if (!state.dragging) {
                beginStackPagedScroll(state, motionEvent);
            }
            updateStackPagedScroll(state, motionEvent);
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!state.dragging) {
                clearStackScrollGesture(recentsView);
                return null;
            }
            finishStackPagedScroll(
                    recentsView,
                    motionEvent,
                    action == MotionEvent.ACTION_CANCEL
                            ? 0f
                            : state.computePrimaryVelocity(),
                    action == MotionEvent.ACTION_CANCEL);
            return true;
        }
        return state.dragging ? true : null;
    }

    private static boolean shouldTakeOverStackPagedScroll(View recentsView) {
        return recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && LauncherRecentsState.isGestureStackReleasedStable(recentsView)
                && !LauncherRecentsState.isSwipeUpGestureActive(recentsView)
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && !LauncherRecentsState.isAppToRecentsEntrySessionActive(recentsView)
                && !LauncherRecentsState.isAppToRecentsStackLayoutDeferred(recentsView)
                && !LauncherRecentsState.isAppToRecentsGestureReleased(recentsView)
                && !LauncherRecentsState.isPendingGestureRecentsStackRelease(recentsView)
                && !LauncherRecentsTransitionController.isGestureRecentsStackReleaseAnimationActive(
                recentsView)
                && !LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView);
    }

    private static boolean shouldStartStackPagedScroll(
            StackScrollGestureState state,
            MotionEvent motionEvent) {
        float primaryDelta =
                resolvePrimaryDirection(state.recentsView, motionEvent) - state.downPrimary;
        float secondaryDelta =
                resolveSecondaryDirection(state.recentsView, motionEvent) - state.downSecondary;
        int touchSlop = ViewConfiguration.get(state.recentsView.getContext()).getScaledTouchSlop();
        return Math.abs(primaryDelta) > touchSlop * STACK_SCROLL_TOUCH_SLOP_RATIO
                && Math.abs(primaryDelta) > Math.abs(secondaryDelta);
    }

    private static void beginStackPagedScroll(
            StackScrollGestureState state,
            MotionEvent motionEvent) {
        View recentsView = state.recentsView;
        state.dragging = true;
        state.startScroll = resolvePrimaryScroll(recentsView);
        state.startPrimary = resolvePrimaryDirection(recentsView, motionEvent);
        cancelStackScrollAnimation(recentsView);
        cancelStackDismissRelayoutAnimation(recentsView);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        freezeStackScrollLayoutAnchor(recentsView, state.startScroll);
        clearGestureReleaseTaskStatesForStackDismiss(recentsView);
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "abortScrollerAnimation",
                LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsCompat.setBooleanField(recentsView, "mIsBeingDragged", true);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mLastMotion",
                Math.round(resolvePrimaryDirection(recentsView, motionEvent)));
        LauncherRecentsCompat.invokeCompat(recentsView, "pageBeginTransition");
        requestParentDisallowIntercept(recentsView, true);
        logStackFlow("stackScroll:begin", recentsView, motionEvent, null);
    }

    private static void updateStackPagedScroll(
            StackScrollGestureState state,
            MotionEvent motionEvent) {
        View recentsView = state.recentsView;
        int primaryScroll = Math.round(
                state.startScroll
                        - (resolvePrimaryDirection(recentsView, motionEvent)
                        - state.startPrimary));
        setPrimaryScroll(recentsView, primaryScroll);
        syncStackScrollPageFields(recentsView, false);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
    }

    private static void finishStackPagedScroll(
            View recentsView,
            MotionEvent motionEvent,
            float primaryVelocity,
            boolean canceled) {
        clearStackScrollGesture(recentsView);
        requestParentDisallowIntercept(recentsView, false);
        releasePagedEdgeEffects(recentsView, motionEvent);
        LauncherRecentsCompat.invokeCompat(recentsView, "resetTouchState", LauncherRecentsCompat.NO_ARGS);
        int currentScroll = resolvePrimaryScroll(recentsView);
        int minScroll =
                LauncherRecentsCompat.readIntField(recentsView, "mMinScroll", currentScroll);
        int maxScroll =
                LauncherRecentsCompat.readIntField(recentsView, "mMaxScroll", currentScroll);
        int projectedScroll = currentScroll;
        if (!canceled && shouldKeepFreeScrollFling(recentsView, Math.round(primaryVelocity))) {
            projectedScroll = Math.round(
                    currentScroll - primaryVelocity * STACK_SCROLL_FLING_DISTANCE_MS);
        }
        projectedScroll = clamp(projectedScroll, minScroll, maxScroll);
        int pageCount = resolvePageCount(recentsView);
        int[] target = resolveNearestStackPageAndScroll(recentsView, projectedScroll, pageCount);
        int targetPage = target[0];
        int targetPageScroll = target[1];
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", -1);
        logStackFlow("stackScroll:finish",
                recentsView,
                motionEvent,
                "velocity=" + Math.round(primaryVelocity)
                        + " targetPage=" + targetPage
                        + " targetPageScroll=" + targetPageScroll
                        + " targetScroll=" + projectedScroll);
        animateStackScrollTo(recentsView, currentScroll, projectedScroll, primaryVelocity);
    }

    private static void animateStackScrollTo(
            View recentsView,
            int startScroll,
            int targetScroll,
            float primaryVelocity) {
        cancelStackScrollAnimation(recentsView);
        freezeStackScrollLayoutAnchorIfNeeded(recentsView, startScroll);
        if (startScroll == targetScroll) {
            setPrimaryScroll(recentsView, targetScroll);
            syncStackScrollPageFields(recentsView, false);
            LauncherRecentsCompat.invokeCompat(recentsView, "pageEndTransition");
            LauncherRecentsState.clearStackScrollFixedAnchorPage(recentsView);
            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        STACK_SCROLL_ANIMATORS.put(recentsView, animator);
        int distance = Math.abs(targetScroll - startScroll);
        int duration = Math.max(
                140,
                Math.min(420, Math.round(distance * 1000f
                        / Math.max(1600f, Math.abs(primaryVelocity)))));
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            int scroll = Math.round(
                    LauncherRecentsLayoutEngine.lerp(startScroll, targetScroll, progress));
            setPrimaryScroll(recentsView, scroll);
            syncStackScrollPageFields(recentsView, false);
            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                STACK_SCROLL_ANIMATORS.remove(recentsView);
                if (canceled) {
                    LauncherRecentsState.clearStackScrollFixedAnchorPage(recentsView);
                    return;
                }
                setPrimaryScroll(recentsView, targetScroll);
                syncStackScrollPageFields(recentsView, false);
                LauncherRecentsCompat.invokeCompat(recentsView, "pageEndTransition");
                LauncherRecentsState.clearStackScrollFixedAnchorPage(recentsView);
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
            }
        });
        animator.start();
    }

    private static void freezeStackScrollLayoutAnchorIfNeeded(View recentsView, int primaryScroll) {
        if (LauncherRecentsState.getStackScrollFixedAnchorPage(recentsView) == null) {
            freezeStackScrollLayoutAnchor(recentsView, primaryScroll);
        }
    }

    private static void freezeStackScrollLayoutAnchor(View recentsView, int primaryScroll) {
        int[] pageAndScroll =
                resolveNearestStackPageAndScroll(recentsView, primaryScroll, resolvePageCount(recentsView));
        LauncherRecentsState.setStackScrollFixedAnchorPage(recentsView, pageAndScroll[0]);
    }

    private static void syncStackScrollPageFields(View recentsView, boolean snapToPage) {
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int[] pageAndScroll =
                resolveNearestStackPageAndScroll(recentsView, primaryScroll, resolvePageCount(recentsView));
        int nearestPage = pageAndScroll[0];
        int nearestScroll = pageAndScroll[1];
        setStackDismissPageFields(
                recentsView,
                nearestPage,
                snapToPage ? nearestPage : -1,
                snapToPage ? 0 : primaryScroll - nearestScroll);
    }

    private static int resolvePageCount(View recentsView) {
        int fallback = recentsView instanceof ViewGroup ? ((ViewGroup) recentsView).getChildCount() : 0;
        return Math.max(
                1,
                LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", fallback));
    }

    private static int[] resolveNearestStackPageAndScroll(
            View recentsView,
            int primaryScroll,
            int pageCount) {
        int nearestPage = 0;
        int nearestScroll = LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                primaryScroll,
                0);
        int nearestDistance = Math.abs(nearestScroll - primaryScroll);
        for (int i = 1; i < pageCount; i++) {
            int pageScroll = LauncherRecentsCompat.invokeInt(
                    recentsView,
                    "getScrollForPage",
                    LauncherRecentsCompat.INT_ARG,
                    primaryScroll,
                    i);
            int distance = Math.abs(pageScroll - primaryScroll);
            if (distance < nearestDistance) {
                nearestPage = i;
                nearestScroll = pageScroll;
                nearestDistance = distance;
            }
        }
        return new int[]{nearestPage, nearestScroll};
    }

    private static void clearStackScrollGesture(View recentsView) {
        StackScrollGestureState state = STACK_SCROLL_GESTURES.remove(recentsView);
        if (state != null) {
            state.recycleVelocityTracker();
        }
    }

    private static void cancelStackScrollAnimation(View recentsView) {
        ValueAnimator animator = STACK_SCROLL_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void suppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        logStackFlow("pagedRelease:suppress", recentsView, motionEvent, null);
        clearRecentsDeferredSnap(recentsView);
        if (motionEvent != null && motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
            if (startUnsnappedFlingIfNeeded(recentsView, motionEvent)) {
                LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
                return;
            }
        } else {
            LauncherRecentsCompat.invokeCompat(
                    recentsView,
                    "abortScrollerAnimation",
                    LauncherRecentsCompat.NO_ARGS);
        }
        releasePagedEdgeEffects(recentsView, motionEvent);
        LauncherRecentsCompat.invokeCompat(recentsView, "resetTouchState", LauncherRecentsCompat.NO_ARGS);
        LauncherRecentsState.clearStackScrollFixedAnchorPage(recentsView);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
    }

    private static boolean startUnsnappedFlingIfNeeded(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        Object velocityTrackerValue =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mVelocityTracker");
        if (!(velocityTrackerValue instanceof VelocityTracker)) {
            logStackFlow("pagedRelease:fling:noVelocityTracker",
                    recentsView, motionEvent, null);
            return false;
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
        finishStackPagedScroll(recentsView, motionEvent, primaryVelocity, false);
        return true;
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

    private static float resolvePrimaryDirection(View recentsView, MotionEvent motionEvent) {
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryDirection",
                new Class<?>[]{MotionEvent.class, int.class},
                motionEvent,
                0);
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return isPrimaryScrollHorizontal(recentsView) ? motionEvent.getX() : motionEvent.getY();
    }

    private static float resolveSecondaryDirection(View recentsView, MotionEvent motionEvent) {
        return isPrimaryScrollHorizontal(recentsView) ? motionEvent.getY() : motionEvent.getX();
    }

    private static void setPrimaryScroll(View recentsView, int primaryScroll) {
        scrollStackDismissTo(recentsView, isPrimaryScrollHorizontal(recentsView), primaryScroll);
    }

    private static void scrollStackDismissTo(
            View recentsView,
            boolean primaryScrollHorizontal,
            int primaryScroll) {
        if (primaryScrollHorizontal) {
            recentsView.scrollTo(primaryScroll, recentsView.getScrollY());
        } else {
            recentsView.scrollTo(recentsView.getScrollX(), primaryScroll);
        }
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
