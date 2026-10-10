package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceControl;

/** Shell-executor-owned surface bridge between a fullscreen task and its TaskView. */
final class OneStepWorkspaceTransition {
    final RecentTaskCard card;
    final int session;
    final SurfaceControl surface;
    final SurfaceControl stage;
    final Point originalPosition;
    final Rect originalBounds;
    final RectF frame;
    float radius;
    boolean exiting;
    boolean returning;
    boolean removing;
    boolean sourcePrepared;

    OneStepWorkspaceTransition(RecentTaskCard card, int session, SurfaceControl surface,
                               SurfaceControl parent, Rect bounds, Point position) throws Exception {
        this.card = card;
        this.session = session;
        this.surface = surface;
        originalBounds = new Rect(bounds);
        originalPosition = new Point(position);
        frame = new RectF(bounds);
        SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setName("OneStep workspace transition").setParent(parent);
        OneStepReflection.call(builder, "setContainerLayer");
        stage = builder.build();
    }

    boolean owns(OneStepShell.Host host) {
        return host != null && host.session == session && host.card != null
                && card.sameTask(host.card);
    }

    void place(SurfaceControl.Transaction tx, SurfaceControl leash, Rect crop, boolean visible)
            throws Exception {
        if (!stage.isValid() || !leash.isValid() || crop.isEmpty() || frame.isEmpty())
            throw new IllegalStateException("Workspace animation surface disappeared");
        float sx = frame.width() / crop.width();
        float sy = frame.height() / crop.height();
        // This layer stays inside the task display area, below system windows. Dropping
        // input also covers the task while it is outside TaskView's obscured region.
        OneStepReflection.call(tx, "setDropInputMode",
                new Class<?>[]{SurfaceControl.class, int.class}, stage, 1);
        tx.setLayer(stage, Integer.MAX_VALUE).setPosition(stage, frame.left, frame.top)
                .setScale(stage, sx, sy).setAlpha(stage, 1f).setVisibility(stage, visible);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class},
                stage, new Rect(0, 0, crop.width(), crop.height()));
        OneStepReflection.call(tx, "setCornerRadius", new Class<?>[]{SurfaceControl.class, float.class},
                stage, radius / Math.max(0.01f, Math.min(sx, sy)));
        tx.reparent(leash, stage).setPosition(leash, -crop.left, -crop.top)
                .setScale(leash, 1f, 1f).setAlpha(leash, 1f).setVisibility(leash, true);
        OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, leash, crop);
    }

    void remove(SurfaceControl.Transaction tx) {
        if (stage.isValid()) tx.reparent(stage, null);
    }

    void release() { stage.release(); }
}
