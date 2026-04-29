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
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private int alpha = 255;
    private int color = Color.WHITE;
    private ColorStateList tintList;

    IosSignalDrawable(float density) {
        this.density = density <= 0f ? 1f : density;
        paint.setStyle(Paint.Style.FILL);
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

        float gap = Math.max(1f * density, width * 0.07f);
        float barWidth = (width - gap * 3f) / 4f;
        float minHeight = height * 0.34f;
        float maxHeight = height * 0.92f;
        float radius = Math.min(barWidth * 0.45f, 2.4f * density);
        float bottom = bounds.bottom - height * 0.04f;

        for (int i = 0; i < 4; i++) {
            float left = bounds.left + i * (barWidth + gap);
            float barHeight = minHeight + (maxHeight - minHeight) * i / 3f;
            rect.set(left, bottom - barHeight, left + barWidth, bottom);
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

    @Override
    public int getIntrinsicWidth() {
        return Math.round(18f * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(12f * density);
    }
}
