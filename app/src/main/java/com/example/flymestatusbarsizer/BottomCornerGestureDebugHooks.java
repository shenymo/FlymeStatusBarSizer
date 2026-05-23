package com.example.flymestatusbarsizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
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

final class BottomCornerGestureDebugHooks {
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int[] WINDOW_TYPE_CANDIDATES = new int[]{
            INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    };
    private static final String ROTATION_TOUCH_HELPER_CLASS =
            "com.android.quickstep.RotationTouchHelper";
    private static final String WINDOW_TITLE = "BottomCornerGestureDebug";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static DebugOverlayView overlayView;
    private static WindowManager overlayWindowManager;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private BottomCornerGestureDebugHooks() {
    }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(ROTATION_TOUCH_HELPER_CLASS, false, loader);
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    scheduleRefresh(chain.getThisObject());
                    return result;
                });
            }
            hookRefreshMethod(module, clazz, "updateGestureTouchRegions");
            hookRefreshMethod(module, clazz, "onDisplayInfoChanged");
            hookRefreshMethod(module, clazz, "setGesturalHeight");
            hookRefreshMethod(module, clazz, "touchInAssistantRegion");
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to hook bottom corner gesture debug overlay", t);
        }
    }

    private static void hookRefreshMethod(FlymeStatusBarSizer module, Class<?> clazz, String name) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!name.equals(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    scheduleRefresh(chain.getThisObject());
                    return result;
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private static void scheduleRefresh(Object helper) {
        if (helper == null) {
            return;
        }
        MAIN_HANDLER.post(() -> refresh(helper));
        MAIN_HANDLER.postDelayed(() -> refresh(helper), 300L);
    }

    private static void refresh(Object helper) {
        Object contextObject = ReflectUtils.getField(helper, "mContext");
        if (!(contextObject instanceof Context)) {
            return;
        }
        Object transformer = ReflectUtils.getField(helper, "mOrientationTouchTransformer");
        if (transformer == null) {
            return;
        }
        RectF left = copyRect(ReflectUtils.getField(transformer, "mAssistantLeftRegion"));
        RectF right = copyRect(ReflectUtils.getField(transformer, "mAssistantRightRegion"));
        if (left == null || right == null) {
            return;
        }
        Context context = (Context) contextObject;
        Point displaySize = resolveDisplaySize(context, transformer, left, right);
        ensureOverlay(context);
        if (overlayView != null) {
            overlayView.setRegions(displaySize.x, displaySize.y, left, right);
        }
    }

    private static RectF copyRect(Object value) {
        return value instanceof RectF ? new RectF((RectF) value) : null;
    }

    private static Point resolveDisplaySize(Context context, Object transformer, RectF left, RectF right) {
        Object cachedDisplayInfo = ReflectUtils.getField(transformer, "mCachedDisplayInfo");
        Object size = ReflectUtils.getField(cachedDisplayInfo, "size");
        if (size instanceof Point) {
            Point point = (Point) size;
            if (point.x > 0 && point.y > 0) {
                return new Point(point.x, point.y);
            }
        }
        int width = Math.round(Math.max(left.right, right.right));
        int height = Math.round(Math.max(left.bottom, right.bottom));
        if ((width <= 0 || height <= 0) && context.getDisplay() != null) {
            Point point = new Point();
            context.getDisplay().getRealSize(point);
            width = point.x;
            height = point.y;
        }
        if (width <= 0 || height <= 0) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        return new Point(Math.max(1, width), Math.max(1, height));
    }

    private static void ensureOverlay(Context context) {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            return;
        }
        removeOverlay();
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        Object wm = appContext.getSystemService(Context.WINDOW_SERVICE);
        if (!(wm instanceof WindowManager)) {
            return;
        }
        if (overlayView == null) {
            overlayView = new DebugOverlayView(appContext);
        }
        WindowManager target = (WindowManager) wm;
        Throwable lastError = null;
        for (int type : WINDOW_TYPE_CANDIDATES) {
            try {
                target.addView(overlayView, buildLayoutParams(appContext, type));
                overlayWindowManager = target;
                return;
            } catch (Throwable t) {
                lastError = t;
                try {
                    target.removeViewImmediate(overlayView);
                } catch (Throwable ignored) {
                }
                removeOverlayFromParent();
            }
        }
        FlymeStatusBarSizer.logMBackWarning("Failed to attach bottom corner gesture debug overlay", lastError);
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

    private static void removeOverlay() {
        WindowManager target = overlayWindowManager;
        overlayWindowManager = null;
        if (target != null && overlayView != null) {
            try {
                target.removeViewImmediate(overlayView);
            } catch (Throwable ignored) {
            }
        }
        removeOverlayFromParent();
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
        private final RectF leftRegion = new RectF();
        private final RectF rightRegion = new RectF();
        private int displayWidth;
        private int displayHeight;

        DebugOverlayView(Context context) {
            super(context);
            fillPaint.setColor(0x66FF0000);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setColor(Color.RED);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3f);
            setWillNotDraw(false);
        }

        void setRegions(int width, int height, RectF left, RectF right) {
            displayWidth = Math.max(1, width);
            displayHeight = Math.max(1, height);
            leftRegion.set(left);
            rightRegion.set(right);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (leftRegion.isEmpty() && rightRegion.isEmpty()) {
                return;
            }
            float scaleX = getWidth() > 0 ? getWidth() / (float) displayWidth : 1f;
            float scaleY = getHeight() > 0 ? getHeight() / (float) displayHeight : 1f;
            canvas.save();
            canvas.scale(scaleX, scaleY);
            drawRegion(canvas, leftRegion);
            drawRegion(canvas, rightRegion);
            canvas.restore();
        }

        private void drawRegion(Canvas canvas, RectF rect) {
            if (rect.isEmpty()) {
                return;
            }
            canvas.drawRect(rect, fillPaint);
            canvas.drawRect(rect, strokePaint);
        }
    }
}
