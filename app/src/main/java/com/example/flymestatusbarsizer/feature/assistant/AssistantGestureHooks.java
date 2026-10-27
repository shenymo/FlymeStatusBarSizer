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
import android.view.ViewConfiguration;

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
        QuickToolsPanel.install(module, loader);
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
            gesture.updateActionPreview();
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
        final SideGestureActions.Action taskSwitcher;
        final SideGestureActions.Action quickTools;
        final SideGestureActions.Action appLauncher;
        final Method cancel, pilfer;
        MotionEvent last;
        boolean consumed;
        boolean fromLeft;
        boolean triggerOnRelease;
        boolean actionPreviewEnabled;
        boolean zoneOverlayEnabled;
        boolean distanceSplitEnabled;
        boolean gestureStarted;
        int hapticZone = -1;
        float gestureTouchSlop;
        float gestureStartX;
        float shortDistancePx;
        float longDistancePx;
        final AssistantGestureActionOverlay actionOverlay;
        final AssistantGestureZoneOverlay zoneOverlay;
        final Method sendEvent;
        int horizontalActionId, upActionId, downActionId;
        int horizontalLongActionId, upLongActionId, downLongActionId;

        Gesture(Object owner, Context context, Method cancel, Method pilfer,
                Method sendEvent, SideGestureActions.Action globalAssistant) {
            this.owner = new WeakReference<>(owner);
            this.context = context;
            this.cancel = cancel;
            this.pilfer = pilfer;
            this.sendEvent = sendEvent;
            this.globalAssistant = globalAssistant;
            this.taskSwitcher = com.example.flymestatusbarsizer.feature.onehanded.RecentTaskSwitcher.action(context);
            this.quickTools = QuickToolsPanel.action(context);
            this.appLauncher = AppLauncherPanel.action(context);
            this.actionOverlay = new AssistantGestureActionOverlay(context);
            this.zoneOverlay = new AssistantGestureZoneOverlay(context);
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
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            Object touchSlop = ReflectUtils.getField(target, "mTouchSlop");
            gestureTouchSlop = touchSlop instanceof Float
                    ? Math.max(0f, (Float) touchSlop)
                    : ViewConfiguration.get(context).getScaledTouchSlop() * 0.75f;
            gestureStartX = rawX;
            shortDistancePx = config.assistantGestureDistanceDp * density;
            hapticZone = -1;
            state.begin(rawX, rawY, shortDistancePx,
                    12f * density, leftEdge, config.assistantGestureUpAngle / 10f,
                    config.assistantGestureDownAngle / 10f,
                    config.assistantGestureMinVerticalDp * density);
            fromLeft = leftEdge;
            zoneOverlayEnabled = config.assistantGestureZoneOverlayEnabled;
            actionPreviewEnabled = config.assistantGesturePreviewEnabled;
            distanceSplitEnabled = config.assistantGestureDistanceSplitEnabled;
            triggerOnRelease = distanceSplitEnabled || config.assistantGestureTriggerMode
                    == SettingsStore.ASSISTANT_GESTURE_TRIGGER_ON_RELEASE;
            longDistancePx = config.assistantGestureLongDistanceDp * density;
            horizontalActionId = leftEdge ? config.sideGestureLeftAction : config.sideGestureRightAction;
            upActionId = leftEdge ? config.sideGestureLeftUpAction : config.sideGestureRightUpAction;
            downActionId = leftEdge ? config.sideGestureLeftDownAction : config.sideGestureRightDownAction;
            horizontalLongActionId = leftEdge
                    ? config.sideGestureLeftLongAction : config.sideGestureRightLongAction;
            upLongActionId = leftEdge
                    ? config.sideGestureLeftUpLongAction : config.sideGestureRightUpLongAction;
            downLongActionId = leftEdge
                    ? config.sideGestureLeftDownLongAction : config.sideGestureRightDownLongAction;
            remember(event);
        }

        void move(MotionEvent event) {
            if (last == null) return;
            // Process batched samples so direction changes cannot be skipped.
            float rawOffsetX = event.getRawX() - event.getX();
            float rawOffsetY = event.getRawY() - event.getY();
            for (int i = 0; i < event.getHistorySize(); i++) {
                state.move(event.getHistoricalX(i) + rawOffsetX,
                        event.getHistoricalY(i) + rawOffsetY, event.getPointerCount());
                updateDistanceHaptics();
            }
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            state.move(rawX, rawY, event.getPointerCount());
            updateDistanceHaptics();
            if (!gestureStarted && state.inwardDistance() > gestureTouchSlop) {
                gestureStarted = true;
                if (zoneOverlayEnabled) {
                    ModuleConfig config = ModuleConfig.load(context);
                    float density = context.getResources().getDisplayMetrics().density;
                    zoneOverlay.show(gestureStartX, state.startY(), fromLeft, shortDistancePx,
                            longDistancePx, distanceSplitEnabled, horizontalActionId, upActionId,
                            downActionId, horizontalLongActionId, upLongActionId,
                            downLongActionId, config.assistantGestureUpAngle / 10f,
                            config.assistantGestureDownAngle / 10f,
                            config.assistantGestureMinVerticalDp * density);
                }
            }
            if (zoneOverlayEnabled && gestureStarted) zoneOverlay.updatePointer(rawX, rawY);
            remember(event);
        }

        void updateDistanceHaptics() {
            if (!state.ready()) {
                hapticZone = -1;
                return;
            }
            int direction = state.direction();
            boolean longDistance = distanceSplitEnabled && state.travelDistance() >= longDistancePx;
            int zone = direction + (longDistance ? 3 : 0);
            if (zone == hapticZone) return;
            hapticZone = zone;
            int actionId = actionId(direction);
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_NONE
                    || actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK) return;
            Object target = owner.get();
            if (target == null
                    || (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(ModuleConfig.load(context).assistantGestureScenes, target))) {
                return;
            }
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher);
            if (!isNavigationAction(actionId) && (action == null || !action.isReady())) return;
            Object panel = target == null ? null : ReflectUtils.getField(target, "mEdgeBackPlugin");
            int feedback = longDistance ? HapticFeedbackConstants.LONG_PRESS
                    : HapticFeedbackConstants.CLOCK_TICK;
            handler.post(() -> HapticFeedbackUtils.perform(context,
                    panel instanceof View ? (View) panel : null, feedback));
        }

        boolean tryClaim(boolean onRelease) {
            Object target = owner.get();
            if (last == null || target == null || consumed || !gestureStarted || !state.ready()) {
                return false;
            }
            if (triggerOnRelease && !onRelease) return false;
            ModuleConfig config = ModuleConfig.load(context);
            if (!config.enabled || !config.assistantGestureEnabled
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false)
                    || config.assistantGestureDistanceSplitEnabled != distanceSplitEnabled
                    || (distanceSplitEnabled && config.assistantGestureLongDistanceDp
                            * context.getResources().getDisplayMetrics().density != longDistancePx)
                    || locked()) { reset(); return false; }
            int direction = state.direction();
            int actionId = actionId(direction);
            boolean longDistance = distanceSplitEnabled && state.travelDistance() >= longDistancePx;
            if (actionId != configuredAction(config, direction, longDistance)) { reset(); return false; }
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target)) {
                return false;
            }
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK) return false;
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher);
            boolean noAction = actionId == SettingsStore.SIDE_GESTURE_ACTION_NONE;
            boolean navigation = isNavigationAction(actionId);
            if (!noAction && !navigation && (action == null || !action.isReady())) { reset(); return false; }
            try {
                // Native predictive back may defer input ownership: ensure the old app loses this stream.
                pilfer.invoke(target);
                cancel.invoke(target, last);
                state.claim();
                consumed = true;
                if (noAction) {
                    return true;
                } else if (navigation) {
                    executeNavigationAction(target, actionId);
                } else {
                    action.execute(fromLeft, (state.startY() + state.currentY()) / 2f, () -> { });
                }
                return true;
            } catch (Throwable t) {
                AssistantHooks.warn("Cannot take over edge back", t);
                reset();
                return false;
            }
        }

        int actionId(int direction) {
            if (distanceSplitEnabled && state.travelDistance() >= longDistancePx) {
                if (direction == AssistantGestureState.UP) return upLongActionId;
                if (direction == AssistantGestureState.DOWN) return downLongActionId;
                return horizontalLongActionId;
            }
            if (direction == AssistantGestureState.UP) return upActionId;
            if (direction == AssistantGestureState.DOWN) return downActionId;
            return horizontalActionId;
        }

        void updateActionPreview() {
            if (!actionPreviewEnabled || !gestureStarted || !state.ready()) {
                actionOverlay.hide();
                return;
            }
            Object target = owner.get();
            if (target == null) {
                actionOverlay.hide();
                return;
            }
            int gestureDirection = state.direction();
            int actionId = actionId(gestureDirection);
            ModuleConfig config = ModuleConfig.load(context);
            boolean longDistance = distanceSplitEnabled && state.travelDistance() >= longDistancePx;
            if (actionId != configuredAction(config, gestureDirection, longDistance)
                    || actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK
                    || (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target))) {
                actionOverlay.hide();
                return;
            }
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher);
            if (actionId != SettingsStore.SIDE_GESTURE_ACTION_NONE
                    && !isNavigationAction(actionId)
                    && (action == null || !action.isReady())) {
                actionOverlay.hide();
                return;
            }
            float density = context.getResources().getDisplayMetrics().density;
            float edge = fromLeft ? 0f : context.getResources().getDisplayMetrics().widthPixels;
            float horizontalDirection = fromLeft ? 1f : -1f;
            float reach = 28f * density
                    + Math.min(170f * density, state.inwardDistance() * 0.72f);
            float anchorX = edge + horizontalDirection * reach;
            float anchorY = state.startY() + state.verticalDelta() * 0.58f;
            actionOverlay.show(SideGestureActions.label(actionId), anchorX, anchorY);
        }

        int configuredAction(ModuleConfig config, int direction, boolean longDistance) {
            if (fromLeft) {
                if (direction == AssistantGestureState.UP) return longDistance
                        ? config.sideGestureLeftUpLongAction : config.sideGestureLeftUpAction;
                if (direction == AssistantGestureState.DOWN) return longDistance
                        ? config.sideGestureLeftDownLongAction : config.sideGestureLeftDownAction;
                return longDistance ? config.sideGestureLeftLongAction : config.sideGestureLeftAction;
            }
            if (direction == AssistantGestureState.UP) return longDistance
                    ? config.sideGestureRightUpLongAction : config.sideGestureRightUpAction;
            if (direction == AssistantGestureState.DOWN) return longDistance
                    ? config.sideGestureRightDownLongAction : config.sideGestureRightDownAction;
            return longDistance ? config.sideGestureRightLongAction : config.sideGestureRightAction;
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
            if (target == null || last == null || !gestureStarted) return;
            Object panel = ReflectUtils.getField(target, "mEdgeBackPlugin");
            if (panel != null) {
                float centerY = state.direction() == AssistantGestureState.HORIZONTAL
                        ? state.startY() : state.startY() + state.verticalDelta() * 0.5f;
                ReflectUtils.setFloatField(panel, "mCenterY", centerY);
                if (panel instanceof View) ((View) panel).postInvalidateOnAnimation();
            }
        }

        void updateAnimationProgress() {
            if (!gestureStarted || state.direction() == AssistantGestureState.HORIZONTAL) return;
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
            gestureStarted = false;
            hapticZone = -1;
            actionOverlay.hide();
            zoneOverlay.hide();
            if (last != null) { last.recycle(); last = null; }
        }
    }
}
