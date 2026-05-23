package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class MBackStarOverlayController {
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE = 2040;
    private static final int INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL = 2041;
    private static final int INTERNAL_WINDOW_PRIVATE_FLAG_TRUSTED_OVERLAY = 16777216;
    private static final int[] INTERNAL_WINDOW_TYPE_CANDIDATES = new int[]{
            INTERNAL_WINDOW_TYPE_STATUS_BAR_ADDITIONAL,
            INTERNAL_WINDOW_TYPE_STATUS_BAR_SUB_PANEL,
            INTERNAL_WINDOW_TYPE_NOTIFICATION_SHADE
    };
    private static final String WINDOW_TITLE = "MBackStarApps";
    private static final float GOLDEN_ANGLE = 2.3999631f;
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityManager activityManager;
    private final MBackStarAppProvider appProvider;
    private final FrameLayout overlayView;
    private final FrameLayout iconsLayer;
    private final ArrayList<IconHolder> iconHolders = new ArrayList<>();

    private WindowManager windowManager;
    private MBackStarApp[] apps = MBackStarApp.EMPTY_ARRAY;
    private IconHolder hoveredHolder;
    private boolean showing;
    private float lastRawX = Float.NaN;
    private float lastRawY = Float.NaN;

    MBackStarOverlayController(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.activityManager = this.context != null
                ? (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE)
                : null;
        this.appProvider = new MBackStarAppProvider(this.context);
        this.iconsLayer = new FrameLayout(this.context);
        this.overlayView = buildOverlayView(this.context, iconsLayer);
    }

    void show(View anchor, MotionEvent startEvent) {
        if (anchor == null || !anchor.isAttachedToWindow()) {
            return;
        }
        rememberMotion(startEvent);
        if (!attachOverlay(anchor.getContext())) {
            return;
        }
        showing = true;
        hoveredHolder = null;
        apps = MBackStarApp.EMPTY_ARRAY;
        iconsLayer.removeAllViews();
        iconHolders.clear();
        overlayView.setAlpha(0f);
        overlayView.animate()
                .alpha(1f)
                .setDuration(120L)
                .start();
        appProvider.requestApps(handler, resolvedApps -> {
            if (!showing) {
                return;
            }
            apps = resolvedApps != null ? resolvedApps : MBackStarApp.EMPTY_ARRAY;
            renderApps();
        });
    }

    boolean handleMBackMotionEvent(MotionEvent event) {
        if (!showing || event == null) {
            return false;
        }
        rememberMotion(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
            updateHover(event.getRawX(), event.getRawY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            updateHover(event.getRawX(), event.getRawY());
            IconHolder target = hoveredHolder;
            if (target != null && target.app != null) {
                launchApp(target.app);
            } else {
                dismiss();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            dismiss();
            return true;
        }
        return true;
    }

    private boolean attachOverlay(Context sourceContext) {
        if (showing && overlayView.isAttachedToWindow()) {
            return true;
        }
        removeOverlayFromParent();
        Object windowManagerObject = sourceContext.getSystemService(Context.WINDOW_SERVICE);
        if (!(windowManagerObject instanceof WindowManager)) {
            return false;
        }
        WindowManager targetWindowManager = (WindowManager) windowManagerObject;
        Throwable lastError = null;
        for (int windowType : INTERNAL_WINDOW_TYPE_CANDIDATES) {
            WindowManager.LayoutParams params = buildLayoutParams(sourceContext, windowType);
            try {
                targetWindowManager.addView(overlayView, params);
                windowManager = targetWindowManager;
                return true;
            } catch (Throwable t) {
                lastError = t;
                try {
                    targetWindowManager.removeViewImmediate(overlayView);
                } catch (Throwable ignored) {
                }
            }
        }
        FlymeStatusBarSizer.logMBackWarning("Failed to attach mBack star overlay", lastError);
        windowManager = null;
        return false;
    }

    private WindowManager.LayoutParams buildLayoutParams(Context sourceContext, int windowType) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle(WINDOW_TITLE);
        params.packageName = sourceContext.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        applyTrustedOverlayFlags(params);
        return params;
    }

    private void dismiss() {
        if (!showing && !overlayView.isAttachedToWindow()) {
            return;
        }
        showing = false;
        hoveredHolder = null;
        apps = MBackStarApp.EMPTY_ARRAY;
        iconHolders.clear();
        iconsLayer.removeAllViews();
        overlayView.animate().cancel();
        WindowManager targetWindowManager = windowManager;
        windowManager = null;
        if (targetWindowManager != null) {
            try {
                targetWindowManager.removeViewImmediate(overlayView);
            } catch (Throwable ignored) {
            }
        }
        removeOverlayFromParent();
    }

    private void removeOverlayFromParent() {
        ViewParent parent = overlayView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlayView);
        }
    }

    private void renderApps() {
        if (!showing || !overlayView.isAttachedToWindow()) {
            return;
        }
        int width = overlayView.getWidth();
        int height = overlayView.getHeight();
        if (width <= 0 || height <= 0) {
            overlayView.post(this::renderApps);
            return;
        }
        iconsLayer.removeAllViews();
        iconHolders.clear();
        hoveredHolder = null;
        MBackStarApp[] safeApps = apps != null ? apps : MBackStarApp.EMPTY_ARRAY;
        int count = safeApps.length;
        int hitSize = dp(78);
        int iconSize = dp(56);
        for (int i = 0; i < count; i++) {
            MBackStarApp app = safeApps[i];
            if (app == null || app.taskId < 0) {
                continue;
            }
            FrameLayout root = buildIconRoot(app, hitSize, iconSize);
            float[] point = resolveStarPoint(i, count, width, height, hitSize);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(hitSize, hitSize);
            params.leftMargin = Math.round(point[0]);
            params.topMargin = Math.round(point[1]);
            iconsLayer.addView(root, params);
            IconHolder holder = new IconHolder(app, root);
            iconHolders.add(holder);
            root.setAlpha(0f);
            root.setScaleX(0.72f);
            root.setScaleY(0.72f);
            root.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(Math.min(180L, i * 12L))
                    .setDuration(160L)
                    .start();
        }
        updateHoverFromLastMotion();
    }

    private FrameLayout buildIconRoot(MBackStarApp app, int hitSize, int iconSize) {
        FrameLayout root = new FrameLayout(context);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setContentDescription(app.label);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(42, 255, 255, 255));
        background.setStroke(Math.max(1, dp(1)), Color.argb(60, 255, 255, 255));
        root.setBackground(background);
        ImageView iconView = new ImageView(context);
        iconView.setImageDrawable(app.icon);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = dp(4);
        iconView.setPadding(padding, padding, padding, padding);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        root.addView(iconView, iconParams);
        root.setLayoutParams(new FrameLayout.LayoutParams(hitSize, hitSize));
        return root;
    }

    private float[] resolveStarPoint(int index, int count, int width, int height, int hitSize) {
        int margin = dp(20);
        float minX = margin;
        float maxX = Math.max(minX, width - hitSize - margin);
        float minY = margin + dp(18);
        float maxY = Math.max(minY, height - hitSize - margin - dp(28));
        float centerX = (width - hitSize) / 2f;
        float centerY = (height * 0.42f) - (hitSize / 2f);
        if (count <= 1) {
            return new float[]{clamp(centerX, minX, maxX), clamp(centerY, minY, maxY)};
        }
        float radiusX = Math.max(1f, (maxX - minX) / 2f);
        float radiusY = Math.max(1f, (maxY - minY) / 2f);
        float fraction = (float) Math.sqrt((index + 1f) / count);
        float angle = -1.5707964f + (index * GOLDEN_ANGLE);
        float x = centerX + ((float) Math.cos(angle) * radiusX * fraction);
        float y = centerY + ((float) Math.sin(angle) * radiusY * fraction * 0.86f);
        return new float[]{clamp(x, minX, maxX), clamp(y, minY, maxY)};
    }

    private void rememberMotion(MotionEvent event) {
        if (event == null) {
            return;
        }
        lastRawX = event.getRawX();
        lastRawY = event.getRawY();
    }

    private void updateHoverFromLastMotion() {
        if (!Float.isNaN(lastRawX) && !Float.isNaN(lastRawY)) {
            updateHover(lastRawX, lastRawY);
        }
    }

    private void updateHover(float rawX, float rawY) {
        setHoveredHolder(findHitHolder(rawX, rawY));
    }

    private IconHolder findHitHolder(float rawX, float rawY) {
        if (iconHolders.isEmpty()) {
            return null;
        }
        int[] location = new int[2];
        overlayView.getLocationOnScreen(location);
        float x = rawX - location[0];
        float y = rawY - location[1];
        for (IconHolder holder : iconHolders) {
            View root = holder.root;
            if (root.getVisibility() != View.VISIBLE) {
                continue;
            }
            float left = root.getX();
            float top = root.getY();
            if (x >= left
                    && x <= left + root.getWidth()
                    && y >= top
                    && y <= top + root.getHeight()) {
                return holder;
            }
        }
        return null;
    }

    private void setHoveredHolder(IconHolder holder) {
        if (hoveredHolder == holder) {
            return;
        }
        IconHolder previous = hoveredHolder;
        hoveredHolder = holder;
        if (previous != null) {
            previous.root.animate()
                    .setStartDelay(0L)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(90L)
                    .start();
        }
        if (holder != null) {
            overlayView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            holder.root.bringToFront();
            holder.root.animate()
                    .setStartDelay(0L)
                    .scaleX(1.32f)
                    .scaleY(1.32f)
                    .setDuration(90L)
                    .start();
        }
    }

    private void launchApp(MBackStarApp app) {
        if (app == null || app.taskId < 0 || activityManager == null) {
            dismiss();
            return;
        }
        int taskId = app.taskId;
        dismiss();
        try {
            activityManager.moveTaskToFront(taskId, 0);
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to move mBack star task to front: " + taskId, t);
        }
    }

    private static FrameLayout buildOverlayView(Context context, FrameLayout iconsLayer) {
        FrameLayout overlay = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                MBackStarOverlayBridge.dispatchMBackMotionEvent(event);
                return true;
            }
        };
        overlay.setBackgroundColor(Color.argb(178, 0, 0, 0));
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.addView(
                iconsLayer,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        return overlay;
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

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class IconHolder {
        final MBackStarApp app;
        final View root;

        IconHolder(MBackStarApp app, View root) {
            this.app = app;
            this.root = root;
        }
    }
}
