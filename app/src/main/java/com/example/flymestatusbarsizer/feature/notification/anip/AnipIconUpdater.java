package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.Context;

import com.example.flymestatusbarsizer.config.SettingsStore;

import java.io.File;

/**
 * Ties the release check, the download and the local bundle together.
 *
 * <p>Driven from the settings app on a background thread. A failed download keeps the previous
 * catalog. Successful installs publish an exact release for SystemUI to reconcile independently.
 */
public final class AnipIconUpdater {
    private AnipIconUpdater() {
    }

    /** Outcome of a release check. */
    public static final class CheckResult {
        public final boolean success;
        public final boolean updateAvailable;
        public final long installedTimestamp;
        public final AnipReleaseClient.ReleaseInfo release;
        public final String message;

        CheckResult(boolean success, boolean updateAvailable, long installedTimestamp,
                AnipReleaseClient.ReleaseInfo release, String message) {
            this.success = success;
            this.updateAvailable = updateAvailable;
            this.installedTimestamp = installedTimestamp;
            this.release = release;
            this.message = message;
        }
    }

    /** Reads the configured source out of the persisted settings. */
    public static int sourceType(Context context) {
        return SettingsStore.readInt(SettingsStore.prefs(context),
                SettingsStore.KEY_ANIP_SOURCE_TYPE, SettingsStore.DEFAULT_ANIP_SOURCE_TYPE);
    }

    public static String repository(Context context) {
        return SettingsStore.readString(SettingsStore.prefs(context),
                SettingsStore.KEY_ANIP_REPOSITORY, SettingsStore.DEFAULT_ANIP_REPOSITORY);
    }

    public static String baseUrl(Context context) {
        return SettingsStore.readString(SettingsStore.prefs(context),
                SettingsStore.KEY_ANIP_BASE_URL, SettingsStore.DEFAULT_ANIP_BASE_URL);
    }

    /**
     * Queries the configured source and reports whether a newer bundle exists.
     *
     * <p>Also reports success when the published bundle is not newer, because that means the local
     * catalog is already current.
     */
    public static CheckResult check(Context context) {
        if (context == null) {
            return new CheckResult(false, false, 0L, null, "无法访问设置");
        }
        long installed = AnipBundleStore.installedTimestamp(context);
        AnipReleaseClient.ReleaseInfo release;
        try {
            release = AnipReleaseClient.fetchManifest(
                    sourceType(context), repository(context), baseUrl(context));
        } catch (Throwable ignored) {
            release = null;
        }
        if (release == null) {
            return new CheckResult(false, false, installed, null,
                    "无法获取图标库更新信息，请检查网络或更换更新来源");
        }
        // Repair synchronization for bundles installed before the desired-state protocol existed.
        synchronized (LOCAL_UPDATE_LOCK) {
            AnipBundleStore.Installed local = AnipBundleStore.resolve(context);
            if (local != null && local.tag.equals(release.tag) && local.timestamp == release.timestamp) {
                publishTarget(context, releaseTarget(context, release));
            }
        }
        boolean newer = AnipReleaseClient.isNewer(release, installed);
        String message = newer
                ? "发现新版本 " + release.tag
                : "图标库已是最新" + (installed > 0L ? "" : "（尚未下载）");
        return new CheckResult(true, newer, installed, release, message);
    }

    private static final Object LOCAL_UPDATE_LOCK = new Object();
    private static long localUpdateGeneration;

    private static String releaseTarget(Context context, AnipReleaseClient.ReleaseInfo release) {
        String url = AnipRemoteSource.bundleUrl(sourceType(context), repository(context),
                baseUrl(context), release.tag, release.assetName, release.downloadUrl);
        return AnipReleaseClient.encodeManifest(release, url);
    }

    /** Publish only after the matching files exist; local installation metadata stays private. */
    private static void publishTarget(Context context, String target) {
        SettingsStore.prefs(context).edit()
                .putString(SettingsStore.KEY_ANIP_BUNDLE_TARGET, target).apply();
        SettingsStore.notifyChanged(context);
    }

    public static void removeDownloadedBundle(Context context) {
        if (context == null) return;
        synchronized (LOCAL_UPDATE_LOCK) {
            localUpdateGeneration++;
            AnipBundleStore.clear(context);
            publishTarget(context, AnipBundleSync.REMOVED);
            reloadLibrary(context);
        }
    }

    /**
     * Downloads and installs the bundle described by {@code release}.
     *
     * @return whether a verified bundle is now installed.
     */
    public static boolean download(Context context, AnipReleaseClient.ReleaseInfo release) {
        if (context == null || release == null) {
            return false;
        }
        String previousTarget;
        long generation;
        synchronized (LOCAL_UPDATE_LOCK) {
            previousTarget = AnipBundleSync.target(SettingsStore.prefs(context));
            generation = localUpdateGeneration;
        }
        String target = releaseTarget(context, release);
        // Pin the source as well as the version while the download is in progress.
        AnipReleaseClient.ReleaseInfo pinned = AnipReleaseClient.parseManifest(target);
        if (pinned == null) return false;
        File staging;
        try {
            staging = File.createTempFile("anip-download-", ".zip", context.getCacheDir());
        } catch (java.io.IOException e) {
            return false;
        }
        boolean downloaded;
        try {
            downloaded = AnipReleaseClient.download(
                    sourceType(context), repository(context), baseUrl(context), pinned, staging);
        } catch (Throwable ignored) {
            downloaded = false;
        }
        if (!downloaded) {
            //noinspection ResultOfMethodCallIgnored
            staging.delete();
            return false;
        }
        try {
            synchronized (LOCAL_UPDATE_LOCK) {
                if (generation != localUpdateGeneration
                        || !previousTarget.equals(AnipBundleSync.target(SettingsStore.prefs(context)))) {
                    return false;
                }
                AnipBundleStore.Installed installed = AnipBundleStore.install(
                        context, staging, release.tag, release.timestamp);
                if (installed == null) return false;
                publishTarget(context, target);
                return true;
            }
        } finally {
            staging.delete();
        }
    }

    /**
     * Reloads the in-memory catalog from the installed bundle.
     *
     * <p>Nothing is shipped in the APK, so without an installed bundle the catalog is simply empty and
     * the notification hook keeps its desktop-icon behaviour.
     *
     * @return whether a usable catalog is loaded afterwards.
     */
    public static boolean reloadLibrary(Context context) {
        AnipBundleStore.Installed installed = AnipBundleStore.resolve(context);
        AnipIconLibrary library = AnipIconLibrary.get();
        library.clearBitmapCache();
        library.invalidate();
        return library.load(installed == null ? null : installed.directory);
    }

    /** Human readable description of where the current catalog comes from. */
    public static String describeInstalled(Context context) {
        AnipBundleStore.Installed installed = AnipBundleStore.resolve(context);
        if (installed == null) {
            return "尚未下载";
        }
        return "版本 " + installed.tag;
    }
}
