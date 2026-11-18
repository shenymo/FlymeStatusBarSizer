package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class TimeNetworkPageController {
    private TimeNetworkPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createConnectionRateSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createTimeExpressionSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createTimeInteractionSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createTimeTypographySettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
