package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Makes system activities eligible for the same multi-window path as user activities. */
public final class OneStepSystemResizePolicy {
    private static final String TAG = OneStepFlowLog.TAG;
    private static boolean installed;

    private OneStepSystemResizePolicy() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> atms = Class.forName("com.android.server.wm.ActivityTaskManagerService", false, loader);
            Class<?> activity = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
            Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
            Class<?> fragment = Class.forName("com.android.server.wm.TaskFragment", false, loader);
            Class<?> displayArea = Class.forName("com.android.server.wm.TaskDisplayArea", false, loader);

            Method retrieveSettings = OneStepReflection.method(atms, "retrieveSettings", ContentResolver.class);
            Method activityResize = OneStepReflection.method(activity, "isResizeable");
            Method taskResize = OneStepReflection.method(task, "isResizeable", boolean.class, boolean.class);
            Method activityMultiWindow = OneStepReflection.method(
                    activity, "supportsMultiWindowInDisplayArea", displayArea);
            Method taskMultiWindow = OneStepReflection.method(
                    fragment, "supportsMultiWindowInDisplayArea", displayArea);

            module.intercept(retrieveSettings, chain -> {
                Object result = chain.proceed();
                forceMultiWindowEnabled(chain.getThisObject(), atms);
                return result;
            });
            module.intercept(activityResize, chain ->
                    isSystemActivity(chain.getThisObject(), activity)
                            ? true : chain.proceed());
            module.intercept(taskResize, chain ->
                    isSystemTask(chain.getThisObject(), task)
                            ? true : chain.proceed());
            module.intercept(activityMultiWindow, chain ->
                    isSystemActivity(chain.getThisObject(), activity)
                            ? true : chain.proceed());
            module.intercept(taskMultiWindow, chain ->
                    isSystemTask(chain.getThisObject(), task)
                            ? true : chain.proceed());

            installed = true;
            OneStepFlowLog.i(TAG, "global system-app multi-window policy installed");
        } catch (Throwable error) {
            OneStepFlowLog.w(TAG, "global system-app multi-window policy unavailable", error);
        }
    }

    private static void forceMultiWindowEnabled(Object service, Class<?> atms) {
        try {
            Field supportsMultiWindow = OneStepReflection.field(atms, "mSupportsMultiWindow");
            supportsMultiWindow.setBoolean(service, true);
        } catch (Throwable error) {
            OneStepFlowLog.w(TAG, "cannot force ActivityTaskManager multi-window support", error);
        }
    }

    private static boolean isSystemActivity(Object value, Class<?> activityClass) {
        if (value == null || !activityClass.isInstance(value)) return false;
        Object info = ReflectUtils.getField(value, "info");
        Object applicationInfo = ReflectUtils.getField(info, "applicationInfo");
        return isSystemApplication(applicationInfo);
    }

    private static boolean isSystemTask(Object value, Class<?> taskClass) {
        if (value == null || !taskClass.isInstance(value)) return false;
        Object activity = null;
        try {
            activity = OneStepReflection.call(value, "getTopActivity",
                    new Class<?>[]{boolean.class, boolean.class}, true, true);
            if (activity == null) activity = OneStepReflection.call(value, "getRootActivity");
        } catch (Throwable ignored) {
        }
        Object info = ReflectUtils.getField(activity, "info");
        Object applicationInfo = ReflectUtils.getField(info, "applicationInfo");
        return isSystemApplication(applicationInfo);
    }

    private static boolean isSystemApplication(Object value) {
        if (!(value instanceof ApplicationInfo)) return false;
        int flags = ((ApplicationInfo) value).flags;
        return (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
}
