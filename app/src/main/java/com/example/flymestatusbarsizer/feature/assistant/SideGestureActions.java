package com.example.flymestatusbarsizer.feature.assistant;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.feature.tasks.TaskProcessStopper;
import com.example.flymestatusbarsizer.feature.windowmode.WindowModeSideGestureHooks;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps action availability and execution separate from edge gesture recognition. */
public final class SideGestureActions {
    private static final ExecutorService TASK_WORKER = Executors.newSingleThreadExecutor();
    public static final String ACTION_CORNER_GESTURE =
            "com.fiyme.statusbarsizer.action.CORNER_GESTURE";
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
            case SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN:
            case SettingsStore.SIDE_GESTURE_ACTION_END_CURRENT_TASK:
                if (context == null) return null;
                return new Action() {
                    @Override public boolean isReady() {
                        return true;
                    }

                    @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                        try {
                            if (action == SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN) {
                                PowerManager power = context.getSystemService(PowerManager.class);
                                PowerManager.class.getMethod("goToSleep", long.class)
                                        .invoke(power, SystemClock.uptimeMillis());
                            } else {
                                ActivityManager manager = context.getSystemService(ActivityManager.class);
                                List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
                                if (tasks.isEmpty()) return;
                                ActivityManager.RunningTaskInfo task = tasks.get(0);
                                if (task.taskId < 0 || task.topActivity == null
                                        || ReflectUtils.getIntField(task, "displayId", -1) != 0
                                        || ReflectUtils.invokeNoArgInt(task, "getActivityType", -1) != 1
                                        || "com.android.systemui".equals(
                                                task.topActivity.getPackageName())) return;
                                int userId = ReflectUtils.getIntField(task, "userId", -1);
                                if (userId < 0) return;
                                Handler main = new Handler(Looper.getMainLooper());
                                TASK_WORKER.execute(() -> {
                                    try {
                                        TaskProcessStopper.remove(context, task.taskId,
                                                task.topActivity.getPackageName(), userId);
                                        if (onSuccess != null) main.post(onSuccess);
                                    } catch (Exception error) {
                                        AssistantHooks.warn("Cannot end current task", error);
                                        main.post(() -> Toast.makeText(context, "结束任务失败", Toast.LENGTH_SHORT).show());
                                    }
                                });
                                return;
                            }
                            if (onSuccess != null) onSuccess.run();
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            AssistantHooks.warn("Cannot execute gesture action: " + label(action), error);
                        }
                    }
                };
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
            case SettingsStore.SIDE_GESTURE_ACTION_WINDOWMODE_PANEL:
                return context == null ? null : WindowModeSideGestureHooks.action(context);
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP:
            case SettingsStore.SIDE_GESTURE_ACTION_WELT_WINDOW:
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

    public static String label(int action) {
        switch (action) {
            case SettingsStore.SIDE_GESTURE_ACTION_BACK: return "返回";
            case SettingsStore.SIDE_GESTURE_ACTION_HOME: return "返回桌面";
            case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return "最近任务";
            case SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN: return "锁屏";
            case SettingsStore.SIDE_GESTURE_ACTION_END_CURRENT_TASK: return "结束当前任务（App）";
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return "全局负一屏";
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return "多应用工作台";
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER: return "任务切换器";
            case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS: return "快捷工具";
            case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER: return "应用启动器";
            case SettingsStore.SIDE_GESTURE_ACTION_WINDOWMODE_PANEL: return "小窗选择面板";
            case SettingsStore.SIDE_GESTURE_ACTION_WELT_WINDOW: return "贴边";
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
            if (target != null) return target.customName.isEmpty()
                    ? "打开：" + SideGestureTargetStore.shortLabel(target) : target.customName;
        }
        return label(action);
    }
}
