package com.example.flymestatusbarsizer.feature.notification.anip;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Reads ANIP release metadata and downloads its resource bundle.
 *
 * <p>The release manifest is small JSON published next to the bundle, for example:
 * <pre>
 * { "schemaVersion": 1, "tag": "54b3c62", "timestamp": 1790955605000,
 *   "assetName": "anip-bundle-54b3c62.zip", "downloadUrl": "anip-bundle-54b3c62.zip",
 *   "size": 1418577, "sha256": "f4c1ab1d..." }
 * </pre>
 *
 * <p>Every download is size limited and verified against the declared SHA-256 before the temporary
 * file is moved into place, so a truncated or tampered bundle can never replace a working one.
 */
public final class AnipReleaseClient {
    private static final int SCHEMA_VERSION = 1;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final String USER_AGENT = "FlymeStatusBarSizer";

    private AnipReleaseClient() {
    }

    /** Metadata for one published bundle. */
    public static final class ReleaseInfo {
        public final String tag;
        public final long timestamp;
        public final String assetName;
        public final long size;
        public final String sha256;
        public final String downloadUrl;

        ReleaseInfo(String tag, long timestamp, String assetName, long size, String sha256,
                String downloadUrl) {
            this.tag = tag;
            this.timestamp = timestamp;
            this.assetName = assetName;
            this.size = size;
            this.sha256 = sha256;
            this.downloadUrl = downloadUrl;
        }

        @Override
        public String toString() {
            return "ReleaseInfo{tag=" + tag + ", timestamp=" + timestamp + ", size=" + size + '}';
        }
    }

    /**
     * Parses a release manifest.
     *
     * <p>Reads through {@link JsonReader} rather than {@code org.json}, matching the icon library
     * reader: the framework JSON object is only a stub in plain JVM unit tests, which would make this
     * logic untestable outside Robolectric.
     *
     * @return the parsed metadata, or {@code null} when the payload is malformed or unsupported.
     */
    public static ReleaseInfo parseManifest(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return parse(new java.io.StringReader(json));
    }

    /** Parses a release manifest from an arbitrary reader. */
    private static ReleaseInfo parse(java.io.Reader source) {
        if (source == null) {
            return null;
        }
        Integer schemaVersion = null;
        String tag = null;
        String assetName = null;
        String sha256 = null;
        String downloadUrl = null;
        Long timestamp = null;
        Long size = null;
        try (JsonReader reader = new JsonReader(source)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "schemaVersion":
                        schemaVersion = readOptionalInt(reader);
                        break;
                    case "tag":
                        tag = readOptionalString(reader);
                        break;
                    case "assetName":
                        assetName = readOptionalString(reader);
                        break;
                    case "sha256":
                        sha256 = readOptionalString(reader);
                        break;
                    case "downloadUrl":
                        downloadUrl = readOptionalString(reader);
                        break;
                    case "timestamp":
                        timestamp = readOptionalLong(reader);
                        break;
                    case "size":
                        size = readOptionalLong(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        } catch (Throwable ignored) {
            return null;
        }

        if (schemaVersion == null || schemaVersion.intValue() != SCHEMA_VERSION) {
            return null;
        }
        if (tag == null) {
            return null;
        }
        // The published name is derived from the tag; refusing anything else keeps a manifest from
        // pointing the download at an unrelated asset.
        if (assetName == null || !assetName.equals(AnipRemoteSource.expectedAssetName(tag))) {
            return null;
        }
        if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
            return null;
        }
        if (timestamp == null || timestamp.longValue() <= 0L) {
            return null;
        }
        if (size == null || size.longValue() <= 0L
                || size.longValue() > AnipRemoteSource.MAX_BUNDLE_BYTES) {
            return null;
        }
        return new ReleaseInfo(tag, timestamp.longValue(), assetName, size.longValue(),
                sha256.toLowerCase(Locale.ROOT), downloadUrl);
    }

    private static String readOptionalString(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token != JsonToken.STRING) {
            reader.skipValue();
            return null;
        }
        return trimToNull(reader.nextString());
    }

    private static Integer readOptionalInt(JsonReader reader) throws IOException {
        Long value = readOptionalLong(reader);
        return value == null ? null : Integer.valueOf(value.intValue());
    }

    private static Long readOptionalLong(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token == JsonToken.NUMBER) {
            return Long.valueOf(reader.nextLong());
        }
        if (token == JsonToken.STRING) {
            // Tolerate numbers published as strings.
            String text = trimToNull(reader.nextString());
            if (text == null) {
                return null;
            }
            try {
                return Long.valueOf(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        reader.skipValue();
        return null;
    }

    /** Whether {@code release} is newer than the currently installed {@code installedTimestamp}. */
    public static boolean isNewer(ReleaseInfo release, long installedTimestamp) {
        return release != null && release.timestamp > installedTimestamp;
    }

    /**
     * Fetches and parses the release manifest.
     *
     * @return the metadata, or {@code null} when the source is unreachable or the payload is invalid.
     */
    public static ReleaseInfo fetchManifest(int sourceType, String repository, String baseUrl) {
        String manifestUrl = AnipRemoteSource.manifestUrl(sourceType, repository, baseUrl);
        if (manifestUrl == null) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = open(manifestUrl);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }
            String body = readLimitedText(connection.getInputStream(),
                    AnipRemoteSource.MAX_MANIFEST_BYTES);
            return body == null ? null : parseManifest(body);
        } catch (Throwable ignored) {
            return null;
        } finally {
            disconnect(connection);
        }
    }

    /**
     * Downloads the bundle for {@code release} into {@code target}.
     *
     * <p>The payload is written to {@code target} directly and deleted again on any failure, so a
     * partially written bundle never survives the call.
     *
     * @return whether a size- and hash-verified bundle now sits at {@code target}.
     */
    public static boolean download(int sourceType, String repository, String baseUrl,
            ReleaseInfo release, File target) {
        if (release == null || target == null) {
            return false;
        }
        String url = AnipRemoteSource.bundleUrl(sourceType, repository, baseUrl, release.tag,
                release.assetName, release.downloadUrl);
        if (url == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        HttpURLConnection connection = null;
        boolean ok = false;
        try {
            connection = open(url);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = 0L;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    written += read;
                    if (written > AnipRemoteSource.MAX_BUNDLE_BYTES) {
                        return false;
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
            if (written != release.size) {
                return false;
            }
            if (!toHex(digest.digest()).equals(release.sha256)) {
                return false;
            }
            ok = true;
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            disconnect(connection);
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json, application/octet-stream");
        return connection;
    }

    private static void disconnect(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Reads a stream as UTF-8 text, refusing payloads larger than {@code limit}. */
    static String readLimitedText(InputStream input, long limit) {
        if (input == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            long total = 0L;
            int read;
            while ((read = reader.read(buffer)) > 0) {
                total += read;
                if (total > limit) {
                    return null;
                }
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
