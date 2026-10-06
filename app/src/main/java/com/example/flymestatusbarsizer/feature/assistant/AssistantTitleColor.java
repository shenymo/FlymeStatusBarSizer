package com.example.flymestatusbarsizer.feature.assistant;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/** Temporarily overrides only the native overview heading, never card titles or desktop views. */
final class AssistantTitleColor implements ViewTreeObserver.OnPreDrawListener {
    private final TextView title;
    private ColorStateList nativeColors, appliedColors;
    private ViewTreeObserver observer;
    private Integer tint;
    private boolean active;

    AssistantTitleColor(View decor) {
        int id = decor.getResources().getIdentifier("title", "id", AssistantProtocol.PACKAGE);
        View view = id == 0 ? null : decor.findViewById(id);
        title = view instanceof TextView ? (TextView) view : null;
    }

    // Also used by the lifecycle tests without depending on the assistant APK's resources.
    AssistantTitleColor(TextView title) { this.title = title; }

    void setTint(Integer color) {
        tint = color;
        if (active) update();
    }

    void apply() {
        if (active || title == null) return;
        active = true;
        nativeColors = title.getTextColors();
        observer = title.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        update();
    }

    private void update() {
        if (title == null || tint == null) return;
        // Wallpaper broadcasts can still update the native color while the global window is open.
        if (title.getTextColors() != appliedColors) nativeColors = title.getTextColors();
        if (appliedColors == null || appliedColors.getDefaultColor() != tint
                || title.getTextColors() != appliedColors) {
            appliedColors = ColorStateList.valueOf(tint);
            title.setTextColor(appliedColors);
        }
    }

    @Override public boolean onPreDraw() {
        if (active) update();
        return true;
    }

    void restore() {
        if (!active) return;
        active = false;
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        // addView may return before attachment: Android then merges the floating observer into
        // the ViewRoot observer. Remove from that live observer too when returning to desktop.
        ViewTreeObserver current = title.getViewTreeObserver();
        if (current != observer && current.isAlive()) current.removeOnPreDrawListener(this);
        observer = null;
        if (title.getTextColors() == appliedColors && nativeColors != null) {
            title.setTextColor(nativeColors);
        }
        appliedColors = null;
        // The subsequent desktop attachment lets WallpaperColorManager apply its latest state.
    }
}
