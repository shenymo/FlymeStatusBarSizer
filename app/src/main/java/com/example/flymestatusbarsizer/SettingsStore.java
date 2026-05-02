package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class SettingsStore {
    static final String AUTHORITY = "com.fiyme.statusbarsizer.settings";
    static final String PREFS = "status_bar_sizer";
    static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY + "/settings");

    static final String KEY_ENABLED = "enabled";
    static final String KEY_GLOBAL_ICON_SCALE = "global_icon_scale";
    static final String KEY_MOBILE_SIGNAL_FACTOR = "mobile_signal_factor";
    static final String KEY_WIFI_SIGNAL_FACTOR = "wifi_signal_factor";
    static final String KEY_BATTERY_FACTOR = "battery_factor";
    static final String KEY_STATUS_ICON_FACTOR = "status_icon_factor";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_X = "ios_signal_desktop_offset_x";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y = "ios_signal_desktop_offset_y";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X = "ios_signal_keyguard_offset_x";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y = "ios_signal_keyguard_offset_y";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X = "ios_signal_control_center_offset_x";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y = "ios_signal_control_center_offset_y";
    static final String KEY_IOS_BATTERY_WIDTH = "ios_battery_width";
    static final String KEY_IOS_BATTERY_HEIGHT = "ios_battery_height";
    static final String KEY_IOS_BATTERY_OFFSET_X = "ios_battery_offset_x";
    static final String KEY_IOS_BATTERY_OFFSET_Y = "ios_battery_offset_y";
    static final String KEY_IOS_BATTERY_TEXT_SIZE = "ios_battery_text_size";
    static final String KEY_IOS_BATTERY_TEXT_WEIGHT = "ios_battery_text_weight";
    static final String KEY_IOS_GROUP_BATTERY_SCALE = "ios_group_battery_scale";
    static final String KEY_IOS_GROUP_SIGNAL_SCALE = "ios_group_signal_scale";
    static final String KEY_IOS_GROUP_WIFI_SCALE = "ios_group_wifi_scale";
    static final String KEY_IOS_GROUP_WIFI_SIGNAL_GAP = "ios_group_wifi_signal_gap";
    static final String KEY_IOS_GROUP_SIGNAL_BATTERY_GAP = "ios_group_signal_battery_gap";
    static final String KEY_IOS_GROUP_START_GAP_ADJUST = "ios_group_start_gap_adjust";
    static final String KEY_IOS_WIFI_WIDTH = "ios_wifi_width";
    static final String KEY_IOS_WIFI_HEIGHT = "ios_wifi_height";
    static final String KEY_IOS_WIFI_OFFSET_X = "ios_wifi_offset_x";
    static final String KEY_IOS_WIFI_OFFSET_Y = "ios_wifi_offset_y";
    static final String KEY_ACTIVITY_ICON_FACTOR = "activity_icon_factor";
    static final String KEY_CONNECTION_RATE_OFFSET_X = "connection_rate_offset_x";
    static final String KEY_CONNECTION_RATE_OFFSET_Y = "connection_rate_offset_y";
    static final String KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = "connection_rate_auto_visibility_enabled";
    static final String KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB = "connection_rate_show_threshold_kb";
    static final String KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB = "connection_rate_hide_threshold_kb";
    static final String KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT = "connection_rate_show_sample_count";
    static final String KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT = "connection_rate_hide_sample_count";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_SHOW_CLOCK_WEEKDAY = "show_clock_weekday";
    static final String KEY_CLOCK_WEEKDAY_HIDE_PREFIX = "clock_weekday_hide_prefix";
    static final String KEY_SHOW_MOBILE_DATA_5G_BADGE = "show_mobile_data_5g_badge";
    static final String KEY_CLOCK_BOLD_ENABLED = "clock_bold_enabled";
    static final String KEY_CLOCK_FONT_WEIGHT = "clock_font_weight";
    static final String KEY_IOS_SIGNAL_DUAL_COMBINED = "ios_signal_dual_combined";
    static final String KEY_IOS_SIGNAL_DEBUG_ENABLED = "ios_signal_debug_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED = "ios_signal_debug_sim1_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED = "ios_signal_debug_sim2_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL = "ios_signal_debug_sim1_level";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL = "ios_signal_debug_sim2_level";
    static final String KEY_IOS_WIFI_DEBUG_ENABLED = "ios_wifi_debug_enabled";
    static final String KEY_IOS_WIFI_DEBUG_VISIBLE = "ios_wifi_debug_visible";
    static final String KEY_IOS_WIFI_DEBUG_LEVEL = "ios_wifi_debug_level";
    static final String KEY_MBACK_LONG_TOUCH_URL_ENABLED = "mback_long_touch_url_enabled";
    static final String KEY_MBACK_LONG_TOUCH_INTENT_URI = "mback_long_touch_intent_uri";
    static final String KEY_MBACK_NAV_BAR_TRANSPARENT = "mback_nav_bar_transparent";
    static final String KEY_MBACK_INSET_SIZE = "mback_inset_size";
    static final String KEY_MBACK_NAV_BAR_HEIGHT = "mback_nav_bar_height";
    static final String KEY_MBACK_HIDE_PILL = "mback_hide_pill";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY = "runtime_signal_debug_summary";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_LEVEL = "runtime_signal_debug_level";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SLOT = "runtime_signal_debug_slot";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID = "runtime_signal_debug_sub_id";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_STATE = "runtime_signal_debug_state";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SOURCE = "runtime_signal_debug_source";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_ERROR = "runtime_signal_debug_error";
    static final String KEY_RUNTIME_WIFI_DEBUG_SUMMARY = "runtime_wifi_debug_summary";
    static final String KEY_RUNTIME_WIFI_DEBUG_SNAPSHOT = "runtime_wifi_debug_snapshot";
    static final String KEY_RUNTIME_WIFI_DEBUG_LEVEL = "runtime_wifi_debug_level";
    static final String KEY_RUNTIME_WIFI_DEBUG_RES_ID = "runtime_wifi_debug_res_id";
    static final String KEY_RUNTIME_WIFI_DEBUG_RES_NAME = "runtime_wifi_debug_res_name";
    static final String KEY_RUNTIME_WIFI_DEBUG_VISIBLE = "runtime_wifi_debug_visible";
    static final String KEY_RUNTIME_WIFI_DEBUG_SOURCE = "runtime_wifi_debug_source";
    static final String KEY_RUNTIME_WIFI_DEBUG_ERROR = "runtime_wifi_debug_error";

    static final boolean DEFAULT_ENABLED = true;
    static final int DEFAULT_GLOBAL_ICON_SCALE = 130;
    static final int DEFAULT_MOBILE_SIGNAL_FACTOR = 50;
    static final int DEFAULT_WIFI_SIGNAL_FACTOR = 70;
    static final int DEFAULT_BATTERY_FACTOR = 70;
    static final int DEFAULT_STATUS_ICON_FACTOR = 46;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X = 6;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y = 4;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y = 2;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y = 2;
    static final int DEFAULT_IOS_BATTERY_WIDTH = 20;
    static final int DEFAULT_IOS_BATTERY_HEIGHT = 16;
    static final int DEFAULT_IOS_BATTERY_OFFSET_X = 0;
    static final int DEFAULT_IOS_BATTERY_OFFSET_Y = 1;
    static final int DEFAULT_IOS_BATTERY_TEXT_SIZE = 75;
    static final int DEFAULT_IOS_BATTERY_TEXT_WEIGHT = 100;
    static final int DEFAULT_IOS_GROUP_BATTERY_SCALE = 100;
    static final int DEFAULT_IOS_GROUP_SIGNAL_SCALE = 74;
    static final int DEFAULT_IOS_GROUP_WIFI_SCALE = 100;
    static final int DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP = 0;
    static final int DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP = 3;
    static final int DEFAULT_IOS_GROUP_START_GAP_ADJUST = 5;
    static final int DEFAULT_IOS_WIFI_WIDTH = 21;
    static final int DEFAULT_IOS_WIFI_HEIGHT = 16;
    static final int DEFAULT_IOS_WIFI_OFFSET_X = 3;
    static final int DEFAULT_IOS_WIFI_OFFSET_Y = 3;
    static final int DEFAULT_ACTIVITY_ICON_FACTOR = 75;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_X = 0;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_Y = -3;
    static final boolean DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = false;
    static final int DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB = 100;
    static final int DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB = 32;
    static final int DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT = 2;
    static final int DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT = 3;
    static final int DEFAULT_TEXT_SCALE = 120;
    static final boolean DEFAULT_SHOW_CLOCK_WEEKDAY = true;
    static final boolean DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX = false;
    static final boolean DEFAULT_SHOW_MOBILE_DATA_5G_BADGE = false;
    static final boolean DEFAULT_CLOCK_BOLD_ENABLED = true;
    static final int DEFAULT_CLOCK_FONT_WEIGHT = 900;
    static final boolean DEFAULT_IOS_SIGNAL_DUAL_COMBINED = true;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_ENABLED = false;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED = true;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED = true;
    static final int DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL = 4;
    static final int DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL = 4;
    static final boolean DEFAULT_IOS_WIFI_DEBUG_ENABLED = false;
    static final boolean DEFAULT_IOS_WIFI_DEBUG_VISIBLE = true;
    static final int DEFAULT_IOS_WIFI_DEBUG_LEVEL = 4;
    static final boolean DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED = false;
    static final String DEFAULT_MBACK_LONG_TOUCH_INTENT_URI = "";
    static final boolean DEFAULT_MBACK_NAV_BAR_TRANSPARENT = false;
    static final int DEFAULT_MBACK_INSET_SIZE = -1;
    static final int DEFAULT_MBACK_NAV_BAR_HEIGHT = -1;
    static final boolean DEFAULT_MBACK_HIDE_PILL = false;
    static final String[] INT_KEYS = {
            KEY_GLOBAL_ICON_SCALE,
            KEY_MOBILE_SIGNAL_FACTOR,
            KEY_WIFI_SIGNAL_FACTOR,
            KEY_BATTERY_FACTOR,
            KEY_STATUS_ICON_FACTOR,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_X,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y,
            KEY_IOS_BATTERY_WIDTH,
            KEY_IOS_BATTERY_HEIGHT,
            KEY_IOS_BATTERY_OFFSET_X,
            KEY_IOS_BATTERY_OFFSET_Y,
            KEY_IOS_BATTERY_TEXT_SIZE,
            KEY_IOS_BATTERY_TEXT_WEIGHT,
            KEY_IOS_GROUP_BATTERY_SCALE,
            KEY_IOS_GROUP_SIGNAL_SCALE,
            KEY_IOS_GROUP_WIFI_SCALE,
            KEY_IOS_GROUP_WIFI_SIGNAL_GAP,
            KEY_IOS_GROUP_SIGNAL_BATTERY_GAP,
            KEY_IOS_GROUP_START_GAP_ADJUST,
            KEY_IOS_WIFI_WIDTH,
            KEY_IOS_WIFI_HEIGHT,
            KEY_IOS_WIFI_OFFSET_X,
            KEY_IOS_WIFI_OFFSET_Y,
            KEY_ACTIVITY_ICON_FACTOR,
            KEY_CONNECTION_RATE_OFFSET_X,
            KEY_CONNECTION_RATE_OFFSET_Y,
            KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
            KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
            KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
            KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
            KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL,
            KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL,
            KEY_IOS_WIFI_DEBUG_LEVEL,
            KEY_TEXT_SCALE,
            KEY_CLOCK_FONT_WEIGHT,
            KEY_MBACK_INSET_SIZE,
            KEY_MBACK_NAV_BAR_HEIGHT
    };

    static final String[] BOOLEAN_KEYS = {
            KEY_ENABLED,
            KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
            KEY_SHOW_CLOCK_WEEKDAY,
            KEY_CLOCK_WEEKDAY_HIDE_PREFIX,
            KEY_SHOW_MOBILE_DATA_5G_BADGE,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_IOS_SIGNAL_DUAL_COMBINED,
            KEY_IOS_SIGNAL_DEBUG_ENABLED,
            KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED,
            KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED,
            KEY_IOS_WIFI_DEBUG_ENABLED,
            KEY_IOS_WIFI_DEBUG_VISIBLE,
            KEY_MBACK_LONG_TOUCH_URL_ENABLED,
            KEY_MBACK_NAV_BAR_TRANSPARENT,
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
            case KEY_GLOBAL_ICON_SCALE:
                return DEFAULT_GLOBAL_ICON_SCALE;
            case KEY_MOBILE_SIGNAL_FACTOR:
                return DEFAULT_MOBILE_SIGNAL_FACTOR;
            case KEY_WIFI_SIGNAL_FACTOR:
                return DEFAULT_WIFI_SIGNAL_FACTOR;
            case KEY_BATTERY_FACTOR:
                return DEFAULT_BATTERY_FACTOR;
            case KEY_STATUS_ICON_FACTOR:
                return DEFAULT_STATUS_ICON_FACTOR;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y;
            case KEY_IOS_BATTERY_WIDTH:
                return DEFAULT_IOS_BATTERY_WIDTH;
            case KEY_IOS_BATTERY_HEIGHT:
                return DEFAULT_IOS_BATTERY_HEIGHT;
            case KEY_IOS_BATTERY_OFFSET_X:
                return DEFAULT_IOS_BATTERY_OFFSET_X;
            case KEY_IOS_BATTERY_OFFSET_Y:
                return DEFAULT_IOS_BATTERY_OFFSET_Y;
            case KEY_IOS_BATTERY_TEXT_SIZE:
                return DEFAULT_IOS_BATTERY_TEXT_SIZE;
            case KEY_IOS_BATTERY_TEXT_WEIGHT:
                return DEFAULT_IOS_BATTERY_TEXT_WEIGHT;
            case KEY_IOS_GROUP_BATTERY_SCALE:
                return DEFAULT_IOS_GROUP_BATTERY_SCALE;
            case KEY_IOS_GROUP_SIGNAL_SCALE:
                return DEFAULT_IOS_GROUP_SIGNAL_SCALE;
            case KEY_IOS_GROUP_WIFI_SCALE:
                return DEFAULT_IOS_GROUP_WIFI_SCALE;
            case KEY_IOS_GROUP_WIFI_SIGNAL_GAP:
                return DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP;
            case KEY_IOS_GROUP_SIGNAL_BATTERY_GAP:
                return DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP;
            case KEY_IOS_GROUP_START_GAP_ADJUST:
                return DEFAULT_IOS_GROUP_START_GAP_ADJUST;
            case KEY_IOS_WIFI_WIDTH:
                return DEFAULT_IOS_WIFI_WIDTH;
            case KEY_IOS_WIFI_HEIGHT:
                return DEFAULT_IOS_WIFI_HEIGHT;
            case KEY_IOS_WIFI_OFFSET_X:
                return DEFAULT_IOS_WIFI_OFFSET_X;
            case KEY_IOS_WIFI_OFFSET_Y:
                return DEFAULT_IOS_WIFI_OFFSET_Y;
            case KEY_ACTIVITY_ICON_FACTOR:
                return DEFAULT_ACTIVITY_ICON_FACTOR;
            case KEY_CONNECTION_RATE_OFFSET_X:
                return DEFAULT_CONNECTION_RATE_OFFSET_X;
            case KEY_CONNECTION_RATE_OFFSET_Y:
                return DEFAULT_CONNECTION_RATE_OFFSET_Y;
            case KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB:
                return DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
            case KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB:
                return DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
            case KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT:
                return DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
            case KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT:
                return DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
            case KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL;
            case KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL;
            case KEY_IOS_WIFI_DEBUG_LEVEL:
                return DEFAULT_IOS_WIFI_DEBUG_LEVEL;
            case KEY_TEXT_SCALE:
                return DEFAULT_TEXT_SCALE;
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
            case KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED:
                return DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
            case KEY_SHOW_CLOCK_WEEKDAY:
                return DEFAULT_SHOW_CLOCK_WEEKDAY;
            case KEY_CLOCK_WEEKDAY_HIDE_PREFIX:
                return DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX;
            case KEY_SHOW_MOBILE_DATA_5G_BADGE:
                return DEFAULT_SHOW_MOBILE_DATA_5G_BADGE;
            case KEY_CLOCK_BOLD_ENABLED:
                return DEFAULT_CLOCK_BOLD_ENABLED;
            case KEY_IOS_SIGNAL_DUAL_COMBINED:
                return DEFAULT_IOS_SIGNAL_DUAL_COMBINED;
            case KEY_IOS_SIGNAL_DEBUG_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_ENABLED;
            case KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED;
            case KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED;
            case KEY_IOS_WIFI_DEBUG_ENABLED:
                return DEFAULT_IOS_WIFI_DEBUG_ENABLED;
            case KEY_IOS_WIFI_DEBUG_VISIBLE:
                return DEFAULT_IOS_WIFI_DEBUG_VISIBLE;
            case KEY_MBACK_LONG_TOUCH_URL_ENABLED:
                return DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
            case KEY_MBACK_NAV_BAR_TRANSPARENT:
                return DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
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

    static boolean includeInBackup(String key) {
        return key != null;
    }
}
