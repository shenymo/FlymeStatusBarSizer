package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class PositionTuningPageController {
    private PositionTuningPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createPositionTuningSettingsCard(), PageViewUtils.matchWrap());
    }
}
