package com.example.flymestatusbarsizer.feature.notification.anip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Installation and loading of a downloaded ANIP bundle.
 *
 * <p>Builds real ZIP archives so the extraction path, the staging rename and the fallback behaviour
 * are exercised end to end rather than mocked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipBundleStoreTest {
    private Context context;
    private SharedPreferences prefs;
    private File workDir;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = SettingsStore.prefs(context);
        prefs.edit().clear().commit();
        AnipBundleStore.clear(context);
        workDir = new File(context.getCacheDir(), "bundle-test");
        AnipBundleStoreTestSupport.deleteRecursively(workDir);
        assertTrue(workDir.mkdirs());
    }

    /** Builds a minimal but well-formed ANIP bundle. */
    private File writeBundle(String tag, String extraEntryName) throws Exception {
        File archive = new File(workDir, "anip-bundle-" + tag + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("app/manifest.json"));
            zip.write(("{\"com.example.app\":{\"label\":\"Example\",\"format\":\"png\","
                    + "\"color\":\"#112233\",\"overlay\":true}}").getBytes("UTF-8"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("app/res/com.example.app.png"));
            zip.write(AnipBundleStoreTestSupport.onePixelPng());
            zip.closeEntry();
            // A tree the extractor is expected to ignore.
            zip.putNextEntry(new ZipEntry("system/mios/manifest.json"));
            zip.write("{}".getBytes("UTF-8"));
            zip.closeEntry();
            if (extraEntryName != null) {
                zip.putNextEntry(new ZipEntry(extraEntryName));
                zip.write("x".getBytes("UTF-8"));
                zip.closeEntry();
            }
        }
        return archive;
    }

    @Test public void installsExtractsAndResolvesABundle() throws Exception {
        File archive = writeBundle("aaa1111", null);
        AnipBundleStore.Installed installed =
                AnipBundleStore.install(context, archive, "aaa1111", 1234L);
        assertNotNull(installed);
        assertTrue(installed.directory.isDirectory());
        assertTrue(new File(installed.directory, "manifest.json").isFile());
        assertTrue(new File(installed.directory, "res/com.example.app.png").isFile());
        // Non-app trees are not extracted.
        assertFalse(new File(installed.directory, "system").exists());

        assertEquals(1234L, AnipBundleStore.installedTimestamp(context));
        assertEquals("aaa1111", AnipBundleStore.installedTag(context));
        AnipBundleStore.Installed resolved = AnipBundleStore.resolve(context);
        assertNotNull(resolved);
        assertEquals(installed.directory, resolved.directory);
    }

    @Test public void installedBundleBecomesTheCatalog() throws Exception {
        AnipIconLibrary library = AnipIconLibrary.get();
        library.invalidate();
        // Nothing is shipped in the APK, so without an installed bundle there is no catalog at all.
        assertFalse(library.load(null));
        assertEquals(0, library.getRuleCount());
        assertNull(library.find("com.example.app"));

        File archive = writeBundle("bbb2222", null);
        assertNotNull(AnipBundleStore.install(context, archive, "bbb2222", 9999L));

        assertTrue(AnipIconUpdater.reloadLibrary(context));
        assertEquals(1, library.getRuleCount());
        assertNotNull(library.find("com.example.app"));

        AnipIconRule rule = library.find("com.example.app");
        assertNotNull(rule);
        assertEquals("res/com.example.app.png", rule.getAssetPath());
        Bitmap bitmap = library.loadBitmap(AnipBundleStore.resolve(context).directory, rule);
        assertNotNull("artwork must decode from the extracted bundle", bitmap);
        assertTrue(bitmap.getWidth() > 0);

        // Removing the bundle empties the catalog again rather than falling back to anything.
        AnipBundleStore.clear(context);
        assertFalse(AnipIconUpdater.reloadLibrary(context));
        assertEquals(0, library.getRuleCount());
        assertNull(library.find("com.example.app"));
        library.invalidate();
    }

    @Test public void unreadableOrIncompleteArchivesAreRejected() throws Exception {
        // Not a ZIP at all.
        File junk = new File(workDir, "junk.zip");
        try (FileOutputStream out = new FileOutputStream(junk)) {
            out.write("this is not a zip".getBytes("UTF-8"));
        }
        assertNull(AnipBundleStore.install(context, junk, "ccc3333", 1L));
        assertNull(AnipBundleStore.resolve(context));

        // A valid ZIP with no app/manifest.json.
        File empty = new File(workDir, "empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(empty))) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("x".getBytes("UTF-8"));
            zip.closeEntry();
        }
        assertNull(AnipBundleStore.install(context, empty, "ddd4444", 1L));
        assertNull(AnipBundleStore.resolve(context));

        // Missing arguments.
        assertNull(AnipBundleStore.install(context, null, "tag", 1L));
        assertNull(AnipBundleStore.install(context, empty, "", 1L));
        assertNull(AnipBundleStore.install(context, empty, "tag", 0L));
        assertNull(AnipBundleStore.resolve(null));
    }

    @Test public void archiveEntriesCannotEscapeTheDestinationDirectory() throws Exception {
        File archive = writeBundle("eee5555", "app/res/../../escaped.png");
        AnipBundleStore.Installed installed =
                AnipBundleStore.install(context, archive, "eee5555", 5L);
        // The good entries still install; the traversal entry must not be written anywhere.
        assertNotNull(installed);
        assertFalse("a traversal entry must not escape", new File(workDir, "escaped.png").exists());
        assertFalse(new File(AnipBundleStore.root(context).getParentFile(), "escaped.png").exists());
    }

    @Test public void installingANewerBundlePrunesThePreviousOne() throws Exception {
        File first = writeBundle("fff6666", null);
        AnipBundleStore.Installed one = AnipBundleStore.install(context, first, "fff6666", 10L);
        assertNotNull(one);
        File firstDir = one.directory;
        assertTrue(firstDir.isDirectory());

        File second = writeBundle("ggg7777", null);
        AnipBundleStore.Installed two = AnipBundleStore.install(context, second, "ggg7777", 20L);
        assertNotNull(two);
        assertFalse("the replaced bundle should be pruned", firstDir.exists());
        assertTrue(two.directory.isDirectory());
        assertEquals("ggg7777", AnipBundleStore.installedTag(context));
        // No staging directory may survive a successful install.
        File[] children = AnipBundleStore.root(context).listFiles();
        assertNotNull(children);
        for (File child : children) {
            assertFalse("staging left behind: " + child.getName(),
                    child.getName().endsWith(".staging"));
        }
    }

    @Test public void installedMetadataDefaultsToNothingInstalled() {
        assertEquals(0L, AnipBundleStore.installedTimestamp(context));
        assertNull(AnipBundleStore.installedTag(context));
        assertNull(AnipBundleStore.resolve(context));
        assertEquals("尚未下载", AnipIconUpdater.describeInstalled(context));
    }

    @Test public void invalidTagsPreserveOutsideFilesAndTheInstalledBundle() throws Exception {
        File archive = writeBundle("safe", null);
        AnipBundleStore.Installed installed = AnipBundleStore.install(context, archive, "safe", 1L);
        assertNotNull(installed);
        File outside = new File(context.getFilesDir(), "outside");
        File outsideStaging = new File(context.getFilesDir(), "outside.staging");
        assertTrue(outside.mkdirs());
        assertTrue(outsideStaging.mkdirs());
        File marker = new File(outside, "keep.txt");
        File stagingMarker = new File(outsideStaging, "keep.txt");
        assertTrue(marker.createNewFile());
        assertTrue(stagingMarker.createNewFile());

        String[] invalid = {"../outside", ".", "..", "v1/../../outside",
                outside.getAbsolutePath(), "..\\outside", "%2e%2e%2foutside", "v1\u0000outside",
                null, "", " "};
        for (String tag : invalid) {
            AnipBundleStore.Installed rejected = AnipBundleStore.install(context, archive, tag, 2L);
            assertTrue("outside target was deleted for " + tag, marker.isFile());
            assertTrue("outside staging was deleted for " + tag, stagingMarker.isFile());
            assertNull("unsafe tag accepted: " + tag, rejected);
            assertEquals("safe", AnipBundleStore.installedTag(context));
            assertEquals(1L, AnipBundleStore.installedTimestamp(context));
            assertTrue(new File(installed.directory, "manifest.json").isFile());
            assertFalse(new File(AnipBundleStore.root(context), "safe.staging").exists());
        }
    }

    @Test public void persistedTraversalTagCannotResolveAnOutsideBundle() throws Exception {
        File outside = new File(context.getFilesDir(), "outside");
        assertTrue(new File(outside, "res").mkdirs());
        assertTrue(new File(outside, "manifest.json").createNewFile());
        prefs.edit().putString(SettingsStore.KEY_ANIP_INSTALLED_TAG, "../outside")
                .putLong(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP, 1L).commit();
        assertNull(AnipBundleStore.installedTag(context));
        assertNull(AnipBundleStore.resolve(context));
    }

    @Test public void installationRejectsLinkedRootTargetAndStagingDirectories() throws Exception {
        File archive = writeBundle("safe", null);
        File outside = new File(workDir, "outside");
        assertTrue(outside.mkdirs());
        File marker = new File(outside, "keep.txt");
        assertTrue(marker.createNewFile());
        File root = AnipBundleStore.root(context);
        File[] links = {root, new File(root, "safe"), new File(root, "safe.staging")};
        for (File link : links) {
            if (!link.equals(root)) {
                assertTrue(root.isDirectory() || root.mkdirs());
            }
            Files.createSymbolicLink(link.toPath(), outside.toPath());
            try {
                assertNull(AnipBundleStore.install(context, archive, "safe", 1L));
                assertTrue("linked directory was traversed: " + link, marker.isFile());
            } finally {
                if (Files.isSymbolicLink(link.toPath())) {
                    Files.delete(link.toPath());
                }
            }
        }
    }

    @Test public void persistedTagCannotResolveALinkedBundle() throws Exception {
        File outside = new File(workDir, "outside");
        assertTrue(new File(outside, "res").mkdirs());
        assertTrue(new File(outside, "manifest.json").createNewFile());
        File root = AnipBundleStore.root(context);
        assertTrue(root.mkdirs());
        File link = new File(root, "safe");
        Files.createSymbolicLink(link.toPath(), outside.toPath());
        try {
            prefs.edit().putString(SettingsStore.KEY_ANIP_INSTALLED_TAG, "safe")
                    .putLong(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP, 1L).commit();
            assertNull(AnipBundleStore.resolve(context));
        } finally {
            Files.delete(link.toPath());
        }
    }

    @Test public void pruningAndClearingNeverFollowLinksInsideBundles() throws Exception {
        File archive = writeBundle("safe", null);
        AnipBundleStore.Installed first = AnipBundleStore.install(context, archive, "v1", 1L);
        assertNotNull(first);
        File outside = new File(workDir, "outside");
        assertTrue(outside.mkdirs());
        File marker = new File(outside, "keep.txt");
        assertTrue(marker.createNewFile());
        Files.createSymbolicLink(new File(first.directory, "link").toPath(), outside.toPath());
        AnipBundleStore.Installed second = AnipBundleStore.install(context, archive, "v2", 2L);
        assertNotNull(second);
        assertFalse(first.directory.exists());
        assertTrue("pruning followed a symbolic link", marker.isFile());

        Files.createSymbolicLink(new File(second.directory, "link").toPath(), outside.toPath());
        AnipBundleStore.clear(context);
        assertFalse(AnipBundleStore.root(context).exists());
        assertTrue("clearing followed a symbolic link", marker.isFile());

        Files.createSymbolicLink(AnipBundleStore.root(context).toPath(), outside.toPath());
        AnipBundleStore.clear(context);
        assertFalse(Files.isSymbolicLink(AnipBundleStore.root(context).toPath()));
        assertTrue("clearing followed the root symbolic link", marker.isFile());
    }
}
