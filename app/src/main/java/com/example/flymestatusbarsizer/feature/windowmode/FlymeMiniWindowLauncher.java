package com.example.flymestatusbarsizer.feature.windowmode;

import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import java.lang.reflect.Method;

public final class FlymeMiniWindowLauncher {
    private static final String START_WINDOW_MODE_KEY = "start_windowmode";
    private static final int MINI_WINDOW_MODE = 11;

    private static Method makeCustomTaskAnimationMethod;
    private static boolean makeCustomTaskAnimationMethodResolved;
    private static Method windowManagerExtGetInstanceMethod;
    private static boolean windowManagerExtGetInstanceMethodResolved;
    private static Method setStartWindowModeMethod;
    private static boolean setStartWindowModeMethodResolved;
    private static Method isWindowModeMethod;
    private static boolean isWindowModeMethodResolved;
    private static Method getWindowModeBoundMethod;
    private static boolean getWindowModeBoundMethodResolved;

    private FlymeMiniWindowLauncher() {
    }

    public static void startTaskInMiniWindow(
            Context context,
            Object activityTaskManager,
            Method startActivityFromRecents,
            int taskId,
            String packageName) throws ReflectiveOperationException {
        if (activityTaskManager == null || startActivityFromRecents == null) {
            throw new IllegalStateException("Recent task service is unavailable");
        }
        Object windowManagerExt = resolveWindowManagerExtInstance(context);
        Method setWindowMode = resolveSetStartWindowModeMethod();
        if (windowManagerExt == null || setWindowMode == null) {
            throw new IllegalStateException("Flyme mini window mode is unavailable");
        }
        setWindowMode.invoke(windowManagerExt, packageName);

        Bundle options = buildMiniWindowTaskOptions(context);
        Object result = startActivityFromRecents.invoke(activityTaskManager, taskId, options);
        if (result instanceof Number && ((Number) result).intValue() < 0) {
            throw new IllegalStateException("Cannot open task in Flyme mini window");
        }
    }

    public static boolean isTaskInMiniWindow(Context context, int displayId, int taskId)
            throws ReflectiveOperationException {
        Object windowManagerExt = resolveWindowManagerExtInstance(context);
        Method method = resolveIsWindowModeMethod();
        if (windowManagerExt == null || method == null) {
            throw new IllegalStateException("Flyme mini window state is unavailable");
        }
        return Boolean.TRUE.equals(method.invoke(
                windowManagerExt,
                displayId,
                taskId,
                MINI_WINDOW_MODE,
                (IBinder) null));
    }

    public static Rect getWindowModeBound(Context context, int displayId, int taskId, int windowMode)
            throws ReflectiveOperationException {
        Object windowManagerExt = resolveWindowManagerExtInstance(context);
        Method method = resolveGetWindowModeBoundMethod();
        if (windowManagerExt == null || method == null) {
            return null;
        }
        Object result = method.invoke(windowManagerExt, displayId, taskId, windowMode);
        return result instanceof Rect ? new Rect((Rect) result) : null;
    }

    public static Bundle buildTaskAnimationOptions(Context context) {
        try {
            Method method = resolveMakeCustomTaskAnimationMethod();
            if (method == null) {
                return null;
            }
            Object result = method.invoke(null, context, 0, 0, null, null, null);
            return result instanceof ActivityOptions ? ((ActivityOptions) result).toBundle() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bundle buildMiniWindowTaskOptions(Context context) {
        Bundle options = buildTaskAnimationOptions(context);
        if (options == null) {
            options = ActivityOptions.makeBasic().toBundle();
        }
        options.putBoolean(START_WINDOW_MODE_KEY, true);
        return options;
    }

    private static Object resolveWindowManagerExtInstance(Context context)
            throws ReflectiveOperationException {
        Method method = resolveWindowManagerExtGetInstanceMethod();
        if (method == null) {
            throw new ClassNotFoundException("flyme.view.WindowManagerExt");
        }
        return method.invoke(null, context);
    }

    private static Method resolveWindowManagerExtGetInstanceMethod() {
        if (!windowManagerExtGetInstanceMethodResolved) {
            try {
                Class<?> type = Class.forName("flyme.view.WindowManagerExt");
                windowManagerExtGetInstanceMethod = type.getMethod("getInstance", Context.class);
                windowManagerExtGetInstanceMethod.setAccessible(true);
            } catch (Throwable ignored) {
                windowManagerExtGetInstanceMethod = null;
            }
            windowManagerExtGetInstanceMethodResolved = true;
        }
        return windowManagerExtGetInstanceMethod;
    }

    private static Method resolveSetStartWindowModeMethod() {
        if (!setStartWindowModeMethodResolved) {
            try {
                Class<?> type = Class.forName("flyme.view.WindowManagerExt");
                setStartWindowModeMethod = type.getMethod("setStartWindowMode", String.class);
                setStartWindowModeMethod.setAccessible(true);
            } catch (Throwable ignored) {
                setStartWindowModeMethod = null;
            }
            setStartWindowModeMethodResolved = true;
        }
        return setStartWindowModeMethod;
    }

    private static Method resolveIsWindowModeMethod() {
        if (!isWindowModeMethodResolved) {
            try {
                Class<?> type = Class.forName("flyme.view.WindowManagerExt");
                isWindowModeMethod = type.getMethod(
                        "isWindowMode",
                        int.class,
                        int.class,
                        int.class,
                        IBinder.class);
                isWindowModeMethod.setAccessible(true);
            } catch (Throwable ignored) {
                isWindowModeMethod = null;
            }
            isWindowModeMethodResolved = true;
        }
        return isWindowModeMethod;
    }

    private static Method resolveGetWindowModeBoundMethod() {
        if (!getWindowModeBoundMethodResolved) {
            try {
                Class<?> type = Class.forName("flyme.view.WindowManagerExt");
                getWindowModeBoundMethod = type.getMethod(
                        "getWindowModeBound",
                        int.class,
                        int.class,
                        int.class);
                getWindowModeBoundMethod.setAccessible(true);
            } catch (Throwable ignored) {
                getWindowModeBoundMethod = null;
            }
            getWindowModeBoundMethodResolved = true;
        }
        return getWindowModeBoundMethod;
    }

    private static Method resolveMakeCustomTaskAnimationMethod() {
        if (!makeCustomTaskAnimationMethodResolved) {
            try {
                for (Method method : ActivityOptions.class.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if ("makeCustomTaskAnimation".equals(method.getName())
                            && parameters.length == 6
                            && parameters[0] == Context.class
                            && parameters[1] == int.class
                            && parameters[2] == int.class
                            && parameters[3] == Handler.class) {
                        method.setAccessible(true);
                        makeCustomTaskAnimationMethod = method;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                makeCustomTaskAnimationMethod = null;
            }
            makeCustomTaskAnimationMethodResolved = true;
        }
        return makeCustomTaskAnimationMethod;
    }
}
