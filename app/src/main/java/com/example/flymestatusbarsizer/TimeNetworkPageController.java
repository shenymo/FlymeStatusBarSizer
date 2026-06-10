package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class TimeNetworkPageController {
    private TimeNetworkPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createConnectionRateSettingsCard(), PageViewUtils.matchWrap());
        root.addView(activity.createTimeExpressionSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createTimeInteractionSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createTimeTypographySettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
