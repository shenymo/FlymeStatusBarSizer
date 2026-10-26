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

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class SideBackAnimation {
    private static final PathInterpolator CANCEL_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final PathInterpolator COMMIT_INTERPOLATOR =
            new PathInterpolator(0.2f, 0.75f, 0.3f, 1f);
    private static final Map<View, State> STATES = new WeakHashMap<>();

    private SideBackAnimation() { }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName(
                "com.flyme.systemui.navigationbar.gestural.EdgeBackView", false, loader);
        Method draw = AssistantReflection.method(type, "onDraw", Canvas.class);
        module.intercept(draw, chain -> {
            View view = (View) chain.getThisObject();
            State state;
            synchronized (STATES) { state = STATES.get(view); }
            if (state == null || !state.active || !state.gestureStarted) return chain.proceed();
            state.draw(view, (Canvas) chain.getArg(0));
            return null;
        });

        Method motion = AssistantReflection.method(type, "onMotionEvent", MotionEvent.class);
        module.intercept(motion, chain -> {
            View view = (View) chain.getThisObject();
            MotionEvent event = (MotionEvent) chain.getArg(0);
            State state = stateFor(view, event);
            if (state == null) return chain.proceed();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                state.move(view, event);
            }
            Object result = chain.proceed();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                boolean committed = action == MotionEvent.ACTION_UP
                        && ReflectUtils.getBooleanField(view, "mTriggerBack", false);
                state.finish(view, committed);
            }
            return result;
        });
    }

    private static State stateFor(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            Context context = view.getContext();
            ModuleConfig config = ModuleConfig.load(context);
            boolean left = ReflectUtils.getBooleanField(view, "mIsLeftPanel", false);
            if (!config.enabled || !config.assistantGestureEnabled) {
                clear(view);
                return null;
            }
            State state = new State(left, event.getRawX(), event.getRawY(), context.getResources()
                    .getDisplayMetrics().density,
                    ViewConfiguration.get(context).getScaledTouchSlop(),
                    ReflectUtils.getBooleanField(view, "mTriggerBackSilently", false));
            synchronized (STATES) {
                State previous = STATES.put(view, state);
                if (previous != null) previous.cancel();
            }
            return state;
        }
        synchronized (STATES) { return STATES.get(view); }
    }

    private static void clear(View view) {
        State previous;
        synchronized (STATES) { previous = STATES.remove(view); }
        if (previous != null) previous.cancel();
    }

    private static final class State {
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
        private float inward, vertical, lastMoveTime;
        private float releaseProgress;
        private boolean active = true;
        private boolean gestureStarted;
        private boolean finishing;
        private boolean committed;
        private ValueAnimator releaseAnimator;

        State(boolean fromLeft, float startX, float startY, float density, float touchSlop,
                boolean silent) {
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
            inward = 0f;
        }

        void move(View view, MotionEvent event) {
            float x = event.getRawX();
            inward = Math.max(0f, fromLeft ? x - startX : startX - x);
            float targetVertical = event.getRawY() - startY;
            float eventTime = event.getEventTime();
            float elapsed = Math.max(1f, eventTime - lastMoveTime);
            float smoothing = 1f - (float) Math.exp(-elapsed / 30f);
            vertical += (targetVertical - vertical) * smoothing;
            lastMoveTime = eventTime;
            if (inward > touchSlop) gestureStarted = true;
            if (gestureStarted) {
                view.postInvalidateOnAnimation();
            }
        }

        void draw(View view, Canvas canvas) {
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

        void finish(View view, boolean committed) {
            if (!active) return;
            if (!gestureStarted) {
                active = false;
                synchronized (STATES) {
                    if (STATES.get(view) == State.this) STATES.remove(view);
                }
                view.setVisibility(View.GONE);
                return;
            }
            if (silent) {
                active = false;
                synchronized (STATES) {
                    if (STATES.get(view) == State.this) STATES.remove(view);
                }
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
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (releaseAnimator != animation) return;
                    active = false;
                    boolean ownsView;
                    synchronized (STATES) {
                        ownsView = STATES.get(view) == State.this;
                        if (ownsView) STATES.remove(view);
                    }
                    if (ownsView) view.setVisibility(View.GONE);
                }
            });
            releaseAnimator.start();
        }

        void cancel() {
            active = false;
            ValueAnimator animator = releaseAnimator;
            releaseAnimator = null;
            if (animator != null) animator.cancel();
        }

        private float dp(float value) { return value * density; }

        private static float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }
}
