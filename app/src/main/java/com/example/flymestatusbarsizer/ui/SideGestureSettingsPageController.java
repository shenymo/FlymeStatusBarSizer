package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class SideGestureSettingsPageController {
    private SideGestureSettingsPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createSideGestureSettingsPage(), PageViewUtils.matchWrap());
    }
}
