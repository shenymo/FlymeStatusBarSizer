package com.example.flymestatusbarsizer.feature.clock;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

final class ClockDetailRecentApp {
    static final ClockDetailRecentApp[] EMPTY_ARRAY = new ClockDetailRecentApp[0];

    final int taskId;
    final int userId;
    final ComponentName componentName;
    final String packageName;
    final Drawable icon;
    final CharSequence label;
    final Bitmap thumbnail;
    final long snapshotId;
    final int taskColor;

    ClockDetailRecentApp(
            int taskId,
            int userId,
            ComponentName componentName,
            Drawable icon,
            CharSequence label,
            Bitmap thumbnail,
            long snapshotId,
            int taskColor) {
        this.taskId = taskId;
        this.userId = userId;
        this.componentName = componentName;
        this.packageName = componentName != null ? sanitizePackageName(componentName.getPackageName()) : "";
        this.icon = icon;
        this.label = sanitizeLabel(label);
        this.thumbnail = thumbnail;
        this.snapshotId = snapshotId;
        this.taskColor = taskColor;
    }

    boolean hasThumbnail() {
        return thumbnail != null && !thumbnail.isRecycled();
    }

    private static CharSequence sanitizeLabel(CharSequence label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.toString().trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String sanitizePackageName(String packageName) {
        if (packageName == null) {
            return "";
        }
        String trimmed = packageName.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
