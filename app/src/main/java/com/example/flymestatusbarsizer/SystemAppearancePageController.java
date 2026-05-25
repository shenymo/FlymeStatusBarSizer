package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class SystemAppearancePageController {
    private SystemAppearancePageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createSystemAppearanceSettingsCard(), PageViewUtils.matchWrap());
    }
}
