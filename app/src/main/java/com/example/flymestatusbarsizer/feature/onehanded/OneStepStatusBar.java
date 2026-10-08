package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Keeps the primary native status bar visible without owning SystemUI's visibility state. */
final class OneStepStatusBar {
    private static volatile boolean visible;
    private static WeakReference<Object> primary = new WeakReference<>(null);
    private static Method apply;

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.window.StatusBarWindowControllerImpl", false, loader);
            Class<?> state = Class.forName(type.getName() + "$State", false, loader);
            Method method = type.getDeclaredMethod("apply", state);
            method.setAccessible(true);
            Field forceVisible = state.getDeclaredField("mForceStatusBarVisible");
            forceVisible.setAccessible(true);
            module.intercept(method, chain -> {
                Object owner = chain.getThisObject();
                if (!isPrimary(owner)) return chain.proceed();
                primary = new WeakReference<>(owner);
                if (!visible) return chain.proceed();
                Object current = chain.getArg(0);
                boolean requested = forceVisible.getBoolean(current);
                forceVisible.setBoolean(current, true);
                try { return chain.proceed(); }
                finally {
                    // Restore the native request, not a snapshot saved when the workspace opened.
                    forceVisible.setBoolean(current, requested);
                }
            });
            apply = method;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (isPrimary(chain.getThisObject())) primary = new WeakReference<>(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable e) { Log.w(OneHandedTaskHooks.TAG, "Native status bar visibility hook unavailable", e); }
    }

    private static boolean isPrimary(Object owner) {
        Object context = ReflectUtils.getField(owner, "mContext");
        return context instanceof Context && ReflectUtils.invokeNoArgInt(context, "getDisplayId", -1) == 0;
    }

    static void setVisible(boolean requested) {
        visible = requested;
        Runnable update = () -> {
            Object owner = primary.get();
            Method method = apply;
            if (owner == null || method == null) return;
            try { method.invoke(owner, ReflectUtils.getField(owner, "mCurrentState")); }
            catch (ReflectiveOperationException | RuntimeException e) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot update native status bar visibility", e);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else new Handler(Looper.getMainLooper()).post(update);
    }
}
