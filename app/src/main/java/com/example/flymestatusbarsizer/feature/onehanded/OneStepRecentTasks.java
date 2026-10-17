package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads eligible recent tasks through the same services as this ROM's SystemUI/Launcher. */
final class OneStepRecentTasks {
    static final int CLONE_USER_ID = 999;
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
        List<RecentTaskCard> cards = new ArrayList<>();
        addRecentTasks(cards, userId, false);
        if (userId != CLONE_USER_ID) addRecentTasks(cards, CLONE_USER_ID, true);
        return cards;
    }

    private void addRecentTasks(List<RecentTaskCard> cards, int userId, boolean optional)
            throws ReflectiveOperationException {
        Object result;
        try {
            result = recentTasks.invoke(taskManager, 32, ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId);
        } catch (ReflectiveOperationException error) {
            if (optional) return;
            throw error;
        }
        if (!(result instanceof List)) return;
        for (Object info : (List<?>) result) {
            RecentTaskCard card = candidate(info, userId);
            if (card == null) continue;
            boolean duplicate = false;
            for (RecentTaskCard existing : cards) {
                if (existing.sameTask(card)) { duplicate = true; break; }
            }
            if (!duplicate) cards.add(card);
        }
    }

    Object taskInfo(RecentTaskCard requested) throws ReflectiveOperationException {
        Object result = recentTasks.invoke(taskManager, 32, ActivityManager.RECENT_IGNORE_UNAVAILABLE, requested.userId);
        if (result instanceof List) for (Object info : (List<?>) result) {
            RecentTaskCard card = candidate(info, requested.userId);
            if (requested.sameTask(card)) return info;
        }
        return null;
    }

    static RecentTaskCard candidate(Object info, int userId) {
        Object config = ReflectUtils.getField(info, "configuration");
        Object window = ReflectUtils.getField(config, "windowConfiguration");
        Object bounds = ReflectUtils.invokeNoArg(window, "getBounds");
        Object token = ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "token"), "asBinder");
        int taskId = ReflectUtils.getIntField(info, "taskId", -1);
        int taskUserId = ReflectUtils.getIntField(info, "userId", -1);
        if (taskId < 0 || token == null || !(bounds instanceof Rect) || ((Rect) bounds).isEmpty()
                || (taskUserId != userId && taskUserId != CLONE_USER_ID)
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
        if (OneStepActivityProtocol.isActivity((ComponentName) component)) return null;
        String packageName = ((ComponentName) component).getPackageName();
        if ("com.android.systemui".equals(packageName) || BuildConfig.APPLICATION_ID.equals(packageName)) return null;
        Object label = ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "taskDescription"), "getLabel");
        String description = label instanceof String && !((String) label).isEmpty()
                ? (String) label : ((ComponentName) component).getPackageName();
        return new RecentTaskCard(taskId, taskUserId, token, description, (ComponentName) component);
    }
}
