package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.view.View;

import java.lang.reflect.Method;

final class LauncherRecentsLaunchController {
    private static final float TASK_LAUNCH_SIBLING_EXIT_EXTRA_WIDTH_RATIO = 0.25f;

    private LauncherRecentsLaunchController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewResetTaskVisuals(module, loader);
        hookRecentsViewOnLayoutForTaskLaunch(module, loader);
        hookRecentsViewWindowVisibilityChanged(module, loader);
        hookRecentsViewDetachedFromWindow(module, loader);
        hookPagedViewSetCurrentPageForTaskLaunch(module, loader);
        hookPagedViewUpdateCurrentPageScrollForTaskLaunch(module, loader);
        hookPagedViewSnapToPageForTaskLaunch(module, loader);
        hookPagedViewScrollToForTaskLaunch(module, loader);
        hookTaskViewClick(module, loader);
        hookTaskViewLaunchWithAnimation(module, loader);
        hookTaskViewPressScale(module, loader);
        hookTaskViewUtilsCreateRecentsWindowAnimator(module, loader);
        hookTaskViewUtilsLaunchFrameCallback(module, loader);
        hookTaskViewSimulatorSetTaskRectTranslation(module, loader);
        hookRecentsViewGetTaskSizeForTaskLaunch(module, loader);
        hookRecentsViewSetFullscreenProgressForTaskLaunch(module, loader);
        hookRecentsViewSetTaskViewsResistanceTranslationForTaskLaunch(module, loader);
        hookRecentsViewCreateAdjacentPageAnimForTaskLaunch(module, loader);
        hookRecentsViewUpdateScrollSynchronously(module, loader);
        hookViewScrollByForTaskLaunch(module);
        hookViewScrollToForTaskLaunch(module);
        hookViewScaleForTaskLaunch(module);
    }

    private static void hookRecentsViewResetTaskVisuals(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
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

    private static void hookRecentsViewOnLayoutForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
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
                    applyFrozenTaskLaunchLayout((View) thisObject);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.onLayout for task launch",
                    t);
        }
    }

    private static void hookRecentsViewWindowVisibilityChanged(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onWindowVisibilityChanged", int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    int visibility = chain.getArg(0) instanceof Integer ? (Integer) chain.getArg(0) : 0;
                    if (visibility != View.VISIBLE
                            && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                        clearTaskLaunchTransitionGeometry(recentsView, false);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onDetachedFromWindow");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                        clearTaskLaunchTransitionGeometry(recentsView, false);
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

    private static void hookPagedViewSetCurrentPageForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setCurrentPage", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        LauncherRecentsTouchController.clearRecentsDeferredSnap((View) thisObject);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateCurrentPageScroll");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressRecentsLaunchScrollMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        LauncherRecentsTouchController.clearRecentsDeferredSnap((View) thisObject);
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

    private static void hookPagedViewSnapToPageForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(
                    "snapToPage",
                    int.class,
                    int.class,
                    int.class,
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressRecentsLaunchScrollMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        LauncherRecentsTouchController.clearRecentsDeferredSnap((View) thisObject);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("scrollTo", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (shouldSuppressRecentsLaunchScrollMutation(thisObject)) {
                    if (thisObject instanceof View) {
                        LauncherRecentsTouchController.clearRecentsDeferredSnap((View) thisObject);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onClick");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (LauncherRecentsState.consumeTaskClickBypass(taskView)) {
                        LauncherRecentsPerf.flow("launch:click:bypass",
                                LauncherRecentsCompat.resolveOwningRecentsView(taskView),
                                taskLaunchDetails(null, taskView, false));
                        return chain.proceed();
                    }
                    View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
                    boolean preparedTransitionGeometry =
                            shouldPrepareTaskLaunchTransitionGeometry(taskView, recentsView);
                    LauncherRecentsPerf.flow("launch:click",
                            recentsView,
                            taskLaunchDetails(recentsView, taskView, preparedTransitionGeometry));
                    if (preparedTransitionGeometry) {
                        LauncherRecentsPerf.startSpan("taskLaunch", recentsView);
                        prepareTaskLaunchTransitionGeometry(recentsView, taskView);
                    }
                    long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                    Object result;
                    try {
                        result = chain.proceed();
                    } finally {
                        LauncherRecentsPerf.end("native:taskClick", nativeStartNs);
                    }
                    if (preparedTransitionGeometry
                            && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                        LauncherRecentsPerf.flow("launch:click:clearUnfrozen", recentsView);
                        clearTaskLaunchTransitionGeometry(recentsView, false);
                    }
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook TaskView.onClick",
                    t);
        }
    }

    private static void hookTaskViewLaunchWithAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("launchWithAnimation");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                View recentsView = null;
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
                    boolean stackLaunch = LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                            && !LauncherRecentsCompat.isDesktopTask(taskView);
                    LauncherRecentsPerf.flow("launch:withAnimation:start",
                            recentsView,
                            taskLaunchDetails(
                                    recentsView,
                                    taskView,
                                    stackLaunch));
                    if (stackLaunch) {
                        LauncherRecentsPerf.startSpan("taskLaunch", recentsView);
                        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, true);
                        prepareTaskLaunchTransitionGeometry(recentsView, taskView);
                        freezeTaskLaunchTransitionGeometryIfNeeded(recentsView, taskView);
                    }
                }
                long nativeStartNs = LauncherRecentsPerf.start(recentsView);
                Object result;
                try {
                    result = chain.proceed();
                } finally {
                    LauncherRecentsPerf.end("native:launchWithAnimation", nativeStartNs);
                }
                if (recentsView != null
                        && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                    LauncherRecentsPerf.flow("launch:withAnimation:attachCleanup", recentsView);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);

            Method scaleDownMethod = clazz.getDeclaredMethod("scaleDown");
            scaleDownMethod.setAccessible(true);
            module.intercept(scaleDownMethod, chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof View) {
                    View taskView = (View) thisObject;
                    if (shouldSuppressTaskPressScale(taskView)) {
                        LauncherRecentsTaskVisuals.resetTaskTouchScale(taskView);
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
                        LauncherRecentsTaskVisuals.resetTaskTouchScale(taskView);
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

    private static void hookTaskViewUtilsCreateRecentsWindowAnimator(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_UTILS_CLASS, false, loader);
            Class<?> recentsViewClass =
                    Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
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
                LauncherRecentsState.TaskLaunchTransitionGeometryContext previousContext =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.get();
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.set(
                        new LauncherRecentsState.TaskLaunchTransitionGeometryContext(
                                recentsView,
                                taskView));
                try {
                    return chain.proceed();
                } finally {
                    restoreTaskLaunchTransitionGeometryContext(previousContext);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.TASK_VIEW_UTILS_CLASS, false, loader);
            Class<?> remoteTargetHandleArrayClass =
                    Class.forName(
                            "[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;",
                            false,
                            loader);
            Class<?> recentsViewClass =
                    Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Class<?> pagedOrientationHandlerClass =
                    Class.forName(
                            LauncherRecentsCompat.PAGED_ORIENTATION_HANDLER_CLASS,
                            false,
                            loader);
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
                View previousRecentsView =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.get();
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.set(recentsView);
                try {
                    applyFrozenTaskLaunchLayout(recentsView);
                    Object result = chain.proceed();
                    updateTaskLaunchSiblingExitProgress(recentsView, chain.getArg(0));
                    applyFrozenTaskLaunchLayout(recentsView);
                    return result;
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
            Class<?> clazz =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_SIMULATOR_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setTaskRectTranslation", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                LauncherRecentsState.TaskLaunchTransitionGeometryContext context =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.get();
                if (context == null
                        || !shouldOverrideTaskLaunchStockGeometry(
                        context.recentsView,
                        context.taskView)) {
                    return chain.proceed();
                }
                LauncherRecentsState.TaskLaunchTaskRectTranslation adjustedTranslation =
                        resolveTaskLaunchTaskRectTranslation(
                                context.recentsView,
                                context.taskView);
                Rect startBounds =
                        resolveTaskLaunchTransitionStartBounds(
                                context.recentsView,
                                context.taskView);
                if (adjustedTranslation == null) {
                    return chain.proceed();
                }
                Object thisObject = chain.getThisObject();
                LauncherRecentsCompat.setIntField(
                        thisObject,
                        "mTaskRectTranslationX",
                        adjustedTranslation.translationX);
                LauncherRecentsCompat.setIntField(
                        thisObject,
                        "mTaskRectTranslationY",
                        adjustedTranslation.translationY);
                applyTaskLaunchSimulatorStartBounds(thisObject, startBounds);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
            Method method =
                    clazz.getDeclaredMethod("createAdjacentPageAnimForTaskLaunch", taskViewClass);
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
                animatorSet.play(
                        ObjectAnimator.ofFloat(recentsView, "taskThumbnailSplashAlpha", 0.0f, 1.0f));
                return animatorSet;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.createAdjacentPageAnimForTaskLaunch",
                    t);
        }
    }

    private static void hookRecentsViewGetTaskSizeForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("getTaskSize", Rect.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg0 = chain.getArg(0);
                LauncherRecentsState.TaskLaunchTransitionGeometryContext context =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.get();
                if (!(thisObject instanceof View)
                        || !(arg0 instanceof Rect)
                        || context == null
                        || context.recentsView != thisObject
                        || !shouldOverrideTaskLaunchStockGeometry(
                        context.recentsView,
                        context.taskView)) {
                    return chain.proceed();
                }
                Rect startBounds =
                        resolveTaskLaunchTransitionStartBounds(
                                context.recentsView,
                                context.taskView);
                if (startBounds == null || startBounds.isEmpty()) {
                    return chain.proceed();
                }
                ((Rect) arg0).set(0, 0, startBounds.width(), startBounds.height());
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.getTaskSize for task launch",
                    t);
        }
    }

    private static void hookRecentsViewSetFullscreenProgressForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setFullscreenProgress", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (shouldSuppressTaskLaunchRecentsViewTransform(chain.getThisObject())) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setFullscreenProgress for task launch",
                    t);
        }
    }

    private static void hookRecentsViewSetTaskViewsResistanceTranslationForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setTaskViewsResistanceTranslation", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (shouldSuppressTaskLaunchRecentsViewTransform(chain.getThisObject())) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsView.setTaskViewsResistanceTranslation for task launch",
                    t);
        }
    }

    private static void hookRecentsViewUpdateScrollSynchronously(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
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
                    if (thisObject instanceof View) {
                        applyFrozenTaskLaunchLayout((View) thisObject);
                    }
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
                    if (thisObject instanceof View) {
                        applyFrozenTaskLaunchLayout((View) thisObject);
                    }
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

    private static void hookViewScaleForTaskLaunch(FlymeStatusBarSizer module) {
        hookViewScaleMethodForTaskLaunch(module, "setScaleX");
        hookViewScaleMethodForTaskLaunch(module, "setScaleY");
    }

    private static void hookViewScaleMethodForTaskLaunch(
            FlymeStatusBarSizer module,
            String methodName) {
        try {
            Method method = View.class.getDeclaredMethod(methodName, float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (shouldSuppressTaskLaunchRecentsViewTransform(chain.getThisObject())) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook View." + methodName + " for task launch",
                    t);
        }
    }

    static boolean shouldSuppressStockTaskLaunchTransformMethod(
            View recentsView,
            String methodName) {
        return recentsView != null
                && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && ("updatePageOffsetsForFlyme".equals(methodName)
                || "updatePageScales".equals(methodName)
                || "updateHorizontalOffset".equals(methodName)
                || "updateTaskViewsSnapshotRadius".equals(methodName));
    }

    static boolean shouldSuppressStockTaskLaunchVisualReset(View recentsView) {
        return recentsView != null
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView));
    }

    static boolean shouldSuppressTaskLaunchSynchronousLayout(View recentsView) {
        return recentsView != null
                && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView);
    }

    private static boolean shouldSuppressTaskLaunchRecentsViewTransform(Object thisObject) {
        return thisObject instanceof View
                && LauncherRecentsCompat.isRecentsViewObject((View) thisObject)
                && LauncherRecentsState.isTaskLaunchLayoutFrozen((View) thisObject);
    }

    private static boolean shouldSuppressTaskPressScale(View taskView) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        return LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView);
    }

    static boolean shouldSuppressTaskHandleActionUp(
            View recentsView,
            View taskView) {
        return false;
    }

    private static boolean shouldPrepareTaskLaunchTransitionGeometry(
            View taskView,
            View recentsView) {
        return taskView != null
                && recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && !LauncherRecentsCompat.isDesktopTask(taskView)
                && taskView.getWidth() > 0
                && taskView.getHeight() > 0
                && recentsView.getWidth() > 0
                && recentsView.getHeight() > 0
                && resolveTaskViewIndex(recentsView, taskView) >= 0
                && !LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && !LauncherRecentsState.hasActiveTaskLaunchTransitionGeometry(recentsView);
    }

    static boolean shouldOverrideTaskLaunchStockGeometry(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsCompat.isDesktopTask(taskView)) {
            return false;
        }
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
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
                || LauncherRecentsCompat.invokeBoolean(recentsView, "showAsGrid", false)) {
            return false;
        }
        int taskIndex = resolveTaskViewIndex(recentsView, taskView);
        int currentPage = LauncherRecentsCompat.invokeInt(recentsView, "getCurrentPage", 0);
        return taskIndex >= 0 && taskIndex != currentPage;
    }

    private static void applyTaskLaunchSimulatorStartBounds(
            Object simulator,
            Rect startBounds) {
        if (simulator == null || startBounds == null || startBounds.isEmpty()) {
            return;
        }
        int width = Math.max(1, startBounds.width());
        int height = Math.max(1, startBounds.height());
        setRectField(simulator, "mFullTaskSize", 0, 0, width, height);
        setRectField(simulator, "mCarouselTaskSize", 0, 0, width, height);
        setRectField(
                simulator,
                "mTaskRect",
                startBounds.left,
                startBounds.top,
                startBounds.left + width,
                startBounds.top + height);
        LauncherRecentsCompat.setBooleanField(simulator, "mLayoutValid", false);
    }

    private static void setRectField(
            Object target,
            String fieldName,
            int left,
            int top,
            int right,
            int bottom) {
        Object value = LauncherRecentsCompat.getFieldCompat(target, fieldName);
        if (value instanceof Rect) {
            ((Rect) value).set(left, top, right, bottom);
        }
    }

    private static void restoreTaskLaunchTransitionGeometryContext(
            LauncherRecentsState.TaskLaunchTransitionGeometryContext previousContext) {
        if (previousContext == null) {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.remove();
        } else {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_TRANSITION_GEOMETRY.set(previousContext);
        }
    }

    private static void restoreTaskLaunchScrollCompensationBypass(View previousRecentsView) {
        if (previousRecentsView == null) {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.remove();
        } else {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.set(
                    previousRecentsView);
        }
    }

    private static void prepareTaskLaunchTransitionGeometry(
            View recentsView,
            View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        LauncherRecentsPerf.flow("launch:prepareGeometry",
                recentsView,
                taskLaunchDetails(recentsView, taskView, true));
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (state == null || state.targetTaskView != taskView) {
            state = new LauncherRecentsState.LaunchTransitionGeometryState(
                    taskView,
                    resolveTaskViewIndex(recentsView, taskView));
            LauncherRecentsState.setActiveTaskLaunchTransitionGeometry(recentsView, state);
        }
        captureTaskLaunchTransitionGeometry(recentsView, state);
    }

    static boolean shouldSuppressRecentsLaunchScrollMutation(Object thisObject) {
        if (!(thisObject instanceof View)) {
            return false;
        }
        View view = (View) thisObject;
        View bypassRecentsView =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_SCROLL_COMPENSATION_BYPASS.get();
        if (view == bypassRecentsView && bypassRecentsView != null) {
            return true;
        }
        return LauncherRecentsCompat.isRecentsViewObject(view)
                && LauncherRecentsLayoutEngine.shouldSuppressStockLayoutMutation(view);
    }

    static boolean shouldSuppressTaskLaunchPageMutation(Object thisObject) {
        if (!(thisObject instanceof View)) {
            return false;
        }
        View view = (View) thisObject;
        return LauncherRecentsCompat.isRecentsViewObject(view)
                && LauncherRecentsLayoutEngine.shouldSuppressStockLayoutMutation(view);
    }

    private static LauncherRecentsState.TaskLaunchTaskRectTranslation
            resolveTaskLaunchTaskRectTranslation(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return null;
        }
        Rect startBounds = resolveTaskLaunchTransitionStartBounds(recentsView, taskView);
        if (startBounds == null || startBounds.isEmpty()) {
            return null;
        }
        return new LauncherRecentsState.TaskLaunchTaskRectTranslation(
                startBounds.left,
                startBounds.top);
    }

    private static Rect resolveTaskLaunchTransitionStartBounds(
            View recentsView,
            View taskView) {
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (state == null || state.targetTaskView != taskView) {
            return null;
        }
        if (state.startBounds.isEmpty()) {
            captureTaskLaunchTransitionGeometry(recentsView, state);
        }
        return state.startBounds.isEmpty() ? null : new Rect(state.startBounds);
    }

    private static void captureTaskLaunchTransitionGeometry(
            View recentsView,
            LauncherRecentsState.LaunchTransitionGeometryState state) {
        if (recentsView == null || state == null || state.targetTaskView == null) {
            return;
        }
        Rect startBounds = resolveTaskLaunchVisualBounds(recentsView, state.targetTaskView);
        if (startBounds != null && !startBounds.isEmpty()) {
            state.startBounds.set(startBounds);
        }
    }

    private static Rect resolveTaskLaunchVisualBounds(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return null;
        }
        Rect localBounds = resolveTaskLaunchThumbnailBounds(taskView);
        if (localBounds == null || localBounds.isEmpty()) {
            localBounds = new Rect(0, 0, taskView.getWidth(), taskView.getHeight());
        }
        float scaleX = Math.max(0.0001f, taskView.getScaleX());
        float scaleY = Math.max(0.0001f, taskView.getScaleY());
        float pivotX = taskView.getPivotX();
        float pivotY = taskView.getPivotY();
        float left = taskView.getX()
                + pivotX
                + ((localBounds.left - pivotX) * scaleX)
                - recentsView.getScrollX();
        float top = taskView.getY()
                + pivotY
                + ((localBounds.top - pivotY) * scaleY)
                - recentsView.getScrollY();
        float right = taskView.getX()
                + pivotX
                + ((localBounds.right - pivotX) * scaleX)
                - recentsView.getScrollX();
        float bottom = taskView.getY()
                + pivotY
                + ((localBounds.bottom - pivotY) * scaleY)
                - recentsView.getScrollY();
        int roundedLeft = Math.round(left);
        int roundedTop = Math.round(top);
        return new Rect(
                roundedLeft,
                roundedTop,
                Math.max(roundedLeft + 1, Math.round(right)),
                Math.max(roundedTop + 1, Math.round(bottom)));
    }

    private static Rect resolveTaskLaunchThumbnailBounds(View taskView) {
        if (taskView == null) {
            return null;
        }
        Rect bounds = new Rect();
        LauncherRecentsCompat.invokeCompat(
                taskView,
                "getThumbnailBounds",
                new Class<?>[]{Rect.class, boolean.class},
                bounds,
                false);
        return bounds.isEmpty() ? null : bounds;
    }

    private static void freezeTaskLaunchTransitionGeometryIfNeeded(
            View recentsView,
            View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (state == null) {
            state = new LauncherRecentsState.LaunchTransitionGeometryState(
                    taskView,
                    resolveTaskViewIndex(recentsView, taskView));
            LauncherRecentsState.setActiveTaskLaunchTransitionGeometry(recentsView, state);
        }
        if (state.frozen) {
            return;
        }
        captureTaskLaunchTransitionGeometry(recentsView, state);
        state.frozen = true;
        captureFrozenTaskLaunchLayout(recentsView, state);
        applyFrozenTaskLaunchLayout(recentsView);
        recentsView.invalidate();
    }

    static void applyFrozenTaskLaunchLayout(View recentsView) {
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (recentsView == null || state == null || !state.frozen) {
            return;
        }
        if (state.frozenTaskStates.isEmpty()) {
            captureFrozenTaskLaunchLayout(recentsView, state);
        }
        boolean primaryScrollHorizontal = isPrimaryScrollHorizontal(recentsView);
        for (int i = 0; i < state.frozenTaskStates.size(); i++) {
            LauncherRecentsState.TaskLaunchFrozenTaskState taskState =
                    state.frozenTaskStates.get(i);
            if (taskState == null || taskState.taskView == null) {
                continue;
            }
            View taskView = taskState.taskView;
            if (LauncherRecentsCompat.resolveOwningRecentsView(taskView) != recentsView) {
                continue;
            }
            float exitProgress = resolveTaskLaunchSiblingExitProgress(
                    recentsView,
                    state,
                    taskState);
            if (taskState.stackVisualState != null) {
                LauncherRecentsTaskVisuals.applyStackTaskVisualState(
                        taskView,
                        taskState.stackVisualState);
            }
            taskView.setVisibility(taskState.visibility);
            taskView.setPivotX(taskState.pivotX);
            taskView.setPivotY(taskState.pivotY);
            taskView.setScaleX(taskState.scaleX);
            taskView.setScaleY(taskState.scaleY);
            taskView.setAlpha(taskState.alpha);
            taskView.setTranslationZ(taskState.translationZ);
            float siblingExitOffset = resolveTaskLaunchSiblingExitOffset(
                    recentsView,
                    state,
                    taskState,
                    exitProgress,
                    primaryScrollHorizontal);
            taskView.setX(primaryScrollHorizontal ? taskState.x + siblingExitOffset : taskState.x);
            taskView.setY(primaryScrollHorizontal ? taskState.y : taskState.y + siblingExitOffset);
        }
        recentsView.invalidate();
    }

    private static float resolveTaskLaunchSiblingExitProgress(
            View recentsView,
            LauncherRecentsState.LaunchTransitionGeometryState state,
            LauncherRecentsState.TaskLaunchFrozenTaskState taskState) {
        if (recentsView == null
                || state == null
                || taskState == null
                || taskState.target) {
            return 0f;
        }
        float progress = LauncherRecentsLayoutEngine.clamp(state.siblingExitProgress, 0f, 1f);
        return progress * progress;
    }

    private static float resolveTaskLaunchSiblingExitOffset(
            View recentsView,
            LauncherRecentsState.LaunchTransitionGeometryState state,
            LauncherRecentsState.TaskLaunchFrozenTaskState taskState,
            float progress,
            boolean primaryScrollHorizontal) {
        if (progress <= 0f || recentsView == null || state == null || taskState == null) {
            return 0f;
        }
        LauncherRecentsState.TaskLaunchFrozenTaskState targetState =
                findTargetFrozenTaskLaunchState(state);
        if (targetState == null || taskState.target) {
            return 0f;
        }
        float taskSize = primaryScrollHorizontal
                ? taskState.taskView.getWidth()
                : taskState.taskView.getHeight();
        float targetSize = primaryScrollHorizontal
                ? targetState.taskView.getWidth()
                : targetState.taskView.getHeight();
        float taskScale = primaryScrollHorizontal ? taskState.scaleX : taskState.scaleY;
        float targetScale = primaryScrollHorizontal ? targetState.scaleX : targetState.scaleY;
        float distance = (primaryScrollHorizontal ? recentsView.getWidth() : recentsView.getHeight())
                + (taskSize * TASK_LAUNCH_SIBLING_EXIT_EXTRA_WIDTH_RATIO);
        float taskCenter = (primaryScrollHorizontal ? taskState.x : taskState.y)
                + (taskSize * taskScale * 0.5f);
        float targetCenter = (primaryScrollHorizontal ? targetState.x : targetState.y)
                + (targetSize * targetScale * 0.5f);
        float direction = taskCenter < targetCenter ? -1f : 1f;
        return direction * distance * progress;
    }

    private static LauncherRecentsState.TaskLaunchFrozenTaskState findTargetFrozenTaskLaunchState(
            LauncherRecentsState.LaunchTransitionGeometryState state) {
        if (state == null) {
            return null;
        }
        for (int i = 0; i < state.frozenTaskStates.size(); i++) {
            LauncherRecentsState.TaskLaunchFrozenTaskState taskState =
                    state.frozenTaskStates.get(i);
            if (taskState != null && taskState.target) {
                return taskState;
            }
        }
        return findFrozenTaskLaunchState(state, state.targetTaskView);
    }

    private static LauncherRecentsState.TaskLaunchFrozenTaskState findFrozenTaskLaunchState(
            LauncherRecentsState.LaunchTransitionGeometryState state,
            View taskView) {
        if (state == null || taskView == null) {
            return null;
        }
        for (int i = 0; i < state.frozenTaskStates.size(); i++) {
            LauncherRecentsState.TaskLaunchFrozenTaskState taskState =
                    state.frozenTaskStates.get(i);
            if (taskState != null && taskState.taskView == taskView) {
                return taskState;
            }
        }
        return null;
    }

    private static void updateTaskLaunchSiblingExitProgress(
            View recentsView,
            Object remoteTargetHandlesObject) {
        LauncherRecentsState.LaunchTransitionGeometryState state =
                LauncherRecentsState.getActiveTaskLaunchTransitionGeometry(recentsView);
        if (state == null || !state.frozen || !(remoteTargetHandlesObject instanceof Object[])) {
            return;
        }
        Object[] remoteTargetHandles = (Object[]) remoteTargetHandlesObject;
        if (remoteTargetHandles.length == 0) {
            return;
        }
        Object simulator = LauncherRecentsCompat.invokeCompat(
                remoteTargetHandles[0],
                "getTaskViewSimulator");
        Object progressObject = LauncherRecentsCompat.getFieldCompat(simulator, "fullScreenProgress");
        float progress = LauncherRecentsCompat.readFloatField(progressObject, "value", 0f);
        state.siblingExitProgress = LauncherRecentsLayoutEngine.clamp(progress, 0f, 1f);
    }

    private static void captureFrozenTaskLaunchLayout(
            View recentsView,
            LauncherRecentsState.LaunchTransitionGeometryState state) {
        if (recentsView == null || state == null) {
            return;
        }
        state.frozenTaskStates.clear();
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            state.frozenTaskStates.add(new LauncherRecentsState.TaskLaunchFrozenTaskState(
                    taskView,
                    i == state.targetIndex || taskView == state.targetTaskView,
                    taskView.getVisibility(),
                    taskView.getX(),
                    taskView.getY(),
                    taskView.getPivotX(),
                    taskView.getPivotY(),
                    taskView.getScaleX(),
                    taskView.getScaleY(),
                    taskView.getAlpha(),
                    taskView.getTranslationZ(),
                    LauncherRecentsState.LAST_APPLIED_STACK_TASK_VISUAL_STATES.get(taskView)));
        }
    }

    private static void attachTaskLaunchCleanup(View recentsView, Object launchResult) {
        Runnable cleanup = () -> clearTaskLaunchTransitionGeometry(recentsView, true);
        if (launchResult == null) {
            cleanup.run();
            return;
        }
        if (!LauncherRecentsCompat.invokeMethodReflectively(
                launchResult,
                "add",
                new Class<?>[]{Runnable.class},
                cleanup)) {
            cleanup.run();
        }
    }

    static void clearTaskLaunchFrozenForNewGesture(View recentsView) {
        if (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
            clearTaskLaunchTransitionGeometry(recentsView, false);
        }
    }

    static void clearTaskLaunchTransitionGeometry(View recentsView, boolean restoreStack) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsPerf.flow("launch:clearGeometry",
                recentsView,
                "restoreStack=" + restoreStack
                        + " frozen=" + LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView));
        LauncherRecentsPerf.endSpan("taskLaunch", recentsView);
        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, false);
        LauncherRecentsState.clearActiveTaskLaunchTransitionGeometry(recentsView);
        if (!restoreStack || !recentsView.isAttachedToWindow() || !recentsView.isShown()) {
            return;
        }
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        if (LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            LauncherRecentsLayoutEngine.applyStackLayout(
                    recentsView,
                    false,
                    "launchClearRestore",
                    true);
        } else {
            LauncherRecentsLayoutEngine.reapplyOriginalTransforms(recentsView);
        }
        recentsView.invalidate();
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

    private static String taskLaunchDetails(
            View recentsView,
            View taskView,
            boolean preparedTransitionGeometry) {
        return "taskIndex=" + resolveTaskViewIndex(recentsView, taskView)
                + " desktop=" + LauncherRecentsCompat.isDesktopTask(taskView)
                + " prepared=" + preparedTransitionGeometry;
    }

    private static int resolveTaskViewIndex(View recentsView, View taskView) {
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            if (LauncherRecentsCompat.getTaskViewAt(recentsView, i) == taskView) {
                return i;
            }
        }
        return -1;
    }

}
