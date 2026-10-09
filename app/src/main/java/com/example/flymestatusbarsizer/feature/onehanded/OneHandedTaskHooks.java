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
import android.util.Log;
import android.view.Display;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.feature.assistant.AssistantGestureScenes;
import com.example.flymestatusbarsizer.feature.assistant.SideGestureActions;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Initializes the live OneStep workspace through LSPosed in SystemUI. */
public final class OneHandedTaskHooks {
    static final String TAG = "FlymeTaskScale";
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

    public static final SideGestureActions.Action ACTION = new SideGestureActions.Action() {
        @Override public boolean isReady() {
            OneStepWorkspace current = controller;
            return installed && current != null && current.canTrigger();
        }

        @Override public void execute(boolean fromLeft, Runnable onSuccess) {
            OneStepWorkspace current = controller;
            if (installed && current != null) current.toggle(fromLeft, onSuccess);
        }
    };

    private OneHandedTaskHooks() {}

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
            installed = true;
            Log.i(TAG, "OneStep Shell task workspace hooks installed");
        } catch (Throwable e) {
            Log.w(TAG, "Cannot install OneStep workspace", e);
            return;
        }
        // Optional lifecycle hooks must not prevent initialization on another Flyme release.
        installImePositionObserver(module, loader);
        try {
            Class<?> recents = Class.forName("com.android.wm.shell.recents.RecentsTransitionHandler", false, loader);
            for (Method method : recents.getDeclaredMethods()) {
                if (!"startRecentsTransition".equals(method.getName())) continue;
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    OneStepWorkspace current = controller;
                    if (current != null) current.beforeRecents();
                    return chain.proceed();
                });
            }
        } catch (Throwable e) { Log.w(TAG, "Recents lifecycle hook unavailable", e); }
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
                        if (current != null && args != null && args.length > 0 && Integer.valueOf(0).equals(args[0])) {
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
                } catch (Exception error) { Log.w(TAG, "Cannot observe Shell IME positioning", error); }
            });
        } catch (Throwable error) { Log.w(TAG, "Shell IME positioning unavailable", error); }
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
                Log.i(TAG, "OneStep Shell workspace ready in SystemUI");
            } catch (Throwable e) { Log.w(TAG, "Cannot initialize OneStep workspace", e); }
        });
    }

    private static void registerEnvironment(Context context, Handler handler) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.USER_SWITCHED");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                    String reason = intent.getStringExtra("reason");
                    if ("homekey".equals(reason) || "recentapps".equals(reason)
                            || "globalactions".equals(reason)) stop(reason);
                } else stop(intent.getAction());
            }
        }, filter, null, handler, Context.RECEIVER_EXPORTED);
        context.registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration config) { stop("configuration changed"); }
            @Override public void onLowMemory() { stop("low memory"); }
        });
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        manager.registerDisplayListener(new DisplayManager.DisplayListener() {
            int rotation = manager.getDisplay(0).getRotation();
            @Override public void onDisplayAdded(int displayId) {}
            @Override public void onDisplayRemoved(int displayId) { if (displayId == 0) stop("display removed"); }
            @Override public void onDisplayChanged(int displayId) {
                if (displayId != 0) return;
                Display display = manager.getDisplay(0);
                if (display == null || display.getRotation() != rotation) {
                    stop("rotation changed");
                    if (display != null) rotation = display.getRotation();
                }
            }
        }, handler);
    }

    private static void stop(String reason) {
        OneStepWorkspace current = controller;
        if (current != null) current.stop(reason);
    }

    static boolean environmentAllowed(Context context) {
        return environmentAllowed(context, false);
    }

    static boolean workspaceAllowed(Context context) {
        return environmentAllowed(context, true);
    }

    static boolean shadeOpen() {
        Object edge = edgeHandler.get();
        int scene = AssistantGestureScenes.current(edge);
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        return scene == SettingsStore.ASSISTANT_GESTURE_SCENE_NOTIFICATION
                || scene == SettingsStore.ASSISTANT_GESTURE_SCENE_CONTROL_CENTER
                || (flags instanceof Number && (((Number) flags).longValue() & (4L | 2048L | 1073741824L)) != 0);
    }

    private static boolean environmentAllowed(Context context, boolean continuing) {
        if (!installed) return false;
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled || !config.assistantGestureEnabled
                || config.sideGestureAction != SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE
                || (config.assistantGestureScenes & SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL) == 0) return false;
        Object edge = edgeHandler.get();
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        // Opening still requires the normal app scene. A running workspace survives the shade,
        // control center and their dialogs, while keyguard/sleep/user-switch protection remains.
        long blocked = BLOCKED_FLAGS & ~262144L;
        if (continuing) blocked &= ~(4L | 2048L | 32768L);
        if (!(flags instanceof Number) || (((Number) flags).longValue() & blocked) != 0
                || (!continuing && AssistantGestureScenes.current(edge)
                != SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL)) return false;
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        return keyguard != null && !keyguard.isKeyguardLocked() && power != null && power.isInteractive();
    }
}
