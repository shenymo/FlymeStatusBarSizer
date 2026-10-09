package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import java.util.HashSet;
import java.util.List;

/** Opaque application task; live workspace views remain owned by SystemUI. */
public final class OneStepActivity extends Activity implements SurfaceHolder.Callback {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IBinder control;
    private IBinder callback;
    private IBinder.DeathRecipient death;
    private SurfaceView surface;
    private boolean attached;
    private boolean mounted;
    private boolean closing;
    private boolean finished;
    private int surfaceWidth;
    private int surfaceHeight;
    private final HashSet<WindowInsetsAnimation> imeAnimations = new HashSet<>();
    private int imeBottom;
    private int sentImeBottom = -1;
    private boolean sentImeAnimating;
    private final Runnable timeout = () -> closeWorkspace(true);
    private final Runnable forceFinish = this::finishHost;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Bundle extras = getIntent().getExtras();
        control = extras == null ? null : extras.getBinder(OneStepActivityProtocol.EXTRA_CONTROL);
        int serverUid = extras == null ? -1 : extras.getInt(OneStepActivityProtocol.EXTRA_UID, -1);
        // A restored Activity must never reconnect to a session whose surfaces were destroyed.
        if (Build.VERSION.SDK_INT < 33 || state != null || control == null || serverUid < 0) {
            finishHost();
            return;
        }
        callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code != OneStepActivityProtocol.SURFACE && code != OneStepActivityProtocol.FINISH)
                    return super.onTransact(code, data, reply, flags);
                if (Binder.getCallingUid() != serverUid) throw new SecurityException("Workspace owner mismatch");
                data.enforceInterface(OneStepActivityProtocol.CALLBACK);
                if (code == OneStepActivityProtocol.SURFACE) {
                    SurfaceControlViewHost.SurfacePackage pack = data.readTypedObject(
                            SurfaceControlViewHost.SurfacePackage.CREATOR);
                    handler.post(() -> mount(pack));
                } else handler.post(OneStepActivity.this::finishHost);
                return true;
            }
        };
        death = () -> handler.post(this::finishHost);
        try { control.linkToDeath(death, 0); }
        catch (RemoteException error) { finishHost(); return; }

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(19, 21, 25)));
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.preferredRefreshRate = 120f;
        params.windowAnimations = 0;
        getWindow().setAttributes(params);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(19, 21, 25));
        surface = new SurfaceView(this);
        // The embedded window and its TaskView children receive input above the Activity's
        // own window. The Activity stays opaque in the task visibility calculation.
        surface.setZOrderOnTop(true);
        if (Build.VERSION.SDK_INT >= 34)
            surface.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // Layout receives the final insets before the first animation frame.
            // Forward only animated insets until the IME has finished moving.
            if (imeAnimations.isEmpty()) sendInsets(insets);
            return insets;
        });
        root.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
                WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override public void onPrepare(WindowInsetsAnimation animation) {
                if ((animation.getTypeMask() & WindowInsets.Type.ime()) != 0) {
                    imeAnimations.add(animation);
                    sendImeInsets(imeBottom);
                }
            }

            @Override public WindowInsets onProgress(WindowInsets insets,
                    List<WindowInsetsAnimation> runningAnimations) {
                if (!imeAnimations.isEmpty()) sendInsets(insets);
                return insets;
            }

            @Override public void onEnd(WindowInsetsAnimation animation) {
                if (imeAnimations.remove(animation) && imeAnimations.isEmpty()) {
                    WindowInsets insets = root.getRootWindowInsets();
                    if (insets != null) sendInsets(insets);
                    else sendImeInsets(imeBottom);
                }
            }
        });
        setContentView(root);
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            bars.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            bars.show(WindowInsets.Type.systemBars());
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> closeWorkspace(true));
        handler.postDelayed(timeout, 12000);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {}

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (closing || finished || width <= 0 || height <= 0) return;
        if (attached) {
            if (width != surfaceWidth || height != surfaceHeight) closeWorkspace(false);
            return;
        }
        IBinder hostToken = surface.getHostToken();
        WindowInsets insets = surface.getRootWindowInsets();
        if (hostToken == null || insets == null) {
            surface.postOnAnimation(() -> surfaceChanged(holder, format, surface.getWidth(), surface.getHeight()));
            return;
        }
        attached = true;
        surfaceWidth = width;
        surfaceHeight = height;
        Insets stable = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        send(OneStepActivityProtocol.ATTACH, data -> {
            data.writeStrongBinder(hostToken);
            data.writeInt(getTaskId());
            data.writeInt(width);
            data.writeInt(height);
            data.writeTypedObject(new Rect(stable.left, stable.top, stable.right, stable.bottom), 0);
        });
    }

    private void mount(SurfaceControlViewHost.SurfacePackage pack) {
        if (pack == null) { closeWorkspace(true); return; }
        if (closing || finished || !attached || mounted || !surface.getHolder().getSurface().isValid()) {
            pack.release();
            return;
        }
        // SurfaceView takes ownership of this parcelled SurfacePackage.
        try { surface.setChildSurfacePackage(pack); }
        catch (RuntimeException error) {
            Log.w("FlymeOneStepActivity", "Cannot embed workspace surface", error);
            pack.release();
            closeWorkspace(true);
            return;
        }
        mounted = true;
        handler.removeCallbacks(timeout);
        send(OneStepActivityProtocol.MOUNTED, data -> {});
        if (imeAnimations.isEmpty()) sendInsets(surface.getRootWindowInsets());
        else sendImeInsets(imeBottom);
    }

    private void sendInsets(WindowInsets insets) {
        if (insets == null) return;
        // Visibility can already be false during the hide animation; its inset still moves.
        sendImeInsets(insets.getInsets(WindowInsets.Type.ime()).bottom);
    }

    private void sendImeInsets(int bottom) {
        imeBottom = bottom;
        if (!mounted || closing || finished) return;
        boolean animating = !imeAnimations.isEmpty();
        if (sentImeBottom == bottom && sentImeAnimating == animating) return;
        sentImeBottom = bottom;
        sentImeAnimating = animating;
        send(OneStepActivityProtocol.INSETS, data -> {
            data.writeInt(bottom);
            data.writeBoolean(animating);
        });
    }

    private void send(int code, OneStepActivityProtocol.Writer writer) {
        try {
            if (!OneStepActivityProtocol.send(control, OneStepActivityProtocol.CONTROL, code, data -> {
                data.writeStrongBinder(callback);
                writer.write(data);
            })) finishHost();
        } catch (RemoteException | RuntimeException error) { finishHost(); }
    }

    private void closeWorkspace(boolean focusMain) {
        if (closing || finished) return;
        closing = true;
        handler.removeCallbacks(timeout);
        send(OneStepActivityProtocol.CLOSE, data -> data.writeBoolean(focusMain));
        // Normally SystemUI replies after restoring every borrowed task. A dead/unresponsive
        // owner must not leave a permanent blank Activity in front of the user's apps.
        if (!finished) handler.postDelayed(forceFinish, 5000);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (!finished) closeWorkspace(false);
    }

    @Override protected void onStop() {
        super.onStop();
        if (!finished) closeWorkspace(false);
    }

    @Override protected void onDestroy() {
        if (!finished) closeWorkspace(false);
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(forceFinish);
        if (control != null && death != null) control.unlinkToDeath(death, 0);
        if (surface != null) surface.getHolder().removeCallback(this);
        super.onDestroy();
    }

    private void finishHost() {
        if (finished) return;
        finished = true;
        // Leave queued SurfacePackage callbacks to run: mount() releases late parcels.
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(forceFinish);
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }
}
