package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

final class SettingsStore {
    static final String PREFS = "status_bar_sizer";

    static final String KEY_ENABLED = "enabled";
    static final String KEY_BATTERY_CODE_DRAW_ENABLED = "battery_code_draw_enabled";
    static final String KEY_SIGNAL_CODE_DRAW_ENABLED = "signal_code_draw_enabled";
    static final String KEY_BATTERY_ICON_STYLE = "battery_icon_style";
    static final String KEY_BATTERY_LEVEL_TEXT_ENABLED = "battery_level_text_enabled";
    static final String KEY_BATTERY_HOLLOW_ENABLED = "battery_hollow_enabled";
    static final String KEY_BATTERY_TEXT_FONT = "battery_text_font";
    static final String KEY_STATUS_BAR_ICON_SCALE_PERCENT = "status_bar_icon_scale_percent";
    static final String KEY_BATTERY_INNER_TEXT_SCALE_PERCENT = "battery_inner_text_scale_percent";
    static final String KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = "connection_rate_auto_visibility_enabled";
    static final String KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB = "connection_rate_show_threshold_kb";
    static final String KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB = "connection_rate_hide_threshold_kb";
    static final String KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT = "connection_rate_show_sample_count";
    static final String KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT = "connection_rate_hide_sample_count";
    static final String KEY_CLOCK_CUSTOM_FORMAT = "clock_custom_format";
    static final String KEY_CLOCK_BOLD_ENABLED = "clock_bold_enabled";
    static final String KEY_CLOCK_FONT_WEIGHT = "clock_font_weight";
    static final String KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT = "clock_and_carrier_text_size_percent";
    static final String KEY_MBACK_LONG_TOUCH_URL_ENABLED = "mback_long_touch_url_enabled";
    static final String KEY_MBACK_LONG_TOUCH_INTENT_URI = "mback_long_touch_intent_uri";
    static final String KEY_MBACK_NAV_BAR_TRANSPARENT = "mback_nav_bar_transparent";
    static final String KEY_NOTIFICATION_BACKGROUND_TRANSPARENT = "notification_background_transparent";
    static final String KEY_MBACK_INSET_SIZE = "mback_inset_size";
    static final String KEY_MBACK_NAV_BAR_HEIGHT = "mback_nav_bar_height";
    static final String KEY_MBACK_HIDE_PILL = "mback_hide_pill";
    static final String KEY_IME_TOOLBAR_ENABLED = "ime_toolbar_enabled";
    static final String KEY_IME_TOOLBAR_ORDER = "ime_toolbar_order";
    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_BATTERY_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_CODE_DRAW_ENABLED = true;
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
    static final int DEFAULT_BATTERY_TEXT_FONT = BATTERY_TEXT_FONT_SYSTEM_DEFAULT;
    static final int DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT = 100;
    static final boolean DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED = false;
    static final int DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB = 100;
    static final int DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB = 32;
    static final int DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT = 2;
    static final int DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT = 3;
    static final String DEFAULT_CLOCK_CUSTOM_FORMAT = "";
    static final boolean DEFAULT_CLOCK_BOLD_ENABLED = true;
    static final int DEFAULT_CLOCK_FONT_WEIGHT = 900;
    static final int DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT = 100;
    static final boolean DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED = false;
    static final String DEFAULT_MBACK_LONG_TOUCH_INTENT_URI = "";
    static final boolean DEFAULT_MBACK_NAV_BAR_TRANSPARENT = false;
    static final boolean DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT = false;
    static final int DEFAULT_MBACK_INSET_SIZE = -1;
    static final int DEFAULT_MBACK_NAV_BAR_HEIGHT = -1;
    static final boolean DEFAULT_MBACK_HIDE_PILL = false;
    static final boolean DEFAULT_IME_TOOLBAR_ENABLED = true;
    static final String DEFAULT_IME_TOOLBAR_ORDER = "paste,delete,select_all,copy,switch_ime";
    static final String[] INT_KEYS = {
            KEY_BATTERY_ICON_STYLE,
            KEY_BATTERY_TEXT_FONT,
            KEY_STATUS_BAR_ICON_SCALE_PERCENT,
            KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
            KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
            KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
            KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
            KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
            KEY_CLOCK_FONT_WEIGHT,
            KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
            KEY_MBACK_INSET_SIZE,
            KEY_MBACK_NAV_BAR_HEIGHT
    };

    static final String[] BOOLEAN_KEYS = {
            KEY_ENABLED,
            KEY_BATTERY_CODE_DRAW_ENABLED,
            KEY_SIGNAL_CODE_DRAW_ENABLED,
            KEY_BATTERY_LEVEL_TEXT_ENABLED,
            KEY_BATTERY_HOLLOW_ENABLED,
            KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_MBACK_LONG_TOUCH_URL_ENABLED,
            KEY_MBACK_NAV_BAR_TRANSPARENT,
            KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
            KEY_MBACK_HIDE_PILL,
            KEY_IME_TOOLBAR_ENABLED
    };

    static final String[] STRING_KEYS = {
            KEY_CLOCK_CUSTOM_FORMAT,
            KEY_MBACK_LONG_TOUCH_INTENT_URI,
            KEY_IME_TOOLBAR_ORDER
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
            case KEY_BATTERY_ICON_STYLE:
                return DEFAULT_BATTERY_ICON_STYLE;
            case KEY_BATTERY_TEXT_FONT:
                return DEFAULT_BATTERY_TEXT_FONT;
            case KEY_STATUS_BAR_ICON_SCALE_PERCENT:
                return DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
            case KEY_BATTERY_INNER_TEXT_SCALE_PERCENT:
                return DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
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
            case KEY_BATTERY_HOLLOW_ENABLED:
                return DEFAULT_BATTERY_HOLLOW_ENABLED;
            case KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED:
                return DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
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
            case KEY_IME_TOOLBAR_ENABLED:
                return DEFAULT_IME_TOOLBAR_ENABLED;
            default:
                return false;
        }
    }

    static String defaultString(String key) {
        if (KEY_CLOCK_CUSTOM_FORMAT.equals(key)) {
            return DEFAULT_CLOCK_CUSTOM_FORMAT;
        }
        if (KEY_MBACK_LONG_TOUCH_INTENT_URI.equals(key)) {
            return DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
        }
        if (KEY_IME_TOOLBAR_ORDER.equals(key)) {
            return DEFAULT_IME_TOOLBAR_ORDER;
        }
        return "";
    }

    static int normalizeBatteryStyle(int value) {
        return value == BATTERY_STYLE_ONEUI ? BATTERY_STYLE_ONEUI : BATTERY_STYLE_IOS;
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

    static boolean includeInBackup(String key) {
        return key != null;
    }
}
