package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.MainActivity;

import android.widget.LinearLayout;

public final class SystemInteractionPageController {
    private SystemInteractionPageController() {
    }

    public static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createMBackActionSettingsCard(), PageViewUtils.matchWrap());
        root.addView(activity.createSideGestureSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createWindowModeSideGestureSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createCarLinkSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createShareTargetsSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createMBackNavigationSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createImeToolbarSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createLauncherRecentsSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
