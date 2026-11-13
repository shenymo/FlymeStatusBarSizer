package com.example.flymestatusbarsizer.feature.battery;

import android.content.SharedPreferences;
import android.util.LruCache;
import com.example.flymestatusbarsizer.config.SettingsStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class BatteryIconLibrary {
    public static final String IOS = "builtin:ios";
    public static final String ONEUI = "builtin:oneui";
    public static final String IOS_LEGACY = "builtin:ios_legacy";
    private static final LruCache<String, BatteryIconLibrary> CACHE = new LruCache<>(4);
    public record Entry(String id, BatteryIconPack pack) {
        public boolean builtIn() { return id.startsWith("builtin:"); }
    }
    private static final List<Entry> BUILT_INS = createBuiltIns();
    public final List<Entry> entries;
    public final String selected;
    public final String error;

    public BatteryIconLibrary(List<Entry> entries, String selected) {
        this(entries, selected, null);
    }

    private BatteryIconLibrary(List<Entry> entries, String selected, String error) {
        this.entries = List.copyOf(entries);
        this.selected = entries.stream().anyMatch(entry -> entry.id().equals(selected)) ? selected : IOS;
        this.error = error;
    }

    public static String legacyId(int value) {
        return value == 1 ? ONEUI : value == 2 ? IOS_LEGACY : IOS;
    }

    public static BatteryIconPack defaultPack() { return BUILT_INS.get(0).pack(); }

    public static BatteryIconLibrary load(SharedPreferences prefs) {
        String raw = SettingsStore.readString(prefs, SettingsStore.KEY_BATTERY_ICON_LIBRARY, "");
        String cacheKey = raw.isEmpty()
                ? legacyId(SettingsStore.readInt(prefs, SettingsStore.KEY_BATTERY_ICON_STYLE, 0)) : raw;
        BatteryIconLibrary cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        BatteryIconLibrary library;
        try {
            library = raw.isEmpty() ? new BatteryIconLibrary(BUILT_INS, cacheKey) : parse(raw);
        } catch (IllegalArgumentException invalid) {
            library = new BatteryIconLibrary(BUILT_INS, IOS, invalid.getMessage());
        }
        CACHE.put(cacheKey, library);
        return library;
    }

    public static BatteryIconLibrary parse(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            if (root.getInt("version") != 1) throw new IllegalArgumentException("不支持的电池图标库版本");
            ArrayList<Entry> entries = new ArrayList<>(BUILT_INS);
            JSONArray items = root.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String id = item.getString("id");
                if (id.isBlank() || id.startsWith("builtin:")
                        || entries.stream().anyMatch(entry -> entry.id().equals(id))) {
                    throw new IllegalArgumentException("图标包标识重复或无效");
                }
                BatteryIconPack pack = BatteryIconPack.parse(item.getJSONObject("pack").toString());
                if (entries.stream().anyMatch(entry -> entry.pack().name.equals(pack.name))) {
                    throw new IllegalArgumentException("图标包名称重复：" + pack.name);
                }
                entries.add(new Entry(id, pack));
            }
            return new BatteryIconLibrary(entries, root.getString("selected"));
        } catch (JSONException invalid) {
            throw new IllegalArgumentException("电池图标库格式错误：" + invalid.getMessage(), invalid);
        }
    }

    public Entry selectedEntry() {
        for (Entry entry : entries) if (entry.id().equals(selected)) return entry;
        return BUILT_INS.get(0);
    }

    public String encode() {
        try {
            JSONArray items = new JSONArray();
            for (Entry entry : entries) {
                if (!entry.builtIn()) items.put(new JSONObject().put("id", entry.id())
                        .put("pack", new JSONObject(entry.pack().json)));
            }
            return new JSONObject().put("version", 1).put("selected", selected).put("items", items).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String vector(int width, String content) {
        return "<vector xmlns:android='http://schemas.android.com/apk/res/android' android:width='" + width
                + "dp' android:height='50dp' android:viewportWidth='" + width + "' android:viewportHeight='50'>"
                + content + "</vector>";
    }

    private static List<Entry> createBuiltIns() {
        String normalBolt = "M520,840L560,560L319,560L640,120L680,120L640,400L881,400L560,840L520,840Z";
        String quickBolt = normalBolt + "M120,720L120,640L468,640L456,720L120,720Z"
                + "M80,520L80,440L308,440L250,520L80,520Z"
                + "M160,320L160,240L454,240L396,320L160,320Z";
        String iosPath = "M14,0H65.5Q79.5,0 79.5,14V36Q79.5,50 65.5,50H14Q0,50 0,36V14Q0,0 14,0Z"
                + "M85.7,14H87.1Q90,14 90,16.9V33.1Q90,36 87.1,36H85.7Q82.8,36 82.8,33.1V16.9Q82.8,14 85.7,14Z";
        String oneUiPath = "M25,0H61A25,25 0,0 1,61,50H25A25,25 0,0 1,25,0Z";
        String legacyShell = "<path android:fillColor='#00000000' android:strokeColor='#73FFFFFF' android:strokeWidth='6.5' "
                + "android:pathData='M19,3.25H72.7Q88.45,3.25 88.45,19V31Q88.45,46.75 72.7,46.75H19Q3.25,46.75 3.25,31V19Q3.25,3.25 19,3.25Z'/>"
                + "<path android:fillColor='#73FFFFFF' android:pathData='M97.5,17.5H99Q105,17.5 105,25Q105,32.5 99,32.5H97.5Z'/>";
        String legacyFill = "M19.6,8.125H72.1Q83.575,8.125 83.575,19.6V30.4Q83.575,41.875 72.1,41.875H19.6Q8.125,41.875 8.125,30.4V19.6Q8.125,8.125 19.6,8.125Z";
        String emptyShell = "<path android:fillColor='#00FFFFFF' android:pathData='M0,0L1,0L1,1Z'/>";
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                int width = i == 0 ? 150 : i == 1 ? 151 : 164;
                String name = i == 0 ? "类 iOS" : i == 1 ? "类 One UI" : "iOS 旧版";
                String path = i == 0 ? iosPath : i == 1 ? oneUiPath : legacyFill;
                String filled = "<path android:fillColor='#E0FFFFFF' android:pathData='" + path + "'/>";
                String background = i == 2 ? "" : vector(width,
                        "<path android:fillColor='#4DFFFFFF' android:pathData='" + path + "'/>");
                JSONObject object = new JSONObject().put("version", 2).put("name", name)
                        .put("fillDirection", "left_to_right")
                        .put("textOffsetX", i == 0 ? -5.25f : 0).put("textOffsetY", 0)
                        .put("textSize", i == 2 ? 29 : 31).put("textAlpha", 224f / 255)
                        .put("textOutline", i == 2 ? 2.9 : 0)
                        .put("shellXml", vector(width, i == 2 ? legacyShell : emptyShell))
                        .put("fillXml", vector(width, filled)).put("backgroundXml", background);
                for (boolean quick : new boolean[]{false, true}) {
                    String bolt = "<group android:scaleX='0.0597222' android:scaleY='0.0597222' android:translateX='"
                            + (i == 2 ? 106 : 91) + "' android:translateY='-3.66667'>"
                            + "<path android:fillColor='#E0FFFFFF' android:pathData='" + (quick ? quickBolt : normalBolt) + "'/></group>";
                    object.put(quick ? "quickChargingXml" : "chargingXml", vector(width, bolt));
                }
                entries.add(new Entry(i == 0 ? IOS : i == 1 ? ONEUI : IOS_LEGACY,
                        BatteryIconPack.parse(object.toString())));
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return List.copyOf(entries);
    }
}
