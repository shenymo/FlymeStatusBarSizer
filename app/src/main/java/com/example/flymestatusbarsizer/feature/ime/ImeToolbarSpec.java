package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.text.TextUtils;

import java.util.ArrayList;

public final class ImeToolbarSpec {
    static final String STOCK_CONTROL_BAR_BACK = "stock_back";
    static final String STOCK_CONTROL_BAR_PLACEHOLDER = "stock_placeholder";
    private static final String CAPTCHA = "captcha";
    private static final String[] ACTION_BUTTONS = {
            "paste", "undo", "delete", "select_all", "copy", "switch_ime", CAPTCHA
    };
    private static final String[] ALL_BUTTONS = {
            "paste", "undo", "delete", "select_all", "copy", "switch_ime", CAPTCHA,
            STOCK_CONTROL_BAR_BACK
    };
    private static final String[] DEFAULT_BUTTONS = {
            "paste", "undo", "delete", "select_all", "copy", "switch_ime",
            STOCK_CONTROL_BAR_BACK
    };
    private static final int STOCK_CONTROL_BAR_SLOT_COUNT = 7;
    private static final String STOCK_CONTROL_BAR_BUTTON_SIZE = "[7WC]";
    private static final String STOCK_CONTROL_BAR_WIDE_BUTTON_SIZE = "[14WC]";
    private static final String SLOT_EMPTY_TOKEN = "__empty__";

    private ImeToolbarSpec() {
    }

    public static int getButtonSlotCount() {
        return STOCK_CONTROL_BAR_SLOT_COUNT;
    }

    public static ArrayList<String> getAllButtons() {
        ArrayList<String> result = new ArrayList<>(ALL_BUTTONS.length);
        for (String button : ALL_BUTTONS) {
            result.add(button);
        }
        return result;
    }

    public static boolean isValidActionName(String action) {
        return isValidAction(action);
    }

    public static boolean isValidButtonName(String button) {
        return isValidAction(button) || STOCK_CONTROL_BAR_BACK.equals(button);
    }

    public static boolean isPlaceholderName(String button) {
        return STOCK_CONTROL_BAR_PLACEHOLDER.equals(button);
    }

    public static String getButtonLabel(String button) {
        if ("paste".equals(button)) {
            return "粘贴";
        }
        if ("undo".equals(button)) {
            return "撤销";
        }
        if ("delete".equals(button)) {
            return "删除";
        }
        if ("select_all".equals(button)) {
            return "全选";
        }
        if ("copy".equals(button)) {
            return "复制";
        }
        if ("switch_ime".equals(button)) {
            return "切换输入法";
        }
        if (CAPTCHA.equals(button)) {
            return "验证码";
        }
        if (STOCK_CONTROL_BAR_BACK.equals(button)) {
            return "返回";
        }
        return button;
    }

    public static int getButtonSpan(String button) {
        return CAPTCHA.equals(button) ? 2 : 1;
    }

    static boolean isCaptchaButton(String button) {
        return CAPTCHA.equals(button);
    }

    static boolean shouldReplaceOriginalControlBar(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        return config != null
                && config.enabled
                && config.imeReplaceOriginalControlBar;
    }

    static ArrayList<String> resolveButtonSlots(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        if (config == null) {
            return defaultButtonSlots();
        }
        return parseButtonSlots(config.imeControlBarButtonSlots);
    }

    public static ArrayList<String> resolveButtonSlots(String slotRaw) {
        if (TextUtils.isEmpty(slotRaw)) {
            return defaultButtonSlots();
        }
        return parseButtonSlots(slotRaw);
    }

    static String buildStockControlBarLayout(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        return buildFixedSlotLayout(resolveButtonSlots(config));
    }

    public static String serializeButtonSlots(ArrayList<String> slots) {
        ArrayList<String> normalized = normalizeButtonSlots(slots);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            String button = normalized.get(i);
            builder.append(TextUtils.isEmpty(button) ? SLOT_EMPTY_TOKEN : button);
        }
        return builder.toString();
    }

    private static String buildFixedSlotLayout(ArrayList<String> slots) {
        ArrayList<String> normalized = normalizeButtonSlots(slots);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            String button = normalized.get(i);
            if (isCaptchaButton(button)) {
                builder.append(button).append(STOCK_CONTROL_BAR_WIDE_BUTTON_SIZE);
                i++;
                continue;
            }
            builder.append(TextUtils.isEmpty(button) ? STOCK_CONTROL_BAR_PLACEHOLDER : button)
                    .append(STOCK_CONTROL_BAR_BUTTON_SIZE);
        }
        return builder.append(";;").toString();
    }

    private static ArrayList<String> parseButtonSlots(String raw) {
        ArrayList<String> result = emptySlotList();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] parts = raw.split(",", -1);
        int slotCount = Math.min(parts.length, result.size());
        for (int i = 0; i < slotCount; i++) {
            String token = parts[i] == null ? "" : parts[i].trim();
            if (TextUtils.isEmpty(token) || SLOT_EMPTY_TOKEN.equals(token)) {
                continue;
            }
            if (isValidButtonName(token) && !result.contains(token)) {
                result.set(i, token);
            }
        }
        return normalizeButtonSlots(result);
    }

    private static ArrayList<String> normalizeButtonSlots(ArrayList<String> slots) {
        ArrayList<String> normalized = emptySlotList();
        if (slots == null) {
            return normalized;
        }
        boolean[] occupied = new boolean[normalized.size()];
        int slotCount = Math.min(slots.size(), normalized.size());
        for (int i = 0; i < slotCount; i++) {
            String button = slots.get(i);
            int span = getButtonSpan(button);
            if (isValidButtonName(button)
                    && !normalized.contains(button)
                    && i + span <= normalized.size()
                    && canPlaceButton(occupied, i, span)) {
                normalized.set(i, button);
                for (int j = 0; j < span; j++) {
                    occupied[i + j] = true;
                }
            }
        }
        return normalized;
    }

    private static ArrayList<String> defaultButtonSlots() {
        ArrayList<String> result = emptySlotList();
        for (int i = 0; i < DEFAULT_BUTTONS.length; i++) {
            result.set(i, DEFAULT_BUTTONS[i]);
        }
        return result;
    }

    private static ArrayList<String> emptySlotList() {
        ArrayList<String> result = new ArrayList<>(STOCK_CONTROL_BAR_SLOT_COUNT);
        for (int i = 0; i < STOCK_CONTROL_BAR_SLOT_COUNT; i++) {
            result.add(null);
        }
        return result;
    }

    private static boolean canPlaceButton(boolean[] occupied, int start, int span) {
        if (occupied == null || start < 0 || span <= 0 || start + span > occupied.length) {
            return false;
        }
        for (int i = 0; i < span; i++) {
            if (occupied[start + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        for (String candidate : ACTION_BUTTONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }
}
