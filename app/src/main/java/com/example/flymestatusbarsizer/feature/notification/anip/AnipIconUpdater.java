package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.Context;

import com.example.flymestatusbarsizer.config.SettingsStore;

import java.io.File;

/**
 * Ties the release check, the download and the local bundle together.
 *
 * <p>Intended to be driven from the settings app on a background thread. Everything degrades to the
 * icons packaged in the APK: an unreachable host, an invalid manifest or an unreadable download all
 * leave the previous catalog in place rather than blanking the notification icons.
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
        boolean newer = AnipReleaseClient.isNewer(release, installed);
        String message = newer
                ? "发现新版本 " + release.tag
                : "图标库已是最新" + (installed > 0L ? "" : "（尚未下载）");
        return new CheckResult(true, newer, installed, release, message);
    }

    /** Guards against two threads starting the first-use download at the same time. */
    private static final java.util.concurrent.atomic.AtomicBoolean AUTO_DOWNLOAD_STARTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Fetches and installs the bundle on a background thread, once per process.
     *
     * <p>Nothing is shipped in the APK, so the first use of the feature has to fetch the catalog.
     * Called from the hook, which runs on SystemUI's main thread, so the work is handed to a worker
     * and the caller keeps its desktop-icon behaviour until the catalog appears.
     *
     * @param context a context from the calling process; each process keeps its own copy.
     */
    public static void ensureInstalledAsync(Context context) {
        if (context == null || AnipBundleStore.resolve(context) != null) {
            return;
        }
        if (!AUTO_DOWNLOAD_STARTED.compareAndSet(false, true)) {
            return;
        }
        final Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        Thread worker = new Thread(() -> {
            try {
                CheckResult result = check(appContext);
                if (result.success && result.updateAvailable && result.release != null) {
                    download(appContext, result.release);
                }
            } catch (Throwable ignored) {
                // Leaving the catalog empty is safe: icons fall back to the desktop icon.
            } finally {
                // Let a later attempt retry if this one failed.
                AUTO_DOWNLOAD_STARTED.set(false);
            }
        }, "anip-first-download");
        worker.setDaemon(true);
        worker.start();
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
        File staging = new File(context.getCacheDir(), "anip-download.zip");
        //noinspection ResultOfMethodCallIgnored
        staging.delete();
        boolean downloaded;
        try {
            downloaded = AnipReleaseClient.download(
                    sourceType(context), repository(context), baseUrl(context), release, staging);
        } catch (Throwable ignored) {
            downloaded = false;
        }
        if (!downloaded) {
            //noinspection ResultOfMethodCallIgnored
            staging.delete();
            return false;
        }
        AnipBundleStore.Installed installed = AnipBundleStore.install(
                context, staging, release.tag, release.timestamp);
        //noinspection ResultOfMethodCallIgnored
        staging.delete();
        if (installed != null) {
            // Raises the cross-process config notification, which is what makes every hooked process
            // drop its cached bundle path and re-read the new catalog. No new IPC mechanism is needed.
            SettingsStore.notifyChanged(context);
        }
        return installed != null;
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
