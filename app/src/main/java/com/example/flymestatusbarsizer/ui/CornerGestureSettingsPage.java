package com.example.flymestatusbarsizer.ui;

import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

public final class CornerGestureSettingsPage {
    private CornerGestureSettingsPage() { }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.removeAllViews();
        SharedPreferences prefs = activity.prefs();
        activity.notifySideGestureActionsChanged();
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(options, "编辑动作与触发区域",
                "分别设置左右底角的动作；拖动圆点调整触发距离。",
                "编辑", activity::openCornerGestureZoneOverlay);
        activity.addDivider(options);
        boolean split = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false);
        activity.addSwitchRow(options, "按滑动距离设置不同动作",
                "分别设置近距离和中距离动作，按松手时的滑动距离执行。",
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false,
                (button, checked) -> bind(activity, root));
        if (split) {
            activity.addSwitchRow(options, "增加远距离动作",
                    "在中距离之外增加一档，滑远后收回可回到较近档位。",
                    SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false,
                    (button, checked) -> bind(activity, root));
        }
        root.addView(activity.buildSectionCard("底角手势",
                "需在“手势增强”中开启“自定义底角手势”。底角动作在松手后执行，默认不执行动作，请先设置。",
                options, true), PageViewUtils.matchWrap());
    }
}
