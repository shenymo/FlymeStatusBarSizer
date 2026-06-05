package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class LauncherRecentsLaunchController {
    private static final long TASK_LAUNCH_HANDOFF_DURATION_MS = 360L;
    private static final long TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS = 320L;
    private static final float TASK_LAUNCH_REAR_PROMOTE_FRACTION = 0.42f;
    private static final float TASK_LAUNCH_TARGET_END_SCALE_BLEED = 1.0f;
    private static final float TASK_LAUNCH_TARGET_END_FULLSCREEN_PROGRESS = 0.94f;
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
                if (LauncherRecentsTouchController.handleStackDismissSetCurrentPage(thisObject)) {
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
                        return chain.proceed();
                    }
                    View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(taskView);
                    if (shouldStartTaskLaunchHandoff(taskView, recentsView)) {
                        if (!prepareTaskLaunchPreflight(taskView, recentsView)) {
                            return null;
                        }
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
                        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, true);
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
        return recentsView != null
                && (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)
                || LauncherRecentsTransitionController.isBlankTapHomeExitActive(recentsView));
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
        if (!prepareTaskLaunchPreflight(taskView, recentsView)) {
            return true;
        }
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
                && !LauncherRecentsState.hasActiveTaskLaunchHandoff(recentsView);
    }

    static boolean shouldReplaceTaskLaunchWithNoAnimation(
            View recentsView,
            View taskView) {
        return taskView != null
                && recentsView != null
                && LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                && !LauncherRecentsCompat.isDesktopTask(taskView);
    }

    private static boolean prepareTaskLaunchPreflight(View taskView, View recentsView) {
        if (taskView == null || recentsView == null) {
            return false;
        }
        Object clearAllButton = LauncherRecentsCompat.invokeCompat(
                recentsView,
                "getClearAllButton",
                LauncherRecentsCompat.NO_ARGS);
        if (clearAllButton == null) {
            return false;
        }
        Object splitSelectResult = LauncherRecentsCompat.invokeCompat(
                taskView,
                "confirmSecondSplitSelectApp",
                LauncherRecentsCompat.NO_ARGS);
        if (splitSelectResult instanceof Boolean && (Boolean) splitSelectResult) {
            return false;
        }
        LauncherRecentsCompat.invokeCompat(taskView, "updateUsageState", LauncherRecentsCompat.NO_ARGS);
        logTaskLaunchTap(taskView);
        return true;
    }

    private static void logTaskLaunchTap(View taskView) {
        if (taskView == null) {
            return;
        }
        ClassLoader loader = taskView.getClass().getClassLoader();
        if (loader == null) {
            return;
        }
        try {
            Object container = LauncherRecentsCompat.invokeCompat(
                    taskView,
                    "getContainer",
                    LauncherRecentsCompat.NO_ARGS);
            Object statsLogManager = LauncherRecentsCompat.invokeCompat(
                    container,
                    "getStatsLogManager",
                    LauncherRecentsCompat.NO_ARGS);
            Object logger = LauncherRecentsCompat.invokeCompat(
                    statsLogManager,
                    "logger",
                    LauncherRecentsCompat.NO_ARGS);
            if (logger == null) {
                return;
            }
            Object itemInfo = LauncherRecentsCompat.invokeCompat(
                    taskView,
                    "getItemInfo",
                    LauncherRecentsCompat.NO_ARGS);
            Object loggerWithItemInfo = LauncherRecentsCompat.invokeCompat(
                    logger,
                    "withItemInfo",
                    new Class<?>[]{
                            Class.forName(
                                    "com.android.launcher3.model.data.ItemInfo",
                                    false,
                                    loader)
                    },
                    itemInfo);
            if (loggerWithItemInfo == null) {
                loggerWithItemInfo = logger;
            }
            Object event = LauncherRecentsCompat.readStaticFieldCompat(
                    "com.android.launcher3.logging.StatsLogManager$LauncherEvent",
                    "LAUNCHER_TASK_LAUNCH_TAP",
                    loader);
            LauncherRecentsCompat.invokeCompat(
                    loggerWithItemInfo,
                    "log",
                    new Class<?>[]{
                            Class.forName(
                                    "com.android.launcher3.logging.StatsLogManager$EventEnum",
                                    false,
                                    loader)
                    },
                    event);
        } catch (Throwable ignored) {
        }
    }

    static boolean shouldOverrideTaskLaunchStockGeometry(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                || LauncherRecentsCompat.isDesktopTask(taskView)) {
            return false;
        }
        LauncherRecentsState.LaunchHandoffState state =
                LauncherRecentsState.getActiveTaskLaunchHandoff(recentsView);
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
        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, true);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        freezeTaskLaunchLayoutIfNeeded(recentsView, taskView);
        recentsView.invalidate();
    }

    static boolean shouldSuppressRecentsLaunchScrollMutation(Object thisObject) {
        if (LauncherRecentsTouchController.shouldBypassStackDismissScrollSuppression()) {
            return false;
        }
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
                            finishTaskLaunchWithoutSystemAnimation(callbackRecentsView);
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

    private static void finishTaskLaunchWithoutSystemAnimation(View recentsView) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, false);
        if (!LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
            return;
        }
        LauncherRecentsCompat.invokeMethodReflectively(
                recentsView,
                "onTaskLaunchAnimationEnd",
                new Class<?>[]{boolean.class},
                true);
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
        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, false);
        LauncherRecentsState.trackRecentsView(recentsView);
        LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
        LauncherRecentsState.LaunchHandoffState state =
                new LauncherRecentsState.LaunchHandoffState(
                        taskView,
                        resolveTaskViewIndex(recentsView, taskView),
                        shouldPromoteRearTaskDuringLaunch(recentsView, taskView),
                        true);
        LauncherRecentsState.setActiveTaskLaunchHandoff(recentsView, state);
        captureLaunchHandoffTaskStates(recentsView, state);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(state.promoteRearCard
                ? TASK_LAUNCH_HANDOFF_DURATION_MS
                : TASK_LAUNCH_FRONT_HANDOFF_DURATION_MS);
        animator.setInterpolator(TASK_LAUNCH_HANDOFF_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            state.progress = value instanceof Float ? (Float) value : 0f;
            LauncherRecentsPerf.hit("animationFrame:launchHandoff", recentsView);
            applyLaunchHandoffLayout(recentsView, state);
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
                if (LauncherRecentsState.getActiveTaskLaunchHandoff(recentsView) != state) {
                    return;
                }
                if (cancelled) {
                    clearTaskLaunchHandoff(recentsView, true);
                    return;
                }
                completeTaskLaunchHandoff(recentsView, state);
                LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, true);
                if (!launchTaskWithoutSystemAnimation(taskView, recentsView)) {
                    clearTaskLaunchHandoff(recentsView, true);
                }
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
        if (state.taskStates.isEmpty()) {
            captureLaunchHandoffTaskStates(recentsView, state);
        }
        applyLaunchHandoffLayout(recentsView, state);
        state.frozen = true;
        recentsView.invalidate();
    }

    private static void freezeTaskLaunchLayoutIfNeeded(View recentsView, View taskView) {
        if (recentsView == null || taskView == null) {
            return;
        }
        LauncherRecentsState.LaunchHandoffState state =
                LauncherRecentsState.getActiveTaskLaunchHandoff(recentsView);
        if (state == null) {
            state = new LauncherRecentsState.LaunchHandoffState(
                    taskView,
                    resolveTaskViewIndex(recentsView, taskView),
                    false,
                    false);
            LauncherRecentsState.setActiveTaskLaunchHandoff(recentsView, state);
        }
        if (state.frozen) {
            return;
        }
        state.progress = 1f;
        if (state.handoffEnabled) {
            if (state.taskStates.isEmpty()) {
                captureLaunchHandoffTaskStates(recentsView, state);
            }
            applyLaunchHandoffLayout(recentsView, state);
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

    static void clearTaskLaunchFrozenForNewGesture(View recentsView) {
        if (LauncherRecentsState.isTaskLaunchLayoutFrozen(recentsView)) {
            clearTaskLaunchHandoff(recentsView, false);
        }
    }

    static void clearTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        if (recentsView == null) {
            return;
        }
        LauncherRecentsState.setTaskLaunchRequestStarted(recentsView, false);
        LauncherRecentsState.clearActiveTaskLaunchHandoff(recentsView);
        cancelTaskLaunchHandoff(recentsView, false);
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

    static void cancelTaskLaunchHandoff(View recentsView, boolean restoreStack) {
        ValueAnimator animator =
                LauncherRecentsState.ACTIVE_TASK_LAUNCH_HANDOFF_ANIMATORS.remove(recentsView);
        if (animator != null) {
            animator.cancel();
        }
        if (restoreStack) {
            LauncherRecentsState.clearActiveTaskLaunchHandoff(recentsView);
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

    private static void captureLaunchHandoffTaskStates(
            View recentsView,
            LauncherRecentsState.LaunchHandoffState state) {
        if (recentsView == null || state == null || state.targetTaskView == null) {
            return;
        }
        state.taskStates.clear();
        int taskViewCount = LauncherRecentsCompat.invokeInt(recentsView, "getTaskViewCount", 0);
        for (int i = 0; i < taskViewCount; i++) {
            View taskView = LauncherRecentsCompat.getTaskViewAt(recentsView, i);
            if (taskView == null || LauncherRecentsCompat.isDesktopTask(taskView)) {
                continue;
            }
            boolean target = taskView == state.targetTaskView;
            if (!target && !isTaskVisibleForLaunchHandoff(recentsView, taskView)) {
                continue;
            }
            state.taskStates.add(captureLaunchHandoffTaskState(taskView, target));
        }
    }

    private static LauncherRecentsState.LaunchHandoffTaskState captureLaunchHandoffTaskState(
            View taskView,
            boolean target) {
        return new LauncherRecentsState.LaunchHandoffTaskState(
                taskView,
                target,
                taskView.getX(),
                taskView.getY(),
                taskView.getPivotX(),
                taskView.getPivotY(),
                Math.max(0.0001f, taskView.getScaleX()),
                Math.max(0.0001f, taskView.getScaleY()),
                resolveLaunchTaskCenterX(taskView, taskView.getWidth() * 0.5f),
                resolveLaunchTaskCenterY(taskView, taskView.getHeight() * 0.5f),
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
                LauncherRecentsTaskVisuals.readStableAlpha(taskView),
                taskView.getTranslationZ(),
                LauncherRecentsCompat.readFloatField(taskView, "fullscreenProgress", 0f));
    }

    private static LauncherRecentsState.LaunchHandoffTaskState findLaunchHandoffTaskState(
            LauncherRecentsState.LaunchHandoffState state,
            View taskView) {
        if (state == null || taskView == null) {
            return null;
        }
        for (int i = 0; i < state.taskStates.size(); i++) {
            LauncherRecentsState.LaunchHandoffTaskState taskState = state.taskStates.get(i);
            if (taskState != null && taskState.taskView == taskView) {
                return taskState;
            }
        }
        return null;
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
        if (state.taskStates.isEmpty()) {
            captureLaunchHandoffTaskStates(recentsView, state);
        }
        LauncherRecentsState.LaunchHandoffTaskState targetTaskState =
                findLaunchHandoffTaskState(state, targetTaskView);
        if (targetTaskState == null) {
            return;
        }
        View frontTaskView = resolveFrontMostTaskView(recentsView);
        LauncherRecentsState.LaunchHandoffTaskState frontTaskState =
                findLaunchHandoffTaskState(state, frontTaskView);
        float progress = state.progress;
        float targetFullscreenProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(
                        progress,
                        state.promoteRearCard
                                ? TASK_LAUNCH_REAR_PROMOTE_FRACTION * 0.32f
                                : 0.18f,
                        1f));
        float siblingMoveProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(progress, 0.04f, 0.82f));
        float visibleSiblingFadeProgress = LauncherRecentsLayoutEngine.smoothStep(
                LauncherRecentsLayoutEngine.remapProgress(progress, 0.20f, 0.94f));
        float targetExtraZ = FlymeStatusBarSizer.dp(recentsView.getContext(), 28);
        float anchorZ = frontTaskState != null
                ? frontTaskState.translationZ
                : targetTaskState.translationZ;
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
        float currentTargetCenterX = targetTaskState.x
                + targetTaskState.pivotX
                + ((targetFocusCenterX - targetTaskState.pivotX) * targetTaskState.scaleX);
        float currentTargetCenterY = targetTaskState.y
                + targetTaskState.pivotY
                + ((targetFocusCenterY - targetTaskState.pivotY) * targetTaskState.scaleY);
        float targetPivotCompensationX =
                currentTargetCenterX - (targetTaskState.x + targetFocusCenterX);
        float targetPivotCompensationY =
                currentTargetCenterY - (targetTaskState.y + targetFocusCenterY);
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
        float scaleNormalizer = Math.abs(targetTaskState.nonGridScale) > 0.0001f
                ? Math.max(0.0001f, targetTaskState.scaleX / targetTaskState.nonGridScale)
                : 1f;
        float targetEndScale = Math.max(
                Math.max(1f, targetViewportWidth / targetFocusWidth),
                Math.max(1f, targetViewportHeight / targetFocusHeight))
                / scaleNormalizer
                * TASK_LAUNCH_TARGET_END_SCALE_BLEED;

        targetTaskView.setPivotX(targetFocusCenterX);
        targetTaskView.setPivotY(targetFocusCenterY);
        LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(
                targetTaskView,
                targetTaskState.horizontalOffsetX);
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.taskOffsetX + targetPivotCompensationX,
                        targetTaskState.taskOffsetX + targetPivotCompensationX + targetDeltaX,
                        targetFullscreenProgress));
        LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.taskOffsetY + targetPivotCompensationY,
                        targetTaskState.taskOffsetY + targetPivotCompensationY + targetDeltaY,
                        targetFullscreenProgress));
        LauncherRecentsTaskVisuals.setBoxTranslationY(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.boxTranslationY,
                        LauncherRecentsTaskVisuals.readOriginalBoxTranslationY(targetTaskView),
                        targetFullscreenProgress));
        LauncherRecentsTaskVisuals.setNonGridScale(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.nonGridScale,
                        targetEndScale,
                        targetFullscreenProgress));
        LauncherRecentsTaskVisuals.setStableAlpha(targetTaskView, 1f);
        // Stop just before TaskView's fullscreen terminal state to avoid a last-frame
        // relayout/visibility flip before launchWithoutAnimation switches activities.
        LauncherRecentsTaskVisuals.setFullscreenProgress(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.fullscreenProgress,
                        TASK_LAUNCH_TARGET_END_FULLSCREEN_PROGRESS,
                        targetFullscreenProgress));
        LauncherRecentsTaskVisuals.setTranslationZ(
                targetTaskView,
                LauncherRecentsLayoutEngine.lerp(
                        targetTaskState.translationZ,
                        Math.max(anchorZ, targetTaskState.translationZ) + targetExtraZ,
                        targetFullscreenProgress));

        float targetLaunchCenterX = resolveLaunchTaskCenterX(targetTaskView, targetFocusCenterX);
        float targetLaunchCenterY = resolveLaunchTaskCenterY(targetTaskView, targetFocusCenterY);
        float targetBackZ = Math.max(
                0f,
                targetTaskView.getTranslationZ() - FlymeStatusBarSizer.dp(recentsView.getContext(), 2));

        for (int i = 0; i < state.taskStates.size(); i++) {
            LauncherRecentsState.LaunchHandoffTaskState taskState = state.taskStates.get(i);
            if (taskState == null || taskState.target || taskState.taskView == null) {
                continue;
            }
            View taskView = taskState.taskView;
            LauncherRecentsTaskVisuals.setHorizontalOffsetTranslationX(
                    taskView,
                    taskState.horizontalOffsetX);
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationX(
                    taskView,
                    taskState.taskOffsetX
                            + ((targetLaunchCenterX - taskState.centerX) * siblingMoveProgress));
            LauncherRecentsTaskVisuals.setTaskOffsetTranslationY(
                    taskView,
                    taskState.taskOffsetY
                            + ((targetLaunchCenterY - taskState.centerY) * siblingMoveProgress));
            LauncherRecentsTaskVisuals.setBoxTranslationY(taskView, taskState.boxTranslationY);
            LauncherRecentsTaskVisuals.setNonGridScale(taskView, taskState.nonGridScale);
            LauncherRecentsTaskVisuals.setStableAlpha(
                    taskView,
                    LauncherRecentsLayoutEngine.lerp(
                            taskState.stableAlpha,
                            taskState.stableAlpha * TASK_LAUNCH_SIBLING_END_ALPHA,
                            visibleSiblingFadeProgress));
            LauncherRecentsTaskVisuals.setFullscreenProgress(taskView, taskState.fullscreenProgress);
            LauncherRecentsTaskVisuals.setTranslationZ(
                    taskView,
                    LauncherRecentsLayoutEngine.lerp(
                            taskState.translationZ,
                            targetBackZ,
                            visibleSiblingFadeProgress));
        }
    }

    private static float resolveLaunchTaskCenterX(View taskView, float centerX) {
        float pivotX = taskView.getPivotX();
        return taskView.getX() + pivotX + ((centerX - pivotX) * taskView.getScaleX());
    }

    private static float resolveLaunchTaskCenterY(View taskView, float centerY) {
        float pivotY = taskView.getPivotY();
        return taskView.getY() + pivotY + ((centerY - pivotY) * taskView.getScaleY());
    }

    private static boolean isTaskVisibleForLaunchHandoff(View recentsView, View taskView) {
        if (recentsView == null
                || taskView == null
                || !taskView.isShown()
                || taskView.getAlpha() <= 0.01f
                || LauncherRecentsTaskVisuals.readStableAlpha(taskView) <= 0.01f) {
            return false;
        }
        float scaleX = Math.max(0.0001f, Math.abs(taskView.getScaleX()));
        float scaleY = Math.max(0.0001f, Math.abs(taskView.getScaleY()));
        float left = taskView.getX()
                + taskView.getPivotX()
                - (taskView.getPivotX() * scaleX)
                - recentsView.getScrollX();
        float top = taskView.getY()
                + taskView.getPivotY()
                - (taskView.getPivotY() * scaleY)
                - recentsView.getScrollY();
        float right = left + (taskView.getWidth() * scaleX);
        float bottom = top + (taskView.getHeight() * scaleY);
        return right > 0f
                && left < recentsView.getWidth()
                && bottom > 0f
                && top < recentsView.getHeight();
    }
}
