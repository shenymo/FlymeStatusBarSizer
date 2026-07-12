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
    boolean cameraCircleBatteryEnabled = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED;
    boolean cameraCircleBatteryHideIconEnabled =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED;
    int cameraCircleBatteryRadiusPercent =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT;
    int cameraCircleBatteryStrokePercent =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT;
    int cameraCircleBatteryXOffsetTenthDp = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP * 10;
    int cameraCircleBatteryYOffsetTenthDp = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP * 10;
    boolean signalCodeDrawEnabled = SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED;
    boolean signalMobileTypeBadgeEnabled = SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED;
    boolean wifiCodeDrawEnabled = SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED;
    boolean signalWifiSwapEnabled = SettingsStore.DEFAULT_SIGNAL_WIFI_SWAP_ENABLED;
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
    boolean lockscreenCanvasClockEnabled = SettingsStore.DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED;
    boolean clockDetailPopupEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_POPUP_ENABLED;
    boolean clockDetailLunarDateEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED;
    boolean clockDetailActionGridEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED;
    String clockDetailActionGridItemsJson =
            SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON;
    String clockDetailAssistantActionCacheJson =
            SettingsStore.DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON;
    boolean mbackLongTouchIntentEnabled = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
    int mbackLongTouchAction = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_ACTION;
    String mbackLongTouchIntentUri = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
    boolean windowModeSideGestureEnabled = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED;
    int windowModeSideGestureAction = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION;
    String windowModeSideGestureIntentUri = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI;
    boolean windowModeSideGesturePrewarmEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED;
    boolean windowModeHoverFullscreenEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED;
    int windowModeHoverFullscreenTimeoutMs =
            SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS;
    boolean windowModeTwoRingLauncherEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED;
    int windowModeTwoRingInnerIconScalePercent =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT;
    int windowModeTwoRingInnerRadiusPercent =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT;
    boolean windowModeRecentInnerRingEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED;
    int windowModeRecentInnerRingIconScalePercent =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT;
    int windowModeRecentInnerRingRadiusPercent =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT;
    boolean mbackNavBarTransparent = SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
    boolean notificationAppIconEnabled = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED;
    int notificationAppIconSizeDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP;
    int notificationAppIconPaddingDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP;
    boolean launcherIosStackRecentsEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED;
    boolean launcherIosStackRecentsBlurEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED;
    boolean launcherIosStackRecentsClearAllButtonEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED;
    int launcherStackRightVisiblePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT;
    int launcherStackLeftMovePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_MOVE_PERCENT;
    int launcherStackLeftRestInsetPercent = SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT;
    int launcherStackMinScalePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_MIN_SCALE_PERCENT;
    int launcherStackMaxLayers = SettingsStore.DEFAULT_LAUNCHER_STACK_MAX_LAYERS;
    int launcherStackEntryLiftPercent = SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_LIFT_PERCENT;
    int launcherStackEntryInitialSpreadPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT;
    int launcherStackReleaseInitialSpreadPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT;
    int launcherStackDesktopEntryVisibleCount =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT;
    int launcherStackDesktopEntryAnchorIndex =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX;
    int launcherStackGestureReleaseDurationMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS;
    int launcherStackStableVisibleRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS;
    int launcherStackEntryLightRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS;
    int launcherStackGestureReleaseCoreRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS;
    int launcherStackAppFlowLightRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS;
    int launcherStackRightBaseSpeedupPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT;
    int launcherStackRightSpeedupPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT;
    int launcherStackHorizontalDragResistancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT;
    int launcherStackHorizontalPageThresholdPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT;
    int launcherStackHorizontalFlingVelocityDp =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP;
    int launcherStackHorizontalSnapDurationMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS;
    int launcherStackBlankExitScaleDeltaPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT;
    int launcherStackBlankExitExtraTravelPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT;
    int launcherStackTaskLaunchExtraWidthPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT;
    int launcherStackDismissSuccessAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS;
    int launcherStackDismissCancelAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS;
    int launcherStackDismissRelayoutAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS;
    int launcherStackDismissDragRelayoutMaxPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT;
    int launcherStackDismissSecondaryDominancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT;
    int launcherStackDismissMinFlingVelocity =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY;
    int launcherStackMenuPullThresholdDp =
            SettingsStore.DEFAULT_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP;
    int launcherStackContentMaxBlurDp = SettingsStore.DEFAULT_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP;
    int launcherStackContentMediumBlurPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT;
    int launcherStackContentBlurStartAlphaPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT;
    int launcherStackLeftFadeDistancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT;
    int launcherStackLeftReleaseAlphaThresholdPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT;
    int launcherStackScrollFrameRate = SettingsStore.DEFAULT_LAUNCHER_STACK_SCROLL_FRAME_RATE;
    int launcherStackFrameRateReleaseDelayMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS;
    boolean launcherAicyEntryEnabled = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_ENABLED;
    String launcherAicyEntryText = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TEXT;
    String launcherAicyEntryTarget = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TARGET;
    String launcherFolderBgColor = SettingsStore.DEFAULT_LAUNCHER_FOLDER_BG_COLOR;
    boolean notificationSystemBlurOnlyEnabled =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED;
    int notificationSystemBlurCarrierColorMode =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE;
    String notificationSystemBlurLightColor =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR;
    String notificationSystemBlurDarkColor =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR;
    boolean notificationTextFollowStatusBarEnabled =
            SettingsStore.DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED;
    boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
    int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
    int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;
    boolean imeReplaceOriginalControlBar = SettingsStore.DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR;
    String imeControlBarButtonSlots = SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;
    int imeControlBarIconScalePercent = SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT;
    int imeControlBarIconAlphaPercent = SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT;
    int imeControlBarYOffsetTenthDp = SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10;
    boolean telephonyDebugEnabled = SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED;
    boolean wifiPerfLoggingEnabled = SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED;
    boolean launcherRecentsPerfLoggingEnabled =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED;
    boolean launcherRecentsFlowLoggingEnabled =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED;
    boolean oneMindPerfDisableEnabled = SettingsStore.DEFAULT_ONEMIND_PERF_DISABLE_ENABLED;
    boolean oneMindLogcatEnabled = SettingsStore.DEFAULT_ONEMIND_LOGCAT_ENABLED;
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

    static SharedPreferences getRemotePreferences() {
        return remotePrefs;
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
            config.cameraCircleBatteryEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED);
            config.cameraCircleBatteryHideIconEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED);
            config.cameraCircleBatteryRadiusPercent = Math.max(80, Math.min(200,
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT,
                            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT)));
            config.cameraCircleBatteryStrokePercent = Math.max(50, Math.min(300,
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT,
                            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT)));
            config.cameraCircleBatteryXOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP * 10);
            config.cameraCircleBatteryYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP * 10);
            config.signalCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
            config.signalMobileTypeBadgeEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED);
            config.wifiCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED);
            config.signalWifiSwapEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_WIFI_SWAP_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_WIFI_SWAP_ENABLED);
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
            config.lockscreenCanvasClockEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LOCKSCREEN_CANVAS_CLOCK_ENABLED,
                    SettingsStore.DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED);
            config.clockDetailPopupEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CLOCK_DETAIL_POPUP_ENABLED,
                    SettingsStore.DEFAULT_CLOCK_DETAIL_POPUP_ENABLED);
            config.clockDetailLunarDateEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CLOCK_DETAIL_LUNAR_DATE_ENABLED,
                    SettingsStore.DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED);
            config.clockDetailActionGridEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
                    SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED);
            config.clockDetailActionGridItemsJson = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
                    SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON);
            config.clockDetailAssistantActionCacheJson = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON,
                    SettingsStore.DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON);
            config.mbackLongTouchIntentEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED,
                    SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED);
            config.mbackLongTouchAction = SettingsStore.normalizeMBackLongTouchAction(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_MBACK_LONG_TOUCH_ACTION,
                            SettingsStore.DEFAULT_MBACK_LONG_TOUCH_ACTION));
            config.mbackLongTouchIntentUri = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI,
                    SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI);
            config.windowModeSideGestureEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                    SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED);
            config.windowModeSideGestureAction = SettingsStore.normalizeWindowModeSideGestureAction(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ACTION,
                            SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION));
            config.windowModeSideGestureIntentUri = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
                    SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI);
            config.windowModeSideGesturePrewarmEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED,
                    SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED);
            config.windowModeHoverFullscreenEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED,
                    SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED);
            config.windowModeHoverFullscreenTimeoutMs =
                    SettingsStore.normalizeWindowModeHoverFullscreenTimeoutMs(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS,
                                    SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS));
            config.windowModeTwoRingLauncherEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED,
                    SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED);
            config.windowModeTwoRingInnerIconScalePercent =
                    SettingsStore.normalizeWindowModeTwoRingInnerIconScalePercent(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT,
                                    SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT));
            config.windowModeTwoRingInnerRadiusPercent =
                    SettingsStore.normalizeWindowModeTwoRingInnerRadiusPercent(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT,
                                    SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT));
            config.windowModeRecentInnerRingEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED,
                    SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED);
            config.windowModeRecentInnerRingIconScalePercent =
                    SettingsStore.normalizeWindowModeRecentInnerRingIconScalePercent(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT,
                                    SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT));
            config.windowModeRecentInnerRingRadiusPercent =
                    SettingsStore.normalizeWindowModeRecentInnerRingRadiusPercent(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT,
                                    SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT));
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
            config.launcherIosStackRecentsEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED);
            config.launcherIosStackRecentsBlurEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED);
            config.launcherIosStackRecentsClearAllButtonEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED);
            config.launcherStackRightVisiblePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT);
            config.launcherStackLeftMovePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT);
            config.launcherStackLeftRestInsetPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT);
            config.launcherStackMinScalePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT);
            config.launcherStackMaxLayers = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_MAX_LAYERS);
            config.launcherStackEntryLiftPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT);
            config.launcherStackEntryInitialSpreadPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT);
            config.launcherStackReleaseInitialSpreadPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT);
            config.launcherStackDesktopEntryVisibleCount = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT);
            config.launcherStackDesktopEntryAnchorIndex = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX);
            config.launcherStackGestureReleaseDurationMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS);
            config.launcherStackStableVisibleRadius = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS);
            config.launcherStackEntryLightRadius = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS);
            config.launcherStackGestureReleaseCoreRadius = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS);
            config.launcherStackAppFlowLightRadius = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS);
            config.launcherStackRightBaseSpeedupPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT);
            config.launcherStackRightSpeedupPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT);
            config.launcherStackHorizontalDragResistancePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT);
            config.launcherStackHorizontalPageThresholdPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT);
            config.launcherStackHorizontalFlingVelocityDp = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP);
            config.launcherStackHorizontalSnapDurationMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS);
            config.launcherStackBlankExitScaleDeltaPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT);
            config.launcherStackBlankExitExtraTravelPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT);
            config.launcherStackTaskLaunchExtraWidthPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT);
            config.launcherStackDismissSuccessAnimMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS);
            config.launcherStackDismissCancelAnimMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS);
            config.launcherStackDismissRelayoutAnimMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS);
            config.launcherStackDismissDragRelayoutMaxPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT);
            config.launcherStackDismissSecondaryDominancePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT);
            config.launcherStackDismissMinFlingVelocity = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY);
            config.launcherStackMenuPullThresholdDp = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP);
            config.launcherStackContentMaxBlurDp = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP);
            config.launcherStackContentMediumBlurPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT);
            config.launcherStackContentBlurStartAlphaPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT);
            config.launcherStackLeftFadeDistancePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT);
            config.launcherStackLeftReleaseAlphaThresholdPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT);
            config.launcherStackScrollFrameRate = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE);
            config.launcherStackFrameRateReleaseDelayMs = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS);
            config.launcherAicyEntryEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_AICY_ENTRY_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_ENABLED);
            config.launcherAicyEntryText = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_AICY_ENTRY_TEXT,
                    SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TEXT);
            config.launcherAicyEntryTarget = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_AICY_ENTRY_TARGET,
                    SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TARGET);
            config.launcherFolderBgColor = SettingsStore.normalizeColorString(
                    SettingsStore.readString(
                            prefs,
                            SettingsStore.KEY_LAUNCHER_FOLDER_BG_COLOR,
                            SettingsStore.DEFAULT_LAUNCHER_FOLDER_BG_COLOR));
            config.notificationSystemBlurOnlyEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED,
                    SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED);
            config.notificationSystemBlurCarrierColorMode = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE,
                    SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE);
            config.notificationSystemBlurLightColor = SettingsStore.normalizeColorString(
                    SettingsStore.readString(
                            prefs,
                            SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
                            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR));
            config.notificationSystemBlurDarkColor = SettingsStore.normalizeColorString(
                    SettingsStore.readString(
                            prefs,
                            SettingsStore.KEY_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
                            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR));
            config.notificationTextFollowStatusBarEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED,
                    SettingsStore.DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED);
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
            config.imeReplaceOriginalControlBar = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR,
                    SettingsStore.DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR);
            config.imeControlBarButtonSlots = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_IME_CONTROL_BAR_BUTTON_SLOTS,
                    SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS);
            config.imeControlBarIconScalePercent = SettingsStore.normalizeImeControlBarIconScalePercent(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_IME_CONTROL_BAR_ICON_SCALE_PERCENT,
                            SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT));
            config.imeControlBarIconAlphaPercent = SettingsStore.normalizeImeControlBarIconAlphaPercent(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_IME_CONTROL_BAR_ICON_ALPHA_PERCENT,
                            SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT));
            config.imeControlBarYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_IME_CONTROL_BAR_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10);
            config.telephonyDebugEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_TELEPHONY_DEBUG_ENABLED,
                    SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED);
            config.wifiPerfLoggingEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WIFI_PERF_LOGGING_ENABLED,
                    SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED);
            config.launcherRecentsPerfLoggingEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED);
            config.launcherRecentsFlowLoggingEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED);
            config.oneMindPerfDisableEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_ONEMIND_PERF_DISABLE_ENABLED,
                    SettingsStore.DEFAULT_ONEMIND_PERF_DISABLE_ENABLED);
            config.oneMindLogcatEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_ONEMIND_LOGCAT_ENABLED,
                    SettingsStore.DEFAULT_ONEMIND_LOGCAT_ENABLED);
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

    private static int readLauncherStackParameter(SharedPreferences prefs, String key) {
        return SettingsStore.normalizeLauncherStackParameter(
                key,
                SettingsStore.readInt(prefs, key, SettingsStore.defaultInt(key)));
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
