package com.example.flymestatusbarsizer.feature.notification.anip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * URL construction and manifest parsing for the online update path.
 *
 * <p>Runs under Robolectric because both {@code android.util.JsonReader} and {@code org.json} are
 * stubs in the mockable {@code android.jar} used for plain JVM tests. These still assert the exact
 * request URLs and validation rules without touching the network, so a regression cannot hide behind
 * an unreachable host.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipReleaseClientTest {
    private static final String VALID_MANIFEST = "{"
            + "\"schemaVersion\": 1,"
            + "\"tag\": \"54b3c62\","
            + "\"timestamp\": 1790955605000,"
            + "\"assetName\": \"anip-bundle-54b3c62.zip\","
            + "\"downloadUrl\": \"anip-bundle-54b3c62.zip\","
            + "\"size\": 1418577,"
            + "\"sha256\": \"f4c1ab1dee927585483144c554405e39385e61be2bed1e5a2c841ed2ca76538f\""
            + "}";

    @Test public void directSourceBuildsCanonicalGitHubUrls() {
        String manifest = AnipRemoteSource.manifestUrl(
                AnipRemoteSource.SOURCE_GITHUB_DIRECT,
                AnipRemoteSource.OFFICIAL_REPOSITORY, null);
        assertEquals("https://github.com/" + AnipRemoteSource.OFFICIAL_REPOSITORY
                + "/releases/latest/download/anip-release.json", manifest);

        String bundle = AnipRemoteSource.bundleUrl(
                AnipRemoteSource.SOURCE_GITHUB_DIRECT,
                AnipRemoteSource.OFFICIAL_REPOSITORY, null,
                "54b3c62", "anip-bundle-54b3c62.zip", "anip-bundle-54b3c62.zip");
        assertEquals("https://github.com/" + AnipRemoteSource.OFFICIAL_REPOSITORY
                + "/releases/download/54b3c62/anip-bundle-54b3c62.zip", bundle);
    }

    @Test public void proxySourcePrefixesTheFullGitHubUrl() {
        String manifest = AnipRemoteSource.manifestUrl(
                AnipRemoteSource.SOURCE_GITHUB_PROXY,
                AnipRemoteSource.OFFICIAL_REPOSITORY, null);
        assertNotNull(manifest);
        assertTrue(manifest.startsWith(AnipRemoteSource.PROXY_PREFIX));
        assertTrue(manifest.endsWith("/releases/latest/download/anip-release.json"));
        // The mirror receives the original URL verbatim.
        assertEquals(AnipRemoteSource.PROXY_PREFIX
                        + "https://github.com/" + AnipRemoteSource.OFFICIAL_REPOSITORY
                        + "/releases/latest/download/anip-release.json",
                manifest);
    }

    @Test public void absoluteDownloadUrlWinsOverTheDeclaredSource() {
        String bundle = AnipRemoteSource.bundleUrl(
                AnipRemoteSource.SOURCE_GITHUB_DIRECT, AnipRemoteSource.OFFICIAL_REPOSITORY, null,
                "54b3c62", "anip-bundle-54b3c62.zip", "https://cdn.example.com/anip-bundle-54b3c62.zip");
        assertEquals("https://cdn.example.com/anip-bundle-54b3c62.zip", bundle);
    }

    @Test public void staticSourceAppendsTheManifestAndBundleNames() {
        assertEquals("https://cdn.example.com/anip/anip-release.json",
                AnipRemoteSource.manifestUrl(AnipRemoteSource.SOURCE_STATIC, null,
                        "https://cdn.example.com/anip"));
        // A trailing slash must not produce a doubled separator.
        assertEquals("https://cdn.example.com/anip/anip-release.json",
                AnipRemoteSource.manifestUrl(AnipRemoteSource.SOURCE_STATIC, null,
                        "https://cdn.example.com/anip/"));
        assertEquals("https://cdn.example.com/anip/anip-bundle-54b3c62.zip",
                AnipRemoteSource.bundleUrl(AnipRemoteSource.SOURCE_STATIC, null,
                        "https://cdn.example.com/anip", "54b3c62", "anip-bundle-54b3c62.zip", null));
    }

    @Test public void repositoryAndBaseUrlValidationRejectsUnsafeValues() {
        assertNull(AnipRemoteSource.normalizeRepository(null));
        assertNull(AnipRemoteSource.normalizeRepository(""));
        assertNull(AnipRemoteSource.normalizeRepository("noslash"));
        assertNull(AnipRemoteSource.normalizeRepository("a/b/c"));
        assertNull(AnipRemoteSource.normalizeRepository("owner/"));
        assertNull(AnipRemoteSource.normalizeRepository("ow ner/repo"));
        assertNull(AnipRemoteSource.normalizeRepository("owner/.."));
        assertNull(AnipRemoteSource.normalizeRepository("owner/re po"));
        assertEquals("owner/repo", AnipRemoteSource.normalizeRepository("  owner/repo  "));
        assertEquals("BetterAndroid/android-notification-icon-project",
                AnipRemoteSource.normalizeRepository(AnipRemoteSource.OFFICIAL_REPOSITORY));

        // Plain HTTP is refused so the bundle cannot be fetched over a cleartext connection.
        assertNull(AnipRemoteSource.normalizeBaseUrl("http://cdn.example.com/anip"));
        assertNull(AnipRemoteSource.normalizeBaseUrl("https://"));
        assertNull(AnipRemoteSource.normalizeBaseUrl(""));
        assertNull(AnipRemoteSource.normalizeBaseUrl("https://cdn.example.com/a?b=c"));
        assertNull(AnipRemoteSource.normalizeBaseUrl("https://cdn.example.com/a#frag"));
        assertEquals("https://cdn.example.com/anip",
                AnipRemoteSource.normalizeBaseUrl("https://cdn.example.com/anip/"));
    }

    @Test public void invalidConfigurationsProduceNoUrl() {
        // A static source with a missing base URL simply has nowhere to go.
        assertNull(AnipRemoteSource.manifestUrl(AnipRemoteSource.SOURCE_STATIC, null, ""));
        assertNull(AnipRemoteSource.manifestUrl(AnipRemoteSource.SOURCE_STATIC, null, null));
        assertNull(AnipRemoteSource.bundleUrl(AnipRemoteSource.SOURCE_STATIC, null, "",
                "54b3c62", "anip-bundle-54b3c62.zip", null));
        // A non-GitHub repository slug is refused rather than silently defaulted.
        assertNull(AnipRemoteSource.manifestUrl(
                AnipRemoteSource.SOURCE_GITHUB_DIRECT, "not-a-slug", null));
        // A bundle reference without a tag cannot be resolved.
        assertNull(AnipRemoteSource.bundleUrl(AnipRemoteSource.SOURCE_GITHUB_DIRECT,
                AnipRemoteSource.OFFICIAL_REPOSITORY, null, null, "x.zip", null));
        assertNull(AnipRemoteSource.bundleUrl(AnipRemoteSource.SOURCE_GITHUB_DIRECT,
                AnipRemoteSource.OFFICIAL_REPOSITORY, null, "54b3c62", null, null));
    }

    @Test public void manifestParsesEveryDeclaredField() {
        AnipReleaseClient.ReleaseInfo info = AnipReleaseClient.parseManifest(VALID_MANIFEST);
        assertNotNull(info);
        assertEquals("54b3c62", info.tag);
        assertEquals(1790955605000L, info.timestamp);
        assertEquals("anip-bundle-54b3c62.zip", info.assetName);
        assertEquals(1418577L, info.size);
        assertEquals("f4c1ab1dee927585483144c554405e39385e61be2bed1e5a2c841ed2ca76538f", info.sha256);
        assertEquals("anip-bundle-54b3c62.zip", info.downloadUrl);
    }

    @Test public void manifestValidationRejectsUnusablePayloads() {
        assertNull(AnipReleaseClient.parseManifest(null));
        assertNull(AnipReleaseClient.parseManifest(""));
        assertNull(AnipReleaseClient.parseManifest("not json"));
        // Unsupported schema version.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace("\"schemaVersion\": 1",
                "\"schemaVersion\": 2")));
        // Missing tag.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace("\"tag\": \"54b3c62\",", "")));
        // Asset name that does not correspond to the tag.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace(
                "\"assetName\": \"anip-bundle-54b3c62.zip\"", "\"assetName\": \"something-else.zip\"")));
        // Malformed digest.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace(
                "\"sha256\": \"f4c1ab1dee927585483144c554405e39385e61be2bed1e5a2c841ed2ca76538f\"",
                "\"sha256\": \"tooshort\"")));
        // Non-positive timestamp.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace(
                "\"timestamp\": 1790955605000", "\"timestamp\": 0")));
        // Size outside the accepted range.
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace(
                "\"size\": 1418577", "\"size\": 0")));
        assertNull(AnipReleaseClient.parseManifest(VALID_MANIFEST.replace(
                "\"size\": 1418577", "\"size\": " + (AnipRemoteSource.MAX_BUNDLE_BYTES + 1))));
    }

    @Test public void updateDetectionComparesTimestamps() {
        AnipReleaseClient.ReleaseInfo info = AnipReleaseClient.parseManifest(VALID_MANIFEST);
        assertNotNull(info);
        assertTrue(AnipReleaseClient.isNewer(info, 0L));
        assertTrue(AnipReleaseClient.isNewer(info, info.timestamp - 1));
        assertFalse(AnipReleaseClient.isNewer(info, info.timestamp));
        assertFalse(AnipReleaseClient.isNewer(info, info.timestamp + 1));
        assertFalse(AnipReleaseClient.isNewer(null, 0L));
    }

    @Test public void expectedAssetNameFollowsTheTag() {
        assertEquals("anip-bundle-54b3c62.zip", AnipRemoteSource.expectedAssetName("54b3c62"));
        assertNull(AnipRemoteSource.expectedAssetName(null));
        assertNull(AnipRemoteSource.expectedAssetName("  "));
    }

    @Test public void hexEncodingMatchesTheDeclaredDigestFormat() {
        assertEquals("00ff10", AnipReleaseClient.toHex(new byte[]{0x00, (byte) 0xff, 0x10}));
        assertEquals("", AnipReleaseClient.toHex(new byte[0]));
    }
}
