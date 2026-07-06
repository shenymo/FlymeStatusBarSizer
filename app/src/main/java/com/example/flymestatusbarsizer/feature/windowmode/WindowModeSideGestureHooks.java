package com.example.flymestatusbarsizer.feature.windowmode;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.feature.mback.MBackHooks;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WindowModeSideGestureHooks {
    private static final Map<Object, Boolean> ACTIVE_ACTIONS = new WeakHashMap<>();
    private static final Map<Object, Boolean> PREWARMED_LAUNCHERS = new WeakHashMap<>();
    private static final int TWO_RING_APP_LIMIT = 11;
    private static final int TWO_RING_OUTER_COUNT = 7;
    private static final Map<String, Field> FIELD_CACHE = new java.util.HashMap<>();
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
            installAppLaunchAnimationHook(module, loader, clazz);
            installNativeAppLauncherTwoRingHook(module, loader, clazz);
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

    private static void installNativeAppLauncherTwoRingHook(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            Class<?> appLauncherClass) {
        installNativeAppLauncherListHook(module, loader, appLauncherClass);
        try {
            Class<?> gestureClass = Class.forName(
                    "com.flyme.systemuitools.windowmode.widget.GestureAppLauncher",
                    false,
                    loader);
            Method layoutMethod = gestureClass.getDeclaredMethod(
                    "onLayout",
                    boolean.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            layoutMethod.setAccessible(true);
            module.intercept(layoutMethod, chain -> {
                Object result = chain.proceed();
                try {
                    Object target = chain.getThisObject();
                    Context context = target instanceof View ? ((View) target).getContext() : null;
                    if (isTwoRingLauncherEnabled(context)) {
                        relayoutTwoRingGestureLauncher(target);
                    }
                } catch (Throwable t) {
                    FlymeStatusBarSizer.logWindowModeWarning(
                            "Failed before Flyme native two-ring layout fallback",
                            t);
                }
                return result;
            });

            Method hitMethod = gestureClass.getDeclaredMethod("q", float.class, float.class);
            hitMethod.setAccessible(true);
            module.intercept(hitMethod, chain -> {
                try {
                    Object target = chain.getThisObject();
                    Context context = target instanceof View ? ((View) target).getContext() : null;
                    if (isTwoRingLauncherEnabled(context)) {
                        Boolean result = selectTwoRingGestureChild(
                                target,
                                asFloat(chain.getArg(0)),
                                asFloat(chain.getArg(1)));
                        if (result != null) {
                            return result;
                        }
                    }
                } catch (Throwable t) {
                    FlymeStatusBarSizer.logWindowModeWarning(
                            "Failed before Flyme native two-ring hit-test fallback",
                            t);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme native app launcher two-ring layout",
                    t);
        }
    }

    private static void installNativeAppLauncherListHook(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            Class<?> appLauncherClass) {
        try {
            Method method = appLauncherClass.getDeclaredMethod("C", List.class);
            method.setAccessible(true);
            Class<?> itemClass = Class.forName("Y0.a", false, loader);
            Class<?> wrapperClass = Class.forName(
                    "com.flyme.systemuitools.windowmode.views.AppLauncherWindow$h",
                    false,
                    loader);
            Constructor<?> constructor = wrapperClass.getDeclaredConstructor(appLauncherClass, itemClass);
            constructor.setAccessible(true);
            module.intercept(method, chain -> {
                Object original = chain.proceed();
                try {
                    if (!isTwoRingLauncherEnabled(resolveAppLauncherContext(chain.getThisObject()))) {
                        return original;
                    }
                    Object source = chain.getArg(0);
                    if (!(source instanceof List) || !(original instanceof List)) {
                        return original;
                    }
                    List<?> sourceList = (List<?>) source;
                    List<?> originalList = (List<?>) original;
                    ArrayList<Object> result = new ArrayList<>();
                    int count = Math.min(TWO_RING_APP_LIMIT, sourceList.size());
                    for (int i = 0; i < count; i++) {
                        Object item = sourceList.get(i);
                        if (itemClass.isInstance(item)) {
                            result.add(constructor.newInstance(chain.getThisObject(), item));
                        }
                    }
                    if (!originalList.isEmpty()) {
                        result.add(originalList.get(originalList.size() - 1));
                    }
                    return result.isEmpty() ? original : result;
                } catch (Throwable t) {
                    FlymeStatusBarSizer.logWindowModeWarning(
                            "Failed to build Flyme native two-ring app list, fallback original",
                            t);
                    return original;
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme native app launcher list size",
                    t);
        }
    }

    private static void relayoutTwoRingGestureLauncher(Object target) {
        if (!(target instanceof View)) {
            return;
        }
        try {
            View launcher = (View) target;
            float centerY = readFloatField(target, "f", -1f);
            if (centerY == -1f) {
                return;
            }
            int measuredWidth = launcher.getMeasuredWidth();
            int childCount = launcher instanceof android.view.ViewGroup
                    ? ((android.view.ViewGroup) launcher).getChildCount()
                    : 0;
            if (childCount <= 0) {
                return;
            }
            int layoutDirection = readIntField(target, "p", 1);
            int touchSlop = readIntField(target, "b", 0);
            int safeDegrees = readIntField(target, "v", 0);
            float outerRadius = readFloatField(target, "g", 0f);
            if (outerRadius <= 0f) {
                return;
            }
            FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                    FlymeStatusBarSizer.loadWindowModeSideGestureConfig(launcher.getContext());
            float innerRadiusRatio = config.twoRingInnerRadiusPercent / 100f;
            float innerIconScale = config.twoRingInnerIconScalePercent / 100f;
            for (int i = 0; i < childCount; i++) {
                View child = ((android.view.ViewGroup) launcher).getChildAt(i);
                if (child == null) {
                    continue;
                }
                applyTwoRingInnerIconScale(child, i, innerIconScale);
                float radius = resolveTwoRingRadius(i, outerRadius, innerRadiusRatio);
                int ringIndex = resolveTwoRingIndex(i);
                int ringCount = resolveTwoRingCount(i, childCount);
                float angle = resolveTwoRingAngle(ringIndex, ringCount, safeDegrees);
                Object layoutParams = child.getLayoutParams();
                writeField(layoutParams, "a", angle);
                double radians = Math.toRadians(angle);
                double centerX = layoutDirection == 0
                        ? Math.sin(radians) * radius
                        : measuredWidth - (Math.sin(radians) * radius);
                double itemCenterY = centerY - (Math.cos(radians) * radius);
                int childLeft = (int) (centerX - (child.getMeasuredWidth() / 2.0d));
                int childTop = (int) (itemCenterY - (child.getMeasuredHeight() / 2.0d));
                child.layout(
                        childLeft,
                        childTop,
                        childLeft + child.getMeasuredWidth(),
                        childTop + child.getMeasuredHeight());
                writeField(layoutParams, "b", radius - (child.getMeasuredWidth() / 2f));
                writeField(layoutParams, "c",
                        radius + (child.getMeasuredWidth() / 2f) + touchSlop);
            }
            writeField(target, "q", true);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to layout Flyme native app launcher as two rings",
                    t);
        }
    }

    private static Boolean selectTwoRingGestureChild(Object target, float x, float y) {
        if (!(target instanceof View)) {
            return null;
        }
        try {
            View launcher = (View) target;
            if (!readBooleanField(target, "q", false)
                    || readFloatField(target, "f", -1f) == -1f) {
                return false;
            }
            if (!(launcher instanceof android.view.ViewGroup)) {
                return null;
            }
            android.view.ViewGroup group = (android.view.ViewGroup) launcher;
            int childCount = group.getChildCount();
            if (childCount <= 0) {
                return false;
            }
            float centerY = readFloatField(target, "f", -1f);
            float distance = resolveTwoRingPointerDistance(target, launcher, x, y, centerY);
            double angle = resolveTwoRingPointerAngle(x, y, centerY, distance);
            int selected = -1;
            for (int i = 0; i < childCount; i++) {
                View child = group.getChildAt(i);
                if (child == null) {
                    continue;
                }
                Object layoutParams = child.getLayoutParams();
                float childAngle = readFloatField(layoutParams, "a", -1f);
                float halfRange = resolveTwoRingHitAngleRange(i, childCount, target) * 0.5f;
                float minRadius = readFloatField(layoutParams, "b", -1f);
                float maxRadius = readFloatField(layoutParams, "c", -1f);
                if (angle >= childAngle - halfRange
                        && angle < childAngle + halfRange
                        && distance >= minRadius
                        && distance <= maxRadius) {
                    selected = i;
                    break;
                }
            }
            int previous = readIntField(target, "m", -1);
            if (previous != selected) {
                if (previous != -1) {
                    invokeIntArg(target, "z", previous);
                }
                if (selected != -1) {
                    invokeIntArg(target, "x", selected);
                } else {
                    writeField(target, "m", -1);
                }
            }
            return selected != -1;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hit-test Flyme native app launcher two rings",
                    t);
            return null;
        }
    }

    private static boolean isTwoRingLauncherEnabled(Context context) {
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        return config.enabled && config.twoRingLauncherEnabled;
    }

    private static int resolveTwoRingIndex(int childIndex) {
        return childIndex < TWO_RING_OUTER_COUNT ? childIndex : childIndex - TWO_RING_OUTER_COUNT;
    }

    private static int resolveTwoRingCount(int childIndex, int childCount) {
        return childIndex < TWO_RING_OUTER_COUNT
                ? Math.min(childCount, TWO_RING_OUTER_COUNT)
                : Math.max(1, childCount - TWO_RING_OUTER_COUNT);
    }

    private static float resolveTwoRingRadius(int childIndex, float outerRadius, float innerRadiusRatio) {
        return childIndex < TWO_RING_OUTER_COUNT
                ? outerRadius
                : outerRadius * innerRadiusRatio;
    }

    private static void applyTwoRingInnerIconScale(View child, int childIndex, float innerIconScale) {
        View icon = findAppIconView(child);
        if (icon == null) {
            return;
        }
        float scale = childIndex < TWO_RING_OUTER_COUNT ? 1f : innerIconScale;
        icon.setScaleX(scale);
        icon.setScaleY(scale);
    }

    private static View findAppIconView(View child) {
        if (child == null) {
            return null;
        }
        Context context = child.getContext();
        int id = child.getResources().getIdentifier(
                "app_icon",
                "id",
                context == null ? "com.flyme.systemuitools" : context.getPackageName());
        return id == 0 ? null : child.findViewById(id);
    }

    private static float resolveTwoRingAngle(int index, int count, int safeDegrees) {
        if (count <= 0) {
            return 45f;
        }
        if (safeDegrees == 0) {
            return (90f / (count + 1f)) * (index + 1f);
        }
        float step = (90f - (safeDegrees * 2f)) / count;
        return (index * step) + (step * 0.5f) + safeDegrees;
    }

    private static float resolveTwoRingHitAngleRange(int childIndex, int childCount, Object target) {
        int safeDegrees = readIntField(target, "v", 0);
        int ringCount = resolveTwoRingCount(childIndex, childCount);
        float range = safeDegrees == 0 ? 90f : 90f - (safeDegrees * 2f);
        return range / Math.max(1, ringCount);
    }

    private static float resolveTwoRingPointerDistance(
            Object target,
            View launcher,
            float x,
            float y,
            float centerY) {
        if (readIntField(target, "p", 1) == 1) {
            x = launcher.getMeasuredWidth() - x;
        }
        float dy = Math.abs(y - centerY);
        return (float) Math.sqrt((x * x) + (dy * dy));
    }

    private static double resolveTwoRingPointerAngle(float x, float y, float centerY, float distance) {
        if (distance <= 0f) {
            return 0d;
        }
        double value = (centerY - y) / distance;
        value = Math.max(-1d, Math.min(1d, value));
        return Math.toDegrees(Math.acos(value));
    }

    private static void installAppLaunchAnimationHook(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            Class<?> appLauncherClass) {
        try {
            Class<?> itemClass = Class.forName("Y0.d", false, loader);
            Method launchMethod = itemClass.getDeclaredMethod("G", int.class);
            launchMethod.setAccessible(true);
            Method method = appLauncherClass.getDeclaredMethod(
                    "j",
                    itemClass,
                    View.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Context context = resolveAppLauncherContext(chain.getThisObject());
                View source = null;
                if (context == null && chain.getArg(1) instanceof View) {
                    source = (View) chain.getArg(1);
                    context = source.getContext();
                }
                if (!isAppLaunchAnimationEnabled(context)) {
                    return chain.proceed();
                }
                Object item = chain.getArg(0);
                if (shouldAnimateNativeAppLaunch(item)) {
                    source = chain.getArg(1) instanceof View ? (View) chain.getArg(1) : null;
                    final Object launchItem = item;
                    final int launchWay = chain.getArg(2) instanceof Integer
                            ? (Integer) chain.getArg(2)
                            : 0;
                    boolean handled = WindowModeSmallWindowLaunchAnimator.play(
                            context,
                            source,
                            item,
                            () -> launchNativeWindowModeApp(launchMethod, launchItem, launchWay));
                    if (handled) {
                        return null;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme window mode app launch animation",
                    t);
        }
    }

    private static boolean isAppLaunchAnimationEnabled(Context context) {
        if (context == null) {
            return false;
        }
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        return config.enabled && config.appLaunchAnimationEnabled && !config.sideGestureEnabled;
    }

    private static boolean shouldAnimateNativeAppLaunch(Object item) {
        if (item == null) {
            return false;
        }
        String packageName = WindowModeSmallWindowLaunchAnimator.resolvePackageName(item);
        return packageName != null
                && !packageName.trim().isEmpty()
                && !"com.meizu.aicy".equals(packageName);
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
        boolean handled = MBackHooks.launchConfiguredIntent(context, config.sideGestureIntentUri);
        if (handled && target != null) {
            synchronized (ACTIVE_ACTIONS) {
                ACTIVE_ACTIONS.put(target, Boolean.TRUE);
            }
        }
        return handled;
    }

    private static boolean consumeActiveGesture(Object target, MotionEvent event) {
        synchronized (ACTIVE_ACTIONS) {
            if (target == null || !ACTIVE_ACTIONS.containsKey(target)) {
                return false;
            }
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ACTIVE_ACTIONS.remove(target);
                return false;
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

    private static Context resolveContext(Object target) {
        Object value = readField(target, "mContext");
        if (value instanceof Context) {
            return (Context) value;
        }
        View anchor = resolveAnchor(target);
        return anchor == null ? null : anchor.getContext();
    }

    private static Context resolveAppLauncherContext(Object target) {
        Object value = readField(target, "c");
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
        try {
            Field field = findField(target.getClass(), name);
            return field == null ? null : field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void launchNativeWindowModeApp(Method launchMethod, Object item, int way) {
        try {
            launchMethod.invoke(item, way);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to launch Flyme window mode app after animation",
                    t);
        }
    }

    private static boolean writeField(Object target, String name, Object value) {
        if (target == null || name == null) {
            return false;
        }
        try {
            Field field = findField(target.getClass(), name);
            if (field == null) {
                return false;
            }
            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> startClass, String name) {
        if (startClass == null || name == null) {
            return null;
        }
        String key = startClass.getName() + "#" + name;
        synchronized (FIELD_CACHE) {
            Field cached = FIELD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = startClass;
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

    private static int readIntField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static float readFloatField(Object target, String name, float fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        Object value = readField(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static float asFloat(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    private static boolean invokeNoArg(Object target, String name) {
        Method method = findNoArgVoidMethod(target == null ? null : target.getClass(), name);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeIntArg(Object target, String name, int value) {
        if (target == null || name == null) {
            return false;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, int.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }
}
