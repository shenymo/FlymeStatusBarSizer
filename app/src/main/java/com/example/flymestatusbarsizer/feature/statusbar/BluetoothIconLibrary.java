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
import java.util.HashMap;
import java.util.Map;

public final class BluetoothIconLibrary {
    private static final LruCache<String, BluetoothIconLibrary> CACHE = new LruCache<>(4);
    public static final String DEFAULT_PACK = "default:bluetooth";
    private static final String DEFAULT_PREFIX = DEFAULT_PACK + ":";
    // Gemini-generated standalone vectors, in BluetoothIconState order.
    private static final String[] DEFAULT_XMLS = {
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.5 12 V10 A1.5 1.5 0 0 1 20.5 10 V12 M17.5 14 V16 A1.5 1.5 0 0 0 20.5 16 V14 M19 11.5 V14.5\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 16.9 H20.95 A0.3 0.3 0 0 1 21.25 17.2 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V17.2 A0.3 0.3 0 0 1 17.05 16.9 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 16 H20.95 A0.3 0.3 0 0 1 21.25 16.3 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V16.3 A0.3 0.3 0 0 1 17.05 16 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 15.1 H20.95 A0.3 0.3 0 0 1 21.25 15.4 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V15.4 A0.3 0.3 0 0 1 17.05 15.1 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 14.2 H20.95 A0.3 0.3 0 0 1 21.25 14.5 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V14.5 A0.3 0.3 0 0 1 17.05 14.2 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 13.3 H20.95 A0.3 0.3 0 0 1 21.25 13.6 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V13.6 A0.3 0.3 0 0 1 17.05 13.3 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 12.4 H20.95 A0.3 0.3 0 0 1 21.25 12.7 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V12.7 A0.3 0.3 0 0 1 17.05 12.4 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 11.5 H20.95 A0.3 0.3 0 0 1 21.25 11.8 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V11.8 A0.3 0.3 0 0 1 17.05 11.5 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 10.6 H20.95 A0.3 0.3 0 0 1 21.25 10.9 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V10.9 A0.3 0.3 0 0 1 17.05 10.6 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M17.95 5.5 H20.05 A0.6 0.6 0 0 1 20.65 6.1 V6.7 A0.6 0.6 0 0 1 20.05 7.3 H17.95 A0.6 0.6 0 0 1 17.35 6.7 V6.1 A0.6 0.6 0 0 1 17.95 5.5 Z\" android:fillColor=\"#FFFFFFFF\" /><path android:pathData=\"M16.45 7.3 H21.55 A1.2 1.2 0 0 1 22.75 8.5 V18.1 A1.2 1.2 0 0 1 21.55 19.3 H16.45 A1.2 1.2 0 0 1 15.25 18.1 V8.5 A1.2 1.2 0 0 1 16.45 7.3 Z\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" /><path android:pathData=\"M17.05 8.8 H20.95 A0.3 0.3 0 0 1 21.25 9.1 V17.5 A0.3 0.3 0 0 1 20.95 17.8 H17.05 A0.3 0.3 0 0 1 16.75 17.5 V9.1 A0.3 0.3 0 0 1 17.05 8.8 Z\" android:fillColor=\"#FFFFFFFF\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"2.4\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /><path android:pathData=\"M19 9 V17 M16.5 14.5 L19 17 L21.5 14.5\" android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"1\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" /></vector>",
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\"><path android:pathData=\"M3 7 L13 17 L8 22 V2 L13 7 L3 17\" android:strokeWidth=\"2.4\" android:strokeColor=\"#FFFFFFFF\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" android:fillColor=\"#00000000\" /><path android:pathData=\"M19 17 V9 M16.5 11.5 L19 9 L21.5 11.5\" android:strokeWidth=\"1\" android:strokeColor=\"#FFFFFFFF\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" android:fillColor=\"#00000000\" /></vector>"
    };

    public record Icon(String xml, RingerVectorXml vector) {}

    public record Pack(String id, String name, Map<BluetoothIconState, Icon> icons) {
        public Pack { icons = Map.copyOf(icons); }
        public boolean builtIn() { return DEFAULT_PACK.equals(id); }

        public Pack withIcon(BluetoothIconState state, Icon icon) {
            EnumMap<BluetoothIconState, Icon> updated = new EnumMap<>(BluetoothIconState.class);
            updated.putAll(icons);
            updated.put(state, icon);
            return new Pack(id, name, updated);
        }

        public Drawable newDrawable(Context context, BluetoothIconState state) {
            Icon entry = icons.get(state);
            Drawable drawable;
            try {
                drawable = entry.vector().newDrawable(context);
            } catch (IllegalArgumentException invalid) {
                entry = DEFAULTS.selectedPack().icons().get(state);
                drawable = entry.vector().newDrawable(context);
            }
            if (state.isTransfer() && !entry.xml().contains("<animated-vector")) {
                AnimationDrawable animation = new AnimationDrawable();
                animation.setOneShot(false);
                animation.addFrame(drawable, 500);
                animation.addFrame(newDrawable(context, BluetoothIconState.CONNECTED), 500);
                return animation;
            }
            return drawable;
        }
    }

    private static final BluetoothIconLibrary DEFAULTS = createDefaults();
    private final List<Pack> packs;
    private final String selected;

    public BluetoothIconLibrary(List<Pack> packs, String selected) {
        this.packs = List.copyOf(packs);
        this.selected = packs.stream().anyMatch(pack -> pack.id().equals(selected)) ? selected : DEFAULT_PACK;
    }

    public static BluetoothIconLibrary defaults() { return DEFAULTS; }

    private static BluetoothIconLibrary createDefaults() {
        EnumMap<BluetoothIconState, Icon> icons = new EnumMap<>(BluetoothIconState.class);
        for (BluetoothIconState state : BluetoothIconState.values()) {
            String xml = DEFAULT_XMLS[state.ordinal()];
            icons.put(state, new Icon(xml, RingerVectorXml.parse(xml)));
        }
        return new BluetoothIconLibrary(List.of(new Pack(DEFAULT_PACK, "内置图标包", icons)), DEFAULT_PACK);
    }

    public static BluetoothIconLibrary load(SharedPreferences prefs, Context context) {
        String raw = SettingsStore.readString(prefs, SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_PACKS, "");
        boolean legacy = raw.isEmpty();
        if (legacy) raw = SettingsStore.readString(prefs, SettingsStore.KEY_STATUS_BAR_BLUETOOTH_ICON_LIBRARY, "");
        if (raw.isEmpty()) return DEFAULTS;
        String cacheKey = (context == null ? "" : context.getPackageName()) + "\n" + legacy + "\n" + raw;
        BluetoothIconLibrary cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        ArrayList<Pack> packs = new ArrayList<>(DEFAULTS.packs);
        String selected = DEFAULT_PACK;
        try {
            JSONObject object = new JSONObject(raw);
            if (legacy) {
                HashMap<String, String> sources = new HashMap<>();
                JSONArray items = object.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String id = item.getString("id");
                        if (!id.isBlank() && !id.startsWith("builtin:") && !id.startsWith(DEFAULT_PREFIX)) {
                            sources.putIfAbsent(id, item.getString("xml"));
                        }
                    }
                }
                JSONObject assignments = object.optJSONObject("selected");
                JSONObject icons = new JSONObject();
                if (assignments != null) {
                    for (BluetoothIconState state : BluetoothIconState.values()) {
                        String xml = sources.get(assignments.optString(state.id));
                        if (xml != null) icons.put(state.id, xml);
                    }
                }
                Pack migrated = readPack("legacy:bluetooth", "原有配置", icons, context);
                if (!migrated.icons().equals(DEFAULTS.selectedPack().icons())) {
                    packs.add(migrated);
                    selected = migrated.id();
                }
            } else {
                JSONArray items = object.getJSONArray("packs");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String id = item.getString("id"), name = item.getString("name").trim();
                    if (id.isBlank() || name.isEmpty() || packs.stream().anyMatch(pack -> pack.id().equals(id))) continue;
                    packs.add(readPack(id, name, item.getJSONObject("icons"), context));
                }
                selected = object.optString("selected", DEFAULT_PACK);
            }
        } catch (JSONException invalid) {
            return DEFAULTS;
        }
        BluetoothIconLibrary library = new BluetoothIconLibrary(packs, selected);
        CACHE.put(cacheKey, library);
        return library;
    }

    private static Pack readPack(String id, String name, JSONObject sources, Context context) {
        EnumMap<BluetoothIconState, Icon> icons = new EnumMap<>(BluetoothIconState.class);
        icons.putAll(DEFAULTS.selectedPack().icons());
        for (BluetoothIconState state : BluetoothIconState.values()) {
            String xml = sources.optString(state.id, "");
            if (xml.isBlank()) continue;
            try {
                RingerVectorXml vector = RingerVectorXml.parse(xml, context);
                if (context != null) vector.newDrawable(context);
                icons.put(state, new Icon(xml, vector));
            } catch (IllegalArgumentException invalid) {
                // Invalid stored XML uses the corresponding built-in state.
            }
        }
        return new Pack(id, name, icons);
    }

    public List<Pack> packs() { return packs; }
    public String selectedId() { return selected; }
    public Pack selectedPack() { return pack(selected); }
    public Pack pack(String id) {
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst().orElse(DEFAULTS.packs.get(0));
    }

    public Drawable newDrawable(Context context, BluetoothIconState state) {
        return selectedPack().newDrawable(context, state);
    }

    public BluetoothIconLibrary withSelection(String id) {
        return new BluetoothIconLibrary(packs, id);
    }

    public BluetoothIconLibrary withPack(Pack pack) {
        ArrayList<Pack> updated = new ArrayList<>(packs);
        int position = -1;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(pack.id())) { position = i; break; }
        }
        if (position < 0) updated.add(pack); else updated.set(position, pack);
        return new BluetoothIconLibrary(updated, selected);
    }

    public BluetoothIconLibrary withoutPack(String id) {
        ArrayList<Pack> updated = new ArrayList<>(packs);
        updated.removeIf(pack -> pack.id().equals(id));
        return new BluetoothIconLibrary(updated, selected);
    }

    public String encode() {
        try {
            JSONArray items = new JSONArray();
            for (Pack pack : packs) {
                if (pack.builtIn()) continue;
                JSONObject icons = new JSONObject();
                for (BluetoothIconState state : BluetoothIconState.values()) {
                    icons.put(state.id, pack.icons().get(state).xml());
                }
                items.put(new JSONObject().put("id", pack.id()).put("name", pack.name()).put("icons", icons));
            }
            return new JSONObject().put("packs", items).put("selected", selected).toString();
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }
}
