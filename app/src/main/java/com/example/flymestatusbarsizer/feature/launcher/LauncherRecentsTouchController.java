package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
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
import java.util.WeakHashMap;

final class LauncherRecentsTouchController {
    private static final String TASK_VIEW_DISMISS_TOUCH_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewDismissTouchController";
    private static final String TASK_VIEW_TOUCH_CONTROLLER_DEPRECATED_CLASS =
            "com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchControllerDeprecated";
    private static final long STACK_DISMISS_SUCCESS_ANIM_MS = 180L;
    private static final long STACK_DISMISS_CANCEL_ANIM_MS = 320L;
    private static final float STACK_DISMISS_VERTICAL_DOMINANCE = 1.2f;
    private static final float STACK_DISMISS_MIN_FLING_VELOCITY = -1200f;
    private static final ThreadLocal<Boolean> TASK_DISMISS_VISIBILITY_BYPASS =
            new ThreadLocal<>();
    private static final WeakHashMap<View, StackDismissGestureState> STACK_DISMISS_GESTURES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> STACK_DISMISS_LAYOUT_OFFSETS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SILENT_NATIVE_DISMISS_RECENTS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, SilentNativeDismissAnchor> SILENT_NATIVE_DISMISS_ANCHORS =
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
                        && thisObject instanceof View
                        && shouldKeepStackDismissGestureAwayFromPagedView(
                        (View) thisObject,
                        motionEvent)) {
                    releasePagedTouchForStackDismiss((View) thisObject);
                    return false;
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
                    if (shouldKeepStackDismissGestureAwayFromPagedView(
                            recentsView,
                            motionEvent)) {
                        releasePagedTouchForStackDismiss(recentsView);
                        return false;
                    }
                    if (LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                            && shouldSuppressPagedRelease(recentsView, motionEvent)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                        suppressPagedRelease(recentsView, motionEvent);
                        return true;
                    }
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
                        motionEvent);
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
                        motionEvent);
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
                        restoreFrozenStackDismissTaskStates(recentsView);
                        clearStackDismissLayoutOffsets();
                        recentsView.invalidate();
                        recentsView.post(() -> finishSilentNativeDismiss(recentsView));
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

    private static boolean isSilentNativeDismissActive(View recentsView) {
        return Boolean.TRUE.equals(SILENT_NATIVE_DISMISS_RECENTS.get(recentsView))
                && LauncherRecentsLayoutEngine.shouldDeferStackLayoutForAppToRecents(recentsView);
    }

    static boolean shouldSuppressNativeDismissTranslation(View recentsView) {
        return isSilentNativeDismissActive(recentsView);
    }

    static boolean isStackDismissPostRemoveAnimationActive(View recentsView) {
        return false;
    }

    private static boolean shouldSuppressNativeTaskDismissTransform(View taskView) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        return isSilentNativeDismissActive(recentsView);
    }

    static boolean shouldBypassStackDismissScrollSuppression() {
        return false;
    }

    static boolean shouldSuppressStackDismissPageMutation(Object thisObject) {
        return thisObject instanceof View
                && LauncherRecentsCompat.isRecentsViewObject(thisObject)
                && isSilentNativeDismissActive((View) thisObject);
    }

    static boolean handleStackDismissSetCurrentPage(Object thisObject) {
        if (!shouldSuppressStackDismissPageMutation(thisObject)) {
            return false;
        }
        View recentsView = (View) thisObject;
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (pageCount > 0) {
            int page = resolveNearestStackDismissPageForScroll(recentsView, pageCount);
            setStackDismissCurrentPageKeepingScroll(recentsView, page);
        }
        clearRecentsDeferredSnap(recentsView);
        return true;
    }

    private static Boolean handleStackDismissControllerTouch(
            Object controller,
            MotionEvent motionEvent) {
        View recentsView = resolveControllerRecentsView(controller);
        if (recentsView == null || motionEvent == null) {
            return null;
        }
        if (!LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
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

    private static Boolean handleStackDismissTouch(View recentsView, MotionEvent motionEvent) {
        int action = motionEvent.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            clearStackDismissGesture(recentsView, true);
            View taskView = findStackTaskUnderRawPoint(
                    recentsView,
                    motionEvent.getRawX(),
                    motionEvent.getRawY());
            if (!isStackDismissTaskCandidate(recentsView, taskView)) {
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
            clearStackDismissGesture(recentsView, true);
            return null;
        }
        state.addMovement(motionEvent);

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = motionEvent.getRawX() - state.downRawX;
            float dy = motionEvent.getRawY() - state.downRawY;
            int touchSlop = ViewConfiguration.get(recentsView.getContext()).getScaledTouchSlop();
            if (!state.dragging) {
                if (isStackDismissDragStart(dx, dy, touchSlop)) {
                    beginStackDismissDrag(state, motionEvent);
                } else if (isHorizontalGesture(dx, dy, touchSlop)) {
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

    private static boolean isStackDismissDragStart(float dx, float dy, int touchSlop) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        return dy < -touchSlop
                && absDy > touchSlop
                && absDy >= absDx * STACK_DISMISS_VERTICAL_DOMINANCE;
    }

    private static boolean isHorizontalGesture(float dx, float dy, int touchSlop) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        return absDx > touchSlop && absDx > absDy;
    }

    private static void beginStackDismissDrag(
            StackDismissGestureState state,
            MotionEvent motionEvent) {
        state.dragging = true;
        state.cancelAnimator();
        state.taskView.animate().cancel();
        LauncherRecentsState.trackRecentsView(state.recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(state.recentsView);
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
        applyStackDismissProgress(state, state.currentDismissTranslationY);
    }

    private static void updateStackDismissDrag(
            StackDismissGestureState state,
            MotionEvent motionEvent) {
        float dy = motionEvent.getRawY() - state.downRawY;
        float translationY = state.startDismissTranslationY + (dy < 0f ? dy : dy * 0.18f);
        state.currentDismissTranslationY = Math.min(state.startDismissTranslationY, translationY);
        applyStackDismissProgress(state, state.currentDismissTranslationY);
    }

    private static void finishStackDismissGesture(
            StackDismissGestureState state,
            boolean canceled) {
        STACK_DISMISS_GESTURES.remove(state.recentsView);
        requestParentDisallowIntercept(state.recentsView, false);
        if (canceled) {
            animateStackDismissCancel(state);
            state.recycleVelocityTracker();
            return;
        }
        float velocityY = state.computeYVelocity();
        boolean dismiss = state.currentDismissTranslationY <= -resolveStackDismissThreshold(state)
                || velocityY <= STACK_DISMISS_MIN_FLING_VELOCITY;
        if (dismiss) {
            animateStackDismissSuccess(state);
        } else {
            animateStackDismissCancel(state);
        }
        state.recycleVelocityTracker();
    }

    private static void animateStackDismissSuccess(StackDismissGestureState state) {
        float start = state.currentDismissTranslationY;
        float end = -resolveStackDismissDistance(state);
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        state.animator = animator;
        animator.setDuration(STACK_DISMISS_SUCCESS_ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslationY = value;
            applyStackDismissProgress(state, value);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                applyStackDismissProgress(state, end);
                animateStackDismissReflow(state);
            }
        });
        animator.start();
    }

    private static void animateStackDismissReflow(StackDismissGestureState state) {
        if (state.siblingMoves.isEmpty()) {
            finishStackDismissAfterReflow(state);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        state.animator = animator;
        animator.setDuration(STACK_DISMISS_SUCCESS_ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.35f));
        animator.addUpdateListener(animation ->
                applyStackDismissReflowProgress(state, (Float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                if (canceled) {
                    return;
                }
                applyStackDismissReflowProgress(state, 1f);
                finishStackDismissAfterReflow(state);
            }
        });
        animator.start();
    }

    private static void finishStackDismissAfterReflow(StackDismissGestureState state) {
        boolean dismissed = invokeNativeDismissTaskView(state.recentsView, state.taskView, state);
        if (!dismissed) {
            resetStackDismissVisuals(state);
        }
    }

    private static void animateStackDismissCancel(StackDismissGestureState state) {
        float start = state.currentDismissTranslationY;
        ValueAnimator animator = ValueAnimator.ofFloat(start, state.startDismissTranslationY);
        state.animator = animator;
        animator.setDuration(STACK_DISMISS_CANCEL_ANIM_MS);
        animator.setInterpolator(new OvershootInterpolator(0.85f));
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            state.currentDismissTranslationY = value;
            applyStackDismissProgress(state, value);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.animator = null;
                resetStackDismissVisuals(state);
            }
        });
        animator.start();
    }

    private static void applyStackDismissProgress(
            StackDismissGestureState state,
            float dismissTranslationY) {
        setStackDismissTranslationY(state.taskView, dismissTranslationY);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(state.recentsView);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        state.recentsView.invalidate();
    }

    private static void applyStackDismissReflowProgress(
            StackDismissGestureState state,
            float progress) {
        for (int i = 0; i < state.siblingMoves.size(); i++) {
            StackDismissSiblingMove move = state.siblingMoves.get(i);
            STACK_DISMISS_LAYOUT_OFFSETS.put(
                    move.taskView,
                    move.targetOffsetPx * progress);
        }
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(state.recentsView);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        state.recentsView.invalidate();
    }

    private static void prepareStackDismissSiblingMoves(StackDismissGestureState state) {
        state.siblingMoves.clear();
        clearStackDismissLayoutOffsets();
        int dismissedIndex = findTaskViewIndex(state.recentsView, state.taskView);
        if (dismissedIndex < 0) {
            return;
        }
        int targetPage = resolveSilentNativeDismissAnchorPage(state.recentsView, state.taskView);
        int targetScrollX = resolveStackDismissScrollForPage(state.recentsView, targetPage);
        int taskViewCount =
                LauncherRecentsCompat.invokeInt(state.recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(state.recentsView, i);
            if (!isStackDismissTaskCandidate(state.recentsView, taskView)
                    || taskView == state.taskView) {
                continue;
            }
            int projectedIndex = i > dismissedIndex ? i - 1 : i;
            float currentRawOffset = resolveStackDismissRawOffset(state.recentsView, i);
            float targetRawOffset = resolveStackDismissRawOffsetAtScroll(
                    state.recentsView,
                    projectedIndex,
                    targetScrollX);
            float targetOffsetPx = targetRawOffset - currentRawOffset;
            if (Math.abs(targetOffsetPx) > 0.5f) {
                state.siblingMoves.add(new StackDismissSiblingMove(
                        taskView,
                        targetOffsetPx));
            }
        }
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
            int scrollX) {
        return resolveStackDismissRawOffset(recentsView, taskIndex)
                + recentsView.getScrollX()
                - scrollX;
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
        setStackDismissTranslationY(state.taskView, state.startDismissTranslationY);
        LauncherRecentsTaskVisuals.setStableAlpha(state.taskView, state.originalStableAlpha);
        LauncherRecentsTaskVisuals.setTranslationZ(state.taskView, state.originalTranslationZ);
        LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(state.recentsView);
    }

    private static void clearStackDismissLayoutOffsets() {
        STACK_DISMISS_LAYOUT_OFFSETS.clear();
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
        float height = state.taskView.getHeight() > 0 ? state.taskView.getHeight() : fallback;
        return LauncherRecentsLayoutEngine.clamp(
                height * 0.24f,
                FlymeStatusBarSizer.dp(state.recentsView.getContext(), 96),
                FlymeStatusBarSizer.dp(state.recentsView.getContext(), 220));
    }

    private static float resolveStackDismissDistance(StackDismissGestureState state) {
        float taskTop = Math.max(0f, state.taskView.getY() - state.recentsView.getScrollY());
        float taskHeight = Math.max(1f, state.taskView.getHeight());
        return Math.max(
                taskTop + taskHeight + FlymeStatusBarSizer.dp(state.recentsView.getContext(), 48),
                state.recentsView.getHeight() * 0.72f);
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
            View taskView,
            StackDismissGestureState state) {
        Class<?> taskViewClass = resolveTaskViewBaseClass(taskView);
        rememberSilentNativeDismissAnchor(recentsView, taskView, state);
        if (taskViewClass != null && invokeSilentNativeDismissTaskView(
                recentsView,
                taskView,
                taskViewClass)) {
            return true;
        }
        SILENT_NATIVE_DISMISS_RECENTS.put(recentsView, Boolean.TRUE);
        if (taskViewClass != null
                && LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "dismissTaskView",
                new Class<?>[]{taskViewClass, boolean.class, boolean.class},
                taskView,
                false,
                true)) {
            return true;
        }
        Object taskIdsObject = LauncherRecentsCompat.invokeCompat(
                taskView,
                "getTaskIds",
                LauncherRecentsCompat.NO_ARGS);
        if (!(taskIdsObject instanceof int[]) || ((int[]) taskIdsObject).length == 0) {
            SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
            SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
            return false;
        }
        int taskId = ((int[]) taskIdsObject)[0];
        boolean dismissed = LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "dismissTask",
                new Class<?>[]{int.class, boolean.class, boolean.class},
                taskId,
                false,
                true);
        if (!dismissed) {
            SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
            SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
        }
        return dismissed;
    }

    private static boolean invokeSilentNativeDismissTaskView(
            View recentsView,
            View taskView,
            Class<?> taskViewClass) {
        if (recentsView == null || taskView == null || taskViewClass == null) {
            return false;
        }
        try {
            ClassLoader loader = recentsView.getClass().getClassLoader();
            Class<?> pendingAnimationClass =
                    Class.forName("com.android.launcher3.anim.PendingAnimation", false, loader);
            Constructor<?> constructor = pendingAnimationClass.getDeclaredConstructor(long.class);
            constructor.setAccessible(true);
            Object pendingAnimation = constructor.newInstance(1L);
            boolean created = LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "createTaskDismissAnimation",
                    new Class<?>[]{
                            pendingAnimationClass,
                            taskViewClass,
                            boolean.class,
                            boolean.class,
                            long.class,
                            boolean.class,
                            boolean.class
                    },
                    pendingAnimation,
                    taskView,
                    false,
                    true,
                    1L,
                    false,
                    true);
            if (!created) {
                return false;
            }
            SILENT_NATIVE_DISMISS_RECENTS.put(recentsView, Boolean.TRUE);
            boolean started = LauncherRecentsCompat.invokeMethodReflectively(
                    recentsView,
                    "runDismissAnimation",
                    new Class<?>[]{pendingAnimationClass},
                    pendingAnimation);
            if (!started) {
                SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
                LauncherRecentsCompat.writeField(recentsView, "mPendingAnimation", null);
            }
            return started;
        } catch (Throwable ignored) {
            SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
            return false;
        }
    }

    private static void rememberSilentNativeDismissAnchor(
            View recentsView,
            View taskView,
            StackDismissGestureState state) {
        if (recentsView == null) {
            return;
        }
        SILENT_NATIVE_DISMISS_ANCHORS.put(
                recentsView,
                new SilentNativeDismissAnchor(captureFrozenStackDismissTaskStates(state)));
    }

    private static int resolveSilentNativeDismissAnchorPage(View recentsView, View taskView) {
        int taskIndex = findTaskViewIndex(recentsView, taskView);
        int pageCount = LauncherRecentsCompat.invokeInt(recentsView, "getPageCount", 0);
        if (taskIndex >= 0) {
            return pageCount > 1
                    ? Math.max(0, Math.min(taskIndex - 1, pageCount - 2))
                    : 0;
        }
        int currentPage = LauncherRecentsCompat.readIntField(recentsView, "mCurrentPage", 0);
        return pageCount > 1
                ? Math.max(0, Math.min(currentPage, pageCount - 2))
                : 0;
    }

    private static void finishSilentNativeDismiss(View recentsView) {
        if (!isSilentNativeDismissActive(recentsView)) {
            SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
            return;
        }
        clearNativeDismissTransforms(recentsView);
        SILENT_NATIVE_DISMISS_RECENTS.remove(recentsView);
        SILENT_NATIVE_DISMISS_ANCHORS.remove(recentsView);
    }

    private static ArrayList<FrozenStackDismissTaskState> captureFrozenStackDismissTaskStates(
            StackDismissGestureState state) {
        ArrayList<FrozenStackDismissTaskState> taskStates = new ArrayList<>();
        if (state == null || state.recentsView == null) {
            return taskStates;
        }
        int taskViewCount = LauncherRecentsCompat.invokeInt(state.recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(state.recentsView, i);
            if (!isStackDismissTaskCandidate(state.recentsView, taskView)
                    || taskView == state.taskView) {
                continue;
            }
            taskStates.add(new FrozenStackDismissTaskState(
                    taskView,
                    captureStackDismissTaskTransform(taskView)));
        }
        return taskStates;
    }

    private static StackDismissTaskTransform captureStackDismissTaskTransform(View taskView) {
        return new StackDismissTaskTransform(
                LauncherRecentsCompat.readFloatField(taskView, "horizontalOffsetTranslationX", 0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f),
                LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f),
                LauncherRecentsCompat.readFloatField(
                        taskView,
                        "boxTranslationY",
                        LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView)),
                LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f),
                LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                taskView.getTranslationZ());
    }

    private static void restoreFrozenStackDismissTaskStates(View recentsView) {
        SilentNativeDismissAnchor anchor = SILENT_NATIVE_DISMISS_ANCHORS.get(recentsView);
        if (anchor == null || anchor.taskStates.isEmpty()) {
            return;
        }
        for (int i = 0; i < anchor.taskStates.size(); i++) {
            FrozenStackDismissTaskState taskState = anchor.taskStates.get(i);
            if (findTaskViewIndex(recentsView, taskState.taskView) < 0
                    || !isStackDismissTaskCandidate(recentsView, taskState.taskView)) {
                continue;
            }
            applyStackDismissTaskTransform(taskState.taskView, taskState.transform);
        }
    }

    private static void applyStackDismissTaskTransform(
            View taskView,
            StackDismissTaskTransform transform) {
        LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(
                taskView,
                transform.horizontalOffsetX);
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, transform.taskOffsetX);
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, transform.taskOffsetY);
        LauncherRecentsTaskVisuals.setBoxTranslationY(taskView, transform.boxTranslationY);
        LauncherRecentsTaskVisuals.setNonGridScale(taskView, transform.nonGridScale);
        LauncherRecentsTaskVisuals.setStableAlpha(taskView, transform.stableAlpha);
        LauncherRecentsTaskVisuals.setTranslationZ(taskView, transform.translationZ);
    }

    private static void setStackDismissCurrentPageKeepingScroll(View recentsView, int page) {
        int pageScroll = resolveStackDismissScrollForPage(recentsView, page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mCurrentScrollOverPage", page);
        LauncherRecentsCompat.setIntField(recentsView, "mNextPage", page);
        LauncherRecentsCompat.setIntField(
                recentsView,
                "mCurrentPageScrollDiff",
                recentsView.getScrollX() - pageScroll);
    }

    private static int resolveStackDismissScrollForPage(View recentsView, int page) {
        return LauncherRecentsCompat.invokeInt(
                recentsView,
                "getScrollForPage",
                LauncherRecentsCompat.INT_ARG,
                recentsView.getScrollX(),
                page);
    }

    private static int resolveNearestStackDismissPageForScroll(View recentsView, int pageCount) {
        int scrollX = recentsView.getScrollX();
        int nearestPage = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < pageCount; i++) {
            int distance = Math.abs(resolveStackDismissScrollForPage(recentsView, i) - scrollX);
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

    private static boolean shouldExposeStackTaskForDismissVisibility(
            View recentsView,
            View taskView) {
        if (!Boolean.TRUE.equals(TASK_DISMISS_VISIBILITY_BYPASS.get())
                || recentsView == null
                || taskView == null
                || LauncherRecentsCompat.isDesktopTask(taskView)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return false;
        }
        if (!(recentsView instanceof ViewGroup)
                || ((ViewGroup) recentsView).indexOfChild(taskView) < 0) {
            return false;
        }
        return taskView.getVisibility() == View.VISIBLE
                && taskView.getAlpha() > 0.01f
                && taskView.getWidth() > 0
                && taskView.getHeight() > 0;
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
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        return dy < -touchSlop
                && absDy > touchSlop
                && absDy >= absDx * 0.8f;
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

    private static boolean shouldSuppressPagedRelease(
            View recentsView,
            MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        int action = motionEvent.getActionMasked();
        return (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && LauncherRecentsCompat.invokeBoolean(recentsView, "isHandlingTouch", false);
    }

    private static void suppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
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
            startPagedSpringBack(recentsView, primaryScroll, minScroll, maxScroll);
            return;
        }
        if (!shouldKeepFreeScrollFling(recentsView, primaryVelocity)) {
            return;
        }
        Object scroller = LauncherRecentsCompat.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        setScrollerFriction(scroller, 0.03f);
        if (!startScrollerFling(
                recentsView,
                scroller,
                primaryScroll,
                primaryVelocity,
                minScroll,
                maxScroll)) {
            return;
        }
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
        return value instanceof Integer ? (Integer) value : recentsView.getScrollX();
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
        int overX = Math.round(recentsView.getWidth() * 0.5f * 0.07f);
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

    private static final class StackDismissGestureState {
        final View recentsView;
        final View taskView;
        final ArrayList<StackDismissSiblingMove> siblingMoves = new ArrayList<>();
        final float downRawX;
        final float downRawY;
        final float startDismissTranslationY;
        final float originalStableAlpha;
        final float originalTranslationZ;
        VelocityTracker velocityTracker;
        ValueAnimator animator;
        boolean dragging;
        float currentDismissTranslationY;

        StackDismissGestureState(View recentsView, View taskView, MotionEvent motionEvent) {
            this.recentsView = recentsView;
            this.taskView = taskView;
            this.downRawX = motionEvent.getRawX();
            this.downRawY = motionEvent.getRawY();
            this.startDismissTranslationY =
                    LauncherRecentsCompat.readFloatField(taskView, "dismissTranslationY", 0f);
            this.currentDismissTranslationY = this.startDismissTranslationY;
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

        float computeYVelocity() {
            if (velocityTracker == null) {
                return 0f;
            }
            velocityTracker.computeCurrentVelocity(1000);
            return velocityTracker.getYVelocity();
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

        StackDismissSiblingMove(View taskView, float targetOffsetPx) {
            this.taskView = taskView;
            this.targetOffsetPx = targetOffsetPx;
        }
    }

    private static final class SilentNativeDismissAnchor {
        final ArrayList<FrozenStackDismissTaskState> taskStates;

        SilentNativeDismissAnchor(ArrayList<FrozenStackDismissTaskState> taskStates) {
            this.taskStates = taskStates;
        }
    }

    private static final class FrozenStackDismissTaskState {
        final View taskView;
        final StackDismissTaskTransform transform;

        FrozenStackDismissTaskState(
                View taskView,
                StackDismissTaskTransform transform) {
            this.taskView = taskView;
            this.transform = transform;
        }
    }

    private static final class StackDismissTaskTransform {
        final float horizontalOffsetX;
        final float taskOffsetX;
        final float taskOffsetY;
        final float boxTranslationY;
        final float nonGridScale;
        final float stableAlpha;
        final float translationZ;

        StackDismissTaskTransform(
                float horizontalOffsetX,
                float taskOffsetX,
                float taskOffsetY,
                float boxTranslationY,
                float nonGridScale,
                float stableAlpha,
                float translationZ) {
            this.horizontalOffsetX = horizontalOffsetX;
            this.taskOffsetX = taskOffsetX;
            this.taskOffsetY = taskOffsetY;
            this.boxTranslationY = boxTranslationY;
            this.nonGridScale = nonGridScale;
            this.stableAlpha = stableAlpha;
            this.translationZ = translationZ;
        }
    }

    static void clearRecentsDeferredSnap(View recentsView) {
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
