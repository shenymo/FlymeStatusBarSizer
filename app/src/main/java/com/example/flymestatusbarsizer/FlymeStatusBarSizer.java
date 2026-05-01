package com.example.flymestatusbarsizer;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class FlymeStatusBarSizer extends XposedModule {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static volatile FlymeStatusBarSizer MODULE;

    private static final Uri SETTINGS_URI = Uri.parse("content://" + SettingsStore.AUTHORITY + "/settings");
    private static final WeakHashMap<View, int[]> ORIGINAL_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_MARGINS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, float[]> ORIGINAL_TRANSLATIONS = new WeakHashMap<>();
    private static final WeakHashMap<View, String> VIEW_ID_NAME_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Typeface> ORIGINAL_TEXT_TYPEFACES = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Integer> ORIGINAL_TEXT_STYLES = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> ORIGINAL_CONNECTION_RATE_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_WIFI_ROOT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Object> LAST_REAL_WIFI_STATES = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> TRACKED_WIFI_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Integer> WIFI_SIGNAL_LEVELS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> WIFI_SIGNAL_HIDDEN_STATES = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, ImageView> MOBILE_SIGNAL_DRAWABLE_OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> MOBILE_VIEW_SLOTS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, MobileSignalInfo> MOBILE_SIGNAL_RAW_INFOS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, MobileSignalInfo> MOBILE_SIGNAL_INFOS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> TRACKED_MOBILE_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> PRIMARY_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> SECONDARY_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> TRACKED_STATUS_TEXT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_CONNECTION_RATE_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, ConnectionRateThresholdState> CONNECTION_RATE_THRESHOLD_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_BATTERY_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<TelephonyManager, Integer> TELEPHONY_MANAGER_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<SignalStrength, Integer> SIGNAL_STRENGTH_SUB_IDS = new WeakHashMap<>();
    private static final WeakHashMap<CellSignalStrength, Integer> CELL_SIGNAL_STRENGTH_SUB_IDS = new WeakHashMap<>();
    private static final HashMap<Integer, Integer> FLYME_SLOT_INDEX_BY_SUB_ID = new HashMap<>();
    private static final HashMap<Integer, PhoneStateListener> SIGNAL_LISTENERS_BY_SUB_ID = new HashMap<>();
    private static final Pattern WIFI_LEVEL_PATTERN = Pattern.compile("(?:^|[_-])([0-4])(?:$|[_-])");
    private static final Pattern WIFI_COMPACT_LEVEL_PATTERN = Pattern.compile("(?:wifi|wlan|signal|level)[_-]?([0-4])");
    private static final Pattern MOBILE_AOSP_LEVEL_PATTERN = Pattern.compile("ic_mobile_([0-5])_[45]_bar");
    private static final Pattern MOBILE_SIGNAL_LEVEL_PATTERN = Pattern.compile("(?:^|[_-])([0-5])(?:$|[_-])");
    private static final Pattern MOBILE_COMPACT_LEVEL_PATTERN = Pattern.compile("(?:signal|mobile|level|bar)[_-]?([0-5])");
    private static final int[] DESKTOP_MOBILE_SIGNAL_SIZE = new int[2];
    private static final int MOBILE_SLOT_UNKNOWN = 0;
    private static final int MOBILE_SLOT_PRIMARY = 1;
    private static final int MOBILE_SLOT_SECONDARY = 2;
    private static final int UNSET_SUB_ID = Integer.MIN_VALUE;
    private static final int SIGNAL_SCENE_DESKTOP = 0;
    private static final int SIGNAL_SCENE_KEYGUARD = 1;
    private static final int SIGNAL_SCENE_CONTROL_CENTER = 2;
    private static final Object CONFIG_REFRESH_LOCK = new Object();
    private static final Object CONFIG_CACHE_LOCK = new Object();
    private static final Object RUNTIME_REFRESH_LOCK = new Object();
    private static final long CONFIG_CACHE_TTL_MS = 5000L;
    private static final long TELEPHONY_CACHE_TTL_MS = 30000L;
    private static final long SIGNAL_DEBUG_REPORT_MIN_INTERVAL_MS = 200L;
    private static final long WIFI_DEBUG_REPORT_MIN_INTERVAL_MS = 120L;
    private static final long[] INITIAL_RUNTIME_REFRESH_DELAYS_MS = {1000L, 3000L};
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static volatile boolean LAST_SIGNAL_DEBUG_ENABLED;
    private static volatile boolean LAST_WIFI_DEBUG_ENABLED;
    private static volatile boolean INTERNAL_WIFI_DEBUG_APPLY;
    private static volatile long LAST_SIGNAL_DEBUG_REPORT_UPTIME;
    private static volatile long LAST_WIFI_DEBUG_REPORT_UPTIME;
    private static volatile String LAST_WIFI_DEBUG_SOURCE = "";
    private static volatile String LAST_WIFI_DEBUG_VISIBLE = "";
    private static volatile String LAST_WIFI_DEBUG_VISIBLE_FROM_STATE = "";
    private static volatile String LAST_WIFI_DEBUG_LEVEL = "";
    private static volatile String LAST_WIFI_DEBUG_RES_ID = "";
    private static volatile String LAST_WIFI_DEBUG_RES_NAME = "";
    private static volatile String LAST_WIFI_DEBUG_STATE = "";
    private static volatile String LAST_WIFI_DEBUG_ERROR = "";
    private static Handler MAIN_HANDLER;
    private static volatile Context SYSTEM_UI_CONTEXT;
    private static volatile Config CACHED_CONFIG;
    private static volatile long CACHED_CONFIG_UPTIME;
    private static BroadcastReceiver USER_UNLOCKED_RECEIVER;
    private static BroadcastReceiver SUBSCRIPTION_CHANGED_RECEIVER;
    private static ContentObserver SETTINGS_OBSERVER;
    private static int primaryMobileSubId = UNSET_SUB_ID;
    private static int secondaryMobileSubId = UNSET_SUB_ID;
    private static int latestPrimarySignalLevel = 0;
    private static int latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
    private static final HashMap<Integer, Integer> TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID = new HashMap<>();
    private static final HashMap<Integer, Long> TELEPHONY_SIGNAL_LEVEL_TIMES_BY_SUB_ID = new HashMap<>();
    private static final HashMap<String, Integer> SYSTEM_UI_ID_CACHE = new HashMap<>();
    private static final HashMap<String, Field> FIELD_CACHE = new HashMap<>();
    private static final HashMap<String, Method> NO_ARG_METHOD_CACHE = new HashMap<>();
    private static volatile Method SET_MEASURED_DIMENSION_METHOD;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }
        MODULE = this;

        ClassLoader loader = param.getDefaultClassLoader();
        hookConstructAndBind(loader, "com.flyme.systemui.statusbar.net.mobile.ui.view.FlymeModernStatusBarMobileView");
        hookConstructAndBind(loader, "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView");
        hookConstructAndBind(loader, "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView");
        hookConstructAndBind(loader, "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView");
        hookFlymeSlotIndexUpdates(loader);
        hookTelephonyDebugSignals(loader);
        hookSignalIconModelDebugStates(loader);
        hookFlymeWifiView(loader);
        hookConnectionRateView(loader);
        hookImageViewTintUpdates(loader);
        hookSignalDrawableLevels(loader);
        hookMBackLongTouchIntent(loader);
        hookMBackNavBarExperiments(loader);
        hookMBackPillVisibility(loader);
        hookConstructors(loader, "com.android.systemui.statusbar.StatusBarIconView", view -> {
            Config config = Config.load(view.getContext());
            if (!config.enabled) {
                return;
            }
            view.setScaleX(config.scaled(config.statusIconFactor));
            view.setScaleY(config.scaled(config.statusIconFactor));
        });
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryMeterView", view -> {
            Config config = Config.load(view.getContext());
            if (!config.enabled) {
                return;
            }
            TRACKED_BATTERY_VIEWS.put(view, Boolean.TRUE);
            normalizeBatterySpacing(view);
            view.post(() -> normalizeBatterySpacing(view));
            disableAncestorClipping(view, 6);
            resizeIosBatteryView(view, config, getBooleanField(view, "mCharging", false));
            view.setScaleX(config.scaled(config.batteryFactor));
            view.setScaleY(config.scaled(config.batteryFactor));
        });
        hookFlymeBatteryMeterViewDraw(loader);
        hookFlymeBatteryMeterViewMeasure(loader);
        hookConstructors(loader, "com.flyme.statusbar.battery.FlymeBatteryTextView", view -> {
            Config config = Config.load(view.getContext());
            if (!config.enabled || !(view instanceof TextView)) {
                return;
            }
            TextView textView = (TextView) view;
            applyTextScale(textView, config);
            textView.setTextColor(Color.WHITE);
            setIntField(textView, "mNormalColor", Color.WHITE);
            setIntField(textView, "mLowColor", Color.WHITE);
        });
        hookBatteryDrawable(loader);
        hookStatusTextView(loader, "com.android.systemui.statusbar.policy.Clock");
        hookClockWeekday(loader);
        hookStatusTextView(loader, "com.android.systemui.statusbar.OperatorNameView");
        hookStatusTextView(loader, "com.android.keyguard.CarrierText");
        hookStatusTextView(loader, "com.android.systemui.util.AutoMarqueeTextView");
        hookConstructors(loader, "com.android.systemui.statusbar.phone.KeyguardStatusBarView", view -> {
            disableChildClipping(view);
            disableAncestorClipping(view, 3);
            applyReferenceSignalSizing(view);
        });
        hookConstructors(loader, "com.flyme.statusbar.bouncer.KeyguardBouncerStatusBarView", view -> {
            disableChildClipping(view);
            disableAncestorClipping(view, 3);
            applyReferenceSignalSizing(view);
        });
        hookConstructors(loader, "com.android.systemui.shade.carrier.ShadeCarrier", view -> {
            disableChildClipping(view);
            disableAncestorClipping(view, 3);
            applyReferenceSignalSizing(view);
        });
    }

    private void hookConstructAndBind(ClassLoader loader, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"constructAndBind".equals(method.getName())) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) || !View.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof View) {
                        View view = (View) result;
                        registerMobileViewSlot(view);
                        applyStatusBarSizing(view);
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook " + className, t);
        }
    }

    private void hookFlymeWifiView(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.systemui.statusbar.net.wifi.FlymeStatusBarWifiView", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"fromContext".equals(name) && !"initViewState".equals(name)
                        && !"updateState".equals(name) && !"applyWifiState".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object targetObject = chain.getThisObject();
                    if (targetObject instanceof View) {
                        trackWifiRootView((View) targetObject);
                    }
                    if (!"fromContext".equals(name) && chain.getArgs().size() > 0) {
                        Object state = chain.getArg(0);
                        if (targetObject instanceof View) {
                            View root = (View) targetObject;
                            Config config = Config.load(root.getContext());
                            if (state != null) {
                                if (!INTERNAL_WIFI_DEBUG_APPLY) {
                                    rememberRealWifiState(root, state);
                                }
                                if (config.enabled && config.iosWifiDebugEnabled) {
                                    applyDebugWifiStateToObject(root, state, config);
                                }
                            }
                        }
                    }
                    Object result = chain.proceed();
                    Object target = result instanceof View ? result : chain.getThisObject();
                    if (target instanceof View) {
                        View view = (View) target;
                        trackWifiRootView(view);
                        applyWifiSizing(view);
                        if (!"fromContext".equals(name) && chain.getArgs().size() > 0) {
                            applyFlymeWifiStateResource(view, chain.getArg(0));
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeStatusBarWifiView", t);
        }
    }

    private void hookFlymeSlotIndexUpdates(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel",
                    false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"handleLatestVmFlymeSlotIndexChanged".equals(method.getName())
                        || method.getParameterTypes().length != 2
                        || method.getParameterTypes()[0] != int.class
                        || method.getParameterTypes()[1] != int.class) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getArg(0) instanceof Integer && chain.getArg(1) instanceof Integer) {
                        int subId = (Integer) chain.getArg(0);
                        int slotIndex = (Integer) chain.getArg(1);
                        recordFlymeSlotIndex(subId, slotIndex);
                        refreshTrackedSignalViews(true);
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook Flyme slot index updates", t);
        }
    }

    private void hookTelephonyDebugSignals(ClassLoader loader) {
        hookTelephonyManagerDebugSignals();
        hookSignalStrengthDebugLevels();
        hookCellSignalStrengthDebugLevels(loader);
    }

    private void hookSignalIconModelDebugStates(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel$Cellular",
                    false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"toSignalDrawableState".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != int.class) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    int fallback = result instanceof Integer ? (Integer) result : 0;
                    Config config = getCachedOrLoadedConfig();
                    if (config == null || !config.enabled || !config.iosSignalDebugEnabled) {
                        return fallback;
                    }
                    return buildSignalDrawableState(getDebugPrimaryLevel(config), fallback);
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SignalIconModel debug states", t);
            reportSignalDebug(SYSTEM_UI_CONTEXT, "SignalIconModel hook",
                    MOBILE_SLOT_UNKNOWN, UNSET_SUB_ID, 0, IosSignalDrawable.NO_SECONDARY_LEVEL,
                    "Failed to hook SignalIconModel debug states: " + t);
        }
    }

    private void hookTelephonyManagerDebugSignals() {
        try {
            for (Method method : TelephonyManager.class.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ("createForSubscriptionId".equals(name)
                        && parameterTypes.length == 1
                        && parameterTypes[0] == int.class
                        && TelephonyManager.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result instanceof TelephonyManager && chain.getArg(0) instanceof Integer) {
                            rememberTelephonyManagerSubId((TelephonyManager) result, (Integer) chain.getArg(0));
                        }
                        return result;
                    });
                } else if ("getSignalStrength".equals(name)
                        && parameterTypes.length == 0
                        && SignalStrength.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        int subId = getKnownTelephonyManagerSubId(chain.getThisObject());
                        if (result instanceof SignalStrength && isValidSubId(subId)) {
                            rememberSignalStrengthSubId((SignalStrength) result, subId);
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook TelephonyManager debug signals", t);
        }
    }

    private void hookSignalStrengthDebugLevels() {
        try {
            for (Method method : SignalStrength.class.getDeclaredMethods()) {
                String name = method.getName();
                if ("getLevel".equals(name)
                        && method.getParameterTypes().length == 0
                        && method.getReturnType() == int.class) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        int fallback = result instanceof Integer ? (Integer) result : 0;
                        int subId = getKnownSignalStrengthSubId(chain.getThisObject());
                        return getDebugSignalLevelForSubId(subId, fallback);
                    });
                } else if ("getCellSignalStrengths".equals(name)) {
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        int subId = getKnownSignalStrengthSubId(chain.getThisObject());
                        if (isValidSubId(subId) && result instanceof Iterable) {
                            for (Object item : (Iterable<?>) result) {
                                if (item instanceof CellSignalStrength) {
                                    rememberCellSignalStrengthSubId((CellSignalStrength) item, subId);
                                }
                            }
                        }
                        return result;
                    });
                }
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook SignalStrength debug levels", t);
        }
    }

    private void hookCellSignalStrengthDebugLevels(ClassLoader loader) {
        String[] classNames = {
                "android.telephony.CellSignalStrengthCdma",
                "android.telephony.CellSignalStrengthGsm",
                "android.telephony.CellSignalStrengthWcdma",
                "android.telephony.CellSignalStrengthTdscdma",
                "android.telephony.CellSignalStrengthLte",
                "android.telephony.CellSignalStrengthNr"
        };
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className, false, loader);
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!"getLevel".equals(method.getName())
                            || method.getParameterTypes().length != 0
                            || method.getReturnType() != int.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        int fallback = result instanceof Integer ? (Integer) result : 0;
                        Object signal = chain.getThisObject();
                        int subId = signal instanceof CellSignalStrength
                                ? getKnownCellSignalStrengthSubId((CellSignalStrength) signal)
                                : UNSET_SUB_ID;
                        return getDebugSignalLevelForSubId(subId, fallback);
                    });
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void hookConnectionRateView(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.statusbar.connectionRateView.ConnectionRateView", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"dispatchDraw".equals(name) && !"onAttachedToWindow".equals(name)
                        && !"onConnectionRateChange".equals(name)
                        && !"onConfigurationChanged".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object thisObject = chain.getThisObject();
                    boolean dispatchDraw = "dispatchDraw".equals(name);
                    if (dispatchDraw) {
                        if (thisObject instanceof View) {
                            View view = (View) thisObject;
                            applyConnectionRateTextScale(view);
                            applyConnectionRateOffset(view);
                            Object canvas = chain.getArg(0);
                            float alignmentOffset = getConnectionRateAlignmentOffset(view)
                                    + getConnectionRateManualDrawOffsetY(view);
                            if (canvas instanceof Canvas && alignmentOffset != 0f) {
                                Canvas drawCanvas = (Canvas) canvas;
                                int saveCount = drawCanvas.save();
                                drawCanvas.translate(0f, alignmentOffset);
                                Object result = chain.proceed();
                                drawCanvas.restoreToCount(saveCount);
                                return result;
                            }
                        }
                        return chain.proceed();
                    }
                    Object result = chain.proceed();
                    if (thisObject instanceof View) {
                        View view = (View) thisObject;
                        trackConnectionRateView(view);
                        ensureConfigRefreshObserver(view.getContext());
                        applyConnectionRateTextScale(view);
                        applyConnectionRateOffset(view);
                        if ("onConnectionRateChange".equals(name) && chain.getArgs().size() == 2
                                && chain.getArg(0) instanceof Boolean) {
                            Object rateArg = chain.getArg(1);
                            double rate = rateArg instanceof Number
                                    ? ((Number) rateArg).doubleValue()
                                    : getDoubleField(view, "mCurrentRate", 0d);
                            applyConnectionRateThresholdVisibility(view,
                                    (Boolean) chain.getArg(0), rate);
                        } else {
                            applyConnectionRateThresholdVisibility(view);
                            view.postDelayed(() -> {
                                applyConnectionRateTextScale(view);
                                applyConnectionRateOffset(view);
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

    private void hookImageViewTintUpdates(ClassLoader loader) {
        try {
            Class<?> clazz = ImageView.class;
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"setImageTintList".equals(name) && !"setColorFilter".equals(name)
                        && !"setImageTintMode".equals(name) && !"setImageResource".equals(name)
                        && !"setImageDrawable".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof ImageView) {
                        ImageView imageView = (ImageView) thisObject;
                        rememberSystemUiContext(imageView.getContext());
                        String idName = getSystemUiIdName(imageView);
                        if ("setImageResource".equals(name) && "mobile_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Integer) {
                            applyMobileSignalResource(imageView, (Integer) chain.getArg(0));
                        }
                        if ("setImageDrawable".equals(name) && "mobile_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Drawable) {
                            Drawable drawable = (Drawable) chain.getArg(0);
                            if (!(drawable instanceof IosSignalDrawable)) {
                                MOBILE_SIGNAL_DRAWABLE_OWNERS.put(drawable, imageView);
                            }
                        }
                        if (("setImageResource".equals(name) || "setImageDrawable".equals(name))
                                && "mobile_signal".equals(idName)
                                && !(imageView.getDrawable() instanceof IosSignalDrawable)) {
                            Config config = Config.load(imageView.getContext());
                            if (config.enabled) {
                                if (isReferenceSignalContextChild(imageView)) {
                                    applyReferenceSignalImageSizing(imageView, config);
                                } else {
                                    applySignalImageSizing(imageView, config);
                                }
                            }
                        }
                        if ("setImageResource".equals(name) && "wifi_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Integer) {
                            TRACKED_WIFI_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
                            applyWifiSignalResource(imageView, (Integer) chain.getArg(0));
                        }
                        if (("setImageResource".equals(name) || "setImageDrawable".equals(name))
                                && isActivityArrowId(idName)) {
                            hideActivityArrowView(imageView);
                        }
                        if ("mobile_signal".equals(idName) && imageView.getDrawable() instanceof IosSignalDrawable) {
                            syncDrawableTint(imageView, (IosSignalDrawable) imageView.getDrawable());
                        } else if ("wifi_signal".equals(idName) && imageView.getDrawable() instanceof IosWifiDrawable) {
                            syncDrawableTint(imageView, (IosWifiDrawable) imageView.getDrawable());
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook ImageView tint updates", t);
        }
    }

    private void hookDrawableLevels() {
        try {
            Method method = Drawable.class.getDeclaredMethod("setLevel", int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof Drawable && chain.getArg(0) instanceof Integer) {
                    ImageView imageView = MOBILE_SIGNAL_DRAWABLE_OWNERS.get((Drawable) thisObject);
                    if (imageView != null) {
                        handleMobileSignalDrawableState(imageView, (Integer) chain.getArg(0));
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            reportSignalDebug(SYSTEM_UI_CONTEXT, "SignalDrawable hook",
                    MOBILE_SLOT_UNKNOWN, UNSET_SUB_ID, 0, IosSignalDrawable.NO_SECONDARY_LEVEL,
                    "Failed to hook SignalDrawable levels: " + t);
        }
    }

    private void hookSignalDrawableLevels(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.settingslib.graph.SignalDrawable", false, loader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"onLevelChange".equals(method.getName()) || method.getParameterTypes().length != 1) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    Object thisObject = chain.getThisObject();
                    if (thisObject instanceof Drawable && chain.getArg(0) instanceof Integer) {
                        ImageView imageView = MOBILE_SIGNAL_DRAWABLE_OWNERS.get((Drawable) thisObject);
                        if (imageView != null) {
                            handleMobileSignalDrawableState(imageView, (Integer) chain.getArg(0));
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable ignored) {
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
                    Config config = Config.load(context);
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
                    Config config = Config.load(view.getContext());
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
        Config config = Config.load(context);
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
        Object barBackground = getField(transitions, "mBarBackground");
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
        Config config = Config.load(context);
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
        Context context = (Context) getField(navigationBar, "mContext");
        if (context == null) {
            return;
        }
        Config config = Config.load(context);
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
        Context context = (Context) getField(drawable, "mContext");
        if (context == null || !(drawable instanceof Drawable)) {
            return false;
        }
        Config config = Config.load(context);
        if (!config.enabled) {
            return false;
        }
        int level = getIntField(drawable, "mLevel", 0);
        boolean pluggedIn = getBooleanField(drawable, "mPluggedIn", false);
        boolean charging = getBooleanField(drawable, "mCharging", false);
        boolean showPercent = getBooleanField(drawable, "mShowPercent", false);
        IosBatteryPainter.draw((Drawable) drawable, canvas, level, pluggedIn, charging, showPercent);
        return true;
    }

    private static boolean drawIosBatteryViewIfNeeded(Object view, Canvas canvas) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        Config config = Config.load(batteryView.getContext());
        if (!config.enabled) {
            return false;
        }
        int level = getIntField(view, "mLastLevel", 0);
        boolean pluggedIn = getBooleanField(view, "mLastPlugged", false);
        boolean charging = getBooleanField(view, "mCharging", false);
        resizeIosBatteryView(batteryView, config, charging);
        boolean showPercent = getBooleanField(view, "mShowBatteryPercent", false);
        int mergedIconsWidth = drawMergedStatusIconsInsideBattery(batteryView, canvas, config);
        int[] batteryRenderSize = getMergedBatteryRenderSize(batteryView, config, charging);
        int width = batteryRenderSize[0];
        int height = batteryRenderSize[1];
        int left = mergedIconsWidth + getMergedBatteryLeadingGap(batteryView, config)
                + dp(batteryView, config.iosBatteryOffsetX);
        int top = Math.round((batteryView.getHeight() - height) / 2f) + dp(batteryView, config.iosBatteryOffsetY);
        int fillColor = normalizeIconColor(getIntField(view, "mFilterColor", Color.BLACK));
        IosBatteryPainter.draw(canvas, new Rect(left, top, left + width, top + height),
                level, pluggedIn, charging, showPercent, config.iosBatteryTextSize,
                fillColor, contrastTextColor(fillColor));
        return true;
    }

    private static boolean measureIosBatteryViewIfNeeded(Object view) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        Config config = Config.load(batteryView.getContext());
        if (!config.enabled) {
            return false;
        }
        boolean charging = getBooleanField(view, "mCharging", false);
        setMeasuredDimension(batteryView, iosBatteryMeasuredWidthWithMergedIcons(batteryView, config, charging),
                iosBatteryMeasuredHeightWithMergedIcons(batteryView, config));
        return true;
    }

    private static int normalizeIconColor(int color) {
        return Color.alpha(color) == 0 ? Color.BLACK : color;
    }

    private static int contrastTextColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return (r * 299 + g * 587 + b * 114) / 1000 < 128 ? Color.WHITE : Color.BLACK;
    }

    private static void resizeIosBatteryView(View view, Config config, boolean charging) {
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

    private static int iosBatteryMeasuredWidth(View view, Config config, boolean charging) {
        return getMergedBatteryRenderSize(view, config, charging)[0];
    }

    private static int iosBatteryMeasuredHeight(View view, Config config) {
        return getMergedBatteryRenderSize(view, config, false)[1] + dp(view, 2);
    }

    private static int iosBatteryMeasuredWidthWithMergedIcons(View view, Config config, boolean charging) {
        return iosBatteryMeasuredWidth(view, config, charging)
                + getMergedStatusIconsWidth(view, config)
                + getMergedBatteryLeadingGap(view, config);
    }

    private static int iosBatteryMeasuredHeightWithMergedIcons(View view, Config config) {
        return Math.max(iosBatteryMeasuredHeight(view, config), getMergedStatusIconsHeight(view, config));
    }

    private static boolean shouldMergeStatusIconsIntoBattery(Config config) {
        return config != null && config.enabled;
    }

    private static int getMergedStatusIconsWidth(View batteryView, Config config) {
        if (!shouldMergeStatusIconsIntoBattery(config)) {
            return 0;
        }
        ArrayList<ImageView> sources = collectBatteryOverlaySignalViews(batteryView, config);
        if (sources.isEmpty()) {
            return 0;
        }
        int width = 0;
        ImageView previous = null;
        for (ImageView source : sources) {
            int[] renderSize = getOverlayRenderSize(source, batteryView, config);
            if (renderSize == null) {
                continue;
            }
            if (previous != null) {
                width += getMergedOverlayGapPx(batteryView, previous, source, config);
            }
            width += renderSize[0];
            previous = source;
        }
        return width;
    }

    private static int getMergedStatusIconsHeight(View batteryView, Config config) {
        if (!shouldMergeStatusIconsIntoBattery(config)) {
            return 0;
        }
        int batteryHeight = getMergedBatteryRenderSize(batteryView, config, false)[1];
        int overlayHeight = hasOverlayRenderSource(batteryView, config) ? getMaxMergedOverlayHeight(batteryView, config) : 0;
        return Math.max(batteryHeight, overlayHeight);
    }

    private static int getMergedBatteryLeadingGap(View batteryView, Config config) {
        return getMergedStatusIconsWidth(batteryView, config) > 0
                ? dp(batteryView, config.iosGroupSignalBatteryGap)
                : 0;
    }

    private static int drawMergedStatusIconsInsideBattery(View batteryView, Canvas canvas, Config config) {
        if (!shouldMergeStatusIconsIntoBattery(config)) {
            return 0;
        }
        ArrayList<ImageView> sources = collectBatteryOverlaySignalViews(batteryView, config);
        if (sources.isEmpty()) {
            return 0;
        }
        int[] batteryRenderSize = getMergedBatteryRenderSize(batteryView, config, false);
        int batteryHeight = batteryRenderSize[1];
        int batteryTop = Math.round((batteryView.getHeight() - batteryHeight) / 2f)
                + dp(batteryView, config.iosBatteryOffsetY);
        float batteryCenterY = batteryTop + batteryHeight / 2f;
        int left = 0;
        ImageView previous = null;
        for (ImageView source : sources) {
            int[] renderSize = getOverlayRenderSize(source, batteryView, config);
            if (renderSize == null) {
                continue;
            }
            if (previous != null) {
                left += getMergedOverlayGapPx(batteryView, previous, source, config);
            }
            int width = renderSize[0];
            int height = renderSize[1];
            int top = Math.round(batteryCenterY - height / 2f);
            drawOverlaySource(source, canvas, left, top, width, height);
            left += width;
            previous = source;
        }
        return left;
    }

    private static ArrayList<ImageView> collectBatteryOverlaySignalViews(View batteryView, Config config) {
        ArrayList<ImageView> result = new ArrayList<>();
        ImageView wifiView = findBatteryOverlayWifiView(batteryView);
        if (shouldIncludeOverlaySource(wifiView, batteryView)) {
            result.add(wifiView);
        }
        for (ImageView mobileView : collectBatteryOverlayMobileViews(batteryView, config)) {
            if (shouldIncludeOverlaySource(mobileView, batteryView) && !result.contains(mobileView)) {
                result.add(mobileView);
            }
        }
        return result;
    }

    private static ArrayList<ImageView> collectBatteryOverlayMobileViews(View batteryView, Config config) {
        ArrayList<ImageView> result = new ArrayList<>();
        ImageView primary = null;
        ImageView secondary = null;
        ImageView fallback = null;
        for (ImageView candidate : collectTrackedMobileSignalViews()) {
            if (!isInSameSystemIconsCluster(candidate, batteryView)) {
                continue;
            }
            MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(candidate);
            if (info == null) {
                info = MOBILE_SIGNAL_RAW_INFOS.get(candidate);
            }
            if (info != null && info.slot == MOBILE_SLOT_PRIMARY && primary == null) {
                primary = candidate;
            } else if (info != null && info.slot == MOBILE_SLOT_SECONDARY && secondary == null) {
                secondary = candidate;
            } else if (fallback == null) {
                fallback = candidate;
            }
        }
        if (primary != null) {
            result.add(primary);
        } else if (fallback != null) {
            result.add(fallback);
        }
        if (!config.iosSignalDualCombined && secondary != null && secondary != primary) {
            result.add(secondary);
        }
        return result;
    }

    private static ImageView findBatteryOverlayWifiView(View batteryView) {
        for (ImageView candidate : new ArrayList<>(WIFI_SIGNAL_LEVELS.keySet())) {
            if (isInSameSystemIconsCluster(candidate, batteryView)) {
                return candidate;
            }
        }
        View clusterRoot = findAncestorByIdName(batteryView, "system_icons");
        if (clusterRoot == null) {
            clusterRoot = batteryView.getParent() instanceof View ? (View) batteryView.getParent() : null;
        }
        View source = clusterRoot == null ? null : findSystemUiChild(clusterRoot, "wifi_signal");
        return source instanceof ImageView ? (ImageView) source : null;
    }

    private static boolean shouldIncludeOverlaySource(ImageView source, View batteryView) {
        return source != null
                && source.getDrawable() != null
                && !isWifiOverlaySourceHidden(source)
                && isInSameSystemIconsCluster(source, batteryView);
    }

    private static boolean isInSameSystemIconsCluster(View candidate, View batteryView) {
        if (candidate == null || batteryView == null) {
            return false;
        }
        View candidateRoot = findAncestorByIdName(candidate, "system_icons");
        View batteryRoot = findAncestorByIdName(batteryView, "system_icons");
        return candidateRoot != null && candidateRoot == batteryRoot;
    }

    private static int getOverlaySourceWidth(ImageView source) {
        int[] originalSize = ORIGINAL_SIZES.get(source);
        if (originalSize != null && originalSize[0] > 0) {
            return originalSize[0];
        }
        ViewGroup.LayoutParams lp = source.getLayoutParams();
        if (lp != null && lp.width > 0) {
            return lp.width;
        }
        if (source.getWidth() > 0) {
            return source.getWidth();
        }
        Drawable drawable = source.getDrawable();
        return drawable == null ? 0 : Math.max(drawable.getIntrinsicWidth(), 0);
    }

    private static int getOverlaySourceHeight(ImageView source) {
        int[] originalSize = ORIGINAL_SIZES.get(source);
        if (originalSize != null && originalSize[1] > 0) {
            return originalSize[1];
        }
        ViewGroup.LayoutParams lp = source.getLayoutParams();
        if (lp != null && lp.height > 0) {
            return lp.height;
        }
        if (source.getHeight() > 0) {
            return source.getHeight();
        }
        Drawable drawable = source.getDrawable();
        return drawable == null ? 0 : Math.max(drawable.getIntrinsicHeight(), 0);
    }

    private static boolean hasOverlayRenderSource(View batteryView, Config config) {
        for (ImageView source : collectBatteryOverlaySignalViews(batteryView, config)) {
            if (getOverlayRenderSize(source, batteryView, config) != null) {
                return true;
            }
        }
        return false;
    }

    private static int getOverlayTargetHeight(View batteryView, Config config) {
        return Math.max(dp(batteryView, config.iosBatteryHeight), 1);
    }

    private static int[] getOverlayRenderSize(ImageView source, View batteryView, Config config) {
        int[] sourceSize = getOverlayReferenceSize(source);
        int sourceWidth = sourceSize[0];
        int sourceHeight = sourceSize[1];
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        int targetHeight = Math.max(1, Math.round(getOverlayTargetHeight(batteryView, config)
                * getMergedOverlayScale(source, config) / 100f));
        int targetWidth = Math.max(1, Math.round(targetHeight * (sourceWidth / (float) sourceHeight)));
        return new int[]{targetWidth, targetHeight};
    }

    private static int[] getOverlayReferenceSize(ImageView source) {
        if (source == null) {
            return new int[]{0, 0};
        }
        if (isMobileSignalOverlaySource(source)) {
            int[] desktopSize = getRecordedSize(DESKTOP_MOBILE_SIGNAL_SIZE);
            if (desktopSize != null && desktopSize[0] > 0 && desktopSize[1] > 0) {
                return desktopSize;
            }
            Drawable drawable = source.getDrawable();
            if (drawable != null) {
                int intrinsicWidth = Math.max(drawable.getIntrinsicWidth(), 0);
                int intrinsicHeight = Math.max(drawable.getIntrinsicHeight(), 0);
                if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                    return new int[]{intrinsicWidth, intrinsicHeight};
                }
            }
        }
        return new int[]{getOverlaySourceWidth(source), getOverlaySourceHeight(source)};
    }

    private static int getMaxMergedOverlayHeight(View batteryView, Config config) {
        int maxHeight = 0;
        for (ImageView source : collectBatteryOverlaySignalViews(batteryView, config)) {
            int[] renderSize = getOverlayRenderSize(source, batteryView, config);
            if (renderSize != null) {
                maxHeight = Math.max(maxHeight, renderSize[1]);
            }
        }
        return maxHeight;
    }

    private static int[] getMergedBatteryRenderSize(View batteryView, Config config, boolean charging) {
        int baseWidth = dp(batteryView, config.iosBatteryWidth);
        int baseHeight = dp(batteryView, config.iosBatteryHeight);
        int scaledWidth = Math.max(1, Math.round(baseWidth * config.iosGroupBatteryScale / 100f));
        int scaledHeight = Math.max(1, Math.round(baseHeight * config.iosGroupBatteryScale / 100f));
        return new int[]{scaledWidth, scaledHeight};
    }

    private static int getMergedOverlayScale(ImageView source, Config config) {
        return isWifiOverlaySource(source) ? config.iosGroupWifiScale : config.iosGroupSignalScale;
    }

    private static int getMergedOverlayGapPx(View batteryView, ImageView previous, ImageView current, Config config) {
        return dp(batteryView, config.iosGroupWifiSignalGap);
    }

    private static boolean isWifiOverlaySource(ImageView source) {
        return "wifi_signal".equals(getSystemUiIdName(source));
    }

    private static boolean isMobileSignalOverlaySource(ImageView source) {
        return "mobile_signal".equals(getSystemUiIdName(source));
    }

    private static void drawOverlaySource(ImageView source, Canvas canvas,
            int left, int top, int width, int height) {
        Drawable drawable = source.getDrawable();
        if (drawable == null || width <= 0 || height <= 0) {
            return;
        }
        Rect oldBounds = new Rect(drawable.getBounds());
        drawable.setBounds(left, top, left + width, top + height);
        drawable.draw(canvas);
        drawable.setBounds(oldBounds);
    }

    private static void refreshBatteryOverlayFor(View sourceView) {
        if (sourceView == null) {
            return;
        }
        View batteryView = findBatteryViewForStatusIcon(sourceView);
        if (batteryView == null) {
            return;
        }
        batteryView.requestLayout();
        batteryView.invalidate();
    }

    private static View findBatteryViewForStatusIcon(View view) {
        View clusterRoot = findAncestorByIdName(view, "system_icons");
        if (!(clusterRoot instanceof ViewGroup)) {
            return null;
        }
        int batteryId = getSystemUiId(view.getContext(), "battery");
        if (batteryId == 0) {
            return null;
        }
        return ((ViewGroup) clusterRoot).findViewById(batteryId);
    }

    private static void applyIosBatteryStyleIfNeeded(Object drawable) {
        Context context = (Context) getField(drawable, "mContext");
        if (context == null) {
            return;
        }
        Config config = Config.load(context);
        if (!config.enabled) {
            return;
        }
        setIntField(drawable, "mDarkModeBackgroundColor", Color.BLACK);
        setIntField(drawable, "mLightModeBackgroundColor", Color.BLACK);
        setIntField(drawable, "mDarkModeFillColor", Color.BLACK);
        setIntField(drawable, "mLightModeFillColor", Color.BLACK);
        setIntField(drawable, "mIconTint", Color.BLACK);
        setPaintColor(drawable, "mFramePaint", Color.BLACK);
        setPaintColor(drawable, "mBatteryPaint", Color.BLACK);
        setPaintColor(drawable, "mTextPaint", Color.WHITE);
        setPaintColor(drawable, "mWarningTextPaint", Color.WHITE);
        setPaintColor(drawable, "mBoltPaint", Color.WHITE);
        setPaintColor(drawable, "mPlusPaint", Color.WHITE);
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

    private static void applyStatusBarSizing(View root) {
        applyStatusBarSizingOnce(root);
        root.postDelayed(() -> applyStatusBarSizingOnce(root), 500);
        root.postDelayed(() -> applyStatusBarSizingOnce(root), 1500);
    }

    private static void applyReferenceSignalSizing(View root) {
        applyReferenceSignalSizingOnce(root);
        root.postDelayed(() -> applyReferenceSignalSizingOnce(root), 500);
        root.postDelayed(() -> applyReferenceSignalSizingOnce(root), 1500);
    }

    private static void applyWifiSizing(View root) {
        applyWifiSizingOnce(root);
        root.postDelayed(() -> applyWifiSizingOnce(root), 300);
        root.postDelayed(() -> applyWifiSizingOnce(root), 1000);
    }

    private static void applyStatusBarSizingOnce(View root) {
        root.post(() -> {
            ensureConfigRefreshObserver(root.getContext());
            Config config = Config.load(root.getContext());
            if (!config.enabled) {
                return;
            }
            normalizeMobileSpacing(root);
            if (shouldUseDesktopSignalReference(root)) {
                View signal = findSystemUiChild(root, "mobile_signal");
                if (signal instanceof ImageView) {
                    applyReferenceSignalImageSizing((ImageView) signal, config);
                    if (shouldMergeStatusIconsIntoBattery(config)) {
                        setMobileSignalViewVisible((ImageView) signal, false);
                    } else {
                        restoreMobileSignalVisibility((ImageView) signal, config);
                    }
                } else {
                    float mobileSignalScale = config.scaled(config.mobileSignalFactor);
                    scaleChild(root, "mobile_signal", mobileSignalScale, mobileSignalScale);
                    applyIosSignalStyle(root, config);
                    offsetChild(root, "mobile_signal",
                            getIosSignalOffsetX(root, config), getIosSignalOffsetY(root, config));
                }
                View type = findSystemUiChild(root, "mobile_type");
                if (type instanceof ImageView) {
                    hideNetworkTypeView((ImageView) type);
                }
            } else {
                float mobileSignalScale = config.scaled(config.mobileSignalFactor);
                scaleChild(root, "mobile_signal", mobileSignalScale, mobileSignalScale);
                applyIosSignalStyle(root, config);
                offsetChild(root, "mobile_signal",
                        getIosSignalOffsetX(root, config), getIosSignalOffsetY(root, config));
                if (shouldMergeStatusIconsIntoBattery(config)) {
                    View signal = findSystemUiChild(root, "mobile_signal");
                    if (signal instanceof ImageView) {
                        setMobileSignalViewVisible((ImageView) signal, false);
                    }
                } else {
                    View signal = findSystemUiChild(root, "mobile_signal");
                    if (signal instanceof ImageView) {
                        restoreMobileSignalVisibility((ImageView) signal, config);
                    }
                }
            }
            scaleChild(root, "wifi_signal", config.scaled(config.wifiSignalFactor), config.scaled(config.wifiSignalFactor));
            applyIosWifiStyle(root, config);
            View type = findSystemUiChild(root, "mobile_type");
            if (type instanceof ImageView) {
                hideNetworkTypeView((ImageView) type);
            }
            hideActivityArrows(root);
            if (shouldRecordDesktopReference(root)) {
                root.post(() -> {
                    recordDesktopIconSize(root, "mobile_signal", DESKTOP_MOBILE_SIGNAL_SIZE);
                });
                root.postDelayed(() -> {
                    recordDesktopIconSize(root, "mobile_signal", DESKTOP_MOBILE_SIGNAL_SIZE);
                }, 300);
            }
        });
    }

    private static void applyWifiSizingOnce(View root) {
        root.post(() -> {
            Config config = Config.load(root.getContext());
            if (!config.enabled) {
                return;
            }
            scaleChild(root, "wifi_signal", config.scaled(config.wifiSignalFactor), config.scaled(config.wifiSignalFactor));
            View wifiIcon = (View) getField(root, "mWifiIcon");
            if (wifiIcon != null) {
                scaleView(wifiIcon, config.scaled(config.wifiSignalFactor), config.scaled(config.wifiSignalFactor));
            }
            applyIosWifiStyle(root, config);
            hideActivityArrows(root);
            View child = findSystemUiChild(root, "wifi_signal");
            if (child instanceof ImageView) {
                boolean visible = !isWifiOverlaySourceHidden((ImageView) child);
                if (shouldMergeStatusIconsIntoBattery(config)) {
                    visible = false;
                }
                setWifiSignalViewVisible((ImageView) child, visible);
            }
        });
    }

    private static void applyReferenceSignalSizingOnce(View root) {
        root.post(() -> {
            ensureConfigRefreshObserver(root.getContext());
            Config config = Config.load(root.getContext());
            if (!config.enabled) {
                return;
            }
            View child = findSystemUiChild(root, "mobile_signal");
            if (child instanceof ImageView) {
                applyReferenceSignalImageSizing((ImageView) child, config);
                if (shouldMergeStatusIconsIntoBattery(config)) {
                    setMobileSignalViewVisible((ImageView) child, false);
                } else {
                    restoreMobileSignalVisibility((ImageView) child, config);
                }
            }
            View type = findSystemUiChild(root, "mobile_type");
            if (type instanceof ImageView) {
                hideNetworkTypeView((ImageView) type);
            }
        });
    }

    private static void applyIosSignalStyle(View root, Config config) {
        View child = findSystemUiChild(root, "mobile_signal");
        if (!(child instanceof ImageView)) {
            return;
        }
        applyIosSignalImageView((ImageView) child, config);
    }

    private static void applyIosWifiStyle(View root, Config config) {
        View child = findSystemUiChild(root, "wifi_signal");
        if (!(child instanceof ImageView)) {
            return;
        }
        ImageView imageView = (ImageView) child;
        if (isWifiOverlaySourceHidden(imageView)) {
            setWifiSignalViewVisible(imageView, false);
            return;
        }
        Integer level = WIFI_SIGNAL_LEVELS.get(imageView);
        if (level != null) {
            applyIosWifiImageView(imageView, level, config);
        }
    }

    private static void applySignalImageSizing(ImageView imageView, Config config) {
        float signalScale = config.scaled(config.mobileSignalFactor);
        scaleView(imageView, signalScale, signalScale);
        applyIosSignalImageView(imageView, config);
    }

    private static void applyReferenceSignalImageSizing(ImageView imageView, Config config) {
        int offsetX = getIosSignalOffsetX(imageView, config);
        int offsetY = getIosSignalOffsetY(imageView, config);
        int[] desktopSize = getRecordedSize(DESKTOP_MOBILE_SIGNAL_SIZE);
        int width = desktopSize == null ? 0 : desktopSize[0];
        int height = desktopSize == null ? 0 : desktopSize[1];
        if (width <= 0 || height <= 0) {
            int baseSize = getSystemUiDimen(imageView.getContext(), "status_bar_mobile_signal_size");
            if (baseSize > 0) {
                width = Math.round(baseSize * config.scaled(config.mobileSignalFactor));
                height = width;
            }
        }
        if (width > 0 && height > 0) {
            ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            if (lp != null) {
                lp.width = width;
                lp.height = height;
                if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                    ((android.widget.FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER;
                }
                imageView.setLayoutParams(lp);
                imageView.requestLayout();
            }
        } else {
            float signalScale = config.scaled(config.mobileSignalFactor);
            scaleView(imageView, signalScale, signalScale);
        }
        applyIosSignalImageView(imageView, config, offsetX, offsetY, false);
        imageView.setTranslationX(dp(imageView, offsetX));
        imageView.setTranslationY(dp(imageView, offsetY));
    }

    private static void hideNetworkTypeView(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        View container = imageView.getParent() instanceof View ? (View) imageView.getParent() : null;
        if (container != null && "mobile_type_container".equals(getSystemUiIdName(container))) {
            setViewCollapsed(container, true);
        }
        setViewCollapsed(imageView, true);
    }

    private static void applyIosSignalImageView(ImageView imageView, Config config) {
        applyIosSignalImageView(imageView, config,
                getIosSignalOffsetX(imageView, config),
                getIosSignalOffsetY(imageView, config),
                true);
    }

    private static void applyIosSignalImageView(ImageView imageView, Config config, boolean applyMarginOffset) {
        applyIosSignalImageView(imageView, config,
                getIosSignalOffsetX(imageView, config),
                getIosSignalOffsetY(imageView, config),
                applyMarginOffset);
    }

    private static void applyIosSignalImageView(ImageView imageView, Config config,
            int offsetXDp, int offsetYDp, boolean applyMarginOffset) {
        MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(imageView);
        if (info == null) {
            info = MOBILE_SIGNAL_RAW_INFOS.get(imageView);
        }
        if (shouldCollapseCombinedSecondarySignal(imageView, config, info)) {
            return;
        }
        int primaryLevel = info == null ? 0 : info.level;
        int secondaryLevel = getDrawableSecondaryLevel(imageView, config, info);
        applyIosSignalImageView(imageView, primaryLevel, secondaryLevel, config);
        if (applyMarginOffset) {
            offsetView(imageView, offsetXDp, offsetYDp);
        }
    }

    private static void applyIosSignalImageView(ImageView imageView, int primaryLevel,
            int secondaryLevel, Config config) {
        TRACKED_MOBILE_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        Drawable current = imageView.getDrawable();
        if (current instanceof IosSignalDrawable) {
            ((IosSignalDrawable) current).setLevels(primaryLevel, secondaryLevel);
            syncDrawableTint(imageView, current);
        } else {
            IosSignalDrawable drawable = new IosSignalDrawable(primaryLevel,
                    imageView.getResources().getDisplayMetrics().density);
            drawable.setLevels(primaryLevel, secondaryLevel);
            syncDrawableTint(imageView, drawable);
            imageView.setImageDrawable(drawable);
        }
        imageView.setAdjustViewBounds(false);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private static boolean shouldCollapseCombinedSecondarySignal(ImageView imageView,
            Config config, MobileSignalInfo info) {
        if (!config.iosSignalDualCombined) {
            return false;
        }
        int slot = info == null ? getKnownMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN) : info.slot;
        if (slot != MOBILE_SLOT_SECONDARY) {
            return false;
        }
        if (info != null) {
            latestSecondarySignalLevel = info.level;
            MOBILE_SIGNAL_INFOS.put(imageView, info);
            SECONDARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
            PRIMARY_SIGNAL_VIEWS.remove(imageView);
        }
        setMobileSignalViewVisible(imageView, false);
        updatePrimarySignalDrawables();
        return true;
    }

    private static int getDrawableSecondaryLevel(ImageView imageView, Config config, MobileSignalInfo info) {
        if (!config.iosSignalDualCombined) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        int slot = info == null ? getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN) : info.slot;
        if (slot != MOBILE_SLOT_PRIMARY || latestSecondarySignalLevel == IosSignalDrawable.NO_SECONDARY_LEVEL) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        return latestSecondarySignalLevel;
    }

    private static void applyMobileSignalResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled) {
            return;
        }
        int subId = getMobileSubId(imageView);
        int slot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
        MobileSignalInfo info = getMobileSignalInfo(imageView.getResources(), resId,
                slot, subId);
        if (info == null) {
            reportSignalDebug(imageView.getContext(), "mobile_signal resource",
                    slot, subId, resId, IosSignalDrawable.NO_SECONDARY_LEVEL,
                    "Unable to parse mobile signal resource: " + getResourceName(imageView.getResources(), resId));
            return;
        }
        reportSignalDebug(imageView.getContext(), "mobile_signal resource",
                info.slot, info.subId, resId, info.level, null);
        applyMobileSignalInfo(imageView, info, config);
    }

    private static void handleMobileSignalDrawableState(ImageView imageView, int state) {
        applyMobileSignalDrawableState(imageView, state);
    }

    private static void applyMobileSignalDrawableState(ImageView imageView, int state) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled) {
            return;
        }
        TRACKED_MOBILE_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        int slot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
        int subId = getMobileSubId(imageView);
        int level = mapSignalDrawableStateLevel(state);
        if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
            reportSignalDebug(imageView.getContext(), "SignalDrawable level",
                    slot, subId, state, level, "Ignored non-signal state");
            return;
        }
        reportSignalDebug(imageView.getContext(), "SignalDrawable level",
                slot, subId, state, level, null);
        applyMobileSignalInfo(imageView, new MobileSignalInfo(slot, level, subId), config);
    }

    private static void applyMobileSignalInfo(ImageView imageView, MobileSignalInfo info, Config config) {
        TRACKED_MOBILE_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        int currentSlot = getMobileSignalSlot(imageView, info.slot);
        if (currentSlot != MOBILE_SLOT_UNKNOWN && currentSlot != info.slot) {
            info = new MobileSignalInfo(currentSlot, info.level, info.subId);
        }
        registerTelephonySignalListener(imageView.getContext(), info.subId);
        if (!config.iosSignalDebugEnabled) {
            rememberTelephonySignalLevel(info.subId, info.level);
        }
        if (!config.iosSignalDebugEnabled) {
            MOBILE_SIGNAL_RAW_INFOS.put(imageView, info);
        }
        MobileSignalInfo displayInfo = info;
        MOBILE_SIGNAL_INFOS.put(imageView, displayInfo);
        if (displayInfo.slot == MOBILE_SLOT_PRIMARY) {
            latestPrimarySignalLevel = displayInfo.level;
            PRIMARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
            SECONDARY_SIGNAL_VIEWS.remove(imageView);
            setMobileSignalViewVisible(imageView, true);
            applyIosSignalImageView(imageView, displayInfo.level, getDrawableSecondaryLevel(imageView, config, displayInfo), config);
        } else if (displayInfo.slot == MOBILE_SLOT_SECONDARY) {
            if (config.iosSignalDebugEnabled && !config.iosSignalDebugSim2Enabled) {
                latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
                SECONDARY_SIGNAL_VIEWS.remove(imageView);
                PRIMARY_SIGNAL_VIEWS.remove(imageView);
                setMobileSignalViewVisible(imageView, false);
                updatePrimarySignalDrawables();
                return;
            }
            latestSecondarySignalLevel = displayInfo.level;
            SECONDARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
            PRIMARY_SIGNAL_VIEWS.remove(imageView);
            if (config.iosSignalDualCombined) {
                setMobileSignalViewVisible(imageView, false);
                updatePrimarySignalDrawables();
            } else {
                setMobileSignalViewVisible(imageView, true);
                applyIosSignalImageView(imageView, displayInfo.level, IosSignalDrawable.NO_SECONDARY_LEVEL, config);
            }
        } else {
            PRIMARY_SIGNAL_VIEWS.remove(imageView);
            SECONDARY_SIGNAL_VIEWS.remove(imageView);
            setMobileSignalViewVisible(imageView, true);
            applyIosSignalImageView(imageView, displayInfo.level, IosSignalDrawable.NO_SECONDARY_LEVEL, config);
        }
        if (shouldMergeStatusIconsIntoBattery(config)) {
            refreshBatteryOverlayFor(imageView);
        }
    }

    private static void updatePrimarySignalDrawables() {
        ArrayList<ImageView> views = new ArrayList<>(PRIMARY_SIGNAL_VIEWS.keySet());
        for (ImageView view : views) {
            if (view == null) {
                continue;
            }
            Config config = Config.load(view.getContext());
            if (!config.enabled || !config.iosSignalDualCombined) {
                continue;
            }
            setMobileSignalViewVisible(view, true);
            MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(view);
            int primaryLevel = info == null ? latestPrimarySignalLevel : info.level;
            int secondaryLevel = latestSecondarySignalLevel;
            applyIosSignalImageView(view, primaryLevel, secondaryLevel, config);
        }
    }

    private static void setMobileSignalViewVisible(ImageView imageView, boolean visible) {
        Config config = Config.load(imageView.getContext());
        if (shouldMergeStatusIconsIntoBattery(config)) {
            View mergedContainer = imageView.getParent() instanceof View
                    ? (View) imageView.getParent() : imageView;
            setViewCollapsed(mergedContainer, true);
            imageView.setVisibility(View.GONE);
            return;
        }
        View container = findMobileSignalContainer(imageView);
        if (container != null && container != imageView) {
            setViewCollapsed(container, !visible);
            imageView.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        setViewCollapsed(imageView, !visible);
    }

    private static View findMobileSignalContainer(ImageView imageView) {
        View current = imageView;
        View mobileContainer = null;
        while (current != null) {
            String idName = getSystemUiIdName(current);
            if ("statusIcons".equals(idName) || "system_icons".equals(idName)) {
                break;
            }
            if (MOBILE_VIEW_SLOTS.containsKey(current) || current.getClass().getName().contains("Mobile")) {
                mobileContainer = current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return mobileContainer == null ? imageView : mobileContainer;
    }

    private static void restoreMobileSignalVisibility(ImageView imageView, Config config) {
        int slot = getKnownMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
        boolean visible = !(config.iosSignalDualCombined && slot == MOBILE_SLOT_SECONDARY);
        setMobileSignalViewVisible(imageView, visible);
    }

    private static void setViewCollapsed(View view, boolean collapsed) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            int[] originalSize = ORIGINAL_SIZES.get(view);
            if (originalSize == null) {
                originalSize = new int[]{lp.width, lp.height};
                ORIGINAL_SIZES.put(view, originalSize);
            }
            if (collapsed) {
                lp.width = 0;
            } else {
                lp.width = originalSize[0];
                lp.height = originalSize[1];
            }
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
                int[] originalMargins = ORIGINAL_MARGINS.get(view);
                if (originalMargins == null) {
                    originalMargins = new int[]{
                            marginLp.leftMargin,
                            marginLp.topMargin,
                            marginLp.rightMargin,
                            marginLp.bottomMargin,
                            marginLp.getMarginStart(),
                            marginLp.getMarginEnd()
                    };
                    ORIGINAL_MARGINS.put(view, originalMargins);
                }
                if (collapsed) {
                    marginLp.leftMargin = 0;
                    marginLp.rightMargin = 0;
                    marginLp.setMarginStart(0);
                    marginLp.setMarginEnd(0);
                } else {
                    marginLp.leftMargin = originalMargins[0];
                    marginLp.topMargin = originalMargins[1];
                    marginLp.rightMargin = originalMargins[2];
                    marginLp.bottomMargin = originalMargins[3];
                    marginLp.setMarginStart(originalMargins[4]);
                    marginLp.setMarginEnd(originalMargins[5]);
                }
            }
            view.setLayoutParams(lp);
        }
        int[] originalPadding = ORIGINAL_PADDINGS.get(view);
        if (originalPadding == null) {
            originalPadding = new int[]{
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            };
            ORIGINAL_PADDINGS.put(view, originalPadding);
        }
        if (collapsed) {
            view.setMinimumWidth(0);
            view.setPadding(0, originalPadding[1], 0, originalPadding[3]);
            view.setVisibility(View.GONE);
        } else {
            view.setPadding(originalPadding[0], originalPadding[1], originalPadding[2], originalPadding[3]);
            view.setVisibility(View.VISIBLE);
        }
        view.requestLayout();
    }

    private static void trackWifiRootView(View view) {
        if (view == null) {
            return;
        }
        TRACKED_WIFI_ROOT_VIEWS.put(view, Boolean.TRUE);
        ensureConfigRefreshObserver(view.getContext());
    }

    private static void rememberRealWifiState(View root, Object state) {
        if (root == null || state == null) {
            return;
        }
        Object copied = copyWifiStateObject(state);
        if (copied != null) {
            LAST_REAL_WIFI_STATES.put(root, copied);
        }
    }

    private static Object copyWifiStateObject(Object state) {
        if (state == null) {
            return null;
        }
        Object copied = invokeNoArg(state, "copy");
        if (copied != null) {
            return copied;
        }
        try {
            Constructor<?> constructor = state.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            Object clone = constructor.newInstance();
            Method copyTo = state.getClass().getDeclaredMethod("copyTo", state.getClass());
            copyTo.setAccessible(true);
            copyTo.invoke(state, clone);
            return clone;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyDebugWifiStates(Config config) {
        Handler handler = MAIN_HANDLER;
        if (handler == null || config == null || !config.enabled || !config.iosWifiDebugEnabled) {
            return;
        }
        handler.post(() -> {
            int injectedCount = 0;
            for (View root : new ArrayList<>(TRACKED_WIFI_ROOT_VIEWS.keySet())) {
                if (root == null) {
                    continue;
                }
                if (applyDebugWifiStateToRoot(root, config)) {
                    injectedCount++;
                }
            }
            if (injectedCount == 0) {
                publishWifiDebugSnapshot(SYSTEM_UI_CONTEXT, "manual hook status refresh",
                        "trackedRoots=0", "No tracked FlymeStatusBarWifiView was found");
            }
        });
    }

    private static void restoreTrackedWifiStates() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            for (View root : new ArrayList<>(TRACKED_WIFI_ROOT_VIEWS.keySet())) {
                if (root == null) {
                    continue;
                }
                Object realState = LAST_REAL_WIFI_STATES.get(root);
                if (realState == null) {
                    continue;
                }
                invokeWifiStateMethod(root, "applyWifiState", copyWifiStateObject(realState));
            }
        });
    }

    private static boolean applyDebugWifiStateToRoot(View root, Config config) {
        if (root == null || config == null) {
            return false;
        }
        Object currentState = getField(root, "mState");
        Object sourceState = currentState != null ? currentState : LAST_REAL_WIFI_STATES.get(root);
        Object debugState = sourceState != null ? copyWifiStateObject(sourceState) : createWifiStateInstance(root);
        if (debugState == null) {
            return false;
        }
        applyDebugWifiStateToObject(root, debugState, config);
        INTERNAL_WIFI_DEBUG_APPLY = true;
        try {
            invokeWifiStateMethod(root, "applyWifiState", debugState);
        } finally {
            INTERNAL_WIFI_DEBUG_APPLY = false;
        }
        applyFlymeWifiStateResource(root, debugState);
        syncWifiRootVisibility(root, debugState);
        return true;
    }

    private static Object createWifiStateInstance(View root) {
        try {
            ClassLoader loader = root == null ? null : root.getClass().getClassLoader();
            Class<?> clazz = Class.forName("com.flyme.systemui.statusbar.net.wifi.WifiIconState",
                    false, loader);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyDebugWifiStateToObject(View root, Object state, Config config) {
        if (root == null || state == null || config == null) {
            return;
        }
        boolean visible = config.iosWifiDebugVisible;
        setBooleanField(state, "visible", visible);
        setBooleanField(state, "activityIn", visible && getBooleanField(state, "activityIn", true));
        setBooleanField(state, "activityOut", visible && getBooleanField(state, "activityOut", true));
        setBooleanField(state, "noDefaultNetwork", !visible);
        setBooleanField(state, "noValidatedNetwork", false);
        setBooleanField(state, "noNetworksAvailable", !visible);
        int resId = visible ? resolveDebugWifiResId(root.getResources(), config.iosWifiDebugLevel) : 0;
        setIntField(state, "resId", resId);
    }

    private static int resolveDebugWifiResId(Resources resources, int level) {
        if (resources == null) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(IosWifiDrawable.MAX_LEVEL, level));
        String packageName = SYSTEM_UI_CONTEXT != null ? SYSTEM_UI_CONTEXT.getPackageName() : SYSTEM_UI;
        String[] candidates = clamped <= 0
                ? new String[]{"stat_sys_wifi_signal_0"}
                : new String[]{
                        "stat_sys_wifi_signal_" + clamped + "_fully_inout",
                        "stat_sys_wifi_signal_" + clamped,
                        "stat_sys_wifi_signal_" + clamped + "_fully_not_inout"
                };
        for (String name : candidates) {
            int resId = resources.getIdentifier(name, "drawable", packageName);
            if (resId != 0) {
                return resId;
            }
        }
        return 0;
    }

    private static void invokeWifiStateMethod(View root, String methodName, Object state) {
        if (root == null || methodName == null || state == null) {
            return;
        }
        try {
            Method method = root.getClass().getDeclaredMethod(methodName, state.getClass());
            method.setAccessible(true);
            method.invoke(root, state);
        } catch (Throwable ignored) {
        }
    }

    private static void syncWifiRootVisibility(View root, Object state) {
        if (root == null || state == null) {
            return;
        }
        Boolean visible = resolveWifiSignalVisibility(state);
        if (visible == null) {
            return;
        }
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        root.requestLayout();
        root.invalidate();
    }

    private static void applyWifiSignalResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled) {
            return;
        }
        TRACKED_WIFI_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        if (shouldHideWifiSignalResource(imageView.getResources(), resId)) {
            reportWifiDebug(imageView.getContext(), "wifi_signal setImageResource",
                    resId, null, Boolean.FALSE, null, null,
                    "resource hidden by name", null);
            applyWifiSignalHidden(imageView, config);
            return;
        }
        Integer level = getWifiSignalLevel(imageView.getResources(), resId);
        if (level == null) {
            reportWifiDebug(imageView.getContext(), "wifi_signal setImageResource",
                    resId, null, Boolean.TRUE, null, null,
                    "resource visible but level parse failed", null);
            return;
        }
        reportWifiDebug(imageView.getContext(), "wifi_signal setImageResource",
                resId, null, Boolean.TRUE, level, null,
                "level parsed from resource", null);
        WIFI_SIGNAL_LEVELS.put(imageView, level);
        applyIosWifiImageView(imageView, level, config);
    }

    private static void applyFlymeWifiStateResource(View root, Object state) {
        Config config = Config.load(root.getContext());
        if (!config.enabled || state == null) {
            return;
        }
        Boolean visibleFromState = resolveWifiSignalVisibility(state);
        int resId = getIntField(state, "resId", 0);
        View child = findSystemUiChild(root, "wifi_signal");
        if (!(child instanceof ImageView)) {
            Object wifiIcon = getField(root, "mWifiIcon");
            child = wifiIcon instanceof View ? (View) wifiIcon : null;
        }
        if (!(child instanceof ImageView)) {
            reportWifiDebug(root.getContext(), "FlymeStatusBarWifiView state",
                    resId, visibleFromState, null, null, state,
                    "wifi_signal child not found", null);
            return;
        }
        ImageView imageView = (ImageView) child;
        TRACKED_WIFI_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        if (visibleFromState != null && !visibleFromState) {
            reportWifiDebug(imageView.getContext(), "FlymeStatusBarWifiView state",
                    resId, visibleFromState, Boolean.FALSE, null, state,
                    "hidden by system state", null);
            applyWifiSignalHidden(imageView, config);
            return;
        }
        if (resId <= 0) {
            if (visibleFromState != null && visibleFromState) {
                WIFI_SIGNAL_HIDDEN_STATES.remove(imageView);
            }
            reportWifiDebug(imageView.getContext(), "FlymeStatusBarWifiView state",
                    resId, visibleFromState, visibleFromState, null, state,
                    "state updated without resource id", null);
            return;
        }
        boolean hidden = visibleFromState == null
                ? shouldHideWifiSignalResource(root.getResources(), resId)
                : !visibleFromState;
        if (hidden) {
            reportWifiDebug(imageView.getContext(), "FlymeStatusBarWifiView state",
                    resId, visibleFromState, Boolean.FALSE, null, state,
                    visibleFromState == null ? "hidden by resource fallback" : "hidden by system state",
                    null);
            applyWifiSignalHidden(imageView, config);
            return;
        }
        Integer level = getWifiSignalLevel(root.getResources(), resId);
        if (level == null) {
            reportWifiDebug(imageView.getContext(), "FlymeStatusBarWifiView state",
                    resId, visibleFromState, Boolean.TRUE, null, state,
                    "visible but level parse failed", null);
            return;
        }
        reportWifiDebug(imageView.getContext(), "FlymeStatusBarWifiView state",
                resId, visibleFromState, Boolean.TRUE, level, state,
                "state and resource both resolved", null);
        WIFI_SIGNAL_LEVELS.put(imageView, level);
        applyIosWifiImageView(imageView, level, config);
    }

    private static void applyIosWifiImageView(ImageView imageView, int level, Config config) {
        WIFI_SIGNAL_HIDDEN_STATES.remove(imageView);
        Drawable current = imageView.getDrawable();
        if (current instanceof IosWifiDrawable) {
            ((IosWifiDrawable) current).setLevelValue(level);
            syncDrawableTint(imageView, current);
        } else {
            IosWifiDrawable drawable = new IosWifiDrawable(level,
                    imageView.getResources().getDisplayMetrics().density);
            syncDrawableTint(imageView, drawable);
            imageView.setImageDrawable(drawable);
        }
        imageView.setAdjustViewBounds(false);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        disableAncestorClipping(imageView, 8);
        applyIosWifiLayout(imageView, config);
        if (shouldMergeStatusIconsIntoBattery(config)) {
            setWifiSignalViewVisible(imageView, false);
            refreshBatteryOverlayFor(imageView);
            return;
        }
        setWifiSignalViewVisible(imageView, true);
    }

    private static void applyWifiSignalHidden(ImageView imageView, Config config) {
        WIFI_SIGNAL_LEVELS.remove(imageView);
        WIFI_SIGNAL_HIDDEN_STATES.put(imageView, Boolean.TRUE);
        setWifiSignalViewVisible(imageView, false);
        if (shouldMergeStatusIconsIntoBattery(config)) {
            refreshBatteryOverlayFor(imageView);
        }
    }

    private static Boolean resolveWifiSignalVisibility(Object state) {
        if (state == null) {
            return null;
        }
        Boolean explicitVisible = findBooleanMember(state,
                "visible", "mVisible", "iconVisible", "isIconVisible",
                "wifiVisible", "isWifiVisible", "shouldShow", "show");
        if (explicitVisible != null) {
            return explicitVisible;
        }
        Boolean enabled = findBooleanMember(state,
                "enabled", "mEnabled", "wifiEnabled", "isWifiEnabled");
        if (enabled != null && !enabled) {
            return false;
        }
        Boolean connected = findBooleanMember(state,
                "connected", "mConnected", "wifiConnected", "isWifiConnected");
        if (connected != null) {
            return connected;
        }
        return null;
    }

    private static Boolean findBooleanMember(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        for (String name : names) {
            Object fieldValue = getField(target, name);
            if (fieldValue instanceof Boolean) {
                return (Boolean) fieldValue;
            }
            Object methodValue = invokeNoArg(target, name);
            if (methodValue instanceof Boolean) {
                return (Boolean) methodValue;
            }
            if (name != null && name.length() > 0) {
                String normalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                Object getterValue = invokeNoArg(target, "get" + normalized);
                if (getterValue instanceof Boolean) {
                    return (Boolean) getterValue;
                }
                Object isValue = invokeNoArg(target, "is" + normalized);
                if (isValue instanceof Boolean) {
                    return (Boolean) isValue;
                }
            }
        }
        return null;
    }

    private static void applyIosWifiLayout(ImageView imageView, Config config) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp != null) {
            float scale = config.scaled(config.wifiSignalFactor);
            lp.width = Math.round(dp(imageView, config.iosWifiWidth) * scale);
            lp.height = Math.round(dp(imageView, config.iosWifiHeight) * scale);
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                ((android.widget.FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER;
            }
            imageView.setLayoutParams(lp);
            imageView.requestLayout();
        }
        disableAncestorClipping(imageView, 8);
        offsetView(imageView, config.iosWifiOffsetX, config.iosWifiOffsetY);
    }

    private static void hideActivityArrows(View root) {
        if (root == null) {
            return;
        }
        hideActivityArrowChild(root, "mobile_in");
        hideActivityArrowChild(root, "mobile_out");
        hideActivityArrowChild(root, "wifi_in");
        hideActivityArrowChild(root, "wifi_out");
        hideActivityArrowChild(root, "mobile_inout");
        View container = findSystemUiChild(root, "inout_container");
        if (container != null) {
            setViewCollapsed(container, true);
        }
    }

    private static void hideActivityArrowChild(View root, String idName) {
        View child = findSystemUiChild(root, idName);
        if (child != null) {
            hideActivityArrowView(child);
        }
    }

    private static boolean isActivityArrowId(String idName) {
        return "mobile_in".equals(idName) || "mobile_out".equals(idName)
                || "wifi_in".equals(idName) || "wifi_out".equals(idName)
                || "mobile_inout".equals(idName);
    }

    private static void hideActivityArrowView(View view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            int[] originalSize = ORIGINAL_SIZES.get(view);
            if (originalSize == null) {
                originalSize = new int[]{lp.width, lp.height};
                ORIGINAL_SIZES.put(view, originalSize);
            }
            lp.width = 0;
            lp.height = 0;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
                marginLp.leftMargin = 0;
                marginLp.topMargin = 0;
                marginLp.rightMargin = 0;
                marginLp.bottomMargin = 0;
                marginLp.setMarginStart(0);
                marginLp.setMarginEnd(0);
            }
            view.setLayoutParams(lp);
        }
        view.setMinimumWidth(0);
        view.setPadding(0, 0, 0, 0);
        view.setVisibility(View.GONE);
        view.requestLayout();
    }

    private static void setWifiSignalViewVisible(ImageView imageView, boolean visible) {
        Config config = Config.load(imageView.getContext());
        if (shouldMergeStatusIconsIntoBattery(config)) {
            visible = false;
        }
        View container = findWifiSignalContainer(imageView);
        if (container != null && container != imageView) {
            setViewCollapsed(container, !visible);
            imageView.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        setViewCollapsed(imageView, !visible);
    }

    private static View findWifiSignalContainer(ImageView imageView) {
        View current = imageView;
        View wifiContainer = null;
        while (current != null) {
            String idName = getSystemUiIdName(current);
            if ("statusIcons".equals(idName) || "system_icons".equals(idName)) {
                break;
            }
            if ("wifi_group".equals(idName) || current.getClass().getName().contains("Wifi")) {
                wifiContainer = current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return wifiContainer == null ? imageView : wifiContainer;
    }

    private static void logToFramework(String message) {
        android.util.Log.w(TAG, message);
        FlymeStatusBarSizer module = MODULE;
        if (module == null) {
            return;
        }
        try {
            module.log(android.util.Log.WARN, TAG, message, null);
        } catch (Throwable ignored) {
        }
    }

    private static void reportSignalDebug(Context context, String source, int slot, int subId,
            int state, int level, String error) {
        Context targetContext = context != null ? context : SYSTEM_UI_CONTEXT;
        if (targetContext == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if ((error == null || error.length() == 0)
                && now - LAST_SIGNAL_DEBUG_REPORT_UPTIME < SIGNAL_DEBUG_REPORT_MIN_INTERVAL_MS) {
            return;
        }
        LAST_SIGNAL_DEBUG_REPORT_UPTIME = now;
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            String levelText = level == IosSignalDrawable.NO_SECONDARY_LEVEL
                    ? "unknown" : Integer.toString(level);
            String stateText = "0x" + Integer.toHexString(state);
            String summary = time
                    + "\nsource=" + safeDebugText(source)
                    + "\nslot=" + slotToDebugText(slot)
                    + "\nsubId=" + subId
                    + "\nstate=" + stateText
                    + "\nlevel=" + levelText;
            if (error != null && error.length() > 0) {
                summary += "\nerror=" + error;
            }
            ContentValues values = new ContentValues();
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY, summary);
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_LEVEL, levelText);
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SLOT, slotToDebugText(slot));
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID, Integer.toString(subId));
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_STATE, stateText);
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SOURCE, safeDebugText(source));
            values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_ERROR, error == null ? "" : error);
            targetContext.getContentResolver().update(SETTINGS_URI, values, null, null);
        } catch (Throwable ignored) {
        }
    }

    private static void reportCurrentTrackedSignalState(Context context, String source) {
        ImageView primaryView = null;
        ImageView secondaryView = null;
        MobileSignalInfo primaryInfo = null;
        MobileSignalInfo secondaryInfo = null;
        ArrayList<ImageView> views = collectTrackedMobileSignalViews();
        for (ImageView view : views) {
            MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(view);
            if (info == null) {
                info = MOBILE_SIGNAL_RAW_INFOS.get(view);
            }
            if (view == null || info == null) {
                continue;
            }
            if (info.slot == MOBILE_SLOT_PRIMARY && primaryInfo == null) {
                primaryView = view;
                primaryInfo = info;
            } else if (info.slot == MOBILE_SLOT_SECONDARY && secondaryInfo == null) {
                secondaryView = view;
                secondaryInfo = info;
            }
        }
        if (primaryInfo == null && secondaryInfo == null) {
            reportSignalDebug(context, source, MOBILE_SLOT_UNKNOWN, UNSET_SUB_ID,
                    0, IosSignalDrawable.NO_SECONDARY_LEVEL,
                    "No tracked mobile signal info yet");
            return;
        }
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String summary = time
                + "\nsource=" + safeDebugText(source)
                + "\nprimary=" + signalInfoSummary(primaryView, primaryInfo)
                + "\nsecondary=" + signalInfoSummary(secondaryView, secondaryInfo)
                + "\nlatestPrimary=" + latestPrimarySignalLevel
                + "\nlatestSecondary=" + (latestSecondarySignalLevel == IosSignalDrawable.NO_SECONDARY_LEVEL
                        ? "unknown" : Integer.toString(latestSecondarySignalLevel))
                + "\ntrackedViews=" + views.size();
        ContentValues values = new ContentValues();
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUMMARY, summary);
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SOURCE, safeDebugText(source));
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SLOT,
                primaryInfo != null && secondaryInfo != null ? "primary+secondary"
                        : primaryInfo != null ? "primary" : "secondary");
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_LEVEL,
                primaryInfo == null ? "" : Integer.toString(primaryInfo.level));
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_SUB_ID,
                primaryInfo == null ? "" : Integer.toString(primaryInfo.subId));
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_STATE,
                primaryView == null ? "" : "0x" + Integer.toHexString(getTrackedSignalDrawableState(primaryView)));
        values.put(SettingsStore.KEY_RUNTIME_SIGNAL_DEBUG_ERROR, "");
        try {
            Context targetContext = primaryView != null ? primaryView.getContext()
                    : secondaryView != null ? secondaryView.getContext() : context;
            if (targetContext != null) {
                targetContext.getContentResolver().update(SETTINGS_URI, values, null, null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void reportWifiDebug(Context context, String source, int resId,
            Boolean visibleFromState, Boolean visibleResolved, Integer level,
            Object state, String note, String error) {
        Context targetContext = context != null ? context : SYSTEM_UI_CONTEXT;
        if (targetContext == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if ((error == null || error.length() == 0)
                && now - LAST_WIFI_DEBUG_REPORT_UPTIME < WIFI_DEBUG_REPORT_MIN_INTERVAL_MS) {
            return;
        }
        LAST_WIFI_DEBUG_REPORT_UPTIME = now;
        String resIdText = resId > 0 ? Integer.toString(resId) : "";
        String resName = resId > 0 ? safeDebugText(getResourceName(targetContext.getResources(), resId)) : "";
        String visibleText = booleanToDebugText(visibleResolved);
        String levelText = level == null ? "unknown" : Integer.toString(level);
        String stateText = state == null ? "" : state.getClass().getName();
        publishWifiDebugEvent(targetContext, source, visibleText, levelText, resIdText, resName,
                stateText, visibleFromState, note, error);
    }

    private static void reportCurrentTrackedWifiState(Context context, String source) {
        ArrayList<ImageView> views = collectTrackedWifiSignalViews();
        int trackedRoots = TRACKED_WIFI_ROOT_VIEWS.size();
        ImageView trackedView = null;
        for (ImageView candidate : views) {
            if (candidate != null) {
                trackedView = candidate;
                break;
            }
        }
        if (trackedView == null
                && LAST_WIFI_DEBUG_SOURCE.length() == 0
                && LAST_WIFI_DEBUG_RES_NAME.length() == 0
                && LAST_WIFI_DEBUG_LEVEL.length() == 0) {
            publishWifiDebugSnapshot(context, source,
                    "trackedViews=0, trackedRoots=" + trackedRoots,
                    "No tracked Wi-Fi signal view yet");
            return;
        }
        Integer level = trackedView == null ? null : WIFI_SIGNAL_LEVELS.get(trackedView);
        Boolean hidden = trackedView == null ? null : WIFI_SIGNAL_HIDDEN_STATES.get(trackedView);
        Boolean visibleResolved = hidden == null ? (level == null ? null : Boolean.TRUE) : !hidden;
        String drawableName = "";
        if (trackedView != null && trackedView.getDrawable() != null) {
            drawableName = trackedView.getDrawable().getClass().getSimpleName();
        }
        String note = "trackedViews=" + views.size() + ", trackedRoots=" + trackedRoots;
        if (trackedView != null) {
            note += ", hasDrawable=" + (trackedView.getDrawable() != null);
            if (drawableName.length() > 0) {
                note += ", drawable=" + drawableName;
            }
        }
        publishWifiDebugSnapshot(context, source,
                "visible=" + (visibleResolved != null ? booleanToDebugText(visibleResolved)
                        : fallbackWifiDebugValue(LAST_WIFI_DEBUG_VISIBLE, "unknown"))
                        + "\nlevel=" + (level != null ? Integer.toString(level)
                        : fallbackWifiDebugValue(LAST_WIFI_DEBUG_LEVEL, "unknown"))
                        + "\nresId=" + fallbackWifiDebugValue(LAST_WIFI_DEBUG_RES_ID, "")
                        + "\nresName=" + safeDebugText(LAST_WIFI_DEBUG_RES_NAME)
                        + "\nlastEventSource=" + safeDebugText(LAST_WIFI_DEBUG_SOURCE)
                        + "\nlastEventVisibleFromState=" + fallbackWifiDebugValue(LAST_WIFI_DEBUG_VISIBLE_FROM_STATE, "unknown")
                        + "\nlastEventState=" + safeDebugText(LAST_WIFI_DEBUG_STATE)
                        + "\nnote=" + note,
                LAST_WIFI_DEBUG_ERROR);
    }

    private static void publishWifiDebugEvent(Context context, String source, String visibleText,
            String levelText, String resIdText, String resName, String stateText,
            Boolean visibleFromState, String note, String error) {
        Context targetContext = context != null ? context : SYSTEM_UI_CONTEXT;
        if (targetContext == null) {
            return;
        }
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            String summary = time
                    + "\nsource=" + safeDebugText(source)
                    + "\nvisible=" + fallbackWifiDebugValue(visibleText, "unknown")
                    + "\nvisibleFromState=" + booleanToDebugText(visibleFromState)
                    + "\nlevel=" + fallbackWifiDebugValue(levelText, "unknown")
                    + "\nresId=" + fallbackWifiDebugValue(resIdText, "")
                    + "\nresName=" + safeDebugText(resName)
                    + "\nstate=" + safeDebugText(stateText);
            if (note != null && note.length() > 0) {
                summary += "\nnote=" + note;
            }
            if (error != null && error.length() > 0) {
                summary += "\nerror=" + error;
            }
            LAST_WIFI_DEBUG_SOURCE = safeDebugText(source);
            LAST_WIFI_DEBUG_VISIBLE = safeDebugText(visibleText);
            LAST_WIFI_DEBUG_VISIBLE_FROM_STATE = booleanToDebugText(visibleFromState);
            LAST_WIFI_DEBUG_LEVEL = safeDebugText(levelText);
            LAST_WIFI_DEBUG_RES_ID = safeDebugText(resIdText);
            LAST_WIFI_DEBUG_RES_NAME = safeDebugText(resName);
            LAST_WIFI_DEBUG_STATE = safeDebugText(stateText);
            LAST_WIFI_DEBUG_ERROR = safeDebugText(error);
            ContentValues values = new ContentValues();
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SUMMARY, summary);
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_LEVEL, safeDebugText(levelText));
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_ID, safeDebugText(resIdText));
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_RES_NAME, safeDebugText(resName));
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_VISIBLE, safeDebugText(visibleText));
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SOURCE, safeDebugText(source));
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_ERROR, error == null ? "" : error);
            targetContext.getContentResolver().update(SETTINGS_URI, values, null, null);
        } catch (Throwable ignored) {
        }
    }

    private static void publishWifiDebugSnapshot(Context context, String source,
            String body, String error) {
        Context targetContext = context != null ? context : SYSTEM_UI_CONTEXT;
        if (targetContext == null) {
            return;
        }
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            String summary = time
                    + "\nsource=" + safeDebugText(source);
            if (body != null && body.length() > 0) {
                summary += "\n" + body;
            }
            if (error != null && error.length() > 0) {
                summary += "\nerror=" + error;
            }
            ContentValues values = new ContentValues();
            values.put(SettingsStore.KEY_RUNTIME_WIFI_DEBUG_SNAPSHOT, summary);
            targetContext.getContentResolver().update(SETTINGS_URI, values, null, null);
        } catch (Throwable ignored) {
        }
    }

    private static ArrayList<ImageView> collectTrackedWifiSignalViews() {
        ArrayList<ImageView> views = new ArrayList<>();
        addTrackedWifiSignalViews(views, TRACKED_WIFI_SIGNAL_VIEWS);
        addTrackedWifiSignalViews(views, WIFI_SIGNAL_LEVELS);
        addTrackedWifiSignalViews(views, WIFI_SIGNAL_HIDDEN_STATES);
        return views;
    }

    private static void addTrackedWifiSignalViews(ArrayList<ImageView> views,
            WeakHashMap<ImageView, ?> source) {
        for (ImageView view : new ArrayList<>(source.keySet())) {
            if (view != null && !views.contains(view)) {
                views.add(view);
            }
        }
    }

    private static String booleanToDebugText(Boolean value) {
        if (value == null) {
            return "unknown";
        }
        return value ? "true" : "false";
    }

    private static String fallbackWifiDebugValue(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static String signalInfoSummary(ImageView view, MobileSignalInfo info) {
        if (info == null) {
            return "none";
        }
        return "slot=" + slotToDebugText(info.slot)
                + ", subId=" + info.subId
                + ", level=" + info.level
                + ", state=0x" + Integer.toHexString(getTrackedSignalDrawableState(view))
                + ", hasView=" + (view != null);
    }

    private static int getTrackedSignalDrawableState(ImageView imageView) {
        if (imageView == null) {
            return 0;
        }
        for (Drawable drawable : new ArrayList<>(MOBILE_SIGNAL_DRAWABLE_OWNERS.keySet())) {
            if (MOBILE_SIGNAL_DRAWABLE_OWNERS.get(drawable) == imageView) {
                return drawable == null ? 0 : drawable.getLevel();
            }
        }
        return 0;
    }

    private static String safeDebugText(String value) {
        return value == null ? "" : value;
    }

    private static String slotToDebugText(int slot) {
        if (slot == MOBILE_SLOT_PRIMARY) {
            return "primary";
        }
        if (slot == MOBILE_SLOT_SECONDARY) {
            return "secondary";
        }
        return "unknown";
    }

    private static void registerMobileViewSlot(View view) {
        if (view == null || !view.getClass().getName().contains("Mobile")) {
            return;
        }
        int slot = getMobileSlotFromView(view);
        if (slot == MOBILE_SLOT_UNKNOWN) {
            return;
        }
        MOBILE_VIEW_SLOTS.put(view, slot);
        View signal = findSystemUiChild(view, "mobile_signal");
        if (signal instanceof ImageView) {
            TRACKED_MOBILE_SIGNAL_VIEWS.put((ImageView) signal, Boolean.TRUE);
            int subId = invokeNoArgInt(view, "getSubId", getIntField(view, "subId", UNSET_SUB_ID));
            int level = getCachedTelephonySignalLevel(subId);
            if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                level = getLastKnownSignalLevelForSlot(slot);
            }
            MobileSignalInfo info = new MobileSignalInfo(slot, level, subId);
            MOBILE_SIGNAL_RAW_INFOS.put((ImageView) signal, info);
            MOBILE_SIGNAL_INFOS.put((ImageView) signal, info);
        }
    }

    private static void recordFlymeSlotIndex(int subId, int slotIndex) {
        if (subId < 0 || slotIndex < 0) {
            return;
        }
        synchronized (FLYME_SLOT_INDEX_BY_SUB_ID) {
            FLYME_SLOT_INDEX_BY_SUB_ID.put(subId, slotIndex);
        }
    }

    private static int getRecordedFlymeSlotForSubId(int subId) {
        synchronized (FLYME_SLOT_INDEX_BY_SUB_ID) {
            Integer slotIndex = FLYME_SLOT_INDEX_BY_SUB_ID.get(subId);
            if (slotIndex == null) {
                return MOBILE_SLOT_UNKNOWN;
            }
            return mobileSlotFromFlymeIndexLocked(slotIndex);
        }
    }

    private static int mobileSlotFromFlymeIndexLocked(int slotIndex) {
        boolean hasZero = false;
        for (Integer value : FLYME_SLOT_INDEX_BY_SUB_ID.values()) {
            if (value != null && value == 0) {
                hasZero = true;
                break;
            }
        }
        if (hasZero) {
            return mobileSlotFromZeroBasedIndex(slotIndex);
        }
        return mobileSlotFromOneBasedIndex(slotIndex);
    }

    private static int mobileSlotFromZeroBasedIndex(int slotIndex) {
        if (slotIndex == 0) {
            return MOBILE_SLOT_PRIMARY;
        }
        if (slotIndex == 1) {
            return MOBILE_SLOT_SECONDARY;
        }
        return MOBILE_SLOT_UNKNOWN;
    }

    private static int mobileSlotFromOneBasedIndex(int slotIndex) {
        if (slotIndex == 1) {
            return MOBILE_SLOT_PRIMARY;
        }
        if (slotIndex == 2) {
            return MOBILE_SLOT_SECONDARY;
        }
        return MOBILE_SLOT_UNKNOWN;
    }

    private static int getKnownMobileSignalSlot(ImageView imageView, int fallback) {
        MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(imageView);
        if (info != null && info.slot != MOBILE_SLOT_UNKNOWN) {
            return info.slot;
        }
        info = MOBILE_SIGNAL_RAW_INFOS.get(imageView);
        if (info != null && info.slot != MOBILE_SLOT_UNKNOWN) {
            return info.slot;
        }
        return getMobileSignalSlot(imageView, fallback);
    }

    private static ArrayList<ImageView> collectTrackedMobileSignalViews() {
        ArrayList<ImageView> views = new ArrayList<>();
        addTrackedMobileSignalViews(views, TRACKED_MOBILE_SIGNAL_VIEWS);
        addTrackedMobileSignalViews(views, MOBILE_SIGNAL_INFOS);
        addTrackedMobileSignalViews(views, MOBILE_SIGNAL_RAW_INFOS);
        addTrackedMobileSignalViews(views, PRIMARY_SIGNAL_VIEWS);
        addTrackedMobileSignalViews(views, SECONDARY_SIGNAL_VIEWS);
        return views;
    }

    private static void addTrackedMobileSignalViews(ArrayList<ImageView> views,
            WeakHashMap<ImageView, ?> source) {
        for (ImageView view : new ArrayList<>(source.keySet())) {
            if (view != null && !views.contains(view)) {
                views.add(view);
            }
        }
    }

    private static int getMobileSignalSlot(ImageView imageView, int fallback) {
        View current = imageView;
        while (current != null) {
            if (current.getClass().getName().contains("Mobile")) {
                int viewSlot = getMobileSlotFromView(current);
                if (viewSlot != MOBILE_SLOT_UNKNOWN) {
                    MOBILE_VIEW_SLOTS.put(current, viewSlot);
                    return viewSlot;
                }
            }
            Integer slot = MOBILE_VIEW_SLOTS.get(current);
            if (slot != null && slot != MOBILE_SLOT_UNKNOWN) {
                return slot;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return fallback;
    }

    private static int getMobileSubId(ImageView imageView) {
        View current = imageView;
        while (current != null) {
            if (current.getClass().getName().contains("Mobile")) {
                int subId = invokeNoArgInt(current, "getSubId", getIntField(current, "subId", UNSET_SUB_ID));
                if (subId != UNSET_SUB_ID && subId >= 0) {
                    return subId;
                }
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return UNSET_SUB_ID;
    }

    private static int getLastKnownSignalLevelForSlot(int slot) {
        if (slot == MOBILE_SLOT_PRIMARY) {
            return latestPrimarySignalLevel;
        }
        if (slot == MOBILE_SLOT_SECONDARY) {
            return latestSecondarySignalLevel == IosSignalDrawable.NO_SECONDARY_LEVEL
                    ? 0 : latestSecondarySignalLevel;
        }
        return latestPrimarySignalLevel;
    }

    private static int queryTelephonySignalLevel(Context context, int subId) {
        if (context == null || subId == UNSET_SUB_ID || subId < 0) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (manager == null) {
                return IosSignalDrawable.NO_SECONDARY_LEVEL;
            }
            SignalStrength strength = manager.createForSubscriptionId(subId).getSignalStrength();
            if (strength == null) {
                return IosSignalDrawable.NO_SECONDARY_LEVEL;
            }
            int level = mapMobileSignalLevel(strength.getLevel());
            rememberTelephonySignalLevel(subId, level);
            return level;
        } catch (Throwable ignored) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
    }

    private static int getCachedTelephonySignalLevel(int subId) {
        synchronized (TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID) {
            Integer level = TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID.get(subId);
            Long uptime = TELEPHONY_SIGNAL_LEVEL_TIMES_BY_SUB_ID.get(subId);
            if (level != null && uptime != null
                    && SystemClock.uptimeMillis() - uptime <= TELEPHONY_CACHE_TTL_MS) {
                return level;
            }
        }
        return IosSignalDrawable.NO_SECONDARY_LEVEL;
    }

    private static void rememberTelephonySignalLevel(int subId, int level) {
        if (!isValidSubId(subId)) {
            return;
        }
        synchronized (TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID) {
            TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID.put(subId, level);
            TELEPHONY_SIGNAL_LEVEL_TIMES_BY_SUB_ID.put(subId, SystemClock.uptimeMillis());
        }
    }

    private static void registerTelephonySignalListener(Context context, int subId) {
        if (context == null || subId == UNSET_SUB_ID || subId < 0) {
            return;
        }
        synchronized (SIGNAL_LISTENERS_BY_SUB_ID) {
            if (SIGNAL_LISTENERS_BY_SUB_ID.containsKey(subId)) {
                return;
            }
            try {
                TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (manager == null) {
                    return;
                }
                Config config = Config.load(context);
                if (!config.iosSignalDebugEnabled) {
                    int initialLevel = queryTelephonySignalLevel(context, subId);
                    if (initialLevel != IosSignalDrawable.NO_SECONDARY_LEVEL) {
                        rememberTelephonySignalLevel(subId, initialLevel);
                    }
                }
                PhoneStateListener listener = new PhoneStateListener(context.getMainExecutor()) {
                    @Override
                    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                        if (signalStrength == null) {
                            return;
                        }
                        updateSignalLevelForSubId(subId, signalStrength.getLevel());
                    }
                };
                manager.createForSubscriptionId(subId).listen(listener,
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                SIGNAL_LISTENERS_BY_SUB_ID.put(subId, listener);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void updateSignalLevelForSubId(int subId, int level) {
        Handler handler = MAIN_HANDLER;
        Runnable action = () -> {
            int mappedLevel = mapMobileSignalLevel(level);
            rememberTelephonySignalLevel(subId, mappedLevel);
            ArrayList<ImageView> signalViews = new ArrayList<>(MOBILE_SIGNAL_RAW_INFOS.keySet());
            for (ImageView imageView : signalViews) {
                MobileSignalInfo info = MOBILE_SIGNAL_RAW_INFOS.get(imageView);
                if (imageView == null || info == null || info.subId != subId) {
                    continue;
                }
                Config config = Config.load(imageView.getContext());
                if (!config.enabled) {
                    continue;
                }
                applyMobileSignalInfo(imageView,
                        new MobileSignalInfo(info.slot, mappedLevel, info.subId), config);
                imageView.requestLayout();
                imageView.invalidate();
            }
        };
        if (handler != null) {
            handler.post(action);
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        }
    }

    private static void rememberSystemUiContext(Context context) {
        if (context == null || SYSTEM_UI_CONTEXT != null) {
            return;
        }
        SYSTEM_UI_CONTEXT = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    private static boolean isValidSubId(int subId) {
        return subId != UNSET_SUB_ID && subId >= 0;
    }

    private static void rememberTelephonyManagerSubId(TelephonyManager manager, int subId) {
        if (manager == null || !isValidSubId(subId)) {
            return;
        }
        synchronized (TELEPHONY_MANAGER_SUB_IDS) {
            TELEPHONY_MANAGER_SUB_IDS.put(manager, subId);
        }
    }

    private static int getKnownTelephonyManagerSubId(Object manager) {
        if (manager instanceof TelephonyManager) {
            synchronized (TELEPHONY_MANAGER_SUB_IDS) {
                Integer subId = TELEPHONY_MANAGER_SUB_IDS.get((TelephonyManager) manager);
                if (subId != null) {
                    return subId;
                }
            }
        }
        int subId = invokeNoArgInt(manager, "getSubscriptionId", UNSET_SUB_ID);
        if (isValidSubId(subId)) {
            return subId;
        }
        return getIntField(manager, "mSubId", UNSET_SUB_ID);
    }

    private static void rememberSignalStrengthSubId(SignalStrength signalStrength, int subId) {
        if (signalStrength == null || !isValidSubId(subId)) {
            return;
        }
        synchronized (SIGNAL_STRENGTH_SUB_IDS) {
            SIGNAL_STRENGTH_SUB_IDS.put(signalStrength, subId);
        }
        try {
            for (CellSignalStrength cellSignalStrength : signalStrength.getCellSignalStrengths()) {
                rememberCellSignalStrengthSubId(cellSignalStrength, subId);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int getKnownSignalStrengthSubId(Object signalStrength) {
        if (!(signalStrength instanceof SignalStrength)) {
            return UNSET_SUB_ID;
        }
        synchronized (SIGNAL_STRENGTH_SUB_IDS) {
            Integer subId = SIGNAL_STRENGTH_SUB_IDS.get((SignalStrength) signalStrength);
            return subId == null ? UNSET_SUB_ID : subId;
        }
    }

    private static void rememberCellSignalStrengthSubId(CellSignalStrength cellSignalStrength, int subId) {
        if (cellSignalStrength == null || !isValidSubId(subId)) {
            return;
        }
        synchronized (CELL_SIGNAL_STRENGTH_SUB_IDS) {
            CELL_SIGNAL_STRENGTH_SUB_IDS.put(cellSignalStrength, subId);
        }
    }

    private static int getKnownCellSignalStrengthSubId(CellSignalStrength cellSignalStrength) {
        if (cellSignalStrength == null) {
            return UNSET_SUB_ID;
        }
        synchronized (CELL_SIGNAL_STRENGTH_SUB_IDS) {
            Integer subId = CELL_SIGNAL_STRENGTH_SUB_IDS.get(cellSignalStrength);
            return subId == null ? UNSET_SUB_ID : subId;
        }
    }

    private static int getDebugSignalLevelForSubId(int subId, int fallbackLevel) {
        if (!isValidSubId(subId)) {
            return fallbackLevel;
        }
        Config config = getCachedOrLoadedConfig();
        if (config == null || !config.enabled || !config.iosSignalDebugEnabled) {
            return fallbackLevel;
        }
        int slot = getMobileSlotForSubId(SYSTEM_UI_CONTEXT, subId);
        if (slot == MOBILE_SLOT_PRIMARY) {
            return config.iosSignalDebugSim1Enabled
                    ? mapMobileSignalLevel(config.iosSignalDebugSim1Level) : 0;
        }
        if (slot == MOBILE_SLOT_SECONDARY) {
            return config.iosSignalDebugSim2Enabled
                    ? mapMobileSignalLevel(config.iosSignalDebugSim2Level) : 0;
        }
        return fallbackLevel;
    }

    private static Config getCachedOrLoadedConfig() {
        Config config = CACHED_CONFIG;
        if (config != null) {
            return config;
        }
        Context context = SYSTEM_UI_CONTEXT;
        return context == null ? null : Config.load(context);
    }

    private static int getDebugPrimaryLevel(Config config) {
        if (config.iosSignalDebugSim1Enabled) {
            return mapMobileSignalLevel(config.iosSignalDebugSim1Level);
        }
        if (config.iosSignalDebugSim2Enabled) {
            return mapMobileSignalLevel(config.iosSignalDebugSim2Level);
        }
        return 0;
    }

    private static int getDebugLevelForSlot(Config config, int slot, int fallbackLevel) {
        if (slot == MOBILE_SLOT_SECONDARY) {
            return config.iosSignalDebugSim2Enabled
                    ? mapMobileSignalLevel(config.iosSignalDebugSim2Level) : 0;
        }
        if (slot == MOBILE_SLOT_PRIMARY || slot == MOBILE_SLOT_UNKNOWN) {
            return getDebugPrimaryLevel(config);
        }
        return fallbackLevel;
    }

    private static int getDefaultDebugSlot(Config config) {
        if (config != null && !config.iosSignalDebugSim1Enabled && config.iosSignalDebugSim2Enabled) {
            return MOBILE_SLOT_SECONDARY;
        }
        return MOBILE_SLOT_PRIMARY;
    }

    private static int buildSignalDrawableState(int level, int fallbackState) {
        int numberOfLevels = (fallbackState >> 8) & 0xff;
        if (numberOfLevels <= 0 || numberOfLevels >= 90) {
            numberOfLevels = IosSignalDrawable.MAX_LEVEL;
        }
        int stateType = (fallbackState >> 16) & 0xff;
        if (stateType == 3) {
            stateType = 0;
        }
        return mapMobileSignalLevel(level) | (numberOfLevels << 8) | (stateType << 16);
    }

    private static int getMobileSlotFromView(View view) {
        int subId = invokeNoArgInt(view, "getSubId", getIntField(view, "subId", UNSET_SUB_ID));
        if (subId != UNSET_SUB_ID && subId >= 0) {
            int slot = getMobileSlotForSubId(view.getContext(), subId);
            if (slot != MOBILE_SLOT_UNKNOWN) {
                return slot;
            }
        }
        int physicalSlot = getPhysicalSlotIndexFromView(view);
        if (physicalSlot == 0) {
            return MOBILE_SLOT_PRIMARY;
        }
        if (physicalSlot == 1) {
            return MOBILE_SLOT_SECONDARY;
        }
        String slotName = invokeNoArgString(view, "getSlot");
        int slotFromName = getMobileSlotFromName(slotName == null ? "" : slotName.toLowerCase());
        if (slotFromName != MOBILE_SLOT_UNKNOWN) {
            return slotFromName;
        }
        return MOBILE_SLOT_UNKNOWN;
    }

    private static int getPhysicalSlotIndexFromView(View view) {
        int slotIndex = invokeNoArgInt(view, "getSlotIndex", UNSET_SUB_ID);
        if (slotIndex >= 0) {
            return slotIndex;
        }
        slotIndex = invokeNoArgInt(view, "getSlotId", UNSET_SUB_ID);
        if (slotIndex >= 0) {
            return slotIndex;
        }
        String[] fieldNames = {"slotIndex", "mSlotIndex", "slotId", "mSlotId", "simSlotIndex",
                "mSimSlotIndex", "phoneId", "mPhoneId"};
        for (String fieldName : fieldNames) {
            slotIndex = getIntField(view, fieldName, UNSET_SUB_ID);
            if (slotIndex >= 0) {
                return slotIndex;
            }
        }
        return -1;
    }

    private static int getMobileSlotForSubId(Context context, int subId) {
        int physicalSlot = getPhysicalSlotIndexForSubId(context, subId);
        if (physicalSlot == 0) {
            primaryMobileSubId = subId;
            return MOBILE_SLOT_PRIMARY;
        }
        if (physicalSlot == 1) {
            secondaryMobileSubId = subId;
            return MOBILE_SLOT_SECONDARY;
        }
        int recordedSlot = getRecordedFlymeSlotForSubId(subId);
        if (recordedSlot != MOBILE_SLOT_UNKNOWN) {
            return recordedSlot;
        }
        if (primaryMobileSubId == UNSET_SUB_ID || primaryMobileSubId == subId) {
            primaryMobileSubId = subId;
            return MOBILE_SLOT_PRIMARY;
        }
        if (secondaryMobileSubId == UNSET_SUB_ID || secondaryMobileSubId == subId) {
            secondaryMobileSubId = subId;
            return MOBILE_SLOT_SECONDARY;
        }
        return MOBILE_SLOT_UNKNOWN;
    }

    private static int getPhysicalSlotIndexForSubId(Context context, int subId) {
        try {
            int slotIndex = android.telephony.SubscriptionManager.getSlotIndex(subId);
            if (slotIndex >= 0) {
                return slotIndex;
            }
        } catch (Throwable ignored) {
        }
        if (context != null) {
            try {
                Class<?> managerClass = Class.forName("android.telephony.SubscriptionManager");
                Method from = managerClass.getDeclaredMethod("from", Context.class);
                Object manager = from.invoke(null, context);
                if (manager != null) {
                    Method getInfo = managerClass.getDeclaredMethod("getActiveSubscriptionInfo", int.class);
                    Object info = getInfo.invoke(manager, subId);
                    if (info != null) {
                        Method getSimSlotIndex = info.getClass().getDeclaredMethod("getSimSlotIndex");
                        Object value = getSimSlotIndex.invoke(info);
                        if (value instanceof Integer) {
                            return (Integer) value;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Class<?> clazz = Class.forName("android.telephony.SubscriptionManager");
            Method method = clazz.getDeclaredMethod("getSlotIndex", int.class);
            Object value = method.invoke(null, subId);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> clazz = Class.forName("android.telephony.SubscriptionManager");
            Method method = clazz.getDeclaredMethod("getSlotId", int.class);
            Object value = method.invoke(null, subId);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static MobileSignalInfo getMobileSignalInfo(Resources resources, int resId, int fallbackSlot, int subId) {
        if (resId == 0) {
            return null;
        }
        String name = getResourceName(resources, resId);
        if (name == null) {
            return null;
        }
        String lowerName = name.toLowerCase();
        int slot = fallbackSlot != MOBILE_SLOT_UNKNOWN ? fallbackSlot : getMobileSlotFromName(lowerName);
        if (lowerName.contains("null") || lowerName.contains("no_signal")
                || lowerName.contains("no_sims") || lowerName.contains("empty")) {
            return new MobileSignalInfo(slot, 0, subId);
        }
        Matcher matcher = MOBILE_AOSP_LEVEL_PATTERN.matcher(lowerName);
        Integer lastLevel = null;
        if (matcher.find()) {
            lastLevel = Integer.parseInt(matcher.group(1));
        }
        if (lastLevel == null) {
            matcher = MOBILE_SIGNAL_LEVEL_PATTERN.matcher(lowerName);
            while (matcher.find()) {
                lastLevel = Integer.parseInt(matcher.group(1));
            }
        }
        if (lastLevel == null) {
            matcher = MOBILE_COMPACT_LEVEL_PATTERN.matcher(lowerName);
            while (matcher.find()) {
                lastLevel = Integer.parseInt(matcher.group(1));
            }
        }
        if (lastLevel == null) {
            return null;
        }
        return new MobileSignalInfo(slot, mapMobileSignalLevel(lastLevel), subId);
    }

    private static int getMobileSlotFromName(String lowerName) {
        if (lowerName.contains("slot0") || lowerName.contains("phone0")
                || lowerName.contains("slot_0") || lowerName.contains("phone_0")) {
            return MOBILE_SLOT_PRIMARY;
        }
        if (lowerName.contains("phone1") || lowerName.contains("phone_1")) {
            return MOBILE_SLOT_SECONDARY;
        }
        if (lowerName.contains("signal1") || lowerName.contains("sim1")
                || lowerName.contains("slot1") || lowerName.contains("sub1")) {
            return MOBILE_SLOT_PRIMARY;
        }
        if (lowerName.contains("signal2") || lowerName.contains("sim2")
                || lowerName.contains("slot2") || lowerName.contains("sub2")) {
            return MOBILE_SLOT_SECONDARY;
        }
        return MOBILE_SLOT_UNKNOWN;
    }

    private static int mapSignalDrawableStateLevel(int state) {
        int lowByteLevel = state & 0xff;
        int numberOfLevels = (state >> 8) & 0xff;
        int stateType = (state >> 16) & 0xff;
        if (state == 0 || numberOfLevels == 0) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        if (stateType == 3) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        if (lowByteLevel >= 90) {
            return 0;
        }
        return mapMobileSignalLevel(lowByteLevel);
    }

    private static int mapMobileSignalLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level >= IosSignalDrawable.MAX_LEVEL) {
            return IosSignalDrawable.MAX_LEVEL;
        }
        return level;
    }

    private static Integer getWifiSignalLevel(Resources resources, int resId) {
        if (resId == 0) {
            return null;
        }
        String name = getResourceName(resources, resId);
        if (name == null) {
            return null;
        }
        String lowerName = name.toLowerCase();
        if (isWifiHiddenResourceName(lowerName)) {
            return null;
        }
        Matcher matcher = WIFI_LEVEL_PATTERN.matcher(lowerName);
        Integer lastLevel = null;
        while (matcher.find()) {
            lastLevel = Integer.parseInt(matcher.group(1));
        }
        if (lastLevel == null) {
            matcher = WIFI_COMPACT_LEVEL_PATTERN.matcher(lowerName);
            while (matcher.find()) {
                lastLevel = Integer.parseInt(matcher.group(1));
            }
        }
        if (lastLevel != null) {
            return mapFlymeWifiResourceLevel(lowerName, lastLevel);
        }
        if (lowerName.contains("fully_connected")) {
            return IosWifiDrawable.MAX_LEVEL;
        }
        return null;
    }

    private static boolean shouldHideWifiSignalResource(Resources resources, int resId) {
        if (resId == 0) {
            return false;
        }
        String name = getResourceName(resources, resId);
        return name != null && isWifiHiddenResourceName(name.toLowerCase());
    }

    private static boolean isWifiHiddenResourceName(String lowerName) {
        return lowerName.contains("null") || lowerName.contains("empty")
                || lowerName.contains("no_network") || lowerName.contains("not_connected")
                || lowerName.contains("disconnected") || lowerName.contains("slash")
                || lowerName.contains("off");
    }

    private static boolean isWifiOverlaySourceHidden(ImageView imageView) {
        return imageView != null && Boolean.TRUE.equals(WIFI_SIGNAL_HIDDEN_STATES.get(imageView));
    }

    private static int mapFlymeWifiResourceLevel(String resourceName, int parsedLevel) {
        if (!resourceName.contains("stat_sys_wifi")) {
            return parsedLevel;
        }
        if (parsedLevel <= 0) {
            return 0;
        }
        return Math.min(IosWifiDrawable.MAX_LEVEL, parsedLevel + 1);
    }

    private static String getResourceName(Resources resources, int resId) {
        try {
            return resources.getResourceEntryName(resId);
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
    }

    private static void applyNetworkTypeLabel(ImageView imageView, String label) {
        hideNetworkTypeView(imageView);
    }

    private static void applyKnownNetworkTypeStyle(View root, Config config) {
        View child = findSystemUiChild(root, "mobile_type");
        if (child instanceof ImageView) {
            hideNetworkTypeView((ImageView) child);
        }
    }


    private static void syncDrawableTint(ImageView imageView, android.graphics.drawable.Drawable drawable) {
        drawable.setState(imageView.getDrawableState());
        drawable.setTintList(imageView.getImageTintList());
        drawable.setTintMode(imageView.getImageTintMode());
        drawable.setColorFilter(imageView.getColorFilter());
    }

    private static boolean shouldUseDesktopSignalReference(View view) {
        return isReferenceSignalContextChild(view);
    }

    private static boolean shouldRecordDesktopReference(View view) {
        return !isReferenceSignalContextChild(view);
    }

    private static boolean isReferenceSignalContextChild(View view) {
        return hasAncestorClass(view,
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                "com.flyme.statusbar.bouncer.KeyguardBouncerStatusBarView",
                "com.flyme.systemui.controlcenter.qs.QSStatusBar",
                "com.android.systemui.shade.carrier.ShadeCarrier",
                "com.android.systemui.shade.carrier.ShadeCarrierGroup");
    }

    private static boolean hasAncestorClass(View view, String... classNames) {
        View current = view;
        while (current != null) {
            String className = current.getClass().getName();
            for (String targetClass : classNames) {
                if (targetClass.equals(className)) {
                    return true;
                }
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static int getIosSignalOffsetX(View view, Config config) {
        int scene = resolveSignalScene(view);
        return config.getActiveSignalOffsetX(scene);
    }

    private static int getIosSignalOffsetY(View view, Config config) {
        int scene = resolveSignalScene(view);
        return config.getActiveSignalOffsetY(scene);
    }

    private static int resolveSignalScene(View view) {
        if (hasAncestorClass(view,
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                "com.flyme.statusbar.bouncer.KeyguardBouncerStatusBarView")) {
            return SIGNAL_SCENE_KEYGUARD;
        }
        if (hasAncestorClass(view,
                "com.flyme.systemui.controlcenter.qs.QSStatusBar",
                "com.android.systemui.shade.carrier.ShadeCarrier",
                "com.android.systemui.shade.carrier.ShadeCarrierGroup")) {
            return SIGNAL_SCENE_CONTROL_CENTER;
        }
        return SIGNAL_SCENE_DESKTOP;
    }


    private void hookStatusTextView(ClassLoader loader, String className) {
        hookConstructors(loader, className, view -> {
            if (!(view instanceof TextView)) {
                return;
            }
            Config config = Config.load(view.getContext());
            if (!config.enabled || !isStatusTextView((TextView) view)) {
                return;
            }
            trackStatusTextView((TextView) view);
            ensureConfigRefreshObserver(view.getContext());
            applyTextScale((TextView) view, config);
            view.postDelayed(() -> {
                Config delayedConfig = Config.load(view.getContext());
                if (delayedConfig.enabled && isStatusTextView((TextView) view)) {
                    applyTextScale((TextView) view, delayedConfig);
                }
            }, 1000);
        });
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
                Config config = Config.load(clock.getContext());
                if (!config.enabled || !config.showClockWeekday || !"clock".equals(getSystemUiIdName(clock))) {
                    return result;
                }
                return appendWeekday((CharSequence) result);
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook Clock weekday", t);
        }
    }

    private static CharSequence appendWeekday(CharSequence timeText) {
        if (timeText == null || timeText.length() == 0) {
            return timeText;
        }
        String weekday = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());
        SpannableStringBuilder builder = new SpannableStringBuilder(timeText);
        builder.append(' ');
        builder.append(weekday);
        return builder;
    }

    private static void applyTextScale(TextView textView, Config config) {
        Float original = ORIGINAL_TEXT_SIZES.get(textView);
        if (original == null) {
            original = textView.getTextSize();
            ORIGINAL_TEXT_SIZES.put(textView, original);
        }
        textView.setTextSize(0, original * config.textScale);
        applyClockFontWeight(textView, config);
    }

    private static void applyClockFontWeight(TextView textView, Config config) {
        if (!"clock".equals(getSystemUiIdName(textView))) {
            return;
        }
        if (!ORIGINAL_TEXT_STYLES.containsKey(textView)) {
            Typeface typeface = textView.getTypeface();
            ORIGINAL_TEXT_TYPEFACES.put(textView, typeface);
            ORIGINAL_TEXT_STYLES.put(textView, typeface != null ? typeface.getStyle() : Typeface.NORMAL);
        }
        Typeface originalTypeface = ORIGINAL_TEXT_TYPEFACES.get(textView);
        Integer originalStyleValue = ORIGINAL_TEXT_STYLES.get(textView);
        int originalStyle = originalStyleValue != null ? originalStyleValue : Typeface.NORMAL;
        if (!config.clockBoldEnabled) {
            textView.setTypeface(originalTypeface, originalStyle);
            return;
        }
        int targetWeight = Math.max(100, Math.min(900, config.clockFontWeight));
        boolean italic = (originalStyle & Typeface.ITALIC) != 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textView.setTypeface(Typeface.create(originalTypeface, targetWeight, italic));
            return;
        }
        int fallbackStyle;
        if (targetWeight >= 600) {
            fallbackStyle = italic ? Typeface.BOLD_ITALIC : Typeface.BOLD;
        } else {
            fallbackStyle = italic ? Typeface.ITALIC : Typeface.NORMAL;
        }
        textView.setTypeface(originalTypeface, fallbackStyle);
    }

    private static void applyConnectionRateTextScale(View view) {
        Config config = Config.load(view.getContext());
        if (!config.enabled) {
            return;
        }
        Object textSize = getField(view, "mTextSize");
        if (textSize instanceof Integer) {
            int currentTextSize = (Integer) textSize;
            Integer original = ORIGINAL_CONNECTION_RATE_TEXT_SIZES.get(view);
            if (original == null && currentTextSize > 0) {
                original = currentTextSize;
                ORIGINAL_CONNECTION_RATE_TEXT_SIZES.put(view, original);
            }
            if (original != null) {
                setIntField(view, "mTextSize", Math.round(original * config.textScale));
            }
        }
        Object unitView = getField(view, "mUnitView");
        if (unitView instanceof TextView) {
            applyTextScale((TextView) unitView, config);
        }
    }

    private static void applyConnectionRateOffset(View view) {
        Config config = Config.load(view.getContext());
        int dx = config.enabled ? dp(view, config.connectionRateOffsetX) : 0;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
            int[] original = ORIGINAL_MARGINS.get(view);
            if (original == null) {
                original = new int[]{
                        marginLp.leftMargin,
                        marginLp.topMargin,
                        marginLp.rightMargin,
                        marginLp.bottomMargin,
                        marginLp.getMarginStart(),
                        marginLp.getMarginEnd()
                };
                ORIGINAL_MARGINS.put(view, original);
            }
            int left = original[0] + dx;
            int top = original[1];
            int right = original[2] - dx;
            int bottom = original[3];
            int start = original[4] + dx;
            int end = original[5] - dx;
            disableAncestorClipping(view, 6);
            if (marginLp.leftMargin != left || marginLp.topMargin != top
                    || marginLp.rightMargin != right || marginLp.bottomMargin != bottom
                    || marginLp.getMarginStart() != start || marginLp.getMarginEnd() != end) {
                marginLp.leftMargin = left;
                marginLp.topMargin = top;
                marginLp.rightMargin = right;
                marginLp.bottomMargin = bottom;
                marginLp.setMarginStart(start);
                marginLp.setMarginEnd(end);
                view.setLayoutParams(marginLp);
                view.requestLayout();
            }
            return;
        }
        float[] original = ORIGINAL_TRANSLATIONS.get(view);
        if (original == null) {
            original = new float[]{view.getTranslationX(), view.getTranslationY()};
            ORIGINAL_TRANSLATIONS.put(view, original);
        }
        disableAncestorClipping(view, 6);
        view.setTranslationX(original[0] + dx);
        view.setTranslationY(original[1]);
    }

    private static float getConnectionRateManualDrawOffsetY(View view) {
        Config config = Config.load(view.getContext());
        return config.enabled ? dp(view, config.connectionRateOffsetY) : 0f;
    }

    private static void trackStatusTextView(TextView textView) {
        TRACKED_STATUS_TEXT_VIEWS.put(textView, Boolean.TRUE);
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
        applyConnectionRateThresholdVisibility(view, state, Config.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, boolean baseShow, double rate) {
        if (view == null) {
            return;
        }
        applyConnectionRateThresholdVisibility(view, baseShow, rate, rememberConnectionRateState(view),
                Config.load(view.getContext()));
    }

    private static void applyConnectionRateThresholdVisibility(View view, ConnectionRateThresholdState state,
            Config config) {
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
            setBooleanField(view, "mShow", state.lastBaseShow);
            applyConnectionRateVisibleState(view, state.lastBaseShow, getBooleanField(view, "mEnable", true),
                    getBooleanField(view, "mIsDemoMode", false));
            return;
        }
        setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(view, state.thresholdVisible, getBooleanField(view, "mEnable", true),
                getBooleanField(view, "mIsDemoMode", false));
    }

    private static void applyConnectionRateThresholdVisibility(View view, boolean baseShow, double rate,
            ConnectionRateThresholdState state, Config config) {
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
            setBooleanField(view, "mShow", baseShow);
            applyConnectionRateVisibleState(view, baseShow, getBooleanField(view, "mEnable", true),
                    getBooleanField(view, "mIsDemoMode", false));
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
            setBooleanField(view, "mShow", false);
            applyConnectionRateVisibleState(view, false, getBooleanField(view, "mEnable", true),
                    getBooleanField(view, "mIsDemoMode", false));
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
        setBooleanField(view, "mShow", state.thresholdVisible);
        applyConnectionRateVisibleState(view, state.thresholdVisible, getBooleanField(view, "mEnable", true),
                getBooleanField(view, "mIsDemoMode", false));
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

    private static long getConnectionRateThresholdSignature(Config config) {
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
        rememberSystemUiContext(context);
        synchronized (CONFIG_REFRESH_LOCK) {
            if (CONFIG_REFRESH_REGISTERED) {
                return;
            }
            Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            rememberSystemUiContext(appContext);
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
            SETTINGS_OBSERVER = new ContentObserver(MAIN_HANDLER) {
                @Override
                public void onChange(boolean selfChange) {
                    invalidateConfigCache();
                    Config config = Config.load(appContext);
                    refreshTrackedTextScaling();
                    if (config.iosSignalDebugEnabled) {
                        normalizeDebugSignalState(config);
                        applyDebugSignalDrawableStates(config);
                    } else {
                        if (LAST_SIGNAL_DEBUG_ENABLED) {
                            clearDebugSignalState();
                        }
                        refreshTrackedSignalViews(LAST_SIGNAL_DEBUG_ENABLED);
                    }
                    LAST_SIGNAL_DEBUG_ENABLED = config.iosSignalDebugEnabled;
                    if (config.iosWifiDebugEnabled) {
                        applyDebugWifiStates(config);
                    } else if (LAST_WIFI_DEBUG_ENABLED) {
                        restoreTrackedWifiStates();
                    }
                    LAST_WIFI_DEBUG_ENABLED = config.iosWifiDebugEnabled;
                    Handler handler = MAIN_HANDLER;
                    if (handler != null) {
                        handler.postDelayed(() -> {
                            reportCurrentTrackedSignalState(appContext, "manual hook status refresh");
                            reportCurrentTrackedWifiState(appContext, "manual hook status refresh");
                        }, 100);
                    }
                }
            };
            try {
                appContext.getContentResolver().registerContentObserver(SETTINGS_URI, true, SETTINGS_OBSERVER);
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
            SUBSCRIPTION_CHANGED_RECEIVER = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    handleSubscriptionsChanged();
                }
            };
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction("android.intent.action.SIM_STATE_CHANGED");
                filter.addAction("android.telephony.action.SIM_CARD_STATE_CHANGED");
                filter.addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED");
                filter.addAction("android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED");
                filter.addAction("android.telephony.action.DEFAULT_DATA_SUBSCRIPTION_CHANGED");
                filter.addAction("android.telephony.action.MULTI_SIM_CONFIG_CHANGED");
                filter.addAction("android.telephony.action.CARRIER_CONFIG_CHANGED");
                registerRuntimeReceiver(appContext, SUBSCRIPTION_CHANGED_RECEIVER, filter);
            } catch (Throwable ignored) {
            }
            CONFIG_REFRESH_REGISTERED = true;
            LAST_SIGNAL_DEBUG_ENABLED = Config.load(appContext).iosSignalDebugEnabled;
            LAST_WIFI_DEBUG_ENABLED = Config.load(appContext).iosWifiDebugEnabled;
            scheduleInitialRuntimeRefreshes();
        }
    }

    private static void scheduleInitialRuntimeRefreshes() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        for (long delay : INITIAL_RUNTIME_REFRESH_DELAYS_MS) {
            handler.postDelayed(() -> refreshTrackedRuntimeViews(true), delay);
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

    private static void handleSubscriptionsChanged() {
        Handler handler = MAIN_HANDLER;
        Runnable action = () -> {
            resetMobileSubscriptionState();
            refreshTrackedSignalViews(true);
        };
        if (handler != null) {
            handler.post(action);
        } else {
            action.run();
        }
    }

    private static void resetMobileSubscriptionState() {
        primaryMobileSubId = UNSET_SUB_ID;
        secondaryMobileSubId = UNSET_SUB_ID;
        latestPrimarySignalLevel = 0;
        latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
        synchronized (FLYME_SLOT_INDEX_BY_SUB_ID) {
            FLYME_SLOT_INDEX_BY_SUB_ID.clear();
        }
        synchronized (TELEPHONY_MANAGER_SUB_IDS) {
            TELEPHONY_MANAGER_SUB_IDS.clear();
        }
        synchronized (SIGNAL_STRENGTH_SUB_IDS) {
            SIGNAL_STRENGTH_SUB_IDS.clear();
        }
        synchronized (CELL_SIGNAL_STRENGTH_SUB_IDS) {
            CELL_SIGNAL_STRENGTH_SUB_IDS.clear();
        }
        synchronized (TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID) {
            TELEPHONY_SIGNAL_LEVELS_BY_SUB_ID.clear();
            TELEPHONY_SIGNAL_LEVEL_TIMES_BY_SUB_ID.clear();
        }
        MOBILE_VIEW_SLOTS.clear();
        TRACKED_MOBILE_SIGNAL_VIEWS.clear();
        MOBILE_SIGNAL_INFOS.clear();
        PRIMARY_SIGNAL_VIEWS.clear();
        SECONDARY_SIGNAL_VIEWS.clear();
    }

    private static void normalizeDebugSignalState(Config config) {
        if (config == null || !config.iosSignalDebugEnabled || config.iosSignalDebugSim2Enabled) {
            return;
        }
        latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
        for (ImageView view : new ArrayList<>(SECONDARY_SIGNAL_VIEWS.keySet())) {
            if (view != null) {
                setMobileSignalViewVisible(view, false);
            }
        }
        SECONDARY_SIGNAL_VIEWS.clear();
        updatePrimarySignalDrawables();
    }

    private static void clearDebugSignalState() {
        latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
        for (ImageView view : collectTrackedMobileSignalViews()) {
            if (view != null) {
                setMobileSignalViewVisible(view, true);
            }
        }
        SECONDARY_SIGNAL_VIEWS.clear();
        updatePrimarySignalDrawables();
    }

    private static void refreshTrackedRuntimeViews() {
        refreshTrackedRuntimeViews(false);
    }

    private static void refreshTrackedRuntimeViews(boolean forceSignalRequery) {
        refreshTrackedTextScaling();
        refreshTrackedBatteryViews();
        refreshTrackedSignalViews(forceSignalRequery);
    }

    private static void invalidateConfigCache() {
        synchronized (CONFIG_CACHE_LOCK) {
            CACHED_CONFIG = null;
            CACHED_CONFIG_UPTIME = 0L;
        }
    }

    private static void refreshTrackedTextScaling() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<TextView> textViews = new ArrayList<>(TRACKED_STATUS_TEXT_VIEWS.keySet());
            for (TextView textView : textViews) {
                if (textView == null) {
                    continue;
                }
                Config config = Config.load(textView.getContext());
                if (config.enabled && isStatusTextView(textView)) {
                    applyTextScale(textView, config);
                    textView.requestLayout();
                    textView.invalidate();
                }
            }
            ArrayList<View> connectionRateViews = new ArrayList<>(TRACKED_CONNECTION_RATE_VIEWS.keySet());
            for (View view : connectionRateViews) {
                if (view == null) {
                    continue;
                }
                applyConnectionRateTextScale(view);
                applyConnectionRateOffset(view);
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
                Config config = Config.load(batteryView.getContext());
                if (!config.enabled) {
                    continue;
                }
                normalizeBatterySpacing(batteryView);
                resizeIosBatteryView(batteryView, config, getBooleanField(batteryView, "mCharging", false));
                batteryView.setScaleX(config.scaled(config.batteryFactor));
                batteryView.setScaleY(config.scaled(config.batteryFactor));
                batteryView.requestLayout();
                batteryView.invalidate();
            }
        });
    }

    private static void refreshTrackedSignalViews() {
        refreshTrackedSignalViews(false);
    }

    private static void applyDebugSignalDrawableStates(Config config) {
        Handler handler = MAIN_HANDLER;
        if (handler == null || config == null || !config.enabled || !config.iosSignalDebugEnabled) {
            return;
        }
        handler.post(() -> {
            normalizeDebugSignalState(config);
            ArrayList<Drawable> drawables = new ArrayList<>(MOBILE_SIGNAL_DRAWABLE_OWNERS.keySet());
            boolean injectedPrimary = false;
            boolean injectedSecondary = false;
            int injectedCount = 0;
            for (Drawable drawable : drawables) {
                ImageView imageView = MOBILE_SIGNAL_DRAWABLE_OWNERS.get(drawable);
                if (drawable == null || imageView == null) {
                    continue;
                }
                int slot = getKnownMobileSignalSlot(imageView, getDefaultDebugSlot(config));
                int fallbackLevel = mapSignalDrawableStateLevel(drawable.getLevel());
                if (fallbackLevel == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                    fallbackLevel = getLastKnownSignalLevelForSlot(slot);
                }
                int fakeLevel = getDebugLevelForSlot(config, slot, fallbackLevel);
                int fakeState = buildSignalDrawableState(fakeLevel, drawable.getLevel());
                reportSignalDebug(imageView.getContext(), "debug SignalDrawable state",
                        slot, getMobileSubId(imageView), fakeState, fakeLevel, null);
                drawable.setLevel(fakeState);
                injectedCount++;
                if (slot == MOBILE_SLOT_PRIMARY) {
                    injectedPrimary = true;
                } else if (slot == MOBILE_SLOT_SECONDARY) {
                    injectedSecondary = true;
                }
            }
            if (injectedCount == 0) {
                ArrayList<ImageView> views = collectTrackedMobileSignalViews();
                for (ImageView imageView : views) {
                    if (imageView == null) {
                        continue;
                    }
                    int slot = getKnownMobileSignalSlot(imageView, getDefaultDebugSlot(config));
                    int fakeLevel = getDebugLevelForSlot(config, slot, getLastKnownSignalLevelForSlot(slot));
                    int fakeState = buildSignalDrawableState(fakeLevel, 0);
                    reportSignalDebug(imageView.getContext(), "debug synthetic SystemUI state",
                            slot, getMobileSubId(imageView), fakeState, fakeLevel, null);
                    applyMobileSignalDrawableState(imageView, fakeState);
                    injectedCount++;
                    if (slot == MOBILE_SLOT_PRIMARY) {
                        injectedPrimary = true;
                    } else if (slot == MOBILE_SLOT_SECONDARY) {
                        injectedSecondary = true;
                    }
                }
            }
            if (config.iosSignalDualCombined && config.iosSignalDebugSim1Enabled
                    && config.iosSignalDebugSim2Enabled && injectedPrimary && !injectedSecondary) {
                latestSecondarySignalLevel = mapMobileSignalLevel(config.iosSignalDebugSim2Level);
                updatePrimarySignalDrawables();
                reportSignalDebug(SYSTEM_UI_CONTEXT, "debug virtual secondary signal",
                        MOBILE_SLOT_SECONDARY, UNSET_SUB_ID,
                        buildSignalDrawableState(latestSecondarySignalLevel, 0),
                        latestSecondarySignalLevel,
                        "No secondary SystemUI signal view was found; using a virtual SIM 2 for combined debug");
            } else if (!config.iosSignalDebugSim2Enabled) {
                latestSecondarySignalLevel = IosSignalDrawable.NO_SECONDARY_LEVEL;
                updatePrimarySignalDrawables();
            } else if (!injectedPrimary && !injectedSecondary) {
                reportSignalDebug(SYSTEM_UI_CONTEXT, "debug SignalDrawable state",
                        MOBILE_SLOT_UNKNOWN, UNSET_SUB_ID, 0,
                        IosSignalDrawable.NO_SECONDARY_LEVEL,
                        "No tracked SystemUI mobile signal drawable or ImageView was found");
            }
        });
    }

    private static void refreshTrackedSignalViews(boolean forceRequery) {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<ImageView> signalViews = forceRequery
                    ? collectTrackedMobileSignalViews()
                    : new ArrayList<>(MOBILE_SIGNAL_RAW_INFOS.keySet());
            for (ImageView imageView : signalViews) {
                if (imageView == null) {
                    continue;
                }
                Config config = Config.load(imageView.getContext());
                if (!config.enabled) {
                    continue;
                }
                MobileSignalInfo info = MOBILE_SIGNAL_RAW_INFOS.get(imageView);
                if (forceRequery || info == null) {
                    int slot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
                    int subId = getMobileSubId(imageView);
                    int level = forceRequery && !config.iosSignalDebugEnabled
                            ? queryTelephonySignalLevel(imageView.getContext(), subId)
                            : getCachedTelephonySignalLevel(subId);
                    if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                        level = getCachedTelephonySignalLevel(subId);
                    }
                    if (level == IosSignalDrawable.NO_SECONDARY_LEVEL
                            && forceRequery && LAST_SIGNAL_DEBUG_ENABLED) {
                        reportSignalDebug(imageView.getContext(), "restore real signal",
                                slot, subId, 0, level, "Real signal query failed; keeping current drawable state");
                        continue;
                    }
                    if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                        level = info == null ? getLastKnownSignalLevelForSlot(slot) : info.level;
                    }
                    info = new MobileSignalInfo(slot, level, subId);
                }
                applyMobileSignalInfo(imageView, info, config);
                imageView.requestLayout();
                imageView.invalidate();
            }
        });
    }

    private static float getConnectionRateBaselineOffset(View view) {
        int textSize = getIntField(view, "mTextSize", 0);
        if (textSize <= 0) {
            return 0f;
        }
        Object paintObject = getField(view, "mPaint");
        Paint paint = paintObject instanceof Paint ? (Paint) paintObject : new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return -(metrics.ascent + metrics.descent) / 2f;
    }

    private static float getConnectionRateAlignmentOffset(View view) {
        Object unitObject = getField(view, "mUnitView");
        if (!(unitObject instanceof TextView)) {
            return getConnectionRateBaselineOffset(view);
        }
        TextView unitView = (TextView) unitObject;
        int textSize = getIntField(view, "mTextSize", 0);
        if (textSize <= 0 || unitView.getHeight() <= 0) {
            return getConnectionRateBaselineOffset(view);
        }
        Object paintObject = getField(view, "mPaint");
        Paint paint = paintObject instanceof Paint ? (Paint) paintObject : new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        int[] unitLocation = new int[2];
        unitView.getLocationOnScreen(unitLocation);
        float currentBaseline = unitView.getHeight() / 2f;
        Float targetBottom = getBatteryAlignedBottom(view, unitLocation);
        if (targetBottom == null) {
            View referenceView = findConnectionRateReferenceView(view);
            if (referenceView == null) {
                return getConnectionRateBaselineOffset(view);
            }
            int[] referenceLocation = new int[2];
            referenceView.getLocationOnScreen(referenceLocation);
            targetBottom = (float) ((referenceLocation[1] - unitLocation[1]) + referenceView.getHeight());
        }
        float targetBaseline = targetBottom - metrics.descent;
        return targetBaseline - currentBaseline;
    }

    private static Float getBatteryAlignedBottom(View connectionRateView, int[] unitLocation) {
        View batteryView = findConnectionRateBatteryView(connectionRateView);
        if (batteryView == null || batteryView.getHeight() <= 0) {
            return null;
        }
        int[] batteryLocation = new int[2];
        batteryView.getLocationOnScreen(batteryLocation);
        return (batteryLocation[1] - unitLocation[1]) + getBatteryVisibleBottom(batteryView);
    }

    private static View findConnectionRateBatteryView(View view) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        int batteryId = getSystemUiId(view.getContext(), "battery");
        if (batteryId == 0) {
            return null;
        }
        return ((ViewGroup) parent).findViewById(batteryId);
    }

    private static float getBatteryVisibleBottom(View batteryView) {
        Config config = Config.load(batteryView.getContext());
        if (config.enabled) {
            int height = dp(batteryView, config.iosBatteryHeight);
            int top = Math.round((batteryView.getHeight() - height) / 2f) + dp(batteryView, config.iosBatteryOffsetY);
            return top + height;
        }
        int visibleHeight = getSystemUiDimen(batteryView.getContext(), "status_bar_battery_unified_icon_height");
        if (visibleHeight <= 0) {
            visibleHeight = getSystemUiDimen(batteryView.getContext(), "status_bar_battery_icon_height");
        }
        if (visibleHeight <= 0) {
            return batteryView.getHeight();
        }
        float top = (batteryView.getHeight() - visibleHeight) / 2f;
        return top + visibleHeight;
    }

    private static View findConnectionRateReferenceView(View view) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) parent;
        boolean passedSelf = false;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!passedSelf) {
                if (child == view) {
                    passedSelf = true;
                }
                continue;
            }
            View candidate = findFirstVisibleLeaf(child);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static View findFirstVisibleLeaf(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        if (!(view instanceof ViewGroup)) {
            return view;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View candidate = findFirstVisibleLeaf(group.getChildAt(i));
            if (candidate != null) {
                return candidate;
            }
        }
        return view;
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

    private static boolean isStatusTextView(TextView textView) {
        String idName = getSystemUiIdName(textView);
        return "operator_name".equals(idName)
                || "battery_percent".equals(idName)
                || "keyguard_clock".equals(idName)
                || "clock".equals(idName)
                || "shade_carrier_text".equals(idName)
                || "mobile_carrier_text".equals(idName)
                || "no_carrier_text".equals(idName)
                || "keyguard_carrier_text".equals(idName)
                || "carrier_text".equals(idName);
    }

    private static void setChildHidden(View root, String idName, boolean hidden) {
        if (!hidden) {
            return;
        }
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return;
        }
        View child = root.findViewById(id);
        if (child != null) {
            child.setVisibility(View.GONE);
        }
    }

    private static void resizeChild(View root, String idName, int widthDp, int heightDp) {
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return;
        }
        View child = root.findViewById(id);
        if (child == null) {
            return;
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (widthDp != ViewGroup.LayoutParams.WRAP_CONTENT) {
            lp.width = dp(child, widthDp);
        } else {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        if (heightDp != ViewGroup.LayoutParams.WRAP_CONTENT) {
            lp.height = dp(child, heightDp);
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        child.setLayoutParams(lp);
        child.requestLayout();
    }

    private static void scaleChild(View root, String idName, float widthScale, float heightScale) {
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return;
        }
        View child = root.findViewById(id);
        if (child == null) {
            return;
        }
        scaleView(child, widthScale, heightScale);
    }

    private static void offsetChild(View root, String idName, int offsetXDp, int offsetYDp) {
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return;
        }
        View child = root.findViewById(id);
        if (child == null) {
            return;
        }
        offsetView(child, offsetXDp, offsetYDp);
    }

    private static void recordDesktopIconSize(View root, String idName, int[] sizeOutput) {
        View child = findSystemUiChild(root, idName);
        if (child == null || isReferenceSignalContextChild(child)) {
            return;
        }
        int width = getCurrentLayoutWidth(child);
        int height = getCurrentLayoutHeight(child);
        if (width > 0 && height > 0) {
            sizeOutput[0] = width;
            sizeOutput[1] = height;
        }
    }

    private static int[] getRecordedSize(int[] size) {
        return size[0] > 0 && size[1] > 0 ? new int[]{size[0], size[1]} : null;
    }

    private static int getCurrentLayoutWidth(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.width > 0) {
            return lp.width;
        }
        return view.getWidth();
    }

    private static int getCurrentLayoutHeight(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height > 0) {
            return lp.height;
        }
        return view.getHeight();
    }

    private static View findSystemUiChild(View root, String idName) {
        int id = getSystemUiId(root.getContext(), idName);
        if (id == 0) {
            return null;
        }
        return root.findViewById(id);
    }

    private static void normalizeMobileSpacing(View root) {
        if (!isStatusIconsChild(root)) {
            return;
        }
        int gapPx = getUnifiedStatusIconGapPx(root.getContext());
        if (gapPx <= 0) {
            return;
        }
        if (applyHorizontalMargins(root, gapPx, 0)) {
            return;
        }
        View mobileGroup = findSystemUiChild(root, "mobile_group");
        if (mobileGroup != null && mobileGroup != root) {
            applyHorizontalMargins(mobileGroup, gapPx, 0);
        }
    }

    private static void normalizeBatterySpacing(View view) {
        if (view == null) {
            return;
        }
        Config config = Config.load(view.getContext());
        int gapPx = Math.max(0, getBatteryGroupStartGapPx(view.getContext(), config));
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
        int[] original = ORIGINAL_MARGINS.get(view);
        if (original == null) {
            original = new int[]{
                    marginLp.leftMargin,
                    marginLp.topMargin,
                    marginLp.rightMargin,
                    marginLp.bottomMargin,
                    marginLp.getMarginStart(),
                    marginLp.getMarginEnd()
            };
            ORIGINAL_MARGINS.put(view, original);
        }
        marginLp.leftMargin = original[0];
        marginLp.topMargin = original[1];
        marginLp.rightMargin = original[2];
        marginLp.bottomMargin = original[3];
        marginLp.leftMargin = gapPx;
        marginLp.rightMargin = original[2];
        marginLp.setMarginStart(gapPx);
        marginLp.setMarginEnd(original[5]);
        view.setLayoutParams(marginLp);
        view.requestLayout();
    }

    private static int getBatteryGroupStartGapPx(Context context, Config config) {
        int baseGap = getUnifiedStatusIconGapPx(context);
        if (context == null || config == null) {
            return baseGap;
        }
        int adjustPx = Math.round(config.iosGroupStartGapAdjust
                * context.getResources().getDisplayMetrics().density);
        return Math.max(0, baseGap + adjustPx);
    }

    private static boolean applyHorizontalMargins(View view, int startPx, int endPx) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return false;
        }
        ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
        int[] original = ORIGINAL_MARGINS.get(view);
        if (original == null) {
            original = new int[]{
                    marginLp.leftMargin,
                    marginLp.topMargin,
                    marginLp.rightMargin,
                    marginLp.bottomMargin,
                    marginLp.getMarginStart(),
                    marginLp.getMarginEnd()
            };
            ORIGINAL_MARGINS.put(view, original);
        }
        marginLp.leftMargin = startPx;
        marginLp.topMargin = original[1];
        marginLp.rightMargin = endPx;
        marginLp.bottomMargin = original[3];
        marginLp.setMarginStart(startPx);
        marginLp.setMarginEnd(endPx);
        view.setLayoutParams(marginLp);
        view.requestLayout();
        return true;
    }

    private static boolean isStatusIconsChild(View view) {
        return hasAncestorIdName(view, "statusIcons") || hasAncestorIdName(view, "system_icons");
    }

    private static boolean hasAncestorIdName(View view, String idName) {
        View current = view;
        while (current != null) {
            if (idName.equals(getSystemUiIdName(current))) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
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

    private static int getUnifiedStatusIconGapPx(Context context) {
        int gap = getSystemUiDimen(context, "stat_sys_battery_margin_start");
        if (gap > 0) {
            return gap;
        }
        gap = getSystemUiDimen(context, "status_bar_bindable_icon_padding");
        if (gap > 0) {
            return gap;
        }
        gap = getSystemUiDimen(context, "status_bar_horizontal_padding");
        if (gap > 0) {
            return gap;
        }
        return Math.round(3f * context.getResources().getDisplayMetrics().density);
    }

    private static void offsetView(View child, int offsetXDp, int offsetYDp) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            child.setTranslationX(dp(child, offsetXDp));
            child.setTranslationY(dp(child, offsetYDp));
            return;
        }
        ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
        int[] original = ORIGINAL_MARGINS.get(child);
        if (original == null) {
            original = new int[]{
                    marginLp.leftMargin,
                    marginLp.topMargin,
                    marginLp.rightMargin,
                    marginLp.bottomMargin,
                    marginLp.getMarginStart(),
                    marginLp.getMarginEnd()
            };
            ORIGINAL_MARGINS.put(child, original);
        }
        int dx = dp(child, offsetXDp);
        int dy = dp(child, offsetYDp);
        marginLp.leftMargin = original[0] + dx;
        marginLp.topMargin = original[1] + dy;
        marginLp.rightMargin = original[2] - dx;
        marginLp.bottomMargin = original[3] - dy;
        marginLp.setMarginStart(original[4] + dx);
        marginLp.setMarginEnd(original[5] - dx);
        child.setLayoutParams(marginLp);
        child.requestLayout();
    }

    private static void scaleView(View child, float widthScale, float heightScale) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            return;
        }
        int[] original = ORIGINAL_SIZES.get(child);
        int currentWidth = lp.width > 0 ? lp.width : child.getWidth();
        int currentHeight = lp.height > 0 ? lp.height : child.getHeight();
        if (original == null) {
            if (currentWidth <= 0 && currentHeight <= 0) {
                return;
            }
            original = new int[]{currentWidth, currentHeight};
            ORIGINAL_SIZES.put(child, original);
        } else {
            if (original[0] <= 0 && currentWidth > 0) {
                original[0] = Math.round(currentWidth / Math.max(widthScale, 0.01f));
            }
            if (original[1] <= 0 && currentHeight > 0) {
                original[1] = Math.round(currentHeight / Math.max(heightScale, 0.01f));
            }
        }
        int baseWidth = original[0];
        int baseHeight = original[1];
        if (baseWidth > 0 && widthScale > 0f) {
            lp.width = Math.round(baseWidth * widthScale);
        }
        if (baseHeight > 0 && heightScale > 0f) {
            lp.height = Math.round(baseHeight * heightScale);
        }
        child.setLayoutParams(lp);
        child.requestLayout();
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

    private static String memberCacheKey(Class<?> clazz, String name) {
        return clazz.getName() + "#" + name;
    }

    private static Field findCachedField(Class<?> targetClass, String name) {
        String key = memberCacheKey(targetClass, name);
        synchronized (FIELD_CACHE) {
            Field cached = FIELD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                synchronized (FIELD_CACHE) {
                    FIELD_CACHE.put(key, field);
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findCachedNoArgMethod(Class<?> targetClass, String name) {
        String key = memberCacheKey(targetClass, name);
        synchronized (NO_ARG_METHOD_CACHE) {
            Method cached = NO_ARG_METHOD_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name);
                method.setAccessible(true);
                synchronized (NO_ARG_METHOD_CACHE) {
                    NO_ARG_METHOD_CACHE.put(key, method);
                }
                return method;
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object getField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                return field.get(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static View getNavBarTransitionsView(Object transitions) {
        Object value = getField(transitions, "mView");
        return value instanceof View ? (View) value : null;
    }

    private static Context getNavBarTransitionsContext(Object transitions) {
        View view = getNavBarTransitionsView(transitions);
        return view == null ? null : view.getContext();
    }

    private static Object getStaticField(ClassLoader loader, String className, String name) {
        if (loader == null || className == null || name == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            Field field = findCachedField(clazz, name);
            return field == null ? null : field.get(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int getStaticIntField(ClassLoader loader, String className, String name) {
        Object value = getStaticField(loader, className, name);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static void setIntField(Object target, String name, int value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.setInt(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        if (target == null || name == null) {
            return;
        }
        try {
            Field field = findCachedField(target.getClass(), name);
            if (field != null) {
                field.setBoolean(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int getIntField(Object target, String name, int fallback) {
        Object value = getField(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static int invokeNoArgInt(Object target, String name, int fallback) {
        Object value = invokeNoArg(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static String invokeNoArgString(Object target, String name) {
        Object value = invokeNoArg(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static boolean invokeNoArgBoolean(Object target, String name, boolean fallback) {
        Object value = invokeNoArg(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        try {
            Method method = findCachedNoArgMethod(target.getClass(), name);
            if (method != null) {
                return method.invoke(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean getBooleanField(Object target, String name, boolean fallback) {
        Object value = getField(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static double getDoubleField(Object target, String name, double fallback) {
        Object value = getField(target, name);
        return value instanceof Double ? (Double) value : fallback;
    }

    private static void setMeasuredDimension(View view, int width, int height) {
        try {
            Method method = SET_MEASURED_DIMENSION_METHOD;
            if (method == null) {
                method = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                method.setAccessible(true);
                SET_MEASURED_DIMENSION_METHOD = method;
            }
            method.invoke(view, width, height);
        } catch (Throwable ignored) {
        }
    }

    private static void setPaintColor(Object target, String name, int color) {
        Object value = getField(target, name);
        if (value instanceof Paint) {
            ((Paint) value).setColor(color);
        }
    }

    private interface ViewAction {
        void apply(View view);
    }

    private static final class MobileSignalInfo {
        final int slot;
        final int level;
        final int subId;

        MobileSignalInfo(int slot, int level) {
            this(slot, level, UNSET_SUB_ID);
        }

        MobileSignalInfo(int slot, int level, int subId) {
            this.slot = slot;
            this.level = mapMobileSignalLevel(level);
            this.subId = subId;
        }
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

    private static final class Config {
        boolean enabled = SettingsStore.DEFAULT_ENABLED;
        float globalIconScale = SettingsStore.DEFAULT_GLOBAL_ICON_SCALE / 100f;
        int mobileSignalFactor = SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR;
        int wifiSignalFactor = SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR;
        int batteryFactor = SettingsStore.DEFAULT_BATTERY_FACTOR;
        int statusIconFactor = SettingsStore.DEFAULT_STATUS_ICON_FACTOR;
        int iosSignalDesktopOffsetX = SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X;
        int iosSignalDesktopOffsetY = SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y;
        int iosSignalKeyguardOffsetX = SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X;
        int iosSignalKeyguardOffsetY = SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y;
        int iosSignalControlCenterOffsetX = SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X;
        int iosSignalControlCenterOffsetY = SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y;
        int iosBatteryWidth = SettingsStore.DEFAULT_IOS_BATTERY_WIDTH;
        int iosBatteryHeight = SettingsStore.DEFAULT_IOS_BATTERY_HEIGHT;
        int iosBatteryOffsetX = SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_X;
        int iosBatteryOffsetY = SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_Y;
        int iosBatteryTextSize = SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE;
        int iosGroupBatteryScale = SettingsStore.DEFAULT_IOS_GROUP_BATTERY_SCALE;
        int iosGroupSignalScale = SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_SCALE;
        int iosGroupWifiScale = SettingsStore.DEFAULT_IOS_GROUP_WIFI_SCALE;
        int iosGroupWifiSignalGap = SettingsStore.DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP;
        int iosGroupSignalBatteryGap = SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP;
        int iosGroupStartGapAdjust = SettingsStore.DEFAULT_IOS_GROUP_START_GAP_ADJUST;
        int iosWifiWidth = SettingsStore.DEFAULT_IOS_WIFI_WIDTH;
        int iosWifiHeight = SettingsStore.DEFAULT_IOS_WIFI_HEIGHT;
        int iosWifiOffsetX = SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X;
        int iosWifiOffsetY = SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y;
        int activityIconFactor = SettingsStore.DEFAULT_ACTIVITY_ICON_FACTOR;
        int connectionRateOffsetX = SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_X;
        int connectionRateOffsetY = SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_Y;
        boolean connectionRateThresholdEnabled = SettingsStore.DEFAULT_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED;
        int connectionRateShowThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB;
        int connectionRateHideThresholdKb = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB;
        int connectionRateShowSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT;
        int connectionRateHideSampleCount = SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT;
        float textScale = SettingsStore.DEFAULT_TEXT_SCALE / 100f;
        boolean showClockWeekday = SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY;
        boolean clockBoldEnabled = SettingsStore.DEFAULT_CLOCK_BOLD_ENABLED;
        int clockFontWeight = SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT;
        boolean iosSignalDualCombined = SettingsStore.DEFAULT_IOS_SIGNAL_DUAL_COMBINED;
        boolean iosSignalDebugEnabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_ENABLED;
        boolean iosSignalDebugSim1Enabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED;
        boolean iosSignalDebugSim2Enabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED;
        int iosSignalDebugSim1Level = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL;
        int iosSignalDebugSim2Level = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL;
        boolean iosWifiDebugEnabled = SettingsStore.DEFAULT_IOS_WIFI_DEBUG_ENABLED;
        boolean iosWifiDebugVisible = SettingsStore.DEFAULT_IOS_WIFI_DEBUG_VISIBLE;
        int iosWifiDebugLevel = SettingsStore.DEFAULT_IOS_WIFI_DEBUG_LEVEL;
        boolean mbackLongTouchIntentEnabled = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_URL_ENABLED;
        String mbackLongTouchIntentUri = SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI;
        boolean mbackNavBarTransparent = SettingsStore.DEFAULT_MBACK_NAV_BAR_TRANSPARENT;
        boolean mbackHidePill = SettingsStore.DEFAULT_MBACK_HIDE_PILL;
        int mbackInsetSize = SettingsStore.DEFAULT_MBACK_INSET_SIZE;
        int mbackNavBarHeight = SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT;

        float scaled(int factorPercent) {
            return 1f + ((globalIconScale - 1f) * (factorPercent / 100f));
        }

        int getActiveSignalOffsetX(int scene) {
            if (scene == SIGNAL_SCENE_KEYGUARD) {
                return iosSignalKeyguardOffsetX;
            }
            if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
                return iosSignalControlCenterOffsetX;
            }
            return iosSignalDesktopOffsetX;
        }

        int getActiveSignalOffsetY(int scene) {
            if (scene == SIGNAL_SCENE_KEYGUARD) {
                return iosSignalKeyguardOffsetY;
            }
            if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
                return iosSignalControlCenterOffsetY;
            }
            return iosSignalDesktopOffsetY;
        }

        static Config load(Context context) {
            if (context == null) {
                return new Config();
            }
            rememberSystemUiContext(context);
            long now = SystemClock.uptimeMillis();
            Config cached = CACHED_CONFIG;
            if (cached != null && now - CACHED_CONFIG_UPTIME <= CONFIG_CACHE_TTL_MS) {
                return cached;
            }
            Config config = new Config();
            try (Cursor cursor = context.getContentResolver().query(SETTINGS_URI, null, null, null, null)) {
                if (cursor == null) {
                    return config;
                }
                int keyColumn = cursor.getColumnIndex("key");
                int valueColumn = cursor.getColumnIndex("value");
                while (cursor.moveToNext()) {
                    String key = cursor.getString(keyColumn);
                    String value = cursor.getString(valueColumn);
                    config.apply(key, value);
                }
            } catch (Throwable ignored) {
            }
            synchronized (CONFIG_CACHE_LOCK) {
                CACHED_CONFIG = config;
                CACHED_CONFIG_UPTIME = now;
            }
            return config;
        }

        private void apply(String key, String value) {
            if (SettingsStore.KEY_ENABLED.equals(key)) {
                enabled = "1".equals(value);
            } else if (SettingsStore.KEY_GLOBAL_ICON_SCALE.equals(key)) {
                globalIconScale = parseInt(value, SettingsStore.DEFAULT_GLOBAL_ICON_SCALE) / 100f;
            } else if (SettingsStore.KEY_MOBILE_SIGNAL_FACTOR.equals(key)) {
                mobileSignalFactor = parseInt(value, SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR);
            } else if (SettingsStore.KEY_WIFI_SIGNAL_FACTOR.equals(key)) {
                wifiSignalFactor = parseInt(value, SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR);
            } else if (SettingsStore.KEY_BATTERY_FACTOR.equals(key)) {
                batteryFactor = parseInt(value, SettingsStore.DEFAULT_BATTERY_FACTOR);
            } else if (SettingsStore.KEY_STATUS_ICON_FACTOR.equals(key)) {
                statusIconFactor = parseInt(value, SettingsStore.DEFAULT_STATUS_ICON_FACTOR);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_X.equals(key)) {
                iosSignalDesktopOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DESKTOP_OFFSET_Y.equals(key)) {
                iosSignalDesktopOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_DESKTOP_OFFSET_Y);
            } else if (SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_X.equals(key)) {
                iosSignalKeyguardOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_SIGNAL_KEYGUARD_OFFSET_Y.equals(key)) {
                iosSignalKeyguardOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_KEYGUARD_OFFSET_Y);
            } else if (SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X.equals(key)) {
                iosSignalControlCenterOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y.equals(key)) {
                iosSignalControlCenterOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_CONTROL_CENTER_OFFSET_Y);
            } else if (SettingsStore.KEY_IOS_BATTERY_WIDTH.equals(key)) {
                iosBatteryWidth = parseInt(value, SettingsStore.DEFAULT_IOS_BATTERY_WIDTH);
            } else if (SettingsStore.KEY_IOS_BATTERY_HEIGHT.equals(key)) {
                iosBatteryHeight = parseInt(value, SettingsStore.DEFAULT_IOS_BATTERY_HEIGHT);
            } else if (SettingsStore.KEY_IOS_BATTERY_OFFSET_X.equals(key)) {
                iosBatteryOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_BATTERY_OFFSET_Y.equals(key)) {
                iosBatteryOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_BATTERY_OFFSET_Y);
            } else if (SettingsStore.KEY_IOS_BATTERY_TEXT_SIZE.equals(key)) {
                iosBatteryTextSize = parseInt(value, SettingsStore.DEFAULT_IOS_BATTERY_TEXT_SIZE);
            } else if (SettingsStore.KEY_IOS_GROUP_BATTERY_SCALE.equals(key)) {
                iosGroupBatteryScale = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_BATTERY_SCALE);
            } else if (SettingsStore.KEY_IOS_GROUP_SIGNAL_SCALE.equals(key)) {
                iosGroupSignalScale = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_SCALE);
            } else if (SettingsStore.KEY_IOS_GROUP_WIFI_SCALE.equals(key)) {
                iosGroupWifiScale = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_WIFI_SCALE);
            } else if (SettingsStore.KEY_IOS_GROUP_WIFI_SIGNAL_GAP.equals(key)) {
                iosGroupWifiSignalGap = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_WIFI_SIGNAL_GAP);
            } else if (SettingsStore.KEY_IOS_GROUP_SIGNAL_BATTERY_GAP.equals(key)) {
                iosGroupSignalBatteryGap = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_SIGNAL_BATTERY_GAP);
            } else if (SettingsStore.KEY_IOS_GROUP_START_GAP_ADJUST.equals(key)) {
                iosGroupStartGapAdjust = parseInt(value, SettingsStore.DEFAULT_IOS_GROUP_START_GAP_ADJUST);
            } else if (SettingsStore.KEY_IOS_WIFI_WIDTH.equals(key)) {
                iosWifiWidth = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_WIDTH);
            } else if (SettingsStore.KEY_IOS_WIFI_HEIGHT.equals(key)) {
                iosWifiHeight = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT);
            } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_X.equals(key)) {
                iosWifiOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_Y.equals(key)) {
                iosWifiOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y);
            } else if (SettingsStore.KEY_ACTIVITY_ICON_FACTOR.equals(key)) {
                activityIconFactor = parseInt(value, SettingsStore.DEFAULT_ACTIVITY_ICON_FACTOR);
            } else if (SettingsStore.KEY_CONNECTION_RATE_OFFSET_X.equals(key)) {
                connectionRateOffsetX = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_X);
            } else if (SettingsStore.KEY_CONNECTION_RATE_OFFSET_Y.equals(key)) {
                connectionRateOffsetY = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_Y);
            } else if (SettingsStore.KEY_CONNECTION_RATE_AUTO_VISIBILITY_ENABLED.equals(key)) {
                connectionRateThresholdEnabled = "1".equals(value);
            } else if (SettingsStore.KEY_CONNECTION_RATE_SHOW_THRESHOLD_KB.equals(key)) {
                connectionRateShowThresholdKb = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_THRESHOLD_KB);
            } else if (SettingsStore.KEY_CONNECTION_RATE_HIDE_THRESHOLD_KB.equals(key)) {
                connectionRateHideThresholdKb = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_THRESHOLD_KB);
            } else if (SettingsStore.KEY_CONNECTION_RATE_SHOW_SAMPLE_COUNT.equals(key)) {
                connectionRateShowSampleCount = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_SHOW_SAMPLE_COUNT);
            } else if (SettingsStore.KEY_CONNECTION_RATE_HIDE_SAMPLE_COUNT.equals(key)) {
                connectionRateHideSampleCount = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_HIDE_SAMPLE_COUNT);
            } else if (SettingsStore.KEY_TEXT_SCALE.equals(key)) {
                textScale = parseInt(value, SettingsStore.DEFAULT_TEXT_SCALE) / 100f;
            } else if (SettingsStore.KEY_SHOW_CLOCK_WEEKDAY.equals(key)) {
                showClockWeekday = "1".equals(value);
            } else if (SettingsStore.KEY_CLOCK_BOLD_ENABLED.equals(key)) {
                clockBoldEnabled = "1".equals(value);
            } else if (SettingsStore.KEY_CLOCK_FONT_WEIGHT.equals(key)) {
                clockFontWeight = parseInt(value, SettingsStore.DEFAULT_CLOCK_FONT_WEIGHT);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DUAL_COMBINED.equals(key)) {
                iosSignalDualCombined = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DEBUG_ENABLED.equals(key)) {
                iosSignalDebugEnabled = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_ENABLED.equals(key)) {
                iosSignalDebugSim1Enabled = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_ENABLED.equals(key)) {
                iosSignalDebugSim2Enabled = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM1_LEVEL.equals(key)) {
                iosSignalDebugSim1Level = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL);
            } else if (SettingsStore.KEY_IOS_SIGNAL_DEBUG_SIM2_LEVEL.equals(key)) {
                iosSignalDebugSim2Level = parseInt(value, SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL);
            } else if (SettingsStore.KEY_IOS_WIFI_DEBUG_ENABLED.equals(key)) {
                iosWifiDebugEnabled = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_WIFI_DEBUG_VISIBLE.equals(key)) {
                iosWifiDebugVisible = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_WIFI_DEBUG_LEVEL.equals(key)) {
                iosWifiDebugLevel = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_DEBUG_LEVEL);
            } else if (SettingsStore.KEY_MBACK_LONG_TOUCH_URL_ENABLED.equals(key)) {
                mbackLongTouchIntentEnabled = "1".equals(value);
            } else if (SettingsStore.KEY_MBACK_LONG_TOUCH_INTENT_URI.equals(key)) {
                mbackLongTouchIntentUri = value == null
                        ? SettingsStore.DEFAULT_MBACK_LONG_TOUCH_INTENT_URI
                        : value;
            } else if (SettingsStore.KEY_MBACK_NAV_BAR_TRANSPARENT.equals(key)) {
                mbackNavBarTransparent = "1".equals(value);
            } else if (SettingsStore.KEY_MBACK_HIDE_PILL.equals(key)) {
                mbackHidePill = "1".equals(value);
            } else if (SettingsStore.KEY_MBACK_INSET_SIZE.equals(key)) {
                mbackInsetSize = parseInt(value, SettingsStore.DEFAULT_MBACK_INSET_SIZE);
            } else if (SettingsStore.KEY_MBACK_NAV_BAR_HEIGHT.equals(key)) {
                mbackNavBarHeight = parseInt(value, SettingsStore.DEFAULT_MBACK_NAV_BAR_HEIGHT);
            }
        }

        private static int parseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }
}
