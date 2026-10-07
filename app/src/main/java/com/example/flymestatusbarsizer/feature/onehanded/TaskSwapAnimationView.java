package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.List;

/** Draw all previews in one topmost window so the two moving cards can cross the left margin. */
final class TaskSwapAnimationView extends View {
    static final long DURATION_MS = 280;
    private final RecentTaskCard[] cards;
    private final RectF[] cardBounds;
    private final int selectedIndex;
    private final RecentTaskCard oldMain;
    private final RectF mainBounds;
    private final RectF incoming = new RectF();
    private final RectF outgoing = new RectF();
    private final RectF imageBounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final float cardRadius;
    private ValueAnimator animator;
    private float progress;
    private boolean finished;

    TaskSwapAnimationView(Context context, List<RecentTaskCard> cards, Rect[] positions,
            int selectedIndex, RecentTaskCard oldMain, Rect mainBounds) {
        super(context);
        this.cards = cards.toArray(new RecentTaskCard[0]);
        this.cardBounds = new RectF[positions.length];
        for (int i = 0; i < positions.length; i++) cardBounds[i] = new RectF(positions[i]);
        this.selectedIndex = selectedIndex;
        this.oldMain = oldMain;
        this.mainBounds = new RectF(mainBounds);
        cardRadius = 4 * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateProgress(0f);
    }

    void start() {
        if (animator != null || finished) return;
        ValueAnimator next = ValueAnimator.ofFloat(0f, 1f);
        animator = next;
        next.setDuration(DURATION_MS);
        next.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
        next.addUpdateListener(value -> {
            if (animator == next) updateProgress((float) value.getAnimatedValue());
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next) return;
                animator = null;
                updateProgress(1f);
                finished = true;
            }
        });
        next.start();
    }

    boolean isFinished() { return finished; }

    void cancel() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) previous.cancel();
    }

    @Override protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }

    private void updateProgress(float value) {
        progress = value;
        interpolate(cardBounds[selectedIndex], mainBounds, value, incoming);
        interpolate(mainBounds, cardBounds[selectedIndex], value, outgoing);
        invalidate();
    }

    static void interpolate(RectF from, RectF to, float fraction, RectF result) {
        float p = Math.max(0f, Math.min(1f, fraction));
        result.set(from.left + (to.left - from.left) * p,
                from.top + (to.top - from.top) * p,
                from.right + (to.right - from.right) * p,
                from.bottom + (to.bottom - from.bottom) * p);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(20, 22, 26));
        for (int i = 0; i < cards.length; i++) {
            if (i != selectedIndex) drawPreview(canvas, cards[i].preview, cardBounds[i], cardRadius);
        }
        drawPreview(canvas, oldMain.preview, outgoing, cardRadius * progress);
        drawPreview(canvas, cards[selectedIndex].preview, incoming, cardRadius * (1f - progress));
    }

    private void drawPreview(Canvas canvas, Bitmap bitmap, RectF bounds, float radius) {
        int save = canvas.save();
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        paint.setColor(Color.rgb(32, 34, 38));
        canvas.drawRect(bounds, paint);
        if (bitmap != null && !bitmap.isRecycled()) {
            float scale = Math.min(bounds.width() / bitmap.getWidth(), bounds.height() / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            imageBounds.set(bounds.centerX() - width / 2, bounds.centerY() - height / 2,
                    bounds.centerX() + width / 2, bounds.centerY() + height / 2);
            canvas.drawBitmap(bitmap, null, imageBounds, paint);
        }
        canvas.restoreToCount(save);
    }
}
