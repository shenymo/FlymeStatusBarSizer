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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class FlymeStatusBarSizer extends XposedModule {
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final boolean DEBUG_WIFI_UPDATES = true;
    private static volatile FlymeStatusBarSizer MODULE;

    private static final Uri SETTINGS_URI = Uri.parse("content://" + SettingsStore.AUTHORITY + "/settings");
    private static final WeakHashMap<View, int[]> ORIGINAL_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_MARGINS = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> ORIGINAL_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, float[]> ORIGINAL_TRANSLATIONS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Float> ORIGINAL_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> ORIGINAL_CONNECTION_RATE_TEXT_SIZES = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, String> NETWORK_TYPE_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Integer> WIFI_SIGNAL_LEVELS = new WeakHashMap<>();
    private static final WeakHashMap<View, String> WIFI_STATE_DEBUG_KEYS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Boolean> TRACKED_STATUS_TEXT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TRACKED_CONNECTION_RATE_VIEWS = new WeakHashMap<>();
    private static final Pattern WIFI_LEVEL_PATTERN = Pattern.compile("(?:^|[_-])([0-4])(?:$|[_-])");
    private static final Pattern WIFI_COMPACT_LEVEL_PATTERN = Pattern.compile("(?:wifi|wlan|signal|level)[_-]?([0-4])");
    private static final int[] DESKTOP_MOBILE_SIGNAL_SIZE = new int[2];
    private static final int[] DESKTOP_NETWORK_TYPE_SIZE = new int[2];
    private static final int SIGNAL_SCENE_DESKTOP = 0;
    private static final int SIGNAL_SCENE_KEYGUARD = 1;
    private static final int SIGNAL_SCENE_CONTROL_CENTER = 2;
    private static final Object CONFIG_REFRESH_LOCK = new Object();
    private static volatile boolean CONFIG_REFRESH_REGISTERED;
    private static Handler MAIN_HANDLER;
    private static BroadcastReceiver USER_UNLOCKED_RECEIVER;
    private static ContentObserver SETTINGS_OBSERVER;

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
        hookFlymeWifiView(loader);
        hookConnectionRateView(loader);
        hookImageViewTintUpdates(loader);
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
                        applyStatusBarSizing((View) result);
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
                        applyWifiStateLevel(view, chain.getArgs());
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, TAG, "Failed to hook FlymeStatusBarWifiView", t);
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
                        && !"setImageDrawable".equals(name) && !"setImageLevel".equals(name)) {
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
                            logWifiImageUpdate(imageView, "setImageResource", (Integer) chain.getArg(0));
                            applyWifiSignalResource(imageView, (Integer) chain.getArg(0));
                        } else if ("setImageLevel".equals(name) && "wifi_signal".equals(idName)
                                && chain.getArgs().size() == 1 && chain.getArg(0) instanceof Integer) {
                            logWifiImageUpdate(imageView, "setImageLevel", (Integer) chain.getArg(0));
                            applyWifiSignalLevel(imageView, (Integer) chain.getArg(0));
                        } else if ("setImageDrawable".equals(name) && "wifi_signal".equals(idName)
                                && !(imageView.getDrawable() instanceof IosWifiDrawable)) {
                            logWifiImageUpdate(imageView, "setImageDrawable", imageView.getDrawable());
                            Config config = Config.load(imageView.getContext());
                            if (config.enabled && config.iosWifiStyle) {
                                applyIosWifiImageView(imageView, getCurrentWifiLevel(imageView), config);
                            }
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
        applyIosWifiImageView(imageView, getCurrentWifiLevel(imageView), config);
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
        IosSignalDrawable drawable = new IosSignalDrawable(imageView.getResources().getDisplayMetrics().density);
        drawable.setState(imageView.getDrawableState());
        drawable.setTintList(imageView.getImageTintList());
        drawable.setTintMode(imageView.getImageTintMode());
        drawable.setColorFilter(imageView.getColorFilter());
        imageView.setImageDrawable(drawable);
        imageView.setAdjustViewBounds(false);
        if (applyMarginOffset) {
            offsetView(imageView, offsetXDp, offsetYDp);
        }
    }

    private static void applyWifiSignalResource(ImageView imageView, int resId) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosWifiStyle) {
            return;
        }
        Integer level = getWifiSignalLevel(imageView.getResources(), resId);
        if (level == null) {
            String resourceName = getResourceName(imageView.getResources(), resId);
            logToFramework("Unable to parse wifi_signal level from resource: "
                    + resourceName + " (" + resId + ")");
            level = IosWifiDrawable.LEVEL_ERROR;
        }
        WIFI_SIGNAL_LEVELS.put(imageView, level);
        applyIosWifiImageView(imageView, level, config);
    }

    private static void applyWifiSignalLevel(ImageView imageView, int imageLevel) {
        Config config = Config.load(imageView.getContext());
        if (!config.enabled || !config.iosWifiStyle) {
            return;
        }
        int level = normalizeWifiImageLevel(imageLevel);
        WIFI_SIGNAL_LEVELS.put(imageView, level);
        applyIosWifiImageView(imageView, level, config);
    }

    private static void applyWifiStateLevel(View root, List<?> args) {
        Config config = Config.load(root.getContext());
        if (!config.enabled || !config.iosWifiStyle) {
            return;
        }
        logWifiStateUpdate(root, args);
        Integer level = extractWifiLevel(root.getResources(), args, 0);
        if (level == null) {
            level = extractWifiLevel(root.getResources(), root, 0);
        }
        if (level == null) {
            logWifiStateDebug(root, args);
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

    private static void logWifiImageUpdate(ImageView imageView, String methodName, Object value) {
        if (!DEBUG_WIFI_UPDATES) {
            return;
        }
        String detail;
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            detail = intValue + " resource=" + getResourceName(imageView.getResources(), intValue);
        } else if (value instanceof Drawable) {
            Drawable drawable = (Drawable) value;
            detail = drawable.getClass().getName() + " level=" + drawable.getLevel()
                    + " intrinsic=" + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight();
        } else {
            detail = String.valueOf(value);
        }
        logToFramework("wifi_signal " + methodName + ": " + detail);
    }

    private static void logWifiStateUpdate(View root, List<?> args) {
        if (!DEBUG_WIFI_UPDATES) {
            return;
        }
        logToFramework("Flyme wifi state update on " + root.getClass().getName()
                + ": " + buildWifiStateDebugSignature(args));
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

    private static int getCurrentWifiLevel(ImageView imageView) {
        Integer rememberedLevel = WIFI_SIGNAL_LEVELS.get(imageView);
        if (rememberedLevel != null) {
            return rememberedLevel;
        }
        Integer resourceLevel = getWifiLevelFromImageViewResource(imageView);
        if (resourceLevel != null) {
            WIFI_SIGNAL_LEVELS.put(imageView, resourceLevel);
            return resourceLevel;
        }
        Integer drawableLevel = getWifiLevelFromDrawable(imageView.getDrawable());
        if (drawableLevel != null) {
            WIFI_SIGNAL_LEVELS.put(imageView, drawableLevel);
            return drawableLevel;
        }
        return IosWifiDrawable.LEVEL_ERROR;
    }

    private static Integer getWifiLevelFromImageViewResource(ImageView imageView) {
        Object resource = getField(imageView, "mResource");
        if (!(resource instanceof Integer)) {
            return null;
        }
        return getWifiSignalLevel(imageView.getResources(), (Integer) resource);
    }

    private static Integer getWifiLevelFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        int level = drawable.getLevel();
        if (level > 0) {
            return normalizeWifiImageLevel(level);
        }
        Drawable current = drawable.getCurrent();
        if (current != null && current != drawable && current.getLevel() > 0) {
            return normalizeWifiImageLevel(current.getLevel());
        }
        return null;
    }

    private static Integer extractWifiLevel(Resources resources, Object value, int depth) {
        if (value == null || depth > 3) {
            return null;
        }
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                Integer level = extractWifiLevel(resources, item, depth + 1);
                if (level != null) {
                    return level;
                }
            }
            return null;
        }
        Class<?> clazz = value.getClass();
        if (isSimpleValue(clazz)) {
            return null;
        }
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName().toLowerCase();
                Object fieldValue;
                try {
                    field.setAccessible(true);
                    fieldValue = field.get(value);
                } catch (Throwable ignored) {
                    continue;
                }
                Integer directLevel = wifiLevelFromNamedValue(resources, name, fieldValue);
                if (directLevel != null) {
                    return directLevel;
                }
                if (shouldInspectWifiField(name, fieldValue)) {
                    Integer nestedLevel = extractWifiLevel(resources, fieldValue, depth + 1);
                    if (nestedLevel != null) {
                        return nestedLevel;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Integer wifiLevelFromNamedValue(Resources resources, String name, Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        int number = ((Number) value).intValue();
        Integer resourceLevel = wifiLevelFromResourceField(resources, name, number);
        if (resourceLevel != null) {
            return resourceLevel;
        }
        if (name.contains("rssi")) {
            return wifiLevelFromRssi(number);
        }
        if (name.contains("level") || name.contains("signal") || name.contains("strength")) {
            if (number > IosWifiDrawable.MAX_LEVEL && number > 10) {
                return null;
            }
            return normalizeWifiImageLevel(number);
        }
        return null;
    }

    private static Integer wifiLevelFromResourceField(Resources resources, String name, int value) {
        if (resources == null || value == 0 || !isLikelyResourceIdField(name)) {
            return null;
        }
        return getWifiSignalLevel(resources, value);
    }

    private static boolean isLikelyResourceIdField(String name) {
        return name.contains("res") || name.contains("resource")
                || name.contains("drawable") || name.contains("icon");
    }

    private static boolean shouldInspectWifiField(String name, Object value) {
        if (value == null || isSimpleValue(value.getClass())) {
            return false;
        }
        return name.contains("wifi") || name.contains("state") || name.contains("model")
                || name.contains("icon") || name.contains("signal");
    }

    private static boolean isSimpleValue(Class<?> clazz) {
        return clazz.isPrimitive() || Number.class.isAssignableFrom(clazz)
                || CharSequence.class.isAssignableFrom(clazz) || Boolean.class == clazz
                || Character.class == clazz || clazz.isEnum();
    }

    private static int wifiLevelFromRssi(int rssi) {
        if (rssi >= -55) {
            return 4;
        }
        if (rssi >= -65) {
            return 3;
        }
        if (rssi >= -75) {
            return 2;
        }
        if (rssi >= -85) {
            return 1;
        }
        return 0;
    }

    private static void logWifiStateDebug(View root, List<?> args) {
        String signature = buildWifiStateDebugSignature(args);
        String lastSignature = WIFI_STATE_DEBUG_KEYS.get(root);
        if (signature.equals(lastSignature)) {
            return;
        }
        WIFI_STATE_DEBUG_KEYS.put(root, signature);
        logToFramework("Unable to extract Wi-Fi level from Flyme wifi state: " + signature);
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

    private static String buildWifiStateDebugSignature(List<?> args) {
        StringBuilder builder = new StringBuilder();
        if (args == null || args.isEmpty()) {
            return "no args";
        }
        for (Object arg : args) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            appendWifiStateDebug(builder, arg, 0);
        }
        return builder.toString();
    }

    private static void appendWifiStateDebug(StringBuilder builder, Object value, int depth) {
        if (value == null) {
            builder.append("null");
            return;
        }
        Class<?> clazz = value.getClass();
        builder.append(clazz.getName()).append("{");
        if (depth > 1 || isSimpleValue(clazz)) {
            builder.append(String.valueOf(value)).append("}");
            return;
        }
        int count = 0;
        while (clazz != null && clazz != Object.class && count < 12) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                String lowerName = name.toLowerCase();
                if (!lowerName.contains("wifi") && !lowerName.contains("level")
                        && !lowerName.contains("signal") && !lowerName.contains("rssi")
                        && !lowerName.contains("icon") && !lowerName.contains("state")) {
                    continue;
                }
                if (count > 0) {
                    builder.append(", ");
                }
                builder.append(name).append("=");
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(value);
                    if (fieldValue == null || isSimpleValue(fieldValue.getClass())) {
                        builder.append(String.valueOf(fieldValue));
                    } else {
                        builder.append(fieldValue.getClass().getName());
                    }
                } catch (Throwable ignored) {
                    builder.append("<err>");
                }
                count++;
                if (count >= 12) {
                    break;
                }
            }
            clazz = clazz.getSuperclass();
        }
        builder.append("}");
    }

    private static int normalizeWifiImageLevel(int imageLevel) {
        if (imageLevel <= 0) {
            return 0;
        }
        if (imageLevel <= IosWifiDrawable.MAX_LEVEL) {
            return imageLevel;
        }
        return IosWifiDrawable.MAX_LEVEL;
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
        String label = getNetworkTypeLabel(imageView.getResources(), resId);
        if (label == null) {
            NETWORK_TYPE_LABELS.remove(imageView);
            ensureNetworkTypePlaceholder(imageView);
            imageView.setVisibility(View.INVISIBLE);
            setParentVisibility(imageView, true);
            return;
        }
        NETWORK_TYPE_LABELS.put(imageView, label);
        imageView.setVisibility(View.VISIBLE);
        setParentVisibility(imageView, true);
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
        synchronized (CONFIG_REFRESH_LOCK) {
            if (CONFIG_REFRESH_REGISTERED) {
                return;
            }
            Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
            SETTINGS_OBSERVER = new ContentObserver(MAIN_HANDLER) {
                @Override
                public void onChange(boolean selfChange) {
                    refreshTrackedTextScaling();
                }
            };
            try {
                appContext.getContentResolver().registerContentObserver(SETTINGS_URI, true, SETTINGS_OBSERVER);
            } catch (Throwable ignored) {
            }
            USER_UNLOCKED_RECEIVER = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    refreshTrackedTextScaling();
                }
            };
            try {
                appContext.registerReceiver(USER_UNLOCKED_RECEIVER, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            } catch (Throwable ignored) {
            }
            CONFIG_REFRESH_REGISTERED = true;
            MAIN_HANDLER.postDelayed(FlymeStatusBarSizer::refreshTrackedTextScaling, 2000);
            MAIN_HANDLER.postDelayed(FlymeStatusBarSizer::refreshTrackedTextScaling, 10000);
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
        Resources resources = context.getResources();
        return resources.getIdentifier(name, "id", SYSTEM_UI);
    }

    private static int getSystemUiDimen(Context context, String name) {
        Resources resources = context.getResources();
        int id = resources.getIdentifier(name, "dimen", SYSTEM_UI);
        return id == 0 ? 0 : resources.getDimensionPixelSize(id);
    }

    private static String getSystemUiIdName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException ignored) {
            return "";
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static Object getField(Object target, String name) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setIntField(Object target, String name, int value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    field.setInt(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int getIntField(Object target, String name, int fallback) {
        Object value = getField(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static boolean getBooleanField(Object target, String name, boolean fallback) {
        Object value = getField(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static void setMeasuredDimension(View view, int width, int height) {
        try {
            Method method = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
            method.setAccessible(true);
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
        boolean iosBatteryStyle = SettingsStore.DEFAULT_IOS_BATTERY_STYLE;
        boolean iosSignalStyle = SettingsStore.DEFAULT_IOS_SIGNAL_STYLE;
        boolean iosNetworkTypeStyle = SettingsStore.DEFAULT_IOS_NETWORK_TYPE_STYLE;
        boolean iosWifiStyle = SettingsStore.DEFAULT_IOS_WIFI_STYLE;

        float scaled(int factorPercent) {
            return 1f + ((globalIconScale - 1f) * (factorPercent / 100f));
        }

        static Config load(Context context) {
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
            } else if (SettingsStore.KEY_IOS_BATTERY_STYLE.equals(key)) {
                iosBatteryStyle = "1".equals(value);
            } else if (SettingsStore.KEY_IOS_SIGNAL_STYLE.equals(key)) {
                iosSignalStyle = "1".equals(value);
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
