package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

/** One coordinate system for the task surface, input margins and moving previews. */
final class TaskScaleLayout {
    final float scale;
    final float offsetY;
    final Rect content;
    final Rect usable;
    final Rect available;
    final int cardWidth;

    TaskScaleLayout(TaskScaleTarget target, Rect usableBounds, float requestedScale, float imeHeight) {
        usable = new Rect(usableBounds);
        TaskScaleOverlay.intersect(usable, target.bounds);
        available = new Rect(usable);
        float bottom = target.bounds.bottom - Math.max(0f, imeHeight);
        float fitted = requestedScale;
        if (imeHeight > 0f) {
            bottom = Math.max(usable.top + 1, Math.min(bottom, usable.bottom));
            fitted = Math.min(requestedScale, (bottom - usable.top) / target.bounds.height());
            available.bottom = Math.min(available.bottom, Math.round(bottom));
        }
        scale = fitted;
        offsetY = bottom - target.bounds.bottom;
        content = target.scaledBounds(scale);
        content.offset(0, Math.round(offsetY));
        // If a tall keyboard requires extra scaling, shrink the left column by the same ratio.
        cardWidth = Math.round(target.bounds.width() * (1f - TaskScaleController.SCALE)
                * Math.min(1f, scale / TaskScaleController.SCALE));
    }

    Rect[] cards(Rect margin, int count, float aspect, int gap) {
        int width = Math.min(margin.width(), cardWidth);
        Rect[] positions = RecentTaskCardLayout.arrange(width, margin.height(), count, aspect, gap);
        for (Rect position : positions) position.offset(margin.width() - width, 0);
        return positions;
    }
}
