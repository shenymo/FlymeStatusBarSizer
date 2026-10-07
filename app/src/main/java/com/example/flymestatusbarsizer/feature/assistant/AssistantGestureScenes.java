package com.example.flymestatusbarsizer.feature.assistant;

import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.util.ReflectUtils;

/** Reads the shade window owned by this EdgeBackGestureHandler, including partial expansion. */
public final class AssistantGestureScenes {
    private AssistantGestureScenes() {}

    static boolean allows(int selected, Object edgeHandler) {
        selected = SettingsStore.normalizeAssistantGestureScenes(selected);
        // Preserve the existing gesture on systems without the Flyme fields when all scenes are enabled.
        if (selected == SettingsStore.DEFAULT_ASSISTANT_GESTURE_SCENES) return true;
        if (selected == 0) return false;
        return (selected & current(edgeHandler)) != 0;
    }

    public static int current(Object edgeHandler) {
        Object controller = ReflectUtils.getField(edgeHandler, "mNotificationShadeWindowController");
        Object state = ReflectUtils.getField(controller, "mCurrentState");
        Object center = ReflectUtils.getField(state, "centerControllerVisible");
        Object qs = ReflectUtils.getField(state, "qsExpanded");
        // Flyme's separate control center and the classic panel's expanded quick settings.
        if (Boolean.TRUE.equals(center) || Boolean.TRUE.equals(qs)) {
            return SettingsStore.ASSISTANT_GESTURE_SCENE_CONTROL_CENTER;
        }
        if (!(center instanceof Boolean) || !(qs instanceof Boolean)) return 0;
        // panelVisible / shadeOrQsExpanded can also be true for a heads-up banner. Use the
        // actual shade expansion so a notification arriving over an app does not change its scene.
        Object interactor = ReflectUtils.invokeNoArg(ReflectUtils.getField(controller, "mShadeInteractorLazy"), "get");
        Object expansion = ReflectUtils.invokeNoArg(interactor, "getLegacyShadeExpansion");
        Object value = ReflectUtils.invokeNoArg(expansion, "getValue");
        if (!(value instanceof Number)) return 0;
        float fraction = ((Number) value).floatValue();
        if (!Float.isFinite(fraction) || fraction < 0f || fraction > 1f) return 0;
        return fraction > 0f ? SettingsStore.ASSISTANT_GESTURE_SCENE_NOTIFICATION
                : SettingsStore.ASSISTANT_GESTURE_SCENE_NORMAL;
    }
}
