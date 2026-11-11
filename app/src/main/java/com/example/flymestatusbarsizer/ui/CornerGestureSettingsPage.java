package com.example.flymestatusbarsizer.ui;

import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.Switch;

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
        Switch takeover = activity.addSwitchRow(options, "接管小窗侧边手势",
                "保留两底角斜滑入口，执行本页配置的动作；关闭后恢复 Flyme 原生小窗。需启用侧边手势增强，并在 LSPosed 为模块启用 SystemUI 和 SystemUITools。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED,
                (button, checked) -> bind(activity, root));
        LinearLayout gestureOptions = new LinearLayout(activity);
        gestureOptions.setOrientation(LinearLayout.VERTICAL);
        boolean split = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false);
        activity.addSwitchRow(gestureOptions, "按距离区分动作",
                "仅影响底角手势，按松手时的滑动距离选择动作。",
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false,
                (button, checked) -> bind(activity, root));
        if (split) {
            activity.addSwitchRow(gestureOptions, "启用远距离动作",
                    "在中距离之外增加一档，滑远后收回可回到较近档位。",
                    SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false,
                    (button, checked) -> bind(activity, root));
        }
        activity.addActionButtonRow(gestureOptions, "手势触发区域与动作编辑",
                "直观调整触发距离与滑动分档动作。",
                "编辑", activity::openCornerGestureZoneOverlay);
        options.addView(gestureOptions, PageViewUtils.matchWrap());
        activity.setEnabledRecursively(gestureOptions, takeover.isChecked());
        gestureOptions.setAlpha(takeover.isChecked() ? 1f : 0.45f);
        root.addView(activity.buildSectionCard("底角手势",
                "与普通侧边手势分别配置，均在松手后触发。接管后请设置动作，默认不执行。",
                options, true), PageViewUtils.matchWrap());
    }
}
