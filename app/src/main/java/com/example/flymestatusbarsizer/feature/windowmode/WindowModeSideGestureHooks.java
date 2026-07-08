package com.example.flymestatusbarsizer.feature.windowmode;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.feature.mback.MBackHooks;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WindowModeSideGestureHooks {
    private static final Map<Object, Boolean> ACTIVE_ACTIONS = new WeakHashMap<>();
    private static final Map<Object, Boolean> PREWARMED_LAUNCHERS = new WeakHashMap<>();
    private static final Map<Object, HoverState> HOVER_STATES = new WeakHashMap<>();
    private static final Map<Object, RecentRingState> RECENT_RING_STATES = new WeakHashMap<>();
    private static final Object RECENT_PACKAGES_LOCK = new Object();
    private static final Object BACKGROUND_LOCK = new Object();
    private static final int TWO_RING_APP_LIMIT = 11;
    private static final int TWO_RING_OUTER_COUNT = 7;
    private static final int RECENT_RING_COUNT = 4;
    private static final int RECENT_TASK_SCAN_LIMIT = 32;
    private static final Map<String, Field> FIELD_CACHE = new java.util.HashMap<>();
    private static final Map<String, Integer> APP_ICON_ID_CACHE = new HashMap<>();
    private static ArrayList<RecentPackage> recentPackagesCache = new ArrayList<>();
    private static long recentPackagesCacheTimeMs;
    private static Handler backgroundHandler;
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
                preloadRecentRingData(chain.getThisObject());
                return result;
            });
            installRecentRingPrepareRefreshHook(module, clazz);
            installNativeAppLauncherLaunchHook(module, loader, clazz);
            installNativeAppLauncherHoverFullscreenHook(module, loader);
            installNativeAppLauncherTwoRingHook(module, loader, clazz);
            Method destroyMethod = findNoArgVoidMethod(clazz, "D");
            if (destroyMethod != null) {
                destroyMethod.setAccessible(true);
                module.intercept(destroyMethod, chain -> {
                    try {
                        return chain.proceed();
                    } finally {
                        Object launcher = resolveAppWindowGestureLauncher(chain.getThisObject());
                        synchronized (PREWARMED_LAUNCHERS) {
                            PREWARMED_LAUNCHERS.remove(chain.getThisObject());
                        }
                        synchronized (RECENT_RING_STATES) {
                            RECENT_RING_STATES.remove(launcher);
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

    private static void installRecentRingPrepareRefreshHook(
            FlymeStatusBarSizer module,
            Class<?> appLauncherClass) {
        try {
            Method method = findNoArgVoidMethod(appLauncherClass, "Q");
            if (method == null) {
                return;
            }
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object appWindow = chain.getThisObject();
                Context context = resolveAppLauncherContext(appWindow);
                FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                        FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
                if (config.enabled && config.twoRingLauncherEnabled && config.recentInnerRingEnabled) {
                    refreshRecentPackagesCache(appWindow, context);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme native recent ring prepare refresh",
                    t);
        }
    }

    private static void installNativeAppLauncherHoverFullscreenHook(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> gestureClass = Class.forName(
                    "com.flyme.systemuitools.windowmode.widget.GestureAppLauncher",
                    false,
                    loader);
            Method hoverMethod = gestureClass.getDeclaredMethod("x", int.class);
            hoverMethod.setAccessible(true);
            module.intercept(hoverMethod, chain -> {
                Object result = chain.proceed();
                recordHoverStart(chain.getThisObject(), asInt(chain.getArg(0)));
                return result;
            });

            Method unhoverMethod = gestureClass.getDeclaredMethod("z", int.class);
            unhoverMethod.setAccessible(true);
            module.intercept(unhoverMethod, chain -> {
                Object result = chain.proceed();
                clearHoverIfMatches(chain.getThisObject(), asInt(chain.getArg(0)));
                return result;
            });

            Method touchMethod = gestureClass.getDeclaredMethod("w", MotionEvent.class, int.class);
            touchMethod.setAccessible(true);
            module.intercept(touchMethod, chain -> {
                MotionEvent event = chain.getArg(0) instanceof MotionEvent
                        ? (MotionEvent) chain.getArg(0)
                        : null;
                if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    clearHover(chain.getThisObject());
                }
                Object result = chain.proceed();
                if (isTerminalEvent(event)) {
                    clearHover(chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme native app launcher hover fullscreen",
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
                Object appWindow = chain.getThisObject();
                try {
                    Context context = resolveAppLauncherContext(appWindow);
                    FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                            FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
                    if (!config.enabled || !config.twoRingLauncherEnabled) {
                        clearRecentRingState(appWindow);
                        return original;
                    }
                    Object source = chain.getArg(0);
                    if (!(source instanceof List) || !(original instanceof List)) {
                        return original;
                    }
                    List<?> sourceList = (List<?>) source;
                    List<?> originalList = (List<?>) original;
                    ArrayList<Object> result = new ArrayList<>();
                    HashSet<String> shownPackages = new HashSet<>();
                    int count = Math.min(TWO_RING_APP_LIMIT, sourceList.size());
                    for (int i = 0; i < count; i++) {
                        Object item = sourceList.get(i);
                        if (itemClass.isInstance(item)) {
                            result.add(constructor.newInstance(appWindow, item));
                            addPackageName(shownPackages, item);
                        }
                    }
                    if (!originalList.isEmpty()) {
                        result.add(originalList.get(originalList.size() - 1));
                    }
                    int recentStart = result.size();
                    if (config.recentInnerRingEnabled) {
                        result.addAll(buildRecentLauncherItems(
                                appWindow,
                                sourceList,
                                itemClass,
                                constructor,
                                shownPackages));
                    }
                    updateRecentRingState(appWindow, recentStart, result.size() - recentStart);
                    return result.isEmpty() ? original : result;
                } catch (Throwable t) {
                    clearRecentRingState(appWindow);
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

    private static List<Object> buildRecentLauncherItems(
            Object appWindow,
            List<?> sourceList,
            Class<?> itemClass,
            Constructor<?> constructor,
            HashSet<String> shownPackages) throws Exception {
        Context context = resolveAppLauncherContext(appWindow);
        ArrayList<RecentPackage> recentPackages = readRecentPackages(appWindow, context);
        if (recentPackages.isEmpty()) {
            return new ArrayList<>();
        }
        HashMap<String, Object> candidates = buildLauncherItemCandidateMap(
                appWindow,
                sourceList,
                itemClass,
                shownPackages);
        ArrayList<Object> result = new ArrayList<>();
        for (RecentPackage recentPackage : recentPackages) {
            String packageName = recentPackage.packageName;
            if (packageName == null
                    || packageName.isEmpty()
                    || shownPackages.contains(packageName)) {
                continue;
            }
            Object item = candidates.get(packageName);
            if (item == null) {
                continue;
            }
            result.add(constructor.newInstance(appWindow, item));
            shownPackages.add(packageName);
            if (result.size() >= RECENT_RING_COUNT) {
                break;
            }
        }
        return result;
    }

    private static HashMap<String, Object> buildLauncherItemCandidateMap(
            Object appWindow,
            List<?> sourceList,
            Class<?> itemClass,
            HashSet<String> shownPackages) {
        HashMap<String, Object> result = new HashMap<>();
        addLauncherItemsByPackage(result, sourceList, itemClass, shownPackages);
        Object manager = readField(appWindow, "g");
        addLauncherItemsByPackage(result, invokeNoArgObject(manager, "F"), itemClass, shownPackages);
        addLauncherItemsByPackage(result, invokeNoArgObject(manager, "G"), itemClass, shownPackages);
        return result;
    }

    private static void addLauncherItemsByPackage(
            HashMap<String, Object> target,
            Object source,
            Class<?> itemClass,
            HashSet<String> shownPackages) {
        if (!(source instanceof List)) {
            return;
        }
        for (Object item : (List<?>) source) {
            if (item == null || !itemClass.isInstance(item)) {
                continue;
            }
            String packageName = resolveLauncherItemPackageName(item);
            if (packageName != null
                    && !packageName.isEmpty()
                    && !shownPackages.contains(packageName)
                    && !target.containsKey(packageName)) {
                target.put(packageName, item);
            }
        }
    }

    private static ArrayList<RecentPackage> readRecentPackages(Object appWindow, Context context) {
        ArrayList<RecentPackage> cached = getCachedRecentPackages();
        return cached == null ? refreshRecentPackagesCache(appWindow, context) : cached;
    }

    private static ArrayList<RecentPackage> refreshRecentPackagesCache(Object appWindow, Context context) {
        HashMap<String, RecentPackage> merged = new HashMap<>();
        addFlymeRecentPackages(merged, appWindow);
        addSystemRecentPackages(merged, context);
        ArrayList<RecentPackage> result = new ArrayList<>(merged.values());
        Collections.sort(result, (left, right) -> -Long.compare(left.timeMs, right.timeMs));
        if (result.size() > RECENT_RING_COUNT) {
            result = new ArrayList<>(result.subList(0, RECENT_RING_COUNT));
        }
        synchronized (RECENT_PACKAGES_LOCK) {
            recentPackagesCache = new ArrayList<>(result);
            recentPackagesCacheTimeMs = SystemClock.uptimeMillis();
        }
        return result;
    }

    private static void addFlymeRecentPackages(HashMap<String, RecentPackage> target, Object appWindow) {
        Object manager = readField(appWindow, "g");
        Object source = invokeNoArgObject(manager, "N");
        if (!(source instanceof List)) {
            return;
        }
        for (Object item : (List<?>) source) {
            String packageName = resolveLauncherItemPackageName(item);
            if (packageName != null && !packageName.isEmpty()) {
                putRecentPackage(target, packageName, readLongNoArg(item, "i"));
            }
        }
    }

    private static void addSystemRecentPackages(HashMap<String, RecentPackage> target, Context context) {
        if (context == null) {
            return;
        }
        try {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return;
            }
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(
                    RECENT_TASK_SCAN_LIMIT,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE);
            if (recentTasks == null || recentTasks.isEmpty()) {
                return;
            }
            long fallbackTimeMs = System.currentTimeMillis();
            for (int i = 0; i < recentTasks.size(); i++) {
                ActivityManager.RecentTaskInfo taskInfo = recentTasks.get(i);
                ComponentName component = resolveRecentTaskComponent(taskInfo);
                String packageName = component == null ? null : component.getPackageName();
                if (packageName != null && !packageName.trim().isEmpty()) {
                    putRecentPackage(target, packageName,
                            resolveRecentTaskTimeMs(taskInfo, fallbackTimeMs - i));
                }
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to read Flyme native app launcher recent tasks",
                    t);
        }
    }

    private static void putRecentPackage(
            HashMap<String, RecentPackage> target,
            String packageName,
            long timeMs) {
        RecentPackage old = target.get(packageName);
        if (old == null || timeMs > old.timeMs) {
            target.put(packageName, new RecentPackage(packageName, timeMs));
        }
    }

    private static ArrayList<RecentPackage> getCachedRecentPackages() {
        synchronized (RECENT_PACKAGES_LOCK) {
            if (recentPackagesCacheTimeMs == 0L) {
                return null;
            }
            return new ArrayList<>(recentPackagesCache);
        }
    }

    private static long resolveRecentTaskTimeMs(
            ActivityManager.RecentTaskInfo taskInfo,
            long fallbackTimeMs) {
        Object value = readField(taskInfo, "lastActiveTime");
        if (!(value instanceof Number)) {
            return fallbackTimeMs;
        }
        long timeMs = ((Number) value).longValue();
        if (timeMs <= 0L) {
            return fallbackTimeMs;
        }
        return timeMs < 1000000000000L
                ? System.currentTimeMillis() - SystemClock.elapsedRealtime() + timeMs
                : timeMs;
    }

    private static ComponentName resolveRecentTaskComponent(ActivityManager.RecentTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity;
        }
        Intent baseIntent = taskInfo.baseIntent;
        return baseIntent == null ? null : baseIntent.getComponent();
    }

    private static void addPackageName(HashSet<String> packages, Object item) {
        String packageName = resolveLauncherItemPackageName(item);
        if (packageName != null && !packageName.trim().isEmpty()) {
            packages.add(packageName);
        }
    }

    private static String resolveLauncherItemPackageName(Object item) {
        Object value = invokeNoArgObject(item, "f");
        return value instanceof String ? ((String) value).trim() : null;
    }

    private static void updateRecentRingState(Object appWindow, int recentStart, int recentCount) {
        Object launcher = resolveAppWindowGestureLauncher(appWindow);
        synchronized (RECENT_RING_STATES) {
            if (launcher == null || recentCount <= 0) {
                RECENT_RING_STATES.remove(launcher);
                return;
            }
            RecentRingState state = new RecentRingState();
            state.recentStart = recentStart;
            state.recentCount = recentCount;
            RECENT_RING_STATES.put(launcher, state);
        }
    }

    private static void clearRecentRingState(Object appWindow) {
        Object launcher = resolveAppWindowGestureLauncher(appWindow);
        synchronized (RECENT_RING_STATES) {
            RECENT_RING_STATES.remove(launcher);
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
            float recentRadiusRatio = config.recentInnerRingRadiusPercent / 100f;
            float recentIconScale = config.recentInnerRingIconScalePercent / 100f;
            RecentRingState recentState = getRecentRingState(target, childCount);
            int fixedCount = resolveFixedChildCount(childCount, recentState);
            for (int i = 0; i < childCount; i++) {
                View child = ((android.view.ViewGroup) launcher).getChildAt(i);
                if (child == null) {
                    continue;
                }
                child.setVisibility(View.VISIBLE);
                applyTwoRingInnerIconScale(child, i, innerIconScale, recentState, recentIconScale);
                float radius = resolveTwoRingRadius(
                        i,
                        fixedCount,
                        outerRadius,
                        innerRadiusRatio,
                        recentRadiusRatio,
                        recentState);
                int ringIndex = resolveTwoRingIndex(i, recentState);
                int ringCount = resolveTwoRingCount(i, fixedCount, recentState);
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
            RecentRingState recentState = getRecentRingState(target, childCount);
            int fixedCount = resolveFixedChildCount(childCount, recentState);
            int selected = -1;
            for (int i = 0; i < childCount; i++) {
                View child = group.getChildAt(i);
                if (child == null || child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                Object layoutParams = child.getLayoutParams();
                float childAngle = readFloatField(layoutParams, "a", -1f);
                float halfRange = resolveTwoRingHitAngleRange(
                        i,
                        fixedCount,
                        target,
                        recentState) * 0.5f;
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

    private static RecentRingState getRecentRingState(Object target, int childCount) {
        synchronized (RECENT_RING_STATES) {
            RecentRingState state = RECENT_RING_STATES.get(target);
            if (state == null
                    || state.recentStart < 0
                    || state.recentStart >= childCount
                    || state.recentCount <= 0) {
                return null;
            }
            state.recentCount = Math.min(state.recentCount, childCount - state.recentStart);
            return state;
        }
    }

    private static int resolveFixedChildCount(int childCount, RecentRingState recentState) {
        return recentState == null ? childCount : Math.min(childCount, recentState.recentStart);
    }

    private static boolean isRecentRingChild(int childIndex, RecentRingState recentState) {
        return recentState != null
                && childIndex >= recentState.recentStart
                && childIndex < recentState.recentStart + recentState.recentCount;
    }

    private static int resolveTwoRingIndex(
            int childIndex,
            RecentRingState recentState) {
        if (isRecentRingChild(childIndex, recentState)) {
            return childIndex - recentState.recentStart;
        }
        return childIndex < TWO_RING_OUTER_COUNT ? childIndex : childIndex - TWO_RING_OUTER_COUNT;
    }

    private static int resolveTwoRingCount(
            int childIndex,
            int fixedCount,
            RecentRingState recentState) {
        if (isRecentRingChild(childIndex, recentState)) {
            return Math.max(1, recentState.recentCount);
        }
        return childIndex < TWO_RING_OUTER_COUNT
                ? Math.min(fixedCount, TWO_RING_OUTER_COUNT)
                : Math.max(1, fixedCount - TWO_RING_OUTER_COUNT);
    }

    private static float resolveTwoRingRadius(
            int childIndex,
            int fixedCount,
            float outerRadius,
            float innerRadiusRatio,
            float recentRadiusRatio,
            RecentRingState recentState) {
        if (isRecentRingChild(childIndex, recentState)) {
            return outerRadius * recentRadiusRatio;
        }
        return childIndex < TWO_RING_OUTER_COUNT
                ? outerRadius
                : outerRadius * innerRadiusRatio;
    }

    private static void applyTwoRingInnerIconScale(
            View child,
            int childIndex,
            float innerIconScale,
            RecentRingState recentState,
            float recentIconScale) {
        View icon = findAppIconView(child);
        if (icon == null) {
            return;
        }
        float scale = isRecentRingChild(childIndex, recentState)
                ? recentIconScale
                : childIndex < TWO_RING_OUTER_COUNT ? 1f : innerIconScale;
        if (icon.getScaleX() == scale && icon.getScaleY() == scale) {
            return;
        }
        icon.setScaleX(scale);
        icon.setScaleY(scale);
    }

    private static View findAppIconView(View child) {
        if (child == null) {
            return null;
        }
        Context context = child.getContext();
        String packageName = context == null ? "com.flyme.systemuitools" : context.getPackageName();
        int id = resolveAppIconId(child, packageName);
        return id == 0 ? null : child.findViewById(id);
    }

    private static int resolveAppIconId(View child, String packageName) {
        synchronized (APP_ICON_ID_CACHE) {
            Integer cached = APP_ICON_ID_CACHE.get(packageName);
            if (cached != null) {
                return cached;
            }
            int id = child.getResources().getIdentifier("app_icon", "id", packageName);
            APP_ICON_ID_CACHE.put(packageName, id);
            return id;
        }
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

    private static float resolveTwoRingHitAngleRange(
            int childIndex,
            int fixedCount,
            Object target,
            RecentRingState recentState) {
        int safeDegrees = readIntField(target, "v", 0);
        int ringCount = resolveTwoRingCount(childIndex, fixedCount, recentState);
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

    private static void installNativeAppLauncherLaunchHook(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            Class<?> appLauncherClass) {
        try {
            Class<?> itemClass = Class.forName("Y0.d", false, loader);
            Method method = appLauncherClass.getDeclaredMethod(
                    "j",
                    itemClass,
                    View.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Context context = resolveAppLauncherContext(chain.getThisObject());
                if (context == null && chain.getArg(1) instanceof View) {
                    context = ((View) chain.getArg(1)).getContext();
                }
                Object item = chain.getArg(0);
                if (shouldLaunchFullscreenFromHover(chain.getThisObject(), context)
                        && launchFullscreenApp(context, item)) {
                    invokeNoArg(chain.getThisObject(), "Z");
                    clearHover(resolveAppWindowGestureLauncher(chain.getThisObject()));
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook Flyme window mode app launch",
                    t);
        }
    }

    private static void recordHoverStart(Object launcher, int index) {
        if (!(launcher instanceof View) || index < 0) {
            clearHover(launcher);
            return;
        }
        Context context = ((View) launcher).getContext();
        if (!isHoverFullscreenEnabled(context)) {
            clearHover(launcher);
            return;
        }
        View child = launcher instanceof ViewGroup ? ((ViewGroup) launcher).getChildAt(index) : null;
        int timeoutMs = resolveHoverFullscreenTimeoutMs(context);
        synchronized (HOVER_STATES) {
            HoverState old = HOVER_STATES.remove(launcher);
            if (old != null) {
                old.clear();
            }
            HoverState state = new HoverState(index, SystemClock.uptimeMillis(), timeoutMs, child);
            HOVER_STATES.put(launcher, state);
            state.start();
        }
    }

    private static void clearHoverIfMatches(Object launcher, int index) {
        synchronized (HOVER_STATES) {
            HoverState state = HOVER_STATES.get(launcher);
            if (state != null && state.index == index) {
                HOVER_STATES.remove(launcher);
                state.clear();
            }
        }
    }

    private static void clearHover(Object launcher) {
        synchronized (HOVER_STATES) {
            HoverState state = HOVER_STATES.remove(launcher);
            if (state != null) {
                state.clear();
            }
        }
    }

    private static boolean shouldLaunchFullscreenFromHover(Object appWindow, Context context) {
        if (!isHoverFullscreenEnabled(context)) {
            return false;
        }
        Object launcher = resolveAppWindowGestureLauncher(appWindow);
        if (launcher == null) {
            return false;
        }
        synchronized (HOVER_STATES) {
            HoverState state = HOVER_STATES.get(launcher);
            return state != null
                    && state.index == readIntField(launcher, "m", -1)
                    && SystemClock.uptimeMillis() - state.startTimeMs >= state.timeoutMs;
        }
    }

    private static boolean isHoverFullscreenEnabled(Context context) {
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        return config.enabled && config.hoverFullscreenEnabled;
    }

    private static int resolveHoverFullscreenTimeoutMs(Context context) {
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        return Math.max(300, Math.min(2000, config.hoverFullscreenTimeoutMs));
    }

    private static boolean launchFullscreenApp(Context context, Object item) {
        if (context == null || item == null) {
            return false;
        }
        String packageName = resolveLauncherItemPackageName(item);
        if (packageName == null
                || packageName.trim().isEmpty()
                || "com.meizu.aicy".equals(packageName)) {
            return false;
        }
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Object launcherInfo = invokeNoArgObject(item, "e");
            Object user = invokeNoArgObject(launcherInfo, "getUser");
            if (user != null && startActivityAsUser(context, intent, user)) {
                return true;
            }
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to launch Flyme app fullscreen from hover",
                    t);
            return false;
        }
    }

    private static boolean startActivityAsUser(Context context, Intent intent, Object user) {
        try {
            for (Method method : Context.class.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"startActivityAsUser".equals(method.getName())) {
                    continue;
                }
                if (types.length == 2
                        && types[0].isAssignableFrom(Intent.class)
                        && types[1].isInstance(user)) {
                    method.setAccessible(true);
                    method.invoke(context, intent, user);
                    return true;
                }
                if (types.length == 3
                        && types[0].isAssignableFrom(Intent.class)
                        && types[1].isAssignableFrom(Bundle.class)
                        && types[2].isInstance(user)) {
                    method.setAccessible(true);
                    method.invoke(context, intent, null, user);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void preloadRecentRingData(Object target) {
        Context context = resolveAppLauncherContext(target);
        FlymeStatusBarSizer.WindowModeSideGestureConfigSnapshot config =
                FlymeStatusBarSizer.loadWindowModeSideGestureConfig(context);
        if (!config.enabled || !config.twoRingLauncherEnabled || !config.recentInnerRingEnabled) {
            return;
        }
        Handler handler = getBackgroundHandler();
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            refreshRecentPackagesCache(target, context);
            invokeContextArg(readField(target, "g"), "P", context);
        });
    }

    private static Handler getBackgroundHandler() {
        synchronized (BACKGROUND_LOCK) {
            if (backgroundHandler == null) {
                HandlerThread thread = new HandlerThread("WindowModeRecentRing");
                thread.start();
                backgroundHandler = new Handler(thread.getLooper());
            }
            return backgroundHandler;
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

    private static Object resolveAppWindowGestureLauncher(Object target) {
        Object value = readField(target, "j");
        return value != null ? value : readField(target, "mGestureAppLauncher");
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

    private static Object invokeNoArgObject(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static long readLongNoArg(Object target, String name) {
        Object value = invokeNoArgObject(target, name);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
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

    private static boolean invokeContextArg(Object target, String name, Context context) {
        if (target == null || name == null || context == null) {
            return false;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, Context.class);
                method.setAccessible(true);
                method.invoke(target, context);
                return true;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static final class HoverState {
        final int index;
        final long startTimeMs;
        final int timeoutMs;
        final View hapticTarget;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable hapticRunnable = this::fireHaptic;
        boolean hapticFired;

        HoverState(int index, long startTimeMs, int timeoutMs, View hapticTarget) {
            this.index = index;
            this.startTimeMs = startTimeMs;
            this.timeoutMs = timeoutMs;
            this.hapticTarget = hapticTarget;
        }

        void start() {
            handler.postDelayed(hapticRunnable, timeoutMs);
        }

        void fireHaptic() {
            if (hapticFired || hapticTarget == null) {
                return;
            }
            hapticFired = true;
            if (!hapticTarget.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)) {
                hapticTarget.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
            hapticTarget.animate()
                    .scaleX(1.16f)
                    .scaleY(1.16f)
                    .setDuration(120L)
                    .start();
        }

        void clear() {
            handler.removeCallbacks(hapticRunnable);
            if (hapticFired && hapticTarget != null) {
                hapticTarget.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(90L)
                        .start();
            }
        }
    }

    private static final class RecentRingState {
        int recentStart;
        int recentCount;
    }

    private static final class RecentPackage {
        final String packageName;
        final long timeMs;

        RecentPackage(String packageName, long timeMs) {
            this.packageName = packageName;
            this.timeMs = timeMs;
        }
    }
}
