package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class SideBackAnimation {
    private static final Map<View, ActiveGesture> GESTURES = new WeakHashMap<>();

    private SideBackAnimation() { }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName(
                "com.flyme.systemui.navigationbar.gestural.EdgeBackView", false, loader);
        Method draw = AssistantReflection.method(type, "onDraw", Canvas.class);
        module.intercept(draw, chain -> {
            View view = (View) chain.getThisObject();
            ActiveGesture gesture;
            synchronized (GESTURES) { gesture = GESTURES.get(view); }
            if (gesture == null || !gesture.active || !gesture.animation.hasStarted()) {
                return chain.proceed();
            }
            gesture.animation.draw(view, (Canvas) chain.getArg(0));
            return null;
        });

        Method motion = AssistantReflection.method(type, "onMotionEvent", MotionEvent.class);
        module.intercept(motion, chain -> {
            View view = (View) chain.getThisObject();
            MotionEvent event = (MotionEvent) chain.getArg(0);
            ActiveGesture gesture = gestureFor(view, event);
            if (gesture == null) return chain.proceed();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                gesture.animation.move(view, event);
            }
            Object result = chain.proceed();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                boolean committed = action == MotionEvent.ACTION_UP
                        && ReflectUtils.getBooleanField(view, "mTriggerBack", false);
                gesture.finish(view, committed);
            }
            return result;
        });
    }

    private static ActiveGesture gestureFor(View view, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            synchronized (GESTURES) { return GESTURES.get(view); }
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!config.enabled || !config.assistantGestureEnabled) {
            clear(view);
            return null;
        }
        SideBackAnimationEffect effect = createEffect(config.sideBackAnimationStyle, config);
        ActiveGesture gesture = new ActiveGesture(effect.start(view, event));
        synchronized (GESTURES) {
            ActiveGesture previous = GESTURES.put(view, gesture);
            if (previous != null) previous.cancel();
        }
        return gesture;
    }

    private static SideBackAnimationEffect createEffect(int style, ModuleConfig config) {
        switch (style) {
            case SettingsStore.SIDE_BACK_ANIMATION_FLOATING_ARROW:
                return new FloatingBackArrowEffect(config);
            case SettingsStore.SIDE_BACK_ANIMATION_RIBBON:
            default:
                return new RibbonSideBackAnimationEffect();
        }
    }

    private static void clear(View view) {
        ActiveGesture previous;
        synchronized (GESTURES) { previous = GESTURES.remove(view); }
        if (previous != null) previous.cancel();
    }

    private static void finish(View view, ActiveGesture gesture) {
        synchronized (GESTURES) {
            if (GESTURES.get(view) == gesture) GESTURES.remove(view);
        }
        gesture.active = false;
    }

    private static final class ActiveGesture {
        private final SideBackAnimationEffect.Session animation;
        private boolean active = true;

        ActiveGesture(SideBackAnimationEffect.Session animation) {
            this.animation = animation;
        }

        void finish(View view, boolean committed) {
            animation.finish(view, committed, () -> SideBackAnimation.finish(view, this));
        }

        void cancel() {
            active = false;
            animation.cancel();
        }
    }
}
