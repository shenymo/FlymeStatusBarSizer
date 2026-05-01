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
        add(cursor, SettingsStore.KEY_ENABLED, prefs.getBoolean(SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED));
        add(cursor, SettingsStore.KEY_GLOBAL_ICON_SCALE, prefs.getInt(SettingsStore.KEY_GLOBAL_ICON_SCALE, SettingsStore.DEFAULT_GLOBAL_ICON_SCALE));
        add(cursor, SettingsStore.KEY_MOBILE_SIGNAL_FACTOR, prefs.getInt(SettingsStore.KEY_MOBILE_SIGNAL_FACTOR, SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR));
        add(cursor, SettingsStore.KEY_WIFI_SIGNAL_FACTOR, prefs.getInt(SettingsStore.KEY_WIFI_SIGNAL_FACTOR, SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR));
        add(cursor, SettingsStore.KEY_BATTERY_FACTOR, prefs.getInt(SettingsStore.KEY_BATTERY_FACTOR, SettingsStore.DEFAULT_BATTERY_FACTOR));
        add(cursor, SettingsStore.KEY_STATUS_ICON_FACTOR, prefs.getInt(SettingsStore.KEY_STATUS_ICON_FACTOR, SettingsStore.DEFAULT_STATUS_ICON_FACTOR));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X,
                        SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y,
                        SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X,
                        SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y,
                        SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y));
        add(cursor, SettingsStore.KEY_IOS_BATTERY_WIDTH, prefs.getInt(SettingsStore.KEY_IOS_BATTERY_WIDTH, SettingsStore.DEFAULT_IOS_BATTERY_WIDTH));
        add(cursor, SettingsStore.KEY_IOS_BATTERY_HEIGHT, prefs.getInt(SettingsStore.KEY_IOS_BATTERY_HEIGHT, SettingsStore.DEFAULT_IOS_BATTERY_HEIGHT));
        add(cursor, SettingsStore.KEY_IOS_BATTERY_OFFSET_X, prefs.getInt(SettingsStore.KEY_IOS_BATTERY_OFFSET_X, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_X));
        add(cursor, SettingsStore.KEY_IOS_BATTERY_OFFSET_Y, prefs.getInt(SettingsStore.KEY_IOS_BATTERY_OFFSET_Y, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_Y));
        add(cursor, SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE, prefs.getInt(SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE, SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE));
        add(cursor, SettingsStore.KEY_IOS_GROUP_BATTERY_SCALE, prefs.getInt(SettingsStore.KEY_IOS_GROUP_BATTERY_SCALE, SettingsStore.DEFAULT_IOS_GROUP_BATTERY_SCALE));
        add(cursor, SettingsStore.KEY_IOS_GROUP_SIGNAL_SCALE, prefs.getInt(SettingsStore.KEY_IOS_GROUP_SIGNAL_SCALE, SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_SCALE));
        add(cursor, SettingsStore.KEY_IOS_GROUP_WIFI_SCALE, prefs.getInt(SettingsStore.KEY_IOS_GROUP_WIFI_SCALE, SettingsStore.DEFAULT_IOS_GROUP_WIFI_SCALE));
        add(cursor, SettingsStore.KEY_IOS_GROUP_WIFI_SIGNAL_GAP, prefs.getInt(SettingsStore.KEY_IOS_GROUP_WIFI_SIGNAL_GAP, SettingsStore.DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP));
        add(cursor, SettingsStore.KEY_IOS_GROUP_SIGNAL_BATTERY_GAP, prefs.getInt(SettingsStore.KEY_IOS_GROUP_SIGNAL_BATTERY_GAP, SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP));
        add(cursor, SettingsStore.KEY_IOS_GROUP_START_GAP_ADJUST, prefs.getInt(SettingsStore.KEY_IOS_GROUP_START_GAP_ADJUST, SettingsStore.DEFAULT_IOS_GROUP_START_GAP_ADJUST));
        add(cursor, SettingsStore.KEY_IOS_WIFI_WIDTH, prefs.getInt(SettingsStore.KEY_IOS_WIFI_WIDTH, SettingsStore.DEFAULT_IOS_WIFI_WIDTH));
        add(cursor, SettingsStore.KEY_IOS_WIFI_HEIGHT, prefs.getInt(SettingsStore.KEY_IOS_WIFI_HEIGHT, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT));
        add(cursor, SettingsStore.KEY_IOS_WIFI_OFFSET_X, prefs.getInt(SettingsStore.KEY_IOS_WIFI_OFFSET_X, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X));
        add(cursor, SettingsStore.KEY_IOS_WIFI_OFFSET_Y, prefs.getInt(SettingsStore.KEY_IOS_WIFI_OFFSET_Y, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y));
        add(cursor, SettingsStore.KEY_ACTIVITY_ICON_FACTOR, prefs.getInt(SettingsStore.KEY_ACTIVITY_ICON_FACTOR, SettingsStore.DEFAULT_ACTIVITY_ICON_FACTOR));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_OFFSET_X, prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_OFFSET_X, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_X));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_OFFSET_Y, prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_OFFSET_Y, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_Y));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                        SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                        SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                        SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                        SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT));
        add(cursor, SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                prefs.getInt(SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                        SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT));
        add(cursor, SettingsStore.KEY_TEXT_SCALE, prefs.getInt(SettingsStore.KEY_TEXT_SCALE, SettingsStore.DEFAULT_TEXT_SCALE));
        add(cursor, SettingsStore.KEY_SHOW_CLOCK_WEEKDAY, prefs.getBoolean(SettingsStore.KEY_SHOW_CLOCK_WEEKDAY, SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY));
        add(cursor, SettingsStore.KEY_CLOCK_BOLD_ENABLED, prefs.getBoolean(SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED));
        add(cursor, SettingsStore.KEY_CLOCK_FONT_WEIGHT, prefs.getInt(SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DUAL_COMBINED,
                prefs.getBoolean(SettingsStore.KEY_IOS_SIGNAL_DUAL_COMBINED,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DUAL_COMBINED));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DEBUG_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_IOS_SIGNAL_DEBUG_ENABLED,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_ENABLED));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL));
        add(cursor, SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL,
                prefs.getInt(SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL,
                        SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL));
        add(cursor, SettingsStore.KEY_IOS_WIFI_DEBUG_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_IOS_WIFI_DEBUG_ENABLED,
                        SettingsStore.DEFAULT_IOS_WIFI_DEBUG_ENABLED));
        add(cursor, SettingsStore.KEY_IOS_WIFI_DEBUG_VISIBLE,
                prefs.getBoolean(SettingsStore.KEY_IOS_WIFI_DEBUG_VISIBLE,
                        SettingsStore.DEFAULT_IOS_WIFI_DEBUG_VISIBLE));
        add(cursor, SettingsStore.KEY_IOS_WIFI_DEBUG_LEVEL,
                prefs.getInt(SettingsStore.KEY_IOS_WIFI_DEBUG_LEVEL,
                        SettingsStore.DEFAULT_IOS_WIFI_DEBUG_LEVEL));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_LEVEL,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_LEVEL, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SLOT,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SLOT, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_STATE,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_STATE, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SOURCE,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SOURCE, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_ERROR,
                prefs.getString(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_ERROR, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SUMMARY,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SUMMARY, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SNAPSHOT,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SNAPSHOT, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_LEVEL,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_LEVEL, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_ID,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_ID, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_NAME,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_NAME, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_VISIBLE,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_VISIBLE, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SOURCE,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SOURCE, ""));
        add(cursor, SettingsStore.KEY_RUNTIME_WIFI_DEBUG_ERROR,
                prefs.getString(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_ERROR, ""));
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
            if (key != null && (key.startsWith("runtime_signal_debug_")
                    || key.startsWith("runtime_wifi_debug_"))) {
                Object value = values.get(key);
                editor.putString(key, value == null ? "" : String.valueOf(value));
                changed++;
            }
        }
        if (changed > 0) {
            editor.apply();
        }
        return changed;
    }
}
