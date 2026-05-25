package com.example.flymestatusbarsizer.feature.theme;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;

public final class FlymeCloudIconHooks {
    private static final String FLYME_THEME_HELPER_CLASS =
            "android.content.res.flymetheme.FlymeThemeHelper";

    private FlymeCloudIconHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookReturnNull(module, loader, "getCustomIcon", String.class);
    }

    private static void hookReturnNull(
            FlymeStatusBarSizer module,
            ClassLoader loader,
            String methodName,
            Class<?>... parameterTypes) {
        try {
            Class<?> clazz = Class.forName(FLYME_THEME_HELPER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            module.intercept(method, chain -> FlymeStatusBarSizer.shouldDisableFlymeCloudIcons()
                    ? null
                    : chain.proceed());
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook Flyme theme icon method: " + methodName,
                    t);
        }
    }

}
