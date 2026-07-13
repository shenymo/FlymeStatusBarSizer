package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;

import java.lang.reflect.Method;

public final class LauncherRecentsHooks {
    private LauncherRecentsHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookTaskCornerRadius(module, loader);
        LauncherRecentsLayoutEngine.installHooks(module, loader);
        LauncherRecentsAttachController.installHooks(module, loader);
        LauncherRecentsStateAnimationController.installHooks(module, loader);
        LauncherRecentsLaunchController.installHooks(module, loader);
        LauncherRecentsTouchController.installHooks(module, loader);
        LauncherRecentsTransitionController.installHooks(module, loader);
        LauncherRecentsPerf.install("hook:install",
                "layout attach state launch touch transition");
    }

    public static void refreshTrackedViews() {
        LauncherRecentsLayoutEngine.refreshTrackedViews();
    }

    private static void hookTaskCornerRadius(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.quickstep.util.TaskCornerRadius",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("get", Context.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Context context = chain.getArg(0) instanceof Context
                        ? (Context) chain.getArg(0)
                        : null;
                FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                        FlymeStatusBarSizer.loadLauncherRecentsConfig(context);
                if (context == null || !config.launcherRecentsCardCornerRadiusEnabled) {
                    return chain.proceed();
                }
                return config.launcherRecentsCardCornerRadiusDp
                        * context.getResources().getDisplayMetrics().density;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook recents card corner radius",
                    t);
        }
    }
}
