package com.example.flymestatusbarsizer.feature.assistant;

import com.example.flymestatusbarsizer.config.SettingsStore;

/** Keeps action availability and execution separate from edge gesture recognition. */
public final class SideGestureActions {
    public interface Action {
        boolean isReady();
        void execute(boolean fromLeft, Runnable onSuccess);
    }

    private SideGestureActions() { }

    static Action resolve(int action, Action globalAssistant) {
        return resolve(action, globalAssistant,
                com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale) {
        switch (action) {
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT:
                return globalAssistant;
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE:
                return taskScale;
            default:
                return null;
        }
    }
}
