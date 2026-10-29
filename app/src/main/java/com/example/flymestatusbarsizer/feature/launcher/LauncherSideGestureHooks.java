package com.example.flymestatusbarsizer.feature.launcher;

import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Method;
import java.util.Collections;

public final class LauncherSideGestureHooks {
    private LauncherSideGestureHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        try {
            Class<?> rootView = Class.forName("com.android.launcher3.LauncherRootView", false, loader);
            Method method = rootView.getDeclaredMethod("setDisallowBackGesture", boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                ModuleConfig config = ModuleConfig.load(view.getContext());
                if (config.enabled && config.assistantGestureEnabled
                        && config.launcherSideGestureEnabled) {
                    view.setSystemGestureExclusionRects(Collections.emptyList());
                }
                return result;
            });
        } catch (ReflectiveOperationException e) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to hook launcher side gesture exclusion", e);
        }
    }
}
