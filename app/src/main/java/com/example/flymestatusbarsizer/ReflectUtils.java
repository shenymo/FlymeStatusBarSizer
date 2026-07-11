package com.example.flymestatusbarsizer;

import android.graphics.Paint;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

final class ReflectUtils {
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, CachedField>>
            FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, CachedMethod>>
            NO_ARG_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, CachedMethod[]>>
            METHOD_CACHE = new ConcurrentHashMap<>();
    private static volatile Method SET_MEASURED_DIMENSION_METHOD;

    private ReflectUtils() {
    }

    private static Field findCachedField(Class<?> targetClass, String name) {
        ConcurrentHashMap<String, CachedField> fields = FIELD_CACHE.get(targetClass);
        CachedField cached = fields != null ? fields.get(name) : null;
        if (cached != null) {
            return cached.field;
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                cacheField(targetClass, name, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        cacheField(targetClass, name, null);
        return null;
    }

    private static void cacheField(Class<?> targetClass, String name, Field field) {
        FIELD_CACHE.computeIfAbsent(targetClass, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(name, new CachedField(field));
    }

    private static Method findCachedNoArgMethod(Class<?> targetClass, String name) {
        ConcurrentHashMap<String, CachedMethod> methods = NO_ARG_METHOD_CACHE.get(targetClass);
        CachedMethod cached = methods != null ? methods.get(name) : null;
        if (cached != null) {
            return cached.method;
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name);
                method.setAccessible(true);
                cacheNoArgMethod(targetClass, name, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        cacheNoArgMethod(targetClass, name, null);
        return null;
    }

    private static void cacheNoArgMethod(Class<?> targetClass, String name, Method method) {
        NO_ARG_METHOD_CACHE.computeIfAbsent(targetClass, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(name, new CachedMethod(new Class<?>[0], method));
    }

    private static Method findCachedMethod(Class<?> targetClass, String name, Class<?>... parameterTypes) {
        Class<?>[] resolvedParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
        CachedMethod cached = findCachedMethodEntry(targetClass, name, resolvedParameterTypes);
        if (cached != null) {
            return cached.method;
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, resolvedParameterTypes);
                method.setAccessible(true);
                cacheMethod(targetClass, name, resolvedParameterTypes, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        cacheMethod(targetClass, name, resolvedParameterTypes, null);
        return null;
    }

    private static CachedMethod findCachedMethodEntry(
            Class<?> targetClass,
            String name,
            Class<?>[] parameterTypes) {
        ConcurrentHashMap<String, CachedMethod[]> methods = METHOD_CACHE.get(targetClass);
        CachedMethod[] entries = methods != null ? methods.get(name) : null;
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
            String name,
            Class<?>[] parameterTypes,
            Method method) {
        synchronized (METHOD_CACHE) {
            if (findCachedMethodEntry(targetClass, name, parameterTypes) != null) {
                return;
            }
            ConcurrentHashMap<String, CachedMethod[]> methods = METHOD_CACHE.computeIfAbsent(
                    targetClass,
                    ignored -> new ConcurrentHashMap<>());
            CachedMethod[] entries = methods.get(name);
            int count = entries != null ? entries.length : 0;
            CachedMethod[] updated = new CachedMethod[count + 1];
            if (count > 0) {
                System.arraycopy(entries, 0, updated, 0, count);
            }
            updated[count] = new CachedMethod(parameterTypes.clone(), method);
            methods.put(name, updated);
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

    static Object getField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                return field.get(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Object getStaticField(ClassLoader loader, String className, String name) {
        if (loader == null || className == null || name == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Field field = findCachedField(clazz, name);
            return field == null ? null : field.get(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    static int getStaticIntField(ClassLoader loader, String className, String name) {
        Object value = getStaticField(loader, className, name);
        return value instanceof Integer ? (Integer) value : 0;
    }

    static void setIntField(Object target, String name, int value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.setInt(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    static void setBooleanField(Object target, String name, boolean value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.setBoolean(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    static void setFloatField(Object target, String name, float value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.setFloat(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    static void setField(Object target, String name, Object value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.set(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    static int getIntField(Object target, String name, int fallback) {
        Object value = getField(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static int invokeNoArgInt(Object target, String name, int fallback) {
        Object value = invokeNoArg(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Method method = findCachedNoArgMethod(target.getClass(), name);
            if (method != null) {
                return method.invoke(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Object invokeMethod(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Method method = findCachedMethod(target.getClass(), name, parameterTypes);
            if (method != null) {
                return method.invoke(target, args);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static boolean getBooleanField(Object target, String name, boolean fallback) {
        Object value = getField(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    static double getDoubleField(Object target, String name, double fallback) {
        Object value = getField(target, name);
        return value instanceof Double ? (Double) value : fallback;
    }

    static void setMeasuredDimension(View view, int width, int height) {
        try {
            Method method = SET_MEASURED_DIMENSION_METHOD;
            if (method == null) {
                method = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                method.setAccessible(true);
                SET_MEASURED_DIMENSION_METHOD = method;
            }
            method.invoke(view, width, height);
        } catch (Throwable ignored) {
        }
    }

    static void setPaintColor(Object target, String name, int color) {
        Object value = getField(target, name);
        if (value instanceof Paint) {
            ((Paint) value).setColor(color);
        }
    }

    private static final class CachedField {
        final Field field;

        CachedField(Field field) {
            this.field = field;
        }
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
