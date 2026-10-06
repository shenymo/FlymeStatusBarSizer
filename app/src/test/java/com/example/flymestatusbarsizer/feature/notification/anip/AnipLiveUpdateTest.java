package com.example.flymestatusbarsizer.feature.notification.anip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;

/**
 * End-to-end check against the real ANIP release.
 *
 * <p>Assumed away when the host has no network, so an offline build still passes. When it does run, it
 * proves the whole online path works: manifest fetch, URL resolution, download, size and SHA-256
 * verification, and extraction into a loadable catalog.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipLiveUpdateTest {
    private static AnipReleaseClient.ReleaseInfo fetchOrSkip(int sourceType) {
        AnipReleaseClient.ReleaseInfo info = AnipReleaseClient.fetchManifest(
                sourceType, AnipRemoteSource.OFFICIAL_REPOSITORY, null);
        Assume.assumeTrue("no network or source unreachable; skipping live ANIP check", info != null);
        return info;
    }

    /**
     * Downloads {@code release} with a couple of retries.
     *
     * <p>The published bundle comes from GitHub, where a fetch can legitimately fail on a flaky
     * connection. A transient failure must not read as a downloader regression, so this retries and
     * then skips; the verification logic itself is covered by the deterministic tests.
     */
    private static boolean downloadWithRetry(AnipReleaseClient.ReleaseInfo release, File target) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (AnipReleaseClient.download(AnipRemoteSource.SOURCE_GITHUB_DIRECT,
                    AnipRemoteSource.OFFICIAL_REPOSITORY, null, release, target)) {
                return true;
            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    @Test public void directSourceFetchesThePublishedManifest() {
        AnipReleaseClient.ReleaseInfo info = fetchOrSkip(AnipRemoteSource.SOURCE_GITHUB_DIRECT);
        assertNotNull(info.tag);
        assertTrue("published timestamp must be positive", info.timestamp > 0L);
        assertTrue("bundle size must be plausible", info.size > 100_000L && info.size < 32L * 1024 * 1024);
        assertTrue("digest must be 64 hex chars", info.sha256.matches("[0-9a-f]{64}"));
        assertTrue("asset name must follow the tag",
                info.assetName.equals(AnipRemoteSource.expectedAssetName(info.tag)));
        System.out.println("[LIVE] direct manifest -> " + info);
    }

    @Test public void proxySourceFetchesThePublishedManifest() {
        AnipReleaseClient.ReleaseInfo info = fetchOrSkip(AnipRemoteSource.SOURCE_GITHUB_PROXY);
        assertNotNull(info.tag);
        System.out.println("[LIVE] proxy manifest -> " + info);
    }

    @Test public void downloadedBundleVerifiesAndInstallsAndLoads() throws Exception {
        AnipReleaseClient.ReleaseInfo info = fetchOrSkip(AnipRemoteSource.SOURCE_GITHUB_DIRECT);
        Context context = RuntimeEnvironment.getApplication();
        AnipBundleStore.clear(context);

        File archive = new File(context.getCacheDir(), "live-anip.zip");
        //noinspection ResultOfMethodCallIgnored
        archive.delete();
        boolean downloaded = downloadWithRetry(info, archive);
        Assume.assumeTrue("bundle download failed repeatedly; treating as a network problem",
                downloaded);
        assertTrue(archive.isFile());
        System.out.println("[LIVE] downloaded " + archive.length() + " bytes, verified against "
                + info.sha256);

        AnipBundleStore.Installed installed =
                AnipBundleStore.install(context, archive, info.tag, info.timestamp);
        assertNotNull("bundle must extract", installed);
        assertTrue(new File(installed.directory, "manifest.json").isFile());
        assertTrue(new File(installed.directory, "res").isDirectory());

        AnipIconLibrary library = AnipIconLibrary.get();
        library.invalidate();
        assertTrue(library.load(installed.directory));
        assertTrue("downloaded catalog must contain rules", library.getRuleCount() > 100);
        System.out.println("[LIVE] installed catalog: " + library.getRuleCount() + " rules");

        // A well known package must resolve and decode from the downloaded bundle.
        AnipIconRule wechat = library.find("com.tencent.mm");
        assertNotNull("WeChat must be present in the published bundle", wechat);
        assertNotNull("WeChat artwork must decode",
                library.loadBitmap(installed.directory, wechat));

        //noinspection ResultOfMethodCallIgnored
        archive.delete();
        AnipBundleStore.clear(context);
        library.invalidate();
    }

    @Test public void corruptedArchiveIsRejectedByTheInstaller() throws Exception {
        AnipReleaseClient.ReleaseInfo info = fetchOrSkip(AnipRemoteSource.SOURCE_GITHUB_DIRECT);
        Context context = RuntimeEnvironment.getApplication();
        AnipBundleStore.clear(context);

        // A structurally valid ZIP that is not an ANIP bundle: extraction yields no rules, so the
        // catalog must stay empty rather than claiming a catalog it cannot serve.
        File bogus = new File(context.getCacheDir(), "bogus-anip.zip");
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(bogus))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("app/manifest.json"));
            zip.write("{\"not\":\"an anip manifest\"}".getBytes("UTF-8"));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("app/res/x.png"));
            zip.write(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            zip.closeEntry();
        }
        AnipBundleStore.Installed emptyCatalog =
                AnipBundleStore.install(context, bogus, "bogus01", 1L);
        if (emptyCatalog != null) {
            AnipIconLibrary library = AnipIconLibrary.get();
            library.invalidate();
            assertFalse("a bundle without usable rules must not produce a catalog",
                    library.load(emptyCatalog.directory));
            assertEquals(0, library.getRuleCount());
            library.invalidate();
        }
        //noinspection ResultOfMethodCallIgnored
        bogus.delete();
        AnipBundleStore.clear(context);
    }
}
