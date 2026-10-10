package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.example.flymestatusbarsizer.BuildConfig;

/** SystemUI end of one Activity's embedded workspace, confined to the UI handler. */
final class OneStepActivitySession {
    interface Listener {
        void onAttached(int taskId, Rect bounds, Rect insets);
        void onReattaching(int taskId, Rect bounds, Rect insets, Runnable mount);
        void onResized(Rect bounds, Rect insets);
        void onMounted(boolean visible);
        void onImeChanged(int bottom, boolean animating);
        void onBarColorsChanged(boolean darkIcons);
        void onClosed(boolean focusMain);
        void onVisibilityChanged(boolean visible);
    }

    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private final IBinder control;
    private final int userId;
    private final int ownerUserId;
    private final int clientUid;
    private IBinder client;
    private IBinder hostToken;
    private IBinder.DeathRecipient death;
    private SurfaceControlViewHost host;
    private FrameLayout content;
    private View view;
    private boolean closed;
    private boolean mounted;
    private boolean attaching;
    private int width;
    private int height;
    private int activityTaskId = -1;
    private Rect stableInsets;
    private int revision;
    private int surfaceRequest;
    private final Runnable restart = this::restartHost;
    private final Runnable restartTimeout = this::checkRestartTimeout;

    OneStepActivitySession(Context context, Handler handler, int userId, int ownerUserId, Listener listener) throws Exception {
        this.context = context;
        this.handler = handler;
        this.userId = userId;
        this.ownerUserId = ownerUserId;
        this.listener = listener;
        clientUid = ((Number) OneStepReflection.call(context.getPackageManager(), "getPackageUidAsUser",
                new Class<?>[]{String.class, int.class}, BuildConfig.APPLICATION_ID, userId)).intValue();
        control = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code < OneStepActivityProtocol.ATTACH || code > OneStepActivityProtocol.DETACH)
                    return super.onTransact(code, data, reply, flags);
                if (Binder.getCallingUid() != clientUid) throw new SecurityException("Workspace client mismatch");
                data.enforceInterface(OneStepActivityProtocol.CONTROL);
                IBinder callback = data.readStrongBinder();
                if (callback == null) return true;
                switch (code) {
                    case OneStepActivityProtocol.ATTACH:
                        IBinder token = data.readStrongBinder();
                        int taskId = data.readInt();
                        int w = data.readInt();
                        int h = data.readInt();
                        Rect insets = data.readTypedObject(Rect.CREATOR);
                        int attachRequest = data.readInt();
                        handler.post(() -> attach(callback, token, taskId, w, h, insets, attachRequest));
                        break;
                    case OneStepActivityProtocol.MOUNTED:
                        int mountedRequest = data.readInt();
                        boolean mountedVisible = data.readBoolean();
                        handler.post(() -> {
                            if (!closed && callback.equals(client) && host != null && !mounted
                                    && mountedRequest == surfaceRequest) {
                                mounted = true;
                                listener.onMounted(mountedVisible);
                            }
                        });
                        break;
                    case OneStepActivityProtocol.INSETS:
                        int bottom = data.readInt();
                        boolean animating = data.dataAvail() > 0 && data.readBoolean();
                        handler.post(() -> {
                            if (!closed && mounted && callback.equals(client))
                                listener.onImeChanged(Math.max(0, Math.min(bottom, height)), animating);
                        });
                        break;
                    case OneStepActivityProtocol.CLOSE:
                        boolean focusMain = data.readBoolean();
                        handler.post(() -> {
                            if (closed) finish(callback);
                            else if (client == null) {
                                listener.onClosed(focusMain);
                                finish(callback);
                            } else if (callback.equals(client)) listener.onClosed(focusMain);
                        });
                        break;
                    case OneStepActivityProtocol.BAR_COLORS:
                        boolean darkIcons = data.readBoolean();
                        handler.post(() -> {
                            if (!closed && mounted && callback.equals(client))
                                listener.onBarColorsChanged(darkIcons);
                        });
                        break;
                    case OneStepActivityProtocol.VISIBILITY:
                        boolean visible = data.readBoolean();
                        handler.post(() -> {
                            if (!closed && callback.equals(client)) listener.onVisibilityChanged(visible);
                        });
                        break;
                    case OneStepActivityProtocol.REMOUNT:
                        IBinder newToken = data.readStrongBinder();
                        int remountRequest = data.readInt();
                        handler.post(() -> {
                            if (closed || !callback.equals(client) || host == null) return;
                            if (!java.util.Objects.equals(hostToken, newToken)) {
                                attach(callback, newToken, activityTaskId, width, height, stableInsets, remountRequest);
                                return;
                            }
                            surfaceRequest = remountRequest;
                            mounted = false;
                            try { sendSurface(); }
                            catch (Exception error) {
                                Log.w(OneHandedTaskHooks.TAG, "Cannot remount workspace", error);
                                listener.onClosed(false);
                            }
                        });
                        break;
                    case OneStepActivityProtocol.DETACH:
                        boolean restartActivity = data.readBoolean();
                        handler.post(() -> {
                            if (closed || !callback.equals(client)) return;
                            disconnectClient();
                            if (view == null) attaching = false;
                            revision++;
                            mounted = false;
                            listener.onVisibilityChanged(false);
                            // Config recreation will attach itself; a finished host needs a new task.
                            handler.removeCallbacks(restart);
                            handler.postDelayed(restart, restartActivity ? 100 : 2000);
                        });
                        break;
                    default: break;
                }
                return true;
            }
        };
    }

    void start() throws Exception {
        Bundle extras = new Bundle();
        extras.putBinder(OneStepActivityProtocol.EXTRA_CONTROL, control);
        extras.putInt(OneStepActivityProtocol.EXTRA_UID, Process.myUid());
        Intent intent = new Intent().setClassName(BuildConfig.APPLICATION_ID, OneStepActivityProtocol.ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtras(extras);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
        options.setLaunchDisplayId(0);
        UserHandle user = UserHandle.getUserHandleForUid(userId * 100000);
        OneStepReflection.call(context, "startActivityAsUser",
                new Class<?>[]{Intent.class, Bundle.class, UserHandle.class}, intent, options.toBundle(), user);
    }

    private void attach(IBinder callback, IBinder token, int taskId, int w, int h, Rect insets, int surfaceRequest) {
        if (closed) {
            finish(callback);
            return;
        }
        if (token == null || taskId < 0 || insets == null
                || w <= 0 || h <= 0 || w > 16384 || h > 16384
                || insets.left < 0 || insets.top < 0 || insets.right < 0 || insets.bottom < 0
                || insets.left + insets.right >= w || insets.top + insets.bottom >= h) {
            // Rotation can deliver old insets with the new surface size for one frame.
            // Keep the current host until a coherent layout arrives.
            return;
        }
        if (client != null && !client.equals(callback) && taskId != activityTaskId) {
            finish(callback);
            return;
        }
        boolean sameHost = callback.equals(client) && token.equals(hostToken);
        if (!callback.equals(client)) {
            disconnectClient();
            client = callback;
            death = () -> handler.post(() -> {
                if (!closed && callback.equals(client)) listener.onClosed(false);
            });
            try { callback.linkToDeath(death, 0); }
            catch (RemoteException error) { listener.onClosed(false); return; }
        }
        handler.removeCallbacks(restart);
        handler.removeCallbacks(restartTimeout);
        activityTaskId = taskId;
        width = w;
        height = h;
        stableInsets = new Rect(insets);
        this.surfaceRequest = surfaceRequest;
        mounted = false;
        int request = ++revision;
        try {
            if (view == null) {
                hostToken = token;
                if (!attaching) {
                    attaching = true;
                    listener.onAttached(taskId, new Rect(0, 0, w, h), insets);
                }
            } else if (sameHost && host != null) {
                host.relayout(width, height);
                listener.onResized(new Rect(0, 0, w, h), insets);
                sendSurface();
            } else {
                listener.onVisibilityChanged(false);
                listener.onReattaching(taskId, new Rect(0, 0, w, h), insets, () -> {
                    if (closed || request != revision || !callback.equals(client)) return;
                    try {
                        listener.onResized(new Rect(0, 0, width, height), stableInsets);
                        // Shell has moved task leashes out of the old surface tree first.
                        if (content != null) content.removeAllViews();
                        if (host != null) host.release();
                        host = null;
                        hostToken = token;
                        createHost();
                    } catch (Exception error) {
                        Log.w(OneHandedTaskHooks.TAG, "Cannot reattach workspace", error);
                        listener.onClosed(false);
                    }
                });
            }
        } catch (Exception error) {
            Log.w(OneHandedTaskHooks.TAG, "Workspace Activity disconnected", error);
            listener.onClosed(false);
        }
    }

    void setView(View view) throws Exception {
        if (closed || client == null || host != null) throw new IllegalStateException("No workspace Activity");
        this.view = view;
        createHost();
        listener.onResized(new Rect(0, 0, width, height), stableInsets);
    }

    boolean isMounted() { return mounted && !closed; }

    private void createHost() throws Exception {
        host = new SurfaceControlViewHost(context, context.getDisplay(), hostToken);
        content = new FrameLayout(context);
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.setTitle("FlymeOneStepContent");
        // This embedded window is a child of the Activity's SurfaceView.
        // TaskView opens input holes for cross-UID application windows inside this surface tree.
        OneStepReflection.call(params, "setTrustedOverlay");
        OneStepReflection.call(host, "setView", new Class<?>[]{View.class, WindowManager.LayoutParams.class}, content, params);
        sendSurface();
    }

    private void disconnectClient() {
        if (client != null && death != null) {
            try { client.unlinkToDeath(death, 0); }
            catch (RuntimeException error) { Log.w(OneHandedTaskHooks.TAG, "Workspace client already disconnected", error); }
        }
        client = null;
        death = null;
    }

    private void restartHost() {
        if (closed || client != null) return;
        if (!OneHandedTaskHooks.workspaceAllowed(context, ownerUserId)) {
            handler.postDelayed(restart, 500);
            return;
        }
        try {
            start();
            handler.removeCallbacks(restartTimeout);
            handler.postDelayed(restartTimeout, 12000);
        }
        catch (Exception error) {
            Log.w(OneHandedTaskHooks.TAG, "Cannot recreate workspace Activity", error);
            listener.onClosed(false);
        }
    }

    private void checkRestartTimeout() {
        if (closed || client != null) return;
        if (!OneHandedTaskHooks.workspaceAllowed(context, ownerUserId)) {
            handler.postDelayed(restartTimeout, 700);
            return;
        }
        Log.w(OneHandedTaskHooks.TAG, "Workspace Activity did not reconnect");
        listener.onClosed(false);
    }

    private void sendSurface() throws RemoteException {
        SurfaceControlViewHost.SurfacePackage pack = host.getSurfacePackage();
        if (pack == null) throw new IllegalStateException("Workspace surface is unavailable");
        try {
            if (!OneStepActivityProtocol.send(client, OneStepActivityProtocol.CALLBACK,
                    OneStepActivityProtocol.SURFACE, data -> {
                        data.writeInt(surfaceRequest);
                        data.writeTypedObject(pack, 0);
                    })) {
                throw new RemoteException("Workspace Activity rejected its surface");
            }
        } finally { pack.release(); }
    }

    void close() {
        if (closed) return;
        closed = true;
        revision++;
        handler.removeCallbacks(restart);
        handler.removeCallbacks(restartTimeout);
        if (client != null) {
            finish(client);
        }
        disconnectClient();
        if (host != null) {
            try { host.release(); }
            catch (RuntimeException error) { Log.w(OneHandedTaskHooks.TAG, "Cannot release workspace surface", error); }
            host = null;
        }
        view = null;
        content = null;
        hostToken = null;
    }

    private void finish(IBinder callback) {
        try {
            OneStepActivityProtocol.send(callback, OneStepActivityProtocol.CALLBACK,
                    OneStepActivityProtocol.FINISH, data -> {});
        } catch (RemoteException error) { Log.w(OneHandedTaskHooks.TAG, "Workspace Activity already gone", error); }
    }
}
