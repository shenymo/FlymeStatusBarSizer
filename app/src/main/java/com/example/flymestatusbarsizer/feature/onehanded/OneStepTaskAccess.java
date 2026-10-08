package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
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

    static boolean application(Object info) {
        Object config = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(config, "getActivityType", -1) == 1
                && ReflectUtils.invokeNoArgInt(config, "getWindowingMode", -1) == 1;
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

    RecentTaskCard findCandidate(RecentTaskCard requested) throws ReflectiveOperationException {
        for (RecentTaskCard card : candidates()) {
            if (requested.sameTask(card)) return card;
        }
        return null;
    }

    void activate(RecentTaskCard requested) throws ReflectiveOperationException {
        if (findCandidate(requested) == null) throw new IllegalStateException("Selected task no longer exists");
        ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
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
