package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;

/** A recent task's identity and icon source. Labels are accessibility-only. */
final class RecentTaskCard {
    final int taskId;
    final int userId;
    final Object token;
    final String description;
    final ComponentName component;
    final boolean home;
    boolean temporary;

    RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component) {
        this(taskId, userId, token, description, component, false);
    }

    RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component, boolean home) {
        this.taskId = taskId;
        this.userId = userId;
        this.token = token;
        this.description = description;
        this.component = component;
        this.home = home;
    }

    boolean sameTask(RecentTaskCard other) {
        return other != null && taskId == other.taskId && userId == other.userId
                && token != null && token.equals(other.token);
    }
}
