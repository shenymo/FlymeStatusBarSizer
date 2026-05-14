package com.example.flymestatusbarsizer.feature.clock;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class ClockDetailRecentAppsProvider {
    private static final int RECENT_TASK_QUERY_SIZE = Integer.MAX_VALUE;
    private static final int MBACK_RECENT_TASK_QUERY_SIZE = 6;
    private static final int RECENT_TASK_FLAGS = ActivityManager.RECENT_IGNORE_UNAVAILABLE;
    private static final int ACTIVITY_INFO_FLAGS =
            PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_UNINSTALLED_PACKAGES;
    private static final int MAX_SNAPSHOT_LONG_EDGE_PX = 720;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String FLYME_LAUNCHER_PACKAGE = "com.meizu.flyme.launcher";
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
            ClockDetailPopupController.HostMode hostMode,
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
            ClockDetailRecentApp[] recentApps = readRecentApps(hostMode);
            deliverRecentApps(resultHandler, callback, recentApps);
        });
    }

    private ClockDetailRecentApp[] readRecentApps(ClockDetailPopupController.HostMode hostMode) {
        if (context == null || activityManager == null) {
            return ClockDetailRecentApp.EMPTY_ARRAY;
        }
        boolean includeSnapshots = hostMode == ClockDetailPopupController.HostMode.MBACK;
        int querySize = includeSnapshots ? MBACK_RECENT_TASK_QUERY_SIZE : RECENT_TASK_QUERY_SIZE;
        try {
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(
                    querySize,
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
                if (includeSnapshots && shouldSkipTaskForMBack(taskInfo, component)) {
                    continue;
                }
                ClockDetailRecentApp app = buildRecentApp(taskInfo, component, includeSnapshots);
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
            ComponentName component,
            boolean includeSnapshot) {
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
            TaskSnapshotData snapshotData = includeSnapshot
                    ? loadTaskSnapshot(taskId)
                    : TaskSnapshotData.EMPTY;
            long snapshotId = snapshotData.snapshotId;
            if (includeSnapshot && snapshotData.bitmap != null && snapshotId <= 0L) {
                snapshotId = SystemClock.uptimeMillis();
            }
            return new ClockDetailRecentApp(
                    taskId,
                    userId,
                    component,
                    icon,
                    label,
                    snapshotData.bitmap,
                    snapshotId,
                    resolveTaskColor(taskInfo, component));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to load recent app icon for " + component.flattenToShortString(),
                    t);
            return null;
        }
    }

    private TaskSnapshotData loadTaskSnapshot(int taskId) {
        if (taskId < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return TaskSnapshotData.EMPTY;
        }
        try {
            Object activityTaskManagerService = getActivityTaskManagerService();
            if (activityTaskManagerService == null) {
                return TaskSnapshotData.EMPTY;
            }
            Object snapshot = invokeTaskSnapshotMethod(
                    activityTaskManagerService,
                    "getTaskSnapshot",
                    taskId,
                    false);
            if (snapshot == null) {
                snapshot = invokeTaskSnapshotMethod(
                        activityTaskManagerService,
                        "takeTaskSnapshot",
                        taskId,
                        true);
            }
            if (snapshot == null) {
                return TaskSnapshotData.EMPTY;
            }
            return new TaskSnapshotData(
                    createBitmapFromSnapshot(snapshot),
                    readLongFromNoArgMethod(snapshot, "getId"));
        } catch (Throwable t) {
            FlymeStatusBarSizer.logClockWarning(
                    "Failed to load recent task snapshot: " + taskId,
                    t);
            return TaskSnapshotData.EMPTY;
        }
    }

    private static Object getActivityTaskManagerService() {
        try {
            Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
            Method method = activityTaskManagerClass.getDeclaredMethod("getService");
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeTaskSnapshotMethod(
            Object service,
            String methodName,
            int taskId,
            boolean firstBooleanValue) {
        if (service == null || methodName == null) {
            return null;
        }
        Method[] methods = service.getClass().getMethods();
        for (Method method : methods) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            Object[] args = buildTaskSnapshotInvocationArgs(
                    method.getParameterTypes(),
                    taskId,
                    firstBooleanValue);
            if (args == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(service, args);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object[] buildTaskSnapshotInvocationArgs(
            Class<?>[] parameterTypes,
            int taskId,
            boolean firstBooleanValue) {
        if (parameterTypes == null
                || parameterTypes.length < 2
                || parameterTypes[0] != int.class) {
            return null;
        }
        Object[] args = new Object[parameterTypes.length];
        args[0] = taskId;
        int booleanIndex = 0;
        for (int i = 1; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == boolean.class) {
                args[i] = booleanIndex == 0 ? firstBooleanValue : Boolean.FALSE;
                booleanIndex++;
                continue;
            }
            return null;
        }
        return booleanIndex > 0 ? args : null;
    }

    private static Bitmap createBitmapFromSnapshot(Object snapshot) {
        if (snapshot == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        HardwareBuffer hardwareBuffer = null;
        try {
            Object hardwareBufferValue = invokeNoArgMethod(snapshot, "getHardwareBuffer");
            if (!(hardwareBufferValue instanceof HardwareBuffer)) {
                return null;
            }
            hardwareBuffer = (HardwareBuffer) hardwareBufferValue;
            ColorSpace colorSpace = null;
            Object colorSpaceValue = invokeNoArgMethod(snapshot, "getColorSpace");
            if (colorSpaceValue instanceof ColorSpace) {
                colorSpace = (ColorSpace) colorSpaceValue;
            }
            Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
            return scaleBitmapIfNeeded(bitmap);
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (hardwareBuffer != null) {
                try {
                    hardwareBuffer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Bitmap scaleBitmapIfNeeded(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longEdge = Math.max(width, height);
        if (longEdge <= 0 || longEdge <= MAX_SNAPSHOT_LONG_EDGE_PX) {
            return bitmap;
        }
        float scale = MAX_SNAPSHOT_LONG_EDGE_PX / (float) longEdge;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        try {
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        } catch (Throwable ignored) {
            return bitmap;
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

    private static boolean shouldSkipTaskForMBack(
            ActivityManager.RecentTaskInfo taskInfo,
            ComponentName component) {
        if (component == null) {
            return true;
        }
        String packageName = component.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            return true;
        }
        if (SYSTEM_UI_PACKAGE.equals(packageName) || FLYME_LAUNCHER_PACKAGE.equals(packageName)) {
            return true;
        }
        Intent baseIntent = taskInfo != null ? taskInfo.baseIntent : null;
        return baseIntent != null && baseIntent.hasCategory(Intent.CATEGORY_HOME);
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

    private static Object invokeNoArgMethod(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long readLongFromNoArgMethod(Object target, String methodName) {
        Object value = invokeNoArgMethod(target, methodName);
        return value instanceof Long ? (Long) value : 0L;
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

    private static final class TaskSnapshotData {
        static final TaskSnapshotData EMPTY = new TaskSnapshotData(null, 0L);

        final Bitmap bitmap;
        final long snapshotId;

        TaskSnapshotData(Bitmap bitmap, long snapshotId) {
            this.bitmap = bitmap;
            this.snapshotId = snapshotId;
        }
    }
}
