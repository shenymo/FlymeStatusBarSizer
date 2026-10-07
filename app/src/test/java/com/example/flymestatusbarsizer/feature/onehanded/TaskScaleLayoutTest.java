package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Point;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TaskScaleLayoutTest {
    private final TaskScaleTarget task = new TaskScaleTarget(1, "task", "surface",
            new Rect(10, 80, 1090, 2480), new Point(10, 80), true);
    private final Rect usable = new Rect(10, 160, 1090, 2440);

    @Test public void normalKeyboardTranslatesEntireGroupWithoutChangingScaleOrCardSize() {
        TaskScaleLayout before = new TaskScaleLayout(task, usable, .7f, 0);
        TaskScaleLayout after = new TaskScaleLayout(task, usable, .7f, 500);
        assertEquals(.7f, after.scale, 0f);
        assertEquals(new Rect(334, 300, 1090, 1980), after.content);
        // Compare equal-height groups: only the existing navigation bar clip affects idle cards.
        Rect[] oldCards = before.cards(new Rect(0, 0, 324, 1680), 3, .45f, 2);
        Rect[] newCards = after.cards(new Rect(0, 0, 324, 1680), 3, .45f, 2);
        assertArrayEquals(oldCards, newCards);
        assertEquals(1980, after.available.bottom);
    }

    @Test public void tallKeyboardScalesBothColumnsToFitBelowStatusBar() {
        TaskScaleLayout frame = new TaskScaleLayout(task, usable, .7f, 1100);
        assertEquals(160, frame.content.top);
        assertEquals(1380, frame.content.bottom);
        assertEquals(1220f / 2400f, frame.scale, .001f);
        Rect margin = new Rect(usable.left, frame.content.top, frame.content.left, frame.content.bottom);
        Rect[] cards = frame.cards(margin, 3, .45f, 2);
        assertEquals(3, cards.length);
        for (Rect card : cards) {
            assertTrue(card.left >= 0);
            assertTrue(card.right <= margin.width());
            assertTrue(card.top >= 0);
            assertTrue(card.bottom <= margin.height());
        }
        assertEquals(2, cards[1].top - cards[0].bottom);
        assertEquals(2, cards[2].top - cards[1].bottom);
        assertTrue(frame.cardWidth < 324);
        assertEquals(usable, frame.usable); // Swap cover still covers space when keyboard hides.
    }

    @Test public void hiddenKeyboardReturnsOriginalBottomRightGeometry() {
        TaskScaleLayout frame = new TaskScaleLayout(task, usable, .7f, 0);
        assertEquals(task.scaledBounds(.7f), frame.content);
        assertEquals(0f, frame.offsetY, 0f);
        assertEquals(usable, frame.available);
    }

    @Test public void onlyVisibleDockedImeReservesSpace() {
        Rect display = new Rect(0, 0, 1080, 2400);
        Rect docked = new Rect(0, 1600, 1080, 2400);
        assertEquals(1600, TaskScaleImeInsets.dockedTop(true, docked, display));
        assertEquals(TaskScaleImeInsets.HIDDEN, TaskScaleImeInsets.dockedTop(false, docked, display));
        assertEquals(TaskScaleImeInsets.HIDDEN, TaskScaleImeInsets.dockedTop(true,
                new Rect(100, 1200, 700, 1800), display));
        assertEquals(TaskScaleImeInsets.HIDDEN, TaskScaleImeInsets.dockedTop(true,
                new Rect(0, 2400, 1080, 2400), display));
    }
}
