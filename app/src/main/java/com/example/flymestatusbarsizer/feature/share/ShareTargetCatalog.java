package com.example.flymestatusbarsizer.feature.share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class ShareTargetCatalog {
    private ShareTargetCatalog() {}

    public static Snapshot load(Context context) {
        return load(context, ShareContentType.DEFAULT);
    }

    public static Snapshot load(Context context, ShareContentType type) {
        return new Session().load(context, type);
    }

    /** Shared only by one editor scan; an explicit rescan starts a fresh session. */
    static final class Session {
        private final BooleanSupplier cancelled;

        Session() { this(() -> false); }
        Session(BooleanSupplier cancelled) { this.cancelled = cancelled; }

        private void checkCancelled() {
            if (cancelled.getAsBoolean()) throw new CancellationException();
        }

        private final Map<MetadataKey, Target> metadata = new LinkedHashMap<>();
        private final Map<MetadataKey, String> labels = new LinkedHashMap<>();
        private final Map<String, String> appNames = new LinkedHashMap<>();
        private final Map<String, Drawable> appIcons = new LinkedHashMap<>();
        private final ShareTargetSystemOrder.Session systemOrder = new ShareTargetSystemOrder.Session();

        Snapshot load(Context context, ShareContentType type) {
            PackageManager pm = context.getPackageManager();
            Map<String, ResolveInfo> found = new LinkedHashMap<>();
            for (String action : new String[]{Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE}) {
                checkCancelled();
                Intent intent = new Intent(action).setType(type.mime);
                for (ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                    ActivityInfo ai = info.activityInfo;
                    if (ai == null || !ai.exported || !ai.enabled || ai.applicationInfo == null
                            || !ai.applicationInfo.enabled) continue;
                    String component = ShareTargetRules.normalizeComponent(ai.packageName + "/" + ai.name);
                    if (component.isEmpty() || found.containsKey(component)) continue;
                    found.put(component, info);
                }
            }
            checkCancelled();
            ShareTargetSystemOrder.Result ordered = systemOrder.load(context, new ArrayList<>(found.values()),
                    info -> label(pm, info));
            List<Target> result = new ArrayList<>();
            for (ResolveInfo info : ordered.targets) {
                checkCancelled();
                ActivityInfo ai = info.activityInfo;
                String component = ShareTargetRules.normalizeComponent(ai.packageName + "/" + ai.name);
                // ResolveInfo may override the Activity label/icon differently for each MIME.
                MetadataKey key = metadataKey(info);
                Target cached = metadata.get(key);
                if (cached == null) {
                    String appName = appNames.get(ai.packageName);
                    if (appName == null) {
                        appName = String.valueOf(ai.applicationInfo.loadLabel(pm));
                        appNames.put(ai.packageName, appName);
                    }
                    String label = familiarLabel(component, label(pm, info));
                    Drawable icon;
                    try { icon = info.loadIcon(pm); }
                    catch (RuntimeException ignored) { icon = pm.getDefaultActivityIcon(); }
                    Drawable appIcon = appIcons.get(ai.packageName);
                    if (appIcon == null) {
                        try { appIcon = ai.applicationInfo.loadIcon(pm); }
                        catch (RuntimeException ignored) { appIcon = icon; }
                        appIcons.put(ai.packageName, appIcon);
                    }
                    cached = new Target(component, label, appName, icon, appIcon);
                    metadata.put(key, cached);
                }
                Drawable icon = cloneDrawable(context, cached.icon);
                if (icon == null && cached.icon != null) {
                    try { icon = info.loadIcon(pm); }
                    catch (RuntimeException ignored) { icon = pm.getDefaultActivityIcon(); }
                }
                Drawable appIcon = cloneDrawable(context, cached.appIcon);
                if (appIcon == null) appIcon = icon;
                result.add(new Target(component, cached.label, cached.appName, icon, appIcon));
            }
            return new Snapshot(result, ordered.available);
        }

        private static Drawable cloneDrawable(Context context, Drawable source) {
            Drawable.ConstantState state = source == null ? null : source.getConstantState();
            // A Drawable owns a view callback; share its resources, not the mutable instance.
            return state == null ? null : state.newDrawable(context.getResources()).mutate();
        }

        private String label(PackageManager pm, ResolveInfo info) {
            checkCancelled();
            return labels.computeIfAbsent(metadataKey(info), key -> String.valueOf(info.loadLabel(pm)));
        }

        private MetadataKey metadataKey(ResolveInfo info) {
            return new MetadataKey(info.activityInfo.packageName + "/" + info.activityInfo.name,
                    info.resolvePackageName, info.labelRes,
                    info.nonLocalizedLabel == null ? null : info.nonLocalizedLabel.toString(), info.icon);
        }
    }

    private record MetadataKey(String component, String resolvePackage, int labelRes,
                               String label, int icon) {}

    private static String familiarLabel(String component, String fallback) {
        switch (component) {
            case "com.tencent.mm/com.tencent.mm.ui.tools.ShareImgUI": return "微信好友";
            case "com.tencent.mm/com.tencent.mm.ui.tools.ShareToTimeLineUI": return "朋友圈";
            case "com.tencent.mm/com.tencent.mm.ui.tools.AddFavoriteUI": return "微信收藏";
            case "com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity": return "QQ 好友";
            case "com.tencent.mobileqq/com.tencent.mobileqq.activity.qfileJumpActivity": return "我的电脑";
            default: return fallback;
        }
    }

    public static final class Snapshot {
        public final List<Target> targets;
        public final boolean systemOrderAvailable;

        Snapshot(List<Target> targets, boolean systemOrderAvailable) {
            this.targets = targets;
            this.systemOrderAvailable = systemOrderAvailable;
        }
    }

    public static final class Target {
        public final String component;
        public final String label;
        public final String appName;
        public final Drawable icon;
        public final Drawable appIcon;

        public Target(String component, String label, String appName, Drawable icon) {
            this(component, label, appName, icon, icon);
        }

        public Target(String component, String label, String appName, Drawable icon, Drawable appIcon) {
            this.component = component;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
            this.appIcon = appIcon;
        }
    }
}
