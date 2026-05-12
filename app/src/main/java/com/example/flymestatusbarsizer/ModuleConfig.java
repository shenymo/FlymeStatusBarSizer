package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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
    private static final Object CALLBACK_DISPATCH_LOCK = new Object();
    private static final long CONFIG_CHANGE_DEBOUNCE_MS = 80L;
    private static volatile Handler callbackHandler;
    private static final Runnable CONFIG_CHANGE_DISPATCH_RUNNABLE = ModuleConfig::notifyConfigChanged;

    boolean enabled = SettingsStore.DEFAULT_ENABLED;
    boolean batteryCodeDrawEnabled = SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED;
    boolean signalCodeDrawEnabled = SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED;
    boolean wifiCodeDrawEnabled = SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED;
    int batteryIconStyle = SettingsStore.DEFAULT_BATTERY_ICON_STYLE;
    boolean batteryLevelTextEnabled = SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED;
    boolean batteryHollowEnabled = SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED;
    boolean batteryHollowFillFollowsLevel = SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL;
    int batteryTextFont = SettingsStore.DEFAULT_BATTERY_TEXT_FONT;
    int statusBarIconScalePercent = SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
    int batteryInnerTextScalePercent = SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
    int batteryIconYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10;
    int batteryTextYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10;
    int batteryBoltYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10;
    int signalSingleYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10;
    int signalBadgeYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10;
    int signalDualYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10;
    int wifiYOffsetTenthDp = SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10;
    int clockRightPaddingOffsetTenthDp = SettingsStore.DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP * 10;
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
    boolean notificationAppIconEnabled = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED;
    int notificationAppIconSizeDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP;
    int notificationAppIconPaddingDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP;
    boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
    int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
    int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;
    boolean imeToolbarEnabled = SettingsStore.DEFAULT_IME_TOOLBAR_ENABLED;
    boolean imeForceStockControlBar = SettingsStore.DEFAULT_IME_FORCE_STOCK_CONTROL_BAR;
    String imeToolbarOrder = SettingsStore.DEFAULT_IME_TOOLBAR_ORDER;
    boolean telephonyDebugEnabled = SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED;
    boolean wifiPerfLoggingEnabled = SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED;
    int telephonyDebugSimCount = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SIM_COUNT;
    int telephonyDebugDefaultDataSlot = SettingsStore.DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT;
    int telephonyDebugSlot1NetworkProfile = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE;
    int telephonyDebugSlot1SignalLevel = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL;
    int telephonyDebugSlot2NetworkProfile = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE;
    int telephonyDebugSlot2SignalLevel = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL;

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
            applyRefreshedConfig(sharedPreferences, true);
        };
        remotePrefsListener = newListener;
        try {
            prefs.registerOnSharedPreferenceChangeListener(newListener);
        } catch (Throwable ignored) {
        }
        applyRefreshedConfig(prefs, false);
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
            config.wifiCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED);
            config.batteryLevelTextEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_LEVEL_TEXT_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED);
            config.batteryHollowEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_HOLLOW_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED);
            config.batteryHollowFillFollowsLevel = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
                    SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL);
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
            config.batteryIconYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_BATTERY_ICON_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10);
            config.batteryTextYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_BATTERY_TEXT_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10);
            config.batteryBoltYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_BATTERY_BOLT_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10);
            config.signalSingleYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_SIGNAL_SINGLE_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10);
            config.signalBadgeYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_SIGNAL_BADGE_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10);
            config.signalDualYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_SIGNAL_DUAL_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10);
            config.wifiYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_WIFI_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10);
            config.clockRightPaddingOffsetTenthDp =
                    SettingsStore.normalizeClockRightPaddingOffsetTenthDp(
                            SettingsStore.readPositionOffsetTenthDp(
                                    prefs,
                                    SettingsStore.KEY_CLOCK_RIGHT_PADDING_OFFSET_DP,
                                    SettingsStore.DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP * 10));
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
            config.notificationAppIconEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                    SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED);
            config.notificationAppIconSizeDp = SettingsStore.normalizeNotificationAppIconSizeDp(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_NOTIFICATION_APP_ICON_SIZE_DP,
                            SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP));
            config.notificationAppIconPaddingDp = SettingsStore.normalizeNotificationAppIconPaddingDp(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_NOTIFICATION_APP_ICON_PADDING_DP,
                            SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP));
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
            config.imeForceStockControlBar = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_IME_FORCE_STOCK_CONTROL_BAR,
                    SettingsStore.DEFAULT_IME_FORCE_STOCK_CONTROL_BAR);
            config.imeToolbarOrder = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_IME_TOOLBAR_ORDER,
                    SettingsStore.DEFAULT_IME_TOOLBAR_ORDER);
            config.telephonyDebugEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED,
                    SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED);
            config.wifiPerfLoggingEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WIFI_PERF_LOGGING_ENABLED,
                    SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED);
            config.telephonyDebugSimCount = SettingsStore.normalizeTelephonyDebugSimCount(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_SIM_COUNT,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_SIM_COUNT));
            config.telephonyDebugDefaultDataSlot = SettingsStore.normalizeTelephonyDebugDefaultDataSlot(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT));
            config.telephonyDebugSlot1NetworkProfile = SettingsStore.normalizeTelephonyDebugNetworkProfile(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE));
            config.telephonyDebugSlot1SignalLevel = SettingsStore.normalizeTelephonyDebugSignalLevel(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL));
            config.telephonyDebugSlot2NetworkProfile = SettingsStore.normalizeTelephonyDebugNetworkProfile(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE));
            config.telephonyDebugSlot2SignalLevel = SettingsStore.normalizeTelephonyDebugSignalLevel(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL,
                            SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL));
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

    private static void applyRefreshedConfig(SharedPreferences prefs, boolean debounce) {
        invalidateCache();
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
            dispatchConfigChanged(debounce);
        }
    }

    private static void dispatchConfigChanged(boolean debounce) {
        if (!debounce) {
            synchronized (CALLBACK_DISPATCH_LOCK) {
                Handler handler = callbackHandler;
                if (handler != null) {
                    handler.removeCallbacks(CONFIG_CHANGE_DISPATCH_RUNNABLE);
                }
            }
            notifyConfigChanged();
            return;
        }
        Handler handler = ensureCallbackHandler();
        if (handler == null) {
            notifyConfigChanged();
            return;
        }
        synchronized (CALLBACK_DISPATCH_LOCK) {
            handler.removeCallbacks(CONFIG_CHANGE_DISPATCH_RUNNABLE);
            handler.postDelayed(CONFIG_CHANGE_DISPATCH_RUNNABLE, CONFIG_CHANGE_DEBOUNCE_MS);
        }
    }

    private static Handler ensureCallbackHandler() {
        Handler handler = callbackHandler;
        if (handler != null) {
            return handler;
        }
        Looper looper = Looper.getMainLooper();
        if (looper == null) {
            return null;
        }
        synchronized (CALLBACK_DISPATCH_LOCK) {
            if (callbackHandler == null) {
                callbackHandler = new Handler(looper);
            }
            return callbackHandler;
        }
    }
}
