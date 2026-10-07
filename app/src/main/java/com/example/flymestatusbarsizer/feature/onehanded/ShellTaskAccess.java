package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.Map;

/** Uses the existing organizer; never registers another organizer or reparents a system task. */
final class ShellTaskAccess implements TaskScaleController.Backend {
    private final Context context;
    private final Object transitions;
    private final Object taskLock;
    private final SparseArray<?> tasks;
    private final Method isIdle;
    private final Method isSameSurface;

    ShellTaskAccess(Context context, Object transitions) throws ReflectiveOperationException {
        this.context = context;
        this.transitions = transitions;
        Object organizer = ReflectUtils.getField(transitions, "mOrganizer");
        taskLock = ReflectUtils.getField(organizer, "mLock");
        Object taskMap = ReflectUtils.getField(organizer, "mTasks");
        if (taskLock == null || !(taskMap instanceof SparseArray)) {
            throw new IllegalStateException("Shell task organizer unavailable");
        }
        tasks = (SparseArray<?>) taskMap;
        isIdle = transitions.getClass().getMethod("isIdle");
        isIdle.setAccessible(true);
        isSameSurface = SurfaceControl.class.getDeclaredMethod("isSameSurface", SurfaceControl.class);
        isSameSurface.setAccessible(true);
    }

    @Override public TaskScaleTarget focusedTask() {
        synchronized (taskLock) {
            for (int i = 0; i < tasks.size(); i++) {
                Object appeared = tasks.valueAt(i);
                Object info = ReflectUtils.invokeNoArg(appeared, "getTaskInfo");
                if (ReflectUtils.getBooleanField(info, "isFocused", false)
                        && ReflectUtils.getIntField(info, "displayId", -1) == 0) {
                    return snapshot(appeared);
                }
            }
        }
        return null;
    }

    private TaskScaleTarget snapshot(Object appeared) {
        Object info = ReflectUtils.invokeNoArg(appeared, "getTaskInfo");
        Object surface = ReflectUtils.invokeNoArg(appeared, "getLeash");
        Object token = ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "token"), "asBinder");
        Object configuration = ReflectUtils.getField(info, "configuration");
        Object window = ReflectUtils.getField(configuration, "windowConfiguration");
        Object bounds = ReflectUtils.invokeNoArg(window, "getBounds");
        Object position = ReflectUtils.getField(info, "positionInParent");
        if (!(surface instanceof SurfaceControl) || !((SurfaceControl) surface).isValid()
                || token == null || !(bounds instanceof Rect) || !(position instanceof Point)) return null;
        int mode = ReflectUtils.invokeNoArgInt(window, "getWindowingMode", -1);
        int type = ReflectUtils.invokeNoArgInt(window, "getActivityType", -1);
        Rect rect = (Rect) bounds;
        boolean eligible = mode == 1 && type == 1 && !rect.isEmpty() && rect.height() > rect.width()
                && ReflectUtils.getIntField(info, "displayId", -1) == 0
                && ReflectUtils.getIntField(info, "parentTaskId", -2) == -1
                && ReflectUtils.getBooleanField(info, "isVisible", false)
                && ReflectUtils.getBooleanField(info, "isFocused", false);
        return new TaskScaleTarget(ReflectUtils.getIntField(info, "taskId", -1), token, surface,
                rect, (Point) position, eligible);
    }

    @Override public boolean allowed() { return OneHandedTaskHooks.environmentAllowed(context); }

    @Override public boolean idle() {
        try {
            // onTransitionFinished is dispatched BEFORE mFinishT is applied on this Flyme build.
            Object known = ReflectUtils.getField(transitions, "mKnownTransitions");
            return Boolean.TRUE.equals(isIdle.invoke(transitions))
                    && known instanceof Map && ((Map<?, ?>) known).isEmpty()
                    && OneHandedTaskHooks.backAnimationIdle();
        } catch (ReflectiveOperationException e) { return false; }
    }

    @Override public boolean sameSurface(TaskScaleTarget a, TaskScaleTarget b) {
        if (a == null || b == null) return false;
        if (a.surface == b.surface) return true;
        try { return Boolean.TRUE.equals(isSameSurface.invoke(a.surface, b.surface)); }
        catch (ReflectiveOperationException e) { return false; }
    }

    @Override public Rect usableBounds(TaskScaleTarget target) {
        WindowMetrics metrics = context.getSystemService(WindowManager.class).getMaximumWindowMetrics();
        Rect bounds = new Rect(metrics.getBounds());
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        bounds.inset(bars.left, bars.top, bars.right, bars.bottom);
        return bounds;
    }

    @Override public void transform(TaskScaleTarget target, TaskScaleLayout layout) {
        applyTransform(target, layout, null);
    }

    @Override public void transformAndCommit(TaskScaleTarget target, TaskScaleLayout layout, Runnable committed) {
        applyTransform(target, layout, committed);
    }

    private void applyTransform(TaskScaleTarget target, TaskScaleLayout layout, Runnable committed) {
        TaskScaleTarget current = focusedTask();
        if (!target.sameTask(current) || !current.eligible || !sameSurface(target, current)) {
            throw new IllegalStateException("Task changed before surface transaction");
        }
        SurfaceControl surface = (SurfaceControl) current.surface;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            transaction.setScale(surface, layout.scale, layout.scale);
            transaction.setPosition(surface,
                    current.position.x + current.bounds.width() * (1f - layout.scale),
                    current.position.y + current.bounds.height() * (1f - layout.scale) + layout.offsetY);
            if (committed != null) transaction.addTransactionCommittedListener(Runnable::run, committed::run);
            transaction.apply();
        }
    }

    @Override public void restore(TaskScaleTarget target) {
        TaskScaleTarget current;
        synchronized (taskLock) { current = snapshot(tasks.get(target.taskId)); }
        // Never mutate a new surface/task that has reused the old task id.
        if (current != null && (!target.sameTask(current) || !sameSurface(target, current))) return;
        SurfaceControl surface = (SurfaceControl) target.surface;
        if (!surface.isValid()) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            transaction.setScale(surface, 1f, 1f);
            // A switch to freeform/PiP already supplies its own new position. Do not overwrite it.
            if (current == null || (current.bounds.equals(target.bounds)
                    && current.position.equals(target.position))) {
                transaction.setPosition(surface, target.position.x, target.position.y);
            }
            transaction.apply();
        }
    }
}
