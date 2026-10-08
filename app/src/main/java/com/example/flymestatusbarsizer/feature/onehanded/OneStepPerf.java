package com.example.flymestatusbarsizer.feature.onehanded;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.Arrays;

/** Passive UI-thread measurements. No invalidation, frame loop, bitmap readback or input tracing. */
final class OneStepPerf {
    private static final String TAG = "FlymeOneStepPerf";
    private static final long PERIOD_MS = 2000;
    private final View host;
    private final Handler handler;
    private final int session;
    private final ArrayList<Pane> panes = new ArrayList<>();
    private final Cadence draws = new Cadence();
    private final ViewTreeObserver.OnDrawListener drawListener = this::onDraw;
    private final Runnable tick = this::tick;
    private long windowStartNs = System.nanoTime();
    private long nextReportUptime;
    private boolean stopped;
    private String phase = "opening";

    OneStepPerf(View host, Handler handler, int session) {
        this.host = host;
        this.handler = handler;
        this.session = session;
        host.getViewTreeObserver().addOnDrawListener(drawListener);
        Log.i(TAG, "session=" + session + " start; periodMs=" + PERIOD_MS
                + "; renderer=SurfaceView; hostDraw=overlay UI traversal only"
                + "; contentFps=unavailable: surface content bypasses host draws; low hostDrawHz is normal"
                + "; bufferReady means PixelCopy observed a queued buffer, not physical presentation"
                + "; Ms samples={n,avg,p50,p95,max}, percentiles use 0.5ms buckets with >128ms overflow"
                + "; forwardSubmitMs excludes target app handling");
        schedule();
    }

    Pane addPane(int slot, boolean main, Display display, String app, int width, int height) {
        Pane pane = new Pane(slot, main, display, app, width, height);
        panes.add(pane);
        return pane;
    }

    void removePane(Pane pane) {
        if (pane == null || !panes.contains(pane)) return;
        if (!stopped) pane.report(System.nanoTime(), "release");
        panes.remove(pane);
    }

    void phase(String next) {
        if (stopped || phase.equals(next)) return;
        report("phase-end");
        phase = next;
    }

    void stop() {
        if (stopped) return;
        report("stop");
        stopped = true;
        handler.removeCallbacks(tick);
        ViewTreeObserver observer = host.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnDrawListener(drawListener);
        panes.clear();
    }

    private void onDraw() {
        if (stopped) return;
        draws.record(System.nanoTime());
    }

    private void schedule() {
        nextReportUptime = SystemClock.uptimeMillis() + PERIOD_MS;
        handler.postAtTime(tick, nextReportUptime);
    }

    private void tick() {
        if (stopped) return;
        report("periodic");
        schedule();
    }

    private void report(String reason) {
        long now = System.nanoTime();
        Display display = host.getDisplay();
        double seconds = Math.max(1L, now - windowStartNs) / 1_000_000_000d;
        Log.i(TAG, "session=" + session + " phase=" + phase + " reason=" + reason
                + " windowMs=" + rounded(seconds * 1000)
                + " physicalReportedHz=" + (display == null ? 0f : display.getRefreshRate())
                + " reportLateMs=" + Math.max(0L, SystemClock.uptimeMillis() - nextReportUptime)
                + " hostDrawHz=" + rounded(draws.count / seconds)
                + " hostDrawGapMs=" + draws.gaps.summary()
                + " hostQuietMs=" + draws.quietMs(now));
        draws.clearWindow();
        windowStartNs = now;
        for (Pane pane : panes) pane.report(now, reason);
    }

    final class Pane {
        private int slot;
        private boolean main;
        private final Display display;
        private String app;
        private final int width;
        private final int height;
        private final Cadence moves = new Cadence();
        private final Samples eventAge = new Samples();
        private final Samples oldestSampleAge = new Samples();
        private final Samples submit = new Samples();
        private final Samples bufferWait = new Samples();
        private final Samples snapshots = new Samples();
        private long startNs = System.nanoTime();
        private boolean surfaceConnected;
        private boolean bufferReady;
        private int surfaceAttachments;
        private int surfaceLosses;
        private int snapshotFailures;
        private int events;
        private int inputSamples;
        private int submitFailures;
        private boolean touching;
        private long touchStartNs;
        private long touchDurationNs;

        Pane(int slot, boolean main, Display display, String app, int width, int height) {
            this.slot = slot;
            this.main = main;
            this.display = display;
            this.app = app;
            this.width = width;
            this.height = height;
        }

        void rebind(int slot, boolean main, String app) {
            if (stopped) return;
            report(System.nanoTime(), "rebind");
            this.slot = slot;
            this.main = main;
            this.app = app;
            // The next app must not inherit the previous app's cadence or gesture baseline.
            moves.lastNs = 0L;
            touching = false;
        }

        void surfaceAttached() {
            if (stopped) return;
            surfaceConnected = true;
            bufferReady = false;
            surfaceAttachments++;
        }

        void surfaceDetached() {
            if (stopped) return;
            if (surfaceConnected) surfaceLosses++;
            surfaceConnected = bufferReady = false;
        }

        void bufferAvailable(long waitMs) {
            if (stopped) return;
            bufferReady = true;
            bufferWait.add(waitMs * 1_000_000L);
            Log.i(TAG, "session=" + session + " event=buffer-ready renderer=SurfaceView slot=" + slot
                    + " display=" + display.getDisplayId() + " app=" + app + " waitMs=" + waitMs);
        }

        void snapshotFinished(long elapsedMs, int result) {
            if (stopped) return;
            snapshots.add(elapsedMs * 1_000_000L);
            if (result != PixelCopy.SUCCESS) snapshotFailures++;
        }

        void forwarded(MotionEvent event, long receivedUptimeMs, long submitStartNs, boolean success) {
            if (stopped) return;
            long now = System.nanoTime();
            events++;
            inputSamples += event.getHistorySize() + 1;
            if (!success) submitFailures++;
            eventAge.add(Math.max(0L, receivedUptimeMs - event.getEventTime()) * 1_000_000L);
            long oldest = event.getHistorySize() == 0 ? event.getEventTime() : event.getHistoricalEventTime(0);
            oldestSampleAge.add(Math.max(0L, receivedUptimeMs - oldest) * 1_000_000L);
            submit.add(now - submitStartNs);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touching = true;
                touchStartNs = now;
                moves.lastNs = 0L;
            } else if (action == MotionEvent.ACTION_MOVE) {
                moves.record(now);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                endTouch(now);
            }
        }

        void cancelTouch() { endTouch(System.nanoTime()); }

        private void endTouch(long now) {
            if (touching) touchDurationNs += now - touchStartNs;
            touching = false;
            moves.lastNs = 0L;
        }

        private void report(long now, String reason) {
            double seconds = Math.max(1L, now - startNs) / 1_000_000_000d;
            if (touching) {
                touchDurationNs += now - touchStartNs;
                touchStartNs = now;
            }
            Log.i(TAG, "session=" + session + " phase=" + phase + " reason=" + reason
                    + " slot=" + slot + " role=" + (main ? "main" : "side")
                    + " display=" + display.getDisplayId() + " app=" + app
                    + " bufferSize=" + width + "x" + height
                    + " reportedHz=" + display.getRefreshRate()
                    + " windowMs=" + rounded(seconds * 1000)
                    + " renderer=SurfaceView contentFps=unavailable"
                    + " surfaceConnected=" + surfaceConnected + " bufferReady=" + bufferReady
                    + " surfaceAttachments=" + surfaceAttachments + " surfaceLosses=" + surfaceLosses
                    + " firstBufferWaitMs=" + bufferWait.summary()
                    + " snapshotCopyMs=" + snapshots.summary() + " snapshotFailures=" + snapshotFailures
                    + " touchActiveMs=" + rounded(touchDurationNs / 1_000_000d)
                    + " touchEvents=" + events + " inputSamples=" + inputSamples
                    + " moveGapMs=" + moves.gaps.summary()
                    + " eventAgeAtHostMs=" + eventAge.summary()
                    + " oldestSampleAgeAtHostMs=" + oldestSampleAge.summary()
                    + " forwardSubmitMs=" + submit.summary() + " submitFailures=" + submitFailures);
            moves.clearWindow();
            eventAge.clear();
            oldestSampleAge.clear();
            submit.clear();
            bufferWait.clear();
            snapshots.clear();
            surfaceAttachments = surfaceLosses = snapshotFailures = events = inputSamples = submitFailures = 0;
            touchDurationNs = 0L;
            startNs = now;
        }
    }

    private static final class Cadence {
        final Samples gaps = new Samples();
        long lastNs;
        int count;

        void record(long now) {
            count++;
            if (lastNs > 0 && now > lastNs) gaps.add(now - lastNs);
            lastNs = now;
        }

        double quietMs(long now) { return lastNs == 0 ? -1d : rounded((now - lastNs) / 1_000_000d); }
        void clearWindow() { count = 0; gaps.clear(); }
    }

    private static final class Samples {
        private final int[] buckets = new int[258];
        private int count;
        private long totalNs;
        private long maxNs;

        void add(long ns) {
            count++;
            totalNs += ns;
            maxNs = Math.max(maxNs, ns);
            buckets[(int) Math.min(buckets.length - 1, (ns + 499_999L) / 500_000L)]++;
        }

        String summary() {
            if (count == 0) return "{n=0}";
            return "{n=" + count + ",avg=" + rounded(totalNs / (count * 1_000_000d))
                    + ",p50=" + percentile(0.5) + ",p95=" + percentile(0.95)
                    + ",max=" + rounded(maxNs / 1_000_000d) + "}";
        }

        private String percentile(double fraction) {
            int target = (int) Math.ceil(count * fraction);
            int cumulative = 0;
            for (int i = 0; i < buckets.length; i++) {
                cumulative += buckets[i];
                if (cumulative >= target) return i == buckets.length - 1 ? ">128" : Double.toString(i * 0.5);
            }
            return "n/a";
        }

        void clear() {
            Arrays.fill(buckets, 0);
            count = 0;
            totalNs = maxNs = 0L;
        }
    }

    private static double rounded(double value) { return Math.round(value * 100d) / 100d; }
}
