package com.example.flymestatusbarsizer.feature.windowmode;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class WindowModeSmallWindowLaunchAnimator {
    private static final int WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int FLYME_WINDOW_MODE_MINI = 11;
    private static final int FLYME_WINDOW_MODE_FREEFORM = 1035;
    private static final long START_DELAY_MS = 48L;
    private static final long DURATION_MS = 260L;
    private static final int[] WINDOW_TYPES = new int[]{
            WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            WINDOW_TYPE_NOTIFICATION_SHADE
    };

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final DecelerateInterpolator INTERPOLATOR = new DecelerateInterpolator(1.18f);
    private static WindowModeSmallWindowLaunchAnimator active;
    private static Method windowManagerExtGetInstanceMethod;
    private static boolean windowManagerExtGetInstanceMethodResolved;
    private static Method getWindowModeBoundMethod;
    private static boolean getWindowModeBoundMethodResolved;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private final Context context;
    private final Rect startRect;
    private final Bitmap bitmap;
    private final FrameLayout overlay;
    private final FrameLayout card;
    private final ImageView imageView;
    private WindowManager windowManager;
    private ValueAnimator animator;

    private WindowModeSmallWindowLaunchAnimator(Context context, Rect startRect, Bitmap bitmap) {
        this.context = context;
        this.startRect = startRect;
        this.bitmap = bitmap;
        this.overlay = new FrameLayout(context);
        this.card = new FrameLayout(context);
        this.imageView = new ImageView(context);
        setupViews();
    }

    static void play(Context context, View sourceView, Object item) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            playOnMain(context, sourceView, item);
        } else {
            MAIN_HANDLER.post(() -> playOnMain(context, sourceView, item));
        }
    }

    static String resolvePackageName(Object item) {
        Object value = invokeNoArg(item, "f");
        return value instanceof String ? (String) value : null;
    }

    private static void playOnMain(Context context, View sourceView, Object item) {
        if (context == null || sourceView == null || item == null
                || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            return;
        }
        Rect startRect = resolveSourceRect(sourceView);
        Bitmap bitmap = createSnapshot(sourceView);
        if (startRect == null || bitmap == null) {
            return;
        }
        if (active != null) {
            active.cancel();
        }
        active = new WindowModeSmallWindowLaunchAnimator(context, startRect, bitmap);
        active.start();
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

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 255, 255, 255));
        background.setCornerRadius(dp(22));
        card.setBackground(background);
        card.setClipToOutline(true);
        card.setElevation(dp(10));
        card.setClipChildren(false);
        card.setClipToPadding(false);

        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(12);
        imageView.setPadding(padding, padding, padding, padding);
        card.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void start() {
        if (!attachOverlay()) {
            cleanup();
            return;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, startRect.width()),
                Math.max(1, startRect.height()));
        params.leftMargin = startRect.left;
        params.topMargin = startRect.top;
        overlay.addView(card, params);
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
        Rect finalTarget = target;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            FrameLayout.LayoutParams params =
                    card.getLayoutParams() instanceof FrameLayout.LayoutParams
                            ? (FrameLayout.LayoutParams) card.getLayoutParams()
                            : new FrameLayout.LayoutParams(startRect.width(), startRect.height());
            params.leftMargin = Math.round(lerp(startRect.left, finalTarget.left, progress));
            params.topMargin = Math.round(lerp(startRect.top, finalTarget.top, progress));
            params.width = Math.max(1, Math.round(lerp(startRect.width(), finalTarget.width(), progress)));
            params.height = Math.max(1, Math.round(lerp(startRect.height(), finalTarget.height(), progress)));
            card.setLayoutParams(params);
            card.invalidateOutline();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanup();
            }
        });
        animator.start();
    }

    private Rect resolveTargetRect() {
        try {
            Object windowManagerExt = resolveWindowManagerExtInstance();
            Method method = resolveGetWindowModeBoundMethod();
            if (windowManagerExt == null || method == null) {
                return null;
            }
            Rect rect = invokeWindowModeBound(windowManagerExt, method, FLYME_WINDOW_MODE_MINI);
            if (rect == null || rect.isEmpty()) {
                rect = invokeWindowModeBound(windowManagerExt, method, FLYME_WINDOW_MODE_FREEFORM);
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
        int targetWidth = Math.round(width * 0.68f);
        int targetHeight = Math.round(targetWidth * 1.35f);
        int maxHeight = Math.round(height * 0.68f);
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = Math.round(targetHeight / 1.35f);
        }
        int left = (width - targetWidth) / 2;
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
        int targetWidth = Math.round(clamp(rect.width(), dp(120), width - margin * 2));
        int targetHeight = Math.round(clamp(rect.height(), dp(160), height - margin * 2));
        int left = Math.round(clamp(rect.left, margin, Math.max(margin, width - targetWidth - margin)));
        int top = Math.round(clamp(rect.top, margin, Math.max(margin, height - targetHeight - margin)));
        return new Rect(left, top, left + targetWidth, top + targetHeight);
    }

    private void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        cleanup();
    }

    private void cleanup() {
        if (active == this) {
            active = null;
        }
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
