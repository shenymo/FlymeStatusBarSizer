package com.example.flymestatusbarsizer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class FlymeStatusBarSizer extends XposedModule {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String TAG_IME_TOOLBAR_ROOT = "flyme_status_bar_sizer_ime_toolbar_root";
    private static final String TAG_IME_TOOLBAR_ORIGINAL = "flyme_status_bar_sizer_ime_toolbar_original";
    private static volatile FlymeStatusBarSizer MODULE;

    private static final WeakHashMap<View, int[]> ORIGINAL_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_MARGINS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_RUNTIME_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> ORIGINAL_INCLUDE_FONT_PADDING = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_TRANSLATION_Y = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, String> VIEW_ID_NAME_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_CONNECTION_RATE_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, ConnectionRateThresholdState> CONNECTION_RATE_THRESHOLD_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_BATTERY_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_STATUS_BAR_ICON_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, View> SIGNAL_DRAWABLE_OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<TelephonyManager, Integer> TELEPHONY_MANAGER_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<SignalStrength, Integer> SIGNAL_STRENGTH_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationLiquidGlassView> NOTIFICATION_GLASS_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationGlassDrawable> NOTIFICATION_GLASS_DRAWABLES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> CLOCK_SECOND_REFRESH_VIEWS = new WeakHashMap<>();
    private static final Object CONFIG_REFRESH_LOCK = new Object();
    private static final long[] INITIAL_RUNTIME_REFRESH_DELAYS_MS = {1000L, 3000L};
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static Handler MAIN_HANDLER;
    private static final Runnable CLOCK_SECOND_REFRESH_RUNNABLE = FlymeStatusBarSizer::refreshClockViewsForSecondTick;
    private static final HashMap<String, Integer> SYSTEM_UI_ID_CACHE = new HashMap<>();
    private static volatile int LAST_SIGNAL_LEVEL = -1;
    private static volatile int LAST_SIGNAL_SUB_ID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static volatile int LAST_CELLULAR_LEVEL = -1;
    private static volatile int LAST_ACTIVE_SUBSCRIPTION_COUNT = -1;
    private static final float IME_TOOLBAR_ICON_VIEWPORT = 960f;
    private static final String[] IME_TOOLBAR_ACTIONS = {
            "paste", "delete", "select_all", "copy", "switch_ime"
    };

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) {
            return;
        }
        MODULE = this;
        ModuleConfig.setConfigChangedCallback(() -> {
            Handler handler = MAIN_HANDLER;
            if (handler != null) {
                handler.post(FlymeStatusBarSizer::refreshTrackedRuntimeViews);
            } else {
                refreshTrackedRuntimeViews();
            }
        });
        ModuleConfig.attachToModule(this);
        ClassLoader loader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (SYSTEM_UI.equals(packageName)) {
            hookSystemUi(loader);
        }
        hookInputMethodService(loader);
    }

    private void hookSystemUi(ClassLoader loader) {
        hookConnectionRateView(loader);
        hookSignalImageAssignments();
        hookSignalTintUpdates();
        hookSignalViewLayout();
        hookSignalDrawableLevelChanges(loader);
        hookTelephonyCreateForSubscriptionId();
        hookTelephonyGetSignalStrength();
        hookSignalStrengthGetLevel();
        hookStatusBarIconConstructors(loader);
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
            if (isBatteryCodeDrawEnabled(config)) {
                disableAncestorClipping(view, 6);
                resizeIosBatteryView(view, config, ReflectUtils.getBooleanField(view, "mCharging", false));
            }
        });
        hookFlymeBatteryMeterViewDraw(loader);
        hookFlymeBatteryMeterViewMeasure(loader);
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryTextView", view -> {
            ModuleConfig config = ModuleConfig.load(view.getContext());
            if (!isBatteryCodeDrawEnabled(config) || !(view instanceof TextView)) {
                return;
            }
            TextView textView = (TextView) view;
            textView.setTextColor(Color.WHITE);
            ReflectUtils.setIntField(textView, "mNormalColor", Color.WHITE);
            ReflectUtils.setIntField(textView, "mLowColor", Color.WHITE);
        });
        hookBatteryDrawable(loader);
        hookClockWeekday(loader);
        hookClockAndCarrierTextSize(loader);
    }

    private void hookInputMethodService(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("android.inputmethodservice.InputMethodService", false, loader);
            Method method = clazz.getDeclaredMethod("setInputView", View.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!(thisObject instanceof Context) || !(arg instanceof View)) {
                    return result;
                }
                Context context = (Context) thisObject;
                View inputView = (View) arg;
                ModuleConfig.rememberSystemUiContext(context);
                ensureConfigRefreshObserver(context);
                TRACKED_INPUT_METHOD_VIEWS.put(thisObject, inputView);
                inputView.post(() -> attachImeToolbarIfNeeded(thisObject, inputView));
                return result;
            });

            Class<?> editorInfoClass = Class.forName("android.view.inputmethod.EditorInfo", false, loader);
            Method onStartInputView = clazz.getDeclaredMethod("onStartInputView", editorInfoClass, boolean.class);
            onStartInputView.setAccessible(true);
            hook(onStartInputView).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    inputView.post(() -> attachImeToolbarIfNeeded(thisObject, inputView));
                }
                return result;
            });

            Method showWindow = clazz.getDeclaredMethod("showWindow", boolean.class);
            showWindow.setAccessible(true);
            hook(showWindow).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    inputView.post(() -> attachImeToolbarIfNeeded(thisObject, inputView));
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook InputMethodService.setInputView", t);
        }
    }

    private void hookSignalImageAssignments() {
        try {
            Method setImageResource = ImageView.class.getDeclaredMethod("setImageResource", int.class);
            setImageResource.setAccessible(true);
            hook(setImageResource).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof ImageView) {
                    onSignalImageResourceAssigned((ImageView) target, ((Integer) chain.getArg(0)).intValue());
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setImageResource", t);
        }
        try {
            Method setImageDrawable = ImageView.class.getDeclaredMethod("setImageDrawable", Drawable.class);
            setImageDrawable.setAccessible(true);
            hook(setImageDrawable).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof ImageView) {
                    onSignalImageDrawableAssigned((ImageView) target, (Drawable) chain.getArg(0));
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setImageDrawable", t);
        }
    }

    private void hookSignalTintUpdates() {
        try {
            Method setImageTintList = ImageView.class.getDeclaredMethod("setImageTintList", ColorStateList.class);
            setImageTintList.setAccessible(true);
            hook(setImageTintList).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof ImageView) {
                    syncSignalTintToCustomDrawable((ImageView) target);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setImageTintList", t);
        }
        try {
            Method setColorFilter = ImageView.class.getDeclaredMethod("setColorFilter", ColorFilter.class);
            setColorFilter.setAccessible(true);
            hook(setColorFilter).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof ImageView) {
                    syncSignalColorFilterToCustomDrawable((ImageView) target,
                            chain.getArg(0) instanceof ColorFilter ? (ColorFilter) chain.getArg(0) : null);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setColorFilter(ColorFilter)", t);
        }
    }

    private void hookSignalViewLayout() {
        try {
            Method onLayout = View.class.getDeclaredMethod(
                    "onLayout", boolean.class, int.class, int.class, int.class, int.class);
            onLayout.setAccessible(true);
            hook(onLayout).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    onSignalViewLayoutChanged((View) target);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook View.onLayout for signal debug", t);
        }
    }

    private void hookSignalDrawableLevelChanges(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.settingslib.graph.SignalDrawable", false, loader);
            Method method = clazz.getDeclaredMethod("onLevelChange", int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof Drawable) {
                    Drawable drawable = (Drawable) target;
                    int rawLevel = chain.getArg(0) instanceof Integer ? (Integer) chain.getArg(0) : drawable.getLevel();
                    onSignalDrawableLevelChanged(drawable, rawLevel);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SignalDrawable.onLevelChange", t);
        }
    }

    private void hookTelephonyCreateForSubscriptionId() {
        try {
            Method method = TelephonyManager.class.getDeclaredMethod("createForSubscriptionId", int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (!isSignalCodeDrawEnabled(config)) {
                    return result;
                }
                if (result instanceof TelephonyManager && chain.getArg(0) instanceof Integer) {
                    TELEPHONY_MANAGER_SUB_IDS.put((TelephonyManager) result, (Integer) chain.getArg(0));
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager.createForSubscriptionId", t);
        }
    }

    private void hookTelephonyGetSignalStrength() {
        try {
            Method method = TelephonyManager.class.getDeclaredMethod("getSignalStrength");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (!isSignalCodeDrawEnabled(config)) {
                    return result;
                }
                if (target instanceof TelephonyManager && result instanceof SignalStrength) {
                    Integer subId = TELEPHONY_MANAGER_SUB_IDS.get(target);
                    if (subId != null) {
                        SIGNAL_STRENGTH_SUB_IDS.put((SignalStrength) result, subId);
                        LAST_SIGNAL_SUB_ID = subId;
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager.getSignalStrength", t);
        }
    }

    private void hookSignalStrengthGetLevel() {
        try {
            Method method = SignalStrength.class.getDeclaredMethod("getLevel");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (!isSignalCodeDrawEnabled(config)) {
                    return result;
                }
                if (result instanceof Integer) {
                    int level = (Integer) result;
                    LAST_CELLULAR_LEVEL = level;
                    Object target = chain.getThisObject();
                    if (target instanceof SignalStrength) {
                        Integer subId = SIGNAL_STRENGTH_SUB_IDS.get(target);
                        if (subId != null) {
                            LAST_SIGNAL_SUB_ID = subId;
                        }
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SignalStrength.getLevel", t);
        }
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
        if (!isBatteryCodeDrawEnabled(config)) {
            return false;
        }
        int level = ReflectUtils.getIntField(drawable, "mLevel", 0);
        boolean pluggedIn = ReflectUtils.getBooleanField(drawable, "mPluggedIn", false);
        boolean charging = ReflectUtils.getBooleanField(drawable, "mCharging", false);
        int tintColor = resolveBatteryTintColor(drawable, Color.BLACK);
        int textColor = resolveBatteryTextColor(tintColor);
        boolean showLevelText = config.batteryLevelTextEnabled;
        drawBatteryByStyle(config, canvas, ((Drawable) drawable).getBounds(), level, pluggedIn, charging,
                tintColor, textColor, showLevelText);
        return true;
    }

    private static boolean drawIosBatteryViewIfNeeded(Object view, Canvas canvas) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        ModuleConfig config = ModuleConfig.load(batteryView.getContext());
        if (!isBatteryCodeDrawEnabled(config)) {
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
        int textColor = resolveBatteryTextColor(fillColor);
        boolean showLevelText = config.batteryLevelTextEnabled;
        drawBatteryByStyle(config, canvas, new Rect(left, top, left + width, top + height),
                level, pluggedIn, charging, fillColor, textColor, showLevelText);
        return true;
    }

    private static int resolveBatteryTextColor(int tintColor) {
        int color = normalizeIconColor(tintColor);
        double luminance = (0.299d * Color.red(color)
                + 0.587d * Color.green(color)
                + 0.114d * Color.blue(color)) / 255d;
        return luminance >= 0.5d ? Color.BLACK : Color.WHITE;
    }

    private static void drawBatteryByStyle(ModuleConfig config, Canvas canvas, Rect bounds, int level,
            boolean pluggedIn, boolean charging, int fillColor, int textColor, boolean showLevelText) {
        float textScale = resolveBatteryInnerTextScale(config);
        Typeface typeface = BatteryTextFontHelper.resolveTypeface(ModuleConfig.getSystemUiContext(), config == null
                ? SettingsStore.DEFAULT_BATTERY_TEXT_FONT
                : config.batteryTextFont);
        int style = config == null
                ? SettingsStore.DEFAULT_BATTERY_ICON_STYLE
                : SettingsStore.normalizeBatteryStyle(config.batteryIconStyle);
        boolean hollow = config != null && config.batteryHollowEnabled;
        if (style == SettingsStore.BATTERY_STYLE_ONEUI) {
            OneUiBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging,
                    fillColor, textColor, showLevelText, textScale, typeface, hollow);
            return;
        }
        IosBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging,
                fillColor, textColor, showLevelText, textScale, typeface, hollow);
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
        if (!isBatteryCodeDrawEnabled(config)) {
            return false;
        }
        boolean charging = ReflectUtils.getBooleanField(view, "mCharging", false);
        ReflectUtils.setMeasuredDimension(batteryView, iosBatteryMeasuredWidthWithMergedIcons(batteryView, config, charging),
                iosBatteryMeasuredHeightWithMergedIcons(batteryView, config));
        return true;
    }

    private static boolean isBatteryCodeDrawEnabled(ModuleConfig config) {
        return config != null && config.enabled && config.batteryCodeDrawEnabled;
    }

    private static boolean isSignalCodeDrawEnabled(ModuleConfig config) {
        return config != null && config.enabled && config.signalCodeDrawEnabled;
    }

    private static float resolveStatusBarIconScale(ModuleConfig config) {
        int percent = config == null
                ? SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT
                : SettingsStore.normalizeScalePercent(config.statusBarIconScalePercent);
        return percent / 100f;
    }

    private static float resolveBatteryInnerTextScale(ModuleConfig config) {
        int percent = config == null
                ? SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT
                : SettingsStore.normalizeScalePercent(config.batteryInnerTextScalePercent);
        return percent / 100f;
    }

    private static float resolveClockAndCarrierTextScale(ModuleConfig config) {
        int percent = config == null
                ? SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT
                : SettingsStore.normalizeScalePercent(config.clockAndCarrierTextSizePercent);
        return percent / 100f;
    }

    private static int resolveClockFontWeight(ModuleConfig config) {
        if (config == null || !config.clockBoldEnabled) {
            return 400;
        }
        return Math.max(100, Math.min(900, config.clockFontWeight));
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
        int size = scaleSize(dp(batteryView, 24), resolveStatusBarIconScale(config));
        return new int[]{size, size};
    }

    private static void applyIosBatteryStyleIfNeeded(Object drawable) {
        Context context = (Context) ReflectUtils.getField(drawable, "mContext");
        if (context == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(context);
        if (!isBatteryCodeDrawEnabled(config)) {
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

    private static void attachImeToolbarIfNeeded(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        Context context = inputView.getContext();
        if (context == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(context);
        if (!config.enabled || !config.imeToolbarEnabled) {
            detachImeToolbarIfPresent(inputMethodService);
            return;
        }
        ViewGroup inputFrame = asViewGroup(ReflectUtils.getField(inputMethodService, "mInputFrame"));
        if (inputFrame == null) {
            return;
        }
        View current = inputFrame.getChildCount() > 0 ? inputFrame.getChildAt(0) : null;
        if (current != null && TAG_IME_TOOLBAR_ROOT.equals(current.getTag())) {
            updateImeToolbarState(inputMethodService, current);
            return;
        }
        if (current != inputView) {
            return;
        }
        if (current == null) {
            return;
        }
        if (current.getParent() != inputFrame) {
            return;
        }
        inputFrame.removeAllViews();
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setTag(TAG_IME_TOOLBAR_ROOT);
        disableAncestorClipping(container, 2);

        ViewGroup currentParent = asViewGroup(current.getParent());
        if (currentParent != null) {
            currentParent.removeView(current);
        }
        current.setTag(TAG_IME_TOOLBAR_ORIGINAL);
        container.addView(current, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(createImeToolbarView(context, inputMethodService, current),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    private static void detachImeToolbarIfPresent(Object inputMethodService) {
        ViewGroup inputFrame = asViewGroup(ReflectUtils.getField(inputMethodService, "mInputFrame"));
        if (inputFrame == null || inputFrame.getChildCount() == 0) {
            return;
        }
        View current = inputFrame.getChildAt(0);
        if (current == null || !TAG_IME_TOOLBAR_ROOT.equals(current.getTag()) || !(current instanceof ViewGroup)) {
            return;
        }
        ViewGroup container = (ViewGroup) current;
        View original = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (TAG_IME_TOOLBAR_ORIGINAL.equals(child.getTag())) {
                original = child;
                break;
            }
        }
        if (original == null) {
            return;
        }
        container.removeView(original);
        original.setTag(null);
        inputFrame.removeAllViews();
        inputFrame.addView(original, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    private static void updateImeToolbarState(Object inputMethodService, View toolbarRoot) {
        if (!(toolbarRoot instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) toolbarRoot;
        if (group.getChildCount() < 2) {
            return;
        }
        View toolbar = group.getChildAt(group.getChildCount() - 1);
        if (!(toolbar instanceof LinearLayout)) {
            return;
        }
        View originalInputView = group.getChildAt(0);
        ViewGroup.LayoutParams layoutParams = toolbar.getLayoutParams();
        group.removeView(toolbar);
        LinearLayout rebuiltBar = createImeToolbarView(group.getContext(), inputMethodService, originalInputView);
        group.addView(rebuiltBar, new LinearLayout.LayoutParams(
                layoutParams != null ? layoutParams.width : ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParams != null ? layoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT));
        group.requestLayout();
        group.invalidate();
    }

    private static LinearLayout createImeToolbarView(Context context, Object inputMethodService, View inputView) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(context, 18);
        int vertical = dp(context, 6);
        bar.setPadding(horizontal, vertical, horizontal, vertical);
        applyImeToolbarBackground(bar, inputView);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(context, 40), 1f);
        buttonParams.leftMargin = dp(context, 4);
        buttonParams.rightMargin = dp(context, 4);
        ArrayList<String> orderedActions = resolveImeToolbarOrder(ModuleConfig.load(context));
        for (String action : orderedActions) {
            ImageButton button = new ImageButton(context);
            configureImeToolbarButton(context, button, action, getImeToolbarActionLabel(action));
            button.setTag(action);
            bar.addView(button, new LinearLayout.LayoutParams(buttonParams));
        }

        applyImeToolbarIconTint(bar, resolveImeToolbarIconColor(context));
        bindImeToolbarButtonActions(inputMethodService, bar);
        updateImeToolbarStateForButtons(inputMethodService, bar);
        return bar;
    }

    private static void updateImeToolbarStateForButtons(Object inputMethodService, LinearLayout bar) {
        updatePasteButtonEnabled(inputMethodService, findImeToolbarButton(bar, "paste"));
    }

    private static View findImeToolbarButton(LinearLayout bar, String action) {
        if (bar == null || TextUtils.isEmpty(action)) {
            return null;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child != null && action.equals(child.getTag())) {
                return child;
            }
        }
        return null;
    }

    private static ArrayList<String> resolveImeToolbarOrder(ModuleConfig config) {
        ArrayList<String> result = new ArrayList<>();
        if (config != null && !TextUtils.isEmpty(config.imeToolbarOrder)) {
            String[] parts = config.imeToolbarOrder.split(",");
            for (String part : parts) {
                String action = part == null ? "" : part.trim();
                if (isValidImeToolbarAction(action) && !result.contains(action)) {
                    result.add(action);
                }
            }
        }
        for (String action : IME_TOOLBAR_ACTIONS) {
            if (!result.contains(action)) {
                result.add(action);
            }
        }
        return result;
    }

    private static boolean isValidImeToolbarAction(String action) {
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        for (String candidate : IME_TOOLBAR_ACTIONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }

    private static String getImeToolbarActionLabel(String action) {
        if ("paste".equals(action)) {
            return "粘贴";
        }
        if ("delete".equals(action)) {
            return "删除";
        }
        if ("select_all".equals(action)) {
            return "全选";
        }
        if ("copy".equals(action)) {
            return "复制";
        }
        if ("switch_ime".equals(action)) {
            return "切换输入法";
        }
        return action;
    }

    private static void applyImeToolbarBackground(LinearLayout bar, View inputView) {
        if (bar == null) {
            return;
        }
        Drawable background = inputView == null ? null : inputView.getBackground();
        if (background != null) {
            Drawable copied = background.getConstantState() != null
                    ? background.getConstantState().newDrawable().mutate()
                    : background.mutate();
            bar.setBackground(copied);
        } else {
            bar.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void configureImeToolbarButton(Context context, ImageButton button, String iconType, String desc) {
        Drawable drawable = createImeToolbarIconDrawable(context, iconType);
        button.setImageDrawable(drawable);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(desc);
        button.setBackground(new ColorDrawable(Color.TRANSPARENT));
        button.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
    }

    private static void applyImeToolbarIconTint(LinearLayout bar, int color) {
        if (bar == null) {
            return;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ImageButton) {
                ((ImageButton) child).setColorFilter(color);
            }
        }
    }

    private static int resolveImeToolbarIconColor(Context context) {
        if (context == null) {
            return Color.WHITE;
        }
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    private static Drawable createImeToolbarIconDrawable(Context context, String iconType) {
        String pathData = getImeToolbarIconPathData(iconType);
        if (TextUtils.isEmpty(pathData) || context == null) {
            return null;
        }
        try {
            return new ImeToolbarPathDrawable(pathData, Math.max(1, dp(context, 24)));
        } catch (Throwable t) {
            android.util.Log.w(TAG, "Failed to create ime toolbar drawable: " + iconType, t);
            return null;
        }
    }

    private static String getImeToolbarIconPathData(String iconType) {
        if (TextUtils.isEmpty(iconType)) {
            return null;
        }
        switch (iconType) {
            case "paste":
                return "M720,840L664,783L727,720L480,720L480,640L727,640L664,576L720,520L880,680L720,840ZM840,440L760,440L760,200Q760,200 760,200Q760,200 760,200L680,200L680,320L280,320L280,200L200,200Q200,200 200,200Q200,200 200,200L200,760Q200,760 200,760Q200,760 200,760L400,760L400,840L200,840Q167,840 143.5,816.5Q120,793 120,760L120,200Q120,167 143.5,143.5Q167,120 200,120L367,120Q378,85 410,62.5Q442,40 480,40Q520,40 551.5,62.5Q583,85 594,120L760,120Q793,120 816.5,143.5Q840,167 840,200L840,440ZM508.5,188.5Q520,177 520,160Q520,143 508.5,131.5Q497,120 480,120Q463,120 451.5,131.5Q440,143 440,160Q440,177 451.5,188.5Q463,200 480,200Q497,200 508.5,188.5Z";
            case "delete":
                return "M280,840Q247,840 223.5,816.5Q200,793 200,760L200,240L160,240L160,160L360,160L360,120L600,120L600,160L800,160L800,240L760,240L760,760Q760,793 736.5,816.5Q713,840 680,840L280,840ZM680,240L280,240L280,760Q280,760 280,760Q280,760 280,760L680,760Q680,760 680,760Q680,760 680,760L680,240ZM360,680L440,680L440,320L360,320L360,680ZM520,680L600,680L600,320L520,320L520,680ZM280,240L280,240L280,760Q280,760 280,760Q280,760 280,760L280,760Q280,760 280,760Q280,760 280,760L280,240Z";
            case "select_all":
                return "M280,680L280,280L680,280L680,680L280,680ZM360,600L600,600L600,360L360,360L360,600ZM200,760L200,840Q167,840 143.5,816.5Q120,793 120,760L200,760ZM120,680L120,600L200,600L200,680L120,680ZM120,520L120,440L200,440L200,520L120,520ZM120,360L120,280L200,280L200,360L120,360ZM200,200L120,200Q120,167 143.5,143.5Q167,120 200,120L200,200ZM280,840L280,760L360,760L360,840L280,840ZM280,200L280,120L360,120L360,200L280,200ZM440,840L440,760L520,760L520,840L440,840ZM440,200L440,120L520,120L520,200L440,200ZM600,840L600,760L680,760L680,840L600,840ZM600,200L600,120L680,120L680,200L600,200ZM760,840L760,760L840,760Q840,793 816.5,816.5Q793,840 760,840ZM760,680L760,600L840,600L840,680L760,680ZM760,520L760,440L840,440L840,520L760,520ZM760,360L760,280L840,280L840,360L760,360ZM760,200L760,120Q793,120 816.5,143.5Q840,167 840,200L760,200Z";
            case "copy":
                return "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640Q720,640 720,640Q720,640 720,640L720,160Q720,160 720,160Q720,160 720,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800Q200,800 200,800Q200,800 200,800L640,800L640,880L200,880ZM360,640Q360,640 360,640Q360,640 360,640L360,160Q360,160 360,160Q360,160 360,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640Z";
            case "switch_ime":
                return "M320,680L640,680L640,600L320,600L320,680ZM200,560L280,560L280,480L200,480L200,560ZM320,560L400,560L400,480L320,480L320,560ZM440,560L520,560L520,480L440,480L440,560ZM560,560L640,560L640,480L560,480L560,560ZM680,560L760,560L760,480L680,480L680,560ZM160,800Q127,800 103.5,776.5Q80,753 80,720L80,240Q80,207 103.5,183.5Q127,160 160,160L800,160Q833,160 856.5,183.5Q880,207 880,240L880,720Q880,753 856.5,776.5Q833,800 800,800L160,800ZM160,360L800,360L800,240Q800,240 800,240Q800,240 800,240L160,240Q160,240 160,240Q160,240 160,240L160,360ZM160,720L800,720Q800,720 800,720Q800,720 800,720L800,440L160,440L160,720Q160,720 160,720Q160,720 160,720ZM160,720Q160,720 160,720Q160,720 160,720L160,440L160,440L160,720Q160,720 160,720Q160,720 160,720Z";
            default:
                return null;
        }
    }

    private static final class ImeToolbarPathDrawable extends Drawable {
        private final Path path;
        private final Paint paint;
        private final int intrinsicSize;
        private int alpha = 255;

        ImeToolbarPathDrawable(String pathData, int intrinsicSize) {
            this.path = SimplePathDataParser.parse(pathData);
            this.intrinsicSize = intrinsicSize;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            if (path == null) {
                return;
            }
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / IME_TOOLBAR_ICON_VIEWPORT,
                    bounds.height() / IME_TOOLBAR_ICON_VIEWPORT);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return alpha < 255 ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }
    }

    private static final class SimplePathDataParser {
        private final String data;
        private int index;
        private final int length;

        private SimplePathDataParser(String data) {
            this.data = data == null ? "" : data;
            this.length = this.data.length();
        }

        static Path parse(String data) {
            try {
                return new SimplePathDataParser(data).parsePath();
            } catch (Throwable t) {
                android.util.Log.w(TAG, "Failed to parse path data", t);
                return null;
            }
        }

        private Path parsePath() {
            Path path = new Path();
            float currentX = 0f;
            float currentY = 0f;
            float startX = 0f;
            float startY = 0f;
            char command = ' ';
            while (hasMore()) {
                skipSeparators();
                if (!hasMore()) {
                    break;
                }
                char next = data.charAt(index);
                if (isCommand(next)) {
                    command = next;
                    index++;
                } else if (command == ' ') {
                    throw new IllegalArgumentException("Path data missing command at " + index);
                }
                boolean relative = Character.isLowerCase(command);
                switch (Character.toUpperCase(command)) {
                    case 'M': {
                        boolean firstPoint = true;
                        while (hasNumber()) {
                            float x = nextFloat();
                            float y = nextFloat();
                            if (relative) {
                                x += currentX;
                                y += currentY;
                            }
                            if (firstPoint) {
                                path.moveTo(x, y);
                                startX = x;
                                startY = y;
                                firstPoint = false;
                            } else {
                                path.lineTo(x, y);
                            }
                            currentX = x;
                            currentY = y;
                        }
                        break;
                    }
                    case 'L': {
                        while (hasNumber()) {
                            float x = nextFloat();
                            float y = nextFloat();
                            if (relative) {
                                x += currentX;
                                y += currentY;
                            }
                            path.lineTo(x, y);
                            currentX = x;
                            currentY = y;
                        }
                        break;
                    }
                    case 'H': {
                        while (hasNumber()) {
                            float x = nextFloat();
                            if (relative) {
                                x += currentX;
                            }
                            path.lineTo(x, currentY);
                            currentX = x;
                        }
                        break;
                    }
                    case 'V': {
                        while (hasNumber()) {
                            float y = nextFloat();
                            if (relative) {
                                y += currentY;
                            }
                            path.lineTo(currentX, y);
                            currentY = y;
                        }
                        break;
                    }
                    case 'Q': {
                        while (hasNumber()) {
                            float controlX = nextFloat();
                            float controlY = nextFloat();
                            float endX = nextFloat();
                            float endY = nextFloat();
                            if (relative) {
                                controlX += currentX;
                                controlY += currentY;
                                endX += currentX;
                                endY += currentY;
                            }
                            path.quadTo(controlX, controlY, endX, endY);
                            currentX = endX;
                            currentY = endY;
                        }
                        break;
                    }
                    case 'Z': {
                        path.close();
                        currentX = startX;
                        currentY = startY;
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unsupported command: " + command);
                }
            }
            return path;
        }

        private boolean hasMore() {
            return index < length;
        }

        private boolean hasNumber() {
            skipSeparators();
            return hasMore() && !isCommand(data.charAt(index));
        }

        private void skipSeparators() {
            while (hasMore()) {
                char c = data.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',') {
                    index++;
                    continue;
                }
                break;
            }
        }

        private float nextFloat() {
            skipSeparators();
            if (!hasMore()) {
                throw new IllegalArgumentException("Unexpected end of path data");
            }
            int start = index;
            boolean seenDot = false;
            boolean seenExp = false;
            while (hasMore()) {
                char c = data.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                    continue;
                }
                if (c == '.' && !seenDot) {
                    seenDot = true;
                    index++;
                    continue;
                }
                if ((c == 'e' || c == 'E') && !seenExp) {
                    seenExp = true;
                    seenDot = false;
                    index++;
                    if (hasMore()) {
                        char sign = data.charAt(index);
                        if (sign == '+' || sign == '-') {
                            index++;
                        }
                    }
                    continue;
                }
                if ((c == '-' || c == '+') && index == start) {
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid number at " + index);
            }
            return Float.parseFloat(data.substring(start, index));
        }

        private boolean isCommand(char c) {
            switch (c) {
                case 'M':
                case 'm':
                case 'L':
                case 'l':
                case 'H':
                case 'h':
                case 'V':
                case 'v':
                case 'Q':
                case 'q':
                case 'Z':
                case 'z':
                    return true;
                default:
                    return false;
            }
        }
    }

    private static void bindImeToolbarButtonActions(Object inputMethodService, LinearLayout bar) {
        if (bar == null) {
            return;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View button = bar.getChildAt(i);
            if (button == null || !(button.getTag() instanceof String)) {
                continue;
            }
            String action = (String) button.getTag();
            if ("paste".equals(action)) {
                button.setOnClickListener(v -> performPasteAction(inputMethodService, v.getContext()));
            } else if ("delete".equals(action)) {
                button.setOnClickListener(v -> performDeleteAction(inputMethodService));
            } else if ("select_all".equals(action)) {
                button.setOnClickListener(v ->
                        performEditorAction(inputMethodService, android.R.id.selectAll));
            } else if ("copy".equals(action)) {
                button.setOnClickListener(v ->
                        performEditorAction(inputMethodService, android.R.id.copy));
            } else if ("switch_ime".equals(action)) {
                button.setOnClickListener(v -> {
                    Context context = v.getContext();
                    try {
                        InputMethodManager imm =
                                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showInputMethodPicker();
                        }
                    } catch (Throwable t) {
                        android.util.Log.w(TAG, "Failed to show input method picker", t);
                    }
                });
            }
        }
    }

    private static void performDeleteAction(Object inputMethodService) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        try {
            CharSequence selectedText = connection.getSelectedText(0);
            if (!TextUtils.isEmpty(selectedText)) {
                connection.commitText("", 1);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            connection.deleteSurroundingText(1, 0);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "Failed to delete surrounding text", t);
        }
    }

    private static void performEditorAction(Object inputMethodService, int actionId) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null) {
            return;
        }
        try {
            connection.performContextMenuAction(actionId);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "Failed to perform editor action: " + actionId, t);
        }
    }

    private static void performPasteAction(Object inputMethodService, Context context) {
        InputConnection connection = getCurrentInputConnectionCompat(inputMethodService);
        if (connection == null || context == null) {
            return;
        }
        try {
            if (connection.performContextMenuAction(android.R.id.paste)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                return;
            }
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() <= 0) {
                return;
            }
            CharSequence text = clipData.getItemAt(0).coerceToText(context);
            if (!TextUtils.isEmpty(text)) {
                connection.commitText(text, 1);
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "Failed to paste clipboard text", t);
        }
    }

    private static void updatePasteButtonEnabled(Object inputMethodService, View pasteButton) {
        if (pasteButton == null) {
            return;
        }
        boolean enabled = getCurrentInputConnectionCompat(inputMethodService) != null;
        pasteButton.setEnabled(enabled);
        pasteButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private static InputConnection getCurrentInputConnectionCompat(Object inputMethodService) {
        Object value = ReflectUtils.invokeNoArg(inputMethodService, "getCurrentInputConnection");
        return value instanceof InputConnection ? (InputConnection) value : null;
    }

    private static ViewGroup asViewGroup(Object object) {
        return object instanceof ViewGroup ? (ViewGroup) object : null;
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
                if (!config.enabled || !isPrimaryStatusBarClockView(clock)) {
                    return result;
                }
                Calendar calendar = resolveClockCalendar(clock);
                CharSequence customText = buildCustomClockText(clock, config, calendar);
                if (customText != null) {
                    return customText;
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook Clock weekday", t);
        }
    }

    private static CharSequence buildCustomClockText(
            TextView clock, ModuleConfig config, Calendar calendar) {
        if (clock == null || config == null) {
            return null;
        }
        String format = config.clockCustomFormat;
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        Locale locale = resolveClockLocale(clock);
        return renderClockExpression(format, calendar, locale);
    }

    private static CharSequence renderClockExpression(String format, Calendar calendar, Locale locale) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        Calendar safeCalendar = calendar != null ? calendar : Calendar.getInstance();
        Locale safeLocale = locale != null ? locale : Locale.getDefault();
        String result = format;
        result = result.replace("{HH}", pad2(safeCalendar.get(Calendar.HOUR_OF_DAY)));
        result = result.replace("{H}", Integer.toString(safeCalendar.get(Calendar.HOUR_OF_DAY)));
        result = result.replace("{hh}", pad2(resolveHour12(safeCalendar)));
        result = result.replace("{h}", Integer.toString(resolveHour12(safeCalendar)));
        result = result.replace("{mm}", pad2(safeCalendar.get(Calendar.MINUTE)));
        result = result.replace("{ss}", pad2(safeCalendar.get(Calendar.SECOND)));
        result = result.replace("{week}", resolveWeekdayLabel(safeCalendar, safeLocale, true));
        result = result.replace("{week_short}", resolveWeekdayLabel(safeCalendar, safeLocale, false));
        result = result.replace("{week_1}", resolveWeekdaySingleLabel(safeCalendar, safeLocale));
        result = result.replace("{ampm}", resolveAmPmLabel(safeCalendar, safeLocale));
        result = result.replace("{period}", resolveTraditionalPeriod(safeCalendar));
        result = result.replace("{branch}", resolveEarthlyBranch(safeCalendar));
        result = result.replace("{branch_alias}", resolveEarthlyBranchAlias(safeCalendar));
        return result;
    }


    private static Calendar resolveClockCalendar(TextView clock) {
        if (clock == null) {
            return Calendar.getInstance();
        }
        Object calendar = ReflectUtils.getField(clock, "mCalendar");
        if (calendar instanceof Calendar) {
            return (Calendar) calendar;
        }
        return Calendar.getInstance();
    }

    private static Locale resolveClockLocale(TextView clock) {
        if (clock == null) {
            return Locale.getDefault();
        }
        Object locale = ReflectUtils.getField(clock, "mLocale");
        if (locale instanceof Locale) {
            return (Locale) locale;
        }
        Configuration configuration = clock.getContext() != null
                ? clock.getContext().getResources().getConfiguration()
                : null;
        if (configuration != null && configuration.locale != null) {
            return configuration.locale;
        }
        return Locale.getDefault();
    }

    private static int resolveHour12(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR);
        return hour == 0 ? 12 : hour;
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String resolveWeekdayLabel(Calendar calendar, Locale locale, boolean full) {
        DateFormatSymbols symbols = new DateFormatSymbols(locale != null ? locale : Locale.getDefault());
        String[] labels = full ? symbols.getWeekdays() : symbols.getShortWeekdays();
        int dayOfWeek = calendar == null ? Calendar.SUNDAY : calendar.get(Calendar.DAY_OF_WEEK);
        if (labels == null || dayOfWeek < 0 || dayOfWeek >= labels.length) {
            return "";
        }
        String label = labels[dayOfWeek];
        if (label == null) {
            return "";
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (full && !trimmed.startsWith("星期") && !trimmed.startsWith("周") && !trimmed.startsWith("週")
                && isChineseLocale(locale)) {
            return "星期" + resolveWeekdaySingleLabel(calendar, locale);
        }
        return trimmed;
    }

    private static String resolveWeekdaySingleLabel(Calendar calendar, Locale locale) {
        String shortLabel = trimWeekdayLabel(resolveWeekdayLabel(calendar, locale, false));
        if (!shortLabel.isEmpty()) {
            return shortLabel;
        }
        switch (calendar == null ? Calendar.SUNDAY : calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "一";
            case Calendar.TUESDAY:
                return "二";
            case Calendar.WEDNESDAY:
                return "三";
            case Calendar.THURSDAY:
                return "四";
            case Calendar.FRIDAY:
                return "五";
            case Calendar.SATURDAY:
                return "六";
            case Calendar.SUNDAY:
            default:
                return "日";
        }
    }

    private static boolean isChineseLocale(Locale locale) {
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
    }

    private static String trimWeekdayLabel(String weekday) {
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

    private static String resolveAmPmLabel(Calendar calendar, Locale locale) {
        int index = calendar == null ? 0 : calendar.get(Calendar.AM_PM);
        return index == Calendar.AM ? "AM" : "PM";
    }

    private static String resolveTraditionalPeriod(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR_OF_DAY);
        if (hour <= 4) {
            return "凌晨";
        }
        if (hour <= 7) {
            return "早晨";
        }
        if (hour <= 11) {
            return "上午";
        }
        if (hour == 12) {
            return "中午";
        }
        if (hour <= 17) {
            return "下午";
        }
        if (hour == 18) {
            return "傍晚";
        }
        return "晚上";
    }

    private static String resolveEarthlyBranch(Calendar calendar) {
        switch (resolveEarthlyBranchIndex(calendar)) {
            case 0:
                return "子";
            case 1:
                return "丑";
            case 2:
                return "寅";
            case 3:
                return "卯";
            case 4:
                return "辰";
            case 5:
                return "巳";
            case 6:
                return "午";
            case 7:
                return "未";
            case 8:
                return "申";
            case 9:
                return "酉";
            case 10:
                return "戌";
            default:
                return "亥";
        }
    }

    private static String resolveEarthlyBranchAlias(Calendar calendar) {
        switch (resolveEarthlyBranchIndex(calendar)) {
            case 0:
                return "夜半";
            case 1:
                return "鸡鸣";
            case 2:
                return "平旦";
            case 3:
                return "日出";
            case 4:
                return "食时";
            case 5:
                return "隅中";
            case 6:
                return "日中";
            case 7:
                return "日昳";
            case 8:
                return "哺时";
            case 9:
                return "日入";
            case 10:
                return "黄昏";
            default:
                return "人定";
        }
    }

    private static int resolveEarthlyBranchIndex(Calendar calendar) {
        int hour = calendar == null ? 0 : calendar.get(Calendar.HOUR_OF_DAY);
        if (hour == 23 || hour == 0) {
            return 0;
        }
        return Math.min(11, Math.max(0, (hour + 1) / 2));
    }

    private static void trackConnectionRateView(View view) {
        TRACKED_CONNECTION_RATE_VIEWS.put(view, Boolean.TRUE);
        rememberConnectionRateState(view);
    }

    private static void trackStatusBarIconView(View view) {
        if (view == null) {
            return;
        }
        TRACKED_STATUS_BAR_ICON_VIEWS.put(view, Boolean.TRUE);
        ensureConfigRefreshObserver(view.getContext());
    }

    private static void applyStatusBarScaleIfNeeded(View view) {
        if (view == null) {
            return;
        }
        if (isNotificationIconView(view)) {
            return;
        }
        if (isPrivacyChipView(view)) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!config.enabled) {
            return;
        }
        String idName = getSystemUiIdName(view);
        if ("mobile_signal".equals(idName) && view instanceof ImageView) {
            if (isSignalCodeDrawEnabled(config)) {
                resetStandaloneImageScale((ImageView) view);
                applySignalIconOverride((ImageView) view);
            } else {
                applyStandaloneStatusBarImageScale(view, config);
            }
            return;
        }
        String className = view.getClass().getName();
        if ("com.android.systemui.statusbar.StatusBarIconView".equals(className)) {
            applyStatusBarIconViewScale(view, config);
            return;
        }
        if ("com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView".equals(className)) {
            applyBindableStatusBarIconScale(view, config);
            return;
        }
        if (isStandaloneStatusBarImageView(view)) {
            applyStandaloneStatusBarImageScale(view, config);
            return;
        }
        if (isStatusBarContainerView(view)) {
            applyStatusBarContainerScale(view, config);
            return;
        }
    }

    private void hookClockAndCarrierTextSize(ClassLoader loader) {
        hookConstructors(loader, "com.android.systemui.statusbar.policy.Clock", view -> {
            if (view instanceof TextView) {
                trackClockAndCarrierTextView((TextView) view);
                scheduleClockAndCarrierTextRelayout((TextView) view);
                applyClockFontWeight((TextView) view);
                applyClockAndCarrierTextSize((TextView) view);
            }
        });
        hookConstructors(loader, "com.android.keyguard.CarrierText", view -> {
            if (view instanceof TextView) {
                trackClockAndCarrierTextView((TextView) view);
                scheduleClockAndCarrierTextRelayout((TextView) view);
                applyClockAndCarrierTextSize((TextView) view);
            }
        });
    }

    private static void onSignalImageResourceAssigned(ImageView view, int resId) {
        trackStatusBarIconView(view);
        String idName = getSystemUiIdName(view);
        if (!isMobileSignalRelatedId(idName)) {
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            if ("mobile_type".equals(idName)) {
                restoreMobileTypeView(view);
            }
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        if ("mobile_type".equals(idName)) {
            hideMobileTypeView(view);
            return;
        }
        if ("mobile_signal".equals(idName)) {
            applySignalIconOverride(view);
        }
        applyStatusBarScaleIfNeeded(view);
    }

    private void hookStatusBarIconConstructors(ClassLoader loader) {
        hookConstructors(loader, "com.android.systemui.statusbar.StatusBarIconView", view -> {
            trackStatusBarIconView(view);
            applyStatusBarScaleIfNeeded(view);
        });
        hookConstructors(loader, "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView", view -> {
            trackStatusBarIconView(view);
            applyStatusBarScaleIfNeeded(view);
        });
        hookConstructors(loader, "com.android.systemui.privacy.OngoingPrivacyChip", view -> {
            trackStatusBarIconView(view);
            applyStatusBarScaleIfNeeded(view);
        });
        hookConstructors(loader, "com.flyme.systemui.privacy.FlymeOngoingPrivacyChip", view -> {
            trackStatusBarIconView(view);
            applyStatusBarScaleIfNeeded(view);
        });
    }

    private static void onSignalImageDrawableAssigned(ImageView view, Drawable drawable) {
        trackStatusBarIconView(view);
        String idName = getSystemUiIdName(view);
        if (!isMobileSignalRelatedId(idName)) {
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            if ("mobile_type".equals(idName)) {
                restoreMobileTypeView(view);
            }
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        if ("mobile_type".equals(idName)) {
            hideMobileTypeView(view);
            return;
        }
        String drawableName = drawable == null ? "null" : drawable.getClass().getName();
        int level = drawable == null ? -1 : drawable.getLevel();
        if ("mobile_signal".equals(idName) && drawable != null) {
            SIGNAL_DRAWABLE_OWNERS.put(drawable, view);
        }
        if ("mobile_signal".equals(idName) && !(drawable instanceof SignalIconDrawable)) {
            applySignalIconOverride(view);
        }
        applyStatusBarScaleIfNeeded(view);
    }

    private static void onSignalViewLayoutChanged(View view) {
        trackStatusBarIconView(view);
        String idName = getSystemUiIdName(view);
        if (!isMobileSignalRelatedId(idName)) {
            if (isStatusBarIconCandidate(view)) {
                applyStatusBarScaleIfNeeded(view);
            }
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            if ("mobile_type".equals(idName)) {
                restoreMobileTypeView(view);
            }
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        if ("mobile_type".equals(idName)) {
            hideMobileTypeView(view);
            return;
        }
        if ("mobile_signal".equals(idName) && view instanceof ImageView) {
            applySignalIconOverride((ImageView) view);
        }
        applyStatusBarScaleIfNeeded(view);
    }

    private static void onSignalDrawableLevelChanged(Drawable drawable, int rawLevel) {
        if (drawable == null) {
            return;
        }
        View owner = SIGNAL_DRAWABLE_OWNERS.get(drawable);
        ModuleConfig config = owner == null ? ModuleConfig.load(ModuleConfig.getSystemUiContext())
                : ModuleConfig.load(owner.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            return;
        }
        LAST_SIGNAL_LEVEL = normalizeSignalLevel(rawLevel);
    }

    private static void syncSignalTintToCustomDrawable(ImageView view) {
        if (view == null || !"mobile_signal".equals(getSystemUiIdName(view))) {
            return;
        }
        Drawable drawable = view.getDrawable();
        if (!(drawable instanceof SignalIconDrawable)) {
            return;
        }
        drawable.setTintList(view.getImageTintList());
        drawable.setState(view.getDrawableState());
    }

    private static void syncSignalColorFilterToCustomDrawable(ImageView view, ColorFilter colorFilter) {
        if (view == null || !"mobile_signal".equals(getSystemUiIdName(view))) {
            return;
        }
        Drawable drawable = view.getDrawable();
        if (!(drawable instanceof SignalIconDrawable)) {
            return;
        }
        drawable.setColorFilter(colorFilter);
    }

    private static int resolveSignalBars(String idName, View view) {
        if ("mobile_signal".equals(idName)) {
            if (LAST_SIGNAL_LEVEL >= 0) {
                return LAST_SIGNAL_LEVEL;
            }
            Drawable drawable = view instanceof ImageView ? ((ImageView) view).getDrawable() : null;
            if (drawable != null) {
                return normalizeSignalLevel(drawable.getLevel());
            }
        }
        return LAST_CELLULAR_LEVEL >= 0 ? LAST_CELLULAR_LEVEL : -1;
    }

    private static int normalizeSignalLevel(int rawLevel) {
        if (rawLevel < 0) {
            return -1;
        }
        int level = rawLevel & 0xff;
        if (level > 4) {
            level = rawLevel % 5;
        }
        if (level < 0) {
            return -1;
        }
        return Math.min(level, 4);
    }

    private static int resolveActiveSubscriptionCount(Context context) {
        if (context == null) {
            return LAST_ACTIVE_SUBSCRIPTION_COUNT;
        }
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) {
                return LAST_ACTIVE_SUBSCRIPTION_COUNT;
            }
            int count = manager.getActiveSubscriptionInfoCount();
            LAST_ACTIVE_SUBSCRIPTION_COUNT = count;
            return count;
        } catch (Throwable ignored) {
            return LAST_ACTIVE_SUBSCRIPTION_COUNT;
        }
    }

    private static void applySignalIconOverride(ImageView view) {
        if (view == null) {
            return;
        }
        Context context = view.getContext();
        ModuleConfig config = ModuleConfig.load(context);
        if (!isSignalCodeDrawEnabled(config)) {
            return;
        }
        int simCount = resolveActiveSubscriptionCount(context);
        boolean mergedDual = simCount >= 2;
        View mobileGroup = findMobileSignalGroup(view);
        updateSignalSlotVisibility(mobileGroup, mergedDual);
        if (mergedDual && !isPrimarySignalView(view, mobileGroup)) {
            return;
        }
        alignSignalIconVertically(view);
        resizeSignalIconView(view);
        disableAncestorClipping(view, 6);
        int intrinsicHeight = resolveSignalIconIntrinsicHeight(view);
        int intrinsicWidth = SignalPreviewPainter.resolveIntrinsicWidth(intrinsicHeight);
        Drawable current = view.getDrawable();
        if (current instanceof SignalIconDrawable
                && ((SignalIconDrawable) current).matchesGeometry(mergedDual, intrinsicWidth, intrinsicHeight)) {
            view.invalidate();
            return;
        }
        SignalIconDrawable drawable = new SignalIconDrawable(mergedDual, intrinsicWidth, intrinsicHeight);
        drawable.setAlpha(view.getImageAlpha());
        drawable.setState(view.getDrawableState());
        drawable.setTintList(view.getImageTintList());
        if (current != null) {
            drawable.setColorFilter(current.getColorFilter());
        }
        view.setImageDrawable(drawable);
        SIGNAL_DRAWABLE_OWNERS.put(drawable, view);
    }

    private static void alignSignalIconVertically(ImageView view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layoutParams;
        int targetGravity = Gravity.START | Gravity.CENTER_VERTICAL;
        if (lp.gravity == targetGravity) {
            return;
        }
        lp.gravity = targetGravity;
        view.setLayoutParams(lp);
    }

    private static void hideMobileTypeView(View view) {
        if (view == null || view.getVisibility() == View.GONE) {
            return;
        }
        view.setVisibility(View.GONE);
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            ((View) parent).requestLayout();
        }
        view.requestLayout();
    }

    private static void restoreMobileTypeView(View view) {
        if (view == null || view.getVisibility() == View.VISIBLE) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            ((View) parent).requestLayout();
        }
        view.requestLayout();
    }

    private static void updateSignalSlotVisibility(View mobileGroup, boolean mergedDual) {
        ArrayList<View> groups = collectSiblingMobileSignalGroups(mobileGroup);
        if (groups.isEmpty()) {
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            View group = groups.get(i);
            boolean shouldShow = !mergedDual || i == 0;
            updateSignalSlotFootprint(group, shouldShow);
            int visibility = shouldShow ? View.VISIBLE : View.GONE;
            if (group.getVisibility() != visibility) {
                group.setVisibility(visibility);
            }
        }
    }

    private static boolean isPrimarySignalView(ImageView view, View mobileGroup) {
        ArrayList<View> groups = collectSiblingMobileSignalGroups(mobileGroup);
        if (groups.isEmpty()) {
            return true;
        }
        View primaryGroup = groups.get(0);
        View primarySignalView = findSystemUiChild(primaryGroup, "mobile_signal");
        return primarySignalView == view;
    }

    private static ArrayList<View> collectSiblingMobileSignalGroups(View mobileGroup) {
        ArrayList<View> groups = new ArrayList<>();
        if (mobileGroup == null) {
            return groups;
        }
        ViewGroup parent = asViewGroup(mobileGroup.getParent());
        if (parent == null) {
            groups.add(mobileGroup);
            return groups;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (isMobileSignalSlotView(child)) {
                groups.add(child);
            }
        }
        if (groups.isEmpty()) {
            groups.add(mobileGroup);
        }
        return groups;
    }

    private static View findMobileSignalGroup(View view) {
        View comboAncestor = findAncestorByIdName(view, "mobile_combo");
        if (comboAncestor != null) {
            return comboAncestor;
        }
        View current = view;
        while (current != null) {
            if (isMobileSignalSlotView(current)) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static boolean isMobileSignalSlotView(View view) {
        if (view == null) {
            return false;
        }
        String idName = getSystemUiIdName(view);
        if ("mobile_combo".equals(idName)) {
            return true;
        }
        return isMobileSignalGroupView(view);
    }

    private static boolean isMobileSignalGroupView(View view) {
        if (view == null) {
            return false;
        }
        String name = view.getClass().getName();
        return "com.flyme.systemui.statusbar.net.mobile.ui.view.FlymeModernStatusBarMobileView".equals(name)
                || "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView".equals(name)
                || "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView".equals(name);
    }

    private static ViewGroup asViewGroup(ViewParent parent) {
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }

    private static void updateSignalSlotFootprint(View group, boolean shouldShow) {
        if (group == null) {
            return;
        }
        ViewGroup.LayoutParams lp = group.getLayoutParams();
        if (lp == null) {
            return;
        }
        rememberOriginalSignalSlotLayout(group, lp);
        int[] originalSize = ORIGINAL_SIZES.get(group);
        int[] originalMargins = ORIGINAL_MARGINS.get(group);
        boolean changed = false;
        if (shouldShow) {
            if (originalSize != null) {
                if (lp.width != originalSize[0]) {
                    lp.width = originalSize[0];
                    changed = true;
                }
                if (lp.height != originalSize[1]) {
                    lp.height = originalSize[1];
                    changed = true;
                }
            }
            if (lp instanceof ViewGroup.MarginLayoutParams && originalMargins != null) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (mlp.leftMargin != originalMargins[0]
                        || mlp.topMargin != originalMargins[1]
                        || mlp.rightMargin != originalMargins[2]
                        || mlp.bottomMargin != originalMargins[3]) {
                    mlp.setMargins(originalMargins[0], originalMargins[1],
                            originalMargins[2], originalMargins[3]);
                    changed = true;
                }
            }
        } else {
            if (lp.width != 0) {
                lp.width = 0;
                changed = true;
            }
            if (lp.height != 0) {
                lp.height = 0;
                changed = true;
            }
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (mlp.leftMargin != 0 || mlp.topMargin != 0
                        || mlp.rightMargin != 0 || mlp.bottomMargin != 0) {
                    mlp.setMargins(0, 0, 0, 0);
                    changed = true;
                }
            }
        }
        if (changed) {
            group.setLayoutParams(lp);
            ViewParent parent = group.getParent();
            if (parent instanceof View) {
                ((View) parent).requestLayout();
            }
            group.requestLayout();
        }
    }

    private static void rememberOriginalSignalSlotLayout(View group, ViewGroup.LayoutParams lp) {
        if (group == null || lp == null) {
            return;
        }
        if (!ORIGINAL_SIZES.containsKey(group)) {
            ORIGINAL_SIZES.put(group, new int[]{lp.width, lp.height});
        }
        if (lp instanceof ViewGroup.MarginLayoutParams && !ORIGINAL_MARGINS.containsKey(group)) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            ORIGINAL_MARGINS.put(group, new int[]{
                    mlp.leftMargin,
                    mlp.topMargin,
                    mlp.rightMargin,
                    mlp.bottomMargin
            });
        }
    }

    private static void resizeSignalIconView(ImageView view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        int targetHeight = resolveTargetSignalIconBoxSize(view);
        int targetWidth = SignalPreviewPainter.resolveIntrinsicWidth(targetHeight);
        boolean changed = false;
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            changed = true;
        }
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            changed = true;
        }
        if (changed) {
            view.setLayoutParams(lp);
            view.requestLayout();
        }
    }

    private static void rememberOriginalLayout(View view, ViewGroup.LayoutParams lp) {
        if (view == null || lp == null || ORIGINAL_SIZES.containsKey(view)) {
            return;
        }
        ORIGINAL_SIZES.put(view, new int[]{lp.width, lp.height});
    }

    private static void rememberOriginalMargins(View view, ViewGroup.MarginLayoutParams lp) {
        if (view == null || lp == null || ORIGINAL_MARGINS.containsKey(view)) {
            return;
        }
        ORIGINAL_MARGINS.put(view, new int[]{lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin});
    }

    private static void rememberOriginalPadding(View view) {
        if (view == null || ORIGINAL_PADDINGS.containsKey(view)) {
            return;
        }
        ORIGINAL_PADDINGS.put(view, new int[]{
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom()
        });
    }

    private static void rememberOriginalTextSize(TextView view) {
        if (view == null || ORIGINAL_TEXT_SIZES.containsKey(view)) {
            return;
        }
        ORIGINAL_TEXT_SIZES.put(view, view.getTextSize());
    }

    private static void rememberOriginalIncludeFontPadding(TextView view) {
        if (view == null || ORIGINAL_INCLUDE_FONT_PADDING.containsKey(view)) {
            return;
        }
        ORIGINAL_INCLUDE_FONT_PADDING.put(view, view.getIncludeFontPadding());
    }

    private static void rememberOriginalTextVerticalAnchor(TextView view) {
        if (view == null) {
            return;
        }
        if (!ORIGINAL_TEXT_TRANSLATION_Y.containsKey(view)) {
            ORIGINAL_TEXT_TRANSLATION_Y.put(view, view.getTranslationY());
        }
    }

    private static void applyClockAndCarrierTextSize(TextView view) {
        if (view == null) {
            return;
        }
        if (!isClockOrLockscreenCarrierText(view)) {
            return;
        }
        rememberOriginalTextSize(view);
        rememberOriginalIncludeFontPadding(view);
        rememberOriginalTextVerticalAnchor(view);
        Float originalSize = ORIGINAL_TEXT_SIZES.get(view);
        if (originalSize == null || originalSize <= 0f) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        float scale = config.enabled ? resolveClockAndCarrierTextScale(config) : 1f;
        boolean changed = false;
        float targetSize = originalSize * scale;
        if (Math.abs(view.getTextSize() - targetSize) > 0.5f) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, targetSize);
            changed = true;
        }
        if (resetClockAndCarrierTextViewScale(view)) {
            changed = true;
        }
        if (restoreOriginalTextLayoutWidth(view)) {
            changed = true;
        }
        if (restoreOriginalClockAndCarrierFontPadding(view)) {
            changed = true;
        }
        if (applyClockAndCarrierTextMetrics(view)) {
            changed = true;
        }
        if (applyClockAndCarrierVerticalAnchor(view)) {
            changed = true;
        }
        if (changed) {
            view.requestLayout();
        }
        view.invalidate();
    }

    private static boolean resetClockAndCarrierTextViewScale(TextView view) {
        if (view == null) {
            return false;
        }
        disableAncestorClipping(view, 4);
        boolean changed = false;
        if (Math.abs(view.getScaleX() - 1f) > 0.001f) {
            view.setScaleX(1f);
            changed = true;
        }
        if (Math.abs(view.getScaleY() - 1f) > 0.001f) {
            view.setScaleY(1f);
            changed = true;
        }
        return changed;
    }

    private static boolean restoreOriginalClockAndCarrierFontPadding(TextView view) {
        if (view == null) {
            return false;
        }
        Boolean original = ORIGINAL_INCLUDE_FONT_PADDING.get(view);
        boolean targetValue = original != null && original;
        if (view.getIncludeFontPadding() == targetValue) {
            return false;
        }
        view.setIncludeFontPadding(targetValue);
        return true;
    }

    private static void scheduleClockAndCarrierTextRelayout(TextView view) {
        if (view == null) {
            return;
        }
        view.post(() -> {
            applyClockFontWeight(view);
            applyClockAndCarrierTextSize(view);
        });
        view.postDelayed(() -> {
            applyClockFontWeight(view);
            applyClockAndCarrierTextSize(view);
        }, 32L);
    }

    private static void trackClockAndCarrierTextView(TextView view) {
        if (view == null || TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS.containsKey(view)) {
            return;
        }
        TRACKED_CLOCK_AND_CARRIER_TEXT_VIEWS.put(view, Boolean.TRUE);
        ensureConfigRefreshObserver(view.getContext());
        updateClockSecondRefreshTracking(view);
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!(v instanceof TextView)) {
                return;
            }
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                return;
            }
            v.post(() -> applyClockAndCarrierTextSize((TextView) v));
        });
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (v instanceof TextView) {
                    updateClockSecondRefreshTracking((TextView) v);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (v instanceof TextView) {
                    CLOCK_SECOND_REFRESH_VIEWS.remove((TextView) v);
                    scheduleNextClockSecondRefresh();
                }
            }
        });
    }

    private static boolean restoreOriginalTextLayoutWidth(TextView view) {
        if (view == null) {
            return false;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int[] originalSize = ORIGINAL_SIZES.get(view);
        if (lp == null || originalSize == null) {
            return false;
        }
        int originalWidth = originalSize[0];
        if (lp.width != originalWidth) {
            lp.width = originalWidth;
            view.setLayoutParams(lp);
            return true;
        }
        return false;
    }

    private static boolean applyClockAndCarrierTextMetrics(TextView view) {
        if (view == null) {
            return false;
        }
        boolean changed = false;
        Paint.FontMetricsInt fontMetrics = view.getPaint().getFontMetricsInt();
        int targetTextBoundsHeight = fontMetrics == null
                ? Math.max(1, Math.round(view.getTextSize()))
                : Math.max(1, fontMetrics.bottom - fontMetrics.top);
        int targetLineHeight = fontMetrics == null
                ? targetTextBoundsHeight
                : Math.max(targetTextBoundsHeight,
                fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading);
        if (applyTextViewLineHeight(view, targetLineHeight)) {
            changed = true;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int[] originalSize = ORIGINAL_SIZES.get(view);
        if (lp == null || originalSize == null) {
            return changed;
        }
        rememberOriginalPadding(view);
        int[] originalPadding = ORIGINAL_PADDINGS.get(view);
        int verticalPadding = 0;
        if (originalPadding != null) {
            verticalPadding = originalPadding[1] + originalPadding[3];
        }
        int originalHeight = originalSize[1];
        if (originalHeight > 0) {
            int targetHeight = Math.max(originalHeight, targetTextBoundsHeight + verticalPadding);
            if (lp.height != targetHeight) {
                lp.height = targetHeight;
                view.setLayoutParams(lp);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyClockAndCarrierVerticalAnchor(TextView view) {
        if (view == null) {
            return false;
        }
        Float originalTranslationY = ORIGINAL_TEXT_TRANSLATION_Y.get(view);
        if (originalTranslationY == null) {
            return false;
        }
        float targetTranslationY = originalTranslationY;
        if (Math.abs(view.getTranslationY() - targetTranslationY) <= 0.5f) {
            return false;
        }
        view.setTranslationY(targetTranslationY);
        return true;
    }

    private static boolean applyTextViewLineHeight(TextView view, int targetLineHeight) {
        if (view == null) {
            return false;
        }
        int normalizedLineHeight = Math.max(1, targetLineHeight);
        int currentLineHeight = view.getLineHeight();
        if (Math.abs(currentLineHeight - normalizedLineHeight) <= 1) {
            return false;
        }
        Object result = ReflectUtils.invokeMethod(
                view,
                "setLineHeight",
                new Class[]{int.class, float.class},
                android.util.TypedValue.COMPLEX_UNIT_PX,
                (float) normalizedLineHeight);
        if (result != null || Math.abs(view.getLineHeight() - normalizedLineHeight) <= 1) {
            return true;
        }
        ReflectUtils.invokeMethod(view, "setLineHeight", new Class[]{int.class}, normalizedLineHeight);
        return Math.abs(view.getLineHeight() - normalizedLineHeight) <= 1;
    }

    private static float resolveTextPivotX(TextView view, int width) {
        if (view == null || width <= 0) {
            return 0f;
        }
        int gravity = Gravity.getAbsoluteGravity(view.getGravity(), view.getLayoutDirection())
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (gravity == Gravity.RIGHT) {
            return width;
        }
        if (gravity == Gravity.CENTER_HORIZONTAL) {
            return width / 2f;
        }
        return 0f;
    }

    private static void applyClockFontWeight(TextView view) {
        if (view == null || !isStatusBarClockView(view)) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        int fontWeight = config.enabled ? resolveClockFontWeight(config) : 400;
        Typeface baseTypeface = view.getTypeface();
        boolean italic = baseTypeface != null && baseTypeface.isItalic();
        Typeface newTypeface;
        try {
            newTypeface = Typeface.create(baseTypeface, fontWeight, italic);
        } catch (Throwable ignored) {
            newTypeface = Typeface.defaultFromStyle(fontWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (newTypeface != null) {
            view.setTypeface(newTypeface);
        }
        view.getPaint().setFakeBoldText(fontWeight >= 600);
        view.requestLayout();
        view.invalidate();
    }

    private static boolean isStatusBarClockView(TextView view) {
        if (view == null) {
            return false;
        }
        if (!"com.android.systemui.statusbar.policy.Clock".equals(view.getClass().getName())) {
            return false;
        }
        String idName = getSystemUiIdName(view);
        return "clock".equals(idName) || "keyguard_clock".equals(idName) || "mz_clock".equals(idName);
    }

    private static boolean isPrimaryStatusBarClockView(TextView view) {
        return view != null
                && "com.android.systemui.statusbar.policy.Clock".equals(view.getClass().getName())
                && "clock".equals(getSystemUiIdName(view));
    }

    private static boolean isClockOrLockscreenCarrierText(TextView view) {
        if (view == null) {
            return false;
        }
        String className = view.getClass().getName();
        String idName = getSystemUiIdName(view);
        if ("com.android.systemui.statusbar.policy.Clock".equals(className)) {
            return "clock".equals(idName) || "keyguard_clock".equals(idName) || "mz_clock".equals(idName);
        }
        if ("com.android.keyguard.CarrierText".equals(className)) {
            return "keyguard_carrier_text".equals(idName) || "carrier_text".equals(idName);
        }
        return false;
    }

    private static boolean isPrivacyChipView(View view) {
        if (view == null) {
            return false;
        }
        String className = view.getClass().getName();
        return "com.android.systemui.privacy.OngoingPrivacyChip".equals(className)
                || "com.flyme.systemui.privacy.FlymeOngoingPrivacyChip".equals(className);
    }

    private static boolean isNotificationIconView(View view) {
        if (view == null) {
            return false;
        }
        return findAncestorByIdName(view, "notificationIcons") != null;
    }

    private static boolean isStatusBarIconCandidate(View view) {
        if (view == null) {
            return false;
        }
        String className = view.getClass().getName();
        if ("com.android.systemui.statusbar.StatusBarIconView".equals(className)
                || "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView".equals(className)
                || isPrivacyChipView(view)) {
            return true;
        }
        String idName = getSystemUiIdName(view);
        return "wifi_signal".equals(idName)
                || "wifi_in".equals(idName)
                || "wifi_out".equals(idName)
                || "inout_container".equals(idName)
                || "mobile_signal".equals(idName)
                || "mobile_type".equals(idName)
                || "mobile_in".equals(idName)
                || "mobile_out".equals(idName)
                || "mobile_inout".equals(idName)
                || "mobile_type_container".equals(idName)
                || "mobile_roaming".equals(idName)
                || "mobile_roaming_space".equals(idName)
                || "mobile_group".equals(idName)
                || "wifi_group".equals(idName)
                || "battery".equals(idName)
                || "notificationIcons".equals(idName)
                || "statusIcons".equals(idName)
                || "privacy_chip".equals(idName)
                || "icons_container".equals(idName);
    }

    private static boolean isStandaloneStatusBarImageView(View view) {
        if (!(view instanceof ImageView)) {
            return false;
        }
        String idName = getSystemUiIdName(view);
        return "wifi_signal".equals(idName)
                || "wifi_in".equals(idName)
                || "wifi_out".equals(idName)
                || "mobile_type".equals(idName)
                || "mobile_in".equals(idName)
                || "mobile_out".equals(idName)
                || "mobile_inout".equals(idName)
                || "mobile_roaming".equals(idName);
    }

    private static boolean isStatusBarContainerView(View view) {
        if (view == null) {
            return false;
        }
        String idName = getSystemUiIdName(view);
        return "wifi_group".equals(idName)
                || "inout_container".equals(idName)
                || "mobile_group".equals(idName)
                || "mobile_type_container".equals(idName)
                || "mobile_roaming_space".equals(idName);
    }

    private static boolean isMobileSignalRelatedId(String idName) {
        return "mobile_signal".equals(idName)
                || "mobile_type".equals(idName)
                || "mobile_in".equals(idName)
                || "mobile_out".equals(idName)
                || "mobile_inout".equals(idName)
                || "mobile_type_container".equals(idName)
                || "mobile_roaming".equals(idName)
                || "mobile_roaming_space".equals(idName)
                || "mobile_group".equals(idName)
                || "inout_container".equals(idName);
    }

    private static int scaleSize(int original, float scale) {
        if (original == 0) {
            return 0;
        }
        if (original < 0) {
            return original;
        }
        return Math.max(1, Math.round(original * scale));
    }

    private static int scaleInsetSize(int original, float scale) {
        if (original == 0) {
            return 0;
        }
        return Math.round(original * scale);
    }

    private static int scaleLayoutSize(int original, float scale) {
        if (original == ViewGroup.LayoutParams.WRAP_CONTENT || original == ViewGroup.LayoutParams.MATCH_PARENT) {
            return Integer.MIN_VALUE;
        }
        return scaleSize(original, scale);
    }

    private static int resolveTargetSignalIconBoxSize(ImageView view) {
        ModuleConfig config = ModuleConfig.load(view.getContext());
        return scaleSize(dp(view, 24), resolveStatusBarIconScale(config));
    }

    private static int resolveSignalIconIntrinsicHeight(ImageView view) {
        if (view == null) {
            return 1;
        }
        if (view.getHeight() > 0) {
            return SignalPreviewPainter.resolveIntrinsicHeight(view.getHeight());
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height > 0) {
            return SignalPreviewPainter.resolveIntrinsicHeight(lp.height);
        }
        int resId = view.getResources().getIdentifier("status_bar_mobile_signal_size",
                "dimen", view.getContext().getPackageName());
        if (resId != 0) {
            try {
                return SignalPreviewPainter.resolveIntrinsicHeight(
                        view.getResources().getDimensionPixelSize(resId));
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return SignalPreviewPainter.resolveIntrinsicHeight(dp(view, 15));
    }

    private static void applyStatusBarIconViewScale(View view, ModuleConfig config) {
        if (!(view instanceof ImageView)) {
            return;
        }
        ImageView imageView = (ImageView) view;
        float scale = resolveStatusBarIconScale(config);
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp != null) {
            rememberOriginalLayout(imageView, lp);
            int[] original = ORIGINAL_SIZES.get(imageView);
            if (original != null) {
                boolean changed = false;
                int width = scaleLayoutSize(original[0], scale);
                int height = scaleLayoutSize(original[1], scale);
                if (width != Integer.MIN_VALUE && lp.width != width) {
                    lp.width = width;
                    changed = true;
                }
                if (height != Integer.MIN_VALUE && lp.height != height) {
                    lp.height = height;
                    changed = true;
                }
                if (changed) {
                    imageView.setLayoutParams(lp);
                }
            }
        }
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        imageView.requestLayout();
        imageView.invalidate();
    }

    private static void applyBindableStatusBarIconScale(View view, ModuleConfig config) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        applyScaleToLayoutParams(group, resolveStatusBarIconScale(config));
        View iconView = findSystemUiChild(group, "icon_view");
        if (iconView != null) {
            applyScaleToLayoutParams(iconView, resolveStatusBarIconScale(config));
            if (iconView instanceof ImageView) {
                ((ImageView) iconView).setScaleType(ImageView.ScaleType.FIT_CENTER);
                ((ImageView) iconView).setAdjustViewBounds(true);
            }
        }
    }

    private static void applyStandaloneStatusBarImageScale(View view, ModuleConfig config) {
        if (!(view instanceof ImageView)) {
            return;
        }
        ImageView imageView = (ImageView) view;
        float scale = resolveStatusBarIconScale(config);
        String idName = getSystemUiIdName(imageView);
        if ("wifi_signal".equals(idName)) {
            applyScaleToLayoutParams(imageView, 1f);
            applySignalWrapperScaleIfNeeded(imageView, scale);
            setImageViewRuntimeScale(imageView, scale);
        } else if ("mobile_signal".equals(idName)) {
            applyMeasuredMobileSignalScale(imageView, scale);
            resetStandaloneImageScale(imageView);
        } else {
            applyScaleToLayoutParams(imageView, scale);
            resetStandaloneImageScale(imageView);
        }
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        imageView.requestLayout();
        imageView.invalidate();
    }

    private static void applySignalWrapperScaleIfNeeded(ImageView imageView, float scale) {
        if (imageView == null) {
            return;
        }
        View wrapper = resolveSignalWrapperView(imageView);
        if (wrapper == null) {
            return;
        }
        applyRuntimeSizedViewScale(wrapper, scale);
    }

    private static View resolveSignalWrapperView(ImageView imageView) {
        if (imageView == null) {
            return null;
        }
        String idName = getSystemUiIdName(imageView);
        ViewParent parent = imageView.getParent();
        if (!(parent instanceof View)) {
            return null;
        }
        View wrapper = (View) parent;
        if ("wifi_signal".equals(idName)) {
            return "wifi_combo".equals(getSystemUiIdName(wrapper)) ? wrapper : null;
        }
        if ("mobile_signal".equals(idName)) {
            return wrapper;
        }
        return null;
    }

    private static void applyMeasuredMobileSignalScale(ImageView imageView, float scale) {
        if (imageView == null) {
            return;
        }
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp == null) {
            return;
        }
        int baseHeight = resolveMobileSignalBaseHeight(imageView);
        if (baseHeight <= 0) {
            return;
        }
        float aspectRatio = resolveMobileSignalAspectRatio(imageView);
        int targetHeight = scaleSize(baseHeight, scale);
        int targetWidth = Math.max(1, Math.round(targetHeight * aspectRatio));
        boolean changed = false;
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            changed = true;
        }
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            changed = true;
        }
        if (changed) {
            imageView.setLayoutParams(lp);
        }
        View wrapper = resolveSignalWrapperView(imageView);
        if (wrapper != null) {
            applyMeasuredMobileSignalWrapperScale(wrapper, targetWidth, targetHeight);
        }
        imageView.requestLayout();
        imageView.invalidate();
    }

    private static void applyMeasuredMobileSignalWrapperScale(View wrapper, int targetWidth, int targetHeight) {
        if (wrapper == null) {
            return;
        }
        ViewGroup.LayoutParams lp = wrapper.getLayoutParams();
        if (lp == null) {
            return;
        }
        boolean changed = false;
        if (lp.width > 0 && lp.width != targetWidth) {
            lp.width = targetWidth;
            changed = true;
        }
        if (lp.height > 0 && lp.height != targetHeight) {
            lp.height = targetHeight;
            changed = true;
        }
        if (changed) {
            wrapper.setLayoutParams(lp);
        }
        wrapper.requestLayout();
        wrapper.invalidate();
    }

    private static int resolveMobileSignalBaseHeight(ImageView imageView) {
        if (imageView == null) {
            return 0;
        }
        int size = getSystemUiDimen(imageView.getContext(), "status_bar_bindable_icon_size");
        if (size > 0) {
            return size;
        }
        size = getSystemUiDimen(imageView.getContext(), "status_bar_icon_size_sp");
        if (size > 0) {
            return size;
        }
        size = getSystemUiDimen(imageView.getContext(), "status_bar_mobile_signal_size");
        if (size > 0) {
            return size;
        }
        int[] currentSize = resolveCurrentViewSize(imageView);
        if (currentSize != null && currentSize[1] > 0) {
            return currentSize[1];
        }
        return dp(imageView, 20);
    }

    private static float resolveMobileSignalAspectRatio(ImageView imageView) {
        if (imageView == null) {
            return 1f;
        }
        int[] currentSize = resolveCurrentViewSize(imageView);
        if (currentSize != null && currentSize[0] > 0 && currentSize[1] > 0) {
            return currentSize[0] / (float) currentSize[1];
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable != null && drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
            return drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
        }
        return 1f;
    }

    private static void applyRuntimeSizedViewScale(View view, float scale) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        int[] runtimeSize = rememberOriginalRuntimeSize(view);
        if (runtimeSize == null || runtimeSize[0] <= 0 || runtimeSize[1] <= 0) {
            return;
        }
        int targetWidth = scaleSize(runtimeSize[0], scale);
        int targetHeight = scaleSize(runtimeSize[1], scale);
        boolean changed = false;
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            changed = true;
        }
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            changed = true;
        }
        if (changed) {
            view.setLayoutParams(lp);
        }
        view.requestLayout();
        view.invalidate();
    }

    private static int[] resolveCurrentViewSize(View view) {
        if (view == null) {
            return null;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0) {
            width = view.getMeasuredWidth();
        }
        if (height <= 0) {
            height = view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (width <= 0 && lp != null && lp.width > 0) {
            width = lp.width;
        }
        if (height <= 0 && lp != null && lp.height > 0) {
            height = lp.height;
        }
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[]{width, height};
    }

    private static int[] rememberOriginalRuntimeSize(View view) {
        if (view == null) {
            return null;
        }
        int[] cached = ORIGINAL_RUNTIME_SIZES.get(view);
        if (cached != null && cached[0] > 0 && cached[1] > 0) {
            return cached;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0) {
            width = view.getMeasuredWidth();
        }
        if (height <= 0) {
            height = view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (width <= 0 && lp != null && lp.width > 0) {
            width = lp.width;
        }
        if (height <= 0 && lp != null && lp.height > 0) {
            height = lp.height;
        }
        if (width <= 0 || height <= 0) {
            return null;
        }
        int[] recorded = new int[]{width, height};
        ORIGINAL_RUNTIME_SIZES.put(view, recorded);
        return recorded;
    }

    private static void setImageViewRuntimeScale(ImageView imageView, float scale) {
        if (imageView == null) {
            return;
        }
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
    }

    private static void resetStandaloneImageScale(ImageView imageView) {
        setImageViewRuntimeScale(imageView, 1f);
    }

    private static void applyStatusBarContainerScale(View view, ModuleConfig config) {
        float scale = resolveStatusBarIconScale(config);
        applyScaleToLayoutParams(view, scale);
        if (view instanceof ViewGroup) {
            applyScaleToChildren(view, scale);
        }
    }

    private static void applyPrivacyChipScale(View view, ModuleConfig config) {
        float scale = resolveStatusBarIconScale(config);
        View iconsContainer = findSystemUiChild(view, "icons_container");
        if (iconsContainer != null) {
            applyScaleToLayoutParams(iconsContainer, scale);
            applyScaleToChildren(iconsContainer, scale);
        }
        View textView = findSystemUiChild(view, "text");
        if (textView instanceof TextView) {
            TextView text = (TextView) textView;
            rememberOriginalTextSize(text);
            Float originalSize = ORIGINAL_TEXT_SIZES.get(text);
            if (originalSize != null) {
                text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, originalSize * scale);
            }
        }
        view.requestLayout();
        view.invalidate();
    }

    private static void applyScaleToChildren(View view, float scale) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            applyScaleToLayoutParams(child, scale);
            if (child instanceof ImageView) {
                ((ImageView) child).setScaleType(ImageView.ScaleType.FIT_CENTER);
                ((ImageView) child).setAdjustViewBounds(true);
            }
            applyScaleToChildren(child, scale);
        }
    }

    private static void applyScaleToLayoutParams(View view, float scale) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        rememberOriginalLayout(view, lp);
        int[] original = ORIGINAL_SIZES.get(view);
        boolean changed = false;
        if (original != null) {
            int width = scaleLayoutSize(original[0], scale);
            int height = scaleLayoutSize(original[1], scale);
            if (width != Integer.MIN_VALUE && lp.width != width) {
                lp.width = width;
                changed = true;
            }
            if (height != Integer.MIN_VALUE && lp.height != height) {
                lp.height = height;
                changed = true;
            }
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            rememberOriginalMargins(view, (ViewGroup.MarginLayoutParams) lp);
            int[] margins = ORIGINAL_MARGINS.get(view);
            if (margins != null) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int left = scaleInsetSize(margins[0], scale);
                int top = scaleInsetSize(margins[1], scale);
                int right = scaleInsetSize(margins[2], scale);
                int bottom = scaleInsetSize(margins[3], scale);
                if (mlp.leftMargin != left || mlp.topMargin != top
                        || mlp.rightMargin != right || mlp.bottomMargin != bottom) {
                    mlp.setMargins(left, top, right, bottom);
                    changed = true;
                }
            }
        }
        rememberOriginalPadding(view);
        int[] paddings = ORIGINAL_PADDINGS.get(view);
        if (paddings != null) {
            int left = scaleInsetSize(paddings[0], scale);
            int top = scaleInsetSize(paddings[1], scale);
            int right = scaleInsetSize(paddings[2], scale);
            int bottom = scaleInsetSize(paddings[3], scale);
            if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                    || view.getPaddingRight() != right || view.getPaddingBottom() != bottom) {
                view.setPadding(left, top, right, bottom);
                changed = true;
            }
        }
        if (changed) {
            view.setLayoutParams(lp);
        }
        view.requestLayout();
        view.invalidate();
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
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
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

    private static void refreshTrackedRuntimeViews() {
        refreshTrackedRuntimeViews(false);
    }

    private static void refreshTrackedRuntimeViews(boolean forceSignalRequery) {
        refreshTrackedConnectionRateViews();
        refreshTrackedBatteryViews();
        refreshTrackedStatusBarIconViews();
        refreshClockAndCarrierTextViews();
        refreshTrackedInputMethodViews();
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
                if (isBatteryCodeDrawEnabled(config)) {
                    resizeIosBatteryView(batteryView, config, ReflectUtils.getBooleanField(batteryView, "mCharging", false));
                }
                ReflectUtils.invokeMethod(batteryView, "apply", new Class[]{boolean.class}, true);
                batteryView.requestLayout();
                batteryView.invalidate();
            }
        });
    }

    private static void refreshTrackedStatusBarIconViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
            for (View view : views) {
                if (view == null) {
                    continue;
                }
                ModuleConfig config = ModuleConfig.load(view.getContext());
                if (!config.enabled) {
                    continue;
                }
                applyStatusBarScaleIfNeeded(view);
            }
        });
    }

    private static void refreshClockAndCarrierTextViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<TextView> textViews = new ArrayList<>(ORIGINAL_TEXT_SIZES.keySet());
            for (TextView textView : textViews) {
                if (textView == null) {
                    continue;
                }
                updateClockSecondRefreshTracking(textView);
                refreshClockTextIfNeeded(textView);
                applyClockFontWeight(textView);
                applyClockAndCarrierTextSize(textView);
            }
        });
    }

    private static void refreshClockTextIfNeeded(TextView textView) {
        if (!isPrimaryStatusBarClockView(textView)) {
            return;
        }
        ReflectUtils.invokeMethod(textView, "updateClock", new Class<?>[0]);
    }

    private static void updateClockSecondRefreshTracking(TextView view) {
        if (!isPrimaryStatusBarClockView(view)) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (shouldUseCustomClockSecondRefresh(config)) {
            CLOCK_SECOND_REFRESH_VIEWS.put(view, Boolean.TRUE);
        } else {
            CLOCK_SECOND_REFRESH_VIEWS.remove(view);
        }
        scheduleNextClockSecondRefresh();
    }

    private static boolean shouldUseCustomClockSecondRefresh(ModuleConfig config) {
        if (config == null || !config.enabled) {
            return false;
        }
        String format = config.clockCustomFormat;
        return format != null && format.contains("{ss}");
    }

    private static void refreshClockViewsForSecondTick() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(CLOCK_SECOND_REFRESH_RUNNABLE);
        ArrayList<TextView> clocks = new ArrayList<>(CLOCK_SECOND_REFRESH_VIEWS.keySet());
        boolean hasActiveClock = false;
        for (TextView clock : clocks) {
            if (clock == null || !clock.isAttachedToWindow()) {
                CLOCK_SECOND_REFRESH_VIEWS.remove(clock);
                continue;
            }
            ModuleConfig config = ModuleConfig.load(clock.getContext());
            if (!shouldUseCustomClockSecondRefresh(config)) {
                CLOCK_SECOND_REFRESH_VIEWS.remove(clock);
                continue;
            }
            hasActiveClock = true;
            refreshClockTextIfNeeded(clock);
            applyClockAndCarrierTextSize(clock);
        }
        if (hasActiveClock) {
            scheduleNextClockSecondRefresh();
        }
    }

    private static void scheduleNextClockSecondRefresh() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(CLOCK_SECOND_REFRESH_RUNNABLE);
        if (CLOCK_SECOND_REFRESH_VIEWS.isEmpty()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long next = ((now / 1000L) + 1L) * 1000L;
        handler.postAtTime(CLOCK_SECOND_REFRESH_RUNNABLE, next);
    }

    private static void refreshTrackedInputMethodViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<Object> services = new ArrayList<>(TRACKED_INPUT_METHOD_VIEWS.keySet());
            for (Object inputMethodService : services) {
                if (inputMethodService == null) {
                    continue;
                }
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(inputMethodService);
                if (inputView == null) {
                    continue;
                }
                refreshImeToolbarNow(inputMethodService, inputView);
            }
        });
    }

    private static void refreshImeToolbarNow(Object inputMethodService, View inputView) {
        if (inputMethodService == null || inputView == null) {
            return;
        }
        detachImeToolbarIfPresent(inputMethodService);
        attachImeToolbarIfNeeded(inputMethodService, inputView);
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
