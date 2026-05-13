package com.example.flymestatusbarsizer.feature.clock;

import android.view.View;

import java.util.WeakHashMap;

public final class ClockDetailPopupBridge {
    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, ClockDetailPopupController> MBACK_POPUPS =
            new WeakHashMap<>();

    private ClockDetailPopupBridge() {
    }

    public static void showFromMBack(View anchor) {
        if (anchor == null) {
            return;
        }
        ClockDetailPopupController controller;
        synchronized (LOCK) {
            controller = MBACK_POPUPS.get(anchor);
            if (controller == null) {
                controller = new ClockDetailPopupController(
                        anchor,
                        ClockDetailPopupController.HostMode.MBACK);
                MBACK_POPUPS.put(anchor, controller);
            }
        }
        controller.showFromMBackTrigger();
    }
}
