package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class MBackStarAppProvider {
    private static final int LAUNCHER_ACTIVITY_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS;
    private static final Object BACKGROUND_LOCK = new Object();
    private static Handler backgroundHandler;
    private static MBackStarApp[] cachedApps = MBackStarApp.EMPTY_ARRAY;
    private static boolean preloadStarted;

    private final Context context;

    interface Callback {
        void onApps(MBackStarApp[] apps);
    }

    MBackStarAppProvider(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
    }

    static void preload(Context context) {
        if (context == null || preloadStarted) {
            return;
        }
        preloadStarted = true;
        Handler workerHandler = getBackgroundHandler();
        if (workerHandler == null) {
            return;
        }
        workerHandler.post(() -> {
            MBackStarApp[] apps = new MBackStarAppProvider(context).readApps();
            if (apps.length > 0) {
                cachedApps = apps;
            }
        });
    }

    void requestApps(Handler resultHandler, Callback callback) {
        if (callback == null) {
            return;
        }
        deliver(resultHandler, callback, cachedApps);
    }

    private MBackStarApp[] readApps() {
        if (context == null) {
            return MBackStarApp.EMPTY_ARRAY;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            return MBackStarApp.EMPTY_ARRAY;
        }
        try {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                    launcherIntent,
                    LAUNCHER_ACTIVITY_FLAGS);
            if (resolveInfos == null || resolveInfos.isEmpty()) {
                return MBackStarApp.EMPTY_ARRAY;
            }
            ArrayList<MBackStarApp> apps = new ArrayList<>(resolveInfos.size());
            HashSet<String> seenPackages = new HashSet<>();
            for (ResolveInfo resolveInfo : resolveInfos) {
                ActivityInfo activityInfo = resolveInfo != null ? resolveInfo.activityInfo : null;
                ComponentName component = activityInfo != null
                        ? new ComponentName(activityInfo.packageName, activityInfo.name)
                        : null;
                if (component == null) {
                    continue;
                }
                if (!seenPackages.add(component.getPackageName())) {
                    continue;
                }
                MBackStarApp app = buildApp(packageManager, activityInfo, component);
                if (app != null) {
                    apps.add(app);
                }
            }
            return apps.isEmpty() ? MBackStarApp.EMPTY_ARRAY : apps.toArray(new MBackStarApp[0]);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to read launcher apps for mBack star overlay", t);
            return MBackStarApp.EMPTY_ARRAY;
        }
    }

    private MBackStarApp buildApp(
            PackageManager packageManager,
            ActivityInfo activityInfo,
            ComponentName component) {
        if (packageManager == null || activityInfo == null || component == null) {
            return null;
        }
        try {
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
            return new MBackStarApp(
                    -1,
                    icon,
                    label,
                    component.getPackageName(),
                    component.getClassName());
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    "Failed to load launcher app for mBack star overlay: "
                            + component.flattenToShortString(),
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
