package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class NetworkTypeDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private final float density;
    private final String label;
    private int alpha = 255;
    private int color = Color.WHITE;
    private ColorStateList tintList;

    NetworkTypeDrawable(String label, float density) {
        this.label = label == null ? "5G" : label;
        this.density = density <= 0f ? 1f : density;
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setTextSize(bounds.height() * 0.41f);
        paint.getTextBounds(label, 0, label.length(), textBounds);
        float x = bounds.exactCenterX();
        float y = bounds.exactCenterY() - (paint.descent() + paint.ascent()) / 2f;
        canvas.drawText(label, x, y, paint);
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
        return Math.round(("5GA".equals(label) || "5G+".equals(label) ? 22f : 17f) * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(12f * density);
    }
}
