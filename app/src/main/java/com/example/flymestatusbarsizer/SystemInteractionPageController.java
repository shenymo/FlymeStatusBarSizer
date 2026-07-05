package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class SystemInteractionPageController {
    private SystemInteractionPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createMBackActionSettingsCard(), PageViewUtils.matchWrap());
        root.addView(activity.createWindowModeSideGestureSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createMBackNavigationSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createImeToolbarSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createLauncherRecentsSettingsCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
