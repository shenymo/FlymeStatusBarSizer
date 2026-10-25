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

    static String label(int action) {
        switch (action) {
            case SettingsStore.SIDE_GESTURE_ACTION_BACK: return "返回";
            case SettingsStore.SIDE_GESTURE_ACTION_HOME: return "主页";
            case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return "后台多任务界面";
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return "全局负一屏";
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return "多应用工作台";
            default: return "无动作";
        }
    }
}
