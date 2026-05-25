package com.example.flymestatusbarsizer;

import com.example.flymestatusbarsizer.feature.clock.ClockDetailActionCodec;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.Map;

final class SettingsStore {
    static final String PREFS = "status_bar_sizer";
    static final String KEY_POSITION_OFFSET_STORAGE_VERSION = "__position_offset_storage_version";
    static final int POSITION_OFFSET_STORAGE_VERSION_LEGACY_DP = 0;
    static final int POSITION_OFFSET_STORAGE_VERSION_TENTH_DP = 1;
    static final int POSITION_OFFSET_MIN_TENTH_DP = -240;
    static final int POSITION_OFFSET_MAX_TENTH_DP = 240;

    static final String KEY_ENABLED = "enabled";
    static final String KEY_BATTERY_CODE_DRAW_ENABLED = "battery_code_draw_enabled";
    static final String KEY_SIGNAL_CODE_DRAW_ENABLED = "signal_code_draw_enabled";
    static final String KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED = "signal_mobile_type_badge_enabled";
    static final String KEY_WIFI_CODE_DRAW_ENABLED = "wifi_code_draw_enabled";
    static final String KEY_SIGNAL_WIFI_SWAP_ENABLED = "signal_wifi_swap_enabled";
    static final String KEY_BATTERY_ICON_STYLE = "battery_icon_style";
    static final String KEY_BATTERY_LEVEL_TEXT_ENABLED = "battery_level_text_enabled";
    static final String KEY_BATTERY_HOLLOW_ENABLED = "battery_hollow_enabled";
    static final String KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL = "battery_hollow_fill_follows_level";
    static final String KEY_BATTERY_TEXT_FONT = "battery_text_font";
    static final String KEY_STATUS_BAR_ICON_SCALE_PERCENT = "status_bar_icon_scale_percent";
    static final String KEY_BATTERY_INNER_TEXT_SCALE_PERCENT = "battery_inner_text_scale_percent";
    static final String KEY_BATTERY_ICON_Y_OFFSET_DP = "battery_icon_y_offset_dp";
    static final String KEY_BATTERY_TEXT_Y_OFFSET_DP = "battery_text_y_offset_dp";
    static final String KEY_BATTERY_BOLT_Y_OFFSET_DP = "battery_bolt_y_offset_dp";
    static final String KEY_SIGNAL_SINGLE_Y_OFFSET_DP = "signal_single_y_offset_dp";
    static final String KEY_SIGNAL_BADGE_Y_OFFSET_DP = "signal_badge_y_offset_dp";
    static final String KEY_SIGNAL_DUAL_Y_OFFSET_DP = "signal_dual_y_offset_dp";
    static final String KEY_WIFI_Y_OFFSET_DP = "wifi_y_offset_dp";
    static final String KEY_CLOCK_RIGHT_PADDING_OFFSET_DP = "clock_right_padding_offset_dp";
    static final String KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = "connection_rate_auto_visibility_enabled";
    static final String KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB = "connection_rate_show_threshold_kb";
    static final String KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB = "connection_rate_hide_threshold_kb";
    static final String KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT = "connection_rate_show_sample_count";
    static final String KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT = "connection_rate_hide_sample_count";
    static final String KEY_CLOCK_CUSTOM_FORMAT = "clock_custom_format";
    static final String KEY_CLOCK_EXPRESSION_TOKEN_ORDER = "clock_expression_token_order";
    static final String KEY_CLOCK_BOLD_ENABLED = "clock_bold_enabled";
    static final String KEY_CLOCK_FONT_WEIGHT = "clock_font_weight";
    static final String KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT = "clock_and_carrier_text_size_percent";
    static final String KEY_CLOCK_DETAIL_POPUP_ENABLED = "clock_detail_popup_enabled";
    static final String KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED = "clock_detail_action_grid_enabled";
    static final String KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON = "clock_detail_action_grid_items_json";
    static final String KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON =
            "clock_detail_assistant_action_cache_json";
    static final String KEY_MBACK_LONG_TOUCH_URL_ENABLED = "mback_long_touch_url_enabled";
    static final String KEY_MBACK_LONG_TOUCH_ACTION = "mback_long_touch_action";
    static final String KEY_MBACK_LONG_TOUCH_INTENT_URI = "mback_long_touch_intent_uri";
    static final String KEY_MBACK_NAV_BAR_TRANSPARENT = "mback_nav_bar_transparent";
    static final String KEY_NOTIFICATION_APP_ICON_ENABLED = "notification_app_icon_enabled";
    static final String KEY_NOTIFICATION_APP_ICON_SIZE_DP = "notification_app_icon_size_dp";
    static final String KEY_NOTIFICATION_APP_ICON_PADDING_DP = "notification_app_icon_padding_dp";
    static final String KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED =
            "launcher_ios_stack_recents_enabled";
    static final String KEY_LAUNCHER_FOLDER_BG_COLOR = "launcher_folder_bg_color";
    static final String KEY_NOTIFICATION_BACKGROUND_COLOR = "notification_background_color";
    static final String KEY_MBACK_INSET_SIZE = "mback_inset_size";
    static final String KEY_MBACK_NAV_BAR_HEIGHT = "mback_nav_bar_height";
    static final String KEY_MBACK_HIDE_PILL = "mback_hide_pill";
    static final String KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR = "ime_force_stock_control_bar";
    static final String KEY_IME_CONTROL_BAR_BUTTON_SLOTS = "ime_control_bar_button_slots";
    static final String KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT = "ime_control_bar_icon_scale_percent";
    static final String KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT = "ime_control_bar_icon_alpha_percent";
    static final String KEY_IME_CONTROL_BAR_Y_OFFSET_DP = "ime_control_bar_y_offset_dp";
    static final String KEY_IME_CONTROL_BAR_BUTTON_ORDER = "ime_toolbar_order";
    static final String KEY_IME_CONTROL_BAR_HIDDEN_BUTTONS = "ime_control_bar_hidden_buttons";
    static final String KEY_IME_CONTROL_BAR_ALIGNMENT = "ime_control_bar_alignment";
    static final String KEY_TELEPHONY_DEBUG_ENABLED = "telephony_debug_enabled";
    static final String KEY_WIFI_PERF_LOGGING_ENABLED = "wifi_perf_logging_enabled";
    static final String KEY_TELEPHONY_DEBUG_SIM_COUNT = "telephony_debug_sim_count";
    static final String KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT = "telephony_debug_default_data_slot";
    static final String KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE = "telephony_debug_slot1_network_profile";
    static final String KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL = "telephony_debug_slot1_signal_level";
    static final String KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE = "telephony_debug_slot2_network_profile";
    static final String KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL = "telephony_debug_slot2_signal_level";
    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_BATTERY_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED = true;
    static final boolean DEFAULT_WIFI_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_WIFI_SWAP_ENABLED = false;
    static final int BATTERY_STYLE_IOS = 0;
    static final int BATTERY_STYLE_ONEUI = 1;
    static final int BATTERY_TEXT_FONT_SYSTEM_DEFAULT = 0;
    static final int BATTERY_TEXT_FONT_SERIF = 1;
    static final int BATTERY_TEXT_FONT_MONOSPACE = 2;
    static final int BATTERY_TEXT_FONT_SANS_SERIF = 3;
    static final int BATTERY_TEXT_FONT_SANS_SERIF_MEDIUM = 4;
    static final int BATTERY_TEXT_FONT_SANS_SERIF_CONDENSED = 5;
    static final int BATTERY_TEXT_FONT_MI_SANS_LATIN_VF_NUMBER = 6;
    static final int DEFAULT_BATTERY_ICON_STYLE = BATTERY_STYLE_IOS;
    static final boolean DEFAULT_BATTERY_LEVEL_TEXT_ENABLED = true;
    static final boolean DEFAULT_BATTERY_HOLLOW_ENABLED = false;
    static final boolean DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL = false;
    static final int DEFAULT_BATTERY_TEXT_FONT = BATTERY_TEXT_FONT_SYSTEM_DEFAULT;
    static final int DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT = 100;
    static final int DEFAULT_BATTERY_ICON_Y_OFFSET_DP = 0;
    static final int DEFAULT_BATTERY_TEXT_Y_OFFSET_DP = 0;
    static final int DEFAULT_BATTERY_BOLT_Y_OFFSET_DP = 0;
    static final int DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP = 0;
    static final int DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP = 0;
    static final int DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP = 0;
    static final int DEFAULT_WIFI_Y_OFFSET_DP = 0;
    static final int DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP = 0;
    static final int CLOCK_RIGHT_PADDING_OFFSET_MIN_TENTH_DP = -30;
    static final int CLOCK_RIGHT_PADDING_OFFSET_MAX_TENTH_DP = 240;
    static final boolean DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = false;
    static final int DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB = 100;
    static final int DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB = 32;
    static final int DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT = 2;
    static final int DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT = 3;
    static final String DEFAULT_CLOCK_CUSTOM_FORMAT = "";
    static final String DEFAULT_CLOCK_EXPRESSION_TOKEN_ORDER = "";
    static final boolean DEFAULT_CLOCK_BOLD_ENABLED = true;
    static final int DEFAULT_CLOCK_FONT_WEIGHT = 900;
    static final int DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT = 100;
    static final boolean DEFAULT_CLOCK_DETAIL_POPUP_ENABLED = false;
    static final boolean DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED = false;
    static final String DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON =
            ClockDetailActionCodec.DEFAULT_PRESET_JSON;
    static final String DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON = "";
    static final boolean DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED = false;
    static final int MBACK_LONG_TOUCH_ACTION_INTENT_URI = 0;
    static final int MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP = 1;
    static final int MBACK_LONG_TOUCH_ACTION_STAR_APPS = 2;
    static final int DEFAULT_MBACK_LONG_TOUCH_ACTION = MBACK_LONG_TOUCH_ACTION_STAR_APPS;
    static final String DEFAULT_MBACK_LONG_TOUCH_INTENT_URI = "";
    static final boolean DEFAULT_MBACK_NAV_BAR_TRANSPARENT = false;
    static final boolean DEFAULT_NOTIFICATION_APP_ICON_ENABLED = false;
    static final int DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP = 20;
    static final int DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP = 0;
    static final boolean DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED = false;
    static final String DEFAULT_LAUNCHER_FOLDER_BG_COLOR = "";
    static final String DEFAULT_NOTIFICATION_BACKGROUND_COLOR = "#1A000000";
    static final int DEFAULT_MBACK_INSET_SIZE = -1;
    static final int DEFAULT_MBACK_NAV_BAR_HEIGHT = -1;
    static final boolean DEFAULT_MBACK_HIDE_PILL = false;
    static final boolean DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR = false;
    static final String DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS =
            "paste,undo,delete,select_all,copy,switch_ime,stock_back";
    static final int DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT = 100;
    static final int DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP = 0;
    static final boolean DEFAULT_TELEPHONY_DEBUG_ENABLED = false;
    static final boolean DEFAULT_WIFI_PERF_LOGGING_ENABLED = false;
    static final int DEFAULT_TELEPHONY_DEBUG_SIM_COUNT = 2;
    static final int TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE = -1;
    static final int TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1 = 0;
    static final int TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD2 = 1;
    static final int DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT = TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE = 0;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_2G = 1;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_3G = 2;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_4G = 3;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_5G = 4;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA = 5;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_5GA = 6;
    static final int TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS = 7;
    static final int DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE = TELEPHONY_DEBUG_NETWORK_PROFILE_5G;
    static final int DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL = 4;
    static final int DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE = TELEPHONY_DEBUG_NETWORK_PROFILE_4G;
    static final int DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL = 2;
    static final String[] INT_KEYS = {
            KEY_POSITION_OFFSET_STORAGE_VERSION,
            KEY_BATTERY_ICON_STYLE,
            KEY_BATTERY_TEXT_FONT,
            KEY_STATUS_BAR_ICON_SCALE_PERCENT,
            KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
            KEY_BATTERY_ICON_Y_OFFSET_DP,
            KEY_BATTERY_TEXT_Y_OFFSET_DP,
            KEY_BATTERY_BOLT_Y_OFFSET_DP,
            KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
            KEY_SIGNAL_BADGE_Y_OFFSET_DP,
            KEY_SIGNAL_DUAL_Y_OFFSET_DP,
            KEY_WIFI_Y_OFFSET_DP,
            KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
            KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
            KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
            KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
            KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
            KEY_CLOCK_FONT_WEIGHT,
            KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
            KEY_MBACK_LONG_TOUCH_ACTION,
            KEY_NOTIFICATION_APP_ICON_SIZE_DP,
            KEY_NOTIFICATION_APP_ICON_PADDING_DP,
            KEY_MBACK_INSET_SIZE,
            KEY_MBACK_NAV_BAR_HEIGHT,
            KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
            KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
            KEY_IME_CONTROL_BAR_Y_OFFSET_DP,
            KEY_TELEPHONY_DEBUG_SIM_COUNT,
            KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
            KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
            KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL,
            KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
            KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL
    };

    static final String[] BOOLEAN_KEYS = {
            KEY_ENABLED,
            KEY_BATTERY_CODE_DRAW_ENABLED,
            KEY_SIGNAL_CODE_DRAW_ENABLED,
            KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
            KEY_WIFI_CODE_DRAW_ENABLED,
            KEY_SIGNAL_WIFI_SWAP_ENABLED,
            KEY_BATTERY_LEVEL_TEXT_ENABLED,
            KEY_BATTERY_HOLLOW_ENABLED,
            KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
            KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_CLOCK_DETAIL_POPUP_ENABLED,
            KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
            KEY_MBACK_LONG_TOUCH_URL_ENABLED,
            KEY_MBACK_NAV_BAR_TRANSPARENT,
            KEY_NOTIFICATION_APP_ICON_ENABLED,
            KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
            KEY_MBACK_HIDE_PILL,
            KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR,
            KEY_TELEPHONY_DEBUG_ENABLED,
            KEY_WIFI_PERF_LOGGING_ENABLED
    };

    static final String[] STRING_KEYS = {
            KEY_CLOCK_CUSTOM_FORMAT,
            KEY_CLOCK_EXPRESSION_TOKEN_ORDER,
            KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
            KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON,
            KEY_MBACK_LONG_TOUCH_INTENT_URI,
            KEY_LAUNCHER_FOLDER_BG_COLOR,
            KEY_NOTIFICATION_BACKGROUND_COLOR,
            KEY_IME_CONTROL_BAR_BUTTON_SLOTS
    };

    static final String[] POSITION_OFFSET_KEYS = {
            KEY_BATTERY_ICON_Y_OFFSET_DP,
            KEY_BATTERY_TEXT_Y_OFFSET_DP,
            KEY_BATTERY_BOLT_Y_OFFSET_DP,
            KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
            KEY_SIGNAL_BADGE_Y_OFFSET_DP,
            KEY_SIGNAL_DUAL_Y_OFFSET_DP,
            KEY_WIFI_Y_OFFSET_DP,
            KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
            KEY_IME_CONTROL_BAR_Y_OFFSET_DP
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

    static void prepareRemoteSync(Context context) {
        migratePositionOffsetStorageIfNeeded(context);
        RemoteSettingsSync.prepare(context);
    }

    static void notifyChanged(Context context) {
        RemoteSettingsSync.syncFromLocal(context);
    }

    static boolean readBoolean(SharedPreferences prefs, String key, boolean defaultValue) {
        Object raw = getRawValue(prefs, key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return defaultValue;
    }

    static int readInt(SharedPreferences prefs, String key, int defaultValue) {
        Object raw = getRawValue(prefs, key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static String readString(SharedPreferences prefs, String key, String defaultValue) {
        Object raw = getRawValue(prefs, key);
        if (raw == null) {
            return defaultValue;
        }
        return String.valueOf(raw);
    }

    static boolean hasExplicitBooleanTrue(SharedPreferences prefs, String key) {
        Object raw = getRawValue(prefs, key);
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            return "1".equals(text) || "true".equalsIgnoreCase(text);
        }
        return false;
    }

    private static Object getRawValue(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) {
            return null;
        }
        Map<String, ?> all = prefs.getAll();
        return all != null ? all.get(key) : null;
    }

    static int defaultInt(String key) {
        switch (key) {
            case KEY_POSITION_OFFSET_STORAGE_VERSION:
                return POSITION_OFFSET_STORAGE_VERSION_TENTH_DP;
            case KEY_BATTERY_ICON_STYLE:
                return DEFAULT_BATTERY_ICON_STYLE;
            case KEY_BATTERY_TEXT_FONT:
                return DEFAULT_BATTERY_TEXT_FONT;
            case KEY_STATUS_BAR_ICON_SCALE_PERCENT:
                return DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
            case KEY_BATTERY_INNER_TEXT_SCALE_PERCENT:
                return DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
            case KEY_BATTERY_ICON_Y_OFFSET_DP:
                return DEFAULT_BATTERY_ICON_Y_OFFSET_DP;
            case KEY_BATTERY_TEXT_Y_OFFSET_DP:
                return DEFAULT_BATTERY_TEXT_Y_OFFSET_DP;
            case KEY_BATTERY_BOLT_Y_OFFSET_DP:
                return DEFAULT_BATTERY_BOLT_Y_OFFSET_DP;
            case KEY_SIGNAL_SINGLE_Y_OFFSET_DP:
                return DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP;
            case KEY_SIGNAL_BADGE_Y_OFFSET_DP:
                return DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP;
            case KEY_SIGNAL_DUAL_Y_OFFSET_DP:
                return DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP;
            case KEY_WIFI_Y_OFFSET_DP:
                return DEFAULT_WIFI_Y_OFFSET_DP;
            case KEY_CLOCK_RIGHT_PADDING_OFFSET_DP:
                return DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP;
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
            case KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT:
                return DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT;
            case KEY_MBACK_LONG_TOUCH_ACTION:
                return DEFAULT_MBACK_LONG_TOUCH_ACTION;
            case KEY_NOTIFICATION_APP_ICON_SIZE_DP:
                return DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP;
            case KEY_NOTIFICATION_APP_ICON_PADDING_DP:
                return DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP;
            case KEY_MBACK_INSET_SIZE:
                return DEFAULT_MBACK_INSET_SIZE;
            case KEY_MBACK_NAV_BAR_HEIGHT:
                return DEFAULT_MBACK_NAV_BAR_HEIGHT;
            case KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT:
                return DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT;
            case KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT:
                return DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT;
            case KEY_IME_CONTROL_BAR_Y_OFFSET_DP:
                return DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP;
            case KEY_TELEPHONY_DEBUG_SIM_COUNT:
                return DEFAULT_TELEPHONY_DEBUG_SIM_COUNT;
            case KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT:
                return DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT;
            case KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE:
                return DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE;
            case KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL:
                return DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL;
            case KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE:
                return DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE;
            case KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL:
                return DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL;
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
            case KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED:
                return DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED;
            case KEY_WIFI_CODE_DRAW_ENABLED:
                return DEFAULT_WIFI_CODE_DRAW_ENABLED;
            case KEY_SIGNAL_WIFI_SWAP_ENABLED:
                return DEFAULT_SIGNAL_WIFI_SWAP_ENABLED;
            case KEY_BATTERY_LEVEL_TEXT_ENABLED:
                return DEFAULT_BATTERY_LEVEL_TEXT_ENABLED;
            case KEY_BATTERY_HOLLOW_ENABLED:
                return DEFAULT_BATTERY_HOLLOW_ENABLED;
            case KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL:
                return DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL;
            case KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED:
                return DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
            case KEY_CLOCK_BOLD_ENABLED:
                return DEFAULT_CLOCK_BOLD_ENABLED;
            case KEY_CLOCK_DETAIL_POPUP_ENABLED:
                return DEFAULT_CLOCK_DETAIL_POPUP_ENABLED;
            case KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED:
                return DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED;
            case KEY_MBACK_LONG_TOUCH_URL_ENABLED:
                return DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
            case KEY_MBACK_NAV_BAR_TRANSPARENT:
                return DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
            case KEY_NOTIFICATION_APP_ICON_ENABLED:
                return DEFAULT_NOTIFICATION_APP_ICON_ENABLED;
            case KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED:
                return DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED;
            case KEY_MBACK_HIDE_PILL:
                return DEFAULT_MBACK_HIDE_PILL;
            case KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR:
                return DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR;
            case KEY_TELEPHONY_DEBUG_ENABLED:
                return DEFAULT_TELEPHONY_DEBUG_ENABLED;
            case KEY_WIFI_PERF_LOGGING_ENABLED:
                return DEFAULT_WIFI_PERF_LOGGING_ENABLED;
            default:
                return false;
        }
    }

    static String defaultString(String key) {
        if (KEY_CLOCK_CUSTOM_FORMAT.equals(key)) {
            return DEFAULT_CLOCK_CUSTOM_FORMAT;
        }
        if (KEY_CLOCK_EXPRESSION_TOKEN_ORDER.equals(key)) {
            return DEFAULT_CLOCK_EXPRESSION_TOKEN_ORDER;
        }
        if (KEY_MBACK_LONG_TOUCH_INTENT_URI.equals(key)) {
            return DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
        }
        if (KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON.equals(key)) {
            return DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON;
        }
        if (KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON.equals(key)) {
            return DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON;
        }
        if (KEY_LAUNCHER_FOLDER_BG_COLOR.equals(key)) {
            return DEFAULT_LAUNCHER_FOLDER_BG_COLOR;
        }
        if (KEY_NOTIFICATION_BACKGROUND_COLOR.equals(key)) {
            return DEFAULT_NOTIFICATION_BACKGROUND_COLOR;
        }
        if (KEY_IME_CONTROL_BAR_BUTTON_SLOTS.equals(key)) {
            return DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;
        }
        return "";
    }

    static int normalizeBatteryStyle(int value) {
        return value == BATTERY_STYLE_ONEUI ? BATTERY_STYLE_ONEUI : BATTERY_STYLE_IOS;
    }

    static int normalizeMBackLongTouchAction(int value) {
        switch (value) {
            case MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP:
            case MBACK_LONG_TOUCH_ACTION_STAR_APPS:
                return value;
            default:
                return MBACK_LONG_TOUCH_ACTION_INTENT_URI;
        }
    }

    static int normalizeBatteryTextFont(int value) {
        switch (value) {
            case BATTERY_TEXT_FONT_SERIF:
            case BATTERY_TEXT_FONT_MONOSPACE:
            case BATTERY_TEXT_FONT_SANS_SERIF:
            case BATTERY_TEXT_FONT_SANS_SERIF_MEDIUM:
            case BATTERY_TEXT_FONT_SANS_SERIF_CONDENSED:
            case BATTERY_TEXT_FONT_MI_SANS_LATIN_VF_NUMBER:
                return value;
            default:
                return BATTERY_TEXT_FONT_SYSTEM_DEFAULT;
        }
    }

    static int normalizeScalePercent(int value) {
        return Math.max(50, Math.min(200, value));
    }

    static int normalizeIconYOffsetDp(int value) {
        return Math.max(-24, Math.min(24, value));
    }

    static int normalizeIconYOffsetTenthDp(int value) {
        return Math.max(POSITION_OFFSET_MIN_TENTH_DP, Math.min(POSITION_OFFSET_MAX_TENTH_DP, value));
    }

    static int normalizeClockRightPaddingOffsetTenthDp(int value) {
        return Math.max(
                CLOCK_RIGHT_PADDING_OFFSET_MIN_TENTH_DP,
                Math.min(CLOCK_RIGHT_PADDING_OFFSET_MAX_TENTH_DP, value));
    }

    static float positionOffsetTenthDpToDp(int value) {
        return normalizeIconYOffsetTenthDp(value) / 10f;
    }

    static float positionOffsetTenthDpToPx(Context context, int value) {
        float offsetDp = positionOffsetTenthDpToDp(value);
        if (context == null) {
            return offsetDp;
        }
        return offsetDp * context.getResources().getDisplayMetrics().density;
    }

    static boolean isPositionOffsetKey(String key) {
        if (key == null) {
            return false;
        }
        for (String candidate : POSITION_OFFSET_KEYS) {
            if (key.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    static int readPositionOffsetStorageVersion(SharedPreferences prefs) {
        return readInt(prefs, KEY_POSITION_OFFSET_STORAGE_VERSION, POSITION_OFFSET_STORAGE_VERSION_LEGACY_DP);
    }

    static int readPositionOffsetTenthDp(SharedPreferences prefs, String key, int defaultValue) {
        int normalizedDefault = normalizeIconYOffsetTenthDp(defaultValue);
        if (prefs == null || key == null) {
            return normalizedDefault;
        }
        Object raw = getRawValue(prefs, key);
        if (raw == null) {
            return normalizedDefault;
        }
        int rawValue;
        if (raw instanceof Number) {
            rawValue = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                rawValue = Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                return normalizedDefault;
            }
        } else {
            return normalizedDefault;
        }
        if (readPositionOffsetStorageVersion(prefs) >= POSITION_OFFSET_STORAGE_VERSION_TENTH_DP) {
            return normalizeIconYOffsetTenthDp(rawValue);
        }
        return normalizeIconYOffsetTenthDp(rawValue * 10);
    }

    static void markPositionOffsetStorageVersion(SharedPreferences.Editor editor) {
        if (editor == null) {
            return;
        }
        editor.putInt(KEY_POSITION_OFFSET_STORAGE_VERSION, POSITION_OFFSET_STORAGE_VERSION_TENTH_DP);
    }

    static void migratePositionOffsetStorageIfNeeded(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs == null) {
            return;
        }
        if (readPositionOffsetStorageVersion(prefs) >= POSITION_OFFSET_STORAGE_VERSION_TENTH_DP) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        for (String key : POSITION_OFFSET_KEYS) {
            if (all == null || !all.containsKey(key)) {
                continue;
            }
            int legacyValueDp = readInt(prefs, key, defaultInt(key));
            editor.putInt(key, normalizeIconYOffsetTenthDp(legacyValueDp * 10));
        }
        markPositionOffsetStorageVersion(editor);
        editor.apply();
    }

    static int normalizeNotificationAppIconSizeDp(int value) {
        return Math.max(12, Math.min(28, value));
    }

    static int normalizeNotificationAppIconPaddingDp(int value) {
        return Math.max(0, Math.min(8, value));
    }

    static String normalizeColorString(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if (text.length() == 6) {
            text = "ff" + text;
        }
        if (text.length() != 8) {
            return "";
        }
        try {
            long color = Long.parseLong(text, 16);
            return String.format(Locale.US, "#%08X", color & 0xffffffffL);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    static Integer parseColorString(String value) {
        String normalized = normalizeColorString(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            long color = Long.parseLong(normalized.substring(1), 16);
            return (int) (color & 0xffffffffL);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static int normalizeImeControlBarIconScalePercent(int value) {
        return Math.max(60, Math.min(180, value));
    }

    static int normalizeImeControlBarIconAlphaPercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

    static int normalizeTelephonyDebugSimCount(int value) {
        return Math.max(0, Math.min(2, value));
    }

    static int normalizeTelephonyDebugDefaultDataSlot(int value) {
        return Math.max(TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE,
                Math.min(TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD2, value));
    }

    static int normalizeTelephonyDebugSignalLevel(int value) {
        return Math.max(0, Math.min(4, value));
    }

    static int normalizeTelephonyDebugNetworkProfile(int value) {
        switch (value) {
            case TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
            case TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return value;
            default:
                return TELEPHONY_DEBUG_NETWORK_PROFILE_4G;
        }
    }

    static boolean includeInBackup(String key) {
        return key != null && !KEY_POSITION_OFFSET_STORAGE_VERSION.equals(key);
    }
}
