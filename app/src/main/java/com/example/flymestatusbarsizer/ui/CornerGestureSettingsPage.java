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
        LinearLayout editOptions = new LinearLayout(activity);
        editOptions.setOrientation(LinearLayout.VERTICAL);
        activity.addActionButtonRow(editOptions, "动作与触发区域", null,
                "编辑", activity::openCornerGestureZoneOverlay);
        root.addView(activity.buildSectionCard("底角手势", null, editOptions, true),
                PageViewUtils.matchWrap());

        LinearLayout distanceOptions = new LinearLayout(activity);
        distanceOptions.setOrientation(LinearLayout.VERTICAL);
        boolean split = SettingsStore.readBoolean(prefs,
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false);
        activity.addSwitchRow(distanceOptions, "按距离设置不同动作", null,
                SettingsStore.KEY_CORNER_GESTURE_SPLIT_ENABLED, false,
                (button, checked) -> bind(activity, root));
        if (split) {
            activity.addSwitchRow(distanceOptions, "增加远距离动作", null,
                    SettingsStore.KEY_CORNER_GESTURE_FAR_ENABLED, false,
                    (button, checked) -> bind(activity, root));
        }
        root.addView(activity.buildSectionCard("滑动距离", null, distanceOptions, true),
                activity.matchWrapWithTop(8));
    }
}
