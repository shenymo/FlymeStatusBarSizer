package com.example.flymestatusbarsizer;

import android.graphics.Paint;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

final class ReflectUtils {
    private static final HashMap<String, Field> FIELD_CACHE = new HashMap<>();
    private static final HashMap<String, Method> NO_ARG_METHOD_CACHE = new HashMap<>();
    private static final HashMap<String, Method> METHOD_CACHE = new HashMap<>();
    private static volatile Method SET_MEASURED_DIMENSION_METHOD;

    private ReflectUtils() {
    }

    private static String memberCacheKey(Class<?> clazz, String name) {
        return clazz.getName() + "#" + name;
    }

    private static String methodCacheKey(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        StringBuilder builder = new StringBuilder(clazz.getName()).append('#').append(name).append('(');
        if (parameterTypes != null) {
            for (Class<?> parameterType : parameterTypes) {
                builder.append(parameterType == null ? "null" : parameterType.getName()).append(',');
            }
        }
        return builder.append(')').toString();
    }

    private static Field findCachedField(Class<?> targetClass, String name) {
        String key = memberCacheKey(targetClass, name);
        synchronized (FIELD_CACHE) {
            Field cached = FIELD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                synchronized (FIELD_CACHE) {
                    FIELD_CACHE.put(key, field);
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findCachedNoArgMethod(Class<?> targetClass, String name) {
        String key = memberCacheKey(targetClass, name);
        synchronized (NO_ARG_METHOD_CACHE) {
            Method cached = NO_ARG_METHOD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name);
                method.setAccessible(true);
                synchronized (NO_ARG_METHOD_CACHE) {
                    NO_ARG_METHOD_CACHE.put(key, method);
                }
                return method;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findCachedMethod(Class<?> targetClass, String name, Class<?>... parameterTypes) {
        String key = methodCacheKey(targetClass, name, parameterTypes);
        synchronized (METHOD_CACHE) {
            Method cached = METHOD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                synchronized (METHOD_CACHE) {
                    METHOD_CACHE.put(key, method);
                }
                return method;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
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
}
