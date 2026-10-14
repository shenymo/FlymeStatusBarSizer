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

    static boolean rotatesPortraitBuffer(OneStepShell.Host host, Rect crop) {
        return host.fullscreenCompat
                && host.landscapeRequested
                && host.logicalBounds.width() > host.logicalBounds.height()
                && !host.actualBounds.isEmpty()
                && host.actualBounds.width() <= host.actualBounds.height()
                && !crop.isEmpty();
    }

    static Rect landscapeCrop(OneStepShell.Host host) {
        Rect actual = host.actualBounds;
        Rect crop = centerCrop(actual, host.width / (float) Math.max(1, host.height));
        crop.offset(-actual.left, -actual.top);
        return crop;
    }

    static void apply(OneStepShell.Host host, SurfaceControl surface,
                      SurfaceControl.Transaction transaction, Rect crop) throws Exception {
        if (rotatesPortraitBuffer(host, crop)) {
            // A portrait-compatible task may keep a portrait buffer while the
            // forced landscape request settles. Rotate the complete buffer into
            // the pane instead of cropping it to a horizontal strip.
            float scale = Math.max(host.width / (float) Math.max(1, crop.height()),
                    host.height / (float) Math.max(1, crop.width()));
            float rotatedWidth = crop.height() * scale;
            float rotatedHeight = crop.width() * scale;
            float rotatedX = (host.width + rotatedWidth) / 2f;
            float rotatedY = (host.height - rotatedHeight) / 2f;
            OneStepReflection.call(transaction, "setPosition",
                    new Class<?>[]{SurfaceControl.class, float.class, float.class},
                    surface, rotatedX, rotatedY);
            OneStepReflection.call(transaction, "setMatrix",
                    new Class<?>[]{SurfaceControl.class, float.class, float.class,
                            float.class, float.class},
                    surface, 0f, scale, -scale, 0f);
            return;
        }
        float sx = host.width / (float) Math.max(1, crop.width());
        float sy = host.height / (float) Math.max(1, crop.height());
        transaction.setPosition(surface, -crop.left * sx, -crop.top * sy)
                .setScale(surface, sx, sy);
    }

    private static Rect centerCrop(Rect source, float targetAspect) {
        Rect crop = new Rect(source);
        if (source.isEmpty() || targetAspect <= 0f) return crop;
        float sourceAspect = source.width() / (float) source.height();
        if (sourceAspect > targetAspect) {
            int width = Math.max(1, Math.min(source.width(),
                    Math.round(source.height() * targetAspect)));
            int left = source.left + (source.width() - width) / 2;
            crop.set(left, source.top, left + width, source.bottom);
        } else if (sourceAspect < targetAspect) {
            int height = Math.max(1, Math.min(source.height(),
                    Math.round(source.width() / targetAspect)));
            int top = source.top + (source.height() - height) / 2;
            crop.set(source.left, top, source.right, top + height);
        }
        return crop;
    }
}
