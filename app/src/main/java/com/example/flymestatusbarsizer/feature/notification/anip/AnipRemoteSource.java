package com.example.flymestatusbarsizer.feature.notification.anip;

import java.net.URI;

/**
 * Describes where ANIP releases are read from and turns it into concrete request URLs.
 *
 * <p>GitHub Releases is the canonical source. Because it is frequently unreachable from mainland
 * China, a proxy prefix is offered as well and the repository can be replaced entirely, matching the
 * approach the reference implementation uses.
 *
 * <p>Kept free of Android and network dependencies so URL construction and manifest parsing rules can
 * be unit tested directly.
 */
public final class AnipRemoteSource {
    /** How a release manifest and its bundle are located. */
    public static final int SOURCE_GITHUB_DIRECT = 0;
    /** GitHub Releases through a public mirror prefix. */
    public static final int SOURCE_GITHUB_PROXY = 1;
    /** An ANIP-compatible static directory that serves {@code anip-release.json}. */
    public static final int SOURCE_STATIC = 2;

    public static final String OFFICIAL_REPOSITORY = "BetterAndroid/android-notification-icon-project";

    private static final String GITHUB_MANIFEST_TEMPLATE =
            "https://github.com/%s/releases/latest/download/anip-release.json";
    private static final String GITHUB_ASSET_TEMPLATE =
            "https://github.com/%s/releases/download/%s/%s";
    private static final String RELEASE_MANIFEST_NAME = "anip-release.json";

    /** Largest accepted release manifest. */
    public static final long MAX_MANIFEST_BYTES = 64L * 1024;
    /** Largest accepted bundle; the published bundle is about 1.4 MB. */
    public static final long MAX_BUNDLE_BYTES = 32L * 1024 * 1024;

    private AnipRemoteSource() {
    }

    /** Validates a {@code owner/repository} slug, returning {@code null} when it is unusable. */
    public static String normalizeRepository(String repository) {
        if (repository == null) {
            return null;
        }
        String value = repository.trim();
        if (value.isEmpty() || value.indexOf(' ') >= 0) {
            return null;
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.length() - 1) {
            return null;
        }
        String owner = value.substring(0, slash);
        String name = value.substring(slash + 1);
        if (name.equals(".") || name.equals("..")) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '/';
            if (!allowed) {
                return null;
            }
        }
        return value;
    }

    /** Validates a base URL used by {@link #SOURCE_STATIC}, returning {@code null} when unusable. */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        String value = baseUrl.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.startsWith("https://")) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                return null;
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Builds the release manifest URL.
     *
     * @param sourceType one of the {@code SOURCE_*} constants.
     * @param repository {@code owner/repository} for the GitHub sources.
     * @param baseUrl directory URL for {@link #SOURCE_STATIC}.
     * @return the URL, or {@code null} when the configuration is invalid.
     */
    public static String manifestUrl(int sourceType, String repository, String baseUrl) {
        if (sourceType == SOURCE_STATIC) {
            String base = normalizeBaseUrl(baseUrl);
            return base == null ? null : base + "/" + RELEASE_MANIFEST_NAME;
        }
        String slug = normalizeRepository(
                repository == null || repository.trim().isEmpty() ? OFFICIAL_REPOSITORY : repository);
        if (slug == null) {
            return null;
        }
        String url = String.format(java.util.Locale.ROOT, GITHUB_MANIFEST_TEMPLATE, slug);
        return sourceType == SOURCE_GITHUB_PROXY ? applyProxy(url) : url;
    }

    /**
     * Resolves the bundle download URL. A release manifest may carry an absolute URL; a relative one
     * is resolved against the release itself.
     *
     * @param sourceType one of the {@code SOURCE_*} constants.
     * @param repository {@code owner/repository} for the GitHub sources.
     * @param baseUrl directory URL for {@link #SOURCE_STATIC}.
     * @param tag the release tag reported by the manifest.
     * @param assetName the bundle file name reported by the manifest.
     * @param manifestDownloadUrl the {@code downloadUrl} field, absolute or relative.
     * @return the URL, or {@code null} when it cannot be resolved.
     */
    public static String bundleUrl(int sourceType, String repository, String baseUrl, String tag,
            String assetName, String manifestDownloadUrl) {
        if (isBlank(tag) || isBlank(assetName)) {
            return null;
        }
        String declared = manifestDownloadUrl == null ? "" : manifestDownloadUrl.trim();
        if (declared.startsWith("https://")) {
            return declared;
        }
        String resolved;
        if (sourceType == SOURCE_STATIC) {
            String base = normalizeBaseUrl(baseUrl);
            if (base == null) {
                return null;
            }
            resolved = base + "/" + assetName;
        } else {
            String slug = normalizeRepository(
                    repository == null || repository.trim().isEmpty()
                            ? OFFICIAL_REPOSITORY : repository);
            if (slug == null) {
                return null;
            }
            resolved = String.format(java.util.Locale.ROOT, GITHUB_ASSET_TEMPLATE, slug, tag, assetName);
        }
        return sourceType == SOURCE_GITHUB_PROXY ? applyProxy(resolved) : resolved;
    }

    /**
     * Prefixes a GitHub URL with the public mirror.
     *
     * <p>The mirror expects the full original URL appended verbatim, which is the convention
     * {@code gh-proxy}-style services use.
     */
    static String applyProxy(String githubUrl) {
        if (githubUrl == null || !githubUrl.startsWith("https://")) {
            return githubUrl;
        }
        return PROXY_PREFIX + githubUrl;
    }

    /** Public GitHub mirror used when the direct source is unreachable. */
    public static final String PROXY_PREFIX = "https://cdn.gh-proxy.org/";

    /** The bundle asset name is fully determined by the tag, which keeps downloads predictable. */
    public static String expectedAssetName(String tag) {
        return isBlank(tag) ? null : "anip-bundle-" + tag + ".zip";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
