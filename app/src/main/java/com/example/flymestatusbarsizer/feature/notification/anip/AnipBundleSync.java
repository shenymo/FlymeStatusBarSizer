package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.SettingsStore;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Reconciles SystemUI's private files with the settings app's desired bundle. */
public final class AnipBundleSync {
    public static final String REMOVED = "removed";
    private static final long RETRY_SECONDS = 30;
    private static final AnipBundleSync INSTANCE = new AnipBundleSync(
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "anip-bundle-sync");
                thread.setDaemon(true);
                return thread;
            }), new Transport() {
                @Override public AnipReleaseClient.ReleaseInfo fetch(int type, String repo, String base) {
                    return AnipReleaseClient.fetchManifest(type, repo, base);
                }

                @Override public boolean download(int type, String repo, String base,
                        AnipReleaseClient.ReleaseInfo release, File destination) {
                    return AnipReleaseClient.download(type, repo, base, release, destination);
                }
            });

    interface Transport {
        AnipReleaseClient.ReleaseInfo fetch(int type, String repo, String base);
        boolean download(int type, String repo, String base,
                AnipReleaseClient.ReleaseInfo release, File destination);
    }

    private final ScheduledExecutorService worker;
    private final Transport transport;
    private Request requested;
    private ScheduledFuture<?> pending;

    AnipBundleSync(ScheduledExecutorService worker, Transport transport) {
        this.worker = worker;
        this.transport = transport;
    }

    public static AnipBundleSync get() {
        return INSTANCE;
    }

    public static String target(SharedPreferences prefs) {
        return prefs == null ? "" : SettingsStore.readString(
                prefs, SettingsStore.KEY_ANIP_BUNDLE_TARGET, "");
    }

    public static boolean isRemoved(SharedPreferences prefs) {
        return REMOVED.equals(target(prefs));
    }

    /** Called on configuration changes and first use, including when an older bundle exists. */
    public synchronized void request(Context context, SharedPreferences remote,
            boolean allowFirstDownload, Runnable onChanged) {
        // An unavailable service is not an instruction to erase files or use default sources.
        if (context == null || remote == null) {
            return;
        }
        Request next = new Request(context, remote, allowFirstDownload, onChanged);
        if (requested != null && requested.sameTarget(next)) {
            return;
        }
        requested = next;
        if (pending != null) {
            pending.cancel(false);
        }
        pending = worker.schedule(() -> reconcile(next), 0, TimeUnit.SECONDS);
    }

    private void reconcile(Request request) {
        File staging = null;
        boolean retry = false;
        boolean changed = false;
        try {
            synchronized (this) {
                if (!isCurrent(request)) return;
                if (REMOVED.equals(request.target)) {
                    AnipBundleStore.clear(request.context);
                    changed = true;
                    return;
                }
            }
            AnipBundleStore.Installed installed = AnipBundleStore.resolve(request.context);
            if (request.target.isEmpty()) {
                if (!request.allowFirstDownload || installed != null) return;
            } else if (installed != null && request.target.equals(SettingsStore.readString(
                    SettingsStore.prefs(request.context), SettingsStore.KEY_ANIP_LOCAL_BUNDLE_TARGET, ""))) {
                return;
            }
            // A published target contains the exact tag, digest, size and URL. Do not substitute
            // whatever happens to be latest when SystemUI gets around to downloading it.
            AnipReleaseClient.ReleaseInfo release = request.target.isEmpty()
                    ? transport.fetch(request.sourceType, request.repository, request.baseUrl)
                    : AnipReleaseClient.parseManifest(request.target);
            if (release == null) {
                retry = request.target.isEmpty();
                return;
            }
            staging = File.createTempFile("anip-sync-", ".zip", request.context.getCacheDir());
            if (!transport.download(request.sourceType, request.repository, request.baseUrl,
                    release, staging)) {
                retry = true;
                return;
            }
            synchronized (this) {
                // Check the live preferences as well as the queued request: config callbacks are
                // debounced, and a deletion may already be visible while its callback is pending.
                if (!isCurrent(request)) return;
                if (AnipBundleStore.install(request.context, staging, release.tag,
                        release.timestamp) == null) {
                    retry = true;
                    return;
                }
                SettingsStore.prefs(request.context).edit()
                        .putString(SettingsStore.KEY_ANIP_LOCAL_BUNDLE_TARGET, request.target).apply();
                changed = true;
            }
        } catch (Exception ignored) {
            // Keep the previous bundle while offline or after a failed verification/extraction.
            retry = true;
        } finally {
            if (staging != null) staging.delete();
            synchronized (this) {
                if (retry && isCurrent(request)) {
                    pending = worker.schedule(() -> reconcile(request), RETRY_SECONDS, TimeUnit.SECONDS);
                }
            }
            if (changed && request.onChanged != null) request.onChanged.run();
        }
    }

    private boolean isCurrent(Request request) {
        return requested == request && request.target.equals(target(request.remote));
    }

    private static final class Request {
        final Context context;
        final SharedPreferences remote;
        final boolean allowFirstDownload;
        final Runnable onChanged;
        final String target;
        final int sourceType;
        final String repository;
        final String baseUrl;

        Request(Context context, SharedPreferences remote, boolean allowFirstDownload, Runnable onChanged) {
            this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            this.remote = remote;
            this.allowFirstDownload = allowFirstDownload;
            this.onChanged = onChanged;
            target = target(remote);
            sourceType = SettingsStore.readInt(remote, SettingsStore.KEY_ANIP_SOURCE_TYPE,
                    SettingsStore.DEFAULT_ANIP_SOURCE_TYPE);
            repository = SettingsStore.readString(remote, SettingsStore.KEY_ANIP_REPOSITORY,
                    SettingsStore.DEFAULT_ANIP_REPOSITORY);
            baseUrl = SettingsStore.readString(remote, SettingsStore.KEY_ANIP_BASE_URL,
                    SettingsStore.DEFAULT_ANIP_BASE_URL);
        }

        boolean sameTarget(Request other) {
            return remote == other.remote && target.equals(other.target)
                    && allowFirstDownload == other.allowFirstDownload && sourceType == other.sourceType
                    && repository.equals(other.repository) && baseUrl.equals(other.baseUrl);
        }
    }
}
