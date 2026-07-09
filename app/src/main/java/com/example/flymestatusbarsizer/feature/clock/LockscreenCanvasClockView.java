package com.example.flymestatusbarsizer.feature.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

final class LockscreenCanvasClockView extends View {
    private static final float DIGIT_WIDTH_RATIO = 0.56f;
    private static final float STROKE_WIDTH_RATIO = 0.035f;
    private static final float BACKGROUND_PADDING_RATIO = 0.16f;

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private String timeText = "";
    private float textSizePx;

    LockscreenCanvasClockView(Context context) {
        super(context);
        setWillNotDraw(false);
        configureStrokePaint(outlinePaint);
    }

    void update(String text, float textSizeSp, int color, Typeface typeface) {
        timeText = text == null ? "" : text;
        textSizePx = Math.max(1f, textSizeSp * getResources().getDisplayMetrics().scaledDensity);

        Typeface safeTypeface = typeface == null ? Typeface.DEFAULT : typeface;
        outlinePaint.setTextSize(textSizePx);
        outlinePaint.setTypeface(safeTypeface);
        outlinePaint.setStrokeWidth(strokeWidth());
        outlinePaint.setColor(color);
        int padding = backgroundPadding();
        setPadding(padding, padding, padding, padding);
        requestLayout();
        invalidate();
    }

    private void configureStrokePaint(Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(measureClockWidth()
                + getPaddingLeft() + getPaddingRight());
        int width = desiredWidth;
        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        if (widthMode == View.MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == View.MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        }
        int height = Math.round(textSizePx + getPaddingTop() + getPaddingBottom());
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode == View.MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == View.MeasureSpec.AT_MOST) {
            height = Math.min(height, heightSize);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (timeText.isEmpty()) {
            return;
        }
        outlinePaint.getFontMetrics(fontMetrics);
        float clockWidth = outlinePaint.measureText(timeText);
        float x = (getWidth() - clockWidth) / 2f;
        float y = (getHeight() - fontMetrics.ascent - fontMetrics.descent) / 2f;
        canvas.drawText(timeText, x, y, outlinePaint);
    }

    private float measureClockWidth() {
        if (!timeText.isEmpty()) {
            outlinePaint.setTextSize(textSizePx);
            return outlinePaint.measureText(timeText);
        }
        return textSizePx * DIGIT_WIDTH_RATIO * 4f;
    }

    private float strokeWidth() {
        return Math.max(2f, textSizePx * STROKE_WIDTH_RATIO);
    }

    private int backgroundPadding() {
        return Math.max(1, Math.round(textSizePx * BACKGROUND_PADDING_RATIO));
    }
}
