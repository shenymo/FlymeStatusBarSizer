package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;
import android.view.SurfaceControl;

/** Surface crop and transform rules for hosted task windows. */
final class OneStepSurfaceGeometry {
    private OneStepSurfaceGeometry() {}

    static Rect crop(OneStepShell.Host host) {
        Rect crop = new Rect(0, 0, host.logicalBounds.width(), host.logicalBounds.height());
        if (host.home()) {
            crop.offset(host.logicalBounds.left - host.originalBounds.left,
                    host.logicalBounds.top - host.originalBounds.top);
        } else if (host.landscapeRequested && !host.actualBounds.isEmpty()) {
            // WM may enlarge the requested frame to satisfy minimum task dimensions.
            // Keep its complete task-local bounds, including during orientation changes.
            crop.set(0, 0, host.actualBounds.width(), host.actualBounds.height());
        } else if (host.fullscreenCompat) {
            Rect actual = host.actualBounds;
            crop.set(host.logicalBounds.width() > host.logicalBounds.height()
                    ? actual : host.logicalBounds);
            if (!crop.intersect(actual))
                throw new IllegalStateException("Fullscreen task has no visible viewport");
            crop.offset(-actual.left, -actual.top);
        } else if (!host.surfaceBounds.isEmpty()) {
            // TaskView can publish the new physical frame before WM updates its
            // cached task bounds. Use the task-local frame during that handoff.
            if (!host.actualBounds.isEmpty() && host.actualBounds.contains(host.logicalBounds)) {
                crop.offset(host.logicalBounds.left - host.actualBounds.left,
                        host.logicalBounds.top - host.actualBounds.top);
            } else {
                crop.set(0, 0, host.surfaceBounds.width(), host.surfaceBounds.height());
            }
        }
        return crop;
    }

    static void apply(OneStepShell.Host host, SurfaceControl surface,
                      SurfaceControl.Transaction transaction, Rect crop) {
        if (host.landscapeRequested) {
            // Fit the whole WM task frame into the pane. Its aspect ratio can differ
            // from the requested 16:9 frame; filling the pane would crop app content.
            float scale = Math.min(host.width / (float) Math.max(1, crop.width()),
                    host.height / (float) Math.max(1, crop.height()));
            transaction.setPosition(surface,
                    (host.width - crop.width() * scale) / 2f - crop.left * scale,
                    (host.height - crop.height() * scale) / 2f - crop.top * scale)
                    .setScale(surface, scale, scale);
            return;
        }
        float sx = host.width / (float) Math.max(1, crop.width());
        float sy = host.height / (float) Math.max(1, crop.height());
        transaction.setPosition(surface, -crop.left * sx, -crop.top * sy)
                .setScale(surface, sx, sy);
    }
}
