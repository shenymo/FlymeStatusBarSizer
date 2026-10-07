package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Rect;

import java.lang.reflect.Method;

/** Reads display coordinates, never the already shifted overlay window's local IME insets. */
final class TaskScaleImeInsets {
    static final int HIDDEN = Integer.MAX_VALUE;
    private final int imeId;
    private final Method peekSource, getDisplayFrame, getFrame, isVisible;

    TaskScaleImeInsets(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> state = Class.forName("android.view.InsetsState", false, loader);
        Class<?> source = Class.forName("android.view.InsetsSource", false, loader);
        imeId = source.getField("ID_IME").getInt(null);
        peekSource = state.getMethod("peekSource", int.class);
        getDisplayFrame = state.getMethod("getDisplayFrame");
        getFrame = source.getMethod("getFrame");
        isVisible = source.getMethod("isVisible");
    }

    int top(Object state) throws ReflectiveOperationException {
        Object source = peekSource.invoke(state, imeId);
        if (source == null) return HIDDEN;
        return dockedTop(Boolean.TRUE.equals(isVisible.invoke(source)),
                (Rect) getFrame.invoke(source), (Rect) getDisplayFrame.invoke(state));
    }

    static int dockedTop(boolean visible, Rect frame, Rect display) {
        // Floating/hardware keyboards reserve no bottom strip and must not lift the whole UI.
        if (!visible || frame == null || display == null || frame.isEmpty() || display.isEmpty()
                || frame.bottom < display.bottom || frame.width() < display.width() / 2
                || frame.top >= display.bottom) return HIDDEN;
        return Math.max(display.top, frame.top);
    }
}
