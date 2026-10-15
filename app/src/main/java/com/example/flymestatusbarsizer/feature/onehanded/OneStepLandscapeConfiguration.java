package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.res.Configuration;
import android.graphics.Rect;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/** Logical app dimensions for the workspace's landscape task, independent of its screen frame. */
final class OneStepLandscapeConfiguration {
    private OneStepLandscapeConfiguration() {}

    static void install(FlymeStatusBarSizer module, ClassLoader loader, Predicate<Object> ownsLandscape)
            throws ReflectiveOperationException {
        Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
        Class<?> fragment = Class.forName("com.android.server.wm.TaskFragment", false, loader);
        Class<?> hint = Class.forName("com.android.server.wm.TaskFragment$ConfigOverrideHint", false, loader);
        Class<?> activity = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        Class<?> container = Class.forName("com.android.server.wm.WindowContainer", false, loader);
        Method resources = OneStepReflection.method(fragment, "computeConfigResourceOverrides",
                Configuration.class, Configuration.class, hint);
        Method screenLayout = OneStepReflection.method(container, "computeScreenLayout",
                int.class, int.class, int.class);
        Method maxBounds = OneStepReflection.method(activity, "providesMaxBounds");

        module.intercept(resources, chain -> {
            Object result = chain.proceed();
            Object target = chain.getThisObject();
            if (!task.isInstance(target) || !ownsLandscape.test(target)) return result;
            Configuration resolved = (Configuration) chain.getArg(0);
            // Activities also call this method on their Task. Only replace the
            // task's own configuration; let Activity aspect-ratio policies use it.
            if (resolved != OneStepReflection.call(target, "getResolvedOverrideConfiguration")) return result;
            Object window = OneStepReflection.get(resolved, "windowConfiguration");
            if (((Number) OneStepReflection.call(window, "getWindowingMode")).intValue() != 6) return result;
            Rect bounds = new Rect((Rect) OneStepReflection.call(window, "getBounds"));
            if (bounds.isEmpty() || bounds.width() <= bounds.height()) return result;
            Configuration parent = (Configuration) chain.getArg(1);
            int density = resolved.densityDpi != 0 ? resolved.densityDpi : parent.densityDpi;
            if (density <= 0) throw new IllegalStateException("Landscape task has no display density");

            // Mode 6 normally intersects appBounds with the portrait display area.
            // Flyme's small windows keep an independent logical canvas. Supply that
            // canvas here while retaining the workspace's existing windowing mode.
            OneStepReflection.call(window, "setAppBounds", new Class<?>[]{Rect.class}, bounds);
            OneStepReflection.call(window, "setMaxBounds", new Class<?>[]{Rect.class}, bounds);
            resolved.densityDpi = density;
            resolved.screenWidthDp = Math.max(1, Math.round(bounds.width() * 160f / density));
            resolved.screenHeightDp = Math.max(1, Math.round(bounds.height() * 160f / density));
            resolved.smallestScreenWidthDp = Math.min(resolved.screenWidthDp, resolved.screenHeightDp);
            resolved.orientation = Configuration.ORIENTATION_LANDSCAPE;
            resolved.screenLayout = ((Number) screenLayout.invoke(null, parent.screenLayout,
                    resolved.screenWidthDp, resolved.screenHeightDp)).intValue();
            return result;
        });
        module.intercept(maxBounds, chain -> {
            // Display.getRealSize/getRealMetrics use Activity maxBounds on this ROM.
            // This provides local display dimensions without enabling Flyme's native
            // small-window lifecycle or installing hooks in each hosted application.
            if (ownsLandscape.test(chain.getThisObject())) return true;
            return chain.proceed();
        });
    }
}
