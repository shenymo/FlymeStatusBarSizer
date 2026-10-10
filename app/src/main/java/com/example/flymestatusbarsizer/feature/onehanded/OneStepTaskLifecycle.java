package com.example.flymestatusbarsizer.feature.onehanded;

import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.util.concurrent.atomic.AtomicBoolean;

/** Reconciles WM visibility with the disjoint surfaces of a live workspace. */
final class OneStepTaskLifecycle {
    private static final String TAG = "FlymeOneStepLifecycle";
    private static final AtomicBoolean REPORTED_ERROR = new AtomicBoolean();
    private static boolean installed;

    private OneStepTaskLifecycle() {}

    static void install(FlymeStatusBarSizer module, ClassLoader loader) throws Exception {
        if (installed) return;
        Class<?> fragment = Class.forName("com.android.server.wm.TaskFragment", false, loader);
        Class<?> activity = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        module.intercept(OneStepReflection.method(fragment, "getVisibility", activity), chain -> {
            Object result = chain.proceed();
            // Flyme: 0 = visible, 1 = behind translucent, 2 = invisible. Never revive
            // an invisible task or alter occlusion between activities inside a task.
            if (!Integer.valueOf(1).equals(result) || !OneStepImePolicy.hasResumedWorkspace()) return result;
            try {
                Object task = chain.getThisObject();
                Object service = OneStepReflection.get(task, "mWmService");
                synchronized (OneStepReflection.get(service, "mGlobalLock")) {
                    if (onlyWorkspaceAbove(task)) return 0;
                }
            } catch (Exception error) { unavailable(error); }
            return result;
        });
        installed = true;
        Log.i(TAG, "Workspace multi-resume visibility policy installed");
    }

    private static boolean onlyWorkspaceAbove(Object task) throws ReflectiveOperationException {
        Object workspace = OneStepImePolicy.resumedWorkspace(task);
        if (workspace == null) return false;
        Object parent = OneStepReflection.call(task, "getParent");
        if (parent == null || parent != OneStepReflection.call(workspace, "getParent")) return false;
        Object display = OneStepReflection.call(task, "getDisplayContent");
        if (display == null || Boolean.TRUE.equals(OneStepReflection.call(display, "isSleeping"))
                || Boolean.TRUE.equals(OneStepReflection.call(display, "isKeyguardLocked"))) return false;

        boolean foundTask = false;
        int count = ((Number) OneStepReflection.call(parent, "getChildCount")).intValue();
        for (int i = count - 1; i >= 0; i--) {
            Object sibling = OneStepReflection.call(parent, "getChildAt", new Class<?>[]{int.class}, i);
            if (sibling == workspace) return foundTask;
            if (sibling == task) { foundTask = true; continue; }
            if (foundTask) continue;
            // The workspace's opaque Activity must remain below this task. Every
            // running task above it must belong to the same workspace, including HOME.
            Object other = OneStepReflection.call(sibling, "asTask");
            if (other == null) return false;
            if (OneStepReflection.call(other, "topRunningActivity") == null) continue;
            if (OneStepImePolicy.resumedWorkspace(other) != workspace) return false;
        }
        return false;
    }

    static void refresh(Object service) {
        try {
            Object root = OneStepReflection.get(service, "mRoot");
            OneStepReflection.call(root, "ensureActivitiesVisible");
            OneStepReflection.call(root, "resumeFocusedTasksTopActivities");
        } catch (Exception error) { unavailable(error); }
    }

    private static void unavailable(Exception error) {
        if (REPORTED_ERROR.compareAndSet(false, true)) Log.w(TAG, "Cannot update workspace lifecycle", error);
    }
}
