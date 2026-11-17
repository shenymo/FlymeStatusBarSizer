package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.SharedPreferences;
import android.content.Context;
import android.util.LruCache;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RingerIconLibrary {
    private static final LruCache<String, RingerIconLibrary> CACHE = new LruCache<>(4);
    private static final String TRIANGLE_ID = "builtin:triangle";
    public enum Target {
        SILENT("静音图标", SettingsStore.KEY_STATUS_BAR_SILENT_ICON_LIBRARY, "ringer_silent"),
        VIBRATE("震动图标", SettingsStore.KEY_STATUS_BAR_VIBRATE_ICON_LIBRARY, "ringer_vibrate"),
        ALARM("闹钟图标", SettingsStore.KEY_STATUS_BAR_ALARM_ICON_LIBRARY, "ringer_alarm");

        public final String title;
        public final String key;
        public final String onlineType;

        Target(String title, String key, String onlineType) {
            this.title = title;
            this.key = key;
            this.onlineType = onlineType;
        }
    }
    public static final String TRIANGLE_XML = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="24dp"
                android:height="24dp"
                android:viewportWidth="24"
                android:viewportHeight="24">
                <path
                    android:fillColor="#FFFFFFFF"
                    android:pathData="M12,2 L22,20 L2,20 Z" />
            </vector>
            """;
    public record Entry(String id, String name, String xml, RingerVectorXml vector, String error) { }
    public final List<Entry> entries;
    public final String selected;

    public RingerIconLibrary(List<Entry> entries, String selected) {
        this.entries = List.copyOf(entries);
        this.selected = selected;
    }

    public static String key(boolean silent) {
        return key(silent ? Target.SILENT : Target.VIBRATE);
    }

    public static String key(Target target) {
        return target.key;
    }

    public static RingerIconLibrary load(SharedPreferences prefs, boolean silent) {
        return load(prefs, silent, null);
    }

    public static RingerIconLibrary load(SharedPreferences prefs, boolean silent, Context context) {
        return load(prefs, silent ? Target.SILENT : Target.VIBRATE, context);
    }

    public static RingerIconLibrary load(SharedPreferences prefs, Target target) {
        return load(prefs, target, null);
    }

    public static RingerIconLibrary load(SharedPreferences prefs, Target target, Context context) {
        String raw = SettingsStore.readString(prefs, key(target), "");
        String cacheKey = (context == null ? "" : context.getPackageName()) + "\n" + raw;
        RingerIconLibrary cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        ArrayList<Entry> entries = new ArrayList<>();
        String selected = "";
        if (!raw.isEmpty()) {
            try {
                JSONObject object = new JSONObject(raw);
                JSONArray items = object.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String id = item.getString("id"), name = item.getString("name"), xml = item.getString("xml");
                    if (id.isBlank() || id.startsWith("builtin:") || name.isBlank()
                            || entries.stream().anyMatch(entry -> entry.id.equals(id))) continue;
                    RingerVectorXml vector = null;
                    String error = null;
                    try {
                        vector = RingerVectorXml.parse(xml, context);
                    } catch (IllegalArgumentException invalid) {
                        error = invalid.getMessage();
                    }
                    entries.add(new Entry(id, name, xml, vector, error));
                }
                selected = object.optString("selected", "");
            } catch (JSONException invalid) {
                entries.clear();
            }
        }
        entries.add(0, new Entry(TRIANGLE_ID, "示例：三角形", TRIANGLE_XML,
                RingerVectorXml.parse(TRIANGLE_XML, context), null));
        String selectedId = selected;
        if (entries.stream().noneMatch(entry -> entry.id.equals(selectedId) && entry.vector != null)) selected = "";
        RingerIconLibrary library = new RingerIconLibrary(entries, selected);
        CACHE.put(cacheKey, library);
        return library;
    }

    public Entry selectedEntry() {
        for (Entry entry : entries) if (entry.id.equals(selected)) return entry;
        return null;
    }

    public String encode() {
        try {
            JSONArray items = new JSONArray();
            for (Entry entry : entries) {
                if (entry.id.startsWith("builtin:")) continue;
                items.put(new JSONObject().put("id", entry.id).put("name", entry.name).put("xml", entry.xml));
            }
            return new JSONObject().put("items", items).put("selected", selected).toString();
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }
}
