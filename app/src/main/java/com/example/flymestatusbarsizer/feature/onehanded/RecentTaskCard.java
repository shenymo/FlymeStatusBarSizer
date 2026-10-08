package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;

/** A recent task's identity and icon source. Labels are accessibility-only. */
final class RecentTaskCard {
    final int taskId;
    final int userId;
    final Object token;
    final String description;
    final ComponentName component;

    RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component) {
        this.taskId = taskId;
        this.userId = userId;
        this.token = token;
        this.description = description;
        this.component = component;
    }

    boolean sameTask(RecentTaskCard other) {
        return other != null && taskId == other.taskId && userId == other.userId
                && token != null && token.equals(other.token);
    }
}
