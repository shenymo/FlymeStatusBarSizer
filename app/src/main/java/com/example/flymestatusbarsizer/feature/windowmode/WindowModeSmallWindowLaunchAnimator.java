package com.example.flymestatusbarsizer.feature.windowmode;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class WindowModeSmallWindowLaunchAnimator {
    private static final int WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int FLYME_WINDOW_MODE_MINI = 11;
    private static final int FLYME_WINDOW_MODE_FREEFORM = 1035;
    private static final long START_DELAY_MS = 48L;
    private static final long DURATION_MS = 320L;
    private static final long EXIT_TIMEOUT_MS = 900L;
    private static final long FADE_OUT_MS = 140L;
    private static final float TARGET_WIDTH_RATIO = 0.68f;
    private static final float TARGET_ASPECT_RATIO = 1.35f;
    private static final float TARGET_MAX_HEIGHT_RATIO = 0.68f;
    private static final int[] WINDOW_TYPES = new int[]{
            WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            WINDOW_TYPE_NOTIFICATION_SHADE
    };

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final PathInterpolator MOVE_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator ICON_INTERPOLATOR =
            new PathInterpolator(0.18f, 0f, 0.12f, 1f);
    private static final PathInterpolator FADE_OUT_INTERPOLATOR =
            new PathInterpolator(0.4f, 0f, 0.82f, 0.72f);
    private static WindowModeSmallWindowLaunchAnimator active;
    private static Method windowManagerExtGetInstanceMethod;
    private static boolean windowManagerExtGetInstanceMethodResolved;
    private static Method getWindowModeBoundMethod;
    private static boolean getWindowModeBoundMethodResolved;
    private static Method getWindowModeBaseBoundMethod;
    private static boolean getWindowModeBaseBoundMethodResolved;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private final Context context;
    private final Rect startRect;
    private final Bitmap bitmap;
    private final Drawable iconDrawable;
    private final String targetPackageName;
    private final Runnable launchAction;
    private final FrameLayout overlay;
    private final FrameLayout card;
    private final ImageView imageView;
    private final GradientDrawable cardBackground;
    private final Runnable exitTimeoutRunnable = this::markLaunchReady;
    private WindowManager windowManager;
    private ValueAnimator animator;
    private Rect targetRect;
    private Object windowManagerExt;
    private Object windowModeListener;
    private boolean moveEnded;
    private boolean launchReady;
    private boolean cleaningUp;
    private boolean canceled;
    private boolean launchActionExecuted;

    private WindowModeSmallWindowLaunchAnimator(
            Context context,
            Rect startRect,
            Bitmap bitmap,
            Drawable iconDrawable,
            String targetPackageName,
            Runnable launchAction) {
        this.context = context;
        this.startRect = startRect;
        this.bitmap = bitmap;
        this.iconDrawable = iconDrawable;
        this.targetPackageName = targetPackageName;
        this.launchAction = launchAction;
        this.overlay = new FrameLayout(context);
        this.card = new FrameLayout(context);
        this.imageView = new ImageView(context);
        this.cardBackground = new GradientDrawable();
        setupViews();
    }

    static boolean play(Context context, View sourceView, Object item, Runnable launchAction) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return playOnMain(context, sourceView, item, launchAction);
        }
        MAIN_HANDLER.post(() -> {
            if (!playOnMain(context, sourceView, item, launchAction) && launchAction != null) {
                launchAction.run();
            }
        });
        return true;
    }

    static String resolvePackageName(Object item) {
        Object value = invokeNoArg(item, "f");
        return value instanceof String ? (String) value : null;
    }

    private static boolean playOnMain(
            Context context,
            View sourceView,
            Object item,
            Runnable launchAction) {
        if (context == null || sourceView == null || item == null
                || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            return false;
        }
        View iconView = resolveSourceIconView(context, sourceView);
        View startView = iconView == null ? sourceView : iconView;
        Rect startRect = resolveSourceRect(startView);
        Drawable iconDrawable = resolveIconDrawable(item);
        Bitmap bitmap = iconDrawable == null ? createSnapshot(startView) : null;
        String packageName = resolvePackageName(item);
        if (startRect == null || (iconDrawable == null && bitmap == null)) {
            return false;
        }
        if (active != null) {
            active.cancel();
        }
        active = new WindowModeSmallWindowLaunchAnimator(
                context,
                startRect,
                bitmap,
                iconDrawable,
                packageName,
                launchAction);
        active.start();
        return true;
    }

    private static View resolveSourceIconView(Context context, View sourceView) {
        try {
            int id = sourceView.getResources().getIdentifier(
                    "app_icon",
                    "id",
                    context == null ? "com.flyme.systemuitools" : context.getPackageName());
            View icon = id == 0 ? null : sourceView.findViewById(id);
            return icon != null && icon.getWidth() > 0 && icon.getHeight() > 0 ? icon : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable resolveIconDrawable(Object item) {
        Object icon = invokeNoArg(item, "j");
        if (icon instanceof Drawable) {
            return (Drawable) icon;
        }
        icon = invokeNoArg(item, "b");
        if (icon instanceof Drawable) {
            return (Drawable) icon;
        }
        Object info = invokeNoArg(item, "e");
        if (info instanceof LauncherActivityInfo) {
            try {
                return ((LauncherActivityInfo) info).getIcon(0);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Rect resolveSourceRect(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new Rect(location[0], location[1], location[0] + width, location[1] + height);
    }

    private static Bitmap createSnapshot(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(),
                    view.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to snapshot Flyme window mode launch icon",
                    t);
            return null;
        }
    }

    private void setupViews() {
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);

        cardBackground.setColor(Color.argb(238, 255, 255, 255));
        cardBackground.setCornerRadius(dp(22));
        card.setBackground(cardBackground);
        card.setClipToOutline(true);
        card.setElevation(dp(10));
        card.setClipChildren(false);
        card.setClipToPadding(false);

        if (iconDrawable != null) {
            imageView.setImageDrawable(cloneDrawable(iconDrawable));
        } else {
            imageView.setImageBitmap(bitmap);
        }
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(8);
        imageView.setPadding(padding, padding, padding, padding);
        card.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void start() {
        if (!attachOverlay()) {
            executeLaunchAction();
            cleanup();
            return;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, startRect.width()),
                Math.max(1, startRect.height()));
        params.leftMargin = startRect.left;
        params.topMargin = startRect.top;
        overlay.addView(card, params);
        registerWindowModeListener();
        executeLaunchAction();
        overlay.postDelayed(this::startAnimation, START_DELAY_MS);
    }

    private boolean attachOverlay() {
        Object service = context.getSystemService(Context.WINDOW_SERVICE);
        if (!(service instanceof WindowManager)) {
            return false;
        }
        windowManager = (WindowManager) service;
        Throwable lastError = null;
        for (int type : WINDOW_TYPES) {
            try {
                windowManager.addView(overlay, buildLayoutParams(type));
                return true;
            } catch (Throwable t) {
                lastError = t;
            }
        }
        FlymeStatusBarSizer.logWindowModeWarning(
                "Failed to attach Flyme window mode launch animation",
                lastError);
        windowManager = null;
        return false;
    }

    private WindowManager.LayoutParams buildLayoutParams(int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle("WindowModeLaunchAnimation");
        params.packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        applyTrustedOverlayFlags(params);
        return params;
    }

    private void startAnimation() {
        if (!overlay.isAttachedToWindow()) {
            executeLaunchAction();
            cleanup();
            return;
        }
        Rect target = resolveTargetRect();
        if (target == null || target.isEmpty()) {
            target = buildFallbackTargetRect();
        }
        if (target == null || target.isEmpty()) {
            cleanup();
            return;
        }
        final Rect fallbackTarget = target;
        targetRect = targetRect == null ? fallbackTarget : targetRect;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.addUpdateListener(animation -> {
            float rawProgress = (Float) animation.getAnimatedValue();
            float progress = MOVE_INTERPOLATOR.getInterpolation(rawProgress);
            Rect currentTarget = targetRect == null ? fallbackTarget : targetRect;
            FrameLayout.LayoutParams params =
                    card.getLayoutParams() instanceof FrameLayout.LayoutParams
                            ? (FrameLayout.LayoutParams) card.getLayoutParams()
                            : new FrameLayout.LayoutParams(startRect.width(), startRect.height());
            params.leftMargin = Math.round(lerp(startRect.left, currentTarget.left, progress));
            params.topMargin = Math.round(lerp(startRect.top, currentTarget.top, progress));
            params.width = Math.max(1, Math.round(lerp(startRect.width(), currentTarget.width(), progress)));
            params.height = Math.max(1, Math.round(lerp(startRect.height(), currentTarget.height(), progress)));
            card.setLayoutParams(params);
            cardBackground.setCornerRadius(lerp(dp(22), dp(30), progress));
            float iconScale = resolveIconScale(rawProgress);
            imageView.setScaleX(iconScale);
            imageView.setScaleY(iconScale);
            card.invalidateOutline();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || cleaningUp) {
                    return;
                }
                moveEnded = true;
                if (launchReady) {
                    fadeOutAndCleanup();
                } else {
                    MAIN_HANDLER.postDelayed(exitTimeoutRunnable, EXIT_TIMEOUT_MS);
                }
            }
        });
        animator.start();
    }

    private float resolveIconScale(float progress) {
        if (progress < 0.18f) {
            return lerp(1f, 0.94f, progress / 0.18f);
        }
        float settleProgress = ICON_INTERPOLATOR.getInterpolation((progress - 0.18f) / 0.82f);
        return lerp(0.94f, 1.06f, settleProgress);
    }

    private Drawable cloneDrawable(Drawable drawable) {
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            return state == null ? drawable : state.newDrawable(context.getResources()).mutate();
        } catch (Throwable ignored) {
            return drawable;
        }
    }

    private Rect resolveTargetRect() {
        try {
            Object windowManagerExt = resolveWindowManagerExtInstance();
            Method method = resolveGetWindowModeBoundMethod();
            Method baseMethod = resolveGetWindowModeBaseBoundMethod();
            if (windowManagerExt == null || method == null) {
                return null;
            }
            Rect rect = largerRect(
                    invokeWindowModeBound(windowManagerExt, method, FLYME_WINDOW_MODE_MINI),
                    invokeWindowModeBound(windowManagerExt, method, FLYME_WINDOW_MODE_FREEFORM));
            if (baseMethod != null) {
                rect = largerRect(
                        rect,
                        invokeWindowModeBaseBound(windowManagerExt, baseMethod, FLYME_WINDOW_MODE_MINI));
                rect = largerRect(
                        rect,
                        invokeWindowModeBaseBound(windowManagerExt, baseMethod, FLYME_WINDOW_MODE_FREEFORM));
            }
            return clampRect(rect);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Rect invokeWindowModeBound(Object windowManagerExt, Method method, int mode)
            throws Exception {
        Object result = method.invoke(windowManagerExt, resolveDisplayId(), 0, mode);
        return result instanceof Rect ? new Rect((Rect) result) : null;
    }

    private Rect invokeWindowModeBaseBound(Object windowManagerExt, Method method, int mode)
            throws Exception {
        Object result = method.invoke(windowManagerExt, resolveDisplayId(), 0, mode, true);
        return result instanceof Rect ? new Rect((Rect) result) : null;
    }

    private static Rect largerRect(Rect first, Rect second) {
        if (first == null || first.isEmpty()) {
            return second == null ? null : new Rect(second);
        }
        if (second == null || second.isEmpty()) {
            return new Rect(first);
        }
        long firstArea = (long) first.width() * first.height();
        long secondArea = (long) second.width() * second.height();
        return new Rect(secondArea > firstArea ? second : first);
    }

    private Rect buildFallbackTargetRect() {
        int width = overlay.getWidth() > 0
                ? overlay.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        int height = overlay.getHeight() > 0
                ? overlay.getHeight()
                : context.getResources().getDisplayMetrics().heightPixels;
        if (width <= 0 || height <= 0) {
            return null;
        }
        int[] targetSize = resolvePreferredTargetSize(width, height);
        int targetWidth = targetSize[0];
        int targetHeight = targetSize[1];
        int left = Math.round((width - targetWidth) / 2f);
        int top = Math.round((height - targetHeight) / 2f - dp(28));
        return clampRect(new Rect(left, top, left + targetWidth, top + targetHeight));
    }

    private Rect clampRect(Rect rect) {
        if (rect == null || rect.isEmpty()) {
            return null;
        }
        int width = overlay.getWidth() > 0
                ? overlay.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        int height = overlay.getHeight() > 0
                ? overlay.getHeight()
                : context.getResources().getDisplayMetrics().heightPixels;
        if (width <= 0 || height <= 0) {
            return null;
        }
        int margin = dp(12);
        int[] preferredSize = resolvePreferredTargetSize(width, height);
        int targetWidth = Math.round(clamp(
                Math.max(rect.width(), preferredSize[0]),
                dp(120),
                width - margin * 2));
        int targetHeight = Math.round(clamp(
                Math.max(rect.height(), preferredSize[1]),
                dp(160),
                height - margin * 2));
        int centerX = rect.centerX();
        int centerY = rect.centerY();
        int left = Math.round(clamp(
                centerX - (targetWidth / 2f),
                margin,
                Math.max(margin, width - targetWidth - margin)));
        int top = Math.round(clamp(
                centerY - (targetHeight / 2f),
                margin,
                Math.max(margin, height - targetHeight - margin)));
        return new Rect(left, top, left + targetWidth, top + targetHeight);
    }

    private Rect clampExactRect(Rect rect) {
        if (rect == null || rect.isEmpty()) {
            return null;
        }
        int width = overlay.getWidth() > 0
                ? overlay.getWidth()
                : context.getResources().getDisplayMetrics().widthPixels;
        int height = overlay.getHeight() > 0
                ? overlay.getHeight()
                : context.getResources().getDisplayMetrics().heightPixels;
        if (width <= 0 || height <= 0) {
            return null;
        }
        int margin = dp(12);
        int targetWidth = Math.round(clamp(rect.width(), dp(120), width - margin * 2));
        int targetHeight = Math.round(clamp(rect.height(), dp(160), height - margin * 2));
        int left = Math.round(clamp(rect.left, margin, Math.max(margin, width - targetWidth - margin)));
        int top = Math.round(clamp(rect.top, margin, Math.max(margin, height - targetHeight - margin)));
        return new Rect(left, top, left + targetWidth, top + targetHeight);
    }

    private void updateTargetFromBound(Rect bound) {
        Rect rect = clampExactRect(bound);
        if (rect != null) {
            targetRect = rect;
        }
    }

    private int[] resolvePreferredTargetSize(int width, int height) {
        int targetWidth = Math.round(width * TARGET_WIDTH_RATIO);
        int targetHeight = Math.round(targetWidth * TARGET_ASPECT_RATIO);
        int maxHeight = Math.round(height * TARGET_MAX_HEIGHT_RATIO);
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = Math.round(targetHeight / TARGET_ASPECT_RATIO);
        }
        return new int[]{targetWidth, targetHeight};
    }

    private void cancel() {
        canceled = true;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        cleanup();
    }

    private void cleanup() {
        if (cleaningUp) {
            return;
        }
        cleaningUp = true;
        if (active == this) {
            active = null;
        }
        MAIN_HANDLER.removeCallbacks(exitTimeoutRunnable);
        unregisterWindowModeListener();
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        try {
            if (windowManager != null && overlay.isAttachedToWindow()) {
                windowManager.removeViewImmediate(overlay);
            }
        } catch (Throwable ignored) {
        }
        windowManager = null;
    }

    private void executeLaunchAction() {
        if (launchActionExecuted || launchAction == null) {
            return;
        }
        launchActionExecuted = true;
        try {
            launchAction.run();
        } catch (Throwable t) {
            FlymeStatusBarSizer.logWindowModeWarning(
                    "Failed to launch Flyme window mode app after animation",
                    t);
        }
    }

    private void markLaunchReady() {
        if (cleaningUp) {
            return;
        }
        launchReady = true;
        MAIN_HANDLER.removeCallbacks(exitTimeoutRunnable);
        if (moveEnded) {
            fadeOutAndCleanup();
        }
    }

    private void fadeOutAndCleanup() {
        if (cleaningUp) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(card.getAlpha(), 0f);
        animator.setDuration(FADE_OUT_MS);
        animator.setInterpolator(FADE_OUT_INTERPOLATOR);
        animator.addUpdateListener(animation ->
                card.setAlpha((Float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanup();
            }
        });
        animator.start();
    }

    private Object resolveWindowManagerExtInstance() {
        try {
            Method method = resolveWindowManagerExtGetInstanceMethod();
            return method != null ? method.invoke(null, context) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveWindowManagerExtGetInstanceMethod() {
        if (!windowManagerExtGetInstanceMethodResolved) {
            try {
                Class<?> clazz = Class.forName("flyme.view.WindowManagerExt");
                windowManagerExtGetInstanceMethod = clazz.getMethod("getInstance", Context.class);
                windowManagerExtGetInstanceMethod.setAccessible(true);
            } catch (Throwable ignored) {
                windowManagerExtGetInstanceMethod = null;
            }
            windowManagerExtGetInstanceMethodResolved = true;
        }
        return windowManagerExtGetInstanceMethod;
    }

    private static Method resolveGetWindowModeBoundMethod() {
        if (!getWindowModeBoundMethodResolved) {
            try {
                Class<?> clazz = Class.forName("flyme.view.WindowManagerExt");
                getWindowModeBoundMethod = clazz.getMethod(
                        "getWindowModeBound",
                        int.class,
                        int.class,
                        int.class);
                getWindowModeBoundMethod.setAccessible(true);
            } catch (Throwable ignored) {
                getWindowModeBoundMethod = null;
            }
            getWindowModeBoundMethodResolved = true;
        }
        return getWindowModeBoundMethod;
    }

    private static Method resolveGetWindowModeBaseBoundMethod() {
        if (!getWindowModeBaseBoundMethodResolved) {
            try {
                Class<?> clazz = Class.forName("flyme.view.WindowManagerExt");
                getWindowModeBaseBoundMethod = clazz.getMethod(
                        "getWindowModeBound",
                        int.class,
                        int.class,
                        int.class,
                        boolean.class);
                getWindowModeBaseBoundMethod.setAccessible(true);
            } catch (Throwable ignored) {
                getWindowModeBaseBoundMethod = null;
            }
            getWindowModeBaseBoundMethodResolved = true;
        }
        return getWindowModeBaseBoundMethod;
    }

    private void registerWindowModeListener() {
        try {
            windowManagerExt = resolveWindowManagerExtInstance();
            if (windowManagerExt == null) {
                return;
            }
            Class<?> listenerClass = Class.forName("flyme.view.WindowManagerExt$WindowModeListener");
            windowModeListener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        if ("toString".equals(method.getName())) {
                            return "WindowModeLaunchAnimationListener";
                        }
                        if (isLaunchReadyCallback(method.getName(), args)) {
                            MAIN_HANDLER.post(this::markLaunchReady);
                        }
                        return null;
                    });
            Method registerMethod = windowManagerExt.getClass().getMethod(
                    "registerWindowModeListener",
                    int.class,
                    listenerClass);
            registerMethod.invoke(windowManagerExt, FLYME_WINDOW_MODE_MINI, windowModeListener);
            registerMethod.invoke(windowManagerExt, FLYME_WINDOW_MODE_FREEFORM, windowModeListener);
        } catch (Throwable ignored) {
            unregisterWindowModeListener();
            windowModeListener = null;
            windowManagerExt = null;
        }
    }

    private boolean isLaunchReadyCallback(String methodName, Object[] args) {
        if ("onBoundChanged".equals(methodName)) {
            if (args != null && args.length > 4 && args[4] instanceof Rect) {
                MAIN_HANDLER.post(() -> updateTargetFromBound((Rect) args[4]));
            }
            return true;
        }
        if ("onWindowModeBoundChanged".equals(methodName)) {
            if (args != null && args.length > 1 && args[1] instanceof Rect) {
                MAIN_HANDLER.post(() -> updateTargetFromBound((Rect) args[1]));
            }
            return true;
        }
        if ("onWindowModeFlingToTarget".equals(methodName)) {
            if (args != null && args.length > 2 && args[2] instanceof Rect) {
                MAIN_HANDLER.post(() -> updateTargetFromBound((Rect) args[2]));
            }
            return true;
        }
        if ("onWindowModeChangeAnimationFinished".equals(methodName)
                || "onShellTransitionFinished".equals(methodName)) {
            return true;
        }
        if (targetPackageName == null || targetPackageName.trim().isEmpty()) {
            return false;
        }
        if ("onPackageNameChanged".equals(methodName)) {
            return args != null
                    && args.length > 1
                    && targetPackageName.equals(args[1]);
        }
        if ("onTopActivityChanged".equals(methodName)) {
            return args != null
                    && args.length > 3
                    && targetPackageName.equals(args[3]);
        }
        return false;
    }

    private void unregisterWindowModeListener() {
        if (windowManagerExt == null || windowModeListener == null) {
            return;
        }
        try {
            Class<?> listenerClass = Class.forName("flyme.view.WindowManagerExt$WindowModeListener");
            Method unregisterMethod = windowManagerExt.getClass().getMethod(
                    "unregisterWindowModeListener",
                    int.class,
                    listenerClass);
            unregisterMethod.invoke(windowManagerExt, FLYME_WINDOW_MODE_MINI, windowModeListener);
            unregisterMethod.invoke(windowManagerExt, FLYME_WINDOW_MODE_FREEFORM, windowModeListener);
        } catch (Throwable ignored) {
        }
        windowModeListener = null;
        windowManagerExt = null;
    }

    private int resolveDisplayId() {
        try {
            if (overlay.getDisplay() != null) {
                return overlay.getDisplay().getDisplayId();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void applyTrustedOverlayFlags(WindowManager.LayoutParams params) {
        Method method = resolveTrustedOverlayMethod();
        if (method != null) {
            try {
                method.invoke(params);
            } catch (Throwable ignored) {
            }
        }
        Field field = resolveTrustedOverlayPrivateFlagsField();
        if (field == null) {
            return;
        }
        try {
            field.setInt(params, field.getInt(params) | PRIVATE_FLAG_TRUSTED_OVERLAY);
        } catch (Throwable ignored) {
        }
    }

    private static Method resolveTrustedOverlayMethod() {
        if (!trustedOverlayMethodResolved) {
            try {
                trustedOverlayMethod = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay");
            } catch (Throwable ignored) {
                trustedOverlayMethod = null;
            }
            trustedOverlayMethodResolved = true;
        }
        return trustedOverlayMethod;
    }

    private static Field resolveTrustedOverlayPrivateFlagsField() {
        if (!trustedOverlayPrivateFlagsFieldResolved) {
            try {
                trustedOverlayPrivateFlagsField =
                        WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
                trustedOverlayPrivateFlagsField.setAccessible(true);
            } catch (Throwable ignored) {
                trustedOverlayPrivateFlagsField = null;
            }
            trustedOverlayPrivateFlagsFieldResolved = true;
        }
        return trustedOverlayPrivateFlagsField;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
