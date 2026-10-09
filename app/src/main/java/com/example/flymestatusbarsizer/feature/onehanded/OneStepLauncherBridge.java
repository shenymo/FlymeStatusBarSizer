package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/** Session-scoped capability between SystemUI and the real Flyme launcher. */
public final class OneStepLauncherBridge {
    static final String LAUNCHER = "com.meizu.flyme.launcher";
    private static final String CONNECT = BuildConfig.APPLICATION_ID + ".workspace.launcher.CONNECT";
    private static final String DESCRIPTOR = CONNECT + ".control";
    private static final int ATTACH = IBinder.FIRST_CALL_TRANSACTION;
    private static final int ACTIVE = ATTACH + 1;
    private static final int LAUNCH = ATTACH + 2;
    private static final AtomicInteger requestCodes = new AtomicInteger();
    // Launcher-process state, accessed on its main thread.
    private static IBinder launcherControl;
    private static IBinder.DeathRecipient launcherDeath;
    private static boolean registered;

    private OneStepLauncherBridge() {}

    interface Listener {
        void onConnected();
        void onLaunch(PendingIntent intent);
        void onUnavailable();
    }

    static final class Session {
        private final Handler handler;
        private final Listener listener;
        private volatile boolean closed;
        private volatile boolean enabled;
        private volatile boolean pending;
        private boolean connected;
        private final Runnable timeout;

        Session(Context context, Handler handler, int userId, Listener listener) throws Exception {
            this.handler = handler;
            this.listener = listener;
            int launcherUid = ((Number) OneStepReflection.call(context.getPackageManager(), "getPackageUidAsUser",
                    new Class<?>[]{String.class, int.class}, LAUNCHER, userId)).intValue();
            timeout = () -> {
                if (!closed && !connected) { close(); listener.onUnavailable(); }
            };
            IBinder control = new Binder() {
                @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (code < ATTACH || code > LAUNCH) return super.onTransact(code, data, reply, flags);
                    if (Binder.getCallingUid() != launcherUid) throw new SecurityException("Launcher UID mismatch");
                    data.enforceInterface(DESCRIPTOR);
                    if (reply == null) return false;
                    if (code == ACTIVE) {
                        reply.writeNoException();
                        reply.writeInt(closed ? 0 : enabled && !pending ? 1 : 2);
                        return true;
                    }
                    boolean accepted = !closed;
                    if (code == ATTACH) {
                        handler.post(() -> {
                            if (!closed && !connected) {
                                connected = true;
                                handler.removeCallbacks(timeout);
                                listener.onConnected();
                            }
                        });
                    } else {
                        PendingIntent intent = data.readTypedObject(PendingIntent.CREATOR);
                        int targetUser = data.readInt();
                        accepted &= enabled && !pending && intent != null && targetUser == userId;
                        if (accepted) {
                            pending = true;
                            handler.post(() -> {
                                if (!closed) listener.onLaunch(intent);
                            });
                        }
                    }
                    reply.writeNoException();
                    reply.writeInt(accepted ? 1 : 0);
                    return true;
                }
            };
            Bundle extras = new Bundle();
            extras.putBinder("control", control);
            Intent connect = new Intent(CONNECT).setPackage(LAUNCHER).putExtras(extras);
            handler.postDelayed(timeout, 3000);
            try {
                OneStepReflection.call(context, "sendBroadcastAsUser", new Class<?>[]{Intent.class, UserHandle.class},
                        connect, UserHandle.getUserHandleForUid(userId * 100000));
            } catch (Exception error) { close(); throw error; }
        }

        void enable(boolean value) { enabled = value && !closed; }
        void retry() { pending = false; }
        void close() { closed = true; enabled = false; handler.removeCallbacks(timeout); }
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            Class<?> launcher = Class.forName("com.android.launcher3.uioverrides.QuickstepLauncher", false, loader);
            Method onCreate = OneStepReflection.method(launcher, "onCreate", Bundle.class);
            module.intercept(onCreate, chain -> {
                Object result = chain.proceed();
                try { register((Context) chain.getThisObject()); }
                catch (RuntimeException error) { Log.w(OneHandedTaskHooks.TAG, "Cannot register workspace receiver", error); }
                return result;
            });
            Class<?> item = Class.forName("com.android.launcher3.model.data.ItemInfo", false, loader);
            Method launch = OneStepReflection.method(launcher, "startActivitySafely", View.class, Intent.class, item);
            module.intercept(launch, chain -> {
                IBinder control = launcherControl;
                if (control == null) return chain.proceed();
                Context context = (Context) chain.getThisObject();
                try {
                    int state = transact(control, ACTIVE, null);
                    if (state == 0) return chain.proceed();
                    if (state != 1) return null;
                    Intent intent = new Intent((Intent) chain.getArg(1));
                    Object info = chain.getArg(2);
                    Object user = ReflectUtils.getField(info, "user");
                    UserHandle target = user instanceof UserHandle ? (UserHandle) user : Process.myUserHandle();
                    // Workspace task ownership currently stays within the foreground Android user.
                    if (!target.equals(Process.myUserHandle())) {
                        Toast.makeText(context, "暂不支持在工作台中打开其他用户的应用", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    PendingIntent pending = pendingIntent(context, intent, info, target);
                    int accepted = transact(control, LAUNCH, data -> {
                        data.writeTypedObject(pending, 0);
                        data.writeInt(Process.myUid() / 100000);
                    });
                    if (accepted != 1) return null;
                    Object callbacks = launch.getReturnType().getConstructor().newInstance();
                    OneStepReflection.call(callbacks, "executeAllAndDestroy");
                    return callbacks;
                } catch (Exception error) {
                    Log.w(OneHandedTaskHooks.TAG, "Cannot launch into workspace", error);
                    Toast.makeText(context, "无法在工作台中打开应用", Toast.LENGTH_SHORT).show();
                    return null;
                }
            });
        } catch (Exception error) { Log.w(OneHandedTaskHooks.TAG, "Launcher workspace hook unavailable", error); }
    }

    private static PendingIntent pendingIntent(Context context, Intent intent, Object item, UserHandle user)
            throws Exception {
        ActivityOptions creatorOptions = ActivityOptions.makeBasic();
        // The WCT is sent by system_server. Its sender-side BAL opt-in does not grant
        // the launcher's creator privileges; Android 15+ requires both explicitly.
        if (Build.VERSION.SDK_INT >= 35) {
            creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(Build.VERSION.SDK_INT >= 36
                    ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }
        Bundle options = creatorOptions.toBundle();
        if (ReflectUtils.getIntField(item, "itemType", -1) == 6) {
            String id = (String) OneStepReflection.call(item, "getDeepShortcutId");
            String pkg = intent.getPackage();
            if (pkg == null && intent.getComponent() != null) pkg = intent.getComponent().getPackageName();
            PendingIntent shortcut = context.getSystemService(LauncherApps.class).getShortcutIntent(pkg, id, options, user);
            if (shortcut == null) throw new IllegalStateException("Shortcut is unavailable");
            return shortcut;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCodes.incrementAndGet(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE, options);
    }

    private static void register(Context activity) {
        if (registered) return;
        Context context = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                Bundle extras = intent.getExtras();
                IBinder control = extras == null ? null : extras.getBinder("control");
                if (control == null) return;
                try {
                    if (transact(control, ATTACH, null) != 1) return;
                    if (launcherControl != null && launcherDeath != null)
                        launcherControl.unlinkToDeath(launcherDeath, 0);
                    launcherControl = control;
                    launcherDeath = () -> main.post(() -> {
                        if (launcherControl == control) { launcherControl = null; launcherDeath = null; }
                    });
                    control.linkToDeath(launcherDeath, 0);
                } catch (RemoteException | RuntimeException error) {
                    Log.w(OneHandedTaskHooks.TAG, "Workspace disconnected", error);
                }
            }
        }, new IntentFilter(CONNECT), "android.permission.STATUS_BAR_SERVICE", main, Context.RECEIVER_EXPORTED);
        registered = true;
    }

    private static int transact(IBinder control, int code, OneStepActivityProtocol.Writer writer)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (writer != null) writer.write(data);
            if (!control.transact(code, data, reply, 0)) return 0;
            reply.readException();
            return reply.readInt();
        } finally { data.recycle(); reply.recycle(); }
    }
}
