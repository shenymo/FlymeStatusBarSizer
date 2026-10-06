package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.SettingsStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Local store for an ANIP bundle downloaded at runtime.
 *
 * <p>The published bundle is a ZIP holding {@code app/manifest.json} plus {@code app/res/*.png}.
 * Loading it streams the archive once and writes the manifest and artwork into a private directory,
 * after which the library reads plain files exactly like it reads the APK assets. Extraction goes
 * through a staging directory that is renamed into place only after the whole archive has been read,
 * so a crash or a truncated download can never leave a half-extracted bundle behind.
 *
 * <p>Every process uses its own cache, matching how the reference implementation avoids sharing files
 * across process boundaries. A process that cannot reach the store simply falls back to the assets
 * packaged in the APK.
 */
public final class AnipBundleStore {
    private static final String DIR_NAME = "anip-bundles";
    private static final String STAGING_SUFFIX = ".staging";
    private static final String MANIFEST_NAME = "manifest.json";
    private static final String RES_DIR = "res";
    /**
     * Only the application icon tree is extracted. The published bundle also ships {@code game/} and
     * {@code system/} trees, but the system ones are vendor specific (MIUI, ColorOS) and Flyme has no
     * equivalent rule set, so carrying them would only add weight.
     */
    private static final String PREFIX = "app/";

    /** Widest accepted archive; guards against a decompression bomb. */
    private static final long MAX_EXTRACTED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;

    private AnipBundleStore() {
    }

    /** Metadata describing the installed bundle. */
    public static final class Installed {
        public final String tag;
        public final long timestamp;
        public final File directory;

        Installed(String tag, long timestamp, File directory) {
            this.tag = tag;
            this.timestamp = timestamp;
            this.directory = directory;
        }
    }

    static SharedPreferences prefs(Context context) {
        return SettingsStore.prefs(context);
    }

    /** Root directory holding every extracted bundle. */
    public static File root(Context context) {
        return new File(context.getFilesDir(), DIR_NAME);
    }

    /** Timestamp of the installed bundle, or {@code 0} when nothing was downloaded. */
    public static long installedTimestamp(Context context) {
        if (context == null) {
            return 0L;
        }
        try {
            return prefs(context).getLong(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP, 0L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** Tag of the installed bundle, or {@code null}. */
    public static String installedTag(Context context) {
        if (context == null) {
            return null;
        }
        try {
            String tag = prefs(context).getString(SettingsStore.KEY_ANIP_INSTALLED_TAG, null);
            return tag == null || tag.isEmpty() ? null : tag;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Returns the installed bundle when its manifest and artwork are present on disk.
     *
     * @return the installed bundle, or {@code null} to use the APK assets instead.
     */
    public static Installed resolve(Context context) {
        if (context == null) {
            return null;
        }
        String tag = installedTag(context);
        long timestamp = installedTimestamp(context);
        if (tag == null || timestamp <= 0L) {
            return null;
        }
        File directory = new File(root(context), tag);
        if (!new File(directory, MANIFEST_NAME).isFile()) {
            return null;
        }
        File resourceDir = new File(directory, RES_DIR);
        if (!resourceDir.isDirectory()) {
            return null;
        }
        return new Installed(tag, timestamp, directory);
    }

    /**
     * Extracts {@code archive} and makes it the installed bundle.
     *
     * @return the installed bundle, or {@code null} when the archive is unusable.
     */
    public static Installed install(Context context, File archive, String tag, long timestamp) {
        if (context == null || archive == null || !archive.isFile()
                || tag == null || tag.isEmpty() || timestamp <= 0L) {
            return null;
        }
        File root = root(context);
        if (!root.isDirectory() && !root.mkdirs()) {
            return null;
        }
        File staging = new File(root, tag + STAGING_SUFFIX);
        deleteRecursively(staging);
        if (!staging.mkdirs()) {
            return null;
        }
        boolean extracted = extract(archive, staging);
        if (!extracted) {
            deleteRecursively(staging);
            return null;
        }
        File target = new File(root, tag);
        deleteRecursively(target);
        if (!staging.renameTo(target)) {
            deleteRecursively(staging);
            return null;
        }
        try {
            prefs(context).edit()
                    .putString(SettingsStore.KEY_ANIP_INSTALLED_TAG, tag)
                    .putLong(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP, timestamp)
                    .apply();
        } catch (Throwable ignored) {
            deleteRecursively(target);
            return null;
        }
        pruneOldBundles(root, target);
        return new Installed(tag, timestamp, target);
    }

    /** Removes the downloaded bundle so the APK assets take over again. */
    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        try {
            deleteRecursively(root(context));
            prefs(context).edit()
                    .remove(SettingsStore.KEY_ANIP_INSTALLED_TAG)
                    .remove(SettingsStore.KEY_ANIP_INSTALLED_TIMESTAMP)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Streams the archive, keeping only {@code app/manifest.json} and {@code app/res/*.png}.
     *
     * <p>The {@code game/} and {@code system/} trees are ignored: system icons are vendor specific and
     * Flyme has no equivalent of the MIUI or ColorOS rule sets.
     */
    private static boolean extract(File archive, File destination) {
        long totalBytes = 0L;
        int entries = 0;
        File resourceDir = new File(destination, RES_DIR);
        try (InputStream input = new java.io.BufferedInputStream(new java.io.FileInputStream(archive));
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    return false;
                }
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(PREFIX)) {
                    continue;
                }
                String relative = name.substring(PREFIX.length());
                File out;
                if (relative.equals(MANIFEST_NAME)) {
                    out = new File(destination, MANIFEST_NAME);
                } else if (relative.startsWith(RES_DIR + "/") && relative.endsWith(".png")) {
                    String fileName = relative.substring(RES_DIR.length() + 1);
                    // Reject any nested path so an archive entry cannot escape the destination.
                    if (fileName.isEmpty() || fileName.indexOf('/') >= 0
                            || fileName.indexOf('\\') >= 0 || fileName.contains("..")) {
                        continue;
                    }
                    if (!resourceDir.isDirectory() && !resourceDir.mkdirs()) {
                        return false;
                    }
                    out = new File(resourceDir, fileName);
                } else {
                    continue;
                }
                if (!writeEntry(zip, out)) {
                    return false;
                }
                totalBytes += out.length();
                if (totalBytes > MAX_EXTRACTED_BYTES) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return new File(destination, MANIFEST_NAME).isFile() && resourceDir.isDirectory();
    }

    private static boolean writeEntry(InputStream input, File target) {
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void pruneOldBundles(File root, File keep) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.equals(keep)) {
                deleteRecursively(child);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
