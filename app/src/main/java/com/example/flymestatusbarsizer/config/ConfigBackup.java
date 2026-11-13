package com.example.flymestatusbarsizer.config;

import android.content.SharedPreferences;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconLibrary;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigBackup {
    public static final String SCHEMA = "flyme_status_bar_sizer_config";
    public static final int VERSION = 1;

    public static final String[] GROUP_IDS = {
            "icons_battery", "time_network", "system_appearance",
            "system_interaction", "recent_tasks", "tools_debug"
    };
    public static final String[] GROUP_LABELS = {
            "状态栏图标与电池", "时间与网速", "系统外观",
            "手势与系统交互", "最近任务布局参数", "工具与调试"
    };

    private ConfigBackup() { }

    public static JSONObject exportSettings(SharedPreferences prefs, Set<String> selectedGroups)
            throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject groups = new JSONObject();
        root.put("schema", SCHEMA);
        root.put("version", VERSION);
        for (int i = 0; i < GROUP_IDS.length; i++) {
            if (!selectedGroups.contains(GROUP_IDS[i])) continue;
            JSONObject values = new JSONObject();
            addValues(values, prefs, SettingsStore.BOOLEAN_KEYS, GROUP_IDS[i], 0);
            addValues(values, prefs, SettingsStore.INT_KEYS, GROUP_IDS[i], 1);
            addValues(values, prefs, SettingsStore.STRING_KEYS, GROUP_IDS[i], 2);
            groups.put(GROUP_IDS[i], values);
        }
        root.put("groups", groups);
        return root;
    }

    public static JSONObject readBackup(String raw) throws JSONException {
        JSONObject root = new JSONObject(raw);
        if (!SCHEMA.equals(root.optString("schema")) || root.optInt("version", 0) != VERSION) {
            throw new JSONException("不支持的配置文件格式");
        }
        JSONObject groups = root.optJSONObject("groups");
        if (groups == null) throw new JSONException("配置文件缺少分组数据");
        boolean found = false;
        for (String group : GROUP_IDS) {
            if (groups.has(group)) {
                if (groups.optJSONObject(group) == null) throw new JSONException("配置分组格式错误");
                found = true;
            }
        }
        if (!found) throw new JSONException("配置文件没有可恢复的分组");
        JSONObject icons = groups.optJSONObject(GROUP_IDS[0]);
        if (icons != null && icons.has(SettingsStore.KEY_BATTERY_ICON_LIBRARY)) {
            String rawLibrary = icons.getString(SettingsStore.KEY_BATTERY_ICON_LIBRARY);
            if (!rawLibrary.isEmpty()) {
                try {
                    BatteryIconLibrary.parse(rawLibrary);
                } catch (IllegalArgumentException invalid) {
                    throw new JSONException(invalid.getMessage());
                }
            }
        }
        return root;
    }

    public static String[] includedGroups(JSONObject root) {
        JSONObject groups = root.optJSONObject("groups");
        List<String> included = new ArrayList<>();
        for (String group : GROUP_IDS) {
            if (groups.has(group)) included.add(group);
        }
        return included.toArray(new String[0]);
    }

    public static void importSettings(SharedPreferences prefs, JSONObject root, Set<String> selectedGroups) {
        JSONObject groups = root.optJSONObject("groups");
        SharedPreferences.Editor editor = prefs.edit();
        putValues(editor, groups, selectedGroups, SettingsStore.BOOLEAN_KEYS, 0);
        putValues(editor, groups, selectedGroups, SettingsStore.INT_KEYS, 1);
        putValues(editor, groups, selectedGroups, SettingsStore.STRING_KEYS, 2);
        JSONObject icons = groups.optJSONObject(GROUP_IDS[0]);
        if (selectedGroups.contains(GROUP_IDS[0]) && icons != null
                && !icons.has(SettingsStore.KEY_BATTERY_ICON_LIBRARY)
                && icons.has(SettingsStore.KEY_BATTERY_ICON_STYLE)) {
            BatteryIconLibrary current = BatteryIconLibrary.load(prefs);
            editor.putString(SettingsStore.KEY_BATTERY_ICON_LIBRARY,
                    new BatteryIconLibrary(current.entries, BatteryIconLibrary.legacyId(
                            icons.optInt(SettingsStore.KEY_BATTERY_ICON_STYLE))).encode());
        }
        SettingsStore.markPositionOffsetStorageVersion(editor);
        editor.apply();
    }

    private static void addValues(JSONObject output, SharedPreferences prefs, String[] keys,
            String group, int type) throws JSONException {
        for (String key : keys) {
            if (!group.equals(groupForKey(key))) continue;
            if (type == 0) {
                boolean value = SettingsStore.readBoolean(prefs, key, SettingsStore.defaultBoolean(key));
                output.put(key, value);
            } else if (type == 1) {
                output.put(key, SettingsStore.readInt(prefs, key, SettingsStore.defaultInt(key)));
            } else if (!SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE.equals(key)
                    && !SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON.equals(key)
                    && !SettingsStore.KEY_COLOR_PICKER_PALETTE.equals(key)) {
                output.put(key, SettingsStore.KEY_BATTERY_ICON_LIBRARY.equals(key)
                        ? BatteryIconLibrary.load(prefs).encode()
                        : SettingsStore.readString(prefs, key, SettingsStore.defaultString(key)));
            }
        }
    }

    private static void putValues(SharedPreferences.Editor editor, JSONObject groups,
            Set<String> selectedGroups, String[] keys, int type) {
        for (String group : selectedGroups) {
            JSONObject values = groups.optJSONObject(group);
            if (values == null) continue;
            for (String key : keys) {
                if (!group.equals(groupForKey(key)) || !values.has(key)) continue;
                if (type == 0) editor.putBoolean(key, values.optBoolean(key));
                else if (type == 1) editor.putInt(key, values.optInt(key));
                else if (!SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE.equals(key)
                        && !SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON.equals(key)
                        && !SettingsStore.KEY_COLOR_PICKER_PALETTE.equals(key)) {
                    editor.putString(key, values.optString(key));
                }
            }
        }
    }

    private static String groupForKey(String key) {
        if (key == null || SettingsStore.KEY_TASK_SWITCHER_LOCKED_APPS.equals(key)
                || SettingsStore.KEY_ASSISTANT_BACKGROUND_IMAGE.equals(key)
                || SettingsStore.KEY_CLOCK_DETAIL_ASSISTANT_ACTION_CACHE_JSON.equals(key)
                || SettingsStore.KEY_COLOR_PICKER_PALETTE.equals(key)) return null;
        if (key.equals(SettingsStore.KEY_WIFI_PERF_LOGGING_ENABLED)
                || key.equals(SettingsStore.KEY_WORKSPACE_FLOW_LOGGING_ENABLED)) return GROUP_IDS[5];
        if (key.startsWith("status_bar_") || key.startsWith("battery_")
                || key.startsWith("camera_circle_") || key.startsWith("signal_")
                || key.startsWith("wifi_") || key.startsWith("anip_")
                || key.startsWith("notification_app_icon_")) return GROUP_IDS[0];
        if (key.startsWith("clock_") || key.startsWith("connection_rate_")) return GROUP_IDS[1];
        if (key.equals(SettingsStore.KEY_HIDE_IDLE_CAMERA_CUTOUT)
                || key.startsWith("assistant_background_")
                || key.startsWith("notification_card_")
                || key.startsWith("notification_system_blur_")
                || key.startsWith("notification_text_")
                || key.startsWith("launcher_recents_card_corner_radius_")
                || key.startsWith("launcher_aicy_") || key.startsWith("launcher_folder_")
                || key.startsWith("launcher_page_indicator_")) return GROUP_IDS[2];
        if (key.startsWith("launcher_stack_")) return GROUP_IDS[4];
        if (key.equals(SettingsStore.KEY_ENABLED) || key.startsWith("telephony_debug_")
                || key.contains("perf_logging") || key.startsWith("onemind_")
                || key.startsWith("mz_safe_")) return GROUP_IDS[5];
        if (key.startsWith("assistant_gesture_") || key.startsWith("side_gesture_")
                || key.startsWith("launcher_side_gesture_") || key.startsWith("side_back_")
                || key.startsWith("launcher_ios_stack_recents_")
                || key.startsWith("corner_gesture_") || key.startsWith("one_handed_")
                || key.startsWith("workspace_") || key.startsWith("mback_")
                || key.startsWith("windowmode_") || key.startsWith("ime_")
                || key.startsWith("share_") || key.startsWith("carlink_")) return GROUP_IDS[3];
        return null;
    }
}
