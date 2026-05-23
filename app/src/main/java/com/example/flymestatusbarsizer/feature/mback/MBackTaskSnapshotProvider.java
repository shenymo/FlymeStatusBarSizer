package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;

import java.lang.reflect.Method;

final class MBackTaskSnapshotProvider {
    private static final Object BACKGROUND_LOCK = new Object();
    private static Handler backgroundHandler;

    private final Context context;

    interface Callback {
        void onSnapshot(int taskId, Bitmap bitmap);
    }

    MBackTaskSnapshotProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
    }

    void requestSnapshot(int taskId, Handler resultHandler, Callback callback) {
        if (taskId < 0 || callback == null) {
            return;
        }
        Handler workerHandler = getBackgroundHandler();
        if (workerHandler == null) {
            deliver(resultHandler, callback, taskId, null);
            return;
        }
        workerHandler.post(() -> deliver(resultHandler, callback, taskId, readSnapshot(taskId)));
    }

    private Bitmap readSnapshot(int taskId) {
        try {
            ClassLoader loader = context != null && context.getClassLoader() != null
                    ? context.getClassLoader()
                    : MBackTaskSnapshotProvider.class.getClassLoader();
            Class<?> wrapperClass = Class.forName(
                    "com.android.systemui.shared.system.ActivityManagerWrapper",
                    false,
                    loader);
            Method getInstanceMethod = wrapperClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            Object wrapper = getInstanceMethod.invoke(null);
            if (wrapper == null) {
                return null;
            }
            Method getTaskThumbnailMethod =
                    wrapperClass.getDeclaredMethod("getTaskThumbnail", int.class, boolean.class);
            getTaskThumbnailMethod.setAccessible(true);
            Object thumbnailData = getTaskThumbnailMethod.invoke(wrapper, taskId, false);
            if (thumbnailData == null) {
                return null;
            }
            Method getThumbnailMethod = thumbnailData.getClass().getDeclaredMethod("getThumbnail");
            getThumbnailMethod.setAccessible(true);
            Object bitmap = getThumbnailMethod.invoke(thumbnailData);
            return bitmap instanceof Bitmap ? (Bitmap) bitmap : null;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    "Failed to read mBack task snapshot: " + taskId,
                    t);
            return null;
        }
    }

    private static Handler getBackgroundHandler() {
        synchronized (BACKGROUND_LOCK) {
            if (backgroundHandler != null) {
                return backgroundHandler;
            }
            try {
                HandlerThread thread = new HandlerThread("MBackTaskSnapshots");
                thread.start();
                backgroundHandler = new Handler(thread.getLooper());
            } catch (Throwable t) {
                FlymeStatusBarSizer.logMBackWarning("Failed to start mBack task snapshot worker", t);
                backgroundHandler = null;
            }
            return backgroundHandler;
        }
    }

    private static void deliver(
            Handler resultHandler,
            Callback callback,
            int taskId,
            Bitmap bitmap) {
        if (resultHandler == null) {
            callback.onSnapshot(taskId, bitmap);
            return;
        }
        resultHandler.post(() -> callback.onSnapshot(taskId, bitmap));
    }
}
