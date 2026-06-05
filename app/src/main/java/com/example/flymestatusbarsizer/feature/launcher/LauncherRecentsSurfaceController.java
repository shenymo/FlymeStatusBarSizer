package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;

final class LauncherRecentsSurfaceController {
    private static final String RECENTS_SURFACE_MANAGER_CLASS =
            "com.meizu.flyme.launcher.quickstep.window.RecentsSurfaceManager";

    private LauncherRecentsSurfaceController() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookSetShown(module, loader);
        hookSetActivityStarted(module, loader);
    }

    private static void hookSetShown(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setShown", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object manager = chain.getThisObject();
                boolean shown = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                boolean currentShown =
                        LauncherRecentsCompat.readBooleanField(manager, "mShown", false);
                Object baseSurface = LauncherRecentsCompat.getFieldCompat(manager, "mBaseSurface");
                Object overviewSurface =
                        LauncherRecentsCompat.getFieldCompat(manager, "mOverviewSurface");
                if (shown && currentShown && baseSurface != null && overviewSurface != null) {
                    Object windowView = LauncherRecentsCompat.getFieldCompat(manager, "mWindowView");
                    LauncherRecentsCompat.invokeCompat(windowView, "invalidate");
                    setTouchable(manager, true);
                    return null;
                }
                Object result = chain.proceed();
                if (shown) {
                    setTouchable(manager, true);
                } else {
                    releaseSurface(manager);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.setShown",
                    t);
        }
    }

    private static void hookSetActivityStarted(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setActivityStarted", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                boolean started = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                Object result = chain.proceed();
                if (!started) {
                    releaseSurface(chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.setActivityStarted",
                    t);
        }
    }

    private static void releaseSurface(Object manager) {
        LauncherRecentsCompat.invokeCompat(manager, "releaseSurface");
    }

    private static void setTouchable(Object manager, boolean touchable) {
        LauncherRecentsCompat.invokeCompat(
                manager,
                "setTouchable",
                LauncherRecentsCompat.BOOLEAN_ARG,
                touchable);
    }
}
