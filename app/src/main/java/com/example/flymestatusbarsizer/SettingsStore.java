package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    static final String AUTHORITY = "com.example.flymestatusbarsizer.settings";
    static final String PREFS = "status_bar_sizer";

    static final String KEY_ENABLED = "enabled";
    static final String KEY_GLOBAL_ICON_SCALE = "global_icon_scale";
    static final String KEY_MOBILE_SIGNAL_FACTOR = "mobile_signal_factor";
    static final String KEY_WIFI_SIGNAL_FACTOR = "wifi_signal_factor";
    static final String KEY_BATTERY_FACTOR = "battery_factor";
    static final String KEY_STATUS_ICON_FACTOR = "status_icon_factor";
    static final String KEY_NETWORK_TYPE_FACTOR = "network_type_factor";
    static final String KEY_NETWORK_TYPE_OFFSET_X = "network_type_offset_x";
    static final String KEY_NETWORK_TYPE_OFFSET_Y = "network_type_offset_y";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_X = "network_type_desktop_offset_x";
    static final String KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y = "network_type_desktop_offset_y";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X = "network_type_keyguard_offset_x";
    static final String KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y = "network_type_keyguard_offset_y";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X = "network_type_control_center_offset_x";
    static final String KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y = "network_type_control_center_offset_y";
    static final String KEY_IOS_SIGNAL_OFFSET_X = "ios_signal_offset_x";
    static final String KEY_IOS_SIGNAL_OFFSET_Y = "ios_signal_offset_y";
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
    static final String KEY_ACTIVITY_ICON_FACTOR = "activity_icon_factor";
    static final String KEY_CONNECTION_RATE_OFFSET_X = "connection_rate_offset_x";
    static final String KEY_CONNECTION_RATE_OFFSET_Y = "connection_rate_offset_y";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_HIDE_MOBILE_TYPE = "hide_mobile_type";
    static final String KEY_IOS_BATTERY_STYLE = "ios_battery_style";
    static final String KEY_IOS_SIGNAL_STYLE = "ios_signal_style";
    static final String KEY_IOS_NETWORK_TYPE_STYLE = "ios_network_type_style";

    static final boolean DEFAULT_ENABLED = true;
    static final int DEFAULT_GLOBAL_ICON_SCALE = 115;
    static final int DEFAULT_MOBILE_SIGNAL_FACTOR = 100;
    static final int DEFAULT_WIFI_SIGNAL_FACTOR = 100;
    static final int DEFAULT_BATTERY_FACTOR = 100;
    static final int DEFAULT_STATUS_ICON_FACTOR = 55;
    static final int DEFAULT_NETWORK_TYPE_FACTOR = 65;
    static final int DEFAULT_NETWORK_TYPE_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_OFFSET_Y = 0;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y = 0;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y = 0;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X = 0;
    static final int DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y = 0;
    static final int DEFAULT_IOS_SIGNAL_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_OFFSET_Y = 0;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y = 0;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y = 0;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X = 0;
    static final int DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y = 0;
    static final int DEFAULT_IOS_BATTERY_WIDTH = 28;
    static final int DEFAULT_IOS_BATTERY_HEIGHT = 14;
    static final int DEFAULT_IOS_BATTERY_OFFSET_X = 0;
    static final int DEFAULT_IOS_BATTERY_OFFSET_Y = 0;
    static final int DEFAULT_IOS_BATTERY_TEXT_SIZE = 72;
    static final int DEFAULT_ACTIVITY_ICON_FACTOR = 75;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_X = 0;
    static final int DEFAULT_CONNECTION_RATE_OFFSET_Y = 0;
    static final int DEFAULT_TEXT_SCALE = 100;
    static final boolean DEFAULT_HIDE_MOBILE_TYPE = false;
    static final boolean DEFAULT_IOS_BATTERY_STYLE = false;
    static final boolean DEFAULT_IOS_SIGNAL_STYLE = false;
    static final boolean DEFAULT_IOS_NETWORK_TYPE_STYLE = false;

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
}
