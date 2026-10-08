package com.example.flymestatusbarsizer.feature.onehanded;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

/** Measures only the host UI. Task surfaces bypass its draw callbacks. */
final class OneStepPerf {
    private final View view;
    private final Handler handler;
    private final int session;
    private final ViewTreeObserver.OnDrawListener onDraw = () -> this.draws++;
    private final Runnable report = this::report;
    private int draws;
    private long since = SystemClock.uptimeMillis();
    private String phase = "opening";
    private boolean stopped;

    OneStepPerf(View view, Handler handler, int session) {
        this.view = view;
        this.handler = handler;
        this.session = session;
        view.getViewTreeObserver().addOnDrawListener(onDraw);
        handler.postDelayed(report, 3000);
        Log.i("FlymeOneStepPerf", "session=" + session + " renderer=ShellTaskView input=native"
                + " contentFps=unavailable; host draws are not app frame rate; Surface rates are hints");
    }

    void phase(String next) { phase = next; }
    void taskReady(int slot, int taskId) {
        Log.i("FlymeOneStepPerf", "session=" + session + " taskAttached=" + taskId + " slot=" + slot);
    }
    private void report() {
        if (stopped) return;
        long now = SystemClock.uptimeMillis();
        Log.i("FlymeOneStepPerf", "session=" + session + " phase=" + phase
                + " hostDrawHz=" + draws * 1000f / Math.max(1, now - since));
        draws = 0;
        since = now;
        handler.postDelayed(report, 3000);
    }
    void stop() {
        stopped = true;
        handler.removeCallbacks(report);
        if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnDrawListener(onDraw);
    }
}
