package com.example.flymestatusbarsizer.feature.assistant;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import android.widget.Toast;

import com.example.flymestatusbarsizer.config.ModuleConfig;

/** SystemUI owns this connection; the assistant never needs to start HOME. */
final class AssistantClient {
    private static AssistantClient instance;
    private static volatile Integer statusBarTint;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reconnect = this::connect;
    private volatile boolean ready;
    private volatile IBinder remote;
    private Context bindingContext;
    private ServiceConnection connection;
    private int generation;
    private long request;
    private Runnable success;

    private AssistantClient(Context context) {
        this.context = context;
        main.post(() -> {
            IntentFilter filter = new IntentFilter("android.intent.action.USER_SWITCHED");
            filter.addAction(Intent.ACTION_USER_UNLOCKED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { disconnect(); connect(); }
            }, filter, Context.RECEIVER_EXPORTED);
            connect();
        });
    }

    static synchronized AssistantClient get(Context context) {
        if (instance == null) instance = new AssistantClient(context);
        return instance;
    }

    static synchronized void statusBarTintChanged(int color) {
        if (statusBarTint != null && statusBarTint == color) return;
        statusBarTint = color;
        AssistantClient current = instance;
        if (current == null) return;
        current.main.post(() -> {
            try {
                AssistantProtocol.send(current.remote, AssistantProtocol.DESCRIPTOR,
                        AssistantProtocol.TITLE_TINT, p -> p.writeInt(color));
            } catch (RemoteException e) { AssistantHooks.warn("Cannot sync assistant title tint", e); }
        });
    }

    static synchronized void refresh() {
        if (instance != null) instance.main.post(() -> {
            if (!enabled(instance.context)) instance.disconnect();
            else instance.connect();
        });
    }

    static boolean enabled(Context context) {
        ModuleConfig c = ModuleConfig.load(context);
        return c.enabled && c.assistantGestureEnabled;
    }

    boolean isReady() {
        IBinder binder = remote;
        return ready && binder != null && binder.isBinderAlive();
    }

    void show(boolean fromLeft, Runnable onSuccess) {
        ready = false;
        main.post(() -> {
            long id = ++request;
            success = onSuccess;
            try {
                if (!AssistantProtocol.send(remote, AssistantProtocol.DESCRIPTOR, AssistantProtocol.SHOW,
                        data -> {
                            data.writeLong(id);
                            data.writeInt(fromLeft ? 0 : 1);
                            Integer tint = com.example.flymestatusbarsizer.feature.statusbar
                                    .StatusBarTintHooks.currentIconTint();
                            if (tint == null) tint = statusBarTint;
                            if (tint != null) data.writeInt(tint);
                        })) failed(id);
            } catch (Throwable t) { AssistantHooks.warn("Assistant request failed", t); failed(id); }
            main.postDelayed(() -> { if (request == id && success != null) failed(id); }, 3000);
        });
    }

    private void failed(long id) {
        if (id != request || success == null) return;
        success = null;
        Toast.makeText(context, "负一屏暂不可用，请先从桌面打开一次", Toast.LENGTH_SHORT).show();
        disconnect();
        main.postDelayed(reconnect, 2000);
    }

    private void disconnect() {
        ready = false;
        try { AssistantProtocol.send(remote, AssistantProtocol.DESCRIPTOR, AssistantProtocol.HIDE, p -> { }); }
        catch (Throwable ignored) { }
        remote = null;
        success = null;
        ++generation;
        main.removeCallbacks(reconnect);
        if (connection != null) {
            try { bindingContext.unbindService(connection); } catch (Throwable ignored) { }
            connection = null;
        }
    }

    private void connect() {
        if (!enabled(context) || connection != null) return;
        main.removeCallbacks(reconnect);
        try {
            bindingContext = currentUserContext();
            int assistantUid = bindingContext.getPackageManager().getApplicationInfo(AssistantProtocol.PACKAGE, 0).uid;
            int epoch = ++generation;
            IBinder callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (Binder.getCallingUid() != assistantUid) return false;
                    if (code != AssistantProtocol.STATE && code != AssistantProtocol.RESULT)
                        return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(AssistantProtocol.CALLBACK);
                    if (code == AssistantProtocol.STATE) {
                        boolean value = data.readInt() != 0;
                        main.post(() -> { if (epoch == generation) ready = value; });
                    } else {
                        long id = data.readLong();
                        boolean shown = data.readInt() != 0;
                        main.post(() -> {
                            if (epoch != generation || id != request) return;
                            if (shown) {
                                Runnable action = success;
                                success = null;
                                if (action != null) action.run();
                            } else failed(id);
                        });
                    }
                    return true;
                }
            };
            connection = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    if (epoch != generation) return;
                    try {
                        if (!AssistantProtocol.DESCRIPTOR.equals(service.getInterfaceDescriptor())) { retry(); return; }
                        remote = service;
                        if (!AssistantProtocol.send(service, AssistantProtocol.DESCRIPTOR, AssistantProtocol.REGISTER,
                                p -> p.writeStrongBinder(callback))) retry();
                    } catch (Throwable t) { AssistantHooks.warn("Assistant control unavailable", t); retry(); }
                }
                @Override public void onServiceDisconnected(ComponentName name) { retry(); }
                @Override public void onBindingDied(ComponentName name) { retry(); }
                @Override public void onNullBinding(ComponentName name) { retry(); }
                private void retry() {
                    if (epoch != generation) return;
                    disconnect();
                    main.postDelayed(reconnect, 3000);
                }
            };
            Intent intent = new Intent(AssistantProtocol.ACTION)
                    .setComponent(new ComponentName(AssistantProtocol.PACKAGE, AssistantProtocol.SERVICE));
            if (!bindingContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                disconnect();
                main.postDelayed(reconnect, 5000);
            }
        } catch (Throwable t) {
            disconnect();
            AssistantHooks.warn("Cannot bind assistant", t);
            main.postDelayed(reconnect, 5000);
        }
    }

    private Context currentUserContext() throws ReflectiveOperationException {
        int user = (Integer) AssistantReflection.method(ActivityManager.class, "getCurrentUser").invoke(null);
        return (Context) AssistantReflection.method(Context.class, "createContextAsUser", UserHandle.class, int.class)
                .invoke(context, UserHandle.getUserHandleForUid(user * 100000), 0);
    }
}
