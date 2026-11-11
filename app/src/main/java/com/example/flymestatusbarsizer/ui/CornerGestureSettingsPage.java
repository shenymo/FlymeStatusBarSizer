package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;
import com.example.flymestatusbarsizer.feature.assistant.SideGestureActions;

public final class CornerGestureSettingsPage {
    private static final String[] TIER_NAMES = {"近距离", "中距离", "远距离"};
    private static final int[] ACTIONS = {
            SettingsStore.SIDE_GESTURE_ACTION_NONE,
            SettingsStore.SIDE_GESTURE_ACTION_BACK,
            SettingsStore.SIDE_GESTURE_ACTION_HOME,
            SettingsStore.SIDE_GESTURE_ACTION_RECENTS,
            SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE,
            SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER,
            SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS,
            SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI,
            SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL
    };

    private CornerGestureSettingsPage() { }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.removeAllViews();
        SharedPreferences prefs = activity.prefs();
        activity.notifySideGestureActionsChanged();
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        activity.addSwitchRow(options, "接管小窗侧边手势",
                "保留两底角斜滑入口，执行本页配置的动作；关闭后恢复 Flyme 原生小窗。需启用侧边手势增强，并在 LSPosed 为模块启用 SystemUI 和 SystemUITools。",
                SettingsStore.KEY_WINDOWMODE_SIDE_GESTURE_ENABLED,
                SettingsStore.DEFAULT_WINDOWMODE_SIDE_GESTURE_ENABLED,
                (button, checked) -> activity.notifySideGestureActionsChanged());
        boolean split = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false);
        boolean far = split && SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false);
        activity.addSwitchRow(options, "按距离区分动作",
                "仅影响底角手势，按松手时的滑动距离选择动作。",
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false,
                (button, checked) -> bind(activity, root));
        if (split) {
            activity.addSwitchRow(options, "启用远距离动作",
                    "在中距离之外增加一档，滑远后收回可回到较近档位。",
                    SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false,
                    (button, checked) -> bind(activity, root));
        }
        root.addView(activity.buildSectionCard("底角手势",
                "与普通侧边手势分别配置，均在松手后触发。接管后请设置动作，默认不执行。",
                options, true), PageViewUtils.matchWrap());

        int[][] actions = SettingsStore.readCornerGestureActions(prefs, false);
        int[][] resolved = SettingsStore.readCornerGestureActions(prefs, true);
        int tierCount = far ? 3 : split ? 2 : 1;
        for (int side = 0; side < 2; side++) {
            LinearLayout sideOptions = new LinearLayout(activity);
            sideOptions.setOrientation(LinearLayout.VERTICAL);
            for (int tier = 0; tier < tierCount; tier++) {
                int selectedSide = side;
                int selectedTier = tier;
                int action = actions[tier][side];
                String label;
                if (action == SettingsStore.SIDE_GESTURE_INHERIT_LONG) {
                    label = "沿用上一档 · " + actionLabel(prefs, resolved[tier][side]);
                } else {
                    label = actionLabel(prefs, action);
                }
                activity.addActionButtonRow(sideOptions, TIER_NAMES[tier] + "动作", label, "设置",
                        () -> showActionPicker(activity, root, selectedSide, selectedTier));
            }
            root.addView(activity.buildSectionCard(side == 0 ? "左底角手势" : "右底角手势",
                    "从屏幕底角斜向内滑动。", sideOptions, true),
                    PageViewUtils.matchWrapWithTop(activity, 8));
        }

        LinearLayout distances = new LinearLayout(activity);
        distances.setOrientation(LinearLayout.VERTICAL);
        activity.addApplySliderRow(distances, "最短触发距离", "不足此距离时不执行动作。",
                SettingsStore.KEY_CORNER_GESTURE_DISTANCE_DP,
                SettingsStore.DEFAULT_CORNER_GESTURE_DISTANCE_DP, 0, 240, "dp");
        if (split) {
            activity.addApplySliderRow(distances, "中距离阈值", "达到此距离后松手，执行中距离动作。",
                    SettingsStore.KEY_CORNER_GESTURE_LONG_DISTANCE_DP,
                    SettingsStore.DEFAULT_CORNER_GESTURE_LONG_DISTANCE_DP, 1, 480, "dp");
        }
        if (far) {
            activity.addApplySliderRow(distances, "远距离阈值", "达到此距离后松手，执行远距离动作。",
                    SettingsStore.KEY_CORNER_GESTURE_FAR_DISTANCE_DP,
                    SettingsStore.DEFAULT_CORNER_GESTURE_FAR_DISTANCE_DP, 2, 720, "dp");
        }
        root.addView(activity.buildSectionCard("滑动距离",
                "距离供左右底角共用；中距离至少比最短距离多 1dp，远距离至少比中距离多 1dp。",
                distances, true), PageViewUtils.matchWrapWithTop(activity, 8));
    }

    private static String actionLabel(SharedPreferences prefs, int action) {
        if (SideGestureTargetStore.isDynamicAction(action)) {
            SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                    SideGestureTargetStore.readRaw(prefs), action);
            if (target != null) return target.label;
        }
        return SideGestureActions.label(action);
    }

    private static void showActionPicker(MainActivity activity, LinearLayout root, int side, int tier) {
        String[] labels = new String[ACTIONS.length + (tier > 0 ? 1 : 0)];
        for (int i = 0; i < ACTIONS.length; i++) labels[i] = SideGestureActions.label(ACTIONS[i]);
        if (tier > 0) labels[ACTIONS.length] = "沿用上一档动作";
        String key = SettingsStore.cornerGestureActionKey(tier, side);
        int current = SettingsStore.readCornerGestureActions(activity.prefs(), false)[tier][side];
        int checked = current == SettingsStore.SIDE_GESTURE_INHERIT_LONG ? ACTIONS.length : -1;
        for (int i = 0; i < ACTIONS.length; i++) if (current == ACTIONS[i]) checked = i;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle((side == 0 ? "左底角 · " : "右底角 · ") + TIER_NAMES[tier] + "动作")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == ACTIONS.length) {
                        activity.putIntSetting(key, SettingsStore.SIDE_GESTURE_INHERIT_LONG);
                        bind(activity, root);
                        return;
                    }
                    int action = ACTIONS[which];
                    if (SideGestureTargetStore.isTargetType(action)) {
                        activity.configureSideGestureTarget(action, target -> {
                            activity.putIntSetting(key, target);
                            bind(activity, root);
                        });
                    } else {
                        activity.putIntSetting(key, action);
                        bind(activity, root);
                    }
                })
                .setNegativeButton("取消", null);
        if (SideGestureTargetStore.isDynamicAction(current)) {
            SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                    SideGestureTargetStore.readRaw(activity.prefs()), current);
            if (target != null) builder.setNeutralButton("查看当前目标", (dialog, which) ->
                    activity.showSideGestureTargetDetails(target, () ->
                            activity.configureSideGestureTarget(target.type, replacement -> {
                                activity.putIntSetting(key, replacement);
                                bind(activity, root);
                            })));
        }
        AlertDialog dialog = builder.show();
        activity.attachDialogButtonHaptics(dialog);
    }
}
