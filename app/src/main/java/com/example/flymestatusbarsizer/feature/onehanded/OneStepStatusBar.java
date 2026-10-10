package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Keeps the primary native status bar visible without owning SystemUI's visibility state. */
final class OneStepStatusBar {
    private static volatile boolean visible;
    private static WeakReference<Object> primary = new WeakReference<>(null);
    private static Method apply;
    private static WeakReference<Object> lightBar = new WeakReference<>(null);
    private static boolean darkIcons;
    private static boolean imeVisible;
    private static volatile boolean backgroundOwned;
    private static boolean backgroundVisible;
    private static boolean backgroundOverridden;
    private static volatile WeakReference<Object> backgroundTransitions = new WeakReference<>(null);

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        installColors(module, loader);
        installBackground(module, loader);
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.window.StatusBarWindowControllerImpl", false, loader);
            Class<?> state = Class.forName(type.getName() + "$State", false, loader);
            Method method = type.getDeclaredMethod("apply", state);
            method.setAccessible(true);
            Field forceVisible = state.getDeclaredField("mForceStatusBarVisible");
            forceVisible.setAccessible(true);
            module.intercept(method, chain -> {
                Object owner = chain.getThisObject();
                if (!isPrimary(owner)) return chain.proceed();
                primary = new WeakReference<>(owner);
                if (!visible) return chain.proceed();
                Object current = chain.getArg(0);
                boolean requested = forceVisible.getBoolean(current);
                forceVisible.setBoolean(current, true);
                try { return chain.proceed(); }
                finally {
                    // Restore the native request, not a snapshot saved when the workspace opened.
                    forceVisible.setBoolean(current, requested);
                }
            });
            apply = method;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (isPrimary(chain.getThisObject())) primary = new WeakReference<>(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable e) { Log.w(OneHandedTaskHooks.TAG, "Native status bar visibility hook unavailable", e); }
    }

    private static void installBackground(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> phone = Class.forName("com.android.systemui.statusbar.phone.PhoneStatusBarTransitions", false, loader);
            Class<?> base = phone.getSuperclass();
            Method names = OneStepReflection.method(base, "modeToString", int.class);
            int transparent = -1;
            for (int mode = 0; mode <= 6; mode++) {
                if ("MODE_TRANSPARENT".equals(names.invoke(null, mode))) { transparent = mode; break; }
            }
            if (transparent < 0) throw new IllegalStateException("Transparent status bar mode unavailable");
            int transparentMode = transparent;
            Method applyBackground = OneStepReflection.method(base, "applyModeBackground", int.class, int.class, boolean.class);
            module.intercept(applyBackground, chain -> {
                Object owner = chain.getThisObject();
                if (!phone.isInstance(owner) || !rememberBackground(owner))
                    return chain.proceed();
                boolean override = useWorkspaceBackground();
                // Keep BarTransitions.mMode native; only its drawable is overridden.
                // Flyme numbers transparent/opaque differently from other releases.
                Object result = override
                        ? chain.proceed(new Object[]{chain.getArg(0), transparentMode, false}) : chain.proceed();
                backgroundOverridden = override;
                if (override) OneStepReflection.call(owner, "finishAnimations");
                return result;
            });
            for (Constructor<?> constructor : phone.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (rememberBackground(chain.getThisObject())) refreshBackground();
                    return result;
                });
            }
        } catch (Throwable error) { Log.w(OneHandedTaskHooks.TAG, "Workspace status bar background hook unavailable", error); }
    }

    private static boolean rememberBackground(Object owner) {
        Object value = ReflectUtils.getField(owner, "mView");
        if (!(value instanceof View)) return false;
        View view = (View) value;
        int displayId = view.getDisplay() == null
                ? ReflectUtils.invokeNoArgInt(view.getContext(), "getDisplayId", -1) : view.getDisplay().getDisplayId();
        if (displayId != 0) return false;
        if (backgroundTransitions.get() != owner) {
            backgroundTransitions = new WeakReference<>(owner);
            backgroundOverridden = false;
        }
        return true;
    }

    private static boolean useWorkspaceBackground() {
        Object owner = lightBar.get();
        return backgroundOwned && backgroundVisible && !OneHandedTaskHooks.shadeOpen()
                && (owner == null || allowsWorkspaceColors(owner));
    }

    static void setBackgroundOwned(boolean owned) {
        backgroundOwned = owned;
        backgroundVisible = owned;
        refreshBackground();
    }

    static void finishBackground(SurfaceControl backdrop) {
        new Handler(Looper.getMainLooper()).post(() -> {
            setBackgroundOwned(false);
            try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                tx.reparent(backdrop, null);
                Object value = ReflectUtils.getField(backgroundTransitions.get(), "mView");
                View view = value instanceof View ? (View) value : null;
                AttachedSurfaceControl root = view == null || !view.isShown() ? null : view.getRootSurfaceControl();
                // Keep the backdrop until the native bar has drawn its restored
                // background. Removing it at task handoff can expose wallpaper.
                if (root != null && root.applyTransactionOnDraw(tx)) view.invalidate();
                else tx.apply();
            } catch (RuntimeException error) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot synchronize status bar backdrop removal", error);
                try (SurfaceControl.Transaction tx = new SurfaceControl.Transaction()) {
                    tx.reparent(backdrop, null).apply();
                } catch (RuntimeException cleanupError) {
                    Log.w(OneHandedTaskHooks.TAG, "Cannot remove status bar backdrop", cleanupError);
                }
            } finally { backdrop.release(); }
        });
    }

    static int nativeBackgroundColor() {
        if (backgroundOwned) return android.graphics.Color.TRANSPARENT;
        Object owner = backgroundTransitions.get();
        Object background = ReflectUtils.getField(owner, "mBarBackground");
        return background == null ? android.graphics.Color.TRANSPARENT
                : ReflectUtils.invokeNoArgInt(background, "getColor", android.graphics.Color.TRANSPARENT);
    }

    private static void refreshBackground() {
        Object owner = backgroundTransitions.get();
        // Leave ordinary app transitions alone. Only enter or release our override.
        if (owner == null || backgroundOverridden == useWorkspaceBackground()) return;
        try {
            int mode = ((Number) OneStepReflection.call(owner, "getMode")).intValue();
            OneStepReflection.call(owner, "applyModeBackground", new Class<?>[]{int.class, int.class, boolean.class},
                    -1, mode, false);
            OneStepReflection.call(owner, "finishAnimations");
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(OneHandedTaskHooks.TAG, "Cannot update workspace status bar background", error);
        }
    }

    private static void installColors(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.phone.LightBarControllerImpl", false, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    rememberLightBar(chain.getThisObject());
                    return result;
                });
            }
            for (Method method : type.getDeclaredMethods()) {
                if ("updateStatus".equals(method.getName()) && method.getParameterCount() == 1) {
                    module.intercept(method, chain -> {
                        Object owner = chain.getThisObject();
                        if (!rememberLightBar(owner)) return chain.proceed();
                        refreshBackground();
                        if (!useWallpaperColors(owner)) return chain.proceed();
                        try {
                            Object icons = OneStepReflection.get(owner, "mStatusBarIconController");
                            OneStepReflection.call(icons, "setIconsDarkArea", new Class<?>[]{ArrayList.class},
                                    (Object) null);
                            Object transitions = OneStepReflection.call(icons, "getTransitionsController");
                            OneStepReflection.call(transitions, "setIconsDark",
                                    new Class<?>[]{boolean.class, boolean.class}, darkIcons, true);
                            return null;
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(OneHandedTaskHooks.TAG, "Cannot tint workspace status bar", error);
                            return chain.proceed();
                        }
                    });
                } else if ("updateNavigation".equals(method.getName()) && method.getParameterCount() == 0) {
                    Field navigationLight = OneStepReflection.field(type, "mNavigationLight");
                    module.intercept(method, chain -> {
                        Object owner = chain.getThisObject();
                        if (!rememberLightBar(owner) || !useWallpaperColors(owner) || imeVisible)
                            return chain.proceed();
                        boolean nativeLight = navigationLight.getBoolean(owner);
                        navigationLight.setBoolean(owner, darkIcons);
                        try { return chain.proceed(); }
                        finally { navigationLight.setBoolean(owner, nativeLight); }
                    });
                }
            }
        } catch (Throwable e) { Log.w(OneHandedTaskHooks.TAG, "Wallpaper system bar tint hook unavailable", e); }
    }

    private static boolean rememberLightBar(Object owner) {
        if (ReflectUtils.getIntField(owner, "mDisplayId", -1) != 0) return false;
        lightBar = new WeakReference<>(owner);
        return true;
    }

    private static boolean useWallpaperColors(Object owner) {
        return visible && allowsWorkspaceColors(owner);
    }

    private static boolean allowsWorkspaceColors(Object owner) {
        return !OneHandedTaskHooks.shadeOpen()
                && !ReflectUtils.getBooleanField(owner, "mBouncerVisible", false)
                && !ReflectUtils.getBooleanField(owner, "mQsCustomizing", false)
                && !ReflectUtils.getBooleanField(owner, "mQsExpanded", false)
                && !ReflectUtils.getBooleanField(owner, "mGlobalActionsVisible", false);
    }

    static void setDarkIcons(boolean dark) {
        if (darkIcons == dark) return;
        darkIcons = dark;
        refreshColors();
    }

    static void setImeVisible(boolean shown) {
        if (imeVisible == shown) return;
        imeVisible = shown;
        refreshColors();
    }

    private static void refreshColors() {
        refreshBackground();
        Object owner = lightBar.get();
        if (owner == null) return;
        try {
            // Native appearance fields keep receiving app/shade updates while overridden.
            // Reevaluate those current values on exit instead of restoring a stale snapshot.
            OneStepReflection.call(owner, "reevaluate");
            OneStepReflection.call(owner, "updateNavigation");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(OneHandedTaskHooks.TAG, "Cannot refresh workspace system bar colors", e);
        }
    }

    private static boolean isPrimary(Object owner) {
        Object context = ReflectUtils.getField(owner, "mContext");
        return context instanceof Context && ReflectUtils.invokeNoArgInt(context, "getDisplayId", -1) == 0;
    }

    static void setVisible(boolean requested) {
        visible = requested;
        Runnable update = () -> {
            backgroundVisible = requested;
            if (!requested) imeVisible = false;
            refreshColors();
            Object owner = primary.get();
            Method method = apply;
            if (owner == null || method == null) return;
            try { method.invoke(owner, ReflectUtils.getField(owner, "mCurrentState")); }
            catch (ReflectiveOperationException | RuntimeException e) {
                Log.w(OneHandedTaskHooks.TAG, "Cannot update native status bar visibility", e);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else new Handler(Looper.getMainLooper()).post(update);
    }
}
