package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

public final class LauncherRecentsHooks {
    private LauncherRecentsHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        LauncherRecentsActivityHooks.installHooks(module, loader);
        LauncherRecentsLayoutEngine.installHooks(module, loader);
        LauncherRecentsAttachController.installHooks(module, loader);
        LauncherRecentsStateAnimationController.installHooks(module, loader);
        LauncherRecentsLaunchController.installHooks(module, loader);
        LauncherRecentsTouchController.installHooks(module, loader);
        LauncherRecentsTransitionController.installHooks(module, loader);
    }

    public static void refreshTrackedViews() {
        LauncherRecentsActivityHooks.refreshTrackedViews();
        LauncherRecentsLayoutEngine.refreshTrackedViews();
    }
}
