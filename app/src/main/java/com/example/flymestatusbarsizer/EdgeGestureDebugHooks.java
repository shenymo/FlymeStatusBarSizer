package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class EdgeGestureDebugHooks {
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int[] WINDOW_TYPE_CANDIDATES = new int[]{
            INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE
    };
    private static final String EDGE_BACK_HANDLER_CLASS =
            "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler";
    private static final String WINDOW_TITLE = "EdgeGestureDebug";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static DebugOverlayView overlayView;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private EdgeGestureDebugHooks() {
    }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(EDGE_BACK_HANDLER_CLASS, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    scheduleRefresh(chain.getThisObject());
                    return result;
                });
            }
            hookRefreshMethod(module, clazz, "updateCurrentUserResources");
            hookRefreshMethod(module, clazz, "updateDisplaySize");
            hookRefreshMethod(module, clazz, "updateIsEnabled");
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook edge gesture debug overlay", t);
        }
    }

    private static void hookRefreshMethod(FlymeStatusBarSizer module, Class<?> clazz, String name) {
        try {
            Method method = clazz.getDeclaredMethod(name);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                scheduleRefresh(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private static void scheduleRefresh(Object handler) {
        if (handler == null) {
            return;
        }
        MAIN_HANDLER.post(() -> refresh(handler));
    }

    private static void refresh(Object handler) {
        Context context = ReflectUtils.getField(handler, "mContext") instanceof Context
                ? (Context) ReflectUtils.getField(handler, "mContext")
                : null;
        if (context == null) {
            return;
        }
        int width = 0;
        int height = 0;
        Object displaySizeObject = ReflectUtils.getField(handler, "mDisplaySize");
        if (displaySizeObject instanceof Point) {
            Point displaySize = (Point) displaySizeObject;
            width = displaySize.x;
            height = displaySize.y;
        }
        if (width <= 0 || height <= 0) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        int leftInset = ReflectUtils.getIntField(handler, "mLeftInset", 0);
        int rightInset = ReflectUtils.getIntField(handler, "mRightInset", 0);
        int leftWidth = Math.max(1,
                (ReflectUtils.getIntField(handler, "mEdgeWidthLeft", 0) + leftInset) * 2);
        int rightWidth = Math.max(1,
                (ReflectUtils.getIntField(handler, "mEdgeWidthRight", 0) + rightInset) * 2);
        int bottomCutout = Math.max(0, Math.round(getFloatField(handler, "mBottomGestureHeight", 0f)));
        ensureOverlay(context);
        if (overlayView != null) {
            overlayView.setRegions(width, height, leftWidth, rightWidth, bottomCutout);
        }
    }

    private static float getFloatField(Object target, String name, float fallback) {
        Object value = ReflectUtils.getField(target, name);
        return value instanceof Float ? (Float) value : fallback;
    }

    private static void ensureOverlay(Context context) {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            return;
        }
        removeOverlayFromParent();
        Object wm = context.getSystemService(Context.WINDOW_SERVICE);
        if (!(wm instanceof WindowManager)) {
            return;
        }
        if (overlayView == null) {
            overlayView = new DebugOverlayView(context);
        }
        WindowManager target = (WindowManager) wm;
        for (int type : WINDOW_TYPE_CANDIDATES) {
            try {
                target.addView(overlayView, buildLayoutParams(context, type));
                return;
            } catch (Throwable ignored) {
                removeOverlayFromParent();
            }
        }
    }

    private static WindowManager.LayoutParams buildLayoutParams(Context context, int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
        params.setTitle(WINDOW_TITLE);
        params.packageName = context.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        applyTrustedOverlayFlags(params);
        return params;
    }

    private static void removeOverlayFromParent() {
        if (overlayView == null) {
            return;
        }
        ViewParent parent = overlayView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlayView);
        }
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
            field.setInt(params, field.getInt(params) | INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY);
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

    private static final class DebugOverlayView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int displayWidth;
        private int displayHeight;
        private int leftWidth;
        private int rightWidth;
        private int bottomCutout;

        DebugOverlayView(Context context) {
            super(context);
            fillPaint.setColor(0x55FF0000);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setColor(Color.RED);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f);
            setWillNotDraw(false);
        }

        void setRegions(int width, int height, int left, int right, int bottom) {
            displayWidth = Math.max(0, width);
            displayHeight = Math.max(0, height);
            leftWidth = Math.max(0, left);
            rightWidth = Math.max(0, right);
            bottomCutout = Math.max(0, bottom);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = displayWidth > 0 ? displayWidth : getWidth();
            int height = displayHeight > 0 ? displayHeight : getHeight();
            int bottom = Math.max(0, height - bottomCutout);
            if (width <= 0 || bottom <= 0) {
                return;
            }
            int left = Math.min(leftWidth, width);
            int right = Math.max(0, width - Math.min(rightWidth, width));
            canvas.drawRect(0, 0, left, bottom, fillPaint);
            canvas.drawRect(0, 0, left, bottom, strokePaint);
            canvas.drawRect(right, 0, width, bottom, fillPaint);
            canvas.drawRect(right, 0, width, bottom, strokePaint);
        }
    }
}
