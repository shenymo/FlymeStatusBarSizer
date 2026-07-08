package com.example.flymestatusbarsizer.feature.clock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

final class LockscreenCanvasClockView extends View {
    private static final float DIGIT_WIDTH_RATIO = 0.56f;
    private static final float COLON_WIDTH_RATIO = 0.18f;
    private static final float GAP_RATIO = 0.14f;
    private static final float STROKE_WIDTH_RATIO = 0.105f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path digitPath = new Path();
    private final RectF rect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final int[] windowLocation = new int[2];
    private String timeText = "";
    private float textSizePx;
    private int fallbackColor = 0xffffffff;
    private Bitmap shaderBitmap;
    private BitmapShader blurShader;

    LockscreenCanvasClockView(Context context) {
        super(context);
        setWillNotDraw(false);
        configureStrokePaint(fillPaint);
        configureStrokePaint(shadowPaint);
    }

    void update(String text, float textSizeSp, int color, android.graphics.Typeface typeface) {
        timeText = text == null ? "" : text;
        textSizePx = Math.max(1f, textSizeSp * getResources().getDisplayMetrics().scaledDensity);

        fallbackColor = color;
        float strokeWidth = strokeWidth();
        fillPaint.setStrokeWidth(strokeWidth);
        shadowPaint.setStrokeWidth(strokeWidth);
        shadowPaint.setColor(0x44000000);
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
        float clockWidth = measureClockWidth();
        float x = (getWidth() - clockWidth) / 2f;
        float y = (getHeight() - textSizePx) / 2f;
        float digitWidth = digitWidth();
        float colonWidth = colonWidth();
        float gap = gap();
        updateBlurShader();
        for (int i = 0; i < timeText.length(); i++) {
            char c = timeText.charAt(i);
            if (c >= '0' && c <= '9') {
                drawDigit(canvas, c - '0', x, y, digitWidth, textSizePx, shadowPaint, true);
                drawDigit(canvas, c - '0', x, y, digitWidth, textSizePx, fillPaint, false);
                x += digitWidth + gap;
            } else if (c == ':') {
                drawColon(canvas, x, y, colonWidth, textSizePx, shadowPaint, true);
                drawColon(canvas, x, y, colonWidth, textSizePx, fillPaint, false);
                x += colonWidth + gap;
            }
        }
    }

    private void drawDigit(Canvas canvas, int digit, float left, float top, float width,
            float height, Paint paint, boolean shadow) {
        float dx = shadow ? shadowOffsetX() : 0f;
        float dy = shadow ? shadowOffsetY() : 0f;
        buildDigitPath(digit, left + dx, top + dy, width, height);
        canvas.drawPath(digitPath, paint);
    }

    private void buildDigitPath(int digit, float left, float top, float width, float height) {
        digitPath.reset();
        float stroke = strokeWidth();
        float l = left + stroke / 2f;
        float t = top + stroke / 2f;
        float r = left + width - stroke / 2f;
        float b = top + height - stroke / 2f;
        float w = r - l;
        float h = b - t;

        switch (digit) {
            case 0:
                drawCapsulePath(l, t, r, b);
                break;
            case 1:
                digitPath.moveTo(l + w * 0.16f, t + h * 0.18f);
                digitPath.lineTo(l + w * 0.55f, t);
                digitPath.lineTo(l + w * 0.55f, b);
                break;
            case 2:
                rect.set(l, t, r, t + w);
                digitPath.arcTo(rect, 205f, 155f, true);
                digitPath.quadTo(r, t + h * 0.46f, l + w * 0.08f, b);
                digitPath.lineTo(r, b);
                break;
            case 3:
                rect.set(l, t, r, t + h * 0.56f);
                digitPath.arcTo(rect, 210f, 300f, true);
                rect.set(l, t + h * 0.44f, r, b);
                digitPath.arcTo(rect, 210f, 300f, true);
                break;
            case 4:
                digitPath.moveTo(r - w * 0.08f, t + h * 0.70f);
                digitPath.lineTo(l, t + h * 0.70f);
                digitPath.lineTo(l + w * 0.74f, t);
                digitPath.lineTo(l + w * 0.74f, b);
                break;
            case 5:
                digitPath.moveTo(r, t);
                digitPath.lineTo(l + w * 0.08f, t);
                digitPath.lineTo(l + w * 0.08f, t + h * 0.42f);
                digitPath.cubicTo(l + w * 0.35f, t + h * 0.34f,
                        r, t + h * 0.42f, r, t + h * 0.66f);
                digitPath.cubicTo(r, t + h * 0.94f,
                        l + w * 0.34f, b, l + w * 0.08f, t + h * 0.82f);
                break;
            case 6:
                digitPath.moveTo(r - w * 0.14f, t + h * 0.08f);
                digitPath.quadTo(l + w * 0.04f, t + h * 0.18f, l + w * 0.08f, t + h * 0.62f);
                rect.set(l, t + h * 0.42f, r, b);
                digitPath.addOval(rect, Path.Direction.CW);
                break;
            case 7:
                digitPath.moveTo(l, t);
                digitPath.lineTo(r, t);
                digitPath.lineTo(l + w * 0.30f, b);
                break;
            case 8:
                rect.set(l, t, r, t + h * 0.52f);
                digitPath.addOval(rect, Path.Direction.CW);
                rect.set(l, t + h * 0.45f, r, b);
                digitPath.addOval(rect, Path.Direction.CW);
                break;
            case 9:
                rect.set(l, t, r, t + h * 0.54f);
                digitPath.addOval(rect, Path.Direction.CW);
                digitPath.moveTo(r, t + h * 0.27f);
                digitPath.lineTo(r, t + h * 0.70f);
                rect.set(l, t + h * 0.46f, r, b);
                digitPath.arcTo(rect, 0f, 140f, false);
                break;
            default:
                break;
        }
    }

    private void drawCapsulePath(float left, float top, float right, float bottom) {
        float radius = (right - left) / 2f;
        rect.set(left, top, right, top + radius * 2f);
        digitPath.arcTo(rect, 180f, 180f, true);
        digitPath.lineTo(right, bottom - radius);
        rect.set(left, bottom - radius * 2f, right, bottom);
        digitPath.arcTo(rect, 0f, 180f, false);
        digitPath.close();
    }

    private void drawColon(Canvas canvas, float left, float top, float width, float height,
            Paint paint, boolean shadow) {
        float dot = strokeWidth() * 0.95f;
        float radius = dot / 2f;
        float cx = left + width / 2f + (shadow ? dot * 0.18f : 0f);
        float dy = shadow ? dot * 0.2f : 0f;
        drawDot(canvas, cx, top + height * 0.36f + dy, radius, paint);
        drawDot(canvas, cx, top + height * 0.64f + dy, radius, paint);
    }

    private void drawDot(Canvas canvas, float cx, float cy, float radius, Paint paint) {
        Paint.Style style = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(style);
    }

    private float measureClockWidth() {
        float total = 0f;
        for (int i = 0; i < timeText.length(); i++) {
            char c = timeText.charAt(i);
            if (c >= '0' && c <= '9') {
                total += digitWidth();
            } else if (c == ':') {
                total += colonWidth();
            }
            if (i < timeText.length() - 1) {
                total += gap();
            }
        }
        return total;
    }

    private float digitWidth() {
        return textSizePx * DIGIT_WIDTH_RATIO;
    }

    private float colonWidth() {
        return textSizePx * COLON_WIDTH_RATIO;
    }

    private float gap() {
        return textSizePx * GAP_RATIO;
    }

    private float strokeWidth() {
        return Math.max(2f, textSizePx * STROKE_WIDTH_RATIO);
    }

    private float shadowOffsetX() {
        return strokeWidth() * 0.18f;
    }

    private float shadowOffsetY() {
        return strokeWidth() * 0.25f;
    }

    private void updateBlurShader() {
        Bitmap blurBitmap = resolveBlurBitmap();
        if (blurBitmap == null || blurBitmap.isRecycled()) {
            fillPaint.setShader(null);
            fillPaint.setColor(fallbackColor);
            return;
        }
        if (blurBitmap != shaderBitmap) {
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
            fillPaint.setShader(null);
            fillPaint.setColor(fallbackColor);
            return;
        }
        getLocationInWindow(windowLocation);
        shaderMatrix.reset();
        shaderMatrix.setScale(
                regionWidth / (float) blurBitmap.getWidth(),
                regionHeight / (float) blurBitmap.getHeight());
        shaderMatrix.postTranslate(-windowLocation[0], -windowLocation[1]);
        blurShader.setLocalMatrix(shaderMatrix);
        fillPaint.setShader(blurShader);
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
