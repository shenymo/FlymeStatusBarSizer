package com.example.flymestatusbarsizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyDisplayInfo;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

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
    private static final WeakHashMap<View, Integer> ORIGINAL_CONNECTION_RATE_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, String> NETWORK_TYPE_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Integer> NETWORK_TYPE_RES_IDS = new WeakHashMap<>();
    private static final HashMap<Integer, String> NETWORK_TYPE_LABELS_BY_SUB_ID = new HashMap<>();
    private static final WeakHashMap<ImageView, Integer> WIFI_SIGNAL_LEVELS = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, ImageView> MOBILE_SIGNAL_DRAWABLE_OWNERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> MOBILE_VIEW_SLOTS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, MobileSignalInfo> MOBILE_SIGNAL_RAW_INFOS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, MobileSignalInfo> MOBILE_SIGNAL_INFOS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> PRIMARY_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> SECONDARY_SIGNAL_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> TRACKED_STATUS_TEXT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_CONNECTION_RATE_VIEWS = new WeakHashMap<>();
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
    private static final int[] DESKTOP_NETWORK_TYPE_SIZE = new int[2];
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
    private static final long[] INITIAL_RUNTIME_REFRESH_DELAYS_MS = {1000L, 3000L};
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static volatile boolean NETWORK_TYPE_REFRESH_PENDING;
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
    private static final HashMap<Integer, String> ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID = new HashMap<>();
    private static final HashMap<Integer, Long> ACTIVE_DATA_NETWORK_LABEL_TIMES_BY_SUB_ID = new HashMap<>();
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
        hookFlymeWifiView(loader);
        hookConnectionRateView(loader);
        hookImageViewTintUpdates(loader);
        hookSignalDrawableLevels(loader);
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
            if (config.iosBatteryStyle) {
                textView.setTextColor(Color.WHITE);
                setIntField(textView, "mNormalColor", Color.WHITE);
                setIntField(textView, "mLowColor", Color.WHITE);
            }
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
                    Object result = chain.proceed();
                    Object target = result instanceof View ? result : chain.getThisObject();
                    if (target instanceof View) {
                        View view = (View) target;
                        applyWifiSizing(view);
                        if (chain.getArgs().size() > 0) {
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
                        view.postDelayed(() -> {
                            applyConnectionRateTextScale(view);
                            applyConnectionRateOffset(view);
                        }, 500);
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
                        String idName = getSystemUiIdName(imageView);
                        if ("setImageResource".equals(name) && "mobile_type".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Integer) {
                            applyNetworkTypeResource(imageView, (Integer) chain.getArg(0));
                            if (isReferenceSignalContextChild(imageView)) {
                                Config config = Config.load(imageView.getContext());
                                if (config.enabled) {
                                    applyReferenceNetworkTypeSizing(imageView, config);
                                }
                            }
                        }
                        if ("setImageResource".equals(name) && "mobile_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Integer) {
                            applyMobileSignalResource(imageView, (Integer) chain.getArg(0));
                        }
                        if ("setImageDrawable".equals(name) && "mobile_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Drawable) {
                            Drawable drawable = (Drawable) chain.getArg(0);
                            MOBILE_SIGNAL_DRAWABLE_OWNERS.put(drawable, imageView);
                            handleMobileSignalDrawableState(imageView, drawable.getLevel());
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
                            applyWifiSignalResource(imageView, (Integer) chain.getArg(0));
                        }
                        if ("mobile_signal".equals(idName) && imageView.getDrawable() instanceof IosSignalDrawable) {
                            syncDrawableTint(imageView, (IosSignalDrawable) imageView.getDrawable());
                        } else if ("mobile_type".equals(idName) && imageView.getDrawable() instanceof NetworkTypeDrawable) {
                            syncDrawableTint(imageView, (NetworkTypeDrawable) imageView.getDrawable());
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
        } catch (Throwable ignored) {
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

    private static boolean drawIosBatteryIfNeeded(Object drawable, Canvas canvas) {
        Context context = (Context) getField(drawable, "mContext");
        if (context == null || !(drawable instanceof Drawable)) {
            return false;
        }
        Config config = Config.load(context);
        if (!config.enabled || !config.iosBatteryStyle) {
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
        if (!config.enabled || !config.iosBatteryStyle) {
            return false;
        }
        int level = getIntField(view, "mLastLevel", 0);
        boolean pluggedIn = getBooleanField(view, "mLastPlugged", false);
        boolean charging = getBooleanField(view, "mCharging", false);
        resizeIosBatteryView(batteryView, config, charging);
        boolean showPercent = getBooleanField(view, "mShowBatteryPercent", false);
        int width = dp(batteryView, config.iosBatteryWidth);
        int height = dp(batteryView, config.iosBatteryHeight);
        int left = dp(batteryView, config.iosBatteryOffsetX);
        int top = Math.round((batteryView.getHeight() - height) / 2f) + dp(batteryView, config.iosBatteryOffsetY);
        int boltWidth = charging ? Math.round(width * 0.5f) : 0;
        int boltExtraHeight = dp(batteryView, 2);
        int fillColor = normalizeIconColor(getIntField(view, "mFilterColor", Color.BLACK));
        IosBatteryPainter.draw(canvas, new Rect(left, top, left + width, top + height),
                level, pluggedIn, charging, showPercent, config.iosBatteryTextSize,
                fillColor, contrastTextColor(fillColor), boltWidth, boltExtraHeight);
        return true;
    }

    private static boolean measureIosBatteryViewIfNeeded(Object view) {
        if (!(view instanceof View)) {
            return false;
        }
        View batteryView = (View) view;
        Config config = Config.load(batteryView.getContext());
        if (!config.enabled || !config.iosBatteryStyle) {
            return false;
        }
        boolean charging = getBooleanField(view, "mCharging", false);
        setMeasuredDimension(batteryView, iosBatteryMeasuredWidth(batteryView, config, charging),
                iosBatteryMeasuredHeight(batteryView, config));
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
        if (!config.iosBatteryStyle) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        int width = iosBatteryMeasuredWidth(view, config, charging);
        int height = iosBatteryMeasuredHeight(view, config);
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
        int batteryWidth = dp(view, config.iosBatteryWidth);
        return charging ? batteryWidth + Math.round(batteryWidth * 0.5f) : batteryWidth;
    }

    private static int iosBatteryMeasuredHeight(View view, Config config) {
        return dp(view, config.iosBatteryHeight) + dp(view, 2);
    }

    private static void applyIosBatteryStyleIfNeeded(Object drawable) {
        Context context = (Context) getField(drawable, "mContext");
        if (context == null) {
            return;
        }
        Config config = Config.load(context);
        if (!config.enabled || !config.iosBatteryStyle) {
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
                } else {
                    scaleChild(root, "mobile_signal", config.scaled(config.mobileSignalFactor),
                            config.scaled(config.mobileSignalFactor));
                    applyIosSignalStyle(root, config);
                }
                applyKnownNetworkTypeStyle(root, config);
                View type = findSystemUiChild(root, "mobile_type");
                if (type instanceof ImageView) {
                    applyReferenceNetworkTypeSizing((ImageView) type, config);
                }
            } else {
                scaleChild(root, "mobile_signal", config.scaled(config.mobileSignalFactor),
                        config.scaled(config.mobileSignalFactor));
                applyIosSignalStyle(root, config);
                applyKnownNetworkTypeStyle(root, config);
            }
            scaleChild(root, "wifi_signal", config.scaled(config.wifiSignalFactor), config.scaled(config.wifiSignalFactor));
            applyIosWifiStyle(root, config);
            if (!shouldUseDesktopSignalReference(root)) {
                float networkTypeScale = config.scaled(config.networkTypeFactor);
                scaleChild(root, "mobile_type", networkTypeScale, networkTypeScale);
            }
            float networkTypeScale = config.scaled(config.networkTypeFactor);
            scaleChild(root, "mobile_volte", networkTypeScale, networkTypeScale);
            offsetNetworkType(root, getNetworkTypeOffsetX(root, config), getNetworkTypeOffsetY(root, config));
            scaleChild(root, "mobile_in", 1f, config.scaled(config.activityIconFactor));
            scaleChild(root, "mobile_out", 1f, config.scaled(config.activityIconFactor));
            scaleChild(root, "wifi_in", 1f, config.scaled(config.activityIconFactor));
            scaleChild(root, "wifi_out", 1f, config.scaled(config.activityIconFactor));
            if (shouldRecordDesktopReference(root)) {
                root.post(() -> {
                    recordDesktopIconSize(root, "mobile_signal", DESKTOP_MOBILE_SIGNAL_SIZE);
                    recordDesktopIconSize(root, "mobile_type", DESKTOP_NETWORK_TYPE_SIZE);
                });
                root.postDelayed(() -> {
                    recordDesktopIconSize(root, "mobile_signal", DESKTOP_MOBILE_SIGNAL_SIZE);
                    recordDesktopIconSize(root, "mobile_type", DESKTOP_NETWORK_TYPE_SIZE);
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
            }
            View type = findSystemUiChild(root, "mobile_type");
            if (type instanceof ImageView) {
                applyKnownNetworkTypeStyle(root, config);
                applyReferenceNetworkTypeSizing((ImageView) type, config);
            }
        });
    }

    private static void applyIosSignalStyle(View root, Config config) {
        if (!config.iosSignalStyle) {
            return;
        }
        View child = findSystemUiChild(root, "mobile_signal");
        if (!(child instanceof ImageView)) {
            return;
        }
        applyIosSignalImageView((ImageView) child, config);
    }

    private static void applyIosWifiStyle(View root, Config config) {
        if (!config.iosWifiStyle) {
            return;
        }
        View child = findSystemUiChild(root, "wifi_signal");
        if (!(child instanceof ImageView)) {
            return;
        }
        ImageView imageView = (ImageView) child;
        Integer level = WIFI_SIGNAL_LEVELS.get(imageView);
        if (level != null) {
            applyIosWifiImageView(imageView, level, config);
        }
    }

    private static void applySignalImageSizing(ImageView imageView, Config config) {
        scaleView(imageView, config.scaled(config.mobileSignalFactor), config.scaled(config.mobileSignalFactor));
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
            scaleView(imageView, config.scaled(config.mobileSignalFactor), config.scaled(config.mobileSignalFactor));
        }
        if (config.iosSignalStyle) {
            applyIosSignalImageView(imageView, config, offsetX, offsetY, false);
            imageView.setTranslationX(dp(imageView, offsetX));
            imageView.setTranslationY(dp(imageView, offsetY));
        } else {
            imageView.setTranslationX(0f);
            imageView.setTranslationY(0f);
        }
    }

    private static void applyReferenceNetworkTypeSizing(ImageView imageView, Config config) {
        int offsetX = getNetworkTypeOffsetX(imageView, config);
        int offsetY = getNetworkTypeOffsetY(imageView, config);
        int[] desktopSize = getRecordedSize(DESKTOP_NETWORK_TYPE_SIZE);
        int width = desktopSize == null ? 0 : desktopSize[0];
        int height = desktopSize == null ? 0 : desktopSize[1];
        if (width <= 0 || height <= 0) {
            int baseHeight = getSystemUiDimen(imageView.getContext(), "status_bar_mobile_type_size");
            if (baseHeight <= 0) {
                scaleView(imageView, config.scaled(config.networkTypeFactor), config.scaled(config.networkTypeFactor));
                imageView.setTranslationX(dp(imageView, offsetX));
                imageView.setTranslationY(dp(imageView, offsetY));
                return;
            }
            float scale = config.scaled(config.networkTypeFactor);
            height = Math.round(baseHeight * scale);
            Drawable drawable = imageView.getDrawable();
            if (drawable != null) {
                int intrinsicWidth = Math.max(drawable.getIntrinsicWidth(), 1);
                int intrinsicHeight = Math.max(drawable.getIntrinsicHeight(), 1);
                width = Math.round(height * (intrinsicWidth / (float) intrinsicHeight));
            } else {
                width = height;
            }
        }
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp == null) {
            return;
        }
        lp.width = width;
        lp.height = height;
        if (lp instanceof android.widget.FrameLayout.LayoutParams) {
            ((android.widget.FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_VERTICAL;
        }
        imageView.setLayoutParams(lp);
        imageView.setAdjustViewBounds(false);
        imageView.requestLayout();
        imageView.setTranslationX(dp(imageView, offsetX));
        imageView.setTranslationY(dp(imageView, offsetY));
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
        if (!config.iosSignalStyle) {
            return;
        }
        MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(imageView);
        int primaryLevel = info == null ? 0 : info.level;
        int slot = info == null ? getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN) : info.slot;
        primaryLevel = getConfiguredSignalLevel(config, slot, primaryLevel);
        int secondaryLevel = getDrawableSecondaryLevel(imageView, config, info);
        applyIosSignalImageView(imageView, primaryLevel, secondaryLevel, config);
        if (applyMarginOffset) {
            offsetView(imageView, offsetXDp, offsetYDp);
        }
    }

    private static void applyIosSignalImageView(ImageView imageView, int primaryLevel,
            int secondaryLevel, Config config) {
        if (!config.iosSignalStyle) {
            return;
        }
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

    private static int getDrawableSecondaryLevel(ImageView imageView, Config config, MobileSignalInfo info) {
        if (!config.iosSignalDualCombined) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        if (config.iosSignalDebugEnabled) {
            if (!config.iosSignalDebugSim1Enabled || !config.iosSignalDebugSim2Enabled) {
                return IosSignalDrawable.NO_SECONDARY_LEVEL;
            }
            int slot = info == null ? getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN) : info.slot;
            return slot != MOBILE_SLOT_SECONDARY
                    ? mapMobileSignalLevel(config.iosSignalDebugSim2Level)
                    : IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        int slot = info == null ? getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN) : info.slot;
        if (slot != MOBILE_SLOT_PRIMARY || latestSecondarySignalLevel == IosSignalDrawable.NO_SECONDARY_LEVEL) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        return latestSecondarySignalLevel;
    }

    private static void applyMobileSignalResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosSignalStyle) {
            return;
        }
        int subId = getMobileSubId(imageView);
        MobileSignalInfo info = getMobileSignalInfo(imageView.getResources(), resId,
                getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN), subId);
        if (info == null) {
            return;
        }
        applyMobileSignalInfo(imageView, info, config);
    }

    private static void handleMobileSignalDrawableState(ImageView imageView, int state) {
        applyMobileSignalDrawableState(imageView, state);
    }

    private static void applyMobileSignalDrawableState(ImageView imageView, int state) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosSignalStyle) {
            return;
        }
        int slot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
        int level = mapSignalDrawableStateLevel(state);
        applyMobileSignalInfo(imageView, new MobileSignalInfo(slot, level, getMobileSubId(imageView)), config);
    }

    private static void applyMobileSignalInfo(ImageView imageView, MobileSignalInfo info, Config config) {
        int currentSlot = getMobileSignalSlot(imageView, info.slot);
        if (currentSlot != MOBILE_SLOT_UNKNOWN && currentSlot != info.slot) {
            info = new MobileSignalInfo(currentSlot, info.level, info.subId);
        }
        registerTelephonySignalListener(imageView.getContext(), info.subId);
        int telephonyLevel = getCachedTelephonySignalLevel(info.subId);
        if (telephonyLevel != IosSignalDrawable.NO_SECONDARY_LEVEL && telephonyLevel != info.level) {
            info = new MobileSignalInfo(info.slot, telephonyLevel, info.subId);
        }
        MOBILE_SIGNAL_RAW_INFOS.put(imageView, info);
        MobileSignalInfo displayInfo = info;
        MOBILE_SIGNAL_INFOS.put(imageView, displayInfo);
        if (config.iosSignalDebugEnabled) {
            applyDebugMobileSignalInfo(imageView, displayInfo, config);
            return;
        }
        if (displayInfo.slot == MOBILE_SLOT_PRIMARY) {
            latestPrimarySignalLevel = displayInfo.level;
            PRIMARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
            SECONDARY_SIGNAL_VIEWS.remove(imageView);
            setMobileSignalViewVisible(imageView, true);
            applyIosSignalImageView(imageView, displayInfo.level, getDrawableSecondaryLevel(imageView, config, displayInfo), config);
        } else if (displayInfo.slot == MOBILE_SLOT_SECONDARY) {
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
    }

    private static void applyDebugMobileSignalInfo(ImageView imageView, MobileSignalInfo info, Config config) {
        int slot = info.slot;
        if (slot == MOBILE_SLOT_SECONDARY) {
            latestSecondarySignalLevel = mapMobileSignalLevel(config.iosSignalDebugSim2Level);
            SECONDARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
            PRIMARY_SIGNAL_VIEWS.remove(imageView);
            if (config.iosSignalDualCombined) {
                setMobileSignalViewVisible(imageView, false);
                updatePrimarySignalDrawables();
                return;
            }
            if (config.iosSignalDebugSim2Enabled) {
                setMobileSignalViewVisible(imageView, true);
                applyIosSignalImageView(imageView, config.iosSignalDebugSim2Level,
                        IosSignalDrawable.NO_SECONDARY_LEVEL, config);
            } else {
                setMobileSignalViewVisible(imageView, false);
            }
            return;
        }

        latestPrimarySignalLevel = getDebugPrimaryDisplayLevel(config, info.level);
        PRIMARY_SIGNAL_VIEWS.put(imageView, Boolean.TRUE);
        SECONDARY_SIGNAL_VIEWS.remove(imageView);
        if (!hasAnyDebugSignal(config)) {
            setMobileSignalViewVisible(imageView, false);
            return;
        }
        setMobileSignalViewVisible(imageView, true);
        applyIosSignalImageView(imageView, latestPrimarySignalLevel,
                getDrawableSecondaryLevel(imageView, config, info), config);
    }

    private static void updatePrimarySignalDrawables() {
        ArrayList<ImageView> views = new ArrayList<>(PRIMARY_SIGNAL_VIEWS.keySet());
        for (ImageView view : views) {
            if (view == null) {
                continue;
            }
            Config config = Config.load(view.getContext());
            if (!config.enabled || !config.iosSignalStyle || !config.iosSignalDualCombined) {
                continue;
            }
            if (config.iosSignalDebugEnabled && !hasAnyDebugSignal(config)) {
                setMobileSignalViewVisible(view, false);
                continue;
            }
            setMobileSignalViewVisible(view, true);
            MobileSignalInfo info = MOBILE_SIGNAL_INFOS.get(view);
            int primaryLevel = info == null ? latestPrimarySignalLevel : info.level;
            primaryLevel = getDebugPrimaryDisplayLevel(config, primaryLevel);
            int secondaryLevel = config.iosSignalDebugEnabled
                    ? getDrawableSecondaryLevel(view, config, info) : latestSecondarySignalLevel;
            applyIosSignalImageView(view, primaryLevel, secondaryLevel, config);
        }
    }

    private static int getConfiguredSignalLevel(Config config, int slot, int fallbackLevel) {
        if (!config.iosSignalDebugEnabled) {
            return fallbackLevel;
        }
        if (slot == MOBILE_SLOT_SECONDARY) {
            return config.iosSignalDebugSim2Enabled
                    ? mapMobileSignalLevel(config.iosSignalDebugSim2Level) : 0;
        }
        return getDebugPrimaryDisplayLevel(config, fallbackLevel);
    }

    private static int getDebugPrimaryDisplayLevel(Config config, int fallbackLevel) {
        if (!config.iosSignalDebugEnabled) {
            return fallbackLevel;
        }
        if (config.iosSignalDebugSim1Enabled) {
            return mapMobileSignalLevel(config.iosSignalDebugSim1Level);
        }
        if (config.iosSignalDebugSim2Enabled) {
            return mapMobileSignalLevel(config.iosSignalDebugSim2Level);
        }
        return 0;
    }

    private static boolean hasAnyDebugSignal(Config config) {
        return config.iosSignalDebugSim1Enabled || config.iosSignalDebugSim2Enabled;
    }

    private static void setMobileSignalViewVisible(ImageView imageView, boolean visible) {
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

    private static void applyWifiSignalResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosWifiStyle) {
            return;
        }
        Integer level = getWifiSignalLevel(imageView.getResources(), resId);
        if (level == null) {
            return;
        }
        WIFI_SIGNAL_LEVELS.put(imageView, level);
        applyIosWifiImageView(imageView, level, config);
    }

    private static void applyFlymeWifiStateResource(View root, Object state) {
        Config config = Config.load(root.getContext());
        if (!config.enabled || !config.iosWifiStyle || state == null) {
            return;
        }
        int resId = getIntField(state, "resId", 0);
        if (resId <= 0) {
            return;
        }
        Integer level = getWifiSignalLevel(root.getResources(), resId);
        if (level == null) {
            return;
        }
        View child = findSystemUiChild(root, "wifi_signal");
        if (!(child instanceof ImageView)) {
            Object wifiIcon = getField(root, "mWifiIcon");
            child = wifiIcon instanceof View ? (View) wifiIcon : null;
        }
        if (child instanceof ImageView) {
            ImageView imageView = (ImageView) child;
            WIFI_SIGNAL_LEVELS.put(imageView, level);
            applyIosWifiImageView(imageView, level, config);
        }
    }

    private static void applyIosWifiImageView(ImageView imageView, int level, Config config) {
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
    }

    private static void applyIosWifiLayout(ImageView imageView, Config config) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp != null) {
            float scale = config.scaled(config.wifiSignalFactor);
            lp.width = Math.round(dp(imageView, config.iosWifiWidth) * scale);
            lp.height = Math.round(dp(imageView, config.iosWifiHeight) * scale);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) lp;
                int[] original = ORIGINAL_MARGINS.get(imageView);
                if (original == null) {
                    original = new int[]{
                            marginLp.leftMargin,
                            marginLp.topMargin,
                            marginLp.rightMargin,
                            marginLp.bottomMargin,
                            marginLp.getMarginStart(),
                            marginLp.getMarginEnd()
                    };
                    ORIGINAL_MARGINS.put(imageView, original);
                }
                int marginEnd = original[5] + dp(imageView, config.iosWifiMarginEnd);
                marginLp.setMarginEnd(marginEnd);
                marginLp.rightMargin = original[2] + dp(imageView, config.iosWifiMarginEnd);
            }
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                ((android.widget.FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER;
            }
            imageView.setLayoutParams(lp);
            imageView.requestLayout();
        }
        disableAncestorClipping(imageView, 8);
        offsetView(imageView, config.iosWifiOffsetX, config.iosWifiOffsetY);
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
            int subId = invokeNoArgInt(view, "getSubId", getIntField(view, "subId", UNSET_SUB_ID));
            int level = getCachedTelephonySignalLevel(subId);
            if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                level = 0;
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

    private static int getTelephonySignalLevel(Context context, int subId) {
        if (context == null || subId == UNSET_SUB_ID || subId < 0) {
            return IosSignalDrawable.NO_SECONDARY_LEVEL;
        }
        int cachedLevel = getCachedTelephonySignalLevel(subId);
        if (cachedLevel != IosSignalDrawable.NO_SECONDARY_LEVEL) {
            return cachedLevel;
        }
        return queryTelephonySignalLevel(context, subId);
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
                int initialLevel = queryTelephonySignalLevel(context, subId);
                if (initialLevel != IosSignalDrawable.NO_SECONDARY_LEVEL) {
                    rememberTelephonySignalLevel(subId, initialLevel);
                }
                String initialNetworkType = queryActiveDataNetworkTypeLabel(context, subId, true);
                if (initialNetworkType != null) {
                    rememberActiveDataNetworkTypeLabel(subId, initialNetworkType);
                }
                PhoneStateListener listener = new PhoneStateListener(context.getMainExecutor()) {
                    @Override
                    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                        if (signalStrength == null) {
                            return;
                        }
                        updateSignalLevelForSubId(subId, signalStrength.getLevel());
                    }

                    @Override
                    public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
                        invalidateNetworkTypeCache();
                        scheduleNetworkTypeRefresh();
                    }

                    @Override
                    public void onDataConnectionStateChanged(int state, int networkType) {
                        invalidateNetworkTypeCache();
                        scheduleNetworkTypeRefresh();
                    }
                };
                manager.createForSubscriptionId(subId).listen(listener,
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                                | PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
                                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
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
                if (!config.enabled || !config.iosSignalStyle) {
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
        Config config = CACHED_CONFIG;
        if (config == null) {
            Context context = SYSTEM_UI_CONTEXT;
            if (context == null) {
                return fallbackLevel;
            }
            config = Config.load(context);
        }
        if (!config.enabled || !config.iosSignalDebugEnabled) {
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
        int highByteLevel = (state >> 8) & 0xff;
        int rawLevel = lowByteLevel;
        if (highByteLevel > 0 && highByteLevel <= IosSignalDrawable.MAX_LEVEL
                && lowByteLevel >= IosSignalDrawable.MAX_LEVEL) {
            rawLevel = highByteLevel;
        } else if (rawLevel >= 90) {
            rawLevel -= 90;
        }
        return mapMobileSignalLevel(rawLevel);
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
        if (lowerName.contains("null") || lowerName.contains("empty")
                || lowerName.contains("no_network") || lowerName.contains("not_connected")
                || lowerName.contains("disconnected") || lowerName.contains("slash")
                || lowerName.contains("off")) {
            return 0;
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

    private static void applyNetworkTypeResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosNetworkTypeStyle) {
            return;
        }
        NETWORK_TYPE_RES_IDS.put(imageView, resId);
        int activeDataSubId = getActiveDataSubId();
        int viewSubId = getMobileSubId(imageView);
        int viewSlot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
        String resourceLabel = getNetworkTypeLabel(imageView.getResources(), resId);
        rememberNetworkTypeLabelForSubId(viewSubId, resourceLabel);
        String label = null;
        if (config.iosSignalDualCombined && viewSlot == MOBILE_SLOT_SECONDARY) {
            label = "";
        } else if (!config.iosSignalDualCombined && isValidSubId(activeDataSubId)
                && isValidSubId(viewSubId) && viewSubId != activeDataSubId) {
            label = "";
        } else {
            label = getCachedOrRememberedActiveDataNetworkTypeLabel(activeDataSubId);
        }
        if (label == null) {
            label = "";
        }
        applyNetworkTypeLabel(imageView, label);
    }

    private static void applyNetworkTypeLabel(ImageView imageView, String label) {
        if (label == null || label.length() == 0) {
            NETWORK_TYPE_LABELS.remove(imageView);
            ensureNetworkTypePlaceholder(imageView);
            imageView.setVisibility(View.INVISIBLE);
            setParentVisibility(imageView, true);
            return;
        }
        String oldLabel = NETWORK_TYPE_LABELS.get(imageView);
        NETWORK_TYPE_LABELS.put(imageView, label);
        imageView.setVisibility(View.VISIBLE);
        setParentVisibility(imageView, true);
        if (label.equals(oldLabel)
                && imageView.getDrawable() instanceof NetworkTypeDrawable) {
            syncDrawableTint(imageView, imageView.getDrawable());
            imageView.setAdjustViewBounds(false);
            return;
        }
        NetworkTypeDrawable drawable = new NetworkTypeDrawable(label, imageView.getResources().getDisplayMetrics().density);
        syncDrawableTint(imageView, drawable);
        imageView.setImageDrawable(drawable);
        imageView.setAdjustViewBounds(false);
    }

    private static void ensureNetworkTypePlaceholder(ImageView imageView) {
        if (imageView.getDrawable() instanceof NetworkTypeDrawable) {
            return;
        }
        NetworkTypeDrawable drawable = new NetworkTypeDrawable("5G",
                imageView.getResources().getDisplayMetrics().density);
        syncDrawableTint(imageView, drawable);
        imageView.setImageDrawable(drawable);
        imageView.setAdjustViewBounds(false);
    }

    private static void applyKnownNetworkTypeStyle(View root, Config config) {
        if (!config.iosNetworkTypeStyle) {
            return;
        }
        View child = findSystemUiChild(root, "mobile_type");
        if (!(child instanceof ImageView)) {
            return;
        }
        ImageView imageView = (ImageView) child;
        String label = NETWORK_TYPE_LABELS.get(imageView);
        if (label == null) {
            return;
        }
        imageView.setVisibility(View.VISIBLE);
        setParentVisibility(imageView, true);
        if (!(imageView.getDrawable() instanceof NetworkTypeDrawable)) {
            NetworkTypeDrawable drawable = new NetworkTypeDrawable(label, imageView.getResources().getDisplayMetrics().density);
            syncDrawableTint(imageView, drawable);
            imageView.setImageDrawable(drawable);
        }
    }

    private static String getNetworkTypeLabel(Resources resources, int resId) {
        if (resId == 0) {
            return null;
        }
        String name;
        try {
            name = resources.getResourceEntryName(resId).toLowerCase();
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
        if (name.contains("5ga") || name.contains("5g_a") || name.contains("5g_advanced")) {
            return "5GA";
        }
        if (name.contains("5g_plus") || name.contains("5g_ca") || name.contains("5g_sa")
                || name.contains("5g_uwb") || name.contains("fully_connected_5g_plus")) {
            return "5G+";
        }
        if (name.contains("5g") && !name.contains("5g_e") && !name.contains("5ge")) {
            return "5G";
        }
        return null;
    }

    private static void rememberNetworkTypeLabelForSubId(int subId, String label) {
        if (!isValidSubId(subId)) {
            return;
        }
        synchronized (NETWORK_TYPE_LABELS_BY_SUB_ID) {
            if (label == null || label.length() == 0) {
                NETWORK_TYPE_LABELS_BY_SUB_ID.remove(subId);
            } else {
                NETWORK_TYPE_LABELS_BY_SUB_ID.put(subId, label);
            }
        }
    }

    private static String getRememberedNetworkTypeLabelForSubId(int subId) {
        if (!isValidSubId(subId)) {
            return null;
        }
        synchronized (NETWORK_TYPE_LABELS_BY_SUB_ID) {
            return NETWORK_TYPE_LABELS_BY_SUB_ID.get(subId);
        }
    }

    private static String getActiveDataNetworkTypeLabel(Context context) {
        return getActiveDataNetworkTypeLabel(context, getActiveDataSubId(), true);
    }

    private static String getActiveDataNetworkTypeLabel(Context context, int subId, boolean preferRemembered) {
        if (context == null) {
            return null;
        }
        if (!isValidSubId(subId)) {
            return null;
        }
        String cachedLabel = getCachedActiveDataNetworkTypeLabel(subId);
        if (cachedLabel != null) {
            return cachedLabel;
        }
        return queryActiveDataNetworkTypeLabel(context, subId, preferRemembered);
    }

    private static String queryActiveDataNetworkTypeLabel(Context context, int subId, boolean preferRemembered) {
        if (context == null) {
            return null;
        }
        if (!isValidSubId(subId)) {
            return null;
        }
        String label = null;
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (manager == null) {
                return null;
            }
            TelephonyManager dataManager = manager.createForSubscriptionId(subId);
            String displayLabel = getTelephonyDisplayInfoNetworkTypeLabel(dataManager);
            if (displayLabel != null) {
                label = displayLabel;
                rememberActiveDataNetworkTypeLabel(subId, label);
                return label;
            }
            String serviceStateLabel = getServiceStateNetworkTypeLabel(dataManager);
            if (serviceStateLabel != null) {
                label = serviceStateLabel;
                rememberActiveDataNetworkTypeLabel(subId, label);
                return label;
            }
            label = "";
            rememberActiveDataNetworkTypeLabel(subId, label);
            return label;
        } catch (Throwable ignored) {
        }
        if (preferRemembered) {
            String rememberedLabel = getRememberedNetworkTypeLabelForSubId(subId);
            if (rememberedLabel != null) {
                label = rememberedLabel;
                rememberActiveDataNetworkTypeLabel(subId, label);
                return label;
            }
        }
        return null;
    }

    private static String getCachedActiveDataNetworkTypeLabel(int subId) {
        synchronized (ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID) {
            String label = ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID.get(subId);
            Long uptime = ACTIVE_DATA_NETWORK_LABEL_TIMES_BY_SUB_ID.get(subId);
            if (label != null && uptime != null
                    && SystemClock.uptimeMillis() - uptime <= TELEPHONY_CACHE_TTL_MS) {
                return label;
            }
        }
        return null;
    }

    private static String getCachedOrRememberedActiveDataNetworkTypeLabel(int subId) {
        String label = getCachedActiveDataNetworkTypeLabel(subId);
        if (label != null) {
            return label;
        }
        return getRememberedNetworkTypeLabelForSubId(subId);
    }

    private static void rememberActiveDataNetworkTypeLabel(int subId, String label) {
        if (!isValidSubId(subId) || label == null) {
            return;
        }
        synchronized (ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID) {
            ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID.put(subId, label);
            ACTIVE_DATA_NETWORK_LABEL_TIMES_BY_SUB_ID.put(subId, SystemClock.uptimeMillis());
        }
    }

    private static void invalidateNetworkTypeCache() {
        synchronized (ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID) {
            ACTIVE_DATA_NETWORK_LABELS_BY_SUB_ID.clear();
            ACTIVE_DATA_NETWORK_LABEL_TIMES_BY_SUB_ID.clear();
        }
    }

    private static int getActiveDataSubId() {
        try {
            Class<?> clazz = Class.forName("android.telephony.SubscriptionManager");
            Method method = clazz.getDeclaredMethod("getActiveDataSubscriptionId");
            Object value = method.invoke(null);
            if (value instanceof Integer && isValidSubId((Integer) value)) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> clazz = Class.forName("android.telephony.SubscriptionManager");
            Method method = clazz.getDeclaredMethod("getDefaultDataSubscriptionId");
            Object value = method.invoke(null);
            if (value instanceof Integer && isValidSubId((Integer) value)) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        return UNSET_SUB_ID;
    }

    private static String getTelephonyDisplayInfoNetworkTypeLabel(TelephonyManager manager) {
        Object displayInfo = invokeNoArg(manager, "getTelephonyDisplayInfo");
        if (displayInfo == null) {
            return null;
        }
        int networkType = invokeNoArgInt(displayInfo, "getNetworkType",
                getIntField(displayInfo, "mNetworkType", -1));
        int overrideNetworkType = invokeNoArgInt(displayInfo, "getOverrideNetworkType",
                getIntField(displayInfo, "mOverrideNetworkType", 0));
        if (overrideNetworkType == 5) {
            return "5GA";
        }
        if (overrideNetworkType == 4) {
            return "5G+";
        }
        if (overrideNetworkType == 3) {
            return "5G";
        }
        if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
            return "5G";
        }
        return "";
    }

    private static String getServiceStateNetworkTypeLabel(TelephonyManager manager) {
        Object serviceState = invokeNoArg(manager, "getServiceState");
        if (serviceState == null) {
            return null;
        }
        int nrState = invokeNoArgInt(serviceState, "getNrState", getIntField(serviceState, "mNrState", -1));
        if (nrState == 3) {
            return "5G";
        }
        Object infos = invokeNoArg(serviceState, "getNetworkRegistrationInfoList");
        if (infos instanceof Iterable) {
            boolean sawRegisteredData = false;
            for (Object info : (Iterable<?>) infos) {
                int domain = invokeNoArgInt(info, "getDomain", -1);
                int transportType = invokeNoArgInt(info, "getTransportType", -1);
                int registrationState = invokeNoArgInt(info, "getRegistrationState",
                        invokeNoArgInt(info, "getNetworkRegistrationState", -1));
                int accessNetworkTechnology = invokeNoArgInt(info, "getAccessNetworkTechnology", -1);
                if (domain == 2 && transportType == 1 && (registrationState == 1 || registrationState == 5)) {
                    sawRegisteredData = true;
                    if (accessNetworkTechnology == TelephonyManager.NETWORK_TYPE_NR) {
                        return "5G";
                    }
                }
            }
            if (sawRegisteredData) {
                return "";
            }
        }
        String state = String.valueOf(serviceState);
        if (state.contains("domain=PS") && state.contains("transportType=WWAN")
                && (state.contains("registrationState=HOME") || state.contains("registrationState=ROAMING"))
                && state.contains("accessNetworkTechnology=NR")) {
            return "5G";
        }
        return "";
    }

    private static void syncDrawableTint(ImageView imageView, android.graphics.drawable.Drawable drawable) {
        drawable.setState(imageView.getDrawableState());
        drawable.setTintList(imageView.getImageTintList());
        drawable.setTintMode(imageView.getImageTintMode());
        drawable.setColorFilter(imageView.getColorFilter());
    }

    private static void setParentVisibility(View view, boolean visible) {
        ViewParent parent = view.getParent();
        if (parent instanceof View && "mobile_type_container".equals(getSystemUiIdName((View) parent))) {
            ((View) parent).setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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
        if (scene == SIGNAL_SCENE_KEYGUARD) {
            return config.iosSignalKeyguardOffsetX;
        }
        if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
            return config.iosSignalControlCenterOffsetX;
        }
        return config.iosSignalDesktopOffsetX;
    }

    private static int getIosSignalOffsetY(View view, Config config) {
        int scene = resolveSignalScene(view);
        if (scene == SIGNAL_SCENE_KEYGUARD) {
            return config.iosSignalKeyguardOffsetY;
        }
        if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
            return config.iosSignalControlCenterOffsetY;
        }
        return config.iosSignalDesktopOffsetY;
    }

    private static int getNetworkTypeOffsetX(View view, Config config) {
        int scene = resolveSignalScene(view);
        if (scene == SIGNAL_SCENE_KEYGUARD) {
            return config.networkTypeKeyguardOffsetX;
        }
        if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
            return config.networkTypeControlCenterOffsetX;
        }
        return config.networkTypeDesktopOffsetX;
    }

    private static int getNetworkTypeOffsetY(View view, Config config) {
        int scene = resolveSignalScene(view);
        if (scene == SIGNAL_SCENE_KEYGUARD) {
            return config.networkTypeKeyguardOffsetY;
        }
        if (scene == SIGNAL_SCENE_CONTROL_CENTER) {
            return config.networkTypeControlCenterOffsetY;
        }
        return config.networkTypeDesktopOffsetY;
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
                    invalidateNetworkTypeCache();
                    refreshTrackedRuntimeViews();
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

    private static void handleSubscriptionsChanged() {
        Handler handler = MAIN_HANDLER;
        Runnable action = () -> {
            resetMobileSubscriptionState();
            invalidateNetworkTypeCache();
            refreshTrackedSignalViews(true);
            refreshTrackedNetworkTypeViews();
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
        synchronized (NETWORK_TYPE_LABELS_BY_SUB_ID) {
            NETWORK_TYPE_LABELS_BY_SUB_ID.clear();
        }
        MOBILE_VIEW_SLOTS.clear();
        MOBILE_SIGNAL_INFOS.clear();
        PRIMARY_SIGNAL_VIEWS.clear();
        SECONDARY_SIGNAL_VIEWS.clear();
    }

    private static void refreshTrackedRuntimeViews() {
        refreshTrackedTextScaling();
        refreshTrackedSignalViews();
        refreshTrackedNetworkTypeViews();
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
                view.requestLayout();
                view.invalidate();
            }
        });
    }

    private static void refreshTrackedSignalViews() {
        refreshTrackedSignalViews(false);
    }

    private static void refreshTrackedSignalViews(boolean forceRequery) {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<ImageView> signalViews = new ArrayList<>(MOBILE_SIGNAL_RAW_INFOS.keySet());
            for (ImageView imageView : signalViews) {
                if (imageView == null) {
                    continue;
                }
                Config config = Config.load(imageView.getContext());
                if (!config.enabled || !config.iosSignalStyle) {
                    continue;
                }
                MobileSignalInfo info = MOBILE_SIGNAL_RAW_INFOS.get(imageView);
                if (forceRequery || info == null) {
                    int slot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
                    int subId = getMobileSubId(imageView);
                    int level = getCachedTelephonySignalLevel(subId);
                    if (level == IosSignalDrawable.NO_SECONDARY_LEVEL) {
                        level = forceRequery || info == null ? 0 : info.level;
                    }
                    info = new MobileSignalInfo(slot, level, subId);
                }
                applyMobileSignalInfo(imageView, info, config);
                imageView.requestLayout();
                imageView.invalidate();
            }
        });
    }

    private static void refreshTrackedNetworkTypeViews() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            ArrayList<ImageView> typeViews = new ArrayList<>(NETWORK_TYPE_RES_IDS.keySet());
            int activeDataSubId = getActiveDataSubId();
            for (ImageView imageView : typeViews) {
                if (imageView == null) {
                    continue;
                }
                Config config = Config.load(imageView.getContext());
                if (!config.enabled || !config.iosNetworkTypeStyle) {
                    continue;
                }
                int viewSubId = getMobileSubId(imageView);
                int viewSlot = getMobileSignalSlot(imageView, MOBILE_SLOT_UNKNOWN);
                String label = null;
                if (config.iosSignalDualCombined && viewSlot == MOBILE_SLOT_SECONDARY) {
                    label = "";
                } else if (!config.iosSignalDualCombined && isValidSubId(activeDataSubId)
                        && isValidSubId(viewSubId) && viewSubId != activeDataSubId) {
                    label = "";
                } else {
                    label = getCachedOrRememberedActiveDataNetworkTypeLabel(activeDataSubId);
                }
                if (label == null) {
                    label = "";
                }
                applyNetworkTypeLabel(imageView, label);
                imageView.requestLayout();
                imageView.invalidate();
            }
        });
    }

    private static void scheduleNetworkTypeRefresh() {
        Handler handler = MAIN_HANDLER;
        if (handler == null) {
            return;
        }
        synchronized (RUNTIME_REFRESH_LOCK) {
            if (NETWORK_TYPE_REFRESH_PENDING) {
                return;
            }
            NETWORK_TYPE_REFRESH_PENDING = true;
        }
        handler.postDelayed(() -> {
            synchronized (RUNTIME_REFRESH_LOCK) {
                NETWORK_TYPE_REFRESH_PENDING = false;
            }
            refreshTrackedNetworkTypeViews();
        }, 80);
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
        if (config.enabled && config.iosBatteryStyle) {
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

    private static void offsetNetworkType(View root, int offsetXDp, int offsetYDp) {
        View container = findSystemUiChild(root, "mobile_type_container");
        if (container != null) {
            offsetView(container, offsetXDp, offsetYDp);
        } else {
            offsetChild(root, "mobile_type", offsetXDp, offsetYDp);
        }
        offsetChild(root, "mobile_volte", offsetXDp, offsetYDp);
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
        int gapPx = getUnifiedStatusIconGapPx(view.getContext());
        if (gapPx <= 0) {
            return;
        }
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

    private static final class Config {
        boolean enabled = SettingsStore.DEFAULT_ENABLED;
        float globalIconScale = SettingsStore.DEFAULT_GLOBAL_ICON_SCALE / 100f;
        int mobileSignalFactor = SettingsStore.DEFAULT_MOBILE_SIGNAL_FACTOR;
        int wifiSignalFactor = SettingsStore.DEFAULT_WIFI_SIGNAL_FACTOR;
        int batteryFactor = SettingsStore.DEFAULT_BATTERY_FACTOR;
        int statusIconFactor = SettingsStore.DEFAULT_STATUS_ICON_FACTOR;
        int networkTypeFactor = SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR;
        int networkTypeDesktopOffsetX = SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X;
        int networkTypeDesktopOffsetY = SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y;
        int networkTypeKeyguardOffsetX = SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X;
        int networkTypeKeyguardOffsetY = SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y;
        int networkTypeControlCenterOffsetX = SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X;
        int networkTypeControlCenterOffsetY = SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y;
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
        int iosWifiWidth = SettingsStore.DEFAULT_IOS_WIFI_WIDTH;
        int iosWifiHeight = SettingsStore.DEFAULT_IOS_WIFI_HEIGHT;
        int iosWifiOffsetX = SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X;
        int iosWifiOffsetY = SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y;
        int iosWifiMarginEnd = SettingsStore.DEFAULT_IOS_WIFI_MARGIN_END;
        int activityIconFactor = SettingsStore.DEFAULT_ACTIVITY_ICON_FACTOR;
        int connectionRateOffsetX = SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_X;
        int connectionRateOffsetY = SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_Y;
        float textScale = SettingsStore.DEFAULT_TEXT_SCALE / 100f;
        boolean showClockWeekday = SettingsStore.DEFAULT_SHOW_CLOCK_WEEKDAY;
        boolean iosBatteryStyle = SettingsStore.DEFAULT_IOS_BATTERY_STYLE;
        boolean iosSignalStyle = SettingsStore.DEFAULT_IOS_SIGNAL_STYLE;
        boolean iosSignalDualCombined = SettingsStore.DEFAULT_IOS_SIGNAL_DUAL_COMBINED;
        boolean iosSignalDebugEnabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_ENABLED;
        boolean iosSignalDebugSim1Enabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_ENABLED;
        boolean iosSignalDebugSim2Enabled = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_ENABLED;
        int iosSignalDebugSim1Level = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM1_LEVEL;
        int iosSignalDebugSim2Level = SettingsStore.DEFAULT_IOS_SIGNAL_DEBUG_SIM2_LEVEL;
        boolean iosNetworkTypeStyle = SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE;
        boolean iosWifiStyle = SettingsStore.DEFAULT_IOS_WIFI_STYLE;

        float scaled(int factorPercent) {
            return 1f + ((globalIconScale - 1f) * (factorPercent / 100f));
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
            } else if (SettingsStore.KEY_NETWORK_TYPE_FACTOR.equals(key)) {
                networkTypeFactor = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_FACTOR);
            } else if (SettingsStore.KEY_NETWORK_TYPE_OFFSET_X.equals(key)) {
                int legacyValue = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_X);
                networkTypeDesktopOffsetX = legacyValue;
                networkTypeKeyguardOffsetX = legacyValue;
                networkTypeControlCenterOffsetX = legacyValue;
            } else if (SettingsStore.KEY_NETWORK_TYPE_OFFSET_Y.equals(key)) {
                int legacyValue = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_OFFSET_Y);
                networkTypeDesktopOffsetY = legacyValue;
                networkTypeKeyguardOffsetY = legacyValue;
                networkTypeControlCenterOffsetY = legacyValue;
            } else if (SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_X.equals(key)) {
                networkTypeDesktopOffsetX = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_X);
            } else if (SettingsStore.KEY_NETWORK_TYPE_DESKTOP_OFFSET_Y.equals(key)) {
                networkTypeDesktopOffsetY = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_DESKTOP_OFFSET_Y);
            } else if (SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_X.equals(key)) {
                networkTypeKeyguardOffsetX = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_X);
            } else if (SettingsStore.KEY_NETWORK_TYPE_KEYGUARD_OFFSET_Y.equals(key)) {
                networkTypeKeyguardOffsetY = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_KEYGUARD_OFFSET_Y);
            } else if (SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X.equals(key)) {
                networkTypeControlCenterOffsetX = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_X);
            } else if (SettingsStore.KEY_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y.equals(key)) {
                networkTypeControlCenterOffsetY = parseInt(value, SettingsStore.DEFAULT_NETWORK_TYPE_CONTROL_CENTER_OFFSET_Y);
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
            } else if (SettingsStore.KEY_IOS_WIFI_WIDTH.equals(key)) {
                iosWifiWidth = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_WIDTH);
            } else if (SettingsStore.KEY_IOS_WIFI_HEIGHT.equals(key)) {
                iosWifiHeight = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_HEIGHT);
            } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_X.equals(key)) {
                iosWifiOffsetX = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_X);
            } else if (SettingsStore.KEY_IOS_WIFI_OFFSET_Y.equals(key)) {
                iosWifiOffsetY = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_OFFSET_Y);
            } else if (SettingsStore.KEY_IOS_WIFI_MARGIN_END.equals(key)) {
                iosWifiMarginEnd = parseInt(value, SettingsStore.DEFAULT_IOS_WIFI_MARGIN_END);
            } else if (SettingsStore.KEY_ACTIVITY_ICON_FACTOR.equals(key)) {
                activityIconFactor = parseInt(value, SettingsStore.DEFAULT_ACTIVITY_ICON_FACTOR);
            } else if (SettingsStore.KEY_CONNECTION_RATE_OFFSET_X.equals(key)) {
                connectionRateOffsetX = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_X);
            } else if (SettingsStore.KEY_CONNECTION_RATE_OFFSET_Y.equals(key)) {
                connectionRateOffsetY = parseInt(value, SettingsStore.DEFAULT_CONNECTION_RATE_OFFSET_Y);
            } else if (SettingsStore.KEY_TEXT_SCALE.equals(key)) {
                textScale = parseInt(value, SettingsStore.DEFAULT_TEXT_SCALE) / 100f;
            } else if (SettingsStore.KEY_SHOW_CLOCK_WEEKDAY.equals(key)) {
                showClockWeekday = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_BATTERY_STYLE.equals(key)) {
                iosBatteryStyle = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_STYLE.equals(key)) {
                iosSignalStyle = "1".equals(value);
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
            } else if (SettingsStore.KEY_IOS_NETWORK_TYPE_STYLE.equals(key)) {
                iosNetworkTypeStyle = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_WIFI_STYLE.equals(key)) {
                iosWifiStyle = "1".equals(value);
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
