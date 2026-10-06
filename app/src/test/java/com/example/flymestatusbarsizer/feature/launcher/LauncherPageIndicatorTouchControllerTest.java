package com.example.flymestatusbarsizer.feature.launcher;

import static org.junit.Assert.*;

import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherPageIndicatorTouchControllerTest {
    private final LauncherPageIndicatorTouchController controller =
            new LauncherPageIndicatorTouchController();
    private final FakeHost host = new FakeHost();
    private final List<Integer> nativeActions = new ArrayList<>();

    @Before public void prepare() {
        controller.prepare(host, targets(new int[]{2, 5, 9}), 8, 2);
    }

    @Test public void tapAndSmallJitterKeepTheOriginalClickStream() throws Throwable {
        assertEquals(false, send(MotionEvent.ACTION_DOWN, 140, 200, 0));
        assertEquals(false, send(MotionEvent.ACTION_MOVE, 145, 201, 20));
        assertEquals(false, send(MotionEvent.ACTION_UP, 145, 201, 40));
        assertEquals(List.of(0, 2, 1), nativeActions);
        assertTrue(host.pages.isEmpty());
        assertEquals(0, host.restores);
        assertFalse(controller.hasGesture());
    }

    @Test public void dragCancelsNativeTouchOnceAndCrossesPagesInBothDirections() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        assertEquals(true, send(MotionEvent.ACTION_MOVE, 140, 200, 20));
        send(MotionEvent.ACTION_MOVE, 300, 200, 40);
        send(MotionEvent.ACTION_MOVE, 0, 200, 60);
        send(MotionEvent.ACTION_UP, 0, 200, 80);
        assertEquals(List.of(0, 3), nativeActions);
        assertEquals(List.of(5, 9, 2), host.pages);
        assertEquals(1, host.shows);
        assertEquals(1, host.restores);
        assertTrue(host.restoredAfterRelease);
        assertFalse(controller.hasGesture());
    }

    @Test public void stationaryMovesDoNotRestartThePageAnimationAndBoundaryHasHysteresis() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        send(MotionEvent.ACTION_MOVE, 140, 200, 30);
        send(MotionEvent.ACTION_MOVE, 161, 200, 40);
        assertEquals(List.of(5), host.pages);
        send(MotionEvent.ACTION_MOVE, 163, 200, 50);
        send(MotionEvent.ACTION_UP, 163, 200, 60);
        assertEquals(List.of(5, 9), host.pages);
    }

    @Test public void physicalCoordinatesSupportRtlAndNonconsecutiveWorkspaceIndices() throws Throwable {
        controller.prepare(host, targets(new int[]{9, 5, 2}), 8, 2);
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 180, 200, 20);
        send(MotionEvent.ACTION_MOVE, 90, 200, 40);
        send(MotionEvent.ACTION_UP, 90, 200, 60);
        assertEquals(List.of(2, 9), host.pages);
    }

    @Test public void gestureStartingOutsideCannotEnterTheIndicatorLater() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 20, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        send(MotionEvent.ACTION_UP, 180, 200, 40);
        assertEquals(List.of(0, 2, 1), nativeActions);
        assertTrue(host.pages.isEmpty());
    }

    @Test public void verticalScrollIsNeverTakenOverLater() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 102, 225, 20);
        send(MotionEvent.ACTION_MOVE, 180, 225, 40);
        send(MotionEvent.ACTION_UP, 180, 225, 60);
        assertEquals(List.of(0, 2, 2, 1), nativeActions);
        assertTrue(host.pages.isEmpty());
    }

    @Test public void longPressKeepsTheOriginalGesture() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 180, 200, ViewConfiguration.getLongPressTimeout() + 1);
        assertEquals(List.of(0, 2), nativeActions);
        assertTrue(host.pages.isEmpty());
    }

    @Test public void stateOrPageTopologyChangeBeforeCapturePassesThrough() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        host.available = false;
        send(MotionEvent.ACTION_MOVE, 180, 200, 20);
        send(MotionEvent.ACTION_UP, 180, 200, 40);
        assertEquals(List.of(0, 2, 1), nativeActions);
        assertTrue(host.pages.isEmpty());
    }

    @Test public void stateChangeAfterCaptureConsumesRemainderWithoutSwitchingMorePages() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        host.available = false;
        send(MotionEvent.ACTION_MOVE, 180, 200, 40);
        assertEquals(true, send(MotionEvent.ACTION_UP, 180, 200, 60));
        assertEquals(List.of(0, 3), nativeActions);
        assertEquals(List.of(5), host.pages);
        assertFalse(controller.hasGesture());
    }

    @Test public void multiTouchBeforeCaptureKeepsNativePinch() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        secondPointer();
        send(MotionEvent.ACTION_MOVE, 180, 200, 40);
        send(MotionEvent.ACTION_UP, 180, 200, 60);
        assertEquals(List.of(0, 5, 2, 1), nativeActions);
        assertTrue(host.pages.isEmpty());
    }

    @Test public void multiTouchAfterCaptureStopsPagingAndDoesNotSendOrphanedUp() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 10);
        assertEquals(true, secondPointer());
        send(MotionEvent.ACTION_MOVE, 180, 200, 40);
        send(MotionEvent.ACTION_UP, 180, 200, 60);
        assertEquals(List.of(0, 3), nativeActions);
        assertEquals(List.of(5), host.pages);
        assertEquals(1, host.restores);
    }

    @Test public void cancelAndLifecycleCleanupReleaseSearchHold() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        send(MotionEvent.ACTION_CANCEL, 180, 200, 40);
        assertEquals(List.of(5), host.pages);
        assertEquals(1, host.restores);
        assertFalse(controller.hasGesture());
        prepare();
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        controller.finish();
        assertFalse(controller.hasGesture());
        assertEquals(2, host.restores);
    }

    @Test public void reflectionFailureAfterCaptureRestoresAndConsumesUntilUp() throws Throwable {
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        host.failSnap = true;
        send(MotionEvent.ACTION_MOVE, 140, 200, 20);
        send(MotionEvent.ACTION_MOVE, 180, 200, 40);
        send(MotionEvent.ACTION_UP, 180, 200, 60);
        assertEquals(List.of(0, 3), nativeActions);
        assertEquals(1, host.failures);
        assertEquals(1, host.restores);
        assertFalse(controller.hasGesture());
        host.failSnap = false;
        prepare();
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 180, 200, 20);
        assertEquals(List.of(9), host.pages);
    }

    @Test public void singlePageKeepsOriginalTouchAndSpecialScreensAreExcluded() throws Throwable {
        controller.prepare(host, new LauncherPageIndicatorTouchController.Targets(
                new int[]{0}, new float[]{100}, new RectF(80, 180, 200, 220)), 8, 2);
        send(MotionEvent.ACTION_DOWN, 100, 200, 0);
        send(MotionEvent.ACTION_MOVE, 180, 200, 20);
        assertEquals(List.of(0, 2), nativeActions);
        for (int id : new int[]{-1000, -201, -200, -1, 100000000}) {
            assertFalse(LauncherPageIndicatorHooks.isDesktopScreen(id));
        }
        assertTrue(LauncherPageIndicatorHooks.isDesktopScreen(0));
        assertTrue(LauncherPageIndicatorHooks.isDesktopScreen(42));
    }

    private LauncherPageIndicatorTouchController.Targets targets(int[] pages) {
        return new LauncherPageIndicatorTouchController.Targets(pages,
                new float[]{100, 140, 180}, new RectF(80, 180, 200, 220));
    }

    private Object send(int action, float x, float y, long time) throws Throwable {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        try {
            return controller.onTouch(event, this::dispatch);
        } finally {
            event.recycle();
        }
    }

    private Object secondPointer() throws Throwable {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = 140 + i * 20;
            coords[i].y = 200;
        }
        MotionEvent event = MotionEvent.obtain(0, 20, MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, properties, coords,
                0, 0, 1, 1, 0, 0, 0, 0);
        try {
            return controller.onTouch(event, this::dispatch);
        } finally {
            event.recycle();
        }
    }

    private Object dispatch(MotionEvent event) {
        nativeActions.add(event.getActionMasked());
        return false;
    }

    private final class FakeHost implements LauncherPageIndicatorTouchController.Host {
        final List<Integer> pages = new ArrayList<>();
        boolean available = true;
        boolean failSnap;
        boolean restoredAfterRelease;
        int shows, restores, failures;

        @Override public boolean isAvailable() { return available; }
        @Override public void showDots() { shows++; }
        @Override public void restoreSearch() {
            restores++;
            restoredAfterRelease = !controller.isDragging();
        }
        @Override public void snapToPage(int page) throws Exception {
            if (failSnap) throw new NoSuchMethodException("snapToPage");
            pages.add(page);
        }
        @Override public void onFailure(Exception error) { failures++; }
    }
}
