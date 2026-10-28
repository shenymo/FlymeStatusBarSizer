package com.example.flymestatusbarsizer.feature.battery;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.ui.ColorPreferenceEditor;

import android.widget.LinearLayout;

public final class CircleBatteryAnimationEditor {
    private final MainActivity activity;
    public CircleBatteryAnimationEditor(MainActivity activity) { this.activity = activity; }

    public void addTo(LinearLayout root) {
        activity.addSwitchRow(root, "启用动画", "关闭后保留配色，圆环静态显示。",
                CircleBatteryAnimationConfig.ENABLED, true);
        activity.addChoiceRow(root, "普通动效", "整体旋转让电量弧绕摄像头顺时针转动，弧长不变；100% 纯色整圆转动不明显。",
                CircleBatteryAnimationConfig.NORMAL, CircleBatteryAnimationConfig.FLOW,
                new int[]{0, 1, 3, 4}, new String[]{"静态", "柔和流光", "呼吸", "整体旋转"});
        activity.addChoiceRow(root, "充电动效", "实际充电时播放；插电但未充电不会显示充电彗星。",
                CircleBatteryAnimationConfig.CHARGING, CircleBatteryAnimationConfig.COMET,
                new int[]{0, 1, 2, 3, 4}, new String[]{"静态", "柔和流光", "彗星", "呼吸", "整体旋转"});
        activity.addChoiceRow(root, "普通配色", "只改变普通状态；充电、低电量和省电沿用各自颜色。",
                CircleBatteryAnimationConfig.PALETTE, CircleBatteryAnimationConfig.STATE_COLOR,
                new int[]{0, 1, 2}, new String[]{"跟随状态色", "双色渐变", "彩虹"});
        ColorPreferenceEditor colors = new ColorPreferenceEditor(activity);
        colors.addColor(root, "双色 · 起始", CircleBatteryAnimationConfig.COLOR_START, 0xFF088BFF);
        colors.addColor(root, "双色 · 结束", CircleBatteryAnimationConfig.COLOR_END, 0xFF00DCC8);
        activity.addChoiceRow(root, "动画速度", "标准：流光 8 秒、彗星 2.5 秒、呼吸和整体旋转 3 秒一轮；旧彩虹保留 3 秒一圈。",
                CircleBatteryAnimationConfig.SPEED, 1,
                new int[]{0, 1, 2}, new String[]{"慢", "标准", "快"});
        activity.addChoiceRow(root, "动画强度", "调整流光、彗星拖尾与呼吸幅度，不改变电量弧长度。",
                CircleBatteryAnimationConfig.STRENGTH, 1,
                new int[]{0, 1, 2}, new String[]{"低", "标准", "高"});
        activity.addSwitchRow(root, "仅充电时播放", "普通使用保持静态，保留插拔电源反馈。",
                CircleBatteryAnimationConfig.CHARGING_ONLY, false);
        activity.addSwitchRow(root, "状态反馈", "接电扫光、拔电渐变、充满轻亮；首次进入低电量时呼吸两次。",
                CircleBatteryAnimationConfig.EVENTS, true);
        activity.addSwitchRow(root, "电量平滑过渡", "电量改变后，弧线端点用 600 毫秒过渡到新位置。",
                CircleBatteryAnimationConfig.SMOOTH, true);
    }
}
