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
    private static final float VIEWPORT_SIZE = 960f;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF SOURCE_BOUNDS = new RectF();
    private static final RectF DRAW_BOUNDS = new RectF();
    private static final Matrix MATRIX = new Matrix();
    private static final Path OUTER_SOURCE_PATH = new Path();
    private static final Path INNER_SOURCE_PATH = new Path();
    private static final Path DOT_SOURCE_PATH = new Path();
    private static final Path OUTER_DRAW_PATH = new Path();
    private static final Path INNER_DRAW_PATH = new Path();
    private static final Path DOT_DRAW_PATH = new Path();
    private static final float VISUAL_ASPECT_RATIO;

    static {
        FILL_PAINT.setStyle(Paint.Style.FILL);
        buildOuterBandPath(OUTER_SOURCE_PATH);
        buildInnerBandPath(INNER_SOURCE_PATH);
        buildDotPath(DOT_SOURCE_PATH);
        RectF outerBounds = new RectF();
        RectF innerBounds = new RectF();
        RectF dotBounds = new RectF();
        OUTER_SOURCE_PATH.computeBounds(outerBounds, true);
        INNER_SOURCE_PATH.computeBounds(innerBounds, true);
        DOT_SOURCE_PATH.computeBounds(dotBounds, true);
        SOURCE_BOUNDS.set(outerBounds);
        SOURCE_BOUNDS.union(innerBounds);
        SOURCE_BOUNDS.union(dotBounds);
        VISUAL_ASPECT_RATIO = SOURCE_BOUNDS.isEmpty()
                ? 1f
                : SOURCE_BOUNDS.width() / Math.max(1f, SOURCE_BOUNDS.height());
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
        transformPath(DOT_SOURCE_PATH, DOT_DRAW_PATH, DRAW_BOUNDS);

        drawPath(canvas, OUTER_DRAW_PATH, resolveOuterBandColor(activeColor, inactiveColor));
        drawPath(canvas, INNER_DRAW_PATH, resolveInnerBandColor(activeColor, inactiveColor));
        drawPath(canvas, DOT_DRAW_PATH, resolveDotColor(activeColor, inactiveColor));
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

    private void drawPath(Canvas canvas, Path path, int color) {
        if (path == null || path.isEmpty()) {
            return;
        }
        FILL_PAINT.setColor(color);
        FILL_PAINT.setColorFilter(colorFilter);
        canvas.drawPath(path, FILL_PAINT);
        FILL_PAINT.setColorFilter(null);
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

    private int resolveDotColor(int activeColor, int inactiveColor) {
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
        path.moveTo(109f, 429f);
        path.lineTo(24f, 344f);
        path.quadTo(116f, 255f, 234f, 207.5f);
        path.quadTo(352f, 160f, 480f, 160f);
        path.quadTo(608f, 160f, 726f, 207.5f);
        path.quadTo(844f, 255f, 936f, 344f);
        path.lineTo(851f, 429f);
        path.quadTo(776f, 357f, 680f, 318.5f);
        path.quadTo(584f, 280f, 480f, 280f);
        path.quadTo(376f, 280f, 280f, 318.5f);
        path.quadTo(184f, 357f, 109f, 429f);
        path.close();
    }

    private static void buildInnerBandPath(Path path) {
        if (path == null) {
            return;
        }
        path.reset();
        path.moveTo(278f, 598f);
        path.lineTo(194f, 514f);
        path.quadTo(253f, 459f, 326.5f, 429.5f);
        path.quadTo(400f, 400f, 480f, 400f);
        path.quadTo(560f, 400f, 633.5f, 429.5f);
        path.quadTo(707f, 459f, 766f, 514f);
        path.lineTo(682f, 598f);
        path.quadTo(640f, 560f, 588.5f, 540f);
        path.quadTo(537f, 520f, 480f, 520f);
        path.quadTo(423f, 520f, 371.5f, 540f);
        path.quadTo(320f, 560f, 278f, 598f);
        path.close();
    }

    private static void buildDotPath(Path path) {
        if (path == null) {
            return;
        }
        path.reset();
        path.moveTo(423.5f, 776.5f);
        path.quadTo(400f, 753f, 400f, 720f);
        path.quadTo(400f, 687f, 423.5f, 663.5f);
        path.quadTo(447f, 640f, 480f, 640f);
        path.quadTo(513f, 640f, 536.5f, 663.5f);
        path.quadTo(560f, 687f, 560f, 720f);
        path.quadTo(560f, 753f, 536.5f, 776.5f);
        path.quadTo(513f, 800f, 480f, 800f);
        path.quadTo(447f, 800f, 423.5f, 776.5f);
        path.close();
    }
}
