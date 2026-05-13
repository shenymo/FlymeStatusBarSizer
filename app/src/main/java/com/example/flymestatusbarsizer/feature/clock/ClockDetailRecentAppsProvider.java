package com.example.flymestatusbarsizer.feature.clock;

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

import java.util.ArrayList;
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

    void requestRecentApps(Handler resultHandler, RecentAppsCallback callback) {
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
            for (ActivityManager.RecentTaskInfo taskInfo : recentTasks) {
                if (taskInfo == null) {
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
            return new ClockDetailRecentApp(taskInfo.id, 0, icon, label);
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
