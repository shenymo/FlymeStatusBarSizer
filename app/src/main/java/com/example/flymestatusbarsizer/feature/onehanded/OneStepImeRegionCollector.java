package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorBoundsInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.WeakHashMap;

/** IME-side geometry only. Cursor subscriptions are enabled by a live workspace capability. */
public final class OneStepImeRegionCollector {
    private static final String TAG = "FlymeOneStepIme";
    private static final int MODES = InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR;
    private static final int FILTERS = InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER
            | InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS;
    private static final WeakHashMap<InputConnection, Request> REQUESTS = new WeakHashMap<>();
    private static final IdentityHashMap<CursorAnchorInfo, AnchorStamp> ANCHORS = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> INTERNAL_REQUEST = new ThreadLocal<>();
    private static Handler main;
    private static Handler worker;
    private static volatile Collector current;
    private static boolean installed;

    private OneStepImeRegionCollector() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (installed || Build.VERSION.SDK_INT < 33) return;
        installed = true;
        try {
            Class<?> service = Class.forName("android.inputmethodservice.InputMethodService", false, loader);
            Class<?> remote = Class.forName("android.inputmethodservice.RemoteInputConnection", false, loader);
            Class<?> session = Class.forName("android.inputmethodservice.IInputMethodSessionWrapper", false, loader);
            main = new Handler(Looper.getMainLooper());

            // Remember the IME's own requests, including ones made by onStartInput
            // before our collector is attached. Never replace its subscription with ours.
            for (Method method : remote.getDeclaredMethods()) {
                if (!method.getName().equals("requestCursorUpdates")) continue;
                int count = method.getParameterCount();
                if (count != 1 && count != 2) continue;
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    if (Boolean.TRUE.equals(INTERNAL_REQUEST.get())) return chain.proceed();
                    InputConnection connection = (InputConnection) chain.getThisObject();
                    int flags = (Integer) chain.getArg(0);
                    Request request = new Request(flags & MODES,
                            count == 1 ? flags & ~MODES : (Integer) chain.getArg(1), count == 1);
                    synchronized (REQUESTS) { REQUESTS.put(connection, request); }
                    Collector active = current;
                    if (active == null || active.connection != connection || active.stopped || !active.augmentRequests)
                        return chain.proceed();
                    Request merged = request.withWorkspace(active.filtered);
                    Object[] args = chain.getArgs().toArray();
                    args[0] = count == 1 ? merged.mode | merged.filter : merged.mode;
                    if (count == 2) args[1] = merged.filter;
                    Object result = chain.proceed(args);
                    // Adding optional geometry must not make an otherwise valid
                    // request from the IME fail on a custom editor.
                    if (Boolean.FALSE.equals(result)) {
                        active.filtered = false;
                        return chain.proceed();
                    }
                    return result;
                });
            }

            module.intercept(OneStepReflection.method(service, "doStartInput",
                    InputConnection.class, EditorInfo.class, boolean.class), chain -> {
                stop((InputMethodService) chain.getThisObject());
                Object result = chain.proceed();
                begin((InputMethodService) chain.getThisObject());
                return result;
            });
            module.intercept(OneStepReflection.method(service, "doFinishInput"), chain -> {
                stop((InputMethodService) chain.getThisObject());
                return chain.proceed();
            });
            module.intercept(OneStepReflection.method(service, "onDestroy"), chain -> {
                stop((InputMethodService) chain.getThisObject());
                return chain.proceed();
            });
            // This private framework dispatch also covers IMEs overriding showWindow/onWindowShown.
            module.intercept(OneStepReflection.method(service, "setImeWindowVisibility", int.class), chain -> {
                Object result = chain.proceed();
                InputMethodService ime = (InputMethodService) chain.getThisObject();
                if (((Integer) chain.getArg(0) & 2) != 0) main.post(() -> begin(ime));
                else stop(ime);
                return result;
            });
            // Stamp at Binder ingress so a queued callback from the previous
            // input connection cannot acquire the next connection's epoch.
            module.intercept(OneStepReflection.method(session, "updateCursorAnchorInfo", CursorAnchorInfo.class), chain -> {
                CursorAnchorInfo info = (CursorAnchorInfo) chain.getArg(0);
                Collector active = current;
                if (info != null && active != null) synchronized (ANCHORS) {
                    if (ANCHORS.size() >= 128) ANCHORS.clear();
                    ANCHORS.put(info, new AnchorStamp(active, active.epoch));
                }
                return chain.proceed();
            });
            // The small enqueue method can be inlined into the Binder stub on
            // an AOT-compiled ROM. Ensure that ingress stamping still runs.
            try {
                Class<?> stub = Class.forName("com.android.internal.inputmethod.IInputMethodSession$Stub", false, loader);
                module.deoptimize(OneStepReflection.method(stub, "onTransact",
                        int.class, Parcel.class, Parcel.class, int.class));
            } catch (Throwable error) { Log.w(TAG, "ime-region Binder dispatch deoptimization unavailable", error); }
            // Observe the message before it calls the overridable IME callback.
            module.intercept(OneStepReflection.method(session, "executeMessage", Message.class), chain -> {
                Message message = (Message) chain.getArg(0);
                if (message.obj instanceof CursorAnchorInfo) {
                    Object receiver = ReflectUtils.getField(chain.getThisObject(), "mInputMethodSession");
                    Collector active = current;
                    AnchorStamp stamp;
                    synchronized (ANCHORS) { stamp = ANCHORS.remove((CursorAnchorInfo) message.obj); }
                    if (active != null && receiver != null
                            && stamp != null && stamp.collector == active && stamp.epoch == active.epoch
                            && !Boolean.FALSE.equals(ReflectUtils.invokeNoArg(receiver, "isEnabled"))) {
                        try { active.onAnchor((CursorAnchorInfo) message.obj); }
                        catch (Throwable error) { Log.w(TAG, "ime-region geometry unavailable", error); }
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "ime-region hooks installed entry=session-dispatch");
        } catch (Throwable error) { Log.w(TAG, "ime-region hooks unavailable", error); }
    }

    private static void begin(InputMethodService service) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> begin(service)); return; }
        try {
            if (!service.getCurrentInputStarted() || service.getDisplay().getDisplayId() != 0) return;
            InputConnection connection = (InputConnection) ReflectUtils.getField(service, "mStartedInputConnection");
            EditorInfo info = service.getCurrentInputEditorInfo();
            if (connection == null || info == null) return;
            Collector previous = current;
            if (previous != null && previous.service == service && previous.connection == connection) {
                previous.attach();
                return;
            }
            if (previous != null) previous.stop();
            // RemoteInputConnection's invoker holds the same Binder registered by IMMS.
            Object invoker = OneStepReflection.get(connection, "mInvoker");
            Object remote = OneStepReflection.get(invoker, "mConnection");
            IBinder token = (IBinder) OneStepReflection.get(service, "mToken");
            if (!(remote instanceof IInterface) || token == null) return;
            if (worker == null) {
                HandlerThread thread = new HandlerThread("OneStepImeRegion");
                thread.start();
                worker = new Handler(thread.getLooper());
            }
            Collector next = new Collector(service, connection, ((IInterface) remote).asBinder(), token, info);
            current = next;
            next.attach();
        } catch (Throwable error) { Log.w(TAG, "ime-region input connection unavailable", error); }
    }

    private static void stop(InputMethodService service) {
        Collector active = current;
        if (active != null && active.service == service) {
            current = null;
            active.stop();
        }
    }

    private static Request desired(InputConnection connection) {
        synchronized (REQUESTS) {
            Request request = REQUESTS.get(connection);
            return request == null ? new Request(0, 0) : request;
        }
    }

    private static final class Request {
        final int mode;
        final int filter;
        final boolean basic;

        Request(int mode, int filter) { this(mode, filter, false); }

        Request(int mode, int filter, boolean basic) {
            this.mode = mode;
            this.filter = filter;
            this.basic = basic;
        }

        Request withWorkspace(boolean filtered) {
            // No filter on an existing request means all geometry, not no geometry.
            int merged = !filtered || (mode != 0 && filter == 0) ? 0 : filter | FILTERS;
            return new Request(mode | MODES, merged, !filtered);
        }
    }

    private static final class AnchorStamp {
        final Collector collector;
        final long epoch;

        AnchorStamp(Collector collector, long epoch) { this.collector = collector; this.epoch = epoch; }
    }

    private static final class Collector {
        final InputMethodService service;
        final InputConnection connection;
        final IBinder connectionToken;
        final IBinder imeToken;
        final String packageName;
        final int fieldId;
        final int inputType;
        final int imeOptions;
        final IBinder callback;
        final Runnable retry = this::attach;
        volatile IBinder server;
        volatile boolean stopped;
        volatile long epoch;
        volatile boolean filtered = true;
        volatile boolean augmentRequests;
        boolean attaching;
        int attempts;
        long pendingEpoch;
        int anchorCount;
        boolean subscribed; // Worker thread only.
        RectF lastRegion;
        int lastSource;
        long logTime;
        Runnable upload;

        Collector(InputMethodService service, InputConnection connection, IBinder connectionToken,
                  IBinder imeToken, EditorInfo info) {
            this.service = service;
            this.connection = connection;
            this.connectionToken = connectionToken;
            this.imeToken = imeToken;
            packageName = info.packageName;
            fieldId = info.fieldId;
            inputType = info.inputType;
            imeOptions = info.imeOptions;
            callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code != OneStepImeRegionProtocol.STATE) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(OneStepImeRegionProtocol.DESCRIPTOR);
                    if (Binder.getCallingUid() != Process.SYSTEM_UID) throw new SecurityException("IME policy server mismatch");
                    long value = data.readLong();
                    main.post(() -> {
                        pendingEpoch = value;
                        if (server != null) setEpoch(value);
                    });
                    return true;
                }
            };
        }

        boolean live() {
            return !stopped && current == this && service.getCurrentInputStarted()
                    && ReflectUtils.getField(service, "mStartedInputConnection") == connection;
        }

        void attach() {
            if (!live() || attaching || server != null) return;
            main.removeCallbacks(retry);
            attaching = true;
            attempts++;
            worker.post(() -> {
                IBinder observer = null;
                try {
                    if (!stopped) {
                        IBinder window = (IBinder) Class.forName("android.os.ServiceManager")
                                .getMethod("getService", String.class).invoke(null, "window");
                        Parcel data = Parcel.obtain();
                        Parcel reply = Parcel.obtain();
                        try {
                            data.writeInterfaceToken(OneStepImeRegionProtocol.DESCRIPTOR);
                            data.writeStrongBinder(imeToken);
                            data.writeStrongBinder(connectionToken);
                            data.writeStrongBinder(callback);
                            data.writeString(packageName);
                            data.writeInt(fieldId);
                            data.writeInt(inputType);
                            data.writeInt(imeOptions);
                            if (window != null && window.transact(OneStepImeRegionProtocol.CONNECT, data, reply, 0)) {
                                reply.readException();
                                observer = reply.readStrongBinder();
                            }
                        } finally { data.recycle(); reply.recycle(); }
                    }
                } catch (Throwable error) { Log.w(TAG, "ime-region attach unavailable", error); }
                IBinder attached = observer;
                main.post(() -> {
                    attaching = false;
                    if (!live()) { if (attached != null) worker.post(() -> closeServer(attached)); return; }
                    server = attached;
                    if (attached != null) {
                        Log.i(TAG, "ime-region attached field=" + fieldId);
                        setEpoch(pendingEpoch);
                    } else if (attempts < 5) main.postDelayed(retry, attempts * 150L);
                    else Log.i(TAG, "ime-region attach rejected; waiting for next input/visibility event");
                });
            });
        }

        void setEpoch(long value) {
            if (!live() || epoch == value) return;
            epoch = value;
            augmentRequests = value != 0;
            lastRegion = null;
            anchorCount = 0;
            filtered = true;
            Log.i(TAG, "ime-region event=" + (value == 0 ? "inactive" : "request") + " epoch=" + value);
            worker.post(() -> {
                if (stopped || epoch != value) return;
                if (value == 0) { restoreSubscription(); return; }
                subscribed = true;
                boolean accepted = request(desired(connection).withWorkspace(true));
                if (!accepted && !stopped && epoch == value) {
                    filtered = false;
                    accepted = request(desired(connection).withWorkspace(false));
                }
                if (!accepted && epoch == value) {
                    augmentRequests = false;
                    restoreSubscription();
                }
                boolean result = accepted;
                main.post(() -> {
                    if (!live() || epoch != value) return;
                    Log.i(TAG, "ime-region request-result epoch=" + value + " accepted=" + result + " filtered=" + filtered);
                    if (result) main.postDelayed(() -> {
                        if (live() && epoch == value && anchorCount == 0)
                            Log.i(TAG, "ime-region no-callback epoch=" + value);
                    }, 1200);
                });
            });
        }

        void onAnchor(CursorAnchorInfo info) {
            if (!live() || epoch == 0 || server == null) return;
            anchorCount++;
            Matrix matrix = info.getMatrix();
            RectF caret = new RectF(info.getInsertionMarkerHorizontal(), info.getInsertionMarkerTop(),
                    info.getInsertionMarkerHorizontal(), info.getInsertionMarkerBottom());
            caret = mapRegion(matrix, caret);
            EditorBoundsInfo boundsInfo = info.getEditorBoundsInfo();
            RectF editor = mapRegion(matrix, boundsInfo == null ? null : boundsInfo.getEditorBounds());
            // Keep small input boxes fully visible. A large text editor should
            // follow its current line rather than lift the whole document.
            boolean useEditor = editor != null && (caret == null
                    || (editor.height() <= caret.height() * 4f && caret.top >= editor.top && caret.bottom <= editor.bottom));
            RectF region = useEditor ? editor : caret;
            int source = useEditor ? OneStepImeRegionProtocol.EDITOR : OneStepImeRegionProtocol.CARET;
            if (region == null && anchorCount == 1) Log.i(TAG, "ime-region invalid-anchor epoch=" + epoch);
            if (lastSource == source && java.util.Objects.equals(region, lastRegion)) return;
            lastRegion = region == null ? null : new RectF(region);
            lastSource = source;
            long generation = epoch;
            IBinder destination = server;
            if (upload != null) worker.removeCallbacks(upload);
            upload = () -> {
                if (stopped || epoch != generation) return;
                try {
                    OneStepActivityProtocol.send(destination, OneStepImeRegionProtocol.DESCRIPTOR,
                            OneStepImeRegionProtocol.REGION, data -> {
                                data.writeLong(generation);
                                data.writeInt(source);
                                OneStepImeRegionProtocol.writeRegion(data, region);
                            });
                } catch (RemoteException error) { Log.w(TAG, "ime-region report unavailable", error); }
            };
            worker.post(upload);
            long now = SystemClock.uptimeMillis();
            if (anchorCount == 1 || now - logTime >= 1000) {
                logTime = now;
                Log.i(TAG, "ime-region anchor epoch=" + generation + " source=" + (useEditor ? "editor" : "caret")
                        + " region=" + region);
            }
        }

        private RectF mapRegion(Matrix matrix, RectF local) {
            if (matrix == null || !OneStepImeRegionProtocol.valid(local)) return null;
            RectF display = new RectF(local);
            // CursorAnchorInfo maps view-local coordinates into the app's logical
            // display. SystemUI applies the TaskView scale exactly once afterward.
            matrix.mapRect(display);
            return OneStepImeRegionProtocol.valid(display) ? display : null;
        }

        boolean request(Request value) {
            INTERNAL_REQUEST.set(true);
            try {
                return value.basic ? connection.requestCursorUpdates(value.mode | value.filter)
                        : connection.requestCursorUpdates(value.mode, value.filter);
            }
            catch (Throwable error) {
                Log.w(TAG, "ime-region cursor request unavailable", error);
                return false;
            } finally { INTERNAL_REQUEST.remove(); }
        }

        void restoreSubscription() {
            if (!subscribed) return;
            subscribed = false;
            Request original = desired(connection);
            request(new Request(original.mode & InputConnection.CURSOR_UPDATE_MONITOR, original.filter, original.basic));
        }

        void stop() {
            if (stopped) return;
            stopped = true;
            epoch = 0;
            augmentRequests = false;
            main.removeCallbacks(retry);
            if (upload != null) worker.removeCallbacks(upload);
            synchronized (ANCHORS) { ANCHORS.values().removeIf(stamp -> stamp.collector == this); }
            worker.post(() -> {
                restoreSubscription();
                closeServer(server);
                server = null;
            });
        }

        void closeServer(IBinder binder) {
            try {
                OneStepActivityProtocol.send(binder, OneStepImeRegionProtocol.DESCRIPTOR,
                        OneStepImeRegionProtocol.CLOSE, data -> {});
            } catch (RemoteException ignored) { }
        }
    }
}
