package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;

/** All state and surface writes belong to the Shell executor's looper. */
final class TaskScaleController {
    static final float SCALE = 0.7f;
    private static final long WAIT_TIMEOUT_MS = 2500;
    private static final long CHECK_INTERVAL_MS = 120;

    interface Backend {
        TaskScaleTarget focusedTask();
        boolean allowed();
        boolean idle();
        boolean sameSurface(TaskScaleTarget a, TaskScaleTarget b);
        void transform(TaskScaleTarget target, float scale) throws Exception;
        void restore(TaskScaleTarget target) throws Exception;
    }

    interface Overlay {
        void show(TaskScaleTarget target, float scale, boolean animating) throws Exception;
        void hide();
    }

    private final Handler handler;
    private final Backend backend;
    private final Overlay overlay;
    private final Runnable check = this::check;
    private TaskScaleTarget target;
    private ValueAnimator animator;
    private Runnable success;
    private long deadline;
    private long generation;
    private boolean suspended;
    private boolean transformed;
    private boolean exiting;
    private float currentScale = 1f;
    private volatile boolean active;
    private volatile boolean recovering;

    TaskScaleController(Handler handler, Backend backend, Overlay overlay) {
        this.handler = handler;
        this.backend = backend;
        this.overlay = overlay;
    }

    boolean canTrigger() {
        if (recovering || !backend.allowed()) return false;
        TaskScaleTarget focused = backend.focusedTask();
        return focused != null && focused.eligible;
    }

    void toggle(Runnable onSuccess) {
        // Capture before posting: never scale a different app that wins focus while we wait.
        TaskScaleTarget requested = backend.focusedTask();
        dispatch(() -> {
            if (recovering) return;
            if (active) {
                exit(true, "gesture");
                return;
            }
            if (!backend.allowed() || requested == null || !requested.eligible) return;
            target = requested;
            active = true;
            suspended = true;
            exiting = false;
            success = onSuccess;
            generation++;
            deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS;
            schedule(32);
        });
    }

    void dispatch(Runnable action) {
        if (Looper.myLooper() == handler.getLooper()) action.run();
        else handler.post(action);
    }

    void refresh() { dispatch(() -> { if (active) schedule(0); }); }

    void stop(String reason) { dispatch(() -> exit(false, reason)); }

    void taskVanished(int taskId) {
        dispatch(() -> { if (target != null && target.taskId == taskId) exit(false, "task vanished"); });
    }

    void suspend() {
        dispatch(() -> {
            if (!active || suspended) return;
            if (exiting) { exit(false, "transition during exit"); return; }
            cancelAnimation();
            // Run before Shell's start transaction. Shell owns the surface throughout transition.
            if (!restore()) { exit(false, "restore before transition failed"); return; }
            overlay.hide();
            suspended = true;
            deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS;
            schedule(32);
        });
    }

    private void check() {
        if (!active || exiting) return;
        TaskScaleTarget focused = backend.focusedTask();
        if (!backend.allowed() || !target.sameTask(focused) || !focused.eligible
                || !target.bounds.equals(focused.bounds)) {
            exit(false, "environment or task changed");
            return;
        }
        if (!backend.idle()) {
            if (!suspended) suspend();
            if (SystemClock.uptimeMillis() >= deadline) exit(false, "transition timeout");
            else schedule(32);
            return;
        }
        if (suspended) {
            // Reacquire after finish transactions, including merged/aborted transitions.
            target = focused;
            suspended = false;
            animateTo(SCALE, false);
        } else if (!backend.sameSurface(target, focused) || !target.position.equals(focused.position)) {
            exit(false, "task surface changed");
            return;
        }
        schedule(CHECK_INTERVAL_MS);
    }

    private void animateTo(float end, boolean exit) {
        cancelAnimation();
        final long epoch = generation;
        try {
            overlay.show(target, currentScale, true);
        } catch (Exception e) {
            fail(e);
            return;
        }
        ValueAnimator next = ValueAnimator.ofFloat(currentScale, end);
        animator = next;
        next.setDuration(200);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(value -> {
            if (animator != next || epoch != generation) return;
            // A transition can start between scheduled checks; never fight its animation.
            if (!backend.idle()) { suspend(); return; }
            if (!backend.allowed()) { exit(false, "environment changed during animation"); return; }
            try {
                currentScale = (float) value.getAnimatedValue();
                transformed = true; // A partly failed transaction still needs cleanup.
                backend.transform(target, currentScale);
                overlay.show(target, currentScale, true);
            } catch (Exception e) { fail(e); }
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next || epoch != generation) return;
                animator = null;
                if (exit) { exit(false, "exit animation finished"); return; }
                try {
                    overlay.show(target, SCALE, false);
                    Runnable callback = success;
                    success = null;
                    if (callback != null) callback.run();
                    Log.i(OneHandedTaskHooks.TAG, "Scaled task=" + target.taskId + " scale=" + SCALE);
                } catch (Exception e) { fail(e); }
            }
        });
        next.start();
    }

    void exit(boolean animate, String reason) {
        if (!active) { overlay.hide(); return; }
        if (animate && !suspended && !exiting && backend.idle() && backend.allowed()) {
            exiting = true;
            handler.removeCallbacks(check);
            animateTo(1f, true);
            return;
        }
        generation++;
        cancelAnimation();
        handler.removeCallbacks(check);
        boolean restored = restore();
        overlay.hide();
        if (!restored) {
            // Keep the original target until cleanup succeeds. Do not abandon a scaled task.
            recovering = true;
            exiting = true;
            success = null;
            long cleanupGeneration = generation;
            handler.postDelayed(() -> {
                if (active && recovering && generation == cleanupGeneration) exit(false, reason);
            }, 200);
            return;
        }
        Log.i(OneHandedTaskHooks.TAG, "Restored task=" + target.taskId + " reason=" + reason);
        active = false;
        target = null;
        success = null;
        suspended = false;
        exiting = false;
        recovering = false;
    }

    private boolean restore() {
        if (transformed && target != null) {
            try { backend.restore(target); }
            catch (Exception e) {
                Log.w(OneHandedTaskHooks.TAG, "Task restore failed; will retry", e);
                return false;
            }
        }
        transformed = false;
        currentScale = 1f;
        return true;
    }

    private void fail(Exception e) {
        Log.w(OneHandedTaskHooks.TAG, "Task scaling failed", e);
        exit(false, "operation failed");
    }

    private void cancelAnimation() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) previous.cancel();
    }

    private void schedule(long delay) {
        handler.removeCallbacks(check);
        handler.postDelayed(check, delay);
    }
}
