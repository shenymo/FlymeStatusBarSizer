package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Array;
import java.util.concurrent.Executor;

/** Runs Flyme's actual icon animator against temporary, pane-local animation surfaces. */
final class OneStepLaunchAnimation {
    private static final String TAG = "FlymeOneStepAnimation";
    private static final String DESCRIPTOR = "flyme.onestep.launch.animation";
    private static final int START = IBinder.FIRST_CALL_TRANSACTION;
    private static final int CANCEL = START + 1;
    private static final int FINISHED = START + 2;
    // Launcher process only. FloatingSurfaceManager may read this from its render thread.
    private static volatile Launch active;
    private static boolean supported;

    private OneStepLaunchAnimation() {}

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> surfaceContext = Class.forName(
                    "com.meizu.flyme.launcher.floatingsurface.ActivitySurfaceContext", false, loader);
            module.intercept(OneStepReflection.method(surfaceContext, "getParentSurface"), chain -> {
                Launch launch = active;
                if (launch != null && launch.iconParent != null && launch.iconParent.isValid()
                        && OneStepReflection.get(chain.getThisObject(), "activity") == launch.launcher)
                    return launch.iconParent;
                return chain.proceed();
            });
            supported = true;
        } catch (Exception error) { Log.w(TAG, "Native floating icon surface unavailable", error); }
    }

    static Launch prepare(Context launcher, View view, Object item, Class<?> itemClass, int ownerUid) {
        if (!supported || view == null || !view.isAttachedToWindow() || ownerUid <= 0) {
            Log.i(TAG, "Native animation unavailable: supported=" + supported
                    + " attached=" + (view != null && view.isAttachedToWindow()) + " owner=" + ownerUid);
            return null;
        }
        Object wrapper = null;
        try {
            wrapper = OneStepReflection.call(launcher, "getActivityLaunchOptions",
                    new Class<?>[]{View.class, itemClass}, view, item);
            ActivityOptions options = (ActivityOptions) OneStepReflection.get(wrapper, "options");
            Object adapter = OneStepReflection.call(options, "getRemoteAnimationAdapter");
            Object runner = OneStepReflection.call(adapter, "getRunner");
            Object factory = OneStepReflection.call(runner, "getFactory");
            Launch launch = new Launch(launcher, factory, OneStepReflection.get(wrapper, "onEndCallback"), ownerUid);
            Log.i(TAG, "Native icon animation prepared");
            return launch;
        } catch (Exception error) {
            if (wrapper != null) {
                try { OneStepReflection.call(OneStepReflection.get(wrapper, "onEndCallback"), "executeAllAndDestroy"); }
                catch (Exception ignored) { }
            }
            Log.w(TAG, "Opening without native launcher animation", error);
            return null;
        }
    }

    static final class Launch extends Binder {
        final Context launcher;
        final Object callbacks;
        private final Object factory;
        private final int ownerUid;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Class<?> targetsClass;
        private final Class<?> resultClass;
        private final Runnable timeout = () -> finish(true);
        private Object result;
        private IBinder finished;
        private IBinder.DeathRecipient death;
        private volatile SurfaceControl iconParent;
        private boolean started;
        private boolean closed;
        private long startedAt;

        Launch(Context launcher, Object factory, Object callbacks, int ownerUid) throws Exception {
            this.launcher = launcher;
            this.factory = factory;
            this.callbacks = callbacks;
            this.ownerUid = ownerUid;
            targetsClass = Array.newInstance(Class.forName("android.view.RemoteAnimationTarget"), 0).getClass();
            resultClass = Class.forName("com.android.launcher3.LauncherAnimationRunner$AnimationResult",
                    false, launcher.getClassLoader());
            // Resolve before accepting the icon tap; incompatible launchers use the normal fallback.
            resultClass.getConstructor(Runnable.class, Runnable.class);
            OneStepReflection.method(factory.getClass(), "onAnimationStart", int.class,
                    targetsClass, targetsClass, targetsClass, resultClass);
            main.postDelayed(timeout, 10000);
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != START && code != CANCEL) return super.onTransact(code, data, reply, flags);
            int callerUid = Binder.getCallingUid();
            if (callerUid != ownerUid) throw new SecurityException(
                    "Workspace owner UID mismatch: expected=" + ownerUid + " actual=" + callerUid);
            data.enforceInterface(DESCRIPTOR);
            if (code == CANCEL) main.post(() -> finish(true));
            else {
                Parcelable[] targets = data.readParcelableArray(launcher.getClassLoader());
                SurfaceControl parent = data.readTypedObject(SurfaceControl.CREATOR);
                IBinder completion = data.readStrongBinder();
                main.post(() -> start(targets, parent, completion));
            }
            return true;
        }

        private void start(Parcelable[] targets, SurfaceControl parent, IBinder completion) {
            if (started || closed || targets == null || targets.length != 2
                    || parent == null || !parent.isValid() || completion == null) {
                if (parent != null) parent.release();
                notifyFinished(completion);
                return;
            }
            started = true;
            startedAt = SystemClock.uptimeMillis();
            Log.i(TAG, "Native icon animation started");
            finished = completion;
            iconParent = parent;
            main.removeCallbacks(timeout);
            main.postDelayed(timeout, 4000);
            try {
                death = () -> main.post(() -> finish(true));
                completion.linkToDeath(death, 0);
                if (active != null) active.finish(true);
                active = this;
                alignLauncherCoordinates(targets);
                Object apps = Array.newInstance(targetsClass.getComponentType(), targets.length);
                for (int i = 0; i < targets.length; i++) Array.set(apps, i, targets[i]);
                Object empty = Array.newInstance(targetsClass.getComponentType(), 0);
                result = resultClass.getConstructor(Runnable.class, Runnable.class).newInstance(
                        (Runnable) () -> {}, (Runnable) () -> main.post(() -> finish(false)));
                // Invoke the native factory on the launcher main thread so construction errors
                // can fall back safely. Its AnimationResult starts the original Flyme spring.
                OneStepReflection.call(factory, "onAnimationStart", new Class<?>[]{int.class,
                        targetsClass, targetsClass, targetsClass, resultClass}, 0, apps, empty, empty, result);
            } catch (Exception error) {
                Log.w(TAG, "Native icon animation failed", error);
                finish(true);
            }
        }

        private void alignLauncherCoordinates(Parcelable[] targets) throws Exception {
            View dragLayer = (View) OneStepReflection.call(launcher, "getDragLayer");
            int[] origin = new int[2];
            dragLayer.getLocationOnScreen(origin);
            // Flyme animates its floating icon in DragLayer coordinates, but adds the
            // DragLayer's screen origin to the application surface matrix each frame.
            // Give the spring a local destination and the icon parent the same origin.
            // The Shell's shared viewport then crops/scales both into the main pane once.
            for (Parcelable target : targets) {
                if (((Number) OneStepReflection.get(target, "mode")).intValue() != 0) continue;
                Rect screen = (Rect) OneStepReflection.get(target, "screenSpaceBounds");
                Rect local = new Rect(screen);
                local.offset(-origin[0], -origin[1]);
                OneStepReflection.field(target.getClass(), "localBounds").set(target, local);
                Log.i(TAG, "Opening coordinates: screen=" + screen + " launcherLocal=" + local
                        + " origin=" + origin[0] + "," + origin[1]);
            }
            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                tx.setPosition(iconParent, origin[0], origin[1]).apply();
            }
        }

        void cancel() { main.post(() -> finish(true)); }

        private void finish(boolean cancel) {
            if (closed) return;
            closed = true;
            Log.i(TAG, "Native icon animation finished: started=" + started + " cancel=" + cancel
                    + " durationMs=" + (started ? SystemClock.uptimeMillis() - startedAt : 0));
            main.removeCallbacks(timeout);
            if (cancel) {
                try {
                    if (result != null) {
                        Object animator = OneStepReflection.get(result, "mMultiAnimator");
                        if (animator == null) animator = OneStepReflection.get(result, "mAnimator");
                        if (animator != null) OneStepReflection.call(animator, "end");
                    }
                    OneStepReflection.call(factory, "onAnimationCancelled");
                } catch (Exception error) { Log.w(TAG, "Cannot cancel native animation", error); }
            }
            try { OneStepReflection.call(callbacks, "executeAllAndDestroy"); }
            catch (Exception error) { Log.w(TAG, "Cannot complete launcher callbacks", error); }
            if (active == this) active = null;
            if (finished != null && death != null) {
                try { finished.unlinkToDeath(death, 0); }
                catch (RuntimeException ignored) { /* linkToDeath may have failed before start. */ }
            }
            notifyFinished(finished);
            finished = null;
            if (iconParent != null) { iconParent.release(); iconParent = null; }
        }
    }

    /** SystemUI side; all state and completion callbacks belong to the Shell executor. */
    static final class Remote {
        private final IBinder endpoint;
        private IBinder.DeathRecipient death;
        private boolean closed;

        Remote(IBinder endpoint) { this.endpoint = endpoint; }

        void start(Parcelable[] targets, SurfaceControl iconParent, Executor executor, Runnable done)
                throws RemoteException {
            death = () -> executor.execute(() -> { if (!closed) done.run(); });
            endpoint.linkToDeath(death, 0);
            IBinder completion = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (code != FINISHED) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(DESCRIPTOR);
                    executor.execute(() -> { if (!closed) done.run(); });
                    return true;
                }
            };
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeParcelableArray(targets, 0);
                data.writeTypedObject(iconParent, 0);
                data.writeStrongBinder(completion);
                if (!endpoint.transact(START, data, null, IBinder.FLAG_ONEWAY))
                    throw new RemoteException("Launcher animation unavailable");
            } finally { data.recycle(); }
        }

        void close(boolean cancel) {
            if (closed) return;
            closed = true;
            if (death != null) {
                try { endpoint.unlinkToDeath(death, 0); }
                catch (RuntimeException ignored) { /* The launcher may already be dead. */ }
            }
            if (cancel) cancelRemote(endpoint);
        }
    }

    static void cancelRemote(IBinder endpoint) {
        if (endpoint == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            endpoint.transact(CANCEL, data, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException | RuntimeException error) { Log.w(TAG, "Launcher animation disconnected", error); }
        finally { data.recycle(); }
    }

    private static void notifyFinished(IBinder endpoint) {
        if (endpoint == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            endpoint.transact(FINISHED, data, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException | RuntimeException error) { Log.w(TAG, "Animation owner disconnected", error); }
        finally { data.recycle(); }
    }
}
