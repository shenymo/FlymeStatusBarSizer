package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class ClockDetailRecentAppsProvider {
    private static final int RECENT_TASK_QUERY_SIZE = Integer.MAX_VALUE;
    private static final int RECENT_TASK_FLAGS = ActivityManager.RECENT_IGNORE_UNAVAILABLE;
    private static final int ACTIVITY_INFO_FLAGS =
            PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_UNINSTALLED_PACKAGES;
    private static final Object BACKGROUND_LOCK = new Object();
    private static Handler backgroundHandler;

    private final Context context;
    private final ActivityManager activityManager;

    interface RecentAppsCallback {
        void onRecentApps(ClockDetailRecentApp[] recentApps);
    }

    ClockDetailRecentAppsProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.activityManager = this.context != null
                ? (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE)
                : null;
    }

    void requestRecentApps(
            Handler resultHandler,
            RecentAppsCallback callback) {
        if (callback == null) {
            return;
        }
        Handler workerHandler = getBackgroundHandler();
        if (workerHandler == null) {
            deliverRecentApps(resultHandler, callback, ClockDetailRecentApp.EMPTY_ARRAY);
            return;
        }
        workerHandler.post(() -> {
            ClockDetailRecentApp[] recentApps = readRecentApps();
            deliverRecentApps(resultHandler, callback, recentApps);
        });
    }

    private ClockDetailRecentApp[] readRecentApps() {
        if (context == null || activityManager == null) {
            return ClockDetailRecentApp.EMPTY_ARRAY;
        }
        try {
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(
                    RECENT_TASK_QUERY_SIZE,
                    RECENT_TASK_FLAGS);
            if (recentTasks == null || recentTasks.isEmpty()) {
                return ClockDetailRecentApp.EMPTY_ARRAY;
            }
            ArrayList<ClockDetailRecentApp> apps = new ArrayList<>(recentTasks.size());
            HashSet<Integer> seenTaskIds = new HashSet<>();
            for (ActivityManager.RecentTaskInfo taskInfo : recentTasks) {
                if (taskInfo == null) {
                    continue;
                }
                int taskId = resolveTaskId(taskInfo);
                if (taskId < 0 || !seenTaskIds.add(taskId)) {
                    continue;
                }
                ComponentName component = resolveComponent(taskInfo);
                if (component == null) {
                    continue;
                }
                ClockDetailRecentApp app = buildRecentApp(taskInfo, component);
                if (app == null) {
                    continue;
                }
                apps.add(app);
            }
            return apps.isEmpty()
                    ? ClockDetailRecentApp.EMPTY_ARRAY
                    : apps.toArray(new ClockDetailRecentApp[0]);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning("Failed to read recent apps for clock detail", t);
            return ClockDetailRecentApp.EMPTY_ARRAY;
        }
    }

    private ClockDetailRecentApp buildRecentApp(
            ActivityManager.RecentTaskInfo taskInfo,
            ComponentName component) {
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
            int taskId = resolveTaskId(taskInfo);
            int userId = resolveUserId(taskInfo);
            return new ClockDetailRecentApp(
                    taskId,
                    userId,
                    component,
                    icon,
                    label,
                    resolveTaskColor(taskInfo, component));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to load recent app icon for " + component.flattenToShortString(),
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

    private static int resolveUserId(ActivityManager.RecentTaskInfo taskInfo) {
        Object userIdValue = readFieldValue(taskInfo, "userId");
        return userIdValue instanceof Integer ? Math.max(0, (Integer) userIdValue) : 0;
    }

    private static int resolveTaskColor(
            ActivityManager.RecentTaskInfo taskInfo,
            ComponentName component) {
        try {
            Object taskDescriptionValue = readFieldValue(taskInfo, "taskDescription");
            if (taskDescriptionValue instanceof ActivityManager.TaskDescription) {
                int backgroundColor =
                        ((ActivityManager.TaskDescription) taskDescriptionValue).getBackgroundColor();
                if (Color.alpha(backgroundColor) == 0 && backgroundColor != 0) {
                    return Color.argb(
                            255,
                            Color.red(backgroundColor),
                            Color.green(backgroundColor),
                            Color.blue(backgroundColor));
                }
                if (backgroundColor != 0) {
                    return backgroundColor;
                }
            }
        } catch (Throwable ignored) {
        }
        String packageName = component != null ? component.getPackageName() : "";
        int hash = TextUtils.isEmpty(packageName) ? 0 : packageName.hashCode();
        float hue = (hash & Integer.MAX_VALUE) % 360;
        float saturation = 0.32f + ((((hash >>> 8) & 0xFF) / 255f) * 0.18f);
        float value = 0.54f + ((((hash >>> 16) & 0xFF) / 255f) * 0.18f);
        return Color.HSVToColor(new float[]{hue, saturation, value});
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
                HandlerThread thread = new HandlerThread("ClockDetailRecentApps");
                thread.start();
                backgroundHandler = new Handler(thread.getLooper());
            } catch (Throwable t) {
                FlymeStatusBarSizer.logClockWarning(
                        "Failed to start clock detail recent apps worker",
                        t);
                backgroundHandler = null;
            }
            return backgroundHandler;
        }
    }

    private static void deliverRecentApps(
            Handler resultHandler,
            RecentAppsCallback callback,
            ClockDetailRecentApp[] recentApps) {
        ClockDetailRecentApp[] safeRecentApps =
                recentApps != null && recentApps.length > 0
                        ? recentApps
                        : ClockDetailRecentApp.EMPTY_ARRAY;
        if (resultHandler == null) {
            callback.onRecentApps(safeRecentApps);
            return;
        }
        resultHandler.post(() -> callback.onRecentApps(safeRecentApps));
    }

}
