package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.view.SurfaceControl;
import android.view.View;

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
                boolean shown = chain.getArg(0) instanceof Boolean && (Boolean) chain.getArg(0);
                boolean wasShown = LauncherRecentsCompat.readBooleanField(manager, "mShown", false);
                Object surfaceValue = LauncherRecentsCompat.getFieldCompat(manager, "mOverviewSurface");
                boolean surfaceValid = surfaceValue instanceof SurfaceControl
                        && ((SurfaceControl) surfaceValue).isValid();
                View windowView = resolveWindowView(manager);
                LauncherRecentsPerf.flow("surface:setShown",
                        windowView,
                        "shown=" + shown
                                + " wasShown=" + wasShown
                                + " surfaceValid=" + surfaceValid);
                long totalStartNs = LauncherRecentsPerf.start(windowView);
                try {
                    checkSurface(manager);
                    if (shown != wasShown) {
                        LauncherRecentsCompat.setBooleanField(manager, "mShown", shown);
                    }
                    if (shown) {
                        ensureWindowViewVisible(manager);
                    }
                    long transactionStartNs = LauncherRecentsPerf.start(windowView);
                    try {
                        setSurfaceVisible(manager, shown);
                    } finally {
                        LauncherRecentsPerf.end("surface:transaction", transactionStartNs);
                    }
                } finally {
                    LauncherRecentsPerf.end("surface:setShown", totalStartNs);
                }
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsSurfaceManager.setShown",
                    t);
        }
    }

    private static void checkSurface(Object manager) {
        LauncherRecentsCompat.invokeCompat(manager, "checkSurface");
    }

    private static void ensureWindowViewVisible(Object manager) {
        Object value = LauncherRecentsCompat.getFieldCompat(manager, "mWindowView");
        if (value instanceof View) {
            View view = (View) value;
            if (view.getVisibility() != View.VISIBLE) {
                view.setVisibility(View.VISIBLE);
            }
        }
    }

    private static View resolveWindowView(Object manager) {
        Object value = LauncherRecentsCompat.getFieldCompat(manager, "mWindowView");
        return value instanceof View ? (View) value : null;
    }

    private static void setSurfaceVisible(Object manager, boolean visible) {
        Object value = LauncherRecentsCompat.getFieldCompat(manager, "mOverviewSurface");
        if (!(value instanceof SurfaceControl)) {
            return;
        }
        SurfaceControl surface = (SurfaceControl) value;
        if (!surface.isValid()) {
            return;
        }
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        transaction.setVisibility(surface, visible);
        transaction.apply();
        transaction.close();
    }
}
