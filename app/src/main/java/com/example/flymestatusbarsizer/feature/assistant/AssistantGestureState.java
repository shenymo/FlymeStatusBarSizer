package com.example.flymestatusbarsizer.feature.assistant;

/** Tracks one touchscreen pointer, signed edge displacement, and direction. */
final class AssistantGestureState {
    static final int HORIZONTAL = 0;
    static final int UP = 1;
    static final int DOWN = 2;
    private boolean tracking;
    private boolean claimed;
    private float startX, startY, dx, dy, distance, backwardLimit, diagonalMin;
    private int inwardDirection;

    void begin(float x, float y, float distance, float backwardLimit, boolean leftEdge,
            float diagonalMin) {
        inwardDirection = leftEdge ? 1 : -1;
        tracking = true;
        claimed = false;
        startX = x;
        startY = y;
        dx = dy = 0;
        this.distance = distance;
        this.backwardLimit = backwardLimit;
        this.diagonalMin = diagonalMin;
    }

    void move(float x, float y, int pointerCount) {
        dx = (x - startX) * inwardDirection;
        dy = y - startY;
        if (pointerCount != 1 || !Float.isFinite(dx) || !Float.isFinite(dy)
                || dx < -backwardLimit) cancel();
    }

    boolean ready() {
        if (!tracking || claimed || dx <= 0f) return false;
        int direction = direction();
        if (direction == HORIZONTAL) return dx >= distance;
        return (float) Math.hypot(dx, dy) >= distance && dx >= distance * 0.35f;
    }

    int direction() {
        if (dx <= 0 || Math.abs(dy) < Math.max(diagonalMin, dx * 0.45f)) return HORIZONTAL;
        return dy < 0 ? UP : DOWN;
    }

    float startY() { return startY; }
    float currentY() { return startY + dy; }
    float inwardDistance() { return dx; }
    float verticalDelta() { return dy; }
    float travelDistance() { return (float) Math.hypot(dx, dy); }

    void claim() { claimed = true; tracking = false; }
    void cancel() { tracking = false; }
}
