package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.text.TextUtils;

import java.util.ArrayList;

public final class ImeToolbarSpec {
    static final String STOCK_CONTROL_BAR_BACK = "stock_back";
    private static final String[] ACTION_BUTTONS = {
            "paste", "undo", "delete", "select_all", "copy", "switch_ime"
    };
    private static final String[] ALL_BUTTONS = {
            "paste", "undo", "delete", "select_all", "copy", "switch_ime", STOCK_CONTROL_BAR_BACK
    };
    private static final String STOCK_CONTROL_BAR_BUTTON_SIZE = "[1WC]";
    private static final int ALIGN_LEFT = 0;
    private static final int ALIGN_RIGHT = 1;
    private static final int ALIGN_JUSTIFY = 2;

    private ImeToolbarSpec() {
    }

    public static void normalizeButtonOrder(ArrayList<String> result) {
        if (result == null) {
            return;
        }
        for (int i = 0; i < ALL_BUTTONS.length; i++) {
            String button = ALL_BUTTONS[i];
            if (!result.contains(button)) {
                result.add(Math.min(i, result.size()), button);
            }
        }
    }

    public static boolean isValidActionName(String action) {
        return isValidAction(action);
    }

    public static boolean isValidButtonName(String button) {
        return isValidAction(button) || STOCK_CONTROL_BAR_BACK.equals(button);
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
        if (STOCK_CONTROL_BAR_BACK.equals(button)) {
            return "返回";
        }
        return button;
    }

    static boolean shouldReplaceOriginalControlBar(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        return config != null
                && config.enabled
                && config.imeReplaceOriginalControlBar;
    }

    static ArrayList<String> resolveButtonOrder(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> result = parseButtonList(config == null ? "" : config.imeControlBarButtonOrder);
        normalizeButtonOrder(result);
        return result;
    }

    static ArrayList<String> resolveVisibleButtons(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> visibleButtons = resolveButtonOrder(config);
        ArrayList<String> hiddenButtons = parseButtonList(
                config == null ? "" : config.imeControlBarHiddenButtons);
        for (int i = visibleButtons.size() - 1; i >= 0; i--) {
            if (hiddenButtons.contains(visibleButtons.get(i))) {
                visibleButtons.remove(i);
            }
        }
        if (visibleButtons.isEmpty()) {
            ArrayList<String> fallback = resolveButtonOrder(config);
            if (!fallback.isEmpty()) {
                visibleButtons.add(fallback.get(0));
            }
        }
        return visibleButtons;
    }

    static String buildStockControlBarLayout(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> visibleButtons = resolveVisibleButtons(config);
        if (visibleButtons.isEmpty()) {
            return null;
        }
        int alignment = config == null ? ALIGN_JUSTIFY : config.imeControlBarAlignment;
        switch (alignment) {
            case ALIGN_LEFT:
                return buildLayout(visibleButtons, null, null);
            case ALIGN_RIGHT:
                return buildLayout(null, null, visibleButtons);
            case ALIGN_JUSTIFY:
            default:
                if (visibleButtons.size() <= 1) {
                    return buildLayout(visibleButtons, null, null);
                }
                ArrayList<String> startButtons = new ArrayList<>(
                        visibleButtons.subList(0, visibleButtons.size() - 1));
                ArrayList<String> endButtons = new ArrayList<>();
                endButtons.add(visibleButtons.get(visibleButtons.size() - 1));
                return buildLayout(startButtons, null, endButtons);
        }
    }

    private static String buildLayout(
            ArrayList<String> startButtons,
            ArrayList<String> centerButtons,
            ArrayList<String> endButtons) {
        return buildSegment(startButtons) + ';'
                + buildSegment(centerButtons) + ';'
                + buildSegment(endButtons);
    }

    private static String buildSegment(ArrayList<String> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < buttons.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(buttons.get(i)).append(STOCK_CONTROL_BAR_BUTTON_SIZE);
        }
        return builder.toString();
    }

    private static ArrayList<String> parseButtonList(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String button = part == null ? "" : part.trim();
            if (isValidButtonName(button) && !result.contains(button)) {
                result.add(button);
            }
        }
        return result;
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
