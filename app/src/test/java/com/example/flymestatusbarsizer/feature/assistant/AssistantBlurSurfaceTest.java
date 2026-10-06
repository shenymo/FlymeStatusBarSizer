package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Rect;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.SurfaceView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, manifest = Config.NONE, shadows = {
        AssistantBlurSurfaceTest.BuilderShadow.class, AssistantBlurSurfaceTest.TransactionShadow.class})
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantBlurSurfaceTest {
    static final Map<SurfaceControl, State> states = new IdentityHashMap<>();

    @Before public void reset() { states.clear(); }

    @Test public void blurIsOnASeparateEffectSurfaceBelowTheActualCardLayer() throws Exception {
        SurfaceControl window = windowSurface();
        SurfaceView card = new SurfaceView(RuntimeEnvironment.getApplication());
        card.setZOrderOnTop(false);
        card.setZOrderMediaOverlay(false);
        int cardLayer = (Integer) AssistantReflection.get(card, "mSubLayer");
        AssistantBlurSurface blur = new AssistantBlurSurface(window, 120);
        try {
            State effect = state(blur);
            assertNotSame(window, effect.surface);
            assertSame(window, effect.parent);
            assertSame(window, effect.relativeTo);
            assertTrue(effect.layer < cardLayer);
            int mask = (Integer) AssistantReflection.field(SurfaceControl.class, "FX_SURFACE_MASK").get(null);
            int type = (Integer) AssistantReflection.field(SurfaceControl.class, "FX_SURFACE_EFFECT").get(null);
            assertEquals(type, effect.flags & mask);
            assertEquals(120, effect.radius);
            assertEquals("The containing window must not blur its child cards", 0, state(window).radius);
            assertFalse(effect.visible);
            assertEquals(cardLayer, AssistantReflection.get(card, "mSubLayer"));
        } finally {
            blur.release();
            window.release();
        }
    }

    @Test public void drawingUsesClippedWindowCoordinatesAndHidesBeforeDetach() throws Exception {
        SurfaceControl window = windowSurface();
        AssistantBlurSurface blur = new AssistantBlurSurface(window, 40);
        State effect = state(blur);
        try {
            RecordingRoot root = new RecordingRoot();
            blur.update(new Rect(60, 20, 200, 320), 0.4f, root);
            assertEquals(1, root.queued);
            assertEquals(60f, effect.x, 0);
            assertEquals(20f, effect.y, 0);
            assertEquals(new Rect(0, 0, 140, 300), effect.crop);
            assertEquals(0.4f, effect.alpha, 0);
            assertTrue(effect.visible);
            blur.update(new Rect(60, 20, 200, 320), 0.4f, root);
            assertEquals("Unchanged frames do not enqueue transactions", 1, root.queued);

            blur.update(new Rect(), 0, root);
            assertFalse(effect.visible);
            // Closing must detach even if the queued drawing callbacks never execute.
            blur.release();
            assertNull(effect.parent);
            assertFalse(effect.visible);
            assertFalse(blur.isValid());
            blur.release();
            blur.update(new Rect(0, 0, 200, 300), 1, root);
            assertFalse(effect.visible);
            assertEquals(2, root.queued);
        } finally {
            blur.release();
            window.release();
        }
    }

    @Test public void rejectedFrameTransactionFallsBackToImmediateApply() throws Exception {
        SurfaceControl window = windowSurface();
        AssistantBlurSurface blur = new AssistantBlurSurface(window, 40);
        RecordingRoot root = new RecordingRoot();
        root.accept = false;
        try {
            blur.update(new Rect(0, 0, 100, 200), 1, root);
            TransactionShadow transaction = Shadow.extract(root.last);
            assertTrue(transaction.applied);
            assertTrue(transaction.closed);
        } finally {
            blur.release();
            window.release();
        }
    }

    static SurfaceControl windowSurface() {
        return new SurfaceControl.Builder().setName("Test assistant window")
                .setBufferSize(400, 600).build();
    }

    static State state(AssistantBlurSurface blur) throws ReflectiveOperationException {
        return state((SurfaceControl) AssistantReflection.get(blur, "surface"));
    }

    static State state(SurfaceControl surface) {
        return states.computeIfAbsent(surface, State::new);
    }

    static final class State {
        final SurfaceControl surface;
        SurfaceControl parent, relativeTo;
        int flags, layer, radius;
        float x, y, alpha;
        Rect crop;
        boolean visible;
        State(SurfaceControl surface) { this.surface = surface; }
    }

    static final class RecordingRoot implements AttachedSurfaceControl {
        int queued;
        boolean accept = true;
        SurfaceControl.Transaction last;
        @Override public boolean applyTransactionOnDraw(SurfaceControl.Transaction transaction) {
            queued++;
            last = transaction;
            return accept;
        }
        @Override public SurfaceControl.Transaction buildReparentTransaction(SurfaceControl child) {
            throw new UnsupportedOperationException();
        }
    }

    // Record actual framework builder/transaction calls. There is no fake Drawable Z API:
    // the tests require production to create and order a separate SurfaceControl.
    @Implements(SurfaceControl.Builder.class)
    public static class BuilderShadow {
        @RealObject SurfaceControl.Builder builder;
        @Implementation protected SurfaceControl build() throws ReflectiveOperationException {
            SurfaceControl surface = Shadow.directlyOn(builder, SurfaceControl.Builder.class, "build");
            State state = state(surface);
            state.parent = (SurfaceControl) AssistantReflection.get(builder, "mParent");
            state.flags = (Integer) AssistantReflection.get(builder, "mFlags");
            return surface;
        }
    }

    @Implements(SurfaceControl.Transaction.class)
    public static class TransactionShadow {
        @RealObject SurfaceControl.Transaction transaction;
        boolean applied, closed;
        @Implementation protected SurfaceControl.Transaction setRelativeLayer(
                SurfaceControl surface, SurfaceControl anchor, int layer) {
            state(surface).relativeTo = anchor;
            state(surface).layer = layer;
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction setBackgroundBlurRadius(SurfaceControl surface, int radius) {
            state(surface).radius = radius;
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction setAlpha(SurfaceControl surface, float alpha) {
            state(surface).alpha = alpha;
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction setVisibility(SurfaceControl surface, boolean visible) {
            state(surface).visible = visible;
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction setPosition(SurfaceControl surface, float x, float y) {
            state(surface).x = x;
            state(surface).y = y;
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction setWindowCrop(SurfaceControl surface, Rect crop) {
            state(surface).crop = new Rect(crop);
            return transaction;
        }
        @Implementation protected SurfaceControl.Transaction reparent(SurfaceControl surface, SurfaceControl parent) {
            state(surface).parent = parent;
            return transaction;
        }
        @Implementation protected void apply() { applied = true; }
        @Implementation protected void close() { closed = true; }
    }
}
