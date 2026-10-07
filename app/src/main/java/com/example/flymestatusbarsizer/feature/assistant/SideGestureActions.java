package com.example.flymestatusbarsizer.feature.assistant;

import com.example.flymestatusbarsizer.config.SettingsStore;

/** Keeps action availability and execution separate from edge gesture recognition. */
final class SideGestureActions {
    interface Action {
        boolean isReady();
        void execute(boolean fromLeft, Runnable onSuccess);
    }

    private SideGestureActions() { }

    static Action resolve(int action, Action globalAssistant) {
        switch (action) {
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT:
                return globalAssistant;
            default:
                return null;
        }
    }
}
