package com.example.flymestatusbarsizer.feature.resolver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.example.flymestatusbarsizer.feature.openwith.OpenWithContentType;
import com.example.flymestatusbarsizer.feature.share.ShareContentType;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class DeclaredIntentCatalog {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ACTIVITY = "activity";
    private static final String ACTIVITY_ALIAS = "activity-alias";
    private static final String INTENT_FILTER = "intent-filter";
    private static final String ACTION = "action";
    private static final String CATEGORY = "category";
    private static final String DATA = "data";

    private static final Object CACHE_LOCK = new Object();
    private static final boolean ASSET_MANAGER_API_AVAILABLE =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                    || HiddenApiBypass.addHiddenApiExemptions("Landroid/content/res/AssetManager;");
    private static volatile Snapshot cache;

    private DeclaredIntentCatalog() { }

    public static List<Entry> load(Context context, BooleanSupplier cancelled) {
        return load(context, cancelled, null);
    }

    public static List<Entry> load(Context context, BooleanSupplier cancelled,
            ProgressListener progress) {
        if (isCancelled(cancelled)) return Collections.emptyList();
        report(progress, 0, 0, "正在检查应用变化");
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(
                PackageManager.GET_ACTIVITIES | PackageManager.GET_DISABLED_COMPONENTS);
        if (isCancelled(cancelled)) return Collections.emptyList();
        long fingerprint = fingerprint(packageManager, packages, cancelled);
        if (isCancelled(cancelled)) return Collections.emptyList();
        Snapshot previous = cache;
        if (previous != null && previous.fingerprint == fingerprint) {
            report(progress, 1, 1, "已使用缓存入口");
            return previous.entries;
        }

        Map<String, Builder> builders = new LinkedHashMap<>();
        int packageCount = packages.size();
        int packageIndex = 0;
        report(progress, 0, packageCount, "正在扫描应用清单");
        for (PackageInfo packageInfo : packages) {
            if (isCancelled(cancelled)) return Collections.emptyList();
            if (packageInfo.packageName != null && packageInfo.applicationInfo != null
                    && packageInfo.applicationInfo.enabled) {
                parsePackage(packageManager, packageInfo, builders, cancelled);
            }
            report(progress, ++packageIndex, packageCount, "正在扫描应用清单");
        }
        List<Entry> result = new ArrayList<>();
        int entryCount = builders.size();
        int entryIndex = 0;
        report(progress, 0, entryCount, "正在读取入口信息");
        for (Builder builder : builders.values()) {
            if (isCancelled(cancelled)) return Collections.emptyList();
            Entry entry = createEntry(packageManager, builder);
            if (entry != null) result.add(entry);
            report(progress, ++entryIndex, entryCount, "正在读取入口信息");
        }
        if (isCancelled(cancelled)) return Collections.emptyList();
        List<Entry> immutable = Collections.unmodifiableList(result);
        synchronized (CACHE_LOCK) {
            Snapshot current = cache;
            if (current == null || current.fingerprint != fingerprint) {
                cache = new Snapshot(fingerprint, immutable);
            }
            return cache.entries;
        }
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }

    private static void report(ProgressListener progress, int current, int total, String phase) {
        if (progress != null) progress.onProgress(current, total, phase);
    }

    private static long fingerprint(
            PackageManager packageManager, List<PackageInfo> packages, BooleanSupplier cancelled) {
        long result = packages.size();
        for (PackageInfo packageInfo : packages) {
            if (isCancelled(cancelled)) return 0L;
            result = 31 * result + String.valueOf(packageInfo.packageName).hashCode();
            result = 31 * result + packageInfo.lastUpdateTime;
            result = 31 * result + (packageInfo.applicationInfo != null
                    && packageInfo.applicationInfo.enabled ? 1 : 0);
            if (packageInfo.activities == null) continue;
            for (ActivityInfo activity : packageInfo.activities) {
                result = 31 * result + String.valueOf(activity.name).hashCode();
                result = 31 * result + (activity.enabled ? 1 : 0);
                try {
                    result = 31 * result + packageManager.getComponentEnabledSetting(
                            new ComponentName(packageInfo.packageName, activity.name));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    private static void parsePackage(PackageManager packageManager, PackageInfo packageInfo,
            Map<String, Builder> builders, BooleanSupplier cancelled) {
        parseManifest(packageInfo, packageInfo.applicationInfo.sourceDir, builders, cancelled);
        String[] splitSourceDirs = packageInfo.applicationInfo.splitSourceDirs;
        if (splitSourceDirs != null) {
            for (String splitSourceDir : splitSourceDirs) {
                if (cancelled.getAsBoolean()) return;
                parseManifest(packageInfo, splitSourceDir, builders, cancelled);
            }
        }
    }

    private static void parseManifest(PackageInfo packageInfo, String sourcePath,
            Map<String, Builder> builders, BooleanSupplier cancelled) {
        XmlResourceParser parser = null;
        AssetManager assets = null;
        try {
            if (!ASSET_MANAGER_API_AVAILABLE) return;
            assets = (AssetManager) AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(assets, sourcePath);
            if (cookie == 0) return;
            parser = assets.openXmlResourceParser(cookie, "AndroidManifest.xml");
            Builder current = null;
            Filter currentFilter = null;
            int activityDepth = -1;
            int event;
            while ((event = parser.getEventType()) != XmlPullParser.END_DOCUMENT) {
                if (cancelled.getAsBoolean()) return;
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (current == null && (ACTIVITY.equals(tag) || ACTIVITY_ALIAS.equals(tag))) {
                        String name = className(packageInfo.packageName,
                                parser.getAttributeValue(ANDROID_NS, "name"));
                        if (name != null) {
                            String component = packageInfo.packageName + "/" + name;
                            current = builders.computeIfAbsent(component,
                                    key -> new Builder(packageInfo.packageName, name));
                            activityDepth = parser.getDepth();
                        }
                    } else if (current != null && currentFilter == null
                            && INTENT_FILTER.equals(tag) && parser.getDepth() == activityDepth + 1) {
                        currentFilter = new Filter();
                    } else if (currentFilter != null) {
                        if (ACTION.equals(tag)) {
                            currentFilter.actions.add(parser.getAttributeValue(ANDROID_NS, "name"));
                        } else if (CATEGORY.equals(tag)) {
                            currentFilter.categories.add(parser.getAttributeValue(ANDROID_NS, "name"));
                        } else if (DATA.equals(tag)) {
                            currentFilter.schemes.add(parser.getAttributeValue(ANDROID_NS, "scheme"));
                            currentFilter.mimeTypes.add(parser.getAttributeValue(ANDROID_NS, "mimeType"));
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if (INTENT_FILTER.equals(tag) && currentFilter != null) {
                        addFilter(current, currentFilter);
                        currentFilter = null;
                    } else if ((ACTIVITY.equals(tag) || ACTIVITY_ALIAS.equals(tag))
                            && current != null && parser.getDepth() == activityDepth) {
                        current = null;
                        activityDepth = -1;
                    }
                }
                parser.next();
            }
        } catch (Exception ignored) {
            // A malformed or inaccessible package is still covered by the normal query scan.
        } finally {
            if (parser != null) parser.close();
            if (assets != null) assets.close();
        }
    }

    private static void addFilter(Builder builder, Filter filter) {
        if (builder == null || !filter.categories.contains(Intent.CATEGORY_DEFAULT)) return;
        if (filter.actions.contains(Intent.ACTION_SEND)
                || filter.actions.contains(Intent.ACTION_SEND_MULTIPLE)) {
            addShareTypes(builder.shareTypes, filter.mimeTypes);
        }
        if (filter.actions.contains(Intent.ACTION_VIEW) || filter.actions.contains(Intent.ACTION_EDIT)) {
            addOpenTypes(builder.openTypes, filter.mimeTypes, filter.schemes,
                    filter.categories.contains(Intent.CATEGORY_BROWSABLE));
        }
    }

    private static void addShareTypes(Set<String> types, Set<String> mimeTypes) {
        if (mimeTypes.isEmpty()) {
            types.add(ShareContentType.DEFAULT.name());
            return;
        }
        for (String mime : mimeTypes) {
            if (mime == null || mime.isEmpty() || "*/*".equals(mime)) {
                types.add(ShareContentType.DEFAULT.name());
                types.add(ShareContentType.IMAGE.name());
                types.add(ShareContentType.VIDEO.name());
                types.add(ShareContentType.TEXT.name());
                types.add(ShareContentType.PDF.name());
                types.add(ShareContentType.AUDIO.name());
                continue;
            }
            String value = mime.toLowerCase().split(";", 2)[0].trim();
            if (value.startsWith("image/")) types.add(ShareContentType.IMAGE.name());
            else if (value.startsWith("video/")) types.add(ShareContentType.VIDEO.name());
            else if (value.startsWith("text/")) types.add(ShareContentType.TEXT.name());
            else if (value.startsWith("application/")) {
                if ("application/pdf".equals(value)) types.add(ShareContentType.PDF.name());
                else types.add(ShareContentType.DEFAULT.name());
            }
            else if (value.startsWith("audio/")) types.add(ShareContentType.AUDIO.name());
            else types.add(ShareContentType.DEFAULT.name());
        }
    }

    private static void addOpenTypes(Set<String> types, Set<String> mimeTypes, Set<String> schemes,
            boolean browsable) {
        if (mimeTypes.isEmpty()) types.add(OpenWithContentType.DEFAULT.name());
        boolean web = browsable && (schemes.contains("http") || schemes.contains("https"));
        if (web) types.add(OpenWithContentType.WEB.name());
        if (mimeTypes.isEmpty()) return;
        for (String mime : mimeTypes) {
            if (mime == null || mime.isEmpty() || "*/*".equals(mime)) {
                types.add(OpenWithContentType.DEFAULT.name());
                types.add(OpenWithContentType.IMAGE.name());
                types.add(OpenWithContentType.VIDEO.name());
                types.add(OpenWithContentType.TEXT.name());
                types.add(OpenWithContentType.PDF.name());
                types.add(OpenWithContentType.AUDIO.name());
                types.add(OpenWithContentType.APK.name());
                types.add(OpenWithContentType.ARCHIVE.name());
                types.add(OpenWithContentType.OTHER.name());
                continue;
            }
            if ("application/*".equals(mime)) {
                types.add(OpenWithContentType.PDF.name());
                types.add(OpenWithContentType.APK.name());
                types.add(OpenWithContentType.ARCHIVE.name());
                types.add(OpenWithContentType.OTHER.name());
                continue;
            }
            String value = mime.toLowerCase().split(";", 2)[0].trim();
            if ("text/html".equals(value) || "application/xhtml+xml".equals(value)) {
                types.add(OpenWithContentType.WEB.name());
            } else if (value.startsWith("image/")) {
                types.add(OpenWithContentType.IMAGE.name());
            } else if (value.startsWith("video/")) {
                types.add(OpenWithContentType.VIDEO.name());
            } else if (value.startsWith("text/")) {
                types.add(OpenWithContentType.TEXT.name());
            } else if ("application/pdf".equals(value)) {
                types.add(OpenWithContentType.PDF.name());
            } else if (value.startsWith("audio/")) {
                types.add(OpenWithContentType.AUDIO.name());
            } else if ("application/vnd.android.package-archive".equals(value)) {
                types.add(OpenWithContentType.APK.name());
            } else if ("application/zip".equals(value)
                    || "application/x-7z-compressed".equals(value)
                    || "application/x-rar-compressed".equals(value)) {
                types.add(OpenWithContentType.ARCHIVE.name());
            } else {
                types.add(OpenWithContentType.OTHER.name());
            }
        }
    }

    private static Entry createEntry(PackageManager packageManager, Builder builder) {
        try {
            ActivityInfo activity = packageManager.getActivityInfo(
                    new ComponentName(builder.packageName, builder.className),
                    PackageManager.GET_DISABLED_COMPONENTS);
            if (!activity.exported || !activity.enabled || activity.applicationInfo == null
                    || !activity.applicationInfo.enabled) return null;
            Drawable icon;
            Drawable appIcon;
            try { icon = activity.loadIcon(packageManager); }
            catch (RuntimeException ignored) { icon = packageManager.getDefaultActivityIcon(); }
            try { appIcon = activity.applicationInfo.loadIcon(packageManager); }
            catch (RuntimeException ignored) { appIcon = icon; }
            String appName = String.valueOf(activity.applicationInfo.loadLabel(packageManager));
            String label = String.valueOf(activity.loadLabel(packageManager));
            return new Entry(builder.packageName + "/" + builder.className, label, appName,
                    icon, appIcon, builder.shareTypes, builder.openTypes, activity);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static String className(String packageName, String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.charAt(0) == '.') return packageName + value;
        return value.indexOf('.') < 0 ? packageName + "." + value : value;
    }

    private static final class Snapshot {
        final long fingerprint;
        final List<Entry> entries;

        Snapshot(long fingerprint, List<Entry> entries) {
            this.fingerprint = fingerprint;
            this.entries = entries;
        }
    }

    private static final class Builder {
        final String packageName;
        final String className;
        final Set<String> shareTypes = new LinkedHashSet<>();
        final Set<String> openTypes = new LinkedHashSet<>();

        Builder(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }
    }

    private static final class Filter {
        final Set<String> actions = new LinkedHashSet<>();
        final Set<String> categories = new LinkedHashSet<>();
        final Set<String> schemes = new LinkedHashSet<>();
        final Set<String> mimeTypes = new LinkedHashSet<>();
    }

    public static final class Entry {
        public final String component;
        public final String label;
        public final String appName;
        public final Drawable icon;
        public final Drawable appIcon;
        public final Set<String> shareTypes;
        public final Set<String> openTypes;
        public final ActivityInfo activityInfo;

        Entry(String component, String label, String appName, Drawable icon, Drawable appIcon,
                Set<String> shareTypes, Set<String> openTypes, ActivityInfo activityInfo) {
            this.component = component;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
            this.appIcon = appIcon;
            this.shareTypes = Collections.unmodifiableSet(new LinkedHashSet<>(shareTypes));
            this.openTypes = Collections.unmodifiableSet(new LinkedHashSet<>(openTypes));
            this.activityInfo = activityInfo;
        }
    }

    public interface ProgressListener {
        void onProgress(int current, int total, String phase);
    }
}
