package com.example.flymestatusbarsizer.feature.windowmode;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailPopupBridge;
import com.example.flymestatusbarsizer.feature.mback.MBackHooks;
import com.example.flymestatusbarsizer.feature.mback.MBackStarOverlayBridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class WindowModeSideGestureHooks {
    private static final int ACTION_INTENT_URI = 0;
    private static final int ACTION_CLOCK_POPUP = 1;
    private static final int ACTION_STAR_APPS = 2;
    private static final Map<Object, Integer> ACTIVE_ACTIONS = new WeakHashMap<>();
    private static final Map<Object, Boolean> PREWARMED_LAUNCHERS = new WeakHashMap<>();
    private static Class<?> appLauncherWindowClass;

    private WindowModeSideGestureHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemuitools.windowmode.views.SlideGestureForwarding",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "onGestureTriggered",
                    int.class,
                    MotionEvent.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object target = chain.getThisObject();
                MotionEvent event = chain.getArg(1) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(1)
                        : null;
                if (consumeActiveGesture(target, event) || handleGesture(target, event)) {
                    return null;
                }
                return chain.proceed();
            });
            installAppLauncherPrewarmHook(module, loader);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme window mode side gesture",
                    t);
        }
    }

    private static void installAppLauncherPrewarmHook(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemuitools.windowmode.views.AppLauncherWindow",
                    false,
                    loader);
            appLauncherWindowClass = clazz;
            Method method = clazz.getDeclaredMethod("X");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                prewarmAppLauncherWindow(chain.getThisObject());
                return result;
            });
            Method destroyMethod = findNoArgVoidMethod(clazz, "D");
            if (destroyMethod != null) {
                destroyMethod.setAccessible(true);
                module.intercept(destroyMethod, chain -> {
                    try {
                        return chain.proceed();
                    } finally {
                        synchronized (PREWARMED_LAUNCHERS) {
                            PREWARMED_LAUNCHERS.remove(chain.getThisObject());
                        }
                    }
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme window mode app launcher prewarm",
                    t);
        }
    }

    private static void prewarmAppLauncherWindow(Object target) {
        if (target == null) {
            return;
        }
        synchronized (PREWARMED_LAUNCHERS) {
            if (PREWARMED_LAUNCHERS.containsKey(target)) {
                return;
            }
        }
        Context context = resolveAppLauncherContext(target);
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        if (!config.enabled || !config.sideGesturePrewarmEnabled || config.sideGestureEnabled) {
            return;
        }
        synchronized (PREWARMED_LAUNCHERS) {
            PREWARMED_LAUNCHERS.put(target, Boolean.TRUE);
        }
        Runnable task = () -> invokePrewarmPrepare(target);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            new Handler(Looper.getMainLooper()).post(task);
        }
    }

    private static void invokePrewarmPrepare(Object target) {
        try {
            Method method = findPrewarmPrepareMethod(target.getClass());
            if (method != null) {
                method.setAccessible(true);
                method.invoke(target);
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to prewarm Flyme window mode app launcher",
                    t);
        }
    }

    private static Method findPrewarmPrepareMethod(Class<?> clazz) {
        Method method = findNoArgVoidMethod(clazz, "R");
        if (method != null) {
            return method;
        }
        return findNoArgVoidMethod(clazz, "Q");
    }

    private static Method findNoArgVoidMethod(Class<?> clazz, String name) {
        try {
            Method method = clazz.getDeclaredMethod(name);
            return method.getReturnType() == Void.TYPE ? method : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean handleGesture(Object target, MotionEvent event) {
        Context context = resolveContext(target);
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        if (!config.enabled || !config.sideGestureEnabled) {
            return false;
        }
        boolean handled = false;
        if (config.sideGestureAction == ACTION_CLOCK_POPUP) {
            View anchor = resolveAnchor(target);
            if (anchor != null) {
                anchor.post(() -> ClockDetailPopupBridge.showFromMBack(anchor));
                handled = true;
            }
        } else if (config.sideGestureAction == ACTION_STAR_APPS) {
            View anchor = resolveAnchor(target);
            if (anchor != null) {
                postStarEvent(anchor, event, true);
                handled = true;
            }
        } else if (config.sideGestureAction == ACTION_INTENT_URI) {
            handled = MBackHooks.launchConfiguredIntent(context, config.sideGestureIntentUri);
        }
        if (handled && target != null) {
            synchronized (ACTIVE_ACTIONS) {
                ACTIVE_ACTIONS.put(target, config.sideGestureAction);
            }
        }
        return handled;
    }

    private static boolean consumeActiveGesture(Object target, MotionEvent event) {
        Integer action;
        synchronized (ACTIVE_ACTIONS) {
            if (target == null || !ACTIVE_ACTIONS.containsKey(target)) {
                return false;
            }
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ACTIVE_ACTIONS.remove(target);
                return false;
            }
            action = ACTIVE_ACTIONS.get(target);
        }
        if (action != null && action == ACTION_STAR_APPS) {
            View anchor = resolveAnchor(target);
            if (anchor != null) {
                postStarEvent(anchor, event, false);
            }
        }
        if (isTerminalEvent(event)) {
            synchronized (ACTIVE_ACTIONS) {
                ACTIVE_ACTIONS.remove(target);
            }
        }
        return true;
    }

    private static boolean isTerminalEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }

    private static void postStarEvent(View anchor, MotionEvent event, boolean show) {
        MotionEvent copy = event == null ? null : MotionEvent.obtain(event);
        anchor.post(() -> {
            try {
                if (show) {
                    MBackStarOverlayBridge.show(anchor);
                }
                MBackStarOverlayBridge.dispatchMBackMotionEvent(copy);
            } finally {
                if (copy != null) {
                    copy.recycle();
                }
            }
        });
    }

    private static Context resolveContext(Object target) {
        Object value = readField(target, "mContext");
        if (value instanceof Context) {
            return (Context) value;
        }
        View anchor = resolveAnchor(target);
        return anchor == null ? null : anchor.getContext();
    }

    private static Context resolveAppLauncherContext(Object target) {
        Object value = readField(target, "f10356c");
        return value instanceof Context ? (Context) value : null;
    }

    private static View resolveAnchor(Object target) {
        Object value = readField(target, "mGestureAppLauncher");
        return value instanceof View ? (View) value : null;
    }

    private static Object readField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
