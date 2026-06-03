package com.example.flymestatusbarsizer.feature.mback;

import android.view.MotionEvent;
import android.view.View;

public final class MBackStarOverlayBridge {
    private static final Object LOCK = new Object();
    private static MBackStarOverlayController controller;
    private static MotionEvent latestMotionEvent;

    private MBackStarOverlayBridge() {
    }

    public static void show(View anchor) {
        if (anchor == null) {
            return;
        }
        MBackStarOverlayController target;
        MotionEvent startEvent;
        synchronized (LOCK) {
            if (controller == null || !controller.isActive()) {
                controller = new MBackStarOverlayController(anchor.getContext());
            }
            target = controller;
            startEvent = latestMotionEvent == null || isTerminalEvent(latestMotionEvent)
                    ? null
                    : MotionEvent.obtain(latestMotionEvent);
        }
        try {
            target.show(anchor, startEvent);
        } finally {
            if (startEvent != null) {
                startEvent.recycle();
            }
        }
    }

    public static boolean dispatchMBackMotionEvent(MotionEvent event) {
        if (isTerminalEvent(event)) {
            clearLatestMotionEvent();
        } else {
            rememberLatestMotionEvent(event);
        }
        MBackStarOverlayController target;
        synchronized (LOCK) {
            target = controller;
        }
        return target != null && target.handleMBackMotionEvent(event);
    }

    public static boolean isActive() {
        MBackStarOverlayController target;
        synchronized (LOCK) {
            target = controller;
        }
        return target != null && target.isActive();
    }

    static void clearLatestMotionEvent() {
        synchronized (LOCK) {
            if (latestMotionEvent != null) {
                latestMotionEvent.recycle();
                latestMotionEvent = null;
            }
        }
    }

    private static void rememberLatestMotionEvent(MotionEvent event) {
        if (event == null) {
            return;
        }
        synchronized (LOCK) {
            if (latestMotionEvent != null) {
                latestMotionEvent.recycle();
            }
            latestMotionEvent = MotionEvent.obtain(event);
        }
    }

    private static boolean isTerminalEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    }
}
