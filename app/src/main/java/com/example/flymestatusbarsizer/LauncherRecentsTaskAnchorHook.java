package com.example.flymestatusbarsizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

final class LauncherRecentsTaskAnchorHook {
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String RECENTS_VIEW_STATE_CONTROLLER_CLASS =
            "com.android.launcher3.uioverrides.RecentsViewStateController";
    private static final String QUICKSTEP_LAUNCHER_CLASS =
            "com.android.launcher3.uioverrides.QuickstepLauncher";
    private static final String LAUNCHER_STATE_CLASS = "com.android.launcher3.LauncherState";

    private static final float DEFAULT_RIGHT_SHIFT_RATIO = 0.16f;
    private static final float MAX_RIGHT_SHIFT_RATIO = 0.28f;
    private static final long RIGHT_SHIFT_ANIMATION_DURATION_MS = 220L;

    private static final WeakHashMap<Object, ValueAnimator> RUNNING_ANIMATORS = new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> SUPPRESS_AUTO_SHIFT = new WeakHashMap<>();

    private final XposedModule module;
    private final String tag;

    LauncherRecentsTaskAnchorHook(XposedModule module, String tag) {
        this.module = module;
        this.tag = tag;
    }

    void install(ClassLoader loader) {
        try {
            Class<?> recentsViewClass = Class.forName(RECENTS_VIEW_CLASS, false, loader);

            Method onPrepareGestureEndAnimation = recentsViewClass.getDeclaredMethod(
                    "onPrepareGestureEndAnimation",
                    Class.forName("android.animation.AnimatorSet", false, loader),
                    Class.forName("com.android.quickstep.GestureState$GestureEndTarget", false, loader),
                    Class.forName("[Lcom.android.quickstep.RemoteTargetGluer$RemoteTargetHandle;", false, loader));
            onPrepareGestureEndAnimation.setAccessible(true);
            module.hook(onPrepareGestureEndAnimation).intercept(chain -> {
                Object recentsViewObject = chain.getThisObject();
                Object animatorSet = chain.getArg(0);
                Object gestureEndTarget = chain.getArg(1);
                Object result = chain.proceed();
                try {
                    handlePrepareGestureEndAnimation(recentsViewObject, animatorSet, gestureEndTarget);
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, tag,
                            "Failed to prepare launcher recents task translation animation", t);
                }
                return result;
            });

            Method dispatchTouchEvent = recentsViewClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            dispatchTouchEvent.setAccessible(true);
            module.hook(dispatchTouchEvent).intercept(chain -> {
                Object recentsViewObject = chain.getThisObject();
                MotionEvent event = chain.getArg(0) instanceof MotionEvent ? (MotionEvent) chain.getArg(0) : null;
                if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    markUserInteractionStarted(recentsViewObject);
                }
                return chain.proceed();
            });

            Method setOverviewStateEnabled = recentsViewClass.getDeclaredMethod(
                    "setOverviewStateEnabled", boolean.class);
            setOverviewStateEnabled.setAccessible(true);
            module.hook(setOverviewStateEnabled).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object enabledArg = chain.getArg(0);
                    if (!(enabledArg instanceof Boolean) || !((Boolean) enabledArg)) {
                        clearShiftState(chain.getThisObject(), true);
                    }
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, tag,
                            "Failed to clear launcher recents task translation state", t);
                }
                return result;
            });

            Method reset = recentsViewClass.getDeclaredMethod("reset");
            reset.setAccessible(true);
            module.hook(reset).intercept(chain -> {
                Object recentsViewObject = chain.getThisObject();
                Object result = chain.proceed();
                try {
                    clearShiftState(recentsViewObject, false);
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, tag,
                            "Failed to reset launcher recents task translation state", t);
                }
                return result;
            });
        } catch (Throwable t) {
            module.log(android.util.Log.WARN, tag,
                    "Failed to hook launcher recents task translation", t);
        }

        try {
            Class<?> recentsViewStateControllerClass =
                    Class.forName(RECENTS_VIEW_STATE_CONTROLLER_CLASS, false, loader);
            Class<?> quickstepLauncherClass = Class.forName(QUICKSTEP_LAUNCHER_CLASS, false, loader);
            Class<?> launcherStateClass = Class.forName(LAUNCHER_STATE_CLASS, false, loader);
            Object overviewState = ReflectUtils.getStaticField(loader, LAUNCHER_STATE_CLASS, "OVERVIEW");

            Method setStateWithAnimation = recentsViewStateControllerClass.getDeclaredMethod(
                    "setStateWithAnimation",
                    launcherStateClass,
                    Class.forName("com.android.launcher3.states.StateAnimationConfig", false, loader),
                    Class.forName("com.android.launcher3.anim.PendingAnimation", false, loader));
            setStateWithAnimation.setAccessible(true);
            module.hook(setStateWithAnimation).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    handleOverviewStateAnimation(chain.getThisObject(), chain.getArg(0), overviewState, chain.getArg(2));
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, tag,
                            "Failed to hook launcher overview state animation", t);
                }
                return result;
            });

            Method onStateSetEnd = quickstepLauncherClass.getDeclaredMethod("onStateSetEnd", launcherStateClass);
            onStateSetEnd.setAccessible(true);
            module.hook(onStateSetEnd).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    handleLauncherOverviewStateSetEnd(chain.getThisObject(), chain.getArg(0), overviewState);
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, tag,
                            "Failed to hook launcher overview state end", t);
                }
                return result;
            });
        } catch (Throwable t) {
            module.log(android.util.Log.WARN, tag,
                    "Failed to hook launcher overview state end", t);
        }
    }

    private static void handleOverviewStateAnimation(
            Object controllerObject, Object toState, Object overviewState, Object pendingAnimationObject) {
        if (controllerObject == null || pendingAnimationObject == null) {
            return;
        }
        if (toState == null || overviewState == null || toState != overviewState) {
            return;
        }

        Object recentsViewObject = ReflectUtils.getField(controllerObject, "recentsView");
        if (!isEligibleRecentsView(recentsViewObject)) {
            return;
        }
        if (Boolean.TRUE.equals(ReflectUtils.invokeNoArg(recentsViewObject, "isGestureActive"))) {
            return;
        }

        Integer targetDiff = computeTargetPageScrollDiff(recentsViewObject);
        if (targetDiff == null) {
            return;
        }

        SUPPRESS_AUTO_SHIFT.put(recentsViewObject, Boolean.FALSE);
        attachShiftAnimatorToPendingAnimation(pendingAnimationObject, recentsViewObject, targetDiff);
    }

    private static void handlePrepareGestureEndAnimation(
            Object recentsViewObject, Object animatorSetObject, Object gestureEndTarget) {
        if (recentsViewObject == null) {
            return;
        }
        if (!isRecentsEndTarget(gestureEndTarget) || !isEligibleRecentsView(recentsViewObject)) {
            clearShiftState(recentsViewObject, true);
            return;
        }

        Integer targetDiff = computeTargetPageScrollDiff(recentsViewObject);
        if (targetDiff == null) {
            clearShiftState(recentsViewObject, true);
            return;
        }

        SUPPRESS_AUTO_SHIFT.put(recentsViewObject, Boolean.FALSE);
        if (animatorSetObject instanceof AnimatorSet) {
            attachShiftAnimatorToGestureEnd((AnimatorSet) animatorSetObject, recentsViewObject, targetDiff);
        } else {
            startStandaloneShiftAnimator(recentsViewObject, targetDiff, RIGHT_SHIFT_ANIMATION_DURATION_MS);
        }
    }

    private static void handleLauncherOverviewStateSetEnd(
            Object launcherObject, Object stateObject, Object overviewState) {
        if (launcherObject == null || stateObject == null || overviewState == null || stateObject != overviewState) {
            return;
        }
        Object recentsViewObject = ReflectUtils.invokeNoArg(launcherObject, "getOverviewPanel");
        if (!isEligibleRecentsView(recentsViewObject)) {
            return;
        }
        if (Boolean.TRUE.equals(ReflectUtils.invokeNoArg(recentsViewObject, "isGestureActive"))) {
            return;
        }
        scheduleApplyTargetShift(recentsViewObject);
    }

    private static boolean isEligibleRecentsView(Object recentsViewObject) {
        if (recentsViewObject == null) {
            return false;
        }

        Object orientationHandler = ReflectUtils.invokeNoArg(recentsViewObject, "getPagedOrientationHandler");
        Object rotationValue = ReflectUtils.invokeNoArg(orientationHandler, "getRotation");
        if (!(rotationValue instanceof Integer) || ((Integer) rotationValue) != 0) {
            return false;
        }

        Object deviceProfile = getDeviceProfile(recentsViewObject);
        if (deviceProfile == null) {
            return false;
        }

        boolean isTablet = ReflectUtils.getBooleanField(deviceProfile, "isTablet", false);
        boolean isLandscape = ReflectUtils.getBooleanField(deviceProfile, "isLandscape", false);
        return !isTablet && !isLandscape;
    }

    private static Integer computeTargetPageScrollDiff(Object recentsViewObject) {
        Object deviceProfile = getDeviceProfile(recentsViewObject);
        if (deviceProfile == null) {
            return null;
        }

        int measuredWidth = 0;
        Rect lastTaskSize = getRectField(recentsViewObject, "mLastComputedTaskSize");
        if (lastTaskSize != null) {
            measuredWidth = lastTaskSize.width();
        }
        if (measuredWidth <= 0) {
            return null;
        }

        int availableWidth = ReflectUtils.getIntField(deviceProfile, "widthPx", 0);
        if (availableWidth <= 0) {
            return null;
        }

        int desiredShift = Math.round(measuredWidth * DEFAULT_RIGHT_SHIFT_RATIO);
        int hardLimit = Math.round(measuredWidth * MAX_RIGHT_SHIFT_RATIO);
        int shift = Math.min(Math.max(0, availableWidth / 3), Math.min(desiredShift, hardLimit));
        if (shift <= 0) {
            return null;
        }
        return -shift;
    }

    private static Object getDeviceProfile(Object recentsViewObject) {
        Object containerContext = ReflectUtils.getField(recentsViewObject, "mContainer");
        if (!(containerContext instanceof Context)) {
            return null;
        }
        Context context = (Context) containerContext;

        Object contextObject = ReflectUtils.invokeNoArg(recentsViewObject, "getContext");
        Context contextForConfig = contextObject instanceof Context ? (Context) contextObject : context;

        Object deviceProfile = ReflectUtils.invokeMethod(contextForConfig, "getDeviceProfile", new Class[0]);
        if (deviceProfile == null) {
            deviceProfile = ReflectUtils.invokeMethod(containerContext, "getDeviceProfile", new Class[0]);
        }
        return deviceProfile;
    }

    private static void attachShiftAnimatorToGestureEnd(
            AnimatorSet animatorSet, Object recentsViewObject, int targetDiff) {
        cancelAnimator(recentsViewObject);

        int startDiff = ReflectUtils.getIntField(recentsViewObject, "mCurrentPageScrollDiff", 0);
        ValueAnimator animator = buildShiftAnimator(recentsViewObject, startDiff, targetDiff);
        long duration = animatorSet.getTotalDuration();
        if (duration <= 0L) {
            duration = RIGHT_SHIFT_ANIMATION_DURATION_MS;
        }
        animator.setDuration(duration);
        RUNNING_ANIMATORS.put(recentsViewObject, animator);
        animatorSet.play(animator);
    }

    private static void attachShiftAnimatorToPendingAnimation(
            Object pendingAnimationObject, Object recentsViewObject, int targetDiff) {
        cancelAnimator(recentsViewObject);

        int startDiff = ReflectUtils.getIntField(recentsViewObject, "mCurrentPageScrollDiff", 0);
        ValueAnimator animator = buildShiftAnimator(recentsViewObject, startDiff, targetDiff);
        RUNNING_ANIMATORS.put(recentsViewObject, animator);
        ReflectUtils.invokeMethod(pendingAnimationObject, "add", new Class[]{Animator.class}, animator);
    }

    private static void startStandaloneShiftAnimator(Object recentsViewObject, int targetDiff, long durationMs) {
        if (!(recentsViewObject instanceof View)) {
            applyScrollDiff(recentsViewObject, targetDiff);
            return;
        }

        cancelAnimator(recentsViewObject);

        int startDiff = ReflectUtils.getIntField(recentsViewObject, "mCurrentPageScrollDiff", 0);
        applyScrollDiff(recentsViewObject, startDiff);

        ValueAnimator animator = buildShiftAnimator(recentsViewObject, startDiff, targetDiff);
        animator.setDuration(durationMs);
        RUNNING_ANIMATORS.put(recentsViewObject, animator);
        ((View) recentsViewObject).post(animator::start);
    }

    private static ValueAnimator buildShiftAnimator(
            Object recentsViewObject, int startDiff, int targetDiff) {
        ValueAnimator animator = ValueAnimator.ofInt(startDiff, targetDiff);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation ->
                applyScrollDiff(recentsViewObject, (Integer) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                ValueAnimator runningAnimator = RUNNING_ANIMATORS.get(recentsViewObject);
                if (runningAnimator == animation) {
                    RUNNING_ANIMATORS.remove(recentsViewObject);
                }
                if (cancelled) {
                    return;
                }
                applyScrollDiff(recentsViewObject, targetDiff);
            }
        });
        return animator;
    }

    private static void clearShiftState(Object recentsViewObject, boolean resetScrollDiff) {
        cancelAnimator(recentsViewObject);
        SUPPRESS_AUTO_SHIFT.remove(recentsViewObject);
        if (resetScrollDiff) {
            applyScrollDiff(recentsViewObject, 0);
        }
    }

    private static void cancelAnimator(Object recentsViewObject) {
        ValueAnimator animator = RUNNING_ANIMATORS.remove(recentsViewObject);
        if (animator != null) {
            animator.cancel();
        }
    }

    private static void markUserInteractionStarted(Object recentsViewObject) {
        if (recentsViewObject == null) {
            return;
        }
        cancelAnimator(recentsViewObject);
        SUPPRESS_AUTO_SHIFT.put(recentsViewObject, Boolean.TRUE);
    }

    private static void scheduleApplyTargetShift(Object recentsViewObject) {
        if (recentsViewObject == null) {
            return;
        }
        SUPPRESS_AUTO_SHIFT.put(recentsViewObject, Boolean.FALSE);
        Runnable applyRunnable = () -> {
            if (!isEligibleRecentsView(recentsViewObject)) {
                return;
            }
            boolean overviewEnabled = ReflectUtils.getBooleanField(recentsViewObject, "mOverviewStateEnabled", false);
            if (!overviewEnabled) {
                return;
            }
            if (Boolean.TRUE.equals(SUPPRESS_AUTO_SHIFT.get(recentsViewObject))) {
                return;
            }
            Integer targetDiff = computeTargetPageScrollDiff(recentsViewObject);
            if (targetDiff == null) {
                return;
            }
            applyScrollDiff(recentsViewObject, targetDiff);
        };
        ReflectUtils.invokeMethod(
                recentsViewObject,
                "runOnPageScrollsInitialized",
                new Class[]{Runnable.class},
                applyRunnable);
        if (recentsViewObject instanceof View) {
            ((View) recentsViewObject).post(applyRunnable);
        } else {
            applyRunnable.run();
        }
    }

    private static void applyScrollDiff(Object recentsViewObject, int pageScrollDiff) {
        if (recentsViewObject == null) {
            return;
        }
        int currentDiff = ReflectUtils.getIntField(recentsViewObject, "mCurrentPageScrollDiff", 0);
        if (currentDiff == pageScrollDiff) {
            return;
        }
        ReflectUtils.setIntField(recentsViewObject, "mCurrentPageScrollDiff", pageScrollDiff);
        ReflectUtils.invokeNoArg(recentsViewObject, "updateCurrentPageScroll");
    }

    private static boolean isRecentsEndTarget(Object gestureEndTarget) {
        return gestureEndTarget != null && "RECENTS".equals(String.valueOf(gestureEndTarget));
    }

    private static Rect getRectField(Object target, String name) {
        Object value = ReflectUtils.getField(target, name);
        return value instanceof Rect ? (Rect) value : null;
    }
}
