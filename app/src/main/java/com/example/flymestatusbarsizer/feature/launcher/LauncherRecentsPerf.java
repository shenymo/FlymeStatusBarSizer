package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.util.Log;
import android.view.View;

import java.util.HashMap;

final class LauncherRecentsPerf {
    private static final String TAG = "FSBS-RecentsPerf";
    private static final long REPORT_WINDOW_NS = 1_000_000_000L;
    private static final long SLOW_CALL_NS = 4_000_000L;

    private static final HashMap<String, Stats> STATS = new HashMap<>();

    private LauncherRecentsPerf() {
    }

    static boolean enabled(View view) {
        return FlymeStatusBarSizer.isLauncherRecentsPerfLoggingEnabled(
                view != null ? view.getContext() : null);
    }

    static long start(View view) {
        return enabled(view) ? System.nanoTime() : 0L;
    }

    static long end(String name, long startNs) {
        if (startNs == 0L) {
            return 0L;
        }
        long nowNs = System.nanoTime();
        long costNs = nowNs - startNs;
        Stats stats = STATS.get(name);
        if (stats == null) {
            stats = new Stats(nowNs);
            STATS.put(name, stats);
        }
        stats.count++;
        stats.totalNs += costNs;
        stats.maxNs = Math.max(stats.maxNs, costNs);
        if (costNs > SLOW_CALL_NS) {
            stats.slowCount++;
        }
        if (nowNs - stats.windowStartNs < REPORT_WINDOW_NS || stats.count == 0) {
            return costNs;
        }
        Log.i(TAG, name
                + " count=" + stats.count
                + " avgMs=" + (stats.totalNs / stats.count / 1_000_000f)
                + " maxMs=" + (stats.maxNs / 1_000_000f)
                + " slow4ms=" + stats.slowCount);
        stats.windowStartNs = nowNs;
        stats.totalNs = 0L;
        stats.maxNs = 0L;
        stats.count = 0;
        stats.slowCount = 0;
        return costNs;
    }

    static boolean isSlowCall(long costNs) {
        return costNs > SLOW_CALL_NS;
    }

    static void logSlowCall(String name, View view, long costNs, String details) {
        if (!enabled(view) || !isSlowCall(costNs)) {
            return;
        }
        Log.i(TAG, name
                + " costMs=" + (costNs / 1_000_000f)
                + (details != null && !details.isEmpty() ? " " + details : ""));
    }

    static void hit(String name) {
        long startNs = System.nanoTime();
        end(name, startNs);
    }

    static void hit(String name, View view) {
        if (!enabled(view)) {
            return;
        }
        hit(name);
    }

    private static final class Stats {
        long windowStartNs;
        long totalNs;
        long maxNs;
        int count;
        int slowCount;

        Stats(long windowStartNs) {
            this.windowStartNs = windowStartNs;
        }
    }
}
