package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** A task-token capability scopes IME policy to a live workspace, including fullscreen tasks. */
public final class OneStepImePolicy {
    private static final String TAG = "FlymeOneStepIme";
    private static final String DESCRIPTOR = BuildConfig.APPLICATION_ID + ".workspace.ime";
    private static final int CONNECT = 0x00534f49;
    private static final int ADD = IBinder.FIRST_CALL_TRANSACTION;
    private static final int REMOVE = ADD + 1;
    private static final int CLOSE = ADD + 2;
    private static final int ROUTING = IBinder.FIRST_CALL_TRANSACTION;
    static final int UNKNOWN = 0;
    static final int DISPLAY = 1;
    static final int APP = 2;

    // Only system_server uses these maps, always under WMS's global lock.
    private static final Map<Object, ServerSession> OWNERS = new IdentityHashMap<>();
    private static final Map<Object, ServerSession> WORKSPACES = new IdentityHashMap<>();
    private static Method fromBinder;
    private static volatile boolean installed;
    private static volatile boolean hasSessions;

    private OneStepImePolicy() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> wms = Class.forName("com.android.server.wm.WindowManagerService", false, loader);
            Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
            Class<?> display = Class.forName("com.android.server.wm.DisplayContent", false, loader);
            Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
            fromBinder = OneStepReflection.method(Class.forName("com.android.server.wm.WindowContainer", false, loader),
                    "fromBinder", IBinder.class);
            module.intercept(OneStepReflection.method(window, "shouldControlIme"), chain -> {
                if (!hasSessions) return chain.proceed();
                Object target = chain.getThisObject();
                try {
                    Object dc = OneStepReflection.call(target, "getDisplayContent");
                    Object service = OneStepReflection.get(target, "mWmService");
                    synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                        ServerSession owner = ownerOf(OneStepReflection.call(target, "getTask"));
                        if (owner != null && owner.live() && dc != null
                                && ReflectUtils.getField(dc, "mRemoteInsetsControlTarget") != null
                                && !Boolean.TRUE.equals(ReflectUtils.invokeNoArg(
                                        ReflectUtils.getField(dc, "mImeWindowsContainer"), "isOrganized"))) return false;
                    }
                } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
                return chain.proceed();
            });
            for (String name : new String[]{"updateImeParent", "adjustForImeIfNeeded", "updateImeControlTarget"}) {
                Method method = "updateImeControlTarget".equals(name)
                        ? OneStepReflection.method(display, name, boolean.class)
                        : OneStepReflection.method(display, name);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    report(chain.getThisObject());
                    return result;
                });
            }
            module.intercept(OneStepReflection.method(task, "removeImmediately", String.class), chain -> {
                if (!hasSessions) return chain.proceed();
                Object target = chain.getThisObject();
                try {
                    Object service = OneStepReflection.get(target, "mWmService");
                    synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                        ServerSession workspace = WORKSPACES.get(target);
                        if (workspace != null) workspace.close();
                        ServerSession owner = OWNERS.remove(target);
                        if (owner != null) {
                            owner.tasks.values().removeIf(value -> value == target);
                            owner.refresh();
                        }
                    }
                } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
                return chain.proceed();
            });
            // Reuse the existing window-service Binder; no new service or SELinux service name.
            module.intercept(OneStepReflection.method(wms, "onTransact", int.class, Parcel.class, Parcel.class, int.class), chain -> {
                if ((Integer) chain.getArg(0) != CONNECT || !installed) return chain.proceed();
                Parcel data = (Parcel) chain.getArg(1);
                Parcel reply = (Parcel) chain.getArg(2);
                if (reply == null) return false;
                data.enforceInterface(DESCRIPTOR);
                Object service = chain.getThisObject();
                Context context = (Context) OneStepReflection.get(service, "mContext");
                int uid = Binder.getCallingUid();
                if (uid != context.getPackageManager().getApplicationInfo("com.android.systemui", 0).uid)
                    throw new SecurityException("Only SystemUI may register a workspace");
                IBinder workspaceToken = data.readStrongBinder();
                IBinder callback = data.readStrongBinder();
                if (callback == null) throw new IllegalArgumentException("Missing workspace lifetime");
                synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                    Object workspace = resolveTask(workspaceToken);
                    Object activity = OneStepReflection.call(workspace, "getTopNonFinishingActivity");
                    if (!standalone(workspace) || !OneStepActivityProtocol.isActivity(
                            (ComponentName) ReflectUtils.getField(activity, "mActivityComponent")))
                        throw new SecurityException("Invalid workspace task");
                    ServerSession previous = WORKSPACES.get(workspace);
                    if (previous != null) previous.close();
                    ServerSession session = new ServerSession(service, workspace, callback, uid);
                    callback.linkToDeath(session, 0);
                    WORKSPACES.put(workspace, session);
                    hasSessions = true;
                    reply.writeNoException();
                    reply.writeStrongBinder(session);
                }
                return true;
            });
            installed = true;
            Log.i(TAG, "workspace IME policy installed");
        } catch (Throwable error) { Log.w(TAG, "Workspace IME policy unavailable", error); }
    }

    private static Object resolveTask(IBinder token) throws ReflectiveOperationException {
        if (token == null) throw new IllegalArgumentException("Missing task token");
        Object container = fromBinder.invoke(null, token);
        Object task = container == null ? null : OneStepReflection.call(container, "asTask");
        if (task == null) throw new IllegalArgumentException("Task no longer exists");
        return task;
    }

    private static boolean standalone(Object task) {
        int mode = ReflectUtils.invokeNoArgInt(task, "getWindowingMode", -1);
        return task != null && Boolean.TRUE.equals(ReflectUtils.invokeNoArg(task, "isAttached"))
                && Boolean.TRUE.equals(ReflectUtils.invokeNoArg(task, "isRootTask"))
                && ReflectUtils.invokeNoArgInt(task, "getDisplayId", -1) == 0 && (mode == 1 || mode == 6);
    }

    private static ServerSession ownerOf(Object task) {
        // HOME can have a child task; match its registered root rather than its numeric ID.
        for (Object current = task; current != null; current = ReflectUtils.invokeNoArg(current, "getParent")) {
            ServerSession owner = OWNERS.get(current);
            if (owner != null) return standalone(current) ? owner : null;
        }
        return null;
    }

    private static void report(Object display) {
        if (!hasSessions) return;
        try {
            Object service = OneStepReflection.get(display, "mWmService");
            synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                if (ReflectUtils.invokeNoArgInt(display, "getDisplayId", -1) != 0) return;
                Object input = ReflectUtils.invokeNoArg(ReflectUtils.getField(display, "mImeInputTarget"), "getWindowState");
                Object task = ReflectUtils.invokeNoArg(input, "getTask");
                Object parent = ReflectUtils.getField(display, "mInputMethodSurfaceParent");
                Object container = ReflectUtils.getField(display, "mImeWindowsContainer");
                Object displaySurface = ReflectUtils.invokeNoArg(ReflectUtils.invokeNoArg(container, "getParent"), "getSurfaceControl");
                int placement = sameSurface(parent, displaySurface) ? DISPLAY : UNKNOWN;
                Object layering = ReflectUtils.getField(display, "mImeLayeringTarget");
                Object appSurface = ReflectUtils.invokeNoArg(ReflectUtils.getField(layering, "mActivityRecord"), "getSurfaceControl");
                if (sameSurface(parent, appSurface)) placement = APP;
                ServerSession owner = ownerOf(task);
                for (ServerSession session : new ArrayList<>(WORKSPACES.values())) {
                    if (!session.live()) { session.close(); continue; }
                    IBinder token = null;
                    if (owner == session) {
                        for (Object current = task; current != null && token == null;
                                current = ReflectUtils.invokeNoArg(current, "getParent")) {
                            for (Map.Entry<IBinder, Object> entry : session.tasks.entrySet())
                                if (entry.getValue() == current) { token = entry.getKey(); break; }
                        }
                    }
                    session.report(token, token == null ? UNKNOWN : placement);
                }
            }
        } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
    }

    private static boolean sameSurface(Object first, Object second) throws ReflectiveOperationException {
        return first instanceof SurfaceControl && second instanceof SurfaceControl
                && Boolean.TRUE.equals(OneStepReflection.call(first, "isSameSurface",
                        new Class<?>[]{SurfaceControl.class}, second));
    }

    private static final class ServerSession extends Binder implements IBinder.DeathRecipient {
        final Object service;
        final Object workspace;
        final IBinder callback;
        final int uid;
        final Handler handler;
        final Object lock;
        final Map<IBinder, Object> tasks = new HashMap<>();
        volatile boolean closed;
        IBinder reportedToken;
        int reportedPlacement = -1;
        boolean reportPending;

        ServerSession(Object service, Object workspace, IBinder callback, int uid) throws ReflectiveOperationException {
            this.service = service;
            this.workspace = workspace;
            this.callback = callback;
            this.uid = uid;
            handler = (Handler) OneStepReflection.get(service, "mH");
            lock = OneStepReflection.get(service, "mGlobalLock");
        }

        boolean live() { return !closed && callback.isBinderAlive() && standalone(workspace); }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code < ADD || code > CLOSE) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESCRIPTOR);
            if (Binder.getCallingUid() != uid) throw new SecurityException("Workspace owner mismatch");
            if (reply == null) return false;
            try {
                synchronized (lock) {
                    if (code == CLOSE) close();
                    else if (code == REMOVE) {
                        Object task = tasks.remove(data.readStrongBinder());
                        if (task != null && OWNERS.get(task) == this) OWNERS.remove(task);
                        refresh();
                    } else {
                        if (!live()) throw new IllegalStateException("Workspace expired");
                        IBinder token = data.readStrongBinder();
                        Object task = resolveTask(token);
                        int type = ReflectUtils.invokeNoArgInt(task, "getActivityType", -1);
                        if (task == workspace || !standalone(task) || (type != 1 && type != 2)
                                || ReflectUtils.getIntField(task, "mUserId", -1)
                                != ReflectUtils.getIntField(workspace, "mUserId", -2))
                            throw new SecurityException("Task is outside this workspace");
                        ServerSession previous = OWNERS.get(task);
                        if (previous != null && previous != this) throw new IllegalStateException("Task already hosted");
                        tasks.put(token, task);
                        OWNERS.put(task, this);
                        Log.i(TAG, "policy-register task=" + ReflectUtils.getIntField(task, "mTaskId", -1));
                        refresh();
                    }
                    reply.writeNoException();
                }
            } catch (Exception error) {
                reply.writeException(error instanceof RuntimeException ? (RuntimeException) error
                        : new IllegalStateException("Cannot update workspace IME policy", error));
            }
            return true;
        }

        void refresh() {
            try {
                Object display = OneStepReflection.call(service, "getDefaultDisplayContentLocked");
                if (display != null) {
                    OneStepReflection.call(display, "updateImeControlTarget", new Class<?>[]{boolean.class}, true);
                    OneStepImePolicy.report(display);
                }
            } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
        }

        void report(IBinder token, int placement) {
            if (java.util.Objects.equals(token, reportedToken) && placement == reportedPlacement) return;
            reportedToken = token;
            reportedPlacement = placement;
            if (reportPending) return;
            reportPending = true;
            // Never transact back into SystemUI while holding WMS's global lock.
            handler.post(() -> {
                IBinder latestToken;
                int latestPlacement;
                synchronized (lock) {
                    reportPending = false;
                    if (closed) return;
                    latestToken = reportedToken;
                    latestPlacement = reportedPlacement;
                }
                try {
                    OneStepActivityProtocol.send(callback, DESCRIPTOR, ROUTING, data -> {
                        data.writeStrongBinder(latestToken);
                        data.writeInt(latestPlacement);
                    });
                } catch (RemoteException error) { binderDied(); }
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            callback.unlinkToDeath(this, 0);
            WORKSPACES.remove(workspace, this);
            hasSessions = !WORKSPACES.isEmpty();
            for (Object task : tasks.values()) OWNERS.remove(task, this);
            tasks.clear();
            refresh();
        }

        @Override public void binderDied() {
            handler.post(() -> {
                try {
                    synchronized (lock) { close(); }
                } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
            });
        }
    }

    interface Listener { void onRouting(IBinder task, int placement); }

    /** SystemUI endpoint. Keep the callback alive until the last task has been restored. */
    static final class Session {
        private final IBinder callback;
        private final IBinder server;
        private volatile boolean closed;

        Session(IBinder workspace, Listener listener) throws Exception {
            callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code != ROUTING) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(DESCRIPTOR);
                    if (Binder.getCallingUid() != Process.SYSTEM_UID) throw new SecurityException("IME server mismatch");
                    IBinder task = data.readStrongBinder();
                    int placement = data.readInt();
                    if (!closed) listener.onRouting(task, placement);
                    return true;
                }
            };
            IBinder window = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "window");
            if (window == null) throw new IllegalStateException("Window service unavailable");
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(workspace);
                data.writeStrongBinder(callback);
                if (!window.transact(CONNECT, data, reply, 0))
                    throw new IllegalStateException("Workspace IME policy unavailable; reload system_server");
                reply.readException();
                server = reply.readStrongBinder();
                if (server == null) throw new IllegalStateException("Missing IME session");
            } finally { data.recycle(); reply.recycle(); }
        }

        void add(IBinder task) throws RemoteException { send(ADD, task); }
        void remove(IBinder task) throws RemoteException { send(REMOVE, task); }
        void close() throws RemoteException {
            if (closed) return;
            send(CLOSE, null);
            closed = true;
        }

        private void send(int code, IBinder task) throws RemoteException {
            if (closed) throw new IllegalStateException("IME session closed");
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(task);
                if (!server.transact(code, data, reply, 0)) throw new RemoteException("IME policy transaction rejected");
                reply.readException();
            } finally { data.recycle(); reply.recycle(); }
        }
    }
}
