package com.example.flymestatusbarsizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class FlymeStatusBarSizer extends XposedModule {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static volatile FlymeStatusBarSizer MODULE;

    private static final WeakHashMap<View, int[]> ORIGINAL_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_MARGINS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, String> VIEW_ID_NAME_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_CONNECTION_RATE_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, ConnectionRateThresholdState> CONNECTION_RATE_THRESHOLD_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_BATTERY_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationLiquidGlassView> NOTIFICATION_GLASS_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationGlassDrawable> NOTIFICATION_GLASS_DRAWABLES = new WeakHashMap<>();
    private static final Object CONFIG_REFRESH_LOCK = new Object();
    private static final long[] INITIAL_RUNTIME_REFRESH_DELAYS_MS = {1000L, 3000L};
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static Handler MAIN_HANDLER;
    private static BroadcastReceiver USER_UNLOCKED_RECEIVER;
    private static ContentObserver SETTINGS_OBSERVER;
    private static final HashMap<String, Integer> SYSTEM_UI_ID_CACHE = new HashMap<>();

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) {
            return;
        }
        MODULE = this;
        ClassLoader loader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (SYSTEM_UI.equals(packageName)) {
            hookSystemUi(loader);
        }
    }

    private void hookSystemUi(ClassLoader loader) {
        hookConnectionRateView(loader);
        hookMBackLongTouchIntent(loader);
        hookMBackNavBarExperiments(loader);
        hookMBackPillVisibility(loader);
        hookNotificationBackgroundTransparency(loader);
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryMeterView", view -> {
            ModuleConfig config = ModuleConfig.load(view.getContext());
            if (!config.enabled) {
                return;
            }
            TRACKED_BATTERY_VIEWS.put(view, Boolean.TRUE);
            disableAncestorClipping(view, 6);
            resizeIosBatteryView(view, config, ReflectUtils.getBooleanField(view, "mCharging", false));
        });
        hookFlymeBatteryMeterViewDraw(loader);
        hookFlymeBatteryMeterViewMeasure(loader);
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryTextView", view -> {
            ModuleConfig config = ModuleConfig.load(view.getContext());
            if (!config.enabled || !(view instanceof TextView)) {
                return;
            }
            TextView textView = (TextView) view;
            textView.setTextColor(Color.WHITE);
            ReflectUtils.setIntField(textView, "mNormalColor", Color.WHITE);
            ReflectUtils.setIntField(textView, "mLowColor", Color.WHITE);
        });
        hookBatteryDrawable(loader);
        hookClockWeekday(loader);
    }

    private void hookConnectionRateView(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.connectionRateView.ConnectionRateView", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"onAttachedToWindow".equals(name)
                        && !"onConnectionRateChange".equals(name)
                        && !"onConfigurationChanged".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object thisObject = chain.getThisObject();
                    Object result = chain.proceed();
                    if (thisObject instanceof View) {
                        View view = (View) thisObject;
                        trackConnectionRateView(view);
                        ensureConfigRefreshObserver(view.getContext());
                        if ("onConnectionRateChange".equals(name) && chain.getArgs().size() == 2
                                && chain.getArg(0) instanceof Boolean) {
                            Object rateArg = chain.getArg(1);
                            double rate = rateArg instanceof Number
                                    ? ((Number) rateArg).doubleValue()
                                    : ReflectUtils.getDoubleField(view, "mCurrentRate", 0d);
                            applyConnectionRateThresholdVisibility(view,
                                    (Boolean) chain.getArg(0), rate);
                        } else {
                            applyConnectionRateThresholdVisibility(view);
                            view.postDelayed(() -> {
                                applyConnectionRateThresholdVisibility(view);
                            }, 500);
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ConnectionRateView", t);
        }
    }

    private void hookBatteryDrawable(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.BatteryMeterDrawable", false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    applyIosBatteryStyleIfNeeded(chain.getThisObject());
                    return result;
                });
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"draw".equals(method.getName()) || method.getParameterTypes().length != 1) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object drawable = chain.getThisObject();
                    if (drawIosBatteryIfNeeded(drawable, (Canvas) chain.getArg(0))) {
                        return null;
                    }
                    applyIosBatteryStyleIfNeeded(drawable);
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook BatteryMeterDrawable", t);
        }
    }

    private void hookFlymeBatteryMeterViewDraw(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.FlymeBatteryMeterView", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"onDraw".equals(method.getName()) || method.getParameterTypes().length != 1) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object view = chain.getThisObject();
                    if (drawIosBatteryViewIfNeeded(view, (Canvas) chain.getArg(0))) {
                        return null;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeBatteryMeterView.onDraw", t);
        }
    }

    private void hookFlymeBatteryMeterViewMeasure(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.FlymeBatteryMeterView", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"onMeasure".equals(method.getName()) || method.getParameterTypes().length != 2) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object view = chain.getThisObject();
                    if (measureIosBatteryViewIfNeeded(view)) {
                        return null;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeBatteryMeterView.onMeasure", t);
        }
    }

    private void hookMBackLongTouchIntent(ClassLoader loader) {
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
                hook(method).intercept(chain -> {
                    Context context = (Context) chain.getArg(0);
                    ModuleConfig config = ModuleConfig.load(context);
                    if (!config.mbackLongTouchIntentEnabled) {
                        return chain.proceed();
                    }
                    String intentUri = config.mbackLongTouchIntentUri;
                    if (intentUri == null || intentUri.trim().isEmpty()) {
                        return chain.proceed();
                    }
                    String fromWhere = (String) chain.getArg(1);
                    if (!"press_navigation".equals(fromWhere)) {
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
            log(android.util.Log.WARN, TAG, "Failed to hook mBack long touch intent", t);
        }
    }

    private void hookMBackNavBarExperiments(ClassLoader loader) {
        hookMBackNavBarTransparency(loader);
        hookMBackInsetOverride(loader);
        hookMBackNavBarHeight(loader);
    }

    private void hookMBackPillVisibility(ClassLoader loader) {
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
                hook(method).intercept(chain -> {
                    Object thisObject = chain.getThisObject();
                    if (!(thisObject instanceof View)) {
                        return chain.proceed();
                    }
                    View view = (View) thisObject;
                    ModuleConfig config = ModuleConfig.load(view.getContext());
                    if (!config.enabled || !config.mbackHidePill) {
                        return chain.proceed();
                    }
                    if ("onDraw".equals(name)) {
                        return null;
                    }
                    Object result = chain.proceed();
                    hideMBackPillView(view);
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook mBack pill visibility", t);
        }
    }

    private void hookNotificationBackgroundTransparency(ClassLoader loader) {
        try {
            Class<?> activatableClass = Class.forName(
                    "com.android.systemui.statusbar.notification.row.ActivatableNotificationView",
                    false,
                    loader);
            for (Method method : activatableClass.getDeclaredMethods()) {
                String name = method.getName();
                if ("setBackground".equals(name) && method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof View) {
                            applyTransparentNotificationBackgroundIfNeeded((View) target);
                        }
                        return result;
                    });
                } else if ("updateBackgroundTint".equals(name) && method.getParameterTypes().length <= 1) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof View) {
                            applyTransparentNotificationBackgroundIfNeeded((View) target);
                        }
                        return result;
                    });
                }
            }

            Class<?> backgroundClass = Class.forName(
                    "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
                    false,
                    loader);
            for (Method method : backgroundClass.getDeclaredMethods()) {
                String name = method.getName();
                if ("onAttachedToWindow".equals(name)
                        && method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof View
                                && shouldUseTransparentNotificationBackground((View) target)) {
                            syncNotificationGlassDrawableState((View) target);
                            ((View) target).invalidate();
                        }
                        return result;
                    });
                } else if (("setActualHeight".equals(name)
                        || "setDrawableAlpha".equals(name)
                        || "setExpandAnimationRunning".equals(name)
                        || "setTint".equals(name)
                        || "setTintForNoBlur".equals(name)
                        || "setState".equals(name)
                        || "drawableStateChanged".equals(name)
                        || "setClipTopAmount".equals(name)
                        || "setClipBottomAmount".equals(name)
                        || "setBottomAmountClips".equals(name))
                        && method.getParameterTypes().length <= 1) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof View) {
                            syncNotificationGlassDrawableState((View) target);
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook notification background transparency", t);
        }
    }

    private void hookMBackNavBarTransparency(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.navigationbar.views.NavigationBarTransitions",
                    false,
                    loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    applyMBackNavBarTransparency(chain.getThisObject());
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
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    applyMBackNavBarTransparency(chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook mBack nav bar transparency", t);
        }
    }

    private void hookMBackInsetOverride(ClassLoader loader) {
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
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    Context context = chain.getArg(1) instanceof Context
                            ? (Context) chain.getArg(1)
                            : null;
                    return overrideMBackInsetsFrameProviders(result, context);
                });
                return;
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook mBack inset override", t);
        }
    }

    private void hookMBackNavBarHeight(ClassLoader loader) {
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
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    applyMBackNavBarHeightOverride(result, chain.getThisObject());
                    return result;
                });
                return;
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook mBack nav bar height", t);
        }
    }

    private static boolean launchConfiguredIntent(Context context, String intentUri) {
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
            if (MODULE != null) {
                MODULE.log(android.util.Log.WARN, TAG,
                        "Failed to launch mBack long touch intent: " + raw, t);
            }
            return false;
        }
    }

    private static void applyMBackNavBarTransparency(Object transitions) {
        Context context = getNavBarTransitionsContext(transitions);
        if (context == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(context);
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
        Object barBackground = ReflectUtils.getField(transitions, "mBarBackground");
        if (barBackground instanceof Drawable) {
            ((Drawable) barBackground).setAlpha(0);
        }
        navBarView.invalidate();
    }

    private static Object overrideMBackInsetsFrameProviders(Object result, Context context) {
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
        ModuleConfig config = ModuleConfig.load(context);
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

    private static void applyMBackNavBarHeightOverride(Object layoutParamsObject, Object navigationBar) {
        if (!(layoutParamsObject instanceof WindowManager.LayoutParams)) {
            return;
        }
        Context context = (Context) ReflectUtils.getField(navigationBar, "mContext");
        if (context == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled || config.mbackNavBarHeight < 0) {
            return;
        }
        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) layoutParamsObject;
        if (layoutParams.gravity != Gravity.BOTTOM || layoutParams.height <= 0) {
            return;
        }
        int heightPx = Math.max(1, dp(context, config.mbackNavBarHeight));
        layoutParams.height = heightPx;
    }

    private static void hideMBackPillView(View view) {
        if (view == null) {
            return;
        }
        view.setAlpha(0f);
        view.invalidate();
    }

    private static void applyTransparentNotificationBackgroundIfNeeded(View root) {
        if (root == null) {
            return;
        }
        try {
            if (!shouldUseTransparentNotificationBackground(root)) {
                hideNotificationLiquidGlassOverlay(root);
                return;
            }
            Object backgroundNormal = ReflectUtils.getField(root, "mBackgroundNormal");
            Object backgroundFlyme = ReflectUtils.getField(root, "mBackgroundFlyme");
            float radius = resolveNotificationCornerRadiusPx(root);
            applyTransparentNotificationBackgroundView(backgroundNormal, root.getContext(), radius);
            applyTransparentNotificationBackgroundView(backgroundFlyme, root.getContext(), radius);
            hideNotificationLiquidGlassOverlay(root);
            ReflectUtils.invokeMethod(root, "setBackgroundTintColor", new Class[]{int.class}, 0);
            ReflectUtils.invokeMethod(root, "setTintColor", new Class[]{int.class}, 0);
            ReflectUtils.invokeMethod(root, "setTintColor", new Class[]{int.class, boolean.class}, 0, false);
            root.invalidate();
        } catch (Throwable t) {
            if (MODULE != null) {
                MODULE.log(android.util.Log.WARN, TAG,
                        "Failed to apply notification liquid glass overlay", t);
            }
        }
    }

    private static void applyTransparentNotificationBackgroundView(Object backgroundView, Context context,
            float cornerRadiusPx) {
        if (backgroundView == null || context == null) {
            return;
        }
        Drawable drawable = createTransparentNotificationDrawable(context, backgroundView, cornerRadiusPx);
        ReflectUtils.invokeMethod(backgroundView, "setCustomBackground",
                new Class[]{Drawable.class}, drawable);
        ReflectUtils.invokeMethod(backgroundView, "setTint", new Class[]{int.class}, 0);
        ReflectUtils.invokeMethod(backgroundView, "setTintForNoBlur", new Class[]{int.class}, 0);
        if (backgroundView instanceof View) {
            View background = (View) backgroundView;
            syncNotificationGlassDrawableState(background);
            background.invalidate();
        }
    }

    private static boolean shouldUseTransparentNotificationBackground(View view) {
        Context context = view == null ? null : view.getContext();
        if (context == null) {
            return false;
        }
        ModuleConfig config = ModuleConfig.load(context);
        return config.enabled && config.notificationBackgroundTransparent;
    }

    private static Drawable createTransparentNotificationDrawable(Context context, Object backgroundView,
            float cornerRadiusPx) {
        if (backgroundView instanceof View) {
            View view = (View) backgroundView;
            NotificationGlassDrawable drawable = NOTIFICATION_GLASS_DRAWABLES.get(view);
            if (drawable == null) {
                drawable = new NotificationGlassDrawable(cornerRadiusPx);
                NOTIFICATION_GLASS_DRAWABLES.put(view, drawable);
            } else {
                drawable.setCornerRadiusPx(cornerRadiusPx);
            }
            return drawable;
        }
        return new ColorDrawable(Color.TRANSPARENT);
    }

    private static void syncNotificationGlassDrawableState(View backgroundView) {
        if (backgroundView == null || !shouldUseTransparentNotificationBackground(backgroundView)) {
            return;
        }
        NotificationGlassDrawable drawable = NOTIFICATION_GLASS_DRAWABLES.get(backgroundView);
        if (drawable == null) {
            return;
        }
        drawable.syncFromBackgroundState(
                ReflectUtils.getIntField(backgroundView, "mActualHeight", backgroundView.getHeight()),
                ReflectUtils.getIntField(backgroundView, "mClipTopAmount", 0),
                ReflectUtils.getIntField(backgroundView, "mClipBottomAmount", 0),
                ReflectUtils.getIntField(backgroundView, "mTintColor", 0),
                ReflectUtils.getIntField(backgroundView, "mDrawableAlpha", 255),
                ReflectUtils.getBooleanField(backgroundView, "mBottomAmountClips", true),
                ReflectUtils.getBooleanField(backgroundView, "mExpandAnimationRunning", false),
                backgroundView.getDrawableState());
    }

    private static int resolveSystemUiDrawableId(Context context, String name) {
        if (context == null || name == null) {
            return 0;
        }
        try {
            return context.getResources().getIdentifier(name, "drawable", SYSTEM_UI);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static NotificationLiquidGlassView ensureNotificationLiquidGlassOverlay(View root) {
        return null;
    }

    private static void hideNotificationLiquidGlassOverlay(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        NotificationLiquidGlassView existing = NOTIFICATION_GLASS_VIEWS.get(root);
        if (existing != null) {
            existing.setVisibility(View.GONE);
        }
    }

    private static void updateNotificationLiquidGlassBinding(ViewGroup row, NotificationLiquidGlassView glassView) {
        if (glassView != null) {
            glassView.setVisibility(View.GONE);
        }
    }

    private static ViewGroup findNotificationGlassSource(View row) {
        ViewParent parent = row == null ? null : row.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view instanceof ViewGroup) {
                return (ViewGroup) view;
            }
            parent = view.getParent();
        }
        return null;
    }

    private static float resolveNotificationCornerRadiusPx(View view) {
        Context context = view == null ? null : view.getContext();
        int radius = context == null ? 0 : getSystemUiDimen(context, "notification_background_radius");
        if (radius > 0) {
            return radius;
        }
        return view == null ? 0f : dp(view, 16f);
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

    private static boolean drawIosBatteryIfNeeded(Object drawable, Canvas canvas) {
        Context context = (Context) ReflectUtils.getField(drawable, "mContext");
        if (context == null || !(drawable instanceof Drawable)) {
            return false;
        }
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled) {
            return false;
        }
        int level = ReflectUtils.getIntField(drawable, "mLevel", 0);
        boolean pluggedIn = ReflectUtils.getBooleanField(drawable, "mPluggedIn", false);
        boolean charging = ReflectUtils.getBooleanField(drawable, "mCharging", false);
        int tintColor = resolveBatteryTintColor(drawable, Color.BLACK);
        IosBatteryPainter.draw(canvas, ((Drawable) drawable).getBounds(), level, pluggedIn, charging,
                tintColor);
        return true;
    }

    private static boolean drawIosBatteryViewIfNeeded(Object view, Canvas canvas) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        ModuleConfig config = ModuleConfig.load(batteryView.getContext());
        if (!config.enabled) {
            return false;
        }
        int level = ReflectUtils.getIntField(view, "mLastLevel", 0);
        boolean pluggedIn = ReflectUtils.getBooleanField(view, "mLastPlugged", false);
        boolean charging = ReflectUtils.getBooleanField(view, "mCharging", false);
        resizeIosBatteryView(batteryView, config, charging);
        int[] batteryRenderSize = getMergedBatteryRenderSize(batteryView, config, charging);
        int width = batteryRenderSize[0];
        int height = batteryRenderSize[1];
        int left = 0;
        int top = Math.round((batteryView.getHeight() - height) / 2f);
        int fillColor = resolveBatteryTintColor(view, Color.BLACK);
        IosBatteryPainter.draw(canvas, new Rect(left, top, left + width, top + height),
                level, pluggedIn, charging, fillColor);
        return true;
    }

    private static int resolveBatteryTintColor(Object target, int fallback) {
        int color = ReflectUtils.getIntField(target, "mFilterColor", 0);
        if (Color.alpha(color) != 0) {
            return color;
        }
        color = ReflectUtils.getIntField(target, "mIconTint", 0);
        if (Color.alpha(color) != 0) {
            return color;
        }
        color = ReflectUtils.getIntField(target, "mLightModeFillColor", 0);
        if (Color.alpha(color) != 0) {
            return color;
        }
        color = ReflectUtils.getIntField(target, "mDarkModeFillColor", 0);
        if (Color.alpha(color) != 0) {
            return color;
        }
        return normalizeIconColor(fallback);
    }

    private static boolean measureIosBatteryViewIfNeeded(Object view) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        ModuleConfig config = ModuleConfig.load(batteryView.getContext());
        if (!config.enabled) {
            return false;
        }
        boolean charging = ReflectUtils.getBooleanField(view, "mCharging", false);
        ReflectUtils.setMeasuredDimension(batteryView, iosBatteryMeasuredWidthWithMergedIcons(batteryView, config, charging),
                iosBatteryMeasuredHeightWithMergedIcons(batteryView, config));
        return true;
    }

    private static int normalizeIconColor(int color) {
        return Color.alpha(color) == 0 ? Color.BLACK : color;
    }

    private static void resizeIosBatteryView(View view, ModuleConfig config, boolean charging) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        int width = iosBatteryMeasuredWidthWithMergedIcons(view, config, charging);
        int height = iosBatteryMeasuredHeightWithMergedIcons(view, config);
        boolean changed = false;
        if (lp.width != width) {
            lp.width = width;
            changed = true;
        }
        if (lp.height > 0 && lp.height < height) {
            lp.height = height;
            changed = true;
        }
        if (changed) {
            view.setLayoutParams(lp);
            view.requestLayout();
        }
    }

    private static int iosBatteryMeasuredWidth(View view, ModuleConfig config, boolean charging) {
        return getMergedBatteryRenderSize(view, config, charging)[0];
    }

    private static int iosBatteryMeasuredHeight(View view, ModuleConfig config) {
        return getMergedBatteryRenderSize(view, config, false)[1] + dp(view, 2);
    }

    private static int iosBatteryMeasuredWidthWithMergedIcons(View view, ModuleConfig config, boolean charging) {
        return iosBatteryMeasuredWidth(view, config, charging);
    }

    private static int iosBatteryMeasuredHeightWithMergedIcons(View view, ModuleConfig config) {
        return iosBatteryMeasuredHeight(view, config);
    }

    private static int[] getMergedBatteryRenderSize(View batteryView, ModuleConfig config, boolean charging) {
        int size = Math.max(1, dp(batteryView, 24));
        return new int[]{size, size};
    }

    private static void applyIosBatteryStyleIfNeeded(Object drawable) {
        Context context = (Context) ReflectUtils.getField(drawable, "mContext");
        if (context == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled) {
            return;
        }
        ReflectUtils.setIntField(drawable, "mDarkModeBackgroundColor", Color.BLACK);
        ReflectUtils.setIntField(drawable, "mLightModeBackgroundColor", Color.BLACK);
        ReflectUtils.setIntField(drawable, "mDarkModeFillColor", Color.BLACK);
        ReflectUtils.setIntField(drawable, "mLightModeFillColor", Color.BLACK);
        ReflectUtils.setIntField(drawable, "mIconTint", Color.BLACK);
        ReflectUtils.setPaintColor(drawable, "mFramePaint", Color.BLACK);
        ReflectUtils.setPaintColor(drawable, "mBatteryPaint", Color.BLACK);
        ReflectUtils.setPaintColor(drawable, "mTextPaint", Color.WHITE);
        ReflectUtils.setPaintColor(drawable, "mWarningTextPaint", Color.WHITE);
        ReflectUtils.setPaintColor(drawable, "mBoltPaint", Color.WHITE);
        ReflectUtils.setPaintColor(drawable, "mPlusPaint", Color.WHITE);
    }

    private void hookConstructors(ClassLoader loader, String className, ViewAction action) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof View) {
                        View view = (View) thisObject;
                        view.post(() -> action.apply(view));
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook " + className, t);
        }
    }

    private void hookClockWeekday(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.systemui.statusbar.policy.Clock", false, loader);
            Method method = clazz.getDeclaredMethod("getSmallTime");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (!(thisObject instanceof TextView) || !(result instanceof CharSequence)) {
                    return result;
                }
                TextView clock = (TextView) thisObject;
                ModuleConfig config = ModuleConfig.load(clock.getContext());
                if (!config.enabled || !config.showClockWeekday || !"clock".equals(getSystemUiIdName(clock))) {
                    return result;
                }
                return appendWeekday((CharSequence) result, config);
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook Clock weekday", t);
        }
    }

    private static CharSequence appendWeekday(CharSequence timeText, ModuleConfig config) {
        if (timeText == null || timeText.length() == 0) {
            return timeText;
        }
        String weekday = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());
        if (config != null && config.clockWeekdayHidePrefix) {
            weekday = normalizeWeekdayLabel(weekday);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(timeText);
        builder.append(' ');
        builder.append(weekday);
        return builder;
    }

    private static String normalizeWeekdayLabel(String weekday) {
        if (weekday == null) {
            return "";
        }
        String normalized = weekday.trim();
        if (normalized.startsWith("星期") && normalized.length() > 2) {
            normalized = normalized.substring(2);
        } else if ((normalized.startsWith("周") || normalized.startsWith("週")) && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        if ("天".equals(normalized)) {
            return "日";
        }
        return normalized;
    }

    private static void trackConnectionRateView(View view) {
        TRACKED_CONNECTION_RATE_VIEWS.put(view, Boolean.TRUE);
        rememberConnectionRateState(view);
    }

    private static ConnectionRateThresholdState rememberConnectionRateState(View view) {
        ConnectionRateThresholdState state = CONNECTION_RATE_THRESHOLD_STATES.get(view);
        if (state == null) {
            state = new ConnectionRateThresholdState();
            CONNECTION_RATE_THRESHOLD_STATES.put(view, state);
        }
        return state;
    }

    private static void applyConnectionRateThresholdVisibility(View view) {
        if (view == null) {
            return;
        }
        ConnectionRateThresholdState state = rememberConnectionRateState(view);
        applyConnectionRateThresholdVisibility(view, state, ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, boolean baseShow, double rate) {
        if (view == null) {
            return;
        }
        applyConnectionRateThresholdVisibility(view, baseShow, rate, rememberConnectionRateState(view),
                ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, ConnectionRateThresholdState state,
            ModuleConfig config) {
        if (view == null || state == null || config == null) {
            return;
        }
        boolean thresholdEnabled = config.enabled && config.connectionRateThresholdEnabled;
        if (state.featureEnabled != thresholdEnabled) {
            state.featureEnabled = thresholdEnabled;
            state.resetCounters();
            if (!thresholdEnabled) {
                state.thresholdVisible = state.lastBaseShow;
            } else {
                state.thresholdVisible = false;
            }
        }
        if (!thresholdEnabled) {
            if (!state.hasBaseShow) {
                return;
            }
            ReflectUtils.setBooleanField(view, "mShow", state.lastBaseShow);
            applyConnectionRateVisibleState(view, state.lastBaseShow, ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        ReflectUtils.setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(view, state.thresholdVisible, ReflectUtils.getBooleanField(view, "mEnable", true),
                ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
    }

    private static void applyConnectionRateThresholdVisibility(View view, boolean baseShow, double rate,
            ConnectionRateThresholdState state, ModuleConfig config) {
        if (view == null || state == null || config == null) {
            return;
        }
        state.lastBaseShow = baseShow;
        state.hasBaseShow = true;
        state.lastRate = rate;
        boolean featureEnabled = config.enabled && config.connectionRateThresholdEnabled;
        long configSignature = getConnectionRateThresholdSignature(config);
        if (!featureEnabled) {
            state.featureEnabled = false;
            state.thresholdVisible = baseShow;
            state.resetCounters();
            state.lastConfigSignature = configSignature;
            ReflectUtils.setBooleanField(view, "mShow", baseShow);
            applyConnectionRateVisibleState(view, baseShow, ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        if (!state.featureEnabled) {
            state.reset();
        }
        state.featureEnabled = true;
        if (state.lastConfigSignature != configSignature) {
            state.resetCounters();
            state.lastConfigSignature = configSignature;
        }
        double safeRate = sanitizeConnectionRate(rate);
        int showThreshold = Math.max(config.connectionRateShowThresholdKb, config.connectionRateHideThresholdKb);
        int hideThreshold = Math.min(config.connectionRateShowThresholdKb, config.connectionRateHideThresholdKb);
        int showSamples = Math.max(1, config.connectionRateShowSampleCount);
        int hideSamples = Math.max(1, config.connectionRateHideSampleCount);
        if (!baseShow) {
            state.resetCounters();
            state.thresholdVisible = false;
            ReflectUtils.setBooleanField(view, "mShow", false);
            applyConnectionRateVisibleState(view, false, ReflectUtils.getBooleanField(view, "mEnable", true),
                    ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        if (state.thresholdVisible) {
            if (safeRate < hideThreshold) {
                state.belowCount++;
                state.aboveCount = 0;
                if (state.belowCount >= hideSamples) {
                    state.thresholdVisible = false;
                    state.belowCount = 0;
                }
            } else {
                state.belowCount = 0;
            }
        } else {
            if (safeRate >= showThreshold) {
                state.aboveCount++;
                state.belowCount = 0;
                if (state.aboveCount >= showSamples) {
                    state.thresholdVisible = true;
                    state.aboveCount = 0;
                }
            } else {
                state.aboveCount = 0;
            }
        }
        ReflectUtils.setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(view, state.thresholdVisible, ReflectUtils.getBooleanField(view, "mEnable", true),
                ReflectUtils.getBooleanField(view, "mIsDemoMode", false));
    }

    private static void applyConnectionRateVisibleState(View view, boolean thresholdVisible, boolean enable,
            boolean isDemoMode) {
        if (view == null) {
            return;
        }
        int visibility = thresholdVisible && enable ? View.VISIBLE : View.GONE;
        if (!isDemoMode && view.getVisibility() != visibility) {
            view.setVisibility(visibility);
        }
    }

    private static double sanitizeConnectionRate(double rate) {
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate < 0d) {
            return 0d;
        }
        return rate;
    }

    private static long getConnectionRateThresholdSignature(ModuleConfig config) {
        if (config == null) {
            return 0L;
        }
        int showThreshold = Math.max(config.connectionRateShowThresholdKb, config.connectionRateHideThresholdKb);
        int hideThreshold = Math.min(config.connectionRateShowThresholdKb, config.connectionRateHideThresholdKb);
        int showSamples = Math.max(1, config.connectionRateShowSampleCount);
        int hideSamples = Math.max(1, config.connectionRateHideSampleCount);
        long signature = 17L;
        signature = signature * 31L + (config.enabled && config.connectionRateThresholdEnabled ? 1L : 0L);
        signature = signature * 31L + showThreshold;
        signature = signature * 31L + hideThreshold;
        signature = signature * 31L + showSamples;
        signature = signature * 31L + hideSamples;
        return signature;
    }

    private static void ensureConfigRefreshObserver(Context context) {
        if (context == null || CONFIG_REFRESH_REGISTERED) {
            return;
        }
        ModuleConfig.rememberSystemUiContext(context);
        synchronized (CONFIG_REFRESH_LOCK) {
            if (CONFIG_REFRESH_REGISTERED) {
                return;
            }
            Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            ModuleConfig.rememberSystemUiContext(appContext);
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
            SETTINGS_OBSERVER = new ContentObserver(MAIN_HANDLER) {
                @Override
                public void onChange(boolean selfChange) {
                    ModuleConfig.invalidateCache();
                    refreshTrackedConnectionRateViews();
                }
            };
            try {
                appContext.getContentResolver().registerContentObserver(
                        Uri.parse("content://" + SettingsStore.AUTHORITY + "/settings"),
                        true,
                        SETTINGS_OBSERVER);
            } catch (Throwable ignored) {
            }
            USER_UNLOCKED_RECEIVER = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    refreshTrackedRuntimeViews();
                }
            };
            try {
                appContext.registerReceiver(USER_UNLOCKED_RECEIVER, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            } catch (Throwable ignored) {
            }
            CONFIG_REFRESH_REGISTERED = true;
            scheduleInitialRuntimeRefreshes();
        }
    }

    private static void scheduleInitialRuntimeRefreshes() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        for (long delay : INITIAL_RUNTIME_REFRESH_DELAYS_MS) {
            handler.postDelayed(FlymeStatusBarSizer::refreshTrackedRuntimeViews, delay);
        }
    }

    private static void registerRuntimeReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        try {
            Method method = Context.class.getDeclaredMethod("registerReceiver",
                    BroadcastReceiver.class, IntentFilter.class, int.class);
            method.invoke(context, receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            return;
        } catch (Throwable ignored) {
        }
        try {
            context.registerReceiver(receiver, filter);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshTrackedRuntimeViews() {
        refreshTrackedRuntimeViews(false);
    }

    private static void refreshTrackedRuntimeViews(boolean forceSignalRequery) {
        refreshTrackedConnectionRateViews();
        refreshTrackedBatteryViews();
    }

    private static void refreshTrackedConnectionRateViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<View> connectionRateViews = new ArrayList<>(TRACKED_CONNECTION_RATE_VIEWS.keySet());
            for (View view : connectionRateViews) {
                if (view == null) {
                    continue;
                }
                applyConnectionRateThresholdVisibility(view);
                view.requestLayout();
                view.invalidate();
            }
        });
    }

    private static void refreshTrackedBatteryViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<View> batteryViews = new ArrayList<>(TRACKED_BATTERY_VIEWS.keySet());
            for (View batteryView : batteryViews) {
                if (batteryView == null) {
                    continue;
                }
                ModuleConfig config = ModuleConfig.load(batteryView.getContext());
                if (!config.enabled) {
                    continue;
                }
                resizeIosBatteryView(batteryView, config, ReflectUtils.getBooleanField(batteryView, "mCharging", false));
                batteryView.requestLayout();
                batteryView.invalidate();
            }
        });
    }

    private static void disableAncestorClipping(View view, int maxDepth) {
        ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof ViewGroup && depth < maxDepth) {
            ViewGroup group = (ViewGroup) parent;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            parent = group.getParent();
            depth++;
        }
    }

    private static void disableChildClipping(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        for (int i = 0; i < group.getChildCount(); i++) {
            disableChildClipping(group.getChildAt(i));
        }
    }

    private static View findSystemUiChild(View root, String idName) {
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return null;
        }
        return root.findViewById(id);
    }

    private static View findAncestorByIdName(View view, String idName) {
        View current = view;
        while (current != null) {
            if (idName.equals(getSystemUiIdName(current))) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static int getSystemUiId(Context context, String name) {
        if (context == null || name == null) {
            return 0;
        }
        synchronized (SYSTEM_UI_ID_CACHE) {
            Integer cached = SYSTEM_UI_ID_CACHE.get(name);
            if (cached != null) {
                return cached;
            }
        }
        Resources resources = context.getResources();
        int id = resources.getIdentifier(name, "id", SYSTEM_UI);
        synchronized (SYSTEM_UI_ID_CACHE) {
            SYSTEM_UI_ID_CACHE.put(name, id);
        }
        return id;
    }

    private static int getSystemUiDimen(Context context, String name) {
        Resources resources = context.getResources();
        int id = resources.getIdentifier(name, "dimen", SYSTEM_UI);
        return id == 0 ? 0 : resources.getDimensionPixelSize(id);
    }

    private static String getSystemUiIdName(View view) {
        if (view == null) {
            return "";
        }
        synchronized (VIEW_ID_NAME_CACHE) {
            String cached = VIEW_ID_NAME_CACHE.get(view);
            if (cached != null) {
                return cached;
            }
        }
        int id = view.getId();
        if (id == View.NO_ID) {
            synchronized (VIEW_ID_NAME_CACHE) {
                VIEW_ID_NAME_CACHE.put(view, "");
            }
            return "";
        }
        try {
            String name = view.getResources().getResourceEntryName(id);
            synchronized (VIEW_ID_NAME_CACHE) {
                VIEW_ID_NAME_CACHE.put(view, name);
            }
            return name;
        } catch (Resources.NotFoundException ignored) {
            synchronized (VIEW_ID_NAME_CACHE) {
                VIEW_ID_NAME_CACHE.put(view, "");
            }
            return "";
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(View view, float value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(Context context, int value) {
        if (context == null) {
            return value;
        }
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static View getNavBarTransitionsView(Object transitions) {
        Object value = ReflectUtils.getField(transitions, "mView");
        return value instanceof View ? (View) value : null;
    }

    private static Context getNavBarTransitionsContext(Object transitions) {
        View view = getNavBarTransitionsView(transitions);
        return view == null ? null : view.getContext();
    }

    private interface ViewAction {
        void apply(View view);
    }

    private static final class ConnectionRateThresholdState {
        boolean featureEnabled;
        boolean thresholdVisible;
        boolean lastBaseShow;
        boolean hasBaseShow;
        double lastRate;
        long lastConfigSignature;
        int aboveCount;
        int belowCount;

        void reset() {
            thresholdVisible = false;
            resetCounters();
        }

        void resetCounters() {
            aboveCount = 0;
            belowCount = 0;
        }
    }
}
