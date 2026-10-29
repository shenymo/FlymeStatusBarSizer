package com.example.flymestatusbarsizer.config;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SideGestureTargetStore {
    public static final int DYNAMIC_ACTION_BASE = 1000;

    public static final class Target {
        public final int id;
        public final int type;
        public final String label;
        public final String value;

        public Target(int id, int type, String label, String value) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.value = value;
        }
    }

    private SideGestureTargetStore() { }

    public static List<Target> parse(String raw) {
        ArrayList<Target> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                int id = item.optInt("id", -1);
                int type = item.optInt("type", -1);
                String label = item.optString("label", "").trim();
                String value = item.optString("value", "").trim();
                if (id < DYNAMIC_ACTION_BASE || !isTargetType(type)
                        || label.isEmpty() || value.isEmpty()) continue;
                result.add(new Target(id, type, label, value));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return result;
    }

    public static String encode(List<Target> targets) {
        JSONArray array = new JSONArray();
        for (Target target : targets) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", target.id);
                item.put("type", target.type);
                item.put("label", target.label);
                item.put("value", target.value);
                array.put(item);
            } catch (Exception ignored) {
            }
        }
        return array.toString();
    }

    public static Target find(String raw, int id) {
        for (Target target : parse(raw)) {
            if (target.id == id) return target;
        }
        return null;
    }

    public static int nextId(List<Target> targets) {
        int next = DYNAMIC_ACTION_BASE;
        for (Target target : targets) next = Math.max(next, target.id + 1);
        return next;
    }

    public static boolean isTargetType(int type) {
        return type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP
                || type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY
                || type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI
                || type == SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL;
    }

    public static boolean isDynamicAction(int action) {
        return action >= DYNAMIC_ACTION_BASE;
    }

    public static String readRaw(SharedPreferences prefs) {
        return SettingsStore.readString(prefs, SettingsStore.KEY_SIDE_GESTURE_TARGETS_JSON, "[]");
    }
}
