package com.example.flymestatusbarsizer.feature.clock;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ClockDetailActionCodec {
    public static final String DEFAULT_PRESET_JSON = buildDefaultPresetJson();

    private static final int FORMAT_VERSION = 3;

    private ClockDetailActionCodec() {
    }

    public static ClockDetailActionSpec[] emptyGrid() {
        return ClockDetailAssistantActionCatalog.createAssistantPresetGrid();
    }

    public static ClockDetailActionSpec[] decode(String rawJson) {
        ClockDetailActionSpec[] specs = emptyGrid();
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return specs;
        }
        try {
            JSONObject root = new JSONObject(rawJson);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return specs;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                int slot = item.optInt("slot", -1);
                if (slot < 0 || slot >= ClockDetailActionSpec.SLOT_COUNT) {
                    continue;
                }
                specs[slot] = new ClockDetailActionSpec(
                        slot,
                        item.optString("type", ClockDetailActionSpec.TYPE_EMPTY),
                        item.optString("title", ""),
                        item.optString("assistantAction", ""),
                        item.optString("packageName", ""),
                        item.optString("componentName", ""),
                        item.optString("intentUri", ""));
            }
        } catch (Throwable ignored) {
            return emptyGrid();
        }
        return ClockDetailAssistantActionCatalog.normalizePresetGrid(specs);
    }

    public static String encode(ClockDetailActionSpec[] specs) {
        return encodeInternal(ClockDetailAssistantActionCatalog.normalizePresetGrid(specs));
    }

    private static String encodeInternal(ClockDetailActionSpec[] specs) {
        try {
            JSONObject root = new JSONObject();
            JSONArray items = new JSONArray();
            root.put("version", FORMAT_VERSION);
            for (int slot = 0; slot < ClockDetailActionSpec.SLOT_COUNT; slot++) {
                ClockDetailActionSpec spec = specAt(specs, slot);
                JSONObject item = new JSONObject();
                item.put("slot", slot);
                item.put("type", spec.type);
                if (!spec.title.isEmpty()) {
                    item.put("title", spec.title);
                }
                if (ClockDetailActionSpec.TYPE_ASSISTANT_ACTION.equals(spec.type)
                        && !spec.assistantAction.isEmpty()) {
                    item.put("assistantAction", spec.assistantAction);
                }
                if (ClockDetailActionSpec.TYPE_APP.equals(spec.type) && !spec.packageName.isEmpty()) {
                    item.put("packageName", spec.packageName);
                }
                if (ClockDetailActionSpec.TYPE_INTENT.equals(spec.type) && !spec.intentUri.isEmpty()) {
                    item.put("intentUri", spec.intentUri);
                }
                if (ClockDetailActionSpec.TYPE_ACTIVITY.equals(spec.type)
                        && !spec.componentName.isEmpty()) {
                    item.put("componentName", spec.componentName);
                }
                items.put(item);
            }
            root.put("items", items);
            return root.toString();
        } catch (Throwable ignored) {
            return "{\"version\":3,\"items\":[]}";
        }
    }

    private static String buildDefaultPresetJson() {
        return encodeInternal(ClockDetailAssistantActionCatalog.createAssistantPresetGrid());
    }

    private static ClockDetailActionSpec specAt(ClockDetailActionSpec[] specs, int slot) {
        if (specs == null
                || slot < 0
                || slot >= ClockDetailActionSpec.SLOT_COUNT
                || slot >= specs.length) {
            return ClockDetailActionSpec.empty(slot);
        }
        ClockDetailActionSpec spec = specs[slot];
        return spec != null ? spec : ClockDetailActionSpec.empty(slot);
    }
}
