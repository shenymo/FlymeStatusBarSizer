package com.example.flymestatusbarsizer;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

final class LauncherStackScaleCurveView extends View {
    private final MainActivity activity;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private float x1;
    private float y1;
    private float x2;
    private float y2;
    private int activePoint;

    LauncherStackScaleCurveView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        x1 = read(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT);
        y1 = read(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT);
        x2 = read(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT);
        y2 = read(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(activity.dp(1));
        gridPaint.setColor(activity.featureStrokeColor());
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(activity.dp(1));
        handlePaint.setColor(withAlpha(activity.subtextColor(), 150));
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(activity.dp(3));
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setColor(activity.primaryColor());
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(activity.primaryColor());
        setBackground(activity.outlinedRect(
                activity.featureSurfaceColor(),
                activity.featureStrokeColor(),
                1,
                16));
        int padding = activity.dp(20);
        setPadding(padding, padding, padding, padding);
        setContentDescription("缩放曲线编辑器，拖动两个控制点调整曲线");
        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(activity.dp(280), widthMeasureSpec),
                resolveSize(activity.dp(190), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        float point1X = mapX(x1, left, right);
        float point1Y = mapY(y1, top, bottom);
        float point2X = mapX(x2, left, right);
        float point2Y = mapY(y2, top, bottom);

        canvas.drawRect(left, top, right, bottom, gridPaint);
        canvas.drawLine(left, bottom, right, top, handlePaint);
        canvas.drawLine(left, bottom, point1X, point1Y, handlePaint);
        canvas.drawLine(point2X, point2Y, right, top, handlePaint);

        curvePath.reset();
        curvePath.moveTo(left, bottom);
        curvePath.cubicTo(point1X, point1Y, point2X, point2Y, right, top);
        canvas.drawPath(curvePath, curvePaint);

        float radius = activity.dp(7);
        canvas.drawCircle(point1X, point1Y, activePoint == 1 ? radius * 1.25f : radius, pointPaint);
        canvas.drawCircle(point2X, point2Y, activePoint == 2 ? radius * 1.25f : radius, pointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePoint = findTouchedPoint(event.getX(), event.getY());
                if (activePoint == 0) {
                    return false;
                }
                disallowParentIntercept(true);
                updateActivePoint(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activePoint == 0) {
                    return false;
                }
                updateActivePoint(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (activePoint == 0) {
                    return false;
                }
                updateActivePoint(event.getX(), event.getY());
                save();
                activePoint = 0;
                disallowParentIntercept(false);
                performClick();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                activePoint = 0;
                disallowParentIntercept(false);
                invalidate();
                return true;
            default:
                return activePoint != 0;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int findTouchedPoint(float touchX, float touchY) {
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        float distance1 = distanceSquared(
                touchX,
                touchY,
                mapX(x1, left, right),
                mapY(y1, top, bottom));
        float distance2 = distanceSquared(
                touchX,
                touchY,
                mapX(x2, left, right),
                mapY(y2, top, bottom));
        float hitRadius = activity.dp(30);
        if (Math.min(distance1, distance2) > hitRadius * hitRadius) {
            return 0;
        }
        return distance1 <= distance2 ? 1 : 2;
    }

    private void updateActivePoint(float touchX, float touchY) {
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        float x = clamp((touchX - left) / Math.max(1f, right - left));
        float y = clamp((bottom - touchY) / Math.max(1f, bottom - top));
        if (activePoint == 1) {
            x1 = x;
            y1 = y;
        } else if (activePoint == 2) {
            x2 = x;
            y2 = y;
        }
        invalidate();
    }

    private void save() {
        int savedX1 = Math.round(x1 * 100f);
        int savedY1 = Math.round(y1 * 100f);
        int savedX2 = Math.round(x2 * 100f);
        int savedY2 = Math.round(y2 * 100f);
        x1 = savedX1 / 100f;
        y1 = savedY1 / 100f;
        x2 = savedX2 / 100f;
        y2 = savedY2 / 100f;
        SharedPreferences.Editor editor = activity.prefs().edit();
        editor.putInt(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X1_PERCENT, savedX1);
        editor.putInt(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y1_PERCENT, savedY1);
        editor.putInt(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_X2_PERCENT, savedX2);
        editor.putInt(SettingsStore.KEY_LAUNCHER_STACK_SCALE_CURVE_Y2_PERCENT, savedY2);
        editor.apply();
        SettingsStore.notifyChanged(activity);
        activity.invalidatePreview();
    }

    private float read(String key) {
        return SettingsStore.readInt(
                activity.prefs(),
                key,
                SettingsStore.defaultInt(key)) / 100f;
    }

    private void disallowParentIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static float mapX(float value, float left, float right) {
        return left + ((right - left) * value);
    }

    private static float mapY(float value, float top, float bottom) {
        return bottom - ((bottom - top) * value);
    }

    private static float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (dx * dx) + (dy * dy);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
