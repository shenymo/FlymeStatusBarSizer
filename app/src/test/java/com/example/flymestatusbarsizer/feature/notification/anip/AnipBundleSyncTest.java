package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Looper;

import com.example.flymestatusbarsizer.config.RemoteSettingsSync;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.notification.NotificationHooks;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real private stores and remote preference notifications; only the network is substituted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AnipBundleSyncTest {
    private Context settings;
    private Context systemUi;
    private SharedPreferences remote;
    private Field remoteField;
    private Object previousRemote;
    private TestWorker worker;
    private FakeTransport transport;
    private AnipBundleSync sync;
    private final AtomicInteger refreshes = new AtomicInteger();
    private final Map<String, File> archives = new HashMap<>();

    @Before public void setUp() throws Exception {
        settings = RuntimeEnvironment.getApplication();
        SettingsStore.prefs(settings).edit().clear().commit();
        AnipBundleStore.clear(settings);
        File files = new File(settings.getFilesDir(), "systemui-private");
        File cache = new File(settings.getCacheDir(), "systemui-private");
        files.mkdirs();
        cache.mkdirs();
        SharedPreferences runtimePrefs = settings.getSharedPreferences("systemui-private", 0);
        runtimePrefs.edit().clear().commit();
        systemUi = new ContextWrapper(settings) {
            @Override public Context getApplicationContext() { return this; }
            @Override public String getPackageName() { return "com.android.systemui"; }
            @Override public boolean isDeviceProtectedStorage() { return true; }
            @Override public File getFilesDir() { return files; }
            @Override public File getCacheDir() { return cache; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return runtimePrefs;
            }
        };
        AnipBundleStore.clear(systemUi);
        AnipIconLibrary.get().invalidate();
        remote = settings.getSharedPreferences("remote-test", 0);
        remote.edit().clear().commit();
        remoteField = RemoteSettingsSync.class.getDeclaredField("remotePrefs");
        remoteField.setAccessible(true);
        previousRemote = remoteField.get(null);
        remoteField.set(null, remote);
        SettingsStore.notifyChanged(settings);
        worker = new TestWorker();
        transport = new FakeTransport();
        sync = new AnipBundleSync(worker, transport);
    }

    @After public void tearDown() throws Exception {
        worker.shutdownNow();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        remoteField.set(null, previousRemote);
        AnipBundleStore.clear(settings);
        AnipBundleStore.clear(systemUi);
        AnipIconLibrary.get().invalidate();
    }

    @Test public void existingSystemUiBundleUpdatesAndInvalidatesRulesAndBitmaps() throws Exception {
        AnipReleaseClient.ReleaseInfo old = release("old", 10, 0xff112233);
        install(systemUi, old);
        assertTrue(AnipIconUpdater.reloadLibrary(systemUi));
        assertEquals(0xff112233, pixel());
        AnipReleaseClient.ReleaseInfo next = release("next", 20, 0xffabcdef);
        install(settings, next);
        AtomicInteger notifications = new AtomicInteger();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
            if (SettingsStore.KEY_ANIP_BUNDLE_TARGET.equals(key)) notifications.incrementAndGet();
        };
        remote.registerOnSharedPreferenceChangeListener(listener);
        publish(next);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, notifications.get());
        assertFalse(remote.contains(SettingsStore.KEY_ANIP_INSTALLED_TAG));
        assertFalse(remote.contains(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP));
        assertEquals("old", AnipBundleStore.installedTag(systemUi));

        request();
        drain();
        assertEquals("next", AnipBundleStore.installedTag(systemUi));
        assertEquals("next", AnipBundleStore.installedTag(settings));
        assertEquals(0, transport.fetches);
        assertEquals(next.downloadUrl, transport.lastRelease.downloadUrl);
        assertEquals(1, refreshes.get());
        assertFalse(AnipIconLibrary.get().isLoaded());
        assertTrue(AnipIconLibrary.get().load(AnipBundleStore.resolve(systemUi).directory));
        assertEquals("next", AnipIconLibrary.get().find("com.example.app").getLabel());
        assertEquals(0xffabcdef, pixel());
        request();
        drain();
        assertEquals(1, transport.downloads);
        // A new process also recognizes the persisted target without downloading again.
        sync = new AnipBundleSync(worker, transport);
        request();
        drain();
        assertEquals(1, transport.downloads);
        remote.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Test public void deletionClearsTheOtherStoreAndSurvivesRestartUntilExplicitInstall() throws Exception {
        AnipReleaseClient.ReleaseInfo old = release("old", 10, 0xff112233);
        install(settings, old);
        install(systemUi, old);
        publish(old);
        AnipIconUpdater.removeDownloadedBundle(settings);
        assertTrue(AnipBundleSync.isRemoved(remote));
        assertNull(AnipBundleStore.resolve(settings));
        assertNotNull(AnipBundleStore.resolve(systemUi));
        // Simulate SystemUI's separate singleton still holding the old catalog.
        assertTrue(AnipIconUpdater.reloadLibrary(systemUi));
        request();
        drain();
        assertNull(AnipBundleStore.resolve(systemUi));
        assertFalse(AnipBundleStore.root(systemUi).exists());
        assertFalse(AnipIconLibrary.get().isLoaded());
        sync = new AnipBundleSync(worker, transport);
        request();
        drain();
        assertEquals(0, transport.fetches);
        assertEquals(0, transport.downloads);
        install(settings, old);
        publish(old);
        request();
        drain();
        assertEquals("old", AnipBundleStore.installedTag(systemUi));
    }

    @Test public void deletionDuringDownloadDiscardsResultBeforeDebouncedCallback() throws Exception {
        AnipReleaseClient.ReleaseInfo old = release("old", 10, 0xff112233);
        install(settings, old);
        install(systemUi, old);
        publish(release("next", 20, 0xffabcdef));
        transport.duringDownload = () -> AnipIconUpdater.removeDownloadedBundle(settings);
        request();
        drain();
        assertEquals("old", AnipBundleStore.installedTag(systemUi));
        assertEquals(0, refreshes.get());
        assertTrue(AnipBundleSync.isRemoved(remote));
        request();
        drain();
        assertNull(AnipBundleStore.resolve(systemUi));
        assertEquals(1, transport.downloads);
    }

    @Test public void systemUiStartupReconcilesDeletionWithoutAnyNotificationOrConfigChange() throws Exception {
        AnipReleaseClient.ReleaseInfo old = release("old", 10, 0xff112233);
        install(settings, old);
        install(systemUi, old);
        AnipIconUpdater.removeDownloadedBundle(settings);
        Field runtimeRemote = ModuleConfig.class.getDeclaredField("remotePrefs");
        runtimeRemote.setAccessible(true);
        Object savedRemote = runtimeRemote.get(null);
        runtimeRemote.set(null, remote);
        Field executor = AnipBundleSync.class.getDeclaredField("worker");
        executor.setAccessible(true);
        ScheduledExecutorService runtimeWorker = (ScheduledExecutorService) executor.get(AnipBundleSync.get());
        try {
            // The Xposed superclass is present only on the test runtime classpath.
            Class.forName("com.example.flymestatusbarsizer.FlymeStatusBarSizer")
                    .getMethod("ensureConfigRefreshObserver", Context.class).invoke(null, systemUi);
            runtimeWorker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            assertNull(AnipBundleStore.resolve(systemUi));
            assertFalse(AnipBundleStore.root(systemUi).exists());
        } finally {
            runtimeRemote.set(null, savedRemote);
            runtimeWorker.shutdownNow();
            assertTrue(runtimeWorker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void newerRequestSupersedesAnInFlightDownload() throws Exception {
        install(systemUi, release("old", 10, 0xff112233));
        AnipReleaseClient.ReleaseInfo next = release("next", 20, 0xffabcdef);
        AnipReleaseClient.ReleaseInfo latest = release("latest", 30, 0xff445566);
        publish(next);
        transport.duringDownload = () -> {
            publish(latest);
            request();
        };
        request();
        drain();
        assertEquals("latest", AnipBundleStore.installedTag(systemUi));
        assertEquals(2, transport.downloads);
        assertEquals(1, refreshes.get());
    }

    @Test public void failedUpdateKeepsOldBundleAndRetriesWithoutAnotherSettingChange() throws Exception {
        install(systemUi, release("old", 10, 0xff112233));
        publish(release("next", 20, 0xffabcdef));
        transport.fail = true;
        request();
        drain();
        assertEquals("old", AnipBundleStore.installedTag(systemUi));
        assertEquals(0, refreshes.get());
        assertNotNull(worker.retry);
        transport.fail = false;
        worker.execute(worker.retry);
        drain();
        assertEquals("next", AnipBundleStore.installedTag(systemUi));
        assertEquals(1, refreshes.get());
    }

    @Test public void firstDownloadUsesRemoteSourceAndSignalsCompletion() throws Exception {
        transport.latest = release("first", 10, 0xff112233);
        SettingsStore.prefs(settings).edit()
                .putInt(SettingsStore.KEY_ANIP_SOURCE_TYPE, AnipRemoteSource.SOURCE_STATIC)
                .putString(SettingsStore.KEY_ANIP_BASE_URL, "https://example.com/icons")
                .putString(SettingsStore.KEY_ANIP_REPOSITORY, "owner/icons").apply();
        SettingsStore.notifyChanged(settings);
        sync.request(systemUi, remote, false, NotificationHooks::onAnipBundleChanged);
        drain();
        assertEquals(0, transport.fetches);
        request();
        drain();
        assertEquals(AnipRemoteSource.SOURCE_STATIC, transport.type);
        assertEquals("https://example.com/icons", transport.base);
        assertEquals("owner/icons", transport.repo);
        assertEquals("first", AnipBundleStore.installedTag(systemUi));
        assertEquals(1, refreshes.get());
        assertEquals("", AnipBundleSync.target(remote));
        // Deletion must also work when only SystemUI downloaded the bundle.
        assertNull(AnipBundleStore.resolve(settings));
        AnipIconUpdater.removeDownloadedBundle(settings);
        request();
        drain();
        assertTrue(AnipBundleSync.isRemoved(remote));
        assertNull(AnipBundleStore.resolve(systemUi));
        assertEquals(1, transport.downloads);
    }

    @Test public void missingServiceOrInvalidTargetDoesNotDownloadOrEraseExistingFiles() throws Exception {
        install(systemUi, release("old", 10, 0xff112233));
        sync.request(systemUi, null, true, NotificationHooks::onAnipBundleChanged);
        SettingsStore.prefs(settings).edit()
                .putString(SettingsStore.KEY_ANIP_BUNDLE_TARGET, "invalid").apply();
        SettingsStore.notifyChanged(settings);
        request();
        drain();
        assertEquals("old", AnipBundleStore.installedTag(systemUi));
        assertEquals(0, transport.downloads);
        assertEquals(0, transport.fetches);
    }

    private void request() {
        sync.request(systemUi, remote, true, () -> {
            refreshes.incrementAndGet();
            NotificationHooks.onAnipBundleChanged();
        });
    }

    private void drain() throws Exception {
        // The second barrier also covers a request queued by a download-completion race.
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void publish(AnipReleaseClient.ReleaseInfo release) {
        SettingsStore.prefs(settings).edit().putString(SettingsStore.KEY_ANIP_BUNDLE_TARGET,
                AnipReleaseClient.encodeManifest(release, release.downloadUrl)).apply();
        SettingsStore.notifyChanged(settings);
    }

    private void install(Context context, AnipReleaseClient.ReleaseInfo release) {
        assertNotNull(AnipBundleStore.install(context, archives.get(release.tag), release.tag,
                release.timestamp));
    }

    private int pixel() {
        AnipIconLibrary library = AnipIconLibrary.get();
        Bitmap bitmap = library.loadBitmap(AnipBundleStore.resolve(systemUi).directory,
                library.find("com.example.app"));
        assertNotNull(bitmap);
        return bitmap.getPixel(0, 0);
    }

    private AnipReleaseClient.ReleaseInfo release(String tag, long timestamp, int color) throws Exception {
        File archive = new File(settings.getCacheDir(), tag + ".zip");
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
        bitmap.recycle();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("app/manifest.json"));
            zip.write(("{\"com.example.app\":{\"label\":\"" + tag
                    + "\",\"format\":\"png\",\"overlay\":true}}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("app/res/com.example.app.png"));
            zip.write(png.toByteArray());
            zip.closeEntry();
        }
        archives.put(tag, archive);
        return new AnipReleaseClient.ReleaseInfo(tag, timestamp, AnipRemoteSource.expectedAssetName(tag),
                archive.length(), AnipReleaseClient.toHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(archive.toPath()))),
                "https://example.com/" + AnipRemoteSource.expectedAssetName(tag));
    }

    private final class FakeTransport implements AnipBundleSync.Transport {
        int fetches;
        int downloads;
        int type;
        String repo;
        String base;
        boolean fail;
        Runnable duringDownload;
        AnipReleaseClient.ReleaseInfo latest;
        AnipReleaseClient.ReleaseInfo lastRelease;

        @Override public AnipReleaseClient.ReleaseInfo fetch(int type, String repo, String base) {
            fetches++;
            this.type = type;
            this.repo = repo;
            this.base = base;
            return latest;
        }

        @Override public boolean download(int type, String repo, String base,
                AnipReleaseClient.ReleaseInfo release, File destination) {
            downloads++;
            lastRelease = release;
            if (duringDownload != null) {
                Runnable action = duringDownload;
                duringDownload = null;
                action.run();
            }
            if (fail) return false;
            try {
                Files.copy(archives.get(release.tag).toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    private static final class TestWorker extends ScheduledThreadPoolExecutor {
        Runnable retry;

        TestWorker() { super(1); }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (delay > 0) {
                retry = command;
                // Tests explicitly advance retries instead of waiting on a wall-clock timer.
                return super.schedule(command, 1, TimeUnit.DAYS);
            }
            return super.schedule(command, delay, unit);
        }
    }
}
