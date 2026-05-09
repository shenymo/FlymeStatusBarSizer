package com.example.flymestatusbarsizer;

import com.example.flymestatusbarsizer.feature.clock.ClockHooks;
import com.example.flymestatusbarsizer.feature.ime.ImeHooks;
import com.example.flymestatusbarsizer.feature.mback.MBackHooks;
import com.example.flymestatusbarsizer.feature.notification.NotificationHooks;

import android.app.Notification;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.util.StateSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

public class FlymeStatusBarSizer extends XposedModule {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String PACKAGE_ANDROID = "android";
    private static final String FLYME_STATUS_BAR_ICON_UTILS =
            "com.flyme.systemui.statusbar.policy.FlymeStatusBarIconUtils";
    private static volatile FlymeStatusBarSizer MODULE;
    private static volatile Method flymeGetApplicationIconMethod;
    private static volatile Method flymeClearApplicationIconCacheMethod;
    private static volatile int LAST_NOTIFICATION_APP_ICON_VIEW_REFRESH_NIGHT = -1;

    private static final WeakHashMap<View, int[]> ORIGINAL_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_MARGINS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_RUNTIME_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, String> VIEW_ID_NAME_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<View, ConnectionRateViewState> CONNECTION_RATE_VIEW_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, BatteryViewState> BATTERY_VIEW_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_BATTERY_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_STATUS_BAR_ICON_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, View> SIGNAL_DRAWABLE_OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<View, SignalViewState> SIGNAL_VIEW_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<View>> SIGNAL_TINT_SOURCE_CACHE =
            new WeakHashMap<>();
    private static final WeakHashMap<TelephonyManager, Integer> TELEPHONY_MANAGER_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<SignalStrength, Integer> SIGNAL_STRENGTH_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<ServiceState, Integer> SERVICE_STATE_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<TelephonyDisplayInfo, TelephonyDisplayInfoState> TELEPHONY_DISPLAY_INFO_STATES =
            new WeakHashMap<>();
    private static final HashMap<Integer, MobileTypeSubState> MOBILE_TYPE_SUB_STATES = new HashMap<>();
    private static final HashMap<Integer, SignalLevelSubState> SIGNAL_LEVEL_SUB_STATES = new HashMap<>();
    private static final HashMap<Integer, Integer> SIGNAL_SUB_SLOT_INDEX_CACHE = new HashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_RESTORE_GUARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_APPLY_GUARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SIGNAL_DRAWABLE_APPLY_GUARDS = new WeakHashMap<>();
    private static final Object CONFIG_REFRESH_LOCK = new Object();
    private static final long[] INITIAL_RUNTIME_REFRESH_DELAYS_MS = {1000L, 3000L};
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static volatile boolean DEFAULT_NETWORK_CALLBACK_REGISTERED;
    private static Handler MAIN_HANDLER;
    private static volatile int LAST_UI_MODE_NIGHT = -1;
    private static final Runnable SIGNAL_ICON_REFRESH_RUNNABLE = FlymeStatusBarSizer::refreshTrackedSignalIconViewsNow;
    private static final Runnable PRIMARY_SIGNAL_ICON_REFRESH_RUNNABLE =
            FlymeStatusBarSizer::refreshTrackedPrimarySignalIconViewsNow;
    private static final ConnectivityManager.NetworkCallback DEFAULT_NETWORK_CALLBACK =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    scheduleTrackedSignalIconRefresh();
                }

                @Override
                public void onLost(Network network) {
                    scheduleTrackedSignalIconRefresh();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    scheduleTrackedSignalIconRefresh();
                }
            };
    private static final HashMap<String, Integer> SYSTEM_UI_ID_CACHE = new HashMap<>();
    private static final HashMap<String, Boolean> NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE =
            new HashMap<>();
    private static final String EXTRA_NOTIFICATION_APP_ICON_REPLACED =
            "flyme_status_bar_sizer_notification_app_icon_replaced";
    private static final int DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP = 20;
    private static final int DEFAULT_NOTIFICATION_APP_ICON_INSET_DP = 1;
    private static final String SIGNAL_LEVEL_LOG_MARKER = "[FSBS_SIGNAL_LEVEL]";
    private static final boolean SIGNAL_LEVEL_DEBUG_LOG_FORCE_ENABLED = false;
    private static final int SIGNAL_IMAGE_ASSIGNMENT_RESOURCE = 1;
    private static final int SIGNAL_IMAGE_ASSIGNMENT_ICON = 2;
    private static final int SIGNAL_IMAGE_ASSIGNMENT_DRAWABLE = 3;
    private static final int TELEPHONY_DEBUG_SUB_ID_CARD1 = 910001;
    private static final int TELEPHONY_DEBUG_SUB_ID_CARD2 = 910002;
    private static final ThreadLocal<Integer> INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static volatile int LAST_SIGNAL_LEVEL = -1;
    private static volatile int LAST_SIGNAL_SUB_ID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static volatile int LAST_CELLULAR_LEVEL = -1;
    private static volatile int LAST_ACTIVE_SUBSCRIPTION_COUNT = -1;
    private static volatile int LAST_SERVICE_STATE_SUB_ID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static volatile int LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID =
            SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static volatile int LAST_MOBILE_TYPE_RESOURCE_ID = 0;
    private static volatile int LAST_MOBILE_TYPE_ICON_RESOURCE_ID = 0;
    private static volatile int LAST_MOBILE_TYPE_VIEW_VISIBILITY = View.VISIBLE;
    private static volatile int LAST_MOBILE_TYPE_RAW_NETWORK_TYPE = Integer.MIN_VALUE;
    private static volatile int LAST_MOBILE_TYPE_NETWORK_TYPE = Integer.MIN_VALUE;
    private static volatile int LAST_MOBILE_TYPE_RAW_OVERRIDE_NETWORK_TYPE = Integer.MIN_VALUE;
    private static volatile int LAST_MOBILE_TYPE_OVERRIDE_NETWORK_TYPE = Integer.MIN_VALUE;
    private static volatile int LAST_MOBILE_TYPE_RAW_NR_STATE = Integer.MIN_VALUE;
    private static volatile int LAST_MOBILE_TYPE_NR_STATE = Integer.MIN_VALUE;
    private static volatile String LAST_MOBILE_TYPE_DEBUG_MODE = "";
    private static volatile String LAST_MOBILE_TYPE_SPOOF_PROFILE = "";
    private static volatile String LAST_MOBILE_TYPE_RAW_FLYME_ICON_GROUP = "";
    private static volatile String LAST_MOBILE_TYPE_FLYME_ICON_GROUP = "";
    private static volatile String LAST_MOBILE_TYPE_DEFAULT_ICON_GROUP = "";
    private static volatile String LAST_MOBILE_TYPE_NETWORK_TYPE_MODEL = "";
    private static volatile String LAST_MOBILE_TYPE_NETWORK_TYPE_MODEL_ICON_GROUP = "";
    private static volatile String LAST_MOBILE_TYPE_RESOURCE_NAME = "";
    private static volatile String LAST_MOBILE_TYPE_ICON_RESOURCE_NAME = "";
    private static volatile String LAST_MOBILE_TYPE_ICON_RESOURCE_PACKAGE = "";
    private static volatile String LAST_MOBILE_TYPE_ICON_TYPE = "";
    private static volatile String LAST_MOBILE_TYPE_DRAWABLE_CLASS = "";
    private static volatile String LAST_MOBILE_TYPE_RAW_NR_CA_STATE = "";
    private static volatile String LAST_MOBILE_TYPE_NR_CA_STATE = "";
    private static volatile int LAST_MOBILE_TYPE_NETWORK_TYPE_MODEL_ICON_ID;
    private static volatile long LAST_MOBILE_TYPE_DEBUG_PUSH_UPTIME;
    private static volatile String LAST_MOBILE_TYPE_DEBUG_SNAPSHOT = "";
    private static final ThreadLocal<Integer> INTERNAL_MOBILE_TYPE_QUERY_DEPTH =
            ThreadLocal.withInitial(() -> 0);
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
        ImeHooks.install(this, loader);
    }

    private void hookSystemUi(ClassLoader loader) {
        installStatusBarHooks(loader);
        installSignalHooks(loader);
        NotificationHooks.install(this, loader);
        MBackHooks.install(this, loader);
        installBatteryHooks(loader);
        ClockHooks.install(this, loader);
    }

    private void installStatusBarHooks(ClassLoader loader) {
        hookConnectionRateView(loader);
        hookStatusBarIconConstructors(loader);
    }

    private void installSignalHooks(ClassLoader loader) {
        hookSignalImageAssignments();
        hookSignalTintUpdates();
        hookSignalDrawableLevelChanges(loader);
        hookSubscriptionManagerDebug();
        hookTelephonyCreateForSubscriptionId();
        hookTelephonyGetDataNetworkType();
        hookTelephonyGetSignalStrength();
        hookTelephonyGetServiceState();
        hookSignalStrengthGetLevel();
        hookSignalStrengthCallbacks(loader);
        hookTelephonyDisplayInfoCallbacks(loader);
        hookTelephonyDisplayInfoAccess();
        hookServiceStateNrAccess();
        hookFlymeFiveGIconDecision(loader);
    }

    private void installBatteryHooks(ClassLoader loader) {
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryMeterView", view -> {
            ModuleConfig config = ModuleConfig.load(view.getContext());
            if (!config.enabled) {
                return;
            }
            TRACKED_BATTERY_VIEWS.put(view, Boolean.TRUE);
            rememberBatteryViewState(view);
            ensureConfigRefreshObserver(view.getContext());
            if (isBatteryCodeDrawEnabled(config)) {
                syncBatteryViewLayoutIfNeeded(view, config, true);
            }
        });
        hookFlymeBatteryMeterViewDraw(loader);
        hookFlymeBatteryMeterViewMeasure(loader);
        hookFlymeBatteryMeterViewBatteryLevelChanged(loader);
        hookFlymeBatteryMeterViewConfigurationChanged(loader);
        hookFlymeBatteryMeterViewDarkChanged(loader);
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
    }

    public void installNotificationHooks(ClassLoader loader) {
        hookNotificationAppIcons(loader);
    }

    public static MBackConfigSnapshot loadMBackConfig(Context context) {
        ModuleConfig config = ModuleConfig.load(context);
        return new MBackConfigSnapshot(config);
    }

    public static ImeConfigSnapshot loadImeConfig(Context context) {
        ModuleConfig config = ModuleConfig.load(context);
        return new ImeConfigSnapshot(config);
    }

    public static ClockConfigSnapshot loadClockConfig(Context context) {
        ModuleConfig config = ModuleConfig.load(context);
        return new ClockConfigSnapshot(config);
    }

    public static void logMBackWarning(String message, Throwable throwable) {
        FlymeStatusBarSizer module = MODULE;
        if (module != null) {
            module.log(android.util.Log.WARN, TAG, message, throwable);
        }
    }

    public static void logImeWarning(String message, Throwable throwable) {
        FlymeStatusBarSizer module = MODULE;
        if (module != null) {
            module.log(android.util.Log.WARN, TAG, message, throwable);
        }
    }

    public static void logClockWarning(String message, Throwable throwable) {
        FlymeStatusBarSizer module = MODULE;
        if (module != null) {
            module.log(android.util.Log.WARN, TAG, message, throwable);
        }
    }

    public static void rememberSystemUiContext(Context context) {
        ModuleConfig.rememberSystemUiContext(context);
    }

    public void intercept(Method method, XposedInterface.Hooker hooker) {
        hook(method).intercept(hooker);
    }

    public <T> void intercept(Constructor<T> constructor, XposedInterface.Hooker hooker) {
        hook(constructor).intercept(hooker);
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
            Method setImageIcon = ImageView.class.getDeclaredMethod("setImageIcon", Icon.class);
            setImageIcon.setAccessible(true);
            hook(setImageIcon).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof ImageView) {
                    onSignalImageIconAssigned((ImageView) target, (Icon) chain.getArg(0));
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setImageIcon", t);
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
                    ImageView view = (ImageView) target;
                    syncSignalTintToCustomDrawable(view);
                    clearNotificationAppIconTintIfNeeded(view);
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
                    ImageView view = (ImageView) target;
                    syncSignalColorFilterToCustomDrawable(view,
                            chain.getArg(0) instanceof ColorFilter ? (ColorFilter) chain.getArg(0) : null);
                    clearNotificationAppIconTintIfNeeded(view);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView.setColorFilter(ColorFilter)", t);
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

    private void hookSubscriptionManagerDebug() {
        try {
            Method method = SubscriptionManager.class.getDeclaredMethod("getActiveSubscriptionInfoCount");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (isTelephonyDebugEnabled(config)) {
                    int count = resolveTelephonyDebugActiveSubscriptionCount(config);
                    LAST_ACTIVE_SUBSCRIPTION_COUNT = count;
                    return count;
                }
                if (result instanceof Integer) {
                    LAST_ACTIVE_SUBSCRIPTION_COUNT = (Integer) result;
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SubscriptionManager.getActiveSubscriptionInfoCount", t);
        }
        try {
            Method method = SubscriptionManager.class.getDeclaredMethod("getDefaultDataSubscriptionId");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (isTelephonyDebugEnabled(config)) {
                    return resolveTelephonyDebugDefaultDataSubId(config);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SubscriptionManager.getDefaultDataSubscriptionId", t);
        }
    }

    private void hookTelephonyCreateForSubscriptionId() {
        try {
            Method method = TelephonyManager.class.getDeclaredMethod("createForSubscriptionId", int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof TelephonyManager && chain.getArg(0) instanceof Integer) {
                    TELEPHONY_MANAGER_SUB_IDS.put((TelephonyManager) result, (Integer) chain.getArg(0));
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager.createForSubscriptionId", t);
        }
    }

    private void hookTelephonyGetDataNetworkType() {
        try {
            Method method = TelephonyManager.class.getDeclaredMethod("getDataNetworkType");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (!(chain.getThisObject() instanceof TelephonyManager)) {
                    return result;
                }
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = resolveEffectiveTelephonyDebugSubId(
                        config,
                        resolveSubIdFromTelephonyManager(chain.getThisObject()));
                int networkType = resolveTelephonyDebugNetworkType(config, subId);
                return networkType != Integer.MIN_VALUE ? networkType : result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager.getDataNetworkType", t);
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
                if (!isSignalCodeDrawEnabled(config)
                        && !isTelephonyDebugEnabled(config)) {
                    return result;
                }
                if (target instanceof TelephonyManager && result instanceof SignalStrength) {
                    int subId = resolveEffectiveTelephonyDebugSubId(
                            config,
                            resolveSubIdFromTelephonyManager(target));
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
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

    private void hookTelephonyGetServiceState() {
        try {
            Method method = TelephonyManager.class.getDeclaredMethod("getServiceState");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (!(chain.getThisObject() instanceof TelephonyManager) || !(result instanceof ServiceState)) {
                    return result;
                }
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = resolveEffectiveTelephonyDebugSubId(
                        config,
                        resolveSubIdFromTelephonyManager(chain.getThisObject()));
                if (SubscriptionManager.isValidSubscriptionId(subId)) {
                    SERVICE_STATE_SUB_IDS.put((ServiceState) result, subId);
                    rememberMobileTypeSubState(subId);
                    LAST_SERVICE_STATE_SUB_ID = subId;
                }
                if (isInternalMobileTypeQueryActive()) {
                    return result;
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager.getServiceState", t);
        }
    }

    private void hookSignalStrengthGetLevel() {
        try {
            Method method = SignalStrength.class.getDeclaredMethod("getLevel");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                if (!isSignalCodeDrawEnabled(config)
                        && !isTelephonyDebugEnabled(config)) {
                    return result;
                }
                if (result instanceof Integer) {
                    Object target = chain.getThisObject();
                    int level = (Integer) result;
                    int subId = target instanceof SignalStrength
                            ? resolveEffectiveTelephonyDebugSubId(
                            config,
                            SIGNAL_STRENGTH_SUB_IDS.get(target) == null
                                    ? SubscriptionManager.INVALID_SUBSCRIPTION_ID
                                    : SIGNAL_STRENGTH_SUB_IDS.get(target))
                            : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                    int spoofedLevel = resolveTelephonyDebugSignalLevel(config, subId);
                    if (spoofedLevel >= 0) {
                        result = spoofedLevel;
                        level = spoofedLevel;
                    }
                    LAST_CELLULAR_LEVEL = level;
                    if (target instanceof SignalStrength) {
                        if (SubscriptionManager.isValidSubscriptionId(subId)
                                && !isInternalSignalLevelQueryActive()) {
                            LAST_SIGNAL_SUB_ID = subId;
                            updateSignalLevelSubState(subId, level, "SignalStrength.getLevel");
                        }
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SignalStrength.getLevel", t);
        }
    }

    private void hookSignalStrengthCallbacks(ClassLoader loader) {
        hookSignalStrengthCallback(loader,
                "com.android.settingslib.mobile.MobileStatusTracker$MobileTelephonyCallback");
        hookSignalStrengthCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl$callbackEvents$1$1$callback$1");
        hookSignalStrengthCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryKairosImpl$callbackEvents$1$2$callback$1");
    }

    private void hookSignalStrengthCallback(ClassLoader loader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Method method = clazz.getDeclaredMethod("onSignalStrengthsChanged", SignalStrength.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (!(arg instanceof SignalStrength)) {
                    return result;
                }
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = resolveEffectiveTelephonyDebugSubId(
                        config,
                        resolveTelephonyCallbackSubId(chain.getThisObject()));
                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    reportSignalLevelDebug("callback-no-subId class=" + className);
                    return result;
                }
                SignalStrength signalStrength = (SignalStrength) arg;
                SIGNAL_STRENGTH_SUB_IDS.put(signalStrength, subId);
                LAST_SIGNAL_SUB_ID = subId;
                updateSignalLevelSubState(subId, signalStrength.getLevel(),
                        className + ".onSignalStrengthsChanged");
                scheduleTrackedSignalIconRefreshForSignalSubId(subId);
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook " + className + ".onSignalStrengthsChanged", t);
        }
    }

    private void hookTelephonyDisplayInfoAccess() {
        try {
            Method method = TelephonyDisplayInfo.class.getDeclaredMethod("getNetworkType");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof Integer && chain.getThisObject() instanceof TelephonyDisplayInfo) {
                    TelephonyDisplayInfo displayInfo = (TelephonyDisplayInfo) chain.getThisObject();
                    int rawNetworkType = (Integer) result;
                    ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                    int previousDisplayInfoSubId = LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID;
                    int previousLastNetworkType = LAST_MOBILE_TYPE_NETWORK_TYPE;
                    TelephonyDisplayInfoState state = rememberTelephonyDisplayInfoState(displayInfo);
                    int subId = resolveEffectiveTelephonyDebugSubId(config, state.subId);
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        state.subId = subId;
                    }
                    int spoofedNetworkType = resolveTelephonyDebugNetworkType(config, state.subId);
                    if (spoofedNetworkType != Integer.MIN_VALUE) {
                        result = spoofedNetworkType;
                    }
                    LAST_MOBILE_TYPE_RAW_NETWORK_TYPE = rawNetworkType;
                    state.networkType = (Integer) result;
                    MobileTypeSubState subState = resolveObservedMobileTypeSubState(displayInfo, state);
                    int previousObservedNetworkType = subState == null
                            ? Integer.MIN_VALUE
                            : subState.networkType;
                    if (subState != null) {
                        subState.networkType = state.networkType;
                    }
                    LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID = state.subId;
                    LAST_MOBILE_TYPE_NETWORK_TYPE = state.networkType;
                    if (isInternalMobileTypeQueryActive()) {
                        return result;
                    }
                    boolean changed = previousDisplayInfoSubId != state.subId
                            || previousLastNetworkType != state.networkType
                            || (subState != null
                            && previousObservedNetworkType != subState.networkType);
                    if (!changed) {
                        return result;
                    }
                    scheduleTrackedSignalIconRefreshForMobileTypeSubId(state.subId);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyDisplayInfo.getNetworkType", t);
        }
        try {
            Method method = TelephonyDisplayInfo.class.getDeclaredMethod("getOverrideNetworkType");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof Integer && chain.getThisObject() instanceof TelephonyDisplayInfo) {
                    TelephonyDisplayInfo displayInfo = (TelephonyDisplayInfo) chain.getThisObject();
                    int rawOverrideNetworkType = (Integer) result;
                    ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                    int previousDisplayInfoSubId = LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID;
                    int previousLastOverrideNetworkType = LAST_MOBILE_TYPE_OVERRIDE_NETWORK_TYPE;
                    TelephonyDisplayInfoState state = rememberTelephonyDisplayInfoState(displayInfo);
                    int subId = resolveEffectiveTelephonyDebugSubId(config, state.subId);
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        state.subId = subId;
                    }
                    int spoofedOverrideNetworkType = resolveTelephonyDebugOverrideNetworkType(
                            config,
                            state.subId);
                    if (spoofedOverrideNetworkType != Integer.MIN_VALUE) {
                        result = spoofedOverrideNetworkType;
                    }
                    LAST_MOBILE_TYPE_RAW_OVERRIDE_NETWORK_TYPE = rawOverrideNetworkType;
                    state.overrideNetworkType = (Integer) result;
                    MobileTypeSubState subState = resolveObservedMobileTypeSubState(displayInfo, state);
                    int previousObservedOverrideNetworkType = subState == null
                            ? Integer.MIN_VALUE
                            : subState.overrideNetworkType;
                    if (subState != null) {
                        subState.overrideNetworkType = state.overrideNetworkType;
                    }
                    LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID = state.subId;
                    LAST_MOBILE_TYPE_OVERRIDE_NETWORK_TYPE = state.overrideNetworkType;
                    if (isInternalMobileTypeQueryActive()) {
                        return result;
                    }
                    boolean changed = previousDisplayInfoSubId != state.subId
                            || previousLastOverrideNetworkType != state.overrideNetworkType
                            || (subState != null
                            && previousObservedOverrideNetworkType != subState.overrideNetworkType);
                    if (!changed) {
                        return result;
                    }
                    scheduleTrackedSignalIconRefreshForMobileTypeSubId(state.subId);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyDisplayInfo.getOverrideNetworkType", t);
        }
    }

    private void hookTelephonyDisplayInfoCallbacks(ClassLoader loader) {
        hookTelephonyDisplayInfoCallback(loader,
                "com.android.settingslib.mobile.MobileStatusTracker$MobileTelephonyCallback");
        hookTelephonyDisplayInfoCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl$callbackEvents$1$1$callback$1");
        hookTelephonyDisplayInfoCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryKairosImpl$callbackEvents$1$2$callback$1");
        hookMobileTypeStateSyncCallback(loader,
                "com.android.settingslib.mobile.MobileStatusTracker$MobileTelephonyCallback");
        hookMobileTypeStateSyncCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl$callbackEvents$1$1$callback$1");
        hookMobileTypeStateSyncCallback(loader,
                "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryKairosImpl$callbackEvents$1$2$callback$1");
        hookActiveDataSubscriptionIdCallback(loader,
                "com.android.settingslib.mobile.MobileStatusTracker$MobileTelephonyCallback");
    }

    private void hookTelephonyDisplayInfoCallback(ClassLoader loader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Method method = clazz.getDeclaredMethod("onDisplayInfoChanged", TelephonyDisplayInfo.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (!(arg instanceof TelephonyDisplayInfo)) {
                    return result;
                }
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = resolveEffectiveTelephonyDebugSubId(
                        config,
                        resolveTelephonyCallbackSubId(chain.getThisObject()));
                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    return result;
                }
                TelephonyDisplayInfo displayInfo = (TelephonyDisplayInfo) arg;
                bindTelephonyDisplayInfoToSubId(displayInfo, subId);
                primeTelephonyDisplayInfoState(displayInfo);
                scheduleTrackedSignalIconRefreshForMobileTypeSubId(subId);
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook " + className + ".onDisplayInfoChanged", t);
        }
    }

    private void hookActiveDataSubscriptionIdCallback(ClassLoader loader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Method method = clazz.getDeclaredMethod("onActiveDataSubscriptionIdChanged", int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = chain.getArg(0) instanceof Integer
                        ? (Integer) chain.getArg(0)
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                subId = resolveEffectiveTelephonyDebugSubId(config, subId);
                syncMobileTypeSubStateFromLiveTelephony(ModuleConfig.getSystemUiContext(), subId);
                scheduleTrackedPrimarySignalIconRefresh();
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG,
                    "Failed to hook " + className + ".onActiveDataSubscriptionIdChanged", t);
        }
    }

    private void hookMobileTypeStateSyncCallback(ClassLoader loader, String className) {
        hookMobileTypeStateSyncMethod(loader, className, "onServiceStateChanged", ServiceState.class);
        hookMobileTypeStateSyncMethod(loader, className, "onDataConnectionStateChanged",
                int.class, int.class);
    }

    private void hookMobileTypeStateSyncMethod(ClassLoader loader,
                                               String className,
                                               String methodName,
                                               Class<?>... parameterTypes) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                int subId = resolveEffectiveTelephonyDebugSubId(
                        config,
                        resolveTelephonyCallbackSubId(chain.getThisObject()));
                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    return result;
                }
                syncMobileTypeSubStateFromLiveTelephony(ModuleConfig.getSystemUiContext(), subId);
                scheduleTrackedSignalIconRefreshForMobileTypeSubId(subId);
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG,
                    "Failed to hook " + className + "." + methodName, t);
        }
    }

    private void hookServiceStateNrAccess() {
        try {
            Method method = ServiceState.class.getDeclaredMethod("getNrState");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof Integer && chain.getThisObject() instanceof ServiceState) {
                    ServiceState serviceState = (ServiceState) chain.getThisObject();
                    int rawNrState = (Integer) result;
                    ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                    int previousServiceStateSubId = LAST_SERVICE_STATE_SUB_ID;
                    int previousLastNrState = LAST_MOBILE_TYPE_NR_STATE;
                    int subId = resolveEffectiveTelephonyDebugSubId(
                            config,
                            SERVICE_STATE_SUB_IDS.get(serviceState) == null
                                    ? SubscriptionManager.INVALID_SUBSCRIPTION_ID
                                    : SERVICE_STATE_SUB_IDS.get(serviceState));
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        SERVICE_STATE_SUB_IDS.put(serviceState, subId);
                    }
                    int spoofedNrState = resolveTelephonyDebugNrState(config, subId);
                    if (spoofedNrState != Integer.MIN_VALUE) {
                        result = spoofedNrState;
                    }
                    MobileTypeSubState subState = SubscriptionManager.isValidSubscriptionId(subId)
                            ? rememberMobileTypeSubState(subId)
                            : null;
                    int previousObservedNrState = subState == null
                            ? Integer.MIN_VALUE
                            : subState.nrState;
                    LAST_MOBILE_TYPE_RAW_NR_STATE = rawNrState;
                    LAST_MOBILE_TYPE_NR_STATE = (Integer) result;
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        if (subState != null) {
                            subState.nrState = (Integer) result;
                        }
                        LAST_SERVICE_STATE_SUB_ID = subId;
                    }
                    if (isInternalMobileTypeQueryActive()) {
                        return result;
                    }
                    boolean changed = previousServiceStateSubId != LAST_SERVICE_STATE_SUB_ID
                            || previousLastNrState != LAST_MOBILE_TYPE_NR_STATE
                            || (subState != null && previousObservedNrState != subState.nrState);
                    if (!changed) {
                        return result;
                    }
                    scheduleTrackedSignalIconRefreshForMobileTypeSubId(subId);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ServiceState.getNrState", t);
        }
        try {
            Method method = ServiceState.class.getDeclaredMethod("getNrCaState");
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (chain.getThisObject() instanceof ServiceState) {
                    ServiceState serviceState = (ServiceState) chain.getThisObject();
                    Object rawNrCaState = result;
                    ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                    int previousServiceStateSubId = LAST_SERVICE_STATE_SUB_ID;
                    String previousLastNrCaState = LAST_MOBILE_TYPE_NR_CA_STATE;
                    int subId = resolveEffectiveTelephonyDebugSubId(
                            config,
                            SERVICE_STATE_SUB_IDS.get(serviceState) == null
                                    ? SubscriptionManager.INVALID_SUBSCRIPTION_ID
                                    : SERVICE_STATE_SUB_IDS.get(serviceState));
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        SERVICE_STATE_SUB_IDS.put(serviceState, subId);
                    }
                    Object spoofedNrCaState = resolveTelephonyDebugNrCaState(config, subId);
                    if (spoofedNrCaState != null) {
                        result = spoofedNrCaState;
                    }
                    MobileTypeSubState subState = SubscriptionManager.isValidSubscriptionId(subId)
                            ? rememberMobileTypeSubState(subId)
                            : null;
                    String previousObservedNrCaState = subState == null
                            ? ""
                            : nonNullText(subState.nrCaState);
                    String resolvedNrCaState = safeToString(result);
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        if (subState != null) {
                            subState.nrCaState = resolvedNrCaState;
                        }
                        LAST_SERVICE_STATE_SUB_ID = subId;
                    }
                    LAST_MOBILE_TYPE_RAW_NR_CA_STATE = safeToString(rawNrCaState);
                    LAST_MOBILE_TYPE_NR_CA_STATE = resolvedNrCaState;
                    if (isInternalMobileTypeQueryActive()) {
                        return result;
                    }
                    boolean changed = previousServiceStateSubId != LAST_SERVICE_STATE_SUB_ID
                            || !TextUtils.equals(previousLastNrCaState, LAST_MOBILE_TYPE_NR_CA_STATE)
                            || (subState != null
                            && !TextUtils.equals(previousObservedNrCaState, subState.nrCaState));
                    if (!changed) {
                        return result;
                    }
                    scheduleTrackedSignalIconRefreshForMobileTypeSubId(subId);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ServiceState.getNrCaState", t);
        }
    }

    private void hookFlymeFiveGIconDecision(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.statusbar.net.FlymeMobileConnectionFeatureKt",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("getFlymeFiveGIcon", int.class, ServiceState.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                LAST_MOBILE_TYPE_RAW_FLYME_ICON_GROUP = describeMobileIconGroup(result);
                ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
                Object serviceStateArg = chain.getArg(1);
                int subId = serviceStateArg instanceof ServiceState
                        ? resolveEffectiveTelephonyDebugSubId(
                        config,
                        SERVICE_STATE_SUB_IDS.get(serviceStateArg) == null
                                ? SubscriptionManager.INVALID_SUBSCRIPTION_ID
                                : SERVICE_STATE_SUB_IDS.get(serviceStateArg))
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                Object spoofedFlymeIcon = resolveTelephonyDebugFlymeFiveGIcon(config, subId, loader);
                int debugProfile = resolveTelephonyDebugNetworkProfile(config, subId);
                if (isTelephonyDebugEnabled(config)
                        && resolveTelephonyDebugActiveSubscriptionCount(config) <= 0) {
                    result = null;
                } else if (spoofedFlymeIcon != null
                        || debugProfile == SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G
                        || debugProfile == SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G
                        || debugProfile == SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G
                        || debugProfile == SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE) {
                    result = spoofedFlymeIcon;
                }
                LAST_MOBILE_TYPE_FLYME_ICON_GROUP = normalizeMobileIconGroupLabel(
                        describeMobileIconGroup(result));
                if (serviceStateArg instanceof ServiceState) {
                    MobileTypeSubState subState = rememberMobileTypeSubState(subId);
                    if (subState != null) {
                        subState.flymeIconGroup = LAST_MOBILE_TYPE_FLYME_ICON_GROUP;
                    }
                    scheduleTrackedSignalIconRefreshForMobileTypeSubId(
                            subId);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeMobileConnectionFeatureKt.getFlymeFiveGIcon", t);
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
                        if ("onAttachedToWindow".equals(name) || "onConfigurationChanged".equals(name)) {
                            rememberConnectionRateBaseState(view, true);
                            applyConnectionRateTextScale(view);
                        }
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
                    if (isTrackedBatteryDrawableOwner(drawable)) {
                        applyIosBatteryStyleIfNeeded(drawable);
                        return chain.proceed();
                    }
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

    private void hookFlymeBatteryMeterViewBatteryLevelChanged(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.FlymeBatteryMeterView", false, loader);
            Method method = clazz.getDeclaredMethod(
                    "onBatteryLevelChanged",
                    int.class,
                    boolean.class,
                    boolean.class,
                    boolean.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    syncBatteryViewAfterStateChange((View) target);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeBatteryMeterView.onBatteryLevelChanged", t);
        }
    }

    private void hookFlymeBatteryMeterViewConfigurationChanged(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.FlymeBatteryMeterView", false, loader);
            Method method = clazz.getDeclaredMethod("onConfigurationChanged", Configuration.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    syncBatteryViewAfterConfigurationChanged((View) target);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeBatteryMeterView.onConfigurationChanged", t);
        }
    }

    private void hookFlymeBatteryMeterViewDarkChanged(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.battery.FlymeBatteryMeterView", false, loader);
            Method method = clazz.getDeclaredMethod("onDarkChanged", ArrayList.class, float.class, int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (target instanceof View) {
                    View batteryView = (View) target;
                    BatteryViewState state = rememberBatteryViewState(batteryView);
                    if (refreshBatteryViewRuntimeSnapshot(batteryView, state)) {
                        batteryView.invalidate();
                    }
                    invalidateLinkedSignalViews(batteryView);
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeBatteryMeterView.onDarkChanged", t);
        }
    }

    private void hookNotificationAppIcons(ClassLoader loader) {
        hookNotificationIconTint(loader);
        hookNotificationStatusBarIconUpdate(loader);
    }

    private void hookNotificationSmallIconReplacement(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.notification.utils.FlymeNotificationIconUtils",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "resetNotificationSmallIconIfNeed",
                    StatusBarNotification.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object notificationArg = chain.getArg(0);
                if (!(notificationArg instanceof StatusBarNotification)) {
                    return result;
                }
                Context context = null;
                Object thisObject = chain.getThisObject();
                Object contextField = ReflectUtils.getField(thisObject, "mContext");
                if (contextField instanceof Context) {
                    context = (Context) contextField;
                }
                replaceNotificationSmallIconWithAppIconIfNeeded(
                        context,
                        (StatusBarNotification) notificationArg);
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook notification small icon replacement", t);
        }
    }

    private void hookNotificationIconTint(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    false,
                    loader);
            Method updateIconColor = clazz.getDeclaredMethod("updateIconColor");
            updateIconColor.setAccessible(true);
            hook(updateIconColor).intercept(chain -> {
                Object result = chain.proceed();
                clearNotificationAppIconTintIfNeeded(chain.getThisObject());
                return result;
            });

            Method onDarkChanged = clazz.getDeclaredMethod(
                    "onDarkChanged",
                    ArrayList.class,
                    float.class,
                    int.class);
            onDarkChanged.setAccessible(true);
            hook(onDarkChanged).intercept(chain -> {
                Object result = chain.proceed();
                clearNotificationAppIconTintIfNeeded(chain.getThisObject());
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook notification icon tint", t);
        }
    }

    private void hookNotificationStatusBarIconUpdate(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if ("updateDrawable".equals(name)) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        applyNotificationStatusBarIconDrawable(chain.getThisObject());
                        return result;
                    });
                    continue;
                }
                if ("onConfigurationChanged".equals(name)
                        && method.getParameterTypes().length == 1
                        && Configuration.class.equals(method.getParameterTypes()[0])) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof View) {
                            refreshNotificationAppIconAfterConfigurationChange(
                                    (View) target,
                                    chain.getArg(0) instanceof Configuration
                                            ? (Configuration) chain.getArg(0) : null);
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook notification status bar update", t);
        }
    }

    private static void replaceNotificationSmallIconWithAppIconIfNeeded(
            Context context, StatusBarNotification sbn) {
        Notification notification = sbn == null ? null : sbn.getNotification();
        markNotificationAppIconReplacement(notification, false);
    }

    private static void clearNotificationAppIconTintIfNeeded(Object target) {
        if (!(target instanceof ImageView)) {
            return;
        }
        try {
            ImageView view = (ImageView) target;
            if (!shouldKeepNotificationAppIconOriginalColors(view)) {
                return;
            }
            synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS) {
                if (Boolean.TRUE.equals(NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS.get(view))) {
                    return;
                }
                NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS.put(view, Boolean.TRUE);
            }
            try {
                clearDrawableColorState(view.getDrawable());
                if (view.getImageTintList() != null) {
                    view.setImageTintList(null);
                }
                if (view.getColorFilter() != null) {
                    view.setColorFilter(null);
                }
            } finally {
                synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS) {
                    NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS.remove(view);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void postClearNotificationAppIconTintIfNeeded(ImageView view) {
        if (view == null) {
            return;
        }
        view.post(() -> clearNotificationAppIconTintIfNeeded(view));
    }

    private static boolean shouldKeepNotificationAppIconOriginalColors(ImageView view) {
        if (view == null || !isNotificationBackedStatusBarIconView(view)) {
            return false;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!config.enabled || !config.notificationAppIconEnabled) {
            return false;
        }
        Object value = ReflectUtils.invokeNoArg(view, "getNotification");
        if (!(value instanceof StatusBarNotification)) {
            return false;
        }
        return wasNotificationAppIconReplaced(((StatusBarNotification) value).getNotification());
    }

    private static Icon getNotificationSmallIcon(Notification notification) {
        Object value = ReflectUtils.invokeNoArg(notification, "getSmallIcon");
        return value instanceof Icon ? (Icon) value : null;
    }

    private static boolean isSameNotificationIcon(Icon actual, Icon expected) {
        if (actual == null || expected == null) {
            return false;
        }
        if (actual == expected) {
            return true;
        }
        Object value = ReflectUtils.invokeMethod(
                actual,
                "sameAs",
                new Class<?>[]{Icon.class},
                expected);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String resolveNotificationSourcePackage(StatusBarNotification sbn) {
        Object value = ReflectUtils.invokeNoArg(sbn, "getOrigPackageName");
        if (value instanceof String && !TextUtils.isEmpty((String) value)) {
            return (String) value;
        }
        String packageName = sbn == null ? null : sbn.getPackageName();
        return TextUtils.isEmpty(packageName) ? null : packageName;
    }

    private static ApplicationInfo resolveNotificationApplicationInfo(
            Context context, String packageName, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            return null;
        }
        if (userId >= 0) {
            Object value = ReflectUtils.invokeMethod(
                    packageManager,
                    "getApplicationInfoAsUser",
                    new Class<?>[]{String.class, int.class, int.class},
                    packageName,
                    0,
                    userId);
            if (value instanceof ApplicationInfo) {
                return (ApplicationInfo) value;
            }
        }
        try {
            return packageManager.getApplicationInfo(packageName, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int resolveNotificationUserId(StatusBarNotification sbn) {
        int userId = ReflectUtils.invokeNoArgInt(sbn, "getUserId", -1);
        if (userId >= 0) {
            return userId;
        }
        Object userHandle = sbn == null ? null : sbn.getUser();
        userId = ReflectUtils.invokeNoArgInt(userHandle, "getIdentifier", -1);
        return Math.max(userId, 0);
    }

    private static boolean shouldUseApplicationIconForNotification(
            Context context, String packageName, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        String cacheKey = buildNotificationAppIconEligibilityCacheKey(packageName, userId);
        synchronized (NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE) {
            Boolean cached = NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        ApplicationInfo appInfo = resolveNotificationApplicationInfo(context, packageName, userId);
        boolean shouldReplace = shouldUseApplicationIconForNotification(
                context,
                packageName,
                appInfo);
        synchronized (NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE) {
            NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE.put(cacheKey, shouldReplace);
        }
        return shouldReplace;
    }

    private static String buildNotificationAppIconEligibilityCacheKey(
            String packageName, int userId) {
        return packageName + ":" + userId;
    }

    private static boolean shouldUseApplicationIconForNotification(
            Context context, String packageName, ApplicationInfo appInfo) {
        if (context == null || appInfo == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (isCoreSystemNotificationPackage(packageName)) {
            return false;
        }
        int flags = appInfo.flags;
        boolean isSystemApp = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (!isSystemApp && !isUpdatedSystemApp) {
            return true;
        }
        return hasLauncherEntry(context.getPackageManager(), packageName);
    }

    private static boolean isCoreSystemNotificationPackage(String packageName) {
        return PACKAGE_ANDROID.equals(packageName)
                || SYSTEM_UI.equals(packageName);
    }

    private static boolean hasLauncherEntry(PackageManager packageManager, String packageName) {
        if (packageManager == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        try {
            return packageManager.getLaunchIntentForPackage(packageName) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Icon createNotificationAppIcon(
            Context context, String packageName, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        Drawable drawable = getCachedNotificationApplicationIcon(context, packageName, userId);
        Bitmap bitmap = createBitmapFromDrawable(drawable, dp(context, 24));
        return bitmap == null ? null : Icon.createWithBitmap(bitmap);
    }

    private static Drawable getCachedNotificationApplicationIcon(
            Context context, String packageName, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            Method method = flymeGetApplicationIconMethod;
            if (method == null) {
                Class<?> clazz = Class.forName(
                        FLYME_STATUS_BAR_ICON_UTILS,
                        false,
                        context.getClassLoader());
                method = clazz.getDeclaredMethod(
                        "getApplicationIcon",
                        Context.class,
                        String.class,
                        int.class);
                method.setAccessible(true);
                flymeGetApplicationIconMethod = method;
            }
            Object value = method.invoke(null, context, packageName, userId);
            return value instanceof Drawable ? (Drawable) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable cloneNotificationIconDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) {
                Drawable clone = state.newDrawable().mutate();
                clone.setLevel(drawable.getLevel());
                clearDrawableColorState(clone);
                return clone;
            }
            Drawable mutated = drawable.mutate();
            clearDrawableColorState(mutated);
            return mutated;
        } catch (Throwable ignored) {
            clearDrawableColorState(drawable);
            return drawable;
        }
    }

    private static int resolveNotificationAppIconRenderSize(View view) {
        if (view == null) {
            return 0;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (config.enabled && config.notificationAppIconEnabled) {
            return dp(view, config.notificationAppIconSizeDp);
        }
        int sizePx = Math.max(view.getWidth(), view.getHeight());
        if (sizePx > 0) {
            return sizePx;
        }
        sizePx = Math.max(view.getMeasuredWidth(), view.getMeasuredHeight());
        if (sizePx > 0) {
            return sizePx;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            sizePx = Math.max(layoutParams.width, layoutParams.height);
            if (sizePx > 0) {
                return sizePx;
            }
        }
        return dp(view, DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP);
    }

    private static Drawable createNotificationStatusBarIconDrawable(
            View view,
            String packageName,
            int userId,
            Drawable drawable) {
        Drawable working = cloneNotificationIconDrawable(drawable);
        if (view == null || working == null) {
            return working;
        }
        int sizePx = resolveNotificationAppIconRenderSize(view);
        if (sizePx <= 0) {
            return working;
        }
        Bitmap bitmap = createFittedNotificationAppIconBitmap(view, working, sizePx);
        if (bitmap == null) {
            return working;
        }
        BitmapDrawable bitmapDrawable = new BitmapDrawable(view.getResources(), bitmap);
        clearDrawableColorState(bitmapDrawable);
        return bitmapDrawable;
    }

    private static Bitmap createFittedNotificationAppIconBitmap(
            View view,
            Drawable drawable,
            int sizePx) {
        if (view == null || drawable == null || sizePx <= 0) {
            return null;
        }
        Bitmap sourceBitmap = createBitmapFromDrawable(drawable, sizePx);
        if (sourceBitmap == null || sourceBitmap.isRecycled()) {
            return null;
        }
        int targetSize = Math.max(1, sizePx);
        Rect sourceBounds = findVisibleBitmapBounds(sourceBitmap);
        if (sourceBounds.isEmpty()) {
            sourceBounds.set(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight());
        }
        int insetPx = Math.max(0, dp(view, DEFAULT_NOTIFICATION_APP_ICON_INSET_DP));
        int availableSize = Math.max(1, targetSize - insetPx * 2);
        float scale = Math.min(
                (float) availableSize / Math.max(1, sourceBounds.width()),
                (float) availableSize / Math.max(1, sourceBounds.height()));
        int destWidth = Math.max(1, Math.round(sourceBounds.width() * scale));
        int destHeight = Math.max(1, Math.round(sourceBounds.height() * scale));
        int left = (targetSize - destWidth) / 2;
        int top = (targetSize - destHeight) / 2;
        Bitmap bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        Rect destBounds = new Rect(left, top, left + destWidth, top + destHeight);
        canvas.drawBitmap(sourceBitmap, sourceBounds, destBounds, paint);
        return bitmap;
    }

    private static Rect findVisibleBitmapBounds(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return new Rect();
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) <= 8) {
                    continue;
                }
                if (x < left) {
                    left = x;
                }
                if (x > right) {
                    right = x;
                }
                if (y < top) {
                    top = y;
                }
                if (y > bottom) {
                    bottom = y;
                }
            }
        }
        if (right < left || bottom < top) {
            return new Rect();
        }
        return new Rect(left, top, right + 1, bottom + 1);
    }

    private static Drawable resolveNotificationStatusBarIconDrawable(Object target) {
        if (!(target instanceof View)) {
            return null;
        }
        View view = (View) target;
        if (!isNotificationBackedStatusBarIconView(view)) {
            return null;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!config.enabled || !config.notificationAppIconEnabled) {
            return null;
        }
        Object value = ReflectUtils.invokeNoArg(target, "getNotification");
        if (!(value instanceof StatusBarNotification)) {
            return null;
        }
        StatusBarNotification sbn = (StatusBarNotification) value;
        String packageName = resolveNotificationSourcePackage(sbn);
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        int userId = resolveNotificationUserId(sbn);
        if (!shouldUseApplicationIconForNotification(view.getContext(), packageName, userId)) {
            return null;
        }
        Drawable drawable = getCachedNotificationApplicationIcon(
                view.getContext(),
                packageName,
                userId);
        if (drawable == null) {
            return null;
        }
        markNotificationAppIconReplacement(sbn.getNotification(), true);
        return createNotificationStatusBarIconDrawable(view, packageName, userId, drawable);
    }

    private static void applyNotificationStatusBarIconDrawable(Object target) {
        if (!(target instanceof ImageView)) {
            return;
        }
        ImageView view = (ImageView) target;
        synchronized (NOTIFICATION_APP_ICON_APPLY_GUARDS) {
            if (Boolean.TRUE.equals(NOTIFICATION_APP_ICON_APPLY_GUARDS.get(view))) {
                return;
            }
            NOTIFICATION_APP_ICON_APPLY_GUARDS.put(view, Boolean.TRUE);
        }
        try {
            Drawable drawable = resolveNotificationStatusBarIconDrawable(target);
            if (drawable == null) {
                if (restoreNotificationStatusBarIconDrawableIfNeeded(view)) {
                    return;
                }
                applyNotificationAppIconViewStyle(view);
                return;
            }
            view.setImageDrawable(drawable);
            clearNotificationAppIconTintIfNeeded(view);
            postClearNotificationAppIconTintIfNeeded(view);
            applyNotificationAppIconViewStyle(view);
        } catch (Throwable ignored) {
        } finally {
            synchronized (NOTIFICATION_APP_ICON_APPLY_GUARDS) {
                NOTIFICATION_APP_ICON_APPLY_GUARDS.remove(view);
            }
        }
    }

    private static boolean restoreNotificationStatusBarIconDrawableIfNeeded(ImageView view) {
        if (view == null || !isNotificationBackedStatusBarIconView(view)) {
            return false;
        }
        Object value = ReflectUtils.invokeNoArg(view, "getNotification");
        if (!(value instanceof StatusBarNotification)) {
            return false;
        }
        Notification notification = ((StatusBarNotification) value).getNotification();
        if (!wasNotificationAppIconReplaced(notification)) {
            return false;
        }
        synchronized (NOTIFICATION_APP_ICON_RESTORE_GUARDS) {
            if (Boolean.TRUE.equals(NOTIFICATION_APP_ICON_RESTORE_GUARDS.get(view))) {
                return false;
            }
            NOTIFICATION_APP_ICON_RESTORE_GUARDS.put(view, Boolean.TRUE);
        }
        try {
            ReflectUtils.invokeNoArg(view, "updateDrawable");
            return true;
        } finally {
            synchronized (NOTIFICATION_APP_ICON_RESTORE_GUARDS) {
                NOTIFICATION_APP_ICON_RESTORE_GUARDS.remove(view);
            }
        }
    }

    private static void applyNotificationAppIconViewStyle(ImageView view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        rememberOriginalLayout(view, lp);
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            rememberOriginalMargins(view, (ViewGroup.MarginLayoutParams) lp);
        }
        rememberOriginalNotificationIconPadding(view);

        ModuleConfig config = ModuleConfig.load(view.getContext());
        boolean customize = shouldCustomizeNotificationAppIconView(view, config);
        boolean changed = false;

        int[] originalSize = ORIGINAL_SIZES.get(view);
        if (customize) {
            int targetWidth = dp(view, config.notificationAppIconSizeDp);
            if (targetWidth > 0 && lp.width != targetWidth) {
                lp.width = targetWidth;
                changed = true;
            }
            if (originalSize != null && lp.height != originalSize[1]) {
                lp.height = originalSize[1];
                changed = true;
            }
        } else if (originalSize != null) {
            if (lp.width != originalSize[0]) {
                lp.width = originalSize[0];
                changed = true;
            }
            if (lp.height != originalSize[1]) {
                lp.height = originalSize[1];
                changed = true;
            }
        }

        int[] originalPadding = ORIGINAL_PADDINGS.get(view);
        if (originalPadding != null) {
            int left = originalPadding[0];
            int top = originalPadding[1];
            int right = originalPadding[2];
            int bottom = originalPadding[3];
            if (customize) {
                int padding = dp(view, config.notificationAppIconPaddingDp);
                left = padding;
                top = padding;
                right = padding;
                bottom = padding;
            }
            int[] currentPadding = readViewPaddingDirect(view);
            if (currentPadding[0] != left || currentPadding[1] != top
                    || currentPadding[2] != right || currentPadding[3] != bottom) {
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

    private static boolean shouldCustomizeNotificationAppIconView(ImageView view, ModuleConfig config) {
        if (view == null || config == null || !config.enabled || !config.notificationAppIconEnabled) {
            return false;
        }
        Object value = ReflectUtils.invokeNoArg(view, "getNotification");
        if (!(value instanceof StatusBarNotification)) {
            return false;
        }
        return wasNotificationAppIconReplaced(((StatusBarNotification) value).getNotification());
    }

    private static Bitmap createBitmapFromDrawable(Drawable drawable, int sizePx) {
        if (drawable == null || sizePx <= 0) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmap;
            }
        }
        Drawable working = drawable;
        try {
            Drawable.ConstantState constantState = drawable.getConstantState();
            if (constantState != null) {
                working = constantState.newDrawable().mutate();
            } else {
                working = drawable.mutate();
            }
        } catch (Throwable ignored) {
            working = drawable;
        }
        clearDrawableColorState(working);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        working.setBounds(0, 0, sizePx, sizePx);
        working.draw(canvas);
        return bitmap;
    }

    private static void clearDrawableColorState(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        try {
            drawable.setTintList(null);
        } catch (Throwable ignored) {
        }
        try {
            drawable.setColorFilter(null);
        } catch (Throwable ignored) {
        }
    }

    private static void markNotificationAppIconReplacement(Notification notification, boolean replaced) {
        if (notification == null || notification.extras == null) {
            return;
        }
        try {
            if (replaced) {
                notification.extras.putBoolean(EXTRA_NOTIFICATION_APP_ICON_REPLACED, true);
                return;
            }
            notification.extras.remove(EXTRA_NOTIFICATION_APP_ICON_REPLACED);
        } catch (Throwable ignored) {
        }
    }

    private static boolean wasNotificationAppIconReplaced(Notification notification) {
        return notification != null
                && notification.extras != null
                && notification.extras.getBoolean(EXTRA_NOTIFICATION_APP_ICON_REPLACED, false);
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
        boolean quickCharging = resolveBatteryQuickCharging(drawable);
        int tintColor = resolveBatteryTintColor(drawable, Color.BLACK);
        int textColor = resolveBatteryTextColor(tintColor);
        boolean showLevelText = config.batteryLevelTextEnabled;
        drawBatteryByStyle(config, canvas, ((Drawable) drawable).getBounds(), level, pluggedIn, charging,
                quickCharging,
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
        BatteryViewState state = rememberBatteryViewState(batteryView);
        ensureBatteryViewRuntimeSnapshot(batteryView, state);
        int size = resolveBatterySquareSize(batteryView, config);
        int width = resolveBatteryRenderWidth(size, config, state.showBolt);
        int height = resolveBatteryRenderHeight(size);
        int top = Math.round((batteryView.getHeight() - height) / 2f);
        boolean showLevelText = config.batteryLevelTextEnabled;
        state.drawBounds.set(0, top, width, top + height);
        drawBatteryByStyle(config, canvas, state.drawBounds,
                state.level, state.pluggedIn, state.charging, state.quickCharging,
                state.tintColor, state.textColor, showLevelText);
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
            boolean pluggedIn, boolean charging, boolean quickCharging,
            int fillColor, int textColor, boolean showLevelText) {
        float textScale = resolveBatteryInnerTextScale(config);
        Typeface typeface = BatteryTextFontHelper.resolveTypeface(ModuleConfig.getSystemUiContext(), config == null
                ? SettingsStore.DEFAULT_BATTERY_TEXT_FONT
                : resolveBatteryTextFontOption(config));
        int style = resolveBatteryStyle(config);
        boolean hollow = config != null && config.batteryHollowEnabled;
        boolean hollowFillFollowsLevel = config != null && config.batteryHollowFillFollowsLevel;
        if (style == SettingsStore.BATTERY_STYLE_ONEUI) {
            OneUiBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging, quickCharging,
                    fillColor, textColor, showLevelText, textScale, typeface, hollow,
                    hollowFillFollowsLevel);
            return;
        }
        IosBatteryPainter.draw(canvas, bounds, level, pluggedIn, charging, quickCharging,
                fillColor, textColor, showLevelText, textScale, typeface, hollow,
                hollowFillFollowsLevel);
    }

    private static boolean resolveBatteryQuickCharging(Object target) {
        Object quickValue = ReflectUtils.getField(target, "mQuickCharging");
        if (quickValue instanceof Boolean) {
            return (Boolean) quickValue;
        }
        Object batteryController = ReflectUtils.getField(target, "mBatteryController");
        Object controllerValue = ReflectUtils.invokeNoArg(batteryController, "isQuickCharging");
        return controllerValue instanceof Boolean && (Boolean) controllerValue;
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

    static int resolveSignalLinkedTintColor(View signalView, ColorStateList tintList, int[] state, int fallbackColor) {
        int fallback = normalizeIconColor(resolveTintListColor(tintList, state, fallbackColor));
        View batteryTintSource = findBestBatteryTintSource(signalView);
        if (batteryTintSource == null) {
            return fallback;
        }
        return resolveBatteryTintColor(batteryTintSource, fallback);
    }

    static int resolveSignalMobileTypeBadgeFontWeight() {
        Context context = ModuleConfig.getSystemUiContext();
        ModuleConfig config = ModuleConfig.load(context);
        if (config == null || !config.enabled) {
            return 400;
        }
        return resolveClockFontWeight(config);
    }

    static int resolveSignalMobileTypeBadge() {
        return resolveSignalMobileTypeBadgeForActiveDataSubscription(
                ModuleConfig.getSystemUiContext());
    }

    private static int resolveSignalMobileTypeBadgeFromText(String value) {
        if (TextUtils.isEmpty(value)) {
            return Integer.MIN_VALUE;
        }
        String normalized = value.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace(":", "")
                .replace(".", "")
                .replace("/", "");
        if (TextUtils.isEmpty(normalized)
                || "NULL".equals(normalized)
                || "UNKNOWN".equals(normalized)) {
            return Integer.MIN_VALUE;
        }
        if (containsAny(normalized,
                "5GA",
                "5GCA",
                "5GPLUS",
                "NRADVANCED",
                "FIVEGA",
                "FIVEGCA",
                "NR5GPLUS")) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_5GA;
        }
        if (containsAny(normalized,
                "5GBASIC",
                "FIVEGBASIC",
                "5G")) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_5G;
        }
        return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
    }

    private static boolean containsAny(String value, String... tokens) {
        if (TextUtils.isEmpty(value) || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (!TextUtils.isEmpty(token) && value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static int resolveSignalMobileTypeBadgeFromTelephonyState() {
        return resolveSignalMobileTypeBadgeFromTelephonyState(
                LAST_MOBILE_TYPE_NETWORK_TYPE,
                LAST_MOBILE_TYPE_OVERRIDE_NETWORK_TYPE,
                LAST_MOBILE_TYPE_NR_STATE,
                LAST_MOBILE_TYPE_NR_CA_STATE);
    }

    private static int resolveSignalMobileTypeBadgeFromTelephonyState(int networkType,
                                                                      int overrideNetworkType,
                                                                      int nrState,
                                                                      String nrCaState) {
        if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_5GA;
        }
        if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_5G;
        }
        if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
            return isNrCaStateEnabled(nrCaState)
                    ? SignalPreviewPainter.MOBILE_TYPE_BADGE_5GA
                    : SignalPreviewPainter.MOBILE_TYPE_BADGE_5G;
        }
        if (nrState == 2 || nrState == 3) {
            return isNrCaStateEnabled(nrCaState)
                    ? SignalPreviewPainter.MOBILE_TYPE_BADGE_5GA
                    : SignalPreviewPainter.MOBILE_TYPE_BADGE_5G;
        }
        return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
    }

    private static boolean isNrCaStateEnabled(String nrCaState) {
        return "true".equalsIgnoreCase(nrCaState);
    }

    private static int resolveSignalMobileTypeBadgeForActiveDataSubscription(Context context) {
        if (context == null || isUsingWifiOnlyInternet(context)) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
        }
        int subId = resolveDefaultDataSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
        }
        MobileTypeSubState state = snapshotResolvedMobileTypeSubState(subId);
        int badge = resolveSignalMobileTypeBadgeFromSubState(state);
        return badge != Integer.MIN_VALUE
                ? badge
                : SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
    }

    private static int resolveSignalMobileTypeBadgeFromSubState(MobileTypeSubState state) {
        if (state == null) {
            return Integer.MIN_VALUE;
        }
        int badge = resolveSignalMobileTypeBadgeFromTelephonyState(
                state.networkType,
                state.overrideNetworkType,
                state.nrState,
                state.nrCaState);
        if (badge != SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE) {
            return badge;
        }
        badge = resolveSignalMobileTypeBadgeFromText(state.flymeIconGroup);
        if (badge != Integer.MIN_VALUE) {
            return badge;
        }
        if (hasMeaningfulMobileTypeSubState(state)) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean hasMeaningfulMobileTypeSubState(MobileTypeSubState state) {
        return state != null
                && (state.networkType != Integer.MIN_VALUE
                || state.overrideNetworkType != Integer.MIN_VALUE
                || state.nrState != Integer.MIN_VALUE
                || !TextUtils.isEmpty(state.nrCaState)
                || !TextUtils.isEmpty(state.flymeIconGroup));
    }

    private static boolean isTelephonyDebugEnabled(ModuleConfig config) {
        return config != null && config.telephonyDebugEnabled;
    }

    private static boolean isSignalLevelDebugLogEnabled(ModuleConfig config) {
        return SIGNAL_LEVEL_DEBUG_LOG_FORCE_ENABLED || isTelephonyDebugEnabled(config);
    }

    private static int resolveTelephonyDebugActiveSubscriptionCount(ModuleConfig config) {
        if (!isTelephonyDebugEnabled(config)) {
            return -1;
        }
        return SettingsStore.normalizeTelephonyDebugSimCount(config.telephonyDebugSimCount);
    }

    private static int resolveTelephonyDebugDefaultDataSlot(ModuleConfig config) {
        int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
        if (simCount <= 0) {
            return SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE;
        }
        int slot = SettingsStore.normalizeTelephonyDebugDefaultDataSlot(
                config.telephonyDebugDefaultDataSlot);
        if (slot == SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_NONE) {
            return slot;
        }
        if (slot >= simCount) {
            return SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1;
        }
        return slot;
    }

    private static int resolveTelephonyDebugSubIdForSlot(int slot) {
        switch (slot) {
            case SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1:
                return TELEPHONY_DEBUG_SUB_ID_CARD1;
            case SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD2:
                return TELEPHONY_DEBUG_SUB_ID_CARD2;
            default:
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    private static boolean isTelephonyDebugSubId(int subId) {
        return subId == TELEPHONY_DEBUG_SUB_ID_CARD1 || subId == TELEPHONY_DEBUG_SUB_ID_CARD2;
    }

    private static int resolveTelephonyDebugDefaultDataSubId(ModuleConfig config) {
        if (!isTelephonyDebugEnabled(config)) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return resolveTelephonyDebugSubIdForSlot(resolveTelephonyDebugDefaultDataSlot(config));
    }

    private static int resolveTelephonyDebugPrimarySignalSubId(ModuleConfig config) {
        int subId = resolveTelephonyDebugDefaultDataSubId(config);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
        if (simCount <= 0) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return resolveTelephonyDebugSubIdForSlot(SettingsStore.TELEPHONY_DEBUG_DEFAULT_DATA_SLOT_CARD1);
    }

    private static int resolveEffectiveTelephonyDebugSubId(ModuleConfig config, int subId) {
        if (!isTelephonyDebugEnabled(config)) {
            return subId;
        }
        int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
        if (simCount <= 0) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD1) {
            return subId;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD2) {
            return simCount >= 2 ? subId : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return resolveTelephonyDebugPrimarySignalSubId(config);
    }

    private static int resolveTelephonyDebugNetworkProfile(ModuleConfig config, int subId) {
        if (!isTelephonyDebugEnabled(config) || !isTelephonyDebugSubId(subId)) {
            return Integer.MIN_VALUE;
        }
        int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD1) {
            return simCount >= 1 ? config.telephonyDebugSlot1NetworkProfile : Integer.MIN_VALUE;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD2) {
            return simCount >= 2 ? config.telephonyDebugSlot2NetworkProfile : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static int resolveTelephonyDebugSignalLevel(ModuleConfig config, int subId) {
        if (!isTelephonyDebugEnabled(config) || !isTelephonyDebugSubId(subId)) {
            return -1;
        }
        int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD1) {
            return simCount >= 1
                    ? SettingsStore.normalizeTelephonyDebugSignalLevel(config.telephonyDebugSlot1SignalLevel)
                    : -1;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD2) {
            return simCount >= 2
                    ? SettingsStore.normalizeTelephonyDebugSignalLevel(config.telephonyDebugSlot2SignalLevel)
                    : -1;
        }
        return -1;
    }

    private static int resolveTelephonyDebugNetworkType(ModuleConfig config, int subId) {
        if (isTelephonyDebugEnabled(config)
                && resolveTelephonyDebugActiveSubscriptionCount(config) <= 0) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
                return TelephonyManager.NETWORK_TYPE_EDGE;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
                return TelephonyManager.NETWORK_TYPE_HSPA;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
                return TelephonyManager.NETWORK_TYPE_LTE;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return TelephonyManager.NETWORK_TYPE_NR;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private static int resolveTelephonyDebugOverrideNetworkType(ModuleConfig config, int subId) {
        if (isTelephonyDebugEnabled(config)
                && resolveTelephonyDebugActiveSubscriptionCount(config) <= 0) {
            return TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE;
        }
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
                return TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private static int resolveTelephonyDebugNrState(ModuleConfig config, int subId) {
        if (isTelephonyDebugEnabled(config)
                && resolveTelephonyDebugActiveSubscriptionCount(config) <= 0) {
            return 0;
        }
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return 3;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
                return 0;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private static Boolean resolveTelephonyDebugNrCaState(ModuleConfig config, int subId) {
        if (isTelephonyDebugEnabled(config)
                && resolveTelephonyDebugActiveSubscriptionCount(config) <= 0) {
            return Boolean.FALSE;
        }
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return Boolean.TRUE;
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private static String resolveTelephonyDebugFlymeIconGroupLabel(ModuleConfig config, int subId) {
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
                return "FIVE_G_BASIC";
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
                return "FIVE_G_CA";
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
                return "FIVE_G_A";
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return "NR_5G_PLUS";
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_OFFLINE:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_2G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_3G:
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_4G:
                return "";
            default:
                return "";
        }
    }

    private static Object resolveTelephonyDebugFlymeFiveGIcon(ModuleConfig config,
                                                              int subId,
                                                              ClassLoader loader) {
        if (loader == null || !isTelephonyDebugEnabled(config)) {
            return null;
        }
        switch (resolveTelephonyDebugNetworkProfile(config, subId)) {
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G:
                return ReflectUtils.getStaticField(loader,
                        "com.android.settingslib.mobile.TelephonyIcons", "FIVE_G_BASIC");
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_CA:
                return ReflectUtils.getStaticField(loader,
                        "com.android.settingslib.mobile.TelephonyIcons", "FIVE_G_CA");
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5GA:
                return ReflectUtils.getStaticField(loader,
                        "com.android.settingslib.mobile.TelephonyIcons", "FIVE_G_A");
            case SettingsStore.TELEPHONY_DEBUG_NETWORK_PROFILE_5G_PLUS:
                return ReflectUtils.getStaticField(loader,
                        "com.android.settingslib.mobile.TelephonyIcons", "NR_5G_PLUS");
            default:
                return null;
        }
    }

    private static MobileTypeSubState buildTelephonyDebugMobileTypeSubState(ModuleConfig config, int subId) {
        int networkType = resolveTelephonyDebugNetworkType(config, subId);
        if (networkType == Integer.MIN_VALUE) {
            return null;
        }
        MobileTypeSubState state = new MobileTypeSubState();
        state.networkType = networkType;
        state.overrideNetworkType = resolveTelephonyDebugOverrideNetworkType(config, subId);
        state.nrState = resolveTelephonyDebugNrState(config, subId);
        Boolean nrCaState = resolveTelephonyDebugNrCaState(config, subId);
        state.nrCaState = nrCaState == null ? "" : String.valueOf(nrCaState);
        state.flymeIconGroup = resolveTelephonyDebugFlymeIconGroupLabel(config, subId);
        return state;
    }

    private static int resolveDefaultDataSubscriptionId() {
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        if (isTelephonyDebugEnabled(config)) {
            return resolveTelephonyDebugDefaultDataSubId(config);
        }
        try {
            return SubscriptionManager.getDefaultDataSubscriptionId();
        } catch (Throwable ignored) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    private static MobileTypeSubState resolveObservedMobileTypeSubState(
            TelephonyDisplayInfo displayInfo, TelephonyDisplayInfoState state) {
        int subId = state == null
                ? SubscriptionManager.INVALID_SUBSCRIPTION_ID
                : state.subId;
        if (!SubscriptionManager.isValidSubscriptionId(subId) && displayInfo != null) {
            TelephonyDisplayInfoState mappedState = TELEPHONY_DISPLAY_INFO_STATES.get(displayInfo);
            if (mappedState != null) {
                subId = mappedState.subId;
            }
        }
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return rememberMobileTypeSubState(subId);
        }
        return null;
    }

    private static boolean isUsingWifiOnlyInternet(Context context) {
        if (context == null) {
            return false;
        }
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) {
                return false;
            }
            Network activeNetwork = manager.getActiveNetwork();
            if (activeNetwork == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
            if (capabilities == null) {
                return false;
            }
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TelephonyManager getTelephonyManagerForSub(Context context, int subId) {
        if (context == null) {
            return null;
        }
        TelephonyManager baseManager = context.getSystemService(TelephonyManager.class);
        if (baseManager == null) {
            return null;
        }
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return baseManager;
        }
        try {
            TelephonyManager perSubManager = baseManager.createForSubscriptionId(subId);
            return perSubManager != null ? perSubManager : baseManager;
        } catch (Throwable ignored) {
            return baseManager;
        }
    }

    private static MobileTypeSubState queryLiveMobileTypeSubState(Context context, int subId) {
        beginInternalMobileTypeQuery();
        try {
            return snapshotLiveMobileTypeSubState(context, subId);
        } finally {
            endInternalMobileTypeQuery();
        }
    }

    private static void beginInternalMobileTypeQuery() {
        INTERNAL_MOBILE_TYPE_QUERY_DEPTH.set(INTERNAL_MOBILE_TYPE_QUERY_DEPTH.get() + 1);
    }

    private static void endInternalMobileTypeQuery() {
        int depth = INTERNAL_MOBILE_TYPE_QUERY_DEPTH.get();
        if (depth <= 1) {
            INTERNAL_MOBILE_TYPE_QUERY_DEPTH.remove();
        } else {
            INTERNAL_MOBILE_TYPE_QUERY_DEPTH.set(depth - 1);
        }
    }

    private static boolean isInternalMobileTypeQueryActive() {
        return INTERNAL_MOBILE_TYPE_QUERY_DEPTH.get() > 0;
    }

    private static MobileTypeSubState snapshotLiveMobileTypeSubState(Context context, int subId) {
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        MobileTypeSubState debugState = buildTelephonyDebugMobileTypeSubState(config, subId);
        if (debugState != null) {
            return debugState;
        }
        TelephonyManager manager = getTelephonyManagerForSub(context, subId);
        if (manager == null) {
            return null;
        }
        MobileTypeSubState state = new MobileTypeSubState();
        try {
            state.networkType = manager.getDataNetworkType();
        } catch (Throwable ignored) {
        }
        ServiceState serviceState = null;
        try {
            serviceState = manager.getServiceState();
        } catch (Throwable ignored) {
        }
        if (serviceState != null) {
            state.nrState = ReflectUtils.invokeNoArgInt(serviceState, "getNrState", Integer.MIN_VALUE);
            state.nrCaState = normalizeMobileIconGroupLabel(
                    safeToString(ReflectUtils.invokeNoArg(serviceState, "getNrCaState")));
        }
        return hasMeaningfulMobileTypeSubState(state) ? state : null;
    }

    private static MobileTypeSubState snapshotResolvedMobileTypeSubState(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return null;
        }
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        MobileTypeSubState debugState = buildTelephonyDebugMobileTypeSubState(config, subId);
        if (debugState != null) {
            return debugState;
        }
        MobileTypeSubState cachedState = snapshotMobileTypeSubState(subId);
        if (cachedState != null) {
            return cachedState;
        }
        return snapshotFallbackMobileTypeSubState(subId);
    }

    private static MobileTypeSubState snapshotFallbackMobileTypeSubState(int subId) {
        if (subId == LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID
                || subId == LAST_SERVICE_STATE_SUB_ID
                || subId == LAST_SIGNAL_SUB_ID) {
            MobileTypeSubState fallback = new MobileTypeSubState();
            fallback.networkType = LAST_MOBILE_TYPE_NETWORK_TYPE;
            fallback.overrideNetworkType = LAST_MOBILE_TYPE_OVERRIDE_NETWORK_TYPE;
            fallback.nrState = LAST_MOBILE_TYPE_NR_STATE;
            fallback.nrCaState = LAST_MOBILE_TYPE_NR_CA_STATE;
            fallback.flymeIconGroup = LAST_MOBILE_TYPE_FLYME_ICON_GROUP;
            if (hasMeaningfulMobileTypeSubState(fallback)) {
                return fallback;
            }
        }
        return null;
    }

    private static void syncMobileTypeSubStateFromLiveTelephony(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        MobileTypeSubState liveState = queryLiveMobileTypeSubState(context, subId);
        MobileTypeSubState cachedState = rememberMobileTypeSubState(subId);
        if (cachedState == null) {
            return;
        }
        cachedState.networkType = liveState == null
                ? Integer.MIN_VALUE
                : liveState.networkType;
        cachedState.overrideNetworkType = liveState == null
                ? Integer.MIN_VALUE
                : liveState.overrideNetworkType;
        cachedState.nrState = liveState == null
                ? Integer.MIN_VALUE
                : liveState.nrState;
        cachedState.nrCaState = liveState == null
                ? ""
                : nonNullText(liveState.nrCaState);
        if (liveState == null
                || resolveSignalMobileTypeBadgeFromTelephonyState(
                cachedState.networkType,
                cachedState.overrideNetworkType,
                cachedState.nrState,
                cachedState.nrCaState) == SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE) {
            cachedState.flymeIconGroup = "";
        }
    }

    private static boolean shouldRefreshMobileTypeForSubId(int subId) {
        return SubscriptionManager.isValidSubscriptionId(subId)
                && subId == resolveDefaultDataSubscriptionId();
    }

    private static void scheduleTrackedSignalIconRefreshForMobileTypeSubId(int subId) {
        if (shouldRefreshMobileTypeForSubId(subId)) {
            scheduleTrackedPrimarySignalIconRefresh();
        }
    }

    private static MobileTypeSubState mergeMobileTypeSubStates(MobileTypeSubState baseState,
                                                               MobileTypeSubState overrideState) {
        if (baseState == null) {
            return overrideState;
        }
        if (overrideState == null) {
            return baseState;
        }
        MobileTypeSubState merged = new MobileTypeSubState();
        merged.networkType = overrideState.networkType != Integer.MIN_VALUE
                ? overrideState.networkType
                : baseState.networkType;
        merged.overrideNetworkType = overrideState.overrideNetworkType != Integer.MIN_VALUE
                ? overrideState.overrideNetworkType
                : baseState.overrideNetworkType;
        merged.nrState = overrideState.nrState != Integer.MIN_VALUE
                ? overrideState.nrState
                : baseState.nrState;
        merged.nrCaState = !TextUtils.isEmpty(overrideState.nrCaState)
                ? overrideState.nrCaState
                : baseState.nrCaState;
        merged.flymeIconGroup = !TextUtils.isEmpty(overrideState.flymeIconGroup)
                ? overrideState.flymeIconGroup
                : baseState.flymeIconGroup;
        return hasMeaningfulMobileTypeSubState(merged) ? merged : null;
    }

    private static int resolveTintListColor(ColorStateList tintList, int[] state, int fallbackColor) {
        if (tintList == null) {
            return fallbackColor;
        }
        return tintList.getColorForState(state == null ? StateSet.NOTHING : state, tintList.getDefaultColor());
    }

    private static View findBestBatteryTintSource(View anchor) {
        if (anchor == null) {
            return null;
        }
        View anchorRoot = anchor.getRootView();
        if (anchorRoot == null) {
            return null;
        }
        View cached = resolveCachedSignalTintSource(anchor, anchorRoot);
        if (cached != null) {
            return cached;
        }
        View best = null;
        int bestScore = Integer.MAX_VALUE;
        int[] anchorLocation = getViewLocation(anchor);
        ArrayList<View> batteryViews = new ArrayList<>(TRACKED_BATTERY_VIEWS.keySet());
        for (View batteryView : batteryViews) {
            if (!isBatteryTintSourceCandidate(anchor, anchorRoot, batteryView)) {
                continue;
            }
            int score = calculateViewDistanceScore(anchorLocation, batteryView);
            if (best == null || score < bestScore) {
                best = batteryView;
                bestScore = score;
            }
        }
        rememberSignalTintSource(anchor, best);
        return best;
    }

    private static boolean isBatteryTintSourceCandidate(View anchor, View anchorRoot, View batteryView) {
        if (anchor == null || anchorRoot == null || batteryView == null) {
            return false;
        }
        if (batteryView.getVisibility() != View.VISIBLE || !batteryView.isShown()) {
            return false;
        }
        if (batteryView.getWindowToken() == null) {
            return false;
        }
        return batteryView.getRootView() == anchorRoot;
    }

    private static int calculateViewDistanceScore(int[] anchorLocation, View target) {
        int[] targetLocation = getViewLocation(target);
        int dx = Math.abs(anchorLocation[0] - targetLocation[0]);
        int dy = Math.abs(anchorLocation[1] - targetLocation[1]);
        return dy * 1000 + dx;
    }

    private static int[] getViewLocation(View view) {
        int[] location = new int[]{0, 0};
        if (view == null) {
            return location;
        }
        try {
            view.getLocationOnScreen(location);
        } catch (Throwable ignored) {
        }
        return location;
    }

    private static View resolveCachedSignalTintSource(View anchor, View anchorRoot) {
        if (anchor == null || anchorRoot == null) {
            return null;
        }
        WeakReference<View> reference = SIGNAL_TINT_SOURCE_CACHE.get(anchor);
        View cached = reference == null ? null : reference.get();
        if (!isBatteryTintSourceCandidate(anchor, anchorRoot, cached)) {
            SIGNAL_TINT_SOURCE_CACHE.remove(anchor);
            return null;
        }
        return cached;
    }

    private static void rememberSignalTintSource(View anchor, View batteryView) {
        if (anchor == null) {
            return;
        }
        if (batteryView == null) {
            SIGNAL_TINT_SOURCE_CACHE.remove(anchor);
            return;
        }
        SIGNAL_TINT_SOURCE_CACHE.put(anchor, new WeakReference<>(batteryView));
    }

    private static void clearSignalTintSourceCache() {
        SIGNAL_TINT_SOURCE_CACHE.clear();
    }

    private static void clearSignalTintSourceCacheForView(View view) {
        if (view == null) {
            return;
        }
        SIGNAL_TINT_SOURCE_CACHE.remove(view);
    }

    private static void clearSignalTintSourceCacheForRoot(View rootView) {
        if (rootView == null) {
            clearSignalTintSourceCache();
            return;
        }
        ArrayList<View> signalViews = new ArrayList<>(SIGNAL_TINT_SOURCE_CACHE.keySet());
        for (View signalView : signalViews) {
            if (signalView == null || signalView.getRootView() == rootView) {
                SIGNAL_TINT_SOURCE_CACHE.remove(signalView);
            }
        }
    }

    private static boolean isTrackedBatteryDrawableOwner(Object drawable) {
        if (!(drawable instanceof Drawable)) {
            return false;
        }
        Drawable.Callback callback = ((Drawable) drawable).getCallback();
        return callback instanceof View && TRACKED_BATTERY_VIEWS.containsKey((View) callback);
    }

    private static void ensureBatteryViewRuntimeSnapshot(View batteryView, BatteryViewState state) {
        if (batteryView == null || state == null || state.hasRuntimeSnapshot) {
            return;
        }
        refreshBatteryViewRuntimeSnapshot(batteryView, state);
    }

    private static boolean refreshBatteryViewRuntimeSnapshot(View batteryView, BatteryViewState state) {
        if (batteryView == null || state == null) {
            return false;
        }
        int level = ReflectUtils.getIntField(batteryView, "mLastLevel", 0);
        boolean pluggedIn = ReflectUtils.getBooleanField(batteryView, "mLastPlugged", false);
        boolean charging = ReflectUtils.getBooleanField(batteryView, "mCharging", false);
        boolean quickCharging = resolveBatteryQuickCharging(batteryView);
        boolean showBolt = charging || pluggedIn;
        int tintColor = resolveBatteryTintColor(batteryView, Color.BLACK);
        int textColor = resolveBatteryTextColor(tintColor);
        boolean changed = !state.hasRuntimeSnapshot
                || state.level != level
                || state.pluggedIn != pluggedIn
                || state.charging != charging
                || state.quickCharging != quickCharging
                || state.showBolt != showBolt
                || state.tintColor != tintColor
                || state.textColor != textColor;
        state.level = level;
        state.pluggedIn = pluggedIn;
        state.charging = charging;
        state.quickCharging = quickCharging;
        state.showBolt = showBolt;
        state.tintColor = tintColor;
        state.textColor = textColor;
        state.hasRuntimeSnapshot = true;
        return changed;
    }

    private static void syncBatteryViewAfterStateChange(View batteryView) {
        if (batteryView == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(batteryView.getContext());
        if (!isBatteryCodeDrawEnabled(config)) {
            return;
        }
        BatteryViewState state = rememberBatteryViewState(batteryView);
        boolean hadSnapshot = state.hasRuntimeSnapshot;
        boolean previousShowBolt = state.showBolt;
        boolean snapshotChanged = refreshBatteryViewRuntimeSnapshot(batteryView, state);
        boolean layoutRelevantChanged = !hadSnapshot || previousShowBolt != state.showBolt;
        boolean layoutChanged = layoutRelevantChanged && syncBatteryViewLayoutIfNeeded(batteryView, config, false);
        if (snapshotChanged || layoutChanged) {
            batteryView.invalidate();
        }
    }

    private static void syncBatteryViewAfterConfigurationChanged(View batteryView) {
        if (batteryView == null) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(batteryView.getContext());
        clearSignalTintSourceCacheForRoot(batteryView.getRootView());
        if (isBatteryCodeDrawEnabled(config)) {
            refreshBatteryViewRuntimeSnapshot(batteryView, rememberBatteryViewState(batteryView));
            syncBatteryViewLayoutIfNeeded(batteryView, config, true);
            batteryView.invalidate();
            return;
        }
        syncBatteryViewLayoutIfNeeded(batteryView, config, true);
    }

    private static BatteryViewState rememberBatteryViewState(View view) {
        if (view == null) {
            return new BatteryViewState();
        }
        BatteryViewState state = BATTERY_VIEW_STATES.get(view);
        if (state != null) {
            return state;
        }
        state = new BatteryViewState();
        BATTERY_VIEW_STATES.put(view, state);
        return state;
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
        BatteryViewState state = rememberBatteryViewState(batteryView);
        ensureBatteryViewRuntimeSnapshot(batteryView, state);
        ReflectUtils.setMeasuredDimension(batteryView,
                iosBatteryMeasuredWidthWithMergedIcons(batteryView, config, state.showBolt),
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

    private static boolean syncBatteryViewLayoutIfNeeded(View view, ModuleConfig config, boolean force) {
        if (view == null) {
            return false;
        }
        BatteryViewState state = rememberBatteryViewState(view);
        captureOriginalBatteryViewLayoutIfNeeded(view, state);
        boolean codeDrawEnabled = isBatteryCodeDrawEnabled(config);
        boolean changed = false;
        if (!codeDrawEnabled) {
            if (restoreOriginalBatteryViewLayoutIfNeeded(view, state)) {
                changed = true;
            }
            state.codeDrawEnabled = false;
            state.hasLayoutSignature = false;
            return changed;
        }
        disableAncestorClipping(view, 6);
        ensureBatteryViewRuntimeSnapshot(view, state);
        boolean showBolt = state.showBolt;
        int width = iosBatteryMeasuredWidthWithMergedIcons(view, config, showBolt);
        int height = iosBatteryMeasuredHeightWithMergedIcons(view, config);
        long layoutSignature = getBatteryLayoutSignature(config, showBolt, width, height);
        if (force || !state.hasLayoutSignature || state.layoutSignature != layoutSignature) {
            boolean hasLayoutParams = view.getLayoutParams() != null;
            if (resizeIosBatteryView(view, width, height)) {
                changed = true;
            }
            if (hasLayoutParams) {
                state.layoutSignature = layoutSignature;
                state.hasLayoutSignature = true;
            } else {
                state.hasLayoutSignature = false;
            }
        }
        state.codeDrawEnabled = true;
        return changed;
    }

    private static void captureOriginalBatteryViewLayoutIfNeeded(View view, BatteryViewState state) {
        if (view == null || state == null || state.originalLayoutCaptured) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        state.originalLayoutWidth = lp.width;
        state.originalLayoutHeight = lp.height;
        state.originalLayoutCaptured = true;
    }

    private static boolean restoreOriginalBatteryViewLayoutIfNeeded(View view, BatteryViewState state) {
        if (view == null || state == null || !state.originalLayoutCaptured) {
            return false;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return false;
        }
        boolean changed = false;
        if (lp.width != state.originalLayoutWidth) {
            lp.width = state.originalLayoutWidth;
            changed = true;
        }
        if (lp.height != state.originalLayoutHeight) {
            lp.height = state.originalLayoutHeight;
            changed = true;
        }
        if (changed) {
            view.setLayoutParams(lp);
            view.requestLayout();
        }
        return changed;
    }

    private static boolean resizeIosBatteryView(View view, int width, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return false;
        }
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
        return changed;
    }

    private static int iosBatteryMeasuredWidth(View view, ModuleConfig config, boolean showBolt) {
        return resolveBatteryRenderWidth(resolveBatterySquareSize(view, config), config, showBolt);
    }

    private static int iosBatteryMeasuredHeight(View view, ModuleConfig config) {
        return resolveBatteryRenderHeight(resolveBatterySquareSize(view, config)) + dp(view, 2);
    }

    private static int iosBatteryMeasuredWidthWithMergedIcons(View view, ModuleConfig config, boolean showBolt) {
        return iosBatteryMeasuredWidth(view, config, showBolt);
    }

    private static int iosBatteryMeasuredHeightWithMergedIcons(View view, ModuleConfig config) {
        return iosBatteryMeasuredHeight(view, config);
    }

    private static long getBatteryLayoutSignature(ModuleConfig config, boolean showBolt, int width, int height) {
        long signature = 17L;
        signature = signature * 31L + (isBatteryCodeDrawEnabled(config) ? 1L : 0L);
        signature = signature * 31L + resolveBatteryStyle(config);
        signature = signature * 31L + SettingsStore.normalizeScalePercent(config == null
                ? SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT
                : config.statusBarIconScalePercent);
        signature = signature * 31L + SettingsStore.normalizeScalePercent(config == null
                ? SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT
                : config.batteryInnerTextScalePercent);
        signature = signature * 31L + (showBolt ? 1L : 0L);
        signature = signature * 31L + width;
        signature = signature * 31L + height;
        return signature;
    }

    private static long getBatteryRenderConfigSignature(ModuleConfig config) {
        long signature = 17L;
        signature = signature * 31L + (isBatteryCodeDrawEnabled(config) ? 1L : 0L);
        signature = signature * 31L + resolveBatteryStyle(config);
        signature = signature * 31L + SettingsStore.normalizeScalePercent(config == null
                ? SettingsStore.DEFAULT_STATUS_BAR_ICON_SCALE_PERCENT
                : config.statusBarIconScalePercent);
        signature = signature * 31L + SettingsStore.normalizeScalePercent(config == null
                ? SettingsStore.DEFAULT_BATTERY_INNER_TEXT_SCALE_PERCENT
                : config.batteryInnerTextScalePercent);
        signature = signature * 31L + (config != null && config.batteryLevelTextEnabled ? 1L : 0L);
        signature = signature * 31L + (config != null && config.batteryHollowEnabled ? 1L : 0L);
        signature = signature * 31L + (config != null && config.batteryHollowFillFollowsLevel ? 1L : 0L);
        signature = signature * 31L + resolveBatteryTextFontOption(config);
        return signature;
    }

    private static int resolveBatterySquareSize(View batteryView, ModuleConfig config) {
        return scaleSize(dp(batteryView, 24), resolveStatusBarIconScale(config));
    }

    private static int resolveBatteryRenderHeight(int size) {
        return size;
    }

    private static int resolveBatteryRenderWidth(int size, ModuleConfig config, boolean showBolt) {
        int style = resolveBatteryStyle(config);
        int width = style == SettingsStore.BATTERY_STYLE_ONEUI
                ? OneUiBatteryPainter.getRequiredWidth(size, showBolt)
                : IosBatteryPainter.getRequiredWidth(size, showBolt);
        return width;
    }

    private static int resolveBatteryStyle(ModuleConfig config) {
        return config == null
                ? SettingsStore.DEFAULT_BATTERY_ICON_STYLE
                : SettingsStore.normalizeBatteryStyle(config.batteryIconStyle);
    }

    private static int resolveBatteryTextFontOption(ModuleConfig config) {
        return config == null
                ? SettingsStore.DEFAULT_BATTERY_TEXT_FONT
                : SettingsStore.normalizeBatteryTextFont(config.batteryTextFont);
    }

    private static boolean shouldShowBatteryBolt(Object target) {
        if (target == null) {
            return false;
        }
        return ReflectUtils.getBooleanField(target, "mCharging", false)
                || ReflectUtils.getBooleanField(target, "mLastPlugged", false)
                || ReflectUtils.getBooleanField(target, "mPluggedIn", false);
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

    private static void trackConnectionRateView(View view) {
        if (view == null) {
            return;
        }
        rememberConnectionRateViewState(view);
    }

    private static void rememberConnectionRateBaseState(View view, boolean overwrite) {
        rememberConnectionRateBaseState(view, resolveConnectionRateUnitView(view),
                rememberConnectionRateViewState(view), overwrite);
    }

    private static void rememberConnectionRateBaseState(
            View view, TextView unitView, ConnectionRateViewState state, boolean overwrite) {
        if (view == null) {
            return;
        }
        if (state == null) {
            return;
        }
        int textSize = ReflectUtils.getIntField(view, "mTextSize", 0);
        if (textSize > 0 && (overwrite || state.originalTextSize <= 0)) {
            state.originalTextSize = textSize;
        }
        int maxWidth = ReflectUtils.getIntField(view, "mMaxWidth", 0);
        if (maxWidth > 0 && (overwrite || state.originalMaxWidth <= 0)) {
            state.originalMaxWidth = maxWidth;
        }
        if (overwrite || Float.isNaN(state.originalRotation)) {
            state.originalRotation = view.getRotation();
        }
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            if (overwrite || state.originalOrientation == Integer.MIN_VALUE) {
                state.originalOrientation = group.getOrientation();
            }
        }
        if (unitView == null) {
            if (overwrite) {
                state.hasAppliedTextScaleSignature = false;
            }
            return;
        }
        if (overwrite || Float.isNaN(state.originalUnitTextSize)) {
            state.originalUnitTextSize = unitView.getTextSize();
        }
        if (overwrite || state.originalUnitMinWidth < 0) {
            state.originalUnitMinWidth = unitView.getMinWidth();
        }
        if (overwrite || state.originalUnitMaxLines == Integer.MIN_VALUE) {
            state.originalUnitMaxLines = unitView.getMaxLines();
        }
        if (overwrite || Float.isNaN(state.originalUnitRotation)) {
            state.originalUnitRotation = unitView.getRotation();
        }
        if (overwrite || state.originalUnitPadding == null) {
            state.originalUnitPadding = new int[]{
                    unitView.getPaddingLeft(),
                    unitView.getPaddingTop(),
                    unitView.getPaddingRight(),
                    unitView.getPaddingBottom()
            };
        }
        if (overwrite) {
            state.hasAppliedTextScaleSignature = false;
        }
    }

    private static TextView resolveConnectionRateUnitView(View view) {
        if (view == null) {
            return null;
        }
        Object unit = ReflectUtils.getField(view, "mUnitView");
        if (unit instanceof TextView) {
            return (TextView) unit;
        }
        if (view instanceof ViewGroup) {
            View child = findSystemUiChild(view, "unit");
            if (child instanceof TextView) {
                return (TextView) child;
            }
        }
        return null;
    }

    private static boolean applyConnectionRateTextScale(View view) {
        if (view == null) {
            return false;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        TextView unitView = resolveConnectionRateUnitView(view);
        ConnectionRateViewState state = rememberConnectionRateViewState(view);
        long signature = getConnectionRateTextScaleSignature(config, unitView != null);
        if (state.hasAppliedTextScaleSignature && state.appliedTextScaleSignature == signature) {
            return false;
        }
        rememberConnectionRateBaseState(view, unitView, state, false);
        float scale = config != null && config.enabled ? resolveClockAndCarrierTextScale(config) : 1f;
        boolean changed = false;

        if (state.originalTextSize > 0) {
            int targetTextSize = scaleSize(state.originalTextSize, scale);
            int currentTextSize = ReflectUtils.getIntField(view, "mTextSize", 0);
            if (currentTextSize != targetTextSize) {
                ReflectUtils.setIntField(view, "mTextSize", targetTextSize);
                changed = true;
            }
        }

        if (state.originalMaxWidth > 0) {
            int targetMaxWidth = scaleSize(state.originalMaxWidth, scale);
            int currentMaxWidth = ReflectUtils.getIntField(view, "mMaxWidth", 0);
            if (currentMaxWidth != targetMaxWidth) {
                ReflectUtils.setIntField(view, "mMaxWidth", targetMaxWidth);
                changed = true;
            }
        }

        if (applyConnectionRateHorizontalState(view, state, config.enabled)) {
            changed = true;
        }

        if (unitView != null) {
            if (!Float.isNaN(state.originalUnitTextSize) && state.originalUnitTextSize > 0f) {
                float targetUnitTextSize = state.originalUnitTextSize * scale;
                if (Math.abs(unitView.getTextSize() - targetUnitTextSize) > 0.5f) {
                    unitView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, targetUnitTextSize);
                    changed = true;
                }
            }

            if (state.originalUnitMinWidth > 0) {
                int targetMinWidth = scaleSize(state.originalUnitMinWidth, scale);
                if (unitView.getMinWidth() != targetMinWidth) {
                    unitView.setMinWidth(targetMinWidth);
                    changed = true;
                }
            }

            int[] originalPadding = state.originalUnitPadding;
            if (originalPadding != null) {
                int left = scaleInsetSize(originalPadding[0], scale);
                int top = scaleInsetSize(originalPadding[1], scale);
                int right = scaleInsetSize(originalPadding[2], scale);
                int bottom = scaleInsetSize(originalPadding[3], scale);
                if (unitView.getPaddingLeft() != left || unitView.getPaddingTop() != top
                        || unitView.getPaddingRight() != right || unitView.getPaddingBottom() != bottom) {
                    unitView.setPadding(left, top, right, bottom);
                    changed = true;
                }
            }

            if (applyConnectionRateUnitHorizontalState(unitView, state, config.enabled)) {
                changed = true;
            }
        }

        if (changed) {
            disableAncestorClipping(view, 4);
            if (unitView != null) {
                unitView.requestLayout();
                unitView.invalidate();
            }
            view.requestLayout();
            view.invalidate();
        }
        state.appliedTextScaleSignature = signature;
        state.hasAppliedTextScaleSignature = true;
        return changed;
    }

    private static long getConnectionRateTextScaleSignature(ModuleConfig config, boolean hasUnitView) {
        boolean enabled = config != null && config.enabled;
        int effectivePercent = enabled
                ? SettingsStore.normalizeScalePercent(config.clockAndCarrierTextSizePercent)
                : SettingsStore.DEFAULT_CLOCK_AND_CARRIER_TEXT_SIZE_PERCENT;
        long signature = 17L;
        signature = signature * 31L + (enabled ? 1L : 0L);
        signature = signature * 31L + effectivePercent;
        signature = signature * 31L + (hasUnitView ? 1L : 0L);
        return signature;
    }

    private static boolean applyConnectionRateHorizontalState(View view, ConnectionRateViewState state, boolean enabled) {
        if (view == null) {
            return false;
        }
        if (state == null) {
            return false;
        }
        boolean changed = false;
        float targetRotation = enabled
                ? 0f
                : (Float.isNaN(state.originalRotation) ? view.getRotation() : state.originalRotation);
        if (Math.abs(view.getRotation() - targetRotation) > 0.5f) {
            view.setRotation(targetRotation);
            changed = true;
        }
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            int targetOrientation = enabled
                    ? LinearLayout.HORIZONTAL
                    : (state.originalOrientation == Integer.MIN_VALUE
                    ? group.getOrientation() : state.originalOrientation);
            if (group.getOrientation() != targetOrientation) {
                group.setOrientation(targetOrientation);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean applyConnectionRateUnitHorizontalState(
            TextView unitView, ConnectionRateViewState state, boolean enabled) {
        if (unitView == null) {
            return false;
        }
        if (state == null) {
            return false;
        }
        boolean changed = false;
        float targetRotation = enabled
                ? 0f
                : (Float.isNaN(state.originalUnitRotation) ? unitView.getRotation() : state.originalUnitRotation);
        if (Math.abs(unitView.getRotation() - targetRotation) > 0.5f) {
            unitView.setRotation(targetRotation);
            changed = true;
        }

        int targetMaxLines = enabled ? 1
                : (state.originalUnitMaxLines == Integer.MIN_VALUE ? unitView.getMaxLines() : state.originalUnitMaxLines);
        if (enabled) {
            if (unitView.getMaxLines() != 1) {
                unitView.setSingleLine(true);
                changed = true;
            }
        } else if (state.originalUnitMaxLines != Integer.MIN_VALUE) {
            if (targetMaxLines == 1) {
                if (unitView.getMaxLines() != 1) {
                    unitView.setSingleLine(true);
                    changed = true;
                }
            } else {
                if (unitView.getMaxLines() == 1) {
                    unitView.setSingleLine(false);
                    changed = true;
                }
                if (unitView.getMaxLines() != targetMaxLines) {
                    unitView.setMaxLines(targetMaxLines);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static void trackStatusBarIconView(View view) {
        if (view == null) {
            return;
        }
        trackSingleStatusBarIconView(view);
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View ancestor = (View) parent;
            if (isStatusBarContainerView(ancestor)) {
                trackSingleStatusBarIconView(ancestor);
            }
            parent = ancestor.getParent();
        }
    }

    private static void trackSingleStatusBarIconView(View view) {
        if (!shouldTrackStatusBarIconView(view)) {
            return;
        }
        boolean alreadyTracked = TRACKED_STATUS_BAR_ICON_VIEWS.containsKey(view);
        TRACKED_STATUS_BAR_ICON_VIEWS.put(view, Boolean.TRUE);
        ensureConfigRefreshObserver(view.getContext());
        if (alreadyTracked) {
            return;
        }
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                return;
            }
            v.post(() -> onSignalViewLayoutChanged(v));
        });
    }

    private static boolean shouldTrackStatusBarIconView(View view) {
        if (view == null) {
            return false;
        }
        if (isPrivacyChipView(view) || isNotificationBackedStatusBarIconView(view)) {
            return true;
        }
        String className = view.getClass().getName();
        if ("com.android.systemui.statusbar.StatusBarIconView".equals(className)
                || "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView".equals(className)) {
            return true;
        }
        String idName = getSystemUiIdName(view);
        return "mobile_signal".equals(idName)
                || isStandaloneStatusBarImageView(view)
                || isStatusBarContainerView(view);
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

    private static void onSignalImageResourceAssigned(ImageView view, int resId) {
        onSignalImageAssigned(view, SIGNAL_IMAGE_ASSIGNMENT_RESOURCE, resId, null, null);
    }

    private static void onSignalImageIconAssigned(ImageView view, Icon icon) {
        onSignalImageAssigned(view, SIGNAL_IMAGE_ASSIGNMENT_ICON, 0, icon, null);
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
        onSignalImageAssigned(view, SIGNAL_IMAGE_ASSIGNMENT_DRAWABLE, 0, null, drawable);
    }

    private static void onSignalImageAssigned(ImageView view, int assignmentType, int resId,
                                              Icon icon, Drawable drawable) {
        trackStatusBarIconView(view);
        if (assignmentType == SIGNAL_IMAGE_ASSIGNMENT_DRAWABLE
                && isSignalDrawableApplyGuardActive(view)) {
            return;
        }
        String idName = getSystemUiIdName(view);
        if ("mobile_type".equals(idName)) {
            recordMobileTypeImageAssignment(view, assignmentType, resId, icon, drawable);
        }
        if (!isMobileSignalRelatedId(idName)) {
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            if ("mobile_type".equals(idName)) {
                setMobileTypeVisibility(view, true);
            }
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        if ("mobile_type".equals(idName)) {
            setMobileTypeVisibility(view, false);
            refreshLinkedSignalViews(view);
            return;
        }
        if ("mobile_signal".equals(idName) && drawable != null) {
            SIGNAL_DRAWABLE_OWNERS.put(drawable, view);
        }
        if ("mobile_signal".equals(idName)) {
            bindSignalViewState(view);
            applySignalIconOverride(view);
            return;
        }
        applyStatusBarScaleIfNeeded(view);
    }

    private static void recordMobileTypeImageAssignment(ImageView view, int assignmentType, int resId,
                                                        Icon icon, Drawable drawable) {
        switch (assignmentType) {
            case SIGNAL_IMAGE_ASSIGNMENT_RESOURCE:
                recordMobileTypeResourceAssignment(view, resId);
                return;
            case SIGNAL_IMAGE_ASSIGNMENT_ICON:
                recordMobileTypeIconAssignment(view, icon);
                return;
            case SIGNAL_IMAGE_ASSIGNMENT_DRAWABLE:
                recordMobileTypeDrawableAssignment(view, drawable);
                return;
            default:
                return;
        }
    }

    private static void onSignalViewLayoutChanged(View view) {
        trackStatusBarIconView(view);
        String idName = getSystemUiIdName(view);
        if ("mobile_signal".equals(idName)) {
            clearSignalTintSourceCacheForView(view);
        }
        if (!isMobileSignalRelatedId(idName)) {
            if (isStatusBarIconCandidate(view)) {
                applyStatusBarScaleIfNeeded(view);
            }
            return;
        }
        ModuleConfig config = ModuleConfig.load(view.getContext());
        if (!isSignalCodeDrawEnabled(config)) {
            if ("mobile_type".equals(idName)) {
                setMobileTypeVisibility(view, true);
            }
            applyStatusBarScaleIfNeeded(view);
            return;
        }
        if ("mobile_type".equals(idName)) {
            setMobileTypeVisibility(view, false);
            refreshLinkedSignalViews(view);
            return;
        }
        if ("mobile_signal".equals(idName) && view instanceof ImageView) {
            bindSignalViewState(view);
            applySignalIconOverride((ImageView) view);
            return;
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
        if (!isSignalCodeDrawEnabled(config)
                && !isTelephonyDebugEnabled(config)) {
            return;
        }
        if (!isSignalCodeDrawEnabled(config)) {
            LAST_SIGNAL_LEVEL = normalizeSignalLevel(rawLevel);
        }
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

    private static TelephonyDisplayInfoState rememberTelephonyDisplayInfoState(TelephonyDisplayInfo displayInfo) {
        TelephonyDisplayInfoState state = TELEPHONY_DISPLAY_INFO_STATES.get(displayInfo);
        if (state == null) {
            state = new TelephonyDisplayInfoState();
            TELEPHONY_DISPLAY_INFO_STATES.put(displayInfo, state);
        }
        return state;
    }

    private static void bindTelephonyDisplayInfoToSubId(TelephonyDisplayInfo displayInfo, int subId) {
        if (displayInfo == null || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        subId = resolveEffectiveTelephonyDebugSubId(config, subId);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        TelephonyDisplayInfoState state = rememberTelephonyDisplayInfoState(displayInfo);
        state.subId = subId;
        LAST_MOBILE_TYPE_DISPLAY_INFO_SUB_ID = subId;
        MobileTypeSubState subState = rememberMobileTypeSubState(subId);
        if (subState != null) {
            if (state.networkType != Integer.MIN_VALUE) {
                subState.networkType = state.networkType;
            }
            if (state.overrideNetworkType != Integer.MIN_VALUE) {
                subState.overrideNetworkType = state.overrideNetworkType;
            }
        }
    }

    private static void primeTelephonyDisplayInfoState(TelephonyDisplayInfo displayInfo) {
        if (displayInfo == null) {
            return;
        }
        beginInternalMobileTypeQuery();
        try {
            try {
                displayInfo.getNetworkType();
            } catch (Throwable ignored) {
            }
            try {
                displayInfo.getOverrideNetworkType();
            } catch (Throwable ignored) {
            }
        } finally {
            endInternalMobileTypeQuery();
        }
    }

    private static int resolveTelephonyCallbackSubId(Object callback) {
        if (callback == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        int subId = resolveSubIdFromCarrierCallbackOwner(ReflectUtils.getField(callback, "$this_run"));
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        subId = resolveSubIdFromCarrierCallbackOwner(ReflectUtils.getField(callback, "this$0"));
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        return resolveSubIdFromCarrierCallbackOwner(callback);
    }

    private static int resolveSubIdFromCarrierCallbackOwner(Object owner) {
        if (owner == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        int subId = ReflectUtils.invokeNoArgInt(owner, "getSubId",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        subId = ReflectUtils.getIntField(owner, "subId", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        subId = ReflectUtils.getIntField(owner, "mSubId", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        subId = resolveSubIdFromTelephonyManager(ReflectUtils.getField(owner, "telephonyManager"));
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        subId = resolveSubIdFromTelephonyManager(ReflectUtils.getField(owner, "mPhone"));
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        Object subscriptionInfo = ReflectUtils.getField(owner, "mSubscriptionInfo");
        if (subscriptionInfo instanceof SubscriptionInfo) {
            return ((SubscriptionInfo) subscriptionInfo).getSubscriptionId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static int resolveSubIdFromTelephonyManager(Object object) {
        if (!(object instanceof TelephonyManager)) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        TelephonyManager manager = (TelephonyManager) object;
        Integer mappedSubId = TELEPHONY_MANAGER_SUB_IDS.get(manager);
        if (mappedSubId != null && SubscriptionManager.isValidSubscriptionId(mappedSubId)) {
            return mappedSubId;
        }
        try {
            return manager.getSubscriptionId();
        } catch (Throwable ignored) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    private static MobileTypeSubState rememberMobileTypeSubState(Integer subId) {
        if (subId == null || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return null;
        }
        synchronized (MOBILE_TYPE_SUB_STATES) {
            MobileTypeSubState state = MOBILE_TYPE_SUB_STATES.get(subId);
            if (state == null) {
                state = new MobileTypeSubState();
                MOBILE_TYPE_SUB_STATES.put(subId, state);
            }
            return state;
        }
    }

    private static MobileTypeSubState snapshotMobileTypeSubState(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return null;
        }
        synchronized (MOBILE_TYPE_SUB_STATES) {
            MobileTypeSubState state = MOBILE_TYPE_SUB_STATES.get(subId);
            if (state == null) {
                return null;
            }
            MobileTypeSubState snapshot = new MobileTypeSubState();
            snapshot.networkType = state.networkType;
            snapshot.overrideNetworkType = state.overrideNetworkType;
            snapshot.nrState = state.nrState;
            snapshot.nrCaState = state.nrCaState;
            snapshot.flymeIconGroup = state.flymeIconGroup;
            return snapshot;
        }
    }

    private static void recordMobileTypeResourceAssignment(ImageView view, int resId) {
        LAST_MOBILE_TYPE_RESOURCE_ID = resId;
        LAST_MOBILE_TYPE_RESOURCE_NAME = resolveResourceName(view == null ? null : view.getContext(), resId);
        LAST_MOBILE_TYPE_ICON_TYPE = "";
        LAST_MOBILE_TYPE_ICON_RESOURCE_ID = 0;
        LAST_MOBILE_TYPE_ICON_RESOURCE_PACKAGE = "";
        LAST_MOBILE_TYPE_ICON_RESOURCE_NAME = "";
        LAST_MOBILE_TYPE_DRAWABLE_CLASS = view == null || view.getDrawable() == null
                ? ""
                : view.getDrawable().getClass().getName();
        LAST_MOBILE_TYPE_VIEW_VISIBILITY = view == null ? View.VISIBLE : view.getVisibility();
    }

    private static void recordMobileTypeIconAssignment(ImageView view, Icon icon) {
        LAST_MOBILE_TYPE_RESOURCE_ID = 0;
        LAST_MOBILE_TYPE_RESOURCE_NAME = "";
        LAST_MOBILE_TYPE_ICON_TYPE = describeIconType(icon);
        LAST_MOBILE_TYPE_ICON_RESOURCE_PACKAGE = resolveIconResourcePackage(icon);
        LAST_MOBILE_TYPE_ICON_RESOURCE_ID = resolveIconResourceId(icon);
        LAST_MOBILE_TYPE_ICON_RESOURCE_NAME = resolveIconResourceName(view == null ? null : view.getContext(), icon);
        LAST_MOBILE_TYPE_DRAWABLE_CLASS = view == null || view.getDrawable() == null
                ? ""
                : view.getDrawable().getClass().getName();
        LAST_MOBILE_TYPE_VIEW_VISIBILITY = view == null ? View.VISIBLE : view.getVisibility();
    }

    private static void recordMobileTypeDrawableAssignment(ImageView view, Drawable drawable) {
        LAST_MOBILE_TYPE_DRAWABLE_CLASS = drawable == null ? "null" : drawable.getClass().getName();
        LAST_MOBILE_TYPE_VIEW_VISIBILITY = view == null ? View.VISIBLE : view.getVisibility();
    }

    private static String describeMobileIconGroup(Object iconGroup) {
        if (iconGroup == null) {
            return "null";
        }
        Object name = ReflectUtils.getField(iconGroup, "name");
        if (name != null) {
            return String.valueOf(name);
        }
        return safeToString(iconGroup);
    }

    private static String normalizeMobileIconGroupLabel(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String normalized = value.trim();
        if (TextUtils.isEmpty(normalized)
                || "null".equalsIgnoreCase(normalized)
                || "unknown".equalsIgnoreCase(normalized)
                || "-".equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String resolveResourceName(Context context, int resId) {
        if (resId == 0) {
            return "0";
        }
        if (context == null) {
            return "0x" + Integer.toHexString(resId);
        }
        try {
            return context.getResources().getResourceName(resId);
        } catch (Throwable ignored) {
            return "0x" + Integer.toHexString(resId);
        }
    }

    private static String resolveIconResourceName(Context context, Icon icon) {
        if (icon == null) {
            return "";
        }
        try {
            if (icon.getType() != Icon.TYPE_RESOURCE) {
                return describeIconType(icon);
            }
            int resId = resolveIconResourceId(icon);
            String packageName = resolveIconResourcePackage(icon);
            if (resId == 0) {
                return "0";
            }
            if (context == null || TextUtils.isEmpty(packageName)
                    || packageName.equals(context.getPackageName())) {
                return resolveResourceName(context, resId);
            }
            PackageManager packageManager = context.getPackageManager();
            Resources resources = packageManager == null ? null : packageManager.getResourcesForApplication(packageName);
            if (resources != null) {
                return resources.getResourceName(resId);
            }
        } catch (Throwable ignored) {
        }
        return "0x" + Integer.toHexString(resolveIconResourceId(icon));
    }

    private static int resolveIconResourceId(Icon icon) {
        if (icon == null) {
            return 0;
        }
        try {
            return icon.getType() == Icon.TYPE_RESOURCE ? icon.getResId() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String resolveIconResourcePackage(Icon icon) {
        if (icon == null) {
            return "";
        }
        try {
            return icon.getType() == Icon.TYPE_RESOURCE ? safeToString(icon.getResPackage()) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String describeIconType(Icon icon) {
        if (icon == null) {
            return "null";
        }
        switch (icon.getType()) {
            case Icon.TYPE_BITMAP:
                return "BITMAP";
            case Icon.TYPE_RESOURCE:
                return "RESOURCE";
            case Icon.TYPE_DATA:
                return "DATA";
            case Icon.TYPE_URI:
                return "URI";
            case Icon.TYPE_ADAPTIVE_BITMAP:
                return "ADAPTIVE_BITMAP";
            case Icon.TYPE_URI_ADAPTIVE_BITMAP:
                return "URI_ADAPTIVE_BITMAP";
            default:
                return "type=" + icon.getType();
        }
    }

    private static String describeVisibility(int visibility) {
        switch (visibility) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return String.valueOf(visibility);
        }
    }

    private static String describeNetworkType(int networkType) {
        if (networkType == Integer.MIN_VALUE) {
            return "unknown";
        }
        String label;
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                label = "UNKNOWN";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
                label = "GPRS";
                break;
            case TelephonyManager.NETWORK_TYPE_EDGE:
                label = "EDGE";
                break;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                label = "UMTS";
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
                label = "CDMA";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                label = "EVDO_0";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                label = "EVDO_A";
                break;
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                label = "1xRTT";
                break;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                label = "HSDPA";
                break;
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                label = "HSUPA";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPA:
                label = "HSPA";
                break;
            case TelephonyManager.NETWORK_TYPE_IDEN:
                label = "IDEN";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                label = "EVDO_B";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                label = "LTE";
                break;
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                label = "EHRPD";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                label = "HSPAP";
                break;
            case TelephonyManager.NETWORK_TYPE_GSM:
                label = "GSM";
                break;
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                label = "TD_SCDMA";
                break;
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                label = "IWLAN";
                break;
            case TelephonyManager.NETWORK_TYPE_NR:
                label = "NR";
                break;
            default:
                label = "unknown";
                break;
        }
        return networkType + " (" + label + ")";
    }

    private static String describeOverrideNetworkType(int overrideNetworkType) {
        if (overrideNetworkType == Integer.MIN_VALUE) {
            return "unknown";
        }
        String label;
        switch (overrideNetworkType) {
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE:
                label = "NONE";
                break;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA:
                label = "LTE_CA";
                break;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO:
                label = "LTE_ADVANCED_PRO";
                break;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA:
                label = "NR_NSA";
                break;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE:
                label = "NR_NSA_MMWAVE";
                break;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED:
                label = "NR_ADVANCED";
                break;
            default:
                label = "unknown";
                break;
        }
        return overrideNetworkType + " (" + label + ")";
    }

    private static String describeNrState(int nrState) {
        if (nrState == Integer.MIN_VALUE) {
            return "unknown";
        }
        String label;
        switch (nrState) {
            case 0:
                label = "NONE";
                break;
            case 1:
                label = "RESTRICTED";
                break;
            case 2:
                label = "NOT_RESTRICTED";
                break;
            case 3:
                label = "CONNECTED";
                break;
            default:
                label = "unknown";
                break;
        }
        return nrState + " (" + label + ")";
    }

    private static String describeMobileTypeBadge(int badge) {
        switch (badge) {
            case Integer.MIN_VALUE:
                return "UNRESOLVED";
            case SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE:
                return "NONE";
            case SignalPreviewPainter.MOBILE_TYPE_BADGE_5G:
                return "5G";
            case SignalPreviewPainter.MOBILE_TYPE_BADGE_5GA:
                return "5GA";
            default:
                return "UNKNOWN(" + badge + ")";
        }
    }

    private static int resolveSignalMobileTypeBadgeFromCachedState(Context context) {
        if (context != null && isUsingWifiOnlyInternet(context)) {
            return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
        }
        int subId = resolveDefaultDataSubscriptionId();
        MobileTypeSubState state = snapshotResolvedMobileTypeSubState(subId);
        int badge = resolveSignalMobileTypeBadgeFromSubState(state);
        if (badge != Integer.MIN_VALUE) {
            return badge;
        }
        return SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE;
    }

    private static String nonEmpty(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private static String nonNullText(String value) {
        return value == null ? "" : value;
    }

    private static String safeToString(Object value) {
        try {
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int resolveSignalBars(String idName, View view) {
        if ("mobile_signal".equals(idName)) {
            int level = resolveSignalLevelForSubId(
                    view == null ? null : view.getContext(),
                    resolveSignalViewSubId(view));
            if (level >= 0) {
                return level;
            }
            if (LAST_SIGNAL_LEVEL >= 0) {
                return LAST_SIGNAL_LEVEL;
            }
        }
        return LAST_CELLULAR_LEVEL >= 0 ? normalizeSignalLevel(LAST_CELLULAR_LEVEL) : -1;
    }

    private static int resolveSignalLevelForSubId(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return -1;
        }
        ModuleConfig config = ModuleConfig.load(
                context == null ? ModuleConfig.getSystemUiContext() : context);
        int debugLevel = resolveTelephonyDebugSignalLevel(config, subId);
        if (debugLevel >= 0) {
            return debugLevel;
        }
        SignalLevelSubState state = snapshotSignalLevelSubState(subId);
        if (state != null && state.level >= 0) {
            return state.level;
        }
        return -1;
    }

    private static MergedSignalLevels resolveMergedSignalLevels(ImageView view, View mobileGroup) {
        MergedSignalLevels levels = new MergedSignalLevels();
        int fallbackLevel = resolveSignalBars("mobile_signal", view);
        levels.primaryLevel = fallbackLevel;
        levels.secondaryLevel = fallbackLevel;
        Context context = view == null ? null : view.getContext();
        ModuleConfig config = ModuleConfig.load(
                context == null ? ModuleConfig.getSystemUiContext() : context);
        if (isTelephonyDebugEnabled(config)) {
            int simCount = resolveTelephonyDebugActiveSubscriptionCount(config);
            if (simCount >= 1) {
                int primaryLevel = resolveSignalLevelForSubId(context, TELEPHONY_DEBUG_SUB_ID_CARD1);
                if (primaryLevel >= 0) {
                    levels.primaryLevel = primaryLevel;
                }
            }
            if (simCount >= 2) {
                int secondaryLevel = resolveSignalLevelForSubId(context, TELEPHONY_DEBUG_SUB_ID_CARD2);
                if (secondaryLevel >= 0) {
                    levels.secondaryLevel = secondaryLevel;
                }
            } else {
                levels.secondaryLevel = levels.primaryLevel;
            }
            normalizeMergedSignalLevels(levels);
            return levels;
        }

        ArrayList<View> groups = collectSiblingMobileSignalGroups(mobileGroup);
        int slot1SubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int slot2SubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int firstObservedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int secondObservedSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        for (int i = 0; i < groups.size(); i++) {
            int subId = resolveSubIdFromCarrierCallbackOwner(groups.get(i));
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                continue;
            }
            if (!SubscriptionManager.isValidSubscriptionId(firstObservedSubId)) {
                firstObservedSubId = subId;
            } else if (subId != firstObservedSubId
                    && !SubscriptionManager.isValidSubscriptionId(secondObservedSubId)) {
                secondObservedSubId = subId;
            }
            int simSlotIndex = resolveSignalSubSlotIndex(context, subId);
            if (simSlotIndex == 0 && !SubscriptionManager.isValidSubscriptionId(slot1SubId)) {
                slot1SubId = subId;
            } else if (simSlotIndex == 1
                    && subId != slot1SubId
                    && !SubscriptionManager.isValidSubscriptionId(slot2SubId)) {
                slot2SubId = subId;
            }
        }

        int primarySubId = SubscriptionManager.isValidSubscriptionId(slot1SubId)
                ? slot1SubId
                : firstObservedSubId;
        int secondarySubId = SubscriptionManager.isValidSubscriptionId(slot2SubId)
                ? slot2SubId
                : secondObservedSubId;
        if (!SubscriptionManager.isValidSubscriptionId(secondarySubId)
                || secondarySubId == primarySubId) {
            secondarySubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        int primaryLevel = resolveSignalLevelForSubId(context, primarySubId);
        if (primaryLevel >= 0) {
            levels.primaryLevel = primaryLevel;
        }
        int secondaryLevel = resolveSignalLevelForSubId(context, secondarySubId);
        if (secondaryLevel >= 0) {
            levels.secondaryLevel = secondaryLevel;
        }
        normalizeMergedSignalLevels(levels);
        return levels;
    }

    private static void normalizeMergedSignalLevels(MergedSignalLevels levels) {
        if (levels == null) {
            return;
        }
        if (levels.primaryLevel < 0 && levels.secondaryLevel >= 0) {
            levels.primaryLevel = levels.secondaryLevel;
        }
        if (levels.secondaryLevel < 0 && levels.primaryLevel >= 0) {
            levels.secondaryLevel = levels.primaryLevel;
        }
        if (levels.primaryLevel < 0) {
            levels.primaryLevel = 0;
        }
        if (levels.secondaryLevel < 0) {
            levels.secondaryLevel = levels.primaryLevel;
        }
    }

    private static int resolveSignalSubSlotIndex(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return -1;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD1) {
            return 0;
        }
        if (subId == TELEPHONY_DEBUG_SUB_ID_CARD2) {
            return 1;
        }
        synchronized (SIGNAL_SUB_SLOT_INDEX_CACHE) {
            Integer cached = SIGNAL_SUB_SLOT_INDEX_CACHE.get(subId);
            if (cached != null) {
                return cached;
            }
        }
        if (context == null) {
            return -1;
        }
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) {
                return -1;
            }
            SubscriptionInfo info = manager.getActiveSubscriptionInfo(subId);
            int slotIndex = info == null ? -1 : info.getSimSlotIndex();
            if (slotIndex >= 0) {
                synchronized (SIGNAL_SUB_SLOT_INDEX_CACHE) {
                    SIGNAL_SUB_SLOT_INDEX_CACHE.put(subId, slotIndex);
                }
            }
            return slotIndex;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void clearSignalSubSlotIndexCache() {
        synchronized (SIGNAL_SUB_SLOT_INDEX_CACHE) {
            SIGNAL_SUB_SLOT_INDEX_CACHE.clear();
        }
    }

    private static int normalizeSignalLevel(int rawLevel) {
        if (rawLevel < 0) {
            return -1;
        }
        int level = rawLevel & 0xff;
        if (level <= 4) {
            return level;
        }
        return 0;
    }

    private static int resolveActiveSubscriptionCount(Context context) {
        ModuleConfig config = ModuleConfig.load(context);
        if (isTelephonyDebugEnabled(config)) {
            int count = resolveTelephonyDebugActiveSubscriptionCount(config);
            if (count != LAST_ACTIVE_SUBSCRIPTION_COUNT) {
                clearSignalSubSlotIndexCache();
            }
            LAST_ACTIVE_SUBSCRIPTION_COUNT = count;
            return count;
        }
        if (context == null) {
            return LAST_ACTIVE_SUBSCRIPTION_COUNT;
        }
        try {
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            if (manager == null) {
                return LAST_ACTIVE_SUBSCRIPTION_COUNT;
            }
            int count = manager.getActiveSubscriptionInfoCount();
            if (count != LAST_ACTIVE_SUBSCRIPTION_COUNT) {
                clearSignalSubSlotIndexCache();
            }
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
        updateSignalSlotVisibility(mobileGroup, simCount);
        if (simCount <= 0) {
            return;
        }
        if (mergedDual && !isPrimarySignalView(view, mobileGroup)) {
            return;
        }
        alignSignalIconVertically(view);
        bindSignalViewState(view);
        int mobileTypeBadge = resolveSignalMobileTypeBadge();
        MergedSignalLevels mergedSignalLevels = mergedDual
                ? resolveMergedSignalLevels(view, mobileGroup)
                : null;
        int signalLevel = mergedDual
                ? mergedSignalLevels.primaryLevel
                : resolveSignalBars("mobile_signal", view);
        int secondarySignalLevel = mergedDual
                ? mergedSignalLevels.secondaryLevel
                : signalLevel;
        resizeSignalIconView(view, mobileTypeBadge);
        disableAncestorClipping(view, 6);
        int intrinsicHeight = resolveSignalIconIntrinsicHeight(view);
        int intrinsicWidth = SignalPreviewPainter.resolveIntrinsicWidth(intrinsicHeight, mobileTypeBadge);
        Drawable current = view.getDrawable();
        if (current instanceof SignalIconDrawable) {
            SignalIconDrawable signalDrawable = (SignalIconDrawable) current;
            if (signalDrawable.matchesGeometry(
                    mergedDual, intrinsicWidth, intrinsicHeight, mobileTypeBadge)) {
                signalDrawable.setSignalLevels(signalLevel, secondarySignalLevel);
                return;
            }
        }
        SignalIconDrawable drawable = new SignalIconDrawable(
                view, mergedDual, intrinsicWidth, intrinsicHeight, mobileTypeBadge,
                signalLevel, secondarySignalLevel);
        drawable.setAlpha(view.getImageAlpha());
        drawable.setState(view.getDrawableState());
        drawable.setTintList(view.getImageTintList());
        applySignalDrawableToView(view, drawable);
    }

    private static void applySignalDrawableToView(ImageView view, Drawable drawable) {
        if (view == null) {
            return;
        }
        synchronized (SIGNAL_DRAWABLE_APPLY_GUARDS) {
            if (Boolean.TRUE.equals(SIGNAL_DRAWABLE_APPLY_GUARDS.get(view))) {
                return;
            }
            SIGNAL_DRAWABLE_APPLY_GUARDS.put(view, Boolean.TRUE);
        }
        try {
            view.setImageDrawable(drawable);
            if (drawable != null) {
                SIGNAL_DRAWABLE_OWNERS.put(drawable, view);
            }
        } finally {
            synchronized (SIGNAL_DRAWABLE_APPLY_GUARDS) {
                SIGNAL_DRAWABLE_APPLY_GUARDS.remove(view);
            }
        }
    }

    private static boolean isSignalDrawableApplyGuardActive(ImageView view) {
        if (view == null) {
            return false;
        }
        synchronized (SIGNAL_DRAWABLE_APPLY_GUARDS) {
            return Boolean.TRUE.equals(SIGNAL_DRAWABLE_APPLY_GUARDS.get(view));
        }
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

    private static void setMobileTypeVisibility(View view, boolean visible) {
        if (view == null) {
            return;
        }
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == targetVisibility) {
            return;
        }
        view.setVisibility(targetVisibility);
        LAST_MOBILE_TYPE_VIEW_VISIBILITY = view.getVisibility();
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            ((View) parent).requestLayout();
        }
        view.requestLayout();
    }

    private static void updateSignalSlotVisibility(View mobileGroup, int simCount) {
        ArrayList<View> groups = collectSiblingMobileSignalGroups(mobileGroup);
        if (groups.isEmpty()) {
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            View group = groups.get(i);
            boolean shouldShow = simCount > 0 && i == 0;
            updateSignalSlotFootprint(group, shouldShow);
            int visibility = shouldShow ? View.VISIBLE : View.GONE;
            if (group.getVisibility() != visibility) {
                group.setVisibility(visibility);
            }
        }
    }

    private static void refreshLinkedSignalViews(View anchorView) {
        if (anchorView == null) {
            return;
        }
        View root = anchorView.getRootView();
        ModuleConfig config = ModuleConfig.load(anchorView.getContext());
        boolean useRootOnlyRefresh = isTelephonyDebugEnabled(config);
        View anchorGroup = null;
        ArrayList<View> linkedGroups = null;
        int anchorSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (!useRootOnlyRefresh) {
            anchorGroup = findMobileSignalGroup(anchorView);
            linkedGroups = collectSiblingMobileSignalGroups(anchorGroup);
            anchorSubId = resolveSubIdFromSignalViewOwner(anchorView);
        }
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View trackedView : views) {
            if (!(trackedView instanceof ImageView)) {
                continue;
            }
            if (!"mobile_signal".equals(getSystemUiIdName(trackedView))) {
                continue;
            }
            if (root != null && trackedView.getRootView() != root) {
                continue;
            }
            if (!useRootOnlyRefresh) {
                View trackedGroup = findMobileSignalGroup(trackedView);
                boolean sameLinkedGroup = linkedGroups != null
                        && !linkedGroups.isEmpty()
                        && linkedGroups.contains(trackedGroup);
                boolean sameSubId = SubscriptionManager.isValidSubscriptionId(anchorSubId)
                        && resolveSignalViewSubId(trackedView) == anchorSubId;
                if (!sameLinkedGroup && !sameSubId) {
                    continue;
                }
            }
            applySignalIconOverride((ImageView) trackedView);
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

    private static void resizeSignalIconView(ImageView view, int mobileTypeBadge) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        int targetHeight = resolveTargetSignalIconBoxSize(view);
        int targetWidth = SignalPreviewPainter.resolveIntrinsicWidth(targetHeight, mobileTypeBadge);
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

    private static void rememberOriginalNotificationIconPadding(View view) {
        if (view == null || ORIGINAL_PADDINGS.containsKey(view)) {
            return;
        }
        ORIGINAL_PADDINGS.put(view, readViewPaddingDirect(view));
    }

    private static int[] readViewPaddingDirect(View view) {
        if (view == null) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                ReflectUtils.getIntField(view, "mPaddingLeft", 0),
                ReflectUtils.getIntField(view, "mPaddingTop", 0),
                ReflectUtils.getIntField(view, "mPaddingRight", 0),
                ReflectUtils.getIntField(view, "mPaddingBottom", 0)
        };
    }

    private static void rememberOriginalTextSize(TextView view) {
        if (view == null || ORIGINAL_TEXT_SIZES.containsKey(view)) {
            return;
        }
        ORIGINAL_TEXT_SIZES.put(view, view.getTextSize());
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
        if (isNotificationBackedStatusBarIconView(view)) {
            return true;
        }
        return findAncestorByIdName(view, "notificationIcons") != null;
    }

    private static boolean isNotificationBackedStatusBarIconView(View view) {
        if (view == null) {
            return false;
        }
        if (!"com.android.systemui.statusbar.StatusBarIconView".equals(view.getClass().getName())) {
            return false;
        }
        return ReflectUtils.invokeNoArg(view, "getNotification") != null;
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
        if (isMobileSignalRelatedContainerView(view)) {
            return;
        }
        applyScaleToLayoutParams(view, scale);
        if (view instanceof ViewGroup) {
            applyScaleToChildren(view, scale);
        }
    }

    private static boolean isMobileSignalRelatedContainerView(View view) {
        if (view == null) {
            return false;
        }
        String idName = getSystemUiIdName(view);
        return "mobile_group".equals(idName)
                || "mobile_type_container".equals(idName)
                || "inout_container".equals(idName)
                || "mobile_roaming_space".equals(idName)
                || "mobile_combo".equals(idName)
                || isMobileSignalGroupView(view);
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

    private static ConnectionRateViewState rememberConnectionRateViewState(View view) {
        ConnectionRateViewState state = CONNECTION_RATE_VIEW_STATES.get(view);
        if (state == null) {
            state = new ConnectionRateViewState();
            CONNECTION_RATE_VIEW_STATES.put(view, state);
        }
        return state;
    }

    private static void applyConnectionRateThresholdVisibility(View view) {
        if (view == null) {
            return;
        }
        ConnectionRateViewState state = rememberConnectionRateViewState(view);
        applyConnectionRateThresholdVisibility(view, state, ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, boolean baseShow, double rate) {
        if (view == null) {
            return;
        }
        applyConnectionRateThresholdVisibility(view, baseShow, rate, rememberConnectionRateViewState(view),
                ModuleConfig.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, ConnectionRateViewState state,
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
            ConnectionRateViewState state, ModuleConfig config) {
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

    public static void ensureConfigRefreshObserver(Context context) {
        if (context == null || CONFIG_REFRESH_REGISTERED) {
            return;
        }
        ModuleConfig.rememberSystemUiContext(context);
        synchronized (CONFIG_REFRESH_LOCK) {
            if (CONFIG_REFRESH_REGISTERED) {
                return;
            }
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
            registerConfigurationCallbacks(context);
            registerDefaultNetworkCallback(context);
            CONFIG_REFRESH_REGISTERED = true;
            scheduleInitialRuntimeRefreshes();
        }
    }

    public static void postToMainHandler(Runnable runnable) {
        Handler handler = MAIN_HANDLER;
        if (handler == null || runnable == null) {
            return;
        }
        handler.post(runnable);
    }

    public static Handler getMainHandler() {
        return MAIN_HANDLER;
    }

    private static void registerConfigurationCallbacks(Context context) {
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        LAST_UI_MODE_NIGHT = readNightModeMask(appContext.getResources().getConfiguration());
        ComponentCallbacks callbacks = new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                int newNightMode = readNightModeMask(newConfig);
                int oldNightMode = LAST_UI_MODE_NIGHT;
                LAST_UI_MODE_NIGHT = newNightMode;
                if (oldNightMode == -1 || oldNightMode == newNightMode) {
                    return;
                }
                Handler handler = MAIN_HANDLER;
                if (handler != null) {
                    handler.post(FlymeStatusBarSizer::refreshNotificationAppIconsForUiModeChange);
                } else {
                    refreshNotificationAppIconsForUiModeChange();
                }
            }

            @Override
            public void onLowMemory() {
            }
        };
        try {
            appContext.registerComponentCallbacks(callbacks);
        } catch (Throwable ignored) {
        }
    }

    private static void registerDefaultNetworkCallback(Context context) {
        if (context == null || DEFAULT_NETWORK_CALLBACK_REGISTERED) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        try {
            ConnectivityManager manager = appContext.getSystemService(ConnectivityManager.class);
            if (manager == null) {
                return;
            }
            manager.registerDefaultNetworkCallback(DEFAULT_NETWORK_CALLBACK, handler);
            DEFAULT_NETWORK_CALLBACK_REGISTERED = true;
        } catch (Throwable ignored) {
        }
    }

    private static int readNightModeMask(Configuration configuration) {
        if (configuration == null) {
            return -1;
        }
        return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static void refreshNotificationAppIconAfterConfigurationChange(
            View view, Configuration configuration) {
        if (!(view instanceof ImageView) || !isNotificationBackedStatusBarIconView(view)) {
            return;
        }
        int nightMode = readNightModeMask(configuration);
        if (nightMode != -1 && nightMode != LAST_NOTIFICATION_APP_ICON_VIEW_REFRESH_NIGHT) {
            LAST_NOTIFICATION_APP_ICON_VIEW_REFRESH_NIGHT = nightMode;
            clearFlymeNotificationAppIconCache(view.getContext());
        }
        view.post(() -> applyNotificationStatusBarIconDrawable(view));
    }

    private static void clearFlymeNotificationAppIconCache(Context context) {
        if (context == null) {
            return;
        }
        try {
            Method method = flymeClearApplicationIconCacheMethod;
            if (method == null) {
                Class<?> clazz = Class.forName(
                        FLYME_STATUS_BAR_ICON_UTILS,
                        false,
                        context.getClassLoader());
                method = clazz.getDeclaredMethod("clearAppCache");
                method.setAccessible(true);
                flymeClearApplicationIconCacheMethod = method;
            }
            method.invoke(null);
        } catch (Throwable ignored) {
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
        clearSignalTintSourceCache();
        clearSignalSubSlotIndexCache();
        refreshTrackedConnectionRateViews();
        refreshTrackedBatteryViews();
        refreshTrackedStatusBarIconViews();
        ClockHooks.refreshTrackedViews();
        refreshTrackedInputMethodViews();
    }

    private static void refreshNotificationAppIconsForUiModeChange() {
        Context context = ModuleConfig.getSystemUiContext();
        if (context != null) {
            clearFlymeNotificationAppIconCache(context);
        }
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View view : views) {
            if (!(view instanceof ImageView) || !isNotificationBackedStatusBarIconView(view)) {
                continue;
            }
            applyNotificationStatusBarIconDrawable(view);
        }
    }

    private static void refreshTrackedConnectionRateViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<View> connectionRateViews = new ArrayList<>(CONNECTION_RATE_VIEW_STATES.keySet());
            for (View view : connectionRateViews) {
                if (view == null) {
                    continue;
                }
                applyConnectionRateTextScale(view);
                applyConnectionRateThresholdVisibility(view);
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
                BatteryViewState state = rememberBatteryViewState(batteryView);
                boolean wasCodeDrawEnabled = state.codeDrawEnabled;
                long previousRenderConfigSignature = state.renderConfigSignature;
                boolean hadRenderConfigSignature = state.hasRenderConfigSignature;
                boolean snapshotChanged = false;
                if (isBatteryCodeDrawEnabled(config)) {
                    snapshotChanged = refreshBatteryViewRuntimeSnapshot(batteryView, state);
                }
                boolean layoutChanged = syncBatteryViewLayoutIfNeeded(batteryView, config, false);
                if (!isBatteryCodeDrawEnabled(config)) {
                    state.hasRenderConfigSignature = false;
                    state.renderConfigSignature = 0L;
                    if (wasCodeDrawEnabled || layoutChanged) {
                        ReflectUtils.invokeMethod(batteryView, "apply", new Class[]{boolean.class}, true);
                        batteryView.invalidate();
                    }
                    continue;
                }
                long renderConfigSignature = getBatteryRenderConfigSignature(config);
                boolean renderConfigChanged = !hadRenderConfigSignature
                        || previousRenderConfigSignature != renderConfigSignature;
                state.renderConfigSignature = renderConfigSignature;
                state.hasRenderConfigSignature = true;
                if (snapshotChanged || layoutChanged || renderConfigChanged || !wasCodeDrawEnabled) {
                    batteryView.invalidate();
                }
            }
        });
    }

    private static void invalidateLinkedSignalViews(View batteryView) {
        if (batteryView == null) {
            return;
        }
        View batteryRoot = batteryView.getRootView();
        if (batteryRoot == null) {
            return;
        }
        clearSignalTintSourceCacheForRoot(batteryRoot);
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View view : views) {
            if (!(view instanceof ImageView)) {
                continue;
            }
            if (!"mobile_signal".equals(getSystemUiIdName(view))) {
                continue;
            }
            if (view.getRootView() != batteryRoot) {
                continue;
            }
            Drawable drawable = ((ImageView) view).getDrawable();
            if (drawable instanceof SignalIconDrawable) {
                drawable.invalidateSelf();
                view.invalidate();
            }
        }
    }

    private static void scheduleTrackedSignalIconRefresh() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            refreshTrackedSignalIconViewsNow();
            return;
        }
        handler.removeCallbacks(PRIMARY_SIGNAL_ICON_REFRESH_RUNNABLE);
        handler.removeCallbacks(SIGNAL_ICON_REFRESH_RUNNABLE);
        handler.post(SIGNAL_ICON_REFRESH_RUNNABLE);
    }

    private static void scheduleTrackedSignalIconRefreshForSignalSubId(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            refreshTrackedSignalIconViewsNow();
            return;
        }
        handler.post(() -> refreshTrackedSignalIconViewsForSubId(subId));
    }

    private static void scheduleTrackedPrimarySignalIconRefresh() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            refreshTrackedPrimarySignalIconViewsNow();
            return;
        }
        handler.removeCallbacks(PRIMARY_SIGNAL_ICON_REFRESH_RUNNABLE);
        handler.post(PRIMARY_SIGNAL_ICON_REFRESH_RUNNABLE);
    }

    private static void refreshTrackedSignalIconViewsForSubId(int subId) {
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        boolean useFastSubIdMatch = isTelephonyDebugEnabled(config);
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View view : views) {
            if (!(view instanceof ImageView)) {
                continue;
            }
            if (!"mobile_signal".equals(getSystemUiIdName(view))) {
                continue;
            }
            if (useFastSubIdMatch) {
                int viewSubId = resolveSignalViewSubId(view);
                if (!SubscriptionManager.isValidSubscriptionId(viewSubId) || viewSubId != subId) {
                    continue;
                }
            } else if (!shouldRefreshSignalViewForSubId((ImageView) view, subId)) {
                continue;
            }
            applySignalIconOverride((ImageView) view);
        }
    }

    private static void refreshTrackedPrimarySignalIconViewsNow() {
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View view : views) {
            if (!(view instanceof ImageView)) {
                continue;
            }
            if (!"mobile_signal".equals(getSystemUiIdName(view))) {
                continue;
            }
            View mobileGroup = findMobileSignalGroup(view);
            if (mobileGroup != null && !isPrimarySignalView((ImageView) view, mobileGroup)) {
                continue;
            }
            applySignalIconOverride((ImageView) view);
        }
    }

    private static void refreshTrackedSignalIconViewsNow() {
        ArrayList<View> views = new ArrayList<>(TRACKED_STATUS_BAR_ICON_VIEWS.keySet());
        for (View view : views) {
            if (!(view instanceof ImageView)) {
                continue;
            }
            if (!"mobile_signal".equals(getSystemUiIdName(view))) {
                continue;
            }
            applySignalIconOverride((ImageView) view);
        }
    }

    private static SignalViewState rememberSignalViewState(View view) {
        if (view == null) {
            return null;
        }
        SignalViewState state = SIGNAL_VIEW_STATES.get(view);
        if (state == null) {
            state = new SignalViewState();
            SIGNAL_VIEW_STATES.put(view, state);
        }
        return state;
    }

    private static void bindSignalViewState(View view) {
        if (view == null || !"mobile_signal".equals(getSystemUiIdName(view))) {
            return;
        }
        SignalViewState state = rememberSignalViewState(view);
        if (state == null) {
            return;
        }
        int subId = resolveSignalViewSubIdInternal(view, state);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            if (state.subId != subId) {
                state.subId = subId;
                reportSignalLevelDebug("bindView subId=" + subId
                        + " view=" + view.getClass().getName()
                        + " id=" + getSystemUiIdName(view));
            }
        }
    }

    private static int resolveSignalViewSubId(View view) {
        SignalViewState state = rememberSignalViewState(view);
        return resolveSignalViewSubIdInternal(view, state);
    }

    private static int resolveSignalViewSubIdInternal(View view, SignalViewState state) {
        ModuleConfig config = ModuleConfig.load(view == null ? ModuleConfig.getSystemUiContext() : view.getContext());
        if (isTelephonyDebugEnabled(config)) {
            int debugSubId = resolveTelephonyDebugPrimarySignalSubId(config);
            if (state != null) {
                state.subId = debugSubId;
            }
            return debugSubId;
        }
        if (state != null
                && SubscriptionManager.isValidSubscriptionId(state.subId)
                && !isTelephonyDebugSubId(state.subId)) {
            return state.subId;
        }
        int subId = resolveSubIdFromSignalViewOwner(view);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            subId = resolveDefaultDataSubscriptionId();
        }
        if (state != null && SubscriptionManager.isValidSubscriptionId(subId)) {
            state.subId = subId;
        }
        return subId;
    }

    private static int resolveSubIdFromSignalViewOwner(View view) {
        if (view == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        View mobileGroup = findMobileSignalGroup(view);
        int subId = resolveSubIdFromCarrierCallbackOwner(mobileGroup);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        View combo = findAncestorByIdName(view, "mobile_combo");
        subId = resolveSubIdFromCarrierCallbackOwner(combo);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return subId;
        }
        Object parent = view.getParent();
        if (parent instanceof View) {
            subId = resolveSubIdFromCarrierCallbackOwner(parent);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                return subId;
            }
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static boolean shouldRefreshSignalViewForSubId(ImageView view, int subId) {
        if (view == null || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return false;
        }
        int viewSubId = resolveSignalViewSubId(view);
        if (SubscriptionManager.isValidSubscriptionId(viewSubId) && viewSubId == subId) {
            return true;
        }
        View mobileGroup = findMobileSignalGroup(view);
        if (mobileGroup == null || !isPrimarySignalView(view, mobileGroup)) {
            return false;
        }
        ArrayList<View> groups = collectSiblingMobileSignalGroups(mobileGroup);
        for (int i = 0; i < groups.size(); i++) {
            if (resolveSubIdFromCarrierCallbackOwner(groups.get(i)) == subId) {
                return true;
            }
        }
        return false;
    }

    private static void queryAndStoreLiveSignalLevel(Context context, int subId, String source) {
        if (context == null || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        int level = queryLiveSignalLevel(context, subId);
        if (level < 0) {
            reportSignalLevelDebug("queryLive skip subId=" + subId + " source=" + source);
            return;
        }
        updateSignalLevelSubState(subId, level, source + ":queryLive");
    }

    private static int queryLiveSignalLevel(Context context, int subId) {
        ModuleConfig config = ModuleConfig.load(context);
        int debugLevel = resolveTelephonyDebugSignalLevel(config, subId);
        if (debugLevel >= 0) {
            return debugLevel;
        }
        TelephonyManager manager = getTelephonyManagerForSub(context, subId);
        if (manager == null) {
            return -1;
        }
        pushInternalSignalLevelQuery();
        try {
            SignalStrength signalStrength = manager.getSignalStrength();
            if (signalStrength == null) {
                return -1;
            }
            SIGNAL_STRENGTH_SUB_IDS.put(signalStrength, subId);
            return normalizeSignalLevel(signalStrength.getLevel());
        } catch (Throwable ignored) {
            return -1;
        } finally {
            popInternalSignalLevelQuery();
        }
    }

    private static void updateSignalLevelSubState(int subId, int rawLevel, String source) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }
        int level = normalizeSignalLevel(rawLevel);
        SignalLevelSubState state = rememberSignalLevelSubState(subId);
        if (state == null) {
            return;
        }
        boolean changed = state.level != level;
        state.level = level;
        state.lastSource = source == null ? "" : source;
        state.lastUpdateElapsedRealtime = SystemClock.elapsedRealtime();
        LAST_SIGNAL_SUB_ID = subId;
        LAST_SIGNAL_LEVEL = level;
        LAST_CELLULAR_LEVEL = level;
        if (changed) {
            reportSignalLevelDebug("update subId=" + subId + " level=" + level
                    + " source=" + state.lastSource);
        }
    }

    private static SignalLevelSubState rememberSignalLevelSubState(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return null;
        }
        synchronized (SIGNAL_LEVEL_SUB_STATES) {
            SignalLevelSubState state = SIGNAL_LEVEL_SUB_STATES.get(subId);
            if (state == null) {
                state = new SignalLevelSubState();
                state.subId = subId;
                SIGNAL_LEVEL_SUB_STATES.put(subId, state);
                reportSignalLevelDebug("state-created subId=" + subId);
            }
            return state;
        }
    }

    private static SignalLevelSubState snapshotSignalLevelSubState(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return null;
        }
        synchronized (SIGNAL_LEVEL_SUB_STATES) {
            return SIGNAL_LEVEL_SUB_STATES.get(subId);
        }
    }

    private static void reportSignalLevelDebug(String detail) {
        ModuleConfig config = ModuleConfig.load(ModuleConfig.getSystemUiContext());
        if (!isSignalLevelDebugLogEnabled(config)) {
            return;
        }
        android.util.Log.i(TAG, SIGNAL_LEVEL_LOG_MARKER + " " + detail);
    }

    private static void pushInternalSignalLevelQuery() {
        INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.set(INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.get() + 1);
    }

    private static void popInternalSignalLevelQuery() {
        int depth = INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.get();
        if (depth <= 1) {
            INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.set(0);
            return;
        }
        INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.set(depth - 1);
    }

    private static boolean isInternalSignalLevelQueryActive() {
        return INTERNAL_SIGNAL_LEVEL_QUERY_DEPTH.get() > 0;
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
                if (isNotificationBackedStatusBarIconView(view) && view instanceof ImageView) {
                    applyNotificationStatusBarIconDrawable(view);
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

    private static void refreshTrackedInputMethodViews() {
        ImeHooks.refreshTrackedInputMethodViews();
    }

    public static void disableAncestorClipping(View view, int maxDepth) {
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

    public static int dp(Context context, int value) {
        if (context == null) {
            return value;
        }
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static Object getFieldCompat(Object target, String name) {
        return ReflectUtils.getField(target, name);
    }

    public static Object invokeNoArgCompat(Object target, String name) {
        return ReflectUtils.invokeNoArg(target, name);
    }

    public static Object invokeMethodCompat(
            Object target, String name, Class<?>[] parameterTypes, Object... args) {
        return ReflectUtils.invokeMethod(target, name, parameterTypes, args);
    }

    public static String getSystemUiIdNameCompat(View view) {
        return getSystemUiIdName(view);
    }

    private interface ViewAction {
        void apply(View view);
    }

    private static final class ConnectionRateViewState {
        int originalTextSize = -1;
        int originalMaxWidth = -1;
        int originalOrientation = Integer.MIN_VALUE;
        float originalRotation = Float.NaN;
        float originalUnitTextSize = Float.NaN;
        int originalUnitMinWidth = -1;
        int originalUnitMaxLines = Integer.MIN_VALUE;
        float originalUnitRotation = Float.NaN;
        int[] originalUnitPadding;
        long appliedTextScaleSignature;
        boolean hasAppliedTextScaleSignature;
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

    private static final class BatteryViewState {
        final Rect drawBounds = new Rect();
        long layoutSignature;
        boolean hasLayoutSignature;
        long renderConfigSignature;
        boolean hasRenderConfigSignature;
        boolean codeDrawEnabled;
        boolean hasRuntimeSnapshot;
        boolean originalLayoutCaptured;
        int originalLayoutWidth = Integer.MIN_VALUE;
        int originalLayoutHeight = Integer.MIN_VALUE;
        int level;
        boolean pluggedIn;
        boolean charging;
        boolean quickCharging;
        boolean showBolt;
        int tintColor = Color.BLACK;
        int textColor = Color.WHITE;
    }

    private static final class TelephonyDisplayInfoState {
        volatile int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int networkType = Integer.MIN_VALUE;
        int overrideNetworkType = Integer.MIN_VALUE;
    }

    private static final class SignalLevelSubState {
        volatile int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        volatile int level = -1;
        volatile String lastSource = "";
        volatile long lastUpdateElapsedRealtime;
    }

    private static final class SignalViewState {
        volatile int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static final class MBackConfigSnapshot {
        public final boolean enabled;
        public final boolean mbackLongTouchIntentEnabled;
        public final String mbackLongTouchIntentUri;
        public final boolean mbackNavBarTransparent;
        public final boolean mbackHidePill;
        public final int mbackInsetSize;
        public final int mbackNavBarHeight;

        private MBackConfigSnapshot(ModuleConfig config) {
            enabled = config != null && config.enabled;
            mbackLongTouchIntentEnabled = config != null && config.mbackLongTouchIntentEnabled;
            mbackLongTouchIntentUri = config == null ? "" : config.mbackLongTouchIntentUri;
            mbackNavBarTransparent = config != null && config.mbackNavBarTransparent;
            mbackHidePill = config != null && config.mbackHidePill;
            mbackInsetSize = config == null ? -1 : config.mbackInsetSize;
            mbackNavBarHeight = config == null ? -1 : config.mbackNavBarHeight;
        }
    }

    public static final class ImeConfigSnapshot {
        public final boolean enabled;
        public final boolean imeToolbarEnabled;
        public final String imeToolbarOrder;

        private ImeConfigSnapshot(ModuleConfig config) {
            enabled = config != null && config.enabled;
            imeToolbarEnabled = config != null && config.imeToolbarEnabled;
            imeToolbarOrder = config == null ? "" : config.imeToolbarOrder;
        }
    }

    public static final class ClockConfigSnapshot {
        public final boolean enabled;
        public final String clockCustomFormat;
        public final int clockFontWeight;
        public final float clockAndCarrierTextScale;

        private ClockConfigSnapshot(ModuleConfig config) {
            enabled = config != null && config.enabled;
            clockCustomFormat = config == null ? "" : config.clockCustomFormat;
            clockFontWeight = resolveClockFontWeight(config);
            clockAndCarrierTextScale = enabled ? resolveClockAndCarrierTextScale(config) : 1f;
        }
    }

    private static final class MergedSignalLevels {
        volatile int primaryLevel = -1;
        volatile int secondaryLevel = -1;
    }

    private static final class MobileTypeSubState {
        volatile int networkType = Integer.MIN_VALUE;
        volatile int overrideNetworkType = Integer.MIN_VALUE;
        volatile int nrState = Integer.MIN_VALUE;
        volatile String nrCaState = "";
        volatile String flymeIconGroup = "";
    }

}
