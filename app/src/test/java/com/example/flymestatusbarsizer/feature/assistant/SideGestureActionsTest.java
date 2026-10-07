package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SideGestureActionsTest {
    private final List<String> calls = new ArrayList<>();
    private final EdgeHandler edge = new EdgeHandler(calls);
    private final AssistantAction assistant = new AssistantAction();
    private final ModuleConfig config = new ModuleConfig();
    private Field activeConfig;
    private Object previousConfig;
    private AssistantGestureHooks.Gesture gesture;

    @Before public void setUp() throws Exception {
        config.enabled = true;
        config.assistantGestureEnabled = true;
        activeConfig = ModuleConfig.class.getDeclaredField("activeConfig");
        activeConfig.setAccessible(true);
        previousConfig = activeConfig.get(null);
        activeConfig.set(null, config);
        gesture = new AssistantGestureHooks.Gesture(edge, RuntimeEnvironment.getApplication(),
                EdgeHandler.class.getMethod("cancelGesture", MotionEvent.class),
                EdgeHandler.class.getMethod("pilferPointers"), assistant);
    }

    @After public void tearDown() throws Exception {
        gesture.reset();
        activeConfig.set(null, previousConfig);
    }

    @Test public void selectedActionRunsOnceAfterNativeBackIsCancelledOnEitherSide() {
        for (boolean left : new boolean[]{true, false}) {
            calls.clear();
            prepareReadyGesture(left);
            assertTrue(gesture.tryClaim());
            assertEquals(Arrays.asList("pilfer", "cancel", "assistant"), calls);
            assertEquals(left, assistant.fromLeft);
            assertNotNull(assistant.onSuccess);
            assertTrue(gesture.consumed);
            assertFalse(gesture.tryClaim());
            assertEquals(3, calls.size());
            // Opening is asynchronous; a later success can still complete the haptic callback.
            assistant.onSuccess.run();
            gesture.reset();
        }
    }

    @Test public void unavailableActionKeepsNativeBackAndCanRecoverOnNextGesture() {
        prepareReadyGesture(true);
        assistant.ready = false;
        assertFalse(gesture.tryClaim());
        assertFalse(gesture.consumed);
        assertTrue(calls.isEmpty());
        assistant.ready = true;
        prepareReadyGesture(true);
        assertTrue(gesture.tryClaim());
    }

    @Test public void changedActionOrDisabledGestureCancelsBeforeTakingInput() {
        prepareReadyGesture(true);
        config.sideGestureAction = -1;
        assertFalse(gesture.tryClaim());
        assertTrue(calls.isEmpty());
        assertNull(SideGestureActions.resolve(-1, assistant));
        config.sideGestureAction = SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT;
        prepareReadyGesture(true);
        config.assistantGestureEnabled = false;
        assertFalse(gesture.tryClaim());
        assertTrue(calls.isEmpty());
    }

    @Test public void assistantConnectionRequiresItsActionAndBothEnableSwitches() {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(AssistantClient.enabled(context));
        config.sideGestureAction = -1;
        assertFalse(AssistantClient.enabled(context));
        config.sideGestureAction = SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT;
        config.assistantGestureEnabled = false;
        assertFalse(AssistantClient.enabled(context));
        config.assistantGestureEnabled = true;
        config.enabled = false;
        assertFalse(AssistantClient.enabled(context));
    }

    @Test public void taskScaleIsDispatchedWithoutAssistantReadiness() {
        AssistantAction scale = new AssistantAction();
        assistant.ready = false;
        config.sideGestureAction = SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE;
        prepareReadyGesture(true);
        gesture.action = SideGestureActions.resolve(config.sideGestureAction, assistant, scale);
        assertFalse(AssistantClient.enabled(RuntimeEnvironment.getApplication()));
        assertTrue(gesture.tryClaim());
        assertNotNull(scale.onSuccess);
        assertNull(assistant.onSuccess);
        assertEquals(Arrays.asList("pilfer", "cancel", "assistant"), calls);
    }

    private void prepareReadyGesture(boolean left) {
        long now = SystemClock.uptimeMillis();
        gesture.actionId = config.sideGestureAction;
        gesture.action = SideGestureActions.resolve(config.sideGestureAction, assistant);
        gesture.fromLeft = left;
        gesture.state.begin(200, 500, now - 600, 140, 600, 12, left, Float.POSITIVE_INFINITY);
        float x = left ? 340 : 60;
        gesture.state.move(x, 500, 1);
        MotionEvent event = MotionEvent.obtain(now - 600, now, MotionEvent.ACTION_MOVE, x, 500, 0);
        gesture.remember(event);
        event.recycle();
    }

    public static class EdgeHandler {
        public boolean mAllowGesture = true;
        public boolean mInterceptBack;
        private final List<String> calls;
        EdgeHandler(List<String> calls) { this.calls = calls; }
        public void pilferPointers() { calls.add("pilfer"); }
        public void cancelGesture(MotionEvent event) { calls.add("cancel"); }
    }

    private final class AssistantAction implements SideGestureActions.Action {
        boolean ready = true;
        boolean fromLeft;
        Runnable onSuccess;
        @Override public boolean isReady() { return ready; }
        @Override public void execute(boolean fromLeft, Runnable onSuccess) {
            calls.add("assistant");
            this.fromLeft = fromLeft;
            this.onSuccess = onSuccess;
        }
    }
}
