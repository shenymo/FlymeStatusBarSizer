package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;

import java.lang.reflect.Method;

public final class LauncherAppearanceHooks {
    private static final String THEME_ICON_UTILS_CLASS =
            "com.meizu.flyme.launcher.utils.ThemeIconUtils";

    private LauncherAppearanceHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookFolderBackgroundColor(module, loader);
    }

    private static void hookFolderBackgroundColor(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(THEME_ICON_UTILS_CLASS, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"getFolderBgColor".equals(method.getName())
                        || method.getReturnType() != int.class
                        || method.getParameterTypes().length == 0) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Context context = chain.getArg(0) instanceof Context
                            ? (Context) chain.getArg(0)
                            : null;
                    FlymeStatusBarSizer.LauncherAppearanceConfigSnapshot config =
                            FlymeStatusBarSizer.loadLauncherAppearanceConfig(context);
                    if (!config.enabled || !config.launcherFolderBgColorEnabled) {
                        return chain.proceed();
                    }
                    return config.launcherFolderBgColor;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook launcher folder background color",
                    t);
        }
    }
}
