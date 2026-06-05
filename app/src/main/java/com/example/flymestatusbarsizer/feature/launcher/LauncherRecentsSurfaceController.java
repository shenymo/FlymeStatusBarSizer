package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.view.SurfaceControl;

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
        hookOnAttachedToWindow(module, loader);
        hookOnOverlaySurfaceChanged(module, loader);
    }

    private static void hookSetShown(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setShown", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object manager = chain.getThisObject();
                boolean shown = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                Object result = chain.proceed();
                if (!shown || !hasValidBaseSurface(manager)) {
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

    private static void hookOnAttachedToWindow(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onAttachedToWindow");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (!hasValidBaseSurface(chain.getThisObject())) {
                    releaseSurface(chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.onAttachedToWindow",
                    t);
        }
    }

    private static void hookOnOverlaySurfaceChanged(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_SURFACE_MANAGER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onOverlaySurfaceChanged", SurfaceControl.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (!hasValidBaseSurface(chain.getThisObject())) {
                    releaseSurface(chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.onOverlaySurfaceChanged",
                    t);
        }
    }

    private static boolean hasValidBaseSurface(Object manager) {
        Object surface = LauncherRecentsCompat.getFieldCompat(manager, "mBaseSurface");
        return surface instanceof SurfaceControl && ((SurfaceControl) surface).isValid();
    }

    private static void releaseSurface(Object manager) {
        LauncherRecentsCompat.invokeCompat(manager, "releaseSurface");
    }
}
