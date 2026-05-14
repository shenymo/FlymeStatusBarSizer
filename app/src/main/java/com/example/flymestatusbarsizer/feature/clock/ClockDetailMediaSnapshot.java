package com.example.flymestatusbarsizer.feature.clock;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

import java.util.Objects;

final class ClockDetailMediaSnapshot {
    static final ClockDetailMediaSnapshot EMPTY = new ClockDetailMediaSnapshot(
            false,
            null,
            "",
            "",
            "",
            "",
            null,
            "");

    final boolean active;
    final Drawable artwork;
    final String artworkKey;
    final CharSequence title;
    final CharSequence subtitle;
    final CharSequence playbackStateLabel;
    final PendingIntent launchIntent;
    final String packageName;

    ClockDetailMediaSnapshot(
            boolean active,
            Drawable artwork,
            String artworkKey,
            CharSequence title,
            CharSequence subtitle,
            CharSequence playbackStateLabel,
            PendingIntent launchIntent,
            String packageName) {
        this.active = active;
        this.artwork = artwork;
        this.artworkKey = sanitizePackageName(artworkKey);
        this.title = sanitize(title);
        this.subtitle = sanitize(subtitle);
        this.playbackStateLabel = sanitize(playbackStateLabel);
        this.launchIntent = launchIntent;
        this.packageName = sanitizePackageName(packageName);
    }

    boolean isEquivalentTo(ClockDetailMediaSnapshot other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        return active == other.active
                && artworkKey.equals(other.artworkKey)
                && textEquals(title, other.title)
                && textEquals(subtitle, other.subtitle)
                && textEquals(playbackStateLabel, other.playbackStateLabel)
                && packageName.equals(other.packageName)
                && Objects.equals(launchIntent, other.launchIntent);
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

    private static boolean textEquals(CharSequence first, CharSequence second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.toString().contentEquals(second);
    }
}
