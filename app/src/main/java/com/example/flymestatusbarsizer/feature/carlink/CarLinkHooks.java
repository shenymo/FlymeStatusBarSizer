package com.example.flymestatusbarsizer.feature.carlink;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Base64;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CarLinkHooks {
    private static final String CARLINK_PACKAGE = "com.upuphone.carlink";
    private static final String MODULE_PACKAGE = "com.fiyme.statusbarsizer";
    private static final String NETEASE_PACKAGE = "com.netease.cloudmusic";
    private static final long NETEASE_LOAD_TIMEOUT_MS = 20_000L;
    private static final String DATA_IMAGE_PREFIX = "data:image";
    private static final String RESOURCE_URI_PREFIX = "android.resource://";
    private static final Set<String> EXPANDED_PACKAGES = new LinkedHashSet<>();
    private static List<Object> cachedExpandedApps;
    private static List<Object> cachedInstalledApps;
    private static Context carLinkContext;

    private CarLinkHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        installStabilityFixes(module, loader);
        installNeteaseColdStartFix(module, loader);
        try {
            Class<?> appListClass = Class.forName(
                    "com.upuphone.carlink.appgrid.ext.AppListExtKt", false, loader);
            Class<?> appItemClass = Class.forName(
                    "com.upuphone.carlink.appdata.LaunchAppItemInfo", false, loader);
            Class<?> commUtilsClass = Class.forName(
                    "com.upuphone.carlink.utils.CommUtilsKt", false, loader);
            Method getAllSupportApp = appListClass.getDeclaredMethod(
                    "getAllSupportApp", Context.class);
            Method getAppPackageList = appListClass.getDeclaredMethod(
                    "getAppPackageList", Context.class);
            Method getInstalledApp = appListClass.getDeclaredMethod(
                    "getInstalledApp", Context.class);
            Method getSortInstalledApp = appListClass.getDeclaredMethod(
                    "getSortInstalledApp", String.class, Context.class);
            Method getSortPckList = appListClass.getDeclaredMethod(
                    "getSortPckList", String.class);
            Method getAssetsUri = commUtilsClass.getDeclaredMethod(
                    "getAssetsUri", String.class);
            Constructor<?> appItemConstructor = appItemClass.getConstructor();
            Method getPackageName = appItemClass.getMethod("getPackageName");
            Method setPackageName = appItemClass.getMethod("setPackageName", String.class);
            Method setAppName = appItemClass.getMethod("setAppName", String.class);
            Method setAppType = appItemClass.getMethod("setAppType", int.class);
            Method getIconUrl = appItemClass.getMethod("getIconUrl");
            Method setIconUrl = appItemClass.getMethod("setIconUrl", String.class);
            Method setExperience = appItemClass.getMethod("setExperience", Boolean.class);
            Method setHidden = appItemClass.getMethod("setHidden", Boolean.class);
            Method setSystemApp = appItemClass.getMethod("setSystemApp", Boolean.class);
            getAllSupportApp.setAccessible(true);
            getAppPackageList.setAccessible(true);
            getInstalledApp.setAccessible(true);
            getSortInstalledApp.setAccessible(true);
            getSortPckList.setAccessible(true);
            getAssetsUri.setAccessible(true);

            module.intercept(getAssetsUri, chain -> {
                Object fileName = chain.getArg(0);
                if (FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()
                        && fileName instanceof String
                        && isDirectImageUri((String) fileName)) {
                    return fileName;
                }
                return chain.proceed();
            });

            module.intercept(getIconUrl, chain -> {
                Object original = chain.proceed();
                if (!FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()
                        || (original instanceof String
                                && ((String) original).startsWith(DATA_IMAGE_PREFIX))) {
                    return original;
                }
                try {
                    Object packageName = getPackageName.invoke(chain.getThisObject());
                    if (!(packageName instanceof String)
                            || !isExpandedPackage((String) packageName)
                            || carLinkContext == null) {
                        return original;
                    }
                    String iconUrl = buildAppIconDataUrl(
                            carLinkContext.getPackageManager(), (String) packageName);
                    setIconUrl.invoke(chain.getThisObject(), iconUrl);
                    return iconUrl;
                } catch (Throwable ignored) {
                    return original;
                }
            });

            module.intercept(getAllSupportApp, chain -> {
                Object original = chain.proceed();
                if (!FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()
                        || !(original instanceof List)
                        || !(chain.getArg(0) instanceof Context)) {
                    return original;
                }
                try {
                    return expandAppList(
                            (Context) chain.getArg(0),
                            (List<?>) original,
                            appItemClass,
                            appItemConstructor,
                            getPackageName,
                            setPackageName,
                            setAppName,
                            setAppType,
                            setIconUrl,
                            setExperience,
                            setHidden,
                            setSystemApp);
                } catch (Throwable t) {
                    synchronized (EXPANDED_PACKAGES) {
                        EXPANDED_PACKAGES.clear();
                        cachedExpandedApps = null;
                        cachedInstalledApps = null;
                    }
                    FlymeStatusBarSizer.logCarLinkWarning(
                            "Failed to expand CarLink app list", t);
                    return original;
                }
            });
            module.intercept(getInstalledApp, chain -> {
                if (!FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()) {
                    return chain.proceed();
                }
                synchronized (EXPANDED_PACKAGES) {
                    if (cachedInstalledApps != null) {
                        return new ArrayList<>(cachedInstalledApps);
                    }
                    Object original = chain.proceed();
                    if (original instanceof List) {
                        cachedInstalledApps = new ArrayList<>((List<?>) original);
                    }
                    return original;
                }
            });
            module.intercept(getAppPackageList, chain -> {
                Object original = chain.proceed();
                if (!FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()
                        || !(original instanceof List)) {
                    return original;
                }
                return removeExpandedPackages((List<?>) original);
            });
            module.intercept(getSortInstalledApp, chain -> {
                Object original = chain.proceed();
                if (!FlymeStatusBarSizer.isCarLinkExpandAppsEnabled()
                        || !(original instanceof List)) {
                    return original;
                }
                String carId = chain.getArg(0) instanceof String
                        ? (String) chain.getArg(0) : "";
                Object selected = getSortPckList.invoke(null, carId);
                return selected instanceof List
                        ? keepSelectedApps(
                                (List<?>) original,
                                (List<?>) selected,
                                appItemClass,
                                getPackageName)
                        : original;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to hook CarLink app list", t);
        }
    }

    private static void installStabilityFixes(
            FlymeStatusBarSizer module, ClassLoader loader) {
        installDayNightIsolation(module, loader);
        installPeriodicRedrawSuppression(module, loader);
        installTouchLogFilter(module, loader);
        installFusionTaskListenerCleanup(module, loader);
    }

    private static void installDayNightIsolation(
            FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isCarLinkDayNightIsolationEnabled()) {
            return;
        }
        try {
            Class<?> connectionManagerClass = Class.forName(
                    "com.upuphone.carlink.application.ICCOAConnectManager", false, loader);
            Method handleMessage = connectionManagerClass.getDeclaredMethod(
                    "handleMessage", byte[].class, byte[].class, int.class);
            handleMessage.setAccessible(true);
            module.intercept(handleMessage, chain -> {
                if (isDayNightCommand(chain.getArg(0))) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to hook CarLink day/night command", t);
        }
    }

    private static void installPeriodicRedrawSuppression(
            FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isCarLinkPeriodicRedrawDisabled()) {
            return;
        }
        try {
            Class<?> mainActivityClass = Class.forName(
                    "com.upuphone.carlink.MainActivity", false, loader);
            Class<?> carActionEventClass = Class.forName(
                    "com.upuphone.carlink.application.CarActionEvent", false, loader);
            Method onCarActionEvent = mainActivityClass.getDeclaredMethod(
                    "onCarActionEvent", carActionEventClass);
            onCarActionEvent.setAccessible(true);
            module.intercept(onCarActionEvent, chain -> null);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to suppress CarLink periodic redraw", t);
        }
    }

    private static void installTouchLogFilter(
            FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isCarLinkTouchLogFilterEnabled()) {
            return;
        }
        try {
            Class<?> logClass = Class.forName("s3.a", false, loader);
            Method debugLog = logClass.getDeclaredMethod("b", String.class, String.class);
            debugLog.setAccessible(true);
            module.intercept(debugLog, chain -> {
                Object message = chain.getArg(1);
                if (message instanceof String && isTouchDebugMessage((String) message)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to filter CarLink touch logs", t);
        }
    }

    private static void installFusionTaskListenerCleanup(
            FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isCarLinkTaskListenerCleanupEnabled()) {
            return;
        }
        try {
            Class<?> fusionActivityClass = Class.forName(
                    "com.upuphone.carlink.FusionMainActivity", false, loader);
            Method onDestroy = fusionActivityClass.getDeclaredMethod("onDestroy");
            Method getListener = fusionActivityClass.getDeclaredMethod("getListener");
            onDestroy.setAccessible(true);
            getListener.setAccessible(true);
            module.intercept(onDestroy, chain -> {
                Object result = chain.proceed();
                unregisterFusionTaskListener(loader, getListener, chain.getThisObject());
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to hook FusionMainActivity cleanup", t);
        }
    }

    private static void unregisterFusionTaskListener(
            ClassLoader loader, Method getListener, Object activity) {
        try {
            Object listener = getListener.invoke(activity);
            if (listener == null) {
                return;
            }
            Class<?> taskManagerClass = Class.forName(
                    "android.app.ActivityTaskManager", false, loader);
            Method getService = taskManagerClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object service = getService.invoke(null);
            Method unregister = service.getClass().getMethod(
                    "unregisterTaskStackListener", Class.forName(
                            "android.app.TaskStackListener", false, loader));
            unregister.setAccessible(true);
            unregister.invoke(service, listener);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to unregister Fusion task listener", t);
        }
    }

    private static boolean isTouchDebugMessage(String message) {
        return message.startsWith("event.x=")
                || message.startsWith("touch mAppContainerWidth=");
    }

    private static boolean isDayNightCommand(Object rawHeader) {
        if (!(rawHeader instanceof byte[])) {
            return false;
        }
        byte[] header = (byte[]) rawHeader;
        if (header.length < 20) {
            return false;
        }
        int command = ((header[14] & 255) << 8) | (header[15] & 255);
        return command == 15;
    }

    private static void installNeteaseColdStartFix(
            FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()) {
            return;
        }
        try {
            Class<?> musicManagerClass = Class.forName(
                    "com.upuphone.carlink.music.MusicManager", false, loader);
            Class<?> musicViewModelClass = Class.forName(
                    "com.upuphone.carlink.music.vm.MusicViewModel", false, loader);
            Class<?> systemExtClass = Class.forName(
                    "com.upuphone.carlink.utils.SystemExtKt", false, loader);
            Method bindMediaComponent = musicManagerClass.getDeclaredMethod("bindMediaComponent");
            Method retryBind = musicManagerClass.getDeclaredMethod("retryBind");
            Method requestRoot = musicViewModelClass.getDeclaredMethod("requestRoot", Context.class);
            Method refreshRoot = musicViewModelClass.getDeclaredMethod("refreshRoot", boolean.class);
            Method killSpecifiedApp = systemExtClass.getDeclaredMethod(
                    "killSpecifiedApp", String.class);
            Field handlerField = musicManagerClass.getDeclaredField("mHandler");
            Field initStateField = musicManagerClass.getDeclaredField("initState");
            Field retryCountField = musicManagerClass.getDeclaredField("retryCount");
            Field currentComponentField = musicManagerClass.getDeclaredField("currentComponent");
            Field mediaBrowserField = musicManagerClass.getDeclaredField("mMediaBrowser");
            Field viewModelHandlerField = musicViewModelClass.getDeclaredField("mHandler");
            Field currentRootStateField = musicViewModelClass.getDeclaredField("currentRootState");
            bindMediaComponent.setAccessible(true);
            retryBind.setAccessible(true);
            requestRoot.setAccessible(true);
            refreshRoot.setAccessible(true);
            killSpecifiedApp.setAccessible(true);
            handlerField.setAccessible(true);
            initStateField.setAccessible(true);
            retryCountField.setAccessible(true);
            currentComponentField.setAccessible(true);
            mediaBrowserField.setAccessible(true);
            viewModelHandlerField.setAccessible(true);
            currentRootStateField.setAccessible(true);

            module.intercept(bindMediaComponent, chain -> {
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && isCurrentNetease(currentComponentField)
                        && initStateField.getInt(null) == 0
                        && mediaBrowserField.get(null) == null) {
                    Object handler = handlerField.get(null);
                    if (handler instanceof Handler) {
                        ((Handler) handler).removeMessages(1);
                    }
                    initStateField.setInt(null, -1);
                }
                Object result = chain.proceed();
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && isCurrentNetease(currentComponentField)
                        && initStateField.getInt(null) == 0) {
                    Object handler = handlerField.get(null);
                    if (handler instanceof Handler) {
                        ((Handler) handler).removeMessages(1);
                        ((Handler) handler).sendEmptyMessageDelayed(1, NETEASE_LOAD_TIMEOUT_MS);
                    }
                }
                return result;
            });
            module.intercept(requestRoot, chain -> {
                Object result = chain.proceed();
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && isCurrentNetease(currentComponentField)
                        && currentRootStateField.getInt(chain.getThisObject()) == 0) {
                    Object handler = viewModelHandlerField.get(chain.getThisObject());
                    if (handler instanceof Handler) {
                        ((Handler) handler).removeMessages(3);
                        ((Handler) handler).sendEmptyMessageDelayed(
                                3, NETEASE_LOAD_TIMEOUT_MS);
                    }
                }
                return result;
            });
            module.intercept(refreshRoot, chain -> {
                Object result = chain.proceed();
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && isCurrentNetease(currentComponentField)
                        && currentRootStateField.getInt(chain.getThisObject()) == 0) {
                    Object handler = viewModelHandlerField.get(chain.getThisObject());
                    if (handler instanceof Handler) {
                        ((Handler) handler).removeMessages(3);
                        ((Handler) handler).sendEmptyMessageDelayed(
                                3, NETEASE_LOAD_TIMEOUT_MS);
                    }
                }
                return result;
            });
            module.intercept(retryBind, chain -> {
                int originalRetryCount = retryCountField.getInt(null);
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && isCurrentNetease(currentComponentField)
                        && originalRetryCount > 1) {
                    retryCountField.setInt(null, 1);
                }
                return chain.proceed();
            });
            module.intercept(killSpecifiedApp, chain -> {
                if (FlymeStatusBarSizer.isCarLinkNeteaseColdStartFixEnabled()
                        && NETEASE_PACKAGE.equals(chain.getArg(0))) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logCarLinkWarning(
                    "Failed to hook NetEase Cloud Music cold start", t);
        }
    }

    private static boolean isCurrentNetease(Field currentComponentField)
            throws IllegalAccessException {
        Object component = currentComponentField.get(null);
        return component instanceof ComponentName
                && NETEASE_PACKAGE.equals(((ComponentName) component).getPackageName());
    }

    private static boolean isExpandedPackage(String packageName) {
        synchronized (EXPANDED_PACKAGES) {
            return EXPANDED_PACKAGES.contains(packageName);
        }
    }

    private static boolean isDirectImageUri(String value) {
        return value.startsWith(RESOURCE_URI_PREFIX)
                || value.startsWith(DATA_IMAGE_PREFIX);
    }

    private static String buildAppIconDataUrl(
            PackageManager packageManager, String packageName) throws Exception {
        Drawable icon = packageManager.getApplicationIcon(packageName);
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        icon.draw(new Canvas(bitmap));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return "data:image/png;base64,"
                + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private static List<Object> expandAppList(
            Context context,
            List<?> original,
            Class<?> appItemClass,
            Constructor<?> appItemConstructor,
            Method getPackageName,
            Method setPackageName,
            Method setAppName,
            Method setAppType,
            Method setIconUrl,
            Method setExperience,
            Method setHidden,
            Method setSystemApp) throws Exception {
        Set<String> packages = new LinkedHashSet<>();
        for (Object item : original) {
            if (appItemClass.isInstance(item)) {
                Object packageName = getPackageName.invoke(item);
                if (packageName instanceof String) {
                    packages.add((String) packageName);
                }
            }
        }
        List<Object> expandedApps = getExpandedApps(
                context,
                packages,
                appItemConstructor,
                setPackageName,
                setAppName,
                setAppType,
                setIconUrl,
                setExperience,
                setHidden,
                setSystemApp);
        ArrayList<Object> result = new ArrayList<>(original.size() + expandedApps.size());
        result.addAll(original);
        for (Object item : expandedApps) {
            Object packageName = getPackageName.invoke(item);
            if (packageName instanceof String && packages.add((String) packageName)) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<Object> getExpandedApps(
            Context context,
            Set<String> originalPackages,
            Constructor<?> appItemConstructor,
            Method setPackageName,
            Method setAppName,
            Method setAppType,
            Method setIconUrl,
            Method setExperience,
            Method setHidden,
            Method setSystemApp) throws Exception {
        synchronized (EXPANDED_PACKAGES) {
            Context applicationContext = context.getApplicationContext();
            carLinkContext = applicationContext == null ? context : applicationContext;
            if (cachedExpandedApps != null) {
                return cachedExpandedApps;
            }

            ArrayList<Object> apps = new ArrayList<>();
            Set<String> expandedPackages = new LinkedHashSet<>();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PackageManager packageManager = context.getPackageManager();
            for (ResolveInfo resolveInfo
                    : packageManager.queryIntentActivities(launcherIntent, 0)) {
                ActivityInfo activityInfo = resolveInfo.activityInfo;
                ApplicationInfo applicationInfo = activityInfo == null
                        ? null : activityInfo.applicationInfo;
                String packageName = applicationInfo == null
                        ? null : applicationInfo.packageName;
                if (packageName == null
                        || !applicationInfo.enabled
                        || (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        || CARLINK_PACKAGE.equals(packageName)
                        || MODULE_PACKAGE.equals(packageName)
                        || originalPackages.contains(packageName)
                        || !expandedPackages.add(packageName)) {
                    continue;
                }
                Object item = appItemConstructor.newInstance();
                CharSequence label = resolveInfo.loadLabel(packageManager);
                int iconResource = activityInfo.getIconResource();
                if (iconResource == 0) {
                    iconResource = applicationInfo.icon;
                }
                setPackageName.invoke(item, packageName);
                setAppName.invoke(item, label == null ? packageName : label.toString());
                setAppType.invoke(item, 0);
                if (iconResource != 0) {
                    setIconUrl.invoke(item,
                            RESOURCE_URI_PREFIX + packageName + "/" + iconResource);
                }
                setExperience.invoke(item, Boolean.TRUE);
                setHidden.invoke(item, Boolean.FALSE);
                setSystemApp.invoke(item, Boolean.FALSE);
                apps.add(item);
            }
            EXPANDED_PACKAGES.clear();
            EXPANDED_PACKAGES.addAll(expandedPackages);
            cachedExpandedApps = apps;
            return apps;
        }
    }

    private static List<Object> removeExpandedPackages(List<?> packages) {
        ArrayList<Object> result = new ArrayList<>(packages.size());
        synchronized (EXPANDED_PACKAGES) {
            for (Object packageName : packages) {
                if (!(packageName instanceof String)
                        || !EXPANDED_PACKAGES.contains(packageName)) {
                    result.add(packageName);
                }
            }
        }
        return result;
    }

    private static List<Object> keepSelectedApps(
            List<?> apps,
            List<?> selectedPackages,
            Class<?> appItemClass,
            Method getPackageName) throws Exception {
        Set<Object> selected = new LinkedHashSet<>(selectedPackages);
        ArrayList<Object> result = new ArrayList<>(apps.size());
        for (Object app : apps) {
            if (!appItemClass.isInstance(app)
                    || selected.contains(getPackageName.invoke(app))) {
                result.add(app);
            }
        }
        return result;
    }
}
