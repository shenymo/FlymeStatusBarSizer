package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class IconsBatteryPageController {
    private IconsBatteryPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createIconSizingCard(), PageViewUtils.matchWrap());
        root.addView(activity.createBatterySettingsCard(), PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(activity.createNotificationSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 12));
        root.addView(activity.createSignalSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 12));
    }
}
