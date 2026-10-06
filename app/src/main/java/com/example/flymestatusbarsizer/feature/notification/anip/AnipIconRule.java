package com.example.flymestatusbarsizer.feature.notification.anip;

import android.graphics.Color;

/**
 * A resolved ANIP notification icon rule.
 *
 * <p>A rule carries the display metadata that the notification icon hook needs before it decides
 * whether to replace a status bar icon and which colours to use. Bitmaps are never held here; load
 * them through {@link AnipIconLibrary#loadBitmap(AnipIconRule)} so that all decoded bitmaps share
 * one bounded cache.
 */
public final class AnipIconRule {
    private final String packageName;
    private final String label;
    private final int color;
    private final boolean overlay;
    private final String assetPath;

    AnipIconRule(String packageName, String label, int color, boolean overlay, String assetPath) {
        this.packageName = packageName;
        this.label = label;
        this.color = color;
        this.overlay = overlay;
        this.assetPath = assetPath;
    }

    /** Package name this rule was declared for, exactly as written in the manifest. */
    public String getPackageName() {
        return packageName;
    }

    /** Localised application name, resolved against the current system language. */
    public String getLabel() {
        return label;
    }

    /** Tint colour declared by the rule, or {@link Color#TRANSPARENT} when the rule declares none. */
    public int getColor() {
        return color;
    }

    /**
     * Whether the icon must replace every notification icon of this application, including the ones
     * that already look like a compliant monochrome icon.
     */
    public boolean isOverlay() {
        return overlay;
    }

    /** Asset path of the PNG backing this rule, relative to the asset root. */
    public String getAssetPath() {
        return assetPath;
    }

    @Override
    public String toString() {
        return "AnipIconRule{" + packageName + ", label=" + label + ", overlay=" + overlay
                + ", asset=" + assetPath + '}';
    }
}
