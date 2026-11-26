package com.example.flymestatusbarsizer.feature.share;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;

/** Missing overrides inherit default ordering while keeping hidden entries type-specific. */
public final class ShareTargetProfiles {
    private final Map<ShareContentType, ShareTargetRules> overrides = new EnumMap<>(ShareContentType.class);

    public ShareTargetRules rulesFor(ShareContentType type, ShareTargetRules defaults) {
        if (type == ShareContentType.DEFAULT) return defaults;
        ShareTargetRules override = overrides.get(type);
        return override != null
                ? override
                : new ShareTargetRules(defaults.order(), Collections.emptySet());
    }

    public boolean hasOverrides() { return !overrides.isEmpty(); }

    public boolean hasOverride(ShareContentType type) { return overrides.containsKey(type); }

    public void set(ShareContentType type, ShareTargetRules rules) {
        if (type == ShareContentType.DEFAULT) return;
        if (rules == null) overrides.remove(type); else overrides.put(type, rules);
    }

    public static ShareTargetProfiles parse(String encoded) {
        ShareTargetProfiles result = new ShareTargetProfiles();
        if (encoded == null || encoded.isEmpty()) return result;
        try {
            JSONObject root = new JSONObject(encoded);
            for (ShareContentType type : ShareContentType.values()) {
                JSONObject item = root.optJSONObject(type.name());
                if (type != ShareContentType.DEFAULT && item != null
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
            for (Map.Entry<ShareContentType, ShareTargetRules> entry : overrides.entrySet()) {
                JSONObject item = new JSONObject();
                item.put("order", entry.getValue().encodeOrder());
                item.put("hidden", entry.getValue().encodeHidden());
                root.put(entry.getKey().name(), item);
            }
        } catch (JSONException error) { throw new IllegalStateException(error); }
        return root.toString();
    }
}
