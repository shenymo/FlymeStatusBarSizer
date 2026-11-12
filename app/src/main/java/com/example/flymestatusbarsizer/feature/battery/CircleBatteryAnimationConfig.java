package com.example.flymestatusbarsizer.feature.battery;

import com.example.flymestatusbarsizer.config.SettingsStore;

import android.content.SharedPreferences;

/** Persistent animation choices. Missing keys are resolved before remote sync or backup. */
public final class CircleBatteryAnimationConfig {
    static final int STATIC = 0, FLOW = 1, COMET = 2, BREATHE = 3, ROTATE = 4;
    public static final int STATE_COLOR = 0, TWO_COLORS = 1, RAINBOW = 2;
    public static final String NORMAL = "camera_circle_animation_normal_effect";
    public static final String CHARGING = "camera_circle_animation_charging_effect";
    public static final String PALETTE = "camera_circle_animation_palette";
    public static final String COLOR_START = "camera_circle_animation_color_start";
    public static final String COLOR_END = "camera_circle_animation_color_end";
    public static final String SPEED = "camera_circle_animation_speed";
    public static final String STRENGTH = "camera_circle_animation_strength";
    public static final String VERSION = "camera_circle_animation_version";
    public static final String ENABLED = "camera_circle_animation_enabled";
    public static final String CHARGING_ONLY = "camera_circle_animation_charging_only";
    public static final String EVENTS = "camera_circle_animation_events";
    public static final String SMOOTH = "camera_circle_animation_smooth";
    public static final String LEGACY_RAINBOW = "camera_circle_animation_legacy_rainbow";
    public static final String[] INT_KEYS = {NORMAL, CHARGING, PALETTE, COLOR_START, COLOR_END, SPEED, STRENGTH, VERSION};
    public static final String[] BOOLEAN_KEYS = {ENABLED, CHARGING_ONLY, EVENTS, SMOOTH, LEGACY_RAINBOW};
    boolean enabled, chargingOnly, events, smooth, legacyRainbow;
    public int normal = FLOW, charging = COMET, palette = STATE_COLOR;
    int colorStart = 0xFF088BFF, colorEnd = 0xFF00DCC8, speed = 1, strength = 1;

    public static int defaultInt(String key) {
        switch (key) {
            case NORMAL: return 1;
            case CHARGING: return 2;
            case PALETTE: return 0;
            case COLOR_START: return 0xFF088BFF;
            case COLOR_END: return 0xFF00DCC8;
            case SPEED: return 1;
            case STRENGTH: return 1;
            case VERSION: return 1;
            default: throw new IllegalArgumentException(key);
        }
    }
    public static boolean defaultBoolean(String key) {
        return false;
    }
    public static boolean isKey(String key) { return key.startsWith("camera_circle_animation_"); }

    static boolean isLegacy(SharedPreferences prefs) {
        return !prefs.contains(VERSION) && !prefs.getAll().isEmpty();
    }
    public static void migrate(SharedPreferences prefs) {
        if (prefs.contains(VERSION)) return;
        boolean legacy = isLegacy(prefs);
        boolean rainbow = legacy && SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TINT_ENABLED, false);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : INT_KEYS) {
            if (!prefs.contains(key)) editor.putInt(key, migratedInt(key, legacy, rainbow));
        }
        for (String key : BOOLEAN_KEYS) {
            if (!prefs.contains(key)) editor.putBoolean(key, migratedBoolean(key, legacy, rainbow));
        }
        editor.apply();
    }
    public static int migratedInt(String key, boolean legacy, boolean rainbow) {
        if (legacy) {
            if (NORMAL.equals(key)) return rainbow ? FLOW : STATIC;
            if (CHARGING.equals(key)) return STATIC;
            if (PALETTE.equals(key)) return rainbow ? RAINBOW : STATE_COLOR;
        }
        return defaultInt(key);
    }
    public static boolean migratedBoolean(String key, boolean legacy, boolean rainbow) {
        if (LEGACY_RAINBOW.equals(key)) return rainbow;
        if (legacy && (EVENTS.equals(key) || SMOOTH.equals(key))) return false;
        return defaultBoolean(key);
    }
    public static CircleBatteryAnimationConfig load(SharedPreferences prefs) {
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        boolean legacy = isLegacy(prefs);
        boolean rainbow = legacy && SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TINT_ENABLED, false);
        c.normal = readInt(prefs, NORMAL, legacy, rainbow);
        if (c.normal != STATIC && c.normal != FLOW && c.normal != BREATHE && c.normal != ROTATE) c.normal = FLOW;
        c.charging = clamp(readInt(prefs, CHARGING, legacy, rainbow), STATIC, ROTATE);
        c.palette = clamp(readInt(prefs, PALETTE, legacy, rainbow), 0, 2);
        c.speed = clamp(readInt(prefs, SPEED, legacy, rainbow), 0, 2);
        c.strength = clamp(readInt(prefs, STRENGTH, legacy, rainbow), 0, 2);
        c.colorStart = readInt(prefs, COLOR_START, legacy, rainbow) | 0xFF000000;
        c.colorEnd = readInt(prefs, COLOR_END, legacy, rainbow) | 0xFF000000;
        c.enabled = readBoolean(prefs, ENABLED, legacy, rainbow);
        c.chargingOnly = readBoolean(prefs, CHARGING_ONLY, legacy, rainbow);
        c.events = readBoolean(prefs, EVENTS, legacy, rainbow);
        c.smooth = readBoolean(prefs, SMOOTH, legacy, rainbow);
        c.legacyRainbow = readBoolean(prefs, LEGACY_RAINBOW, legacy, rainbow);
        return c;
    }
    private static int readInt(SharedPreferences p, String k, boolean l, boolean r) {
        return SettingsStore.readInt(p, k, migratedInt(k, l, r));
    }
    private static boolean readBoolean(SharedPreferences p, String k, boolean l, boolean r) {
        return SettingsStore.readBoolean(p, k, migratedBoolean(k, l, r));
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
