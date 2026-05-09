package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.text.TextUtils;

import java.util.ArrayList;

final class ImeToolbarSpec {
    private static final String[] ACTIONS = {
            "paste", "delete", "select_all", "copy", "switch_ime"
    };

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
