package com.example.flymestatusbarsizer.feature.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class LauncherRecentsActivityHooks {
    private static final String OVERVIEW_COMPONENT_OBSERVER_CLASS =
            "com.android.quickstep.OverviewComponentObserver";
    private static final String RECENTS_ACTIVITY_CLASS = "com.android.quickstep.RecentsActivity";
    private static final String FALLBACK_RECENTS_VIEW_CLASS =
            "com.android.quickstep.fallback.FallbackRecentsView";
    private static final String RECENTS_MODEL_CLASS = "com.android.quickstep.RecentsModel";
    private static final String TASK_ICON_CALLBACK_CLASS =
            "com.android.quickstep.TaskIconCache$GetTaskIconCallback";
    private static final Class<?>[] LIST_ARG = new Class[]{List.class};
    private static final Class<?>[] CONTEXT_ARG = new Class[]{Context.class};
    private static final Class<?>[] CONSUMER_ARG = new Class[]{Consumer.class};
    private static final Class<?>[] DISMISS_TASK_ARGS =
            new Class[]{int.class, boolean.class, boolean.class};
    private static final WeakHashMap<Activity, RecentsActivityRouteController> CONTROLLERS =
            new WeakHashMap<>();

    private LauncherRecentsActivityHooks() {
    }

    static void installHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        hookOverviewComponentObserverTargets(module, loader);
        hookRecentsActivitySetupViews(module, loader);
        hookRecentsActivityOnEnterAnimationComplete(module, loader);
        hookRecentsActivityOnDestroy(module, loader);
        hookFallbackRecentsViewLoadPlan(module, loader);
    }

    static void refreshTrackedViews() {
        ArrayList<RecentsActivityRouteController> controllers;
        synchronized (CONTROLLERS) {
            controllers = new ArrayList<>(CONTROLLERS.values());
        }
        for (RecentsActivityRouteController controller : controllers) {
            if (controller != null) {
                controller.refreshFromConfig();
            }
        }
    }

    private static void hookOverviewComponentObserverTargets(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(OVERVIEW_COMPONENT_OBSERVER_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("updateOverviewTargets");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (!FlymeStatusBarSizer.loadLauncherRecentsConfig(null)
                        .launcherRecentsActivityIosEnabled) {
                    return chain.proceed();
                }
                Object observer = chain.getThisObject();
                boolean originalHomeDisabled =
                        LauncherRecentsCompat.readBooleanField(observer, "mIsHomeDisabled", false);
                LauncherRecentsCompat.setBooleanField(observer, "mIsHomeDisabled", true);
                try {
                    Object result = chain.proceed();
                    LauncherRecentsCompat.setBooleanField(observer, "mIsDefaultHome", false);
                    return result;
                } finally {
                    LauncherRecentsCompat.setBooleanField(
                            observer,
                            "mIsHomeDisabled",
                            originalHomeDisabled);
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook OverviewComponentObserver.updateOverviewTargets",
                    t);
        }
    }

    private static void hookRecentsActivitySetupViews(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_ACTIVITY_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("setupViews");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object activityObject = chain.getThisObject();
                if (activityObject instanceof Activity) {
                    RecentsActivityRouteController controller =
                            getOrCreateController((Activity) activityObject, loader);
                    controller.onViewsReady();
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsActivity.setupViews",
                    t);
        }
    }

    private static void hookRecentsActivityOnEnterAnimationComplete(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_ACTIVITY_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onEnterAnimationComplete");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object activityObject = chain.getThisObject();
                if (activityObject instanceof Activity) {
                    RecentsActivityRouteController controller =
                            getOrCreateController((Activity) activityObject, loader);
                    controller.refreshVisuals();
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsActivity.onEnterAnimationComplete",
                    t);
        }
    }

    private static void hookRecentsActivityOnDestroy(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(RECENTS_ACTIVITY_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("onDestroy");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object activityObject = chain.getThisObject();
                try {
                    return chain.proceed();
                } finally {
                    if (activityObject instanceof Activity) {
                        removeController((Activity) activityObject);
                    }
                }
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook RecentsActivity.onDestroy",
                    t);
        }
    }

    private static void hookFallbackRecentsViewLoadPlan(
            FlymeStatusBarSizer module,
            ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(FALLBACK_RECENTS_VIEW_CLASS, false, loader);
            Method method = clazz.getDeclaredMethod("applyLoadPlan", List.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (!FlymeStatusBarSizer.loadLauncherRecentsConfig(null)
                        .launcherRecentsActivityIosEnabled) {
                    return result;
                }
                Object recentsViewObject = chain.getThisObject();
                if (!(recentsViewObject instanceof View)) {
                    return result;
                }
                Activity activity = findOwningActivity((View) recentsViewObject);
                if (activity == null) {
                    return result;
                }
                RecentsActivityRouteController controller = getOrCreateController(activity, loader);
                Object argument = chain.getArg(0);
                controller.onFallbackLoadPlan(argument instanceof List ? (List<?>) argument : null);
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to hook FallbackRecentsView.applyLoadPlan",
                    t);
        }
    }

    private static Activity findOwningActivity(View view) {
        Context context = view != null ? view.getContext() : null;
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static RecentsActivityRouteController getOrCreateController(
            Activity activity,
            ClassLoader loader) {
        synchronized (CONTROLLERS) {
            RecentsActivityRouteController controller = CONTROLLERS.get(activity);
            if (controller == null) {
                controller = new RecentsActivityRouteController(activity, loader);
                CONTROLLERS.put(activity, controller);
            }
            return controller;
        }
    }

    private static void removeController(Activity activity) {
        synchronized (CONTROLLERS) {
            CONTROLLERS.remove(activity);
        }
    }

    private interface IconReceiver {
        void onIcon(Drawable icon, String contentDescription, String title);
    }

    private static final class RecentsActivityRouteController {
        private final Activity activity;
        private final ClassLoader loader;
        private final Consumer<List<?>> taskConsumer;
        private View fallbackRecentsView;
        private View actionsView;
        private ViewGroup overlayHost;
        private LauncherRecentsActivityOverlayView overlayView;
        private List<?> latestGroupTasks = Collections.emptyList();
        private int preferredTaskId = -1;
        private int generation;

        RecentsActivityRouteController(Activity activity, ClassLoader loader) {
            this.activity = activity;
            this.loader = loader;
            this.taskConsumer = this::onTasksLoaded;
        }

        void onViewsReady() {
            captureViews();
            refreshFromConfig();
            requestTasksFromModel();
        }

        void onFallbackLoadPlan(List<?> groupTasks) {
            captureViews();
            if (groupTasks != null) {
                latestGroupTasks = new ArrayList<>(groupTasks);
                submitTasks(groupTasks, false);
            }
        }

        void refreshFromConfig() {
            if (!isActivityAlive()) {
                return;
            }
            captureViews();
            if (!FlymeStatusBarSizer.loadLauncherRecentsConfig(activity)
                    .launcherRecentsActivityIosEnabled) {
                restoreStockOverview();
                return;
            }
            ensureOverlay();
            if (overlayView == null || fallbackRecentsView == null) {
                return;
            }
            fallbackRecentsView.setAlpha(0f);
            fallbackRecentsView.setClickable(false);
            fallbackRecentsView.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (actionsView != null) {
                actionsView.setVisibility(View.GONE);
            }
            overlayView.setVisibility(View.VISIBLE);
            if (!latestGroupTasks.isEmpty()) {
                submitTasks(latestGroupTasks, false);
            } else {
                requestTasksFromModel();
            }
        }

        void refreshVisuals() {
            if (!isActivityRouteEnabled() || latestGroupTasks.isEmpty()) {
                return;
            }
            submitTasks(latestGroupTasks, true);
        }

        private void restoreStockOverview() {
            if (overlayView != null) {
                overlayView.setVisibility(View.GONE);
            }
            if (fallbackRecentsView != null) {
                fallbackRecentsView.setAlpha(1f);
                fallbackRecentsView.setClickable(true);
                fallbackRecentsView.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            if (actionsView != null) {
                actionsView.setVisibility(View.VISIBLE);
            }
        }

        private boolean isActivityRouteEnabled() {
            return FlymeStatusBarSizer.loadLauncherRecentsConfig(activity)
                    .launcherRecentsActivityIosEnabled;
        }

        private void captureViews() {
            Object overviewPanel =
                    LauncherRecentsCompat.invokeCompat(activity, "getOverviewPanel");
            if (overviewPanel instanceof View) {
                fallbackRecentsView = (View) overviewPanel;
            }
            Object actions = FlymeStatusBarSizer.getFieldCompat(activity, "mActionsView");
            if (actions instanceof View) {
                actionsView = (View) actions;
            }
            if (fallbackRecentsView != null) {
                ViewParent parent = fallbackRecentsView.getParent();
                if (parent instanceof ViewGroup) {
                    overlayHost = (ViewGroup) parent;
                }
            }
            if (overlayHost == null) {
                View root = activity.findViewById(android.R.id.content);
                if (root instanceof ViewGroup) {
                    overlayHost = (ViewGroup) root;
                }
            }
        }

        private void ensureOverlay() {
            if (overlayHost == null) {
                return;
            }
            if (overlayView == null) {
                overlayView = new LauncherRecentsActivityOverlayView(
                        activity,
                        new LauncherRecentsActivityOverlayView.Callbacks() {
                            @Override
                            public void onHomeRequested() {
                                startHome();
                            }

                            @Override
                            public void onTaskActivated(int taskId) {
                                preferredTaskId = taskId;
                                syncFallbackToTask(taskId);
                                if (overlayView != null) {
                                    overlayView.setActiveTask(taskId, true);
                                }
                            }

                            @Override
                            public void onTaskLaunchRequested(int taskId) {
                                launchTask(taskId);
                            }

                            @Override
                            public void onTaskDismissRequested(int taskId) {
                                dismissTask(taskId);
                            }
                        });
            }
            if (overlayView.getParent() != overlayHost) {
                if (overlayView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlayView.getParent()).removeView(overlayView);
                }
                overlayHost.addView(
                        overlayView,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        private void startHome() {
            if (fallbackRecentsView == null) {
                return;
            }
            LauncherRecentsCompat.invokeCompat(
                    fallbackRecentsView,
                    "startHome",
                    LauncherRecentsCompat.NO_ARGS);
        }

        private void launchTask(int taskId) {
            preferredTaskId = taskId;
            if (!syncFallbackToTask(taskId)) {
                requestTasksFromModel();
                return;
            }
            View taskView = resolveTaskView(taskId);
            if (taskView != null) {
                taskView.performClick();
            }
        }

        private void dismissTask(int taskId) {
            preferredTaskId = taskId;
            if (fallbackRecentsView == null) {
                return;
            }
            LauncherRecentsCompat.invokeCompat(
                    fallbackRecentsView,
                    "dismissTask",
                    DISMISS_TASK_ARGS,
                    taskId,
                    true,
                    true);
            if (overlayView != null) {
                overlayView.removeTask(taskId);
            }
            fallbackRecentsView.post(this::requestTasksFromModel);
        }

        private boolean syncFallbackToTask(int taskId) {
            View taskView = resolveTaskView(taskId);
            if (fallbackRecentsView == null || taskView == null) {
                return false;
            }
            if (fallbackRecentsView instanceof ViewGroup) {
                int index = ((ViewGroup) fallbackRecentsView).indexOfChild(taskView);
                if (index >= 0) {
                    if (!LauncherRecentsCompat.invokeMethodReflectively(
                            fallbackRecentsView,
                            "snapToPage",
                            LauncherRecentsCompat.INT_ARG,
                            index)) {
                        LauncherRecentsCompat.invokeMethodReflectively(
                                fallbackRecentsView,
                                "setCurrentPage",
                                LauncherRecentsCompat.INT_ARG,
                                index);
                    }
                }
            }
            taskView.requestFocus();
            return true;
        }

        private View resolveTaskView(int taskId) {
            if (fallbackRecentsView == null || taskId == -1) {
                return null;
            }
            Object taskView = LauncherRecentsCompat.invokeCompat(
                    fallbackRecentsView,
                    "getTaskViewByTaskId",
                    LauncherRecentsCompat.INT_ARG,
                    taskId);
            return taskView instanceof View ? (View) taskView : null;
        }

        private void requestTasksFromModel() {
            if (!isActivityRouteEnabled()) {
                return;
            }
            Object recentsModel = resolveRecentsModel();
            if (recentsModel == null) {
                if (overlayView != null && latestGroupTasks.isEmpty()) {
                    overlayView.setCards(Collections.emptyList(), -1);
                }
                return;
            }
            LauncherRecentsCompat.invokeCompat(
                    recentsModel,
                    "getTasks",
                    CONSUMER_ARG,
                    taskConsumer);
        }

        private void onTasksLoaded(List<?> groupTasks) {
            if (!isActivityAlive() || !isActivityRouteEnabled()) {
                return;
            }
            latestGroupTasks = groupTasks == null
                    ? Collections.emptyList()
                    : new ArrayList<>(groupTasks);
            submitTasks(groupTasks, false);
        }

        private void submitTasks(List<?> groupTasks, boolean forceVisualRefresh) {
            ensureOverlay();
            if (overlayView == null) {
                return;
            }
            ArrayList<LauncherRecentsActivityOverlayView.CardRecord> cards = new ArrayList<>();
            if (groupTasks != null) {
                for (Object groupTask : groupTasks) {
                    LauncherRecentsActivityOverlayView.CardRecord card =
                            createCardRecord(groupTask, forceVisualRefresh);
                    if (card != null) {
                        cards.add(card);
                    }
                }
            }
            int resolvedActiveTaskId = resolvePreferredTaskId(cards);
            overlayView.setCards(cards, resolvedActiveTaskId);
            if (resolvedActiveTaskId != -1) {
                preferredTaskId = resolvedActiveTaskId;
                syncFallbackToTask(resolvedActiveTaskId);
            }
            for (LauncherRecentsActivityOverlayView.CardRecord card : cards) {
                requestTaskVisuals(card, forceVisualRefresh);
            }
        }

        private int resolvePreferredTaskId(
                List<LauncherRecentsActivityOverlayView.CardRecord> cards) {
            int fallbackTaskId = resolveCurrentFallbackTaskId();
            if (containsTask(cards, fallbackTaskId)) {
                return fallbackTaskId;
            }
            if (containsTask(cards, preferredTaskId)) {
                return preferredTaskId;
            }
            return cards.isEmpty() ? -1 : cards.get(0).taskId;
        }

        private int resolveCurrentFallbackTaskId() {
            if (fallbackRecentsView == null) {
                return -1;
            }
            Object currentTaskView =
                    LauncherRecentsCompat.invokeCompat(
                            fallbackRecentsView,
                            "getCurrentPageTaskView",
                            LauncherRecentsCompat.NO_ARGS);
            int taskId = resolveTaskIdFromTaskView(currentTaskView);
            if (taskId != -1) {
                return taskId;
            }
            Object runningTaskView =
                    LauncherRecentsCompat.invokeCompat(
                            fallbackRecentsView,
                            "getRunningTaskView",
                            LauncherRecentsCompat.NO_ARGS);
            return resolveTaskIdFromTaskView(runningTaskView);
        }

        private int resolveTaskIdFromTaskView(Object taskView) {
            Object task = LauncherRecentsCompat.invokeCompat(
                    taskView,
                    "getFirstTask",
                    LauncherRecentsCompat.NO_ARGS);
            return resolveTaskId(task);
        }

        private LauncherRecentsActivityOverlayView.CardRecord createCardRecord(
                Object groupTask,
                boolean forceVisualRefresh) {
            Object primaryTask = resolvePrimaryTask(groupTask);
            int taskId = resolveTaskId(primaryTask);
            if (primaryTask == null || taskId == -1) {
                return null;
            }
            String typeName = resolveGroupTypeName(groupTask);
            boolean isDesktop = "DESKTOP".equals(typeName);
            boolean isGrouped = !isDesktop && resolveTaskCount(groupTask) > 1;
            String badgeText = isDesktop ? "桌面" : (isGrouped ? "分屏" : "");
            String subtitle;
            if (isDesktop) {
                subtitle = "桌面任务，先按单卡降级显示";
            } else if (isGrouped) {
                subtitle = "分屏任务，先按单卡降级显示";
            } else {
                subtitle = "轻点当前卡片打开";
            }
            String title = readStringField(primaryTask, "title");
            if (TextUtils.isEmpty(title)) {
                title = readPackageName(primaryTask);
            }
            String contentDescription = readStringField(primaryTask, "titleDescription");
            if (TextUtils.isEmpty(contentDescription)) {
                contentDescription = title;
            }
            int accentColor = resolveAccentColor(primaryTask);
            int[] systemTaskSize = resolveSystemTaskSize(taskId);
            return new LauncherRecentsActivityOverlayView.CardRecord(
                    taskId,
                    accentColor,
                    title,
                    TextUtils.isEmpty(title) ? "最近任务" : title,
                    subtitle,
                    systemTaskSize[0],
                    systemTaskSize[1],
                    badgeText,
                    contentDescription,
                    forceVisualRefresh ? null : resolveTaskIcon(primaryTask),
                    forceVisualRefresh ? null : resolveTaskThumbnail(primaryTask),
                    ++generation);
        }

        private void requestTaskVisuals(
                LauncherRecentsActivityOverlayView.CardRecord card,
                boolean forceVisualRefresh) {
            Object primaryTask = findPrimaryTaskById(card.taskId);
            if (primaryTask == null) {
                return;
            }
            Drawable existingIcon = forceVisualRefresh ? null : resolveTaskIcon(primaryTask);
            if (existingIcon != null || !TextUtils.isEmpty(readStringField(primaryTask, "title"))) {
                overlayView.updateTaskIcon(
                        card.taskId,
                        card.generation,
                        existingIcon,
                        readStringField(primaryTask, "title"),
                        readStringField(primaryTask, "titleDescription"));
            } else {
                requestTaskIcon(primaryTask, card);
            }
            Bitmap existingThumbnail = forceVisualRefresh ? null : resolveTaskThumbnail(primaryTask);
            if (existingThumbnail != null && !existingThumbnail.isRecycled()) {
                overlayView.updateTaskThumbnail(card.taskId, card.generation, existingThumbnail);
            } else {
                requestTaskThumbnail(primaryTask, card);
            }
        }

        private Object findPrimaryTaskById(int taskId) {
            List<?> snapshot = latestGroupTasks;
            if (snapshot == null) {
                return null;
            }
            for (Object groupTask : snapshot) {
                Object primaryTask = resolvePrimaryTask(groupTask);
                if (resolveTaskId(primaryTask) == taskId) {
                    return primaryTask;
                }
            }
            return null;
        }

        private int[] resolveSystemTaskSize(int taskId) {
            View taskView = resolveTaskView(taskId);
            if (taskView == null) {
                return new int[]{0, 0};
            }
            return new int[]{
                    Math.max(0, taskView.getWidth()),
                    Math.max(0, taskView.getHeight())
            };
        }

        private void requestTaskThumbnail(
                Object primaryTask,
                LauncherRecentsActivityOverlayView.CardRecord card) {
            Object recentsModel = resolveRecentsModel();
            if (recentsModel == null) {
                return;
            }
            Object thumbnailCache =
                    LauncherRecentsCompat.invokeCompat(
                            recentsModel,
                            "getThumbnailCache",
                            LauncherRecentsCompat.NO_ARGS);
            if (thumbnailCache == null) {
                return;
            }
            LauncherRecentsCompat.invokeCompat(
                    thumbnailCache,
                    "updateThumbnailInBackground",
                    new Class[]{primaryTask.getClass(), Consumer.class},
                    primaryTask,
                    (Consumer<Object>) thumbnailData -> {
                        Bitmap bitmap = resolveThumbnailBitmap(thumbnailData);
                        if (bitmap == null || overlayView == null) {
                            return;
                        }
                        overlayView.updateTaskThumbnail(card.taskId, card.generation, bitmap);
                    });
        }

        private void requestTaskIcon(
                Object primaryTask,
                LauncherRecentsActivityOverlayView.CardRecord card) {
            Object recentsModel = resolveRecentsModel();
            if (recentsModel == null) {
                return;
            }
            Object iconCache =
                    LauncherRecentsCompat.invokeCompat(
                            recentsModel,
                            "getIconCache",
                            LauncherRecentsCompat.NO_ARGS);
            if (iconCache == null) {
                return;
            }
            Class<?> callbackClass = resolveTaskIconCallbackClass(loader);
            if (callbackClass == null) {
                return;
            }
            Object callback = createIconCallbackProxy(loader, (icon, contentDescription, title) -> {
                if (overlayView == null) {
                    return;
                }
                overlayView.updateTaskIcon(
                        card.taskId,
                        card.generation,
                        icon,
                        title,
                        contentDescription);
            });
            if (callback == null) {
                return;
            }
            LauncherRecentsCompat.invokeCompat(
                    iconCache,
                    "getIconInBackground",
                    new Class[]{primaryTask.getClass(), callbackClass},
                    primaryTask,
                    callback);
        }

        private Object resolveRecentsModel() {
            Object singleton = LauncherRecentsCompat.readStaticFieldCompat(
                    RECENTS_MODEL_CLASS,
                    "INSTANCE",
                    loader);
            if (singleton == null) {
                return null;
            }
            return LauncherRecentsCompat.invokeCompat(
                    singleton,
                    "get",
                    CONTEXT_ARG,
                    activity);
        }

        private boolean isActivityAlive() {
            if (activity.isFinishing()) {
                return false;
            }
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                    || !activity.isDestroyed();
        }

        private String resolveGroupTypeName(Object groupTask) {
            Object taskViewType = FlymeStatusBarSizer.getFieldCompat(groupTask, "taskViewType");
            return taskViewType == null ? "" : String.valueOf(taskViewType);
        }

        private int resolveTaskCount(Object groupTask) {
            Object tasks = LauncherRecentsCompat.invokeCompat(
                    groupTask,
                    "getTasks",
                    LauncherRecentsCompat.NO_ARGS);
            return tasks instanceof List ? ((List<?>) tasks).size() : 0;
        }

        private Object resolvePrimaryTask(Object groupTask) {
            Object firstTask = LauncherRecentsCompat.invokeCompat(
                    groupTask,
                    "firstTask",
                    LauncherRecentsCompat.NO_ARGS);
            if (firstTask != null) {
                return firstTask;
            }
            Object tasks = LauncherRecentsCompat.invokeCompat(
                    groupTask,
                    "getTasks",
                    LauncherRecentsCompat.NO_ARGS);
            if (tasks instanceof List && !((List<?>) tasks).isEmpty()) {
                return ((List<?>) tasks).get(0);
            }
            return null;
        }

        private int resolveTaskId(Object task) {
            Object taskKey = FlymeStatusBarSizer.getFieldCompat(task, "key");
            Object taskId = FlymeStatusBarSizer.getFieldCompat(taskKey, "id");
            return taskId instanceof Integer ? (Integer) taskId : -1;
        }

        private String readPackageName(Object task) {
            Object taskKey = FlymeStatusBarSizer.getFieldCompat(task, "key");
            Object component = LauncherRecentsCompat.invokeCompat(
                    taskKey,
                    "getComponent",
                    LauncherRecentsCompat.NO_ARGS);
            if (component instanceof ComponentName) {
                return ((ComponentName) component).getPackageName();
            }
            Object packageName = LauncherRecentsCompat.invokeCompat(
                    taskKey,
                    "getPackageName",
                    LauncherRecentsCompat.NO_ARGS);
            return packageName instanceof String ? (String) packageName : "最近任务";
        }

        private int resolveAccentColor(Object task) {
            Object value = FlymeStatusBarSizer.getFieldCompat(task, "colorPrimary");
            if (value instanceof Integer && ((Integer) value) != 0) {
                return (Integer) value;
            }
            return Color.parseColor("#2F4269");
        }

        private Drawable resolveTaskIcon(Object task) {
            Object icon = FlymeStatusBarSizer.getFieldCompat(task, "icon");
            return icon instanceof Drawable ? (Drawable) icon : null;
        }

        private Bitmap resolveTaskThumbnail(Object task) {
            Object thumbnailData = FlymeStatusBarSizer.getFieldCompat(task, "thumbnail");
            return resolveThumbnailBitmap(thumbnailData);
        }

        private Bitmap resolveThumbnailBitmap(Object thumbnailData) {
            Object bitmap = LauncherRecentsCompat.invokeCompat(
                    thumbnailData,
                    "getThumbnail",
                    LauncherRecentsCompat.NO_ARGS);
            return bitmap instanceof Bitmap ? (Bitmap) bitmap : null;
        }

        private String readStringField(Object target, String fieldName) {
            Object value = FlymeStatusBarSizer.getFieldCompat(target, fieldName);
            return value instanceof String ? (String) value : "";
        }

        private boolean containsTask(
                List<LauncherRecentsActivityOverlayView.CardRecord> cards,
                int taskId) {
            if (taskId == -1) {
                return false;
            }
            for (LauncherRecentsActivityOverlayView.CardRecord card : cards) {
                if (card.taskId == taskId) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Object createIconCallbackProxy(
            ClassLoader loader,
            IconReceiver receiver) {
        try {
            Class<?> callbackClass = resolveTaskIconCallbackClass(loader);
            return Proxy.newProxyInstance(
                    loader,
                    new Class[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onTaskIconReceived".equals(method.getName())
                                && args != null
                                && args.length >= 3) {
                            Drawable icon = args[0] instanceof Drawable
                                    ? (Drawable) args[0]
                                    : null;
                            String contentDescription = args[1] instanceof String
                                    ? (String) args[1]
                                    : "";
                            String title = args[2] instanceof String
                                    ? (String) args[2]
                                    : "";
                            receiver.onIcon(icon, contentDescription, title);
                        }
                        return null;
                    });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to create TaskIconCache callback proxy",
                    t);
            return null;
        }
    }

    private static Class<?> resolveTaskIconCallbackClass(ClassLoader loader) {
        try {
            return Class.forName(TASK_ICON_CALLBACK_CLASS, false, loader);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logLauncherWarning(
                    "Failed to resolve TaskIconCache callback class",
                    t);
            return null;
        }
    }
}
