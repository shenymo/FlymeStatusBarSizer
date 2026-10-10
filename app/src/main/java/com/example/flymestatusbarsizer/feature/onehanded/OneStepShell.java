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
import android.graphics.RectF;
import android.os.Handler;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
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
        void onLaunchFailed(Host host);
        void onTaskReused(Host launching, Host existing);
        void onNavigation(Navigation navigation);
        void onCoverageChanged(boolean covered);
        void onTaskChanged(Host host, RecentTaskCard previous);
        void onImeRoutingChanged(IBinder task, int placement);
        void onWorkspaceTransitionCancelled();
    }

    final class Host {
        final SurfaceView view;
        final Object controller;
        // UI-thread state belongs to this TaskView, including while it replaces Home.
        private float appliedSurfaceRadius = -1;
        private boolean surfaceCornersUnavailable;
        volatile RecentTaskCard card;
        PendingIntent launchIntent;
        IBinder launchCookie;
        IBinder launchTransition;
        Host launchHome;
        OneStepLaunchAnimation.Remote launchAnimation;
        volatile boolean animationPending;
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
        // TaskView and launcher animation APIs require the organizer's RunningTaskInfo.
        ActivityManager.RunningTaskInfo runningInfo;
        // RootTaskInfo queries only refresh the viewport/mode snapshot, never runningInfo.
        final Rect actualBounds = new Rect();
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
        boolean backIntercepted;
        boolean restoring;
        boolean homeBehindPending;
        boolean homeBehindReady;
        boolean notified;
        boolean collected;
        boolean requireFocused;
        boolean coveredByLaunch;
        boolean openingRevealed;
        volatile boolean closing;
        boolean replacement;
        Navigation navigation;
        boolean surfaceMissing;
        boolean fullscreenCompat;
        boolean imeRegistered;
        boolean workspaceRestorePending;
        boolean workspaceRestoreCommitted;
        String lastImeTaskState;
        String lastImeModeRequest;

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
                        executor.execute(() -> {
                            if (current(this) && ready && main && !home() && !closing) restoreOne(this,
                                    () -> { if (OneStepShell.this.listener != null)
                                        OneStepShell.this.listener.onRemoved(this); }, 0);
                        });
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

        void setCornerRadius(float radius) {
            if (surfaceCornersUnavailable || appliedSurfaceRadius == radius) return;
            try {
                // SurfaceView rounds both the compositor crop and the window hole.
                OneStepReflection.call(view, "setCornerRadius", new Class<?>[]{float.class}, radius);
                appliedSurfaceRadius = radius;
            } catch (ReflectiveOperationException | RuntimeException error) {
                surfaceCornersUnavailable = true;
                Log.w(TAG, "Task surface corner radius unavailable", error);
            }
        }
    }

    final class Navigation {
        final RecentTaskCard card;
        final Host source;
        final int generation = session;
        final Object info;
        final Point position;
        SurfaceControl leash;
        volatile boolean cancelled;
        boolean staged;
        Host target;

        Navigation(RecentTaskCard card, Host source, Object info) {
            this.card = card;
            this.source = source;
            this.info = info;
            Object point = ReflectUtils.getField(info, "positionInParent");
            position = point instanceof Point ? new Point((Point) point) : new Point();
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
    private final java.util.concurrent.atomic.AtomicInteger activityRequest = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<IBinder, Integer> pending = new HashMap<>();
    private final Map<Object, Navigation> incoming = new HashMap<>();
    private final java.util.HashSet<Object> declined = new java.util.HashSet<>();
    // Shell owns these transactions. Keep merged finishes too: they are applied after the
    // observer's onTransitionFinished callback and may otherwise undo the hosted parent.
    private final Map<IBinder, ArrayList<SurfaceControl.Transaction>> finishes = new HashMap<>();
    private OpeningAnimation openingAnimation;
    private WaitingOpening waitingOpening;
    private OneStepWorkspaceTransition workspaceTransition;
    private int closingFocusTask = -1;
    private final Map<Integer, JSONObject> journal = new HashMap<>();
    private volatile boolean initialized;
    private volatile boolean transitionBusy;
    private volatile boolean accepting;
    private volatile boolean covered;
    private volatile boolean suspended;
    private volatile boolean hostVisible = true;
    private volatile int session;
    private volatile int activityTaskId = -1;
    private volatile Object activityToken;
    private volatile int activityUserId;
    private OneStepImePolicy.Session imeSession;
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
                boolean fullscreen = "moveTaskViewToFullscreen".equals(method.getName());
                executor.execute(() -> {
                    for (Host host : new ArrayList<>(hosts)) {
                        if (host.controller != args[0] || !current(host)) continue;
                        if (fullscreen) focus(mainHost());
                        else restoreOne(host, () -> { if (listener != null) listener.onRemoved(host); }, 0);
                        break;
                    }
                });
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
                        if (accepting && workspaceTransition != null && !workspaceTransition.exiting
                                && isActivityTask(trigger) && (type == 1 || type == 3)) {
                            pending.put((IBinder) args[0], session);
                            transitionBusy = true;
                            return transaction();
                        }
                        if (accepting && (type == 1 || type == 3)
                                && (requested == null || (current(requested) && !requested.main))) {
                            Navigation navigation = offerNavigation(trigger);
                            if (navigation != null) {
                                pending.put((IBinder) args[0], session);
                                transitionBusy = true;
                                return transaction();
                            }
                        }
                        // Leave PiP, split-screen, keyguard and mode-change requests to Flyme.
                        if (requested != null && current(requested) && type == 2 && taskEnded(trigger)) {
                            requested.closing = true;
                            pending.put((IBinder) args[0], session);
                            transitionBusy = true;
                            Object wct = transaction();
                            arrangeTasks(wct, mainHost());
                            Log.i(TAG, "Hosted task closing: task=" + requested.card.taskId);
                            return wct;
                        }
                        return requested != null && current(requested) && (type == 2 || type == 4)
                                ? transaction() : null;
                    case "startAnimation": return animate(args);
                    case "onTransitionConsumed":
                        if (waitingOpening != null && waitingOpening.args[0].equals(args[0]))
                            finishWaitingOpening(waitingOpening, false);
                        if (openingAnimation != null && openingAnimation.transition.equals(args[0]))
                            finishOpeningAnimation(openingAnimation, true, false);
                        for (Host host : hosts) if (host.launchAnimation != null && args[0].equals(host.launchTransition)) {
                            cancelLaunchAnimation(host);
                            present(host, null, null);
                        }
                        pending.remove(args[0]);
                        transitionBusy = !pending.isEmpty();
                        if (args[2] != null) reattach((SurfaceControl.Transaction) args[2]);
                        if (pending.isEmpty()) retired.clear();
                        break;
                    // Let Shell queue other transitions until the native opening animation finishes.
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

    private void logImeTask(Host host, String event, Object info, boolean force) {
        try {
            String snapshot = "main=" + host.main + " " + OneStepImeDiagnostics.taskInfo(info);
            if (!force && snapshot.equals(host.lastImeTaskState)) return;
            host.lastImeTaskState = snapshot;
            Log.i(OneStepImeDiagnostics.TAG, "task session=" + host.session + " event=" + event
                    + " expectedMode=" + (host.home() || host.fullscreenCompat ? 1 : 6)
                    + " fullscreenCompat=" + host.fullscreenCompat + " " + snapshot);
        } catch (Throwable error) { OneStepImeDiagnostics.unavailable(error); }
    }

    private void configureHostedMode(Object wct, Host host, String event, Object info) throws Exception {
        if (host.home()) return;
        updateHostedConfiguration(host, info, false);
        if (host.fullscreenCompat) {
            logImeTask(host, "fullscreen-compatible", info, false);
            return;
        }
        mode(wct, host.token, 6);
        bounds(wct, host.token, host.logicalBounds);
        try {
            String snapshot = "event=" + event + " requestedMode=" + (host.home() ? 1 : 6)
                    + " requestedBounds=" + host.logicalBounds + " before={" + OneStepImeDiagnostics.taskInfo(info) + "}";
            if (snapshot.equals(host.lastImeModeRequest)) return;
            host.lastImeModeRequest = snapshot;
            Log.i(OneStepImeDiagnostics.TAG, "mode-request session=" + host.session + " " + snapshot);
            // applyTransaction returning does not establish that WM accepted the mode.
            // Read fresh server TaskInfo after the transaction, not the organizer cache.
            later(() -> {
                if (!current(host) || host.closing || host.restoring) return;
                try {
                    for (Object actual : tasks.roots()) {
                        if (matches(host.card, actual)) {
                            updateHostedConfiguration(host, actual, true);
                            logImeTask(host, "after-" + event, actual, true);
                            present(host, null, null);
                            return;
                        }
                    }
                    logImeTask(host, "after-" + event, null, true);
                } catch (Throwable error) { OneStepImeDiagnostics.unavailable(error); }
            }, 500);
        } catch (Throwable error) { OneStepImeDiagnostics.unavailable(error); }
    }

    private void updateHostedConfiguration(Host host, Object info, boolean modeApplied)
            throws ReflectiveOperationException {
        Object config = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
        host.actualBounds.set((Rect) OneStepReflection.call(config, "getBounds"));
        if (host.home()) return;
        if (windowMode(info) == 6) host.fullscreenCompat = false;
        else if (windowMode(info) == 1 && (modeApplied
                || Boolean.FALSE.equals(ReflectUtils.getField(info, "supportsMultiWindow"))))
            host.fullscreenCompat = true;
    }

    void logImeTasks(int request, String event) {
        executor.execute(() -> {
            if (!accepting || request != session) return;
            try {
                for (Object info : tasks.roots()) {
                    Host host = find(info);
                    if (host != null && current(host) && !host.closing)
                        logImeTask(host, event, info, host.main);
                }
            } catch (Throwable error) { OneStepImeDiagnostics.unavailable(error); }
        });
    }

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
        covered = false;
        suspended = false;
        hostVisible = true;
        declined.clear();
        accepting = true;
        closingFocusTask = -1;
        Log.i(OneStepImeDiagnostics.TAG, "workspace-begin session=" + session + " user=" + userId);
    }

    void prepareWorkspaceEntry(RecentTaskCard card, Runnable ready) {
        int request = session;
        executor.execute(() -> {
            if (!accepting || request != session) return;
            try {
                Object appeared = appeared(card.taskId);
                Object info = appeared == null ? null : OneStepReflection.call(appeared, "getTaskInfo");
                if (!matches(card, info) || taskEnded(info) || suspended
                        || tasks.defaultFocusedTaskId() != card.taskId)
                    throw new IllegalStateException("Entry task is no longer in front");
                Object config = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
                Rect bounds = (Rect) OneStepReflection.call(config, "getBounds");
                Point position = (Point) OneStepReflection.get(info, "positionInParent");
                SurfaceControl leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
                workspaceTransition = new OneStepWorkspaceTransition(card, request, leash, rootSurface(), bounds, position);
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    placeWorkspaceTransition(tx);
                    tx.addTransactionCommittedListener(executor, () -> ui.post(() -> {
                        if (accepting && session == request && !suspended) ready.run();
                    }));
                    tx.apply();
                }
            } catch (Exception error) { fail("无法准备工作台过渡", error); }
        });
    }

    void prepareWorkspaceExit(Host host, RectF frame, float radius, Runnable ready) {
        int request = session;
        RectF start = new RectF(frame);
        executor.execute(() -> {
            if (!accepting || request != session) return;
            try {
                if (!current(host) || !host.ready || host.closing || suspended || covered || !hostVisible)
                    throw new IllegalStateException("Exit task is no longer visible");
                if (workspaceTransition == null) workspaceTransition = new OneStepWorkspaceTransition(
                        host.card, request, host.leash, rootSurface(), host.originalBounds, host.originalPosition);
                if (!workspaceTransition.owns(host)) throw new IllegalStateException("Another task owns the transition");
                workspaceTransition.exiting = true;
                workspaceTransition.frame.set(start);
                workspaceTransition.radius = radius;
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    placeWorkspaceTransition(tx);
                    tx.addTransactionCommittedListener(executor, () -> ui.post(() -> {
                        if (accepting && session == request && !suspended) ready.run();
                    }));
                    tx.apply();
                }
            } catch (Exception error) {
                Log.w(TAG, "Cannot stage workspace exit", error);
                cancelWorkspaceMotion();
            }
        });
    }

    void workspaceFrame(Host host, RectF frame, float radius) {
        RectF next = new RectF(frame);
        executor.execute(() -> {
            OneStepWorkspaceTransition motion = workspaceTransition;
            // close() can run on UI immediately after the last frame was queued. Let
            // that frame reach the fullscreen endpoint before the queued restoration.
            if (motion == null || !motion.owns(host) || motion.returning) return;
            try {
                motion.frame.set(next);
                motion.radius = radius;
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    placeWorkspaceTransition(tx);
                    tx.apply();
                }
            } catch (Exception error) {
                Log.w(TAG, "Cannot move workspace surface", error);
                cancelWorkspaceMotion();
            }
        });
    }

    void finishWorkspaceEntry(Host host, Runnable finished) {
        int request = session;
        executor.execute(() -> {
            if (!accepting || request != session || !current(host)) return;
            OneStepWorkspaceTransition motion = workspaceTransition;
            if (motion == null) { ui.post(finished); return; }
            if (!motion.owns(host) || motion.exiting) return;
            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
                if (parent == null || !parent.isValid() || host.surfaceMissing)
                    throw new IllegalStateException("Entry TaskView disappeared");
                workspaceTransition = null;
                place(host, parent, tx);
                for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                    for (SurfaceControl.Transaction finish : transactions) place(host, parent, finish);
                motion.remove(tx);
                tx.addTransactionCommittedListener(executor, () -> ui.post(() -> {
                    if (accepting && request == session) finished.run();
                }));
                tx.apply();
                motion.release();
            } catch (Exception error) {
                workspaceTransition = motion;
                fail("无法完成工作台进场", error);
            }
        });
    }

    private void cancelWorkspaceMotion() {
        int request = session;
        ui.post(() -> {
            if (request == session && listener != null) listener.onWorkspaceTransitionCancelled();
        });
    }

    private void placeWorkspaceTransition(SurfaceControl.Transaction tx) throws Exception {
        OneStepWorkspaceTransition motion = workspaceTransition;
        if (motion == null || motion.returning) return;
        Host owner = null;
        for (Host host : hosts) if (motion.owns(host) && host.borrowed) { owner = host; break; }
        Rect crop = owner == null ? new Rect(0, 0, motion.originalBounds.width(), motion.originalBounds.height())
                : surfaceCrop(owner);
        motion.place(tx, owner == null ? motion.surface : owner.leash, crop,
                !suspended && !covered && hostVisible);
    }

    void attachActivity(int taskId, Runnable ready) {
        int request = session;
        int binding = activityRequest.incrementAndGet();
        executor.execute(() -> attachActivity(taskId, ready, request, binding, 0));
    }

    private void attachActivity(int taskId, Runnable ready, int request, int binding, int attempt) {
        if (!accepting || request != session || binding != activityRequest.get()) return;
        try {
            if (activityTaskId == taskId && activityToken != null && imeSession != null) {
                ui.post(() -> { if (accepting && request == session && binding == activityRequest.get()) ready.run(); });
                return;
            }
            Object appeared = appeared(taskId);
            if (appeared == null) {
                if (attempt >= 100) throw new IllegalStateException("Workspace task did not appear");
                later(() -> attachActivity(taskId, ready, request, binding, attempt + 1), 50);
                return;
            }
            Object info = OneStepReflection.call(appeared, "getTaskInfo");
            if (!isActivityTask(info) || OneStepTaskAccess.display(info) != 0
                    || ReflectUtils.getIntField(info, "parentTaskId", -2) != -1
                    || ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1) != 1)
                throw new IllegalStateException("Workspace must be an independent fullscreen Activity");
            activityToken = OneStepReflection.get(info, "token");
            activityTaskId = taskId;
            if (imeSession != null) imeSession.close();
            imeSession = new OneStepImePolicy.Session((IBinder) OneStepTaskAccess.token(info), (task, placement) ->
                    ui.post(() -> {
                        if (accepting && request == session && listener != null)
                            listener.onImeRoutingChanged(task, placement);
                    }));
            for (Host host : hosts) if (current(host) && host.imeRegistered)
                imeSession.add((IBinder) host.card.token);
            Object wct = transaction();
            bool(wct, "setForceTranslucent", activityToken, false);
            bool(wct, "setAlwaysOnTop", activityToken, false);
            if (!suspended) reorder(wct, activityToken, true);
            OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
            ui.post(() -> { if (accepting && request == session && binding == activityRequest.get()) ready.run(); });
        } catch (Exception error) { fail("无法接管工作台窗口", error); }
    }

    void rebindActivity(int taskId, Runnable ready) {
        int request = session;
        int binding = activityRequest.incrementAndGet();
        executor.execute(() -> {
            if (!accepting || request != session || binding != activityRequest.get()) return;
            try {
                if (waitingOpening != null) finishWaitingOpening(waitingOpening, true);
                if (openingAnimation != null) finishOpeningAnimation(openingAnimation, true, true);
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    for (Host host : hosts) if (current(host) && host.borrowed) {
                        host.surfaceMissing = true;
                        if (host.leash != null && host.leash.isValid())
                            tx.reparent(host.leash, rootSurface()).setVisibility(host.leash, false);
                    }
                    tx.apply();
                }
                if (activityTaskId != taskId) {
                    activityTaskId = -1;
                    activityToken = null;
                }
                attachActivity(taskId, ready, request, binding, 0);
            } catch (Exception error) { fail("无法重新连接工作台窗口", error); }
        });
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
        return create(context, card, focused, false);
    }

    Host create(Context context, RecentTaskCard card, boolean focused, boolean replacement) throws ReflectiveOperationException {
        if (!accepting) throw new IllegalStateException("Workspace is closing");
        Host host = new Host(context, card, session, focused);
        host.replacement = replacement;
        executor.execute(() -> {
            hosts.add(host);
            if (!current(host)) releaseView(host);
            else if (host.home()) afterResume(() -> {
                if (current(host) && !host.ready) launchFailed(host,
                        new IllegalStateException("HOME surface attach timed out"));
            }, 10000);
        });
        return host;
    }

    Host createNavigation(Context context, Navigation navigation) throws ReflectiveOperationException {
        if (!accepting || navigation.cancelled || navigation.generation != session)
            throw new IllegalStateException("Navigation expired");
        Host host = new Host(context, navigation.card, session, false);
        host.replacement = true;
        host.navigation = navigation;
        executor.execute(() -> {
            if (!current(host) || navigation.cancelled) { releaseView(host); return; }
            navigation.target = host;
            hosts.add(host);
            if (host.initialized) acquire(host);
        });
        return host;
    }

    Host createLaunch(Context context, PendingIntent intent, IBinder animation, Host home, Rect restoreBounds)
            throws ReflectiveOperationException {
        if (!accepting) throw new IllegalStateException("Workspace is closing");
        Host host = new Host(context, null, session, false);
        host.launchIntent = intent;
        host.launchCookie = new Binder();
        host.launchHome = home;
        host.launchAnimation = animation == null ? null : new OneStepLaunchAnimation.Remote(animation);
        host.animationPending = animation != null;
        host.launchRestoreBounds = new Rect(restoreBounds);
        executor.execute(() -> {
            hosts.add(host);
            if (!current(host)) releaseView(host);
            else afterResume(() -> {
                if (!current(host) || host.ready) return;
                if (openingAnimation != null && openingAnimation.app == host)
                    finishOpeningAnimation(openingAnimation, true, true);
                else launchFailed(host, new IllegalStateException("Launcher task attach timed out"));
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
                    if (changed && !host.home() && !host.fullscreenCompat) {
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
            if (!accepting || suspended || !hostVisible || covered || (host != null && (!current(host) || !host.borrowed))) return;
            try {
                // Change both roles in one WM transaction, with the new main task on top.
                // Separate disable/enable transitions can briefly focus Home or another app.
                Object wct = transaction();
                for (Host candidate : hosts) {
                    if (!current(candidate) || !candidate.borrowed || candidate.closing) continue;
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
        executor.execute(() -> {
            closingFocusTask = focusTask;
            restoreAll(focusTask, finished, 0, request);
        });
    }

    void setSuspended(boolean value) {
        suspended = value;
        int request = session;
        executor.execute(() -> {
            if (!accepting || request != session) return;
            try {
                if (workspaceTransition != null && suspended) {
                    try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                        tx.setVisibility(workspaceTransition.stage, false).apply();
                    }
                    cancelWorkspaceMotion();
                }
                Object wct = transaction();
                for (Host host : hosts) if (current(host) && host.borrowed && !host.closing) {
                    bool(wct, "setFocusable", host.token, !suspended && host.main);
                    present(host, null, null);
                }
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
            } catch (Exception error) { fail("无法暂停或恢复应用窗口", error); }
        });
    }

    void setHostVisible(boolean value) {
        hostVisible = value;
        if (!value) executor.execute(() -> {
            if (workspaceTransition != null && accepting) cancelWorkspaceMotion();
        });
    }

    private boolean current(Host host) {
        return host != null && accepting && session == host.session && !host.released && !host.restoring;
    }

    private void acquire(Host host) {
        if (!current(host) || host.closing || host.borrowed || !host.initialized || host.logicalBounds.isEmpty()) return;
        if (suspended) { later(() -> acquire(host), 250); return; }
        if (host.navigation != null && host.navigation.cancelled) {
            restoreOne(host, () -> { if (listener != null) listener.onLaunchFailed(host); }, 0);
            return;
        }
        try {
            if (host.card == null) {
                if (!host.launching) launch(host);
                return;
            }
            Object appeared = appeared(host.card.taskId);
            if (appeared == null) {
                if (host.home()) throw new IllegalStateException("HOME task disappeared");
                if (host.navigation != null) {
                    if (++host.acquireAttempts > 150) throw new IllegalStateException("Navigation task did not appear");
                    later(() -> acquire(host), 50);
                    return;
                }
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
                    save(host, recent);
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
            ActivityManager.RunningTaskInfo info = (ActivityManager.RunningTaskInfo)
                    OneStepReflection.call(appeared, "getTaskInfo");
            if (taskEnded(info)) {
                host.closing = true;
                taskRemoved(host);
                return;
            }
            boolean eligible = host.home() ? OneStepTaskAccess.home(info) : OneStepTaskAccess.application(info);
            if (host.launchCookie != null || host.navigation != null) eligible = activityType(info) == 1
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
            host.runningInfo = info;
            host.leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
            if (!host.leash.isValid() || rootSurface() == null) throw new IllegalStateException("Task surface unavailable");
            if (workspaceTransition != null && workspaceTransition.owns(host))
                workspaceTransition.frame.set(host.logicalBounds);
            save(host, info);
            if (imeSession == null) throw new IllegalStateException("Workspace IME session unavailable");
            host.imeRegistered = true;
            imeSession.add((IBinder) host.card.token);
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
            if (!host.home()) {
                // TaskView skips this registration when Shell transitions are enabled.
                // Root back must retire the pane even when Android keeps the task in recents.
                OneStepReflection.call(organizer, "setInterceptBackPressedOnTaskRoot",
                        new Class<?>[]{tokenClass, boolean.class}, host.token, true);
                host.backIntercepted = true;
            }
            Object wct = transaction();
            // HOME stays a fullscreen HOME task. Only its surface is fitted into the pane.
            configureHostedMode(wct, host, "acquire", info);
            bool(wct, "setFocusable", host.token, host.main);
            if (host.main) for (Host other : hosts) {
                if (other != host && current(other) && other.borrowed)
                    bool(wct, "setFocusable", other.token, false);
            }
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
            if (host.home()) {
                // HOME retains fullscreen bounds. Commit its pane parent/crop before
                // bringing it above the workspace, otherwise WM can expose it fullscreen
                // while Shell is still collecting the opening transition.
                SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
                if (parent == null || !parent.isValid()) throw new IllegalStateException("HOME pane surface unavailable");
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    place(host, parent, tx);
                    tx.addTransactionCommittedListener(executor, () -> {
                        if (!current(host) || host.closing) return;
                        try { submit(wct); }
                        catch (Exception error) { launchFailed(host, error); }
                    });
                    tx.apply();
                }
            } else if (host.launchCookie != null || host.navigation != null) {
                // The launch already has a Shell transition. Make the task non-occluding
                // before its start transaction can expose it above the opaque workspace.
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                host.collected = true;
                present(host, null, null);
                for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                    for (SurfaceControl.Transaction tx : transactions) reattach(tx);
            } else submit(wct);
            // The pane becomes ready after its presentation transaction commits.
        } catch (Exception error) {
            if (host.launchCookie != null || host.home() || host.replacement) launchFailed(host, error);
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
        // Shell owns the transition; Flyme's native icon runner animates our pane-local
        // wrappers below. Do not also run WindowManager's fullscreen fallback animation.
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
        host.prepared = true;
        save(host, info);
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
        if (host.navigation != null) cancelNavigation(host.navigation);
        for (Host candidate : hosts) if (current(candidate) && candidate.coveredByLaunch) {
            candidate.coveredByLaunch = false;
            try { present(candidate, null, null); }
            catch (Exception restoreError) { Log.w(TAG, "Cannot uncover desktop", restoreError); }
        }
        restoreOne(host, () -> { if (listener != null) listener.onLaunchFailed(host); }, 0);
    }

    private void remember(Host host, Object info) throws ReflectiveOperationException {
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
            if (host.controller != controller || !current(host) || host.closing) continue;
            host.surfaceMissing = !visible;
            if (host.borrowed) {
                try {
                    if (visible) present(host, null, null);
                    else if (host.leash != null && host.leash.isValid()) {
                        // Keep the borrowed leash alive independently of a SurfaceView
                        // being recreated while an external Activity covers the host.
                        try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                            tx.reparent(host.leash, rootSurface()).setVisibility(host.leash, false).apply();
                        }
                    }
                }
                catch (Exception error) { fail("无法连接应用画面", error); }
            }
        }
    }

    private void taskRemoved(Host host) {
        if (host.restoring || host.released) return;
        if (host.card == null) { launchFailed(host, new IllegalStateException("Launching task disappeared")); return; }
        try {
            Object appeared = appeared(host.card.taskId);
            ActivityManager.RunningTaskInfo info = appeared == null ? null
                    : (ActivityManager.RunningTaskInfo) OneStepReflection.call(appeared, "getTaskInfo");
            if (!host.closing && matches(host.card, info) && !taskEnded(info)) {
                if (OneStepTaskAccess.display(info) == 0
                        && ReflectUtils.getIntField(info, "parentTaskId", -2) == -1
                        && (windowMode(info) == 1 || windowMode(info) == 6)) {
                    // A launch can hand a live task back to the fullscreen listener.
                    // Reclaim only the same standalone task, never PiP/split containers.
                    host.runningInfo = info;
                    host.leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
                    synchronized (OneStepReflection.get(organizer, "mLock")) {
                        Object owner = OneStepReflection.call(organizer, "getTaskListener",
                                new Class<?>[]{ActivityManager.RunningTaskInfo.class}, info);
                        if (owner != null && owner != host.controller) taskCallback(owner, "onTaskVanished", info, null);
                        listeners().put(host.card.taskId, host.controller);
                        taskCallback(host.controller, "onTaskAppeared", info, host.leash);
                    }
                    Object wct = transaction();
                    configureHostedMode(wct, host, "reclaim", info);
                    bool(wct, "setForceTranslucent", host.token, true);
                    bool(wct, "setFocusable", host.token, host.main);
                    OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                    present(host, null, null);
                    return;
                }
                // A listener handoff or window-mode conversion is not an application death.
                releaseExternalHost(host, info);
                return;
            }
            host.closing = true;
            restoreOne(host, () -> { if (listener != null) listener.onRemoved(host); }, 0);
        } catch (Exception error) { fail("无法处理应用退出", error); }
    }

    private boolean animate(Object[] args) throws Exception {
        List<?> changes = (List<?>) OneStepReflection.call(args[1], "getChanges");
        boolean ours = pending.containsKey(args[0]);
        boolean allOurs = true;
        for (Object change : changes) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            if (info != null && find(info) == null && !isActivityTask(info) && !isRetiredTask(info)) allOurs = false;
        }
        if (!ours && (!allOurs || hosts.isEmpty())) return false;
        SurfaceControl.Transaction start = (SurfaceControl.Transaction) args[2];
        SurfaceControl.Transaction finish = (SurfaceControl.Transaction) args[3];
        boolean handled = false;
        for (Object change : changes) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            Host host = find(info);
            if (host == null && ours && info != null) {
                Navigation navigation = incoming.get(OneStepTaskAccess.token(info));
                if (navigation != null) {
                    stageNavigation(navigation, start, finish);
                    handled = true;
                }
            }
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
                    if (!returned.closing) {
                        SurfaceControl surface = (SurfaceControl) OneStepReflection.call(change, "getLeash");
                        returnSurface(returned, surface, start);
                        returnSurface(returned, surface, finish);
                    }
                    handled = true;
                    break;
                }
            }
            if (host == null) continue;
            int changeMode = ((Number) OneStepReflection.call(change, "getMode")).intValue();
            if (changeMode == 2 && taskEnded(info)) {
                host.closing = true;
                // WindowManager removes the task; only this pane is retired after the transition.
                executor.execute(() -> taskRemoved(host));
                handled = true;
                continue;
            }
            if (!current(host)) continue;
            host.runningInfo = (ActivityManager.RunningTaskInfo) info;
            host.collected = true;
            // Keep the organizer's lifetime-owned leash. TransitionInfo handles are temporary.
            present(host, start, finish);
            handled = true;
        }
        if (!ours && !handled) return false;
        for (Host host : hosts) {
            if (current(host) && host.animationPending && args[0].equals(host.launchTransition)) {
                if (!host.borrowed && current(host.launchHome)) {
                    waitingOpening = new WaitingOpening(host, args);
                    Log.i(TAG, "Waiting for opening task surface: task="
                            + (host.card == null ? -1 : host.card.taskId));
                    start.apply();
                    WaitingOpening waiting = waitingOpening;
                    later(() -> resumeOpening(waiting), 50);
                    return true;
                }
                if (startOpeningAnimation(host, args)) return true;
                cancelLaunchAnimation(host);
                present(host, start, finish);
                break;
            }
        }
        pending.remove(args[0]);
        transitionBusy = !pending.isEmpty();
        placeWorkspaceTransition(start);
        placeWorkspaceTransition(finish);
        start.apply();
        OneStepReflection.call(args[4], "onTransitionFinished", new Class<?>[]{wctClass}, (Object) null);
        return true;
    }

    private final class WaitingOpening {
        final Host app;
        final Object[] args;
        final long deadline = SystemClock.uptimeMillis() + 2000;

        WaitingOpening(Host app, Object[] args) {
            this.app = app;
            this.args = args.clone();
        }
    }

    private void resumeOpening(WaitingOpening waiting) {
        if (waitingOpening != waiting) return;
        Host app = waiting.app;
        if (!current(app) || app.closing || !current(app.launchHome)) {
            finishWaitingOpening(waiting, true);
            return;
        }
        if (app.borrowed) {
            waitingOpening = null;
            // Shell's start transaction was applied to allow onTaskAppeared to arrive.
            // Keep its finish callback pending and animate using the organizer-owned leash.
            try (SurfaceControl.Transaction start = new SurfaceControl.Transaction()) {
                Object[] args = waiting.args.clone();
                args[2] = start;
                if (startOpeningAnimation(app, args)) return;
                start.apply();
            }
            waitingOpening = waiting;
            finishWaitingOpening(waiting, true);
        } else if (SystemClock.uptimeMillis() >= waiting.deadline) {
            Log.w(TAG, "Opening surface wait timed out; completing without animation");
            finishWaitingOpening(waiting, true);
        } else later(() -> resumeOpening(waiting), 50);
    }

    private void finishWaitingOpening(WaitingOpening waiting, boolean finishTransition) {
        if (waitingOpening != waiting) return;
        waitingOpening = null;
        cancelLaunchAnimation(waiting.app);
        try {
            present(waiting.app, null, (SurfaceControl.Transaction) waiting.args[3]);
        } catch (Exception error) { Log.w(TAG, "Cannot present waiting application", error); }
        pending.remove(waiting.args[0]);
        transitionBusy = !pending.isEmpty();
        if (finishTransition) {
            try { OneStepReflection.call(waiting.args[4], "onTransitionFinished",
                    new Class<?>[]{wctClass}, (Object) null); }
            catch (Exception error) { Log.w(TAG, "Cannot finish waiting opening transition", error); }
        }
    }

    private final class OpeningAnimation {
        final Host app;
        final Host home;
        final IBinder transition;
        final Object callback;
        final OneStepLaunchAnimation.Remote remote;
        final ArrayList<SurfaceControl> surfaces = new ArrayList<>();
        SurfaceControl stage;
        SurfaceControl icons;

        OpeningAnimation(Host app, Host home, IBinder transition, Object callback) {
            this.app = app;
            this.home = home;
            this.transition = transition;
            this.callback = callback;
            remote = app.launchAnimation;
        }

        SurfaceControl layer(String name, SurfaceControl parent) throws Exception {
            SurfaceControl.Builder builder = new SurfaceControl.Builder().setName("OneStep " + name).setParent(parent);
            OneStepReflection.call(builder, "setContainerLayer");
            SurfaceControl surface = builder.build();
            surfaces.add(surface);
            return surface;
        }
    }

    private boolean startOpeningAnimation(Host app, Object[] args) {
        Host home = app.launchHome;
        if (openingAnimation != null || !current(home) || !home.borrowed || !app.borrowed
                || app.launchAnimation == null) {
            Log.w(TAG, "Opening animation not ready: task=" + (app.card == null ? -1 : app.card.taskId)
                    + " borrowed=" + app.borrowed + " home=" + current(home)
                    + " running=" + (openingAnimation != null));
            return false;
        }
        OpeningAnimation animation = new OpeningAnimation(app, home, (IBinder) args[0], args[4]);
        SurfaceControl.Transaction start = (SurfaceControl.Transaction) args[2];
        try {
            SurfaceControl parent = (SurfaceControl) OneStepReflection.call(home.controller, "getSurfaceControl");
            if (parent == null || !parent.isValid() || home.leash == null || !home.leash.isValid()
                    || app.leash == null || !app.leash.isValid()) {
                Log.w(TAG, "Opening animation surface unavailable: task=" + app.card.taskId);
                return false;
            }
            animation.stage = animation.layer("opening viewport", parent);
            SurfaceControl coordinates = animation.layer("launcher coordinates", animation.stage);
            SurfaceControl homeLeash = animation.layer("desktop animation", coordinates);
            SurfaceControl appLeash = animation.layer("application animation", coordinates);
            animation.icons = animation.layer("floating icons", coordinates);
            // The native animator works in full desktop coordinates. The enclosing viewport
            // removes system insets, then the existing pane View supplies its screen scale.
            Rect viewport = new Rect(0, 0, app.logicalBounds.width(), app.logicalBounds.height());
            start.setLayer(animation.stage, 1).setVisibility(animation.stage, true);
            crop(start, animation.stage, viewport);
            start.setPosition(coordinates, -app.logicalBounds.left, -app.logicalBounds.top).setVisibility(coordinates, true);
            start.setLayer(homeLeash, 0).setPosition(homeLeash, home.originalBounds.left, home.originalBounds.top)
                    .setVisibility(homeLeash, true);
            // Flyme keeps the expanding floating icon opaque. The opening application
            // fades in ABOVE it, as it does above the launcher's window on the desktop.
            start.setLayer(appLeash, 2).setPosition(appLeash, app.logicalBounds.left, app.logicalBounds.top)
                    .setAlpha(appLeash, 0f).setVisibility(appLeash, true);
            start.setLayer(animation.icons, 1).setVisibility(animation.icons, true);
            start.reparent(home.leash, homeLeash).setPosition(home.leash, 0, 0).setScale(home.leash, 1, 1)
                    .setLayer(home.leash, 0).setAlpha(home.leash, 1f).setVisibility(home.leash, true);
            crop(start, home.leash, null);
            Rect appCrop = surfaceCrop(app);
            start.reparent(app.leash, appLeash).setPosition(app.leash, -appCrop.left, -appCrop.top).setScale(app.leash, 1, 1)
                    .setLayer(app.leash, 0).setAlpha(app.leash, 1f).setVisibility(app.leash, true);
            crop(start, app.leash, appCrop);
            Parcelable[] targets = new Parcelable[]{animationTarget(app, appLeash, 1, 1),
                    animationTarget(home, homeLeash, 4, 0)};
            openingAnimation = animation;
            start.addTransactionCommittedListener(executor, () -> {
                if (openingAnimation != animation) return;
                try {
                    Log.i(TAG, "Starting native launcher animation: task=" + app.card.taskId);
                    animation.remote.start(targets, animation.icons, executor,
                            () -> finishOpeningAnimation(animation, false, true));
                } catch (Exception error) {
                    Log.w(TAG, "Cannot start native launcher animation", error);
                    finishOpeningAnimation(animation, true, true);
                }
            });
            start.apply();
            later(() -> {
                if (openingAnimation == animation) {
                    Log.w(TAG, "Native launcher animation timed out; completing application launch");
                    finishOpeningAnimation(animation, true, true);
                }
            }, 4000);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Cannot prepare native launcher animation", error);
            openingAnimation = null;
            // Undo every queued reparent in the same start transaction before falling back.
            try {
                place(app, (SurfaceControl) OneStepReflection.call(app.controller, "getSurfaceControl"), start);
                place(home, (SurfaceControl) OneStepReflection.call(home.controller, "getSurfaceControl"), start);
                removeAnimationSurfaces(animation, start);
            } catch (Exception cleanupError) { Log.w(TAG, "Cannot reset opening surfaces", cleanupError); }
            return false;
        }
    }

    private Parcelable animationTarget(Host host, SurfaceControl leash, int mode, int order) throws Exception {
        // Copy TaskInfo: native code may retain or modify it, but the organizer owns the original.
        Parcel parcel = Parcel.obtain();
        ActivityManager.RunningTaskInfo info;
        try {
            host.runningInfo.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Parcelable.Creator<?> creator = (Parcelable.Creator<?>) OneStepReflection.field(
                    ActivityManager.RunningTaskInfo.class, "CREATOR").get(null);
            info = (ActivityManager.RunningTaskInfo) creator.createFromParcel(parcel);
        } finally { parcel.recycle(); }
        Object window = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
        OneStepReflection.call(window, "setBounds", new Class<?>[]{Rect.class},
                host.home() ? host.originalBounds : host.logicalBounds);
        Class<?> util = Class.forName("com.android.wm.shell.shared.TransitionUtil", false, loader);
        return (Parcelable) OneStepReflection.method(util, "newSyntheticTarget", ActivityManager.RunningTaskInfo.class,
                SurfaceControl.class, int.class, int.class, boolean.class).invoke(null, info, leash, mode, order, false);
    }

    private void finishOpeningAnimation(OpeningAnimation animation, boolean cancel, boolean finishTransition) {
        if (openingAnimation != animation) return;
        openingAnimation = null;
        Log.i(TAG, "Finishing native launcher animation: task=" + animation.app.card.taskId + " cancel=" + cancel);
        animation.remote.close(cancel);
        animation.app.launchAnimation = null;
        animation.app.openingRevealed = true;
        // The application's TaskView is mounted underneath Home. Hide Home in this same
        // transaction so its reset content cannot flash between animation end and UI removal.
        if (current(animation.app) && !animation.app.closing && current(animation.home)) animation.home.coveredByLaunch = true;
        try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
            for (Host host : new Host[]{animation.home, animation.app}) {
                if (host.closing) continue;
                SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
                if (parent == null || !parent.isValid() || !host.leash.isValid()) continue;
                place(host, parent, tx);
                for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                    for (SurfaceControl.Transaction finish : transactions) place(host, parent, finish);
            }
            removeAnimationSurfaces(animation, tx);
            tx.addTransactionCommittedListener(executor, () -> {
                animation.app.animationPending = false;
                // Signal readiness only after the final surface handoff has committed.
                try { present(animation.app, null, null); }
                catch (Exception error) { launchFailed(animation.app, error); }
            });
            tx.apply();
        } catch (Exception error) {
            animation.app.animationPending = false;
            launchFailed(animation.app, error);
        }
        finally {
            pending.remove(animation.transition);
            transitionBusy = !pending.isEmpty();
            if (finishTransition) {
                try { OneStepReflection.call(animation.callback, "onTransitionFinished",
                        new Class<?>[]{wctClass}, (Object) null); }
                catch (Exception error) { Log.w(TAG, "Cannot finish opening transition", error); }
            }
        }
    }

    private void cancelLaunchAnimation(Host host) {
        if (host.launchAnimation != null) host.launchAnimation.close(true);
        host.launchAnimation = null;
        host.animationPending = false;
    }

    private void removeAnimationSurfaces(OpeningAnimation animation, SurfaceControl.Transaction tx) {
        for (int i = animation.surfaces.size() - 1; i >= 0; i--) {
            SurfaceControl surface = animation.surfaces.get(i);
            if (surface.isValid()) tx.reparent(surface, null);
            surface.release();
        }
        animation.surfaces.clear();
    }

    private void crop(SurfaceControl.Transaction tx, SurfaceControl surface, Rect bounds) throws Exception {
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, surface, bounds);
    }

    private Navigation offerNavigation(Object info) throws Exception {
        if (!accepting || suspended || workspaceTransition != null) return null;
        RecentTaskCard card = OneStepTaskAccess.runningCard(info);
        if (card == null || card.userId != activityUserId || declined.contains(card.token)
                || !OneStepTaskAccess.externalTaskAllowed(info)) return null;
        Navigation previous = incoming.get(card.token);
        if (previous != null) return previous;
        Host source = mainHost();
        if (source == null || source.home() || !source.ready || source.closing || matches(source.card, info)) return null;
        Navigation navigation = new Navigation(card, source, info);
        Object appeared = appeared(card.taskId);
        if (appeared != null) navigation.leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
        incoming.put(card.token, navigation);
        setCovered(false);
        ui.post(() -> {
            if (accepting && session == navigation.generation && !navigation.cancelled && listener != null)
                listener.onNavigation(navigation);
        });
        afterResume(() -> {
            if (incoming.get(card.token) != navigation) return;
            if (navigation.target != null && current(navigation.target))
                launchFailed(navigation.target, new IllegalStateException("Navigation attach timed out"));
            else cancelNavigation(navigation);
        }, 8000);
        return navigation;
    }

    private void stageNavigation(Navigation navigation, SurfaceControl.Transaction start,
                                 SurfaceControl.Transaction finish) throws Exception {
        if (find(navigation.info) != null) return;
        if (navigation.leash == null) {
            Object appeared = appeared(navigation.card.taskId);
            if (appeared != null) navigation.leash = (SurfaceControl) OneStepReflection.call(appeared, "getLeash");
        }
        if (navigation.cancelled || (navigation.target != null && navigation.target.borrowed) || navigation.leash == null
                || !navigation.leash.isValid() || !current(navigation.source)) return;
        SurfaceControl parent = (SurfaceControl) OneStepReflection.call(navigation.source.controller, "getSurfaceControl");
        if (parent == null || !parent.isValid()) return;
        if (!navigation.staged) saveNavigation(navigation);
        // Fit the first external frame into the source pane while the UI mounts its own
        // TaskView. No task/Activity is relaunched and the original result chain survives.
        placeSurface(navigation.source, navigation.leash, parent, start);
        placeSurface(navigation.source, navigation.leash, parent, finish);
        start.setAlpha(navigation.leash, 1f).setLayer(navigation.leash, 1);
        finish.setAlpha(navigation.leash, 1f).setLayer(navigation.leash, 1);
        navigation.staged = true;
    }

    void abandonNavigation(Navigation navigation) { executor.execute(() -> cancelNavigation(navigation)); }

    void completeNavigation(Navigation navigation) {
        executor.execute(() -> incoming.remove(navigation.card.token, navigation));
    }

    private void cancelNavigation(Navigation navigation) {
        if (navigation.cancelled) return;
        navigation.cancelled = true;
        incoming.remove(navigation.card.token, navigation);
        declined.add(navigation.card.token);
        // Once acquired, restoreOne owns recovery, including its journal and finish surfaces.
        if (navigation.staged && (navigation.target == null || !navigation.target.borrowed)) {
            try {
                for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                    for (SurfaceControl.Transaction tx : transactions) unstageNavigation(navigation, tx);
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    unstageNavigation(navigation, tx);
                    tx.apply();
                }
                if (navigation.target == null || !navigation.target.prepared) forget(navigation.card.taskId);
            } catch (Exception error) { Log.w(TAG, "Cannot return navigation surface", error); }
        }
        if (accepting) setCovered(true);
    }

    private void unstageNavigation(Navigation navigation, SurfaceControl.Transaction tx) throws Exception {
        if (navigation.leash == null || !navigation.leash.isValid()) return;
        tx.reparent(navigation.leash, rootSurface()).setPosition(navigation.leash,
                navigation.position.x, navigation.position.y).setScale(navigation.leash, 1f, 1f)
                .setAlpha(navigation.leash, 1f);
        crop(tx, navigation.leash, null);
        tx.setFrameRate(navigation.leash, 0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private void saveNavigation(Navigation navigation) throws Exception {
        Object info = navigation.info;
        Object window = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
        ComponentName component = baseComponent(info);
        if (component == null) throw new IllegalStateException("Navigation has no base component");
        JSONObject item = new JSONObject();
        item.put("task", navigation.card.taskId).put("user", navigation.card.userId).put("boot", bootCount);
        item.put("component", component.flattenToString()).put("mode", windowMode(info));
        item.put("bounds", ((Rect) OneStepReflection.call(window, "getBounds")).flattenToString());
        item.put("x", navigation.position.x).put("y", navigation.position.y);
        item.put("focusable", ReflectUtils.getBooleanField(info, "isFocusable", true));
        item.put("alwaysOnTop", Boolean.TRUE.equals(OneStepReflection.call(window, "isAlwaysOnTop")));
        item.put("stagedNavigation", true);
        journal.put(navigation.card.taskId, item);
        persist();
    }

    private void setCovered(boolean value) {
        if (covered == value) return;
        covered = value;
        if (value && workspaceTransition != null) cancelWorkspaceMotion();
        int request = session;
        ui.post(() -> {
            if (accepting && session == request && listener != null) listener.onCoverageChanged(value);
        });
    }

    void checkFocus(int request) {
        executor.execute(() -> {
            if (!accepting || suspended || request != session) return;
            try {
                Object focused = null;
                for (Object info : tasks.roots()) {
                    if (OneStepTaskAccess.display(info) != 0 || taskEnded(info)) continue;
                    Host host = find(info);
                    if (host != null && current(host) && !host.closing) {
                        updateHostedConfiguration(host, info, false);
                        logImeTask(host, "task-state", info, false);
                    }
                    if (host != null && current(host) && !host.home()) {
                        RecentTaskCard next = OneStepTaskAccess.runningCard(info);
                        if (next != null && !next.temporary && host.card.component != null
                                && !next.component.getPackageName().equals(host.card.component.getPackageName())) {
                            RecentTaskCard previous = host.card;
                            host.card = next;
                            ui.post(() -> {
                                if (accepting && session == request && listener != null)
                                    listener.onTaskChanged(host, previous);
                            });
                        }
                    }
                    if (ReflectUtils.getBooleanField(info, "isFocused", false)) focused = info;
                }
                if (focused == null || OneHandedTaskHooks.shadeOpen()) return;
                if (workspaceTransition != null && matches(workspaceTransition.card, focused)) {
                    setCovered(false);
                    return;
                }
                Host host = find(focused);
                if (isActivityTask(focused) || (host != null && current(host))) {
                    if (host != null && host.ready && !host.main && pending.isEmpty()
                            && incoming.isEmpty() && mainHost() != null && mainHost().ready) {
                        offerNavigation(focused);
                        return;
                    }
                    setCovered(false);
                    return;
                }
                for (Host candidate : hosts) if (current(candidate) && (hasCookie(candidate, focused)
                        || matches(candidate.card, focused))) return;
                if (incoming.containsKey(OneStepTaskAccess.token(focused))) return;
                // A focus notification can precede or replace the Shell OPEN callback.
                if (offerNavigation(focused) == null) setCovered(true);
            } catch (Exception error) { Log.w(TAG, "Cannot inspect workspace focus", error); }
        });
    }

    private void observeTransition(Object[] args) throws Exception {
        if (!accepting) return;
        placeWorkspaceTransition((SurfaceControl.Transaction) args[2]);
        placeWorkspaceTransition((SurfaceControl.Transaction) args[3]);
        ArrayList<Host> backgroundTasks = new ArrayList<>();
        List<?> changes = (List<?>) OneStepReflection.call(args[1], "getChanges");
        boolean externalOpening = false;
        if (!pending.containsKey(args[0])) for (Object change : changes) {
            Object info = OneStepReflection.call(change, "getTaskInfo");
            int mode = ((Number) OneStepReflection.call(change, "getMode")).intValue();
            if (info == null || isActivityTask(info) || find(info) != null
                    || (workspaceTransition != null && matches(workspaceTransition.card, info))
                    || OneStepTaskAccess.display(info) != 0 || (mode != 1 && mode != 3)) continue;
            boolean launching = false;
            for (Host candidate : hosts) if (current(candidate)
                    && (hasCookie(candidate, info) || matches(candidate.card, info))) launching = true;
            if (launching) continue;
            externalOpening = true;
            Navigation navigation = offerNavigation(info);
            if (navigation != null) stageNavigation(navigation, (SurfaceControl.Transaction) args[2],
                    (SurfaceControl.Transaction) args[3]);
            else setCovered(true);
        }
        for (Object change : changes) {
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
            if (host != null && mode == 2 && taskEnded(info)) {
                host.closing = true;
                // A finished task can already have undefined bounds/mode. It is not a
                // move to another display or an external fullscreen application.
                // Retire it even when another Shell handler owns the closing animation.
                later(() -> { if (current(host)) taskRemoved(host); }, 0);
                continue;
            }
            if (!suspended && hostVisible && host != null && current(host) && host.main && host.ready && !host.home()
                    && !host.animationPending && !host.coveredByLaunch && mode == 4
                    && !externalOpening && !covered && incoming.isEmpty() && !pending.containsKey(args[0])) {
                // Apps may implement root back with moveTaskToBack instead of finishing.
                backgroundTasks.add(host);
            }
            if (isRetiredTask(info) && taskEnded(info)) continue;
            int windowMode = ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1);
            if (host != null && current(host)) updateHostedConfiguration(host, info, false);
            if (host != null && current(host) && !host.home() && windowMode == 1
                    && !host.fullscreenCompat
                    && OneStepTaskAccess.display(info) == 0
                    && ReflectUtils.getIntField(info, "parentTaskId", -1) == -1) {
                // Some cross-app launches reset the containing task to fullscreen.
                // Keep the hosted viewport; explicit TaskView fullscreen still exits.
                Object wct = transaction();
                configureHostedMode(wct, host, "fullscreen-repair", info);
                bool(wct, "setForceTranslucent", host.token, true);
                OneStepReflection.call(organizer, "applyTransaction", new Class<?>[]{wctClass}, wct);
                for (Object actual : tasks.roots()) if (matches(host.card, actual)) {
                    updateHostedConfiguration(host, actual, true);
                    windowMode = windowMode(actual);
                    break;
                }
            }
            boolean compatibleMode = host != null && (host.home() ? windowMode == 1
                    : windowMode == 6 || (host.fullscreenCompat && windowMode == 1));
            if (host != null && (!compatibleMode || OneStepTaskAccess.display(info) != 0
                    || ReflectUtils.getIntField(info, "parentTaskId", -1) != -1)) {
                // The system has already chosen a new container/mode. Preserve that choice.
                if (!pending.containsKey(args[0])) {
                    releaseExternalHost(host, info);
                    continue;
                }
            }

        }
        // Check every change for an external app switch before returning to the desktop.
        // Run after transition collection so restoration can also repair its finish surface.
        for (Host host : backgroundTasks) later(() -> {
            if (current(host) && host.main && !host.closing && !covered && incoming.isEmpty()) {
                restoreOne(host, () -> { if (listener != null) listener.onRemoved(host); }, 0);
            }
        }, 0);
    }

    private void releaseExternalHost(Host host, Object info) throws Exception {
        if (!current(host) || host.restoring) return;
        // A task moved to PiP/another container no longer belongs to this pane.
        // Release just that task while keeping every other pane and the workspace alive.
        host.externalReturn = true;
        host.originalMode = windowMode(info);
        Object config = OneStepReflection.get(OneStepReflection.get(info, "configuration"), "windowConfiguration");
        host.originalBounds = new Rect((Rect) OneStepReflection.call(config, "getBounds"));
        host.originalPosition = new Point((Point) OneStepReflection.get(info, "positionInParent"));
        host.originalAlwaysOnTop = Boolean.TRUE.equals(OneStepReflection.call(config, "isAlwaysOnTop"));
        host.returnParent = ReflectUtils.getIntField(info, "parentTaskId", -1);
        host.returnDisplay = OneStepTaskAccess.display(info);
        restoreOne(host, () -> { if (listener != null) listener.onRemoved(host); }, 0);
    }

    private void reattach(SurfaceControl.Transaction tx) throws Exception {
        for (Host host : hosts) {
            if (!current(host) || host.closing || !host.borrowed || !host.collected) continue;
            SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
            if (!host.surfaceMissing && parent != null && parent.isValid() && host.leash != null && host.leash.isValid()) {
                place(host, parent, tx);
            }
        }
        placeWorkspaceTransition(tx);
    }

    private void present(Host host, SurfaceControl.Transaction start, SurfaceControl.Transaction finish) throws Exception {
        if (!current(host) || host.closing || host.surfaceMissing || !host.borrowed || !host.collected || host.width <= 0 || host.height <= 0) return;
        SurfaceControl parent = (SurfaceControl) OneStepReflection.call(host.controller, "getSurfaceControl");
        if (parent == null || !parent.isValid() || host.leash == null || !host.leash.isValid()) return;
        OneStepReflection.call(host.controller, "prepareOpen",
                new Class<?>[]{ActivityManager.RunningTaskInfo.class, SurfaceControl.class}, host.runningInfo, host.leash);
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
                    if (accepting && session == host.session && !host.released && !host.restoring && !host.ready
                            && !host.closing && !host.animationPending) {
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
        if (workspaceTransition != null && workspaceTransition.owns(host) && !workspaceTransition.returning) {
            workspaceTransition.place(tx, surface, surfaceCrop(host), !suspended && !covered && hostVisible);
            return;
        }
        // Only the native animator may transform these temporary wrappers until it finishes.
        if (openingAnimation != null && (openingAnimation.app == host || openingAnimation.home == host)) return;
        Rect crop = surfaceCrop(host);
        float sx = host.width / (float) crop.width();
        float sy = host.height / (float) crop.height();
        tx.reparent(surface, parent).setPosition(surface, -crop.left * sx, -crop.top * sy)
                .setScale(surface, sx, sy)
                // Keep a newly appeared app hidden until the native stage takes over,
                // even if SurfaceView relayout temporarily changes sibling surface order.
                .setAlpha(surface, host.coveredByLaunch || (host.animationPending && !host.openingRevealed) ? 0f : 1f)
                .setVisibility(surface, !suspended);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class},
                surface, crop);
        // A Surface frame-rate vote is a scheduling preference, not a per-app FPS cap.
        tx.setFrameRate(surface, host.main ? 120f : 30f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private Rect surfaceCrop(Host host) {
        Rect crop = new Rect(0, 0, host.logicalBounds.width(), host.logicalBounds.height());
        if (host.home()) crop.offset(host.logicalBounds.left - host.originalBounds.left,
                host.logicalBounds.top - host.originalBounds.top);
        else if (host.fullscreenCompat) {
            Rect actual = host.actualBounds;
            crop.set(host.logicalBounds);
            if (!crop.intersect(actual)) throw new IllegalStateException("Fullscreen task has no visible viewport");
            crop.offset(-actual.left, -actual.top);
        }
        return crop;
    }

    private void restoreOne(Host host, Runnable finished, int attempt) {
        host.restoring = true;
        try {
            if (!restore(host)) {
                later(() -> restoreOne(host, finished, attempt), 50);
                return;
            }
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
            for (Navigation navigation : new ArrayList<>(incoming.values())) cancelNavigation(navigation);
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
                if (!restore(host)) {
                    later(() -> restoreAll(focusTask, finished, attempt, request), 50);
                    return;
                }
                hosts.remove(host);
            }
            if (imeSession != null) {
                imeSession.close();
                imeSession = null;
            }
            int selectedFocus = focusTask >= 0 ? focusTask : retainFocus;
            if (selectedFocus >= 0) {
                try { tasks.focusTask(selectedFocus); }
                catch (Exception error) { Log.w(TAG, "Restored task no longer accepts focus", error); }
            }
            finishWorkspaceRestoration(() -> {
                if (finished != null && request == closeRequest.get()) finished.run();
            });
        } catch (Exception error) {
            Log.w(TAG, "Workspace restoration will retry", error);
            later(() -> restoreAll(focusTask, finished, attempt + 1, request), Math.min(5000, 200 + attempt * 400));
        }
    }

    private boolean moveHomeBehindWorkspace(Host host) throws Exception {
        if (host.homeBehindReady) return true;
        if (host.homeBehindPending) return false;
        Object wct = transaction();
        reorder(wct, host.token, false);
        arrangeTasks(wct, mainHost());
        Class<?> runnableClass = Class.forName(
                "com.android.wm.shell.common.SyncTransactionQueue$TransactionRunnable", false, loader);
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{runnableClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
            if ("runWithTransaction".equals(method.getName())) {
                SurfaceControl.Transaction tx = (SurfaceControl.Transaction) args[0];
                tx.addTransactionCommittedListener(executor, () -> {
                    host.homeBehindReady = true;
                    host.homeBehindPending = false;
                });
            }
            return null;
        });
        Object queue = OneStepReflection.get(syncQueue, "mQueue");
        synchronized (queue) {
            // runInSync joins the in-flight WCT, not the last queued one. Wait for
            // an idle queue so this commit always belongs to HOME's own reorder.
            if (!((List<?>) queue).isEmpty()) return false;
            host.homeBehindPending = true;
            try {
                OneStepReflection.call(syncQueue, "queue", new Class<?>[]{wctClass}, wct);
                OneStepReflection.call(syncQueue, "runInSync", new Class<?>[]{runnableClass}, callback);
            } catch (Exception error) {
                host.homeBehindPending = false;
                throw error;
            }
        }
        return false;
    }

    private boolean restore(Host host) throws Exception {
        if (host.released) return true;
        if (host.workspaceRestorePending) return false;
        // A close can arrive during the reorder. Keep the pane and its recovery
        // record alive until WM's layer changes have reached the compositor.
        if (host.homeBehindPending) return false;
        if (waitingOpening != null && (waitingOpening.app == host || waitingOpening.app.launchHome == host))
            finishWaitingOpening(waitingOpening, true);
        if (openingAnimation != null && (openingAnimation.app == host || openingAnimation.home == host))
            finishOpeningAnimation(openingAnimation, true, true);
        cancelLaunchAnimation(host);
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
            if (host.home() && accepting && host.session == session && !host.externalReturn
                    && !host.closing && matches(host.card, info) && !taskEnded(info)
                    && !moveHomeBehindWorkspace(host)) return false;
            if (host.backIntercepted) {
                if (matches(host.card, info)) {
                    OneStepReflection.call(organizer, "setInterceptBackPressedOnTaskRoot",
                            new Class<?>[]{tokenClass, boolean.class}, host.token, false);
                }
                host.backIntercepted = false;
            }
            if (!host.closing && matches(host.card, info) && !taskEnded(info)) {
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
                } else if (activityToken != null && closingFocusTask >= 0) {
                    // Side applications must never flash fullscreen above the exiting host.
                    reorder(wct, host.token, host.card.taskId == closingFocusTask);
                }
                if (workspaceTransition != null && workspaceTransition.owns(host)
                        && workspaceTransition.exiting && closingFocusTask == host.card.taskId
                        && !host.workspaceRestoreCommitted) {
                    restoreWorkspaceSurface(host, wct);
                    return false;
                }
                if (!host.workspaceRestoreCommitted)
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
            if (host.prepared && !host.closing) {
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
        if (host.imeRegistered) {
            imeSession.remove((IBinder) host.card.token);
            host.imeRegistered = false;
        }
        host.prepared = false;
        releaseView(host);
        return true;
    }

    private void returnSurface(Host host, SurfaceControl leash, SurfaceControl.Transaction tx) throws Exception {
        if (!leash.isValid()) return;
        if (workspaceTransition != null && workspaceTransition.owns(host)) workspaceTransition.returning = true;
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
        if (host.surfaceMissing) tx.setVisibility(leash, true);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, leash, null);
        tx.setFrameRate(leash, 0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private void restoreWorkspaceSurface(Host host, Object wct) throws Exception {
        OneStepWorkspaceTransition motion = workspaceTransition;
        Class<?> runnableClass = Class.forName(
                "com.android.wm.shell.common.SyncTransactionQueue$TransactionRunnable", false, loader);
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{runnableClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method.getName(), args);
            if ("runWithTransaction".equals(method.getName())) {
                SurfaceControl.Transaction tx = (SurfaceControl.Transaction) args[0];
                try {
                    // WM's fullscreen configuration and the final parent/crop change land
                    // in the same transaction. Keep TaskView alive until it commits.
                    returnSurface(host, host.leash, tx);
                    for (ArrayList<SurfaceControl.Transaction> transactions : finishes.values())
                        for (SurfaceControl.Transaction finish : transactions) returnSurface(host, host.leash, finish);
                    motion.remove(tx);
                    if (workspaceTransition == motion) workspaceTransition = null;
                    tx.addTransactionCommittedListener(executor, () -> {
                        host.workspaceRestorePending = false;
                        host.workspaceRestoreCommitted = true;
                        motion.release();
                    });
                } catch (Exception error) {
                    host.workspaceRestorePending = false;
                    Log.w(TAG, "Fullscreen surface handoff will retry", error);
                }
            }
            return null;
        });
        Object queue = OneStepReflection.get(syncQueue, "mQueue");
        synchronized (queue) {
            if (!((List<?>) queue).isEmpty()) return;
            host.workspaceRestorePending = true;
            try {
                OneStepReflection.call(syncQueue, "queue", new Class<?>[]{wctClass}, wct);
                OneStepReflection.call(syncQueue, "runInSync", new Class<?>[]{runnableClass}, callback);
            } catch (Exception error) {
                host.workspaceRestorePending = false;
                throw error;
            }
        }
    }

    private void finishWorkspaceRestoration(Runnable finished) throws Exception {
        OneStepWorkspaceTransition motion = workspaceTransition;
        if (motion == null) { ui.post(finished); return; }
        if (motion.removing) {
            later(() -> {
                try { finishWorkspaceRestoration(finished); }
                catch (Exception error) { Log.w(TAG, "Cannot finish workspace restoration", error); }
            }, 50);
            return;
        }
        try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
            if (!motion.returning && motion.surface.isValid()) {
                // Acquisition may fail before there is a Host to restore. Return the
                // staged task to its current WM container, including an external move.
                Object appeared = appeared(motion.card.taskId);
                Object info = appeared == null ? null : OneStepReflection.call(appeared, "getTaskInfo");
                if (matches(motion.card, info) && !taskEnded(info)) {
                    int parentId = ReflectUtils.getIntField(info, "parentTaskId", -1);
                    SurfaceControl parent;
                    if (parentId >= 0) {
                        Object parentInfo = appeared(parentId);
                        if (parentInfo == null) throw new IllegalStateException("Task parent has not appeared");
                        parent = (SurfaceControl) OneStepReflection.call(parentInfo, "getLeash");
                    } else parent = (SurfaceControl) ((SparseArray<?>) OneStepReflection.get(displayAreas, "mLeashes"))
                            .get(OneStepTaskAccess.display(info));
                    if (parent == null || !parent.isValid()) throw new IllegalStateException("Task display disappeared");
                    Point position = (Point) OneStepReflection.get(info, "positionInParent");
                    ArrayList<SurfaceControl.Transaction> transactions = new ArrayList<>();
                    transactions.add(tx);
                    for (ArrayList<SurfaceControl.Transaction> pendingFinishes : finishes.values()) transactions.addAll(pendingFinishes);
                    for (SurfaceControl.Transaction transaction : transactions) {
                        transaction.reparent(motion.surface, parent).setPosition(motion.surface, position.x, position.y)
                                .setScale(motion.surface, 1f, 1f).setAlpha(motion.surface, 1f);
                        crop(transaction, motion.surface, null);
                    }
                }
            }
            motion.returning = true;
            motion.remove(tx);
            tx.addTransactionCommittedListener(executor, () -> {
                if (workspaceTransition == motion) workspaceTransition = null;
                motion.release();
                ui.post(finished);
            });
            motion.removing = true;
            tx.apply();
        } catch (Exception error) {
            motion.removing = false;
            throw error;
        }
    }

    private void releaseView(Host host) {
        if (host.released) return;
        cancelLaunchAnimation(host);
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
        if (covered || suspended || !hostVisible) return;
        if (activityToken == null) throw new IllegalStateException("Workspace Activity is not attached");
        // Only the workspace panes are above this opaque fullscreen task. Their logical
        // viewports overlap, so they stay translucent to WM; the Activity below them
        // occludes unrelated apps and Home without hiding or freezing those processes.
        reorder(wct, activityToken, true);
        for (Host candidate : hosts) {
            if (current(candidate) && candidate.borrowed && !candidate.closing && candidate != focused)
                reorder(wct, candidate.token, true);
        }
        if (current(focused) && focused.borrowed && !focused.closing) reorder(wct, focused.token, true);
    }

    private Host mainHost() {
        for (Host host : hosts) if (current(host) && host.borrowed && !host.closing && host.main) return host;
        return null;
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
    private boolean isRetiredTask(Object info) {
        for (Host host : retired) if (matches(host.card, info)) return true;
        return false;
    }
    static boolean taskEnded(Object info) {
        return info != null && (!ReflectUtils.getBooleanField(info, "isRunning", true)
                || ReflectUtils.getIntField(info, "numActivities", -1) == 0);
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

    private void afterResume(Runnable action, long delay) {
        int request = session;
        later(() -> {
            if (!accepting || request != session) return;
            if (suspended) waitForResume(action, delay, request);
            else action.run();
        }, delay);
    }

    private void waitForResume(Runnable action, long delay, int request) {
        if (!accepting || request != session) return;
        if (suspended) later(() -> waitForResume(action, delay, request), 500);
        else afterResume(action, delay);
    }
    private void fail(String message, Exception error) {
        int request = session;
        ui.post(() -> {
            if (accepting && request == session && listener != null) listener.onFailure(message, error);
        });
    }
    private static Object objectMethod(Object proxy, String name, Object[] args) {
        if ("equals".equals(name)) return proxy == args[0];
        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
        return "FlymeOneStepShell";
    }

    private void save(Host host, Object info) throws Exception {
        JSONObject item = new JSONObject();
        item.put("task", host.card.taskId).put("user", host.card.userId).put("boot", bootCount);
        ComponentName base = baseComponent(info);
        if (base == null) throw new IllegalStateException("Cannot journal a task without its base component");
        item.put("component", base.flattenToString());
        item.put("mode", host.originalMode).put("bounds", host.originalBounds.flattenToString());
        item.put("focusable", host.originalFocusable).put("alwaysOnTop", host.originalAlwaysOnTop);
        item.put("x", host.originalPosition.x).put("y", host.originalPosition.y);
        item.put("imeInsetsExcluded", true);
        item.put("forceTranslucent", true);
        item.put("home", host.home());
        item.put("rootBackIntercepted", !host.home());
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
            if (item.optBoolean("rootBackIntercepted", false)) {
                OneStepReflection.call(organizer, "setInterceptBackPressedOnTaskRoot",
                        new Class<?>[]{tokenClass, boolean.class}, token, false);
            }
            Object wct = transaction();
            boolean imeInsetsExcluded = item.optBoolean("imeInsetsExcluded", false);
            if (imeInsetsExcluded) bool(wct, "setExcludeImeInsets", token, false);
            boolean forceTranslucent = item.optBoolean("forceTranslucent", false);
            if (forceTranslucent) bool(wct, "setForceTranslucent", token, false);
            Rect bounds = Rect.unflattenFromString(item.optString("bounds", ""));
            boolean home = item.optBoolean("home", false) && OneStepTaskAccess.home(info);
            if (OneStepTaskAccess.display(info) != 0
                    || (!home && ReflectUtils.invokeNoArgInt(info, "getWindowingMode", -1) != 6
                    && !(item.optBoolean("stagedNavigation", false) && windowMode(info) == item.optInt("mode")))
                    || bounds == null) {
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
                            .setPosition(leash, item.optInt("x", 0), item.optInt("y", 0)).setAlpha(leash, 1)
                            .setVisibility(leash, true);
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
