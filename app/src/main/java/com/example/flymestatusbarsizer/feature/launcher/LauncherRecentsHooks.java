package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class LauncherRecentsHooks {
    private static final Class<?>[] NO_ARGS = new Class[0];
    private static final Class<?>[] INT_ARG = new Class[]{int.class};
    private static final Class<?>[] FLOAT_ARG = new Class[]{float.class};
    private static final Class<?>[] BOOLEAN_ARG = new Class[]{boolean.class};
    private static final String LAUNCHER_RECENTS_VIEW_CLASS =
            "com.android.quickstep.views.LauncherRecentsView";
    private static final String PAGED_VIEW_CLASS = "com.android.launcher3.PagedView";
    private static final String PAGED_ORIENTATION_HANDLER_CLASS =
            "com.android.launcher3.touch.PagedOrientationHandler";
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView";
    private static final String TASK_VIEW_SIMULATOR_CLASS =
            "com.android.quickstep.util.TaskViewSimulator";
    private static final String TASK_VIEW_UTILS_CLASS = "com.android.quickstep.TaskViewUtils";
    private static final long BLANK_TAP_HOME_EXIT_DURATION_MS = 360L;
    private static final float BLANK_TAP_HOME_EXIT_SCALE_DELTA = 0.07f;
    private static final float BLANK_TAP_HOME_EXIT_TRAVEL_RATIO = 0.90f;
    private static final float STACK_DEPTH_CURVE_POWER = 0.82f;
    private static final float STACK_FRONT_VISIBLE_RATIO = 0.50f;
    private static final float STACK_FRONT_SHIFT_START_PROGRESS = 0.12f;
    private static final float STACK_FRONT_REVEAL_CURVE_POWER = 0.72f;
    private static final float STACK_ENTRY_LIFT_RATIO = 0.05f;
    private static final float STACK_BACK_SPREAD_RATIO = 0.14f;
    private static final float STACK_MIN_OVERLAP_RATIO = 0.20f;
    private static final float STACK_SCALE_STEP = 0.065f;
    private static final float STACK_MIN_SCALE = 0.80f;
    private static final float STACK_LEFT_INSET_RATIO = 0.05f;
    private static final float MAX_STACK_LAYERS = 3.0f;
    private static final long TASK_LAUNCH_HANDOFF_DURATION_MS = 88L;
    private static final long TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS = 52L;
    private static final long TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS = 1200L;
    private static final float TASK_LAUNCH_REAR_PROMOTE_FRACTION = 0.42f;
    private static final float TASK_LAUNCH_SIBLING_END_ALPHA = 0.16f;
    private static final DecelerateInterpolator BLANK_TAP_HOME_EXIT_INTERPOLATOR =
            new DecelerateInterpolator(1.6f);
    private static final WeakHashMap<View, Boolean> TRACKED_RECENTS_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, ValueAnimator> ACTIVE_HOME_EXIT_ANIMATORS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ValueAnimator> ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> BLANK_TAP_HOME_EXIT_PROGRESS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, LaunchHandoffState> ACTIVE_TASK_LAUNCH_HANDOFFS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> BYPASS_TASK_CLICK_INTERCEPTION =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TASK_LAUNCH_REQUEST_STARTED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_NON_GRID_SCALES = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_BOX_TRANSLATION_YS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_XS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TASK_OFFSET_YS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_HORIZONTAL_OFFSET_XS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_NON_GRID_SCALES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_BOX_TRANSLATION_YS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_STABLE_ALPHAS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> LAST_STOCK_TRANSLATION_ZS =
            new WeakHashMap<>();
    private static final ThreadLocal<TaskLaunchSimulatorTranslationContext>
            ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION = new ThreadLocal<>();
    private static final ThreadLocal<View> ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS =
            new ThreadLocal<>();
    private static volatile Handler mainHandler;

    private static final class LaunchHandoffState {
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

    private static final class TaskLaunchSimulatorTranslationContext {
        final View recentsView;
        final View taskView;

        TaskLaunchSimulatorTranslationContext(View recentsView, View taskView) {
            this.recentsView = recentsView;
            this.taskView = taskView;
        }
    }

    private static final class TaskLaunchTaskRectTranslation {
        final int translationX;
        final int translationY;

        TaskLaunchTaskRectTranslation(int translationX, int translationY) {
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }

    private LauncherRecentsHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewConstructors(module, loader);
        hookRecentsViewMethod(module, loader, "updatePageOffsetsForFlyme");
        hookRecentsViewMethod(module, loader, "updatePageScales");
        hookRecentsViewOnLayout(module, loader);
        hookRecentsViewOnScrollChanged(module, loader);
        hookRecentsViewContentAlpha(module, loader);
        hookRecentsViewResetTaskVisuals(module, loader);
        hookRecentsViewDraw(module, loader);
        hookRecentsViewStartHome(module, loader);
        hookRecentsViewFreeScrollSettling(module, loader);
        hookRecentsViewPrepareGestureEndAnimation(module, loader);
        hookRecentsViewGestureAnimationEnd(module, loader);
        hookRecentsViewWindowVisibilityChanged(module, loader);
        hookRecentsViewDetachedFromWindow(module, loader);
        hookRecentsViewNotifyHandleActionUp(module, loader);
        hookPagedViewOnTouchEvent(module, loader);
        hookPagedViewSetCurrentPageForTaskLaunch(module, loader);
        hookPagedViewUpdateCurrentPageScrollForTaskLaunch(module, loader);
        hookPagedViewSnapToDestination(module, loader);
        hookPagedViewSnapToPageForTaskLaunch(module, loader);
        hookPagedViewScrollToForTaskLaunch(module, loader);
        hookTaskViewClick(module, loader);
        hookTaskViewLaunchWithAnimation(module, loader);
        hookTaskViewPressScale(module, loader);
        hookTaskViewUtilsCreateRecentsWindowAnimator(module, loader);
        hookTaskViewUtilsLaunchFrameCallback(module, loader);
        hookTaskViewSimulatorSetTaskRectTranslation(module, loader);
        hookRecentsViewCreateTaskLaunchAnimation(module, loader);
        hookRecentsViewCreateAdjacentPageAnimForTaskLaunch(module, loader);
        hookRecentsViewUpdateScrollSynchronously(module, loader);
        hookViewScrollByForTaskLaunch(module);
        hookViewScrollToForTaskLaunch(module);
    }

    public static void refreshTrackedViews() {
        Runnable refreshRunnable = () -> {
            ArrayList<View> views = new ArrayList<>(TRACKED_RECENTS_VIEWS.keySet());
            for (View recentsView : views) {
                if (recentsView == null) {
                    continue;
                }
                if (isTaskLaunchLayoutFrozen(recentsView)) {
                    continue;
                }
                prepareRecentsView(recentsView);
                if (shouldUseStackLayout(recentsView)) {
                    applyStackLayout(recentsView, false);
                } else {
                    reapplyOriginalTransforms(recentsView);
                }
            }
        };
        Handler handler = ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(refreshRunnable);
        } else {
            refreshRunnable.run();
        }
    }

    private static void hookRecentsViewConstructors(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LAUNCHER_RECENTS_VIEW_CLASS, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof View) {
                        View recentsView = (View) thisObject;
                        trackRecentsView(recentsView);
                        recentsView.post(() -> {
                            prepareRecentsView(recentsView);
                            captureStockTaskStates(recentsView);
                            applyStackLayout(recentsView, false);
                        });
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook LauncherRecentsView constructors",
                    t);
        }
    }

    private static void hookRecentsViewMethod(
            FlymeStatusBarSizer module, ClassLoader loader, String methodName) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (shouldSuppressStockTaskLaunchTransformMethod(recentsView, methodName)) {
                        return null;
                    }
                }
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView." + methodName,
                    t);
        }
    }

    private static void hookRecentsViewOnLayout(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onLayout",
                    boolean.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onLayout",
                    t);
        }
    }

    private static void hookRecentsViewOnScrollChanged(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onScrollChanged", int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onScrollChanged",
                    t);
        }
    }

    private static void hookRecentsViewContentAlpha(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setContentAlpha", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        captureStockTaskStates(recentsView);
                        applyStackLayout(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setContentAlpha",
                    t);
        }
    }

    private static void hookRecentsViewDraw(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("draw", Canvas.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.draw",
                    t);
        }
    }

    private static void hookRecentsViewStartHome(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("startHome", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldAnimateBlankTapHomeExit(recentsView)) {
                        if (invokeBoolean(recentsView, "canStartHomeSafely", false)) {
                            startBlankTapHomeExitAnimation(recentsView);
                            return null;
                        }
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.startHome",
                    t);
        }
    }

    private static void hookRecentsViewFreeScrollSettling(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onNotSnappingToPageInFreeScroll");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
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

    private static void hookRecentsViewPrepareGestureEndAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> gestureEndTargetClass =
                    Class.forName("com.android.quickstep.GestureState$GestureEndTarget", false, loader);
            Class<?> remoteTargetHandleArrayClass =
                    Class.forName("[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;", false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onPrepareGestureEndAnimation",
                    AnimatorSet.class,
                    gestureEndTargetClass,
                    remoteTargetHandleArrayClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object endTarget = chain.getArg(1);
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)
                            && isRecentsGestureEndTarget(endTarget)) {
                        switchRunningTaskToScreenshot(recentsView);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onPrepareGestureEndAnimation",
                    t);
        }
    }

    private static void hookRecentsViewGestureAnimationEnd(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onGestureAnimationEnd");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object endTarget = FlymeStatusBarSizer.getFieldCompat(thisObject, "mCurrentGestureEndTarget");
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldUseStackLayout(recentsView)
                            && isRecentsGestureEndTarget(endTarget)) {
                        finishRunningTaskReleaseToStack(recentsView);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onGestureAnimationEnd",
                    t);
        }
    }

    private static void hookRecentsViewResetTaskVisuals(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("resetTaskVisuals");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (shouldSuppressStockTaskLaunchVisualReset(recentsView)) {
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.resetTaskVisuals",
                    t);
        }
    }

    private static void hookRecentsViewWindowVisibilityChanged(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onWindowVisibilityChanged", int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    int visibility = chain.getArg(0) instanceof Integer ? (Integer) chain.getArg(0) : 0;
                    if (visibility != View.VISIBLE && isTaskLaunchLayoutFrozen(recentsView)) {
                        clearTaskLaunchHandoff(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onWindowVisibilityChanged",
                    t);
        }
    }

    private static void hookRecentsViewDetachedFromWindow(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onDetachedFromWindow");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (isTaskLaunchLayoutFrozen(recentsView)) {
                        clearTaskLaunchHandoff(recentsView, false);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onDetachedFromWindow",
                    t);
        }
    }

    private static void hookPagedViewOnTouchEvent(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                MotionEvent motionEvent = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                if (isRecentsViewObject(thisObject)
                        && thisObject instanceof View
                        && motionEvent != null) {
                    View recentsView = (View) thisObject;
                    if (shouldUseStackLayout(recentsView)
                            && shouldSuppressPagedRelease(recentsView, motionEvent)) {
                        trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
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
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass = Class.forName(TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("notifyHandleActionUp", taskViewClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (thisObject instanceof View && arg0 instanceof View) {
                    View recentsView = (View) thisObject;
                    View taskView = (View) arg0;
                    if (shouldSuppressTaskHandleActionUp(recentsView, taskView)) {
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

    private static void hookPagedViewSetCurrentPageForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setCurrentPage", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        clearRecentsDeferredSnap((View) thisObject);
                    }
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.setCurrentPage",
                    t);
        }
    }

    private static void hookPagedViewUpdateCurrentPageScrollForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateCurrentPageScroll");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        clearRecentsDeferredSnap((View) thisObject);
                    }
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.updateCurrentPageScroll",
                    t);
        }
    }

    private static void hookPagedViewSnapToDestination(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("snapToDestination");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (isRecentsViewObject(thisObject) && thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    trackRecentsView(recentsView);
                    prepareRecentsView(recentsView);
                    if (shouldApplyDynamicStackLayout(recentsView)) {
                        applyStackLayout(recentsView, false);
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

    private static void hookPagedViewSnapToPageForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "snapToPage",
                    int.class,
                    int.class,
                    int.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        clearRecentsDeferredSnap((View) thisObject);
                    }
                    return false;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.snapToPage",
                    t);
        }
    }

    private static void hookPagedViewScrollToForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("scrollTo", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        clearRecentsDeferredSnap((View) thisObject);
                    }
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook PagedView.scrollTo",
                    t);
        }
    }

    private static void hookTaskViewClick(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onClick");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (consumeTaskClickBypass(taskView)) {
                        return chain.proceed();
                    }
                    View recentsView = resolveOwningRecentsView(taskView);
                    if (shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
                        if (handleTaskClickWithoutSystemAnimation(taskView, recentsView)) {
                            return null;
                        }
                        return chain.proceed();
                    }
                    if (shouldStartTaskLaunchHandoff(taskView, recentsView)) {
                        startTaskLaunchHandoff(taskView, recentsView);
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView.onClick",
                    t);
        }
    }

    private static void hookTaskViewLaunchWithAnimation(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("launchWithAnimation");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                View recentsView = null;
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    recentsView = resolveOwningRecentsView(taskView);
                    if (shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
                        prepareTaskLaunchWithoutSystemAnimation(recentsView, taskView);
                        if (launchTaskWithoutSystemAnimation(taskView, recentsView)) {
                            return null;
                        }
                    }
                    if (shouldUseStackLayout(recentsView) && !isDesktopTask(taskView)) {
                        trackRecentsView(recentsView);
                        prepareRecentsView(recentsView);
                        TASK_LAUNCH_REQUEST_STARTED.put(recentsView, Boolean.TRUE);
                        freezeTaskLaunchLayoutIfNeeded(recentsView, taskView);
                    }
                }
                Object result = chain.proceed();
                if (recentsView != null && isTaskLaunchLayoutFrozen(recentsView)) {
                    attachTaskLaunchCleanup(recentsView, result);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView.launchWithAnimation",
                    t);
        }
    }

    private static void hookTaskViewPressScale(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_CLASS, false, loader);

            Method scaleDownMethod = clazz.getDeclaredMethod("scaleDown");
            scaleDownMethod.setAccessible(true);
            module.intercept(scaleDownMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (shouldSuppressTaskPressScale(taskView)) {
                        resetTaskTouchScale(taskView);
                        return null;
                    }
                }
                return chain.proceed();
            });

            Method scaleUpMethod = clazz.getDeclaredMethod("scaleUp", boolean.class);
            scaleUpMethod.setAccessible(true);
            module.intercept(scaleUpMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (shouldSuppressTaskPressScale(taskView)) {
                        resetTaskTouchScale(taskView);
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView press scale",
                    t);
        }
    }

    private static void trackRecentsView(View recentsView) {
        if (recentsView == null) {
            return;
        }
        TRACKED_RECENTS_VIEWS.put(recentsView, Boolean.TRUE);
    }

    private static void prepareRecentsView(View recentsView) {
        if (!(recentsView instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) recentsView;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        ViewParent parent = group.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).setClipChildren(false);
        }
    }

    private static void applyStackLayout(View recentsView, boolean captureStockState) {
        if (recentsView == null) {
            return;
        }
        LaunchHandoffState launchState = ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        if (launchState != null && launchState.frozen) {
            return;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        if (!shouldUseStackLayout(config, recentsView, taskViewCount)) {
            restoreTaskTransforms(recentsView, taskViewCount);
            return;
        }

        float pageSpacing = readIntField(recentsView, "mPageSpacing", 0);
        float referenceWidth = 0f;
        float referenceHeight = 0f;
        float pageSpan = 0f;
        float[] rawOffsets = new float[taskViewCount];

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            rememberOriginalTaskState(taskView);
            rawOffsets[i] = invokeInt(
                    recentsView,
                    "getUnclampedScrollOffset",
                    INT_ARG,
                    invokeInt(recentsView, "getScrollOffset", INT_ARG, 0, i),
                    i);
            if (taskView.getWidth() > 0) {
                referenceWidth = Math.max(referenceWidth, taskView.getWidth());
                pageSpan = Math.max(pageSpan, taskView.getWidth() + pageSpacing);
            }
            if (taskView.getHeight() > 0) {
                referenceHeight = Math.max(referenceHeight, taskView.getHeight());
            }
        }

        if (referenceWidth <= 0f) {
            referenceWidth = Math.max(1, recentsView.getWidth());
        }
        if (referenceHeight <= 0f) {
            referenceHeight = Math.max(1, recentsView.getHeight());
        }
        if (pageSpan <= 1f) {
            pageSpan = referenceWidth + pageSpacing;
        }
        if (pageSpan <= 1f) {
            pageSpan = Math.max(1f, referenceWidth);
        }

        float blankTapExitProgress = readBlankTapHomeExitProgress(recentsView);
        float stackEntryProgress = resolveStackEntryProgress(recentsView);
        float stackVerticalProgress = resolveStackVerticalProgress(recentsView);
        boolean isTouchHandling = invokeBoolean(recentsView, "isHandlingTouch", false);
        float maxTranslationZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 24);
        float zStepPx = FlymeStatusBarSizer.dp(recentsView.getContext(), 8);

        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            if (isDesktopTask(taskView)) {
                restoreTaskTransform(taskView);
                continue;
            }
            if (captureStockState) {
                captureStockTaskState(taskView);
            }
            float rawOffset = rawOffsets[i];
            float dismissTranslationX = readFloatField(taskView, "dismissTranslationX", 0f);
            // Keep the stock gap-closing animation, but remap its logical page position into
            // the compressed stack so sibling cards move into the dismissed slot instead of
            // adding a second full-page horizontal shift on top of it.
            float effectiveRawOffset = rawOffset + dismissTranslationX;
            float progress = effectiveRawOffset / pageSpan;
            float taskWidth = taskView.getWidth() > 0 ? taskView.getWidth() : referenceWidth;
            float taskHeight = taskView.getHeight() > 0 ? taskView.getHeight() : referenceHeight;
            float taskCenteredLeftPx = Math.max(0f, (recentsView.getWidth() - taskWidth) * 0.5f);
            float stackBaseOffsetPx =
                    -taskCenteredLeftPx + (taskWidth * STACK_LEFT_INSET_RATIO);
            float stackFrontLeftPx = recentsView.getWidth() - (taskWidth * STACK_FRONT_VISIBLE_RATIO);
            float screenFrontOffsetPx = stackFrontLeftPx - taskCenteredLeftPx;
            float maxFrontOffsetPx = stackBaseOffsetPx
                    + (taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO));
            float stackFrontOffsetPx = Math.min(screenFrontOffsetPx, maxFrontOffsetPx);
            float stackBackSpreadPx = Math.min(
                    taskWidth * STACK_BACK_SPREAD_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 96));
            float stackEntryLiftPx = Math.min(
                    taskHeight * STACK_ENTRY_LIFT_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 40));
            float blankTapExitTravelPx = Math.max(
                    taskWidth * BLANK_TAP_HOME_EXIT_TRAVEL_RATIO,
                    FlymeStatusBarSizer.dp(recentsView.getContext(), 220));
            float stockVisibleOffset = effectiveRawOffset
                    + readLastStockTaskOffsetX(taskView)
                    + readLastStockHorizontalOffsetX(taskView);
            boolean shouldHoldLeadCardCentered = isTouchHandling
                    && stackVerticalProgress < 0.999f
                    && progress >= 0f
                    && progress < 1.0f;
            float horizontalEntryProgress = shouldHoldLeadCardCentered
                    ? 0f
                    : stackEntryProgress;
            float frontShiftProgress = remapProgress(
                    horizontalEntryProgress,
                    STACK_FRONT_SHIFT_START_PROGRESS,
                    1.0f);
            float frontBaseOffset = lerp(stackBaseOffsetPx, stackFrontOffsetPx, frontShiftProgress);
            float desiredVisibleOffset;
            float desiredScale;
            float desiredTranslationZ;
            float desiredTaskOffsetY;
            float desiredBoxTranslationY;

            if (progress >= 0f) {
                float positiveProgress = Math.max(0f, progress);
                int rightLayer = (int) Math.floor(positiveProgress);
                float localProgress = positiveProgress - rightLayer;
                float handoffProgress = smoothStep((float) Math.pow(
                        localProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                float maxLeadSeparationPx = taskWidth * (1.0f - STACK_MIN_OVERLAP_RATIO);
                desiredVisibleOffset = frontBaseOffset
                        + (rightLayer * maxLeadSeparationPx)
                        + (handoffProgress * maxLeadSeparationPx);
                desiredScale = 1.0f;
                desiredTranslationZ = maxTranslationZ
                        + zStepPx
                        + (Math.min(progress, MAX_STACK_LAYERS) / MAX_STACK_LAYERS * maxTranslationZ);
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            } else {
                float stackDepth = clamp(-progress, 0f, MAX_STACK_LAYERS);
                float revealCurve = (float) Math.pow(
                        clamp(stackDepth / MAX_STACK_LAYERS, 0f, 1f),
                        STACK_DEPTH_CURVE_POWER);
                float visualStackDepth = revealCurve * MAX_STACK_LAYERS;
                float backgroundSpreadProgress = clamp(
                        (stackDepth - 1.0f) / Math.max(1.0f, MAX_STACK_LAYERS - 1.0f),
                        0f,
                        1f);
                float backgroundSpreadCurve = (float) Math.pow(
                        backgroundSpreadProgress,
                        STACK_DEPTH_CURVE_POWER);
                float backgroundStackOffset = stackBaseOffsetPx
                        - (stackBackSpreadPx * backgroundSpreadCurve);
                float incomingProgress = remapProgress(progress, -1.0f, 0.0f);
                float frontRevealProgress = smoothStep((float) Math.pow(
                        incomingProgress,
                        STACK_FRONT_REVEAL_CURVE_POWER));
                desiredVisibleOffset = lerp(
                        backgroundStackOffset,
                        frontBaseOffset,
                        frontRevealProgress);
                desiredScale = Math.max(
                        STACK_MIN_SCALE,
                        1.0f - (STACK_SCALE_STEP * visualStackDepth));
                desiredTranslationZ = Math.max(0f, maxTranslationZ - (revealCurve * maxTranslationZ));
                desiredTaskOffsetY = stackEntryLiftPx * (1.0f - stackVerticalProgress);
            }
            desiredVisibleOffset = lerp(stockVisibleOffset, desiredVisibleOffset, horizontalEntryProgress);
            desiredScale = lerp(readLastStockNonGridScale(taskView), desiredScale, stackVerticalProgress);
            desiredTaskOffsetY = lerp(
                    readLastStockTaskOffsetY(taskView),
                    desiredTaskOffsetY,
                    stackVerticalProgress);
            desiredBoxTranslationY = lerp(
                    readLastStockBoxTranslationY(taskView),
                    readOriginalBoxTranslationY(taskView),
                    stackVerticalProgress);
            desiredTranslationZ = lerp(
                    readLastStockTranslationZ(taskView),
                    desiredTranslationZ,
                    stackVerticalProgress);
            float desiredStableAlpha = readLastStockStableAlpha(taskView);
            if (blankTapExitProgress > 0f) {
                if (isTaskVisibleInViewport(
                        recentsView,
                        taskCenteredLeftPx,
                        taskWidth,
                        desiredVisibleOffset,
                        desiredScale)) {
                    desiredVisibleOffset -= blankTapExitTravelPx * blankTapExitProgress;
                    desiredScale *= 1.0f - (BLANK_TAP_HOME_EXIT_SCALE_DELTA * blankTapExitProgress);
                    desiredStableAlpha *= 1.0f - blankTapExitProgress;
                } else {
                    desiredStableAlpha = 0f;
                }
            }
            float translationCompensationX = desiredVisibleOffset - effectiveRawOffset;

            taskView.setPivotX(taskWidth * 0.5f);
            taskView.setPivotY(taskHeight * 0.5f);
            setHorizontalOffsetTranslationX(taskView, 0f);
            setTaskOffsetTranslationX(taskView, translationCompensationX);
            setTaskOffsetTranslationY(taskView, desiredTaskOffsetY);
            setBoxTranslationY(taskView, desiredBoxTranslationY);
            setNonGridScale(taskView, desiredScale);
            setStableAlpha(taskView, desiredStableAlpha);
            taskView.setTranslationZ(desiredTranslationZ);
        }
        if (launchState != null && launchState.handoffEnabled) {
            applyLaunchHandoffLayout(recentsView, launchState);
        }
    }

    private static void hookTaskViewUtilsCreateRecentsWindowAnimator(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_UTILS_CLASS, false, loader);
            Class<?> recentsViewClass = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass = Class.forName(TASK_VIEW_CLASS, false, loader);
            Class<?> remoteAnimationTargetArrayClass =
                    Class.forName("[Landroid.view.RemoteAnimationTarget;", false, loader);
            Class<?> depthControllerClass =
                    Class.forName(
                            "com.android.launcher3.statehandlers.DepthController",
                            false,
                            loader);
            Class<?> transitionInfoClass = Class.forName("android.window.TransitionInfo", false, loader);
            Class<?> pendingAnimationClass =
                    Class.forName("com.android.launcher3.anim.PendingAnimation", false, loader);
            Method method = clazz.getDeclaredMethod(
                    "createRecentsWindowAnimator",
                    recentsViewClass,
                    taskViewClass,
                    boolean.class,
                    remoteAnimationTargetArrayClass,
                    remoteAnimationTargetArrayClass,
                    remoteAnimationTargetArrayClass,
                    depthControllerClass,
                    transitionInfoClass,
                    pendingAnimationClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = chain.getArg(0) instanceof View ? (View) chain.getArg(0) : null;
                View taskView = chain.getArg(1) instanceof View ? (View) chain.getArg(1) : null;
                if (!shouldOverrideTaskLaunchStockGeometry(recentsView, taskView)) {
                    return chain.proceed();
                }
                TaskLaunchSimulatorTranslationContext previousContext =
                        ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.get();
                ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.set(
                        new TaskLaunchSimulatorTranslationContext(recentsView, taskView));
                try {
                    return chain.proceed();
                } finally {
                    restoreTaskLaunchSimulatorTranslationContext(previousContext);
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskViewUtils.createRecentsWindowAnimator",
                    t);
        }
    }

    private static void hookTaskViewUtilsLaunchFrameCallback(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_UTILS_CLASS, false, loader);
            Class<?> remoteTargetHandleArrayClass =
                    Class.forName("[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;", false, loader);
            Class<?> recentsViewClass = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass = Class.forName(TASK_VIEW_CLASS, false, loader);
            Class<?> pagedOrientationHandlerClass =
                    Class.forName(PAGED_ORIENTATION_HANDLER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "lambda$createRecentsWindowAnimator$0",
                    remoteTargetHandleArrayClass,
                    recentsViewClass,
                    float[].class,
                    taskViewClass,
                    int[].class,
                    RectF.class,
                    boolean[].class,
                    pagedOrientationHandlerClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                View recentsView = chain.getArg(1) instanceof View ? (View) chain.getArg(1) : null;
                View taskView = chain.getArg(3) instanceof View ? (View) chain.getArg(3) : null;
                if (!shouldSuppressTaskLaunchScrollCompensation(recentsView, taskView)) {
                    return chain.proceed();
                }
                View previousRecentsView = ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.get();
                ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.set(recentsView);
                try {
                    return chain.proceed();
                } finally {
                    restoreTaskLaunchScrollCompensationBypass(previousRecentsView);
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskViewUtils launch frame callback",
                    t);
        }
    }

    private static void hookTaskViewSimulatorSetTaskRectTranslation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(TASK_VIEW_SIMULATOR_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setTaskRectTranslation", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                TaskLaunchSimulatorTranslationContext context =
                        ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.get();
                if (context == null
                        || !shouldOverrideTaskLaunchStockGeometry(
                        context.recentsView,
                        context.taskView)) {
                    return chain.proceed();
                }
                TaskLaunchTaskRectTranslation adjustedTranslation =
                        resolveTaskLaunchTaskRectTranslation(
                                context.recentsView,
                                context.taskView);
                if (adjustedTranslation == null) {
                    return chain.proceed();
                }
                Object thisObject = chain.getThisObject();
                setIntField(thisObject, "mTaskRectTranslationX", adjustedTranslation.translationX);
                setIntField(thisObject, "mTaskRectTranslationY", adjustedTranslation.translationY);
                invokeMethodReflectively(thisObject, "calculateTaskSize", NO_ARGS);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskViewSimulator.setTaskRectTranslation",
                    t);
        }
    }

    private static void hookRecentsViewCreateAdjacentPageAnimForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass = Class.forName(TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("createAdjacentPageAnimForTaskLaunch", taskViewClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (!(thisObject instanceof View) || !(arg0 instanceof View)) {
                    return chain.proceed();
                }
                View recentsView = (View) thisObject;
                View taskView = (View) arg0;
                if (!shouldUseStackFriendlyAdjacentLaunchAnimation(recentsView, taskView)) {
                    return chain.proceed();
                }
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.play(ObjectAnimator.ofFloat(recentsView, "taskThumbnailSplashAlpha", 0.0f, 1.0f));
                return animatorSet;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.createAdjacentPageAnimForTaskLaunch",
                    t);
        }
    }

    private static void hookRecentsViewCreateTaskLaunchAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass = Class.forName(TASK_VIEW_CLASS, false, loader);
            Constructor<?> pendingAnimationConstructor =
                    Class.forName("com.android.launcher3.anim.PendingAnimation", false, loader)
                            .getDeclaredConstructor(long.class);
            pendingAnimationConstructor.setAccessible(true);
            Method method = clazz.getDeclaredMethod(
                    "createTaskLaunchAnimation",
                    taskViewClass,
                    long.class,
                    android.view.animation.Interpolator.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                if (!(thisObject instanceof View) || !(arg0 instanceof View)) {
                    return chain.proceed();
                }
                View recentsView = (View) thisObject;
                View taskView = (View) arg0;
                if (!shouldSuppressStockTaskLaunchAnimationBuild(recentsView, taskView)) {
                    return chain.proceed();
                }
                Object emptyPendingAnimation =
                        createPendingAnimationInstance(pendingAnimationConstructor, 0L);
                if (emptyPendingAnimation == null) {
                    return chain.proceed();
                }
                writeField(recentsView, "mPendingAnimation", emptyPendingAnimation);
                return emptyPendingAnimation;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.createTaskLaunchAnimation",
                    t);
        }
    }

    private static void hookRecentsViewUpdateScrollSynchronously(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateScrollSynchronously");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View
                        && shouldSuppressTaskLaunchSynchronousLayout((View) thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.updateScrollSynchronously",
                    t);
        }
    }

    private static void hookViewScrollByForTaskLaunch(FlymeStatusBarSizer module) {
        try {
            Method method = View.class.getDeclaredMethod("scrollBy", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressRecentsLaunchScrollMutation(thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook View.scrollBy for task launch",
                    t);
        }
    }

    private static void hookViewScrollToForTaskLaunch(FlymeStatusBarSizer module) {
        try {
            Method method = View.class.getDeclaredMethod("scrollTo", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressRecentsLaunchScrollMutation(thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook View.scrollTo for task launch",
                    t);
        }
    }

    private static void restoreTaskTransforms(View recentsView, int taskViewCount) {
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null) {
                continue;
            }
            restoreTaskTransform(taskView);
        }
    }

    private static void restoreTaskTransform(View taskView) {
        setHorizontalOffsetTranslationX(taskView, 0f);
        setTaskOffsetTranslationX(taskView, 0f);
        setTaskOffsetTranslationY(taskView, 0f);
        setBoxTranslationY(taskView, readOriginalBoxTranslationY(taskView));
        setNonGridScale(taskView, readOriginalNonGridScale(taskView));
        setStableAlpha(taskView, readLastStockStableAlpha(taskView));
        taskView.setTranslationZ(readLastStockTranslationZ(taskView));
    }

    private static boolean shouldUseStackLayout(View recentsView) {
        if (recentsView == null) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(recentsView.getContext());
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        return shouldUseStackLayout(config, recentsView, taskViewCount);
    }

    private static boolean shouldUseStackLayout(
            FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config,
            View recentsView,
            int taskViewCount) {
        return config != null
                && config.enabled
                && config.launcherIosStackRecentsEnabled
                && taskViewCount > 0
                && !invokeBoolean(recentsView, "showAsGrid", false)
                && !invokeBoolean(recentsView, "isSplitSelectionActive", false);
    }

    private static boolean shouldApplyDynamicStackLayout(View recentsView) {
        return shouldUseStackLayout(recentsView) && !isTaskLaunchLayoutFrozen(recentsView);
    }

    private static boolean shouldSuppressStockTaskLaunchTransformMethod(
            View recentsView,
            String methodName) {
        return recentsView != null
                && isTaskLaunchLayoutFrozen(recentsView)
                && ("updatePageOffsetsForFlyme".equals(methodName)
                || "updatePageScales".equals(methodName));
    }

    private static boolean shouldSuppressStockTaskLaunchVisualReset(View recentsView) {
        return recentsView != null && isTaskLaunchLayoutFrozen(recentsView);
    }

    private static boolean shouldSuppressStockTaskLaunchAnimationBuild(
            View recentsView,
            View taskView) {
        return shouldOverrideTaskLaunchStockGeometry(recentsView, taskView);
    }

    private static boolean shouldSuppressTaskLaunchSynchronousLayout(View recentsView) {
        return recentsView != null && isTaskLaunchLayoutFrozen(recentsView);
    }

    private static void reapplyOriginalTransforms(View recentsView) {
        cancelBlankTapHomeExitAnimation(recentsView, true);
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        restoreTaskTransforms(recentsView, taskViewCount);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageScales", NO_ARGS);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "updatePageOffsetsForFlyme", NO_ARGS);
        recentsView.requestLayout();
        recentsView.invalidate();
    }

    private static boolean shouldAnimateBlankTapHomeExit(View recentsView) {
        return recentsView != null
                && shouldUseStackLayout(recentsView)
                && readBooleanField(recentsView, "mTouchDownToStartHome", false);
    }

    private static void startBlankTapHomeExitAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        ValueAnimator runningAnimator = ACTIVE_HOME_EXIT_ANIMATORS.get(recentsView);
        if (runningAnimator != null) {
            if (runningAnimator.isStarted() || runningAnimator.isRunning()) {
                return;
            }
            ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
        }
        setPageAnimOffScreenStart(recentsView, true);
        setBlankTapHomeExitProgress(recentsView, 0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(BLANK_TAP_HOME_EXIT_DURATION_MS);
        animator.setInterpolator(BLANK_TAP_HOME_EXIT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            float progress = value instanceof Float ? (Float) value : 0f;
            setBlankTapHomeExitProgress(recentsView, progress);
            recentsView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
                if (cancelled) {
                    clearBlankTapHomeExitProgress(recentsView);
                    return;
                }
                finishBlankTapHomeExit(recentsView);
            }
        });
        ACTIVE_HOME_EXIT_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void finishBlankTapHomeExit(View recentsView) {
        if (recentsView == null) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "handleStartHome",
                new Class[]{boolean.class},
                false);
        Runnable resetRunnable = () -> clearBlankTapHomeExitProgress(recentsView);
        Handler handler = ensureMainHandler();
        if (handler != null && Looper.myLooper() != handler.getLooper()) {
            handler.post(resetRunnable);
        } else {
            recentsView.post(resetRunnable);
        }
    }

    private static void cancelBlankTapHomeExitAnimation(View recentsView, boolean resetTransform) {
        ValueAnimator animator = ACTIVE_HOME_EXIT_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (resetTransform) {
            clearBlankTapHomeExitProgress(recentsView);
        }
    }

    private static void clearBlankTapHomeExitProgress(View recentsView) {
        if (recentsView == null) {
            return;
        }
        BLANK_TAP_HOME_EXIT_PROGRESS.remove(recentsView);
        setPageAnimOffScreenStart(recentsView, false);
        recentsView.invalidate();
    }

    private static void setBlankTapHomeExitProgress(View recentsView, float progress) {
        if (recentsView == null) {
            return;
        }
        BLANK_TAP_HOME_EXIT_PROGRESS.put(recentsView, clamp(progress, 0f, 1f));
    }

    private static float readBlankTapHomeExitProgress(View recentsView) {
        Float value = BLANK_TAP_HOME_EXIT_PROGRESS.get(recentsView);
        return value != null ? value : 0f;
    }

    private static View getTaskViewAt(View recentsView, int index) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(recentsView, "getTaskViewAt", INT_ARG, index);
        return value instanceof View ? (View) value : null;
    }

    private static boolean shouldSuppressTaskPressScale(View taskView) {
        View recentsView = resolveOwningRecentsView(taskView);
        return shouldUseStackLayout(recentsView);
    }

    private static boolean handleTaskClickWithoutSystemAnimation(
            View taskView,
            View recentsView) {
        if (!shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
            return false;
        }
        Object splitSelectResult = FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "confirmSecondSplitSelectApp",
                NO_ARGS);
        if (splitSelectResult instanceof Boolean && (Boolean) splitSelectResult) {
            return true;
        }
        FlymeStatusBarSizer.invokeMethodCompat(taskView, "updateUsageState", NO_ARGS);
        prepareTaskLaunchWithoutSystemAnimation(recentsView, taskView);
        return launchTaskWithoutSystemAnimation(taskView, recentsView);
    }

    private static boolean shouldSuppressTaskHandleActionUp(
            View recentsView,
            View taskView) {
        return shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView);
    }

    private static boolean shouldStartTaskLaunchHandoff(View taskView, View recentsView) {
        return taskView != null
                && recentsView != null
                && shouldUseStackLayout(recentsView)
                && !isDesktopTask(taskView)
                && !isTaskLaunchLayoutFrozen(recentsView)
                && !ACTIVE_TASK_LAUNCH_HANDOFFS.containsKey(recentsView);
    }

    private static boolean shouldReplaceTaskLaunchWithNoAnimation(
            View recentsView,
            View taskView) {
        return taskView != null
                && recentsView != null
                && shouldUseStackLayout(recentsView)
                && !isDesktopTask(taskView);
    }

    private static boolean shouldOverrideTaskLaunchStockGeometry(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || !shouldUseStackLayout(recentsView)
                || isDesktopTask(taskView)) {
            return false;
        }
        LaunchHandoffState state = ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        return state != null
                && state.frozen
                && state.targetTaskView == taskView;
    }

    private static boolean shouldSuppressTaskLaunchScrollCompensation(
            View recentsView,
            View taskView) {
        return shouldOverrideTaskLaunchStockGeometry(recentsView, taskView)
                && resolveTaskLaunchTaskRectTranslation(recentsView, taskView) != null;
    }

    private static boolean shouldUseStackFriendlyAdjacentLaunchAnimation(
            View recentsView,
            View taskView) {
        if (!shouldOverrideTaskLaunchStockGeometry(recentsView, taskView)
                || invokeBoolean(recentsView, "showAsGrid", false)) {
            return false;
        }
        int taskIndex = resolveTaskViewIndex(recentsView, taskView);
        int currentPage = invokeInt(recentsView, "getCurrentPage", 0);
        return taskIndex >= 0 && taskIndex != currentPage;
    }

    private static void restoreTaskLaunchSimulatorTranslationContext(
            TaskLaunchSimulatorTranslationContext previousContext) {
        if (previousContext == null) {
            ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.remove();
        } else {
            ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.set(previousContext);
        }
    }

    private static void restoreTaskLaunchScrollCompensationBypass(View previousRecentsView) {
        if (previousRecentsView == null) {
            ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.remove();
        } else {
            ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.set(previousRecentsView);
        }
    }

    private static void prepareTaskLaunchWithoutSystemAnimation(
            View recentsView,
            View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        cancelTaskLaunchHandoff(recentsView, true);
        TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        freezeTaskLaunchLayoutIfNeeded(recentsView, taskView);
        recentsView.invalidate();
    }

    private static boolean shouldSuppressRecentsLaunchScrollMutation(Object thisObject) {
        if (!(thisObject instanceof View)) {
            return false;
        }
        View view = (View) thisObject;
        View bypassRecentsView = ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.get();
        if (view == bypassRecentsView && bypassRecentsView != null) {
            return true;
        }
        return isRecentsViewObject(view) && isTaskLaunchLayoutFrozen(view);
    }

    private static boolean shouldSuppressTaskLaunchPageMutation(Object thisObject) {
        if (!(thisObject instanceof View)) {
            return false;
        }
        View view = (View) thisObject;
        return isRecentsViewObject(view) && isTaskLaunchLayoutFrozen(view);
    }

    private static boolean launchTaskWithoutSystemAnimation(View taskView, View recentsView) {
        if (taskView == null) {
            return false;
        }
        ClassLoader loader = taskView.getClass().getClassLoader();
        if (loader == null) {
            loader = LauncherRecentsHooks.class.getClassLoader();
        }
        if (loader == null) {
            return false;
        }
        try {
            Class<?> function1Class = Class.forName("kotlin.jvm.functions.Function1", false, loader);
            final Object kotlinUnitInstance = resolveKotlinUnitInstance(loader);
            final View callbackRecentsView = recentsView;
            Object callback = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{function1Class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("equals".equals(methodName)) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("hashCode".equals(methodName)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(methodName)) {
                            return "TaskLaunchWithoutAnimationCallback";
                        }
                        boolean launched = args != null
                                && args.length > 0
                                && args[0] instanceof Boolean
                                && (Boolean) args[0];
                        if (launched) {
                            scheduleTaskLaunchNoAnimationCleanup(callbackRecentsView);
                        } else {
                            clearTaskLaunchHandoff(callbackRecentsView, true);
                        }
                        return kotlinUnitInstance;
                    });
            return invokeMethodReflectively(
                    taskView,
                    "launchWithoutAnimation",
                    new Class<?>[]{boolean.class, function1Class},
                    false,
                    callback);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to replace task launch animation with launchWithoutAnimation",
                    t);
            return false;
        }
    }

    private static void scheduleTaskLaunchNoAnimationCleanup(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable cleanup = () -> clearTaskLaunchHandoff(recentsView, true);
        Handler handler = ensureMainHandler();
        if (handler != null) {
            handler.postDelayed(cleanup, TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS);
        } else {
            recentsView.postDelayed(cleanup, TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS);
        }
    }

    private static TaskLaunchTaskRectTranslation resolveTaskLaunchTaskRectTranslation(
            View recentsView,
            View taskView) {
        if (recentsView == null || taskView == null) {
            return null;
        }
        Rect baseTaskRect = new Rect();
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "getTaskSize",
                new Class<?>[]{Rect.class},
                baseTaskRect);
        if (baseTaskRect.isEmpty()) {
            return null;
        }
        float actualTaskLeft = taskView.getX() - recentsView.getScrollX();
        float actualTaskTop = taskView.getY() - recentsView.getScrollY();
        return new TaskLaunchTaskRectTranslation(
                Math.round(actualTaskLeft - baseTaskRect.left),
                Math.round(actualTaskTop - baseTaskRect.top));
    }

    private static Object resolveKotlinUnitInstance(ClassLoader loader) {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit", false, loader);
            Field field = unitClass.getField("INSTANCE");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean consumeTaskClickBypass(View taskView) {
        Boolean value = BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        return value != null && value;
    }

    private static boolean consumeTaskLaunchRequestStarted(View recentsView) {
        Boolean value = TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        return value != null && value;
    }

    private static boolean isTaskLaunchLayoutFrozen(View recentsView) {
        LaunchHandoffState state = ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        return state != null && state.frozen;
    }

    private static boolean isRecentsGestureEndTarget(Object value) {
        return value instanceof Enum && "RECENTS".equals(((Enum<?>) value).name());
    }

    private static void switchRunningTaskToScreenshot(View recentsView) {
        if (recentsView == null) {
            return;
        }
        Runnable applyRunnable = () -> {
            finishRunningTaskRecentsAnimation(recentsView);
            finishRunningTaskReleaseToStack(recentsView);
        };
        if (!invokeMethodReflectively(
                recentsView,
                "switchToScreenshot",
                new Class<?>[]{Runnable.class},
                applyRunnable)) {
            applyRunnable.run();
        }
    }

    private static void finishRunningTaskReleaseToStack(View recentsView) {
        if (recentsView == null) {
            return;
        }
        invokeMethodReflectively(
                recentsView,
                "setRunningTaskViewShowScreenshot",
                BOOLEAN_ARG,
                true);
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setEnableDrawingLiveTile",
                BOOLEAN_ARG,
                false);
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setRunningTaskHidden",
                BOOLEAN_ARG,
                false);
        captureStockTaskStates(recentsView);
        applyStackLayout(recentsView, false);
        recentsView.invalidate();
    }

    private static void finishRunningTaskRecentsAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        invokeMethodReflectively(
                recentsView,
                "finishRecentsAnimation",
                new Class<?>[]{boolean.class, boolean.class, Runnable.class},
                true,
                false,
                null);
    }

    private static View resolveOwningRecentsView(View taskView) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(taskView, "getRecentsView", NO_ARGS);
        if (value instanceof View) {
            return (View) value;
        }
        ViewParent parent = taskView != null ? taskView.getParent() : null;
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (isRecentsViewObject(parentView)) {
                return parentView;
            }
            parent = parentView.getParent();
        }
        return null;
    }

    private static void resetTaskTouchScale(View taskView) {
        if (taskView == null) {
            return;
        }
        Object animator = FlymeStatusBarSizer.getFieldCompat(taskView, "mTaskThumbScaleAnimator");
        if (animator instanceof Animator) {
            Animator taskScaleAnimator = (Animator) animator;
            if (taskScaleAnimator.isStarted() || taskScaleAnimator.isRunning()) {
                taskScaleAnimator.cancel();
            }
        }
        Object scaleUpRunnable = FlymeStatusBarSizer.getFieldCompat(taskView, "mScaleUpRunnable");
        if (scaleUpRunnable instanceof Runnable) {
            taskView.removeCallbacks((Runnable) scaleUpRunnable);
        }
        writeField(taskView, "mTaskThumbScaleAnimator", null);
        setNonGridScale(taskView, readFloatField(taskView, "nonGridScale", 1f));
    }

    private static void startTaskLaunchHandoff(View taskView, View recentsView) {
        if (taskView == null || recentsView == null) {
            return;
        }
        cancelTaskLaunchHandoff(recentsView, true);
        TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        trackRecentsView(recentsView);
        prepareRecentsView(recentsView);
        LaunchHandoffState state = new LaunchHandoffState(
                taskView,
                resolveTaskViewIndex(recentsView, taskView),
                shouldPromoteRearTaskDuringLaunch(recentsView, taskView),
                true);
        ACTIVE_TASK_LAUNCH_HANDOFFS.put(recentsView, state);
        applyStackLayout(recentsView, false);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(state.promoteRearCard
                ? TASK_LAUNCH_HANDOFF_DURATION_MS
                : TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS);
        animator.setInterpolator(BLANK_TAP_HOME_EXIT_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            state.progress = value instanceof Float ? (Float) value : 0f;
            recentsView.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.get(recentsView) == animation) {
                    ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.remove(recentsView);
                }
                if (ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView) != state) {
                    return;
                }
                if (cancelled) {
                    clearTaskLaunchHandoff(recentsView, true);
                    return;
                }
                completeTaskLaunchHandoff(recentsView, state);
                continueTaskLaunchClick(taskView, recentsView);
            }
        });
        ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void completeTaskLaunchHandoff(View recentsView, LaunchHandoffState state) {
        if (recentsView == null || state == null) {
            return;
        }
        state.progress = 1f;
        applyStackLayout(recentsView, false);
        state.frozen = true;
        recentsView.invalidate();
    }

    private static void continueTaskLaunchClick(View taskView, View recentsView) {
        if (taskView == null) {
            clearTaskLaunchHandoff(recentsView, true);
            return;
        }
        BYPASS_TASK_CLICK_INTERCEPTION.put(taskView, Boolean.TRUE);
        writeField(taskView, "mDownTime", SystemClock.uptimeMillis());
        try {
            if (!invokeMethodReflectively(taskView, "onClick", NO_ARGS)) {
                clearTaskLaunchHandoff(recentsView, true);
                return;
            }
            if (!consumeTaskLaunchRequestStarted(recentsView)
                    && isTaskLaunchLayoutFrozen(recentsView)) {
                clearTaskLaunchHandoff(recentsView, true);
            }
        } finally {
            BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        }
    }

    private static void freezeTaskLaunchLayoutIfNeeded(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        LaunchHandoffState state = ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        if (state == null) {
            state = new LaunchHandoffState(
                    taskView,
                    resolveTaskViewIndex(recentsView, taskView),
                    false,
                    false);
            ACTIVE_TASK_LAUNCH_HANDOFFS.put(recentsView, state);
        }
        if (state.frozen) {
            return;
        }
        state.progress = 1f;
        if (state.handoffEnabled) {
            applyStackLayout(recentsView, false);
        }
        state.frozen = true;
        recentsView.invalidate();
    }

    private static void attachTaskLaunchCleanup(View recentsView, Object launchResult) {
        Runnable cleanup = () -> clearTaskLaunchHandoff(recentsView, true);
        if (launchResult == null) {
            cleanup.run();
            return;
        }
        if (!invokeMethodReflectively(
                launchResult,
                "add",
                new Class<?>[]{Runnable.class},
                cleanup)) {
            cleanup.run();
        }
    }

    private static void clearTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        if (recentsView == null) {
            return;
        }
        TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        ACTIVE_TASK_LAUNCH_HANDOFFS.remove(recentsView);
        cancelTaskLaunchHandoff(recentsView, false);
        if (!restoreStack || !recentsView.isAttachedToWindow() || !recentsView.isShown()) {
            return;
        }
        prepareRecentsView(recentsView);
        if (shouldUseStackLayout(recentsView)) {
            applyStackLayout(recentsView, false);
        } else {
            reapplyOriginalTransforms(recentsView);
        }
        recentsView.invalidate();
    }

    private static void cancelTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        ValueAnimator animator = ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (restoreStack) {
            ACTIVE_TASK_LAUNCH_HANDOFFS.remove(recentsView);
        }
    }

    private static int resolveTaskViewIndex(View recentsView, View taskView) {
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            if (getTaskViewAt(recentsView, i) == taskView) {
                return i;
            }
        }
        return -1;
    }

    private static boolean shouldPromoteRearTaskDuringLaunch(View recentsView, View taskView) {
        if (taskView == null) {
            return false;
        }
        View frontTaskView = resolveFrontMostTaskView(recentsView);
        if (frontTaskView == null || frontTaskView == taskView) {
            return false;
        }
        return readFloatField(taskView, "nonGridScale", 1f)
                < (readFloatField(frontTaskView, "nonGridScale", 1f) - 0.02f);
    }

    private static View resolveFrontMostTaskView(View recentsView) {
        View frontTaskView = null;
        float highestZ = Float.NEGATIVE_INFINITY;
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null || isDesktopTask(taskView)) {
                continue;
            }
            float translationZ = taskView.getTranslationZ();
            if (frontTaskView == null || translationZ > highestZ) {
                frontTaskView = taskView;
                highestZ = translationZ;
            }
        }
        return frontTaskView;
    }

    private static void applyLaunchHandoffLayout(View recentsView, LaunchHandoffState state) {
        if (recentsView == null
                || state == null
                || state.frozen
                || !state.handoffEnabled
                || state.targetTaskView == null) {
            return;
        }
        View targetTaskView = state.targetTaskView;
        if (resolveOwningRecentsView(targetTaskView) != recentsView) {
            return;
        }
        View frontTaskView = resolveFrontMostTaskView(recentsView);
        float progress = smoothStep(state.progress);
        float promoteProgress = state.promoteRearCard
                ? smoothStep(remapProgress(progress, 0f, TASK_LAUNCH_REAR_PROMOTE_FRACTION))
                : progress;
        float settleProgress = state.promoteRearCard
                ? smoothStep(remapProgress(progress, TASK_LAUNCH_REAR_PROMOTE_FRACTION, 1f))
                : progress;
        float siblingRetreatPx = Math.min(
                Math.max(targetTaskView.getWidth(), recentsView.getWidth()) * 0.04f,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 22));
        float siblingDropPx = Math.min(
                Math.max(targetTaskView.getHeight(), recentsView.getHeight()) * 0.035f,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 14));
        float targetLiftPx = Math.min(
                Math.max(targetTaskView.getHeight(), recentsView.getHeight()) * 0.045f,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 18));
        float targetExtraZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 18);
        float anchorTaskOffsetX = frontTaskView != null
                ? readFloatField(frontTaskView, "taskOffsetTranslationX", 0f)
                : readFloatField(targetTaskView, "taskOffsetTranslationX", 0f);
        float anchorTaskOffsetY = frontTaskView != null
                ? readFloatField(frontTaskView, "taskOffsetTranslationY", 0f)
                : readFloatField(targetTaskView, "taskOffsetTranslationY", 0f);
        float anchorScale = frontTaskView != null
                ? readFloatField(frontTaskView, "nonGridScale", 1f)
                : readFloatField(targetTaskView, "nonGridScale", 1f);
        float anchorZ = frontTaskView != null
                ? frontTaskView.getTranslationZ()
                : targetTaskView.getTranslationZ();

        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null || isDesktopTask(taskView)) {
                continue;
            }
            float startHorizontalOffsetX = readFloatField(taskView, "horizontalOffsetTranslationX", 0f);
            float startTaskOffsetX = readFloatField(taskView, "taskOffsetTranslationX", 0f);
            float startTaskOffsetY = readFloatField(taskView, "taskOffsetTranslationY", 0f);
            float startBoxTranslationY = readFloatField(
                    taskView,
                    "boxTranslationY",
                    readOriginalBoxTranslationY(taskView));
            float startScale = readFloatField(taskView, "nonGridScale", 1f);
            float startAlpha = readStableAlpha(taskView);
            float startTranslationZ = taskView.getTranslationZ();
            if (taskView == targetTaskView) {
                float handoffHorizontalOffsetX = startHorizontalOffsetX;
                float handoffTaskOffsetX = startTaskOffsetX;
                float handoffTaskOffsetY = startTaskOffsetY;
                float handoffBoxTranslationY = startBoxTranslationY;
                float handoffScale = startScale;
                float handoffTranslationZ = startTranslationZ;
                if (state.promoteRearCard) {
                    handoffTaskOffsetX = lerp(
                            startTaskOffsetX,
                            lerp(startTaskOffsetX, anchorTaskOffsetX, 0.58f),
                            promoteProgress);
                    handoffTaskOffsetY = lerp(
                            startTaskOffsetY,
                            anchorTaskOffsetY - (targetLiftPx * 0.35f),
                            promoteProgress);
                    handoffBoxTranslationY = lerp(
                            startBoxTranslationY,
                            readLastStockBoxTranslationY(taskView),
                            promoteProgress);
                    handoffScale = lerp(startScale, Math.max(anchorScale, startScale), promoteProgress);
                    handoffTranslationZ = lerp(
                            startTranslationZ,
                            Math.max(anchorZ, startTranslationZ) + (targetExtraZ * 0.35f),
                            promoteProgress);
                }
                float endHorizontalOffsetX = readLastStockHorizontalOffsetX(taskView);
                float endTaskOffsetX = readLastStockTaskOffsetX(taskView);
                float endTaskOffsetY = readLastStockTaskOffsetY(taskView);
                float endBoxTranslationY = readLastStockBoxTranslationY(taskView);
                float endScale = readLastStockNonGridScale(taskView);
                float endTranslationZ = Math.max(
                        readLastStockTranslationZ(taskView),
                        Math.max(anchorZ, startTranslationZ) + targetExtraZ);
                // Handoff should end near the last stock launch geometry, otherwise Quickstep
                // immediately applies a large horizontal compensation before its own launch anim.
                setHorizontalOffsetTranslationX(
                        taskView,
                        lerp(handoffHorizontalOffsetX, endHorizontalOffsetX, settleProgress));
                setTaskOffsetTranslationX(taskView, lerp(handoffTaskOffsetX, endTaskOffsetX, settleProgress));
                setTaskOffsetTranslationY(taskView, lerp(handoffTaskOffsetY, endTaskOffsetY, settleProgress));
                setBoxTranslationY(taskView, lerp(handoffBoxTranslationY, endBoxTranslationY, settleProgress));
                setNonGridScale(taskView, lerp(handoffScale, endScale, settleProgress));
                setStableAlpha(taskView, 1f);
                taskView.setTranslationZ(lerp(handoffTranslationZ, endTranslationZ, settleProgress));
                continue;
            }
            float direction = i < state.targetIndex ? -1f : 1f;
            setHorizontalOffsetTranslationX(taskView, 0f);
            setTaskOffsetTranslationX(taskView, startTaskOffsetX + (direction * siblingRetreatPx * progress));
            setTaskOffsetTranslationY(taskView, startTaskOffsetY + (siblingDropPx * progress));
            setBoxTranslationY(taskView, lerp(
                    readFloatField(taskView, "boxTranslationY", readOriginalBoxTranslationY(taskView)),
                    readOriginalBoxTranslationY(taskView),
                    progress));
            setNonGridScale(taskView, lerp(startScale, startScale * 0.965f, progress));
            setStableAlpha(taskView, lerp(startAlpha, startAlpha * TASK_LAUNCH_SIBLING_END_ALPHA, progress));
            taskView.setTranslationZ(lerp(startTranslationZ, Math.max(0f, startTranslationZ - targetExtraZ), progress));
        }
    }

    private static boolean shouldSuppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return false;
        }
        int action = motionEvent.getActionMasked();
        return (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && invokeBoolean(recentsView, "isHandlingTouch", false);
    }

    private static void suppressPagedRelease(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        clearRecentsDeferredSnap(recentsView);
        if (motionEvent != null && motionEvent.getActionMasked() == MotionEvent.ACTION_UP) {
            startUnsnappedFlingIfNeeded(recentsView, motionEvent);
        } else {
            FlymeStatusBarSizer.invokeMethodCompat(recentsView, "abortScrollerAnimation", NO_ARGS);
        }
        releasePagedEdgeEffects(recentsView, motionEvent);
        FlymeStatusBarSizer.invokeMethodCompat(recentsView, "resetTouchState", NO_ARGS);
        if (shouldApplyDynamicStackLayout(recentsView)) {
            captureStockTaskStates(recentsView);
            applyStackLayout(recentsView, false);
            recentsView.invalidate();
        }
    }

    private static void startUnsnappedFlingIfNeeded(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null || motionEvent == null) {
            return;
        }
        Object velocityTrackerValue = FlymeStatusBarSizer.getFieldCompat(recentsView, "mVelocityTracker");
        if (!(velocityTrackerValue instanceof VelocityTracker)) {
            return;
        }
        VelocityTracker velocityTracker = (VelocityTracker) velocityTrackerValue;
        velocityTracker.addMovement(motionEvent);
        int maximumVelocity = invokeInt(recentsView, "getMaximumVelocity", Integer.MAX_VALUE);
        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
        int activePointerId = readIntField(recentsView, "mActivePointerId", -1);
        int primaryVelocity = Math.round(resolvePrimaryVelocity(recentsView, velocityTracker, activePointerId));
        int primaryScroll = resolvePrimaryScroll(recentsView);
        int minScroll = readIntField(recentsView, "mMinScroll", primaryScroll);
        int maxScroll = readIntField(recentsView, "mMaxScroll", primaryScroll);

        if (primaryScroll < minScroll || primaryScroll > maxScroll) {
            startPagedSpringBack(recentsView, primaryScroll, minScroll, maxScroll);
            return;
        }
        if (!shouldKeepFreeScrollFling(recentsView, primaryVelocity)) {
            return;
        }
        Object scroller = FlymeStatusBarSizer.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        setScrollerFriction(scroller, 0.03f);
        if (!startScrollerFling(recentsView, scroller, primaryScroll, primaryVelocity, minScroll, maxScroll)) {
            return;
        }
        setIntField(recentsView, "mNextPage", readIntField(recentsView, "mCurrentPage", -1));
    }

    private static boolean shouldKeepFreeScrollFling(View recentsView, int primaryVelocity) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "shouldFlingForVelocity",
                INT_ARG,
                primaryVelocity);
        return value instanceof Boolean && (Boolean) value;
    }

    private static float resolvePrimaryVelocity(
            View recentsView,
            VelocityTracker velocityTracker,
            int activePointerId) {
        Object orientationHandler = FlymeStatusBarSizer.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
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
        Object orientationHandler = FlymeStatusBarSizer.getFieldCompat(recentsView, "mOrientationHandler");
        Object value = FlymeStatusBarSizer.invokeMethodCompat(
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
        Object scroller = FlymeStatusBarSizer.getFieldCompat(recentsView, "mScroller");
        if (scroller == null) {
            return;
        }
        invokeScrollerSpringBack(scroller, primaryScroll, minScroll, maxScroll);
        setIntField(recentsView, "mNextPage", readIntField(recentsView, "mCurrentPage", -1));
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
        invokeScrollerMethod(scroller, "setFriction", FLOAT_ARG, friction);
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
        FlymeStatusBarSizer.invokeMethodCompat(scroller, methodName, parameterTypes, args);
        Object activeScroller = FlymeStatusBarSizer.getFieldCompat(scroller, "usingScroller");
        if (activeScroller != null && activeScroller != scroller) {
            FlymeStatusBarSizer.invokeMethodCompat(activeScroller, methodName, parameterTypes, args);
        }
    }

    private static int readScrollerFinalX(Object scroller, int fallback) {
        if (scroller == null) {
            return fallback;
        }
        Object value = FlymeStatusBarSizer.invokeMethodCompat(scroller, "getFinalX", NO_ARGS);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        Object activeScroller = FlymeStatusBarSizer.getFieldCompat(scroller, "usingScroller");
        Object activeValue = FlymeStatusBarSizer.invokeMethodCompat(activeScroller, "getFinalX", NO_ARGS);
        return activeValue instanceof Integer ? (Integer) activeValue : fallback;
    }

    private static void releasePagedEdgeEffects(View recentsView, MotionEvent motionEvent) {
        if (recentsView == null) {
            return;
        }
        releaseEdgeEffect(FlymeStatusBarSizer.getFieldCompat(recentsView, "mEdgeGlowLeft"), motionEvent);
        releaseEdgeEffect(FlymeStatusBarSizer.getFieldCompat(recentsView, "mEdgeGlowRight"), motionEvent);
    }

    private static void releaseEdgeEffect(Object edgeEffect, MotionEvent motionEvent) {
        if (edgeEffect == null) {
            return;
        }
        if (motionEvent != null) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    edgeEffect,
                    "onRelease",
                    new Class<?>[]{MotionEvent.class},
                    motionEvent);
        }
        FlymeStatusBarSizer.invokeMethodCompat(edgeEffect, "onRelease", NO_ARGS);
    }

    private static void clearRecentsDeferredSnap(View recentsView) {
        Object handlerValue = FlymeStatusBarSizer.getFieldCompat(
                recentsView,
                "mMainHandlerForAbortScrollAndCheckSnap");
        Object timeoutValue = FlymeStatusBarSizer.getFieldCompat(recentsView, "mTimeoutToCheckSnap");
        Object abortRunnerValue = FlymeStatusBarSizer.getFieldCompat(
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
        setBooleanField(recentsView, "mNeedCheckSnapToDestination", false);
        setIntField(recentsView, "mLastHandleActionUpChildIndex", -1);
    }

    private static void captureStockTaskStates(View recentsView) {
        int taskViewCount = invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = getTaskViewAt(recentsView, i);
            if (taskView == null || isDesktopTask(taskView)) {
                continue;
            }
            captureStockTaskState(taskView);
        }
    }

    private static void captureStockTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        rememberOriginalTaskState(taskView);
        LAST_STOCK_TASK_OFFSET_XS.put(taskView, readFloatField(taskView, "taskOffsetTranslationX", 0f));
        LAST_STOCK_TASK_OFFSET_YS.put(taskView, readFloatField(taskView, "taskOffsetTranslationY", 0f));
        LAST_STOCK_HORIZONTAL_OFFSET_XS.put(
                taskView,
                readFloatField(taskView, "horizontalOffsetTranslationX", 0f));
        LAST_STOCK_NON_GRID_SCALES.put(taskView, readFloatField(taskView, "nonGridScale", 1f));
        LAST_STOCK_BOX_TRANSLATION_YS.put(
                taskView,
                readFloatField(taskView, "boxTranslationY", readOriginalBoxTranslationY(taskView)));
        LAST_STOCK_STABLE_ALPHAS.put(taskView, readStableAlpha(taskView));
        LAST_STOCK_TRANSLATION_ZS.put(taskView, taskView.getTranslationZ());
    }

    private static void setHorizontalOffsetTranslationX(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setHorizontalOffsetTranslationX",
                FLOAT_ARG,
                value);
    }

    private static void setTaskOffsetTranslationX(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setTaskOffsetTranslationX",
                FLOAT_ARG,
                value);
    }

    private static void setTaskOffsetTranslationY(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setTaskOffsetTranslationY",
                FLOAT_ARG,
                value);
    }

    private static void setNonGridScale(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setNonGridScale",
                FLOAT_ARG,
                value);
    }

    private static void setBoxTranslationY(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setBoxTranslationY",
                FLOAT_ARG,
                value);
    }

    private static void setStableAlpha(View taskView, float value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                taskView,
                "setStableAlpha",
                FLOAT_ARG,
                clamp(value, 0f, 1f));
    }

    private static boolean isDesktopTask(View taskView) {
        return taskView != null
                && taskView.getClass().getName().contains("DesktopTaskView");
    }

    private static boolean isRecentsViewObject(Object value) {
        if (!(value instanceof View)) {
            return false;
        }
        String className = value.getClass().getName();
        return RECENTS_VIEW_CLASS.equals(className)
                || LAUNCHER_RECENTS_VIEW_CLASS.equals(className)
                || className.endsWith(".RecentsView")
                || className.endsWith("LauncherRecentsView");
    }

    private static void rememberOriginalTaskState(View taskView) {
        if (taskView == null) {
            return;
        }
        if (!ORIGINAL_NON_GRID_SCALES.containsKey(taskView)) {
            ORIGINAL_NON_GRID_SCALES.put(taskView, readFloatField(taskView, "nonGridScale", 1f));
        }
        if (!ORIGINAL_BOX_TRANSLATION_YS.containsKey(taskView)) {
            ORIGINAL_BOX_TRANSLATION_YS.put(taskView, readFloatField(taskView, "boxTranslationY", 0f));
        }
    }

    private static float readOriginalNonGridScale(View taskView) {
        Float value = ORIGINAL_NON_GRID_SCALES.get(taskView);
        return value != null ? value : 1f;
    }

    private static float readOriginalBoxTranslationY(View taskView) {
        Float value = ORIGINAL_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockTaskOffsetX(View taskView) {
        Float value = LAST_STOCK_TASK_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockTaskOffsetY(View taskView) {
        Float value = LAST_STOCK_TASK_OFFSET_YS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockHorizontalOffsetX(View taskView) {
        Float value = LAST_STOCK_HORIZONTAL_OFFSET_XS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readLastStockNonGridScale(View taskView) {
        Float value = LAST_STOCK_NON_GRID_SCALES.get(taskView);
        return value != null ? value : readOriginalNonGridScale(taskView);
    }

    private static float readLastStockBoxTranslationY(View taskView) {
        Float value = LAST_STOCK_BOX_TRANSLATION_YS.get(taskView);
        return value != null ? value : readOriginalBoxTranslationY(taskView);
    }

    private static float readLastStockStableAlpha(View taskView) {
        Float value = LAST_STOCK_STABLE_ALPHAS.get(taskView);
        return value != null ? value : 1f;
    }

    private static float readLastStockTranslationZ(View taskView) {
        Float value = LAST_STOCK_TRANSLATION_ZS.get(taskView);
        return value != null ? value : 0f;
    }

    private static float readStableAlpha(View taskView) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(taskView, "getStableAlpha", NO_ARGS);
        if (value instanceof Float) {
            return (Float) value;
        }
        return taskView.getAlpha();
    }

    private static int invokeInt(Object target, String methodName, int fallback) {
        return invokeInt(target, methodName, NO_ARGS, fallback);
    }

    private static int invokeInt(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            int fallback,
            Object... args) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, methodName, parameterTypes, args);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static boolean invokeBoolean(Object target, String methodName, boolean fallback) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, methodName, NO_ARGS);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int readIntField(Object target, String name, int fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static float readFloatField(Object target, String name, float fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Float ? (Float) value : fallback;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        Object value = FlymeStatusBarSizer.getFieldCompat(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        writeField(target, name, value);
    }

    private static void setIntField(Object target, String name, int value) {
        writeField(target, name, value);
    }

    private static void writeField(Object target, String name, Object value) {
        if (target == null || name == null) {
            return;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static boolean invokeMethodReflectively(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null) {
            return false;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static Object createPendingAnimationInstance(
            Constructor<?> constructor,
            long durationMs) {
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(durationMs);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float resolveStackEntryProgress(View recentsView) {
        float adjacentOffset = clamp(
                readFloatField(recentsView, "mAdjacentPageHorizontalOffset", 0f),
                0f,
                1f);
        float fullscreenProgress = clamp(
                readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        float collapsedProgress = Math.max(adjacentOffset, fullscreenProgress);
        return clamp((1.0f - collapsedProgress) * contentAlpha, 0f, 1f);
    }

    private static float resolveStackVerticalProgress(View recentsView) {
        float fullscreenProgress = clamp(
                readFloatField(recentsView, "mFullscreenProgress", 0f),
                0f,
                1f);
        float contentAlpha = clamp(
                readFloatField(recentsView, "mContentAlpha", 1f),
                0f,
                1f);
        return clamp((1.0f - fullscreenProgress) * contentAlpha, 0f, 1f);
    }

    private static boolean isTaskVisibleInViewport(
            View recentsView,
            float centeredLeftPx,
            float taskWidth,
            float desiredVisibleOffset,
            float desiredScale) {
        float clampedScale = Math.max(0.5f, desiredScale);
        float translatedLeftPx = centeredLeftPx + desiredVisibleOffset;
        float actualLeftPx = translatedLeftPx + ((1.0f - clampedScale) * taskWidth * 0.5f);
        float actualRightPx = actualLeftPx + (taskWidth * clampedScale);
        return actualRightPx > 0f && actualLeftPx < recentsView.getWidth();
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * clamp(progress, 0f, 1f));
    }

    private static float remapProgress(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1f : 0f;
        }
        return clamp((value - start) / (end - start), 0f, 1f);
    }

    private static float smoothStep(float value) {
        float clamped = clamp(value, 0f, 1f);
        return clamped * clamped * (3.0f - (2.0f * clamped));
    }

    private static void setPageAnimOffScreenStart(View recentsView, boolean value) {
        FlymeStatusBarSizer.invokeMethodCompat(
                recentsView,
                "setPageAnimOffScreenStart",
                BOOLEAN_ARG,
                value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Handler ensureMainHandler() {
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
