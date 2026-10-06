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
        activity.addSwitchRow(camera, "前摄空闲时隐藏黑边",
                "移除摄像头周围的软件黑色填充，前摄使用时恢复。物理黑边无法移除。",
                SettingsStore.KEY_HIDE_IDLE_CAMERA_CUTOUT, SettingsStore.DEFAULT_HIDE_IDLE_CAMERA_CUTOUT);
        root.addView(activity.buildSectionCard("摄像头挖孔", "", camera), PageViewUtils.matchWrap());
        LinearLayout tint = new LinearLayout(activity);
        tint.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(tint, "启用图标颜色设置", "关闭后跟随系统，保留各界面配置。",
                SettingsStore.KEY_STATUS_BAR_TINT_ENABLED, SettingsStore.DEFAULT_STATUS_BAR_TINT_ENABLED);
        String[] scenes = {"桌面", "最近任务", "下拉通知栏", "控制中心", "锁屏"};
        for (int i = 0; i < scenes.length; i++) {
            activity.addDivider(tint);
            activity.addChoiceRow(tint, scenes[i], "", SettingsStore.STATUS_BAR_TINT_KEYS[i],
                    0, new int[]{0, 1, 2}, new String[]{"跟随系统", "固定黑色", "固定白色"});
        }
        root.addView(activity.buildSectionCard("状态栏图标颜色",
                "各界面独立设置，其他界面保持系统原生变色。", tint), PageViewUtils.matchWrap());
        LinearLayout organizer = new LinearLayout(activity);
        organizer.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(organizer, "AI 整理桌面",
                "按应用用途生成文件夹，支持预览和撤销。保留底栏、小组件及特殊快捷方式。",
                "打开", activity::showLauncherOrganizerPage);
        root.addView(activity.buildSectionCard("桌面整理", "", organizer), PageViewUtils.matchWrap());
        root.addView(activity.createSystemAppearanceSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
