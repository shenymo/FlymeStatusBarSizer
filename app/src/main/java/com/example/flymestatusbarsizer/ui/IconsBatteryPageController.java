package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.feature.statusbar.StatusBarIconVisibility;

import android.widget.LinearLayout;

public final class IconsBatteryPageController {
    private IconsBatteryPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createIconSizingCard(), PageViewUtils.matchWrap());
        addIconVisibilityCard(activity, root);
        addRingerIconCard(activity, root);
        root.addView(activity.createBatterySettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createNotificationSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createSignalSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }

    private static void addRingerIconCard(MainActivity activity, LinearLayout root) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(content, "使用矢量图标",
                "将状态栏静音和震动图标改为矢量资源，放大时保持清晰。",
                com.example.flymestatusbarsizer.config.SettingsStore
                        .KEY_STATUS_BAR_RINGER_VECTOR_ICONS_ENABLED,
                com.example.flymestatusbarsizer.config.SettingsStore
                        .DEFAULT_STATUS_BAR_RINGER_VECTOR_ICONS_ENABLED);
        root.addView(activity.buildSectionCard("静音与震动", null, content),
                PageViewUtils.matchWrapWithTop(activity, 8));
    }

    private static void addIconVisibilityCard(MainActivity activity, LinearLayout root) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        String group = null;
        for (StatusBarIconVisibility.Icon icon : StatusBarIconVisibility.ICONS) {
            if (!icon.group.equals(group)) {
                activity.addProfileSectionHeader(content, icon.group, "");
                group = icon.group;
            } else {
                activity.addDivider(content);
            }
            activity.addSwitchRow(content, "隐藏" + icon.title, icon.description, icon.key, false);
        }
        root.addView(activity.buildSectionCard("隐藏状态图标",
                "开启后隐藏对应图标并释放占位，作用于状态栏、锁屏和下拉面板。时间、电池、网速、通知和权限提示仍由各自设置控制。",
                content), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
