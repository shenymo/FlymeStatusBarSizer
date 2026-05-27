package com.example.flymestatusbarsizer.feature.launcher;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

final class LauncherRecentsPerf {
    private static final boolean ENABLED = true;
    private static final String TAG = "FSBS-RecentsPerf";
    private static final long REPORT_WINDOW_NS = 1_000_000_000L;
    private static final long SLOW_CALL_NS = 4_000_000L;

    private static final HashMap<String, Stats> STATS = new HashMap<>();

    private LauncherRecentsPerf() {
    }

    static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    static void end(String name, long startNs) {
        if (!ENABLED || startNs == 0L) {
            return;
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
            return;
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
    }

    static void hit(String name) {
        long startNs = start();
        end(name, startNs);
    }

    static void reportPending() {
        if (!ENABLED || STATS.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Stats> entry : STATS.entrySet()) {
            Stats stats = entry.getValue();
            if (stats.count == 0) {
                continue;
            }
            Log.i(TAG, entry.getKey()
                    + " count=" + stats.count
                    + " avgMs=" + (stats.totalNs / stats.count / 1_000_000f)
                    + " maxMs=" + (stats.maxNs / 1_000_000f)
                    + " slow4ms=" + stats.slowCount);
        }
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
