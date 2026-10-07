package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityManager;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Flyme Android 16 Shell integration. Missing hook points leave native back untouched. */
public final class OneHandedTaskHooks {
    static final String TAG = "FlymeTaskScale";
    // Values verified against this ROM's QuickStepContract.getSystemUiStateString().
    private static final long BLOCKED_FLAGS = 1L | 4L | 8L | 64L | 512L | 2048L
            | 16384L | 32768L | 65536L | 262144L | 524288L | 2097152L
            | 8388608L | 67108864L | 134217728L | 536870912L | 2147483648L
            | 34359738368L;
    private static volatile TaskScaleController controller;
    private static volatile Object attachedTransitions;
    private static volatile Object backAnimation;
    private static volatile WeakReference<Object> edgeHandler = new WeakReference<>(null);
    private static volatile boolean installed;
    private static volatile boolean imeVisible;
    private static volatile boolean imeTracking;
    private static volatile int imeTop = TaskScaleImeInsets.HIDDEN;
    private static volatile boolean dismissingIme;

    public static final SideGestureActions.Action ACTION = new SideGestureActions.Action() {
        @Override public boolean isReady() {
            TaskScaleController current = controller;
            return installed && current != null && current.canTrigger();
        }

        @Override public void execute(boolean fromLeft, Runnable onSuccess) {
            TaskScaleController current = controller;
            if (installed && current != null) current.toggle(onSuccess);
        }
    };

    private OneHandedTaskHooks() {}

    public static void trackEdgeHandler(Object edge) {
        if (ReflectUtils.getIntField(edge, "mDisplayId", -1) == 0) {
            edgeHandler = new WeakReference<>(edge);
        }
    }

    public static void refresh() {
        TaskScaleController current = controller;
        if (current != null) current.refresh();
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (Build.VERSION.SDK_INT < 33 || installed) return;
        try {
            Class<?> transitions = Class.forName("com.android.wm.shell.transition.Transitions", false, loader);
            Class<?> organizer = Class.forName("com.android.wm.shell.ShellTaskOrganizer", false, loader);
            Class<?> back = Class.forName("com.android.wm.shell.back.BackAnimationController", false, loader);
            Class<?> commandQueue = Class.forName("com.android.systemui.statusbar.CommandQueue", false, loader);
            Class<?> sysUi = Class.forName("com.android.systemui.model.SysUiStateImpl", false, loader);
            Class<?> recents = Class.forName("com.android.wm.shell.recents.RecentsTransitionHandler", false, loader);
            // Resolve required signatures before installing anything.
            Method request = named(transitions, "requestStartTransition");
            Method ready = named(transitions, "onTransitionReady");
            Method processQueue = named(transitions, "processReadyQueue");
            Method gestureStarted = back.getDeclaredMethod("onGestureStarted", float.class, float.class, int.class);
            Method finishBack = back.getDeclaredMethod("finishBackAnimation");
            Method ime = commandQueue.getDeclaredMethod("setImeWindowStatus", int.class, int.class, int.class, boolean.class);
            Method state = sysUi.getDeclaredMethod("notifyAndSetSystemUiStateChanged", long.class, long.class);
            Method changed = organizer.getDeclaredMethod("onTaskInfoChanged", ActivityManager.RunningTaskInfo.class);
            Method vanished = organizer.getDeclaredMethod("onTaskVanished", ActivityManager.RunningTaskInfo.class);
            Method startRecents = named(recents, "startRecentsTransition");
            for (Constructor<?> constructor : transitions.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    attach(chain.getThisObject());
                    return result;
                });
            }
            for (Constructor<?> constructor : back.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    backAnimation = chain.getThisObject();
                    return result;
                });
            }
            for (Method method : new Method[]{request, ready}) {
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    TaskScaleController current = controller;
                    if (current != null) current.suspend();
                    Object result = chain.proceed();
                    refresh();
                    return result;
                });
            }
            gestureStarted.setAccessible(true);
            module.intercept(gestureStarted, chain -> {
                dismissingIme = imeTracking && imeVisible;
                TaskScaleController current = controller;
                if (current != null && !dismissingIme) current.suspend();
                Object result = chain.proceed();
                refresh();
                return result;
            });
            for (Method method : new Method[]{processQueue, finishBack, changed}) {
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    if (method == finishBack) dismissingIme = false;
                    refresh();
                    return result;
                });
            }
            vanished.setAccessible(true);
            module.intercept(vanished, chain -> {
                TaskScaleController current = controller;
                if (current != null) current.taskVanished(
                        ReflectUtils.getIntField(chain.getArg(0), "taskId", -1));
                return chain.proceed();
            });
            startRecents.setAccessible(true);
            module.intercept(startRecents, chain -> {
                stop("recents started");
                return chain.proceed();
            });
            imeTracking = installImeTracking(module, loader);
            ime.setAccessible(true);
            module.intercept(ime, chain -> {
                if (((Integer) chain.getArg(0)) == 0) {
                    imeVisible = (((Integer) chain.getArg(1)) & 2) != 0;
                    if (imeVisible && !imeTracking) stop("IME insets unavailable");
                    else if (!imeVisible) updateImeTop(TaskScaleImeInsets.HIDDEN);
                }
                return chain.proceed();
            });
            state.setAccessible(true);
            module.intercept(state, chain -> {
                Object result = chain.proceed();
                if (ReflectUtils.invokeNoArgInt(chain.getThisObject(), "getDisplayId", -1) == 0
                        && ((((Number) chain.getArg(0)).longValue() & blockedFlags()) != 0)) {
                    stop("SystemUI state");
                }
                return result;
            });
            installed = true;
            Log.i(TAG, "Task scaling hooks installed");
        } catch (Throwable e) {
            installed = false;
            stop("hook initialization failed");
            Log.w(TAG, "Task scaling unavailable on this SystemUI", e);
        }
    }

    private static boolean installImeTracking(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            TaskScaleImeInsets reader = new TaskScaleImeInsets(loader);
            Class<?> perDisplay = Class.forName(
                    "com.android.wm.shell.common.DisplayInsetsController$PerDisplay", false, loader);
            for (String name : new String[]{"insetsChanged", "insetsControlChanged"}) {
                Method method = named(perDisplay, name);
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    if (ReflectUtils.getIntField(chain.getThisObject(), "mDisplayId", -1) == 0) {
                        try { updateImeTop(reader.top(chain.getArg(0))); }
                        catch (ReflectiveOperationException | RuntimeException e) {
                            stop("cannot read IME bounds");
                            Log.w(TAG, "Cannot read display IME insets", e);
                        }
                    }
                    return result;
                });
            }
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "IME avoidance unavailable; retain exit-on-keyboard fallback", e);
            return false;
        }
    }

    private static void updateImeTop(int top) {
        imeTop = top;
        TaskScaleController current = controller;
        if (current != null) current.imeChanged(top);
    }

    private static long blockedFlags() {
        return imeTracking ? BLOCKED_FLAGS & ~262144L : BLOCKED_FLAGS;
    }

    private static Method named(Class<?> type, String name) throws NoSuchMethodException {
        Method match = null;
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName())) continue;
            if (match != null) throw new NoSuchMethodException("Ambiguous " + name);
            match = method;
        }
        if (match == null) throw new NoSuchMethodException(name);
        return match;
    }

    private static void attach(Object transitions) {
        if (attachedTransitions == transitions) return;
        Object executor = ReflectUtils.getField(transitions, "mMainExecutor");
        Object sourceContext = ReflectUtils.getField(transitions, "mContext");
        if (!(executor instanceof Executor) || !(sourceContext instanceof Context)) return;
        attachedTransitions = transitions;
        ((Executor) executor).execute(() -> {
            try {
                if (controller != null) return;
                Context context = (Context) sourceContext;
                Handler handler = new Handler(Looper.myLooper());
                ShellTaskAccess backend = new ShellTaskAccess(context, transitions);
                TaskScaleRecentTasks recentTasks = createRecentTasks(context);
                RecentTaskCardsLoader cards = recentTasks == null ? null : new RecentTaskCardsLoader(
                        recentTasks, Executors.newSingleThreadExecutor(r -> new Thread(r, "FlymeTaskPreviews")), handler);
                TaskScaleOverlay overlay = new TaskScaleOverlay(context, () -> {
                    TaskScaleController current = controller;
                    if (current != null) current.dispatch(() -> current.exit(true, "outside tap"));
                }, cards, card -> {
                    TaskScaleController current = controller;
                    if (current != null && recentTasks != null) {
                        current.selectTask(card, () -> recentTasks.launch(card));
                    }
                });
                TaskScaleController current = new TaskScaleController(handler, backend, overlay);
                registerEnvironment(context, handler);
                controller = current;
                current.imeChanged(imeTop);
                Log.i(TAG, "Task scaling controller ready on " + Thread.currentThread().getName());
            } catch (Throwable e) {
                Log.w(TAG, "Cannot initialize task scaling controller", e);
            }
        });
    }

    private static TaskScaleRecentTasks createRecentTasks(Context context) {
        try { return new TaskScaleRecentTasks(context); }
        catch (Throwable e) {
            // Optional preview APIs must not disable the already working task scaling feature.
            Log.w(TAG, "Recent task cards unavailable", e);
            return null;
        }
    }

    private static void registerEnvironment(Context context, Handler handler) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.USER_SWITCHED");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                    TaskScaleController current = controller;
                    if (current != null) current.systemDialogsClosed();
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
        TaskScaleController current = controller;
        if (current != null) current.stop(reason);
    }

    static boolean backAnimationIdle() {
        if (dismissingIme) return true;
        Object back = backAnimation;
        return back != null && !ReflectUtils.getBooleanField(back, "mBackGestureStarted", true)
                && !ReflectUtils.getBooleanField(back, "mPostCommitAnimationInProgress", true);
    }

    static boolean environmentAllowed(Context context) {
        if (!installed || (imeVisible && !imeTracking)) return false;
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled || !config.assistantGestureEnabled
                || config.sideGestureAction != SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE
                || (config.assistantGestureScenes & SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL) == 0) return false;
        Object edge = edgeHandler.get();
        Object flags = ReflectUtils.invokeNoArg(ReflectUtils.getField(edge, "mSysUiState"), "getFlags");
        if (!(flags instanceof Number) || (((Number) flags).longValue() & blockedFlags()) != 0
                || AssistantGestureScenes.current(edge) != SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL) return false;
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        return keyguard != null && !keyguard.isKeyguardLocked() && power != null && power.isInteractive();
    }
}
