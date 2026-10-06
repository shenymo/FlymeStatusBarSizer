package com.example.flymestatusbarsizer.config;

import com.example.flymestatusbarsizer.feature.share.ShareTargetRules;
import com.example.flymestatusbarsizer.feature.share.ShareTargetProfiles;

import com.example.flymestatusbarsizer.feature.battery.CircleBatteryAnimationConfig;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarIconVisibility;
import com.example.flymestatusbarsizer.feature.wifi.WifiIconStyles;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import java.util.Collections;
import java.util.Set;

public final class ModuleConfig {
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

    public boolean enabled = SettingsStore.DEFAULT_ENABLED;
    public Set<String> hiddenStatusBarSlots = Collections.emptySet();
    public boolean shareTargetsEnabled = SettingsStore.DEFAULT_SHARE_TARGETS_ENABLED;
    public ShareTargetProfiles shareTargetProfiles = new ShareTargetProfiles();
    public ShareTargetRules shareTargetRules = ShareTargetRules.EMPTY;
    public boolean statusBarTintEnabled = SettingsStore.DEFAULT_STATUS_BAR_TINT_ENABLED;
    public final int[] statusBarTintModes = new int[SettingsStore.STATUS_BAR_TINT_KEYS.length];
    public boolean batteryCodeDrawEnabled = SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED;
    public CircleBatteryAnimationConfig circleAnimation = new CircleBatteryAnimationConfig();
    public boolean cameraCircleBatteryEnabled = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED;
    public boolean cameraCircleBatteryHideIconEnabled =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED;
    public boolean cameraCircleBatteryTintEnabled =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_TINT_ENABLED;
    public int cameraCircleBatteryTransparencyTenthPercent =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT;
    public int cameraCircleBatteryNormalLightColor =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR;
    public int cameraCircleBatteryNormalDarkColor =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR;
    public int cameraCircleBatteryChargingColor =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR;
    public int cameraCircleBatteryPowerSaveColor =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR;
    public int cameraCircleBatteryLowColor =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_LOW_COLOR;
    public int cameraCircleBatteryRadiusPercent =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT;
    public int cameraCircleBatteryStrokePercent =
            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT;
    public int cameraCircleBatteryXOffsetTenthDp = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP * 100;
    public int cameraCircleBatteryYOffsetTenthDp = SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP * 100;
    public boolean signalCodeDrawEnabled = SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED;
    public boolean signalMobileTypeBadgeEnabled = SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED;
    public String signalMobileTypeBadge5gText = SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5G_TEXT;
    public String signalMobileTypeBadge5gaText = SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5GA_TEXT;
    public String signalMobileTypeBadgeNon5gText =
            SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_NON_5G_TEXT;
    public boolean wifiCodeDrawEnabled = SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED;
    public int wifiBandCornerPercent = SettingsStore.DEFAULT_WIFI_BAND_CORNER_PERCENT;
    public int wifiTipCornerPercent = SettingsStore.DEFAULT_WIFI_TIP_CORNER_PERCENT;
    public int wifiBandGapPercent = SettingsStore.DEFAULT_WIFI_BAND_GAP_PERCENT;
    public int wifiIconStyle = SettingsStore.DEFAULT_WIFI_ICON_STYLE;
    public boolean signalWifiSwapEnabled = SettingsStore.DEFAULT_SIGNAL_WIFI_SWAP_ENABLED;
    public int signalBar1HeightPercent = SettingsStore.DEFAULT_SIGNAL_BAR1_HEIGHT_PERCENT;
    public int signalBar2HeightPercent = SettingsStore.DEFAULT_SIGNAL_BAR2_HEIGHT_PERCENT;
    public int signalBar3HeightPercent = SettingsStore.DEFAULT_SIGNAL_BAR3_HEIGHT_PERCENT;
    public int signalBarCornerRadiusPercent = SettingsStore.DEFAULT_SIGNAL_BAR_CORNER_RADIUS_PERCENT;
    public int signalDotCornerRadiusPercent = SettingsStore.DEFAULT_SIGNAL_DOT_CORNER_RADIUS_PERCENT;
    public int batteryIconStyle = SettingsStore.DEFAULT_BATTERY_ICON_STYLE;
    public boolean batteryLevelTextEnabled = SettingsStore.DEFAULT_BATTERY_LEVEL_TEXT_ENABLED;
    public boolean batteryHollowEnabled = SettingsStore.DEFAULT_BATTERY_HOLLOW_ENABLED;
    public boolean batteryHollowFillFollowsLevel = SettingsStore.DEFAULT_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL;
    public int batteryTextFont = SettingsStore.DEFAULT_BATTERY_TEXT_FONT;
    public int statusBarIconScalePercent = SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT;
    public int batteryInnerTextScalePercent = SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT;
    public int batteryBodyWidthPercent = SettingsStore.DEFAULT_BATTERY_BODY_WIDTH_PERCENT;
    public int batteryBodyHeightPercent = SettingsStore.DEFAULT_BATTERY_BODY_HEIGHT_PERCENT;
    public int batteryCornerRadiusPercent = SettingsStore.DEFAULT_BATTERY_CORNER_RADIUS_PERCENT;
    public int batteryCapWidthPercent = SettingsStore.DEFAULT_BATTERY_CAP_WIDTH_PERCENT;
    public int batteryIconYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_ICON_Y_OFFSET_DP * 10;
    public int batteryTextYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_TEXT_Y_OFFSET_DP * 10;
    public int batteryBoltYOffsetTenthDp = SettingsStore.DEFAULT_BATTERY_BOLT_Y_OFFSET_DP * 10;
    public int signalSingleYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_SINGLE_Y_OFFSET_DP * 10;
    public int signalBadgeYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_BADGE_Y_OFFSET_DP * 10;
    public int signalDualYOffsetTenthDp = SettingsStore.DEFAULT_SIGNAL_DUAL_Y_OFFSET_DP * 10;
    public int wifiYOffsetTenthDp = SettingsStore.DEFAULT_WIFI_Y_OFFSET_DP * 10;
    public int clockRightPaddingOffsetTenthDp = SettingsStore.DEFAULT_CLOCK_RIGHT_PADDING_OFFSET_DP * 10;
    public boolean connectionRateThresholdEnabled = SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
    public int connectionRateShowThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
    public int connectionRateHideThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
    public int connectionRateShowSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
    public int connectionRateHideSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
    public String clockCustomFormat = SettingsStore.DEFAULT_CLOCK_CUSTOM_FORMAT;
    public boolean clockBoldEnabled = SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED;
    public int clockFontWeight = SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT;
    public int clockAndCarrierTextSizePercent = SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT;
    public boolean lockscreenCanvasClockEnabled = SettingsStore.DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED;
    public boolean clockDetailPopupEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_POPUP_ENABLED;
    public boolean clockDetailLunarDateEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED;
    public boolean clockDetailActionGridEnabled = SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED;
    public String clockDetailActionGridItemsJson =
            SettingsStore.DEFAULT_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON;
    public String clockDetailAssistantActionCacheJson =
            SettingsStore.DEFAULT_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON;
    public boolean mbackLongTouchIntentEnabled = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
    public boolean assistantGestureEnabled = SettingsStore.DEFAULT_ASSISTANT_GESTURE_ENABLED;
    public int assistantGestureScenes = SettingsStore.DEFAULT_ASSISTANT_GESTURE_SCENES;
    public int assistantGestureSide = SettingsStore.DEFAULT_ASSISTANT_GESTURE_SIDE;
    public int assistantGestureDistanceDp = SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP;
    public int assistantGestureHoldMs = SettingsStore.DEFAULT_ASSISTANT_GESTURE_HOLD_MS;
    public boolean assistantGestureVerticalLimitEnabled = SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED;
    public int assistantGestureVerticalLimitDp = SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP;
    public int mbackLongTouchAction = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_ACTION;
    public String mbackLongTouchIntentUri = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
    public boolean windowModeSideGestureEnabled = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED;
    public int windowModeSideGestureAction = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION;
    public String windowModeSideGestureIntentUri = SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI;
    public boolean windowModeSideGesturePrewarmEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED;
    public boolean windowModeHoverFullscreenEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED;
    public int windowModeHoverFullscreenTimeoutMs =
            SettingsStore.DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS;
    public boolean windowModeTwoRingLauncherEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED;
    public int windowModeTwoRingOuterAppCount =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT;
    public int windowModeTwoRingInnerAppCount =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT;
    public int windowModeTwoRingInnerIconScalePercent =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT;
    public int windowModeTwoRingInnerRadiusPercent =
            SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT;
    public boolean windowModeRecentInnerRingEnabled =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED;
    public int windowModeRecentInnerRingAppCount =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT;
    public int windowModeRecentInnerRingIconScalePercent =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT;
    public int windowModeRecentInnerRingRadiusPercent =
            SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT;
    public boolean carLinkExpandAppsEnabled = SettingsStore.DEFAULT_CARLINK_EXPAND_APPS_ENABLED;
    public boolean carLinkNeteaseColdStartFixEnabled =
            SettingsStore.DEFAULT_CARLINK_NETEASE_COLD_START_FIX_ENABLED;
    public boolean carLinkDayNightIsolationEnabled =
            SettingsStore.DEFAULT_CARLINK_DAY_NIGHT_ISOLATION_ENABLED;
    public boolean carLinkPeriodicRedrawDisabled =
            SettingsStore.DEFAULT_CARLINK_PERIODIC_REDRAW_DISABLED;
    public boolean carLinkTouchLogFilterEnabled =
            SettingsStore.DEFAULT_CARLINK_TOUCH_LOG_FILTER_ENABLED;
    public boolean carLinkTaskListenerCleanupEnabled =
            SettingsStore.DEFAULT_CARLINK_TASK_LISTENER_CLEANUP_ENABLED;
    public boolean mbackNavBarTransparent = SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
    public boolean notificationAppIconEnabled = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED;
    public boolean anipIconEnabled = SettingsStore.DEFAULT_ANIP_ICON_ENABLED;
    public String anipIconMode = SettingsStore.DEFAULT_ANIP_ICON_MODE;

    /**
     * The persisted per-application ANIP overrides, or the empty default.
     *
     * <p>Lives here rather than in a helper class so callers do not need an extra indirection for a
     * single null check.
     */
    public String anipOverrides() {
        return anipIconMode == null ? SettingsStore.DEFAULT_ANIP_ICON_MODE : anipIconMode;
    }
    public int anipIconColorMode = SettingsStore.DEFAULT_ANIP_ICON_COLOR_MODE;
    public int notificationAppIconSizeDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP;
    public int notificationAppIconPaddingDp = SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP;
    public boolean notificationCardCornerRadiusEnabled =
            SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED;
    public int notificationCardCornerRadiusDp =
            SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_DP;
    public boolean launcherRecentsCardCornerRadiusEnabled =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED;
    public int launcherRecentsCardCornerRadiusDp =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP;
    public boolean launcherIosStackRecentsEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED;
    public boolean launcherIosStackRecentsBlurEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED;
    public boolean launcherIosStackRecentsShadowEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_SHADOW_ENABLED;
    public boolean launcherIosStackRecentsClearAllButtonEnabled =
            SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED;
    public boolean launcherStackCurrentAppCentered =
            SettingsStore.DEFAULT_LAUNCHER_STACK_CURRENT_APP_CENTERED;
    public int launcherStackRightVisiblePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT;
    public int launcherStackLeftMovePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_MOVE_PERCENT;
    public int launcherStackLeftRestInsetPercent = SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT;
    public int launcherStackMinScalePercent = SettingsStore.DEFAULT_LAUNCHER_STACK_MIN_SCALE_PERCENT;
    public int launcherStackScaleCurveX1Percent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT;
    public int launcherStackScaleCurveY1Percent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT;
    public int launcherStackScaleCurveX2Percent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT;
    public int launcherStackScaleCurveY2Percent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT;
    public int launcherStackMaxLayers = SettingsStore.DEFAULT_LAUNCHER_STACK_MAX_LAYERS;
    public int launcherStackEntryLiftPercent = SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_LIFT_PERCENT;
    public int launcherStackEntryInitialSpreadPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT;
    public int launcherStackReleaseInitialSpreadPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT;
    public int launcherStackDesktopEntryVisibleCount =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT;
    public int launcherStackDesktopEntryAnchorIndex =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX;
    public int launcherStackGestureReleaseDurationMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS;
    public int launcherStackStableVisibleRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS;
    public int launcherStackEntryLightRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS;
    public int launcherStackGestureReleaseCoreRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS;
    public int launcherStackAppFlowLightRadius =
            SettingsStore.DEFAULT_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS;
    public int launcherStackRightBaseSpeedupPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT;
    public int launcherStackRightSpeedupPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT;
    public int launcherStackHorizontalDragResistancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT;
    public int launcherStackHorizontalPageThresholdPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT;
    public int launcherStackHorizontalFlingVelocityDp =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP;
    public int launcherStackHorizontalSnapDurationMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS;
    public int launcherStackBlankExitScaleDeltaPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT;
    public int launcherStackBlankExitExtraTravelPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT;
    public int launcherStackTaskLaunchExtraWidthPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT;
    public int launcherStackDismissSuccessAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS;
    public int launcherStackDismissCancelAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS;
    public int launcherStackDismissRelayoutAnimMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS;
    public int launcherStackDismissDragRelayoutMaxPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT;
    public int launcherStackDismissSecondaryDominancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT;
    public int launcherStackDismissMinFlingVelocity =
            SettingsStore.DEFAULT_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY;
    public int launcherStackMenuPullThresholdDp =
            SettingsStore.DEFAULT_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP;
    public int launcherStackContentMaxBlurDp = SettingsStore.DEFAULT_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP;
    public int launcherStackShadowElevationDp =
            SettingsStore.DEFAULT_LAUNCHER_STACK_SHADOW_ELEVATION_DP;
    public int launcherStackContentBlurStartAlphaPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT;
    public int launcherStackLeftFadeDistancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT;
    public int launcherStackTitleFadeDistancePercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_TITLE_FADE_DISTANCE_PERCENT;
    public int launcherStackLeftReleaseAlphaThresholdPercent =
            SettingsStore.DEFAULT_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT;
    public int launcherStackScrollFrameRate = SettingsStore.DEFAULT_LAUNCHER_STACK_SCROLL_FRAME_RATE;
    public int launcherStackFrameRateReleaseDelayMs =
            SettingsStore.DEFAULT_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS;
    public boolean launcherAicyEntryEnabled = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_ENABLED;
    public String launcherAicyEntryText = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TEXT;
    public String launcherAicyEntryTarget = SettingsStore.DEFAULT_LAUNCHER_AICY_ENTRY_TARGET;
    public String launcherFolderBgColor = SettingsStore.DEFAULT_LAUNCHER_FOLDER_BG_COLOR;
    public boolean notificationSystemBlurOnlyEnabled =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED;
    public int notificationSystemBlurCarrierColorMode =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE;
    public String notificationSystemBlurLightColor =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR;
    public String notificationSystemBlurDarkColor =
            SettingsStore.DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR;
    public boolean notificationTextFollowStatusBarEnabled =
            SettingsStore.DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED;
    public boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
    public int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
    public int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;
    public int mbackPillLength = SettingsStore.DEFAULT_MBACK_PILL_LENGTH;
    public int mbackPillThickness = SettingsStore.DEFAULT_MBACK_PILL_THICKNESS;
    public boolean mbackPillInteractionSyncEnabled =
            SettingsStore.DEFAULT_MBACK_PILL_INTERACTION_SYNC_ENABLED;
    public boolean imeReplaceOriginalControlBar = SettingsStore.DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR;
    public String imeControlBarButtonSlots = SettingsStore.DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;
    public int imeControlBarIconScalePercent = SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT;
    public int imeControlBarIconAlphaPercent = SettingsStore.DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT;
    public int imeControlBarYOffsetTenthDp = SettingsStore.DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP * 10;
    public boolean telephonyDebugEnabled = SettingsStore.DEFAULT_TELEPHONY_DEBUG_ENABLED;
    public boolean wifiPerfLoggingEnabled = SettingsStore.DEFAULT_WIFI_PERF_LOGGING_ENABLED;
    public boolean launcherRecentsPerfLoggingEnabled =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED;
    public boolean launcherRecentsFlowLoggingEnabled =
            SettingsStore.DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED;
    public boolean oneMindPerfDisableEnabled = SettingsStore.DEFAULT_ONEMIND_PERF_DISABLE_ENABLED;
    public boolean mzSafeBackgroundOptimizationEnabled =
            SettingsStore.DEFAULT_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED;
    public boolean oneMindLogcatEnabled = SettingsStore.DEFAULT_ONEMIND_LOGCAT_ENABLED;
    public int telephonyDebugSimCount = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SIM_COUNT;
    public int telephonyDebugDefaultDataSlot = SettingsStore.DEFAULT_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT;
    public int telephonyDebugSlot1NetworkProfile = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE;
    public int telephonyDebugSlot1SignalLevel = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL;
    public int telephonyDebugSlot2NetworkProfile = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE;
    public int telephonyDebugSlot2SignalLevel = SettingsStore.DEFAULT_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL;

    public static ModuleConfig load(Context context) {
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

    public static void setConfigChangedCallback(Runnable callback) {
        configChangedCallback = callback;
    }

    public static void attachToModule(XposedModule module) {
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

    public static void rememberSystemUiContext(Context context) {
        if (context == null || systemUiContext != null) {
            return;
        }
        systemUiContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    public static SharedPreferences getRemotePreferences() {
        return remotePrefs;
    }

    public static Context getSystemUiContext() {
        return systemUiContext;
    }

    private static ModuleConfig fromSharedPreferences(SharedPreferences prefs) {
        if (prefs == null) {
            return null;
        }
        try {
            ModuleConfig config = new ModuleConfig();
            config.shareTargetsEnabled = SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_SHARE_TARGETS_ENABLED, SettingsStore.DEFAULT_SHARE_TARGETS_ENABLED);
            config.shareTargetProfiles = ShareTargetProfiles.parse(
                    SettingsStore.readString(prefs, SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
            config.shareTargetRules = ShareTargetRules.parse(
                    SettingsStore.readString(prefs, SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                    SettingsStore.readString(prefs, SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
            config.enabled = SettingsStore.readBoolean(prefs, SettingsStore.KEY_ENABLED, SettingsStore.DEFAULT_ENABLED);
            config.hiddenStatusBarSlots = StatusBarIconVisibility.readHiddenSlots(
                    key -> SettingsStore.readBoolean(prefs, key, false));
            config.statusBarTintEnabled = SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_STATUS_BAR_TINT_ENABLED, SettingsStore.DEFAULT_STATUS_BAR_TINT_ENABLED);
            for (int i = 0; i < config.statusBarTintModes.length; i++) {
                int mode = SettingsStore.readInt(prefs, SettingsStore.STATUS_BAR_TINT_KEYS[i], 0);
                config.statusBarTintModes[i] = mode == 1 || mode == 2 ? mode : 0;
            }
            config.batteryCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_BATTERY_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_BATTERY_CODE_DRAW_ENABLED);
            config.circleAnimation = CircleBatteryAnimationConfig.load(prefs);
            config.cameraCircleBatteryEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED);
            config.cameraCircleBatteryHideIconEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED);
            // Legacy tint is consumed by animation migration; the fallback paint follows the new palette.
            config.cameraCircleBatteryTintEnabled =
                    config.circleAnimation.palette == CircleBatteryAnimationConfig.RAINBOW;
            config.cameraCircleBatteryTransparencyTenthPercent = Math.max(0, Math.min(1000,
                    SettingsStore.readInt(prefs,
                            SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT,
                            SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT)));
            config.cameraCircleBatteryNormalLightColor = 0xFF000000 | SettingsStore.readInt(prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR);
            config.cameraCircleBatteryNormalDarkColor = 0xFF000000 | SettingsStore.readInt(prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR);
            config.cameraCircleBatteryChargingColor = 0xFF000000 | SettingsStore.readInt(prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR);
            config.cameraCircleBatteryPowerSaveColor = 0xFF000000 | SettingsStore.readInt(prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR);
            config.cameraCircleBatteryLowColor = 0xFF000000 | SettingsStore.readInt(prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_LOW_COLOR,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_LOW_COLOR);
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
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP * 100);
            config.cameraCircleBatteryYOffsetTenthDp = SettingsStore.readPositionOffsetTenthDp(
                    prefs,
                    SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP,
                    SettingsStore.DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP * 100);
            config.signalCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_CODE_DRAW_ENABLED);
            config.signalMobileTypeBadgeEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
                    SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED);
            config.signalMobileTypeBadge5gText = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_5G_TEXT,
                    SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5G_TEXT);
            config.signalMobileTypeBadge5gaText = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_5GA_TEXT,
                    SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_5GA_TEXT);
            config.signalMobileTypeBadgeNon5gText = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_SIGNAL_MOBILE_TYPE_BADGE_NON_5G_TEXT,
                    SettingsStore.DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_NON_5G_TEXT);
            config.wifiCodeDrawEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_WIFI_CODE_DRAW_ENABLED,
                    SettingsStore.DEFAULT_WIFI_CODE_DRAW_ENABLED);
            config.wifiIconStyle = WifiIconStyles.normalize(SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_WIFI_ICON_STYLE,
                    SettingsStore.DEFAULT_WIFI_ICON_STYLE));
            config.wifiBandCornerPercent = Math.max(0, Math.min(45, SettingsStore.readInt(
                    prefs, SettingsStore.KEY_WIFI_BAND_CORNER_PERCENT,
                    SettingsStore.DEFAULT_WIFI_BAND_CORNER_PERCENT)));
            config.wifiTipCornerPercent = Math.max(0, Math.min(30, SettingsStore.readInt(
                    prefs, SettingsStore.KEY_WIFI_TIP_CORNER_PERCENT,
                    SettingsStore.DEFAULT_WIFI_TIP_CORNER_PERCENT)));
            config.wifiBandGapPercent = Math.max(40, Math.min(120, SettingsStore.readInt(
                    prefs, SettingsStore.KEY_WIFI_BAND_GAP_PERCENT,
                    SettingsStore.DEFAULT_WIFI_BAND_GAP_PERCENT)));
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
            config.batteryBodyWidthPercent = SettingsStore.normalizeBatteryGeometryPercent(
                    SettingsStore.KEY_BATTERY_BODY_WIDTH_PERCENT,
                    SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_BODY_WIDTH_PERCENT,
                            SettingsStore.DEFAULT_BATTERY_BODY_WIDTH_PERCENT));
            config.batteryBodyHeightPercent = SettingsStore.normalizeBatteryGeometryPercent(
                    SettingsStore.KEY_BATTERY_BODY_HEIGHT_PERCENT,
                    SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_BODY_HEIGHT_PERCENT,
                            SettingsStore.DEFAULT_BATTERY_BODY_HEIGHT_PERCENT));
            config.batteryCornerRadiusPercent = SettingsStore.normalizeBatteryGeometryPercent(
                    SettingsStore.KEY_BATTERY_CORNER_RADIUS_PERCENT,
                    SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_CORNER_RADIUS_PERCENT,
                            SettingsStore.DEFAULT_BATTERY_CORNER_RADIUS_PERCENT));
            config.batteryCapWidthPercent = SettingsStore.normalizeBatteryGeometryPercent(
                    SettingsStore.KEY_BATTERY_CAP_WIDTH_PERCENT,
                    SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_CAP_WIDTH_PERCENT,
                            SettingsStore.DEFAULT_BATTERY_CAP_WIDTH_PERCENT));
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
            config.signalBar1HeightPercent = Math.max(0, Math.min(100, SettingsStore.readInt(prefs, SettingsStore.KEY_SIGNAL_BAR1_HEIGHT_PERCENT, SettingsStore.DEFAULT_SIGNAL_BAR1_HEIGHT_PERCENT)));
            config.signalBar2HeightPercent = Math.max(0, Math.min(100, SettingsStore.readInt(prefs, SettingsStore.KEY_SIGNAL_BAR2_HEIGHT_PERCENT, SettingsStore.DEFAULT_SIGNAL_BAR2_HEIGHT_PERCENT)));
            config.signalBar3HeightPercent = Math.max(0, Math.min(100, SettingsStore.readInt(prefs, SettingsStore.KEY_SIGNAL_BAR3_HEIGHT_PERCENT, SettingsStore.DEFAULT_SIGNAL_BAR3_HEIGHT_PERCENT)));
            config.signalBarCornerRadiusPercent = Math.max(0, Math.min(100, SettingsStore.readInt(prefs, SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT, SettingsStore.DEFAULT_SIGNAL_BAR_CORNER_RADIUS_PERCENT)));
            config.signalDotCornerRadiusPercent = Math.max(0, Math.min(100, SettingsStore.readInt(prefs, SettingsStore.KEY_SIGNAL_DOT_CORNER_RADIUS_PERCENT, SettingsStore.DEFAULT_SIGNAL_DOT_CORNER_RADIUS_PERCENT)));
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
            config.assistantGestureEnabled = SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED, SettingsStore.DEFAULT_ASSISTANT_GESTURE_ENABLED);
            config.assistantGestureScenes = SettingsStore.normalizeAssistantGestureScenes(SettingsStore.readInt(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_SCENES, SettingsStore.DEFAULT_ASSISTANT_GESTURE_SCENES));
            config.assistantGestureSide = SettingsStore.normalizeAssistantGestureSide(SettingsStore.readInt(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_SIDE, SettingsStore.DEFAULT_ASSISTANT_GESTURE_SIDE));
            config.assistantGestureDistanceDp = Math.max(40, Math.min(240, SettingsStore.readInt(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP)));
            config.assistantGestureHoldMs = Math.max(250, Math.min(2000, SettingsStore.readInt(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_HOLD_MS, SettingsStore.DEFAULT_ASSISTANT_GESTURE_HOLD_MS)));
            config.assistantGestureVerticalLimitEnabled = SettingsStore.readBoolean(prefs,
                    SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED,
                    SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED);
            config.assistantGestureVerticalLimitDp = SettingsStore.normalizeAssistantGestureVerticalLimitDp(
                    SettingsStore.readInt(prefs, SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP,
                            SettingsStore.DEFAULT_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP));
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
            config.windowModeTwoRingOuterAppCount =
                    SettingsStore.normalizeWindowModeTwoRingOuterAppCount(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_TWO_RING_OUTER_APP_COUNT,
                                    SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT));
            config.windowModeTwoRingInnerAppCount =
                    SettingsStore.normalizeWindowModeTwoRingInnerAppCount(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_TWO_RING_INNER_APP_COUNT,
                                    SettingsStore.DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT));
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
            config.windowModeRecentInnerRingAppCount =
                    SettingsStore.normalizeWindowModeRecentInnerRingAppCount(
                            SettingsStore.readInt(
                                    prefs,
                                    SettingsStore.KEY_WINDOWMODE_RECENT_INNER_RING_APP_COUNT,
                                    SettingsStore.DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT));
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
            config.carLinkExpandAppsEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_EXPAND_APPS_ENABLED,
                    SettingsStore.DEFAULT_CARLINK_EXPAND_APPS_ENABLED);
            config.carLinkNeteaseColdStartFixEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_NETEASE_COLD_START_FIX_ENABLED,
                    SettingsStore.DEFAULT_CARLINK_NETEASE_COLD_START_FIX_ENABLED);
            config.carLinkDayNightIsolationEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_DAY_NIGHT_ISOLATION_ENABLED,
                    SettingsStore.DEFAULT_CARLINK_DAY_NIGHT_ISOLATION_ENABLED);
            config.carLinkPeriodicRedrawDisabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_PERIODIC_REDRAW_DISABLED,
                    SettingsStore.DEFAULT_CARLINK_PERIODIC_REDRAW_DISABLED);
            config.carLinkTouchLogFilterEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_TOUCH_LOG_FILTER_ENABLED,
                    SettingsStore.DEFAULT_CARLINK_TOUCH_LOG_FILTER_ENABLED);
            config.carLinkTaskListenerCleanupEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_CARLINK_TASK_LISTENER_CLEANUP_ENABLED,
                    SettingsStore.DEFAULT_CARLINK_TASK_LISTENER_CLEANUP_ENABLED);
            config.mbackNavBarTransparent = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT,
                    SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT);
            config.notificationAppIconEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_APP_ICON_ENABLED,
                    SettingsStore.DEFAULT_NOTIFICATION_APP_ICON_ENABLED);
            config.anipIconEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_ANIP_ICON_ENABLED,
                    SettingsStore.DEFAULT_ANIP_ICON_ENABLED);
            config.anipIconMode = SettingsStore.readString(
                    prefs,
                    SettingsStore.KEY_ANIP_ICON_MODE,
                    SettingsStore.DEFAULT_ANIP_ICON_MODE);
            config.anipIconColorMode = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_ANIP_ICON_COLOR_MODE,
                    SettingsStore.DEFAULT_ANIP_ICON_COLOR_MODE);
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
            config.notificationCardCornerRadiusEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED,
                    SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED);
            config.notificationCardCornerRadiusDp = SettingsStore.normalizeCardCornerRadiusDp(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_NOTIFICATION_CARD_CORNER_RADIUS_DP,
                            SettingsStore.DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_DP));
            config.launcherRecentsCardCornerRadiusEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED);
            config.launcherRecentsCardCornerRadiusDp = SettingsStore.normalizeCardCornerRadiusDp(
                    SettingsStore.readInt(
                            prefs,
                            SettingsStore.KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP,
                            SettingsStore.DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP));
            config.launcherIosStackRecentsEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED);
            config.launcherIosStackRecentsBlurEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED);
            config.launcherIosStackRecentsShadowEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_SHADOW_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_SHADOW_ENABLED);
            config.launcherIosStackRecentsClearAllButtonEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED,
                    SettingsStore.DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED);
            config.launcherStackCurrentAppCentered = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_LAUNCHER_STACK_CURRENT_APP_CENTERED,
                    SettingsStore.DEFAULT_LAUNCHER_STACK_CURRENT_APP_CENTERED);
            config.launcherStackRightVisiblePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT);
            config.launcherStackLeftMovePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT);
            config.launcherStackLeftRestInsetPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT);
            config.launcherStackMinScalePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT);
            config.launcherStackScaleCurveX1Percent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT);
            config.launcherStackScaleCurveY1Percent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT);
            config.launcherStackScaleCurveX2Percent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT);
            config.launcherStackScaleCurveY2Percent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT);
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
            config.launcherStackShadowElevationDp = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_SHADOW_ELEVATION_DP);
            config.launcherStackContentBlurStartAlphaPercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT);
            config.launcherStackLeftFadeDistancePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT);
            config.launcherStackTitleFadeDistancePercent = readLauncherStackParameter(
                    prefs, SettingsStore.KEY_LAUNCHER_STACK_TITLE_FADE_DISTANCE_PERCENT);
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
            config.mbackPillLength = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_MBACK_PILL_LENGTH,
                    SettingsStore.DEFAULT_MBACK_PILL_LENGTH);
            config.mbackPillThickness = SettingsStore.readInt(
                    prefs,
                    SettingsStore.KEY_MBACK_PILL_THICKNESS,
                    SettingsStore.DEFAULT_MBACK_PILL_THICKNESS);
            config.mbackPillInteractionSyncEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MBACK_PILL_INTERACTION_SYNC_ENABLED,
                    SettingsStore.DEFAULT_MBACK_PILL_INTERACTION_SYNC_ENABLED);
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
            config.mzSafeBackgroundOptimizationEnabled = SettingsStore.readBoolean(
                    prefs,
                    SettingsStore.KEY_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED,
                    SettingsStore.DEFAULT_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED);
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
