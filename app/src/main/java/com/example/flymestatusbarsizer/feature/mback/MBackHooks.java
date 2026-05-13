package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.feature.clock.ClockDetailPopupBridge;

import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MBackHooks {
    private static final int MBACK_LONG_TOUCH_ACTION_INTENT_URI = 0;
    private static final int MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP = 1;
    private static WeakReference<View> latestMBackButtonRef = new WeakReference<>(null);

    private MBackHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookLongTouchIntent(module, loader);
        hookNavBarExperiments(module, loader);
        hookPillVisibility(module, loader);
    }

    public static String resolveLongTouchIntentUri(Context context, String fromWhere) {
        FlymeStatusBarSizer.MBackConfigSnapshot config = FlymeStatusBarSizer.loadMBackConfig(context);
        if (!config.mbackLongTouchIntentEnabled
                || config.mbackLongTouchAction != MBACK_LONG_TOUCH_ACTION_INTENT_URI
                || !"press_navigation".equals(fromWhere)) {
            return null;
        }
        String intentUri = config.mbackLongTouchIntentUri;
        if (intentUri == null) {
            return null;
        }
        String trimmed = intentUri.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean shouldShowClockPopup(Context context, String fromWhere) {
        FlymeStatusBarSizer.MBackConfigSnapshot config = FlymeStatusBarSizer.loadMBackConfig(context);
        return config.mbackLongTouchIntentEnabled
                && config.mbackLongTouchAction == MBACK_LONG_TOUCH_ACTION_CLOCK_POPUP
                && "press_navigation".equals(fromWhere);
    }

    public static void rememberMBackButton(View view) {
        if (view == null) {
            return;
        }
        latestMBackButtonRef = new WeakReference<>(view);
    }

    public static View resolveMBackButtonAnchor() {
        View view = latestMBackButtonRef.get();
        return view != null && view.isAttachedToWindow() ? view : null;
    }

    public static boolean launchConfiguredIntent(Context context, String intentUri) {
        if (context == null || intentUri == null) {
            return false;
        }
        String raw = intentUri.trim();
        if (raw.isEmpty()) {
            return false;
        }
        try {
            Intent intent;
            if (raw.startsWith("intent:") || raw.contains("#Intent;")) {
                intent = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(raw));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Context launchContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            launchContext.startActivity(intent);
            return true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    "Failed to launch mBack long touch intent: " + raw,
                    t);
            return false;
        }
    }

    public static boolean shouldHidePill(View view) {
        if (view == null) {
            return false;
        }
        FlymeStatusBarSizer.MBackConfigSnapshot config =
                FlymeStatusBarSizer.loadMBackConfig(view.getContext());
        return config.enabled && config.mbackHidePill;
    }

    public static void hidePillView(View view) {
        if (view == null) {
            return;
        }
        view.setAlpha(0f);
        view.invalidate();
    }

    public static void applyNavBarTransparency(Object transitions) {
        Context context = getNavBarTransitionsContext(transitions);
        if (context == null) {
            return;
        }
        FlymeStatusBarSizer.MBackConfigSnapshot config =
                FlymeStatusBarSizer.loadMBackConfig(context);
        if (!config.enabled || !config.mbackNavBarTransparent) {
            return;
        }
        View navBarView = getNavBarTransitionsView(transitions);
        if (navBarView == null) {
            return;
        }
        Drawable background = navBarView.getBackground();
        if (background != null) {
            background.setAlpha(0);
        }
        Object barBackground = readField(transitions, "mBarBackground");
        if (barBackground instanceof Drawable) {
            ((Drawable) barBackground).setAlpha(0);
        }
        navBarView.invalidate();
    }

    public static Object overrideInsetsFrameProviders(Object result, Context context) {
        if (result == null || context == null) {
            return result;
        }
        Class<?> resultClass = result.getClass();
        Class<?> componentType = resultClass.getComponentType();
        if (!resultClass.isArray()
                || componentType == null
                || !"android.view.InsetsFrameProvider".equals(componentType.getName())) {
            return result;
        }
        FlymeStatusBarSizer.MBackConfigSnapshot config =
                FlymeStatusBarSizer.loadMBackConfig(context);
        if (!config.enabled || config.mbackInsetSize < 0) {
            return result;
        }
        int bottomInsetPx = Math.max(0, dp(context, config.mbackInsetSize));
        int providerCount = Array.getLength(result);
        if (providerCount > 0) {
            setInsetsFrameProviderInsetsSize(Array.get(result, 0), Insets.of(0, 0, 0, bottomInsetPx));
        }
        if (providerCount > 2) {
            setInsetsFrameProviderInsetsSize(Array.get(result, 2), Insets.of(0, 0, 0, bottomInsetPx));
        }
        return result;
    }

    public static void applyNavBarHeightOverride(Object layoutParamsObject, Object navigationBar) {
        if (!(layoutParamsObject instanceof WindowManager.LayoutParams)) {
            return;
        }
        Context context = asContext(readField(navigationBar, "mContext"));
        if (context == null) {
            return;
        }
        FlymeStatusBarSizer.MBackConfigSnapshot config =
                FlymeStatusBarSizer.loadMBackConfig(context);
        if (!config.enabled || config.mbackNavBarHeight < 0) {
            return;
        }
        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) layoutParamsObject;
        if (layoutParams.gravity != Gravity.BOTTOM || layoutParams.height <= 0) {
            return;
        }
        layoutParams.height = Math.max(1, dp(context, config.mbackNavBarHeight));
    }

    private static int dp(Context context, int value) {
        if (context == null) {
            return value;
        }
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static View getNavBarTransitionsView(Object transitions) {
        Object value = readField(transitions, "mView");
        return value instanceof View ? (View) value : null;
    }

    private static Context getNavBarTransitionsContext(Object transitions) {
        View view = getNavBarTransitionsView(transitions);
        return view == null ? null : view.getContext();
    }

    private static Context asContext(Object value) {
        return value instanceof Context ? (Context) value : null;
    }

    private static Object readField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void setInsetsFrameProviderInsetsSize(Object provider, Insets insets) {
        if (provider == null || insets == null) {
            return;
        }
        try {
            Method method = provider.getClass().getDeclaredMethod("setInsetsSize", Insets.class);
            method.setAccessible(true);
            method.invoke(provider, insets);
        } catch (Throwable ignored) {
        }
    }

    private static void hookLongTouchIntent(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.navigationbar.actions.NavBarActionsConfig",
                    false,
                    loader);
            Method cancelMethod = clazz.getDeclaredMethod("requestCancelTISSwipeUp", String.class);
            cancelMethod.setAccessible(true);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"helpStartAI".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2
                        || parameterTypes[0] != Context.class
                        || parameterTypes[1] != String.class) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Context context = (Context) chain.getArg(0);
                    String fromWhere = (String) chain.getArg(1);
                    if (shouldShowClockPopup(context, fromWhere)) {
                        View anchor = resolveMBackButtonAnchor();
                        if (anchor == null) {
                            return chain.proceed();
                        }
                        try {
                            cancelMethod.invoke(null, "show mback clock popup from " + fromWhere);
                        } catch (Throwable ignored) {
                        }
                        anchor.post(() -> ClockDetailPopupBridge.showFromMBack(anchor));
                        return null;
                    }
                    String intentUri = resolveLongTouchIntentUri(context, fromWhere);
                    if (intentUri == null) {
                        return chain.proceed();
                    }
                    try {
                        cancelMethod.invoke(null, "launch mback long touch intent from " + fromWhere);
                    } catch (Throwable ignored) {
                    }
                    if (launchConfiguredIntent(context, intentUri)) {
                        return null;
                    }
                    return chain.proceed();
                });
                return;
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook mBack long touch intent", t);
        }
    }

    private static void hookNavBarExperiments(FlymeStatusBarSizer module, ClassLoader loader) {
        hookNavBarTransparency(module, loader);
        hookInsetOverride(module, loader);
        hookNavBarHeight(module, loader);
    }

    private static void hookPillVisibility(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.navigationbar.MBackButtonView",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"onDraw".equals(name)
                        && !"setDarkIntensity".equals(name)
                        && !"updateResources".equals(name)
                        && !"onAttachedToWindow".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object thisObject = chain.getThisObject();
                    if (!(thisObject instanceof View)) {
                        return chain.proceed();
                    }
                    View view = (View) thisObject;
                    rememberMBackButton(view);
                    if (!shouldHidePill(view)) {
                        return chain.proceed();
                    }
                    if ("onDraw".equals(name)) {
                        return null;
                    }
                    Object result = chain.proceed();
                    hidePillView(view);
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook mBack pill visibility", t);
        }
    }

    private static void hookNavBarTransparency(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.navigationbar.views.NavigationBarTransitions",
                    false,
                    loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    applyNavBarTransparency(chain.getThisObject());
                    return result;
                });
            }
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean backgroundAlphaMethod = "setBackgroundOverrideAlpha".equals(name)
                        && parameterTypes.length == 1
                        && parameterTypes[0] == float.class;
                boolean transitionMethod = "onTransition".equals(name)
                        && parameterTypes.length == 3
                        && parameterTypes[0] == int.class
                        && parameterTypes[1] == int.class
                        && parameterTypes[2] == boolean.class;
                if (!backgroundAlphaMethod && !transitionMethod) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    applyNavBarTransparency(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook mBack nav bar transparency", t);
        }
    }

    private static void hookInsetOverride(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.navigationbar.views.NavigationBar",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"getInsetsFrameProvider".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                Class<?> returnType = method.getReturnType();
                if (parameterTypes.length != 2
                        || parameterTypes[0] != int.class
                        || parameterTypes[1] != Context.class
                        || !returnType.isArray()
                        || returnType.getComponentType() == null
                        || !"android.view.InsetsFrameProvider".equals(returnType.getComponentType().getName())) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    Context context = chain.getArg(1) instanceof Context
                            ? (Context) chain.getArg(1)
                            : null;
                    return overrideInsetsFrameProviders(result, context);
                });
                return;
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook mBack inset override", t);
        }
    }

    private static void hookNavBarHeight(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.navigationbar.views.NavigationBar",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"getBarLayoutParamsForRotation".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1
                        || parameterTypes[0] != int.class
                        || !WindowManager.LayoutParams.class.equals(method.getReturnType())) {
                    continue;
                }
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    applyNavBarHeightOverride(result, chain.getThisObject());
                    return result;
                });
                return;
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook mBack nav bar height", t);
        }
    }
}
