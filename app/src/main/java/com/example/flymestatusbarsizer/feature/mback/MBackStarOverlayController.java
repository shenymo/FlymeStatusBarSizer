package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

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
    private static final float SEMICIRCLE_START_RADIANS = 3.4906585f;
    private static final float SEMICIRCLE_END_RADIANS = 5.9341195f;
    private static final float ICON_GAP_RATIO = 0.5f;
    private static final float GYRO_PARALLAX_SCALE = 120f;
    private static final int GYRO_PARALLAX_MAX_OFFSET_DP = 10;
    private static final int PREVIEW_WIDTH_DP = 180;
    private static final int PREVIEW_TOP_MARGIN_DP = 14;
    private static final int PREVIEW_CORNER_RADIUS_DP = 22;
    private static final long LAUNCH_ANIMATION_DURATION_MS = 260L;
    private static final long LAUNCH_OVERLAY_DISMISS_DELAY_MS = 360L;
    private static final DecelerateInterpolator LAUNCH_ANIMATION_INTERPOLATOR =
            new DecelerateInterpolator(1.18f);
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Method moveTaskToFrontWithOptionsMethod;
    private static boolean moveTaskToFrontWithOptionsMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityManager activityManager;
    private final MBackStarAppProvider appProvider;
    private final MBackTaskSnapshotProvider snapshotProvider;
    private final FrameLayout overlayView;
    private final FrameLayout iconsLayer;
    private final FrameLayout previewContainer;
    private final ImageView previewImageView;
    private final ArrayList<IconHolder> iconHolders = new ArrayList<>();
    private final HashMap<Integer, Bitmap> previewCache = new HashMap<>();
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            handleGyroEvent(event);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private WindowManager windowManager;
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private MBackStarApp[] apps = MBackStarApp.EMPTY_ARRAY;
    private IconHolder hoveredHolder;
    private boolean showing;
    private boolean gyroRegistered;
    private boolean launchAnimationRunning;
    private long lastGyroTimestampNanos;
    private float parallaxX;
    private float parallaxY;
    private float lastRawX = Float.NaN;
    private float lastRawY = Float.NaN;
    private float mBackRawX = Float.NaN;
    private float mBackRawY = Float.NaN;
    private ValueAnimator launchAnimator;
    private GradientDrawable previewBackground;
    private float previewCornerRadius;

    MBackStarOverlayController(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.activityManager = this.context != null
                ? (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE)
                : null;
        this.appProvider = new MBackStarAppProvider(this.context);
        this.snapshotProvider = new MBackTaskSnapshotProvider(this.context);
        this.iconsLayer = new FrameLayout(this.context);
        this.previewImageView = buildPreviewImageView(this.context);
        this.previewContainer = buildPreviewContainer(this.context, previewImageView);
        this.overlayView = buildOverlayView(this.context, iconsLayer, previewContainer);
        Object sensorObject = this.context != null
                ? this.context.getSystemService(Context.SENSOR_SERVICE)
                : null;
        if (sensorObject instanceof SensorManager) {
            sensorManager = (SensorManager) sensorObject;
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    void show(View anchor, MotionEvent startEvent) {
        if (anchor == null || !anchor.isAttachedToWindow()) {
            return;
        }
        lastRawX = Float.NaN;
        lastRawY = Float.NaN;
        mBackRawX = Float.NaN;
        mBackRawY = Float.NaN;
        resetLaunchAnimationState();
        resetParallax();
        rememberMotion(startEvent);
        rememberMBackOrigin(anchor, startEvent);
        if (!attachOverlay(anchor.getContext())) {
            return;
        }
        showing = true;
        hoveredHolder = null;
        apps = MBackStarApp.EMPTY_ARRAY;
        previewCache.clear();
        hidePreview();
        iconsLayer.removeAllViews();
        iconHolders.clear();
        overlayView.setAlpha(0f);
        overlayView.animate()
                .alpha(1f)
                .setDuration(120L)
                .start();
        startGyro();
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
        if (launchAnimationRunning) {
            return true;
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

    boolean isActive() {
        return showing || launchAnimationRunning;
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
        launchAnimationRunning = false;
        stopGyro();
        hoveredHolder = null;
        lastRawX = Float.NaN;
        lastRawY = Float.NaN;
        mBackRawX = Float.NaN;
        mBackRawY = Float.NaN;
        resetParallax();
        apps = MBackStarApp.EMPTY_ARRAY;
        previewCache.clear();
        resetLaunchAnimationState();
        hidePreview();
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
        if (!showing || launchAnimationRunning || !overlayView.isAttachedToWindow()) {
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
        hidePreview();
        MBackStarApp[] safeApps = apps != null ? apps : MBackStarApp.EMPTY_ARRAY;
        int count = safeApps.length;
        float originX = resolveOverlayOriginX(width);
        float originY = resolveOverlayOriginY(height);
        float maxRadius = resolveMaxSemicircleRadius(width, height, originX, originY);
        int hitSize = resolveAdaptiveHitSize(count, maxRadius);
        int iconSize = resolveAdaptiveIconSize(hitSize);
        float radius = resolveSemicircleRadius(count, iconSize, maxRadius);
        for (int i = 0; i < count; i++) {
            MBackStarApp app = safeApps[i];
            if (app == null || app.taskId < 0) {
                continue;
            }
            FrameLayout root = buildIconRoot(app, hitSize, iconSize);
            float[] point = resolveSemicirclePoint(
                    i,
                    count,
                    hitSize,
                    iconSize,
                    originX,
                    originY,
                    radius);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(hitSize, hitSize);
            params.leftMargin = Math.round(point[0]);
            params.topMargin = Math.round(point[1]);
            iconsLayer.addView(root, params);
            IconHolder holder = new IconHolder(app, root, resolveIconDepth(i, count));
            iconHolders.add(holder);
            root.setCameraDistance(dp(900));
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

    private float[] resolveSemicirclePoint(
            int index,
            int count,
            int hitSize,
            int iconSize,
            float originX,
            float originY,
            float radius) {
        float centerAngle = (SEMICIRCLE_START_RADIANS + SEMICIRCLE_END_RADIANS) / 2f;
        float maxAngleRange = SEMICIRCLE_END_RADIANS - SEMICIRCLE_START_RADIANS;
        float targetStep = (iconSize * (1f + ICON_GAP_RATIO)) / Math.max(1f, radius);
        float step = count <= 1
                ? 0f
                : Math.min(maxAngleRange / Math.max(1f, count - 1f), targetStep);
        float startAngle = centerAngle - (step * (count - 1f) / 2f);
        float angle = startAngle + (step * index);
        float x = originX + ((float) Math.cos(angle) * radius) - (hitSize / 2f);
        float y = originY + ((float) Math.sin(angle) * radius) - (hitSize / 2f);
        int margin = dp(12);
        return new float[]{
                clamp(x, margin, Math.max(margin, overlayView.getWidth() - hitSize - margin)),
                clamp(y, margin, Math.max(margin, overlayView.getHeight() - hitSize - margin))
        };
    }

    private float resolveOverlayOriginX(int width) {
        int[] location = new int[2];
        overlayView.getLocationOnScreen(location);
        if (!Float.isNaN(mBackRawX)) {
            return clamp(mBackRawX - location[0], dp(24), width - dp(24));
        }
        return width / 2f;
    }

    private float resolveOverlayOriginY(int height) {
        int[] location = new int[2];
        overlayView.getLocationOnScreen(location);
        if (!Float.isNaN(mBackRawY)) {
            return clamp(mBackRawY - location[1], dp(80), height - dp(8));
        }
        return height - dp(24);
    }

    private float resolveMaxSemicircleRadius(int width, int height, float originX, float originY) {
        float margin = dp(18);
        float leftRoom = Math.max(1f, originX - margin);
        float rightRoom = Math.max(1f, width - originX - margin);
        float topRoom = Math.max(1f, originY - margin);
        float radius = Math.min(Math.min(leftRoom, rightRoom), topRoom);
        return Math.max(dp(72), radius);
    }

    private int resolveAdaptiveHitSize(int count, float maxRadius) {
        int safeCount = Math.max(1, count);
        float arcLength = (SEMICIRCLE_END_RADIANS - SEMICIRCLE_START_RADIANS) * maxRadius;
        float spacing = arcLength / Math.max(1, safeCount - 1);
        int bySpacing = Math.round((spacing / (1f + ICON_GAP_RATIO)) + dp(18));
        int byCount = dp(82 - Math.min(42, safeCount * 3));
        return Math.round(clamp(
                Math.min(bySpacing, byCount),
                dp(42),
                dp(78)));
    }

    private int resolveAdaptiveIconSize(int hitSize) {
        return Math.max(dp(28), hitSize - dp(18));
    }

    private float resolveSemicircleRadius(int count, int iconSize, float maxRadius) {
        float minRadius = dp(82);
        float requiredRadius = count <= 1
                ? minRadius
                : (iconSize * (1f + ICON_GAP_RATIO) * Math.max(1, count - 1))
                        / (SEMICIRCLE_END_RADIANS - SEMICIRCLE_START_RADIANS)
                        * 1.02f;
        return clamp(requiredRadius, minRadius, maxRadius);
    }

    private static float resolveIconDepth(int index, int count) {
        if (count <= 1) {
            return 1f;
        }
        float center = (count - 1f) / 2f;
        float edgeDistance = Math.abs(index - center) / Math.max(1f, center);
        return 0.72f + ((1f - edgeDistance) * 0.38f);
    }

    private void rememberMBackOrigin(View anchor, MotionEvent startEvent) {
        if (startEvent != null) {
            mBackRawX = startEvent.getRawX();
            mBackRawY = startEvent.getRawY();
            return;
        }
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        mBackRawX = location[0] + (anchor.getWidth() / 2f);
        mBackRawY = location[1] + (anchor.getHeight() / 2f);
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

    private void startGyro() {
        if (gyroRegistered || sensorManager == null || gyroSensor == null) {
            return;
        }
        try {
            gyroRegistered = sensorManager.registerListener(
                    gyroListener,
                    gyroSensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    handler);
        } catch (Throwable ignored) {
            gyroRegistered = false;
        }
    }

    private void stopGyro() {
        if (!gyroRegistered || sensorManager == null) {
            return;
        }
        try {
            sensorManager.unregisterListener(gyroListener);
        } catch (Throwable ignored) {
        }
        gyroRegistered = false;
        lastGyroTimestampNanos = 0L;
    }

    private void handleGyroEvent(SensorEvent event) {
        if (!showing
                || launchAnimationRunning
                || event == null
                || event.values == null
                || event.values.length < 2) {
            return;
        }
        long timestamp = event.timestamp;
        float dt = lastGyroTimestampNanos == 0L
                ? 0.016f
                : Math.min(0.05f, (timestamp - lastGyroTimestampNanos) / 1000000000f);
        lastGyroTimestampNanos = timestamp;
        float maxOffset = dp(GYRO_PARALLAX_MAX_OFFSET_DP);
        parallaxX = clamp(
                (parallaxX * 0.88f) + (-event.values[1] * GYRO_PARALLAX_SCALE * dt),
                -maxOffset,
                maxOffset);
        parallaxY = clamp(
                (parallaxY * 0.88f) + (event.values[0] * GYRO_PARALLAX_SCALE * dt),
                -maxOffset,
                maxOffset);
        applyParallax();
    }

    private void resetParallax() {
        parallaxX = 0f;
        parallaxY = 0f;
        lastGyroTimestampNanos = 0L;
    }

    private void applyParallax() {
        if (iconHolders.isEmpty()) {
            return;
        }
        for (IconHolder holder : iconHolders) {
            float depth = holder.depth;
            holder.root.setTranslationX(parallaxX * depth);
            holder.root.setTranslationY(parallaxY * depth);
            holder.root.setRotationY(clamp(parallaxX * depth * 0.42f, -5.5f, 5.5f));
            holder.root.setRotationX(clamp(-parallaxY * depth * 0.42f, -5.5f, 5.5f));
        }
        if (hoveredHolder != null && previewContainer.getVisibility() == View.VISIBLE) {
            updatePreviewPosition(hoveredHolder);
        }
    }

    private void updateHover(float rawX, float rawY) {
        if (launchAnimationRunning) {
            return;
        }
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
            showPreview(holder);
        } else {
            hidePreview();
        }
    }

    private void showPreview(IconHolder holder) {
        if (launchAnimationRunning) {
            return;
        }
        if (holder == null || holder.app == null || holder.app.taskId < 0) {
            hidePreview();
            return;
        }
        int taskId = holder.app.taskId;
        Bitmap cachedBitmap = previewCache.get(taskId);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            showPreviewBitmap(holder, cachedBitmap);
            return;
        }
        previewImageView.setImageBitmap(null);
        setPreviewVisible(false);
        snapshotProvider.requestSnapshot(taskId, handler, (resolvedTaskId, bitmap) -> {
            if (!showing
                    || launchAnimationRunning
                    || bitmap == null
                    || bitmap.isRecycled()
                    || hoveredHolder == null
                    || hoveredHolder.app == null
                    || hoveredHolder.app.taskId != resolvedTaskId) {
                return;
            }
            previewCache.put(resolvedTaskId, bitmap);
            showPreviewBitmap(hoveredHolder, bitmap);
        });
    }

    private void showPreviewBitmap(IconHolder holder, Bitmap bitmap) {
        if (holder == null || bitmap == null || bitmap.isRecycled()) {
            hidePreview();
            return;
        }
        previewImageView.setImageBitmap(bitmap);
        updatePreviewPosition(holder);
        setPreviewVisible(true);
    }

    private void updatePreviewPosition(IconHolder holder) {
        if (holder == null || previewContainer == null || overlayView.getWidth() <= 0) {
            return;
        }
        int previewWidth = Math.min(dp(PREVIEW_WIDTH_DP), overlayView.getWidth() - dp(24));
        int imageWidth = previewImageView.getDrawable() != null
                ? previewImageView.getDrawable().getIntrinsicWidth()
                : 0;
        int imageHeight = previewImageView.getDrawable() != null
                ? previewImageView.getDrawable().getIntrinsicHeight()
                : 0;
        int previewHeight = imageWidth > 0 && imageHeight > 0
                ? Math.round(previewWidth * (imageHeight / (float) imageWidth))
                : Math.round(previewWidth * 1.72f);
        FrameLayout.LayoutParams params =
                previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams
                        ? (FrameLayout.LayoutParams) previewContainer.getLayoutParams()
                        : new FrameLayout.LayoutParams(previewWidth, previewHeight);
        params.width = previewWidth;
        params.height = previewHeight;
        float centerX = holder.root.getX() + (holder.root.getWidth() / 2f);
        int margin = dp(12);
        params.leftMargin = Math.round(clamp(
                centerX - (previewWidth / 2f),
                margin,
                Math.max(margin, overlayView.getWidth() - previewWidth - margin)));
        params.topMargin = Math.round(clamp(
                holder.root.getY() - previewHeight - dp(PREVIEW_TOP_MARGIN_DP),
                margin,
                Math.max(margin, overlayView.getHeight() - previewHeight - margin)));
        previewContainer.setLayoutParams(params);
    }

    private void hidePreview() {
        previewImageView.setImageBitmap(null);
        setPreviewVisible(false);
    }

    private void setPreviewVisible(boolean visible) {
        if (previewContainer.getVisibility() == (visible ? View.VISIBLE : View.GONE)) {
            return;
        }
        if (visible) {
            previewContainer.setAlpha(0f);
            previewContainer.setScaleX(0.96f);
            previewContainer.setScaleY(0.96f);
            previewContainer.setVisibility(View.VISIBLE);
            previewContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100L)
                    .start();
            return;
        }
        previewContainer.animate().cancel();
        previewContainer.setVisibility(View.GONE);
    }

    private void launchApp(MBackStarApp app) {
        if (app == null || app.taskId < 0 || activityManager == null) {
            dismiss();
            return;
        }
        int taskId = app.taskId;
        if (previewContainer.getVisibility() == View.VISIBLE
                && previewImageView.getDrawable() != null
                && overlayView.getWidth() > 0
                && overlayView.getHeight() > 0
                && startLaunchAnimation(taskId)) {
            return;
        }
        dismiss();
        moveTaskToFront(taskId);
    }

    private boolean startLaunchAnimation(int taskId) {
        FrameLayout.LayoutParams params =
                previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams
                        ? (FrameLayout.LayoutParams) previewContainer.getLayoutParams()
                        : null;
        if (params == null) {
            return false;
        }
        int startWidth = previewContainer.getWidth() > 0 ? previewContainer.getWidth() : params.width;
        int startHeight = previewContainer.getHeight() > 0 ? previewContainer.getHeight() : params.height;
        int targetWidth = overlayView.getWidth();
        int targetHeight = overlayView.getHeight();
        if (startWidth <= 0 || startHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }
        launchAnimationRunning = true;
        stopGyro();
        overlayView.animate().cancel();
        overlayView.setAlpha(1f);
        iconsLayer.animate().cancel();
        iconsLayer.animate()
                .alpha(0f)
                .setDuration(90L)
                .start();
        previewContainer.animate().cancel();
        previewContainer.bringToFront();
        previewContainer.setAlpha(1f);
        previewContainer.setScaleX(1f);
        previewContainer.setScaleY(1f);
        previewContainer.setTranslationX(0f);
        previewContainer.setTranslationY(0f);
        previewContainer.setClipToOutline(true);
        setPreviewCornerRadius(dp(PREVIEW_CORNER_RADIUS_DP));

        final int startLeft = params.leftMargin;
        final int startTop = params.topMargin;
        launchAnimator = ValueAnimator.ofFloat(0f, 1f);
        launchAnimator.setDuration(LAUNCH_ANIMATION_DURATION_MS);
        launchAnimator.setInterpolator(LAUNCH_ANIMATION_INTERPOLATOR);
        launchAnimator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            FrameLayout.LayoutParams currentParams =
                    previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams
                            ? (FrameLayout.LayoutParams) previewContainer.getLayoutParams()
                            : new FrameLayout.LayoutParams(startWidth, startHeight);
            currentParams.leftMargin = Math.round(lerp(startLeft, 0f, progress));
            currentParams.topMargin = Math.round(lerp(startTop, 0f, progress));
            currentParams.width = Math.max(1, Math.round(lerp(startWidth, targetWidth, progress)));
            currentParams.height = Math.max(1, Math.round(lerp(startHeight, targetHeight, progress)));
            previewContainer.setLayoutParams(currentParams);
            previewContainer.invalidateOutline();
        });
        launchAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!moveTaskToFront(taskId)) {
                    dismiss();
                    return;
                }
                handler.postDelayed(() -> {
                    if (launchAnimationRunning) {
                        dismiss();
                    }
                }, LAUNCH_OVERLAY_DISMISS_DELAY_MS);
            }
        });
        launchAnimator.start();
        return true;
    }

    private void resetLaunchAnimationState() {
        if (launchAnimator != null) {
            launchAnimator.cancel();
            launchAnimator = null;
        }
        launchAnimationRunning = false;
        iconsLayer.animate().cancel();
        iconsLayer.setAlpha(1f);
        previewContainer.animate().cancel();
        previewContainer.setAlpha(1f);
        previewContainer.setScaleX(1f);
        previewContainer.setScaleY(1f);
        previewContainer.setTranslationX(0f);
        previewContainer.setTranslationY(0f);
        previewContainer.setClipToOutline(true);
        setPreviewCornerRadius(dp(PREVIEW_CORNER_RADIUS_DP));
    }

    private boolean moveTaskToFront(int taskId) {
        try {
            Bundle options = buildNoAnimationOptions();
            if (startActivityFromRecents(taskId, options)) {
                return true;
            }
            Method method = resolveMoveTaskToFrontWithOptionsMethod();
            if (method != null && options != null) {
                method.invoke(activityManager, taskId, 0, options);
            } else {
                activityManager.moveTaskToFront(taskId, 0);
            }
            return true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning("Failed to move mBack star task to front: " + taskId, t);
            try {
                activityManager.moveTaskToFront(taskId, 0);
                return true;
            } catch (Throwable fallback) {
                FlymeStatusBarSizer.logMBackWarning(
                        "Failed to fallback move mBack star task to front: " + taskId,
                        fallback);
            }
        }
        return false;
    }

    private boolean startActivityFromRecents(int taskId, Bundle options) {
        try {
            Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
            Method getServiceMethod = activityTaskManagerClass.getDeclaredMethod("getService");
            getServiceMethod.setAccessible(true);
            Object service = getServiceMethod.invoke(null);
            if (service == null) {
                return false;
            }
            Class<?> serviceClass = Class.forName("android.app.IActivityTaskManager");
            Method method = serviceClass.getMethod("startActivityFromRecents", int.class, Bundle.class);
            method.setAccessible(true);
            Object result = method.invoke(service, taskId, options);
            return !(result instanceof Integer) || (Integer) result >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Bundle buildNoAnimationOptions() {
        try {
            return ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private FrameLayout buildPreviewContainer(Context context, ImageView imageView) {
        FrameLayout container = new FrameLayout(context);
        container.setVisibility(View.GONE);
        container.setClipToOutline(true);
        container.setElevation(dp(18));
        previewCornerRadius = dp(PREVIEW_CORNER_RADIUS_DP);
        previewBackground = new GradientDrawable();
        previewBackground.setShape(GradientDrawable.RECTANGLE);
        previewBackground.setCornerRadius(previewCornerRadius);
        previewBackground.setColor(Color.argb(235, 16, 18, 24));
        previewBackground.setStroke(Math.max(1, dp(1)), Color.argb(92, 255, 255, 255));
        container.setBackground(previewBackground);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), previewCornerRadius);
            }
        });
        container.addView(
                imageView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        return container;
    }

    private static ImageView buildPreviewImageView(Context context) {
        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(false);
        return imageView;
    }

    private static FrameLayout buildOverlayView(
            Context context,
            FrameLayout iconsLayer,
            View previewContainer) {
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
        overlay.addView(
                previewContainer,
                new FrameLayout.LayoutParams(1, 1));
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

    private static Method resolveMoveTaskToFrontWithOptionsMethod() {
        if (!moveTaskToFrontWithOptionsMethodResolved) {
            try {
                moveTaskToFrontWithOptionsMethod = ActivityManager.class.getMethod(
                        "moveTaskToFront",
                        int.class,
                        int.class,
                        Bundle.class);
            } catch (Throwable ignored) {
                moveTaskToFrontWithOptionsMethod = null;
            }
            moveTaskToFrontWithOptionsMethodResolved = true;
        }
        return moveTaskToFrontWithOptionsMethod;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()));
    }

    private void setPreviewCornerRadius(float radius) {
        previewCornerRadius = radius;
        if (previewBackground != null) {
            previewBackground.setCornerRadius(radius);
        }
        if (previewContainer != null) {
            previewContainer.invalidateOutline();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    private static final class IconHolder {
        final MBackStarApp app;
        final View root;
        final float depth;

        IconHolder(MBackStarApp app, View root, float depth) {
            this.app = app;
            this.root = root;
            this.depth = depth;
        }
    }
}
