package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class IosSignalDrawable extends Drawable {
    static final int MAX_LEVEL = 4;
    static final int NO_SECONDARY_LEVEL = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private int primaryLevel;
    private int secondaryLevel = NO_SECONDARY_LEVEL;
    private int alpha = 255;
    private int color = Color.WHITE;
    private ColorStateList tintList;

    IosSignalDrawable(int primaryLevel, float density) {
        this.primaryLevel = normalizeLevel(primaryLevel);
        this.density = density <= 0f ? 1f : density;
        paint.setStyle(Paint.Style.FILL);
    }

    void setLevels(int primaryLevel, int secondaryLevel) {
        int normalizedPrimary = normalizeLevel(primaryLevel);
        int normalizedSecondary = secondaryLevel == NO_SECONDARY_LEVEL
                ? NO_SECONDARY_LEVEL : normalizeLevel(secondaryLevel);
        if (this.primaryLevel == normalizedPrimary && this.secondaryLevel == normalizedSecondary) {
            return;
        }
        this.primaryLevel = normalizedPrimary;
        this.secondaryLevel = normalizedSecondary;
        invalidateSelf();
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

        if (secondaryLevel != NO_SECONDARY_LEVEL) {
            RectF primaryBounds = new RectF(bounds.left, bounds.top,
                    bounds.right, bounds.top + height * 0.75f);
            drawBars(canvas, primaryBounds, primaryLevel);
            drawSecondaryDots(canvas, bounds, width, height, secondaryLevel);
            return;
        }

        drawBars(canvas, new RectF(bounds), primaryLevel);
    }

    private void drawBars(Canvas canvas, RectF bounds, int level) {
        float width = bounds.width();
        float height = bounds.height();
        float gap = Math.max(1f * density, width * 0.07f);
        float barWidth = (width - gap * 3f) / 4f;
        float minHeight = height * 0.34f;
        float maxHeight = height * 0.92f;
        float radius = Math.min(barWidth * 0.45f, 2.4f * density);
        float bottom = bounds.bottom - height * 0.04f;
        int activeLevel = normalizeLevel(level);

        for (int i = 0; i < 4; i++) {
            float left = bounds.left + i * (barWidth + gap);
            float barHeight = minHeight + (maxHeight - minHeight) * i / 3f;
            rect.set(left, bottom - barHeight, left + barWidth, bottom);
            paint.setAlpha(i < activeLevel ? alpha : Math.round(alpha * 0.22f));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    private void drawSecondaryDots(Canvas canvas, Rect bounds, float width, float height, int level) {
        int activeLevel = normalizeLevel(level);
        float barGap = Math.max(1f * density, width * 0.07f);
        float barWidth = (width - barGap * 3f) / 4f;
        float dotSize = Math.max(2.4f * density, Math.min(barWidth * 0.82f, height * 0.2f));
        float radius = dotSize * 0.42f;
        float top = bounds.bottom - Math.max(dotSize + 0.35f * density, height * 0.13f);
        for (int i = 0; i < 4; i++) {
            float cx = bounds.left + i * (barWidth + barGap) + barWidth / 2f;
            paint.setAlpha(i < activeLevel ? alpha : Math.round(alpha * 0.22f));
            rect.set(cx - dotSize / 2f, top, cx + dotSize / 2f, top + dotSize);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
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
        if (level >= MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return level;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(18f * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(12f * density);
    }
}
