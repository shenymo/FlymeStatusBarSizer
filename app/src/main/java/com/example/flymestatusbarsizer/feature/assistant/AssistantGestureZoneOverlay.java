package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Draws the configured gesture zones from the actual pointer-down position. */
final class AssistantGestureZoneOverlay {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:GestureZones";
    private final Context context;
    private final WindowManager windowManager;
    private final ZoneView view;
    private boolean attached;
    private boolean attempted;

    AssistantGestureZoneOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new ZoneView(context);
    }

    void show(float startX, float startY, boolean fromLeft, float shortDistancePx,
            float longDistancePx, boolean splitEnabled, int horizontalAction, int upAction,
            int downAction, int horizontalLongAction, int upLongAction, int downLongAction,
            float upAngle, float downAngle, float minVertical) {
        view.setSpec(startX, startY, fromLeft, shortDistancePx, longDistancePx, splitEnabled,
                horizontalAction, upAction, downAction, horizontalLongAction, upLongAction,
                downLongAction, upAngle, downAngle, minVertical);
        if (!attached && !attempted) {
            attempted = true;
            attach();
        }
        if (attached) view.invalidate();
    }

    void hide() {
        if (attached) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (Throwable ignored) {
            }
        }
        attached = false;
        attempted = false;
    }

    void updatePointer(float x, float y) {
        view.pointerX = x;
        view.pointerY = y;
        if (attached) view.invalidate();
    }

    private void attach() {
        if (windowManager == null) return;
        Throwable last = null;
        for (int type : WINDOW_TYPES) {
            WindowManager.LayoutParams params = buildLayoutParams(type);
            try {
                windowManager.addView(view, params);
                attached = true;
                return;
            } catch (Throwable error) {
                last = error;
                try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) { }
            }
        }
        AssistantHooks.warn("Cannot attach gesture zone overlay", last);
    }

    private WindowManager.LayoutParams buildLayoutParams(int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle(TITLE);
        params.packageName = context.getPackageName();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        applyTrustedOverlay(params);
        return params;
    }

    private static void applyTrustedOverlay(WindowManager.LayoutParams params) {
        try {
            Method method = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay");
            method.invoke(params);
        } catch (Throwable ignored) {
        }
        try {
            Field field = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            field.setAccessible(true);
            field.setInt(params, field.getInt(params) | 0x01000000);
        } catch (Throwable ignored) {
        }
    }

    private final class ZoneView extends View {
        private final Paint shortFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shortStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint startPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final float scaledDensity;
        private float startX;
        private float startY;
        private float pointerX;
        private float pointerY;
        private float shortDistance;
        private float longDistance;
        private float upAngleTan;
        private float downAngleTan;
        private float minVertical;
        private boolean fromLeft;
        private boolean splitEnabled;
        private int horizontalAction;
        private int upAction;
        private int downAction;
        private int horizontalLongAction;
        private int upLongAction;
        private int downLongAction;

        ZoneView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            shortFill.setColor(0x263f8cff);
            longFill.setColor(0x2affaa55);
            shortStroke.setColor(0xa869a9ff);
            longStroke.setColor(0xb8ffb45f);
            shortStroke.setStyle(Paint.Style.STROKE);
            longStroke.setStyle(Paint.Style.STROKE);
            shortStroke.setStrokeWidth(dp(1));
            longStroke.setStrokeWidth(dp(1));
            startPaint.setColor(Color.WHITE);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1));
            labelBackground.setColor(0xe5222222);
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(13f * scaledDensity);
            labelPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
        }

        void setSpec(float startX, float startY, boolean fromLeft, float shortDistance,
                float longDistance, boolean splitEnabled, int horizontalAction, int upAction,
                int downAction, int horizontalLongAction, int upLongAction,
                int downLongAction, float upAngle, float downAngle, float minVertical) {
            this.startX = startX;
            this.startY = startY;
            pointerX = startX;
            pointerY = startY;
            this.fromLeft = fromLeft;
            this.shortDistance = shortDistance;
            this.longDistance = longDistance;
            this.splitEnabled = splitEnabled;
            this.horizontalAction = horizontalAction;
            this.upAction = upAction;
            this.downAction = downAction;
            this.horizontalLongAction = horizontalLongAction;
            this.upLongAction = upLongAction;
            this.downLongAction = downLongAction;
            upAngleTan = (float) Math.tan(Math.toRadians(upAngle));
            downAngleTan = (float) Math.tan(Math.toRadians(downAngle));
            this.minVertical = minVertical;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float availableX = fromLeft ? getWidth() - startX : startX;
            if (availableX <= 0f) return;
            int direction = fromLeft ? 1 : -1;
            float upperY = startY;
            float lowerY = getHeight() - startY;
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            drawMode(canvas, availableX, upperY, lowerY, direction, false);
            if (splitEnabled) drawMode(canvas, availableX, upperY, lowerY, direction, true);
            drawThresholds(canvas, direction, availableX);
            canvas.restore();
            drawStartLabel(canvas, direction);
        }

        private void drawMode(Canvas canvas, float availableX, float upperY, float lowerY,
                int direction, boolean longMode) {
            float minRadius;
            float maxRadius;
            int horizontal;
            int up;
            int down;
            Paint fill;
            Paint stroke;
            if (!splitEnabled || !longMode) {
                minRadius = shortDistance;
                maxRadius = splitEnabled ? longDistance : 0f;
                horizontal = horizontalAction;
                up = upAction;
                down = downAction;
                fill = shortFill;
                stroke = shortStroke;
                if (splitEnabled && longDistance <= shortDistance) return;
            } else {
                minRadius = Math.max(shortDistance, longDistance);
                maxRadius = 0f;
                horizontal = horizontalLongAction;
                up = upLongAction;
                down = downLongAction;
                fill = longFill;
                stroke = longStroke;
            }
            if (minRadius < 0f || (maxRadius > 0f && maxRadius <= minRadius)) return;
            drawHorizontal(canvas, availableX, upperY, lowerY, direction, minRadius, maxRadius,
                    fill, stroke);
            drawDiagonal(canvas, availableX, upperY, lowerY, direction, minRadius, maxRadius,
                    true, fill, stroke);
            drawDiagonal(canvas, availableX, upperY, lowerY, direction, minRadius, maxRadius,
                    false, fill, stroke);
            String mode = splitEnabled ? (longMode ? "长" : "短") : "";
            float labelDistance = maxRadius > 0f
                    ? (minRadius + maxRadius) * 0.5f : minRadius + dp(56);
            drawLabel(canvas, direction, labelDistance, startY,
                    (mode.length() == 0 ? "横向" : mode + "距离横向") + " · "
                            + SideGestureActions.label(horizontal),
                    fill.getColor());
            float diagonalX = Math.max(shortDistance * 0.35f + dp(22), labelDistance);
            diagonalX = Math.min(availableX * 0.82f, Math.max(shortDistance * 0.35f, diagonalX));
            float upDiagonalY = angleBand(diagonalX, true) + dp(26);
            float downDiagonalY = angleBand(diagonalX, false) + dp(26);
            drawLabel(canvas, direction, diagonalX, startY - upDiagonalY,
                    (mode.length() == 0 ? "上斜" : mode + "距离上斜") + " · "
                            + SideGestureActions.label(up), fill.getColor());
            drawLabel(canvas, direction, diagonalX, startY + downDiagonalY,
                    (mode.length() == 0 ? "下斜" : mode + "距离下斜") + " · "
                            + SideGestureActions.label(down), fill.getColor());
        }

        private void drawHorizontal(Canvas canvas, float availableX, float upperY, float lowerY,
                int direction, float minRadius, float maxRadius, Paint fill, Paint stroke) {
            float start = Math.max(0f, shortDistance);
            float end = maxRadius > 0f ? Math.min(availableX, maxRadius) : availableX;
            if (end <= start) return;
            drawHorizontalSide(canvas, start, end, upperY, direction, minRadius, maxRadius,
                    true, fill, stroke);
            drawHorizontalSide(canvas, start, end, lowerY, direction, minRadius, maxRadius,
                    false, fill, stroke);
        }

        private void drawHorizontalSide(Canvas canvas, float start, float end, float verticalLimit,
                int direction, float minRadius, float maxRadius, boolean upper, Paint fill,
                Paint stroke) {
            Path path = new Path();
            boolean started = false;
            int steps = 96;
            for (int i = 0; i <= steps; i++) {
                float x = start + (end - start) * i / steps;
                float inner = radialLower(x, minRadius);
                float outer = radialUpper(x, maxRadius);
                float band = angleBand(x, upper);
                float high = Math.min(verticalLimit, Math.min(band, outer));
                if (high <= inner) continue;
                float y = upper ? startY - high : startY + high;
                if (!started) {
                    path.moveTo(screenX(x, direction), y);
                    started = true;
                } else {
                    path.lineTo(screenX(x, direction), y);
                }
            }
            if (!started) return;
            for (int i = steps; i >= 0; i--) {
                float x = start + (end - start) * i / steps;
                float inner = radialLower(x, minRadius);
                float outer = radialUpper(x, maxRadius);
                float band = angleBand(x, upper);
                float high = Math.min(verticalLimit, Math.min(band, outer));
                if (high > inner) {
                    path.lineTo(screenX(x, direction), upper
                            ? startY - inner : startY + inner);
                }
            }
            path.close();
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }

        private void drawDiagonal(Canvas canvas, float availableX, float upperY, float lowerY,
                int direction, float minRadius, float maxRadius, boolean upper, Paint fill,
                Paint stroke) {
            float start = shortDistance * 0.35f;
            float end = maxRadius > 0f ? Math.min(availableX, maxRadius) : availableX;
            if (end <= start) return;
            float verticalLimit = upper ? upperY : lowerY;
            Path path = new Path();
            boolean started = false;
            int steps = 96;
            for (int i = 0; i <= steps; i++) {
                float x = start + (end - start) * i / steps;
                float inner = radialLower(x, minRadius);
                float outer = radialUpper(x, maxRadius);
                float band = angleBand(x, upper);
                float low = Math.max(band, inner);
                float high = Math.min(verticalLimit, outer);
                if (high <= low) continue;
                float y = upper ? startY - high : startY + high;
                if (!started) {
                    path.moveTo(screenX(x, direction), y);
                    started = true;
                } else {
                    path.lineTo(screenX(x, direction), y);
                }
            }
            if (!started) return;
            for (int i = steps; i >= 0; i--) {
                float x = start + (end - start) * i / steps;
                float inner = radialLower(x, minRadius);
                float outer = radialUpper(x, maxRadius);
                float band = angleBand(x, upper);
                float low = Math.max(band, inner);
                float high = Math.min(verticalLimit, outer);
                if (high > low) {
                    path.lineTo(screenX(x, direction), upper
                            ? startY - low : startY + low);
                }
            }
            path.close();
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }

        private void drawThresholds(Canvas canvas, int direction, float availableX) {
            ringPaint.setColor(shortStroke.getColor());
            canvas.drawCircle(startX, startY, shortDistance, ringPaint);
            drawDistanceLabel(canvas, direction, shortDistance, "短 " + distanceText(shortDistance),
                    shortStroke.getColor());
            if (splitEnabled) {
                ringPaint.setColor(longStroke.getColor());
                canvas.drawCircle(startX, startY, longDistance, ringPaint);
                drawDistanceLabel(canvas, direction, longDistance,
                        "长 " + distanceText(longDistance), longStroke.getColor());
            }
            startPaint.setColor(Color.WHITE);
            startPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(startX, startY, dp(5), startPaint);
            startPaint.setColor(0xfff5d76e);
            canvas.drawCircle(pointerX, pointerY, dp(7), startPaint);
            startPaint.setColor(Color.WHITE);
        }

        private void drawDistanceLabel(Canvas canvas, int direction, float distance, String text,
                int color) {
            if (distance <= 0f) return;
            drawLabel(canvas, direction, Math.min(distance, Math.max(0f, getWidth() / 2f)),
                    startY - dp(18), text, color);
        }

        private void drawStartLabel(Canvas canvas, int direction) {
            drawLabel(canvas, direction, 0f, startY - dp(22), "起点", Color.WHITE);
        }

        private void drawLabel(Canvas canvas, int direction, float distance, float y, String text,
                int color) {
            labelPaint.setTextSize(13f * scaledDensity);
            float width = labelPaint.measureText(text) + dp(16);
            float height = dp(30);
            float center = screenX(distance, direction);
            float left = Math.max(dp(6), Math.min(getWidth() - width - dp(6), center - width / 2f));
            float top = Math.max(dp(6), Math.min(getHeight() - height - dp(6), y - height / 2f));
            labelBackground.setColor(Color.argb(0xe5, Color.red(color), Color.green(color),
                    Color.blue(color)));
            canvas.drawRoundRect(left, top, left + width, top + height, dp(7), dp(7),
                    labelBackground);
            labelPaint.setColor(Color.WHITE);
            Paint.FontMetrics metrics = labelPaint.getFontMetrics();
            float baseline = top + (height - metrics.bottom - metrics.top) / 2f;
            canvas.drawText(text, left + (width - labelPaint.measureText(text)) / 2f,
                    baseline, labelPaint);
        }

        private float radialLower(float x, float radius) {
            if (radius <= 0f || x >= radius) return 0f;
            return (float) Math.sqrt(Math.max(0f, radius * radius - x * x));
        }

        private float radialUpper(float x, float radius) {
            if (radius <= 0f) return Float.POSITIVE_INFINITY;
            if (x >= radius) return 0f;
            return (float) Math.sqrt(Math.max(0f, radius * radius - x * x));
        }

        private float angleBand(float x, boolean upper) {
            return Math.max(minVertical, x * (upper ? upAngleTan : downAngleTan));
        }

        private float screenX(float distance, int direction) {
            return startX + direction * distance;
        }

        private String distanceText(float px) {
            return Math.round(px / density) + "dp";
        }

        private float dp(float value) {
            return value * density;
        }
    }
}
