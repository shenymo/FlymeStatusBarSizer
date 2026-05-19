package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.util.Property;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class LauncherRecentsCompat {
    static final Class<?>[] NO_ARGS = new Class[0];
    static final Class<?>[] INT_ARG = new Class[]{int.class};
    static final Class<?>[] FLOAT_ARG = new Class[]{float.class};
    static final Class<?>[] BOOLEAN_ARG = new Class[]{boolean.class};

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
        return FlymeStatusBarSizer.invokeMethodCompat(target, methodName, parameterTypes, args);
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

    static boolean setStaticFloatPropertyCompat(
            String className,
            String fieldName,
            ClassLoader loader,
            Object target,
            float value) {
        if (className == null || fieldName == null || loader == null || target == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            Object propertyObject = field.get(null);
            if (!(propertyObject instanceof Property)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Property<Object, Float> property = (Property<Object, Float>) propertyObject;
            property.set(target, Float.valueOf(value));
            return true;
        } catch (Throwable ignored) {
            return false;
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

    static boolean invokeMethodReflectively(
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

    static Object createPendingAnimationInstance(
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

    static Object resolveKotlinUnitInstance(ClassLoader loader) {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit", false, loader);
            Field field = unitClass.getField("INSTANCE");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
