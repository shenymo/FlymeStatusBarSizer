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
    static final String KEY_MOBILE_SIGNAL_FACTOR_OFF = "mobile_signal_factor_off";
    static final String KEY_WIFI_SIGNAL_FACTOR = "wifi_signal_factor";
    static final String KEY_BATTERY_FACTOR = "battery_factor";
    static final String KEY_STATUS_ICON_FACTOR = "status_icon_factor";
    static final String KEY_NETWORK_TYPE_FACTOR = "network_type_factor";
    static final String KEY_NETWORK_TYPE_FACTOR_OFF = "network_type_factor_off";
    static final String KEY_NETWORK_TYPE_OFFSET_X = "network_type_offset_x";
    static final String KEY_NETWORK_TYPE_OFFSET_Y = "network_type_offset_y";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_X = "network_type_desktop_offset_x";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y = "network_type_desktop_offset_y";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF = "network_type_desktop_offset_x_off";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF = "network_type_desktop_offset_y_off";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X = "network_type_keyguard_offset_x";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y = "network_type_keyguard_offset_y";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF = "network_type_keyguard_offset_x_off";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF = "network_type_keyguard_offset_y_off";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X = "network_type_control_center_offset_x";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y = "network_type_control_center_offset_y";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF = "network_type_control_center_offset_x_off";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF = "network_type_control_center_offset_y_off";
    static final String KEY_IOS_SIGNAL_OFFSET_X = "ios_signal_offset_x";
    static final String KEY_IOS_SIGNAL_OFFSET_Y = "ios_signal_offset_y";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_X = "ios_signal_desktop_offset_x";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y = "ios_signal_desktop_offset_y";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF = "ios_signal_desktop_offset_x_off";
    static final String KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF = "ios_signal_desktop_offset_y_off";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X = "ios_signal_keyguard_offset_x";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y = "ios_signal_keyguard_offset_y";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF = "ios_signal_keyguard_offset_x_off";
    static final String KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF = "ios_signal_keyguard_offset_y_off";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X = "ios_signal_control_center_offset_x";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y = "ios_signal_control_center_offset_y";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF = "ios_signal_control_center_offset_x_off";
    static final String KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF = "ios_signal_control_center_offset_y_off";
    static final String KEY_IOS_BATTERY_WIDTH = "ios_battery_width";
    static final String KEY_IOS_BATTERY_HEIGHT = "ios_battery_height";
    static final String KEY_IOS_BATTERY_OFFSET_X = "ios_battery_offset_x";
    static final String KEY_IOS_BATTERY_OFFSET_Y = "ios_battery_offset_y";
    static final String KEY_IOS_BATTERY_TEXT_SIZE = "ios_battery_text_size";
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
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_SHOW_CLOCK_WEEKDAY = "show_clock_weekday";
    static final String KEY_CLOCK_BOLD_ENABLED = "clock_bold_enabled";
    static final String KEY_CLOCK_FONT_WEIGHT = "clock_font_weight";
    static final String KEY_IOS_BATTERY_STYLE = "ios_battery_style";
    static final String KEY_IOS_SIGNAL_STYLE = "ios_signal_style";
    static final String KEY_IOS_SIGNAL_DUAL_COMBINED = "ios_signal_dual_combined";
    static final String KEY_IOS_SIGNAL_DEBUG_ENABLED = "ios_signal_debug_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED = "ios_signal_debug_sim1_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED = "ios_signal_debug_sim2_enabled";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL = "ios_signal_debug_sim1_level";
    static final String KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL = "ios_signal_debug_sim2_level";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY = "runtime_signal_debug_summary";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_LEVEL = "runtime_signal_debug_level";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SLOT = "runtime_signal_debug_slot";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID = "runtime_signal_debug_sub_id";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_STATE = "runtime_signal_debug_state";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_SOURCE = "runtime_signal_debug_source";
    static final String KEY_RUNTIME_SIGNAL_DEBUG_ERROR = "runtime_signal_debug_error";
    static final String KEY_IOS_NETWORK_TYPE_STYLE = "ios_network_type_style";
    static final String KEY_IOS_WIFI_STYLE = "ios_wifi_style";

    static final boolean DEFAULT_ENABLED = true;
    static final int DEFAULT_GLOBAL_ICON_SCALE = 125;
    static final int DEFAULT_MOBILE_SIGNAL_FACTOR = 50;
    static final int DEFAULT_MOBILE_SIGNAL_FACTOR_OFF = DEFAULT_MOBILE_SIGNAL_FACTOR;
    static final int DEFAULT_WIFI_SIGNAL_FACTOR = 70;
    static final int DEFAULT_BATTERY_FACTOR = 100;
    static final int DEFAULT_STATUS_ICON_FACTOR = 46;
    static final int DEFAULT_NETWORK_TYPE_FACTOR = 145;
    static final int DEFAULT_NETWORK_TYPE_FACTOR_OFF = DEFAULT_NETWORK_TYPE_FACTOR;
    static final int DEFAULT_NETWORK_TYPE_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_OFFSET_Y = -6;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y = -3;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF = DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF = DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y = -3;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF = DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF = DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y = -3;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF = DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF = DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y;
    static final int DEFAULT_IOS_SIGNAL_OFFSET_X = 6;
    static final int DEFAULT_IOS_SIGNAL_OFFSET_Y = 0;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X = 8;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y = 6;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF = DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF = DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y = 2;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF = DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF = DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y = 2;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF = DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF = DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y;
    static final int DEFAULT_IOS_BATTERY_WIDTH = 20;
    static final int DEFAULT_IOS_BATTERY_HEIGHT = 16;
    static final int DEFAULT_IOS_BATTERY_OFFSET_X = 0;
    static final int DEFAULT_IOS_BATTERY_OFFSET_Y = 1;
    static final int DEFAULT_IOS_BATTERY_TEXT_SIZE = 81;
    static final int DEFAULT_IOS_GROUP_BATTERY_SCALE = 100;
    static final int DEFAULT_IOS_GROUP_SIGNAL_SCALE = 100;
    static final int DEFAULT_IOS_GROUP_WIFI_SCALE = 100;
    static final int DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP = 2;
    static final int DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP = 2;
    static final int DEFAULT_IOS_GROUP_START_GAP_ADJUST = 0;
    static final int DEFAULT_IOS_WIFI_WIDTH = 21;
    static final int DEFAULT_IOS_WIFI_HEIGHT = 16;
    static final int DEFAULT_IOS_WIFI_OFFSET_X = 2;
    static final int DEFAULT_IOS_WIFI_OFFSET_Y = 1;
    static final int DEFAULT_ACTIVITY_ICON_FACTOR = 75;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_X = 0;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_Y = -3;
    static final int DEFAULT_TEXT_SCALE = 118;
    static final boolean DEFAULT_SHOW_CLOCK_WEEKDAY = true;
    static final boolean DEFAULT_CLOCK_BOLD_ENABLED = false;
    static final int DEFAULT_CLOCK_FONT_WEIGHT = 700;
    static final boolean DEFAULT_IOS_BATTERY_STYLE = true;
    static final boolean DEFAULT_IOS_SIGNAL_STYLE = true;
    static final boolean DEFAULT_IOS_SIGNAL_DUAL_COMBINED = true;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_ENABLED = false;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED = true;
    static final boolean DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED = true;
    static final int DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL = 4;
    static final int DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL = 4;
    static final boolean DEFAULT_IOS_NETWORK_TYPE_STYLE = true;
    static final boolean DEFAULT_IOS_WIFI_STYLE = true;

    static final String[] INT_KEYS = {
            KEY_GLOBAL_ICON_SCALE,
            KEY_MOBILE_SIGNAL_FACTOR,
            KEY_MOBILE_SIGNAL_FACTOR_OFF,
            KEY_WIFI_SIGNAL_FACTOR,
            KEY_BATTERY_FACTOR,
            KEY_STATUS_ICON_FACTOR,
            KEY_NETWORK_TYPE_FACTOR,
            KEY_NETWORK_TYPE_FACTOR_OFF,
            KEY_NETWORK_TYPE_OFFSET_X,
            KEY_NETWORK_TYPE_OFFSET_Y,
            KEY_NETWORK_TYPE_DESKTOP_OFFSET_X,
            KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y,
            KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF,
            KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF,
            KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X,
            KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y,
            KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF,
            KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF,
            KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X,
            KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y,
            KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF,
            KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF,
            KEY_IOS_SIGNAL_OFFSET_X,
            KEY_IOS_SIGNAL_OFFSET_Y,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_X,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF,
            KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF,
            KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF,
            KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF,
            KEY_IOS_BATTERY_WIDTH,
            KEY_IOS_BATTERY_HEIGHT,
            KEY_IOS_BATTERY_OFFSET_X,
            KEY_IOS_BATTERY_OFFSET_Y,
            KEY_IOS_BATTERY_TEXT_SIZE,
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
            KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL,
            KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL,
            KEY_TEXT_SCALE,
            KEY_CLOCK_FONT_WEIGHT
    };

    static final String[] BOOLEAN_KEYS = {
            KEY_ENABLED,
            KEY_SHOW_CLOCK_WEEKDAY,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_IOS_BATTERY_STYLE,
            KEY_IOS_SIGNAL_STYLE,
            KEY_IOS_SIGNAL_DUAL_COMBINED,
            KEY_IOS_SIGNAL_DEBUG_ENABLED,
            KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED,
            KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED,
            KEY_IOS_NETWORK_TYPE_STYLE,
            KEY_IOS_WIFI_STYLE
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
            case KEY_MOBILE_SIGNAL_FACTOR_OFF:
                return DEFAULT_MOBILE_SIGNAL_FACTOR_OFF;
            case KEY_WIFI_SIGNAL_FACTOR:
                return DEFAULT_WIFI_SIGNAL_FACTOR;
            case KEY_BATTERY_FACTOR:
                return DEFAULT_BATTERY_FACTOR;
            case KEY_STATUS_ICON_FACTOR:
                return DEFAULT_STATUS_ICON_FACTOR;
            case KEY_NETWORK_TYPE_FACTOR:
                return DEFAULT_NETWORK_TYPE_FACTOR;
            case KEY_NETWORK_TYPE_FACTOR_OFF:
                return DEFAULT_NETWORK_TYPE_FACTOR_OFF;
            case KEY_NETWORK_TYPE_OFFSET_X:
                return DEFAULT_NETWORK_TYPE_OFFSET_X;
            case KEY_NETWORK_TYPE_OFFSET_Y:
                return DEFAULT_NETWORK_TYPE_OFFSET_Y;
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_X:
                return DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X;
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y:
                return DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y;
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF:
                return DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF;
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF:
                return DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF;
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X:
                return DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X;
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y:
                return DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y;
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF:
                return DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF;
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF:
                return DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF;
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X:
                return DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X;
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y:
                return DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y;
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF:
                return DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF;
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF:
                return DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF;
            case KEY_IOS_SIGNAL_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_OFFSET_X;
            case KEY_IOS_SIGNAL_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_OFFSET_Y;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X_OFF;
            case KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF:
                return DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y_OFF;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X_OFF;
            case KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF:
                return DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y_OFF;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X_OFF;
            case KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF:
                return DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y_OFF;
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
            case KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL;
            case KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL;
            case KEY_TEXT_SCALE:
                return DEFAULT_TEXT_SCALE;
            case KEY_CLOCK_FONT_WEIGHT:
                return DEFAULT_CLOCK_FONT_WEIGHT;
            default:
                return 0;
        }
    }

    static boolean defaultBoolean(String key) {
        switch (key) {
            case KEY_ENABLED:
                return DEFAULT_ENABLED;
            case KEY_SHOW_CLOCK_WEEKDAY:
                return DEFAULT_SHOW_CLOCK_WEEKDAY;
            case KEY_CLOCK_BOLD_ENABLED:
                return DEFAULT_CLOCK_BOLD_ENABLED;
            case KEY_IOS_BATTERY_STYLE:
                return DEFAULT_IOS_BATTERY_STYLE;
            case KEY_IOS_SIGNAL_STYLE:
                return DEFAULT_IOS_SIGNAL_STYLE;
            case KEY_IOS_SIGNAL_DUAL_COMBINED:
                return DEFAULT_IOS_SIGNAL_DUAL_COMBINED;
            case KEY_IOS_SIGNAL_DEBUG_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_ENABLED;
            case KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED;
            case KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED:
                return DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED;
            case KEY_IOS_NETWORK_TYPE_STYLE:
                return DEFAULT_IOS_NETWORK_TYPE_STYLE;
            case KEY_IOS_WIFI_STYLE:
                return DEFAULT_IOS_WIFI_STYLE;
            default:
                return false;
        }
    }

    static boolean includeInBackup(String key) {
        if (key == null) {
            return false;
        }
        switch (key) {
            case KEY_NETWORK_TYPE_FACTOR:
            case KEY_NETWORK_TYPE_FACTOR_OFF:
            case KEY_NETWORK_TYPE_OFFSET_X:
            case KEY_NETWORK_TYPE_OFFSET_Y:
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_X:
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y:
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_X_OFF:
            case KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y_OFF:
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X:
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y:
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X_OFF:
            case KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y_OFF:
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X:
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y:
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X_OFF:
            case KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y_OFF:
            case KEY_IOS_BATTERY_STYLE:
            case KEY_IOS_SIGNAL_STYLE:
            case KEY_IOS_NETWORK_TYPE_STYLE:
            case KEY_IOS_WIFI_STYLE:
                return false;
            default:
                return true;
        }
    }
}
