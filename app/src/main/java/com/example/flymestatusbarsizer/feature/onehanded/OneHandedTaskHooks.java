package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.assistant.AssistantGestureScenes;
import com.example.flymestatusbarsizer.feature.assistant.SideGestureActions;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

/** Initializes the live OneStep workspace through LSPosed in SystemUI. */
public final class OneHandedTaskHooks {
    static final String TAG = OneStepFlowLog.TAG;
    // Values verified against this ROM's QuickStepContract.getSystemUiStateString().
    private static final long BLOCKED_FLAGS = 1L | 4L | 8L | 64L | 512L | 2048L
            | 16384L | 32768L | 65536L | 262144L | 524288L | 2097152L
            | 8388608L | 67108864L | 134217728L | 536870912L | 2147483648L
            | 34359738368L;
    private static volatile OneStepWorkspace controller;
    private static volatile WeakReference<Object> edgeHandler = new WeakReference<>(null);
    private static volatile boolean installed;
    private static volatile Object shellTransitions;
    private static volatile Object taskViewFactory;
    private static volatile Object taskDisplayAreas;
    private static volatile boolean imeVisible;
    private static volatile boolean imePositioning;
    private static volatile boolean workspaceTriggerReceiverInstalled;

    public static final SideGestureActions.Action ACTION = new SideGestureActions.Action() {
        @Override public boolean isReady() {
            OneStepWorkspace current = controller;
            return installed && current != null && current.canTrigger();
        }

        @Override public void execute(boolean fromLeft, Runnable onSuccess) {
            OneStepWorkspace current = controller;
            if (installed && current != null) {
                current.useSideGestureProfile();
                current.toggle(fromLeft, onSuccess);
            }
        }
    };

    private OneHandedTaskHooks() {}

    static boolean oneHandedWorkspaceEnabled(Context context) {
        if (context == null) return false;
        ModuleConfig config = ModuleConfig.load(context);
        return config.enabled && config.oneHandedModeEnabled
                && config.oneHandedModeAction == SettingsStore.ONE_HANDED_MODE_ACTION_TASK_SCALE;
    }

    public static void trackEdgeHandler(Object edge) {
        if (edgeHandler.get() != edge && ReflectUtils.getIntField(edge, "mDisplayId", -1) == 0) {
            edgeHandler = new WeakReference<>(edge);
        }
    }

    public static void refresh() {
        OneStepWorkspace current = controller;
        if (current != null) current.refresh();
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (Build.VERSION.SDK_INT < 33 || installed) return;
        OneStepStatusBar.install(module, loader);
        try {
            hookConstruction(module, loader, "com.android.wm.shell.RootTaskDisplayAreaOrganizer", value -> {
                taskDisplayAreas = value;
                attach();
            });
            hookConstruction(module, loader, "com.android.wm.shell.taskview.TaskViewFactoryController", value -> {
                taskViewFactory = value;
                attach();
            });
            hookConstruction(module, loader, "com.android.wm.shell.transition.Transitions", value -> {
                shellTransitions = value;
                attach();
            });
            hookTaskViewTransitions(module, loader);
            installed = true;
            OneStepFlowLog.i(TAG, "OneStep Shell task workspace hooks installed");
        } catch (Throwable e) {
            OneStepFlowLog.w(TAG, "Cannot install OneStep workspace", e);
            OneStepFlowLog.hookSummary("systemui.workspace");
            return;
        }
        installOneHandedActivationHook(module, loader);
        installOneHandedStartHook(module, loader);
        // Optional lifecycle hooks must not prevent initialization on another Flyme release.
        installImePositionObserver(module, loader);
        try {
            Method home = Class.forName("com.flyme.systemui.navigationbar.MBackButtonController", false, loader)
                    .getDeclaredMethod("toHome");
            home.setAccessible(true);
            module.intercept(home, chain -> {
                OneStepFlowLog.hookTriggered("MBackButtonController.toHome");
                OneStepWorkspace current = controller;
                return current != null && current.blocksNavigation() ? null : chain.proceed();
            });
        } catch (Throwable error) { OneStepFlowLog.w(TAG, "mBack Home guard unavailable", error); }
        try {
            Class<?> recents = Class.forName("com.android.wm.shell.recents.RecentsTransitionHandler", false, loader);
            Class<?> runner = Class.forName("android.view.IRecentsAnimationRunner", false, loader);
            Class<?> snapshots = Array.newInstance(Class.forName("android.window.TaskSnapshot", false, loader), 0).getClass();
            for (Method method : recents.getDeclaredMethods()) {
                if (!"startRecentsTransition".equals(method.getName())) continue;
                int runnerIndex = -1;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++) if (parameters[i] == runner) runnerIndex = i;
                if (runnerIndex < 0) continue;
                int callbackIndex = runnerIndex;
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    OneStepFlowLog.hookTriggered("RecentsTransitionHandler.startRecentsTransition");
                    OneStepWorkspace current = controller;
                    if (current != null && current.blocksNavigation()) {
                        // Normally disabled SysUiState flags prevent the launcher from starting.
                        // Complete an already queued request as cancelled, without starting a WCT.
                        try {
                            Object callback = chain.getArg(callbackIndex);
                            if (callback != null) OneStepReflection.call(callback, "onAnimationCanceled",
                                    new Class<?>[]{int[].class, snapshots}, new int[0], Array.newInstance(snapshots.getComponentType(), 0));
                        } catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot cancel workspace recents request", error); }
                        return null;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable e) { OneStepFlowLog.w(TAG, "Recents lifecycle hook unavailable", e); }
        OneStepFlowLog.hookSummary("systemui.workspace");
    }

    private static void installOneHandedActivationHook(FlymeStatusBarSizer module, ClassLoader loader) {
        installOneHandedHook(module, loader, "onActivatedActionChanged",
                OneHandedTaskHooks::consumeOneHandedActivation,
                "One-handed activation routed to workspace",
                "One-handed activation hook unavailable");
    }

    private static void installOneHandedStartHook(FlymeStatusBarSizer module, ClassLoader loader) {
        installOneHandedHook(module, loader, "startOneHanded",
                OneHandedTaskHooks::consumeOneHandedStart,
                "One-handed start routed to workspace",
                "One-handed start hook unavailable");
    }

    private static void installOneHandedHook(FlymeStatusBarSizer module, ClassLoader loader,
                                             String methodName, Predicate<Object> consumer,
                                             String installedMessage, String unavailableMessage) {
        try {
            Class<?> controllerClass = Class.forName(
                    "com.android.wm.shell.onehanded.OneHandedController", false, loader);
            Method method = controllerClass.getDeclaredMethod(methodName);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                String point = "OneHandedController." + methodName;
                OneStepFlowLog.hookTriggered(point);
                boolean consumed = consumer.test(chain.getThisObject());
                OneStepFlowLog.hookMatched(point, consumed,
                        consumed ? "workspace_condition_passed" : "workspace_condition_rejected");
                OneStepFlowLog.hookEffective(point, consumed,
                        consumed ? "workspace_toggle_requested" : "native_path_preserved");
                return consumed ? null : chain.proceed();
            });
            OneStepFlowLog.i(TAG, installedMessage);
        } catch (Throwable error) {
            OneStepFlowLog.w(TAG, unavailableMessage, error);
        }
    }

    private static boolean consumeOneHandedActivation(Object target) {
        OneStepWorkspace current = controller;
        if (current == null || !installed || !current.canTrigger()) return false;
        Object source = ReflectUtils.getField(target, "mContext");
        if (!(source instanceof Context)) return false;
        Context context = (Context) source;
        if (!oneHandedWorkspaceEnabled(context)
                || !ReflectUtils.getBooleanField(target, "mIsShortcutEnabled", false)
                || ReflectUtils.getBooleanField(target, "mIsSwipeToNotificationEnabled", false)) {
            return false;
        }
        int userId = ReflectUtils.getIntField(target, "mUserId", 0);
        if (getSecureIntForUser(context, "one_handed_mode_activated", 0, userId) != 1) return false;
        putSecureIntForUser(context, "one_handed_mode_activated", 0, userId);
        current.useOneHandedProfile();
        current.toggle(false, null);
        OneStepFlowLog.i(TAG, "Native one-handed gesture opened workspace");
        return true;
    }

    private static boolean consumeOneHandedStart(Object target) {
        OneStepWorkspace current = controller;
        if (current == null || !installed) return false;
        Object source = ReflectUtils.getField(target, "mContext");
        if (!(source instanceof Context)) return false;
        if (!oneHandedWorkspaceEnabled((Context) source)
                || !current.canTrigger()) return false;
        current.useOneHandedProfile();
        current.toggle(false, null);
        OneStepFlowLog.i(TAG, "Native one-handed start opened workspace");
        return true;
    }

    private static int getSecureIntForUser(Context context, String key, int fallback, int userId) {
        try {
            Method method = Settings.Secure.class.getMethod("getIntForUser",
                    android.content.ContentResolver.class, String.class, int.class, int.class);
            Object value = method.invoke(null, context.getContentResolver(), key, fallback, userId);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Throwable ignored) {
            return Settings.Secure.getInt(context.getContentResolver(), key, fallback);
        }
    }

    private static boolean putSecureIntForUser(Context context, String key, int value, int userId) {
        try {
            Method method = Settings.Secure.class.getMethod("putIntForUser",
                    android.content.ContentResolver.class, String.class, int.class, int.class);
            Object result = method.invoke(null, context.getContentResolver(), key, value, userId);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return Settings.Secure.putInt(context.getContentResolver(), key, value);
        }
    }

    private static void installImePositionObserver(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> processor = Class.forName(
                    "com.android.wm.shell.common.DisplayImeController$ImePositionProcessor", false, loader);
            hookConstruction(module, loader, "com.android.wm.shell.common.DisplayImeController", ime -> {
                try {
                    Object observer = Proxy.newProxyInstance(loader, new Class<?>[]{processor}, (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "equals": return proxy == args[0];
                                case "hashCode": return System.identityHashCode(proxy);
                                case "toString": return "OneStepImePositionObserver";
                                default: return null;
                            }
                        }
                        OneStepWorkspace current = controller;
                        if (args != null && args.length > 0 && Integer.valueOf(0).equals(args[0])) {
                            // Entry needs IME state before the workspace has a window.
                            switch (method.getName()) {
                                case "onImeVisibilityChanged":
                                    imeVisible = (Boolean) args[1];
                                    break;
                                case "onImeStartPositioning":
                                    imePositioning = true;
                                    imeVisible = (Boolean) args[3];
                                    break;
                                case "onImeEndPositioning":
                                    imePositioning = false;
                                    break;
                                case "onImeControlTargetChanged":
                                    if (!(Boolean) args[1]) {
                                        imePositioning = false;
                                        imeVisible = false;
                                    }
                                    break;
                                default: break;
                            }
                            if (current == null) return method.getReturnType() == int.class ? 0 : null;
                            switch (method.getName()) {
                                case "onImeStartPositioning":
                                    current.onShellImeStart((Integer) args[2], (Boolean) args[3], (Boolean) args[4]);
                                    break;
                                case "onImePositionChanged":
                                    current.onShellImePosition((Integer) args[1]);
                                    break;
                                case "onImeEndPositioning":
                                    current.onShellImeEnd((Boolean) args[1]);
                                    break;
                                case "onImeControlTargetChanged":
                                    if (!(Boolean) args[1]) current.onShellImeControlLost();
                                    break;
                                default: break;
                            }
                        }
                        // Observe only; keep Shell's IME alpha and surface transaction intact.
                        return method.getReturnType() == int.class ? 0 : null;
                    });
                    OneStepReflection.call(ime, "addPositionProcessor", new Class<?>[]{processor}, observer);
                } catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot observe Shell IME positioning", error); }
            });
        } catch (Throwable error) { OneStepFlowLog.w(TAG, "Shell IME positioning unavailable", error); }
    }

    private static void hookConstruction(FlymeStatusBarSizer module, ClassLoader loader, String name,
                                         java.util.function.Consumer<Object> callback) throws Exception {
        Class<?> type = Class.forName(name, false, loader);
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            module.intercept(constructor, chain -> {
                Object result = chain.proceed();
                callback.accept(chain.getThisObject());
                return result;
            });
        }
    }

    private static void hookTaskViewTransitions(FlymeStatusBarSizer module, ClassLoader loader) {
        String[] owners = {
                "com.android.wm.shell.taskview.TaskViewTransitions",
                "com.android.wm.shell.taskview.TaskViewTaskController"
        };
        for (String owner : owners) try {
            Class<?> type = Class.forName(owner, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                boolean transitionsOwner = owner.endsWith("TaskViewTransitions");
                if (transitionsOwner
                        ? !("setTaskViewVisible".equals(name) || "removeTaskView".equals(name))
                        : !"moveTaskViewToFullscreen".equals(name)) continue;
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    String point = owner + "." + name;
                    OneStepFlowLog.hookTriggered(point);
                    OneStepWorkspace current = controller;
                    if (current == null) {
                        OneStepFlowLog.hookBypassed(point, "workspace_controller_unavailable");
                    } else {
                        Object[] args = chain.getArgs().toArray();
                        boolean matched = notifyTaskViewTransition(current, name,
                                chain.getThisObject(), args);
                        OneStepFlowLog.hookMatched(point, matched,
                                matched ? "workspace_task_view" : "foreign_task_view");
                        OneStepFlowLog.hookEffective(point, matched,
                                matched ? "workspace_callback_dispatched" : "callback_ignored");
                    }
                    return result;
                });
            }
        } catch (ClassNotFoundException ignored) { }
        catch (Throwable error) {
            OneStepFlowLog.i(TAG, "TaskView transition callback hook unavailable");
            OneStepFlowLog.hookUnavailable("TaskViewTransitions", error);
        }
    }

    private static boolean notifyTaskViewTransition(OneStepWorkspace workspace, String name,
                                                 Object owner, Object[] args) {
        if (args.length == 0 || args[0] == null
                || !"TaskViewTaskController".equals(args[0].getClass().getSimpleName())) {
            Object[] withController = new Object[args.length + 1];
            withController[0] = owner;
            System.arraycopy(args, 0, withController, 1, args.length);
            args = withController;
        }
        return workspace.onTaskViewTransition(name, args);
    }

    private static void attach() {
        if (shellTransitions == null || taskViewFactory == null || taskDisplayAreas == null) return;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (controller != null) return;
            Object source = ReflectUtils.getField(shellTransitions, "mContext");
            if (!(source instanceof Context)) return;
            Context context = (Context) source;
            try {
                OneStepWorkspace current = new OneStepWorkspace(context, handler,
                        shellTransitions, taskViewFactory, taskDisplayAreas);
                registerEnvironment(context, handler);
                controller = current;
                OneStepFlowLog.hookTriggered("OneStepWorkspace.attach");
                registerWorkspaceTriggerReceiver(context, handler);
                OneStepFlowLog.i(TAG, "OneStep Shell workspace ready in SystemUI");
            } catch (Throwable e) { OneStepFlowLog.w(TAG, "Cannot initialize OneStep workspace", e); }
        });
    }

    private static void registerWorkspaceTriggerReceiver(Context context, Handler handler) {
        if (workspaceTriggerReceiverInstalled) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (!OneStepWorkspaceProtocol.ACTION_OPEN.equals(intent.getAction())) return;
                OneStepFlowLog.i(TAG, "URL scheme workspace request received");
                handler.postDelayed(() -> {
                    OneStepWorkspace current = controller;
                    if (current == null || !installed || !current.canTrigger()) {
                        OneStepFlowLog.i(TAG, "URL scheme workspace request ignored: unavailable");
                        return;
                    }
                    current.useSideGestureProfile();
                    current.toggle(false, null);
                }, 250);
            }
        };
        context.registerReceiver(receiver,
                new IntentFilter(OneStepWorkspaceProtocol.ACTION_OPEN),
                Context.RECEIVER_EXPORTED);
        workspaceTriggerReceiverInstalled = true;
    }

    private static void registerEnvironment(Context context, Handler handler) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.USER_SWITCHED");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())
                        && "homekey".equals(intent.getStringExtra("reason"))) {
                    // Pressure Home injects keys through NavBarExt, bypassing toHome().
                    OneStepWorkspace current = controller;
                    if (current != null) current.exitToHome();
                    return;
                }
                refresh();
            }
        }, filter, null, handler, Context.RECEIVER_EXPORTED);
        context.registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration config) { refresh(); }
            @Override public void onLowMemory() { }
        });
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        manager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) { if (displayId == 0) refresh(); }
            @Override public void onDisplayRemoved(int displayId) { if (displayId == 0) refresh(); }
            @Override public void onDisplayChanged(int displayId) {
                if (displayId == 0) refresh();
            }
        }, handler);
    }

    static boolean environmentAllowed(Context context) {
        if (!installed) return false;
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled) return false;
        Object edge = edgeHandler.get();
        if (edge == null) return workspaceAllowed(context, -1);
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        long blocked = BLOCKED_FLAGS & ~262144L;
        return flags instanceof Number && (((Number) flags).longValue() & blocked) == 0
                && AssistantGestureScenes.current(edge) == SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL
                && workspaceAllowed(context, -1);
    }

    static boolean workspaceAllowed(Context context, int userId) {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        DisplayManager displays = context.getSystemService(DisplayManager.class);
        if (!installed || keyguard == null || keyguard.isKeyguardLocked()
                || power == null || !power.isInteractive() || displays == null || displays.getDisplay(0) == null)
            return false;
        if (userId >= 0) {
            try {
                if (currentUserId() != userId) return false;
            } catch (Exception error) { return false; }
        }
        return true;
    }

    static int currentUserId() throws ReflectiveOperationException {
        return (Integer) Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
    }

    static boolean shadeOpen() {
        Object edge = edgeHandler.get();
        int scene = AssistantGestureScenes.current(edge);
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        return scene == SettingsStore.ASSISTANT_GESTURE_SCENE_NOTIFICATION
                || scene == SettingsStore.ASSISTANT_GESTURE_SCENE_CONTROL_CENTER
                || (flags instanceof Number && (((Number) flags).longValue() & (4L | 2048L | 1073741824L)) != 0);
    }

    static boolean imeBusy() {
        Object edge = edgeHandler.get();
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        return imeVisible || imePositioning
                || (flags instanceof Number && (((Number) flags).longValue() & 262144L) != 0);
    }

}
