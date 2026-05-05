package com.example.flymestatusbarsizer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SharedPreferences prefs = SettingsStore.prefs(getContext());
        MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
        add(cursor, SettingsStore.KEY_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED));
        add(cursor, SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                        SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED));
        add(cursor, SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                        SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED));
        add(cursor, SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                        SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED));
        add(cursor, SettingsStore.KEY_BATTERY_ICON_STYLE,
                SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_ICON_STYLE,
                        SettingsStore.DEFAULT_BATTERY_ICON_STYLE));
        add(cursor, SettingsStore.KEY_BATTERY_TEXT_FONT,
                SettingsStore.normalizeBatteryTextFont(
                        SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_TEXT_FONT,
                                SettingsStore.DEFAULT_BATTERY_TEXT_FONT)));
        add(cursor, SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                SettingsStore.normalizeScalePercent(
                        SettingsStore.readInt(prefs, SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                                SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT)));
        add(cursor, SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                SettingsStore.normalizeScalePercent(
                        SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                                SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT)));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                        SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                SettingsStore.readInt(prefs, SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                        SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                SettingsStore.readInt(prefs, SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                        SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                SettingsStore.readInt(prefs, SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                        SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                SettingsStore.readInt(prefs, SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                        SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT));
        add(cursor, SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                SettingsStore.readString(prefs, SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                        SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT));
        add(cursor, SettingsStore.KEY_CLOCK_BOLD_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_CLOCK_BOLD_ENABLED,
                        SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED));
        add(cursor, SettingsStore.KEY_CLOCK_FONT_WEIGHT,
                SettingsStore.readInt(prefs, SettingsStore.KEY_CLOCK_FONT_WEIGHT,
                        SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT));
        add(cursor, SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                SettingsStore.normalizeScalePercent(
                        SettingsStore.readInt(prefs, SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                                SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT)));
        add(cursor, SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                        SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED));
        add(cursor, SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                SettingsStore.readString(prefs, SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                        SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI));
        add(cursor, SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                        SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT));
        add(cursor, SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                        SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT));
        add(cursor, SettingsStore.KEY_MBACK_HIDE_PILL,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_MBACK_HIDE_PILL,
                        SettingsStore.DEFAULT_MBACK_HIDE_PILL));
        add(cursor, SettingsStore.KEY_IME_TOOLBAR_ENABLED,
                SettingsStore.readBoolean(prefs, SettingsStore.KEY_IME_TOOLBAR_ENABLED,
                        SettingsStore.DEFAULT_IME_TOOLBAR_ENABLED));
        add(cursor, SettingsStore.KEY_IME_TOOLBAR_ORDER,
                SettingsStore.readString(prefs, SettingsStore.KEY_IME_TOOLBAR_ORDER,
                        SettingsStore.DEFAULT_IME_TOOLBAR_ORDER));
        add(cursor, SettingsStore.KEY_MBACK_INSET_SIZE,
                SettingsStore.readInt(prefs, SettingsStore.KEY_MBACK_INSET_SIZE,
                        SettingsStore.DEFAULT_MBACK_INSET_SIZE));
        add(cursor, SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                SettingsStore.readInt(prefs, SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                        SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT));
        return cursor;
    }

    private static void add(MatrixCursor cursor, String key, boolean value) {
        cursor.addRow(new Object[]{key, value ? "1" : "0"});
    }

    private static void add(MatrixCursor cursor, String key, int value) {
        cursor.addRow(new Object[]{key, Integer.toString(value)});
    }

    private static void add(MatrixCursor cursor, String key, String value) {
        cursor.addRow(new Object[]{key, value == null ? "" : value});
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.com.fiyme.statusbarsizer.settings";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (values == null || getContext() == null) {
            return 0;
        }
        SharedPreferences.Editor editor = SettingsStore.prefs(getContext()).edit();
        int changed = 0;
        for (String key : values.keySet()) {
            Object value = values.get(key);
            if (contains(SettingsStore.BOOLEAN_KEYS, key)) {
                editor.putBoolean(key, toBoolean(value, SettingsStore.defaultBoolean(key)));
            } else if (contains(SettingsStore.INT_KEYS, key)) {
                editor.putInt(key, toInt(value, SettingsStore.defaultInt(key)));
            } else {
                editor.putString(key, value == null ? "" : String.valueOf(value));
            }
            changed++;
        }
        if (changed > 0) {
            editor.apply();
            try {
                getContext().getContentResolver().notifyChange(uri, null);
            } catch (Throwable ignored) {
            }
        }
        return changed;
    }

    private static boolean contains(String[] keys, String target) {
        if (keys == null || target == null) {
            return false;
        }
        for (String key : keys) {
            if (target.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean toBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return false;
        }
        return fallback;
    }

    private static int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
