package com.example.flymestatusbarsizer.feature.assistant;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
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
    private static boolean cornerReceiverInstalled;

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
                Object owner = chain.getThisObject();
                com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.trackEdgeHandler(owner);
                Object c = ReflectUtils.getField(owner, "mContext");
                if (c instanceof Context) {
                    AssistantClient.get((Context) c);
                    synchronized (GESTURES) {
                        if (!GESTURES.containsKey(owner)) {
                            GESTURES.put(owner, new Gesture(owner, (Context) c, cancel, pilfer, sendEvent,
                                    AssistantClient.get((Context) c)));
                        }
                    }
                }
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
            if (action == MotionEvent.ACTION_MOVE) {
                if (gesture.tryClaim(false)) return null;
            }
            Object result = chain.proceed();
            if (action == MotionEvent.ACTION_MOVE) gesture.updateAnimationProgress();
            return result;
        });
    }

    private static void registerCornerReceiver(Context context) {
        if (cornerReceiverInstalled) return;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                int tier = intent.getIntExtra("tier", -1);
                if (tier < 0 || tier > SettingsStore.SIDE_GESTURE_FAR) return;
                ModuleConfig config = ModuleConfig.load(c);
                if (!config.enabled || !config.assistantGestureEnabled
                        || !config.windowModeSideGestureEnabled) return;
                synchronized (GESTURES) {
                    for (Gesture gesture : GESTURES.values()) {
                        Object owner = gesture.owner.get();
                        if (owner != null && ReflectUtils.getIntField(owner, "mDisplayId", -1) == 0) {
                            if (!gesture.locked()) {
                                gesture.executeCornerAction(config,
                                        intent.getBooleanExtra("from_left", true), tier);
                            }
                            return;
                        }
                    }
                }
            }
        }, new IntentFilter(SideGestureActions.ACTION_CORNER_GESTURE),
                "android.permission.STATUS_BAR_SERVICE", new Handler(Looper.getMainLooper()),
                Context.RECEIVER_EXPORTED);
        cornerReceiverInstalled = true;
    }

    static final class Gesture {
        final WeakReference<Object> owner;
        final Context context;
        final Handler handler = new Handler(Looper.getMainLooper());
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
        boolean actionIconPreviewEnabled;
        boolean zoneOverlayEnabled;
        boolean gestureStarted;
        int hapticZone = -1;
        int hapticCandidateZone = -1;
        int previewActionId = Integer.MIN_VALUE;
        int previewTier;
        boolean previewReady;
        String previewText = "";
        ModuleConfig gestureConfig;
        float gestureTouchSlop;
        float gestureStartX;
        float shortDistancePx;
        float longDistancePx;
        float farDistancePx;
        int sideOffset;
        boolean fanMode;
        int fanBand;
        int fanZone;
        int fanZoneKey = -1;
        boolean fanZoneValid;
        int[] fanShortBoundaries;
        int[] fanLongBoundaries;
        float fanCornerDistancePx;
        final AssistantGestureActionOverlay actionOverlay;
        final AssistantGestureZoneOverlay zoneOverlay;
        final Method sendEvent;

        Gesture(Object owner, Context context, Method cancel, Method pilfer,
                Method sendEvent, SideGestureActions.Action globalAssistant) {
            this.owner = new WeakReference<>(owner);
            this.context = context;
            this.cancel = cancel;
            this.pilfer = pilfer;
            this.sendEvent = sendEvent;
            this.globalAssistant = globalAssistant;
            this.taskSwitcher = com.example.flymestatusbarsizer.feature.taskswitcher.RecentTaskSwitcher.action(context);
            this.quickTools = QuickToolsPanel.action(context);
            this.appLauncher = AppLauncherPanel.action(context);
            this.actionOverlay = new AssistantGestureActionOverlay(context);
            this.zoneOverlay = new AssistantGestureZoneOverlay(context);
            registerCornerReceiver(context);
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
            gestureConfig = config;
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
            hapticCandidateZone = -1;
            previewActionId = Integer.MIN_VALUE;
            previewReady = false;
            previewText = "";
            state.begin(rawX, rawY, shortDistancePx,
                    12f * density, leftEdge, config.assistantGestureUpAngle / 10f,
                    config.assistantGestureDownAngle / 10f,
                    config.assistantGestureMinVerticalDp * density);
            fromLeft = leftEdge;
            zoneOverlayEnabled = config.assistantGestureZoneOverlayEnabled;
            actionPreviewEnabled = config.assistantGesturePreviewEnabled;
            actionIconPreviewEnabled = config.assistantGestureIconPreviewEnabled;
            triggerOnRelease = config.assistantGestureDistanceSplitEnabled
                    || config.assistantGestureTriggerMode == SettingsStore.ASSISTANT_GESTURE_TRIGGER_ON_RELEASE;
            longDistancePx = config.assistantGestureDistanceSplitEnabled
                    ? config.assistantGestureLongDistanceDp * density : Float.POSITIVE_INFINITY;
            farDistancePx = config.assistantGestureFarEnabled
                    ? config.assistantGestureFarDistanceDp * density : Float.POSITIVE_INFINITY;
            sideOffset = leftEdge ? 0 : 3;
            fanMode = config.sideGestureMode == SettingsStore.SIDE_GESTURE_MODE_FAN;
            fanBand = 0;
            fanZone = 0;
            fanZoneKey = -1;
            fanZoneValid = false;
            int fanSide = leftEdge ? 0 : 1;
            fanShortBoundaries = config.sideGestureFanShortBoundaries[fanSide];
            fanLongBoundaries = config.sideGestureFanLongBoundaries[fanSide];
            if (fanMode) {
                longDistancePx = Math.max(config.assistantGestureDistanceDp + 1,
                        config.assistantGestureLongDistanceDp) * density;
                fanCornerDistancePx = longDistancePx
                        + config.sideGestureFanCornerDistanceDp * density;
                triggerOnRelease = true;
            }
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
                    float density = context.getResources().getDisplayMetrics().density;
                    if (fanMode) {
                        zoneOverlay.showFan(gestureStartX, state.startY(), fromLeft, shortDistancePx,
                                longDistancePx, fanCornerDistancePx, gestureConfig);
                    } else {
                        zoneOverlay.show(gestureStartX, state.startY(), fromLeft, shortDistancePx,
                                longDistancePx, farDistancePx,
                                gestureConfig.assistantGestureUpAngle / 10f,
                                gestureConfig.assistantGestureDownAngle / 10f,
                                gestureConfig.assistantGestureMinVerticalDp * density, gestureConfig);
                    }
                }
            }
            if (zoneOverlayEnabled && gestureStarted) zoneOverlay.updatePointer(rawX, rawY);
            remember(event);
        }

        void updateDistanceHaptics() {
            if (!gestureReady()) {
                return;
            }
            if (fanMode) updateFanZone();
            int direction = state.direction();
            int tier = distanceTier();
            int zone = fanMode ? fanZoneKey : direction + tier * 3;
            if (zone == hapticCandidateZone) return;
            hapticCandidateZone = zone;
            if (zone == hapticZone) return;
            int actionId = currentActionId();
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_NONE
                    || actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK) return;
            Object target = owner.get();
            if (target == null
                    || (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(gestureConfig.assistantGestureScenes, target))) {
                return;
            }
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher, context);
            if (!isNavigationAction(actionId) && (action == null || !action.isReady())) return;
            hapticZone = zone;
            Object panel = target == null ? null : ReflectUtils.getField(target, "mEdgeBackPlugin");
            int feedback = !fanMode && tier == SettingsStore.SIDE_GESTURE_FAR
                    ? HapticFeedbackConstants.CONFIRM
                    : (fanMode ? fanBand > 0 : tier == SettingsStore.SIDE_GESTURE_LONG)
                    ? HapticFeedbackConstants.LONG_PRESS
                    : HapticFeedbackConstants.CLOCK_TICK;
            handler.post(() -> HapticFeedbackUtils.perform(context,
                    panel instanceof View ? (View) panel : null, feedback));
        }

        boolean tryClaim(boolean onRelease) {
            Object target = owner.get();
            if (last == null || target == null || consumed || !gestureStarted || !gestureReady()) {
                return false;
            }
            if (triggerOnRelease && !onRelease) return false;
            ModuleConfig config = gestureConfig;
            if (config == null || !config.enabled || !config.assistantGestureEnabled
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false)) {
                reset();
                return false;
            }
            if (fanMode) updateFanZone();
            int actionId = currentActionId();
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target)) {
                return false;
            }
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK) return false;
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher, context);
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

        int distanceTier() {
            return SettingsStore.sideGestureTier(state.travelDistance(), longDistancePx, farDistancePx);
        }

        boolean gestureReady() {
            return fanMode ? state.readyForFan(shortDistancePx) : state.ready();
        }

        void updateFanZone() {
            float distance = state.travelDistance();
            float angle = state.angleDegrees();
            int oldBand = fanBand;
            int oldZone = fanZone;
            int nextBand;
            int nextZone;
            if (distance >= fanCornerDistancePx) {
                nextBand = 2;
                nextZone = angle < 0f ? 0 : 1;
            } else if (distance >= longDistancePx) {
                nextBand = 1;
                nextZone = SettingsStore.sideGestureFanZone(angle, fanLongBoundaries);
            } else {
                nextBand = 0;
                nextZone = SettingsStore.sideGestureFanZone(angle, fanShortBoundaries);
            }
            if (fanZoneValid && oldBand == nextBand && oldZone != nextZone) {
                if (nextBand == 2) {
                    if (Math.abs(angle) < 3f) nextZone = oldZone;
                } else {
                    int[] bounds = nextBand == 0 ? fanShortBoundaries : fanLongBoundaries;
                    float boundary = bounds[nextZone > oldZone ? oldZone : nextZone] / 10f;
                    if (Math.abs(angle - boundary) < 3f) nextZone = oldZone;
                }
            }
            fanBand = nextBand;
            fanZone = nextZone;
            fanZoneValid = true;
            fanZoneKey = fanBand == 0 ? fanZone : fanBand == 1 ? 3 + fanZone : 8 + fanZone;
        }

        int currentActionId() {
            if (!fanMode) {
                return gestureConfig.sideGestureActions[distanceTier()][sideOffset + state.direction()];
            }
            int side = fromLeft ? 0 : 1;
            int slot = fanBand == 0 ? side * 3 + fanZone
                    : fanBand == 1 ? side * 5 + fanZone : side * 2 + fanZone;
            return gestureConfig.sideGestureFanActions[fanBand][slot];
        }

        void updateActionPreview() {
            if ((!actionPreviewEnabled && !actionIconPreviewEnabled)
                    || !gestureStarted || !gestureReady()) {
                actionOverlay.hideAll();
                return;
            }
            Object target = owner.get();
            if (target == null) {
                actionOverlay.hideAll();
                return;
            }
            ModuleConfig config = gestureConfig;
            int tier = distanceTier();
            if (fanMode) updateFanZone();
            int actionId = currentActionId();
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target)) {
                actionOverlay.hideAll();
                return;
            }
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher, context);
            if (actionId != SettingsStore.SIDE_GESTURE_ACTION_NONE
                    && actionId != SettingsStore.SIDE_GESTURE_ACTION_BACK
                    && !isNavigationAction(actionId)
                    && (action == null || !action.isReady())) {
                previewReady = false;
                actionOverlay.hideAll();
                return;
            }
            float density = context.getResources().getDisplayMetrics().density;
            float edge = fromLeft ? 0f : context.getResources().getDisplayMetrics().widthPixels;
            float horizontalDirection = fromLeft ? 1f : -1f;
            float reach = 28f * density
                    + Math.min(170f * density, state.inwardDistance() * 0.72f);
            float anchorX = edge + horizontalDirection * reach;
            float anchorY = state.startY() + state.verticalDelta() * 0.58f;
            if (actionId != previewActionId || (fanMode ? fanZoneKey : tier) != previewTier || !previewReady) {
                previewActionId = actionId;
                previewTier = fanMode ? fanZoneKey : tier;
                previewReady = true;
                previewText = SideGestureActions.label(actionId, context);
            }
            if (actionPreviewEnabled && actionId != SettingsStore.SIDE_GESTURE_ACTION_BACK) {
                actionOverlay.show(previewText, anchorX, anchorY);
            } else {
                actionOverlay.hide();
            }
            if (actionIconPreviewEnabled) actionOverlay.showIcon(actionId, config);
            else actionOverlay.hideIcon();
        }

        void executeCornerAction(ModuleConfig config, boolean left, int tier) {
            int actionId = config.cornerGestureActions[tier][left ? 0 : 1];
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_NONE) return;
            Object target = owner.get();
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT
                    && !AssistantGestureScenes.allows(config.assistantGestureScenes, target)) return;
            if (actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK || isNavigationAction(actionId)) {
                try {
                    executeNavigationAction(target, actionId);
                } catch (ReflectiveOperationException | IllegalStateException error) {
                    AssistantHooks.warn("Cannot execute bottom corner navigation", error);
                }
                return;
            }
            SideGestureActions.Action action = SideGestureActions.resolve(actionId, globalAssistant,
                    com.example.flymestatusbarsizer.feature.onehanded.OneHandedTaskHooks.ACTION,
                    taskSwitcher, quickTools, appLauncher, context);
            if (action != null && action.isReady()) {
                action.execute(left, context.getResources().getDisplayMetrics().heightPixels * 0.75f,
                        () -> { });
            }
        }

        boolean isNavigationAction(int actionId) {
            return actionId == SettingsStore.SIDE_GESTURE_ACTION_HOME
                    || actionId == SettingsStore.SIDE_GESTURE_ACTION_RECENTS;
        }

        void executeNavigationAction(Object target, int actionId) throws ReflectiveOperationException {
            int keyCode = actionId == SettingsStore.SIDE_GESTURE_ACTION_BACK ? KeyEvent.KEYCODE_BACK
                    : actionId == SettingsStore.SIDE_GESTURE_ACTION_HOME
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
            hapticCandidateZone = -1;
            previewActionId = Integer.MIN_VALUE;
            previewReady = false;
            previewText = "";
            gestureConfig = null;
            actionOverlay.hideAll();
            zoneOverlay.hide();
            if (last != null) { last.recycle(); last = null; }
        }
    }
}
