package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.text.TextUtils;

import java.util.ArrayList;

final class ImeToolbarSpec {
    private static final String[] ACTIONS = {
            "paste", "delete", "select_all", "copy", "switch_ime"
    };
    private static final String STOCK_CONTROL_BAR_BUTTON_SIZE = "[1WC]";

    private ImeToolbarSpec() {
    }

    static ArrayList<String> resolveToolbarOrder(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> result = new ArrayList<>();
        if (config != null && !TextUtils.isEmpty(config.imeToolbarOrder)) {
            String[] parts = config.imeToolbarOrder.split(",");
            for (String part : parts) {
                String action = part == null ? "" : part.trim();
                if (isValidAction(action) && !result.contains(action)) {
                    result.add(action);
                }
            }
        }
        for (String action : ACTIONS) {
            if (!result.contains(action)) {
                result.add(action);
            }
        }
        return result;
    }

    static boolean isValidActionName(String action) {
        return isValidAction(action);
    }

    static boolean shouldEmbedInStockControlBar(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        return config != null
                && config.enabled
                && config.imeToolbarEnabled
                && config.imeForceStockControlBar;
    }

    static String buildStockControlBarLayout(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        ArrayList<String> actions = resolveToolbarOrder(config);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(actions.get(i)).append(STOCK_CONTROL_BAR_BUTTON_SIZE);
        }
        builder.append(";;back").append(STOCK_CONTROL_BAR_BUTTON_SIZE);
        return builder.toString();
    }

    static String getActionLabel(String action) {
        if ("paste".equals(action)) {
            return "粘贴";
        }
        if ("delete".equals(action)) {
            return "删除";
        }
        if ("select_all".equals(action)) {
            return "全选";
        }
        if ("copy".equals(action)) {
            return "复制";
        }
        if ("switch_ime".equals(action)) {
            return "切换输入法";
        }
        return action;
    }

    private static boolean isValidAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        for (String candidate : ACTIONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }
}
