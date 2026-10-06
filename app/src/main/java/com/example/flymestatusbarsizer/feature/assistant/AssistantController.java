package com.example.flymestatusbarsizer.feature.assistant;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Window;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs inside AssistantService. All component/window state is confined to the main thread. */
final class AssistantController implements ComponentCallbacks {
    final Context context;
    final Handler main = new Handler(Looper.getMainLooper());
    final IBinder control;
    final FlymeStatusBarSizer module;
    private WeakReference<Object> component = new WeakReference<>(null);
    private static final Set<Class<?>> CALLBACK_TYPES = new HashSet<>();
    private IBinder client;
    private IBinder.DeathRecipient clientDeath;
    private Handler observer;
    private HandlerThread observerThread;
    private boolean destroyed;
    private boolean hooksReady;
    volatile AssistantWindowSession session;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) { publishState(); return; }
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if (!"homekey".equals(reason) && !"recentapps".equals(reason)) return;
            }
            restore(true);
        }
    };

    AssistantController(Context context, FlymeStatusBarSizer module) {
        this.context = context;
        this.module = module;
        control = new Binder() {
            { attachInterface(null, AssistantProtocol.DESCRIPTOR); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
                if (!AssistantProtocol.isSystemUi(context)) return false;
                if (code < AssistantProtocol.REGISTER || code > AssistantProtocol.TITLE_TINT)
                    return super.onTransact(code, data, reply, flags);
                data.enforceInterface(AssistantProtocol.DESCRIPTOR);
                if (code == AssistantProtocol.REGISTER) {
                    IBinder callback = data.readStrongBinder();
                    main.post(() -> register(callback));
                } else if (code == AssistantProtocol.SHOW) {
                    long id = data.readLong();
                    boolean fromLeft = data.dataAvail() < 4 || data.readInt() != 1;
                    Integer tint = data.dataAvail() >= 4 ? data.readInt() : null;
                    main.post(() -> show(id, fromLeft, tint));
                } else if (code == AssistantProtocol.TITLE_TINT) {
                    int tint = data.readInt();
                    main.post(() -> {
                        AssistantWindowSession current = session;
                        if (current != null && !current.restoring) current.titleColor.setTint(tint);
                    });
                } else main.post(() -> restore(true));
                return true;
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction("android.intent.action.USER_SWITCHED");
        filter.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        context.registerComponentCallbacks(this);
    }

    void setHooksReady(boolean ready) { hooksReady = ready; publishState(); }

    void created(Object value) {
        component = new WeakReference<>(value);
        try { installCallbackHooks(value); }
        catch (Throwable t) { AssistantHooks.warn("Cannot isolate launcher callbacks", t); }
        publishState();
    }

    void componentDestroying(Object value) {
        if (session != null && session.component == value) restore(false);
        if (component.get() == value) component.clear();
        publishState();
    }

    void refresh() {
        main.post(() -> {
            if (!AssistantClient.enabled(context)) restore(true);
            publishState();
        });
    }

    void destroy() {
        destroyed = true;
        restore(false);
        unregisterClient();
        context.unregisterReceiver(receiver);
        context.unregisterComponentCallbacks(this);
        if (observerThread != null) observerThread.quitSafely();
        main.removeCallbacksAndMessages(null);
    }

    @Override public void onConfigurationChanged(Configuration configuration) { restore(true); }
    @Override public void onLowMemory() { }

    private boolean ready() {
        if (destroyed || !hooksReady || session != null || !AssistantClient.enabled(context) || !interactive()) return false;
        Object c = component.get();
        if (c == null) return false;
        try {
            Window window = (Window) AssistantReflection.get(c, "mWindow");
            return window != null && window.getAttributes().type == 4
                    && Boolean.TRUE.equals(AssistantReflection.call(c, "isAttachToWindow"))
                    && "CLOSED".equals(AssistantReflection.call(c, "getPanelState").toString());
        } catch (Throwable t) { return false; }
    }

    private boolean interactive() {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return power != null && power.isInteractive() && (keyguard == null || !keyguard.isKeyguardLocked());
    }

    private void register(IBinder callback) {
        if (destroyed || callback == null) return;
        if (client != null && !client.equals(callback)) restore(true);
        unregisterClient();
        client = callback;
        clientDeath = () -> main.post(() -> {
            if (client != callback) return;
            unregisterClient();
            restore(true);
        });
        try { client.linkToDeath(clientDeath, 0); }
        catch (RemoteException e) { clientDeath.binderDied(); return; }
        publishState();
    }

    private void unregisterClient() {
        if (client != null && clientDeath != null) client.unlinkToDeath(clientDeath, 0);
        client = null;
        clientDeath = null;
    }

    void publishState() {
        if (client == null) return;
        boolean ready = ready();
        try {
            AssistantProtocol.send(client, AssistantProtocol.CALLBACK, AssistantProtocol.STATE,
                    p -> p.writeInt(ready ? 1 : 0));
        } catch (Throwable t) { unregisterClient(); restore(true); }
    }

    private void result(IBinder callback, long id, boolean shown) {
        try {
            AssistantProtocol.send(callback, AssistantProtocol.CALLBACK, AssistantProtocol.RESULT,
                    p -> { p.writeLong(id); p.writeInt(shown ? 1 : 0); });
        } catch (Throwable t) { restore(true); }
    }

    private void show(long id, boolean fromLeft, Integer tint) {
        IBinder requester = client;
        if (requester == null) return;
        if (!ready()) { result(requester, id, false); publishState(); return; }
        Object c = component.get();
        try {
            Task task = topTask();
            String lifecycle = AssistantWindowSession.lifecycle(c);
            // Even with Launcher underneath, its type-4 window cannot cover an expanded shade.
            // Always use the independent host and restore the captured lifecycle on close.
            if (!"CREATED".equals(lifecycle) && !"STARTED".equals(lifecycle) && !"RESUMED".equals(lifecycle))
                throw new IllegalStateException("Launcher component is not active: " + lifecycle);
            installCallbackHooks(c);
            AssistantWindowSession current = new AssistantWindowSession(c, fromLeft);
            current.titleColor.setTint(tint);
            session = current;
            publishState();
            current.attach();
            main.postDelayed(() -> {
                if (session != current || current.restoring) { result(requester, id, false); return; }
                try {
                    current.open();
                    confirmShown(current, requester, id, SystemClock.uptimeMillis() + 1200);
                } catch (Throwable t) {
                    AssistantHooks.warn("Cannot open assistant panel", t);
                    restore(true); result(requester, id, false);
                }
            }, 100);
            watchTask(current, task);
        } catch (Throwable t) {
            AssistantHooks.warn("Cannot create independent assistant window", t);
            restore(true);
            result(requester, id, false);
            publishState();
        }
    }

    private void confirmShown(AssistantWindowSession current, IBinder requester, long id, long deadline) {
        if (session != current || current.restoring) { result(requester, id, false); return; }
        if (current.decor.isAttachedToWindow() && current.decor.hasWindowFocus()) {
            result(requester, id, true);
        } else if (SystemClock.uptimeMillis() < deadline) {
            main.postDelayed(() -> confirmShown(current, requester, id, deadline), 80);
        } else {
            restore(true);
            result(requester, id, false);
        }
    }

    void restore(boolean reattach) {
        AssistantWindowSession current = session;
        if (current == null || current.restoring) return;
        try { current.restore(reattach); }
        catch (Throwable t) { AssistantHooks.warn("Assistant host restoration failed", t); }
        finally {
            session = null;
            publishState();
        }
    }

    /** True means this message must not mutate the temporary global host. */
    boolean interceptLauncherMessage(Object dispatcher, Message message) {
        AssistantWindowSession current = session;
        if (current == null || current.restoring) return false;
        try {
            if (AssistantReflection.get(dispatcher, "mSlidePanelOverlayComponent") != current.component) return false;
            switch (message.what) {
                case 3: case 4: case 10: // Attach/detach/destroy will rebuild or remove the native view.
                    restore(false); return false;
                case 6: case 8: // Launcher is coming to the foreground.
                    restore(true); return false;
                case 7:
                    current.restoreLifecycle = "STARTED"; return true;
                case 9:
                    current.restoreLifecycle = "CREATED"; return true;
                case 14: return false; // Back can use the existing edit/close behavior.
                default: return true; // Ignore desktop transforms, scroll and Quickstep while global.
            }
        } catch (Throwable t) { restore(true); return false; }
    }

    void panelClosed(Object panel) {
        AssistantWindowSession current = session;
        if (current != null && current.panel == panel && !current.restoring) {
            // Do not remove the view in the middle of native listener iteration.
            main.post(() -> { if (session == current) restore(true); });
        } else publishState();
    }

    private void installCallbackHooks(Object c) throws ReflectiveOperationException {
        Object callback = AssistantReflection.get(c, "mProxyCallbacks");
        if (callback == null) throw new IllegalStateException("No launcher callback");
        Class<?> type = callback.getClass();
        if (CALLBACK_TYPES.contains(type)) return;
        Method[] methods = type.getDeclaredMethods();
        long supported = java.util.Arrays.stream(methods).filter(m -> isOverlayCallback(m.getName())).count();
        if (supported != 4) throw new IllegalStateException("Unsupported launcher callback " + type.getName());
        int found = 0;
        for (Method method : methods) {
            String name = method.getName();
            if (!isOverlayCallback(name)) continue;
            method.setAccessible(true);
            module.intercept(method, chain -> {
                AssistantWindowSession current = AssistantHooks.currentSession();
                if (current != null && current.suppressCallbacks && chain.getThisObject() == current.callback)
                    return method.getReturnType() == int.class ? 0 : null;
                return chain.proceed();
            });
            ++found;
        }
        if (found != 4) throw new IllegalStateException("Unsupported launcher callback " + type.getName());
        CALLBACK_TYPES.add(type);
    }

    private static boolean isOverlayCallback(String name) {
        return name.equals("overlayStateChanged") || name.equals("overlayScrollChanged")
                || name.equals("onOverlaySurfaceChanged") || name.equals("onQuickstepInteraction");
    }

    private Task topTask() {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks.isEmpty()) return null;
            ActivityManager.RunningTaskInfo info = tasks.get(0);
            return info.topActivity == null ? null : new Task(info.id, info.topActivity);
        } catch (Throwable ignored) { return null; }
    }

    private void watchTask(AssistantWindowSession current, Task baseline) {
        if (observer == null) {
            observerThread = new HandlerThread("FlymeAssistantWatch");
            observerThread.start();
            observer = new Handler(observerThread.getLooper());
        }
        observer.postDelayed(() -> {
            Task top = topTask();
            main.post(() -> {
                if (session != current || current.restoring) return;
                if (!interactive() || !AssistantClient.enabled(context)
                        || (baseline != null && top != null && !baseline.same(top))) restore(true);
                else watchTask(current, baseline == null ? top : baseline);
            });
        }, 400);
    }

    private static final class Task {
        final int id;
        final ComponentName activity;
        Task(int id, ComponentName activity) { this.id = id; this.activity = activity; }
        boolean same(Task other) { return id == other.id && activity.equals(other.activity); }
    }
}
