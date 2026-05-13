package com.example.flymestatusbarsizer.feature.clock;

import android.graphics.drawable.Drawable;

final class ClockDetailRecentApp {
    static final ClockDetailRecentApp[] EMPTY_ARRAY = new ClockDetailRecentApp[0];

    final int taskId;
    final int userId;
    final Drawable icon;
    final CharSequence label;

    ClockDetailRecentApp(int taskId, int userId, Drawable icon, CharSequence label) {
        this.taskId = taskId;
        this.userId = userId;
        this.icon = icon;
        this.label = sanitizeLabel(label);
    }

    private static CharSequence sanitizeLabel(CharSequence label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.toString().trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
