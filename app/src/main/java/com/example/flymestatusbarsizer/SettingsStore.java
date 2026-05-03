package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class SettingsStore {
    static final String AUTHORITY = "com.fiyme.statusbarsizer.settings";
    static final String PREFS = "status_bar_sizer";
    static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY + "/settings");

    static final String KEY_ENABLED = "enabled";
    static final String KEY_BATTERY_CODE_DRAW_ENABLED = "battery_code_draw_enabled";
    static final String KEY_SIGNAL_CODE_DRAW_ENABLED = "signal_code_draw_enabled";
    static final String KEY_BATTERY_ICON_STYLE = "battery_icon_style";
    static final String KEY_BATTERY_LEVEL_TEXT_ENABLED = "battery_level_text_enabled";
    static final String KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = "connection_rate_auto_visibility_enabled";
    static final String KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB = "connection_rate_show_threshold_kb";
    static final String KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB = "connection_rate_hide_threshold_kb";
    static final String KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT = "connection_rate_show_sample_count";
    static final String KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT = "connection_rate_hide_sample_count";
    static final String KEY_SHOW_CLOCK_WEEKDAY = "show_clock_weekday";
    static final String KEY_CLOCK_WEEKDAY_HIDE_PREFIX = "clock_weekday_hide_prefix";
    static final String KEY_CLOCK_BOLD_ENABLED = "clock_bold_enabled";
    static final String KEY_CLOCK_FONT_WEIGHT = "clock_font_weight";
    static final String KEY_MBACK_LONG_TOUCH_URL_ENABLED = "mback_long_touch_url_enabled";
    static final String KEY_MBACK_LONG_TOUCH_INTENT_URI = "mback_long_touch_intent_uri";
    static final String KEY_MBACK_NAV_BAR_TRANSPARENT = "mback_nav_bar_transparent";
    static final String KEY_NOTIFICATION_BACKGROUND_TRANSPARENT = "notification_background_transparent";
    static final String KEY_MBACK_INSET_SIZE = "mback_inset_size";
    static final String KEY_MBACK_NAV_BAR_HEIGHT = "mback_nav_bar_height";
    static final String KEY_MBACK_HIDE_PILL = "mback_hide_pill";
    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_BATTERY_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_CODE_DRAW_ENABLED = true;
    static final int BATTERY_STYLE_IOS = 0;
    static final int BATTERY_STYLE_ONEUI = 1;
    static final int DEFAULT_BATTERY_ICON_STYLE = BATTERY_STYLE_IOS;
    static final boolean DEFAULT_BATTERY_LEVEL_TEXT_ENABLED = true;
    static final boolean DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = false;
    static final int DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB = 100;
    static final int DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB = 32;
    static final int DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT = 2;
    static final int DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT = 3;
    static final boolean DEFAULT_SHOW_CLOCK_WEEKDAY = true;
    static final boolean DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX = false;
    static final boolean DEFAULT_CLOCK_BOLD_ENABLED = true;
    static final int DEFAULT_CLOCK_FONT_WEIGHT = 900;
    static final boolean DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED = false;
    static final String DEFAULT_MBACK_LONG_TOUCH_INTENT_URI = "";
    static final boolean DEFAULT_MBACK_NAV_BAR_TRANSPARENT = false;
    static final boolean DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT = false;
    static final int DEFAULT_MBACK_INSET_SIZE = -1;
    static final int DEFAULT_MBACK_NAV_BAR_HEIGHT = -1;
    static final boolean DEFAULT_MBACK_HIDE_PILL = false;
    static final String[] INT_KEYS = {
            KEY_BATTERY_ICON_STYLE,
            KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
            KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
            KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
            KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
            KEY_CLOCK_FONT_WEIGHT,
            KEY_MBACK_INSET_SIZE,
            KEY_MBACK_NAV_BAR_HEIGHT
    };

    static final String[] BOOLEAN_KEYS = {
            KEY_ENABLED,
            KEY_BATTERY_CODE_DRAW_ENABLED,
            KEY_SIGNAL_CODE_DRAW_ENABLED,
            KEY_BATTERY_LEVEL_TEXT_ENABLED,
            KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
            KEY_SHOW_CLOCK_WEEKDAY,
            KEY_CLOCK_WEEKDAY_HIDE_PREFIX,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_MBACK_LONG_TOUCH_URL_ENABLED,
            KEY_MBACK_NAV_BAR_TRANSPARENT,
            KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
            KEY_MBACK_HIDE_PILL
    };

    static final String[] STRING_KEYS = {
            KEY_MBACK_LONG_TOUCH_INTENT_URI
    };

    private SettingsStore() {
    }

    static SharedPreferences prefs(Context context) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Context deviceContext = appContext.isDeviceProtectedStorage()
                ? appContext
                : appContext.createDeviceProtectedStorageContext();
        if (deviceContext != null) {
            if (appContext != deviceContext) {
                try {
                    deviceContext.moveSharedPreferencesFrom(appContext, PREFS);
                } catch (Throwable ignored) {
                }
            }
            return deviceContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void notifyChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(SETTINGS_URI, null);
        } catch (Throwable ignored) {
        }
    }

    static int defaultInt(String key) {
        switch (key) {
            case KEY_BATTERY_ICON_STYLE:
                return DEFAULT_BATTERY_ICON_STYLE;
            case KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB:
                return DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
            case KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB:
                return DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
            case KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT:
                return DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
            case KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT:
                return DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
            case KEY_CLOCK_FONT_WEIGHT:
                return DEFAULT_CLOCK_FONT_WEIGHT;
            case KEY_MBACK_INSET_SIZE:
                return DEFAULT_MBACK_INSET_SIZE;
            case KEY_MBACK_NAV_BAR_HEIGHT:
                return DEFAULT_MBACK_NAV_BAR_HEIGHT;
            default:
                return 0;
        }
    }

    static boolean defaultBoolean(String key) {
        switch (key) {
            case KEY_ENABLED:
                return DEFAULT_ENABLED;
            case KEY_BATTERY_CODE_DRAW_ENABLED:
                return DEFAULT_BATTERY_CODE_DRAW_ENABLED;
            case KEY_SIGNAL_CODE_DRAW_ENABLED:
                return DEFAULT_SIGNAL_CODE_DRAW_ENABLED;
            case KEY_BATTERY_LEVEL_TEXT_ENABLED:
                return DEFAULT_BATTERY_LEVEL_TEXT_ENABLED;
            case KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED:
                return DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
            case KEY_SHOW_CLOCK_WEEKDAY:
                return DEFAULT_SHOW_CLOCK_WEEKDAY;
            case KEY_CLOCK_WEEKDAY_HIDE_PREFIX:
                return DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX;
            case KEY_CLOCK_BOLD_ENABLED:
                return DEFAULT_CLOCK_BOLD_ENABLED;
            case KEY_MBACK_LONG_TOUCH_URL_ENABLED:
                return DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
            case KEY_MBACK_NAV_BAR_TRANSPARENT:
                return DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
            case KEY_NOTIFICATION_BACKGROUND_TRANSPARENT:
                return DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT;
            case KEY_MBACK_HIDE_PILL:
                return DEFAULT_MBACK_HIDE_PILL;
            default:
                return false;
        }
    }

    static String defaultString(String key) {
        if (KEY_MBACK_LONG_TOUCH_INTENT_URI.equals(key)) {
            return DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
        }
        return "";
    }

    static int normalizeBatteryStyle(int value) {
        return value == BATTERY_STYLE_ONEUI ? BATTERY_STYLE_ONEUI : BATTERY_STYLE_IOS;
    }

    static boolean includeInBackup(String key) {
        return key != null;
    }
}
