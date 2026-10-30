package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

import android.widget.LinearLayout;

public final class SystemAppearancePageController {
    private SystemAppearancePageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        LinearLayout camera = new LinearLayout(activity);
        camera.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(camera, "前置摄像头空闲时隐藏黑边",
                "隐藏摄像头周围的软件黑边，使用前置摄像头时恢复。屏幕物理黑边无法隐藏。",
                SettingsStore.KEY_HIDE_IDLE_CAMERA_CUTOUT, SettingsStore.DEFAULT_HIDE_IDLE_CAMERA_CUTOUT);
        root.addView(activity.buildSectionCard("摄像头挖孔显示", "", camera), PageViewUtils.matchWrap());
        LinearLayout tint = new LinearLayout(activity);
        tint.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(tint, "自定义状态栏图标颜色", "关闭后跟随系统颜色，已保存的各界面设置会保留。",
                SettingsStore.KEY_STATUS_BAR_TINT_ENABLED, SettingsStore.DEFAULT_STATUS_BAR_TINT_ENABLED);
        String[] scenes = {"桌面", "最近任务", "下拉通知栏", "控制中心", "锁屏"};
        for (int i = 0; i < scenes.length; i++) {
            activity.addDivider(tint);
            activity.addChoiceRow(tint, scenes[i], "", SettingsStore.STATUS_BAR_TINT_KEYS[i],
                    0, new int[]{0, 1, 2}, new String[]{"跟随系统", "固定黑色", "固定白色"});
        }
        root.addView(activity.buildSectionCard("状态栏图标颜色",
                "可分别设置桌面、最近任务、通知栏、控制中心和锁屏的图标颜色；未设置的界面跟随系统。", tint), PageViewUtils.matchWrap());
        LinearLayout organizer = new LinearLayout(activity);
        organizer.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(organizer, "AI 桌面整理",
                "按应用用途生成文件夹，支持预览、调整和撤销。保留底栏、小组件及特殊快捷方式。",
                "打开", activity::showLauncherOrganizerPage);
        activity.addActionButtonRow(organizer, "按使用情况排序",
                "按最近使用频率整理所有文件夹内的应用，与 AI 桌面整理分开使用。",
                "打开", () -> activity.openPage(MainActivity.Page.LAUNCHER_USAGE_SORT));
        root.addView(activity.buildSectionCard("桌面整理", "", organizer), PageViewUtils.matchWrap());
        root.addView(activity.createSystemAppearanceSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
