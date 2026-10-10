package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PowerManager;
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
    private WallpaperManager wallpaper;
    private boolean watchingWallpaper;
    private boolean darkBarIcons;
    private final WallpaperManager.OnColorsChangedListener wallpaperColors = (colors, which) -> {
        if (!this.finished && (which & WallpaperManager.FLAG_SYSTEM) != 0) updateBarColors(colors);
    };
    private boolean attached;
    private boolean mounted;
    private boolean remountPending;
    private boolean closing;
    private boolean finished;
    private boolean started;
    private int surfaceWidth;
    private int surfaceHeight;
    private IBinder lastHostToken;
    private Rect lastStableInsets;
    private int surfaceRequest;
    private final HashSet<WindowInsetsAnimation> imeAnimations = new HashSet<>();
    private int imeBottom;
    private int sentImeBottom = -1;
    private boolean sentImeAnimating;
    private final Runnable timeout = this::onAttachmentTimeout;
    private final Runnable forceFinish = this::finishHost;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Bundle extras = getIntent().getExtras();
        control = extras == null ? null : extras.getBinder(OneStepActivityProtocol.EXTRA_CONTROL);
        int serverUid = extras == null ? -1 : extras.getInt(OneStepActivityProtocol.EXTRA_UID, -1);
        // A recreation may reconnect only to the still-live session capability.
        if (Build.VERSION.SDK_INT < 33 || control == null || !control.isBinderAlive() || serverUid < 0) {
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
                    int request = data.readInt();
                    SurfaceControlViewHost.SurfacePackage pack = data.readTypedObject(
                            SurfaceControlViewHost.SurfacePackage.CREATOR);
                    handler.post(() -> mount(pack, request));
                } else handler.post(OneStepActivity.this::finishHost);
                return true;
            }
        };
        death = () -> handler.post(this::finishHost);
        try { control.linkToDeath(death, 0); }
        catch (RemoteException error) { finishHost(); return; }

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.preferredRefreshRate = 120f;
        params.windowAnimations = 0;
        getWindow().setAttributes(params);
        FrameLayout root = new FrameLayout(this);
        // SystemUI's workspace root animates wallpaper dimming with the task surfaces.
        root.setBackgroundColor(Color.TRANSPARENT);
        surface = new SurfaceView(this);
        // The embedded window and its TaskView children receive input above the Activity's
        // own window. The Activity stays opaque in the task visibility calculation.
        surface.setZOrderOnTop(true);
        surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 34)
            surface.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            // Layout receives the final insets before the first animation frame.
            // Forward only animated insets until the IME has finished moving.
            if (imeAnimations.isEmpty()) sendInsets(insets);
            surface.post(this::updateSurfaceLayout);
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
            bars.show(WindowInsets.Type.systemBars());
        }
        watchWallpaperColors();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { });
        handler.postDelayed(timeout, 12000);
    }

    private void watchWallpaperColors() {
        updateBarColors(null);
        try {
            wallpaper = getSystemService(WallpaperManager.class);
            if (wallpaper == null) return;
            wallpaper.addOnColorsChangedListener(wallpaperColors, handler);
            watchingWallpaper = true;
            updateBarColors(wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM));
        } catch (RuntimeException error) {
            Log.w("FlymeOneStepActivity", "Cannot read wallpaper colors", error);
        }
    }

    private void updateBarColors(WallpaperColors colors) {
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars == null) return;
        boolean darkText = colors != null
                && (colors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
        // Account for the 8% dim layer before choosing dark icons on a bright wallpaper.
        if (darkText) {
            int color = colors.getPrimaryColor().toArgb();
            darkText = Color.luminance(Color.rgb(Math.round(Color.red(color) * 0.92f),
                    Math.round(Color.green(color) * 0.92f), Math.round(Color.blue(color) * 0.92f))) > 0.35f;
        }
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        bars.setSystemBarsAppearance(darkText ? mask : 0, mask);
        darkBarIcons = darkText;
        sendBarColors();
    }

    private void sendBarColors() {
        if (mounted && !closing && !finished)
            send(OneStepActivityProtocol.BAR_COLORS, data -> data.writeBoolean(darkBarIcons));
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {}

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (closing || finished || width <= 0 || height <= 0) return;
        IBinder hostToken = surface.getHostToken();
        WindowInsets insets = surface.getRootWindowInsets();
        if (hostToken == null || insets == null) {
            surface.postOnAnimation(this::updateSurfaceLayout);
            return;
        }
        Insets stable = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect stableRect = new Rect(stable.left, stable.top, stable.right, stable.bottom);
        if (attached && width == surfaceWidth && height == surfaceHeight
                && hostToken.equals(lastHostToken) && stableRect.equals(lastStableInsets)) {
            if (!mounted && !remountPending) {
                remountPending = true;
                scheduleAttachmentTimeout();
                int request = ++surfaceRequest;
                send(OneStepActivityProtocol.REMOUNT, data -> {
                    data.writeStrongBinder(hostToken);
                    data.writeInt(request);
                });
            }
            return;
        }
        attached = true;
        mounted = false;
        remountPending = true;
        scheduleAttachmentTimeout();
        surfaceWidth = width;
        surfaceHeight = height;
        lastHostToken = hostToken;
        lastStableInsets = stableRect;
        int request = ++surfaceRequest;
        send(OneStepActivityProtocol.ATTACH, data -> {
            data.writeStrongBinder(hostToken);
            data.writeInt(getTaskId());
            data.writeInt(width);
            data.writeInt(height);
            data.writeTypedObject(stableRect, 0);
            data.writeInt(request);
        });
    }

    private void updateSurfaceLayout() {
        if (surface != null && surface.getHolder().getSurface().isValid())
            surfaceChanged(surface.getHolder(), PixelFormat.TRANSLUCENT, surface.getWidth(), surface.getHeight());
    }

    private void scheduleAttachmentTimeout() {
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 12000);
    }

    private void onAttachmentTimeout() {
        if (closing || finished || mounted) return;
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (power != null && !power.isInteractive() || keyguard != null && keyguard.isKeyguardLocked()) {
            handler.postDelayed(timeout, 700);
            return;
        }
        closeWorkspace(false);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        surface.requestApplyInsets();
        surface.post(this::updateSurfaceLayout);
        sendBarColors();
    }

    private void mount(SurfaceControlViewHost.SurfacePackage pack, int request) {
        if (request != surfaceRequest) {
            if (pack != null) pack.release();
            return;
        }
        remountPending = false;
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
        send(OneStepActivityProtocol.MOUNTED, data -> {
            data.writeInt(request);
            data.writeBoolean(started);
        });
        send(OneStepActivityProtocol.VISIBILITY, data -> data.writeBoolean(started));
        sendBarColors();
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
        mounted = false;
        remountPending = false;
        if (!closing && !finished) send(OneStepActivityProtocol.VISIBILITY, data -> data.writeBoolean(false));
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (attached && !closing && !finished)
            send(OneStepActivityProtocol.VISIBILITY, data -> data.writeBoolean(true));
    }

    @Override protected void onStop() {
        started = false;
        super.onStop();
        if (!closing && !finished)
            send(OneStepActivityProtocol.VISIBILITY, data -> data.writeBoolean(false));
    }

    @Override protected void onDestroy() {
        if (!finished && !closing) send(OneStepActivityProtocol.DETACH,
                data -> data.writeBoolean(!isChangingConfigurations()));
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(forceFinish);
        if (control != null && death != null) control.unlinkToDeath(death, 0);
        if (surface != null) surface.getHolder().removeCallback(this);
        if (watchingWallpaper) {
            try { wallpaper.removeOnColorsChangedListener(wallpaperColors); }
            catch (RuntimeException error) { Log.w("FlymeOneStepActivity", "Cannot remove wallpaper listener", error); }
            watchingWallpaper = false;
        }
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
