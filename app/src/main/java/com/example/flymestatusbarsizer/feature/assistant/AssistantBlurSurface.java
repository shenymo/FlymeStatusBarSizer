package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Rect;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.reflect.Method;

/** A separate compositor effect below the window AND its embedded SurfaceView cards. */
final class AssistantBlurSurface {
    static final String NAME = "FlymeStatusBarSizer:AssistantBlur";
    private final SurfaceControl surface;
    private final SurfaceControl parent;
    private final Method setCrop;
    private final Rect lastBounds = new Rect();
    private float lastAlpha = -1;
    private boolean released;

    static AssistantBlurSurface create(View content, int radius) throws ReflectiveOperationException {
        Object root = AssistantReflection.call(content, "getViewRootImpl");
        if (root == null) return null;
        SurfaceControl parent = (SurfaceControl) AssistantReflection.call(root, "getSurfaceControl");
        // Attachment can precede the first window relayout. Try again on the next pre-draw.
        if (parent == null || !parent.isValid()) return null;
        return new AssistantBlurSurface(parent, radius);
    }

    AssistantBlurSurface(SurfaceControl parent, int radius) throws ReflectiveOperationException {
        this.parent = parent;
        setCrop = AssistantReflection.method(SurfaceControl.Transaction.class,
                "setWindowCrop", SurfaceControl.class, Rect.class);
        Method setRelativeLayer = AssistantReflection.method(SurfaceControl.Transaction.class,
                "setRelativeLayer", SurfaceControl.class, SurfaceControl.class, int.class);
        Method setBlurRadius = AssistantReflection.method(SurfaceControl.Transaction.class,
                "setBackgroundBlurRadius", SurfaceControl.class, int.class);
        SurfaceControl.Builder builder = new SurfaceControl.Builder().setName(NAME)
                .setParent(parent).setHidden(true).setOpaque(false);
        AssistantReflection.call(builder, "setEffectLayer");
        surface = builder.build();
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            // SurfaceView.updateRelativeZ uses this same window surface as its anchor (-2).
            // Unlike Drawable Z adjustment, this orders an actual, separate compositor layer.
            setRelativeLayer.invoke(transaction, surface, parent, -3);
            setBlurRadius.invoke(transaction, surface, radius);
            transaction.setAlpha(surface, 0).setVisibility(surface, false).apply();
        } catch (ReflectiveOperationException | RuntimeException error) {
            release();
            throw error;
        }
    }

    boolean isValid() { return !released && parent.isValid() && surface.isValid(); }

    void update(Rect bounds, float alpha, AttachedSurfaceControl root) throws ReflectiveOperationException {
        if (!isValid()) return;
        if (lastBounds.equals(bounds) && lastAlpha == alpha) return;
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        boolean queued = false;
        try {
            boolean visible = alpha > 0 && !bounds.isEmpty();
            if (visible) {
                transaction.setPosition(surface, bounds.left, bounds.top);
                setCrop.invoke(transaction, surface, new Rect(0, 0, bounds.width(), bounds.height()));
            }
            transaction.setAlpha(surface, alpha).setVisibility(surface, visible);
            // Keep the blur's crop/position on the same rendered frame as the sliding cards.
            queued = root != null && root.applyTransactionOnDraw(transaction);
            if (!queued) transaction.apply();
            lastBounds.set(bounds);
            lastAlpha = alpha;
        } finally {
            // A queued transaction is retained/consumed by ViewRootImpl's render callback.
            if (!queued) transaction.close();
        }
    }

    void release() {
        if (released) return;
        released = true;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            if (surface.isValid()) {
                // Detach immediately: a pending draw may never run after window removal.
                transaction.setVisibility(surface, false).reparent(surface, null).apply();
            }
        } finally {
            surface.release();
        }
    }
}
