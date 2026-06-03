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
    }

    private static void hookSetShown(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setShown", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object manager = chain.getThisObject();
                boolean targetShown = chain.getArg(0) instanceof Boolean
                        && (Boolean) chain.getArg(0);
                boolean currentShown =
                        LauncherRecentsCompat.readBooleanField(manager, "mShown", false);
                Object baseSurface = LauncherRecentsCompat.getFieldCompat(manager, "mBaseSurface");
                Object overviewSurface =
                        LauncherRecentsCompat.getFieldCompat(manager, "mOverviewSurface");
                if (targetShown && currentShown && baseSurface != null && overviewSurface != null) {
                    Object windowView = LauncherRecentsCompat.getFieldCompat(manager, "mWindowView");
                    LauncherRecentsCompat.invokeCompat(windowView, "invalidate");
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.setShown",
                    t);
        }
    }
}
