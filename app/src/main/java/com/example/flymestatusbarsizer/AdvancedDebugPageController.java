package com.example.flymestatusbarsizer;

import android.widget.LinearLayout;

final class AdvancedDebugPageController {
    private AdvancedDebugPageController() {
    }

    static void bind(MainActivity activity, LinearLayout root) {
        root.addView(activity.createAdvancedToolsCard(), PageViewUtils.matchWrap());
        root.addView(activity.createConfigManagementCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createPerformanceDebugCard(), PageViewUtils.matchWrapWithTop(activity, 8));
        root.addView(activity.createOneMindPerfControlCard(), PageViewUtils.matchWrapWithTop(activity, 8));
    }
}
