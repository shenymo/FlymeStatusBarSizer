package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.util.Log;
import android.view.View;

import java.util.HashMap;

final class LauncherRecentsPerf {
    private static final String TAG = "FSBS-RecentsPerf";
    private static final String FLOW_TAG = "FSBS-RecentsFlow";
    private static final long REPORT_WINDOW_NS = 1_000_000_000L;
    private static final long SLOW_CALL_NS = 4_000_000L;

    private static final HashMap<String, Stats> STATS = new HashMap<>();
    private static final ThreadLocal<View> CURRENT_VIEW = new ThreadLocal<>();

    private LauncherRecentsPerf() {
    }

    static boolean enabled(View view) {
        return FlymeStatusBarSizer.isLauncherRecentsPerfLoggingEnabled(
                view != null ? view.getContext() : null);
    }

    static boolean flowEnabled(View view) {
        return FlymeStatusBarSizer.isLauncherRecentsFlowLoggingEnabled(
                view != null ? view.getContext() : null);
    }

    static long start(View view) {
        if (!enabled(view)) {
            return 0L;
        }
        CURRENT_VIEW.set(view);
        return System.nanoTime();
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
        View view = CURRENT_VIEW.get();
        Log.i(TAG, name
                + stateSuffix(view)
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
                + stateSuffix(view)
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
        long startNs = start(view);
        end(name, startNs);
    }

    static void flow(String name, View view) {
        flow(name, view, null);
    }

    static void flow(String name, View view, String details) {
        if (!flowEnabled(view)) {
            return;
        }
        Log.i(FLOW_TAG, name
                + stateSuffix(view)
                + " page=" + page(view)
                + " nextPage=" + nextPage(view)
                + " scroll=" + primaryScroll(view)
                + " taskCount=" + taskCount(view)
                + (details != null && !details.isEmpty() ? " " + details : ""));
    }

    static void install(String name, String details) {
        if (!FlymeStatusBarSizer.isLauncherRecentsFlowLoggingEnabled(null)) {
            return;
        }
        Log.i(FLOW_TAG, name
                + (details != null && !details.isEmpty() ? " " + details : ""));
    }

    private static String stateSuffix(View view) {
        return " phase=" + phase(view) + " launcherState=" + launcherState(view);
    }

    private static int page(View view) {
        return LauncherRecentsCompat.invokeInt(view, "getCurrentPage", -1);
    }

    private static int nextPage(View view) {
        return LauncherRecentsCompat.readIntField(view, "mNextPage", -1);
    }

    private static int taskCount(View view) {
        return LauncherRecentsCompat.invokeInt(view, "getTaskViewCount", 0);
    }

    private static int primaryScroll(View view) {
        if (view == null) {
            return 0;
        }
        Object orientationHandler =
                LauncherRecentsCompat.getFieldCompat(view, "mOrientationHandler");
        Object value = LauncherRecentsCompat.invokeCompat(
                orientationHandler,
                "getPrimaryScroll",
                new Class<?>[]{View.class},
                view);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return view.getScrollX();
    }

    private static String phase(View view) {
        if (view == null) {
            return "unknown";
        }
        if (LauncherRecentsTransitionController.isBlankTapHomeExitActive(view)) {
            return "returnHomeAnim";
        }
        if (LauncherRecentsState.hasActiveTaskLaunchTransitionGeometry(view)
                || LauncherRecentsState.isTaskLaunchLayoutFrozen(view)) {
            return "launchTaskAnim";
        }
        if (LauncherRecentsTransitionController.hasGestureRecentsStackReleaseProgress(view)
                || LauncherRecentsTransitionController
                .isGestureRecentsStackReleaseAnimationActive(view)
                || LauncherRecentsState.isPendingGestureRecentsStackRelease(view)
                || LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(view)
                || LauncherRecentsState.isGestureStackReleasedStable(view)) {
            return "enterRecentsAnim";
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(view)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(view)
                || LauncherRecentsState.isAppToRecentsStackLayoutDeferred(view)
                || LauncherRecentsState.isAppToRecentsGestureReleased(view)) {
            return "enterRecents";
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(view)
                || LauncherRecentsStateAnimationController.isOverviewPeekStockAnimationActive(view)) {
            return "stackStateAnim";
        }
        if (view.isShown()) {
            return "stackIdle";
        }
        return "normal";
    }

    private static String launcherState(View view) {
        Object stateManager = LauncherRecentsCompat.invokeCompat(view, "getStateManager");
        Object state = LauncherRecentsCompat.invokeCompat(stateManager, "getState");
        return state != null ? String.valueOf(state) : "unknown";
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
