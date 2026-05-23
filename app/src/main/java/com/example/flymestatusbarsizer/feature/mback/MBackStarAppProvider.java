package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class MBackStarAppProvider {
    private static final int RECENT_TASK_QUERY_SIZE = Integer.MAX_VALUE;
    private static final int RECENT_TASK_FLAGS = ActivityManager.RECENT_IGNORE_UNAVAILABLE;
    private static final int ACTIVITY_INFO_FLAGS =
            PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_UNINSTALLED_PACKAGES;
    private static final Object BACKGROUND_LOCK = new Object();
    private static Handler backgroundHandler;

    private final Context context;
    private final ActivityManager activityManager;

    interface Callback {
        void onApps(MBackStarApp[] apps);
    }

    MBackStarAppProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.activityManager = this.context != null
                ? (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE)
                : null;
    }

    void requestApps(Handler resultHandler, Callback callback) {
        if (callback == null) {
            return;
        }
        Handler workerHandler = getBackgroundHandler();
        if (workerHandler == null) {
            deliver(resultHandler, callback, MBackStarApp.EMPTY_ARRAY);
            return;
        }
        workerHandler.post(() -> deliver(resultHandler, callback, readApps()));
    }

    private MBackStarApp[] readApps() {
        if (context == null || activityManager == null) {
            return MBackStarApp.EMPTY_ARRAY;
        }
        try {
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(
                    RECENT_TASK_QUERY_SIZE,
                    RECENT_TASK_FLAGS);
            if (recentTasks == null || recentTasks.isEmpty()) {
                return MBackStarApp.EMPTY_ARRAY;
            }
            ArrayList<MBackStarApp> apps = new ArrayList<>(recentTasks.size());
            HashSet<Integer> seenTaskIds = new HashSet<>();
            for (ActivityManager.RecentTaskInfo taskInfo : recentTasks) {
                int taskId = resolveTaskId(taskInfo);
                if (taskId < 0 || !seenTaskIds.add(taskId)) {
                    continue;
                }
                ComponentName component = resolveComponent(taskInfo);
                if (component == null) {
                    continue;
                }
                MBackStarApp app = buildApp(taskId, component);
                if (app != null) {
                    apps.add(app);
                }
            }
            return apps.isEmpty() ? MBackStarApp.EMPTY_ARRAY : apps.toArray(new MBackStarApp[0]);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to read recent apps for mBack star overlay", t);
            return MBackStarApp.EMPTY_ARRAY;
        }
    }

    private MBackStarApp buildApp(int taskId, ComponentName component) {
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            return null;
        }
        try {
            ActivityInfo activityInfo = packageManager.getActivityInfo(component, ACTIVITY_INFO_FLAGS);
            Drawable icon = activityInfo.loadIcon(packageManager);
            if (icon == null) {
                icon = packageManager.getDefaultActivityIcon();
            }
            CharSequence label = activityInfo.loadLabel(packageManager);
            if ((label == null || label.toString().trim().isEmpty())
                    && activityInfo.applicationInfo != null) {
                label = activityInfo.applicationInfo.loadLabel(packageManager);
            }
            if (label == null || label.toString().trim().isEmpty()) {
                label = component.getPackageName();
            }
            return new MBackStarApp(taskId, icon, label);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    "Failed to load recent app for mBack star overlay: "
                            + component.flattenToShortString(),
                    t);
            return null;
        }
    }

    private static ComponentName resolveComponent(ActivityManager.RecentTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity;
        }
        Intent baseIntent = taskInfo.baseIntent;
        return baseIntent != null ? baseIntent.getComponent() : null;
    }

    private static int resolveTaskId(ActivityManager.RecentTaskInfo taskInfo) {
        if (taskInfo == null) {
            return -1;
        }
        Object taskIdValue = readFieldValue(taskInfo, "taskId");
        if (taskIdValue instanceof Integer) {
            int taskId = (Integer) taskIdValue;
            if (taskId >= 0) {
                return taskId;
            }
        }
        return taskInfo.id;
    }

    private static Object readFieldValue(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Handler getBackgroundHandler() {
        synchronized (BACKGROUND_LOCK) {
            if (backgroundHandler != null) {
                return backgroundHandler;
            }
            try {
                HandlerThread thread = new HandlerThread("MBackStarApps");
                thread.start();
                backgroundHandler = new Handler(thread.getLooper());
            } catch (Throwable t) {
                FlymeStatusBarSizer.logMBackWarning("Failed to start mBack star apps worker", t);
                backgroundHandler = null;
            }
            return backgroundHandler;
        }
    }

    private static void deliver(Handler resultHandler, Callback callback, MBackStarApp[] apps) {
        MBackStarApp[] safeApps = apps != null && apps.length > 0
                ? apps
                : MBackStarApp.EMPTY_ARRAY;
        if (resultHandler == null) {
            callback.onApps(safeApps);
            return;
        }
        resultHandler.post(() -> callback.onApps(safeApps));
    }
}
