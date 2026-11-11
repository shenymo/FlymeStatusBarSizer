package com.example.flymestatusbarsizer.feature.assistant;

/** Tracks one touchscreen pointer, signed edge displacement, and direction. */
final class AssistantGestureState {
    static final int HORIZONTAL = 0;
    static final int UP = 1;
    static final int DOWN = 2;
    private boolean tracking;
    private boolean claimed;
    private float startX, startY, dx, dy, backwardLimit;
    private AssistantGestureGeometry geometry;
    private int inwardDirection;

    void begin(float x, float y, float distance, float backwardLimit, boolean leftEdge,
            float upAngleDegrees, float downAngleDegrees, float minVertical) {
        inwardDirection = leftEdge ? 1 : -1;
        tracking = true;
        claimed = false;
        startX = x;
        startY = y;
        dx = dy = 0;
        this.backwardLimit = backwardLimit;
        geometry = new AssistantGestureGeometry(distance, upAngleDegrees, downAngleDegrees,
                minVertical);
    }

    void move(float x, float y, int pointerCount) {
        dx = (x - startX) * inwardDirection;
        dy = y - startY;
        if (pointerCount != 1 || !Float.isFinite(dx) || !Float.isFinite(dy)
                || dx < -backwardLimit) cancel();
    }

    boolean ready() {
        return tracking && !claimed && geometry.ready(dx, dy);
    }

    int direction() {
        return dx <= 0f ? HORIZONTAL : geometry.direction(dx, dy);
    }

    float startY() { return startY; }
    float currentY() { return startY + dy; }
    float inwardDistance() { return dx; }
    float verticalDelta() { return dy; }
    float travelDistance() { return (float) Math.hypot(dx, dy); }

    void claim() { claimed = true; tracking = false; }
    void cancel() { tracking = false; }
}
