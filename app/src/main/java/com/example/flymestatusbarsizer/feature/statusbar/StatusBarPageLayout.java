package com.example.flymestatusbarsizer.feature.statusbar;

import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class StatusBarPageLayout {
    public enum Item {
        DATE("日期"), TEMPERATURE("电池温度"), POWER("充电功率"), MEMORY("已用内存"),
        SONG("歌曲名"), LYRICS("原生歌词"), PREVIOUS("上一首"), PLAY_PAUSE("播放/暂停"), NEXT("下一首");

        public final String label;
        Item(String label) { this.label = label; }
        public boolean isMedia() { return this == SONG || isButton(); }
        public boolean isButton() { return ordinal() >= PREVIOUS.ordinal(); }
    }

    public static final String DEFAULT_LEFT = "[\"DATE\",\"TEMPERATURE\"]";
    public static final String DEFAULT_RIGHT = "[\"POWER\",\"MEMORY\"]";
    public final List<Item> left;
    public final List<Item> right;

    private StatusBarPageLayout(List<Item> left, List<Item> right) {
        this.left = Collections.unmodifiableList(left);
        this.right = Collections.unmodifiableList(right);
    }

    public static StatusBarPageLayout parse(String left, String right) {
        EnumSet<Item> used = EnumSet.noneOf(Item.class);
        try {
            return new StatusBarPageLayout(read(new JSONArray(left), used),
                    read(new JSONArray(right), used));
        } catch (JSONException invalid) {
            return new StatusBarPageLayout(new ArrayList<>(List.of(Item.DATE, Item.TEMPERATURE)),
                    new ArrayList<>(List.of(Item.POWER, Item.MEMORY)));
        }
    }

    private static List<Item> read(JSONArray array, EnumSet<Item> used) {
        List<Item> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String id = array.optString(i);
            for (Item item : Item.values()) {
                if (item.name().equals(id) && used.add(item)) result.add(item);
            }
        }
        return result;
    }

    public static String encode(List<Item> items) {
        JSONArray result = new JSONArray();
        for (Item item : items) result.put(item.name());
        return result.toString();
    }
}
