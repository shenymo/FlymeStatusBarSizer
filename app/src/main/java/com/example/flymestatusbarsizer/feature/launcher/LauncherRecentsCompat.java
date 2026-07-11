package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

final class LauncherRecentsCompat {
    static final Class<?>[] NO_ARGS = new Class[0];
    static final Class<?>[] INT_ARG = new Class[]{int.class};
    static final Class<?>[] FLOAT_ARG = new Class[]{float.class};
    static final Class<?>[] BOOLEAN_ARG = new Class[]{boolean.class};
    static final Class<?>[] BOOLEAN_INT_ARG = new Class[]{boolean.class, int.class};

    static final String LAUNCHER_RECENTS_VIEW_CLASS =
            "com.android.quickstep.views.LauncherRecentsView";
    static final String PAGED_VIEW_CLASS = "com.android.launcher3.PagedView";
    static final String PAGED_ORIENTATION_HANDLER_CLASS =
            "com.android.launcher3.touch.PagedOrientationHandler";
    static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    static final String TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView";
    static final String TASK_VIEW_SIMULATOR_CLASS =
            "com.android.quickstep.util.TaskViewSimulator";
    static final String TASK_VIEW_UTILS_CLASS = "com.android.quickstep.TaskViewUtils";

    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, CachedMethod[]>>
            METHOD_CACHE = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private static final ConcurrentHashMap<Method, XposedInterface.Invoker>
            METHOD_INVOKER_CACHE = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private static final ConcurrentHashMap<Constructor<?>, XposedInterface.CtorInvoker>
            CONSTRUCTOR_INVOKER_CACHE = new ConcurrentHashMap<>();

    private LauncherRecentsCompat() {
    }

    static Object invokeCompat(Object target, String methodName) {
        return invokeCompat(target, methodName, NO_ARGS);
    }

    static Object invokeCompat(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        return invokeMethod(target, methodName, parameterTypes, args);
    }

    static Object getFieldCompat(Object target, String name) {
        return FlymeStatusBarSizer.getFieldCompat(target, name);
    }

    static Object readStaticFieldCompat(String className, String fieldName, ClassLoader loader) {
        if (className == null || fieldName == null || loader == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static View getTaskViewAt(View recentsView, int index) {
        Object value = invokeCompat(recentsView, "getTaskViewAt", INT_ARG, index);
        return value instanceof View ? (View) value : null;
    }

    static boolean isDesktopTask(View taskView) {
        return taskView != null
                && taskView.getClass().getName().contains("DesktopTaskView");
    }

    static boolean isRecentsViewObject(Object value) {
        if (!(value instanceof View)) {
            return false;
        }
        String className = value.getClass().getName();
        return RECENTS_VIEW_CLASS.equals(className)
                || LAUNCHER_RECENTS_VIEW_CLASS.equals(className)
                || className.endsWith(".RecentsView")
                || className.endsWith("LauncherRecentsView");
    }

    static View resolveOwningRecentsView(View taskView) {
        Object value = invokeCompat(taskView, "getRecentsView", NO_ARGS);
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

    static int invokeInt(Object target, String methodName, int fallback) {
        return invokeInt(target, methodName, NO_ARGS, fallback);
    }

    static int invokeInt(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            int fallback,
            Object... args) {
        Object value = invokeCompat(target, methodName, parameterTypes, args);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static boolean invokeBoolean(Object target, String methodName, boolean fallback) {
        Object value = invokeCompat(target, methodName, NO_ARGS);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    static int readIntField(Object target, String name, int fallback) {
        Object value = getFieldCompat(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static float readFloatField(Object target, String name, float fallback) {
        Object value = getFieldCompat(target, name);
        return value instanceof Float ? (Float) value : fallback;
    }

    static boolean readBooleanField(Object target, String name, boolean fallback) {
        Object value = getFieldCompat(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    static void setBooleanField(Object target, String name, boolean value) {
        writeField(target, name, value);
    }

    static void setIntField(Object target, String name, int value) {
        writeField(target, name, value);
    }

    static void writeField(Object target, String name, Object value) {
        if (target == null || name == null) {
            return;
        }
        FlymeStatusBarSizer.setFieldCompat(target, name, value);
    }

    static boolean invokeMethodReflectively(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null) {
            return false;
        }
        Method method = findCachedMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return false;
        }
        return invokeCachedMethod(method, target, args, false) != null
                || method.getReturnType() == Void.TYPE;
    }

    static Object createPendingAnimationInstance(
            Constructor<?> constructor,
            long durationMs) {
        if (constructor == null) {
            return null;
        }
        try {
            XposedInterface.CtorInvoker invoker = getCachedConstructorInvoker(constructor);
            if (invoker != null) {
                return invoker.newInstance(durationMs);
            }
            return constructor.newInstance(durationMs);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object resolveKotlinUnitInstance(ClassLoader loader) {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit", false, loader);
            Field field = unitClass.getField("INSTANCE");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeMethod(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) {
        if (target == null || methodName == null) {
            return null;
        }
        Method method = findCachedMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return null;
        }
        return invokeCachedMethod(method, target, args, true);
    }

    private static Object invokeCachedMethod(
            Method method,
            Object target,
            Object[] args,
            boolean returnResult) {
        try {
            XposedInterface.Invoker invoker = getCachedMethodInvoker(method);
            if (invoker != null) {
                Object result = invoker.invoke(target, args);
                return returnResult ? result : Boolean.TRUE;
            }
            Object result = method.invoke(target, args);
            return returnResult ? result : Boolean.TRUE;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findCachedMethod(
            Class<?> targetClass,
            String methodName,
            Class<?>[] parameterTypes) {
        if (targetClass == null || methodName == null) {
            return null;
        }
        Class<?>[] resolvedParameterTypes = parameterTypes == null ? NO_ARGS : parameterTypes;
        CachedMethod cached = findCachedMethodEntry(
                targetClass,
                methodName,
                resolvedParameterTypes);
        if (cached != null) {
            return cached.method;
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, resolvedParameterTypes);
                method.setAccessible(true);
                cacheMethod(targetClass, methodName, resolvedParameterTypes, method);
                return method;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        cacheMethod(targetClass, methodName, resolvedParameterTypes, null);
        return null;
    }

    private static CachedMethod findCachedMethodEntry(
            Class<?> targetClass,
            String methodName,
            Class<?>[] parameterTypes) {
        ConcurrentHashMap<String, CachedMethod[]> methods = METHOD_CACHE.get(targetClass);
        CachedMethod[] entries = methods != null ? methods.get(methodName) : null;
        if (entries == null) {
            return null;
        }
        for (CachedMethod entry : entries) {
            if (sameParameterTypes(entry.parameterTypes, parameterTypes)) {
                return entry;
            }
        }
        return null;
    }

    private static void cacheMethod(
            Class<?> targetClass,
            String methodName,
            Class<?>[] parameterTypes,
            Method method) {
        synchronized (METHOD_CACHE) {
            if (findCachedMethodEntry(targetClass, methodName, parameterTypes) != null) {
                return;
            }
            ConcurrentHashMap<String, CachedMethod[]> methods = METHOD_CACHE.get(targetClass);
            if (methods == null) {
                methods = new ConcurrentHashMap<>();
                METHOD_CACHE.put(targetClass, methods);
            }
            CachedMethod[] entries = methods.get(methodName);
            int count = entries != null ? entries.length : 0;
            CachedMethod[] updated = new CachedMethod[count + 1];
            if (count > 0) {
                System.arraycopy(entries, 0, updated, 0, count);
            }
            updated[count] = new CachedMethod(parameterTypes.clone(), method);
            methods.put(methodName, updated);
        }
    }

    private static boolean sameParameterTypes(Class<?>[] first, Class<?>[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("rawtypes")
    private static XposedInterface.Invoker getCachedMethodInvoker(Method method) {
        XposedInterface.Invoker cached = METHOD_INVOKER_CACHE.get(method);
        if (cached != null) {
            return cached;
        }
        XposedInterface.Invoker invoker =
                FlymeStatusBarSizer.getMethodInvokerCompat(method);
        if (invoker != null) {
            XposedInterface.Invoker previous = METHOD_INVOKER_CACHE.putIfAbsent(method, invoker);
            return previous != null ? previous : invoker;
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static XposedInterface.CtorInvoker getCachedConstructorInvoker(
            Constructor<?> constructor) {
        XposedInterface.CtorInvoker cached = CONSTRUCTOR_INVOKER_CACHE.get(constructor);
        if (cached != null) {
            return cached;
        }
        XposedInterface.CtorInvoker invoker =
                FlymeStatusBarSizer.getConstructorInvokerCompat(constructor);
        if (invoker != null) {
            XposedInterface.CtorInvoker previous =
                    CONSTRUCTOR_INVOKER_CACHE.putIfAbsent(constructor, invoker);
            return previous != null ? previous : invoker;
        }
        return null;
    }

    private static final class CachedMethod {
        final Class<?>[] parameterTypes;
        final Method method;

        CachedMethod(Class<?>[] parameterTypes, Method method) {
            this.parameterTypes = parameterTypes;
            this.method = method;
        }
    }
}
