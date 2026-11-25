package com.example.flymestatusbarsizer.feature.openwith;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.example.flymestatusbarsizer.feature.resolver.DeclaredIntentCatalog;
import com.example.flymestatusbarsizer.feature.share.ShareTargetRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class OpenWithTargetCatalog {
    private static final String CONTENT_URI = "content://com.example.flymestatusbarsizer/item";
    private static final String FILE_URI = "file:///sdcard/item";

    private OpenWithTargetCatalog() { }

    public static Snapshot load(Context context, OpenWithContentType type) {
        return load(context, type, null);
    }

    public static Snapshot load(Context context, OpenWithContentType type,
            DeclaredIntentCatalog.ProgressListener progress) {
        return load(context, type, () -> false, progress);
    }

    public static Snapshot load(Context context, OpenWithContentType type,
            BooleanSupplier cancelled, DeclaredIntentCatalog.ProgressListener progress) {
        checkCancelled(cancelled);
        report(progress, 0, 0, "正在查询打开方式");
        PackageManager packageManager = context.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String action : new String[]{Intent.ACTION_VIEW, Intent.ACTION_EDIT}) {
            for (Intent query : queries(action, type)) {
                checkCancelled(cancelled);
                for (ResolveInfo info : packageManager.queryIntentActivities(query,
                        PackageManager.MATCH_DEFAULT_ONLY)) {
                    ActivityInfo activity = info.activityInfo;
                    if (activity == null || !activity.exported || !activity.enabled
                            || activity.applicationInfo == null || !activity.applicationInfo.enabled) continue;
                    String component = ShareTargetRules.normalizeComponent(
                            activity.packageName + "/" + activity.name);
                    if (!component.isEmpty()) found.putIfAbsent(component, info);
                }
            }
        }
        List<Target> targets = new ArrayList<>();
        int total = found.size();
        int processed = 0;
        report(progress, 0, total, "正在加载打开方式");
        for (Map.Entry<String, ResolveInfo> entry : found.entrySet()) {
            checkCancelled(cancelled);
            ResolveInfo info = entry.getValue();
            ActivityInfo activity = info.activityInfo;
            Drawable icon;
            Drawable appIcon;
            try { icon = info.loadIcon(packageManager); }
            catch (RuntimeException ignored) { icon = packageManager.getDefaultActivityIcon(); }
            try { appIcon = activity.applicationInfo.loadIcon(packageManager); }
            catch (RuntimeException ignored) { appIcon = icon; }
            targets.add(new Target(entry.getKey(), String.valueOf(info.loadLabel(packageManager)),
                    String.valueOf(activity.applicationInfo.loadLabel(packageManager)), icon, appIcon));
            report(progress, ++processed, total, "正在加载打开方式");
        }
        checkCancelled(cancelled);
        Map<String, Target> declared = new LinkedHashMap<>();
        for (DeclaredIntentCatalog.Entry entry : DeclaredIntentCatalog.load(context, cancelled,
                progress)) {
            if (entry.openTypes.contains(type.name())) {
                declared.put(entry.component, new Target(entry.component, entry.label, entry.appName,
                        entry.icon, entry.appIcon));
            }
        }
        checkCancelled(cancelled);
        for (Target target : targets) declared.remove(target.component);
        targets.addAll(declared.values());
        return new Snapshot(targets, true);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) throw new CancellationException();
    }

    private static void report(
            DeclaredIntentCatalog.ProgressListener progress, int current, int total, String phase) {
        if (progress != null) progress.onProgress(current, total, phase);
    }

    private static List<Intent> queries(String action, OpenWithContentType type) {
        List<Intent> result = new ArrayList<>();
        if (type == OpenWithContentType.WEB) {
            result.add(new Intent(action, Uri.parse("https://example.com/"))
                    .addCategory(Intent.CATEGORY_BROWSABLE));
            return result;
        }
        String mime = type.mime;
        result.add(new Intent(action).setDataAndType(Uri.parse(CONTENT_URI), mime));
        result.add(new Intent(action).setDataAndType(Uri.parse(FILE_URI), mime));
        if (type == OpenWithContentType.DEFAULT) {
            result.add(new Intent(action, Uri.parse(CONTENT_URI)));
            result.add(new Intent(action, Uri.parse(FILE_URI)));
        }
        return result;
    }

    public static final class Snapshot {
        public final List<Target> targets;
        public final boolean available;

        Snapshot(List<Target> targets, boolean available) {
            this.targets = targets;
            this.available = available;
        }
    }

    public static final class Target {
        public final String component;
        public final String label;
        public final String appName;
        public final Drawable icon;
        public final Drawable appIcon;

        Target(String component, String label, String appName, Drawable icon, Drawable appIcon) {
            this.component = component;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
            this.appIcon = appIcon;
        }
    }
}
