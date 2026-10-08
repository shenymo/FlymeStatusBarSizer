package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.function.Consumer;

/** A holder-owned output surface. PixelCopy is used only for readiness and transition snapshots. */
final class OneStepSurfaceView extends SurfaceView implements SurfaceHolder.Callback2 {
    interface Listener {
        void onSurfaceAvailable(Surface surface);
        void onSurfaceDestroyed();
        void onBufferAvailable(long waitMs);
        void onBufferTimeout();
        void onSnapshotFinished(long elapsedMs, int result);
    }

    private static final long BUFFER_TIMEOUT_MS = 5000;
    private static final long PROBE_INTERVAL_MS = 50;
    private final Handler handler;
    private final Listener listener;
    private final ArrayList<Runnable> redrawCompletions = new ArrayList<>();
    private final Runnable probe = this::probeBuffer;
    private boolean disposed;
    private boolean available;
    private boolean waitingForBuffer;
    private boolean bufferReady;
    private int epoch;
    private long waitStarted;

    OneStepSurfaceView(Context context, Handler handler, int width, int height, Listener listener) {
        super(context);
        this.handler = handler;
        this.listener = listener;
        // Keep the surface behind the host so loading covers, snapshots and toolbar stay above it.
        setZOrderOnTop(false);
        if (Build.VERSION.SDK_INT >= 34) setSurfaceLifecycle(SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().setFixedSize(width, height);
        getHolder().addCallback(this);
    }

    boolean isSurfaceAvailable() { return !disposed && available && getHolder().getSurface().isValid(); }

    void awaitBuffer() {
        if (!isSurfaceAvailable()) return;
        epoch++;
        bufferReady = false;
        waitingForBuffer = true;
        waitStarted = SystemClock.uptimeMillis();
        handler.removeCallbacks(probe);
        handler.post(probe);
    }

    void requestSnapshot(Consumer<Bitmap> result) {
        if (!isSurfaceAvailable() || !bufferReady || getWidth() <= 0 || getHeight() <= 0) {
            result.accept(null);
            return;
        }
        int requestEpoch = epoch;
        long started = SystemClock.uptimeMillis();
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(getHolder().getSurface(), bitmap, status -> {
                if (disposed || epoch != requestEpoch) {
                    bitmap.recycle();
                    result.accept(null);
                    return;
                }
                listener.onSnapshotFinished(SystemClock.uptimeMillis() - started, status);
                if (status == PixelCopy.SUCCESS) result.accept(bitmap);
                else { bitmap.recycle(); result.accept(null); }
            }, handler);
        } catch (IllegalArgumentException e) {
            bitmap.recycle();
            listener.onSnapshotFinished(SystemClock.uptimeMillis() - started, PixelCopy.ERROR_SOURCE_INVALID);
            result.accept(null);
        }
    }

    private void probeBuffer() {
        if (!waitingForBuffer || !isSurfaceAvailable()) return;
        int requestEpoch = epoch;
        // Copy one pixel, not the full app image. SUCCESS proves a buffer was queued; it is not
        // a physical presentation timestamp. There is no polling once this surface is ready.
        Bitmap pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(getHolder().getSurface(), new Rect(0, 0, 1, 1), pixel, status -> {
                pixel.recycle();
                if (disposed || epoch != requestEpoch || !waitingForBuffer) return;
                finishProbe(status);
            }, handler);
        } catch (IllegalArgumentException e) {
            pixel.recycle();
            if (!disposed && epoch == requestEpoch) finishProbe(PixelCopy.ERROR_SOURCE_INVALID);
        }
    }

    private void finishProbe(int status) {
        int probeEpoch = epoch;
        long elapsed = SystemClock.uptimeMillis() - waitStarted;
        if (status == PixelCopy.SUCCESS) {
            waitingForBuffer = false;
            bufferReady = true;
            completeRedraws();
            if (!disposed && epoch == probeEpoch) listener.onBufferAvailable(elapsed);
        } else if (elapsed >= BUFFER_TIMEOUT_MS) {
            waitingForBuffer = false;
            completeRedraws();
            if (!disposed && epoch == probeEpoch) listener.onBufferTimeout();
        } else {
            handler.postDelayed(probe, PROBE_INTERVAL_MS);
        }
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        epoch++;
        waitingForBuffer = false;
        handler.removeCallbacks(probe);
        completeRedraws();
        getHolder().removeCallback(this);
        // The holder, not the workspace, owns and releases this Surface.
    }

    private void completeRedraws() {
        if (redrawCompletions.isEmpty()) return;
        ArrayList<Runnable> completions = new ArrayList<>(redrawCompletions);
        redrawCompletions.clear();
        for (Runnable completion : completions) completion.run();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (disposed) return;
        epoch++;
        available = true;
        bufferReady = false;
        listener.onSurfaceAvailable(holder.getSurface());
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        epoch++;
        available = false;
        waitingForBuffer = bufferReady = false;
        handler.removeCallbacks(probe);
        completeRedraws();
        if (!disposed) listener.onSurfaceDestroyed();
    }

    @Override public void surfaceRedrawNeeded(SurfaceHolder holder) { }

    @Override public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable drawingFinished) {
        if (disposed || !waitingForBuffer) drawingFinished.run();
        else redrawCompletions.add(drawingFinished);
    }
}
