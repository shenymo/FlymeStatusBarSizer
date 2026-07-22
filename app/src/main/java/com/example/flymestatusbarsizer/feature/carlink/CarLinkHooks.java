package com.example.flymestatusbarsizer.feature.carlink;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CarLinkHooks {
    private static final String CARLINK_PACKAGE = "com.upuphone.carlink";
    private static final String MODULE_PACKAGE = "com.fiyme.statusbarsizer";
    private static final Set<String> EXPANDED_PACKAGES = new LinkedHashSet<>();

    private CarLinkHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> appListClass = Class.forName(
                    "com.upuphone.carlink.appgrid.ext.AppListExtKt", false, loader);
            Class<?> appItemClass = Class.forName(
                    "com.upuphone.carlink.appdata.LaunchAppItemInfo", false, loader);
            Class<?> appItemExtClass = Class.forName(
                    "com.upuphone.carlink.appdata.AppItemInfoKt", false, loader);
            Method getAllSupportApp = appListClass.getDeclaredMethod(
                    "getAllSupportApp", Context.class);
            Method getAppPackageList = appListClass.getDeclaredMethod(
                    "getAppPackageList", Context.class);
            Method getSortInstalledApp = appListClass.getDeclaredMethod(
                    "getSortInstalledApp", String.class, Context.class);
            Method getSortPckList = appListClass.getDeclaredMethod(
                    "getSortPckList", String.class);
            Method pkgToAppItemInfo = appItemExtClass.getDeclaredMethod(
                    "pkgToAppItemInfo", String.class, int.class);
            Method getPackageName = appItemClass.getMethod("getPackageName");
            Method setExperience = appItemClass.getMethod("setExperience", Boolean.class);
            Method setHidden = appItemClass.getMethod("setHidden", Boolean.class);
            Method setSystemApp = appItemClass.getMethod("setSystemApp", Boolean.class);
            getAllSupportApp.setAccessible(true);
            getAppPackageList.setAccessible(true);
            getSortInstalledApp.setAccessible(true);
            getSortPckList.setAccessible(true);
            pkgToAppItemInfo.setAccessible(true);

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
                            pkgToAppItemInfo,
                            getPackageName,
                            setExperience,
                            setHidden,
                            setSystemApp);
                } catch (Throwable t) {
                    synchronized (EXPANDED_PACKAGES) {
                        EXPANDED_PACKAGES.clear();
                    }
                    FlymeStatusBarSizer.logCarLinkWarning(
                            "Failed to expand CarLink app list", t);
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

    private static List<Object> expandAppList(
            Context context,
            List<?> original,
            Class<?> appItemClass,
            Method pkgToAppItemInfo,
            Method getPackageName,
            Method setExperience,
            Method setHidden,
            Method setSystemApp) throws Exception {
        ArrayList<Object> result = new ArrayList<>(original.size());
        Set<String> packages = new LinkedHashSet<>();
        for (Object item : original) {
            result.add(item);
            if (appItemClass.isInstance(item)) {
                Object packageName = getPackageName.invoke(item);
                if (packageName instanceof String) {
                    packages.add((String) packageName);
                }
            }
        }

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager packageManager = context.getPackageManager();
        Set<String> expandedPackages = new LinkedHashSet<>();
        for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(launcherIntent, 0)) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            ApplicationInfo applicationInfo = activityInfo == null ? null : activityInfo.applicationInfo;
            String packageName = applicationInfo == null ? null : applicationInfo.packageName;
            if (packageName == null
                    || !applicationInfo.enabled
                    || (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || CARLINK_PACKAGE.equals(packageName)
                    || MODULE_PACKAGE.equals(packageName)
                    || !packages.add(packageName)) {
                continue;
            }
            Object item = pkgToAppItemInfo.invoke(null, packageName, 0);
            if (!appItemClass.isInstance(item)
                    || getPackageName.invoke(item) == null) {
                packages.remove(packageName);
                continue;
            }
            setExperience.invoke(item, Boolean.TRUE);
            setHidden.invoke(item, Boolean.FALSE);
            setSystemApp.invoke(item, Boolean.FALSE);
            result.add(item);
            expandedPackages.add(packageName);
        }
        synchronized (EXPANDED_PACKAGES) {
            EXPANDED_PACKAGES.clear();
            EXPANDED_PACKAGES.addAll(expandedPackages);
        }
        return result;
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
