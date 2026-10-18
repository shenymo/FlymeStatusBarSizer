package com.example.flymestatusbarsizer.feature.assistant;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Maps native 0..width animation progress to either screen edge, only in the global host. */
final class AssistantPanelMotion {
    private final View panel, content;
    private final Field panelX, dragEnabled;
    private final Method setPanelX, startDrag, resetTouch, openPanel, closePanel, cancelAnimation;
    private final Object interpolator;
    private final int slop, minVelocity, maxVelocity, duration;
    private VelocityTracker velocity;
    private long downTime = Long.MIN_VALUE;
    private long lastMovementTime;
    private int pointerId = -1;
    private float downX, downY, dragStartX, dragStartOffset;
    private float lastX;
    private int direction;
    private boolean tracking, dragging, rejected, settlingTouch, closingLeft;
    private float closeStartX, closeStartOffset;

    AssistantPanelMotion(View panel, View content, boolean fromLeft) throws ReflectiveOperationException {
        this.panel = panel;
        this.content = content;
        direction = fromLeft ? -1 : 1;
        Class<?> type = panel.getClass();
        panelX = AssistantReflection.field(type, "mPanelX");
        dragEnabled = AssistantReflection.field(type, "mPanelDragEnable");
        setPanelX = AssistantReflection.method(type, "setPanelX", int.class);
        startDrag = AssistantReflection.method(type, "startDrag");
        resetTouch = AssistantReflection.method(type, "resetTouchState");
        openPanel = AssistantReflection.method(type, "openPanel", int.class);
        closePanel = AssistantReflection.method(type, "closePanel", int.class);
        interpolator = AssistantReflection.get(panel, "mSlidingPanelLayoutInterpolator");
        cancelAnimation = AssistantReflection.method(interpolator.getClass(), "cancelAll");
        ViewConfiguration config = ViewConfiguration.get(panel.getContext());
        slop = config.getScaledPagingTouchSlop();
        minVelocity = config.getScaledMinimumFlingVelocity();
        maxVelocity = config.getScaledMaximumFlingVelocity();
        duration = Math.max(1, (Integer) AssistantReflection.get(panel, "animateTime"));
    }

    private int width() { return panel.getMeasuredWidth(); }

    private float offset() throws IllegalAccessException {
        float x = Math.max(0, Math.min(width(), panelX.getInt(panel)));
        if (closingLeft && closeStartX > 0) {
            return -width() + (closeStartOffset + width()) * Math.min(1, x / closeStartX);
        }
        return direction * (width() - x);
    }

    void positionChanged() throws IllegalAccessException {
        // Keep native layout and content layout direction intact; do not mirror cards or text.
        content.setTranslationX(offset() - content.getLeft());
    }

    void opened() throws IllegalAccessException {
        closingLeft = false;
        direction = -1;
        positionChanged();
    }

    void prepareClose() throws ReflectiveOperationException {
        if (settlingTouch || closingLeft) return;
        closeStartOffset = offset();
        closeStartX = panelX.getInt(panel);
        direction = -1;
        closingLeft = true;
        clearTouch();
        resetTouch.invoke(panel);
        // Interpolate from the current position if Back interrupts a right-side animation.
        positionChanged();
    }

    boolean intercept(MotionEvent event) throws ReflectiveOperationException {
        if (closingLeft) return true;
        if (!dragEnabled.getBoolean(panel)) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(event);
        if (!tracking) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
            if (dragging) finish(false);
            else clearTouch();
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && !dragging && !rejected) {
            int index = event.findPointerIndex(pointerId);
            if (index < 0 || event.getPointerCount() != 1) { clearTouch(); return false; }
            float dx = event.getX(index) - downX;
            float dy = event.getY(index) - downY;
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) rejected = true;
            if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                cancelAnimation.invoke(interpolator);
                dragStartOffset = offset();
                dragStartX = event.getX(index);
                dragging = true;
                startDrag.invoke(panel);
            }
        }
        if (action == MotionEvent.ACTION_UP && !dragging) clearTouch();
        return dragging;
    }

    boolean touch(MotionEvent event) throws ReflectiveOperationException {
        if (closingLeft) return true;
        if (!dragEnabled.getBoolean(panel)) {
            if (dragging) finish(false);
            else clearTouch();
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(event);
        if (!tracking) return true;
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || event.getPointerCount() != 1 || event.findPointerIndex(pointerId) < 0) {
            if (dragging) finish(false);
            else clearTouch();
            return true;
        }
        velocity.addMovement(event);
        float x = event.getX(event.findPointerIndex(pointerId));
        if (x != lastX) { lastMovementTime = event.getEventTime(); lastX = x; }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!dragging) intercept(event);
            if (dragging) move(event);
        } else if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                move(event);
                velocity.computeCurrentVelocity(1000, maxVelocity);
                float speed = velocity.getXVelocity(pointerId);
                float offset = offset();
                boolean fling = event.getEventTime() - lastMovementTime < 120
                        && Math.abs(speed) >= minVelocity;
                boolean close = fling ? Math.abs(offset) > slop && speed * offset > 0
                        : Math.abs(offset) >= width() * 0.5f;
                finish(close);
            } else clearTouch();
        }
        return true;
    }

    private void begin(MotionEvent event) {
        // The same DOWN can visit both interception and touch handling.
        if (tracking && downTime == event.getDownTime()) return;
        clearTouch();
        if (width() <= 0 || event.getPointerCount() != 1) return;
        tracking = true;
        downTime = event.getDownTime();
        pointerId = event.getPointerId(0);
        downX = event.getX();
        downY = event.getY();
        lastX = downX;
        lastMovementTime = event.getEventTime();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
    }

    private void move(MotionEvent event) throws ReflectiveOperationException {
        float value = dragStartOffset + event.getX(event.findPointerIndex(pointerId)) - dragStartX;
        value = Math.max(-width(), Math.min(width(), value));
        if (value != 0) direction = value < 0 ? -1 : 1;
        setPanelX.invoke(panel, Math.round(width() - Math.abs(value)));
    }

    private void finish(boolean close) throws ReflectiveOperationException {
        // Keep native closing/opening listeners and animator completion/host restoration.
        settlingTouch = true;
        try { (close ? closePanel : openPanel).invoke(panel, duration); }
        finally {
            settlingTouch = false;
            clearTouch();
            resetTouch.invoke(panel);
        }
    }

    void closeFromOutside() throws ReflectiveOperationException {
        if (closingLeft) return;
        prepareClose();
        closePanel.invoke(panel, duration);
    }

    private void clearTouch() {
        tracking = dragging = rejected = false;
        pointerId = -1;
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    void dispose() throws ReflectiveOperationException {
        clearTouch();
        cancelAnimation.invoke(interpolator);
        resetTouch.invoke(panel);
        // The session has disabled global hooks. closeOverlay(0) alone is insufficient:
        // after onPanelClosed it is a no-op, leaving the right-side translation on desktop.
        setPanelX.invoke(panel, 0);
    }
}
