package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BluetoothIconLibrary {
    private static final LruCache<String, BluetoothIconLibrary> CACHE = new LruCache<>(4);
    private static final String DEFAULT_PREFIX = "default:bluetooth:";
    // Gemini-generated standalone vectors, in BluetoothIconState order.
    private static final String[] DEFAULT_XMLS = {
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M19.5 12.0a1.5 1.5 0 1 0 3.0 0a1.5 1.5 0 1 0 -3.0 0\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M1.5 12.0a1.5 1.5 0 1 0 3.0 0a1.5 1.5 0 1 0 -3.0 0\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 17.6667h4v1.33333h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 16.3333h4v2.66667h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 15h4v4h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 13.6667h4v5.33333h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 12.3333h4v6.66667h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 11h4v8h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 9.66667h4v9.33333h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 8.33333h4v10.6667h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M17.0 6.0H21.0Q22.0 6.0 22.0 7.0V19.0Q22.0 20.0 21.0 20.0H17.0Q16.0 20.0 16.0 19.0V7.0Q16.0 6.0 17.0 6.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M18.0 3.0H20.0Q20.0 3.0 20.0 3.0V6.0Q20.0 6.0 20.0 6.0H18.0Q18.0 6.0 18.0 6.0V3.0Q18.0 3.0 18.0 3.0Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17 7h4v12h-4z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M19 14l-2 2-2-2M17 10v6\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><group android:translateX=\"-4\"><path android:pathData=\"M7 7l10 10-5 5V2l5 5-10 10\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></group><path android:pathData=\"M15 12l2-2 2 2M17 16v-6\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1.6\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>"
    };

    public record Entry(String id, String name, String xml, RingerVectorXml vector, String error) {
        public boolean builtIn() { return id.startsWith(DEFAULT_PREFIX); }
    }

    private static final BluetoothIconLibrary DEFAULTS = createDefaults();
    private final List<Entry> entries;
    private final EnumMap<BluetoothIconState, String> selected;

    public BluetoothIconLibrary(List<Entry> entries, Map<BluetoothIconState, String> selected) {
        this.entries = List.copyOf(entries);
        this.selected = new EnumMap<>(BluetoothIconState.class);
        this.selected.putAll(selected);
    }

    public static BluetoothIconLibrary defaults() {
        return DEFAULTS;
    }

    private static BluetoothIconLibrary createDefaults() {
        ArrayList<Entry> entries = new ArrayList<>();
        EnumMap<BluetoothIconState, String> selected = new EnumMap<>(BluetoothIconState.class);
        for (BluetoothIconState state : BluetoothIconState.values()) {
            String id = DEFAULT_PREFIX + state.id;
            String xml = DEFAULT_XMLS[state.ordinal()];
            entries.add(new Entry(id, state.title + "（默认）", xml,
                    RingerVectorXml.parse(xml), null));
            selected.put(state, id);
        }
        return new BluetoothIconLibrary(entries, selected);
    }

    public static BluetoothIconLibrary load(SharedPreferences prefs, Context context) {
        String raw = SettingsStore.readString(prefs,
                SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_LIBRARY, "");
        if (raw.isEmpty()) return DEFAULTS;
        String cacheKey = (context == null ? "" : context.getPackageName()) + "\n" + raw;
        BluetoothIconLibrary cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        ArrayList<Entry> entries = new ArrayList<>(DEFAULTS.entries);
        EnumMap<BluetoothIconState, String> selected = new EnumMap<>(DEFAULTS.selected);
        try {
            JSONObject object = new JSONObject(raw);
            JSONArray items = object.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String id = item.getString("id"), name = item.getString("name"), xml = item.getString("xml");
                    if (id.isBlank() || id.startsWith("builtin:") || id.startsWith(DEFAULT_PREFIX)
                            || name.isBlank() || xml.isBlank()
                            || entries.stream().anyMatch(entry -> entry.id().equals(id))) continue;
                    RingerVectorXml vector = null;
                    String error = null;
                    try {
                        vector = RingerVectorXml.parse(xml, context);
                    } catch (IllegalArgumentException invalid) {
                        error = invalid.getMessage();
                    }
                    entries.add(new Entry(id, name, xml, vector, error));
                }
            }
            JSONObject assignments = object.optJSONObject("selected");
            if (assignments != null) {
                for (BluetoothIconState state : BluetoothIconState.values()) {
                    String id = assignments.optString(state.id, "");
                    if (entries.stream().anyMatch(entry -> entry.id().equals(id) && entry.vector() != null)) {
                        selected.put(state, id);
                    }
                }
            }
        } catch (JSONException invalid) {
            return DEFAULTS;
        }
        BluetoothIconLibrary library = new BluetoothIconLibrary(entries, selected);
        CACHE.put(cacheKey, library);
        return library;
    }

    public List<Entry> entries() { return entries; }
    public String selectedId(BluetoothIconState state) { return selected.get(state); }

    public Entry selectedEntry(BluetoothIconState state) {
        String id = selected.get(state);
        for (Entry entry : entries) {
            if (entry.id().equals(id)) return entry;
        }
        return DEFAULTS.entries.get(state.ordinal());
    }

    public Drawable newDrawable(Context context, BluetoothIconState state) {
        Entry entry = selectedEntry(state);
        Drawable icon;
        try {
            icon = entry.vector().newDrawable(context);
        } catch (IllegalArgumentException invalid) {
            entry = DEFAULTS.selectedEntry(state);
            icon = entry.vector().newDrawable(context);
        }
        if (state.isTransfer() && !entry.xml().contains("<animated-vector")) {
            AnimationDrawable animation = new AnimationDrawable();
            animation.setOneShot(false);
            animation.addFrame(icon, 500);
            animation.addFrame(DEFAULTS.selectedEntry(BluetoothIconState.CONNECTED)
                    .vector().newDrawable(context), 500);
            return animation;
        }
        return icon;
    }

    public BluetoothIconLibrary withSelection(BluetoothIconState state, String id) {
        EnumMap<BluetoothIconState, String> updated = new EnumMap<>(selected);
        updated.put(state, id);
        return new BluetoothIconLibrary(entries, updated);
    }

    public BluetoothIconLibrary withEntries(List<Entry> updatedEntries) {
        EnumMap<BluetoothIconState, String> updated = new EnumMap<>(selected);
        for (BluetoothIconState state : BluetoothIconState.values()) {
            if (updatedEntries.stream().noneMatch(entry -> entry.id().equals(updated.get(state)))) {
                updated.put(state, DEFAULT_PREFIX + state.id);
            }
        }
        return new BluetoothIconLibrary(updatedEntries, updated);
    }

    public String encode() {
        try {
            JSONArray items = new JSONArray();
            for (Entry entry : entries) {
                if (entry.builtIn()) continue;
                items.put(new JSONObject().put("id", entry.id()).put("name", entry.name()).put("xml", entry.xml()));
            }
            JSONObject assignments = new JSONObject();
            for (BluetoothIconState state : BluetoothIconState.values()) {
                assignments.put(state.id, selected.get(state));
            }
            return new JSONObject().put("items", items).put("selected", assignments).toString();
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }
}
