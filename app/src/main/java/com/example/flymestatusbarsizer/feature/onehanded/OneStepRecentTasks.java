package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads eligible recent tasks through the same services as this ROM's SystemUI/Launcher. */
final class OneStepRecentTasks {
    private final Object wrapper;
    private final Object taskManager;
    private final Method currentUser;
    private final Method recentTasks;

    OneStepRecentTasks(Context context) throws ReflectiveOperationException {
        Class<?> wrapperClass = Class.forName("com.android.systemui.shared.system.ActivityManagerWrapper",
                false, context.getClassLoader());
        wrapper = wrapperClass.getMethod("getInstance").invoke(null);
        currentUser = wrapperClass.getMethod("getCurrentUserId");
        Class<?> manager = Class.forName("android.app.ActivityTaskManager");
        taskManager = manager.getMethod("getInstance").invoke(null);
        recentTasks = manager.getMethod("getRecentTasks", int.class, int.class, int.class);
    }

    List<RecentTaskCard> recentTasks() throws ReflectiveOperationException {
        int userId = ((Number) currentUser.invoke(wrapper)).intValue();
        Object result = recentTasks.invoke(taskManager, 32, ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId);
        List<RecentTaskCard> cards = new ArrayList<>();
        if (!(result instanceof List)) return cards;
        for (Object info : (List<?>) result) {
            RecentTaskCard card = candidate(info, userId);
            if (card != null) cards.add(card);
        }
        return cards;
    }

    static RecentTaskCard candidate(Object info, int userId) {
        Object config = ReflectUtils.getField(info, "configuration");
        Object window = ReflectUtils.getField(config, "windowConfiguration");
        Object bounds = ReflectUtils.invokeNoArg(window, "getBounds");
        Object token = ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "token"), "asBinder");
        int taskId = ReflectUtils.getIntField(info, "taskId", -1);
        if (taskId < 0 || token == null || !(bounds instanceof Rect) || ((Rect) bounds).isEmpty()
                || ((Rect) bounds).height() <= ((Rect) bounds).width()
                || ReflectUtils.getIntField(info, "userId", -1) != userId
                || ReflectUtils.getIntField(info, "displayId", -1) != 0
                || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1
                || ReflectUtils.invokeNoArgInt(window, "getWindowingMode", -1) != 1
                || ReflectUtils.invokeNoArgInt(window, "getActivityType", -1) != 1) return null;
        Object intent = ReflectUtils.getField(info, "baseIntent");
        if (intent instanceof Intent && (((Intent) intent).getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) {
            return null;
        }
        Object component = ReflectUtils.getField(info, "topActivity");
        if (!(component instanceof ComponentName) && intent instanceof Intent) component = ((Intent) intent).getComponent();
        if (!(component instanceof ComponentName)) return null;
        Object label = ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "taskDescription"), "getLabel");
        String description = label instanceof String && !((String) label).isEmpty()
                ? (String) label : ((ComponentName) component).getPackageName();
        return new RecentTaskCard(taskId, userId, token, description, (ComponentName) component);
    }
}
