package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class LauncherRecentsLaunchController {
    private static final long TASK_LAUNCH_HANDOFF_DURATION_MS = 360L;
    private static final long TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS = 320L;
    private static final long TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS = 1200L;
    private static final float TASK_LAUNCH_REAR_PROMOTE_FRACTION = 0.42f;
    private static final float TASK_LAUNCH_TARGET_END_SCALE_BLEED = 1.0f;
    private static final float TASK_LAUNCH_TARGET_END_FULLSCREEN_PROGRESS = 0.94f;
    private static final float TASK_LAUNCH_ADJACENT_SHIFT_RATIO = 0.15f;
    private static final float TASK_LAUNCH_SIBLING_END_ALPHA = 0.0f;
    private static final DecelerateInterpolator TASK_LAUNCH_HANDOFF_INTERPOLATOR =
            new DecelerateInterpolator(1.12f);

    private LauncherRecentsLaunchController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookRecentsViewResetTaskVisuals(module, loader);
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
        hookRecentsViewCreateTaskLaunchAnimation(module, loader);
        hookRecentsViewCreateAdjacentPageAnimForTaskLaunch(module, loader);
        hookRecentsViewUpdateScrollSynchronously(module, loader);
        hookViewScrollByForTaskLaunch(module);
        hookViewScrollToForTaskLaunch(module);
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
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onDetachedFromWindow");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object result = chain.proceed();
                if (thisObject instanceof View) {
                    View recentsView = (View) thisObject;
                    if (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
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

    private static void hookPagedViewSetCurrentPageForTaskLaunch(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.PAGED_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setCurrentPage", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                if (LauncherRecentsTouchController.handleStackDismissSetCurrentPage(
                        thisObject,
                        chain.getArg(0))) {
                    return null;
                }
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
                if (shouldSuppressTaskLaunchPageMutation(thisObject)) {
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
                        return chain.proceed();
                    }
                    View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
                    if (shouldStartTaskLaunchHandoff(taskView, recentsView)) {
                        startTaskLaunchHandoff(taskView, recentsView);
                        return null;
                    }
                    if (shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
                        if (handleTaskClickWithoutSystemAnimation(taskView, recentsView)) {
                            return null;
                        }
                        return chain.proceed();
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
                    if (shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
                        prepareTaskLaunchWithoutSystemAnimation(recentsView, taskView);
                        if (launchTaskWithoutSystemAnimation(taskView, recentsView)) {
                            return null;
                        }
                    }
                    if (LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                            && !LauncherRecentsCompat.isDesktopTask(taskView)) {
                        LauncherRecentsState.trackRecentsView(recentsView);
                        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                        LauncherRecentsState.TASK_LAUNCH_REQUEST_STARTED.put(
                                recentsView,
                                Boolean.TRUE);
                        freezeTaskLaunchLayoutIfNeeded(recentsView, taskView);
                    }
                }
                Object result = chain.proceed();
                if (recentsView != null
                        && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
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
                LauncherRecentsState.TaskLaunchSimulatorTranslationContext previousContext =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.get();
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.set(
                        new LauncherRecentsState.TaskLaunchSimulatorTranslationContext(
                                recentsView,
                                taskView));
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
            Class<?> clazz =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_SIMULATOR_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setTaskRectTranslation", int.class, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                LauncherRecentsState.TaskLaunchSimulatorTranslationContext context =
                        LauncherRecentsState.ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.get();
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
                LauncherRecentsCompat.invokeMethodReflectively(
                        thisObject,
                        "calculateTaskSize",
                        LauncherRecentsCompat.NO_ARGS);
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

    private static void hookRecentsViewCreateTaskLaunchAnimation(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LauncherRecentsCompat.RECENTS_VIEW_CLASS, false, loader);
            Class<?> taskViewClass =
                    Class.forName(LauncherRecentsCompat.TASK_VIEW_CLASS, false, loader);
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
                        LauncherRecentsCompat.createPendingAnimationInstance(
                                pendingAnimationConstructor,
                                0L);
                if (emptyPendingAnimation == null) {
                    return chain.proceed();
                }
                LauncherRecentsCompat.writeField(recentsView, "mPendingAnimation", emptyPendingAnimation);
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

    static boolean shouldSuppressStockTaskLaunchTransformMethod(
            View recentsView,
            String methodName) {
        return recentsView != null
                && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                && ("updatePageOffsetsForFlyme".equals(methodName)
                || "updatePageScales".equals(methodName));
    }

    static boolean shouldSuppressStockTaskLaunchVisualReset(View recentsView) {
        return recentsView != null && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView);
    }

    static boolean shouldSuppressStockTaskLaunchAnimationBuild(
            View recentsView,
            View taskView) {
        return shouldOverrideTaskLaunchStockGeometry(recentsView, taskView);
    }

    static boolean shouldSuppressTaskLaunchSynchronousLayout(View recentsView) {
        return recentsView != null
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTouchController.shouldSuppressStackDismissPageMutation(
                recentsView));
    }

    private static boolean shouldSuppressTaskPressScale(View taskView) {
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
        return LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView);
    }

    private static boolean handleTaskClickWithoutSystemAnimation(
            View taskView,
            View recentsView) {
        if (!shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView)) {
            return false;
        }
        Object splitSelectResult = LauncherRecentsCompat.invokeCompat(
                taskView,
                "confirmSecondSplitSelectApp",
                LauncherRecentsCompat.NO_ARGS);
        if (splitSelectResult instanceof Boolean && (Boolean) splitSelectResult) {
            return true;
        }
        LauncherRecentsCompat.invokeCompat(taskView, "updateUsageState", LauncherRecentsCompat.NO_ARGS);
        prepareTaskLaunchWithoutSystemAnimation(recentsView, taskView);
        return launchTaskWithoutSystemAnimation(taskView, recentsView);
    }

    static boolean shouldSuppressTaskHandleActionUp(
            View recentsView,
            View taskView) {
        return shouldReplaceTaskLaunchWithNoAnimation(recentsView, taskView);
    }

    private static boolean shouldStartTaskLaunchHandoff(View taskView, View recentsView) {
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
                && !LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.containsKey(recentsView);
    }

    static boolean shouldReplaceTaskLaunchWithNoAnimation(
            View recentsView,
            View taskView) {
        return taskView != null
                && recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && !LauncherRecentsCompat.isDesktopTask(taskView);
    }

    static boolean shouldOverrideTaskLaunchStockGeometry(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsCompat.isDesktopTask(taskView)) {
            return false;
        }
        LauncherRecentsState.LaunchHandoffState state =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
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

    private static void restoreTaskLaunchSimulatorTranslationContext(
            LauncherRecentsState.TaskLaunchSimulatorTranslationContext previousContext) {
        if (previousContext == null) {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.remove();
        } else {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_SIMULATOR_TRANSLATION.set(previousContext);
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

    private static void prepareTaskLaunchWithoutSystemAnimation(
            View recentsView,
            View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        cancelTaskLaunchHandoff(recentsView, true);
        LauncherRecentsState.TASK_LAUNCH_REQUEST_STARTED.put(recentsView, Boolean.TRUE);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        freezeTaskLaunchLayoutIfNeeded(recentsView, taskView);
        recentsView.invalidate();
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
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(view)
                || LauncherRecentsTouchController.shouldSuppressStackDismissPageMutation(view));
    }

    static boolean shouldSuppressTaskLaunchPageMutation(Object thisObject) {
        if (!(thisObject instanceof View)) {
            return false;
        }
        View view = (View) thisObject;
        return LauncherRecentsCompat.isRecentsViewObject(view)
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(view)
                || LauncherRecentsTouchController.shouldSuppressStackDismissPageMutation(view));
    }

    private static boolean launchTaskWithoutSystemAnimation(View taskView, View recentsView) {
        if (taskView == null) {
            return false;
        }
        ClassLoader loader = taskView.getClass().getClassLoader();
        if (loader == null) {
            loader = LauncherRecentsLaunchController.class.getClassLoader();
        }
        if (loader == null) {
            return false;
        }
        try {
            Class<?> function1Class = Class.forName("kotlin.jvm.functions.Function1", false, loader);
            final Object kotlinUnitInstance = LauncherRecentsCompat.resolveKotlinUnitInstance(loader);
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
            return LauncherRecentsCompat.invokeMethodReflectively(
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
        // A successful task launch is leaving overview, so delayed cleanup should only clear the
        // frozen handoff state. Restoring the stack while the launcher window is still transitioning
        // out can flash the original recents cards for a frame.
        Runnable cleanup = () -> clearTaskLaunchHandoff(recentsView, false);
        Handler handler = LauncherRecentsState.ensureMainHandler();
        if (handler != null) {
            handler.postDelayed(cleanup, TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS);
        } else {
            recentsView.postDelayed(cleanup, TASK_LAUNCH_NO_ANIMATION_CLEANUP_DELAY_MS);
        }
    }

    private static LauncherRecentsState.TaskLaunchTaskRectTranslation
            resolveTaskLaunchTaskRectTranslation(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return null;
        }
        Rect baseTaskRect = new Rect();
        LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getTaskSize",
                new Class<?>[]{Rect.class},
                baseTaskRect);
        if (baseTaskRect.isEmpty()) {
            return null;
        }
        float actualTaskLeft = taskView.getX() - recentsView.getScrollX();
        float actualTaskTop = taskView.getY() - recentsView.getScrollY();
        return new LauncherRecentsState.TaskLaunchTaskRectTranslation(
                Math.round(actualTaskLeft - baseTaskRect.left),
                Math.round(actualTaskTop - baseTaskRect.top));
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

    private static float[] resolveTaskLaunchTargetCenter(View recentsView) {
        if (recentsView == null) {
            return null;
        }
        View rootView = recentsView.getRootView();
        if (rootView == null || rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            return new float[]{
                    recentsView.getScrollX() + (recentsView.getWidth() * 0.5f),
                    recentsView.getScrollY() + (recentsView.getHeight() * 0.5f)
            };
        }
        int[] recentsLocation = new int[2];
        int[] rootLocation = new int[2];
        recentsView.getLocationInWindow(recentsLocation);
        rootView.getLocationInWindow(rootLocation);
        return new float[]{
                (rootLocation[0] - recentsLocation[0])
                        + (rootView.getWidth() * 0.5f)
                        + recentsView.getScrollX(),
                (rootLocation[1] - recentsLocation[1])
                        + (rootView.getHeight() * 0.5f)
                        + recentsView.getScrollY()
        };
    }

    private static void startTaskLaunchHandoff(View taskView, View recentsView) {
        if (taskView == null || recentsView == null) {
            return;
        }
        cancelTaskLaunchHandoff(recentsView, true);
        LauncherRecentsState.TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsState.LaunchHandoffState state =
                new LauncherRecentsState.LaunchHandoffState(
                        taskView,
                        resolveTaskViewIndex(recentsView, taskView),
                        shouldPromoteRearTaskDuringLaunch(recentsView, taskView),
                        true);
        LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.put(recentsView, state);
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(state.promoteRearCard
                ? TASK_LAUNCH_HANDOFF_DURATION_MS
                : TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS);
        animator.setInterpolator(TASK_LAUNCH_HANDOFF_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            state.progress = value instanceof Float ? (Float) value : 0f;
            LauncherRecentsLayoutEngine.applyDynamicStackLayoutIfNeeded(recentsView);
            recentsView.invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.get(recentsView)
                        == animation) {
                    LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.remove(recentsView);
                }
                if (LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView) != state) {
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
        LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.put(recentsView, animator);
        animator.start();
    }

    private static void completeTaskLaunchHandoff(
            View recentsView,
            LauncherRecentsState.LaunchHandoffState state) {
        if (recentsView == null || state == null) {
            return;
        }
        state.progress = 1f;
        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        state.frozen = true;
        recentsView.invalidate();
    }

    private static void continueTaskLaunchClick(View taskView, View recentsView) {
        if (taskView == null) {
            clearTaskLaunchHandoff(recentsView, true);
            return;
        }
        LauncherRecentsState.BYPASS_TASK_CLICK_INTERCEPTION.put(taskView, Boolean.TRUE);
        LauncherRecentsCompat.writeField(taskView, "mDownTime", SystemClock.uptimeMillis());
        try {
            if (!LauncherRecentsCompat.invokeMethodReflectively(
                    taskView,
                    "onClick",
                    LauncherRecentsCompat.NO_ARGS)) {
                clearTaskLaunchHandoff(recentsView, true);
                return;
            }
            if (!LauncherRecentsState.consumeTaskLaunchRequestStarted(recentsView)
                    && LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
                clearTaskLaunchHandoff(recentsView, true);
            }
        } finally {
            LauncherRecentsState.BYPASS_TASK_CLICK_INTERCEPTION.remove(taskView);
        }
    }

    private static void freezeTaskLaunchLayoutIfNeeded(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        LauncherRecentsState.LaunchHandoffState state =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.get(recentsView);
        if (state == null) {
            state = new LauncherRecentsState.LaunchHandoffState(
                    taskView,
                    resolveTaskViewIndex(recentsView, taskView),
                    false,
                    false);
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.put(recentsView, state);
        }
        if (state.frozen) {
            return;
        }
        state.progress = 1f;
        if (state.handoffEnabled) {
            LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
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
        if (!LauncherRecentsCompat.invokeMethodReflectively(
                launchResult,
                "add",
                new Class<?>[]{Runnable.class},
                cleanup)) {
            cleanup.run();
        }
    }

    static void clearTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.TASK_LAUNCH_REQUEST_STARTED.remove(recentsView);
        LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.remove(recentsView);
        cancelTaskLaunchHandoff(recentsView, false);
        if (!restoreStack || !recentsView.isAttachedToWindow() || !recentsView.isShown()) {
            return;
        }
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        if (LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
        } else {
            LauncherRecentsLayoutEngine.reapplyOriginalTransforms(recentsView);
        }
        recentsView.invalidate();
    }

    static void cancelTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        ValueAnimator animator =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (restoreStack) {
            LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFFS.remove(recentsView);
        }
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

    private static boolean shouldPromoteRearTaskDuringLaunch(View recentsView, View taskView) {
        if (taskView == null) {
            return false;
        }
        View frontTaskView = resolveFrontMostTaskView(recentsView);
        if (frontTaskView == null || frontTaskView == taskView) {
            return false;
        }
        return LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f)
                < (LauncherRecentsCompat.readFloatField(frontTaskView, "nonGridScale", 1f) - 0.02f);
    }

    private static View resolveFrontMostTaskView(View recentsView) {
        View frontTaskView = null;
        float highestZ = Float.NEGATIVE_INFINITY;
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
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

    static void applyLaunchHandoffLayout(
            View recentsView,
            LauncherRecentsState.LaunchHandoffState state) {
        if (recentsView == null
                || state == null
                || state.frozen
                || !state.handoffEnabled
                || state.targetTaskView == null) {
            return;
        }
        View targetTaskView = state.targetTaskView;
        if (LauncherRecentsCompat.resolveOwningRecentsView(targetTaskView) != recentsView) {
            return;
        }
        View frontTaskView = resolveFrontMostTaskView(recentsView);
        float progress = state.progress;
        float targetFullscreenProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(
                        progress,
                        state.promoteRearCard
                                ? TASK_LAUNCH_REAR_PROMOTE_FRACTION * 0.32f
                                : 0.18f,
                        1f));
        float adjacentMoveProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(progress, 0.04f, 0.76f));
        float adjacentFadeProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(progress, 0.20f, 0.94f));
        float otherFadeProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(progress, 0.12f, 0.56f));
        float targetExtraZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 28);
        float adjacentShiftPx = Math.min(
                Math.max(targetTaskView.getWidth(), recentsView.getWidth())
                        * TASK_LAUNCH_ADJACENT_SHIFT_RATIO,
                FlymeStatusBarSizer.dp(recentsView.getContext(), 120));
        float anchorZ = frontTaskView != null
                ? frontTaskView.getTranslationZ()
                : targetTaskView.getTranslationZ();
        float targetWidth = Math.max(1f, targetTaskView.getWidth());
        float targetHeight = Math.max(1f, targetTaskView.getHeight());
        Rect targetThumbnailBounds = resolveTaskLaunchThumbnailBounds(targetTaskView);
        float targetFocusCenterX = targetWidth * 0.5f;
        float targetFocusCenterY = targetHeight * 0.5f;
        float targetFocusWidth = targetWidth;
        float targetFocusHeight = targetHeight;
        if (targetThumbnailBounds != null) {
            targetFocusCenterX = targetThumbnailBounds.exactCenterX();
            targetFocusCenterY = targetThumbnailBounds.exactCenterY();
            targetFocusWidth = Math.max(1f, targetThumbnailBounds.width());
            targetFocusHeight = Math.max(1f, targetThumbnailBounds.height());
        }
        float startTargetPivotX = targetTaskView.getPivotX();
        float startTargetPivotY = targetTaskView.getPivotY();
        float startTargetScaleX = Math.max(0.0001f, targetTaskView.getScaleX());
        float startTargetScaleY = Math.max(0.0001f, targetTaskView.getScaleY());
        float currentTargetCenterX = targetTaskView.getX()
                + startTargetPivotX
                + ((targetFocusCenterX - startTargetPivotX) * startTargetScaleX);
        float currentTargetCenterY = targetTaskView.getY()
                + startTargetPivotY
                + ((targetFocusCenterY - startTargetPivotY) * startTargetScaleY);
        float targetPivotCompensationX =
                currentTargetCenterX - (targetTaskView.getX() + targetFocusCenterX);
        float targetPivotCompensationY =
                currentTargetCenterY - (targetTaskView.getY() + targetFocusCenterY);
        float[] handoffTargetCenter = resolveTaskLaunchTargetCenter(recentsView);
        float viewportCenterX = handoffTargetCenter != null
                ? handoffTargetCenter[0]
                : recentsView.getScrollX() + (recentsView.getWidth() * 0.5f);
        float viewportCenterY = handoffTargetCenter != null
                ? handoffTargetCenter[1]
                : recentsView.getScrollY() + (recentsView.getHeight() * 0.5f);
        float targetDeltaX = viewportCenterX - currentTargetCenterX;
        float targetDeltaY = viewportCenterY - currentTargetCenterY;
        View rootView = recentsView.getRootView();
        float targetViewportWidth = rootView != null && rootView.getWidth() > 0
                ? rootView.getWidth()
                : recentsView.getWidth();
        float targetViewportHeight = rootView != null && rootView.getHeight() > 0
                ? rootView.getHeight()
                : recentsView.getHeight();
        float targetStartNonGridScale =
                LauncherRecentsCompat.readFloatField(targetTaskView, "nonGridScale", 1f);
        float scaleNormalizer = Math.abs(targetStartNonGridScale) > 0.0001f
                ? Math.max(0.0001f, targetTaskView.getScaleX() / targetStartNonGridScale)
                : 1f;
        float targetEndScale = Math.max(
                Math.max(1f, targetViewportWidth / targetFocusWidth),
                Math.max(1f, targetViewportHeight / targetFocusHeight))
                / scaleNormalizer
                * TASK_LAUNCH_TARGET_END_SCALE_BLEED;

        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            float startHorizontalOffsetX =
                    LauncherRecentsCompat.readFloatField(taskView, "horizontalOffsetTranslationX", 0f);
            float startTaskOffsetX =
                    LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationX", 0f);
            float startTaskOffsetY =
                    LauncherRecentsCompat.readFloatField(taskView, "taskOffsetTranslationY", 0f);
            float startBoxTranslationY = LauncherRecentsCompat.readFloatField(
                    taskView,
                    "boxTranslationY",
                    LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView));
            float startScale = LauncherRecentsCompat.readFloatField(taskView, "nonGridScale", 1f);
            float startAlpha = LauncherRecentsTaskVisuals.readStableAlpha(taskView);
            float startTranslationZ = taskView.getTranslationZ();
            float startFullscreenProgress =
                    LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f);
            if (taskView == targetTaskView) {
                taskView.setPivotX(targetFocusCenterX);
                taskView.setPivotY(targetFocusCenterY);
                LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(
                        taskView,
                        startHorizontalOffsetX);
                LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startTaskOffsetX + targetPivotCompensationX,
                                startTaskOffsetX + targetPivotCompensationX + targetDeltaX,
                                targetFullscreenProgress));
                LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startTaskOffsetY + targetPivotCompensationY,
                                startTaskOffsetY + targetPivotCompensationY + targetDeltaY,
                                targetFullscreenProgress));
                LauncherRecentsTaskVisuals.setBoxTranslationY(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startBoxTranslationY,
                                LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                                targetFullscreenProgress));
                LauncherRecentsTaskVisuals.setNonGridScale(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startScale,
                                targetEndScale,
                                targetFullscreenProgress));
                LauncherRecentsTaskVisuals.setStableAlpha(taskView, 1f);
                // Stop just before TaskView's fullscreen terminal state to avoid a last-frame
                // relayout/visibility flip before launchWithoutAnimation switches activities.
                LauncherRecentsTaskVisuals.setFullscreenProgress(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startFullscreenProgress,
                                TASK_LAUNCH_TARGET_END_FULLSCREEN_PROGRESS,
                                targetFullscreenProgress));
                LauncherRecentsTaskVisuals.setTranslationZ(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startTranslationZ,
                                Math.max(anchorZ, startTranslationZ) + targetExtraZ,
                                targetFullscreenProgress));
                continue;
            }
            boolean isImmediateLeft = state.targetIndex >= 0 && i == state.targetIndex - 1;
            boolean isImmediateRight = state.targetIndex >= 0 && i == state.targetIndex + 1;
            if (isImmediateLeft || isImmediateRight) {
                float direction = isImmediateLeft ? -1f : 1f;
                LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(taskView, startHorizontalOffsetX);
                LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(
                        taskView,
                        startTaskOffsetX + (direction * adjacentShiftPx * adjacentMoveProgress));
                LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, startTaskOffsetY);
                LauncherRecentsTaskVisuals.setBoxTranslationY(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startBoxTranslationY,
                                LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                                adjacentFadeProgress));
                LauncherRecentsTaskVisuals.setNonGridScale(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startScale,
                                startScale * 0.985f,
                                adjacentMoveProgress));
                LauncherRecentsTaskVisuals.setStableAlpha(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startAlpha,
                                startAlpha * TASK_LAUNCH_SIBLING_END_ALPHA,
                                adjacentFadeProgress));
                LauncherRecentsTaskVisuals.setFullscreenProgress(taskView, startFullscreenProgress);
                LauncherRecentsTaskVisuals.setTranslationZ(
                        taskView,
                        LauncherRecentsLayoutEngine.lerp(
                                startTranslationZ,
                                Math.max(0f, startTranslationZ - targetExtraZ),
                                adjacentFadeProgress));
                continue;
            }
            LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(taskView, startHorizontalOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(taskView, startTaskOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(taskView, startTaskOffsetY);
            LauncherRecentsTaskVisuals.setBoxTranslationY(
                    taskView,
                    LauncherRecentsLayoutEngine.lerp(
                            startBoxTranslationY,
                            LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(taskView),
                            otherFadeProgress));
            LauncherRecentsTaskVisuals.setNonGridScale(taskView, startScale);
            LauncherRecentsTaskVisuals.setStableAlpha(
                    taskView,
                    LauncherRecentsLayoutEngine.lerp(
                            startAlpha,
                            startAlpha * TASK_LAUNCH_SIBLING_END_ALPHA,
                            otherFadeProgress));
            LauncherRecentsTaskVisuals.setFullscreenProgress(taskView, startFullscreenProgress);
            LauncherRecentsTaskVisuals.setTranslationZ(
                    taskView,
                    LauncherRecentsLayoutEngine.lerp(
                            startTranslationZ,
                            Math.max(0f, startTranslationZ - targetExtraZ),
                            otherFadeProgress));
        }
    }
}
