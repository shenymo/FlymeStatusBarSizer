package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.lang.reflect.Method;

final class LauncherRecentsTouchController {
    private LauncherRecentsTouchController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookPagedViewOnTouchEvent(module, loader);
        hookRecentsViewNotifyHandleActionUp(module, loader);
        hookRecentsViewFreeScrollSettling(module, loader);
        hookPagedViewSnapToDestination(module, loader);
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
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (LauncherRecentsLayoutEngine.shouldApplyDynamicStackLayout(recentsView)) {
                        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
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
                    LauncherRecentsState.trackRecentsView(recentsView);
                    LauncherRecentsLayoutEngine.prepareRecentsView(recentsView);
                    if (LauncherRecentsLayoutEngine.shouldApplyDynamicStackLayout(recentsView)) {
                        LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
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
        if (LauncherRecentsLayoutEngine.shouldApplyDynamicStackLayout(recentsView)) {
            LauncherRecentsTaskVisuals.captureStockTaskStates(recentsView);
            LauncherRecentsLayoutEngine.applyStackLayout(recentsView, false);
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
