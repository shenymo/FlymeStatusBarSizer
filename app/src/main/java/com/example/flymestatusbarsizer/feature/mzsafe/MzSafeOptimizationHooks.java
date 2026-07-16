package com.example.flymestatusbarsizer.feature.mzsafe;

import android.app.Service;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;

public final class MzSafeOptimizationHooks {
    private MzSafeOptimizationHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        hook(module, loader, "com.meizu.safe.installer.AppDisposalJobService", "d", null);
        hook(module, loader, "com.meizu.safe.installer.AppDisposalJobService", "onStartJob", false);
        hook(module, loader, "com.meizu.safe.clean.service.CleanAutoService", "onStartCommand", 2);
        hook(module, loader, "filtratorsdk.y50", "h", null);
        hook(module, loader, "filtratorsdk.y50", "j", null);
        hook(module, loader, "filtratorsdk.y50", "k", null);
        hook(module, loader, "com.meizu.safe.track.EventTrackService", "y", null);
        hook(module, loader, "com.meizu.safe.track.EventTrackService", "z", null);
        hook(module, loader, "filtratorsdk.ju0", "e", null);
    }

    private static void hook(FlymeStatusBarSizer module, ClassLoader loader,
            String className, String methodName, Object result) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    if (!FlymeStatusBarSizer.isMzSafeBackgroundOptimizationEnabled()) {
                        return chain.proceed();
                    }
                    if (chain.getThisObject() instanceof Service
                            && "onStartCommand".equals(methodName)) {
                        ((Service) chain.getThisObject()).stopSelf();
                    }
                    return result;
                });
            }
        } catch (Throwable ignored) {
        }
    }
}
