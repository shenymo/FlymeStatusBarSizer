package com.example.flymestatusbarsizer.feature.tasks;

import android.content.ComponentName;

/** A recent task's identity and icon source. Labels are accessibility-only. */
public final class RecentTaskCard {
    public final int taskId;
    public final int userId;
    public final Object token;
    public final String description;
    public final ComponentName component;
    public final String lockPackageName;
    public final boolean home;
    public boolean temporary;

    public RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component) {
        this(taskId, userId, token, description, component, component.getPackageName(), false);
    }

    public RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component, boolean home) {
        this(taskId, userId, token, description, component, component.getPackageName(), home);
    }

    public RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component,
            String lockPackageName) {
        this(taskId, userId, token, description, component, lockPackageName, false);
    }

    private RecentTaskCard(int taskId, int userId, Object token, String description, ComponentName component,
            String lockPackageName, boolean home) {
        this.taskId = taskId;
        this.userId = userId;
        this.token = token;
        this.description = description;
        this.component = component;
        this.lockPackageName = lockPackageName;
        this.home = home;
    }

    public boolean sameTask(RecentTaskCard other) {
        return other != null && taskId == other.taskId && userId == other.userId
                && token != null && token.equals(other.token);
    }
}
