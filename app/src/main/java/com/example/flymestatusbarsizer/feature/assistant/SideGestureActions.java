package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;
import com.example.flymestatusbarsizer.config.ModuleConfig;

/** Keeps action availability and execution separate from edge gesture recognition. */
public final class SideGestureActions {
    public interface Action {
        boolean isReady();
        void execute(boolean fromLeft, Runnable onSuccess);
        default void execute(boolean fromLeft, float anchorY, Runnable onSuccess) {
            execute(fromLeft, onSuccess);
        }
    }

    private SideGestureActions() { }

    static Action resolve(int action, Action globalAssistant) {
        return resolve(action, globalAssistant,
                com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale) {
        return resolve(action, globalAssistant, taskScale, null);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale, Action taskSwitcher) {
        return resolve(action, globalAssistant, taskScale, taskSwitcher, null);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale, Action taskSwitcher,
            Action quickTools) {
        return resolve(action, globalAssistant, taskScale, taskSwitcher, quickTools, null);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale, Action taskSwitcher,
            Action quickTools, Action appLauncher) {
        return resolve(action, globalAssistant, taskScale, taskSwitcher, quickTools, appLauncher, null);
    }

    static Action resolve(int action, Action globalAssistant, Action taskScale, Action taskSwitcher,
            Action quickTools, Action appLauncher, Context context) {
        switch (action) {
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT:
                return globalAssistant;
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE:
                return taskScale;
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER:
                return taskSwitcher;
            case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS:
                return quickTools;
            case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER:
                return appLauncher;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP:
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY:
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI:
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL:
                return context == null ? null : SideGestureLaunchAction.action(context, action);
            default:
                if (context != null && SideGestureTargetStore.isDynamicAction(action)) {
                    return SideGestureLaunchAction.action(context, action);
                }
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
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER: return "任务切换器";
            case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS: return "快捷工具";
            case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER: return "应用启动器";
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return "打开指定应用";
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return "打开 Activity";
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return "打开 Intent URI";
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL: return "打开 Scheme URL";
            default: return "无动作";
        }
    }

    static String label(int action, Context context) {
        if (context != null && SideGestureTargetStore.isDynamicAction(action)) {
            SideGestureTargetStore.Target target = SideGestureTargetStore.find(
                    ModuleConfig.load(context).sideGestureTargetsJson, action);
            if (target != null) return "打开：" + target.label;
        }
        return label(action);
    }
}
