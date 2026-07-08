package com.example.flymestatusbarsizer.feature.clock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

final class LockscreenCanvasClockView extends View {
    private static final float DIGIT_WIDTH_RATIO = 0.56f;
    private static final float STROKE_WIDTH_RATIO = 0.035f;
    private static final float BACKGROUND_PADDING_RATIO = 0.16f;

    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final Matrix centerCropMatrix = new Matrix();
    private final Matrix shaderMatrix = new Matrix();
    private final Matrix globalMatrix = new Matrix();
    private final Matrix invertMatrix = new Matrix();
    private String timeText = "";
    private float textSizePx;
    private Bitmap shaderBitmap;
    private BitmapShader blurShader;
    private boolean blurFillAvailable;

    LockscreenCanvasClockView(Context context) {
        super(context);
        setWillNotDraw(false);
        configureFillPaint(blurPaint);
        configureStrokePaint(outlinePaint);
    }

    void update(String text, float textSizeSp, int color, Typeface typeface) {
        timeText = text == null ? "" : text;
        textSizePx = Math.max(1f, textSizeSp * getResources().getDisplayMetrics().scaledDensity);

        Typeface safeTypeface = typeface == null ? Typeface.DEFAULT : typeface;
        blurPaint.setTextSize(textSizePx);
        blurPaint.setTypeface(safeTypeface);
        outlinePaint.setTextSize(textSizePx);
        outlinePaint.setTypeface(safeTypeface);
        outlinePaint.setStrokeWidth(strokeWidth());
        outlinePaint.setColor(color);
        int padding = backgroundPadding();
        setPadding(padding, padding, padding, padding);
        requestLayout();
        invalidate();
    }

    private void configureFillPaint(Paint paint) {
        paint.setStyle(Paint.Style.FILL);
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
        updateBlurShader();
        if (blurFillAvailable) {
            canvas.drawText(timeText, x, y, blurPaint);
        }
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

    private void updateBlurShader() {
        blurFillAvailable = false;
        Bitmap blurBitmap = resolveBlurBitmap();
        if (!isUsableBitmap(blurBitmap)) {
            blurBitmap = shaderBitmap;
        }
        if (!isUsableBitmap(blurBitmap)) {
            blurPaint.setShader(null);
            return;
        }
        if (blurBitmap != shaderBitmap || blurShader == null) {
            shaderBitmap = blurBitmap;
            blurShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        Rect wallpaperRegion = resolveWallpaperRegion();
        int regionWidth = wallpaperRegion == null || wallpaperRegion.width() <= 0
                ? resolveScreenWidth()
                : wallpaperRegion.width();
        int regionHeight = wallpaperRegion == null || wallpaperRegion.height() <= 0
                ? resolveScreenHeight()
                : wallpaperRegion.height();
        if (regionWidth <= 0 || regionHeight <= 0) {
            blurPaint.setShader(null);
            return;
        }
        centerCropBitmapShader(
                centerCropMatrix,
                blurBitmap.getWidth(),
                blurBitmap.getHeight(),
                regionWidth,
                regionHeight);
        globalMatrix.reset();
        invertMatrix.reset();
        transformMatrixToGlobal(globalMatrix);
        globalMatrix.invert(invertMatrix);
        shaderMatrix.reset();
        shaderMatrix.postConcat(centerCropMatrix);
        shaderMatrix.postConcat(invertMatrix);
        blurShader.setLocalMatrix(shaderMatrix);
        blurPaint.setShader(blurShader);
        blurFillAvailable = true;
    }

    private boolean isUsableBitmap(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0;
    }

    private void centerCropBitmapShader(Matrix matrix, int bitmapWidth, int bitmapHeight,
            int regionWidth, int regionHeight) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            matrix.reset();
            return;
        }
        float bitmapRatio = bitmapWidth / (float) bitmapHeight;
        float regionRatio = regionWidth / (float) regionHeight;
        float scale;
        float dx = 0f;
        float dy = 0f;
        if (bitmapRatio > regionRatio) {
            scale = regionHeight / (float) bitmapHeight;
            dx = -(((bitmapWidth * regionHeight) / (float) bitmapHeight) - regionWidth) / 2f;
        } else {
            scale = regionWidth / (float) bitmapWidth;
            dy = -(((bitmapHeight * regionWidth) / (float) bitmapWidth) - regionHeight) / 2f;
        }
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
    }

    private Bitmap resolveBlurBitmap() {
        Object manager = resolveWallpaperBlurDrawableManager();
        Object bitmap = FlymeStatusBarSizer.getFieldCompat(manager, "mBlurBitmap");
        return bitmap instanceof Bitmap ? (Bitmap) bitmap : null;
    }

    private Rect resolveWallpaperRegion() {
        Object manager = resolveWallpaperBlurDrawableManager();
        Object region = FlymeStatusBarSizer.getFieldCompat(manager, "mWallpaperRegion");
        return region instanceof Rect ? (Rect) region : null;
    }

    private Object resolveWallpaperBlurDrawableManager() {
        try {
            ClassLoader loader = getContext() == null ? null : getContext().getClassLoader();
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.wallpaper.WallpaperBlurDrawableManager",
                    false,
                    loader == null ? getClass().getClassLoader() : loader);
            return clazz.getDeclaredMethod("getInstance", Context.class).invoke(null, getContext());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int resolveScreenWidth() {
        View root = getRootView();
        if (root != null && root.getWidth() > 0) {
            return root.getWidth();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int resolveScreenHeight() {
        View root = getRootView();
        if (root != null && root.getHeight() > 0) {
            return root.getHeight();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }
}
