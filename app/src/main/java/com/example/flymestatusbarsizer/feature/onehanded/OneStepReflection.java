package com.example.flymestatusbarsizer.feature.onehanded;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/** Shell mutations must report failures instead of silently leaving a task half attached. */
final class OneStepReflection {
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();

    private OneStepReflection() {}

    static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        String key = type.getName() + "." + name + Arrays.toString(parameters);
        Method cached = METHODS.get(key);
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(type)) return cached;
        try {
            Method result = type.getMethod(name, parameters);
            result.setAccessible(true);
            METHODS.put(key, result);
            return result;
        } catch (NoSuchMethodException ignored) { }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name, parameters);
                result.setAccessible(true);
                METHODS.put(key, result);
                return result;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(key);
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    static Object call(Object target, String name, Class<?>[] parameters, Object... args)
            throws ReflectiveOperationException {
        return method(target.getClass(), name, parameters).invoke(target, args);
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static Object get(Object target, String name) throws ReflectiveOperationException {
        return field(target.getClass(), name).get(target);
    }
}
