package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.view.SurfaceView;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Borrows existing tasks from Flyme's organizer. Never registers another organizer or a display. */
final class OneStepShell {
    private static final String TAG = "FlymeOneStepShell";
    interface Listener {
        void onReady(Host host);
        void onRemoved(Host host);
        void onFailure(String message, Exception error);
        void onExternalTransition();
        void onLaunchFailed(Host host);
        void onTaskReused(Host launching, Host existing);
    }

    final class Host {
        final SurfaceView view;
        final Object controller;
        volatile RecentTaskCard card;
        PendingIntent launchIntent;
        IBinder launchCookie;
        IBinder launchTransition;
        long launchStartedAt;
        Rect launchRestoreBounds;
        final Map<Integer, Object> launchSnapshots = new HashMap<>();
        final int session;
        volatile boolean ready;
        volatile boolean initialized;
        volatile boolean released;
        boolean launching;
        boolean prepared;
        int acquireAttempts;
        Object info;
        Object token;
        Object oldListener;
        Object oldSpecificListener;
        SurfaceControl leash;
        Rect originalBounds;
        Point originalPosition;
        int originalMode;
        boolean originalFocusable;
        boolean originalAlwaysOnTop;
        volatile boolean commitPending;
        int returnParent = -1;
        int returnDisplay;
        boolean externalReturn;
        Rect logicalBounds = new Rect();
        int width;
        int height;
        boolean main;
        boolean borrowed;
        boolean restoring;
        boolean notified;
        boolean collected;
        boolean requireFocused;

        boolean home() { return card != null && card.home; }

        Host(Context context, RecentTaskCard card, int session, boolean requireFocused)
                throws ReflectiveOperationException {
            this.card = card;
            this.session = session;
            this.requireFocused = requireFocused;
            controller = controllerClass.getConstructor(Context.class, organizerClass, controllerInterface,
                    syncQueue.getClass()).newInstance(context, organizer, taskViewController, syncQueue);
            view = (SurfaceView) taskViewClass.getConstructor(Context.class, controllerInterface, controllerClass)
                    .newInstance(context, taskViewController, controller);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{viewListenerClass}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
                switch (method.getName()) {
                    case "onInitialized":
                        initialized = true;
                        executor.execute(() -> acquire(this));
                        break;
                    case "onTaskRemovalStarted":
                        executor.execute(() -> taskRemoved(this));
                        break;
                    case "onBackPressedOnTaskRoot":
                        // Only used on ROMs that intercept root back for embedded tasks.
                        requestExternalExit();
                        break;
                    default: break;
                }
                return null;
            });
            OneStepReflection.call(view, "setListener", new Class<?>[]{Executor.class, viewListenerClass},
                    (Executor) command -> ui.post(command), listener);
            view.setZOrderOnTop(false);
            if (android.os.Build.VERSION.SDK_INT >= 34) view.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        }

        void obscure(Rect rect) throws ReflectiveOperationException {
            OneStepReflection.call(view, "setObscuredTouchRect", new Class<?>[]{Rect.class}, rect);
        }
    }

    private final Handler ui;
    private final Context context;
    private final Object transitions;
    private final Object organizer;
    private final Object displayAreas;
    private final Object syncQueue;
    private final Executor executor;
    private final ClassLoader loader;
    private final Class<?> organizerClass;
    private final Class<?> controllerClass;
    private final Class<?> controllerInterface;
    private final Class<?> taskViewClass;
    private final Class<?> viewListenerClass;
    private final Class<?> taskListenerClass;
    private final Class<?> wctClass;
    private final Class<?> tokenClass;
    private final Class<?> transitionHandlerClass;
    private final Object taskViewController;
    private final Object transitionHandler;
    private final Object observer;
    private final SharedPreferences recovery;
    private final int bootCount;
    private final OneStepTaskAccess tasks;
    // Accessed only on the Shell executor, including task listener ownership changes.
    private final ArrayList<Host> hosts = new ArrayList<>();
    private final ArrayList<Host> retired = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger closeRequest = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<IBinder, Integer> pending = new HashMap<>();
    // Shell owns these transactions. Keep merged finishes too: they are applied after the
    // observer's onTransitionFinished callback and may otherwise undo the hosted parent.
    private final Map<IBinder, ArrayList<SurfaceControl.Transaction>> finishes = new HashMap<>();
    private final Map<Integer, JSONObject> journal = new HashMap<>();
    private volatile boolean initialized;
    private volatile boolean transitionBusy;
    private volatile boolean accepting;
    private volatile int session;
    private volatile int activityTaskId = -1;
    private volatile Object activityToken;
    private volatile int activityUserId;
    private Listener listener;
    private int initAttempts;

    OneStepShell(Context context, Handler ui, Object transitions, Object factory, Object displayAreas,
                 OneStepTaskAccess tasks) throws Exception {
        this.ui = ui;
        this.context = context;
        this.transitions = transitions;
        this.displayAreas = displayAreas;
        this.tasks = tasks;
        loader = context.getClassLoader();
        organizer = OneStepReflection.get(factory, "mTaskOrganizer");
        if (organizer != OneStepReflection.get(transitions, "mOrganizer")) {
            throw new IllegalStateException("TaskView and Transitions have different organizers");
        }
        syncQueue = OneStepReflection.get(factory, "mSyncQueue");
        executor = (Executor) OneStepReflection.call(organizer, "getExecutor");
        organizerClass = Class.forName("com.android.wm.shell.ShellTaskOrganizer", false, loader);
        controllerClass = Class.forName("com.android.wm.shell.taskview.TaskViewTaskController", false, loader);
        controllerInterface = Class.forName("com.android.wm.shell.taskview.TaskViewController", false, loader);
        taskViewClass = Class.forName("com.android.wm.shell.taskview.TaskView", false, loader);
        viewListenerClass = Class.forName("com.android.wm.shell.taskview.TaskView$Listener", false, loader);
        taskListenerClass = Class.forName("com.android.wm.shell.ShellTaskOrganizer$TaskListener", false, loader);
        wctClass = Class.forName("android.window.WindowContainerTransaction");
        tokenClass = Class.forName("android.window.WindowContainerToken");
        transitionHandlerClass = Class.forName("com.android.wm.shell.transition.Transitions$TransitionHandler", false, loader);
        recovery = context.getSharedPreferences("flyme_onestep_borrowed_tasks", Context.MODE_PRIVATE);
        bootCount = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        taskViewController = Proxy.newProxyInstance(loader, new Class<?>[]{controllerInterface}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
            if ("isUsingShellTransitions".equals(method.getName())) return true;
            if ("setTaskViewVisible".equals(method.getName())) {
                executor.execute(() -> surfaceChanged(args[0], (Boolean) args[1]));
            } else if ("moveTaskViewToFullscreen".equals(method.getName()) || "removeTaskView".equals(method.getName())) {
                requestExternalExit();
            }
            // Bounds belong to the workspace's stable logical viewport, not the thumbnail size.
            // Tasks are borrowed by identity; startActivity/removeTask are never used here.
            return null;
        });
        transitionHandler = Proxy.newProxyInstance(loader, new Class<?>[]{transitionHandlerClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
            try {
                switch (method.getName()) {
                    case "handleRequest":
                        Object trigger = OneStepReflection.call(args[1], "getTriggerTask");
                        int type = ((Number) OneStepReflection.call(args[1], "getType")).intValue();
                        Host requested = find(trigger);
                        // Leave PiP, split-screen, keyguard and mode-change requests to Flyme.
                        return requested != null && current(requested) && (type == 2 || type == 4)
                                ? transaction() : null;
                    case "startAnimation": return animate(args);
                    case "onTransitionConsumed":
                        pending.remove(args[0]);
                        transitionBusy = !pending.isEmpty();
                        if (args[2] != null) reattach((SurfaceControl.Transaction) args[2]);
                        if (pending.isEmpty()) retired.clear();
                        break;
                    // Our transitions finish synchronously; merges are left to the normal queue.
                    default: break;
                }
            } catch (Exception error) { fail("应用窗口转场失败", error); }
            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
        });
        Class<?> observerClass = Class.forName("com.android.wm.shell.transition.Transitions$TransitionObserver", false, loader);
        observer = Proxy.newProxyInstance(loader, new Class<?>[]{observerClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
            try {
                if ("onTransitionReady".equals(method.getName())) {
                    if (accepting || !pending.isEmpty()) {
                        finishes.computeIfAbsent((IBinder) args[0], key -> new ArrayList<>())
                                .add((SurfaceControl.Transaction) args[3]);
                    }
                    observeTransition(args);
                }
                else if ("onTransitionFinished".equals(method.getName())) {
                    pending.remove(args[0]);
                    transitionBusy = !pending.isEmpty();
                    ArrayList<SurfaceControl.Transaction> transactions = finishes.remove(args[0]);
                    if (transactions != null) {
                        for (SurfaceControl.Transaction tx : transactions) reattach(tx);
                    }
                    if (pending.isEmpty()) retired.clear();
                } else if ("onTransitionMerged".equals(method.getName())) {
                    Integer value = pending.remove(args[0]);
                    if (value != null) pending.put((IBinder) args[1], value);
                    ArrayList<SurfaceControl.Transaction> transactions = finishes.remove(args[0]);
                    if (transactions != null) {
                        finishes.computeIfAbsent((IBinder) args[1], key -> new ArrayList<>()).addAll(transactions);
                    }
                    transitionBusy = !pending.isEmpty();
                }
            } catch (Exception error) { fail("无法同步应用窗口", error); }
            return null;
        });
        // Resolve mutation entry points before the first task can be borrowed.
        OneStepReflection.method(wctClass, "setBounds", tokenClass, Rect.class);
        OneStepReflection.method(wctClass, "setWindowingMode", tokenClass, int.class);
        OneStepReflection.method(wctClass, "setFocusable", tokenClass, boolean.class);
        OneStepReflection.method(wctClass, "setForceTranslucent", tokenClass, boolean.class);
        OneStepReflection.method(wctClass, "setAlwaysOnTop", tokenClass, boolean.class);
        OneStepReflection.method(wctClass, "setExcludeImeInsets", tokenClass, boolean.class);
        OneStepReflection.method(SurfaceControl.Transaction.class, "setWindowCrop", SurfaceControl.class, Rect.class);
        executor.execute(() -> initialize(observerClass));
    }

    void setListener(Listener listener) { this.listener = listener; }
    boolean available() { return initialized && !accepting && !transitionBusy; }

    private void initialize(Class<?> observerClass) {
        try {
            if (rootSurface() == null || !Boolean.TRUE.equals(OneStepReflection.call(transitions, "isRegistered"))) {
                if (++initAttempts < 100) later(() -> initialize(observerClass), 100);
                return;
            }
            recover();
            OneStepReflection.call(transitions, "addHandler", new Class<?>[]{transitionHandlerClass}, transitionHandler);
            OneStepReflection.call(transitions, "registerObserver", new Class<?>[]{observerClass}, observer);
            initialized = true;
        } catch (Exception error) {
            Log.w(TAG, "Shell task hosting is unavailable", error);
            if (++initAttempts < 100) later(() -> initialize(observerClass), 500);
        }
    }

    void begin(int value, int userId) {
        if (!available()) throw new IllegalStateException("Shell workspace is not ready");
        closeRequest.incrementAndGet();
        session = value;
        activityTaskId = -1;
        activityToken = null;
        activityUserId = userId;
        accepting = true;
    }

    void attachActivity(int taskId, Runnable ready) {
        int request = session;
        executor.execute(() -> attachActivity(taskId, ready, request, 0));
    }

    private void attachActivity(int taskId, Runnable ready, int request, int attempt) {
        if (!accepting || request != session) return;
        try {
            Object appeared = appeared(taskId);
            if (appeared == null) {
                if (attempt >= 100) throw new IllegalStateException("Workspace task did not appear");
                later(() -> attachActivity(taskId, ready, request, attempt + 1), 50);
                return;
            }
            Object info = OneStepReflection.call(appeared, "getTaskInfo");
            if (!isActivityTask(info) || OneStepTaskAccess.display(info) != 0
                    || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1
                    || ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1) != 1)
                throw new IllegalStateException("Workspace must be an independent fullscreen Activity");
            activityToken = OneStepReflection.get(info, "token");
            activityTaskId = taskId;
            Object wct = transaction();
            bool(wct, "setForceTranslucent", activityToken, false);
            bool(wct, "setAlwaysOnTop", activityToken, false);
            reorder(wct, activityToken, true);
            OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
            ui.post(() -> { if (accepting && request == session) ready.run(); });
        } catch (Exception error) { fail("无法接管工作台窗口", error); }
    }

    boolean isActivityTask(Object info) {
        if (info == null || ReflectUtils.getIntField(info, "userId", -1) != activityUserId) return false;
        Object component = ReflectUtils.getField(info, "baseActivity");
        if (!(component instanceof ComponentName)) component = ReflectUtils.getField(info, "topActivity");
        if (!(component instanceof ComponentName) || !OneStepActivityProtocol.isActivity((ComponentName) component)) return false;
        return activityTaskId < 0 || (OneStepTaskAccess.taskId(info) == activityTaskId
                && activityToken != null
                && java.util.Objects.equals(ReflectUtils.invokeNoArg(activityToken, "asBinder"), OneStepTaskAccess.token(info)));
    }

    Host create(Context context, RecentTaskCard card, boolean focused) throws ReflectiveOperationException {
        if (!accepting) throw new IllegalStateException("Workspace is closing");
        Host host = new Host(context, card, session, focused);
        executor.execute(() -> {
            hosts.add(host);
            if (!current(host)) releaseView(host);
            else if (host.home()) later(() -> {
                if (current(host) && !host.ready) launchFailed(host,
                        new IllegalStateException("HOME surface attach timed out"));
            }, 10000);
        });
        return host;
    }

    Host createLaunch(Context context, PendingIntent intent, Rect restoreBounds) throws ReflectiveOperationException {
        if (!accepting) throw new IllegalStateException("Workspace is closing");
        Host host = new Host(context, null, session, false);
        host.launchIntent = intent;
        host.launchCookie = new Binder();
        host.launchRestoreBounds = new Rect(restoreBounds);
        executor.execute(() -> {
            hosts.add(host);
            if (!current(host)) releaseView(host);
            else later(() -> {
                if (current(host) && !host.ready) launchFailed(host,
                        new IllegalStateException("Launcher task attach timed out"));
            }, 10000);
        });
        return host;
    }

    void geometry(Host host, Rect logicalBounds, int width, int height, boolean main) {
        Rect bounds = new Rect(logicalBounds);
        executor.execute(() -> {
            if (!current(host)) return;
            boolean changed = !host.logicalBounds.equals(bounds);
            host.logicalBounds = bounds;
            host.width = width;
            host.height = height;
            host.main = main;
            try {
                if (host.borrowed) {
                    if (changed && !host.home()) {
                        Object wct = transaction();
                        bounds(wct, host.token, bounds);
                        submit(wct);
                    }
                    present(host, null, null);
                } else if (host.initialized) acquire(host);
            } catch (Exception error) { fail("无法调整应用窗口", error); }
        });
    }

    void focus(Host host) {
        executor.execute(() -> {
            if (!accepting || (host != null && (!current(host) || !host.borrowed))) return;
            try {
                // Change both roles in one WM transaction, with the new main task on top.
                // Separate disable/enable transitions can briefly focus Home or another app.
                Object wct = transaction();
                for (Host candidate : hosts) {
                    if (!current(candidate) || !candidate.borrowed) continue;
                    candidate.main = candidate == host;
                    bool(wct, "setFocusable", candidate.token, candidate.main);
                }
                arrangeTasks(wct, host);
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
            }
            catch (Exception error) { fail("无法聚焦应用窗口", error); }
        });
    }

    void release(Host host, Runnable finished) {
        executor.execute(() -> restoreOne(host, finished, 0));
    }

    void close(int focusTask, Runnable finished) {
        accepting = false;
        int request = closeRequest.incrementAndGet();
        executor.execute(() -> restoreAll(focusTask, finished, 0, request));
    }

    /** Called before native recents starts, on the same Shell executor as its transition handler. */
    void beforeRecents() {
        accepting = false;
        int request = closeRequest.incrementAndGet();
        executor.execute(() -> restoreAll(-1, null, 0, request));
    }

    private boolean current(Host host) {
        return host != null && accepting && session == host.session && !host.released && !host.restoring;
    }

    private void acquire(Host host) {
        if (!current(host) || host.borrowed || !host.initialized || host.logicalBounds.isEmpty()) return;
        try {
            if (host.card == null) {
                if (!host.launching) launch(host);
                return;
            }
            Object appeared = appeared(host.card.taskId);
            if (appeared == null) {
                if (host.home()) throw new IllegalStateException("HOME task disappeared");
                if (host.launchCookie != null) {
                    later(() -> acquire(host), 50);
                    return;
                }
                if (!host.launching) {
                    if (host.requireFocused) throw new IllegalStateException("Focused task disappeared");
                    host.launching = true;
                    Object recent = tasks.recentTaskInfo(host.card);
                    if (!matches(host.card, recent)) throw new IllegalStateException("Recent task disappeared");
                    remember(host, recent);
                    // A dormant task can briefly resume fullscreen before its organizer
                    // callback arrives. Make it non-occluding before that launch, with a
                    // journal entry covering failure before any TaskView owns the task.
                    save(host);
                    host.prepared = true;
                    Object wct = transaction();
                    bool(wct, "setForceTranslucent", host.token, true);
                    OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                    tasks.activate(host.card);
                }
                if (++host.acquireAttempts > 100) throw new IllegalStateException("Task did not appear in Shell");
                later(() -> acquire(host), 50);
                return;
            }
            Object info = OneStepReflection.call(appeared, "getTaskInfo");
            boolean eligible = host.home() ? OneStepTaskAccess.home(info) : OneStepTaskAccess.application(info);
            if (host.launchCookie != null) eligible = activityType(info) == 1
                    && (windowMode(info) == 1 || windowMode(info) == 6);
            if (!matches(host.card, info) || !eligible
                    || OneStepTaskAccess.display(info) != 0 || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1) {
                throw new IllegalStateException("Selected task changed identity or windowing mode");
            }
            if (host.requireFocused) {
                int focused = tasks.defaultFocusedTaskId();
                if (focused != host.card.taskId && focused != activityTaskId)
                    throw new IllegalStateException("Focused application changed before acquisition");
            }
            if (!host.prepared) remember(host, info);
            else host.info = info;
            host.leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
            if (!host.leash.isValid() || rootSurface() == null) throw new IllegalStateException("Task surface unavailable");
            save(host);
            synchronized (OneStepReflection.get(organizer, "mLock")) {
                SparseArray<Object> listeners = listeners();
                host.oldSpecificListener = listeners.get(host.card.taskId);
                host.oldListener = OneStepReflection.call(organizer, "getTaskListener",
                        new Class<?>[]{ActivityManager.RunningTaskInfo.class}, info);
                host.borrowed = true;
                if (host.oldListener != null) taskCallback(host.oldListener, "onTaskVanished", info, null);
                listeners.put(host.card.taskId, host.controller);
                taskCallback(host.controller, "onTaskAppeared", info, host.leash);
            }
            Object wct = transaction();
            // HOME stays a fullscreen HOME task. Only its surface is fitted into the pane.
            if (!host.home()) {
                mode(wct, host.token, 6);
                bounds(wct, host.token, host.logicalBounds);
            }
            bool(wct, "setFocusable", host.token, host.main);
            // All panes keep one logical viewport. Their disjoint screen rectangles are Surface
            // transforms, so WM must not occlude the other tasks at their overlapping bounds.
            bool(wct, "setForceTranslucent", host.token, true);
            bool(wct, "setAlwaysOnTop", host.token, false);
            // Flyme's InsetsPolicy reports an empty IME frame to this task and its children,
            // retaining keyboard visibility/control while avoiding app resize and adjustPan.
            // Keep this enabled throughout hosting, including main/side swaps.
            bool(wct, "setExcludeImeInsets", host.token, true);
            Host main = host;
            for (Host candidate : hosts) if (current(candidate) && candidate.borrowed && candidate.main) main = candidate;
            arrangeTasks(wct, main);
            if (host.launchCookie != null) {
                // The launch already has a Shell transition. Make the task non-occluding
                // before its start transaction can expose it above the opaque workspace.
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                host.collected = true;
                present(host, null, null);
            } else submit(wct);
            // The surface is made visible by the collected Shell transition, after relayout.
        } catch (Exception error) {
            if (host.launchCookie != null || host.home()) launchFailed(host, error);
            else fail("无法接管应用任务", error);
        }
    }

    private void launch(Host host) throws Exception {
        host.launching = true;
        host.launchStartedAt = SystemClock.uptimeMillis();
        for (Object info : tasks.roots()) host.launchSnapshots.put(OneStepTaskAccess.taskId(info), info);
        synchronized (OneStepReflection.get(organizer, "mLock")) {
            SparseArray<?> all = (SparseArray<?>) OneStepReflection.get(organizer, "mTasks");
            for (int i = 0; i < all.size(); i++) {
                Object info = OneStepReflection.call(all.valueAt(i), "getTaskInfo");
                host.launchSnapshots.put(OneStepTaskAccess.taskId(info), info);
            }
        }
        ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
        options.setLaunchDisplayId(0);
        options.setLaunchBounds(host.logicalBounds);
        OneStepReflection.call(options, "setLaunchWindowingMode", new Class<?>[]{int.class}, 6);
        OneStepReflection.call(options, "setLaunchCookie", new Class<?>[]{IBinder.class}, host.launchCookie);
        if (android.os.Build.VERSION.SDK_INT >= 34)
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        Object wct = transaction();
        OneStepReflection.call(wct, "sendPendingIntent", new Class<?>[]{PendingIntent.class, Intent.class, Bundle.class},
                host.launchIntent, null, options.toBundle());
        host.launchTransition = submit(wct, 1);
        later(() -> discoverLaunch(host), 50);
    }

    private void discoverLaunch(Host host) {
        if (!current(host) || host.borrowed) return;
        try {
            if (host.card == null) {
                ArrayList<Object> infos = new ArrayList<>();
                synchronized (OneStepReflection.get(organizer, "mLock")) {
                    SparseArray<?> all = (SparseArray<?>) OneStepReflection.get(organizer, "mTasks");
                    for (int i = 0; i < all.size(); i++) infos.add(OneStepReflection.call(all.valueAt(i), "getTaskInfo"));
                }
                for (Object info : infos) if (hasCookie(host, info)) { identifyLaunch(host, info); break; }
            }
            if (host.card != null) acquire(host);
            else later(() -> discoverLaunch(host), 50);
        } catch (Exception error) { launchFailed(host, error); }
    }

    private void identifyLaunch(Host host, Object info) throws Exception {
        if (host.card != null) return;
        Host existing = find(info);
        if (existing != null && existing != host) {
            // Deliver the launcher Intent normally, but keep one owner for a reused task.
            host.launching = false;
            restoreOne(host, () -> { if (listener != null) listener.onTaskReused(host, existing); }, 0);
            return;
        }
        if (OneStepTaskAccess.token(info) == null || baseComponent(info) == null
                || ReflectUtils.getIntField(info, "userId", -1) != activityUserId)
            throw new IllegalStateException("Launched application cannot be hosted");
        ComponentName component = baseComponent(info);
        host.card = new RecentTaskCard(OneStepTaskAccess.taskId(info), activityUserId,
                OneStepTaskAccess.token(info), component.getPackageName(), component);
        Object previous = host.launchSnapshots.get(host.card.taskId);
        if (previous != null && !matches(host.card, previous)) previous = null;
        remember(host, previous == null ? info : previous);
        if (previous == null) {
            host.originalMode = 1;
            host.originalBounds = new Rect(host.launchRestoreBounds);
            host.originalPosition = new Point();
        }
        host.info = info;
        host.prepared = true;
        save(host);
        host.launchSnapshots.clear();
        if (activityType(info) != 1 || OneStepTaskAccess.display(info) != 0
                || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1)
            throw new IllegalStateException("Launched application is not a standalone task");
        Object wct = transaction();
        bool(wct, "setForceTranslucent", host.token, true);
        OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
    }

    static boolean hasCookie(Host host, Object info) {
        Object cookies = ReflectUtils.getField(info, "launchCookies");
        return host.launchCookie != null && cookies instanceof List && ((List<?>) cookies).contains(host.launchCookie);
    }

    private static int activityType(Object info) {
        Object config = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(config, "getActivityType", -1);
    }

    private static int windowMode(Object info) { return ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1); }

    private void launchFailed(Host host, Exception error) {
        if (!current(host)) return;
        Log.w(TAG, "Cannot attach launcher application", error);
        restoreOne(host, () -> { if (listener != null) listener.onLaunchFailed(host); }, 0);
    }

    private void remember(Host host, Object info) throws ReflectiveOperationException {
        host.info = info;
        host.token = OneStepReflection.get(info, "token");
        Object config = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
        host.originalBounds = new Rect((Rect) OneStepReflection.call(config, "getBounds"));
        host.originalMode = ((Number) OneStepReflection.call(config, "getWindowingMode")).intValue();
        Object position = OneStepReflection.get(info, "positionInParent");
        host.originalPosition = position instanceof Point ? new Point((Point) position) : new Point();
        host.originalFocusable = ReflectUtils.getBooleanField(info, "isFocusable", true);
        host.originalAlwaysOnTop = Boolean.TRUE.equals(OneStepReflection.call(config, "isAlwaysOnTop"));
    }

    private void surfaceChanged(Object controller, boolean visible) {
        for (Host host : new ArrayList<>(hosts)) {
            if (host.controller != controller || !current(host)) continue;
            if (!visible) {
                fail("应用窗口表面已断开", new IllegalStateException("TaskView surface destroyed"));
            } else if (host.borrowed) {
                try { present(host, null, null); }
                catch (Exception error) { fail("无法连接应用画面", error); }
            }
        }
    }

    private void taskRemoved(Host host) {
        if (host.restoring || host.released) return;
        if (host.card == null) { launchFailed(host, new IllegalStateException("Launching task disappeared")); return; }
        try {
            Object appeared = appeared(host.card.taskId);
            Object info = appeared == null ? null : OneStepReflection.call(appeared, "getTaskInfo");
            if (matches(host.card, info)) {
                // A listener handoff or window-mode conversion is not an application death.
                requestExternalExit();
                return;
            }
            restoreOne(host, () -> { if (listener != null) listener.onRemoved(host); }, 0);
        } catch (Exception error) { fail("无法处理应用退出", error); }
    }

    private boolean animate(Object[] args) throws Exception {
        List<?> changes = (List<?>) OneStepReflection.call(args[1], "getChanges");
        boolean ours = pending.containsKey(args[0]);
        boolean allOurs = true;
        for (Object change : changes) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            if (info != null && find(info) == null) allOurs = false;
        }
        if (!ours && (!allOurs || hosts.isEmpty())) return false;
        SurfaceControl.Transaction start = (SurfaceControl.Transaction) args[2];
        SurfaceControl.Transaction finish = (SurfaceControl.Transaction) args[3];
        boolean handled = false;
        for (Object change : changes) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            Host host = find(info);
            if (host == null && ours) {
                for (Host launching : hosts) {
                    if (!current(launching) || launching.borrowed || !hasCookie(launching, info)) continue;
                    SurfaceControl parent = (SurfaceControl) OneStepReflection.call(launching.controller, "getSurfaceControl");
                    SurfaceControl surface = (SurfaceControl) OneStepReflection.call(change, "getLeash");
                    if (parent != null && parent.isValid() && surface != null && surface.isValid()) {
                        placeSurface(launching, surface, parent, start);
                        placeSurface(launching, surface, parent, finish);
                        handled = true;
                    }
                    break;
                }
            }
            if (host == null && ours) {
                for (Host returned : retired) if (matches(returned.card, info)) {
                    SurfaceControl surface = (SurfaceControl) OneStepReflection.call(change, "getLeash");
                    returnSurface(returned, surface, start);
                    returnSurface(returned, surface, finish);
                    handled = true;
                    break;
                }
            }
            if (host == null) continue;
            int changeMode = ((Number) OneStepReflection.call(change, "getMode")).intValue();
            if (changeMode == 2) continue; // Let WindowManager finish removing this task.
            if (!current(host)) continue;
            host.info = info;
            host.collected = true;
            // Keep the organizer's lifetime-owned leash. TransitionInfo handles are temporary.
            present(host, start, finish);
            handled = true;
        }
        if (!ours && !handled) return false;
        pending.remove(args[0]);
        transitionBusy = !pending.isEmpty();
        start.apply();
        OneStepReflection.call(args[4], "onTransitionFinished", new Class<?>[]{wctClass}, (Object) null);
        return true;
    }

    private void observeTransition(Object[] args) throws Exception {
        if (!accepting) return;
        for (Object change : (List<?>) OneStepReflection.call(args[1], "getChanges")) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            if (info == null) continue;
            // The opaque host is part of this session. Its launch/reorder is not an
            // external app switch; onStop and Binder death handle actual host loss.
            if (isActivityTask(info)) continue;
            for (Host candidate : new ArrayList<>(hosts)) {
                if (!current(candidate) || candidate.borrowed || !hasCookie(candidate, info)) continue;
                try { identifyLaunch(candidate, info); acquire(candidate); }
                catch (Exception error) { launchFailed(candidate, error); }
            }
            Host host = find(info);
            boolean acquiring = false;
            for (Host candidate : hosts) {
                if (current(candidate) && !candidate.borrowed && candidate.launching && matches(candidate.card, info)) {
                    acquiring = true;
                    break;
                }
            }
            if (acquiring) continue;
            int mode = ((Number) OneStepReflection.call(change, "getMode")).intValue();
            int windowMode = ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1);
            if (host != null && (windowMode != (host.home() ? 1 : 6) || OneStepTaskAccess.display(info) != 0
                    || ReflectUtils.getIntField(info, "parentTaskId", -1) != -1)) {
                // The system has already chosen a new container/mode. Preserve that choice.
                if (!pending.containsKey(args[0])) {
                    host.externalReturn = true;
                    host.originalMode = windowMode;
                    Object config = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
                    host.originalBounds = new Rect((Rect) OneStepReflection.call(config, "getBounds"));
                    host.originalPosition = new Point((Point) OneStepReflection.get(info, "positionInParent"));
                    host.originalAlwaysOnTop = Boolean.TRUE.equals(OneStepReflection.call(config, "isAlwaysOnTop"));
                    host.returnParent = ReflectUtils.getIntField(info, "parentTaskId", -1);
                    host.returnDisplay = OneStepTaskAccess.display(info);
                    requestExternalExit();
                    return;
                }
            }
            if (!pending.containsKey(args[0]) && host == null && OneStepTaskAccess.display(info) == 0
                    && (mode == 1 || mode == 3)) {
                // A separately launched task (including external pickers) continues normally.
                requestExternalExit();
                return;
            }
        }
    }

    private void requestExternalExit() {
        if (!accepting) return;
        accepting = false;
        int request = closeRequest.incrementAndGet();
        executor.execute(() -> restoreAll(-1, () -> {
            if (listener != null) listener.onExternalTransition();
        }, 0, request));
    }

    private void reattach(SurfaceControl.Transaction tx) throws Exception {
        for (Host host : hosts) {
            if (!current(host) || !host.borrowed || !host.collected) continue;
            SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
            if (parent != null && parent.isValid() && host.leash != null && host.leash.isValid()) {
                place(host, parent, tx);
            }
        }
    }

    private void present(Host host, SurfaceControl.Transaction start, SurfaceControl.Transaction finish) throws Exception {
        if (!current(host) || !host.borrowed || !host.collected || host.width <= 0 || host.height <= 0) return;
        SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
        if (parent == null || !parent.isValid() || host.leash == null || !host.leash.isValid()) return;
        OneStepReflection.call(host.controller, "prepareOpen",
                new Class<?>[]{ActivityManager.RunningTaskInfo.class, SurfaceControl.class}, host.info, host.leash);
        boolean own = start == null;
        SurfaceControl.Transaction tx = own ? new SurfaceControl.Transaction() : start;
        try {
            place(host, parent, tx);
            if (finish != null) place(host, parent, finish);
            if (!host.notified) {
                host.notified = true;
                OneStepReflection.call(host.controller, "notifyAppeared", new Class<?>[]{boolean.class}, true);
            }
            if (!host.ready && !host.commitPending) {
                host.commitPending = true;
                // This signals transaction commitment, not measured content FPS or presentation.
                tx.addTransactionCommittedListener(command -> ui.post(command), () -> {
                    host.commitPending = false;
                    if (accepting && session == host.session && !host.released && !host.restoring) {
                        host.ready = true;
                        if (listener != null) listener.onReady(host);
                    }
                });
            }
            if (own) tx.apply();
        } finally { if (own) tx.close(); }
    }

    private void place(Host host, SurfaceControl parent, SurfaceControl.Transaction tx) throws Exception {
        placeSurface(host, host.leash, parent, tx);
    }

    private void placeSurface(Host host, SurfaceControl surface, SurfaceControl parent, SurfaceControl.Transaction tx)
            throws Exception {
        Rect crop = new Rect(0, 0, host.logicalBounds.width(), host.logicalBounds.height());
        if (host.home()) crop.offset(host.logicalBounds.left - host.originalBounds.left,
                host.logicalBounds.top - host.originalBounds.top);
        float sx = host.width / (float) crop.width();
        float sy = host.height / (float) crop.height();
        tx.reparent(surface, parent).setPosition(surface, -crop.left * sx, -crop.top * sy)
                .setScale(surface, sx, sy)
                .setAlpha(surface, 1f).setVisibility(surface, true);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class},
                surface, crop);
        // A Surface frame-rate vote is a scheduling preference, not a per-app FPS cap.
        tx.setFrameRate(surface, host.main ? 120f : 30f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private void restoreOne(Host host, Runnable finished, int attempt) {
        host.restoring = true;
        try {
            restore(host);
            hosts.remove(host);
            if (finished != null) ui.post(finished);
        } catch (Exception error) {
            Log.w(TAG, "Task restoration will retry", error);
            later(() -> restoreOne(host, finished, attempt + 1), Math.min(5000, 200 + attempt * 400));
        }
    }

    private void restoreAll(int focusTask, Runnable finished, int attempt, int request) {
        if (request != closeRequest.get()) return;
        try {
            int retainFocus = -1;
            if (focusTask < 0) {
                try {
                    int focused = tasks.defaultFocusedTaskId();
                    boolean owned = false;
                    for (Host host : hosts) owned |= host.card != null && host.card.taskId == focused;
                    if (!owned && focused != activityTaskId) retainFocus = focused;
                } catch (Exception error) { Log.w(TAG, "Cannot remember external task focus", error); }
            }
            // Restore the selected task last, without stealing focus from Home/Recents/external apps.
            ArrayList<Host> order = new ArrayList<>(hosts);
            order.sort((a, b) -> Boolean.compare(a.card != null && a.card.taskId == focusTask,
                    b.card != null && b.card.taskId == focusTask));
            for (Host host : order) {
                host.restoring = true;
                restore(host);
                hosts.remove(host);
            }
            int selectedFocus = focusTask >= 0 ? focusTask : retainFocus;
            if (selectedFocus >= 0) {
                try { tasks.focusTask(selectedFocus); }
                catch (Exception error) { Log.w(TAG, "Restored task no longer accepts focus", error); }
            }
            if (finished != null) ui.post(() -> {
                if (request == closeRequest.get()) finished.run();
            });
        } catch (Exception error) {
            Log.w(TAG, "Workspace restoration will retry", error);
            later(() -> restoreAll(focusTask, finished, attempt + 1, request), Math.min(5000, 200 + attempt * 400));
        }
    }

    private void restore(Host host) throws Exception {
        if (host.released) return;
        if (host.launchCookie != null && host.launching && host.card == null) {
            // A close can race the organizer callback. Resolve the cookie before dropping
            // the placeholder, so a late application cannot be stranded in multiwindow.
            for (Object info : tasks.roots()) if (hasCookie(host, info)) { identifyLaunch(host, info); break; }
            if (host.card == null && (pending.containsKey(host.launchTransition)
                    || SystemClock.uptimeMillis() - host.launchStartedAt < 1500))
                throw new IllegalStateException("Waiting for launching task to settle");
        }
        if (host.borrowed) {
            Object appeared = appeared(host.card.taskId);
            Object info = appeared == null ? null : OneStepReflection.call(appeared, "getTaskInfo");
            if (matches(host.card, info)) {
                Object wct = transaction();
                mode(wct, host.token, host.originalMode);
                bounds(wct, host.token, host.originalBounds);
                bool(wct, "setFocusable", host.token, host.originalFocusable);
                bool(wct, "setForceTranslucent", host.token, false);
                bool(wct, "setAlwaysOnTop", host.token, host.originalAlwaysOnTop);
                bool(wct, "setExcludeImeInsets", host.token, false);
                if (accepting && host.session == session) {
                    // Replacing a side pane returns a fullscreen task. Keep it below the
                    // opaque Activity in the same transaction so it cannot stop our host.
                    Host main = null;
                    for (Host candidate : hosts)
                        if (current(candidate) && candidate.borrowed && candidate.main) main = candidate;
                    arrangeTasks(wct, main);
                }
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                SurfaceControl leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
                if (leash.isValid()) {
                    // A launch/swap finish can still be queued when the desktop is returned.
                    // Rewrite its previously recorded reparent before releasing the TaskView.
                    for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                        for (SurfaceControl.Transaction tx : transactions) returnSurface(host, leash, tx);
                    try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                        returnSurface(host, leash, tx);
                        tx.apply();
                    }
                }
                // Restore a coherent cached TaskInfo before handing it to the original listener.
                Object window = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
                OneStepReflection.call(window, "setWindowingMode", new Class<?>[]{int.class}, host.originalMode);
                OneStepReflection.call(window, "setBounds", new Class<?>[]{Rect.class}, host.originalBounds);
                synchronized (OneStepReflection.get(organizer, "mLock")) {
                    SparseArray<Object> listeners = listeners();
                    if (listeners.get(host.card.taskId) == host.controller) {
                        if (host.oldSpecificListener != null && !host.externalReturn) listeners.put(host.card.taskId, host.oldSpecificListener);
                        else listeners.remove(host.card.taskId);
                        Object restoredListener = OneStepReflection.call(organizer, "getTaskListener",
                                new Class<?>[]{ActivityManager.RunningTaskInfo.class}, info);
                        if (restoredListener != null) taskCallback(restoredListener, "onTaskAppeared", info, leash);
                    }
                }
            } else {
                synchronized (OneStepReflection.get(organizer, "mLock")) {
                    if (listeners().get(host.card.taskId) == host.controller) listeners().remove(host.card.taskId);
                }
            }
            forget(host.card.taskId);
            host.borrowed = false;
            if (!pending.isEmpty() && !retired.contains(host)) retired.add(host);
        } else {
            if (host.prepared) {
                Object wct = transaction();
                bool(wct, "setForceTranslucent", host.token, false);
                if (host.launchCookie != null) {
                    mode(wct, host.token, host.originalMode);
                    bounds(wct, host.token, host.originalBounds);
                    bool(wct, "setFocusable", host.token, host.originalFocusable);
                    bool(wct, "setAlwaysOnTop", host.token, host.originalAlwaysOnTop);
                    bool(wct, "setExcludeImeInsets", host.token, false);
                    if (accepting && host.session == session) {
                        Host main = null;
                        for (Host candidate : hosts)
                            if (current(candidate) && candidate.borrowed && candidate.main) main = candidate;
                        arrangeTasks(wct, main);
                    }
                }
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                if (host.launchCookie != null) {
                    Object appeared = appeared(host.card.taskId);
                    if (appeared != null && matches(host.card, OneStepReflection.call(appeared, "getTaskInfo"))) {
                        SurfaceControl leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
                        if (leash.isValid()) {
                            for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                                for (SurfaceControl.Transaction tx : transactions) returnSurface(host, leash, tx);
                            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                                returnSurface(host, leash, tx);
                                tx.apply();
                            }
                        }
                    } else if (pending.containsKey(host.launchTransition)) {
                        throw new IllegalStateException("Waiting for launch surface restoration");
                    }
                }
            }
            if (host.card != null && journal.containsKey(host.card.taskId)) forget(host.card.taskId);
        }
        host.prepared = false;
        releaseView(host);
    }

    private void returnSurface(Host host, SurfaceControl leash, SurfaceControl.Transaction tx) throws Exception {
        if (!leash.isValid()) return;
        SurfaceControl parent;
        if (host.returnParent >= 0) {
            Object parentInfo = appeared(host.returnParent);
            if (parentInfo == null) throw new IllegalStateException("Target task parent is unavailable");
            parent = (SurfaceControl) OneStepReflection.call(parentInfo, "getLeash");
        } else {
            parent = (SurfaceControl) ((SparseArray<?>) OneStepReflection.get(displayAreas, "mLeashes")).get(host.returnDisplay);
        }
        if (parent == null || !parent.isValid()) throw new IllegalStateException("Task display area unavailable");
        tx.reparent(leash, parent).setScale(leash, 1, 1)
                .setPosition(leash, host.originalPosition.x, host.originalPosition.y).setAlpha(leash, 1);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, leash, null);
        tx.setFrameRate(leash, 0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private void releaseView(Host host) {
        if (host.released) return;
        host.released = true;
        host.ready = false;
        // Reset before surfaceDestroyed can enqueue a hide for a task already returned to Android.
        try { OneStepReflection.call(host.controller, "resetTaskInfo"); }
        catch (Exception error) { Log.w(TAG, "Cannot clear TaskView controller", error); }
        ui.post(() -> {
            try { OneStepReflection.call(host.view, "release"); }
            catch (Exception error) { Log.w(TAG, "Cannot release TaskView", error); }
        });
    }

    private void submit(Object wct) throws Exception {
        submit(wct, 6);
    }

    private IBinder submit(Object wct, int type) throws Exception {
        IBinder token = (IBinder) OneStepReflection.call(transitions, "startTransition",
                new Class<?>[]{int.class, wctClass, transitionHandlerClass}, type, wct, transitionHandler);
        pending.put(token, session);
        transitionBusy = true;
        return token;
    }

    private Object transaction() throws ReflectiveOperationException { return wctClass.getConstructor().newInstance(); }
    private void mode(Object wct, Object token, int mode) throws ReflectiveOperationException {
        OneStepReflection.call(wct, "setWindowingMode", new Class<?>[]{tokenClass, int.class}, token, mode);
    }
    private void bounds(Object wct, Object token, Rect bounds) throws ReflectiveOperationException {
        OneStepReflection.call(wct, "setBounds", new Class<?>[]{tokenClass, Rect.class}, token, bounds);
    }
    private void bool(Object wct, String method, Object token, boolean value) throws ReflectiveOperationException {
        OneStepReflection.call(wct, method, new Class<?>[]{tokenClass, boolean.class}, token, value);
    }
    private void reorder(Object wct, Object token, boolean top) throws ReflectiveOperationException { bool(wct, "reorder", token, top); }

    private void arrangeTasks(Object wct, Host focused) throws ReflectiveOperationException {
        if (activityToken == null) throw new IllegalStateException("Workspace Activity is not attached");
        // Only the workspace panes are above this opaque fullscreen task. Their logical
        // viewports overlap, so they stay translucent to WM; the Activity below them
        // occludes unrelated apps and Home without hiding or freezing those processes.
        reorder(wct, activityToken, true);
        for (Host candidate : hosts) {
            if (current(candidate) && candidate.borrowed && candidate != focused)
                reorder(wct, candidate.token, true);
        }
        if (focused != null) reorder(wct, focused.token, true);
    }

    @SuppressWarnings("unchecked")
    private SparseArray<Object> listeners() throws ReflectiveOperationException {
        return (SparseArray<Object>) OneStepReflection.get(organizer, "mTaskListeners");
    }
    private Object appeared(int taskId) throws ReflectiveOperationException {
        synchronized (OneStepReflection.get(organizer, "mLock")) {
            return ((SparseArray<?>) OneStepReflection.get(organizer, "mTasks")).get(taskId);
        }
    }
    private SurfaceControl rootSurface() throws ReflectiveOperationException {
        SurfaceControl root = (SurfaceControl) ((SparseArray<?>) OneStepReflection.get(displayAreas, "mLeashes")).get(0);
        return root != null && root.isValid() ? root : null;
    }
    private void taskCallback(Object listener, String name, Object info, SurfaceControl surface) throws ReflectiveOperationException {
        if ("onTaskAppeared".equals(name)) {
            OneStepReflection.method(taskListenerClass, name, ActivityManager.RunningTaskInfo.class, SurfaceControl.class)
                    .invoke(listener, info, surface);
        } else {
            OneStepReflection.method(taskListenerClass, name, ActivityManager.RunningTaskInfo.class).invoke(listener, info);
        }
    }
    private Host find(Object info) {
        for (Host host : hosts) if (host.borrowed && !host.released && matches(host.card, info)) return host;
        return null;
    }
    private static boolean matches(RecentTaskCard card, Object info) {
        return card != null && info != null && card.taskId == OneStepTaskAccess.taskId(info)
                && card.userId == ReflectUtils.getIntField(info, "userId", -1)
                && card.token.equals(OneStepTaskAccess.token(info));
    }
    private void later(Runnable action, long delay) {
        try { OneStepReflection.call(executor, "executeDelayed", new Class<?>[]{Runnable.class, long.class}, action, delay); }
        catch (Exception error) { Log.w(TAG, "Shell executor stopped", error); }
    }
    private void fail(String message, Exception error) {
        ui.post(() -> { if (listener != null) listener.onFailure(message, error); });
    }
    private static Object objectMethod(Object proxy, String name, Object[] args) {
        if ("equals".equals(name)) return proxy == args[0];
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        return "FlymeOneStepShell";
    }

    private void save(Host host) throws Exception {
        JSONObject item = new JSONObject();
        item.put("task", host.card.taskId).put("user", host.card.userId).put("boot", bootCount);
        ComponentName base = baseComponent(host.info);
        if (base == null) throw new IllegalStateException("Cannot journal a task without its base component");
        item.put("component", base.flattenToString());
        item.put("mode", host.originalMode).put("bounds", host.originalBounds.flattenToString());
        item.put("focusable", host.originalFocusable).put("alwaysOnTop", host.originalAlwaysOnTop);
        item.put("x", host.originalPosition.x).put("y", host.originalPosition.y);
        item.put("imeInsetsExcluded", true);
        item.put("forceTranslucent", true);
        item.put("home", host.home());
        journal.put(host.card.taskId, item);
        persist();
    }
    private void forget(int taskId) throws Exception { journal.remove(taskId); persist(); }
    private void persist() throws Exception {
        JSONArray items = new JSONArray();
        for (JSONObject item : journal.values()) items.put(item);
        if (!recovery.edit().putString("tasks", items.toString()).commit()) {
            throw new IllegalStateException("Cannot persist borrowed task recovery state");
        }
    }
    private void recover() throws Exception {
        JSONArray items = new JSONArray(recovery.getString("tasks", "[]"));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (bootCount < 0 || item.optInt("boot", -2) != bootCount) continue;
            int id = item.getInt("task");
            Object appeared = appeared(id);
            if (appeared == null) continue;
            Object info = OneStepReflection.call(appeared, "getTaskInfo");
            ComponentName base = baseComponent(info);
            if (base == null || !base.flattenToString().equals(item.optString("component"))
                    || ReflectUtils.getIntField(info, "userId", -1) != item.getInt("user")) continue;
            Object token = OneStepReflection.get(info, "token");
            Object wct = transaction();
            boolean imeInsetsExcluded = item.optBoolean("imeInsetsExcluded", false);
            if (imeInsetsExcluded) bool(wct, "setExcludeImeInsets", token, false);
            boolean forceTranslucent = item.optBoolean("forceTranslucent", false);
            if (forceTranslucent) bool(wct, "setForceTranslucent", token, false);
            Rect bounds = Rect.unflattenFromString(item.optString("bounds", ""));
            boolean home = item.optBoolean("home", false) && OneStepTaskAccess.home(info);
            if (OneStepTaskAccess.display(info) != 0
                    || (!home && ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1) != 6) || bounds == null) {
                // The task may have left the workspace, or SystemUI died between preparing
                // a dormant task and acquiring it. Clear our flags without changing its mode.
                if (imeInsetsExcluded || forceTranslucent) {
                    OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                }
                continue;
            }
            mode(wct, token, item.getInt("mode"));
            bounds(wct, token, bounds);
            bool(wct, "setFocusable", token, item.optBoolean("focusable", true));
            bool(wct, "setForceTranslucent", token, false);
            bool(wct, "setAlwaysOnTop", token, item.optBoolean("alwaysOnTop", false));
            OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
            SurfaceControl leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
            if (leash.isValid()) {
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    tx.reparent(leash, rootSurface()).setScale(leash, 1, 1)
                            .setPosition(leash, item.optInt("x", 0), item.optInt("y", 0)).setAlpha(leash, 1);
                    OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, leash, null);
                    tx.setFrameRate(leash, 0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                    tx.apply();
                }
            }
        }
        journal.clear();
        persist();
    }

    private static ComponentName baseComponent(Object info) {
        Object base = ReflectUtils.getField(info, "baseActivity");
        if (base instanceof ComponentName) return (ComponentName) base;
        Object intent = ReflectUtils.getField(info, "baseIntent");
        return intent instanceof android.content.Intent ? ((android.content.Intent) intent).getComponent() : null;
    }
}
