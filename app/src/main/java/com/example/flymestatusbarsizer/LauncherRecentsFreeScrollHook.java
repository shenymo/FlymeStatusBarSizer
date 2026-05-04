package com.example.flymestatusbarsizer;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

final class LauncherRecentsFreeScrollHook {
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String PAGED_VIEW_CLASS = "com.android.launcher3.PagedView";

    private final XposedModule module;
    private final String tag;

    LauncherRecentsFreeScrollHook(XposedModule module, String tag) {
        this.module = module;
        this.tag = tag;
    }

    void install(ClassLoader loader) {
        try {
            Class<?> recentsViewClass = Class.forName(RECENTS_VIEW_CLASS, false, loader);

            Method onNotSnapping = recentsViewClass.getDeclaredMethod("onNotSnappingToPageInFreeScroll");
            onNotSnapping.setAccessible(true);
            module.hook(onNotSnapping).intercept(chain -> null);

            Method setEnableFreeScrollOneArg = recentsViewClass.getMethod("setEnableFreeScroll", boolean.class);
            setEnableFreeScrollOneArg.setAccessible(true);
            module.hook(setEnableFreeScrollOneArg).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!isLauncherRecentsView(target)) {
                    return chain.proceed();
                }
                boolean enable = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                if (!enable) {
                    ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                    clearRecentsPendingSnap(target);
                    return null;
                }
                Object result = chain.proceed();
                ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                return result;
            });

            Method setEnableFreeScrollTwoArg = recentsViewClass.getMethod("setEnableFreeScroll", boolean.class, boolean.class);
            setEnableFreeScrollTwoArg.setAccessible(true);
            module.hook(setEnableFreeScrollTwoArg).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!isLauncherRecentsView(target)) {
                    return chain.proceed();
                }
                boolean enable = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                if (!enable) {
                    ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                    clearRecentsPendingSnap(target);
                    return null;
                }
                Object result = chain.proceed();
                ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                return result;
            });

            Method requestAbortScrollerAnim = recentsViewClass.getDeclaredMethod("requestAbortScrollerAnim");
            requestAbortScrollerAnim.setAccessible(true);
            module.hook(requestAbortScrollerAnim).intercept(chain -> {
                clearRecentsPendingSnap(chain.getThisObject());
                return null;
            });

            Method dispatchTouchEvent = recentsViewClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            dispatchTouchEvent.setAccessible(true);
            module.hook(dispatchTouchEvent).intercept(chain -> {
                Object target = chain.getThisObject();
                MotionEvent event = chain.getArg(0) instanceof MotionEvent ? (MotionEvent) chain.getArg(0) : null;
                ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                if (event != null) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN
                            || action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        clearRecentsPendingSnap(target);
                    }
                }
                Object result = chain.proceed();
                ReflectUtils.setBooleanField(target, "mFreeScroll", true);
                if (event != null) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        clearRecentsPendingSnap(target);
                    }
                }
                return result;
            });

            Class<?> pagedViewClass = Class.forName(PAGED_VIEW_CLASS, false, loader);
            Method onTouchEvent = pagedViewClass.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            onTouchEvent.setAccessible(true);
            module.hook(onTouchEvent).intercept(chain -> {
                Object target = chain.getThisObject();
                MotionEvent event = chain.getArg(0) instanceof MotionEvent ? (MotionEvent) chain.getArg(0) : null;
                if (event == null || !isLauncherRecentsView(target)
                        || !ReflectUtils.getBooleanField(target, "mFreeScroll", false)) {
                    return chain.proceed();
                }
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    Boolean handled = handleRecentsPagedViewTouchRelease(target, event, action == MotionEvent.ACTION_CANCEL);
                    if (handled != null) {
                        return handled;
                    }
                }
                return chain.proceed();
            });

            Method snapToDestination = pagedViewClass.getDeclaredMethod("snapToDestination");
            snapToDestination.setAccessible(true);
            module.hook(snapToDestination).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!isLauncherRecentsView(target) || !ReflectUtils.getBooleanField(target, "mFreeScroll", false)) {
                    return chain.proceed();
                }
                clearRecentsPendingSnap(target);
                return null;
            });

            Method snapToPageWithVelocity = pagedViewClass.getDeclaredMethod("snapToPageWithVelocity", int.class, int.class);
            snapToPageWithVelocity.setAccessible(true);
            module.hook(snapToPageWithVelocity).intercept(chain -> {
                Object target = chain.getThisObject();
                if (!isLauncherRecentsView(target) || !ReflectUtils.getBooleanField(target, "mFreeScroll", false)) {
                    return chain.proceed();
                }
                if (!(chain.getArg(1) instanceof Integer)) {
                    return chain.proceed();
                }
                if (startRecentsFreeFling(target, (Integer) chain.getArg(1))) {
                    return Boolean.TRUE;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            module.log(android.util.Log.WARN, tag, "Failed to hook launcher recents free scroll", t);
        }
    }

    private static boolean isLauncherRecentsView(Object target) {
        if (target == null) {
            return false;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            if (RECENTS_VIEW_CLASS.equals(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private static void clearRecentsPendingSnap(Object recentsView) {
        if (recentsView == null) {
            return;
        }
        ReflectUtils.setBooleanField(recentsView, "mNeedCheckSnapToDestination", false);
        ReflectUtils.setIntField(recentsView, "mLastHandleActionUpChildIndex", -1);
        Object handlerObject = ReflectUtils.getField(recentsView, "mMainHandlerForAbortScrollAndCheckSnap");
        if (!(handlerObject instanceof Handler)) {
            return;
        }
        Handler handler = (Handler) handlerObject;
        Object timeoutRunnable = ReflectUtils.getField(recentsView, "mTimeoutToCheckSnap");
        if (timeoutRunnable instanceof Runnable) {
            handler.removeCallbacks((Runnable) timeoutRunnable);
        }
        Object abortRunnable = ReflectUtils.getField(recentsView, "mAbortRecentsViewScrollAnimRunner");
        if (abortRunnable instanceof Runnable) {
            handler.removeCallbacks((Runnable) abortRunnable);
        }
    }

    private static Boolean handleRecentsPagedViewTouchRelease(Object pagedView, MotionEvent event, boolean cancelled) {
        if (pagedView == null || event == null) {
            return null;
        }
        if (!ReflectUtils.getBooleanField(pagedView, "mIsBeingDragged", false)) {
            return null;
        }

        clearRecentsPendingSnap(pagedView);
        releasePagedViewEdgeEffects(pagedView, event);

        if (cancelled) {
            ReflectUtils.invokeNoArg(pagedView, "resetTouchState");
            return Boolean.TRUE;
        }

        int activePointerId = ReflectUtils.getIntField(pagedView, "mActivePointerId", -1);
        int pointerIndex = activePointerId == -1 ? -1 : event.findPointerIndex(activePointerId);
        if (pointerIndex < 0) {
            ReflectUtils.invokeNoArg(pagedView, "resetTouchState");
            return Boolean.TRUE;
        }

        int velocity = getPagedViewPrimaryVelocity(pagedView, activePointerId);
        int minFlingVelocity = ReflectUtils.getIntField(pagedView, "mMinFlingVelocity", 0);
        int currentScroll = getPagedViewPrimaryScroll(pagedView);
        int minScroll = ReflectUtils.getIntField(pagedView, "mMinScroll", Integer.MIN_VALUE);
        int maxScroll = ReflectUtils.getIntField(pagedView, "mMaxScroll", Integer.MAX_VALUE);

        if (currentScroll < minScroll || currentScroll > maxScroll) {
            startRecentsSpringBack(pagedView, currentScroll, minScroll, maxScroll);
        } else if (Math.abs(velocity) >= minFlingVelocity) {
            startRecentsFreeFling(pagedView, velocity);
        } else {
            ReflectUtils.setIntField(pagedView, "mNextPage", -1);
            if (pagedView instanceof View) {
                ((View) pagedView).invalidate();
            }
        }

        ReflectUtils.invokeNoArg(pagedView, "resetTouchState");
        return Boolean.TRUE;
    }

    private static void releasePagedViewEdgeEffects(Object pagedView, MotionEvent event) {
        Object left = ReflectUtils.getField(pagedView, "mEdgeGlowLeft");
        Object right = ReflectUtils.getField(pagedView, "mEdgeGlowRight");
        if (left != null) {
            ReflectUtils.invokeMethod(left, "onRelease", new Class[]{MotionEvent.class}, event);
            ReflectUtils.invokeNoArg(left, "onRelease");
        }
        if (right != null) {
            ReflectUtils.invokeMethod(right, "onRelease", new Class[]{MotionEvent.class}, event);
            ReflectUtils.invokeNoArg(right, "onRelease");
        }
    }

    private static int getPagedViewPrimaryVelocity(Object pagedView, int activePointerId) {
        Object velocityTrackerObject = ReflectUtils.getField(pagedView, "mVelocityTracker");
        Object orientationHandler = ReflectUtils.getField(pagedView, "mOrientationHandler");
        if (!(velocityTrackerObject instanceof VelocityTracker) || orientationHandler == null) {
            return 0;
        }
        VelocityTracker velocityTracker = (VelocityTracker) velocityTrackerObject;
        int maximumVelocity = ReflectUtils.getIntField(pagedView, "mMaximumVelocity", 0);
        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
        Object velocity = ReflectUtils.invokeMethod(orientationHandler, "getPrimaryVelocity",
                new Class[]{VelocityTracker.class, int.class}, velocityTracker, activePointerId);
        if (velocity instanceof Float) {
            return Math.round((Float) velocity);
        }
        if (velocity instanceof Integer) {
            return (Integer) velocity;
        }
        return 0;
    }

    private static int getPagedViewPrimaryScroll(Object pagedView) {
        Object orientationHandler = ReflectUtils.getField(pagedView, "mOrientationHandler");
        if (orientationHandler == null) {
            return 0;
        }
        Object value = ReflectUtils.invokeMethod(orientationHandler, "getPrimaryScroll",
                new Class[]{View.class}, pagedView);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static void startRecentsSpringBack(Object pagedView, int currentScroll, int minScroll, int maxScroll) {
        Object scroller = ReflectUtils.getField(pagedView, "mScroller");
        if (scroller == null) {
            return;
        }
        ReflectUtils.invokeMethod(scroller, "springBack",
                new Class[]{int.class, int.class, int.class, int.class, int.class, int.class},
                currentScroll, 0, minScroll, maxScroll, 0, 0);
        ReflectUtils.setIntField(pagedView, "mNextPage", -1);
        if (pagedView instanceof View) {
            ((View) pagedView).invalidate();
        }
    }

    private void logWarn(String message, Throwable t) {
        module.log(android.util.Log.WARN, tag, message, t);
    }

    private static boolean startRecentsFreeFling(Object pagedView, int velocity) {
        if (pagedView == null) {
            return false;
        }
        Object scroller = ReflectUtils.getField(pagedView, "mScroller");
        Object orientationHandler = ReflectUtils.getField(pagedView, "mOrientationHandler");
        if (scroller == null || orientationHandler == null) {
            return false;
        }
        try {
            Object finishedValue = ReflectUtils.invokeNoArg(scroller, "isFinished");
            if (!(finishedValue instanceof Boolean) || !((Boolean) finishedValue)) {
                ReflectUtils.invokeNoArg(scroller, "abortAnimation");
            }

            int start = 0;
            Object value = ReflectUtils.invokeMethod(orientationHandler, "getPrimaryScroll",
                    new Class[]{View.class}, pagedView);
            if (value instanceof Integer) {
                start = (Integer) value;
            }

            int minScroll = ReflectUtils.getIntField(pagedView, "mMinScroll", Integer.MIN_VALUE);
            int maxScroll = ReflectUtils.getIntField(pagedView, "mMaxScroll", Integer.MAX_VALUE);
            if (minScroll > maxScroll) {
                int swap = minScroll;
                minScroll = maxScroll;
                maxScroll = swap;
            }

            int overX = 0;
            if (pagedView instanceof View) {
                overX = Math.round(((View) pagedView).getWidth() * 0.035f);
            }

            ReflectUtils.invokeMethod(scroller, "fling",
                    new Class[]{int.class, int.class, int.class, int.class, int.class, int.class,
                            int.class, int.class, int.class, int.class},
                    start, 0, -velocity, 0, minScroll, maxScroll, 0, 0, overX, 0);

            int finalX = ReflectUtils.invokeNoArgInt(scroller, "getFinalX", start);
            if (finalX < minScroll || finalX > maxScroll) {
                int clamped = Math.max(minScroll, Math.min(maxScroll, finalX));
                ReflectUtils.invokeMethod(scroller, "setFinalX", new Class[]{int.class}, clamped);
            }

            ReflectUtils.setIntField(pagedView, "mNextPage", -1);
            if (pagedView instanceof View) {
                ((View) pagedView).invalidate();
            }
            clearRecentsPendingSnap(pagedView);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
