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
    static final int POSITION_OFFSET_STORAGE_VERSION_CAMERA_TENTH_DP = 2;
    static final int POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP = 3;
    static final int POSITION_OFFSET_MIN_TENTH_DP = -240;
    static final int POSITION_OFFSET_MAX_TENTH_DP = 240;
    static final int CAMERA_CIRCLE_BATTERY_OFFSET_MIN_TENTH_DP = -3000;
    static final int CAMERA_CIRCLE_BATTERY_OFFSET_MAX_TENTH_DP = 3000;

    static final String KEY_ENABLED = "enabled";
    static final String KEY_BATTERY_CODE_DRAW_ENABLED = "battery_code_draw_enabled";
    static final String KEY_CAMERA_CIRCLE_BATTERY_ENABLED = "camera_circle_battery_enabled";
    static final String KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED =
            "camera_circle_battery_hide_icon_enabled";
    static final String KEY_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT =
            "camera_circle_battery_radius_percent";
    static final String KEY_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT =
            "camera_circle_battery_stroke_percent";
    static final String KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP =
            "camera_circle_battery_x_offset_dp";
    static final String KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP =
            "camera_circle_battery_y_offset_dp";
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
    static final String KEY_LOCKSCREEN_CANVAS_CLOCK_ENABLED = "lockscreen_canvas_clock_enabled";
    static final String KEY_CLOCK_DETAIL_POPUP_ENABLED = "clock_detail_popup_enabled";
    static final String KEY_CLOCK_DETAIL_LUNAR_DATE_ENABLED = "clock_detail_lunar_date_enabled";
    static final String KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED = "clock_detail_action_grid_enabled";
    static final String KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON = "clock_detail_action_grid_items_json";
    static final String KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON =
            "clock_detail_assistant_action_cache_json";
    static final String KEY_MBACK_LONG_TOUCH_URL_ENABLED = "mback_long_touch_url_enabled";
    static final String KEY_MBACK_LONG_TOUCH_ACTION = "mback_long_touch_action";
    static final String KEY_MBACK_LONG_TOUCH_INTENT_URI = "mback_long_touch_intent_uri";
    static final String KEY_WINDOWMODE_SIDE_GESTURE_ENABLED = "windowmode_side_gesture_enabled";
    static final String KEY_WINDOWMODE_SIDE_GESTURE_ACTION = "windowmode_side_gesture_action";
    static final String KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI = "windowmode_side_gesture_intent_uri";
    static final String KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED =
            "windowmode_side_gesture_prewarm_enabled";
    static final String KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED =
            "windowmode_hover_fullscreen_enabled";
    static final String KEY_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS =
            "windowmode_hover_fullscreen_timeout_ms";
    static final String KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED =
            "windowmode_two_ring_launcher_enabled";
    static final String KEY_WINDOWMODE_TWO_RING_OUTER_APP_COUNT =
            "windowmode_two_ring_outer_app_count";
    static final String KEY_WINDOWMODE_TWO_RING_INNER_APP_COUNT =
            "windowmode_two_ring_inner_app_count";
    static final String KEY_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT =
            "windowmode_two_ring_inner_icon_scale_percent";
    static final String KEY_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT =
            "windowmode_two_ring_inner_radius_percent";
    static final String KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED =
            "windowmode_recent_inner_ring_enabled";
    static final String KEY_WINDOWMODE_RECENT_INNER_RING_APP_COUNT =
            "windowmode_recent_inner_ring_app_count";
    static final String KEY_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT =
            "windowmode_recent_inner_ring_icon_scale_percent";
    static final String KEY_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT =
            "windowmode_recent_inner_ring_radius_percent";
    static final String KEY_MBACK_NAV_BAR_TRANSPARENT = "mback_nav_bar_transparent";
    static final String KEY_NOTIFICATION_APP_ICON_ENABLED = "notification_app_icon_enabled";
    static final String KEY_NOTIFICATION_APP_ICON_SIZE_DP = "notification_app_icon_size_dp";
    static final String KEY_NOTIFICATION_APP_ICON_PADDING_DP = "notification_app_icon_padding_dp";
    static final String KEY_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED =
            "notification_card_corner_radius_enabled";
    static final String KEY_NOTIFICATION_CARD_CORNER_RADIUS_DP =
            "notification_card_corner_radius_dp";
    static final String KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED =
            "launcher_recents_card_corner_radius_enabled";
    static final String KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP =
            "launcher_recents_card_corner_radius_dp";
    static final String KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED =
            "launcher_ios_stack_recents_enabled";
    static final String KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED =
            "launcher_ios_stack_recents_blur_enabled";
    static final String KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED =
            "launcher_ios_stack_recents_clear_all_button_enabled";
    static final String KEY_LAUNCHER_STACK_CURRENT_APP_CENTERED =
            "launcher_stack_current_app_centered";
    static final String KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT =
            "launcher_stack_right_visible_percent";
    static final String KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT =
            "launcher_stack_left_move_percent";
    static final String KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT =
            "launcher_stack_left_rest_inset_percent";
    static final String KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT =
            "launcher_stack_min_scale_percent";
    static final String KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT =
            "launcher_stack_scale_curve_x1_percent";
    static final String KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT =
            "launcher_stack_scale_curve_y1_percent";
    static final String KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT =
            "launcher_stack_scale_curve_x2_percent";
    static final String KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT =
            "launcher_stack_scale_curve_y2_percent";
    static final String KEY_LAUNCHER_STACK_MAX_LAYERS =
            "launcher_stack_max_layers";
    static final String KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT =
            "launcher_stack_entry_lift_percent";
    static final String KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT =
            "launcher_stack_entry_initial_spread_percent";
    static final String KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT =
            "launcher_stack_release_initial_spread_percent";
    static final String KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT =
            "launcher_stack_desktop_entry_visible_count";
    static final String KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX =
            "launcher_stack_desktop_entry_anchor_index";
    static final String KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS =
            "launcher_stack_gesture_release_duration_ms";
    static final String KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS =
            "launcher_stack_stable_visible_radius";
    static final String KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS =
            "launcher_stack_entry_light_radius";
    static final String KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS =
            "launcher_stack_gesture_release_core_radius";
    static final String KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS =
            "launcher_stack_app_flow_light_radius";
    static final String KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT =
            "launcher_stack_right_base_speedup_percent";
    static final String KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT =
            "launcher_stack_right_speedup_percent";
    static final String KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT =
            "launcher_stack_horizontal_drag_resistance_percent";
    static final String KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT =
            "launcher_stack_horizontal_page_threshold_percent";
    static final String KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP =
            "launcher_stack_horizontal_fling_velocity_dp";
    static final String KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS =
            "launcher_stack_horizontal_snap_duration_ms";
    static final String KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT =
            "launcher_stack_blank_exit_scale_delta_percent";
    static final String KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT =
            "launcher_stack_blank_exit_extra_travel_percent";
    static final String KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT =
            "launcher_stack_task_launch_extra_width_percent";
    static final String KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS =
            "launcher_stack_dismiss_success_anim_ms";
    static final String KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS =
            "launcher_stack_dismiss_cancel_anim_ms";
    static final String KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS =
            "launcher_stack_dismiss_relayout_anim_ms";
    static final String KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT =
            "launcher_stack_dismiss_drag_relayout_max_percent";
    static final String KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT =
            "launcher_stack_dismiss_secondary_dominance_percent";
    static final String KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY =
            "launcher_stack_dismiss_min_fling_velocity";
    static final String KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP =
            "launcher_stack_menu_pull_threshold_dp";
    static final String KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP =
            "launcher_stack_content_max_blur_dp";
    static final String KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT =
            "launcher_stack_content_medium_blur_percent";
    static final String KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT =
            "launcher_stack_content_blur_start_alpha_percent";
    static final String KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT =
            "launcher_stack_left_fade_distance_percent";
    static final String KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT =
            "launcher_stack_left_release_alpha_threshold_percent";
    static final String KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE =
            "launcher_stack_scroll_frame_rate";
    static final String KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS =
            "launcher_stack_frame_rate_release_delay_ms";
    static final String KEY_LAUNCHER_AICY_ENTRY_ENABLED = "launcher_aicy_entry_enabled";
    static final String KEY_LAUNCHER_AICY_ENTRY_TEXT = "launcher_aicy_entry_text";
    static final String KEY_LAUNCHER_AICY_ENTRY_TARGET = "launcher_aicy_entry_target";
    static final String KEY_LAUNCHER_FOLDER_BG_COLOR = "launcher_folder_bg_color";
    static final String KEY_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED =
            "notification_system_blur_only_enabled";
    static final String KEY_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE =
            "notification_system_blur_carrier_color_mode";
    static final String KEY_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR =
            "notification_system_blur_light_color";
    static final String KEY_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR =
            "notification_system_blur_dark_color";
    static final String KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED =
            "notification_text_follow_status_bar_enabled";
    static final String KEY_MBACK_INSET_SIZE = "mback_inset_size";
    static final String KEY_MBACK_NAV_BAR_HEIGHT = "mback_nav_bar_height";
    static final String KEY_MBACK_HIDE_PILL = "mback_hide_pill";
    static final String KEY_MBACK_PILL_LENGTH = "mback_pill_length";
    static final String KEY_MBACK_PILL_THICKNESS = "mback_pill_thickness";
    static final String KEY_MBACK_PILL_INTERACTION_SYNC_ENABLED =
            "mback_pill_interaction_sync_enabled";
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
    static final String KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED =
            "launcher_recents_perf_logging_enabled";
    static final String KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED =
            "launcher_recents_flow_logging_enabled";
    static final String KEY_ONEMIND_PERF_DISABLE_ENABLED =
            "onemind_perf_disable_enabled";
    static final String KEY_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED =
            "mz_safe_background_optimization_enabled";
    static final String KEY_F2FS_GC_ENABLED = "f2fs_gc_enabled";
    static final String KEY_ONEMIND_LOGCAT_ENABLED =
            "onemind_logcat_enabled";
    static final String KEY_ONEMIND_HOOK_INSTALLED_UPTIME_MS =
            "__onemind_hook_installed_uptime_ms";
    static final String KEY_ONEMIND_HOOK_INTERCEPT_COUNT =
            "__onemind_hook_intercept_count";
    static final String KEY_ONEMIND_HOOK_LAST_INTERCEPT_UPTIME_MS =
            "__onemind_hook_last_intercept_uptime_ms";
    static final String KEY_ONEMIND_HOOK_LAST_INTERCEPT_POINT =
            "__onemind_hook_last_intercept_point";
    static final String KEY_TELEPHONY_DEBUG_SIM_COUNT = "telephony_debug_sim_count";
    static final String KEY_TELEPHONY_DEBUG_DEFAULT_DATA_SLOT = "telephony_debug_default_data_slot";
    static final String KEY_TELEPHONY_DEBUG_SLOT1_NETWORK_PROFILE = "telephony_debug_slot1_network_profile";
    static final String KEY_TELEPHONY_DEBUG_SLOT1_SIGNAL_LEVEL = "telephony_debug_slot1_signal_level";
    static final String KEY_TELEPHONY_DEBUG_SLOT2_NETWORK_PROFILE = "telephony_debug_slot2_network_profile";
    static final String KEY_TELEPHONY_DEBUG_SLOT2_SIGNAL_LEVEL = "telephony_debug_slot2_signal_level";
    static final boolean DEFAULT_ENABLED = true;
    static final boolean DEFAULT_BATTERY_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED = false;
    static final boolean DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED = false;
    static final int DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT = 100;
    static final int DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT = 100;
    static final int DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP = 0;
    static final int DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP = 0;
    static final boolean DEFAULT_SIGNAL_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_MOBILE_TYPE_BADGE_ENABLED = true;
    static final boolean DEFAULT_WIFI_CODE_DRAW_ENABLED = true;
    static final boolean DEFAULT_SIGNAL_WIFI_SWAP_ENABLED = false;
    static final int BATTERY_STYLE_IOS = 0;
    static final int BATTERY_STYLE_ONEUI = 1;
    static final int BATTERY_STYLE_FLYME_CAPSULE = 2;
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
    static final boolean DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED = false;
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
    static final boolean DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED = true;
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
    static final boolean DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED = false;
    static final int DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION = MBACK_LONG_TOUCH_ACTION_INTENT_URI;
    static final String DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI = "";
    static final boolean DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED = false;
    static final boolean DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED = false;
    static final int DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS = 1000;
    static final boolean DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED = false;
    static final int DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT = 7;
    static final int DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT = 4;
    static final int DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT = 62;
    static final boolean DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED = false;
    static final int DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT = 4;
    static final int DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT = 38;
    static final boolean DEFAULT_MBACK_NAV_BAR_TRANSPARENT = false;
    static final boolean DEFAULT_NOTIFICATION_APP_ICON_ENABLED = false;
    static final int DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP = 20;
    static final int DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP = 0;
    static final boolean DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED = false;
    static final int DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_DP = 14;
    static final boolean DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED = false;
    static final int DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP = 24;
    static final boolean DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED = false;
    static final boolean DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED = true;
    static final boolean DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED = true;
    static final boolean DEFAULT_LAUNCHER_STACK_CURRENT_APP_CENTERED = false;
    static final int DEFAULT_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT = 80;
    static final int DEFAULT_LAUNCHER_STACK_LEFT_MOVE_PERCENT = 45;
    static final int DEFAULT_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT = -15;
    static final int DEFAULT_LAUNCHER_STACK_MIN_SCALE_PERCENT = 92;
    static final int DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT = 33;
    static final int DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT = 0;
    static final int DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT = 67;
    static final int DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT = 100;
    static final int DEFAULT_LAUNCHER_STACK_MAX_LAYERS = 3;
    static final int DEFAULT_LAUNCHER_STACK_ENTRY_LIFT_PERCENT = 5;
    static final int DEFAULT_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT = 80;
    static final int DEFAULT_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT = 35;
    static final int DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT = 3;
    static final int DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX = 0;
    static final int DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS = 320;
    static final int DEFAULT_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS = 2;
    static final int DEFAULT_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS = 1;
    static final int DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS = 2;
    static final int DEFAULT_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS = 3;
    static final int DEFAULT_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT = 16;
    static final int DEFAULT_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT = 40;
    static final int DEFAULT_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT = 0;
    static final int DEFAULT_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT = 14;
    static final int DEFAULT_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP = 500;
    static final int DEFAULT_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS = 750;
    static final int DEFAULT_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT = 4;
    static final int DEFAULT_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT = 18;
    static final int DEFAULT_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT = 25;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS = 180;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS = 320;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS = 320;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT = 50;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT = 120;
    static final int DEFAULT_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY = 1200;
    static final int DEFAULT_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP = 100;
    static final int DEFAULT_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP = 18;
    static final int DEFAULT_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT = 50;
    static final int DEFAULT_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT = 85;
    static final int DEFAULT_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT = 24;
    static final int DEFAULT_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT = 5;
    static final int DEFAULT_LAUNCHER_STACK_SCROLL_FRAME_RATE = 120;
    static final int DEFAULT_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS = 5000;
    static final boolean DEFAULT_LAUNCHER_AICY_ENTRY_ENABLED = false;
    static final String DEFAULT_LAUNCHER_AICY_ENTRY_TEXT = "Aicy";
    static final String DEFAULT_LAUNCHER_AICY_ENTRY_TARGET = "";
    static final String DEFAULT_LAUNCHER_FOLDER_BG_COLOR = "";
    static final boolean DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED = false;
    static final int NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_FOLLOW_SYSTEM = 0;
    static final int NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_LIGHT = 1;
    static final int NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_DARK = 2;
    static final int DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE =
            NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_FOLLOW_SYSTEM;
    static final String DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR = "#26FFFFFF";
    static final String DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR = "#331A1A1A";
    static final boolean DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED = false;
    static final int DEFAULT_MBACK_INSET_SIZE = -1;
    static final int DEFAULT_MBACK_NAV_BAR_HEIGHT = -1;
    static final boolean DEFAULT_MBACK_HIDE_PILL = false;
    static final int DEFAULT_MBACK_PILL_LENGTH = -1;
    static final int DEFAULT_MBACK_PILL_THICKNESS = -1;
    static final boolean DEFAULT_MBACK_PILL_INTERACTION_SYNC_ENABLED = false;
    static final boolean DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR = false;
    static final String DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS =
            "paste,undo,delete,select_all,copy,switch_ime,stock_back";
    static final int DEFAULT_IME_CONTROL_BAR_ICON_SCALE_PERCENT = 100;
    static final int DEFAULT_IME_CONTROL_BAR_ICON_ALPHA_PERCENT = 100;
    static final int DEFAULT_IME_CONTROL_BAR_Y_OFFSET_DP = 0;
    static final boolean DEFAULT_TELEPHONY_DEBUG_ENABLED = false;
    static final boolean DEFAULT_WIFI_PERF_LOGGING_ENABLED = false;
    static final boolean DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED = false;
    static final boolean DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED = false;
    static final boolean DEFAULT_ONEMIND_PERF_DISABLE_ENABLED = false;
    static final boolean DEFAULT_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED = false;
    static final boolean DEFAULT_F2FS_GC_ENABLED = false;
    static final boolean DEFAULT_ONEMIND_LOGCAT_ENABLED = false;
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
            KEY_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT,
            KEY_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT,
            KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP,
            KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP,
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
            KEY_WINDOWMODE_SIDE_GESTURE_ACTION,
            KEY_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS,
            KEY_WINDOWMODE_TWO_RING_OUTER_APP_COUNT,
            KEY_WINDOWMODE_TWO_RING_INNER_APP_COUNT,
            KEY_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT,
            KEY_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT,
            KEY_WINDOWMODE_RECENT_INNER_RING_APP_COUNT,
            KEY_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT,
            KEY_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT,
            KEY_NOTIFICATION_APP_ICON_SIZE_DP,
            KEY_NOTIFICATION_APP_ICON_PADDING_DP,
            KEY_NOTIFICATION_CARD_CORNER_RADIUS_DP,
            KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP,
            KEY_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE,
            KEY_MBACK_INSET_SIZE,
            KEY_MBACK_NAV_BAR_HEIGHT,
            KEY_MBACK_PILL_LENGTH,
            KEY_MBACK_PILL_THICKNESS,
            KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT,
            KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT,
            KEY_LAUNCHER_STACK_MAX_LAYERS,
            KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT,
            KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT,
            KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT,
            KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT,
            KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX,
            KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS,
            KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS,
            KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS,
            KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS,
            KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS,
            KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT,
            KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT,
            KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT,
            KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT,
            KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP,
            KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS,
            KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT,
            KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT,
            KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY,
            KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP,
            KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP,
            KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT,
            KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT,
            KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE,
            KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS,
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
            KEY_CAMERA_CIRCLE_BATTERY_ENABLED,
            KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED,
            KEY_SIGNAL_CODE_DRAW_ENABLED,
            KEY_SIGNAL_MOBILE_TYPE_BADGE_ENABLED,
            KEY_WIFI_CODE_DRAW_ENABLED,
            KEY_SIGNAL_WIFI_SWAP_ENABLED,
            KEY_BATTERY_LEVEL_TEXT_ENABLED,
            KEY_BATTERY_HOLLOW_ENABLED,
            KEY_BATTERY_HOLLOW_FILL_FOLLOWS_LEVEL,
            KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED,
            KEY_CLOCK_BOLD_ENABLED,
            KEY_LOCKSCREEN_CANVAS_CLOCK_ENABLED,
            KEY_CLOCK_DETAIL_POPUP_ENABLED,
            KEY_CLOCK_DETAIL_LUNAR_DATE_ENABLED,
            KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED,
            KEY_MBACK_LONG_TOUCH_URL_ENABLED,
            KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
            KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED,
            KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED,
            KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED,
            KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED,
            KEY_MBACK_NAV_BAR_TRANSPARENT,
            KEY_NOTIFICATION_APP_ICON_ENABLED,
            KEY_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED,
            KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED,
            KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED,
            KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED,
            KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED,
            KEY_LAUNCHER_STACK_CURRENT_APP_CENTERED,
            KEY_LAUNCHER_AICY_ENTRY_ENABLED,
            KEY_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED,
            KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED,
            KEY_MBACK_HIDE_PILL,
            KEY_MBACK_PILL_INTERACTION_SYNC_ENABLED,
            KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR,
            KEY_TELEPHONY_DEBUG_ENABLED,
            KEY_WIFI_PERF_LOGGING_ENABLED,
            KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED,
            KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED,
            KEY_ONEMIND_PERF_DISABLE_ENABLED,
            KEY_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED,
            KEY_F2FS_GC_ENABLED,
            KEY_ONEMIND_LOGCAT_ENABLED
    };

    static final String[] STRING_KEYS = {
            KEY_CLOCK_CUSTOM_FORMAT,
            KEY_CLOCK_EXPRESSION_TOKEN_ORDER,
            KEY_CLOCK_DETAIL_ACTION_GRID_ITEMS_JSON,
            KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON,
            KEY_MBACK_LONG_TOUCH_INTENT_URI,
            KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI,
            KEY_LAUNCHER_AICY_ENTRY_TEXT,
            KEY_LAUNCHER_AICY_ENTRY_TARGET,
            KEY_LAUNCHER_FOLDER_BG_COLOR,
            KEY_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR,
            KEY_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR,
            KEY_IME_CONTROL_BAR_BUTTON_SLOTS
    };

    static final String[] LAUNCHER_STACK_PARAMETER_KEYS = {
            KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT,
            KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT,
            KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT,
            KEY_LAUNCHER_STACK_MAX_LAYERS,
            KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT,
            KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT,
            KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT,
            KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT,
            KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX,
            KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS,
            KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS,
            KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS,
            KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS,
            KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS,
            KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT,
            KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT,
            KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT,
            KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT,
            KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS,
            KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT,
            KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY,
            KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP,
            KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP,
            KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT,
            KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT,
            KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT,
            KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE,
            KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS
    };

    static boolean isLauncherStackParameterKey(String key) {
        if (key == null) {
            return false;
        }
        for (String candidate : LAUNCHER_STACK_PARAMETER_KEYS) {
            if (key.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    static final String[] POSITION_OFFSET_KEYS = {
            KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP,
            KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP,
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
                return POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP;
            case KEY_BATTERY_ICON_STYLE:
                return DEFAULT_BATTERY_ICON_STYLE;
            case KEY_BATTERY_TEXT_FONT:
                return DEFAULT_BATTERY_TEXT_FONT;
            case KEY_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_RADIUS_PERCENT;
            case KEY_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_STROKE_PERCENT;
            case KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP;
            case KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP;
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
            case KEY_WINDOWMODE_SIDE_GESTURE_ACTION:
                return DEFAULT_WINDOWMODE_SIDE_GESTURE_ACTION;
            case KEY_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS:
                return DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_TIMEOUT_MS;
            case KEY_WINDOWMODE_TWO_RING_OUTER_APP_COUNT:
                return DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT;
            case KEY_WINDOWMODE_TWO_RING_INNER_APP_COUNT:
                return DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT;
            case KEY_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT:
                return DEFAULT_WINDOWMODE_TWO_RING_INNER_ICON_SCALE_PERCENT;
            case KEY_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT:
                return DEFAULT_WINDOWMODE_TWO_RING_INNER_RADIUS_PERCENT;
            case KEY_WINDOWMODE_RECENT_INNER_RING_APP_COUNT:
                return DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT;
            case KEY_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT:
                return DEFAULT_WINDOWMODE_RECENT_INNER_RING_ICON_SCALE_PERCENT;
            case KEY_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT:
                return DEFAULT_WINDOWMODE_RECENT_INNER_RING_RADIUS_PERCENT;
            case KEY_NOTIFICATION_APP_ICON_SIZE_DP:
                return DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP;
            case KEY_NOTIFICATION_APP_ICON_PADDING_DP:
                return DEFAULT_NOTIFICATION_APP_ICON_PADDING_DP;
            case KEY_NOTIFICATION_CARD_CORNER_RADIUS_DP:
                return DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_DP;
            case KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP:
                return DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_DP;
            case KEY_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE:
                return DEFAULT_NOTIFICATION_SYSTEM_BLUR_CARRIER_COLOR_MODE;
            case KEY_MBACK_INSET_SIZE:
                return DEFAULT_MBACK_INSET_SIZE;
            case KEY_MBACK_NAV_BAR_HEIGHT:
                return DEFAULT_MBACK_NAV_BAR_HEIGHT;
            case KEY_MBACK_PILL_LENGTH:
                return DEFAULT_MBACK_PILL_LENGTH;
            case KEY_MBACK_PILL_THICKNESS:
                return DEFAULT_MBACK_PILL_THICKNESS;
            case KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT;
            case KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_LEFT_MOVE_PERCENT;
            case KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT:
                return DEFAULT_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT;
            case KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_MIN_SCALE_PERCENT;
            case KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT:
                return DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT;
            case KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT:
                return DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT;
            case KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT:
                return DEFAULT_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT;
            case KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT:
                return DEFAULT_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT;
            case KEY_LAUNCHER_STACK_MAX_LAYERS:
                return DEFAULT_LAUNCHER_STACK_MAX_LAYERS;
            case KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT:
                return DEFAULT_LAUNCHER_STACK_ENTRY_LIFT_PERCENT;
            case KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT:
                return DEFAULT_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT;
            case KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT:
                return DEFAULT_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT;
            case KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT:
                return DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT;
            case KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX:
                return DEFAULT_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX;
            case KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS:
                return DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS;
            case KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS:
                return DEFAULT_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS;
            case KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS:
                return DEFAULT_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS;
            case KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS:
                return DEFAULT_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS;
            case KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS:
                return DEFAULT_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS;
            case KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT:
                return DEFAULT_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT;
            case KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT:
                return DEFAULT_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT;
            case KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT;
            case KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT:
                return DEFAULT_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT;
            case KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP:
                return DEFAULT_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP;
            case KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS:
                return DEFAULT_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS;
            case KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT:
                return DEFAULT_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT;
            case KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT:
                return DEFAULT_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT;
            case KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT:
                return DEFAULT_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT;
            case KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS:
                return DEFAULT_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS;
            case KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS:
                return DEFAULT_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS;
            case KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS:
                return DEFAULT_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS;
            case KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT:
                return DEFAULT_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT;
            case KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT;
            case KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY:
                return DEFAULT_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY;
            case KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP:
                return DEFAULT_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP;
            case KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP:
                return DEFAULT_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP;
            case KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT:
                return DEFAULT_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT;
            case KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT:
                return DEFAULT_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT;
            case KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT:
                return DEFAULT_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT;
            case KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT:
                return DEFAULT_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT;
            case KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE:
                return DEFAULT_LAUNCHER_STACK_SCROLL_FRAME_RATE;
            case KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS:
                return DEFAULT_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS;
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
            case KEY_CAMERA_CIRCLE_BATTERY_ENABLED:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_ENABLED;
            case KEY_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED:
                return DEFAULT_CAMERA_CIRCLE_BATTERY_HIDE_ICON_ENABLED;
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
            case KEY_LOCKSCREEN_CANVAS_CLOCK_ENABLED:
                return DEFAULT_LOCKSCREEN_CANVAS_CLOCK_ENABLED;
            case KEY_CLOCK_DETAIL_POPUP_ENABLED:
                return DEFAULT_CLOCK_DETAIL_POPUP_ENABLED;
            case KEY_CLOCK_DETAIL_LUNAR_DATE_ENABLED:
                return DEFAULT_CLOCK_DETAIL_LUNAR_DATE_ENABLED;
            case KEY_CLOCK_DETAIL_ACTION_GRID_ENABLED:
                return DEFAULT_CLOCK_DETAIL_ACTION_GRID_ENABLED;
            case KEY_MBACK_LONG_TOUCH_URL_ENABLED:
                return DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
            case KEY_WINDOWMODE_SIDE_GESTURE_ENABLED:
                return DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED;
            case KEY_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED:
                return DEFAULT_WINDOWMODE_SIDE_GESTURE_PREWARM_ENABLED;
            case KEY_WINDOWMODE_HOVER_FULLSCREEN_ENABLED:
                return DEFAULT_WINDOWMODE_HOVER_FULLSCREEN_ENABLED;
            case KEY_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED:
                return DEFAULT_WINDOWMODE_TWO_RING_LAUNCHER_ENABLED;
            case KEY_WINDOWMODE_RECENT_INNER_RING_ENABLED:
                return DEFAULT_WINDOWMODE_RECENT_INNER_RING_ENABLED;
            case KEY_MBACK_NAV_BAR_TRANSPARENT:
                return DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
            case KEY_NOTIFICATION_APP_ICON_ENABLED:
                return DEFAULT_NOTIFICATION_APP_ICON_ENABLED;
            case KEY_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED:
                return DEFAULT_NOTIFICATION_CARD_CORNER_RADIUS_ENABLED;
            case KEY_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED:
                return DEFAULT_LAUNCHER_RECENTS_CARD_CORNER_RADIUS_ENABLED;
            case KEY_LAUNCHER_IOS_STACK_RECENTS_ENABLED:
                return DEFAULT_LAUNCHER_IOS_STACK_RECENTS_ENABLED;
            case KEY_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED:
                return DEFAULT_LAUNCHER_IOS_STACK_RECENTS_BLUR_ENABLED;
            case KEY_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED:
                return DEFAULT_LAUNCHER_IOS_STACK_RECENTS_CLEAR_ALL_BUTTON_ENABLED;
            case KEY_LAUNCHER_STACK_CURRENT_APP_CENTERED:
                return DEFAULT_LAUNCHER_STACK_CURRENT_APP_CENTERED;
            case KEY_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED:
                return DEFAULT_NOTIFICATION_SYSTEM_BLUR_ONLY_ENABLED;
            case KEY_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED:
                return DEFAULT_NOTIFICATION_TEXT_FOLLOW_STATUS_BAR_ENABLED;
            case KEY_MBACK_HIDE_PILL:
                return DEFAULT_MBACK_HIDE_PILL;
            case KEY_MBACK_PILL_INTERACTION_SYNC_ENABLED:
                return DEFAULT_MBACK_PILL_INTERACTION_SYNC_ENABLED;
            case KEY_IME_REPLACE_ORIGINAL_CONTROL_BAR:
                return DEFAULT_IME_REPLACE_ORIGINAL_CONTROL_BAR;
            case KEY_TELEPHONY_DEBUG_ENABLED:
                return DEFAULT_TELEPHONY_DEBUG_ENABLED;
            case KEY_WIFI_PERF_LOGGING_ENABLED:
                return DEFAULT_WIFI_PERF_LOGGING_ENABLED;
            case KEY_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED:
                return DEFAULT_LAUNCHER_RECENTS_PERF_LOGGING_ENABLED;
            case KEY_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED:
                return DEFAULT_LAUNCHER_RECENTS_FLOW_LOGGING_ENABLED;
            case KEY_ONEMIND_PERF_DISABLE_ENABLED:
                return DEFAULT_ONEMIND_PERF_DISABLE_ENABLED;
            case KEY_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED:
                return DEFAULT_MZ_SAFE_BACKGROUND_OPTIMIZATION_ENABLED;
            case KEY_F2FS_GC_ENABLED:
                return DEFAULT_F2FS_GC_ENABLED;
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
        if (KEY_WINDOWMODE_SIDE_GESTURE_INTENT_URI.equals(key)) {
            return DEFAULT_WINDOWMODE_SIDE_GESTURE_INTENT_URI;
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
        if (KEY_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR.equals(key)) {
            return DEFAULT_NOTIFICATION_SYSTEM_BLUR_LIGHT_COLOR;
        }
        if (KEY_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR.equals(key)) {
            return DEFAULT_NOTIFICATION_SYSTEM_BLUR_DARK_COLOR;
        }
        if (KEY_IME_CONTROL_BAR_BUTTON_SLOTS.equals(key)) {
            return DEFAULT_IME_CONTROL_BAR_BUTTON_SLOTS;
        }
        return "";
    }

    static int normalizeBatteryStyle(int value) {
        switch (value) {
            case BATTERY_STYLE_ONEUI:
            case BATTERY_STYLE_FLYME_CAPSULE:
                return value;
            default:
                return BATTERY_STYLE_IOS;
        }
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

    static int normalizeWindowModeSideGestureAction(int value) {
        return value == MBACK_LONG_TOUCH_ACTION_INTENT_URI
                ? value
                : MBACK_LONG_TOUCH_ACTION_INTENT_URI;
    }

    static int normalizeWindowModeHoverFullscreenTimeoutMs(int value) {
        return Math.max(300, Math.min(2000, value));
    }

    static int normalizeWindowModeTwoRingOuterAppCount(int value) {
        return Math.max(0, Math.min(DEFAULT_WINDOWMODE_TWO_RING_OUTER_APP_COUNT, value));
    }

    static int normalizeWindowModeTwoRingInnerAppCount(int value) {
        return Math.max(0, Math.min(DEFAULT_WINDOWMODE_TWO_RING_INNER_APP_COUNT, value));
    }

    static int normalizeWindowModeTwoRingInnerIconScalePercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

    static int normalizeWindowModeTwoRingInnerRadiusPercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

    static int normalizeWindowModeRecentInnerRingIconScalePercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

    static int normalizeWindowModeRecentInnerRingAppCount(int value) {
        return Math.max(0, Math.min(DEFAULT_WINDOWMODE_RECENT_INNER_RING_APP_COUNT, value));
    }

    static int normalizeWindowModeRecentInnerRingRadiusPercent(int value) {
        return Math.max(10, Math.min(100, value));
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
        return value / 10f;
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

    static boolean isCameraCircleBatteryOffsetKey(String key) {
        return KEY_CAMERA_CIRCLE_BATTERY_X_OFFSET_DP.equals(key)
                || KEY_CAMERA_CIRCLE_BATTERY_Y_OFFSET_DP.equals(key);
    }

    static int normalizePositionOffsetTenthDp(String key, int value) {
        if (isCameraCircleBatteryOffsetKey(key)) {
            return Math.max(CAMERA_CIRCLE_BATTERY_OFFSET_MIN_TENTH_DP,
                    Math.min(CAMERA_CIRCLE_BATTERY_OFFSET_MAX_TENTH_DP, value));
        }
        return normalizeIconYOffsetTenthDp(value);
    }

    static int readPositionOffsetStorageVersion(SharedPreferences prefs) {
        return readInt(prefs, KEY_POSITION_OFFSET_STORAGE_VERSION, POSITION_OFFSET_STORAGE_VERSION_LEGACY_DP);
    }

    static int readPositionOffsetTenthDp(SharedPreferences prefs, String key, int defaultValue) {
        int normalizedDefault = normalizePositionOffsetTenthDp(key, defaultValue);
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
        int storageVersion = readPositionOffsetStorageVersion(prefs);
        if (isCameraCircleBatteryOffsetKey(key)) {
            if (storageVersion >= POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP) {
                return normalizePositionOffsetTenthDp(key, rawValue);
            }
            if (storageVersion >= POSITION_OFFSET_STORAGE_VERSION_CAMERA_TENTH_DP) {
                return normalizePositionOffsetTenthDp(key, rawValue * 10);
            }
        } else if (storageVersion >= POSITION_OFFSET_STORAGE_VERSION_TENTH_DP) {
            return normalizePositionOffsetTenthDp(key, rawValue);
        }
        return normalizePositionOffsetTenthDp(key, rawValue * 10);
    }

    static void markPositionOffsetStorageVersion(SharedPreferences.Editor editor) {
        if (editor == null) {
            return;
        }
        editor.putInt(KEY_POSITION_OFFSET_STORAGE_VERSION,
                POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP);
    }

    static void migratePositionOffsetStorageIfNeeded(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs == null) {
            return;
        }
        int storageVersion = readPositionOffsetStorageVersion(prefs);
        if (storageVersion >= POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        for (String key : POSITION_OFFSET_KEYS) {
            if (all == null || !all.containsKey(key)) {
                continue;
            }
            boolean cameraOffset = isCameraCircleBatteryOffsetKey(key);
            boolean needsMigration = storageVersion < POSITION_OFFSET_STORAGE_VERSION_TENTH_DP
                    || (cameraOffset
                    && storageVersion < POSITION_OFFSET_STORAGE_VERSION_CAMERA_HUNDREDTH_DP);
            if (needsMigration) {
                int legacyValueDp = readInt(prefs, key, defaultInt(key));
                int multiplier = cameraOffset
                        ? (storageVersion >= POSITION_OFFSET_STORAGE_VERSION_CAMERA_TENTH_DP ? 10 : 100)
                        : 10;
                editor.putInt(key, normalizePositionOffsetTenthDp(key, legacyValueDp * multiplier));
            }
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

    static int normalizeCardCornerRadiusDp(int value) {
        return Math.max(0, Math.min(40, value));
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

    static int normalizeLauncherStackParameter(String key, int value) {
        if (KEY_LAUNCHER_STACK_RIGHT_VISIBLE_PERCENT.equals(key)) {
            return Math.max(20, Math.min(120, value));
        }
        if (KEY_LAUNCHER_STACK_LEFT_MOVE_PERCENT.equals(key)) {
            return Math.max(0, Math.min(200, value));
        }
        if (KEY_LAUNCHER_STACK_LEFT_REST_INSET_PERCENT.equals(key)) {
            return Math.max(-100, Math.min(60, value));
        }
        if (KEY_LAUNCHER_STACK_MIN_SCALE_PERCENT.equals(key)) {
            return Math.max(60, Math.min(110, value));
        }
        if (KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT.equals(key)
                || KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT.equals(key)
                || KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT.equals(key)
                || KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT.equals(key)) {
            return Math.max(0, Math.min(100, value));
        }
        if (KEY_LAUNCHER_STACK_MAX_LAYERS.equals(key)) {
            return Math.max(1, Math.min(10, value));
        }
        if (KEY_LAUNCHER_STACK_ENTRY_LIFT_PERCENT.equals(key)) {
            return Math.max(0, Math.min(50, value));
        }
        if (KEY_LAUNCHER_STACK_ENTRY_INITIAL_SPREAD_PERCENT.equals(key)) {
            return Math.max(0, Math.min(250, value));
        }
        if (KEY_LAUNCHER_STACK_RELEASE_INITIAL_SPREAD_PERCENT.equals(key)) {
            return Math.max(0, Math.min(200, value));
        }
        if (KEY_LAUNCHER_STACK_DESKTOP_ENTRY_VISIBLE_COUNT.equals(key)) {
            return Math.max(1, Math.min(10, value));
        }
        if (KEY_LAUNCHER_STACK_DESKTOP_ENTRY_ANCHOR_INDEX.equals(key)) {
            return Math.max(0, Math.min(9, value));
        }
        if (KEY_LAUNCHER_STACK_GESTURE_RELEASE_DURATION_MS.equals(key)) {
            return Math.max(60, Math.min(1500, value));
        }
        if (KEY_LAUNCHER_STACK_STABLE_VISIBLE_RADIUS.equals(key)
                || KEY_LAUNCHER_STACK_GESTURE_RELEASE_CORE_RADIUS.equals(key)
                || KEY_LAUNCHER_STACK_APP_FLOW_LIGHT_RADIUS.equals(key)) {
            return Math.max(1, Math.min(8, value));
        }
        if (KEY_LAUNCHER_STACK_ENTRY_LIGHT_RADIUS.equals(key)) {
            return Math.max(1, Math.min(6, value));
        }
        if (KEY_LAUNCHER_STACK_RIGHT_BASE_SPEEDUP_PERCENT.equals(key)) {
            return Math.max(0, Math.min(120, value));
        }
        if (KEY_LAUNCHER_STACK_RIGHT_SPEEDUP_PERCENT.equals(key)) {
            return Math.max(0, Math.min(200, value));
        }
        if (KEY_LAUNCHER_STACK_HORIZONTAL_DRAG_RESISTANCE_PERCENT.equals(key)) {
            return Math.max(-100, Math.min(95, value));
        }
        if (KEY_LAUNCHER_STACK_HORIZONTAL_PAGE_THRESHOLD_PERCENT.equals(key)) {
            return Math.max(1, Math.min(80, value));
        }
        if (KEY_LAUNCHER_STACK_HORIZONTAL_FLING_VELOCITY_DP.equals(key)) {
            return Math.max(50, Math.min(3000, value));
        }
        if (KEY_LAUNCHER_STACK_HORIZONTAL_SNAP_DURATION_MS.equals(key)) {
            return Math.max(50, Math.min(2500, value));
        }
        if (KEY_LAUNCHER_STACK_BLANK_EXIT_SCALE_DELTA_PERCENT.equals(key)) {
            return Math.max(0, Math.min(50, value));
        }
        if (KEY_LAUNCHER_STACK_BLANK_EXIT_EXTRA_TRAVEL_PERCENT.equals(key)) {
            return Math.max(0, Math.min(150, value));
        }
        if (KEY_LAUNCHER_STACK_TASK_LAUNCH_EXTRA_WIDTH_PERCENT.equals(key)) {
            return Math.max(0, Math.min(200, value));
        }
        if (KEY_LAUNCHER_STACK_DISMISS_SUCCESS_ANIM_MS.equals(key)) {
            return Math.max(30, Math.min(1000, value));
        }
        if (KEY_LAUNCHER_STACK_DISMISS_CANCEL_ANIM_MS.equals(key)
                || KEY_LAUNCHER_STACK_DISMISS_RELAYOUT_ANIM_MS.equals(key)) {
            return Math.max(50, Math.min(1500, value));
        }
        if (KEY_LAUNCHER_STACK_DISMISS_DRAG_RELAYOUT_MAX_PERCENT.equals(key)) {
            return Math.max(0, Math.min(100, value));
        }
        if (KEY_LAUNCHER_STACK_DISMISS_SECONDARY_DOMINANCE_PERCENT.equals(key)) {
            return Math.max(20, Math.min(400, value));
        }
        if (KEY_LAUNCHER_STACK_DISMISS_MIN_FLING_VELOCITY.equals(key)) {
            return Math.max(100, Math.min(6000, value));
        }
        if (KEY_LAUNCHER_STACK_MENU_PULL_THRESHOLD_DP.equals(key)) {
            return Math.max(10, Math.min(400, value));
        }
        if (KEY_LAUNCHER_STACK_CONTENT_MAX_BLUR_DP.equals(key)) {
            return Math.max(0, Math.min(80, value));
        }
        if (KEY_LAUNCHER_STACK_CONTENT_MEDIUM_BLUR_PERCENT.equals(key)) {
            return Math.max(0, Math.min(100, value));
        }
        if (KEY_LAUNCHER_STACK_CONTENT_BLUR_START_ALPHA_PERCENT.equals(key)) {
            return Math.max(0, Math.min(100, value));
        }
        if (KEY_LAUNCHER_STACK_LEFT_FADE_DISTANCE_PERCENT.equals(key)) {
            return Math.max(1, Math.min(150, value));
        }
        if (KEY_LAUNCHER_STACK_LEFT_RELEASE_ALPHA_THRESHOLD_PERCENT.equals(key)) {
            return Math.max(0, Math.min(100, value));
        }
        if (KEY_LAUNCHER_STACK_SCROLL_FRAME_RATE.equals(key)) {
            return Math.max(30, Math.min(240, value));
        }
        if (KEY_LAUNCHER_STACK_FRAME_RATE_RELEASE_DELAY_MS.equals(key)) {
            return Math.max(0, Math.min(30000, value));
        }
        return value;
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
