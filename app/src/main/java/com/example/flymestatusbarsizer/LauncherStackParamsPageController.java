package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class LauncherStackParamsPageController {
    private LauncherStackParamsPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createLauncherStackParamsSettingsCard(), PageViewUtils.matchWrap());
    }
}
