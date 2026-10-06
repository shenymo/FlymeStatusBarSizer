package com.example.flymestatusbarsizer.feature.assistant;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/** Invalidates Aicy's cached remote surface only when moving between window hosts. */
final class AssistantSurfaceHost {
    private final Method setStartRelease;

    private AssistantSurfaceHost(Method setStartRelease) {
        this.setStartRelease = setStartRelease;
    }

    AssistantSurfaceHost(Class<?> surfaceType) throws NoSuchMethodException {
        this(AssistantReflection.method(surfaceType, "setStartRelease", boolean.class));
    }

    static AssistantSurfaceHost create(ClassLoader loader) {
        try {
            return new AssistantSurfaceHost(Class.forName(
                    "com.meizu.assistant.function.adapter.container.AlphaSurfaceView", false, loader));
        } catch (ReflectiveOperationException | LinkageError error) {
            AssistantHooks.warn("Aicy surface host refresh is unavailable", error);
            return new AssistantSurfaceHost((Method) null);
        }
    }

    void prepareForHostChange(View view) {
        if (setStartRelease == null) return;
        if (setStartRelease.getDeclaringClass().isInstance(view) && view.isAttachedToWindow()) {
            try {
                // WidgetSurfaceView normally preserves its SurfacePackage on detach. A valid
                // cached package skips attachSurface(newHostToken) on the next attachment.
                // Use its native release branch so reattachment requests the new host token;
                // keep the service, card data, observers and visibility callbacks intact.
                setStartRelease.invoke(view, true);
            } catch (ReflectiveOperationException | RuntimeException error) {
                AssistantHooks.warn("Cannot refresh Aicy surface for window migration", error);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                prepareForHostChange(group.getChildAt(i));
            }
        }
    }
}
