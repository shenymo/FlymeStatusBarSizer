package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Keeps the primary native status bar visible without owning SystemUI's visibility state. */
final class OneStepStatusBar {
    private static volatile boolean visible;
    private static WeakReference<Object> primary = new WeakReference<>(null);
    private static Method apply;
    private static WeakReference<Object> lightBar = new WeakReference<>(null);
    private static boolean darkIcons;
    private static boolean imeVisible;

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        installColors(module, loader);
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.window.StatusBarWindowControllerImpl", false, loader);
            Class<?> state = Class.forName(type.getName() + "$State", false, loader);
            Method method = type.getDeclaredMethod("apply", state);
            method.setAccessible(true);
            Field forceVisible = state.getDeclaredField("mForceStatusBarVisible");
            forceVisible.setAccessible(true);
            module.intercept(method, chain -> {
                Object owner = chain.getThisObject();
                if (!isPrimary(owner)) return chain.proceed();
                primary = new WeakReference<>(owner);
                if (!visible) return chain.proceed();
                Object current = chain.getArg(0);
                boolean requested = forceVisible.getBoolean(current);
                forceVisible.setBoolean(current, true);
                try { return chain.proceed(); }
                finally {
                    // Restore the native request, not a snapshot saved when the workspace opened.
                    forceVisible.setBoolean(current, requested);
                }
            });
            apply = method;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (isPrimary(chain.getThisObject())) primary = new WeakReference<>(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable e) { Log.w(OneHandedTaskHooks.TAG, "Native status bar visibility hook unavailable", e); }
    }

    private static void installColors(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.phone.LightBarControllerImpl", false, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    rememberLightBar(chain.getThisObject());
                    return result;
                });
            }
            for (Method method : type.getDeclaredMethods()) {
                if ("updateStatus".equals(method.getName()) && method.getParameterCount() == 1) {
                    module.intercept(method, chain -> {
                        Object owner = chain.getThisObject();
                        if (!rememberLightBar(owner) || !useWallpaperColors(owner)) return chain.proceed();
                        try {
                            Object icons = OneStepReflection.get(owner, "mStatusBarIconController");
                            OneStepReflection.call(icons, "setIconsDarkArea", new Class<?>[]{ArrayList.class},
                                    (Object) null);
                            Object transitions = OneStepReflection.call(icons, "getTransitionsController");
                            OneStepReflection.call(transitions, "setIconsDark",
                                    new Class<?>[]{boolean.class, boolean.class}, darkIcons, true);
                            return null;
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(OneHandedTaskHooks.TAG, "Cannot tint workspace status bar", error);
                            return chain.proceed();
                        }
                    });
                } else if ("updateNavigation".equals(method.getName()) && method.getParameterCount() == 0) {
                    Field navigationLight = OneStepReflection.field(type, "mNavigationLight");
                    module.intercept(method, chain -> {
                        Object owner = chain.getThisObject();
                        if (!rememberLightBar(owner) || !useWallpaperColors(owner) || imeVisible)
                            return chain.proceed();
                        boolean nativeLight = navigationLight.getBoolean(owner);
                        navigationLight.setBoolean(owner, darkIcons);
                        try { return chain.proceed(); }
                        finally { navigationLight.setBoolean(owner, nativeLight); }
                    });
                }
            }
        } catch (Throwable e) { Log.w(OneHandedTaskHooks.TAG, "Wallpaper system bar tint hook unavailable", e); }
    }

    private static boolean rememberLightBar(Object owner) {
        if (ReflectUtils.getIntField(owner, "mDisplayId", -1) != 0) return false;
        lightBar = new WeakReference<>(owner);
        return true;
    }

    private static boolean useWallpaperColors(Object owner) {
        return visible && !OneHandedTaskHooks.shadeOpen()
                && !ReflectUtils.getBooleanField(owner, "mBouncerVisible", false)
                && !ReflectUtils.getBooleanField(owner, "mQsCustomizing", false)
                && !ReflectUtils.getBooleanField(owner, "mQsExpanded", false)
                && !ReflectUtils.getBooleanField(owner, "mGlobalActionsVisible", false);
    }

    static void setDarkIcons(boolean dark) {
        if (darkIcons == dark) return;
        darkIcons = dark;
        refreshColors();
    }

    static void setImeVisible(boolean shown) {
        if (imeVisible == shown) return;
        imeVisible = shown;
        refreshColors();
    }

    private static void refreshColors() {
        Object owner = lightBar.get();
        if (owner == null) return;
        try {
            // Native appearance fields keep receiving app/shade updates while overridden.
            // Reevaluate those current values on exit instead of restoring a stale snapshot.
            OneStepReflection.call(owner, "reevaluate");
            OneStepReflection.call(owner, "updateNavigation");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(OneHandedTaskHooks.TAG, "Cannot refresh workspace system bar colors", e);
        }
    }

    private static boolean isPrimary(Object owner) {
        Object context = ReflectUtils.getField(owner, "mContext");
        return context instanceof Context && ReflectUtils.invokeNoArgInt(context, "getDisplayId", -1) == 0;
    }

    static void setVisible(boolean requested) {
        visible = requested;
        Runnable update = () -> {
            if (!requested) imeVisible = false;
            refreshColors();
            Object owner = primary.get();
            Method method = apply;
            if (owner == null || method == null) return;
            try { method.invoke(owner, ReflectUtils.getField(owner, "mCurrentState")); }
            catch (ReflectiveOperationException | RuntimeException e) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot update native status bar visibility", e);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else new Handler(Looper.getMainLooper()).post(update);
    }
}
