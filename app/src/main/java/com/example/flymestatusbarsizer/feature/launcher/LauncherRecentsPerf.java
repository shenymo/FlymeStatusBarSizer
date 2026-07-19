package com.example.flymestatusbarsizer.feature.launcher;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class LauncherRecentsPerf {
    private static final String TAG = "FSBS-RecentsPerf";
    private static final String FLOW_TAG = "FSBS-RecentsFlow";
    private static final long SLOW_CALL_NS = 4_000_000L;
    private static final long FINISH_TIMEOUT_MS = 100L;

    private static final WeakHashMap<View, ViewState> VIEW_STATES = new WeakHashMap<>();

    private LauncherRecentsPerf() {
    }

    static boolean enabled(View view) {
        if (view == null) {
            return false;
        }
        FlymeStatusBarSizer.LauncherRecentsConfigSnapshot config =
                FlymeStatusBarSizer.loadLauncherRecentsConfig(view.getContext());
        return config != null
                && config.launcherIosStackRecentsEnabled
                && config.launcherRecentsPerfLoggingEnabled;
    }

    static boolean flowEnabled(View view) {
        return FlymeStatusBarSizer.isLauncherRecentsFlowLoggingEnabled(
                view != null ? view.getContext() : null);
    }

    static void beginSession(String name, View view) {
        View recentsView = resolveRecentsView(view);
        if (!enabled(recentsView)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return;
        }
        ViewState state = VIEW_STATES.get(recentsView);
        if (state == null) {
            state = new ViewState(recentsView);
            VIEW_STATES.put(recentsView, state);
        }
        Session old = state.sessions.get(name);
        if (old != null && !old.ending) {
            return;
        }
        old = state.sessions.remove(name);
        if (old != null) {
            cancelCallbacks(recentsView, old);
            reportSession(state, old, old.result != null ? old.result : "abort");
        }
        Session session = new Session(name, recentsView);
        state.sessions.put(name, session);
        ensureFrameMetrics(state);
    }

    static void finishSession(String name, View view, String result) {
        View recentsView = resolveRecentsView(view);
        ViewState state = VIEW_STATES.get(recentsView);
        Session session = state != null ? state.sessions.get(name) : null;
        if (session == null || session.ending) {
            return;
        }
        session.ending = true;
        session.result = result;
        session.endNs = System.nanoTime();
        if (state.window == null) {
            finishSessionNow(state, name);
            return;
        }
        session.finishFallback = () -> finishSessionNow(state, name);
        if (!recentsView.postDelayed(session.finishFallback, FINISH_TIMEOUT_MS)) {
            finishSessionNow(state, name);
        }
    }

    static void pulseSession(String name, View view, long idleMs) {
        View recentsView = resolveRecentsView(view);
        if (!enabled(recentsView)
                || !LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)) {
            return;
        }
        ViewState state = VIEW_STATES.get(recentsView);
        Session session = state != null ? state.sessions.get(name) : null;
        if (session == null || session.ending) {
            beginSession(name, recentsView);
            state = VIEW_STATES.get(recentsView);
            session = state != null ? state.sessions.get(name) : null;
        }
        if (session == null) {
            return;
        }
        if (session.pulseFinish != null) {
            recentsView.removeCallbacks(session.pulseFinish);
        }
        Session current = session;
        current.pulseFinish = () -> finishSession(name, recentsView, "success");
        if (!recentsView.postDelayed(current.pulseFinish, idleMs)) {
            finishSession(name, recentsView, "success");
        }
    }

    static long start(View view) {
        View recentsView = resolveRecentsView(view);
        if (!enabled(recentsView)) {
            return 0L;
        }
        return VIEW_STATES.containsKey(recentsView)
                || LauncherRecentsLayoutEngine.shouldUseStackLayout(recentsView)
                ? System.nanoTime()
                : 0L;
    }

    static long end(String name, View view, long startNs) {
        if (startNs == 0L) {
            return 0L;
        }
        long costNs = System.nanoTime() - startNs;
        View recentsView = resolveRecentsView(view);
        recordCall(recentsView, name, costNs);
        if (costNs > SLOW_CALL_NS) {
            Log.i(TAG, "slow name=" + name
                    + stateSuffix(recentsView)
                    + " taskCount=" + taskCount(recentsView)
                    + " costMs=" + ms(costNs));
        }
        return costNs;
    }

    static void measure(String name, View view, Runnable runnable) {
        long startNs = start(view);
        try {
            runnable.run();
        } finally {
            end(name, view, startNs);
        }
    }

    static void clearView(View view) {
        ViewState state = VIEW_STATES.remove(view);
        if (state == null) {
            return;
        }
        ArrayList<Session> sessions = new ArrayList<>(state.sessions.values());
        state.sessions.clear();
        for (Session session : sessions) {
            cancelCallbacks(state.view, session);
            reportSession(
                    state,
                    session,
                    session.result != null ? session.result : "abort");
        }
        removeFrameMetrics(state);
    }

    static void flow(String name, View view) {
        flow(name, view, null);
    }

    static void flow(String name, View view, String details) {
        if (name.startsWith("layout:")
                || name.endsWith(":frame")
                || name.contains("movingBlankTapHome")
                || !flowEnabled(view)) {
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

    private static void ensureFrameMetrics(ViewState state) {
        if (state.window != null) {
            return;
        }
        Window window = resolveWindow(state.view);
        if (window == null) {
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        Window.OnFrameMetricsAvailableListener listener =
                (target, metrics, dropped) -> recordFrame(state, metrics);
        try {
            window.addOnFrameMetricsAvailableListener(listener, handler);
            state.window = window;
            state.frameListener = listener;
        } catch (Throwable ignored) {
        }
    }

    private static void recordFrame(ViewState state, FrameMetrics metrics) {
        long totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
        if (totalNs <= 0L) {
            return;
        }
        float refreshRate = refreshRate(state.view);
        long budgetNs = Math.max(1L, Math.round(1_000_000_000d / refreshRate));
        ArrayList<String> finished = new ArrayList<>();
        for (Map.Entry<String, Session> entry : state.sessions.entrySet()) {
            Session session = entry.getValue();
            session.frameMetricsAvailable = true;
            session.refreshRate = refreshRate;
            session.frameCount++;
            session.totalFrameNs += totalNs;
            session.maxFrameNs = Math.max(session.maxFrameNs, totalNs);
            if (totalNs > budgetNs * 1.5d) {
                session.jankFrames++;
            }
            if (totalNs > budgetNs * 3d) {
                session.severeJankFrames++;
            }
            if (session.ending) {
                finished.add(entry.getKey());
            }
        }
        for (String name : finished) {
            finishSessionNow(state, name);
        }
    }

    private static void finishSessionNow(ViewState state, String name) {
        if (state == null) {
            return;
        }
        Session session = state.sessions.remove(name);
        if (session == null) {
            return;
        }
        cancelCallbacks(state.view, session);
        reportSession(state, session, session.result != null ? session.result : "success");
        if (state.sessions.isEmpty()) {
            removeFrameMetrics(state);
            VIEW_STATES.remove(state.view);
        }
    }

    private static void reportSession(ViewState state, Session session, String result) {
        long endNs = session.endNs != 0L ? session.endNs : System.nanoTime();
        float avgFrameMs = session.frameCount > 0
                ? ms(session.totalFrameNs / session.frameCount)
                : 0f;
        float jankRate = session.frameCount > 0
                ? session.jankFrames * 100f / session.frameCount
                : 0f;
        Log.i(TAG, "session=" + session.name
                + " result=" + result
                + " durationMs=" + ms(endNs - session.startNs)
                + " refreshHz=" + session.refreshRate
                + " frames=" + session.frameCount
                + " jank=" + session.jankFrames
                + " severe=" + session.severeJankFrames
                + " jankRate=" + jankRate
                + " avgFrameMs=" + avgFrameMs
                + " maxFrameMs=" + ms(session.maxFrameNs)
                + " frameMetrics=" + (session.frameMetricsAvailable ? "available" : "unavailable")
                + formatCalls(" layout", session.layout)
                + formatCalls(" blur", session.blur)
                + formatCalls(" native", session.nativeCalls)
                + formatCalls(" module", session.module)
                + " taskCountStart=" + session.taskCountStart
                + " taskCountEnd=" + taskCount(state.view));
    }

    private static String formatCalls(String prefix, CallStats stats) {
        if (stats.count == 0) {
            return prefix + "Count=0";
        }
        return prefix + "Count=" + stats.count
                + prefix + "AvgMs=" + ms(stats.totalNs / stats.count)
                + prefix + "MaxMs=" + ms(stats.maxNs);
    }

    private static void recordCall(View recentsView, String name, long costNs) {
        ViewState state = VIEW_STATES.get(recentsView);
        if (state == null) {
            return;
        }
        for (Session session : state.sessions.values()) {
            CallStats stats = callStats(session, name);
            stats.count++;
            stats.totalNs += costNs;
            stats.maxNs = Math.max(stats.maxNs, costNs);
        }
    }

    private static CallStats callStats(Session session, String name) {
        if (name.startsWith("layout") || name.startsWith("applyStackLayout")) {
            return session.layout;
        }
        if (name.startsWith("blur")) {
            return session.blur;
        }
        if (name.startsWith("native")) {
            return session.nativeCalls;
        }
        return session.module;
    }

    private static void cancelCallbacks(View view, Session session) {
        if (session.finishFallback != null) {
            view.removeCallbacks(session.finishFallback);
        }
        if (session.pulseFinish != null) {
            view.removeCallbacks(session.pulseFinish);
        }
    }

    private static void removeFrameMetrics(ViewState state) {
        if (state.window == null || state.frameListener == null) {
            return;
        }
        try {
            state.window.removeOnFrameMetricsAvailableListener(state.frameListener);
        } catch (Throwable ignored) {
        }
        state.window = null;
        state.frameListener = null;
    }

    private static Window resolveWindow(View view) {
        Context context = view != null ? view.getContext() : null;
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return ((Activity) context).getWindow();
            }
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) {
                break;
            }
            context = base;
        }
        Object container = LauncherRecentsCompat.getFieldCompat(view, "mContainer");
        if (container instanceof Activity) {
            return ((Activity) container).getWindow();
        }
        Object window = LauncherRecentsCompat.invokeCompat(container, "getWindow");
        return window instanceof Window ? (Window) window : null;
    }

    private static View resolveRecentsView(View view) {
        if (view == null) {
            return null;
        }
        View recentsView = LauncherRecentsCompat.resolveOwningRecentsView(view);
        return recentsView != null ? recentsView : view;
    }

    private static float refreshRate(View view) {
        Display display = view != null ? view.getDisplay() : null;
        float refreshRate = display != null ? display.getRefreshRate() : 60f;
        return refreshRate > 0f ? refreshRate : 60f;
    }

    private static float ms(long valueNs) {
        return valueNs / 1_000_000f;
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
        return LauncherRecentsCompat.invokeInt(resolveRecentsView(view), "getTaskViewCount", 0);
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
        return value instanceof Integer ? (Integer) value : view.getScrollX();
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
                || LauncherRecentsState.isPendingGestureRecentsStackReleaseHandoff(view)) {
            return "enterRecentsAnim";
        }
        if (LauncherRecentsState.isSwipeUpGestureActive(view)
                || LauncherRecentsState.isAppToRecentsEntrySessionActive(view)
                || LauncherRecentsState.isAppToRecentsGestureReleased(view)) {
            return "enterRecents";
        }
        if (LauncherRecentsStateAnimationController.isOverviewStateStackAnimationActive(view)
                || LauncherRecentsStateAnimationController.isOverviewPeekStockAnimationActive(view)) {
            return "stackStateAnim";
        }
        return view.isShown() ? "stackIdle" : "normal";
    }

    private static String launcherState(View view) {
        Object stateManager = LauncherRecentsCompat.invokeCompat(view, "getStateManager");
        Object state = LauncherRecentsCompat.invokeCompat(stateManager, "getState");
        return state != null ? String.valueOf(state) : "unknown";
    }

    private static final class ViewState {
        final View view;
        final HashMap<String, Session> sessions = new HashMap<>();
        Window window;
        Window.OnFrameMetricsAvailableListener frameListener;

        ViewState(View view) {
            this.view = view;
        }
    }

    private static final class Session {
        final String name;
        final long startNs = System.nanoTime();
        final int taskCountStart;
        final CallStats layout = new CallStats();
        final CallStats blur = new CallStats();
        final CallStats nativeCalls = new CallStats();
        final CallStats module = new CallStats();
        long endNs;
        long totalFrameNs;
        long maxFrameNs;
        int frameCount;
        int jankFrames;
        int severeJankFrames;
        float refreshRate;
        boolean frameMetricsAvailable;
        boolean ending;
        String result;
        Runnable finishFallback;
        Runnable pulseFinish;

        Session(String name, View view) {
            this.name = name;
            taskCountStart = taskCount(view);
            refreshRate = LauncherRecentsPerf.refreshRate(view);
        }
    }

    private static final class CallStats {
        long totalNs;
        long maxNs;
        int count;
    }
}
