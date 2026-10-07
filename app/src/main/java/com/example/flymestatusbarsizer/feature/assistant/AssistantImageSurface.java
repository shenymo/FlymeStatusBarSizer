package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.AttachedSurfaceControl;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.reflect.Constructor;

/** A buffer below the window and SurfaceView cards, with the panel's full (unclipped) image. */
final class AssistantImageSurface {
    private final SurfaceControl surface, parent;
    private final int width, height;
    private boolean released;
    private final Rect lastCrop = new Rect();
    private float lastAlpha = -1;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;

    static AssistantImageSurface create(View content, Bitmap image) throws ReflectiveOperationException {
        Object root = AssistantReflection.call(content, "getViewRootImpl");
        if (root == null || content.getWidth() <= 0 || content.getHeight() <= 0) return null;
        SurfaceControl parent = (SurfaceControl) AssistantReflection.call(root, "getSurfaceControl");
        if (parent == null || !parent.isValid()) return null;
        return new AssistantImageSurface(parent, image, content.getWidth(), content.getHeight());
    }

    private AssistantImageSurface(SurfaceControl parent, Bitmap bitmap, int width, int height)
            throws ReflectiveOperationException {
        this.parent = parent;
        this.width = width;
        this.height = height;
        surface = new SurfaceControl.Builder().setName("FlymeStatusBarSizer:AssistantImage")
                .setParent(parent).setHidden(true).setOpaque(true).setFormat(PixelFormat.RGBA_8888)
                .setBufferSize(width, height).build();
        try {
            Constructor<Surface> constructor = Surface.class.getDeclaredConstructor(SurfaceControl.class);
            constructor.setAccessible(true);
            Surface buffer = constructor.newInstance(surface);
            try {
                Canvas canvas = buffer.lockCanvas(null);
                try {
                    canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(bitmap, null, centerCrop(bitmap.getWidth(), bitmap.getHeight(), width, height),
                            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                } finally { buffer.unlockCanvasAndPost(canvas); }
            } finally { buffer.release(); }
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                AssistantReflection.method(SurfaceControl.Transaction.class, "setRelativeLayer",
                        SurfaceControl.class, SurfaceControl.class, int.class).invoke(transaction, surface, parent, -3);
                transaction.setAlpha(surface, 0).setVisibility(surface, false).apply();
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            release();
            throw error;
        }
    }

    static RectF centerCrop(int imageWidth, int imageHeight, int width, int height) {
        float scale = Math.max(width / (float) imageWidth, height / (float) imageHeight);
        float w = imageWidth * scale, h = imageHeight * scale;
        return new RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
    }

    boolean isValid(View content) {
        return !released && parent.isValid() && surface.isValid()
                && width == content.getWidth() && height == content.getHeight();
    }

    void update(Rect visible, int x, int y, float alpha, AttachedSurfaceControl root)
            throws ReflectiveOperationException {
        if (released || !parent.isValid() || !surface.isValid()) return;
        Rect crop = new Rect(visible);
        crop.offset(-x, -y);
        if (lastCrop.equals(crop) && lastAlpha == alpha && lastX == x && lastY == y) return;
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        boolean queued = false;
        try {
            boolean show = alpha > 0 && !visible.isEmpty();
            if (show) {
                transaction.setPosition(surface, x, y);
                AssistantReflection.method(SurfaceControl.Transaction.class, "setWindowCrop",
                        SurfaceControl.class, Rect.class).invoke(transaction, surface, crop);
            }
            transaction.setAlpha(surface, alpha).setVisibility(surface, show);
            queued = root != null && root.applyTransactionOnDraw(transaction);
            if (!queued) transaction.apply();
            lastCrop.set(crop); lastAlpha = alpha; lastX = x; lastY = y;
        } finally { if (!queued) transaction.close(); }
    }

    void release() {
        if (released) return;
        released = true;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            if (surface.isValid()) transaction.setVisibility(surface, false).reparent(surface, null).apply();
        } finally { surface.release(); }
    }
}
