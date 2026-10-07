package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class TaskSwapAnimationViewTest {
    private TaskSwapAnimationView view;
    private final Rect main = new Rect(324, 640, 1080, 2320);
    private final Rect[] cards = {
            new Rect(40, 660, 280, 1193),
            new Rect(40, 1199, 280, 1732),
            new Rect(40, 1738, 280, 2271)
    };

    @Before public void setUp() throws Exception {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        durationScale(1f);
        view = new TaskSwapAnimationView(RuntimeEnvironment.getApplication(),
                Arrays.asList(card(2), card(3), card(4)), cards, 1, card(1), main);
        view.layout(0, 0, 1080, 2320);
    }

    @After public void tearDown() throws Exception { view.cancel(); durationScale(1f); }

    @Test public void bothPreviewsMoveAndResizeWhileOtherSlotsRemainFixed() {
        assertEquals(new RectF(cards[1]), bounds("incoming"));
        assertEquals(new RectF(main), bounds("outgoing"));
        view.start();
        idle(96);
        assertFalse(view.isFinished());
        assertTrue(bounds("incoming").width() > cards[1].width());
        assertTrue(bounds("incoming").width() < main.width());
        assertTrue(bounds("outgoing").width() < main.width());
        assertTrue(bounds("outgoing").width() > cards[1].width());
        RectF[] staticSlots = (RectF[]) ReflectUtils.getField(view, "cardBounds");
        assertEquals(new RectF(cards[0]), staticSlots[0]);
        assertEquals(new RectF(cards[2]), staticSlots[2]);
        idle(300);
        assertTrue(view.isFinished());
        assertEquals(new RectF(main), bounds("incoming"));
        assertEquals(new RectF(cards[1]), bounds("outgoing"));
    }

    @Test public void cancelledAnimationStopsDrawingNewFramesAndDoesNotReportFinished() {
        view.start();
        idle(80);
        RectF before = new RectF(bounds("incoming"));
        view.cancel();
        idle(400);
        assertFalse(view.isFinished());
        assertEquals(before, bounds("incoming"));
    }

    @Test public void disabledSystemAnimationsReachFinalPositionsWithoutWaiting() throws Exception {
        durationScale(0f);
        view.start();
        idle(32);
        assertTrue(view.isFinished());
        assertEquals(new RectF(main), bounds("incoming"));
        assertEquals(new RectF(cards[1]), bounds("outgoing"));
    }

    @Test public void keyboardMovementRetargetsBothPreviewsWithoutRestartingSwap() {
        view.start();
        idle(96);
        RectF before = new RectF(bounds("incoming"));
        Rect shiftedMain = new Rect(main);
        shiftedMain.offset(0, -400);
        Rect[] shiftedCards = new Rect[cards.length];
        for (int i = 0; i < cards.length; i++) {
            shiftedCards[i] = new Rect(cards[i]);
            shiftedCards[i].offset(0, -400);
        }
        view.setLayout(shiftedCards, shiftedMain);
        assertEquals(before.top - 400, bounds("incoming").top, .001f);
        assertEquals(before.width(), bounds("incoming").width(), .001f);
        idle(210);
        assertTrue(view.isFinished());
        assertEquals(new RectF(shiftedMain), bounds("incoming"));
        assertEquals(new RectF(shiftedCards[1]), bounds("outgoing"));
        // A later IME frame must also update an already completed preview awaiting task commit.
        view.setLayout(cards, main);
        assertTrue(view.isFinished());
        assertEquals(new RectF(main), bounds("incoming"));
    }

    private RectF bounds(String field) { return (RectF) ReflectUtils.getField(view, field); }

    private void durationScale(float value) throws Exception {
        java.lang.reflect.Method method = ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
        method.setAccessible(true);
        method.invoke(null, value);
    }

    private void idle(long ms) {
        for (long remaining = ms; remaining > 0; remaining -= 16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Math.min(16, remaining)));
        }
    }

    private RecentTaskCard card(int id) {
        return new RecentTaskCard(id, 0, "token-" + id, "App " + id, null);
    }
}
