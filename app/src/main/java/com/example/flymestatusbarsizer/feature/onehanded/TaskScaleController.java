package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
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
    private static final long SWAP_TIMEOUT_MS = 5000;

    interface Backend {
        TaskScaleTarget focusedTask();
        boolean allowed();
        boolean idle();
        boolean sameSurface(TaskScaleTarget a, TaskScaleTarget b);
        default Rect usableBounds(TaskScaleTarget target) { return new Rect(target.bounds); }
        void transform(TaskScaleTarget target, TaskScaleLayout layout) throws Exception;
        default void transformAndCommit(TaskScaleTarget target, TaskScaleLayout layout, Runnable committed) throws Exception {
            transform(target, layout);
            committed.run();
        }
        void restore(TaskScaleTarget target) throws Exception;
    }

    interface Overlay {
        void show(TaskScaleTarget target, TaskScaleLayout layout, boolean animating) throws Exception;
        void hide();
        default void pause() { hide(); }
        void beginSwap(TaskScaleTarget current, RecentTaskCard selected, Runnable covered) throws Exception;
        default boolean swapAnimationFinished() { return true; }
        void endSwap(TaskScaleTarget current, TaskScaleLayout layout) throws Exception;
        default void moveSwap(TaskScaleTarget current, TaskScaleLayout layout) throws Exception {}
        default void tasksChanged() {}
    }

    interface TaskLauncher { boolean launch() throws Exception; }

    private final Handler handler;
    private final Backend backend;
    private final Overlay overlay;
    private final Runnable check = this::check;
    private TaskScaleTarget target;
    private RecentTaskCard pendingTask;
    private TaskScaleTarget swapOriginal;
    private boolean swapLaunched;
    private boolean swapRollback;
    private boolean swapAwaitingCommit;
    private ValueAnimator animator;
    private ValueAnimator imeAnimator;
    private int imeTop = TaskScaleImeInsets.HIDDEN;
    private float imeHeight;
    private Rect usableBounds;
    private long layoutGeneration;
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
            try { usableBounds = backend.usableBounds(target); }
            catch (RuntimeException e) { fail(e); target = null; return; }
            imeHeight = desiredImeHeight();
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

    void imeChanged(int top) {
        dispatch(() -> {
            if (imeTop == top) return;
            imeTop = top;
            layoutGeneration++;
            cancelImeAnimation();
            if (!active || exiting || recovering) return;
            if (suspended && pendingTask == null) {
                imeHeight = desiredImeHeight();
                return;
            }
            ValueAnimator next = ValueAnimator.ofFloat(imeHeight, desiredImeHeight());
            imeAnimator = next;
            next.setDuration(220);
            next.setInterpolator(new DecelerateInterpolator());
            next.addUpdateListener(value -> {
                if (imeAnimator != next || !active) return;
                imeHeight = (float) value.getAnimatedValue();
                layoutGeneration++;
                if (!backend.allowed()) { exit(false, "environment changed during IME movement"); return; }
                try {
                    if (pendingTask != null) {
                        overlay.moveSwap(swapOriginal, layout(swapOriginal, SCALE));
                    } else if (!suspended && backend.idle()) {
                        transformed = true;
                        TaskScaleLayout frame = layout(target, currentScale);
                        backend.transform(target, frame);
                        overlay.show(target, frame, animator != null);
                    }
                } catch (Exception e) { fail(e); }
            });
            next.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (imeAnimator != next) return;
                    imeAnimator = null;
                    if (active) schedule(0);
                }
            });
            next.start();
        });
    }

    private float desiredImeHeight() {
        // Some floating/hardware IMEs report only the already excluded navigation-bar strip.
        return target == null || imeTop == TaskScaleImeInsets.HIDDEN
                || (usableBounds != null && imeTop >= usableBounds.bottom) ? 0f
                : Math.max(0, target.bounds.bottom - imeTop);
    }

    private TaskScaleLayout layout(TaskScaleTarget task, float scale) {
        return new TaskScaleLayout(task, usableBounds, scale, imeHeight);
    }

    void stop(String reason) { dispatch(() -> exit(false, reason)); }

    // Task launch itself can close system dialogs. Actual focus, shade, recents and lock events
    // still decide whether to exit; the generic broadcast is not proof the user left this mode.
    void systemDialogsClosed() { refresh(); }

    void selectTask(RecentTaskCard requested, TaskLauncher launcher) {
        dispatch(() -> {
            if (!active || recovering || suspended || exiting || animator != null || pendingTask != null
                    || !backend.allowed() || !backend.idle() || requested.matches(target)
                    || !target.sameTask(backend.focusedTask())) return;
            pendingTask = requested;
            swapOriginal = target;
            swapLaunched = false;
            swapRollback = false;
            swapAwaitingCommit = false;
            success = null;
            final long epoch = ++generation;
            deadline = SystemClock.uptimeMillis() + SWAP_TIMEOUT_MS;
            Log.i(OneHandedTaskHooks.TAG, "Preparing task swap " + target.taskId + " -> " + requested.taskId);
            try {
                // Do not restore or launch until an opaque, scaled preview has reached SF.
                overlay.beginSwap(target, requested, () -> dispatch(() -> {
                    if (!active || generation != epoch || pendingTask != requested || swapLaunched) return;
                    if (!backend.allowed() || !target.sameTask(backend.focusedTask())) {
                        exit(false, "environment changed before card swap");
                        return;
                    }
                    swapLaunched = true;
                    Log.i(OneHandedTaskHooks.TAG, "Swap cover committed; launching task=" + requested.taskId);
                    if (!restore()) { exit(false, "restore under swap cover failed"); return; }
                    suspended = true;
                    deadline = SystemClock.uptimeMillis() + SWAP_TIMEOUT_MS;
                    try { swapRollback = !launcher.launch(); }
                    catch (Exception e) {
                        Log.w(OneHandedTaskHooks.TAG, "Cannot switch to recent task", e);
                        swapRollback = true;
                    }
                    if (active) schedule(32);
                }));
            } catch (Exception e) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot prepare card swap", e);
                cancelUnlaunchedSwap();
            }
            if (active) schedule(32);
        });
    }

    void taskVanished(int taskId) {
        dispatch(() -> {
            if ((target != null && target.taskId == taskId && pendingTask == null)
                    || (pendingTask != null && pendingTask.taskId == taskId)) exit(false, "task vanished");
            else if (active) overlay.tasksChanged();
        });
    }

    void suspend() {
        dispatch(() -> {
            if (active && pendingTask != null) {
                // Keep the cover and card slots throughout Shell's own transactions.
                if (swapLaunched && transformed) {
                    generation++;
                    swapAwaitingCommit = false;
                    if (!restore()) exit(false, "restore during card swap failed");
                }
                return;
            }
            if (!active || suspended) return;
            if (exiting) { exit(false, "transition during exit"); return; }
            cancelAnimation();
            // Run before Shell's start transaction. Shell owns the surface throughout transition.
            if (!restore()) { exit(false, "restore before transition failed"); return; }
            overlay.pause();
            suspended = true;
            deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS;
            schedule(32);
        });
    }

    private void check() {
        if (!active || exiting) return;
        TaskScaleTarget focused = backend.focusedTask();
        if (!backend.allowed()) { exit(false, "environment changed"); return; }
        if (pendingTask != null) {
            checkSwap(focused);
            return;
        }
        if (!target.sameTask(focused) || !focused.eligible
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

    private void checkSwap(TaskScaleTarget focused) {
        if (SystemClock.uptimeMillis() >= deadline) {
            if (!swapLaunched) cancelUnlaunchedSwap();
            else exit(false, "task swap timeout expected=" + pendingTask.taskId
                    + " focused=" + (focused == null ? -1 : focused.taskId)
                    + " idle=" + backend.idle() + " awaitingCommit=" + swapAwaitingCommit);
            return;
        }
        if (!swapLaunched) { schedule(32); return; }
        boolean expected = swapRollback ? swapOriginal.sameTask(focused) : pendingTask.matches(focused);
        if (!backend.idle()) { suspend(); schedule(32); return; }
        if (!expected) {
            if (focused != null && !swapOriginal.sameTask(focused)) {
                exit(false, "unexpected task during card swap");
                return;
            }
        } else if (focused.eligible && !swapAwaitingCommit) {
            if (!overlay.swapAnimationFinished() || imeAnimator != null) { schedule(32); return; }
            target = focused;
            swapAwaitingCommit = true;
            currentScale = SCALE;
            transformed = true;
            long epoch = ++generation;
            long frameGeneration = layoutGeneration;
            try {
                backend.transformAndCommit(target, layout(target, SCALE), () -> dispatch(() -> {
                    if (!active || generation != epoch || pendingTask == null || !swapAwaitingCommit) return;
                    if (layoutGeneration != frameGeneration || imeAnimator != null) {
                        swapAwaitingCommit = false;
                        schedule(32);
                        return;
                    }
                    TaskScaleTarget current = backend.focusedTask();
                    if (!backend.allowed() || !target.sameTask(current) || !current.eligible
                            || !target.bounds.equals(current.bounds)) {
                        exit(false, "environment changed before swap reveal");
                        return;
                    }
                    if (!backend.idle() || !backend.sameSurface(target, current)) {
                        suspend();
                        schedule(32);
                        return;
                    }
                    clearSwap();
                    suspended = false;
                    try { overlay.endSwap(target, layout(target, SCALE)); }
                    catch (Exception e) { fail(e); return; }
                    Log.i(OneHandedTaskHooks.TAG, "Swapped main task=" + target.taskId);
                    schedule(CHECK_INTERVAL_MS);
                }));
            } catch (Exception e) { fail(e); return; }
        }
        if (pendingTask != null) schedule(32);
    }

    private void cancelUnlaunchedSwap() {
        generation++;
        clearSwap();
        if (!backend.allowed() || !backend.idle() || !target.sameTask(backend.focusedTask())) {
            exit(false, "environment changed while cancelling card swap");
            return;
        }
        try {
            // IME may have finished moving the cover while the old task stayed underneath it.
            TaskScaleLayout frame = layout(target, currentScale);
            transformed = true;
            backend.transform(target, frame);
            overlay.endSwap(target, frame);
        }
        catch (Exception e) { fail(e); }
        if (active) schedule(CHECK_INTERVAL_MS);
    }

    private void clearSwap() {
        pendingTask = null;
        swapOriginal = null;
        swapLaunched = false;
        swapRollback = false;
        swapAwaitingCommit = false;
    }

    private void animateTo(float end, boolean exit) {
        cancelAnimation();
        final long epoch = generation;
        try {
            overlay.show(target, layout(target, currentScale), true);
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
                TaskScaleLayout frame = layout(target, currentScale);
                backend.transform(target, frame);
                overlay.show(target, frame, true);
            } catch (Exception e) { fail(e); }
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next || epoch != generation) return;
                animator = null;
                if (exit) { exit(false, "exit animation finished"); return; }
                try {
                    overlay.show(target, layout(target, SCALE), false);
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
        boolean wasSwapping = pendingTask != null;
        clearSwap();
        if (!active) { overlay.hide(); return; }
        if (animate && imeHeight == 0f && !wasSwapping && !suspended && !exiting && backend.idle() && backend.allowed()) {
            exiting = true;
            handler.removeCallbacks(check);
            animateTo(1f, true);
            return;
        }
        generation++;
        cancelAnimation();
        cancelImeAnimation();
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

    private void cancelImeAnimation() {
        ValueAnimator previous = imeAnimator;
        imeAnimator = null;
        if (previous != null) previous.cancel();
    }

    private void schedule(long delay) {
        handler.removeCallbacks(check);
        handler.postDelayed(check, delay);
    }
}
