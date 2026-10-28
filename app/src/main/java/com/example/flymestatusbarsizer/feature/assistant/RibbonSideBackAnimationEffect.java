package com.example.flymestatusbarsizer.feature.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;

import com.example.flymestatusbarsizer.util.ReflectUtils;

final class RibbonSideBackAnimationEffect implements SideBackAnimationEffect {
    private static final PathInterpolator CANCEL_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final PathInterpolator COMMIT_INTERPOLATOR =
            new PathInterpolator(0.2f, 0.75f, 0.3f, 1f);

    @Override
    public Session start(View view, MotionEvent downEvent) {
        Context context = view.getContext();
        return new RibbonSession(
                ReflectUtils.getBooleanField(view, "mIsLeftPanel", false),
                downEvent.getRawX(),
                downEvent.getRawY(),
                context.getResources().getDisplayMetrics().density,
                ViewConfiguration.get(context).getScaledTouchSlop(),
                ReflectUtils.getBooleanField(view, "mTriggerBackSilently", false));
    }

    private static final class RibbonSession implements Session {
        private final boolean fromLeft;
        private final boolean silent;
        private final float startX;
        private final float startY;
        private final float density;
        private final float touchSlop;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path ribbon = new Path();
        private float inward;
        private float vertical;
        private float lastMoveTime;
        private float releaseProgress;
        private boolean started;
        private boolean finishing;
        private boolean committed;
        private ValueAnimator releaseAnimator;

        RibbonSession(boolean fromLeft, float startX, float startY, float density,
                float touchSlop, boolean silent) {
            this.fromLeft = fromLeft;
            this.silent = silent;
            this.startX = startX;
            this.startY = startY;
            this.density = density;
            this.touchSlop = touchSlop;
            lastMoveTime = android.os.SystemClock.uptimeMillis();
            fill.setColor(0x4c3c3c3c);
            fill.setStyle(Paint.Style.FILL);
            outline.setColor(0x28ffffff);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(1.25f));
            highlight.setColor(0x35ffffff);
            highlight.setStyle(Paint.Style.STROKE);
            highlight.setStrokeWidth(dp(1.5f));
        }

        @Override
        public boolean hasStarted() {
            return started;
        }

        @Override
        public void move(View view, MotionEvent event) {
            float x = event.getRawX();
            inward = Math.max(0f, fromLeft ? x - startX : startX - x);
            float targetVertical = event.getRawY() - startY;
            float elapsed = Math.max(1f, event.getEventTime() - lastMoveTime);
            float smoothing = 1f - (float) Math.exp(-elapsed / 30f);
            vertical += (targetVertical - vertical) * smoothing;
            lastMoveTime = event.getEventTime();
            if (inward > touchSlop) started = true;
            if (started) view.postInvalidateOnAnimation();
        }

        @Override
        public void draw(View view, Canvas canvas) {
            if (silent) return;
            float distance = (float) Math.hypot(inward, vertical);
            float reach = dp(28f) + Math.min(dp(170f), inward * 0.72f);
            float trackedVertical = vertical;
            float opacity = 1f;
            float scale = 1f;
            if (finishing) {
                float finish = releaseProgress;
                if (committed) {
                    float pop = clamp(finish / 0.3f);
                    float settle = clamp((finish - 0.3f) / 0.7f);
                    settle = settle * settle * (3f - 2f * settle);
                    reach = reach * (1f - 0.28f * settle) + dp(14f) * pop;
                    scale = 1f - 0.35f * settle;
                    opacity = 1f - finish;
                } else {
                    reach *= 1f - finish;
                    trackedVertical *= 1f - finish;
                    scale = 1f - 0.3f * finish;
                    opacity = 1f - finish;
                }
            }

            float edge = fromLeft ? 0f : view.getWidth();
            float direction = fromLeft ? 1f : -1f;
            float endX = edge + direction * reach;
            float endY = startY + trackedVertical * 0.58f * scale;
            float thickness = (dp(10f) + Math.min(dp(9f), distance * 0.045f)) * scale;
            float curve = Math.min(dp(48f), Math.abs(trackedVertical) * 0.35f) * scale;
            float startTop = startY - thickness * 0.52f;
            float startBottom = startY + thickness * 0.52f;
            float endThickness = thickness * 1.22f;
            ribbon.reset();
            ribbon.moveTo(edge, startTop);
            ribbon.cubicTo(edge + direction * reach * 0.28f, startTop,
                    endX - direction * reach * 0.25f, endY - endThickness / 2f - curve,
                    endX, endY - endThickness / 2f);
            ribbon.cubicTo(endX + direction * dp(8f), endY,
                    endX + direction * dp(8f), endY,
                    endX, endY + endThickness / 2f);
            ribbon.cubicTo(endX - direction * reach * 0.25f, endY + endThickness / 2f + curve,
                    edge + direction * reach * 0.28f, startBottom, edge, startBottom);
            ribbon.close();
            fill.setAlpha(Math.round(76f * opacity));
            outline.setAlpha(Math.round(42f * opacity));
            highlight.setAlpha(Math.round(54f * opacity));
            canvas.drawPath(ribbon, fill);
            canvas.drawPath(ribbon, outline);
            canvas.drawCircle(endX, endY, endThickness * 0.33f, highlight);
        }

        @Override
        public void finish(View view, boolean committed, Runnable onFinished) {
            if (!started) {
                view.setVisibility(View.GONE);
                onFinished.run();
                return;
            }
            if (silent) {
                onFinished.run();
                return;
            }
            ValueAnimator previousAnimator = releaseAnimator;
            releaseAnimator = null;
            if (previousAnimator != null) previousAnimator.cancel();
            finishing = true;
            this.committed = committed;
            releaseProgress = 0f;
            view.setVisibility(View.VISIBLE);
            releaseAnimator = ValueAnimator.ofFloat(0f, 1f);
            releaseAnimator.setDuration(committed ? 196L : 228L);
            releaseAnimator.setInterpolator(committed ? COMMIT_INTERPOLATOR : CANCEL_INTERPOLATOR);
            releaseAnimator.addUpdateListener(animator -> {
                releaseProgress = (Float) animator.getAnimatedValue();
                view.postInvalidateOnAnimation();
            });
            releaseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (releaseAnimator != animation) return;
                    releaseAnimator = null;
                    view.setVisibility(View.GONE);
                    onFinished.run();
                }
            });
            releaseAnimator.start();
        }

        @Override
        public void cancel() {
            ValueAnimator animator = releaseAnimator;
            releaseAnimator = null;
            if (animator != null) animator.cancel();
        }

        private float dp(float value) {
            return value * density;
        }

        private static float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }
}
