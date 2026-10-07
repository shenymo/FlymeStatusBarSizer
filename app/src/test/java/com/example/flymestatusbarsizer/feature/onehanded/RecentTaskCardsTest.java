package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecentTaskCardsTest {
    private final List<Runnable> work = new ArrayList<>();
    private final List<List<RecentTaskCard>> results = new ArrayList<>();
    private final FakeSource source = new FakeSource();
    private final RecentTaskCardsLoader loader = new RecentTaskCardsLoader(source, work::add,
            new Handler(Looper.getMainLooper()));
    private final TaskScaleTarget foreground = new TaskScaleTarget(1, "token-1", "surface",
            new Rect(0, 0, 1080, 2400), new Point(), true);

    @Test public void loadsOnlyThreeMostRecentBackgroundTasksAndKeepsFullPreviewAspect() {
        source.cards = Arrays.asList(card(1), card(2), card(2), card(3), card(4), card(5));
        loader.request(foreground, 100, 220, results::add);
        assertTrue(source.readIds.isEmpty());
        work.remove(0).run();
        assertTrue(results.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Arrays.asList(2, 3, 4), source.readIds);
        List<RecentTaskCard> cards = results.get(0);
        assertEquals(3, cards.size());
        for (RecentTaskCard card : cards) {
            assertEquals(100, card.preview.getWidth());
            assertEquals(200, card.preview.getHeight());
            assertEquals(Color.BLUE, card.preview.getPixel(50, 100));
        }
        for (Bitmap original : source.originals) assertTrue(original.isRecycled());
    }

    @Test public void cancelledAndSupersededLoadsCannotReturnOldCards() {
        source.cards = Arrays.asList(card(2));
        loader.request(foreground, 100, 200, results::add);
        loader.cancel();
        work.remove(0).run();
        assertTrue(source.readIds.isEmpty());
        loader.request(foreground, 100, 200, results::add);
        work.remove(0).run();
        loader.cancel(); // Cancel after loading, before UI delivery.
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(results.isEmpty());
        loader.request(foreground, 100, 200, results::add);
        loader.request(foreground, 100, 200, results::add);
        work.remove(0).run();
        work.remove(0).run();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, results.size());
    }

    @Test public void unavailableSnapshotKeepsTaskOrderAndDoesNotPreventOtherCards() {
        source.cards = Arrays.asList(card(2), card(3), card(4));
        source.unavailable = 3;
        loader.request(foreground, 100, 200, results::add);
        work.remove(0).run();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3, results.get(0).size());
        assertNull(results.get(0).get(1).preview);
        assertEquals(3, results.get(0).get(1).taskId);
        assertNotNull(results.get(0).get(2).preview);
    }

    @Test public void layoutPreservesAspectAndOnlyUsesNarrowGapsAcrossScreenSizes() {
        for (int width : new int[]{240, 324, 432}) {
            int height = width * 5;
            Rect[] cards = RecentTaskCardLayout.arrange(width, height, 3, .45f, 6);
            assertEquals(3, cards.length);
            for (int i = 0; i < cards.length; i++) {
                assertTrue(new Rect(0, 0, width, height).contains(cards[i]));
                assertEquals(.45f, cards[i].width() / (float) cards[i].height(), .003f);
                if (i > 0) assertEquals(6, cards[i].top - cards[i - 1].bottom);
            }
            Rect[] single = RecentTaskCardLayout.arrange(width, height, 1, .45f, 6);
            assertEquals(cards[0].width(), single[0].width());
            assertEquals(cards[0].height(), single[0].height());
        }
        assertEquals(0, RecentTaskCardLayout.arrange(2, 2, 3, .45f, 6).length);
    }

    @Test public void previewTapSelectsButDragOrSecondPointerDoesNot() {
        int[] selections = {0};
        RecentTaskCardView view = new RecentTaskCardView(RuntimeEnvironment.getApplication(), card(2),
                () -> selections[0]++);
        view.layout(0, 0, 100, 200);
        touch(view, MotionEvent.ACTION_DOWN, 10, 10);
        touch(view, MotionEvent.ACTION_UP, 10, 10);
        assertEquals(1, selections[0]);
        touch(view, MotionEvent.ACTION_DOWN, 10, 10);
        touch(view, MotionEvent.ACTION_MOVE, 90, 190);
        touch(view, MotionEvent.ACTION_UP, 90, 190);
        touch(view, MotionEvent.ACTION_DOWN, 10, 10);
        touch(view, MotionEvent.ACTION_POINTER_DOWN, 10, 10);
        touch(view, MotionEvent.ACTION_UP, 10, 10);
        assertEquals(1, selections[0]);
        assertEquals("切换到 App 2", view.getContentDescription());
    }

    @Test public void recentTaskFilterExcludesOtherUsersDisplaysAndUnsupportedWindows() {
        Info info = new Info();
        assertNotNull(TaskScaleRecentTasks.candidate(info, 0));
        info.userId = 10;
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.userId = 0;
        info.displayId = 1;
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.displayId = 0;
        info.parentTaskId = 42;
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.parentTaskId = -1;
        info.configuration.windowConfiguration.mode = 5;
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.configuration.windowConfiguration.mode = 1;
        info.configuration.windowConfiguration.type = 2;
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.configuration.windowConfiguration.type = 1;
        info.configuration.windowConfiguration.bounds.set(0, 0, 2400, 1080);
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
        info.configuration.windowConfiguration.bounds.set(0, 0, 1080, 2400);
        info.baseIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        assertNull(TaskScaleRecentTasks.candidate(info, 0));
    }

    @Test public void selectedSlotReceivesOldMainAndOtherSlotsStayPutAfterMruRefresh() {
        RecentTaskCardSlots slots = new RecentTaskCardSlots();
        slots.update(Arrays.asList(card(2), card(3), card(4)));
        slots.rememberMain(card(1));
        slots.begin(foreground, card(3));
        assertEquals(Arrays.asList(2, 1, 4), ids(slots.cards()));
        slots.end(target(3));
        slots.update(Arrays.asList(card(1), card(2), card(4)));
        assertEquals(Arrays.asList(2, 1, 4), ids(slots.cards()));
        slots.begin(target(3), card(4));
        slots.end(target(4));
        slots.update(Arrays.asList(card(3), card(1), card(2)));
        assertEquals(Arrays.asList(2, 1, 3), ids(slots.cards()));
    }

    @Test public void failedSwapRollsBackSlotsAndExitingStartsANewMruSession() {
        RecentTaskCardSlots slots = new RecentTaskCardSlots();
        slots.update(Arrays.asList(card(2), card(3), card(4)));
        slots.begin(foreground, card(3));
        slots.end(foreground);
        assertEquals(Arrays.asList(2, 3, 4), ids(slots.cards()));
        slots.clear();
        slots.update(Arrays.asList(card(4), card(3), card(2)));
        assertEquals(Arrays.asList(4, 3, 2), ids(slots.cards()));
    }

    @Test public void mainPreviewIsAvailableForTheSlotExchange() {
        source.cards = Arrays.asList(card(1), card(2), card(3), card(4));
        List<RecentTaskCard> main = new ArrayList<>();
        loader.request(foreground, 100, 220, results::add, main::add);
        work.remove(0).run();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Arrays.asList(2, 3, 4, 1), source.readIds);
        assertEquals(1, main.get(0).taskId);
        assertNotNull(main.get(0).preview);
    }

    private TaskScaleTarget target(int id) {
        return new TaskScaleTarget(id, "token-" + id, "surface-" + id,
                foreground.bounds, new Point(), true);
    }

    private List<Integer> ids(List<RecentTaskCard> cards) {
        List<Integer> ids = new ArrayList<>();
        for (RecentTaskCard card : cards) ids.add(card.taskId);
        return ids;
    }

    private static RecentTaskCard card(int id) {
        return new RecentTaskCard(id, 0, "token-" + id, "App " + id, null);
    }

    private void touch(RecentTaskCardView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private static final class FakeSource implements RecentTaskCardsLoader.Source {
        List<RecentTaskCard> cards;
        int unavailable = -1;
        final List<Integer> readIds = new ArrayList<>();
        final List<Bitmap> originals = new ArrayList<>();
        @Override public List<RecentTaskCard> recentTasks() { return cards; }
        @Override public Bitmap thumbnail(int id) {
            readIds.add(id);
            if (id == unavailable) return null;
            Bitmap bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLUE);
            originals.add(bitmap);
            return bitmap;
        }
    }

    public static class Info {
        public int taskId = 2, userId = 0, displayId = 0, parentTaskId = -1;
        public Token token = new Token();
        public Configuration configuration = new Configuration();
        public ComponentName topActivity = new ComponentName("example.app", "example.app.Main");
        public Intent baseIntent = new Intent().setComponent(topActivity);
    }
    public static class Token { public Object asBinder() { return this; } }
    public static class Configuration { public Window windowConfiguration = new Window(); }
    public static class Window {
        int mode = 1, type = 1;
        Rect bounds = new Rect(0, 0, 1080, 2400);
        public int getWindowingMode() { return mode; }
        public int getActivityType() { return type; }
        public Rect getBounds() { return bounds; }
    }
}
