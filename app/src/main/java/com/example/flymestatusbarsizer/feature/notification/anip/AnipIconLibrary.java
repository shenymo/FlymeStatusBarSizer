package com.example.flymestatusbarsizer.feature.notification.anip;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reader for an ANIP icon bundle that was downloaded at runtime.
 *
 * <p>The bundle is not shipped in the APK, so this reads an extracted bundle directory prepared by
 * {@link AnipBundleStore}. Without one there is no catalog and every lookup misses, which makes the
 * notification hook fall back to its desktop-icon behaviour.
 *
 * <p>Manifest shape (abridged):
 * <pre>
 * {
 *   "com.tencent.mm": { "label": {"en":"WeChat"}, "format": "png",
 *                       "color": "#2AAE67", "overlay": true },
 *   "com.zhiliaoapp.musically": { "target": "com.ss.android.ugc.aweme", "label": "TikTok Lite" }
 * }
 * </pre>
 *
 * <p>A rule that declares {@code target} borrows the artwork of another package; it may either omit
 * {@code overlay}/{@code color} (inheriting them from the target) or override them. This mirrors the
 * resolution order used by the ANIP SDK.
 */
public final class AnipIconLibrary {
    private static final String MANIFEST_NAME = "manifest.json";
    private static final String RESOURCE_DIR = "res";
    private static final String PNG_SUFFIX = ".png";

    private static final int MAX_CACHED_BITMAPS = 96;

    private static final Object INSTANCE_LOCK = new Object();
    private static volatile AnipIconLibrary instance;

    private final Object loadLock = new Object();
    private final Object bitmapLock = new Object();

    private volatile boolean loaded;
    private Map<String, AnipIconRule> rulesByPackage = Collections.emptyMap();
    private List<AnipIconRule> allRules = Collections.emptyList();

    private final LinkedHashMap<String, Object> bitmapCache =
            new LinkedHashMap<String, Object>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > MAX_CACHED_BITMAPS;
                }
            };

    private AnipIconLibrary() {
    }

    /**
     * Returns the shared library instance.
     *
     * <p>Cheap to call: the manifest is parsed on the first successful {@link #load} and then reused.
     */
    public static AnipIconLibrary get() {
        AnipIconLibrary local = instance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = instance;
                if (local == null) {
                    local = new AnipIconLibrary();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Loads the catalog from an extracted bundle directory.
     *
     * <p>Parsing performs file I/O, so call it from a background thread when possible. A {@code null}
     * or unreadable directory leaves the library empty rather than failing: the caller then falls back
     * to the desktop icon.
     *
     * @return whether the library holds at least one usable rule.
     */
    public boolean load(File bundleDirectory) {
        if (loaded) {
            return !allRules.isEmpty();
        }
        synchronized (loadLock) {
            if (loaded) {
                return !allRules.isEmpty();
            }
            if (!parseBundle(bundleDirectory)) {
                rulesByPackage = Collections.emptyMap();
                allRules = Collections.emptyList();
            }
            loaded = true;
            return !allRules.isEmpty();
        }
    }

    /** Number of manifest rules currently available; {@code 0} before a successful {@link #load}. */
    public int getRuleCount() {
        return allRules.size();
    }

    /** Whether a catalog has been parsed. */
    public boolean isLoaded() {
        return loaded;
    }

    /** All parsed rules, in manifest order. Immutable. */
    public List<AnipIconRule> getRules() {
        return allRules;
    }

    /** Returns the rule for {@code packageName}, or {@code null} when the bundle does not cover it. */
    public AnipIconRule find(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        return rulesByPackage.get(packageName);
    }

    /**
     * Decodes the PNG of {@code rule} from {@code bundleDirectory}, reusing a bounded per-process
     * cache.
     *
     * @return the decoded bitmap, or {@code null} when the artwork is missing or undecodable.
     */
    public Bitmap loadBitmap(File bundleDirectory, AnipIconRule rule) {
        if (bundleDirectory == null || rule == null) {
            return null;
        }
        String path = rule.getAssetPath();
        synchronized (bitmapLock) {
            Object cached = bitmapCache.get(path);
            if (cached instanceof Bitmap) {
                return (Bitmap) cached;
            }
            if (cached != null) {
                return null;
            }
        }
        Bitmap bitmap = null;
        try {
            File file = new File(bundleDirectory, path);
            if (file.isFile()) {
                try (InputStream input = new FileInputStream(file)) {
                    bitmap = BitmapFactory.decodeStream(input);
                }
            }
        } catch (Throwable ignored) {
            bitmap = null;
        }
        synchronized (bitmapLock) {
            // A null marker keeps a broken entry from being re-read on every lookup.
            bitmapCache.put(path, bitmap);
        }
        return bitmap;
    }

    /** Drops every cached bitmap. The parsed manifest is kept. */
    public void clearBitmapCache() {
        synchronized (bitmapLock) {
            bitmapCache.clear();
        }
    }

    /**
     * Drops the parsed catalog so the next {@code load} re-reads the bundle.
     *
     * <p>Used after a bundle is installed or removed, where the catalog must be rebuilt rather than
     * reused.
     */
    public void invalidate() {
        synchronized (loadLock) {
            loaded = false;
            rulesByPackage = Collections.emptyMap();
            allRules = Collections.emptyList();
        }
        clearBitmapCache();
    }

    /** Reads {@code manifest.json} and builds the rule tables. */
    private boolean parseBundle(File bundleDirectory) {
        if (bundleDirectory == null) {
            return false;
        }
        File manifest = new File(bundleDirectory, MANIFEST_NAME);
        File resources = new File(bundleDirectory, RESOURCE_DIR);
        if (!manifest.isFile() || !resources.isDirectory()) {
            return false;
        }
        try (InputStream input = new FileInputStream(manifest)) {
            return resolveRules(readDrafts(new InputStreamReader(input, StandardCharsets.UTF_8)));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Map<String, RuleDraft> readDrafts(Reader manifest) throws IOException {
        Map<String, RuleDraft> drafts = new LinkedHashMap<>();
        try (JsonReader reader = new JsonReader(manifest)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String packageName = reader.nextName();
                readRule(reader, packageName, drafts);
            }
            reader.endObject();
        }
        return drafts;
    }

    /** Resolves alias rules against their targets and publishes the lookup tables. */
    private boolean resolveRules(Map<String, RuleDraft> drafts) {
        Map<String, AnipIconRule> resolved = new LinkedHashMap<>(drafts.size());
        List<AnipIconRule> ordered = new ArrayList<>(drafts.size());
        for (Map.Entry<String, RuleDraft> entry : drafts.entrySet()) {
            String packageName = entry.getKey();
            RuleDraft draft = entry.getValue();
            RuleDraft parent = draft.target == null ? null : drafts.get(draft.target);
            // A target that is itself missing leaves nothing to borrow from, so the rule is dropped
            // rather than pointing at artwork that was never published.
            if (draft.target != null && parent == null) {
                continue;
            }
            boolean overlay = draft.overlay != null
                    ? draft.overlay.booleanValue()
                    : parent != null && Boolean.TRUE.equals(parent.overlay);
            String colorHex = draft.color != null
                    ? draft.color
                    : parent == null ? null : parent.color;
            String assetPackage = draft.target != null ? draft.target : packageName;
            String label = draft.label == null ? null : draft.label.resolve(Locale.getDefault());
            if (label == null && parent != null && parent.label != null) {
                label = parent.label.resolve(Locale.getDefault());
            }
            if (label == null || label.isEmpty()) {
                label = packageName;
            }
            AnipIconRule rule = new AnipIconRule(
                    packageName,
                    label,
                    parseColor(colorHex),
                    overlay,
                    RESOURCE_DIR + "/" + assetPackage + PNG_SUFFIX);
            resolved.put(packageName, rule);
            ordered.add(rule);
        }
        rulesByPackage = Collections.unmodifiableMap(resolved);
        allRules = Collections.unmodifiableList(ordered);
        return !allRules.isEmpty();
    }

    private static void readRule(JsonReader reader, String packageName, Map<String, RuleDraft> drafts)
            throws IOException {
        RuleDraft draft = new RuleDraft();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "label":
                    draft.label = readLabel(reader);
                    break;
                case "target":
                    draft.target = readOptionalString(reader);
                    break;
                case "color":
                    draft.color = readOptionalString(reader);
                    break;
                case "overlay":
                    draft.overlay = readOptionalBoolean(reader);
                    break;
                default:
                    // format, contributors and any future metadata are irrelevant to icon lookup.
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        drafts.put(packageName, draft);
    }

    /**
     * Reads a label that is either a plain string or an object of language tag to text.
     *
     * <p>Localised at parse time against the language in effect, so the raw form is not retained.
     */
    private static AnipLabel readLabel(JsonReader reader) throws IOException {
        AnipLabel label = new AnipLabel();
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING) {
            label.plain = normalize(reader.nextString());
            return label;
        }
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return label;
        }
        reader.beginObject();
        while (reader.hasNext()) {
            String language = reader.nextName();
            String text = readOptionalString(reader);
            if (text != null) {
                label.localized.put(language, text);
            }
        }
        reader.endObject();
        return label;
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
        return normalize(reader.nextString());
    }

    private static Boolean readOptionalBoolean(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        if (token != JsonToken.BOOLEAN) {
            reader.skipValue();
            return null;
        }
        return reader.nextBoolean();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Parses {@code #RRGGBB} into an opaque colour; unknown formats become transparent. */
    static int parseColor(String value) {
        if (value == null) {
            return Color.TRANSPARENT;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.isEmpty() || hex.length() > 8) {
            return Color.TRANSPARENT;
        }
        try {
            long parsed = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                return (int) (parsed | 0xFF000000L);
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return Color.TRANSPARENT;
        }
    }

    /** Mutable staging object used while the manifest is being read. */
    private static final class RuleDraft {
        AnipLabel label;
        String target;
        String color;
        Boolean overlay;
    }

    /** Raw label text: either a plain string or a language tag to text map. */
    static final class AnipLabel {
        final Map<String, String> localized = new LinkedHashMap<>();
        String plain;

        /** Resolves the best text for {@code locale}: plain, exact language, base language, first. */
        String resolve(Locale locale) {
            if (plain != null && !plain.isEmpty()) {
                return plain;
            }
            if (localized.isEmpty()) {
                return null;
            }
            if (locale != null) {
                String exact = matchLanguageTag(locale.toLanguageTag());
                if (exact != null) {
                    return exact;
                }
                String language = locale.getLanguage();
                if (language != null && !language.isEmpty()) {
                    String base = matchLanguageTag(language);
                    if (base != null) {
                        return base;
                    }
                }
            }
            return localized.values().iterator().next();
        }

        private String matchLanguageTag(String tag) {
            if (tag == null || tag.isEmpty()) {
                return null;
            }
            String direct = localized.get(tag);
            if (direct != null) {
                return direct;
            }
            for (Map.Entry<String, String> entry : localized.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(tag)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
