package com.example.flymestatusbarsizer.feature.notification.anip;

import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codec for the per-application notification icon overrides and the effective-mode lookup.
 *
 * <p>Stored as one JSON object under {@link SettingsStore#KEY_ANIP_ICON_MODE}, for example
 * {@code {"com.tencent.mm":1,"com.taobao.taobao":2}}. Only applications the user changed appear in
 * the map, so the value stays small and readable inside a configuration backup.
 *
 * <p>This class never writes preferences itself. Persisting the encoded value is the settings
 * screen's job, because that is where the cross-process sync notification is raised.
 */
public final class AnipIconMode {
    /** Use the ANIP artwork when the bundle covers the application, otherwise the desktop icon. */
    public static final int FOLLOW = 0;
    /** Prefer the ANIP artwork for this application. */
    public static final int ANIP = 1;
    /** Never use ANIP artwork for this application; keep the desktop-icon behaviour. */
    public static final int APPLICATION = 2;

    private AnipIconMode() {
    }

    /**
     * Returns the effective mode for {@code packageName}: the per-application override when present,
     * otherwise the global switch state.
     */
    public static int get(SharedPreferences prefs, boolean anipEnabled, String packageName) {
        int fallback = anipEnabled ? FOLLOW : APPLICATION;
        if (prefs == null || packageName == null || packageName.isEmpty()) {
            return fallback;
        }
        Integer override = readOverrides(prefs).get(packageName);
        return override != null ? override.intValue() : fallback;
    }

    /** Whether the effective mode for {@code packageName} permits ANIP artwork. */
    public static boolean mayUseAnip(SharedPreferences prefs, boolean anipEnabled, String packageName) {
        return get(prefs, anipEnabled, packageName) != APPLICATION;
    }

    /** Reads the override map, ignoring malformed entries and unknown modes. */
    public static Map<String, Integer> readOverrides(SharedPreferences prefs) {
        if (prefs == null) {
            return Collections.emptyMap();
        }
        String raw = SettingsStore.readString(
                prefs, SettingsStore.KEY_ANIP_ICON_MODE, SettingsStore.DEFAULT_ANIP_ICON_MODE);
        return decodeOverrides(raw);
    }

    /** Decodes the persisted JSON into an immutable override map; never throws. */
    public static Map<String, Integer> decodeOverrides(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String packageName = keys.next();
                if (packageName == null || packageName.isEmpty()) {
                    continue;
                }
                int mode = root.optInt(packageName, FOLLOW);
                if (mode == ANIP || mode == APPLICATION) {
                    result.put(packageName, Integer.valueOf(mode));
                }
            }
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns a copy of {@code overrides} with one application changed.
     *
     * <p>Passing {@link #FOLLOW} removes the entry, so the application follows the global switch
     * again instead of pinning today's behaviour.
     */
    public static Map<String, Integer> withOverride(
            Map<String, Integer> overrides, String packageName, int mode) {
        Map<String, Integer> result = new LinkedHashMap<>(
                overrides == null ? Collections.<String, Integer>emptyMap() : overrides);
        if (packageName == null || packageName.isEmpty()) {
            return result;
        }
        if (mode == FOLLOW) {
            result.remove(packageName);
        } else if (mode == ANIP || mode == APPLICATION) {
            result.put(packageName, Integer.valueOf(mode));
        }
        return result;
    }

    /** Encodes an override map as the persisted JSON string. Never throws. */
    public static String encodeOverrides(Map<String, Integer> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return SettingsStore.DEFAULT_ANIP_ICON_MODE;
        }
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    continue;
                }
                int mode = entry.getValue().intValue();
                if (mode == ANIP || mode == APPLICATION) {
                    root.put(entry.getKey(), mode);
                }
            }
        } catch (Throwable ignored) {
            return SettingsStore.DEFAULT_ANIP_ICON_MODE;
        }
        return root.toString();
    }

    /** Human-readable name of a mode, for the settings list. */
    public static String describe(int mode) {
        switch (mode) {
            case ANIP:
                return "ANIP 图标";
            case APPLICATION:
                return "应用图标";
            default:
                return "跟随总开关";
        }
    }
}
