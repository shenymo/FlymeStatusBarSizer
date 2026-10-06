package com.example.flymestatusbarsizer.feature.statusbar;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

public final class StatusBarTintHooks {
    private static final String TAG = "StatusBarTint";
    private static final String LAUNCHER = "com.meizu.flyme.launcher";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ACTION = "com.fiyme.statusbarsizer.STATUS_BAR_SCENE";
    private static final String REQUEST = ACTION + ".REQUEST";
    private static final String PHONE = "com.android.systemui.statusbar.phone.";
    private static final String HEADER = "com.flyme.systemui.statusbar.phone.StatusBarHeaderView";
    private static final String QS = "com.flyme.systemui.controlcenter.qs.QSStatusBarController";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Integer> PANEL_COLORS = new WeakHashMap<>();
    // Also tracks native dark receivers; headers have no overridden tint and keep a null value.
    private static final Map<Object, Integer> PANEL_TINTS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Object>> LOCK_VIEWS = new WeakHashMap<>();
    private static final Runnable UPDATE = StatusBarTintHooks::updateColors;
    private static final Runnable SEND = () -> sendLauncherScene(false);
    private static WeakReference<Activity> launcherActivity = new WeakReference<>(null);
    private static Object dispatcher, panel, center, keyguard, statusState;
    private static Context systemContext;
    private static int launcherScene = -1, sentScene = -2, lastScene = -2;
    private static boolean launcherReceiverRegistered, launcherVisible;
    private static boolean shadeExpanded, centerExpanded;
    private static boolean replaying, forceRefresh;
    private static boolean tintActive, restorePending;
    // Keep native inputs separate; dispatcher fields always hold the effective scene color.
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private static final ArrayList<Rect> EMPTY_AREAS = new ArrayList<>();
    private static ArrayList<Rect> nativeAreas = new ArrayList<>();
    private static float nativeIntensity;
    private static int nativeTint = Color.WHITE, nativeContrast = Color.BLACK;
    private static ArrayList<Rect> appliedAreas, panelNativeAreas;
    private static int appliedTint, appliedContrast, panelNativeTint;
    private static float appliedIntensity;
    /**
     * Latest status bar icon colour, published for other features that draw their own monochrome
     * icons. Starts white, which is what the shade uses on a dark background.
     */
    private static volatile int publishedIconTint = Color.WHITE;
    /**
     * Status bar darkness observed from the dispatcher callback, tracked whether or not the scene tint
     * feature is enabled. The dispatcher's own field is not reliably refreshed while the feature is off.
     */
    private static volatile float liveDarkIntensity;
    private static volatile boolean liveDarkIntensityObserved;

    private StatusBarTintHooks() {}

    public static void installSystemUi(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(PHONE + "DarkIconDispatcherImpl", false, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (chain.getArgs().size() > 1 && Integer.valueOf(0).equals(chain.getArg(0))
                            && chain.getArg(1) instanceof Context) {
                        dispatcher = chain.getThisObject();
                        tintActive = false;
                        restorePending = false;
                        syncEnabled();
                        registerSceneReceiver((Context) chain.getArg(1));
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Dispatcher unavailable", error);
        }
        hook(module, loader, PHONE + "DarkIconDispatcherImpl", "applyDarkIntensity", chain -> {
            nativeIntensity = (Float) chain.getArg(0);
            // Track the real status bar darkness even when the scene tint feature is off, so that
            // monochrome artwork (the ANIP notification icons) can be painted in the matching colour.
            liveDarkIntensity = nativeIntensity;
            liveDarkIntensityObserved = true;
            if (chain.getThisObject() != dispatcher || !syncEnabled()) {
                // The feature is off, but notification icons still have to follow the status bar.
                if (chain.getThisObject() == dispatcher) {
                    notifyIconTintChanged(resolveNativeIconTint());
                }
                return chain.proceed();
            }
            nativeTint = (Integer) ARGB.evaluate(nativeIntensity,
                    field(dispatcher, "mLightModeIconColorSingleTone"),
                    field(dispatcher, "mDarkModeIconColorSingleTone"));
            nativeContrast = (Integer) ARGB.evaluate(nativeIntensity,
                    field(dispatcher, "mLightModeContrastColor"),
                    field(dispatcher, "mDarkModeContrastColor"));
            applyDispatcherTint(false);
            return null;
        });
        hook(module, loader, PHONE + "DarkIconDispatcherImpl", "setIconsDarkArea", chain -> {
            ArrayList<?> incoming = (ArrayList<?>) chain.getArg(0);
            if (chain.getThisObject() != dispatcher || !syncEnabled()) return chain.proceed();
            ArrayList<?> areas = incoming;
            if (areas == null ? !nativeAreas.isEmpty() : !nativeAreas.equals(areas)) {
                nativeAreas = copyAreas(areas);
            }
            applyDispatcherTint(false);
            return null;
        });
        // Compiled animation callers must enter the hooks instead of an inlined color calculation.
        deoptimize(module, loader, PHONE + "LightBarTransitionsController",
                "dispatchDark", "setIconTintInternal", "lambda$animateIconTint$0");
        deoptimize(module, loader, PHONE + "LightBarControllerImpl", "updateStatus");
        for (String type : new String[]{HEADER, QS}) {
            hook(module, loader, type, "onDarkChanged", chain -> {
                // Panel caches must retain native colors, not the desktop override.
                if (!tintActive || !enabled()) return chain.proceed();
                PANEL_TINTS.putIfAbsent(chain.getThisObject(), null);
                return dispatcher == null ? chain.proceed()
                        : chain.proceed(new Object[]{nativeAreas, nativeIntensity, nativeTint});
            });
            hook(module, loader, type, QS.equals(type) ? "onViewDetached" : "onDetachedFromWindow", chain -> {
                PANEL_TINTS.remove(chain.getThisObject());
                PANEL_COLORS.remove(chain.getThisObject());
                return chain.proceed();
            });
            // The expanded header also colors editing controls and dates; only tint the status bar.
            if (!QS.equals(type)) continue;
            hook(module, loader, type, "onViewAttached", chain -> {
                PANEL_TINTS.remove(chain.getThisObject());
                return chain.proceed();
            });
            hook(module, loader, type, "updateViewColor", chain -> {
                Object target = chain.getThisObject();
                if (!replaying) PANEL_COLORS.put(target, (Integer) chain.getArg(0));
                if (!enabled()) return chain.proceed();
                int currentScene = scene();
                // Each panel owns its tint; a system-default panel never inherits home/lock overrides.
                int panelScene = bool(target, "mIsBelongToClassicPanel")
                        ? (currentScene == 3 ? 3 : 2) : 3;
                int selected = mode(currentScene == panelScene ? panelScene : -1);
                int tint = selected == 0 ? (Integer) chain.getArg(0) : color(selected);
                Integer previous = PANEL_TINTS.get(target);
                if (previous != null && previous == tint) return null;
                Object result = selected == 0 ? chain.proceed()
                        : chain.proceed(new Object[]{color(selected)});
                // The first callback during attachment arrives before the icon manager exists.
                if (field(target, "mIconManager") != null) PANEL_TINTS.put(target, tint);
                return result;
            });
        }
        hook(module, loader, PHONE + "KeyguardStatusBarView", "updateIconsAndTextColors", chain -> {
            View view = (View) chain.getThisObject();
            Object manager = chain.getArg(0);
            LOCK_VIEWS.put(view, new WeakReference<>(manager));
            if (!enabled()) return chain.proceed();
            int currentScene = scene();
            int selected = mode(currentScene == 2 || currentScene == 3 ? currentScene :
                    currentScene == 4 ? 4 : -1);
            if (selected == 0) return chain.proceed();
            try {
                applyLockColor(view, manager, selected);
                return null;
            } catch (ReflectiveOperationException error) {
                Log.w(TAG, "Lockscreen tint unavailable", error);
                return chain.proceed();
            }
        });
        watch(module, loader, "com.android.systemui.shade.NotificationPanelViewController",
                new String[]{"updateExpansionAndVisibility"}, 0);
        watch(module, loader, "com.flyme.systemui.controlcenter.phone.CenterController",
                new String[]{"updatePanelExpanded"}, 1);
        watch(module, loader, "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl",
                new String[]{"notifyKeyguardState", "notifyPrimaryBouncerShowing",
                        "notifyKeyguardGoingAway", "notifyKeyguardFadingAway",
                        "notifyKeyguardDoneFading"}, 2);
        watch(module, loader, "com.android.systemui.statusbar.StatusBarStateControllerImpl",
                new String[]{"setState", "setIsDozing", "onShadeOrQsExpanded"}, 3);
        hook(module, loader, "com.android.systemui.shade.QuickSettingsControllerImpl", "setExpanded", chain -> {
            Object result = chain.proceed();
            sceneChanged();
            return result;
        });
    }

    private static void watch(FlymeStatusBarSizer module, ClassLoader loader, String type,
            String[] names, int kind) {
        for (String name : names) hook(module, loader, type, name, chain -> {
            Object result = chain.proceed();
            switch (kind) {
                case 0: {
                    Object target = chain.getThisObject();
                    boolean expanded = positive(target, "mExpandedFraction");
                    if (panel == target && shadeExpanded == expanded) return result;
                    panel = target;
                    shadeExpanded = expanded;
                    break;
                }
                case 1: {
                    Object target = chain.getThisObject();
                    boolean expanded = positive(target, "mExpandedFraction");
                    if (center == target && centerExpanded == expanded) return result;
                    center = target;
                    centerExpanded = expanded;
                    break;
                }
                case 2: keyguard = chain.getThisObject(); break;
                case 3: statusState = chain.getThisObject(); break;
                default: break;
            }
            sceneChanged();
            return result;
        });
    }

    private static int scene() {
        int state = ReflectUtils.getIntField(statusState, "mState", -1);
        // mShowing becomes false before the lockscreen finishes fading/sliding away.
        boolean locked = bool(keyguard, "mShowing") || state == 1
                || bool(keyguard, "mKeyguardGoingAway") || bool(keyguard, "mKeyguardFadingAway");
        boolean blocked = bool(statusState, "mIsDozing") || bool(panel, "mDozing")
                || bool(panel, "mIsPrimaryBouncerShowing") || bool(keyguard, "mPrimaryBouncerShowing")
                || (locked && bool(keyguard, "mOccluded"));
        boolean control = positive(center, "mExpandedFraction");
        boolean shade = positive(panel, "mExpandedFraction") && (!locked || state == 2);
        boolean qs = shade && Boolean.TRUE.equals(
                ReflectUtils.invokeNoArg(field(panel, "mQsController"), "getExpanded"));
        return selectScene(blocked, control || qs, shade, locked,
                !locked ? launcherScene : -1);
    }

    // Overlay scenes take precedence; unknown/occluded states always use native colors.
    static int selectScene(boolean blocked, boolean control, boolean shade, boolean locked, int launcher) {
        if (blocked) return -1;
        if (control) return 3;
        if (shade) return 2;
        if (locked) return 4;
        return launcher == 0 || launcher == 1 ? launcher : -1;
    }

    private static int mode(int scene) {
        if (scene < 0) return 0;
        ModuleConfig config = ModuleConfig.load(null);
        return config.enabled && config.statusBarTintEnabled ? config.statusBarTintModes[scene] : 0;
    }

    private static boolean enabled() {
        ModuleConfig config = ModuleConfig.load(null);
        return config.enabled && config.statusBarTintEnabled;
    }

    private static boolean syncEnabled() {
        boolean active = enabled();
        if (dispatcher == null || active == tintActive) return active;
        if (active) {
            // Native code may have changed these while the feature was off.
            nativeAreas = copyAreas((ArrayList<?>) field(dispatcher, "mTintAreas"));
            nativeIntensity = (Float) field(dispatcher, "mDarkIntensity");
            nativeTint = (Integer) field(dispatcher, "mIconTint");
            nativeContrast = (Integer) field(dispatcher, "mContrastTint");
        } else {
            // Restore before allowing the native callbacks to run again.
            ReflectUtils.setField(dispatcher, "mTintAreas", copyAreas(nativeAreas));
            ReflectUtils.setFloatField(dispatcher, "mDarkIntensity", nativeIntensity);
            ReflectUtils.setIntField(dispatcher, "mIconTint", nativeTint);
            ReflectUtils.setIntField(dispatcher, "mContrastTint", nativeContrast);
        }
        tintActive = active;
        restorePending = !active;
        appliedAreas = null;
        panelNativeAreas = null;
        PANEL_TINTS.clear();
        lastScene = -2;
        return active;
    }

    private static int color(int mode) { return mode == 1 ? Color.BLACK : Color.WHITE; }
    private static Object field(Object object, String name) { return ReflectUtils.getField(object, name); }
    private static boolean bool(Object object, String name) {
        return ReflectUtils.getBooleanField(object, name, false);
    }
    private static boolean positive(Object object, String name) {
        Object value = field(object, name);
        return value instanceof Number && ((Number) value).floatValue() > 0f;
    }

    private static void sceneChanged() {
        if (!enabled()) return;
        int current = scene();
        if (current == lastScene) return;
        lastScene = current;
        MAIN.removeCallbacks(UPDATE);
        MAIN.post(UPDATE);
    }

    public static void refresh() {
        if (!enabled() && !tintActive && !restorePending) return;
        forceRefresh = true;
        MAIN.removeCallbacks(UPDATE);
        MAIN.post(UPDATE);
    }

    private static void updateColors() {
        boolean force = forceRefresh;
        forceRefresh = false;
        boolean active = syncEnabled();
        if (!active && !restorePending) return;
        force |= restorePending;
        restorePending = false;
        // Rebuilt views need a forced refresh; opening a panel does not invalidate their colors.
        if (force) PANEL_TINTS.replaceAll((target, tint) -> null);
        if (active) applyDispatcherTint(force);
        else ReflectUtils.invokeNoArg(dispatcher, "applyIconTint");
        int currentScene = active ? scene() : -1;
        replaying = true;
        try {
            for (Map.Entry<Object, Integer> entry : new ArrayList<>(PANEL_COLORS.entrySet())) {
                ReflectUtils.invokeMethod(entry.getKey(), "updateViewColor",
                        new Class<?>[]{int.class}, entry.getValue());
            }
            for (Map.Entry<View, WeakReference<Object>> entry : new ArrayList<>(LOCK_VIEWS.entrySet())) {
                if (!force && currentScene != 4 && !entry.getKey().isShown()) continue;
                Object manager = entry.getValue().get();
                if (manager != null) ReflectUtils.invokeMethod(entry.getKey(), "updateIconsAndTextColors",
                        new Class<?>[]{manager.getClass()}, manager);
            }
        } finally {
            replaying = false;
        }
    }

    private static ArrayList<Rect> copyAreas(ArrayList<?> areas) {
        ArrayList<Rect> copy = new ArrayList<>();
        if (areas != null) {
            for (Object area : areas) copy.add(new Rect((Rect) area));
        }
        return copy;
    }

    private static void applyDispatcherTint(boolean force) {
        if (dispatcher == null) return;
        int currentScene = scene();
        int selected = mode(currentScene);
        ArrayList<Rect> areas = selected == 0 ? nativeAreas : EMPTY_AREAS;
        float intensity = selected == 0 ? nativeIntensity : selected == 1 ? 1f : 0f;
        int tint = selected == 0 ? nativeTint : color(selected);
        int contrast = selected == 0 ? nativeContrast : color(selected == 1 ? 2 : 1);
        boolean nativeChanged = panelNativeTint != nativeTint || panelNativeAreas != nativeAreas;
        panelNativeTint = nativeTint;
        panelNativeAreas = nativeAreas;
        if (!force && appliedAreas == areas
                && appliedIntensity == intensity && appliedTint == tint && appliedContrast == contrast) {
            // The fixed output did not change. Only panels need the updated native colors.
            if (nativeChanged) {
                for (Object target : new ArrayList<>(PANEL_TINTS.keySet())) {
                    ReflectUtils.invokeMethod(target, "onDarkChanged",
                            new Class<?>[]{ArrayList.class, float.class, int.class},
                            nativeAreas, nativeIntensity, nativeTint);
                }
            }
            return;
        }
        appliedAreas = areas;
        appliedIntensity = intensity;
        appliedTint = tint;
        appliedContrast = contrast;
        // Publish one consistent state to callbacks, newly added icons and the notification flow.
        ReflectUtils.setField(dispatcher, "mTintAreas", areas);
        ReflectUtils.setFloatField(dispatcher, "mDarkIntensity", intensity);
        ReflectUtils.setIntField(dispatcher, "mIconTint", tint);
        ReflectUtils.setIntField(dispatcher, "mContrastTint", contrast);
        ReflectUtils.invokeNoArg(dispatcher, "applyIconTint");
        // Publish before applying so icons rebuilt as a result already see the new colour.
        publishedIconTint = tint;
        notifyIconTintChanged(tint);
    }

    /**
     * Rebuilds the monochrome notification icons when the status bar icon colour changes.
     *
     * <p>Those icons are painted in this colour, so a stale one is invisible on the matching
     * background. Announced from both the scene tint path and the plain intensity callback, because
     * the two are independent: the notification icons must adapt with the tint feature switched off.
     */
    private static void notifyIconTintChanged(int tint) {
        if (lastAnnouncedTint == tint) {
            return;
        }
        lastAnnouncedTint = tint;
        try {
            com.example.flymestatusbarsizer.feature.notification.NotificationHooks
                    .refreshTrackedNotificationIconsForTintChange(tint);
        } catch (Throwable ignored) {
        }
    }

    private static volatile int lastAnnouncedTint = Integer.MIN_VALUE;

    /**
     * The icon colour the dispatcher would use for the darkness currently in effect.
     *
     * <p>Read directly rather than from {@code mIconTint}, which the scene tint feature only keeps
     * current while it is enabled.
     */
    private static int resolveNativeIconTint() {
        Object current = dispatcher;
        if (current == null) {
            return publishedIconTint;
        }
        float intensity = liveDarkIntensityObserved
                ? liveDarkIntensity
                : 0f;
        if (!liveDarkIntensityObserved) {
            Object raw = ReflectUtils.getField(current, "mDarkIntensity");
            if (raw instanceof Number) {
                intensity = ((Number) raw).floatValue();
            }
        }
        String fieldName = intensity > 0.5f
                ? "mDarkModeIconColorSingleTone"
                : "mLightModeIconColorSingleTone";
        Object colorValue = ReflectUtils.getField(current, fieldName);
        return colorValue instanceof Integer ? ((Integer) colorValue).intValue() : publishedIconTint;
    }

    private static void applyLockColor(View view, Object manager, int mode) throws ReflectiveOperationException {
        int tint = color(mode);
        int contrast = color(mode == 1 ? 2 : 1);
        float intensity = mode == 1 ? 1f : 0f;
        ArrayList<?> areas = (ArrayList<?>) field(view, "mEmptyTintRect");
        Class<?> changeType = Class.forName(PHONE + "SysuiDarkIconDispatcher$DarkChange", false,
                view.getClass().getClassLoader());
        Object change = changeType.getConstructor(Collection.class, float.class, int.class)
                .newInstance(areas, intensity, tint);
        for (String name : new String[]{"mCarrierLabel", "mClock", "mDate"}) {
            Object text = field(view, name);
            if (text instanceof TextView) ((TextView) text).setTextColor(tint);
        }
        View user = (View) field(view, "mUserSwitcherContainer");
        if (user != null) {
            int id = view.getResources().getIdentifier("current_user_name", "id", SYSTEM_UI);
            View text = id == 0 ? null : user.findViewById(id);
            if (text instanceof TextView) ((TextView) text).setTextColor(tint);
        }
        ReflectUtils.invokeMethod(manager, "setTint", new Class<?>[]{int.class, int.class}, tint, contrast);
        ReflectUtils.invokeMethod(field(view, "mDarkChange"), "setValue", new Class<?>[]{Object.class}, change);
        for (String name : new String[]{"battery", "clock", "connection_rate", "battery_percent"}) {
            int id = view.getResources().getIdentifier(name, "id", SYSTEM_UI);
            if (id != 0) ReflectUtils.invokeMethod(view, "applyDarkness",
                    new Class<?>[]{int.class, ArrayList.class, float.class, int.class}, id, areas, intensity, tint);
        }
    }

    private static void registerSceneReceiver(Context context) {
        if (systemContext != null || Build.VERSION.SDK_INT < 34) return;
        systemContext = context;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!LAUNCHER.equals(getSentFromPackage())) return;
                launcherScene = intent.getIntExtra("scene", -1);
                sceneChanged();
            }
        }, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
        send(context, new Intent(REQUEST).setPackage(LAUNCHER));
    }

    public static void installLauncher(FlymeStatusBarSizer module, ClassLoader loader) {
        // Observe the complete lifecycle dispatch, not Activity's overridden/empty base callbacks.
        // Pause precedes the app launch animation; stop marks the launcher becoming invisible.
        for (String method : new String[]{"callActivityOnResume", "callActivityOnStop"}) {
            hook(module, loader, "android.app.Instrumentation", method, chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getArg(0);
                if (!LAUNCHER.equals(activity.getPackageName())
                        || ReflectUtils.invokeNoArg(activity, "getStateManager") == null) return result;
                if ("callActivityOnResume".equals(method)) {
                    launcherActivity = new WeakReference<>(activity);
                    launcherVisible = true;
                    registerLauncherReceiver(activity.getApplicationContext());
                } else if (activity == launcherActivity.get()) {
                    launcherVisible = false;
                } else {
                    return result;
                }
                MAIN.removeCallbacks(SEND);
                sendLauncherScene(false);
                return result;
            });
        }
        for (String method : new String[]{"onStateTransitionStart", "onStateTransitionEnd"}) {
            hook(module, loader, "com.android.launcher3.statemanager.StateManager", method, chain -> {
                Object result = chain.proceed();
                MAIN.removeCallbacks(SEND);
                MAIN.post(SEND);
                return result;
            });
        }
    }

    private static void registerLauncherReceiver(Context context) {
        if (launcherReceiverRegistered || Build.VERSION.SDK_INT < 34) return;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (SYSTEM_UI.equals(getSentFromPackage())) sendLauncherScene(true);
            }
        }, new IntentFilter(REQUEST), Context.RECEIVER_EXPORTED);
        launcherReceiverRegistered = true;
    }

    private static void sendLauncherScene(boolean force) {
        Activity activity = launcherActivity.get();
        if (activity == null || Build.VERSION.SDK_INT < 34) return;
        int selected = -1;
        // Keep the scene while the launcher remains visible behind an opening app.
        if (launcherVisible && !activity.isFinishing() && !activity.isDestroyed()) {
            Object manager = ReflectUtils.invokeNoArg(activity, "getStateManager");
            Object state = ReflectUtils.invokeNoArg(manager, "getState");
            Object normal = ReflectUtils.getStaticField(activity.getClassLoader(),
                    "com.android.launcher3.LauncherState", "NORMAL");
            if (state != null && state == normal) selected = 0;
            else if (bool(state, "isRecentsViewVisible")) selected = 1;
        }
        if (!force && selected == sentScene) return;
        sentScene = selected;
        send(activity, new Intent(ACTION).setPackage(SYSTEM_UI).putExtra("scene", selected));
    }

    private static void send(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) context.sendBroadcast(intent, null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
    }

    private static void deoptimize(FlymeStatusBarSizer module, ClassLoader loader, String type,
            String... names) {
        try {
            for (Method method : Class.forName(type, false, loader).getDeclaredMethods()) {
                for (String name : names) {
                    if (name.equals(method.getName())) module.deoptimize(method);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot deoptimize tint callers: " + type, error);
        }
    }

    private static void hook(FlymeStatusBarSizer module, ClassLoader loader, String type,
            String name, XposedInterface.Hooker hooker) {
        try {
            Class<?> clazz = Class.forName(type, false, loader);
            boolean found = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!name.equals(method.getName())) continue;
                method.setAccessible(true);
                module.intercept(method, hooker);
                found = true;
            }
            if (!found) Log.w(TAG, "Missing hook: " + type + "." + name);
        } catch (Throwable error) {
            Log.w(TAG, "Cannot hook " + type + "." + name, error);
        }
    }
}
