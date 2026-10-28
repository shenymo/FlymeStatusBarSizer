package com.example.flymestatusbarsizer.feature.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

final class FloatingBackArrowEffect implements SideBackAnimationEffect {
    private static final PathInterpolator CANCEL_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final PathInterpolator COMMIT_INTERPOLATOR =
            new PathInterpolator(0.2f, 0.75f, 0.3f, 1f);
    private static final PathInterpolator DISTANCE_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0.2f, 1f);

    private final int longDistanceDp;
    private final float upAngleDegrees;
    private final float downAngleDegrees;
    private final int minVerticalDp;

    FloatingBackArrowEffect(ModuleConfig config) {
        longDistanceDp = config == null ? 0 : config.assistantGestureLongDistanceDp;
        upAngleDegrees = config == null ? 24.2f : config.assistantGestureUpAngle / 10f;
        downAngleDegrees = config == null ? 24.2f : config.assistantGestureDownAngle / 10f;
        minVerticalDp = config == null ? 24 : config.assistantGestureMinVerticalDp;
    }

    @Override
    public Session start(View view, MotionEvent downEvent) {
        Context context = view.getContext();
        return new SessionImpl(
                ReflectUtils.getBooleanField(view, "mIsLeftPanel", false),
                downEvent.getRawX(),
                downEvent.getRawY(),
                context.getResources().getDisplayMetrics().density,
                ViewConfiguration.get(context).getScaledTouchSlop(),
                ReflectUtils.getBooleanField(view, "mTriggerBackSilently", false),
                longDistanceDp,
                upAngleDegrees,
                downAngleDegrees,
                minVerticalDp);
    }

    private static final class SessionImpl implements Session {
        private final boolean fromLeft;
        private final boolean silent;
        private final float startX;
        private final float startY;
        private final float density;
        private final float touchSlop;
        private final float longDistancePx;
        private final AssistantGestureState directionState;
        private final float upAngleTan;
        private final float downAngleTan;
        private final Paint arrowOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float inward;
        private float vertical;
        private float lastMoveTime;
        private float releaseProgress;
        private float longProgress;
        private boolean longDistance;
        private boolean started;
        private boolean finishing;
        private boolean committed;
        private ValueAnimator releaseAnimator;
        private ValueAnimator distanceAnimator;

        SessionImpl(boolean fromLeft, float startX, float startY, float density,
                float touchSlop, boolean silent, float longDistanceDp,
                float upAngleDegrees, float downAngleDegrees, float minVerticalDp) {
            this.fromLeft = fromLeft;
            this.silent = silent;
            this.startX = startX;
            this.startY = startY;
            this.density = density;
            this.touchSlop = touchSlop;
            this.longDistancePx = longDistanceDp * density;
            longDistance = longDistancePx <= 0f;
            longProgress = longDistance ? 1f : 0f;
            directionState = new AssistantGestureState();
            directionState.begin(startX, startY, 0f, 12f * density, fromLeft,
                    upAngleDegrees, downAngleDegrees, minVerticalDp * density);
            upAngleTan = (float) Math.tan(Math.toRadians(upAngleDegrees));
            downAngleTan = (float) Math.tan(Math.toRadians(downAngleDegrees));
            lastMoveTime = android.os.SystemClock.uptimeMillis();
            arrowOutline.setColor(0xff000000);
            arrowOutline.setStyle(Paint.Style.STROKE);
            arrowOutline.setStrokeWidth(dp(5f));
            arrowOutline.setStrokeCap(Paint.Cap.ROUND);
            arrowOutline.setStrokeJoin(Paint.Join.ROUND);
            arrow.setColor(0xffffffff);
            arrow.setStyle(Paint.Style.STROKE);
            arrow.setStrokeWidth(dp(2.5f));
            arrow.setStrokeCap(Paint.Cap.ROUND);
            arrow.setStrokeJoin(Paint.Join.ROUND);
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
            directionState.move(x, event.getRawY(), event.getPointerCount());
            if (inward > touchSlop) started = true;
            boolean nextLongDistance = longDistancePx <= 0f
                    || Math.hypot(inward, vertical) >= longDistancePx;
            animateDistanceChange(view, nextLongDistance);
            if (started) view.postInvalidateOnAnimation();
        }

        private void animateDistanceChange(View view, boolean nextLongDistance) {
            if (longDistance == nextLongDistance) return;
            longDistance = nextLongDistance;
            ValueAnimator previous = distanceAnimator;
            distanceAnimator = null;
            if (previous != null) previous.cancel();
            ValueAnimator animator = ValueAnimator.ofFloat(longProgress, nextLongDistance ? 1f : 0f);
            distanceAnimator = animator;
            animator.setDuration(180L);
            animator.setInterpolator(DISTANCE_INTERPOLATOR);
            animator.addUpdateListener(valueAnimator -> {
                longProgress = (Float) valueAnimator.getAnimatedValue();
                view.postInvalidateOnAnimation();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (distanceAnimator == animation) distanceAnimator = null;
                }
            });
            animator.start();
        }

        @Override
        public void draw(View view, Canvas canvas) {
            if (silent) return;
            boolean isLongDistance = this.longDistance;
            int gestureDirection = directionState.direction();
            float edge = fromLeft ? 0f : view.getWidth();
            float direction = fromLeft ? 1f : -1f;
            float offset = dp(16f) + Math.min(dp(12f), inward * 0.08f);
            float alongDistance = cubicBezier(longProgress, 0f, 0.24f, 0.78f, 1f) * dp(22f);
            float arrowY = startY + vertical * 0.2f;
            float vectorX = direction;
            float vectorY = gestureDirection == AssistantGestureState.UP
                    ? -Math.abs(vectorX) * upAngleTan
                    : gestureDirection == AssistantGestureState.DOWN
                    ? Math.abs(vectorX) * downAngleTan : 0f;
            float vectorLength = Math.max(1f, (float) Math.hypot(vectorX, vectorY));
            float unitX = vectorX / vectorLength;
            float unitY = vectorY / vectorLength;
            if (finishing) {
                float finish = releaseProgress;
                if (committed) {
                    if (isLongDistance) alongDistance += dp(20f) * finish;
                } else {
                    offset *= 1f - finish;
                    alongDistance *= 1f - finish;
                }
                arrowOutline.setAlpha(Math.round(180f * (1f - finish)));
                arrow.setAlpha(Math.round(220f * (1f - finish)));
            } else {
                arrowOutline.setAlpha(180);
                arrow.setAlpha(220);
            }
            float tipX = edge + direction * offset + unitX * alongDistance;
            float tipY = arrowY + unitY * alongDistance;
            float length = dp(7f);
            float wing = dp(6f);
            float baseX = tipX - unitX * length;
            float baseY = tipY - unitY * length;
            canvas.drawLine(baseX - unitY * wing, baseY + unitX * wing, tipX, tipY, arrowOutline);
            canvas.drawLine(baseX + unitY * wing, baseY - unitX * wing, tipX, tipY, arrowOutline);
            canvas.drawLine(baseX - unitY * wing, baseY + unitX * wing, tipX, tipY, arrow);
            canvas.drawLine(baseX + unitY * wing, baseY - unitX * wing, tipX, tipY, arrow);
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
            ValueAnimator previousDistanceAnimator = distanceAnimator;
            distanceAnimator = null;
            if (previousDistanceAnimator != null) previousDistanceAnimator.cancel();
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
            ValueAnimator distance = distanceAnimator;
            distanceAnimator = null;
            if (distance != null) distance.cancel();
        }

        private float dp(float value) {
            return value * density;
        }

        private float cubicBezier(float t, float p0, float p1, float p2, float p3) {
            float remaining = 1f - t;
            return remaining * remaining * remaining * p0
                    + 3f * remaining * remaining * t * p1
                    + 3f * remaining * t * t * p2
                    + t * t * t * p3;
        }
    }
}
