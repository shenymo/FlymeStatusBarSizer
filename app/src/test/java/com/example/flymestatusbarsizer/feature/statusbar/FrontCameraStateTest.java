package com.example.flymestatusbarsizer.feature.statusbar;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class FrontCameraStateTest {
    @Test public void startupAndMissingCameraMetadataRetainTheMask() {
        FrontCameraState state = new FrontCameraState();
        assertFalse(state.shouldHide(true, true));
        state.reset(List.of("front"));
        assertFalse(state.shouldHide(true, true));
        // SystemUI can restart while a camera is already open; its initial unavailable event
        // must never be interpreted as an idle camera.
        assertFalse(state.update("front", false));
        assertFalse(state.shouldHide(true, true));
        assertTrue(state.update("front", true));
        assertTrue(state.shouldHide(true, true));
        state.reset(List.of());
        assertFalse(state.shouldHide(true, true));
    }

    @Test public void openingRestoresAndClosingHidesWithoutDuplicateRefreshes() {
        FrontCameraState state = new FrontCameraState();
        state.reset(List.of("front"));
        assertTrue(state.update("front", true));
        assertFalse(state.update("front", true));
        assertTrue(state.shouldHide(true, true));
        // Unavailable + opened and closed + available can each arrive for one transition.
        assertTrue(state.update("front", false));
        assertFalse(state.update("front", false));
        assertFalse(state.shouldHide(true, true));
        assertTrue(state.update("front", true));
        assertFalse(state.update("front", true));
        assertTrue(state.shouldHide(true, true));
    }

    @Test public void allFrontCamerasMustBeIdleAndRearEventsCannotClearFrontUse() {
        FrontCameraState state = new FrontCameraState();
        state.reset(List.of("front-wide", "front-tele"));
        state.update("front-wide", true);
        assertFalse(state.shouldHide(true, true));
        state.update("front-tele", true);
        assertTrue(state.shouldHide(true, true));
        assertFalse(state.update("rear", false));
        assertTrue(state.shouldHide(true, true));
        state.update("front-wide", false);
        state.update("front-tele", false);
        state.update("front-wide", true);
        state.update("rear", true);
        assertFalse(state.shouldHide(true, true));
        state.update("front-tele", true);
        assertTrue(state.shouldHide(true, true));
    }

    @Test public void togglingSettingsUsesTheCurrentCameraState() {
        FrontCameraState state = new FrontCameraState();
        state.reset(List.of("front"));
        state.update("front", true);
        assertFalse(state.shouldHide(false, true));
        assertFalse(state.shouldHide(true, false));
        assertTrue(state.shouldHide(true, true));
        state.update("front", false);
        assertFalse(state.shouldHide(true, false));
        assertFalse(state.shouldHide(true, true));
        state.update("front", true);
        assertTrue(state.shouldHide(true, true));
    }
}
