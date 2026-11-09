package com.example.flymestatusbarsizer.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ReleaseUpdateChecker {
    private static final String API_URL =
            "https://api.github.com/repos/Xposed-Modules-Repo/com.fiyme.statusbarsizer/releases/latest";
    private static final String RELEASE_PATH_PREFIX =
            "/Xposed-Modules-Repo/com.fiyme.statusbarsizer/releases/";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private ReleaseUpdateChecker() {
    }

    public static Result check(String installedVersion) {
        if (!isVersion(installedVersion)) {
            return Result.failure();
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "FlymeStatusBarSizer");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Result.failure();
            }
            String body = readLimited(connection.getInputStream());
            if (body == null) {
                return Result.failure();
            }
            JSONObject release = new JSONObject(body);
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) {
                return Result.success(null);
            }
            String pageUrl = release.optString("html_url", "");
            if (!isReleasePage(pageUrl)) {
                return Result.failure();
            }
            String changelog = formatChangelog(release.optString("body", ""));
            String remoteVersion = findApkVersion(release.optJSONArray("assets"));
            if (!isVersion(remoteVersion)) {
                return Result.failure();
            }
            if (remoteVersion.compareTo(installedVersion) <= 0) {
                return Result.success(null);
            }
            return Result.success(new ReleaseInfo(remoteVersion, pageUrl, changelog));
        } catch (Exception ignored) {
            return Result.failure();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String findApkVersion(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            if (name.matches("\\d{12}\\.apk")) {
                return name.substring(0, name.length() - 4);
            }
        }
        return null;
    }

    private static boolean isReleasePage(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(RELEASE_PATH_PREFIX)
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isVersion(String value) {
        return value != null && value.matches("\\d{12}");
    }

    private static String formatChangelog(String markdown) {
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "• ")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "• ")
                .replaceAll("!?\\[([^]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("\\*\\*(.*?)\\*\\*|__(.*?)__", "$1$2")
                .replaceAll("\\*(.*?)\\*|_(.*?)_", "$1$2")
                .replace("`", "")
                .replaceAll("(?m)^\\s*```.*$", "")
                .trim();
        if (text.length() > 8000) {
            text = text.substring(0, 8000).trim() + "…";
        }
        return text;
    }

    private static String readLimited(InputStream input) throws Exception {
        if (input == null) {
            return null;
        }
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class Result {
        private final boolean successful;
        private final ReleaseInfo release;

        private Result(boolean successful, ReleaseInfo release) {
            this.successful = successful;
            this.release = release;
        }

        static Result success(ReleaseInfo release) {
            return new Result(true, release);
        }

        static Result failure() {
            return new Result(false, null);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public ReleaseInfo release() {
            return release;
        }
    }

    public static final class ReleaseInfo {
        private final String version;
        private final String pageUrl;
        private final String changelog;

        private ReleaseInfo(String version, String pageUrl, String changelog) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.changelog = changelog;
        }

        public String version() {
            return version;
        }

        public String pageUrl() {
            return pageUrl;
        }

        public String changelog() {
            return changelog;
        }
    }
}
