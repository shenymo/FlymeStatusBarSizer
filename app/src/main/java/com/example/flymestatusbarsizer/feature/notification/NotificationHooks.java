package com.example.flymestatusbarsizer.feature.notification;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class NotificationHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String PACKAGE_ANDROID = "android";
    private static final String FLYME_STATUS_BAR_ICON_UTILS =
            "com.flyme.systemui.statusbar.policy.FlymeStatusBarIconUtils";
    private static final String EXTRA_NOTIFICATION_APP_ICON_REPLACED =
            "flyme_status_bar_sizer_notification_app_icon_replaced";
    private static final int DEFAULT_NOTIFICATION_APP_ICON_SIZE_DP = 20;
    private static final int DEFAULT_NOTIFICATION_APP_ICON_INSET_DP = 1;
    private static final int MAX_RENDERED_NOTIFICATION_APP_ICON_CACHE_SIZE = 64;
    private static final int FLYME_LIGHT_NOTIFICATION_BLUR_MASK = 0xB2FFFFFF;
    private static final int FLYME_DARK_NOTIFICATION_BLUR_MASK = 0xB21A1A1A;
    private static final int NOTIFICATION_SYSTEM_BLUR_ONLY_COLOR = 0x1A000000;

    private static volatile Method flymeGetApplicationIconMethod;
    private static volatile Method flymeClearApplicationIconCacheMethod;
    private static volatile int LAST_NOTIFICATION_APP_ICON_VIEW_REFRESH_NIGHT = -1;

    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_RESTORE_GUARDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_APPLY_GUARDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NOTIFICATION_APP_ICON_ACTIVE_STATES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationAppIconTintState>
            NOTIFICATION_APP_ICON_TINT_STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, NotificationAppIconViewSignature>
            NOTIFICATION_APP_ICON_LAST_SIGNATURES = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> NOTIFICATION_APP_ICON_LAST_DRAWABLES =
            new WeakHashMap<>();
    private static final WeakHashMap<TextView, ColorStateList> NOTIFICATION_TEXT_COLOR_STATES =
            new WeakHashMap<>();
    private static final HashMap<String, Boolean> NOTIFICATION_APP_ICON_ELIGIBILITY_CACHE =
            new HashMap<>();
    private static final LinkedHashMap<NotificationAppIconViewSignature, Bitmap>
            RENDERED_NOTIFICATION_APP_ICON_CACHE =
            new LinkedHashMap<NotificationAppIconViewSignature, Bitmap>(
                    MAX_RENDERED_NOTIFICATION_APP_ICON_CACHE_SIZE,
                    0.75f,
                    true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<NotificationAppIconViewSignature, Bitmap> eldest) {
                    return size() > MAX_RENDERED_NOTIFICATION_APP_ICON_CACHE_SIZE;
                }
            };

    private NotificationHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        hookNotificationBlurMask(module, loader);
        hookNotificationSystemBlurOnly(module, loader);
        hookNotificationAppIcons(module, loader);
    }

    public static void clearNotificationAppIconTintIfNeeded(Object target) {
        if (!(target instanceof ImageView)) {
            return;
        }
        ImageView view = (ImageView) target;
        if (!isNotificationAppIconTintClearCandidate(view)) {
            return;
        }
        try {
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
                rememberNotificationAppIconTintState(view);
                clearDrawableColorState(view.getDrawable());
                if (view.getImageTintList() != null) {
                    view.setImageTintList((ColorStateList) null);
                }
                if (view.getColorFilter() != null) {
                    view.setColorFilter((ColorFilter) null);
                }
            } finally {
                synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS) {
                    NOTIFICATION_APP_ICON_TINT_CLEAR_GUARDS.remove(view);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyNotificationStatusBarIconDrawable(Object target) {
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
            NotificationAppIconBinding binding = resolveNotificationAppIconBinding(view);
            if (binding == null) {
                if (restoreNotificationStatusBarIconDrawableIfNeeded(view)) {
                    return;
                }
                clearNotificationAppIconReplacementState(
                        view,
                        resolveNotificationViewNotification(view));
                applyNotificationStatusBarIconViewStyle(view);
                return;
            }
            markNotificationAppIconReplacement(binding.notification, true);
            setNotificationAppIconActive(view, true);
            if (shouldReuseNotificationAppIconDrawable(view, binding.signature)) {
                clearNotificationAppIconTintIfNeeded(view);
                scheduleNotificationAppIconTintClear(view);
                applyNotificationStatusBarIconViewStyle(view);
                return;
            }
            Drawable drawable = resolveNotificationStatusBarIconDrawable(view, binding);
            if (drawable == null) {
                clearNotificationAppIconReplacementState(view, binding.notification);
                applyNotificationStatusBarIconViewStyle(view);
                return;
            }
            view.setImageDrawable(drawable);
            rememberNotificationAppIconRenderState(view, binding.signature, drawable);
            clearNotificationAppIconTintIfNeeded(view);
            scheduleNotificationAppIconTintClear(view);
            applyNotificationStatusBarIconViewStyle(view);
        } catch (Throwable ignored) {
        } finally {
            synchronized (NOTIFICATION_APP_ICON_APPLY_GUARDS) {
                NOTIFICATION_APP_ICON_APPLY_GUARDS.remove(view);
            }
        }
    }

    public static void refreshNotificationAppIconsForUiModeChange() {
        Context context = FlymeStatusBarSizer.getSystemUiContextCompat();
        if (context != null) {
            clearFlymeNotificationAppIconCache(context);
        }
        clearRenderedNotificationAppIconCache();
        ArrayList<View> views = FlymeStatusBarSizer.getTrackedStatusBarIconViewsSnapshot();
        for (View view : views) {
            if (!(view instanceof ImageView) || !isNotificationBackedStatusBarIconView(view)) {
                continue;
            }
            applyNotificationStatusBarIconDrawable(view);
        }
    }

    public static void clearRenderedNotificationAppIconCache() {
        synchronized (RENDERED_NOTIFICATION_APP_ICON_CACHE) {
            RENDERED_NOTIFICATION_APP_ICON_CACHE.clear();
        }
        synchronized (NOTIFICATION_APP_ICON_LAST_SIGNATURES) {
            NOTIFICATION_APP_ICON_LAST_SIGNATURES.clear();
            NOTIFICATION_APP_ICON_LAST_DRAWABLES.clear();
        }
    }

    private static void hookNotificationAppIcons(FlymeStatusBarSizer module, ClassLoader loader) {
        hookNotificationIconTint(module, loader);
        hookNotificationStatusBarIconUpdate(module, loader);
    }

    private static void hookNotificationBlurMask(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "setBlurBackground",
                    boolean.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object colorArg = chain.getArg(1);
                if (!(colorArg instanceof Integer)) {
                    return chain.proceed();
                }
                Object target = chain.getThisObject();
                FlymeStatusBarSizer.NotificationConfigSnapshot config =
                        target instanceof View
                                ? FlymeStatusBarSizer.loadNotificationConfig(
                                        ((View) target).getContext())
                                : FlymeStatusBarSizer.loadNotificationConfig(null);
                if (!config.enabled
                        || (!config.notificationSystemBlurOnlyEnabled
                        && !config.notificationBackgroundColorEnabled)) {
                    applyNotificationTextFollowStatusBar(target, false);
                    return chain.proceed();
                }
                if (!config.notificationSystemBlurOnlyEnabled
                        && !shouldReplaceNotificationBackgroundColor((Integer) colorArg)) {
                    return chain.proceed();
                }
                Object[] args;
                try {
                    applyNotificationTextFollowStatusBar(
                            target,
                            config.notificationSystemBlurOnlyEnabled
                                    && config.notificationTextFollowStatusBarEnabled);
                    args = chain.getArgs().toArray();
                    args[1] = config.notificationSystemBlurOnlyEnabled
                            ? NOTIFICATION_SYSTEM_BLUR_ONLY_COLOR
                            : config.notificationBackgroundColor;
                } catch (Throwable t) {
                    FlymeStatusBarSizer.logNotificationWarning(
                            "Failed to apply notification background color",
                            t);
                    return chain.proceed();
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logNotificationWarning(
                    "Failed to hook notification blur mask",
                    t);
        }
    }

    private static void hookNotificationSystemBlurOnly(
            FlymeStatusBarSizer module, ClassLoader loader) {
        hookMzBackgroundBlur(module, loader);
        hookWallpaperBackgroundBlur(module, loader);
        hookMediaSystemBlurOnly(module, loader);
    }

    private static void hookMzBackgroundBlur(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.flyme.systemui.utils.MzBlurUtils", false, loader);
            Class<?> function0 = Class.forName("kotlin.jvm.functions.Function0", false, loader);
            Method method = clazz.getDeclaredMethod(
                    "setBackgroundBlurDrawable",
                    View.class,
                    int.class,
                    float.class,
                    float.class,
                    int.class,
                    boolean.class,
                    int.class,
                    int.class,
                    function0,
                    java.util.function.Consumer.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (!shouldApplyNotificationSystemBlurOnly(chain.getArg(0))) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[4] = NOTIFICATION_SYSTEM_BLUR_ONLY_COLOR;
                args[6] = 255;
                return chain.proceed(args);
            });

            Method alphaMethod = clazz.getDeclaredMethod(
                    "setBackgroundBlurDrawableAlpha",
                    View.class,
                    int.class,
                    boolean.class,
                    function0,
                    java.util.function.Consumer.class);
            alphaMethod.setAccessible(true);
            module.intercept(alphaMethod, chain -> {
                if (!shouldApplyNotificationSystemBlurOnly(chain.getArg(0))) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[1] = 255;
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logNotificationWarning(
                    "Failed to hook notification live blur",
                    t);
        }
    }

    private static void hookWallpaperBackgroundBlur(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.wallpaper.WallpaperBlurDrawableManager",
                    false,
                    loader);
            hookWallpaperBlurColorMethod(
                    module,
                    clazz.getDeclaredMethod(
                            "setAllForegroundColor",
                            View.class,
                            int.class),
                    1);
            hookWallpaperBlurColorMethod(
                    module,
                    clazz.getDeclaredMethod(
                            "addBlurDrawableTo",
                            View.class,
                            int.class,
                            float.class),
                    1);
            hookWallpaperBlurColorMethod(
                    module,
                    clazz.getDeclaredMethod(
                            "addBlurDrawableTo",
                            View.class,
                            int.class,
                            float.class,
                            float.class,
                            float.class,
                            float.class),
                    1);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logNotificationWarning(
                    "Failed to hook notification wallpaper blur",
                    t);
        }
    }

    private static void hookWallpaperBlurColorMethod(
            FlymeStatusBarSizer module, Method method, int colorArgIndex) {
        method.setAccessible(true);
        module.intercept(method, chain -> {
            if (!shouldApplyNotificationSystemBlurOnly(chain.getArg(0))) {
                return chain.proceed();
            }
            Object[] args = chain.getArgs().toArray();
            args[colorArgIndex] = NOTIFICATION_SYSTEM_BLUR_ONLY_COLOR;
            return chain.proceed(args);
        });
    }

    private static void hookMediaSystemBlurOnly(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.flyme.systemui.media.controls.ui.view.MediaCarouseTransitionLayout",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("setMediaBackground", BitmapDrawable.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (!shouldApplyNotificationSystemBlurOnly(chain.getThisObject())) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[0] = null;
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logNotificationWarning(
                    "Failed to hook notification media blur",
                    t);
        }
    }

    private static boolean shouldApplyNotificationSystemBlurOnly(Object target) {
        if (!(target instanceof View)) {
            return false;
        }
        View view = (View) target;
        if (!isNotificationBlurTarget(view)) {
            return false;
        }
        FlymeStatusBarSizer.NotificationConfigSnapshot config =
                FlymeStatusBarSizer.loadNotificationConfig(view.getContext());
        return config.enabled && config.notificationSystemBlurOnlyEnabled;
    }

    private static boolean isNotificationBlurTarget(View view) {
        Class<?> current = view.getClass();
        while (current != null) {
            String name = current.getName();
            if ("com.android.systemui.statusbar.notification.row.NotificationBackgroundView"
                    .equals(name)
                    || "com.android.systemui.statusbar.notification.shelf.NotificationShelfBackgroundView"
                    .equals(name)
                    || "com.flyme.systemui.media.controls.ui.view.MediaCarouseTransitionLayout"
                    .equals(name)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean shouldReplaceNotificationBackgroundColor(int color) {
        if (color == FLYME_LIGHT_NOTIFICATION_BLUR_MASK
                || color == FLYME_DARK_NOTIFICATION_BLUR_MASK) {
            return true;
        }
        int alpha = Color.alpha(color);
        return alpha > 0
                && Color.red(color) >= 245
                && Color.green(color) >= 245
                && Color.blue(color) >= 245;
    }

    private static void applyNotificationTextFollowStatusBar(Object target, boolean enabled) {
        View root = findNotificationTextRoot(target);
        if (root == null) {
            return;
        }
        Integer textColor = enabled
                ? FlymeStatusBarSizer.resolveStatusBarIconTintColorCompat(root)
                : null;
        if (textColor == null) {
            updateNotificationTextColors(root, false, 0);
            return;
        }
        updateNotificationTextColors(root, true, textColor);
    }

    private static View findNotificationTextRoot(Object target) {
        if (!(target instanceof View)) {
            return null;
        }
        View view = (View) target;
        View fallback = null;
        ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof View && depth < 8) {
            View parentView = (View) parent;
            if (fallback == null) {
                fallback = parentView;
            }
            String className = parentView.getClass().getName();
            if (className.contains("ExpandableNotificationRow")
                    || className.contains("NotificationContentView")) {
                return parentView;
            }
            if (className.contains("NotificationStackScrollLayout")) {
                break;
            }
            parent = parentView.getParent();
            depth++;
        }
        return fallback;
    }

    private static void updateNotificationTextColors(View view, boolean enabled, int textColor) {
        if (view instanceof TextView) {
            if (enabled) {
                applyNotificationTextColor((TextView) view, textColor);
            } else {
                restoreNotificationTextColor((TextView) view);
            }
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            updateNotificationTextColors(group.getChildAt(i), enabled, textColor);
        }
    }

    private static void applyNotificationTextColor(TextView view, int textColor) {
        ColorStateList originalColors = NOTIFICATION_TEXT_COLOR_STATES.get(view);
        if (originalColors == null) {
            originalColors = view.getTextColors();
            NOTIFICATION_TEXT_COLOR_STATES.put(view, originalColors);
        }
        int originalColor = originalColors.getColorForState(
                view.getDrawableState(),
                originalColors.getDefaultColor());
        int targetColor = Color.argb(
                Color.alpha(originalColor),
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor));
        if (view.getCurrentTextColor() != targetColor) {
            view.setTextColor(targetColor);
        }
    }

    private static void restoreNotificationTextColor(TextView view) {
        ColorStateList originalColors = NOTIFICATION_TEXT_COLOR_STATES.remove(view);
        if (originalColors != null) {
            view.setTextColor(originalColors);
        }
    }

    private static void hookNotificationIconTint(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    false,
                    loader);
            Method updateIconColor = clazz.getDeclaredMethod("updateIconColor");
            updateIconColor.setAccessible(true);
            module.intercept(updateIconColor, chain -> {
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
            module.intercept(onDarkChanged, chain -> {
                Object result = chain.proceed();
                clearNotificationAppIconTintIfNeeded(chain.getThisObject());
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logNotificationWarning("Failed to hook notification icon tint", t);
        }
    }

    private static void hookNotificationStatusBarIconUpdate(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    false,
                    loader);
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if ("updateDrawable".equals(name)) {
                    method.setAccessible(true);
                    module.intercept(method, chain -> {
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
                    module.intercept(method, chain -> {
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
            FlymeStatusBarSizer.logNotificationWarning(
                    "Failed to hook notification status bar update",
                    t);
        }
    }

    private static void scheduleNotificationAppIconTintClear(ImageView view) {
        if (!isNotificationAppIconTintClearCandidate(view)) {
            return;
        }
        synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED) {
            if (Boolean.TRUE.equals(NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED.get(view))) {
                return;
            }
            NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED.put(view, Boolean.TRUE);
        }
        Runnable clearRunnable = () -> {
            try {
                clearNotificationAppIconTintIfNeeded(view);
            } finally {
                synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED) {
                    NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED.remove(view);
                }
            }
        };
        boolean posted;
        try {
            posted = view.post(clearRunnable);
        } catch (Throwable ignored) {
            posted = false;
        }
        if (posted) {
            return;
        }
        synchronized (NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED) {
            NOTIFICATION_APP_ICON_TINT_CLEAR_SCHEDULED.remove(view);
        }
        clearNotificationAppIconTintIfNeeded(view);
    }

    private static boolean shouldKeepNotificationAppIconOriginalColors(ImageView view) {
        if (view == null || !isNotificationBackedStatusBarIconView(view)) {
            return false;
        }
        FlymeStatusBarSizer.NotificationConfigSnapshot config =
                FlymeStatusBarSizer.loadNotificationConfig(view.getContext());
        if (!config.enabled || !config.notificationAppIconEnabled) {
            return false;
        }
        return isNotificationAppIconApplied(
                view,
                resolveNotificationViewNotification(view));
    }

    private static NotificationAppIconBinding resolveNotificationAppIconBinding(View view) {
        if (view == null || !isNotificationBackedStatusBarIconView(view)) {
            return null;
        }
        FlymeStatusBarSizer.NotificationConfigSnapshot config =
                FlymeStatusBarSizer.loadNotificationConfig(view.getContext());
        if (!config.enabled || !config.notificationAppIconEnabled) {
            return null;
        }
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(view, "getNotification");
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
        int renderSizePx = resolveNotificationAppIconRenderSize(view);
        if (renderSizePx <= 0) {
            return null;
        }
        int paddingPx = dp(view, config.notificationAppIconPaddingDp);
        int nightMode = readNightModeMask(view.getResources().getConfiguration());
        NotificationAppIconViewSignature signature = new NotificationAppIconViewSignature(
                packageName,
                userId,
                renderSizePx,
                paddingPx,
                nightMode);
        return new NotificationAppIconBinding(
                sbn.getNotification(),
                packageName,
                userId,
                signature);
    }

    private static Drawable resolveNotificationStatusBarIconDrawable(
            View view, NotificationAppIconBinding binding) {
        if (view == null || binding == null) {
            return null;
        }
        Drawable renderedDrawable = getRenderedNotificationAppIconDrawable(view, binding.signature);
        if (renderedDrawable != null) {
            return renderedDrawable;
        }
        Drawable drawable = getCachedNotificationApplicationIcon(
                view.getContext(),
                binding.packageName,
                binding.userId);
        if (drawable == null) {
            return null;
        }
        RenderedNotificationAppIcon renderedIcon = createNotificationStatusBarIconDrawable(
                view,
                drawable,
                binding.signature.renderSizePx);
        if (renderedIcon == null) {
            return null;
        }
        cacheRenderedNotificationAppIconBitmap(binding.signature, renderedIcon.bitmap);
        return renderedIcon.drawable;
    }

    private static String resolveNotificationSourcePackage(StatusBarNotification sbn) {
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(sbn, "getOrigPackageName");
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
            Object value = FlymeStatusBarSizer.invokeMethodCompat(
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
        Object userIdValue = FlymeStatusBarSizer.invokeNoArgCompat(sbn, "getUserId");
        if (userIdValue instanceof Integer && ((Integer) userIdValue).intValue() >= 0) {
            return ((Integer) userIdValue).intValue();
        }
        Object userHandle = sbn == null ? null : sbn.getUser();
        Object identifier = FlymeStatusBarSizer.invokeNoArgCompat(userHandle, "getIdentifier");
        int userId = identifier instanceof Integer ? ((Integer) identifier).intValue() : -1;
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
        FlymeStatusBarSizer.NotificationConfigSnapshot config =
                FlymeStatusBarSizer.loadNotificationConfig(view.getContext());
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

    private static RenderedNotificationAppIcon createNotificationStatusBarIconDrawable(
            View view,
            Drawable drawable,
            int sizePx) {
        Drawable working = cloneNotificationIconDrawable(drawable);
        if (view == null || working == null) {
            return new RenderedNotificationAppIcon(working, null);
        }
        if (sizePx <= 0) {
            return new RenderedNotificationAppIcon(working, null);
        }
        Bitmap bitmap = createFittedNotificationAppIconBitmap(view, working, sizePx);
        if (bitmap == null) {
            return new RenderedNotificationAppIcon(working, null);
        }
        BitmapDrawable bitmapDrawable = new BitmapDrawable(view.getResources(), bitmap);
        clearDrawableColorState(bitmapDrawable);
        return new RenderedNotificationAppIcon(bitmapDrawable, bitmap);
    }

    private static Drawable getRenderedNotificationAppIconDrawable(
            View view, NotificationAppIconViewSignature signature) {
        if (view == null || signature == null) {
            return null;
        }
        Bitmap bitmap;
        synchronized (RENDERED_NOTIFICATION_APP_ICON_CACHE) {
            bitmap = RENDERED_NOTIFICATION_APP_ICON_CACHE.get(signature);
            if (bitmap != null && bitmap.isRecycled()) {
                RENDERED_NOTIFICATION_APP_ICON_CACHE.remove(signature);
                bitmap = null;
            }
        }
        if (bitmap == null) {
            return null;
        }
        BitmapDrawable drawable = new BitmapDrawable(view.getResources(), bitmap);
        clearDrawableColorState(drawable);
        return drawable;
    }

    private static void cacheRenderedNotificationAppIconBitmap(
            NotificationAppIconViewSignature signature, Bitmap bitmap) {
        if (signature == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        synchronized (RENDERED_NOTIFICATION_APP_ICON_CACHE) {
            RENDERED_NOTIFICATION_APP_ICON_CACHE.put(signature, bitmap);
        }
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

    private static boolean restoreNotificationStatusBarIconDrawableIfNeeded(ImageView view) {
        if (view == null || !isNotificationBackedStatusBarIconView(view)) {
            return false;
        }
        Notification notification = resolveNotificationViewNotification(view);
        if (!isNotificationAppIconApplied(view, notification)) {
            return false;
        }
        synchronized (NOTIFICATION_APP_ICON_RESTORE_GUARDS) {
            if (Boolean.TRUE.equals(NOTIFICATION_APP_ICON_RESTORE_GUARDS.get(view))) {
                return false;
            }
            NOTIFICATION_APP_ICON_RESTORE_GUARDS.put(view, Boolean.TRUE);
        }
        try {
            clearNotificationAppIconReplacementState(view, notification);
            FlymeStatusBarSizer.invokeNoArgCompat(view, "updateDrawable");
            restoreNotificationAppIconTintState(view);
            return true;
        } catch (Throwable ignored) {
            setNotificationAppIconActive(view, true);
            markNotificationAppIconReplacement(notification, true);
            return false;
        } finally {
            synchronized (NOTIFICATION_APP_ICON_RESTORE_GUARDS) {
                NOTIFICATION_APP_ICON_RESTORE_GUARDS.remove(view);
            }
        }
    }

    private static void applyNotificationStatusBarIconViewStyle(ImageView view) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        FlymeStatusBarSizer.rememberOriginalLayoutCompat(view, lp);
        FlymeStatusBarSizer.rememberOriginalNotificationIconPaddingCompat(view);

        FlymeStatusBarSizer.NotificationConfigSnapshot config =
                FlymeStatusBarSizer.loadNotificationConfig(view.getContext());
        boolean customize = shouldCustomizeNotificationStatusBarIconView(view, config);
        boolean changed = false;

        int[] originalSize = FlymeStatusBarSizer.getOriginalSizeCompat(view);
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

        int[] originalPadding = FlymeStatusBarSizer.getOriginalPaddingCompat(view);
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
            int[] currentPadding = FlymeStatusBarSizer.readViewPaddingDirectCompat(view);
            if (currentPadding[0] != left || currentPadding[1] != top
                    || currentPadding[2] != right || currentPadding[3] != bottom) {
                view.setPadding(left, top, right, bottom);
                changed = true;
            }
        }

        if (changed) {
            view.setLayoutParams(lp);
            view.requestLayout();
            view.invalidate();
        }
    }

    private static boolean shouldCustomizeNotificationStatusBarIconView(
            ImageView view, FlymeStatusBarSizer.NotificationConfigSnapshot config) {
        if (view == null
                || config == null
                || !config.enabled
                || !config.notificationAppIconEnabled) {
            return false;
        }
        return isNotificationBackedStatusBarIconView(view);
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
            drawable.setTintList((ColorStateList) null);
        } catch (Throwable ignored) {
        }
        try {
            drawable.setColorFilter((ColorFilter) null);
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

    private static Notification resolveNotificationViewNotification(View view) {
        if (view == null) {
            return null;
        }
        Object value = FlymeStatusBarSizer.invokeNoArgCompat(view, "getNotification");
        if (!(value instanceof StatusBarNotification)) {
            return null;
        }
        return ((StatusBarNotification) value).getNotification();
    }

    private static boolean isNotificationAppIconApplied(View view, Notification notification) {
        return isNotificationAppIconActive(view)
                || wasNotificationAppIconReplaced(notification);
    }

    private static boolean isNotificationAppIconTintClearCandidate(ImageView view) {
        return view != null && isNotificationAppIconActive(view);
    }

    private static boolean isNotificationAppIconActive(View view) {
        synchronized (NOTIFICATION_APP_ICON_ACTIVE_STATES) {
            return Boolean.TRUE.equals(NOTIFICATION_APP_ICON_ACTIVE_STATES.get(view));
        }
    }

    private static void setNotificationAppIconActive(View view, boolean active) {
        if (view == null) {
            return;
        }
        synchronized (NOTIFICATION_APP_ICON_ACTIVE_STATES) {
            if (active) {
                NOTIFICATION_APP_ICON_ACTIVE_STATES.put(view, Boolean.TRUE);
            } else {
                NOTIFICATION_APP_ICON_ACTIVE_STATES.remove(view);
            }
        }
    }

    private static void clearNotificationAppIconReplacementState(
            View view, Notification notification) {
        setNotificationAppIconActive(view, false);
        clearNotificationAppIconRenderState(view);
        markNotificationAppIconReplacement(notification, false);
    }

    private static void rememberNotificationAppIconTintState(ImageView view) {
        if (view == null) {
            return;
        }
        ColorStateList tintList = view.getImageTintList();
        ColorFilter colorFilter = view.getColorFilter();
        synchronized (NOTIFICATION_APP_ICON_TINT_STATES) {
            if (tintList == null
                    && colorFilter == null
                    && NOTIFICATION_APP_ICON_TINT_STATES.containsKey(view)) {
                return;
            }
            NOTIFICATION_APP_ICON_TINT_STATES.put(
                    view,
                    new NotificationAppIconTintState(tintList, colorFilter));
        }
    }

    private static void restoreNotificationAppIconTintState(ImageView view) {
        if (view == null) {
            return;
        }
        NotificationAppIconTintState tintState;
        synchronized (NOTIFICATION_APP_ICON_TINT_STATES) {
            tintState = NOTIFICATION_APP_ICON_TINT_STATES.remove(view);
        }
        if (tintState == null
                || (tintState.tintList == null && tintState.colorFilter == null)) {
            FlymeStatusBarSizer.invokeNoArgCompat(view, "updateIconColor");
            return;
        }
        try {
            view.setImageTintList(tintState.tintList);
        } catch (Throwable ignored) {
        }
        try {
            view.setColorFilter(tintState.colorFilter);
        } catch (Throwable ignored) {
        }
    }

    private static boolean shouldReuseNotificationAppIconDrawable(
            ImageView view, NotificationAppIconViewSignature signature) {
        if (view == null || signature == null || !isNotificationAppIconActive(view)) {
            return false;
        }
        synchronized (NOTIFICATION_APP_ICON_LAST_SIGNATURES) {
            NotificationAppIconViewSignature lastSignature =
                    NOTIFICATION_APP_ICON_LAST_SIGNATURES.get(view);
            Drawable lastDrawable = NOTIFICATION_APP_ICON_LAST_DRAWABLES.get(view);
            if (!signature.equals(lastSignature) || lastDrawable == null) {
                return false;
            }
            try {
                clearDrawableColorState(lastDrawable);
                view.setImageDrawable(lastDrawable);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static void rememberNotificationAppIconRenderState(
            View view, NotificationAppIconViewSignature signature, Drawable drawable) {
        if (view == null || signature == null || drawable == null) {
            return;
        }
        synchronized (NOTIFICATION_APP_ICON_LAST_SIGNATURES) {
            NOTIFICATION_APP_ICON_LAST_SIGNATURES.put(view, signature);
            NOTIFICATION_APP_ICON_LAST_DRAWABLES.put(view, drawable);
        }
    }

    private static void clearNotificationAppIconRenderState(View view) {
        if (view == null) {
            return;
        }
        synchronized (NOTIFICATION_APP_ICON_LAST_SIGNATURES) {
            NOTIFICATION_APP_ICON_LAST_SIGNATURES.remove(view);
            NOTIFICATION_APP_ICON_LAST_DRAWABLES.remove(view);
        }
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
            clearRenderedNotificationAppIconCache();
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

    private static int readNightModeMask(Configuration configuration) {
        if (configuration == null) {
            return -1;
        }
        return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static boolean isNotificationBackedStatusBarIconView(View view) {
        if (view == null) {
            return false;
        }
        if (!"com.android.systemui.statusbar.StatusBarIconView".equals(view.getClass().getName())) {
            return false;
        }
        return FlymeStatusBarSizer.invokeNoArgCompat(view, "getNotification") != null;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class NotificationAppIconBinding {
        final Notification notification;
        final String packageName;
        final int userId;
        final NotificationAppIconViewSignature signature;

        NotificationAppIconBinding(
                Notification notification,
                String packageName,
                int userId,
                NotificationAppIconViewSignature signature) {
            this.notification = notification;
            this.packageName = packageName;
            this.userId = userId;
            this.signature = signature;
        }
    }

    private static final class NotificationAppIconViewSignature {
        final String packageName;
        final int userId;
        final int renderSizePx;
        final int paddingPx;
        final int nightMode;

        NotificationAppIconViewSignature(
                String packageName,
                int userId,
                int renderSizePx,
                int paddingPx,
                int nightMode) {
            this.packageName = packageName;
            this.userId = userId;
            this.renderSizePx = renderSizePx;
            this.paddingPx = paddingPx;
            this.nightMode = nightMode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NotificationAppIconViewSignature)) {
                return false;
            }
            NotificationAppIconViewSignature other = (NotificationAppIconViewSignature) obj;
            return userId == other.userId
                    && renderSizePx == other.renderSizePx
                    && paddingPx == other.paddingPx
                    && nightMode == other.nightMode
                    && TextUtils.equals(packageName, other.packageName);
        }

        @Override
        public int hashCode() {
            int result = packageName == null ? 0 : packageName.hashCode();
            result = 31 * result + userId;
            result = 31 * result + renderSizePx;
            result = 31 * result + paddingPx;
            result = 31 * result + nightMode;
            return result;
        }
    }

    private static final class RenderedNotificationAppIcon {
        final Drawable drawable;
        final Bitmap bitmap;

        RenderedNotificationAppIcon(Drawable drawable, Bitmap bitmap) {
            this.drawable = drawable;
            this.bitmap = bitmap;
        }
    }

    private static final class NotificationAppIconTintState {
        final ColorStateList tintList;
        final ColorFilter colorFilter;

        NotificationAppIconTintState(ColorStateList tintList, ColorFilter colorFilter) {
            this.tintList = tintList;
            this.colorFilter = colorFilter;
        }
    }
}
