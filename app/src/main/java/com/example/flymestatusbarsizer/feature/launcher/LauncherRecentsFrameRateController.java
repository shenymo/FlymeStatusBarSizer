package com.example.flymestatusbarsizer.feature.launcher;

import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import java.util.WeakHashMap;

final class LauncherRecentsFrameRateController {
    private static final float STACK_SCROLL_FRAME_RATE = 120f;
    private static final long RELEASE_DELAY_MS = 5000L;
    private static final WeakHashMap<View, Runnable> RELEASE_RUNNABLES = new WeakHashMap<>();

    private LauncherRecentsFrameRateController() {
    }

    static void onTouch(View recentsView, MotionEvent event) {
        if (recentsView == null || event == null) {
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            releaseLater(recentsView);
        } else {
            request(recentsView);
        }
    }

    static void onActiveScroll(View recentsView) {
        request(recentsView);
        releaseLater(recentsView);
    }

    static void releaseNow(View recentsView) {
        if (recentsView == null || Build.VERSION.SDK_INT < 35) {
            return;
        }
        Runnable pending = RELEASE_RUNNABLES.remove(recentsView);
        if (pending != null) {
            recentsView.removeCallbacks(pending);
        }
        recentsView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
    }

    private static void request(View recentsView) {
        if (recentsView == null || Build.VERSION.SDK_INT < 35) {
            return;
        }
        Runnable pending = RELEASE_RUNNABLES.remove(recentsView);
        if (pending != null) {
            recentsView.removeCallbacks(pending);
        }
        recentsView.setRequestedFrameRate(STACK_SCROLL_FRAME_RATE);
    }

    private static void releaseLater(View recentsView) {
        if (recentsView == null || Build.VERSION.SDK_INT < 35) {
            return;
        }
        Runnable old = RELEASE_RUNNABLES.remove(recentsView);
        if (old != null) {
            recentsView.removeCallbacks(old);
        }
        Runnable release = () -> releaseNow(recentsView);
        RELEASE_RUNNABLES.put(recentsView, release);
        recentsView.postDelayed(release, RELEASE_DELAY_MS);
    }
}
