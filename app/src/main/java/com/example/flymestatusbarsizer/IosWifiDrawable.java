package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class IosWifiDrawable extends Drawable {
    static final int LEVEL_ERROR = -1;
    static final int MAX_LEVEL = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final Path path = new Path();
    private final float density;
    private int level;
    private int alpha = 255;
    private int color = Color.WHITE;
    private ColorStateList tintList;

    IosWifiDrawable(int level, float density) {
        this.level = level;
        this.density = density <= 0f ? 1f : density;
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    void setLevelValue(int level) {
        if (this.level == level) {
            return;
        }
        this.level = level;
        invalidateSelf();
    }

    @Override
    protected boolean onLevelChange(int level) {
        setLevelValue(normalizeLevel(level));
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float width = bounds.width();
        float height = bounds.height();
        if (width <= 0f || height <= 0f) {
            return;
        }

        paint.setColor(color);
        paint.setAlpha(alpha);

        if (level == LEVEL_ERROR) {
            drawError(canvas, bounds, width, height);
            return;
        }

        int activeLevel = Math.max(0, Math.min(MAX_LEVEL, level));
        float cx = bounds.left + width / 2f;
        float startAngle = 218f;
        float sweepAngle = 104f;
        float maxRadius = Math.min(width * 0.49f, height * 1.18f);
        float thickness = height * 0.145f;
        float gap = height * 0.105f;
        float outerRadius = maxRadius - thickness / 2f;
        float middleRadius = outerRadius - thickness - gap;
        float innerRadius = middleRadius - thickness - gap;
        float cy = bounds.top + (height + outerRadius + thickness / 2f - innerRadius + thickness / 2f) / 2f;

        drawArcBand(canvas, cx, cy, outerRadius, thickness,
                startAngle, sweepAngle, activeLevel >= 4);
        drawArcBand(canvas, cx, cy, middleRadius, thickness,
                startAngle, sweepAngle, activeLevel >= 3);
        drawSolidWedge(canvas, cx, cy, innerRadius + thickness / 2f,
                startAngle, sweepAngle, activeLevel >= 2);
        if (activeLevel == 0) {
            drawExclamation(canvas, bounds, width, height);
        }
    }

    private void drawArcBand(Canvas canvas, float cx, float cy, float radius, float thickness,
            float startAngle, float sweepAngle, boolean active) {
        setSignalPaint(active);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, startAngle, sweepAngle, false, paint);
    }

    private void drawSolidWedge(Canvas canvas, float cx, float cy, float radius,
            float startAngle, float sweepAngle, boolean active) {
        setSignalPaint(active);
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(cx, cy);
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        path.arcTo(arc, startAngle, sweepAngle);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void setSignalPaint(boolean active) {
        paint.setColor(color);
        paint.setAlpha(active ? alpha : Math.round(alpha * 0.24f));
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawExclamation(Canvas canvas, Rect bounds, float width, float height) {
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        float stroke = Math.max(1.2f * density, width * 0.055f);
        float cx = bounds.right - width * 0.13f;
        float top = bounds.bottom - height * 0.42f;
        float bottom = bounds.bottom - height * 0.20f;
        canvas.drawRoundRect(cx - stroke / 2f, top, cx + stroke / 2f, bottom,
                stroke / 2f, stroke / 2f, paint);
        canvas.drawCircle(cx, bounds.bottom - height * 0.10f, stroke * 0.58f, paint);
    }

    private void drawError(Canvas canvas, Rect bounds, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f * density, height * 0.11f));
        float padX = width * 0.18f;
        float padY = height * 0.18f;
        canvas.drawLine(bounds.left + padX, bounds.top + padY,
                bounds.right - padX, bounds.bottom - padY, paint);
        canvas.drawLine(bounds.right - padX, bounds.top + padY,
                bounds.left + padX, bounds.bottom - padY, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setTint(int tintColor) {
        color = tintColor;
        invalidateSelf();
    }

    @Override
    public void setTintList(ColorStateList tint) {
        tintList = tint;
        updateTintColor();
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        invalidateSelf();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return updateTintColor();
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    private boolean updateTintColor() {
        if (tintList == null) {
            invalidateSelf();
            return false;
        }
        int newColor = tintList.getColorForState(getState(), tintList.getDefaultColor());
        if (newColor == color) {
            return false;
        }
        color = newColor;
        invalidateSelf();
        return true;
    }

    private static int normalizeLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= MAX_LEVEL) {
            return level;
        }
        return MAX_LEVEL;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(20f * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(14f * density);
    }
}
