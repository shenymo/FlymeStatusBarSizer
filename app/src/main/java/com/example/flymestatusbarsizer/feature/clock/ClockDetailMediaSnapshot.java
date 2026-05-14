package com.example.flymestatusbarsizer.feature.clock;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

final class ClockDetailMediaSnapshot {
    static final ClockDetailMediaSnapshot EMPTY = new ClockDetailMediaSnapshot(
            false,
            null,
            "",
            "",
            "",
            null,
            "");

    final boolean active;
    final Drawable artwork;
    final CharSequence title;
    final CharSequence subtitle;
    final CharSequence playbackStateLabel;
    final PendingIntent launchIntent;
    final String packageName;

    ClockDetailMediaSnapshot(
            boolean active,
            Drawable artwork,
            CharSequence title,
            CharSequence subtitle,
            CharSequence playbackStateLabel,
            PendingIntent launchIntent,
            String packageName) {
        this.active = active;
        this.artwork = artwork;
        this.title = sanitize(title);
        this.subtitle = sanitize(subtitle);
        this.playbackStateLabel = sanitize(playbackStateLabel);
        this.launchIntent = launchIntent;
        this.packageName = sanitizePackageName(packageName);
    }

    private static CharSequence sanitize(CharSequence value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.toString().trim();
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
