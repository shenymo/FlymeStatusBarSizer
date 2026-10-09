package com.example.flymestatusbarsizer.feature.launcher;

import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Owns only gestures that start in the indicator hit area and become horizontal drags. */
final class LauncherPageIndicatorTouchController {
    private static final float HORIZONTAL_DRAG_RATIO = 1.3f;

    interface Host {
        boolean isAvailable() throws Exception;
        void showDots() throws Exception;
        void restoreSearch() throws Exception;
        void snapToPage(int page) throws Exception;
        void onFailure(Exception error);
    }

    interface Dispatcher {
        Object dispatch(MotionEvent event) throws Throwable;
    }

    static final class Targets {
        final int[] pages;
        final float[] centers;
        final RectF bounds;

        Targets(int[] pages, float[] centers, RectF bounds) {
            this.pages = pages;
            this.centers = centers;
            this.bounds = new RectF(bounds);
        }

        int nearest(float x, int previous, float hysteresis) {
            int nearest = 0;
            for (int i = 1; i < centers.length; i++) {
                if (Math.abs(x - centers[i]) < Math.abs(x - centers[nearest])) nearest = i;
            }
            if (previous >= 0 && nearest != previous) {
                float boundary = (centers[nearest] + centers[previous]) / 2f;
                if (Math.abs(x - boundary) < hysteresis) return previous;
            }
            return nearest;
        }
    }

    private Host host;
    private Targets targets;
    private float downX;
    private float downY;
    private int pointerId;
    private long downTime;
    private float touchSlop;
    private float hysteresis;
    private int selected = -1;
    private boolean dragging;
    // Once native views have received CANCEL, never give them an orphaned MOVE/UP.
    private boolean consumeRemainder;

    void prepare(Host host, Targets targets, float touchSlop, float hysteresis) {
        reset();
        this.host = host;
        this.targets = targets;
        this.touchSlop = touchSlop;
        this.hysteresis = hysteresis;
    }

    boolean isDragging() {
        return dragging;
    }

    boolean hasGesture() {
        return host != null || consumeRemainder;
    }

    Object onTouch(MotionEvent event, Dispatcher dispatcher) throws Throwable {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            consumeRemainder = false;
            if (host == null || targets.pages.length < 2
                    || !targets.bounds.contains(event.getRawX(), event.getRawY())) {
                reset();
            } else {
                downX = event.getRawX();
                downY = event.getRawY();
                downTime = event.getEventTime();
                pointerId = event.getPointerId(0);
            }
            return dispatcher.dispatch(event);
        }
        if (host == null) {
            boolean consumed = consumeRemainder;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) reset();
            return consumed ? true : dispatcher.dispatch(event);
        }
        if (action == MotionEvent.ACTION_CANCEL || event.getPointerCount() != 1
                || event.getPointerId(0) != pointerId) {
            boolean consumed = dragging;
            finish();
            consumeRemainder = consumed && action != MotionEvent.ACTION_CANCEL;
            return consumed ? true : dispatcher.dispatch(event);
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean consumed = dragging;
            if (consumed && available()) updateTarget(event.getRawX());
            finish();
            return consumed ? true : dispatcher.dispatch(event);
        }
        if (action != MotionEvent.ACTION_MOVE) {
            return dragging ? true : dispatcher.dispatch(event);
        }
        if (!available()) {
            boolean consumed = dragging;
            finish();
            consumeRemainder = consumed;
            return consumed ? true : dispatcher.dispatch(event);
        }
        if (!dragging) {
            float dx = Math.abs(event.getRawX() - downX);
            float dy = Math.abs(event.getRawY() - downY);
            if (event.getEventTime() - downTime >= ViewConfiguration.getLongPressTimeout()
                    || (dy > touchSlop && dy >= dx)) {
                reset();
                return dispatcher.dispatch(event);
            }
            if (dx <= touchSlop || dx <= dy * HORIZONTAL_DRAG_RATIO) return dispatcher.dispatch(event);
            dragging = true;
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            try {
                dispatcher.dispatch(cancel);
            } finally {
                cancel.recycle();
            }
            try {
                host.showDots();
            } catch (Exception error) {
                fail(error);
                return true;
            }
        }
        updateTarget(event.getRawX());
        return true;
    }

    private boolean available() {
        try {
            return host.isAvailable();
        } catch (Exception error) {
            host.onFailure(error);
            return false;
        }
    }

    private void updateTarget(float x) {
        int next = targets.nearest(x, selected, hysteresis);
        if (next == selected) return;
        try {
            host.snapToPage(targets.pages[next]);
            selected = next;
        } catch (Exception error) {
            fail(error);
        }
    }

    private void fail(Exception error) {
        host.onFailure(error);
        finish();
        consumeRemainder = true;
    }

    void finish() {
        Host previous = host;
        boolean restore = dragging;
        reset();
        if (restore && previous != null) {
            try {
                previous.restoreSearch();
            } catch (Exception error) {
                previous.onFailure(error);
            }
        }
    }

    void reset() {
        host = null;
        targets = null;
        selected = -1;
        dragging = false;
        consumeRemainder = false;
    }
}
