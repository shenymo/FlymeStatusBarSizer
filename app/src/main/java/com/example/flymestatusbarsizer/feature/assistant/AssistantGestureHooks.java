package com.example.flymestatusbarsizer.feature.assistant;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class AssistantGestureHooks {
    private static final Map<Object, Gesture> GESTURES = new WeakHashMap<>();

    static void install(FlymeStatusBarSizer module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler", false, loader);
        Method motion = AssistantReflection.method(type, "onMotionEvent", MotionEvent.class);
        try { SideBackAnimation.install(module, loader); }
        catch (Throwable t) { AssistantHooks.warn("Cannot install custom edge back animation", t); }
        Method cancel = AssistantReflection.method(type, "cancelGesture", MotionEvent.class);
        Method pilfer = AssistantReflection.method(type, "pilferPointers");
        Method sendEvent = AssistantReflection.method(type, "sendEvent", int.class, int.class);
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            module.intercept(constructor, chain -> {
                Object result = chain.proceed();
                com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.trackEdgeHandler(chain.getThisObject());
                Object c = ReflectUtils.getField(chain.getThisObject(), "mContext");
                if (c instanceof Context) AssistantClient.get((Context) c);
                return result;
            });
        }
        module.intercept(motion, chain -> {
            Object owner = chain.getThisObject();
            com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.trackEdgeHandler(owner);
            MotionEvent event = (MotionEvent) chain.getArg(0);
            Gesture gesture;
            synchronized (GESTURES) {
                gesture = GESTURES.get(owner);
                if (gesture == null) {
                    Object c = ReflectUtils.getField(owner, "mContext");
                    if (!(c instanceof Context) || Looper.myLooper() == null) return chain.proceed();
                    gesture = new Gesture(owner, (Context) c, cancel, pilfer, sendEvent,
                            AssistantClient.get((Context) c));
                    GESTURES.put(owner, gesture);
                }
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                gesture.reset();
                Object result = chain.proceed();
                gesture.begin(event);
                return result;
            }
            if (gesture.consumed) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) gesture.reset();
                return null;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                gesture.reset();
                return chain.proceed();
            }
            gesture.move(event);
            if (action == MotionEvent.ACTION_MOVE) gesture.updateAnimation();
            // UP must be intercepted before the native plugin commits back.
            if (action == MotionEvent.ACTION_UP) {
                boolean claimed = gesture.tryClaim(true);
                gesture.reset();
                return claimed ? null : chain.proceed();
            }
            Object result = chain.proceed();
            if (action == MotionEvent.ACTION_MOVE) {
                gesture.updateAnimationProgress();
                gesture.tryClaim(false);
            }
            return result;
        });
    }

    static final class Gesture {
        final WeakReference<Object> owner;
        final Context context;
        final Handler handler = new Handler(Looper.myLooper());
        final AssistantGestureState state = new AssistantGestureState();
        final SideGestureActions.Action globalAssistant;
        final Method cancel, pilfer;
        MotionEvent last;
        boolean consumed;
        boolean fromLeft;
        boolean triggerOnRelease;
        final Method sendEvent;
        int horizontalActionId, upActionId, downActionId;

        Gesture(Object owner, Context context, Method cancel, Method pilfer,
                Method sendEvent, SideGestureActions.Action globalAssistant) {
            this.owner = new WeakReference<>(owner);
            this.context = context;
            this.cancel = cancel;
            this.pilfer = pilfer;
            this.sendEvent = sendEvent;
            this.globalAssistant = globalAssistant;
        }

        void begin(MotionEvent event) {
            Object target = owner.get();
            ModuleConfig config = ModuleConfig.load(context);
            // The input monitor sees ordinary taps too. Reject them before readiness can
            // query ActivityTaskManager or another action's remote service.
            if (target == null || !config.enabled || !config.assistantGestureEnabled
                    || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) || event.getPointerCount() != 1
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || ReflectUtils.getIntField(target, "mDisplayId", -1) != 0
                    || ReflectUtils.invokeNoArgInt(event, "getDisplayId", -1) != 0
                    || ReflectUtils.getBooleanField(target, "mIsTrackpadThreeFingerSwipe", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false)) return;
            boolean leftEdge = ReflectUtils.getBooleanField(target, "mIsOnLeftEdge", false);
            if (locked()) return;
            float density = context.getResources().getDisplayMetrics().density;
            state.begin(event.getX(), event.getY(), config.assistantGestureDistanceDp * density,
                    12f * density, leftEdge,
                    config.assistantGestureVerticalLimitEnabled
                            ? config.assistantGestureVerticalLimitDp * density : Float.POSITIVE_INFINITY,
                    24f * density);
            fromLeft = leftEdge;
            triggerOnRelease = config.assistantGestureTriggerMode
                    == SettingsStore.ASSISTANT_GESTURE_TRIGGER_ON_RELEASE;
            horizontalActionId = leftEdge ? config.sideGestureLeftAction : config.sideGestureRightAction;
            upActionId = leftEdge ? config.sideGestureLeftUpAction : config.sideGestureRightUpAction;
            downActionId = leftEdge ? config.sideGestureLeftDownAction : config.sideGestureRightDownAction;
            remember(event);
        }

        void move(MotionEvent event) {
            if (last == null) return;
            // Process batched samples so direction changes cannot be skipped.
            for (int i = 0; i < event.getHistorySize(); i++) {
                state.move(event.getHistoricalX(i), event.getHistoricalY(i), event.getPointerCount());
            }
            state.move(event.getX(), event.getY(), event.getPointerCount());
            remember(event);
        }

        boolean tryClaim(boolean onRelease) {
            Object target = owner.get();
            if (last == null || target == null || consumed || !state.ready()) return false;
            if (triggerOnRelease && !onRelease) return false;
            ModuleConfig config = ModuleConfig.load(context);
            if (!config.enabled || !config.assistantGestureEnabled
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false)
                    || locked()) { reset(); return false; }
            int direction = state.direction();
            int actionId = actionId(direction);
            if (actionId != configuredAction(config, direction)) { reset(); return false; }
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target)) {
                return false;
            }
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK) return false;
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant);
            boolean noAction = actionId == SettingsStore.SIDE_GESTURE_ACTION_NONE;
            boolean navigation = isNavigationAction(actionId);
            if (!noAction && !navigation && (action == null || !action.isReady())) { reset(); return false; }
            try {
                // Native predictive back may defer input ownership: ensure the old app loses this stream.
                pilfer.invoke(target);
                cancel.invoke(target, last);
                state.claim();
                consumed = true;
                Object panel = ReflectUtils.getField(target, "mEdgeBackPlugin");
                Runnable haptic = () -> handler.post(() -> HapticFeedbackUtils.perform(
                        context, panel instanceof View ? (View) panel : null, HapticFeedbackConstants.LONG_PRESS));
                if (noAction) {
                    return true;
                } else if (navigation) {
                    executeNavigationAction(target, actionId);
                    haptic.run();
                } else {
                    action.execute(fromLeft, haptic);
                }
                return true;
            } catch (Throwable t) {
                AssistantHooks.warn("Cannot take over edge back", t);
                reset();
                return false;
            }
        }

        int actionId(int direction) {
            if (direction == AssistantGestureState.UP) return upActionId;
            if (direction == AssistantGestureState.DOWN) return downActionId;
            return horizontalActionId;
        }

        int configuredAction(ModuleConfig config, int direction) {
            if (fromLeft) {
                if (direction == AssistantGestureState.UP) return config.sideGestureLeftUpAction;
                if (direction == AssistantGestureState.DOWN) return config.sideGestureLeftDownAction;
                return config.sideGestureLeftAction;
            }
            if (direction == AssistantGestureState.UP) return config.sideGestureRightUpAction;
            if (direction == AssistantGestureState.DOWN) return config.sideGestureRightDownAction;
            return config.sideGestureRightAction;
        }

        boolean isNavigationAction(int actionId) {
            return actionId == SettingsStore.SIDE_GESTURE_ACTION_HOME
                    || actionId == SettingsStore.SIDE_GESTURE_ACTION_RECENTS;
        }

        void executeNavigationAction(Object target, int actionId) throws ReflectiveOperationException {
            int keyCode = actionId == SettingsStore.SIDE_GESTURE_ACTION_HOME
                    ? KeyEvent.KEYCODE_HOME : KeyEvent.KEYCODE_APP_SWITCH;
            Object down = sendEvent.invoke(target, 0, keyCode);
            Object up = sendEvent.invoke(target, 1, keyCode);
            if (Boolean.FALSE.equals(down) || Boolean.FALSE.equals(up)) {
                throw new IllegalStateException("System navigation key injection failed");
            }
        }

        void updateAnimation() {
            Object target = owner.get();
            if (target == null || last == null) return;
            Object panel = ReflectUtils.getField(target, "mEdgeBackPlugin");
            if (panel != null) {
                float centerY = state.direction() == AssistantGestureState.HORIZONTAL
                        ? state.startY() : state.startY() + state.verticalDelta() * 0.5f;
                ReflectUtils.setFloatField(panel, "mCenterY", centerY);
                if (panel instanceof View) ((View) panel).postInvalidateOnAnimation();
            }
        }

        void updateAnimationProgress() {
            if (state.direction() == AssistantGestureState.HORIZONTAL) return;
            Object target = owner.get();
            Object panel = ReflectUtils.getField(target, "mEdgeBackPlugin");
            if (!(panel instanceof View)) return;
            float width = ((View) panel).getWidth();
            if (width <= 0) return;
            Object pathWidth = ReflectUtils.getField(panel, "mBackViewWidth");
            Object pathHeight = ReflectUtils.getField(panel, "mMaxHeight");
            if (!(pathWidth instanceof Float) || !(pathHeight instanceof Float)) return;
            float stretchedHeight = (Float) pathWidth + Math.abs(state.verticalDelta());
            ReflectUtils.invokeMethod(panel, "computePath",
                    new Class<?>[]{float.class, float.class, float.class},
                    state.travelDistance() / width, stretchedHeight, pathHeight);
            ((View) panel).postInvalidateOnAnimation();
        }

        boolean locked() {
            KeyguardManager manager = context.getSystemService(KeyguardManager.class);
            return manager != null && manager.isKeyguardLocked();
        }

        void remember(MotionEvent event) {
            if (last != null) last.recycle();
            last = MotionEvent.obtain(event);
        }

        void reset() {
            state.cancel();
            consumed = false;
            if (last != null) { last.recycle(); last = null; }
        }
    }
}
