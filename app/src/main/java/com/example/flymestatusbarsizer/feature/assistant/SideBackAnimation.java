package com.example.flymestatusbarsizer.feature.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class SideBackAnimation {
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
        private float inward, vertical;
        private float releaseScale = 1f;
        private boolean active = true;
        private boolean gestureStarted;
        private ValueAnimator releaseAnimator;

        State(boolean fromLeft, float startX, float startY, float density, float touchSlop,
                boolean silent) {
            this.fromLeft = fromLeft;
            this.silent = silent;
            this.startX = startX;
            this.startY = startY;
            this.density = density;
            this.touchSlop = touchSlop;
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
            vertical = event.getRawY() - startY;
            if (inward > touchSlop) {
                gestureStarted = true;
                view.postInvalidateOnAnimation();
            }
        }

        void draw(View view, Canvas canvas) {
            if (silent) return;
            float edge = fromLeft ? 0f : view.getWidth();
            float direction = fromLeft ? 1f : -1f;
            float distance = (float) Math.hypot(inward, vertical);
            float reach = (dp(28) + Math.min(dp(170), inward * 0.72f)) * releaseScale;
            float endX = edge + direction * reach;
            float endY = startY + vertical * 0.58f * releaseScale;
            float thickness = dp(10) + Math.min(dp(9), distance * 0.045f);
            float curve = Math.min(dp(48), Math.abs(vertical) * 0.35f);
            float startTop = startY - thickness * 0.52f;
            float startBottom = startY + thickness * 0.52f;
            float endThickness = thickness * 1.22f;
            ribbon.reset();
            ribbon.moveTo(edge, startTop);
            ribbon.cubicTo(edge + direction * reach * 0.28f, startTop,
                    endX - direction * reach * 0.25f, endY - endThickness / 2f - curve,
                    endX, endY - endThickness / 2f);
            ribbon.cubicTo(endX + direction * dp(8), endY,
                    endX + direction * dp(8), endY,
                    endX, endY + endThickness / 2f);
            ribbon.cubicTo(endX - direction * reach * 0.25f, endY + endThickness / 2f + curve,
                    edge + direction * reach * 0.28f, startBottom, edge, startBottom);
            ribbon.close();
            fill.setAlpha(Math.round(76 * releaseScale));
            outline.setAlpha(Math.round(42 * releaseScale));
            highlight.setAlpha(Math.round(54 * releaseScale));
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
            if (releaseAnimator != null) releaseAnimator.cancel();
            view.setVisibility(View.VISIBLE);
            releaseAnimator = ValueAnimator.ofFloat(1f, 0f);
            releaseAnimator.setDuration(committed ? 176L : 220L);
            releaseAnimator.setInterpolator(new DecelerateInterpolator());
            releaseAnimator.addUpdateListener(animator -> {
                releaseScale = (Float) animator.getAnimatedValue();
                view.postInvalidateOnAnimation();
            });
            releaseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (releaseAnimator != animation) return;
                    active = false;
                    synchronized (STATES) {
                        if (STATES.get(view) == State.this) STATES.remove(view);
                    }
                    view.setVisibility(View.GONE);
                }
            });
            releaseAnimator.start();
        }

        void cancel() {
            active = false;
            if (releaseAnimator != null) releaseAnimator.cancel();
        }

        private float dp(float value) { return value * density; }
    }
}
