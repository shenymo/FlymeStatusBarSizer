package com.example.flymestatusbarsizer.feature.statusbar;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Unknown or unavailable cameras retain the native mask until idle is confirmed. */
final class FrontCameraState {
    private final Map<String, Boolean> idle = new HashMap<>();

    synchronized void reset(Collection<String> cameraIds) {
        idle.clear();
        for (String id : cameraIds) idle.put(id, false);
    }

    synchronized boolean update(String cameraId, boolean isIdle) {
        if (!idle.containsKey(cameraId)) return false;
        boolean before = allIdle();
        idle.put(cameraId, isIdle);
        return before != allIdle();
    }

    synchronized boolean shouldHide(boolean moduleEnabled, boolean featureEnabled) {
        return moduleEnabled && featureEnabled && allIdle();
    }

    private boolean allIdle() {
        return !idle.isEmpty() && !idle.containsValue(false);
    }
}
