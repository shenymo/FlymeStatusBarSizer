package com.example.flymestatusbarsizer.feature.ime;

import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.List;

public final class ImeWindowModeBridge {
    private static final String TAG = "FlymeImeWelt";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String ACTION_WELT_WINDOW =
            "com.fiyme.statusbarsizer.action.IME_WELT_WINDOW";
    private static final int FULLSCREEN_WINDOW_MODE = 1;
    private static final int MINI_WINDOW_MODE = 11;
    private static final int PINNED_WINDOW_MODE = 12;
    private static final int WELT_WINDOW_MODE = 268;
    private static final long MOVE_DELAY_MS = 220L;
    private static final long MODE_SWITCH_DELAY_MS = 350L;
    private static final int MODE_SWITCH_RETRY_COUNT = 5;
    private static final long REQUEST_DEBOUNCE_MS = 1500L;
    private static final String EXTRA_TARGET_PACKAGE =
            "com.fiyme.statusbarsizer.extra.IME_TARGET_PACKAGE";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean receiverRegistered;
    private static long requestBlockedUntil;
    private static Method windowManagerExtGetInstance;
    private static Method getVirtualWindowingModeByTask;
    private static Method getWindowModeBound;
    private static Method enterWindowMode;
    private static Method moveWindowModeBound;

    private ImeWindowModeBridge() {
    }

    public static void request(Context context) {
        request(context, null);
    }

    public static void request(Context context, String targetPackage) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(ACTION_WELT_WINDOW).setPackage(SYSTEM_UI_PACKAGE);
        if (targetPackage != null && !targetPackage.isEmpty()) {
            intent.putExtra(EXTRA_TARGET_PACKAGE, targetPackage);
        }
        Log.i(TAG, "broadcast_send targetPackage=" + targetPackage);
        if (Build.VERSION.SDK_INT >= 34) {
            context.sendBroadcast(intent, null,
                    BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
        } else {
            context.sendBroadcast(intent);
        }
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        try {
            Class<?> application = Class.forName(
                    "com.android.systemui.SystemUIApplication", false, loader);
            Method onCreate = application.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            module.intercept(onCreate, chain -> {
                Object result = chain.proceed();
                try {
                    register((Context) chain.getThisObject());
                } catch (Throwable error) {
                    FlymeStatusBarSizer.logWindowModeWarning(
                            "Failed to register IME welt request receiver", error);
                }
                return result;
            });
        } catch (Throwable error) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to hook SystemUI application for IME welt requests", error);
        }
    }

    private static void register(Context context) {
        if (context == null || receiverRegistered) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (intent == null || !ACTION_WELT_WINDOW.equals(intent.getAction())
                        || !isAuthorizedSender(this)) {
                    return;
                }
                String targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
                Log.i(TAG, "broadcast_receive targetPackage=" + targetPackage);
                handleRequest(receiverContext, targetPackage);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_WELT_WINDOW);
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
    }

    private static boolean isAuthorizedSender(BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        String packageName = receiver.getSentFromPackage();
        return "com.android.inputmethod.latin".equals(packageName)
                || "com.google.android.inputmethod.latin".equals(packageName)
                || "com.tencent.wetype".equals(packageName)
                || "flyme.inputmethod".equals(packageName)
                || BuildConfig.APPLICATION_ID.equals(packageName)
                || receiver.getSentFromUid() == Process.SHELL_UID
                || receiver.getSentFromUid() == Process.ROOT_UID;
    }

    private static void handleRequest(Context context, String targetPackage) {
        try {
            long now = SystemClock.uptimeMillis();
            if (now < requestBlockedUntil) {
                Log.i(TAG, "request_ignored reason=debounce remainingMs="
                        + (requestBlockedUntil - now));
                return;
            }
            Object windowManager = windowManagerExt(context);
            if (windowManager == null) {
                return;
            }
            Object task = resolveTargetTask(targetPackage, windowManager);
            if (task == null || !isApplicationTask(task)) {
                Log.w(TAG, "request_ignored reason=no_application_task targetPackage="
                        + targetPackage + " task=" + taskSummary(task));
                return;
            }
            int taskId = ReflectUtils.getIntField(task, "taskId", -1);
            int displayId = ReflectUtils.getIntField(task, "displayId", -1);
            if (taskId <= 0 || displayId < 0) {
                Log.w(TAG, "request_ignored reason=invalid_task task=" + taskSummary(task));
                return;
            }
            logAllRootTasks(context, windowManager, "before");
            int currentMode = getCurrentWindowMode(windowManager, task, displayId, taskId);
            if (currentMode <= 0) {
                Log.w(TAG, "request_ignored reason=invalid_mode task=" + taskSummary(task));
                return;
            }
            requestBlockedUntil = now + REQUEST_DEBOUNCE_MS;
            Log.i(TAG, "request_start targetPackage=" + targetPackage
                    + " task=" + taskSummary(task) + " currentMode=" + currentMode);
            moveTargetToSideMini(context, windowManager, displayId, taskId, currentMode);
        } catch (Throwable error) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to convert focused task to welt window", error);
        }
    }

    private static Object resolveTargetTask(String targetPackage, Object windowManager)
            throws ReflectiveOperationException {
        Class<?> activityTaskManager = Class.forName("android.app.ActivityTaskManager");
        Object service = activityTaskManager.getMethod("getService").invoke(null);
        Class<?> serviceType = Class.forName("android.app.IActivityTaskManager");
        Object focused = serviceType.getMethod("getFocusedRootTaskInfo").invoke(service);
        Log.i(TAG, "focused_task targetPackage=" + targetPackage
                + " task=" + taskSummary(focused));
        Object allTasks = serviceType.getMethod("getAllRootTaskInfos").invoke(service);
        int focusedDisplayId = ReflectUtils.getIntField(focused, "displayId", 0);
        int focusedTaskId = ReflectUtils.getIntField(focused, "taskId", -1);
        Object firstMiniTask = null;
        Object matchingMiniTask = null;
        Object matchingFullscreenTask = null;
        Object focusedFullscreenTask = null;
        if (allTasks instanceof List) {
            for (Object task : (List<?>) allTasks) {
                int displayId = ReflectUtils.getIntField(task, "displayId", -1);
                int taskId = ReflectUtils.getIntField(task, "taskId", -1);
                if (displayId != focusedDisplayId || taskId <= 0 || !isApplicationTask(task)) {
                    continue;
                }
                int mode = getCurrentWindowMode(windowManager, task, displayId, taskId);
                String packageName = taskPackage(task);
                if (packageName == null) {
                    continue;
                }
                if (mode == MINI_WINDOW_MODE) {
                    if (firstMiniTask == null) {
                        firstMiniTask = task;
                    }
                    if (targetPackage != null && targetPackage.equals(packageName)) {
                        matchingMiniTask = task;
                    }
                } else if (mode == FULLSCREEN_WINDOW_MODE) {
                    if (taskId == focusedTaskId) {
                        focusedFullscreenTask = task;
                    }
                    if (targetPackage != null && targetPackage.equals(packageName)) {
                        matchingFullscreenTask = task;
                    }
                }
            }
        }
        Object selectedMini = matchingMiniTask != null ? matchingMiniTask : firstMiniTask;
        if (selectedMini != null) {
            Log.i(TAG, "target_selected reason=normal_mini task=" + taskSummary(selectedMini));
            return selectedMini;
        }
        Object selectedFullscreen = matchingFullscreenTask != null
                ? matchingFullscreenTask : focusedFullscreenTask;
        if (selectedFullscreen != null) {
            Log.i(TAG, "target_selected reason=fullscreen_fallback task="
                    + taskSummary(selectedFullscreen));
            return selectedFullscreen;
        }
        Log.w(TAG, "target_missing targetPackage=" + targetPackage);
        return null;
    }

    private static void moveTargetToSideMini(
            Context context, Object windowManager, int displayId, int taskId, int currentMode) {
        if (currentMode == MINI_WINDOW_MODE) {
            moveToEdge(context, windowManager, displayId, taskId);
            return;
        }
        if (currentMode == FULLSCREEN_WINDOW_MODE) {
            enterWindowMode(windowManager, displayId, taskId, FULLSCREEN_WINDOW_MODE,
                    MINI_WINDOW_MODE, null);
            MAIN.postDelayed(() -> finishFullscreenToMini(
                    context, windowManager, displayId, taskId, 0), MODE_SWITCH_DELAY_MS);
            return;
        }
        Log.w(TAG, "request_ignored reason=unsupported_mode taskId=" + taskId
                + " currentMode=" + currentMode);
    }

    private static String taskSummary(Object task) {
        if (task == null) {
            return "null";
        }
        Object configuration = ReflectUtils.getField(task, "configuration");
        Object windowConfiguration = ReflectUtils.getField(configuration, "windowConfiguration");
        int mode = ReflectUtils.invokeNoArgInt(windowConfiguration, "getWindowingMode", -1);
        return "id=" + ReflectUtils.getIntField(task, "taskId", -1)
                + ",display=" + ReflectUtils.getIntField(task, "displayId", -1)
                + ",package=" + taskPackage(task) + ",mode=" + mode;
    }

    private static void logAllRootTasks(
            Context context, Object windowManager, String phase) {
        try {
            Class<?> activityTaskManager = Class.forName("android.app.ActivityTaskManager");
            Object service = activityTaskManager.getMethod("getService").invoke(null);
            Class<?> serviceType = Class.forName("android.app.IActivityTaskManager");
            Object value = serviceType.getMethod("getAllRootTaskInfos").invoke(service);
            if (!(value instanceof List)) {
                Log.w(TAG, "window_list phase=" + phase + " count=0 result=" + value);
                return;
            }
            List<?> tasks = (List<?>) value;
            Log.i(TAG, "window_list phase=" + phase + " count=" + tasks.size());
            for (int i = 0; i < tasks.size(); i++) {
                Object task = tasks.get(i);
                int displayId = ReflectUtils.getIntField(task, "displayId", -1);
                int taskId = ReflectUtils.getIntField(task, "taskId", -1);
                int mode = displayId >= 0 && taskId > 0
                        ? getCurrentWindowMode(windowManager, task, displayId, taskId)
                        : taskWindowMode(task);
                Rect bound = displayId >= 0 && taskId > 0
                        ? windowModeBound(windowManager, displayId, taskId, mode) : null;
                String packageName = taskPackage(task);
                Log.i(TAG, "window_state phase=" + phase + " index=" + i
                        + " taskId=" + taskId + " displayId=" + displayId
                        + " app=" + applicationLabel(context, packageName)
                        + " package=" + packageName + " mode=" + mode
                        + "(" + windowModeName(mode) + ")"
                        + " bound=" + bound);
            }
        } catch (Throwable error) {
            Log.w(TAG, "window_list_failed phase=" + phase, error);
        }
    }

    private static String applicationLabel(Context context, String packageName) {
        if (context == null || packageName == null) {
            return "unknown";
        }
        try {
            return String.valueOf(context.getPackageManager()
                    .getApplicationLabel(context.getPackageManager()
                            .getApplicationInfo(packageName, 0)));
        } catch (Throwable error) {
            return packageName;
        }
    }

    private static int taskWindowMode(Object task) {
        Object configuration = ReflectUtils.getField(task, "configuration");
        Object windowConfiguration = ReflectUtils.getField(configuration, "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(windowConfiguration, "getWindowingMode", -1);
    }

    private static String windowModeName(int mode) {
        if (mode == FULLSCREEN_WINDOW_MODE) {
            return "全屏";
        }
        if (mode == 11) {
            return "小窗";
        }
        if (mode == PINNED_WINDOW_MODE) {
            return "中间小窗";
        }
        if (mode == WELT_WINDOW_MODE) {
            return "贴边";
        }
        if (mode == 1035) {
            return "自由窗口";
        }
        return "未知";
    }

    private static String taskPackage(Object task) {
        Object topActivity = ReflectUtils.getField(task, "topActivity");
        if (topActivity instanceof android.content.ComponentName) {
            return ((android.content.ComponentName) topActivity).getPackageName();
        }
        Object baseActivity = ReflectUtils.getField(task, "baseActivity");
        return baseActivity instanceof android.content.ComponentName
                ? ((android.content.ComponentName) baseActivity).getPackageName() : null;
    }

    private static boolean isApplicationTask(Object task) {
        Object configuration = ReflectUtils.getField(task, "configuration");
        Object windowConfiguration = ReflectUtils.getField(configuration, "windowConfiguration");
        int activityType = ReflectUtils.invokeNoArgInt(windowConfiguration, "getActivityType", -1);
        if (activityType != 1) {
            return false;
        }
        Object topActivity = ReflectUtils.getField(task, "topActivity");
        if (topActivity instanceof android.content.ComponentName) {
            String packageName = ((android.content.ComponentName) topActivity).getPackageName();
            return !SYSTEM_UI_PACKAGE.equals(packageName)
                    && !"com.meizu.flyme.launcher".equals(packageName)
                    && !"com.android.inputmethod.latin".equals(packageName)
                    && !"com.google.android.inputmethod.latin".equals(packageName)
                    && !"com.tencent.wetype".equals(packageName)
                    && !"flyme.inputmethod".equals(packageName);
        }
        return true;
    }

    private static int getCurrentWindowMode(
            Object windowManager, Object task, int displayId, int taskId) {
        int mode = ((Number) invoke(windowManager, getVirtualWindowingModeByTask(
                windowManager.getClass()), displayId, taskId)).intValue();
        if (mode > 0) {
            return mode;
        }
        Object configuration = ReflectUtils.getField(task, "configuration");
        Object windowConfiguration = ReflectUtils.getField(configuration, "windowConfiguration");
        mode = ReflectUtils.invokeNoArgInt(windowConfiguration, "getWindowingMode", -1);
        return mode > 0 ? mode : FULLSCREEN_WINDOW_MODE;
    }

    private static void moveToEdge(Context context, Object windowManager, int displayId, int taskId) {
        Rect bound = windowModeBound(windowManager, displayId, taskId, MINI_WINDOW_MODE);
        if (bound == null || bound.isEmpty()) {
            Log.w(TAG, "move_ignored reason=no_bound taskId=" + taskId);
            return;
        }
        Log.i(TAG, "move_start taskId=" + taskId + " bound=" + bound);
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager == null ? null : displayManager.getDisplay(displayId);
        if (display == null) {
            Log.w(TAG, "move_ignored reason=no_display taskId=" + taskId
                    + " displayId=" + displayId);
            return;
        }
        Point size = new Point();
        display.getRealSize(size);
        int width = size.x;
        if (width <= 0) {
            return;
        }
        int windowWidth = Math.min(bound.width(), width);
        boolean left = bound.centerX() < width / 2;
        int leftEdge = left ? 0 : width - windowWidth;
        Rect edgeBound = new Rect(leftEdge, bound.top, leftEdge + windowWidth,
                bound.top + bound.height());
        invoke(windowManager, moveWindowModeBound(windowManager.getClass()),
                displayId, taskId, MINI_WINDOW_MODE, edgeBound, null, null, true);
        Log.i(TAG, "move_done taskId=" + taskId + " edgeBound=" + edgeBound);
        logAllRootTasks(context, windowManager, "after");
    }

    private static void finishFullscreenToMini(
            Context context, Object windowManager, int displayId, int taskId, int retryCount) {
        int currentMode = ((Number) invoke(windowManager,
                getVirtualWindowingModeByTask(windowManager.getClass()), displayId, taskId)).intValue();
        Log.i(TAG, "fullscreen_to_mini_check taskId=" + taskId + " currentMode="
                + currentMode + " retry=" + retryCount);
        if (currentMode == MINI_WINDOW_MODE) {
            moveToEdge(context, windowManager, displayId, taskId);
            return;
        }
        if (currentMode != FULLSCREEN_WINDOW_MODE || retryCount >= MODE_SWITCH_RETRY_COUNT) {
            Log.w(TAG, "fullscreen_to_mini_abort taskId=" + taskId
                    + " currentMode=" + currentMode);
            return;
        }
        MAIN.postDelayed(() -> finishFullscreenToMini(
                context, windowManager, displayId, taskId, retryCount + 1), MOVE_DELAY_MS);
    }

    private static Object windowManagerExt(Context context) throws ReflectiveOperationException {
        Method method = windowManagerExtGetInstance;
        if (method == null) {
            Class<?> type = Class.forName("flyme.view.WindowManagerExt");
            method = type.getMethod("getInstance", Context.class);
            method.setAccessible(true);
            windowManagerExtGetInstance = method;
        }
        return method.invoke(null, context);
    }

    private static void enterWindowMode(
            Object windowManager, int displayId, int taskId, int oldMode, int newMode, Rect bound) {
        Log.i(TAG, "enter_mode displayId=" + displayId + " taskId=" + taskId
                + " oldMode=" + oldMode + " newMode=" + newMode + " bound=" + bound);
        Method method = enterWindowMode(windowManager.getClass());
        invoke(windowManager, method, displayId, taskId, oldMode, newMode, bound, 0f, true);
    }

    private static Rect windowModeBound(
            Object windowManager, int displayId, int taskId, int mode) {
        Object value = invoke(windowManager, getWindowModeBound(windowManager.getClass()),
                displayId, taskId, mode);
        return value instanceof Rect ? new Rect((Rect) value) : null;
    }

    private static Method getVirtualWindowingModeByTask(Class<?> type) {
        if (getVirtualWindowingModeByTask == null) {
            getVirtualWindowingModeByTask = method(type, "getVirtualWindowingModeByTask",
                    int.class, int.class);
        }
        return getVirtualWindowingModeByTask;
    }

    private static Method getWindowModeBound(Class<?> type) {
        if (getWindowModeBound == null) {
            getWindowModeBound = method(type, "getWindowModeBound", int.class, int.class, int.class);
        }
        return getWindowModeBound;
    }

    private static Method enterWindowMode(Class<?> type) {
        if (enterWindowMode == null) {
            enterWindowMode = method(type, "enterWindowMode", int.class, int.class,
                    int.class, int.class, Rect.class, float.class, boolean.class);
        }
        return enterWindowMode;
    }

    private static Method moveWindowModeBound(Class<?> type) {
        if (moveWindowModeBound == null) {
            moveWindowModeBound = method(type, "moveWindowModeBound", int.class, int.class,
                    int.class, Rect.class, float[].class, float[].class, boolean.class);
        }
        return moveWindowModeBound;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable error) {
            throw new IllegalStateException("Flyme window method unavailable: " + name, error);
        }
    }

    private static Object invoke(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            throw new IllegalStateException("Flyme window service is unavailable");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Flyme window call failed: " + method.getName(), error);
        }
    }
}
