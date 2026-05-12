package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class TelephonyDebugPageController {
    private TelephonyDebugPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createTelephonyDebugSettingsCard(), PageViewUtils.matchWrap());
    }
}
