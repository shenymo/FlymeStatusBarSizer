package com.example.flymestatusbarsizer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;

final class ModuleConfig {
    private static final Uri SETTINGS_URI = Uri.parse("content://" + SettingsStore.AUTHORITY + "/settings");
    private static final Object CACHE_LOCK = new Object();
    private static final long CACHE_TTL_MS = 5000L;

    private static volatile Context systemUiContext;
    private static volatile ModuleConfig cachedConfig;
    private static volatile long cachedConfigUptime;

    boolean enabled = SettingsStore.DEFAULT_ENABLED;
    boolean connectionRateThresholdEnabled = SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
    int connectionRateShowThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
    int connectionRateHideThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
    int connectionRateShowSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
    int connectionRateHideSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
    boolean showClockWeekday = SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY;
    boolean clockWeekdayHidePrefix = SettingsStore.DEFAULT_CLOCK_WEEKDAY_HIDE_PREFIX;
    boolean mbackLongTouchIntentEnabled = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
    String mbackLongTouchIntentUri = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
    boolean mbackNavBarTransparent = SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
    boolean notificationBackgroundTransparent = SettingsStore.DEFAULT_NOTIFICATION_BACKGROUND_TRANSPARENT;
    boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
    int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
    int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;
    boolean signalHookDebugEnabled = SettingsStore.DEFAULT_SIGNAL_HOOK_DEBUG_ENABLED;
    boolean signalHookLogcatEnabled = SettingsStore.DEFAULT_SIGNAL_HOOK_LOGCAT_ENABLED;
    int signalDebugResetSeq = SettingsStore.DEFAULT_SIGNAL_DEBUG_RESET_SEQ;

    static ModuleConfig load(Context context) {
        if (context == null) {
            return new ModuleConfig();
        }
        rememberSystemUiContext(context);
        long now = SystemClock.uptimeMillis();
        ModuleConfig cached = cachedConfig;
        if (cached != null && now - cachedConfigUptime <= CACHE_TTL_MS) {
            return cached;
        }
        ModuleConfig config = new ModuleConfig();
        try (Cursor cursor = context.getContentResolver().query(SETTINGS_URI, null, null, null, null)) {
            if (cursor == null) {
                return config;
            }
            int keyColumn = cursor.getColumnIndex("key");
            int valueColumn = cursor.getColumnIndex("value");
            while (cursor.moveToNext()) {
                String key = cursor.getString(keyColumn);
                String value = cursor.getString(valueColumn);
                config.apply(key, value);
            }
        } catch (Throwable ignored) {
        }
        synchronized (CACHE_LOCK) {
            cachedConfig = config;
            cachedConfigUptime = now;
        }
        return config;
    }

    static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedConfig = null;
            cachedConfigUptime = 0L;
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

    private void apply(String key, String value) {
        if (SettingsStore.KEY_ENABLED.equals(key)) {
            enabled = "1".equals(value);
        } else if (SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED.equals(key)) {
            connectionRateThresholdEnabled = "1".equals(value);
        } else if (SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB.equals(key)) {
            connectionRateShowThresholdKb = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB);
        } else if (SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB.equals(key)) {
            connectionRateHideThresholdKb = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB);
        } else if (SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT.equals(key)) {
            connectionRateShowSampleCount = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT);
        } else if (SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT.equals(key)) {
            connectionRateHideSampleCount = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT);
        } else if (SettingsStore.KEY_SHOW_CLOCK_WEEKDAY.equals(key)) {
            showClockWeekday = "1".equals(value);
        } else if (SettingsStore.KEY_CLOCK_WEEKDAY_HIDE_PREFIX.equals(key)) {
            clockWeekdayHidePrefix = "1".equals(value);
        } else if (SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED.equals(key)) {
            mbackLongTouchIntentEnabled = "1".equals(value);
        } else if (SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI.equals(key)) {
            mbackLongTouchIntentUri = value == null
                    ? SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI
                    : value;
        } else if (SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT.equals(key)) {
            mbackNavBarTransparent = "1".equals(value);
        } else if (SettingsStore.KEY_NOTIFICATION_BACKGROUND_TRANSPARENT.equals(key)) {
            notificationBackgroundTransparent = "1".equals(value);
        } else if (SettingsStore.KEY_MBACK_HIDE_PILL.equals(key)) {
            mbackHidePill = "1".equals(value);
        } else if (SettingsStore.KEY_MBACK_INSET_SIZE.equals(key)) {
            mbackInsetSize = parseInt(value, SettingsStore.DEFAULT_MBACK_INSET_SIZE);
        } else if (SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT.equals(key)) {
            mbackNavBarHeight = parseInt(value, SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT);
        } else if (SettingsStore.KEY_SIGNAL_HOOK_DEBUG_ENABLED.equals(key)) {
            signalHookDebugEnabled = "1".equals(value);
        } else if (SettingsStore.KEY_SIGNAL_HOOK_LOGCAT_ENABLED.equals(key)) {
            signalHookLogcatEnabled = "1".equals(value);
        } else if (SettingsStore.KEY_SIGNAL_DEBUG_RESET_SEQ.equals(key)) {
            signalDebugResetSeq = parseInt(value, SettingsStore.DEFAULT_SIGNAL_DEBUG_RESET_SEQ);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
