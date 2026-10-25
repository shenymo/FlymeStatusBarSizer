package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.UserHandle;

import com.example.flymestatusbarsizer.BuildConfig;

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
    private final Method removeTask;
    private final Method focusTask;
    private final OneStepRecentTasks recent;
    private Object taskListener;
    private Object windowManagerExt;
    private Method isWindowMode;
    private Method setStartWindowMode;
    private Method makeCustomTaskAnimation;

    OneStepTaskAccess(Context context) throws ReflectiveOperationException {
        this.context = context;
        service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Class<?> tasks = Class.forName("android.app.IActivityTaskManager");
        roots = tasks.getMethod("getAllRootTaskInfos");
        launch = tasks.getMethod("startActivityFromRecents", int.class, Bundle.class);
        removeTask = tasks.getMethod("removeTask", int.class);
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
        // The workspace itself is hosted by SystemUI. Re-entering the SystemUI
        // root task would make Shell and the workspace own the same task graph.
        if ("com.android.systemui".equals(card.component.getPackageName())
                || BuildConfig.APPLICATION_ID.equals(card.component.getPackageName())) return false;
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
        ComponentName defaultHome = defaultHomeComponent(userId);
        for (Object root : roots()) {
            if (display(root) != 0 || !home(root) || token(root) == null
                    || ReflectUtils.getIntField(root, "userId", -1) != userId
                    || ReflectUtils.getIntField(root, "parentTaskId", -2) != -1) continue;
            Object component = ReflectUtils.getField(root, "topActivity");
            if (!(component instanceof ComponentName)) component = ReflectUtils.getField(root, "baseActivity");
            if (component instanceof ComponentName
                    && (defaultHome == null
                    || defaultHome.getPackageName().equals(((ComponentName) component).getPackageName()))) {
                return new RecentTaskCard(taskId(root), userId, token(root), "桌面", (ComponentName) component, true);
            }
        }
        throw new IllegalStateException("Default HOME task is unavailable");
    }

    private ComponentName defaultHomeComponent(int userId) {
        try {
            Context userContext = (Context) OneStepReflection.call(context, "createContextAsUser",
                    new Class<?>[]{UserHandle.class, int.class}, userHandle(userId), 0);
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo home = userContext.getPackageManager().resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (home != null && home.activityInfo != null) {
                return new ComponentName(home.activityInfo.packageName, home.activityInfo.name);
            }
        } catch (Exception ignored) { }
        return null;
    }

    RecentTaskCard focusedTask() throws ReflectiveOperationException {
        return focusedTask(roots());
    }

    private static RecentTaskCard focusedTask(List<?> rootTasks) {
        for (Object root : rootTasks) {
            if (display(root) == 0 && application(root)
                    && ReflectUtils.getBooleanField(root, "isFocused", false)
                    && externalTaskAllowed(root)) {
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

    void openMiniWindow(RecentTaskCard requested) throws ReflectiveOperationException {
        RecentTaskCard current = findCandidate(requested);
        if (current == null || !(current.token instanceof IBinder)) {
            throw new IllegalStateException("Selected task no longer exists");
        }
        initWindowManagerExt();
        int previousFocusedTaskId = defaultFocusedTaskId();
        setStartWindowMode.invoke(windowManagerExt, current.component.getPackageName());
        Bundle options = buildSmallWindowTaskOptions();
        Object launchResult = launch.invoke(service, current.taskId, options);
        if (!(launchResult instanceof Number) || ((Number) launchResult).intValue() < 0) {
            throw new IllegalStateException("Cannot open selected task in mini window");
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            Object result = isWindowMode.invoke(windowManagerExt, 0, current.taskId, 11, null);
            if (Boolean.TRUE.equals(result)) return;
            SystemClock.sleep(100L);
        }
        if (previousFocusedTaskId >= 0) restoreFocusedTask(previousFocusedTaskId);
        throw new IllegalStateException("Flyme mini window was not entered");
    }

    private Bundle buildSmallWindowTaskOptions() throws ReflectiveOperationException {
        ActivityOptions taskAnimation = null;
        if (makeCustomTaskAnimation == null) {
            for (Method method : ActivityOptions.class.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("makeCustomTaskAnimation".equals(method.getName()) && parameters.length == 6
                        && parameters[0] == Context.class && parameters[1] == int.class
                        && parameters[2] == int.class && parameters[3] == Handler.class) {
                    method.setAccessible(true);
                    makeCustomTaskAnimation = method;
                    break;
                }
            }
        }
        if (makeCustomTaskAnimation != null) {
            Object result = makeCustomTaskAnimation.invoke(null, context, 0, 0, null, null, null);
            if (result instanceof ActivityOptions) taskAnimation = (ActivityOptions) result;
        }
        Bundle options = taskAnimation != null
                ? taskAnimation.toBundle() : ActivityOptions.makeBasic().toBundle();
        options.putBoolean("start_windowmode", true);
        return options;
    }

    private void initWindowManagerExt() throws ReflectiveOperationException {
        if (windowManagerExt != null) return;
        Class<?> type = Class.forName("flyme.view.WindowManagerExt", false, context.getClassLoader());
        windowManagerExt = type.getMethod("getInstance", Context.class).invoke(null, context);
        isWindowMode = type.getMethod("isWindowMode", int.class, int.class, int.class, IBinder.class);
        setStartWindowMode = type.getMethod("setStartWindowMode", String.class);
    }

    void remove(RecentTaskCard requested) throws ReflectiveOperationException {
        Object result = removeTask.invoke(service, requested.taskId);
        if (Boolean.FALSE.equals(result)) throw new IllegalStateException("Cannot remove recent task");
    }

    int defaultFocusedTaskId() throws ReflectiveOperationException {
        for (Object root : roots()) {
            if (display(root) == 0 && ReflectUtils.getBooleanField(root, "isFocused", false)) return taskId(root);
        }
        return -1;
    }

    private void restoreFocusedTask(int id) {
        try { focusTask.invoke(service, id); }
        catch (ReflectiveOperationException ignored) { }
    }

    void focusTask(int id) throws ReflectiveOperationException { focusTask.invoke(service, id); }

    void startHome(int userId) throws ReflectiveOperationException {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Context userContext = (Context) OneStepReflection.call(context, "createContextAsUser",
                new Class<?>[]{UserHandle.class, int.class}, userHandle(userId), 0);
        ResolveInfo home = userContext.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (home != null && home.activityInfo != null) {
            intent.setComponent(new ComponentName(home.activityInfo.packageName, home.activityInfo.name));
        }
        OneStepReflection.call(context, "startActivityAsUser",
                new Class<?>[]{Intent.class, Bundle.class, android.os.UserHandle.class}, intent, options.toBundle(),
                userHandle(userId));
    }

    private static UserHandle userHandle(int userId) {
        return UserHandle.getUserHandleForUid(userId * 100000);
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
