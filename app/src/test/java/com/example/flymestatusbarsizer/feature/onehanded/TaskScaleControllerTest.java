package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Point;
import android.graphics.Rect;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class TaskScaleControllerTest {
    private final FakeBackend backend = new FakeBackend();
    private final FakeOverlay overlay = new FakeOverlay();
    private TaskScaleController controller;
    private int successes;

    @Before public void setUp() throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        java.lang.reflect.Method scale = ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
        scale.setAccessible(true);
        scale.invoke(null, 1f);
        backend.focused = target(11, "task-A", "surface-A");
        controller = new TaskScaleController(new Handler(Looper.getMainLooper()), backend, overlay);
    }

    @After public void tearDown() { controller.stop("test cleanup"); idle(300); }

    @Test public void scalesWithoutChangingTaskAndRestoresOnSecondGesture() {
        enter();
        assertEquals(.7f, backend.scale, .001f);
        assertEquals(1, successes);
        assertTrue(overlay.visible);
        assertFalse(overlay.animating);
        controller.toggle(() -> successes++);
        idle(400);
        assertEquals("frames=" + backend.scales + ", overlayAnimating=" + overlay.animating,
                1f, backend.scale, .001f);
        assertFalse(overlay.visible);
        assertEquals(1, successes);
    }

    @Test public void waitsForPredictiveBackCancellationAndFinishTransactions() {
        backend.idle = false;
        controller.toggle(() -> successes++);
        idle(300);
        assertTrue(backend.scales.isEmpty());
        assertFalse(overlay.visible);
        backend.idle = true;
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void neverScalesAnAppThatReplacesTheRequestedTaskWhileWaiting() {
        backend.idle = false;
        controller.toggle(() -> successes++);
        idle(100);
        backend.focused = target(12, "task-B", "surface-B");
        backend.idle = true;
        idle(400);
        assertTrue(backend.scales.isEmpty());
        assertEquals(0, successes);
    }

    @Test public void transitionPausesUntilActuallyIdleAndReacquiresSurface() {
        enter();
        backend.idle = false;
        controller.suspend();
        idle(100);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
        backend.focused = target(11, "task-A", "replacement-surface");
        // Simulate the early onTransitionFinished callback while its transaction is pending.
        controller.refresh();
        idle(100);
        assertEquals(1f, backend.scale, 0f);
        backend.idle = true;
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals("replacement-surface", backend.lastTransformed.surface);
        assertEquals(1, successes);
    }

    @Test public void reusedTaskIdDoesNotInheritScale() {
        enter();
        backend.focused = target(11, "different-token", "surface-B");
        controller.refresh();
        idle(400);
        assertFalse(overlay.visible);
        assertEquals("task-A", backend.lastRestored.token);
        assertEquals(1f, backend.scale, 0f);
    }

    @Test public void imeLockscreenOrDisabledSettingRestoresAndCancelsPendingResume() {
        enter();
        backend.idle = false;
        controller.suspend();
        backend.allowed = false;
        controller.refresh();
        idle(100);
        backend.idle = true;
        backend.allowed = true;
        idle(400);
        assertFalse(overlay.visible);
        assertEquals(1f, backend.scale, 0f);
    }

    @Test public void stuckTransitionHasBoundedWait() {
        backend.idle = false;
        controller.toggle(() -> successes++);
        idle(2700);
        backend.idle = true;
        idle(400);
        assertTrue(backend.scales.isEmpty());
        assertEquals(0, successes);
    }

    @Test public void overlayFailureRestoresAndDoesNotReportSuccess() {
        overlay.fail = true;
        controller.toggle(() -> successes++);
        idle(400);
        assertFalse(overlay.visible);
        assertEquals(1f, backend.scale, 0f);
        assertEquals(0, successes);
    }

    @Test public void partialSurfaceFailureStillRestores() {
        backend.failTransform = true;
        controller.toggle(() -> successes++);
        idle(400);
        assertNotNull(backend.lastRestored);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
    }

    @Test public void restoreFailureRetainsTargetAndRetries() {
        enter();
        backend.restoreFailures = 1;
        controller.stop("screen off");
        assertFalse(controller.canTrigger());
        idle(300);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
        assertTrue(controller.canTrigger());
    }

    @Test public void taskVanishingDuringAnimationCannotResurrectOverlay() {
        controller.toggle(() -> successes++);
        idle(90);
        assertTrue(overlay.animating);
        assertEquals(0, successes);
        controller.taskVanished(11);
        idle(400);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
        assertEquals(0, successes);
    }

    @Test public void bottomRightGeometryRespectsNonZeroOriginAndMargins() {
        TaskScaleTarget target = new TaskScaleTarget(1, "token", "surface",
                new Rect(10, 80, 1010, 2080), new Point(10, 80), true);
        assertEquals(new Rect(310, 680, 1010, 2080), target.scaledBounds(.7f));
        assertEquals(target.bounds, target.scaledBounds(1f));
        Rect outside = new Rect(0, 0, 10, 40);
        TaskScaleOverlay.intersect(outside, new Rect(0, 80, 1080, 2080));
        assertTrue(outside.isEmpty());
    }

    private void enter() { controller.toggle(() -> successes++); idle(400); }
    private void idle(long ms) {
        // Advance vsync as well as Handler time, even when no periodic check is queued (exit).
        for (long remaining = ms; remaining > 0; remaining -= 16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Math.min(16, remaining)));
        }
    }
    private TaskScaleTarget target(int id, String token, String surface) {
        return new TaskScaleTarget(id, token, surface, new Rect(0, 0, 1080, 2400), new Point(), true);
    }

    private static final class FakeBackend implements TaskScaleController.Backend {
        TaskScaleTarget focused, lastTransformed, lastRestored;
        boolean allowed = true, idle = true, failTransform;
        int restoreFailures;
        float scale = 1f;
        final List<Float> scales = new ArrayList<>();
        @Override public TaskScaleTarget focusedTask() { return focused; }
        @Override public boolean allowed() { return allowed; }
        @Override public boolean idle() { return idle; }
        @Override public boolean sameSurface(TaskScaleTarget a, TaskScaleTarget b) { return a.surface.equals(b.surface); }
        @Override public void transform(TaskScaleTarget target, float value) throws Exception {
            lastTransformed = target;
            scale = value;
            scales.add(value);
            if (failTransform) throw new Exception("transaction failure");
        }
        @Override public void restore(TaskScaleTarget target) throws Exception {
            if (restoreFailures-- > 0) throw new Exception("restore failure");
            lastRestored = target;
            scale = 1f;
        }
    }

    private static final class FakeOverlay implements TaskScaleController.Overlay {
        boolean visible, animating, fail;
        @Override public void show(TaskScaleTarget target, float scale, boolean animate) throws Exception {
            if (fail) throw new Exception("window rejected");
            visible = true;
            animating = animate;
        }
        @Override public void hide() { visible = false; }
    }
}
