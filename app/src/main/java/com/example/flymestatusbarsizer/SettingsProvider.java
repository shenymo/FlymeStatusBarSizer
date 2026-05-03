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
        add(cursor, SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                        SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED));
        add(cursor, SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                        SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED));
        add(cursor, SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                        SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED));
        add(cursor, SettingsStore.KEY_BATTERY_ICON_STYLE,
                prefs.getInt(SettingsStore.KEY_BATTERY_ICON_STYLE,
                        SettingsStore.DEFAULT_BATTERY_ICON_STYLE));
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
        add(cursor, SettingsStore.KEY_SHOW_CLOCK_WEEKDAY, prefs.getBoolean(SettingsStore.KEY_SHOW_CLOCK_WEEKDAY, SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY));
        add(cursor, SettingsStore.KEY_CLOCK_WEEKDAY_HIDE_PREFIX,
                prefs.getBoolean(SettingsStore.KEY_CLOCK_WEEKDAY_HIDE_PREFIX,
                        SettingsStore.DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX));
        add(cursor, SettingsStore.KEY_CLOCK_BOLD_ENABLED, prefs.getBoolean(SettingsStore.KEY_CLOCK_BOLD_ENABLED, SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED));
        add(cursor, SettingsStore.KEY_CLOCK_FONT_WEIGHT, prefs.getInt(SettingsStore.KEY_CLOCK_FONT_WEIGHT, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT));
        add(cursor, SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                prefs.getBoolean(SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                        SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED));
        add(cursor, SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                prefs.getString(SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                        SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI));
        add(cursor, SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                prefs.getBoolean(SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                        SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT));
        add(cursor, SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                prefs.getBoolean(SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                        SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT));
        add(cursor, SettingsStore.KEY_MBACK_HIDE_PILL,
                prefs.getBoolean(SettingsStore.KEY_MBACK_HIDE_PILL,
                        SettingsStore.DEFAULT_MBACK_HIDE_PILL));
        add(cursor, SettingsStore.KEY_MBACK_INSET_SIZE,
                prefs.getInt(SettingsStore.KEY_MBACK_INSET_SIZE,
                        SettingsStore.DEFAULT_MBACK_INSET_SIZE));
        add(cursor, SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                prefs.getInt(SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
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
            editor.putString(key, value == null ? "" : String.valueOf(value));
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
}
