package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Path;

/** Geometry shared by gesture recognition and both zone previews. */
public final class AssistantGestureGeometry {
    private final float distance;
    private final float upAngleTan;
    private final float downAngleTan;
    private final float minVertical;

    public AssistantGestureGeometry(float distance, float upAngle, float downAngle,
            float minVertical) {
        this.distance = distance;
        upAngleTan = (float) Math.tan(Math.toRadians(upAngle));
        downAngleTan = (float) Math.tan(Math.toRadians(downAngle));
        this.minVertical = minVertical;
    }

    public int direction(float dx, float dy) {
        if (dx <= 0f) return 0;
        float threshold = Math.max(minVertical, dx * (dy < 0f ? upAngleTan : downAngleTan));
        return Math.abs(dy) < threshold ? 0 : dy < 0f ? 1 : 2;
    }

    public boolean ready(float dx, float dy) {
        if (dx <= 0f) return false;
        if (direction(dx, dy) == 0) return dx >= distance;
        return (float) Math.hypot(dx, dy) >= distance && dx >= distance * 0.35f;
    }

    // Paths use inward distance as X and displacement from pointer-down as Y.
    public Path region(int direction, float minRadius, float maxRadius, float width,
            float upperHeight, float lowerHeight) {
        Path region = new Path();
        float start = direction == 0 ? distance : distance * 0.35f;
        if (start >= width) return region;
        region.addRect(start, -upperHeight, width, lowerHeight, Path.Direction.CW);

        Path band = new Path();
        if (direction == 0) {
            band.moveTo(0f, -minVertical);
            if (upAngleTan > 0f) {
                band.lineTo(Math.min(width, minVertical / upAngleTan), -minVertical);
            }
            band.lineTo(width, -Math.max(minVertical, width * upAngleTan));
            band.lineTo(width, Math.max(minVertical, width * downAngleTan));
            if (downAngleTan > 0f) {
                band.lineTo(Math.min(width, minVertical / downAngleTan), minVertical);
            }
            band.lineTo(0f, minVertical);
        } else {
            float sign = direction == 1 ? -1f : 1f;
            float angleTan = direction == 1 ? upAngleTan : downAngleTan;
            float height = Math.max(direction == 1 ? upperHeight : lowerHeight,
                    Math.max(minVertical, width * angleTan));
            band.moveTo(0f, sign * minVertical);
            if (angleTan > 0f) {
                band.lineTo(Math.min(width, minVertical / angleTan), sign * minVertical);
            }
            band.lineTo(width, sign * Math.max(minVertical, width * angleTan));
            band.lineTo(width, sign * height);
            band.lineTo(0f, sign * height);
        }
        band.close();
        region.op(band, Path.Op.INTERSECT);

        float innerRadius = Math.max(distance, minRadius);
        if (innerRadius > 0f) {
            Path inner = new Path();
            inner.addCircle(0f, 0f, innerRadius, Path.Direction.CW);
            region.op(inner, Path.Op.DIFFERENCE);
        }
        if (Float.isFinite(maxRadius)) {
            Path outer = new Path();
            outer.addCircle(0f, 0f, maxRadius, Path.Direction.CW);
            region.op(outer, Path.Op.INTERSECT);
        }
        return region;
    }
}
