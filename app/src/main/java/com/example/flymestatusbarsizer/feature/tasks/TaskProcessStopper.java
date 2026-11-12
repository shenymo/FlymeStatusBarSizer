package com.example.flymestatusbarsizer.feature.tasks;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.TaskInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TaskProcessStopper {
    private static final String LAUNCHER = "com.meizu.flyme.launcher";
    private static final String ACTION = BuildConfig.APPLICATION_ID + ".task.STOP";
    private static final String TAG = "FlymeTaskStopper";

    private TaskProcessStopper() { }

    public static void installLauncher(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> policy = Class.forName("com.meizu.flyme.launcher.quickstep.TaskKillAlgorithm", false, loader);
            Method instance = policy.getMethod("getInstance", Context.class);
            Method removeOne = policy.getDeclaredMethod("lambda$removeTaskById$0",
                    int.class, String.class, Context.class, String.class);
            Method removeWithoutKill = policy.getDeclaredMethod("lambda$removeTaskNotKillProcess$1", int.class);
            Method removeAll = policy.getDeclaredMethod("killAllTasks",
                    Context.class, Runnable.class, int[].class, boolean.class);
            removeOne.setAccessible(true);
            removeWithoutKill.setAccessible(true);
            removeAll.setAccessible(true);
            Method locks = Class.forName("com.meizu.systemui.shared.model.LockTaskManager", false, loader)
                    .getMethod("getAndCreateManager", Context.class);
            Class<?> wrapperClass = Class.forName("com.android.systemui.shared.system.ActivityManagerWrapper", false, loader);
            Class<?> application = Class.forName(LAUNCHER + ".LauncherApplication", false, loader);
            Method onCreate = application.getDeclaredMethod("onCreate");
            module.intercept(onCreate, chain -> {
                Object result = chain.proceed();
                if (!LAUNCHER.equals(Application.getProcessName())) return result;
                Context context = (Context) chain.getThisObject();
                HandlerThread thread = new HandlerThread(TAG);
                thread.start();
                Handler worker = new Handler(thread.getLooper());
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) {
                        boolean all = intent.getBooleanExtra("all", false);
                        String packageName = intent.getStringExtra("package");
                        int userId = intent.getIntExtra("user", -1);
                        int taskId = intent.getIntExtra("task", -1);
                        if (!all && (packageName == null || packageName.isEmpty() || userId < 0 || taskId < 0
                                || LAUNCHER.equals(packageName) || "com.android.systemui".equals(packageName))) {
                            setResult(Activity.RESULT_CANCELED, "Invalid task target", null);
                            return;
                        }
                        try {
                            locks.invoke(null, c);
                            Object algorithm = instance.invoke(null, c);
                            PendingResult pending = goAsync();
                            worker.post(() -> {
                                try {
                                    if (all) {
                                        Object wrapper = wrapperClass.getMethod("getInstance").invoke(null);
                                        ActivityManager.RunningTaskInfo[] running = (ActivityManager.RunningTaskInfo[])
                                                wrapperClass.getMethod("getRunningTasks", boolean.class).invoke(wrapper, true);
                                        int[] keep = {-1, -1};
                                        int count = 0;
                                        for (ActivityManager.RunningTaskInfo task : running) {
                                            if ((Boolean) wrapperClass.getMethod("isHomeTask",
                                                    ActivityManager.RunningTaskInfo.class).invoke(null, task)) break;
                                            if (count < keep.length && task.isVisible()) keep[count++] = task.taskId;
                                        }
                                        removeAll.invoke(algorithm, c, null, keep, false);
                                        Log.i(TAG, "Finished launcher global cleanup");
                                    } else {
                                        Class<?> managerClass = Class.forName("android.app.ActivityTaskManager");
                                        Object manager = managerClass.getMethod("getInstance").invoke(null);
                                        int max = ((Number) managerClass.getMethod("getMaxRecentTasksStatic").invoke(null)).intValue();
                                        List<?> recent = (List<?>) managerClass
                                                .getMethod("getRecentTasks", int.class, int.class, int.class)
                                                .invoke(manager, max, 15, userId);
                                        TaskInfo target = null;
                                        int matching = 0;
                                        for (Object value : recent) {
                                            TaskInfo task = (TaskInfo) value;
                                            if (ReflectUtils.getIntField(task, "userId", -1) != userId) continue;
                                            if (task.taskId == taskId) target = task;
                                            ComponentName base = task.baseIntent.getComponent();
                                            if (base != null && packageName.equals(base.getPackageName())) matching++;
                                        }
                                        if (target == null) {
                                            for (ActivityManager.RunningTaskInfo task : c.getSystemService(ActivityManager.class)
                                                    .getRunningTasks(Integer.MAX_VALUE)) {
                                                if (task.taskId == taskId
                                                        && ReflectUtils.getIntField(task, "userId", -1) == userId) {
                                                    target = task;
                                                    break;
                                                }
                                            }
                                        }
                                        if (target != null) {
                                            ComponentName base = target.baseIntent.getComponent();
                                            if (base == null || !packageName.equals(base.getPackageName())) {
                                                throw new IllegalArgumentException("Task target does not match its base application");
                                            }
                                            boolean otherTask = matching > (recent.contains(target) ? 1 : 0);
                                            if (otherTask) {
                                                removeWithoutKill.invoke(algorithm, taskId);
                                            } else {
                                                removeOne.invoke(algorithm, taskId, base.getPackageName(), c,
                                                        base.getPackageName() + ":" + userId);
                                            }
                                        }
                                        Log.i(TAG, "Finished launcher cleanup task=" + taskId
                                                + " package=" + packageName + " user=" + userId);
                                    }
                                    pending.setResult(Activity.RESULT_OK, null, null);
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    Log.w(TAG, "Launcher task cleanup failed", error);
                                    pending.setResult(Activity.RESULT_CANCELED, error.toString(), null);
                                } finally {
                                    pending.finish();
                                }
                            });
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(TAG, "Cannot start launcher task cleanup", error);
                            setResult(Activity.RESULT_CANCELED, error.toString(), null);
                        }
                    }
                }, new IntentFilter(ACTION), "android.permission.STATUS_BAR_SERVICE",
                        new Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED);
                return result;
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Cannot install launcher task control", error);
        }
    }

    // Called by the gesture/switcher worker; the ordered result confirms completion in Launcher.
    public static void remove(Context context, int taskId, String packageName, int userId) throws Exception {
        request(context, new Intent(ACTION).setPackage(LAUNCHER)
                .putExtra("task", taskId).putExtra("package", packageName).putExtra("user", userId));
    }

    public static void clearAll(Context context) throws Exception {
        request(context, new Intent(ACTION).setPackage(LAUNCHER).putExtra("all", true));
    }

    private static void request(Context context, Intent request) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        String[] failure = {"Launcher task control unavailable"};
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) failure[0] = null;
                else if (getResultData() != null) failure[0] = getResultData();
                done.countDown();
            }
        };
        Context.class.getMethod("sendOrderedBroadcastAsUser", Intent.class, UserHandle.class, String.class,
                BroadcastReceiver.class, Handler.class, int.class, String.class, android.os.Bundle.class)
                .invoke(context, request, UserHandle.getUserHandleForUid(
                                ((Number) ActivityManager.class.getMethod("getCurrentUser").invoke(null)).intValue() * 100000),
                        "android.permission.REMOVE_TASKS", result, new Handler(Looper.getMainLooper()),
                        Activity.RESULT_CANCELED, null, null);
        if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Launcher task control timed out");
        if (failure[0] != null) throw new IllegalStateException(failure[0]);
    }
}
