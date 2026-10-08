package com.example.flymestatusbarsizer.feature.onehanded;

import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** OneStep's display/task/input operations, executed under SystemUI's existing permissions. */
final class OneStepTaskAccess {
    private final Context context;
    private final Object service;
    private final Object input;
    private final Object windows;
    private final Method roots;
    private final Method move;
    private final Method launch;
    private final Method focus;
    private final Method focusTask;
    private final Method inject;
    private final Method displayId;
    private final Method imePolicy;
    private final Method getImePolicy;
    private final TaskScaleRecentTasks recent;

    OneStepTaskAccess(Context context) throws ReflectiveOperationException {
        this.context = context;
        service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Class<?> tasks = Class.forName("android.app.IActivityTaskManager");
        roots = tasks.getMethod("getAllRootTaskInfos");
        move = tasks.getMethod("moveRootTaskToDisplay", int.class, int.class);
        launch = tasks.getMethod("startActivityFromRecents", int.class, Bundle.class);
        focus = tasks.getMethod("focusTopTask", int.class);
        focusTask = tasks.getMethod("setFocusedTask", int.class);
        Class<?> inputClass;
        try { inputClass = Class.forName("android.hardware.input.InputManagerGlobal"); }
        catch (ClassNotFoundException e) { inputClass = Class.forName("android.hardware.input.InputManager"); }
        input = inputClass.getMethod("getInstance").invoke(null);
        inject = inputClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        displayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
        displayId.setAccessible(true);
        windows = Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService").invoke(null);
        Class<?> wm = Class.forName("android.view.IWindowManager");
        imePolicy = wm.getMethod("setDisplayImePolicy", int.class, int.class);
        getImePolicy = wm.getMethod("getDisplayImePolicy", int.class);
        recent = new TaskScaleRecentTasks(context);
    }

    List<?> roots() throws ReflectiveOperationException {
        Object value = roots.invoke(service);
        if (!(value instanceof List)) throw new IllegalStateException("Root tasks unavailable");
        return (List<?>) value;
    }

    static int taskId(Object info) { return ReflectUtils.getIntField(info, "taskId", -1); }
    static int display(Object info) { return ReflectUtils.getIntField(info, "displayId", -1); }
    static Object token(Object info) {
        return ReflectUtils.invokeNoArg(ReflectUtils.getField(info, "token"), "asBinder");
    }

    static boolean application(Object info) {
        Object config = ReflectUtils.getField(ReflectUtils.getField(info, "configuration"), "windowConfiguration");
        return ReflectUtils.invokeNoArgInt(config, "getActivityType", -1) == 1
                && ReflectUtils.invokeNoArgInt(config, "getWindowingMode", -1) == 1;
    }

    RecentTaskCard focusedTask() throws ReflectiveOperationException {
        for (Object root : roots()) {
            if (display(root) == 0 && application(root)
                    && ReflectUtils.getBooleanField(root, "isFocused", false)) {
                return TaskScaleRecentTasks.candidate(root, ReflectUtils.getIntField(root, "userId", -1));
            }
        }
        return null;
    }

    List<RecentTaskCard> candidates() throws ReflectiveOperationException { return recent.recentTasks(); }

    void attach(RecentTaskCard requested, int targetDisplay) throws ReflectiveOperationException {
        // A label or task id alone is insufficient: revalidate the task's binder identity.
        RecentTaskCard valid = null;
        for (RecentTaskCard card : candidates()) {
            if (requested.sameTask(card)) { valid = card; break; }
        }
        if (valid == null) throw new IllegalStateException("Selected task no longer exists");
        for (Object root : roots()) {
            if (taskId(root) == valid.taskId && valid.token.equals(token(root))) {
                move.invoke(service, valid.taskId, targetDisplay);
                focus(targetDisplay);
                return;
            }
        }
        ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
        options.setLaunchDisplayId(targetDisplay);
        Object result = launch.invoke(service, valid.taskId, options.toBundle());
        if (!(result instanceof Number) || ((Number) result).intValue() < 0) {
            throw new IllegalStateException("Cannot launch recent task on virtual display");
        }
    }

    void configureDisplay(int id) throws ReflectiveOperationException {
        // DISPLAY_IME_POLICY_LOCAL: the keyboard is rendered and touched inside the same pane.
        imePolicy.invoke(windows, id, 0);
        if (((Number) getImePolicy.invoke(windows, id)).intValue() != 0) {
            throw new IllegalStateException("Local display keyboard policy was rejected");
        }
        try {
            Class.forName("android.view.IWindowManager")
                    .getMethod("setIgnoreOrientationRequest", int.class, boolean.class)
                    .invoke(windows, id, true);
        } catch (NoSuchMethodException ignored) {
            // The fixed virtual display still uses its own logical dimensions for touch mapping.
        }
    }

    void focus(int id) throws ReflectiveOperationException { focus.invoke(service, id); }

    int defaultFocusedTaskId() throws ReflectiveOperationException {
        for (Object root : roots()) {
            if (display(root) == 0 && ReflectUtils.getBooleanField(root, "isFocused", false)) return taskId(root);
        }
        return -1;
    }

    void focusTask(int id) throws ReflectiveOperationException { focusTask.invoke(service, id); }

    void motion(int id, MotionEvent source, int width, int height, int viewWidth, int viewHeight)
            throws ReflectiveOperationException {
        if (viewWidth <= 0 || viewHeight <= 0 || id <= 0) return;
        MotionEvent event = MotionEvent.obtain(source);
        try {
            Matrix transform = new Matrix();
            transform.setScale(width / (float) viewWidth, height / (float) viewHeight);
            event.transform(transform);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            displayId.invoke(event, id);
            if (Boolean.FALSE.equals(inject.invoke(input, event, 0))) {
                throw new IllegalStateException("Virtual display rejected touch input");
            }
        } finally { event.recycle(); }
    }

    void back(int id) throws ReflectiveOperationException {
        focus(id);
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
            displayId.invoke(event, id);
            if (Boolean.FALSE.equals(inject.invoke(input, event, 0))) {
                throw new IllegalStateException("Virtual display rejected back key");
            }
        }
    }

    void restoreDisplay(int id) throws ReflectiveOperationException {
        // Include tasks opened by the hosted app, not just the original task moved into this pane.
        for (Object root : new ArrayList<>(roots())) {
            if (display(root) == id) move.invoke(service, taskId(root), 0);
        }
    }
}
