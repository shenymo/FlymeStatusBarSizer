package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.inputmethod.EditorInfo;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A task-token capability scopes IME policy to a live workspace, including fullscreen tasks. */
public final class OneStepImePolicy {
    private static final String TAG = "FlymeOneStepIme";
    private static final String DESCRIPTOR = BuildConfig.APPLICATION_ID + ".workspace.ime";
    private static final int CONNECT = 0x00534f49;
    private static final int ADD = IBinder.FIRST_CALL_TRANSACTION;
    private static final int REMOVE = ADD + 1;
    private static final int CLOSE = ADD + 2;
    private static final int ROUTING = IBinder.FIRST_CALL_TRANSACTION;
    private static final long PENDING_REGION_MAX_AGE_MS = 1500;
    static final int UNKNOWN = 0;
    static final int DISPLAY = 1;
    static final int APP = 2;

    // Only system_server uses these maps, always under WMS's global lock.
    private static final Map<Object, ServerSession> OWNERS = new IdentityHashMap<>();
    private static final Map<Object, ServerSession> WORKSPACES = new IdentityHashMap<>();
    private static final AtomicLong INPUT_EVENTS = new AtomicLong();
    private static final AtomicLong REGION_CALLBACKS = new AtomicLong();
    private static final AtomicBoolean INPUT_REGION_ERROR = new AtomicBoolean();
    private static final AtomicReference<EditorBinding> LATEST_EDITOR = new AtomicReference<>();
    private static volatile ServerSession[] inputSessions = new ServerSession[0];
    private static volatile String inputRegionHook = "unavailable";
    private static volatile boolean editorHookInstalled;
    private static Method fromBinder;
    private static volatile boolean installed;
    private static volatile boolean hasSessions;
    // The workspace only hosts display 0. Access under WMS's global lock.
    private static volatile ImeRegionObserver imeRegionObserver;

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
                if ((Integer) chain.getArg(0) == OneStepImeRegionProtocol.CONNECT && installed)
                    return connectImeRegionObserver(chain.getThisObject(), (Parcel) chain.getArg(1),
                            (Parcel) chain.getArg(2));
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
                    inputSessions = WORKSPACES.values().toArray(new ServerSession[0]);
                    hasSessions = true;
                    reply.writeNoException();
                    reply.writeStrongBinder(session);
                }
                return true;
            });
            installed = true;
            installInputRegionHooks(module, loader);
            Log.i(TAG, "workspace IME policy installed build=" + BuildConfig.VERSION_NAME);
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
        if (!hasSessions && imeRegionObserver == null) return;
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
                boolean imeVisible = Boolean.TRUE.equals(ReflectUtils.invokeNoArg(
                        ReflectUtils.getField(display, "mInputMethodWindow"), "isVisible"));
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
                    session.report(token, token == null ? UNKNOWN : placement,
                            token == null ? null : windowBinder(input), token != null && imeVisible);
                }
                if (imeRegionObserver != null) imeRegionObserver.refresh();
            }
        } catch (Exception error) { OneStepImeDiagnostics.unavailable(error); }
    }

    private static boolean sameSurface(Object first, Object second) throws ReflectiveOperationException {
        return first instanceof SurfaceControl && second instanceof SurfaceControl
                && Boolean.TRUE.equals(OneStepReflection.call(first, "isSameSurface",
                        new Class<?>[]{SurfaceControl.class}, second));
    }

    private static IBinder windowBinder(Object window) throws ReflectiveOperationException {
        Object client = OneStepReflection.get(window, "mClient");
        if (!(client instanceof IInterface)) throw new IllegalStateException("Missing window client");
        return ((IInterface) client).asBinder();
    }

    private static void installInputRegionHooks(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> session = Class.forName("com.android.server.wm.Session", false, loader);
            Class<?> stub = Class.forName("android.view.IWindowSession$Stub", false, loader);
            int rectangleTransaction = OneStepReflection.field(stub,
                    "TRANSACTION_onRectangleOnScreenRequested").getInt(null);
            // Read the app's request at the Binder entry. Hooking only the small
            // Session method can be bypassed if ART has inlined it into the stub.
            module.intercept(OneStepReflection.method(session, "onTransact",
                    int.class, Parcel.class, Parcel.class, int.class), chain -> {
                if (hasSessions && (Integer) chain.getArg(0) == rectangleTransaction) {
                    Parcel data = (Parcel) chain.getArg(1);
                    int position = data.dataPosition();
                    try {
                        data.enforceInterface("android.view.IWindowSession");
                        IBinder window = data.readStrongBinder();
                        Rect rectangle = data.readInt() != 0 ? Rect.CREATOR.createFromParcel(data) : null;
                        if (data.dataAvail() != 0)
                            throw new IllegalArgumentException("Unexpected input-region transaction payload");
                        receiveInputRegion(chain.getThisObject(), window, rectangle);
                    } catch (Throwable error) {
                        inputRegionError(error);
                    } finally {
                        data.setDataPosition(position);
                    }
                }
                // Preserve the original dispatch, including validation and the
                // accessibility path. Collection does not depend on that path.
                return chain.proceed();
            });
            inputRegionHook = "session-binder";
            Log.i(TAG, "workspace input-region hook installed entry=" + inputRegionHook
                    + " transaction=" + rectangleTransaction);
        } catch (Throwable error) {
            Log.w(TAG, "Workspace input-region Binder observer unavailable", error);
            try {
                Class<?> session = Class.forName("com.android.server.wm.Session", false, loader);
                module.intercept(OneStepReflection.method(session, "onRectangleOnScreenRequested",
                        IBinder.class, Rect.class), chain -> {
                    if (hasSessions) receiveInputRegion(chain.getThisObject(),
                            (IBinder) chain.getArg(0), (Rect) chain.getArg(1));
                    return chain.proceed();
                });
                inputRegionHook = "session-method";
                Log.i(TAG, "workspace input-region hook installed entry=" + inputRegionHook);
            } catch (Throwable fallbackError) {
                Log.w(TAG, "Workspace input regions unavailable", fallbackError);
            }
        }

        try {
            Class<?> imms = Class.forName("com.android.server.inputmethod.InputMethodManagerService", false, loader);
            Method start = null;
            // Prefer the internal entry after IMMS's caller checks. Parameter tails
            // differ between Android versions; only the window and editor are needed.
            for (String name : new String[]{"startInputOrWindowGainedFocusInternalLocked", "startInputOrWindowGainedFocus"}) {
                for (Method method : imms.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(name) && parameters.length > 7
                            && parameters[2] == IBinder.class && parameters[6] == EditorInfo.class) {
                        start = method;
                        break;
                    }
                }
                if (start != null) break;
            }
            if (start == null) throw new NoSuchMethodException("IMMS startInputOrWindowGainedFocus");
            start.setAccessible(true);
            module.intercept(start, chain -> {
                Object result = chain.proceed();
                if (chain.getArg(6) != null) {
                    try {
                        Object connection = chain.getArg(7);
                        onInputStarted(new EditorBinding((IBinder) chain.getArg(2),
                                (EditorInfo) chain.getArg(6), connection instanceof IInterface
                                        ? ((IInterface) connection).asBinder() : null));
                    }
                    catch (Throwable error) { inputRegionError(error); }
                }
                return result;
            });
            editorHookInstalled = true;
            Log.i(TAG, "workspace editor-change hook installed entry=" + start.getName());
        } catch (Throwable error) { Log.w(TAG, "Workspace editor-change observer unavailable", error); }
    }

    private static void receiveInputRegion(Object session, IBinder window, Rect rectangle) {
        REGION_CALLBACKS.incrementAndGet();
        try {
            reportInputRegion(session, window, rectangle, INPUT_EVENTS.incrementAndGet());
        } catch (Throwable error) { inputRegionError(error); }
    }

    private static void inputRegionError(Throwable error) {
        if (INPUT_REGION_ERROR.compareAndSet(false, true))
            Log.w(TAG, "Workspace input-region callback failed", error);
    }

    private static void reportInputRegion(Object windowSession, IBinder windowToken, Rect requested,
                                          long sequence) throws ReflectiveOperationException {
        if (windowToken == null) return;
        Object service = OneStepReflection.get(windowSession, "mService");
        synchronized (OneStepReflection.get(service, "mGlobalLock")) {
            // Resolve the sender first: its pre-draw request can arrive before IMMS
            // has changed mImeInputTarget to this window.
            Object windows = OneStepReflection.get(service, "mWindowMap");
            if (!(windows instanceof Map)) throw new IllegalStateException("Unexpected WMS window map");
            Object window = ((Map<?, ?>) windows).get(windowToken);
            if (window == null || OneStepReflection.get(window, "mSession") != windowSession) return;
            Object task = OneStepReflection.call(window, "getTask");
            ServerSession owner = ownerOf(task);
            if (owner == null || !owner.live()) return;
            owner.regionRequests++;
            // Keep an active cursor subscription authoritative over incidental
            // rectangle requests (which can describe a different scroll target).
            if (owner.anchorObserver != null) return;
            Object display = OneStepReflection.call(window, "getDisplayContent");
            Object input = ReflectUtils.invokeNoArg(ReflectUtils.getField(display, "mImeInputTarget"), "getWindowState");
            if (input != window && ReflectUtils.getField(display, "mCurrentFocus") != window) {
                owner.logInputRegion("ignored-unfocused");
                return;
            }
            IBinder taskToken = owner.tokenForTask(task);
            Object taskBounds = ReflectUtils.invokeNoArg(owner.tasks.get(taskToken), "getBounds");
            Rect bounds = taskBounds instanceof Rect ? new Rect((Rect) taskBounds) : null;
            RectF normalized = null;
            if (requested != null && requested.left <= requested.right && requested.top < requested.bottom
                    && bounds != null && !bounds.isEmpty()) {
                // ViewRootImpl has already added the window origin and app scrolling.
                // These are logical display coordinates, before the TaskView transform.
                // A caret may have zero width; only its vertical extent is needed.
                RectF region = new RectF(Math.max(requested.left, bounds.left),
                        Math.max(requested.top, bounds.top), Math.min(requested.right, bounds.right),
                        Math.min(requested.bottom, bounds.bottom));
                if (region.left <= region.right && region.top < region.bottom) {
                    normalized = new RectF((region.left - bounds.left) / bounds.width(),
                            (region.top - bounds.top) / bounds.height(),
                            (region.right - bounds.left) / bounds.width(),
                            (region.bottom - bounds.top) / bounds.height());
                }
            }
            owner.cacheInputRegion(windowToken, taskToken, bounds, normalized, sequence);
            report(display);
            if (windowToken.equals(owner.reportedWindow)) {
                owner.applyPendingInputRegion();
                owner.logInputRegion(normalized == null ? "outside-task" : "received");
            } else owner.logInputRegion("pending-ime-target");
        }
    }

    private static void onInputStarted(EditorBinding editor) {
        if (editor.window == null) return;
        // Keep the latest identity even outside a workspace. An already focused
        // editor need not restart input when its task is acquired by SystemUI.
        LATEST_EDITOR.accumulateAndGet(editor, (previous, next) ->
                previous == null || next.sequence > previous.sequence ? next : previous);
        long sequence = editor.sequence;
        for (ServerSession session : inputSessions) {
            // IMMS may hold its own lock. Never acquire WMS's lock on that thread.
            session.handler.post(() -> {
                try {
                    synchronized (session.lock) {
                        if (session.closed || sequence < session.editorSequence) return;
                        Object windows = OneStepReflection.get(session.service, "mWindowMap");
                        if (!(windows instanceof Map)) throw new IllegalStateException("Unexpected WMS window map");
                        Object window = ((Map<?, ?>) windows).get(editor.window);
                        if (window == null || ownerOf(OneStepReflection.call(window, "getTask")) != session) return;
                        session.updateEditor(editor);
                        if (imeRegionObserver != null) imeRegionObserver.refresh();
                    }
                } catch (Throwable error) { inputRegionError(error); }
            });
        }
    }

    private static final class EditorBinding {
        final long sequence = INPUT_EVENTS.incrementAndGet();
        final IBinder window;
        final IBinder connection;
        final String packageName;
        final int fieldId;
        final int inputType;
        final int imeOptions;

        EditorBinding(IBinder window, EditorInfo info, IBinder connection) {
            this.window = window;
            this.connection = connection;
            packageName = info.packageName;
            fieldId = info.fieldId;
            inputType = info.inputType;
            imeOptions = info.imeOptions;
        }

        boolean sameEditor(EditorBinding other) {
            return window.equals(other.window) && java.util.Objects.equals(packageName, other.packageName)
                    && fieldId == other.fieldId && inputType == other.inputType && imeOptions == other.imeOptions
                    && (fieldId > 0 || (connection != null && connection.equals(other.connection)));
        }
    }

    private static boolean connectImeRegionObserver(Object service, Parcel data, Parcel reply) {
        if (reply == null) return false;
        data.enforceInterface(OneStepImeRegionProtocol.DESCRIPTOR);
        IBinder imeToken = data.readStrongBinder();
        IBinder connection = data.readStrongBinder();
        IBinder callback = data.readStrongBinder();
        String packageName = data.readString();
        int fieldId = data.readInt();
        int inputType = data.readInt();
        int imeOptions = data.readInt();
        try {
            synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                Object display = OneStepReflection.call(service, "getDefaultDisplayContentLocked");
                int uid = Binder.getCallingUid();
                // Registration may precede creation of the IME window; the IME
                // retries after its visibility dispatch in that case.
                if (callback == null || connection == null || !ownsImeWindow(display, imeToken, uid)) {
                    reply.writeNoException();
                    reply.writeStrongBinder(null);
                    return true;
                }
                ImeRegionObserver observer = new ImeRegionObserver(service, imeToken, connection,
                        callback, uid, packageName, fieldId, inputType, imeOptions);
                callback.linkToDeath(observer, 0);
                if (imeRegionObserver != null) imeRegionObserver.close();
                imeRegionObserver = observer;
                observer.refresh();
                reply.writeNoException();
                reply.writeStrongBinder(observer);
            }
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Cannot observe workspace input", error));
        }
        return true;
    }

    private static boolean ownsImeWindow(Object display, IBinder token, int uid) {
        Object window = ReflectUtils.getField(display, "mInputMethodWindow");
        return token != null && window != null
                && ReflectUtils.getIntField(window, "mOwnerUid", -1) == uid
                && token.equals(ReflectUtils.getField(ReflectUtils.getField(window, "mToken"), "token"));
    }

    private static final class ImeRegionObserver extends Binder implements IBinder.DeathRecipient {
        final Object service;
        final Object lock;
        final Handler handler;
        final IBinder imeToken;
        final IBinder connection;
        final IBinder callback;
        final int uid;
        final String packageName;
        final int fieldId;
        final int inputType;
        final int imeOptions;
        ServerSession target;
        long editorSequence;
        long epoch;
        Rect bounds;
        boolean closed;
        String lastReason;

        ImeRegionObserver(Object service, IBinder imeToken, IBinder connection, IBinder callback,
                          int uid, String packageName, int fieldId, int inputType, int imeOptions)
                throws ReflectiveOperationException {
            this.service = service;
            this.imeToken = imeToken;
            this.connection = connection;
            this.callback = callback;
            this.uid = uid;
            this.packageName = packageName;
            this.fieldId = fieldId;
            this.inputType = inputType;
            this.imeOptions = imeOptions;
            lock = OneStepReflection.get(service, "mGlobalLock");
            handler = (Handler) OneStepReflection.get(service, "mH");
        }

        void refresh() throws ReflectiveOperationException {
            if (closed) return;
            Object display = OneStepReflection.call(service, "getDefaultDisplayContentLocked");
            ServerSession next = null;
            String reason = "ime-window-mismatch";
            if (ownsImeWindow(display, imeToken, uid)) {
                Object input = ReflectUtils.invokeNoArg(ReflectUtils.getField(display, "mImeInputTarget"), "getWindowState");
                next = ownerOf(ReflectUtils.invokeNoArg(input, "getTask"));
                EditorBinding editor = next == null ? null : next.editor;
                if (next == null || !next.live()) reason = "outside-workspace";
                else if (!next.reportedImeVisible) reason = "ime-hidden";
                else if (next.reportedPlacement != DISPLAY) reason = "ime-not-on-display";
                else if (editor == null) reason = "editor-missing";
                else if (!connection.equals(editor.connection)) reason = "connection-mismatch";
                else if (!editor.window.equals(next.reportedWindow)) reason = "editor-window-mismatch";
                else if (!java.util.Objects.equals(packageName, editor.packageName) || fieldId != editor.fieldId
                        || inputType != editor.inputType || imeOptions != editor.imeOptions) reason = "editor-mismatch";
                else reason = "active";
                if (!"active".equals(reason)) next = null;
            }
            if (!reason.equals(lastReason)) {
                lastReason = reason;
                Log.i(TAG, "ime-region policy-state reason=" + reason);
            }
            long sequence = next == null ? 0 : next.editorSequence;
            Rect nextBounds = next == null ? null : next.reportedBounds;
            if (target == next && editorSequence == sequence && java.util.Objects.equals(bounds, nextBounds)) return;
            clearRegion();
            target = next;
            editorSequence = sequence;
            bounds = nextBounds == null ? null : new Rect(nextBounds);
            epoch = next == null ? 0 : INPUT_EVENTS.incrementAndGet();
            Log.i(TAG, "ime-region policy epoch=" + epoch + " active=" + (next != null));
            long notifiedEpoch = epoch;
            // Never call into the IME while holding the window-manager lock.
            handler.post(() -> {
                try {
                    OneStepActivityProtocol.send(callback, OneStepImeRegionProtocol.DESCRIPTOR,
                            OneStepImeRegionProtocol.STATE, parcel -> parcel.writeLong(notifiedEpoch));
                } catch (RemoteException error) { binderDied(); }
            });
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != OneStepImeRegionProtocol.REGION && code != OneStepImeRegionProtocol.CLOSE)
                return super.onTransact(code, data, reply, flags);
            data.enforceInterface(OneStepImeRegionProtocol.DESCRIPTOR);
            if (Binder.getCallingUid() != uid) throw new SecurityException("Input method owner mismatch");
            try {
                synchronized (lock) {
                    if (code == OneStepImeRegionProtocol.CLOSE) { close(); return true; }
                    long receivedEpoch = data.readLong();
                    int source = data.readInt();
                    RectF region = OneStepImeRegionProtocol.readRegion(data);
                    if (closed) return true;
                    refresh();
                    if (target == null || receivedEpoch == 0 || receivedEpoch != epoch) return true;
                    REGION_CALLBACKS.incrementAndGet();
                    target.regionRequests++;
                    RectF normalized = normalizeInputRegion(region, bounds);
                    if (source != OneStepImeRegionProtocol.EDITOR && source != OneStepImeRegionProtocol.CARET)
                        normalized = null;
                    target.anchorObserver = normalized == null ? null : this;
                    target.setInputRegion(normalized, INPUT_EVENTS.incrementAndGet());
                    target.logInputRegion(normalized == null ? "anchor-invalid"
                            : source == OneStepImeRegionProtocol.EDITOR ? "anchor-editor" : "anchor-caret");
                }
            } catch (Exception error) { inputRegionError(error); }
            return true;
        }

        void clearRegion() {
            if (target != null && target.anchorObserver == this) {
                target.anchorObserver = null;
                target.setInputRegion(null, INPUT_EVENTS.incrementAndGet());
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            clearRegion();
            target = null;
            callback.unlinkToDeath(this, 0);
            if (imeRegionObserver == this) imeRegionObserver = null;
            handler.post(() -> {
                try {
                    OneStepActivityProtocol.send(callback, OneStepImeRegionProtocol.DESCRIPTOR,
                            OneStepImeRegionProtocol.STATE, data -> data.writeLong(0));
                } catch (RemoteException ignored) { }
            });
        }

        @Override public void binderDied() {
            handler.post(() -> { synchronized (lock) { close(); } });
        }
    }

    private static RectF normalizeInputRegion(RectF requested, Rect bounds) {
        if (!OneStepImeRegionProtocol.valid(requested) || bounds == null || bounds.isEmpty()) return null;
        RectF clipped = new RectF(Math.max(requested.left, bounds.left), Math.max(requested.top, bounds.top),
                Math.min(requested.right, bounds.right), Math.min(requested.bottom, bounds.bottom));
        if (!OneStepImeRegionProtocol.valid(clipped)) return null;
        return new RectF((clipped.left - bounds.left) / bounds.width(), (clipped.top - bounds.top) / bounds.height(),
                (clipped.right - bounds.left) / bounds.width(), (clipped.bottom - bounds.top) / bounds.height());
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
        IBinder reportedWindow;
        Rect reportedBounds;
        long inputRegionSequence = INPUT_EVENTS.get();
        RectF reportedInputRegion;
        IBinder regionWindow;
        IBinder regionTask;
        Rect regionBounds;
        RectF pendingInputRegion;
        long regionSequence;
        long regionTime;
        EditorBinding editor;
        ImeRegionObserver anchorObserver;
        long editorSequence;
        int regionRequests;
        int regionUpdates;
        long regionLogTime;
        String regionLogReason;
        boolean reportedImeVisible;
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

        IBinder tokenForTask(Object task) {
            for (Object current = task; current != null; current = ReflectUtils.invokeNoArg(current, "getParent")) {
                for (Map.Entry<IBinder, Object> entry : tasks.entrySet())
                    if (entry.getValue() == current) return entry.getKey();
            }
            return null;
        }

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
                        Log.i(TAG, "policy-register task=" + ReflectUtils.getIntField(task, "mTaskId", -1)
                                + " build=" + BuildConfig.VERSION_NAME + " regionHook=" + inputRegionHook
                                + " editorHook=" + editorHookInstalled);
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

        void report(IBinder token, int placement, IBinder inputWindow, boolean imeVisible) {
            EditorBinding latest = LATEST_EDITOR.get();
            if (token != null && latest != null && latest.window.equals(inputWindow)) updateEditor(latest);
            Object taskBounds = ReflectUtils.invokeNoArg(tasks.get(token), "getBounds");
            Rect bounds = taskBounds instanceof Rect ? (Rect) taskBounds : null;
            boolean sameTarget = java.util.Objects.equals(token, reportedToken)
                    && java.util.Objects.equals(inputWindow, reportedWindow)
                    && java.util.Objects.equals(bounds, reportedBounds);
            boolean visibilityChanged = reportedImeVisible != imeVisible;
            reportedImeVisible = imeVisible;
            if (sameTarget && placement == reportedPlacement) {
                if (visibilityChanged) logInputRegion(imeVisible ? "ime-shown" : "ime-hidden");
                return;
            }
            if (!sameTarget) {
                reportedWindow = inputWindow;
                reportedBounds = bounds == null ? null : new Rect(bounds);
                reportedInputRegion = null;
            }
            reportedToken = token;
            reportedPlacement = placement;
            if (!sameTarget) applyPendingInputRegion();
            if (visibilityChanged) logInputRegion(imeVisible ? "ime-shown" : "ime-hidden");
            scheduleReport();
        }

        void updateEditor(EditorBinding next) {
            if (next.sequence <= editorSequence) return;
            EditorBinding previous = editor;
            editor = next;
            editorSequence = next.sequence;
            if (previous == null || !next.window.equals(previous.window)) {
                logInputRegion("editor-window");
            } else if (!next.sameEditor(previous)) {
                if (next.window.equals(regionWindow) && regionSequence < next.sequence) clearPendingInputRegion();
                if (next.window.equals(reportedWindow)) setInputRegion(null, next.sequence);
                logInputRegion("editor-changed");
            } else logInputRegion("editor-rebound-preserved");
        }

        void cacheInputRegion(IBinder window, IBinder task, Rect bounds, RectF region, long sequence) {
            if (sequence < regionSequence || sequence < inputRegionSequence) return;
            regionWindow = window;
            regionTask = task;
            regionBounds = bounds;
            pendingInputRegion = region;
            regionSequence = sequence;
            regionTime = SystemClock.uptimeMillis();
        }

        void applyPendingInputRegion() {
            if (regionWindow != null && regionWindow.equals(reportedWindow)
                    && java.util.Objects.equals(regionTask, reportedToken)
                    && java.util.Objects.equals(regionBounds, reportedBounds)
                    && SystemClock.uptimeMillis() - regionTime <= PENDING_REGION_MAX_AGE_MS)
                setInputRegion(pendingInputRegion, regionSequence);
        }

        void clearPendingInputRegion() {
            regionWindow = null;
            regionTask = null;
            regionBounds = null;
            pendingInputRegion = null;
        }

        void setInputRegion(RectF region, long sequence) {
            // A delayed editor reset must not discard a newer rectangle from the app.
            if (sequence < inputRegionSequence) return;
            inputRegionSequence = sequence;
            if (java.util.Objects.equals(region, reportedInputRegion)) return;
            reportedInputRegion = region;
            regionUpdates++;
            scheduleReport();
        }

        void logInputRegion(String reason) {
            long now = SystemClock.uptimeMillis();
            if (reason.equals(regionLogReason) && now - regionLogTime < 1000) return;
            regionLogTime = now;
            regionLogReason = reason;
            Log.i(TAG, "input-region event=" + reason + " requests=" + regionRequests
                    + " updates=" + regionUpdates + " callbacks=" + REGION_CALLBACKS.get()
                    + " hook=" + inputRegionHook + " task="
                    + ReflectUtils.getIntField(tasks.get(reportedToken), "mTaskId", -1)
                    + " bounds=" + reportedBounds + " region=" + reportedInputRegion
                    + " pending=" + pendingInputRegion);
        }

        void scheduleReport() {
            if (reportPending) return;
            reportPending = true;
            // Never transact back into SystemUI while holding WMS's global lock.
            handler.post(() -> {
                IBinder latestToken;
                int latestPlacement;
                RectF latestRegion;
                synchronized (lock) {
                    reportPending = false;
                    if (closed) return;
                    latestToken = reportedToken;
                    latestPlacement = reportedPlacement;
                    latestRegion = reportedInputRegion == null ? null : new RectF(reportedInputRegion);
                }
                try {
                    OneStepActivityProtocol.send(callback, DESCRIPTOR, ROUTING, data -> {
                        data.writeStrongBinder(latestToken);
                        data.writeInt(latestPlacement);
                        data.writeInt(latestRegion != null ? 1 : 0);
                        if (latestRegion != null) latestRegion.writeToParcel(data, 0);
                    });
                } catch (RemoteException error) { binderDied(); }
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            callback.unlinkToDeath(this, 0);
            WORKSPACES.remove(workspace, this);
            inputSessions = WORKSPACES.values().toArray(new ServerSession[0]);
            hasSessions = !WORKSPACES.isEmpty();
            for (Object task : tasks.values()) OWNERS.remove(task, this);
            tasks.clear();
            clearPendingInputRegion();
            editor = null;
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

    interface Listener { void onRouting(IBinder task, int placement, RectF inputRegion); }

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
                    RectF inputRegion = data.readInt() != 0 ? RectF.CREATOR.createFromParcel(data) : null;
                    if (!closed) listener.onRouting(task, placement, inputRegion);
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
