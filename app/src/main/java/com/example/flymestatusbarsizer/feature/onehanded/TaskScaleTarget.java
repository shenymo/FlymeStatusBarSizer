package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Point;
import android.graphics.Rect;

/** An immutable snapshot. A task id alone is not sufficient after a task is destroyed. */
final class TaskScaleTarget {
    final int taskId;
    final Object token;
    final Object surface;
    final Rect bounds;
    final Point position;
    final boolean eligible;

    TaskScaleTarget(int taskId, Object token, Object surface, Rect bounds, Point position,
            boolean eligible) {
        this.taskId = taskId;
        this.token = token;
        this.surface = surface;
        this.bounds = new Rect(bounds);
        this.position = new Point(position);
        this.eligible = eligible;
    }

    boolean sameTask(TaskScaleTarget other) {
        return other != null && taskId == other.taskId && token != null && token.equals(other.token);
    }

    Rect scaledBounds(float scale) {
        return new Rect(bounds.right - Math.round(bounds.width() * scale),
                bounds.bottom - Math.round(bounds.height() * scale), bounds.right, bounds.bottom);
    }
}
