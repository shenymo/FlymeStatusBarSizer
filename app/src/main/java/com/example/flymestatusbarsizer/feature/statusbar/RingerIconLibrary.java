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
    private static final String SILENT_XML = """
            <vector android:height="24dp" android:width="24dp" android:autoMirrored="true"
                android:viewportWidth="24" android:viewportHeight="24"
                xmlns:android="http://schemas.android.com/apk/res/android">
                <path android:fillColor="#FFFFFFFF"
                    android:pathData="M19.8,22.6 L16.775,19.575Q16.15,19.975 15.45,20.263Q14.75,20.55 14,20.725V18.675Q14.35,18.55 14.688,18.425Q15.025,18.3 15.325,18.125L12,14.8V20L7,15H3V9H6.2L1.4,4.2L2.8,2.8L21.2,21.2ZM19.6,16.8 L18.15,15.35Q18.575,14.575 18.788,13.725Q19,12.875 19,11.975Q19,9.625 17.625,7.775Q16.25,5.925 14,5.275V3.225Q17.1,3.925 19.05,6.362Q21,8.8 21,11.975Q21,13.3 20.638,14.525Q20.275,15.75 19.6,16.8ZM16.25,13.45 L14,11.2V7.95Q15.175,8.5 15.838,9.6Q16.5,10.7 16.5,12Q16.5,12.375 16.438,12.738Q16.375,13.1 16.25,13.45ZM12,9.2 L9.4,6.6 12,4ZM10,15.15V12.8L8.2,11H5V13H7.85ZM9.1,11.9Z" />
            </vector>
            """;
    private static final String VIBRATE_XML = """
            <vector android:height="19dp" android:width="19dp" android:viewportWidth="24"
                android:viewportHeight="24" xmlns:android="http://schemas.android.com/apk/res/android">
                <path android:fillColor="#FFFFFFFF"
                    android:pathData="M1,9h2v6H1V9zM4,17h2V7H4V17zM21,9v6h2V9H21zM18,17h2V7h-2V17zM17,5.5v13c0,0.83 -0.67,1.5 -1.5,1.5h-7C7.67,20 7,19.33 7,18.5v-13C7,4.67 7.67,4 8.5,4h7C16.33,4 17,4.67 17,5.5zM15,6H9v12h6V6z" />
            </vector>
            """;
    private static final String ALARM_XML = """
            <vector android:height="17dp" android:width="17dp" android:viewportWidth="24"
                android:viewportHeight="24" xmlns:android="http://schemas.android.com/apk/res/android">
                <path android:fillColor="#FFFFFFFF"
                    android:pathData="M13,8h-2v5.41l3.79,3.8 1.42,-1.42 -3.21,-3.2zM12,4c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9 -4.03,-9 -9,-9zM12,20c-3.86,0 -7,-3.14 -7,-7s3.14,-7 7,-7 7,3.14 7,7 -3.14,7 -7,7zM16.056,3.346l1.282,-1.535 4.607,3.85 -1.28,1.54zM2.056,5.654L6.663,1.81l1.28,1.536L3.338,7.19z" />
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

    public static String builtInXml(Target target) {
        return switch (target) {
            case SILENT -> SILENT_XML;
            case VIBRATE -> VIBRATE_XML;
            case ALARM -> ALARM_XML;
        };
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
