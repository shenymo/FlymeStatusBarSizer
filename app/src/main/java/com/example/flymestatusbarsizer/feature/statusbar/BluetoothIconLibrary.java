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
