package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Bitmap;
import android.content.ComponentName;

/** A recent task's identity and its system-provided preview. Labels are accessibility-only. */
final class RecentTaskCard {
    final int taskId;
    final int userId;
    final Object token;
    final String description;
    final Bitmap preview;
    final ComponentName component;

    RecentTaskCard(int taskId, int userId, Object token, String description, Bitmap preview) {
        this(taskId, userId, token, description, preview, null);
    }

    RecentTaskCard(int taskId, int userId, Object token, String description, Bitmap preview,
            ComponentName component) {
        this.taskId = taskId;
        this.userId = userId;
        this.token = token;
        this.description = description;
        this.preview = preview;
        this.component = component;
    }

    boolean matches(TaskScaleTarget target) {
        return target != null && taskId == target.taskId && token != null && token.equals(target.token);
    }

    boolean sameTask(RecentTaskCard other) {
        return other != null && taskId == other.taskId && userId == other.userId
                && token != null && token.equals(other.token);
    }

    RecentTaskCard withPreview(Bitmap bitmap) {
        return new RecentTaskCard(taskId, userId, token, description, bitmap, component);
    }
}
