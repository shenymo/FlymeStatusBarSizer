package com.example.flymestatusbarsizer.feature.openwith;

import com.example.flymestatusbarsizer.feature.share.ShareTargetRules;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

public final class OpenWithTargetProfiles {
    private final Map<OpenWithContentType, ShareTargetRules> overrides =
            new EnumMap<>(OpenWithContentType.class);

    public ShareTargetRules rulesFor(OpenWithContentType type, ShareTargetRules defaults) {
        return overrides.getOrDefault(type, defaults);
    }

    public boolean hasOverrides() { return !overrides.isEmpty(); }
    public boolean hasOverride(OpenWithContentType type) { return overrides.containsKey(type); }

    public void set(OpenWithContentType type, ShareTargetRules rules) {
        if (type == OpenWithContentType.DEFAULT) return;
        if (rules == null) overrides.remove(type); else overrides.put(type, rules);
    }

    public static OpenWithTargetProfiles parse(String encoded) {
        OpenWithTargetProfiles result = new OpenWithTargetProfiles();
        if (encoded == null || encoded.isEmpty()) return result;
        try {
            JSONObject root = new JSONObject(encoded);
            for (OpenWithContentType type : OpenWithContentType.values()) {
                JSONObject item = root.optJSONObject(type.name());
                if (type != OpenWithContentType.DEFAULT && item != null
                        && item.opt("order") instanceof String && item.opt("hidden") instanceof String) {
                    result.set(type, ShareTargetRules.parse(item.getString("order"), item.getString("hidden")));
                }
            }
        } catch (JSONException ignored) { }
        return result;
    }

    public String encode() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<OpenWithContentType, ShareTargetRules> entry : overrides.entrySet()) {
                JSONObject item = new JSONObject();
                item.put("order", entry.getValue().encodeOrder());
                item.put("hidden", entry.getValue().encodeHidden());
                root.put(entry.getKey().name(), item);
            }
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        return root.toString();
    }
}
