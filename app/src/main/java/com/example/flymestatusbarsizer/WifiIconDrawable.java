package com.example.flymestatusbarsizer;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import android.view.View;

import java.lang.ref.WeakReference;

final class WifiIconDrawable extends Drawable {
    private static final int MAX_LEVEL = 4;
    private static final int DRAW_ALPHA = 224;
    private static final float INACTIVE_ALPHA_RATIO = 0.3f;
    private static final float SOURCE_LEFT = 4.318f;
    private static final float SOURCE_TOP = 1.117f;
    private static final float SOURCE_RIGHT = 50.617f;
    private static final float SOURCE_BOTTOM = 34.332f;
    private static final float VISUAL_ASPECT_RATIO =
            (SOURCE_RIGHT - SOURCE_LEFT) / (SOURCE_BOTTOM - SOURCE_TOP);
    private static final float SOURCE_THICKEN_STROKE_WIDTH = 1.35f;

    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF SOURCE_BOUNDS =
            new RectF(SOURCE_LEFT, SOURCE_TOP, SOURCE_RIGHT, SOURCE_BOTTOM);
    private static final RectF DRAW_BOUNDS = new RectF();
    private static final Matrix MATRIX = new Matrix();
    private static final Path OUTER_SOURCE_PATH = new Path();
    private static final Path INNER_SOURCE_PATH = new Path();
    private static final Path SECTOR_SOURCE_PATH = new Path();
    private static final Path OUTER_DRAW_PATH = new Path();
    private static final Path INNER_DRAW_PATH = new Path();
    private static final Path SECTOR_DRAW_PATH = new Path();

    static {
        STROKE_PAINT.setStyle(Paint.Style.STROKE);
        STROKE_PAINT.setStrokeCap(Paint.Cap.ROUND);
        STROKE_PAINT.setStrokeJoin(Paint.Join.ROUND);
        FILL_PAINT.setStyle(Paint.Style.FILL);
        buildOuterBandPath(OUTER_SOURCE_PATH);
        buildInnerBandPath(INNER_SOURCE_PATH);
        buildSectorPath(SECTOR_SOURCE_PATH);
    }

    private final WeakReference<View> ownerViewRef;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final int visualBandHeight;

    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;
    private int level;

    WifiIconDrawable(View ownerView, int intrinsicWidth, int intrinsicHeight,
            int visualBandHeight, int level) {
        this.ownerViewRef = new WeakReference<>(ownerView);
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.visualBandHeight = Math.max(1, visualBandHeight);
        this.level = sanitizeLevel(level);
    }

    boolean matchesGeometry(int intrinsicWidth, int intrinsicHeight, int visualBandHeight) {
        return this.intrinsicWidth == Math.max(1, intrinsicWidth)
                && this.intrinsicHeight == Math.max(1, intrinsicHeight)
                && this.visualBandHeight == Math.max(1, visualBandHeight);
    }

    boolean setLevelValue(int level) {
        int sanitized = sanitizeLevel(level);
        if (this.level == sanitized) {
            return false;
        }
        this.level = sanitized;
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        updateDrawColor(getState());
        int baseColor = SignalPreviewPainter.modulateColorAlpha(drawColor, alpha);
        int activeColor = SignalPreviewPainter.modulateColorAlpha(baseColor, DRAW_ALPHA);
        int inactiveColor = scaleAlpha(activeColor, INACTIVE_ALPHA_RATIO);
        IconMetrics.resolveCenteredVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        if (VISUAL_CANVAS.isEmpty()) {
            return;
        }

        DRAW_BOUNDS.set(VISUAL_CANVAS.rect.left, VISUAL_CANVAS.rect.top,
                VISUAL_CANVAS.rect.right, VISUAL_CANVAS.baselineY);
        if (DRAW_BOUNDS.isEmpty()) {
            return;
        }

        transformPath(OUTER_SOURCE_PATH, OUTER_DRAW_PATH, DRAW_BOUNDS);
        transformPath(INNER_SOURCE_PATH, INNER_DRAW_PATH, DRAW_BOUNDS);
        transformPath(SECTOR_SOURCE_PATH, SECTOR_DRAW_PATH, DRAW_BOUNDS);
        float thickenStrokeWidth = Math.max(
                0.75f,
                DRAW_BOUNDS.height() * SOURCE_THICKEN_STROKE_WIDTH / SOURCE_BOUNDS.height());
        drawSolidPath(canvas, OUTER_DRAW_PATH, resolveOuterBandColor(activeColor, inactiveColor),
                thickenStrokeWidth);
        drawSolidPath(canvas, INNER_DRAW_PATH, resolveInnerBandColor(activeColor, inactiveColor),
                thickenStrokeWidth);
        drawSolidPath(canvas, SECTOR_DRAW_PATH, resolveSectorColor(activeColor, inactiveColor),
                thickenStrokeWidth);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(alpha, 255));
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setTintList(ColorStateList tint) {
        tintList = tint;
        updateDrawColor(getState());
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return updateDrawColor(state);
    }

    private boolean updateDrawColor(int[] state) {
        int fallbackColor = tintList == null
                ? Color.WHITE
                : tintList.getColorForState(state == null ? StateSet.NOTHING : state, tintList.getDefaultColor());
        int resolvedColor = FlymeStatusBarSizer.resolveSignalLinkedTintColor(
                ownerViewRef.get(),
                tintList,
                state,
                fallbackColor);
        if (drawColor == resolvedColor) {
            return false;
        }
        drawColor = resolvedColor;
        invalidateSelf();
        return true;
    }

    private void drawFilledPath(Canvas canvas, Path path, int color) {
        if (path == null || path.isEmpty()) {
            return;
        }
        FILL_PAINT.setColor(color);
        FILL_PAINT.setColorFilter(colorFilter);
        canvas.drawPath(path, FILL_PAINT);
        FILL_PAINT.setColorFilter(null);
    }

    private void drawSolidPath(Canvas canvas, Path path, int color, float strokeWidth) {
        if (path == null || path.isEmpty()) {
            return;
        }
        drawFilledPath(canvas, path, color);
        STROKE_PAINT.setStrokeWidth(strokeWidth);
        STROKE_PAINT.setColor(color);
        STROKE_PAINT.setColorFilter(colorFilter);
        canvas.drawPath(path, STROKE_PAINT);
        STROKE_PAINT.setColorFilter(null);
    }

    private static void transformPath(Path source, Path target, RectF drawBounds) {
        if (source == null || target == null || drawBounds == null || drawBounds.isEmpty()) {
            if (target != null) {
                target.reset();
            }
            return;
        }
        float scaleX = drawBounds.width() / SOURCE_BOUNDS.width();
        float scaleY = drawBounds.height() / SOURCE_BOUNDS.height();
        MATRIX.reset();
        MATRIX.setScale(scaleX, scaleY);
        MATRIX.postTranslate(drawBounds.left - SOURCE_BOUNDS.left * scaleX,
                drawBounds.top - SOURCE_BOUNDS.top * scaleY);
        target.reset();
        source.transform(MATRIX, target);
    }

    private int resolveSectorColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 1 ? activeColor : inactiveColor;
    }

    private int resolveInnerBandColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 2 ? activeColor : inactiveColor;
    }

    private int resolveOuterBandColor(int activeColor, int inactiveColor) {
        return resolveVisibleBars() >= 3 ? activeColor : inactiveColor;
    }

    private int resolveVisibleBars() {
        if (level <= 0) {
            return 1;
        }
        if (level == 1) {
            return 2;
        }
        return 3;
    }

    private static int sanitizeLevel(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, MAX_LEVEL);
    }

    private static int scaleAlpha(int color, float ratio) {
        int alpha = (color >>> 24) & 0xff;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * ratio)));
        return SignalPreviewPainter.withFixedAlpha(color, scaledAlpha);
    }

    private static void buildOuterBandPath(Path path) {
        if (path == null) {
            return;
        }
        path.reset();
        // Mirrors res/drawable/wifi.xml so the custom drawable stays aligned with the iOS reference.
        path.moveTo(6.982f, 14.373f);
        path.cubicTo(7.283f, 14.674f, 7.691f, 14.674f, 7.992f, 14.352f);
        path.cubicTo(13.02f, 9.109f, 19.852f, 6.209f, 27.457f, 6.209f);
        path.cubicTo(35.084f, 6.209f, 42.045f, 9.002f, 46.943f, 14.352f);
        path.cubicTo(47.244f, 14.674f, 47.652f, 14.674f, 47.953f, 14.373f);
        path.lineTo(50.359f, 11.967f);
        path.cubicTo(50.617f, 11.688f, 50.617f, 11.301f, 50.381f, 11f);
        path.cubicTo(45.611f, 5.199f, 36.48f, 1.117f, 27.457f, 1.117f);
        path.cubicTo(18.455f, 1.117f, 9.303f, 5.178f, 4.555f, 11f);
        path.cubicTo(4.318f, 11.301f, 4.34f, 11.688f, 4.576f, 11.967f);
        path.close();
    }

    private static void buildInnerBandPath(Path path) {
        if (path == null) {
            return;
        }
        path.reset();
        path.moveTo(15.576f, 23.01f);
        path.cubicTo(15.898f, 23.311f, 16.242f, 23.311f, 16.607f, 22.924f);
        path.cubicTo(19.207f, 20.131f, 23.439f, 18.24f, 27.457f, 18.262f);
        path.cubicTo(31.496f, 18.24f, 35.729f, 20.131f, 38.328f, 22.924f);
        path.cubicTo(38.693f, 23.311f, 39.037f, 23.311f, 39.359f, 23.01f);
        path.lineTo(42.131f, 20.281f);
        path.cubicTo(42.367f, 20.023f, 42.41f, 19.68f, 42.174f, 19.4f);
        path.cubicTo(39.188f, 15.684f, 33.387f, 13.191f, 27.457f, 13.191f);
        path.cubicTo(21.549f, 13.191f, 15.77f, 15.705f, 12.762f, 19.4f);
        path.cubicTo(12.525f, 19.68f, 12.568f, 20.023f, 12.805f, 20.281f);
        path.close();
    }

    private static void buildSectorPath(Path path) {
        if (path == null) {
            return;
        }
        path.reset();
        path.moveTo(27.457f, 34.332f);
        path.cubicTo(27.758f, 34.332f, 28.037f, 34.139f, 28.725f, 33.494f);
        path.lineTo(33.58f, 28.832f);
        path.cubicTo(33.816f, 28.617f, 33.881f, 28.359f, 33.623f, 28.037f);
        path.cubicTo(32.527f, 26.555f, 29.992f, 25.244f, 27.457f, 25.244f);
        path.cubicTo(24.943f, 25.244f, 22.408f, 26.555f, 21.313f, 28.037f);
        path.cubicTo(21.098f, 28.359f, 21.119f, 28.617f, 21.355f, 28.832f);
        path.lineTo(26.211f, 33.494f);
        path.cubicTo(26.898f, 34.182f, 27.178f, 34.332f, 27.457f, 34.332f);
        path.close();
    }
}
