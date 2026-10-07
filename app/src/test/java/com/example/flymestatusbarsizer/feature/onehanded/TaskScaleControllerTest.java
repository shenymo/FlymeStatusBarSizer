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

    @Test public void lockscreenOrDisabledSettingRestoresAndCancelsPendingResume() {
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

    @Test public void cardSwitchRestoresOldTaskBeforeLaunchAndWaitsForSelectedTaskAndIdle() {
        enter();
        RecentTaskCard card = card(12, "task-B");
        controller.selectTask(card, () -> {
            assertEquals(1f, backend.scale, 0f);
            assertEquals("task-A", backend.lastRestored.token);
            assertTrue(overlay.visible);
            assertTrue(overlay.covered);
            backend.idle = false;
            return true;
        });
        idle(100);
        backend.focused = null; // Focus handoff is not instantaneous.
        idle(100);
        backend.focused = target(12, "task-B", "surface-B");
        idle(100);
        assertEquals(1f, backend.scale, 0f);
        backend.idle = true;
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals("task-B", backend.lastTransformed.token);
        assertTrue(overlay.visible);
        assertEquals(1, successes);
    }

    @Test public void staleOrRejectedCardReturnsToOriginalScaledTask() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> false);
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals("task-A", backend.lastTransformed.token);
        assertTrue(overlay.visible);
    }

    @Test public void failedCardLaunchReturnsToOriginalTask() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> { throw new Exception("task removed"); });
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertTrue(overlay.visible);
    }

    @Test public void selectedTaskIdReuseCannotScaleAnUnexpectedTask() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "recycled-task", "surface-C");
        idle(400);
        assertEquals(1f, backend.scale, 0f);
        assertEquals("task-A", backend.lastTransformed.token);
        assertFalse(overlay.visible);
    }

    @Test public void stopDuringCardSwitchCannotResurrectScaling() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> true);
        controller.stop("screen off");
        backend.focused = target(12, "task-B", "surface-B");
        idle(400);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
    }

    @Test public void cardSwitchTimesOutWithoutScalingLateTask() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> true);
        idle(5300);
        backend.focused = target(12, "task-B", "surface-B");
        idle(400);
        assertEquals(1f, backend.scale, 0f);
        assertFalse(overlay.visible);
    }

    @Test public void repeatedCardClickAndRemovedSelectedTaskDoNotLaunchAgain() {
        enter();
        int[] launches = {0};
        controller.selectTask(card(12, "task-B"), () -> { launches[0]++; return true; });
        controller.selectTask(card(13, "task-C"), () -> { launches[0]++; return true; });
        assertEquals(1, launches[0]);
        controller.taskVanished(12);
        backend.focused = target(12, "task-B", "surface-B");
        idle(400);
        assertFalse(overlay.visible);
        assertEquals(1f, backend.scale, 0f);
    }

    @Test public void swapWaitsForBothCoverAndTaskTransactionsWithoutFullscreenAnimation() {
        enter();
        overlay.autoCover = false;
        backend.autoCommit = false;
        int[] launches = {0};
        controller.selectTask(card(12, "task-B"), () -> { launches[0]++; return true; });
        idle(200);
        assertEquals(0, launches[0]);
        assertEquals(.7f, backend.scale, .001f);
        assertNull(backend.lastRestored);
        overlay.coverCommit.run();
        assertEquals(1, launches[0]);
        assertEquals("task-A", backend.lastRestored.token);
        backend.scales.clear();
        backend.focused = target(12, "task-B", "surface-B");
        idle(300);
        assertEquals(java.util.Collections.singletonList(.7f), backend.scales);
        assertTrue(overlay.covered);
        assertEquals(0, overlay.swapReveals);
        backend.commit.run();
        assertTrue(overlay.visible);
        assertFalse(overlay.covered);
        assertEquals(1, overlay.swapReveals);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void missingCoverCommitCancelsSwapWithoutLaunchingOrLeavingScaledMode() {
        enter();
        overlay.autoCover = false;
        int[] launches = {0};
        controller.selectTask(card(12, "task-B"), () -> { launches[0]++; return true; });
        idle(5300);
        overlay.coverCommit.run();
        assertEquals(0, launches[0]);
        assertTrue(overlay.visible);
        assertFalse(overlay.covered);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void launchClosingSystemDialogsDoesNotExitSwapOrScaledMode() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> {
            controller.systemDialogsClosed();
            return true;
        });
        idle(100);
        assertTrue(overlay.covered);
        backend.focused = target(12, "task-B", "surface-B");
        idle(400);
        controller.systemDialogsClosed();
        idle(300);
        assertTrue(overlay.visible);
        assertEquals(.7f, backend.scale, .001f);
        // A real focus change still exits even without trusting the dialog broadcast.
        backend.focused = target(13, "task-C", "surface-C");
        controller.systemDialogsClosed();
        idle(200);
        assertFalse(overlay.visible);
    }

    @Test public void transientSelectedVisibilityDuringTransitionDoesNotExitSwap() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.idle = false;
        backend.focused = new TaskScaleTarget(12, "task-B", "surface-B",
                new Rect(0, 0, 1080, 2400), new Point(), false);
        idle(200);
        assertTrue(overlay.covered);
        backend.focused = target(12, "task-B", "surface-B");
        backend.idle = true;
        idle(300);
        assertTrue(overlay.visible);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void oldTaskDisappearingDuringSwapDoesNotCancelSelectedTask() {
        enter();
        controller.selectTask(card(12, "task-B"), () -> true);
        controller.taskVanished(11);
        backend.focused = target(12, "task-B", "surface-B");
        idle(400);
        assertTrue(overlay.visible);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void transitionBeforeRevealInvalidatesOldCommitAndKeepsCover() {
        enter();
        backend.autoCommit = false;
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        idle(100);
        Runnable previousCommit = backend.commit;
        backend.idle = false;
        controller.suspend();
        previousCommit.run();
        assertTrue(overlay.covered);
        assertEquals(0, overlay.swapReveals);
        backend.idle = true;
        idle(100);
        backend.commit.run();
        assertEquals(1, overlay.swapReveals);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void exitBeforeCommitCannotRevealOrResurrectSwap() {
        enter();
        backend.autoCommit = false;
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        idle(100);
        controller.stop("screen off");
        backend.commit.run();
        idle(300);
        assertFalse(overlay.visible);
        assertEquals(0, overlay.swapReveals);
        assertEquals(1f, backend.scale, .001f);
    }

    @Test public void fastTaskLaunchStillWaitsForThePreviewExchangeAnimation() {
        enter();
        overlay.animationComplete = false;
        backend.autoCommit = false;
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        idle(200);
        assertTrue(overlay.covered);
        assertNull(backend.commit);
        assertEquals(0, overlay.swapReveals);
        overlay.animationComplete = true;
        idle(100);
        assertNotNull(backend.commit);
        assertTrue(overlay.covered);
        backend.commit.run();
        assertEquals(1, overlay.swapReveals);
        assertEquals(.7f, backend.scale, .001f);
    }

    @Test public void interruptedPreviewAnimationCannotLaterRevealTheTask() {
        enter();
        overlay.animationComplete = false;
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        idle(100);
        controller.stop("screen off during animation");
        overlay.animationComplete = true;
        idle(400);
        assertFalse(overlay.visible);
        assertEquals(0, overlay.swapReveals);
        assertEquals(1f, backend.scale, .001f);
    }

    @Test public void imeMovesTaskAndCardsTogetherAndRestoresWithoutExiting() {
        enter();
        controller.imeChanged(1800);
        idle(96);
        assertTrue(backend.layout.offsetY < 0f);
        assertTrue(backend.layout.offsetY > -600f);
        assertEquals(backend.layout.content, overlay.layout.content);
        idle(300);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals(-600f, backend.layout.offsetY, .001f);
        assertEquals(new Rect(324, 120, 1080, 1800), backend.layout.content);
        assertTrue(overlay.visible);
        assertFalse(overlay.animating);
        assertNull(backend.lastRestored);
        controller.imeChanged(TaskScaleImeInsets.HIDDEN);
        idle(400);
        assertEquals(new Rect(324, 720, 1080, 2400), backend.layout.content);
        assertEquals(1, successes);
        assertTrue(overlay.visible);
    }

    @Test public void tallKeyboardFitsMainAboveItAndRapidHideCancelsOldMovement() {
        enter();
        controller.imeChanged(1400);
        idle(400);
        assertEquals(1400f / 2400f, backend.scale, .001f);
        assertEquals(0, backend.layout.content.top);
        assertEquals(1400, backend.layout.content.bottom);
        controller.imeChanged(1200);
        idle(64);
        controller.imeChanged(TaskScaleImeInsets.HIDDEN);
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals(0f, backend.layout.offsetY, .001f);
        assertTrue(overlay.visible);
    }

    @Test public void enteringWithKeyboardAlreadyVisibleStartsAboveKeyboard() {
        controller.imeChanged(1500);
        enter();
        assertEquals(1500, backend.layout.content.bottom);
        assertEquals(0, backend.layout.content.top);
        assertEquals(1500f / 2400f, backend.scale, .001f);
        assertEquals(1, successes);
    }

    @Test public void navigationStripFromFloatingImeDoesNotMoveLayout() {
        backend.usable = new Rect(0, 80, 1080, 2340);
        enter();
        controller.imeChanged(2340);
        idle(400);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals(0f, backend.layout.offsetY, 0f);
        assertEquals(new Rect(324, 720, 1080, 2400), backend.layout.content);
    }

    @Test public void stoppingDuringImeMovementDoesNotResurrectTaskOrMargins() {
        enter();
        controller.imeChanged(1600);
        idle(64);
        controller.stop("screen off");
        int writes = backend.scales.size();
        idle(400);
        assertEquals(writes, backend.scales.size());
        assertEquals(1f, backend.scale, .001f);
        assertFalse(overlay.visible);
    }

    @Test public void keyboardHidingDuringSwapMovesPreviewAndWaitsBeforeReveal() {
        enter();
        controller.imeChanged(1600);
        idle(400);
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        controller.imeChanged(TaskScaleImeInsets.HIDDEN);
        idle(96);
        assertTrue(overlay.covered);
        assertTrue(overlay.swapMoves > 0);
        assertNull(backend.commit);
        idle(400);
        assertFalse(overlay.covered);
        assertEquals(.7f, backend.scale, .001f);
        assertEquals(0f, backend.layout.offsetY, .001f);
        assertEquals(backend.layout.content, overlay.layout.content);
    }

    @Test public void imeChangeInvalidatesTaskCommitBeforeSwapReveal() {
        enter();
        backend.autoCommit = false;
        controller.selectTask(card(12, "task-B"), () -> true);
        backend.focused = target(12, "task-B", "surface-B");
        idle(100);
        Runnable originalCommit = backend.commit;
        controller.imeChanged(1800);
        originalCommit.run();
        assertEquals(0, overlay.swapReveals);
        idle(400);
        assertNotSame(originalCommit, backend.commit);
        assertEquals(1800, backend.layout.content.bottom);
        backend.commit.run();
        assertEquals(1, overlay.swapReveals);
        assertEquals(backend.layout.content, overlay.layout.content);
    }

    @Test public void imeChangeWhileSuspendedResumesAtLatestKeyboardHeight() {
        enter();
        backend.idle = false;
        controller.suspend();
        int writes = backend.scales.size();
        controller.imeChanged(1700);
        idle(300);
        assertEquals(writes, backend.scales.size());
        backend.idle = true;
        idle(400);
        assertEquals(1700, backend.layout.content.bottom);
        assertTrue(overlay.visible);
    }

    @Test public void missingSwapCoverCommitStillRestoresLatestImePosition() {
        enter();
        overlay.autoCover = false;
        controller.selectTask(card(12, "task-B"), () -> { fail("must not launch"); return true; });
        controller.imeChanged(1600);
        idle(5300);
        assertFalse(overlay.covered);
        assertTrue(overlay.visible);
        assertEquals(1600, backend.layout.content.bottom);
        assertEquals(backend.layout.content, overlay.layout.content);
    }

    private RecentTaskCard card(int id, String token) {
        return new RecentTaskCard(id, 0, token, "App " + id, null);
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
        boolean autoCommit = true;
        Runnable commit;
        int restoreFailures;
        float scale = 1f;
        TaskScaleLayout layout;
        Rect usable;
        final List<Float> scales = new ArrayList<>();
        @Override public TaskScaleTarget focusedTask() { return focused; }
        @Override public boolean allowed() { return allowed; }
        @Override public boolean idle() { return idle; }
        @Override public Rect usableBounds(TaskScaleTarget target) {
            return usable != null ? new Rect(usable) : new Rect(target.bounds);
        }
        @Override public boolean sameSurface(TaskScaleTarget a, TaskScaleTarget b) { return a.surface.equals(b.surface); }
        @Override public void transform(TaskScaleTarget target, TaskScaleLayout frame) throws Exception {
            lastTransformed = target;
            layout = frame;
            scale = frame.scale;
            scales.add(frame.scale);
            if (failTransform) throw new Exception("transaction failure");
        }
        @Override public void transformAndCommit(TaskScaleTarget target, TaskScaleLayout frame, Runnable committed) throws Exception {
            transform(target, frame);
            commit = committed;
            if (autoCommit) committed.run();
        }
        @Override public void restore(TaskScaleTarget target) throws Exception {
            if (restoreFailures-- > 0) throw new Exception("restore failure");
            lastRestored = target;
            scale = 1f;
        }
    }

    private static final class FakeOverlay implements TaskScaleController.Overlay {
        boolean visible, animating, fail, covered;
        boolean autoCover = true;
        boolean animationComplete = true;
        int swapReveals;
        Runnable coverCommit;
        TaskScaleLayout layout;
        int swapMoves;
        @Override public void show(TaskScaleTarget target, TaskScaleLayout frame, boolean animate) throws Exception {
            if (fail) throw new Exception("window rejected");
            visible = true;
            animating = animate;
            layout = frame;
        }
        @Override public void beginSwap(TaskScaleTarget current, RecentTaskCard selected, Runnable onCovered) throws Exception {
            if (fail) throw new Exception("window rejected");
            visible = true;
            covered = true;
            coverCommit = onCovered;
            if (autoCover) onCovered.run();
        }
        @Override public void endSwap(TaskScaleTarget current, TaskScaleLayout frame) throws Exception {
            covered = false;
            swapReveals++;
            show(current, frame, false);
        }
        @Override public void moveSwap(TaskScaleTarget current, TaskScaleLayout frame) {
            layout = frame;
            swapMoves++;
        }
        @Override public boolean swapAnimationFinished() { return animationComplete; }
        @Override public void hide() { visible = false; covered = false; }
    }
}
