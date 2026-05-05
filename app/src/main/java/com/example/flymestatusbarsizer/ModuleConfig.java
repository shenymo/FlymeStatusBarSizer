package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

final class ModuleConfig {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final Object CACHE_LOCK = new Object();

    private static volatile Context systemUiContext;
    private static volatile SharedPreferences remotePrefs;
    private static volatile SharedPreferences.OnSharedPreferenceChangeListener remotePrefsListener;
    private static volatile Runnable configChangedCallback;
    private static volatile ModuleConfig activeConfig;
    private static volatile ModuleConfig lastGoodConfig;

    boolean enabled = SettingsStore.DEFAULT_ENABLED;
    boolean batteryCodeDrawEnabled = SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED;
    boolean signalCodeDrawEnabled = SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED;
    int batteryIconStyle = SettingsStore.DEFAULT_BATTERY_ICON_STYLE;
    boolean batteryLevelTextEnabled = SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED;
    int batteryTextFont = SettingsStore.DEFAULT_BATTERY_TEXT_FONT;
    int statusBarIconScalePercent = SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
    int batteryInnerTextScalePercent = SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
    boolean connectionRateThresholdEnabled = SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
    int connectionRateShowThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
    int connectionRateHideThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
    int connectionRateShowSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
    int connectionRateHideSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
    String clockCustomFormat = SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT;
    boolean clockBoldEnabled = SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED;
    int clockFontWeight = SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT;
    int clockAndCarrierTextSizePercent = SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT;
    boolean mbackLongTouchIntentEnabled = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
    String mbackLongTouchIntentUri = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
    boolean mbackNavBarTransparent = SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
    boolean notificationBackgroundTransparent = SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT;
    boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
    int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
    int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;
    boolean imeToolbarEnabled = SettingsStore.DEFAULT_IME_TOOLBAR_ENABLED;
    String imeToolbarOrder = SettingsStore.DEFAULT_IME_TOOLBAR_ORDER;

    static ModuleConfig load(Context context) {
        if (context != null) {
            rememberSystemUiContext(context);
        }
        ModuleConfig cached = activeConfig;
        if (cached != null) {
            return cached;
        }
        synchronized (CACHE_LOCK) {
            if (activeConfig != null) {
                return activeConfig;
            }
            SharedPreferences prefs = remotePrefs;
            ModuleConfig config = null;
            if (prefs != null) {
                config = fromSharedPreferences(prefs);
                if (config != null) {
                    lastGoodConfig = config;
                    activeConfig = config;
                    return config;
                }
            }
            if (lastGoodConfig != null) {
                activeConfig = lastGoodConfig;
                return lastGoodConfig;
            }
            config = new ModuleConfig();
            activeConfig = config;
            return config;
        }
    }

    static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            activeConfig = null;
        }
    }

    static void setConfigChangedCallback(Runnable callback) {
        configChangedCallback = callback;
    }

    static void attachToModule(XposedModule module) {
        if (module == null) {
            return;
        }
        try {
            updateRemotePreferences(module.getRemotePreferences(SettingsStore.PREFS));
        } catch (Throwable t) {
            Log.w(TAG, "Failed to obtain remote preferences from Xposed runtime", t);
        }
    }

    private static void updateRemotePreferences(SharedPreferences prefs) {
        SharedPreferences previous = remotePrefs;
        SharedPreferences.OnSharedPreferenceChangeListener listener = remotePrefsListener;
        if (previous != null && listener != null) {
            try {
                previous.unregisterOnSharedPreferenceChangeListener(listener);
            } catch (Throwable ignored) {
            }
        }
        remotePrefs = prefs;
        if (prefs == null) {
            invalidateCache();
            return;
        }
        SharedPreferences.OnSharedPreferenceChangeListener newListener = (sharedPreferences, key) -> {
            invalidateCache();
            ModuleConfig refreshed = fromSharedPreferences(sharedPreferences);
            if (refreshed != null) {
                synchronized (CACHE_LOCK) {
                    activeConfig = refreshed;
                    lastGoodConfig = refreshed;
                }
                notifyConfigChanged();
            }
        };
        remotePrefsListener = newListener;
        try {
            prefs.registerOnSharedPreferenceChangeListener(newListener);
        } catch (Throwable ignored) {
        }
        ModuleConfig refreshed = fromSharedPreferences(prefs);
        synchronized (CACHE_LOCK) {
            if (refreshed != null) {
                activeConfig = refreshed;
                lastGoodConfig = refreshed;
            } else {
                activeConfig = null;
            }
        }
        if (refreshed != null) {
            notifyConfigChanged();
        }
    }

    static void rememberSystemUiContext(Context context) {
        if (context == null || systemUiContext != null) {
            return;
        }
        systemUiContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    static Context getSystemUiContext() {
        return systemUiContext;
    }

    private static ModuleConfig fromSharedPreferences(SharedPreferences prefs) {
        if (prefs == null) {
            return null;
        }
        try {
            ModuleConfig config = new ModuleConfig();
            config.enabled = SettingsStore.readBoolean(prefs, SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED);
            config.batteryCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED);
            config.signalCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
            config.batteryLevelTextEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED);
            config.batteryIconStyle = SettingsStore.normalizeBatteryStyle(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_BATTERY_ICON_STYLE,
                            SettingsStore.DEFAULT_BATTERY_ICON_STYLE));
            config.batteryTextFont = SettingsStore.normalizeBatteryTextFont(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_BATTERY_TEXT_FONT,
                            SettingsStore.DEFAULT_BATTERY_TEXT_FONT));
            config.statusBarIconScalePercent = SettingsStore.normalizeScalePercent(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_STATUS_BAR_ICON_SCALE_PERCENT,
                            SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT));
            config.batteryInnerTextScalePercent = SettingsStore.normalizeScalePercent(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_BATTERY_INNER_TEXT_SCALE_PERCENT,
                            SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT));
            config.connectionRateThresholdEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
                    SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED);
            config.connectionRateShowThresholdKb = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB,
                    SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB);
            config.connectionRateHideThresholdKb = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB,
                    SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB);
            config.connectionRateShowSampleCount = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT,
                    SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT);
            config.connectionRateHideSampleCount = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT,
                    SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT);
            config.clockCustomFormat = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_CLOCK_CUSTOM_FORMAT,
                    SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT);
            config.clockBoldEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CLOCK_BOLD_ENABLED,
                    SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED);
            config.clockFontWeight = Math.max(100, Math.min(900,
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_CLOCK_FONT_WEIGHT,
                            SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT)));
            config.clockAndCarrierTextSizePercent = SettingsStore.normalizeScalePercent(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT,
                            SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT));
            config.mbackLongTouchIntentEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                    SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED);
            config.mbackLongTouchIntentUri = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                    SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI);
            config.mbackNavBarTransparent = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                    SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT);
            config.notificationBackgroundTransparent = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT,
                    SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT);
            config.mbackHidePill = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_HIDE_PILL,
                    SettingsStore.DEFAULT_MBACK_HIDE_PILL);
            config.mbackInsetSize = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_MBACK_INSET_SIZE,
                    SettingsStore.DEFAULT_MBACK_INSET_SIZE);
            config.mbackNavBarHeight = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT,
                    SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT);
            config.imeToolbarEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_IME_TOOLBAR_ENABLED,
                    SettingsStore.DEFAULT_IME_TOOLBAR_ENABLED);
            config.imeToolbarOrder = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_IME_TOOLBAR_ORDER,
                    SettingsStore.DEFAULT_IME_TOOLBAR_ORDER);
            return config;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load remote module config", t);
            return null;
        }
    }

    private static void notifyConfigChanged() {
        Runnable callback = configChangedCallback;
        if (callback == null) {
            return;
        }
        try {
            callback.run();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to dispatch config change callback", t);
        }
    }
}
