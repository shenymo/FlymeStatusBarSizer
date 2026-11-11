package com.example.flymestatusbarsizer.feature.tasks;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
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

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TaskProcessStopper {
    private static final String LAUNCHER = "com.meizu.flyme.launcher";
    private static final String ACTION = BuildConfig.APPLICATION_ID + ".task.STOP";
    private static final String TAG = "FlymeTaskStopper";

    private TaskProcessStopper() { }

    public static void installLauncher(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> application = Class.forName(LAUNCHER + ".LauncherApplication", false, loader);
            Method onCreate = application.getDeclaredMethod("onCreate");
            module.intercept(onCreate, chain -> {
                Object result = chain.proceed();
                if (!LAUNCHER.equals(Application.getProcessName())) return result;
                Context context = (Context) chain.getThisObject();
                HandlerThread thread = new HandlerThread(TAG);
                thread.start();
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) {
                        String packageName = intent.getStringExtra("package");
                        int userId = intent.getIntExtra("user", -1);
                        int taskId = intent.getIntExtra("task", -1);
                        if (packageName == null || packageName.isEmpty() || userId < 0 || taskId < 0
                                || LAUNCHER.equals(packageName) || "com.android.systemui".equals(packageName)) {
                            setResult(Activity.RESULT_CANCELED, "Invalid task target", null);
                            return;
                        }
                        try {
                            Object tasks = Class.forName("android.app.ActivityTaskManager")
                                    .getMethod("getService").invoke(null);
                            // A prior stop of the same app may already have removed this task.
                            Class.forName("android.app.IActivityTaskManager")
                                    .getMethod("removeTask", int.class).invoke(tasks, taskId);
                            ActivityManager manager = c.getSystemService(ActivityManager.class);
                            ActivityManager.class.getMethod("forceStopPackageAsUser", String.class, int.class)
                                    .invoke(manager, packageName, userId);
                            Log.i(TAG, "Stopped task=" + taskId + " package=" + packageName + " user=" + userId);
                            setResult(Activity.RESULT_OK, null, null);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(TAG, "Cannot stop task=" + taskId + " package=" + packageName + " user=" + userId, error);
                            setResult(Activity.RESULT_CANCELED, error.toString(), null);
                        }
                    }
                }, new IntentFilter(ACTION), "android.permission.STATUS_BAR_SERVICE",
                        new Handler(thread.getLooper()), Context.RECEIVER_EXPORTED);
                return result;
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Cannot install launcher task control", error);
        }
    }

    // Called by the gesture/switcher worker; the ordered result confirms completion in Launcher.
    public static void remove(Context context, int taskId, String packageName, int userId) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        String[] failure = {"Launcher task control unavailable"};
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) failure[0] = null;
                else if (getResultData() != null) failure[0] = getResultData();
                done.countDown();
            }
        };
        Intent request = new Intent(ACTION).setPackage(LAUNCHER)
                .putExtra("task", taskId).putExtra("package", packageName).putExtra("user", userId);
        Context.class.getMethod("sendOrderedBroadcastAsUser", Intent.class, UserHandle.class, String.class,
                BroadcastReceiver.class, Handler.class, int.class, String.class, android.os.Bundle.class)
                .invoke(context, request, UserHandle.getUserHandleForUid(
                                ((Number) ActivityManager.class.getMethod("getCurrentUser").invoke(null)).intValue() * 100000),
                        "android.permission.FORCE_STOP_PACKAGES", result, new Handler(Looper.getMainLooper()),
                        Activity.RESULT_CANCELED, null, null);
        if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Launcher task control timed out");
        if (failure[0] != null) throw new IllegalStateException(failure[0]);
    }
}
