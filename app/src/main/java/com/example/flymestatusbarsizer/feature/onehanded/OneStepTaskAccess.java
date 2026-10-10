package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/** Task discovery and focus under SystemUI; rendering is owned by OneStepShell. */
final class OneStepTaskAccess {
    private final Context context;
    private final Object service;
    private final Method roots;
    private final Method launch;
    private final Method focusTask;
    private final OneStepRecentTasks recent;
    private Object taskListener;

    OneStepTaskAccess(Context context) throws ReflectiveOperationException {
        this.context = context;
        service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Class<?> tasks = Class.forName("android.app.IActivityTaskManager");
        roots = tasks.getMethod("getAllRootTaskInfos");
        launch = tasks.getMethod("startActivityFromRecents", int.class, Bundle.class);
        focusTask = tasks.getMethod("setFocusedTask", int.class);
        recent = new OneStepRecentTasks(context);
    }

    List<?> roots() throws ReflectiveOperationException {
        Object value = roots.invoke(service);
        if (!(value instanceof List)) throw new IllegalStateException("Root tasks unavailable");
        return (List<?>) value;
    }

    static int taskId(Object info) { return ReflectUtils.getIntField(info, "taskId", -1); }
    static int display(Object info) { return ReflectUtils.getIntField(info, "displayId", -1); }
    static Object token(Object info) {
        return ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "token"), "asBinder");
    }

    static RecentTaskCard runningCard(Object info) {
        Object component = ReflectUtils.getField(info, "topActivity");
        if (!(component instanceof ComponentName)) component = ReflectUtils.getField(info, "baseActivity");
        if (!(component instanceof ComponentName) || token(info) == null || taskId(info) < 0
                || OneStepActivityProtocol.isActivity((ComponentName) component)) return null;
        RecentTaskCard card = new RecentTaskCard(taskId(info), ReflectUtils.getIntField(info, "userId", -1),
                token(info), ((ComponentName) component).getPackageName(), (ComponentName) component);
        Object intent = ReflectUtils.getField(info, "baseIntent");
        String name = card.component.getClassName();
        String action = intent instanceof Intent ? ((Intent) intent).getAction() : null;
        card.temporary = (intent instanceof Intent
                && (((Intent) intent).getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0)
                || Intent.ACTION_CHOOSER.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)
                || Intent.ACTION_OPEN_DOCUMENT.equals(action) || Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)
                || Intent.ACTION_CREATE_DOCUMENT.equals(action)
                || name.contains("ResolverActivity") || name.contains("ChooserActivity")
                || name.contains("GrantPermissionsActivity");
        return card;
    }

    static boolean externalTaskAllowed(Object info) {
        RecentTaskCard card = runningCard(info);
        if (card == null || display(info) != 0 || OneStepShell.taskEnded(info)
                || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1) return false;
        Object window = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        int mode = ReflectUtils.invokeNoArgInt(window, "getWindowingMode", -1);
        String name = card.component.getClassName();
        // Authentication, calls and translucent system UI remain above the workspace.
        return ReflectUtils.invokeNoArgInt(window, "getActivityType", -1) == 1
                && (mode == 1 || mode == 6)
                && !ReflectUtils.getBooleanField(info, "isTopActivityTransparent", false)
                && !name.contains("InCallActivity") && !name.contains("ConfirmDeviceCredential")
                && !name.contains("Biometric") && !name.contains("GrantPermissionsActivity");
    }

    static boolean application(Object info) {
        Object config = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(config, "getActivityType", -1) == 1
                && ReflectUtils.invokeNoArgInt(config, "getWindowingMode", -1) == 1;
    }

    static boolean home(Object info) {
        Object config = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(config, "getActivityType", -1) == 2
                && ReflectUtils.invokeNoArgInt(config, "getWindowingMode", -1) == 1;
    }

    RecentTaskCard homeTask(int userId) throws ReflectiveOperationException {
        for (Object root : roots()) {
            if (display(root) != 0 || !home(root) || token(root) == null
                    || ReflectUtils.getIntField(root, "userId", -1) != userId
                    || ReflectUtils.getIntField(root, "parentTaskId", -2) != -1) continue;
            Object component = ReflectUtils.getField(root, "topActivity");
            if (!(component instanceof ComponentName)) component = ReflectUtils.getField(root, "baseActivity");
            if (component instanceof ComponentName
                    && OneStepLauncherBridge.LAUNCHER.equals(((ComponentName) component).getPackageName())) {
                return new RecentTaskCard(taskId(root), userId, token(root), "桌面", (ComponentName) component, true);
            }
        }
        throw new IllegalStateException("Flyme HOME task is unavailable");
    }

    RecentTaskCard focusedTask() throws ReflectiveOperationException {
        return focusedTask(roots());
    }

    private static RecentTaskCard focusedTask(List<?> rootTasks) {
        for (Object root : rootTasks) {
            if (display(root) == 0 && application(root)
                    && ReflectUtils.getBooleanField(root, "isFocused", false)) {
                return OneStepRecentTasks.candidate(root, ReflectUtils.getIntField(root, "userId", -1));
            }
        }
        return null;
    }

    List<RecentTaskCard> candidates() throws ReflectiveOperationException { return recent.recentTasks(); }

    Object recentTaskInfo(RecentTaskCard card) throws ReflectiveOperationException { return recent.taskInfo(card); }

    RecentTaskCard findCandidate(RecentTaskCard requested) throws ReflectiveOperationException {
        for (RecentTaskCard card : candidates()) {
            if (requested.sameTask(card)) return card;
        }
        return null;
    }

    void activate(RecentTaskCard requested) throws ReflectiveOperationException {
        if (findCandidate(requested) == null) throw new IllegalStateException("Selected task no longer exists");
        ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
        // Resume dormant recents behind the opaque host. Shell moves the task above
        // the host only after setting up its TaskView and translucent multiwindow state.
        OneStepReflection.call(options, "setAvoidMoveToFront");
        Object result = launch.invoke(service, requested.taskId, options.toBundle());
        if (!(result instanceof Number) || ((Number) result).intValue() < 0) {
            throw new IllegalStateException("Cannot resume recent task");
        }
    }

    int defaultFocusedTaskId() throws ReflectiveOperationException {
        for (Object root : roots()) {
            if (display(root) == 0 && ReflectUtils.getBooleanField(root, "isFocused", false)) return taskId(root);
        }
        return -1;
    }

    void focusTask(int id) throws ReflectiveOperationException { focusTask.invoke(service, id); }

    void startHome(int userId) throws ReflectiveOperationException {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        OneStepReflection.call(context, "startActivityAsUser",
                new Class<?>[]{Intent.class, Bundle.class, android.os.UserHandle.class}, intent, options.toBundle(),
                android.os.UserHandle.getUserHandleForUid(userId * 100000));
    }

    void returnToPage(RecentTaskCard requested) throws ReflectiveOperationException {
        boolean exists = false;
        for (Object info : roots()) if (requested.taskId == taskId(info) && requested.token.equals(token(info))
                && requested.userId == ReflectUtils.getIntField(info, "userId", -1)) exists = true;
        if (!exists) throw new IllegalStateException("The source task no longer exists");
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        OneStepReflection.call(options, "setLaunchTaskId", new Class<?>[]{int.class}, requested.taskId);
        // CLEAR_TOP returns to an exported source Activity in its existing task. This
        // follows same-task back semantics, rather than duplicating that task.
        Intent intent = new Intent().setComponent(requested.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        OneStepReflection.call(context, "startActivityAsUser",
                new Class<?>[]{Intent.class, Bundle.class, android.os.UserHandle.class}, intent, options.toBundle(),
                android.os.UserHandle.getUserHandleForUid(requested.userId * 100000));
    }

    void registerTaskChanges(Runnable callback) throws ReflectiveOperationException {
        if (taskListener != null) return;
        ClassLoader loader = context.getClassLoader();
        Class<?> listenerType = Class.forName(
                "com.android.systemui.shared.system.TaskStackChangeListener", false, loader);
        Class<?> listenersType = Class.forName(
                "com.android.systemui.shared.system.TaskStackChangeListeners", false, loader);
        Object listeners = listenersType.getMethod("getInstance").invoke(null);
        Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerType}, (proxy, method, args) -> {
            String name = method.getName();
            if ("equals".equals(name)) return proxy == args[0];
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "FlymeOneStepTaskListener";
            switch (name) {
                case "onTaskStackChanged":
                case "onTaskCreated":
                case "onTaskRemoved":
                case "onTaskMovedToFront":
                case "onTaskFocusChanged":
                case "onActivityRequestedOrientationChanged":
                case "onTaskDisplayChanged":
                case "onRecentTaskListUpdated":
                case "onTaskProfileLocked":
                case "onLockTaskModeChanged":
                    callback.run();
                    break;
                default:
                    break;
            }
            // Snapshot ownership stays with SystemUI; this listener only observes task changes.
            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
        });
        listenersType.getMethod("registerTaskStackListener", listenerType).invoke(listeners, listener);
        taskListener = listener;
    }
}
