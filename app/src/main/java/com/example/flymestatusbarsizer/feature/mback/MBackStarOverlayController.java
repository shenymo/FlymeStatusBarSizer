package com.example.flymestatusbarsizer.feature.mback;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.Transliterator;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

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
    private static final float ICON_GAP_RATIO = 0.35f; // 调紧图标排列间距比例
    private static final float GYRO_PARALLAX_SCALE = 120f;
    private static final int GYRO_PARALLAX_MAX_OFFSET_DP = 10;
    private static final int PREVIEW_WIDTH_DP = 128;
    private static final int PREVIEW_CORNER_RADIUS_DP = 22;
    private static final String[] LETTER_BUCKETS = new String[]{
            "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R",
            "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };
    private static final float SMALL_WINDOW_ICON_SCALE = 1f / 3f;
    private static final int FLYME_WINDOW_MODE_MINI = 11;
    private static final int FLYME_WINDOW_MODE_FREEFORM = 1035;
    private static final String START_WINDOW_MODE_BUNDLE_KEY = "start_windowmode";
    private static final long SMALL_WINDOW_ANIMATION_DURATION_MS = 260L;
    private static final long SMALL_WINDOW_HOVER_TIMEOUT_MS = 1000L;
    private static final long SMALL_WINDOW_OVERLAY_DISMISS_DELAY_MS = 60L;
    private static final long LAUNCH_ANIMATION_DURATION_MS = 260L;
    private static final long LAUNCH_OVERLAY_DISMISS_DELAY_MS = 360L;
    private static final DecelerateInterpolator LAUNCH_ANIMATION_INTERPOLATOR =
            new DecelerateInterpolator(1.18f);
    private static Method trustedOverlayMethod;
    private static boolean trustedOverlayMethodResolved;
    private static Method activityTaskManagerGetServiceMethod;
    private static boolean activityTaskManagerGetServiceMethodResolved;
    private static Method startActivityFromRecentsMethod;
    private static boolean startActivityFromRecentsMethodResolved;
    private static Method makeCustomTaskAnimationMethod;
    private static boolean makeCustomTaskAnimationMethodResolved;
    private static Method windowManagerExtGetInstanceMethod;
    private static boolean windowManagerExtGetInstanceMethodResolved;
    private static Method setStartWindowModeMethod;
    private static boolean setStartWindowModeMethodResolved;
    private static Method getWindowModeBoundMethod;
    private static boolean getWindowModeBoundMethodResolved;
    private static Field trustedOverlayPrivateFlagsField;
    private static boolean trustedOverlayPrivateFlagsFieldResolved;
    private static Transliterator pinyinTransliterator;
    private static boolean pinyinTransliteratorResolved;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MBackStarAppProvider appProvider;
    private final FrameLayout overlayView;
    private final FrameLayout iconsLayer;
    private final FrameLayout previewContainer;
    private final ImageView previewImageView;
    private final TextView previewTextView;
    private final ArrayList<IconHolder> iconHolders = new ArrayList<>();
    private final HashMap<String, ArrayList<MBackStarApp>> appGroups = new HashMap<>();
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
    private String selectedLetter;
    private int selectedLetterIndex = -1;
    private long hoveredHolderStartTimeMs;
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
    private float letterSlideStartRawX = Float.NaN;
    private ValueAnimator launchAnimator;
    private ValueAnimator hoverTimerAnimator;
    private boolean smallWindowReadyHapticFired;
    private GradientDrawable previewBackground;
    private float previewCornerRadius;

    MBackStarOverlayController(Context context) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        this.context = appContext != null ? appContext : context;
        this.appProvider = new MBackStarAppProvider(this.context);
        this.iconsLayer = new FrameLayout(this.context);
        this.previewImageView = buildPreviewImageView(this.context);
        this.previewTextView = buildPreviewTextView(this.context);
        this.previewContainer = buildPreviewContainer(this.context, previewImageView, previewTextView);
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
        letterSlideStartRawX = Float.NaN;
        resetLaunchAnimationState();
        resetParallax();
        rememberMotion(startEvent);
        rememberMBackOrigin(anchor, startEvent);
        if (!attachOverlay(anchor.getContext())) {
            return;
        }
        showing = true;
        cancelHoverTimer();
        hoveredHolder = null;
        selectedLetter = null;
        selectedLetterIndex = -1;
        hoveredHolderStartTimeMs = 0L;
        smallWindowReadyHapticFired = false;
        apps = MBackStarApp.EMPTY_ARRAY;
        appGroups.clear();
        hidePreview();
        iconsLayer.removeAllViews();
        iconHolders.clear();
        overlayView.setBackgroundColor(getOverlayBackgroundColor(context));
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
                launchApp(target, shouldLaunchSmallWindow(target));
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
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
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
        if (Build.VERSION.SDK_INT >= 31) {
            params.setBlurBehindRadius(dp(30));
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
        cancelHoverTimer();
        hoveredHolder = null;
        selectedLetter = null;
        selectedLetterIndex = -1;
        hoveredHolderStartTimeMs = 0L;
        lastRawX = Float.NaN;
        lastRawY = Float.NaN;
        mBackRawX = Float.NaN;
        mBackRawY = Float.NaN;
        letterSlideStartRawX = Float.NaN;
        resetParallax();
        apps = MBackStarApp.EMPTY_ARRAY;
        appGroups.clear();
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
        buildAppGroups();
        selectedLetter = null;
        selectedLetterIndex = -1;
        renderStarMap(true);
        updateHoverFromLastMotion();
    }

    private void buildAppGroups() {
        appGroups.clear();
        for (String letter : LETTER_BUCKETS) {
            appGroups.put(letter, new ArrayList<>());
        }
        MBackStarApp[] safeApps = apps != null ? apps : MBackStarApp.EMPTY_ARRAY;
        for (MBackStarApp app : safeApps) {
            if (app == null) {
                continue;
            }
            String bucket = resolveAppBucket(app);
            ArrayList<MBackStarApp> group = appGroups.get(bucket);
            if (group == null) {
                group = appGroups.get("#");
            }
            if (group != null) {
                group.add(app);
            }
        }
    }

    private boolean hasAppsForLetter(String letter) {
        ArrayList<MBackStarApp> group = appGroups.get(letter);
        return group != null && !group.isEmpty();
    }

    private void renderStarMap(boolean animate) {
        if (!showing || launchAnimationRunning || !overlayView.isAttachedToWindow()) {
            return;
        }
        int width = overlayView.getWidth();
        int height = overlayView.getHeight();
        if (width <= 0 || height <= 0) {
            overlayView.post(() -> renderStarMap(animate));
            return;
        }
        cancelHoverTimer();
        iconsLayer.removeAllViews();
        iconHolders.clear();
        hoveredHolder = null;
        hoveredHolderStartTimeMs = 0L;
        hidePreview();
        float originX = resolveOverlayOriginX(width);
        float originY = resolveOverlayOriginY(height);
        float maxRadius = resolveMaxLetterRadius(width, height, originX, originY);
        int letterHitSize = dp(28);
        int letterSize = dp(28);
        float letterRadius = resolveLetterRadius(maxRadius);
        for (int i = 0; i < LETTER_BUCKETS.length; i++) {
            String letter = LETTER_BUCKETS[i];
            boolean enabled = hasAppsForLetter(letter);
            FrameLayout root = buildLetterRoot(letter, enabled, letterHitSize, letterSize);
            int visualIndex = selectedLetterIndex >= 0
                    ? i - selectedLetterIndex + (LETTER_BUCKETS.length / 2)
                    : i;
            float[] point = resolveSemicirclePoint(
                    visualIndex,
                    LETTER_BUCKETS.length,
                    letterHitSize,
                    letterSize,
                    originX,
                    originY,
                    letterRadius);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(letterHitSize, letterHitSize);
            params.leftMargin = Math.round(point[0]);
            params.topMargin = Math.round(point[1]);
            iconsLayer.addView(root, params);
            IconHolder holder = new IconHolder(null, letter, enabled, root, null,
                    resolveIconDepth(visualIndex, LETTER_BUCKETS.length));
            iconHolders.add(holder);
            animateHolder(root, animate, i);
        }
        renderSelectedLetterApps(originX, originY, letterRadius, maxRadius, animate);
    }

    private void renderSelectedLetterApps(
            float originX,
            float originY,
            float letterRadius,
            float maxRadius,
            boolean animate) {
        ArrayList<MBackStarApp> group = selectedLetter != null ? appGroups.get(selectedLetter) : null;
        if (group == null || group.isEmpty()) {
            return;
        }
        int count = group.size();
        int hitSize = Math.round(clamp(dp(54) - Math.min(dp(12), count), dp(42), dp(56)));
        int iconSize = resolveAdaptiveIconSize(hitSize);
        float layerGap = hitSize * 0.78f;
        int availableLayers = Math.max(1, (int) Math.floor(
                Math.max(1f, maxRadius - letterRadius - dp(34)) / Math.max(1f, layerGap)) + 1);
        int desiredLayers = Math.max(1, (int) Math.ceil(count / 12f));
        int layerCount = Math.min(desiredLayers, availableLayers);
        int appsPerLayer = (int) Math.ceil(count / (float) layerCount);
        for (int i = 0; i < count; i++) {
            MBackStarApp app = group.get(i);
            if (app == null) {
                continue;
            }
            int layer = Math.min(layerCount - 1, i / Math.max(1, appsPerLayer));
            int ringIndex = i - (layer * appsPerLayer);
            int ringCount = Math.min(appsPerLayer, count - (layer * appsPerLayer));
            float radius = maxRadius - (layer * layerGap);
            FrameLayout root = buildIconRoot(app, hitSize, iconSize);
            HoverTimerView timerView = findHoverTimerView(root);
            float[] point = resolveSemicirclePoint(
                    ringIndex,
                    ringCount,
                    hitSize,
                    iconSize,
                    originX,
                    originY,
                    radius);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(hitSize, hitSize);
            params.leftMargin = Math.round(point[0]);
            params.topMargin = Math.round(point[1]);
            iconsLayer.addView(root, params);
            IconHolder holder = new IconHolder(app, null, true, root, timerView,
                    resolveIconDepth(ringIndex, ringCount));
            iconHolders.add(holder);
            animateHolder(root, animate, i);
        }
    }

    private void animateHolder(View root, boolean animate, int index) {
        root.setCameraDistance(dp(900));
        if (animate) {
            root.setAlpha(0f);
            root.setScaleX(0.72f);
            root.setScaleY(0.72f);
            root.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(Math.min(180L, index * 12L))
                    .setDuration(260L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                    .start();
        } else {
            root.setAlpha(1f);
            root.setScaleX(1f);
            root.setScaleY(1f);
        }
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
        HoverTimerView timerView = new HoverTimerView(context);
        timerView.setVisibility(View.GONE);
        int timerSize = Math.min(hitSize, Math.round(iconSize * 1.36f));
        FrameLayout.LayoutParams timerParams = new FrameLayout.LayoutParams(timerSize, timerSize);
        timerParams.gravity = Gravity.CENTER;
        root.addView(timerView, timerParams);
        root.setLayoutParams(new FrameLayout.LayoutParams(hitSize, hitSize));
        return root;
    }

    private FrameLayout buildLetterRoot(String letter, boolean enabled, int hitSize, int iconSize) {
        FrameLayout root = new FrameLayout(context);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setContentDescription(letter);
        TextView textView = new TextView(context);
        textView.setText(letter);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(enabled
                ? (isNightMode() ? Color.WHITE : Color.argb(230, 20, 24, 32))
                : Color.argb(105, 180, 185, 195));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        textParams.gravity = Gravity.CENTER;
        root.addView(textView, textParams);
        root.setLayoutParams(new FrameLayout.LayoutParams(hitSize, hitSize));
        return root;
    }

    private static HoverTimerView findHoverTimerView(FrameLayout root) {
        if (root == null || root.getChildCount() == 0) {
            return null;
        }
        View child = root.getChildAt(root.getChildCount() - 1);
        return child instanceof HoverTimerView ? (HoverTimerView) child : null;
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
        return clamp(radius, dp(72), dp(135)); // 限制最大半径上限为 135dp
    }

    private float resolveMaxLetterRadius(int width, int height, float originX, float originY) {
        float margin = dp(18);
        float leftRoom = Math.max(1f, originX - margin);
        float rightRoom = Math.max(1f, width - originX - margin);
        float topRoom = Math.max(1f, originY - margin);
        float radius = Math.min(Math.min(leftRoom, rightRoom), topRoom);
        return clamp(radius, dp(190), dp(320));
    }

    private float resolveLetterRadius(float maxRadius) {
        return clamp(maxRadius * 0.74f, dp(132), maxRadius);
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
        float minRadius = dp(75); // 稍微调低最小基准半径
        float requiredRadius = count <= 1
                ? minRadius
                : (iconSize * (1f + ICON_GAP_RATIO) * Math.max(1, count - 1))
                        / (SEMICIRCLE_END_RADIANS - SEMICIRCLE_START_RADIANS)
                        * 0.95f;
        // 乘以 0.75 的收聚收紧比例，使整体距离向手势原点聚拢收缩，操作更为省力
        float finalRadius = clamp(requiredRadius, minRadius, maxRadius) * 0.75f;
        return Math.max(dp(68), finalRadius); // 确保绝对不低于 68dp 避开大拇指遮挡
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
        int letterIndex = resolveLetterIndexFromSlide(rawX);
        if (letterIndex >= 0 && letterIndex != selectedLetterIndex) {
            selectedLetterIndex = letterIndex;
            selectedLetter = LETTER_BUCKETS[letterIndex];
            renderStarMap(false);
            setHoveredHolder(findLetterHolder(selectedLetter));
            return;
        }
        IconHolder holder = findHitHolder(rawX, rawY);
        if (holder != null && holder.isLetter()) {
            holder = findLetterHolder(selectedLetter);
        }
        setHoveredHolder(holder);
    }

    private int resolveLetterIndexFromSlide(float rawX) {
        if (Float.isNaN(letterSlideStartRawX)) {
            letterSlideStartRawX = rawX;
        }
        float step = dp(18);
        int delta = Math.round((rawX - letterSlideStartRawX) / Math.max(1f, step));
        int base = selectedLetterIndex >= 0 ? selectedLetterIndex : LETTER_BUCKETS.length / 2;
        int index = Math.round(clamp(base + delta, 0, LETTER_BUCKETS.length - 1));
        if (index != base) {
            letterSlideStartRawX = rawX;
        }
        return index;
    }

    private IconHolder findLetterHolder(String letter) {
        if (letter == null) {
            return null;
        }
        for (IconHolder holder : iconHolders) {
            if (holder != null && letter.equals(holder.letter)) {
                return holder;
            }
        }
        return null;
    }

    private IconHolder findHitHolder(float rawX, float rawY) {
        if (iconHolders.isEmpty()) {
            return null;
        }
        int[] location = new int[2];
        overlayView.getLocationOnScreen(location);
        float x = rawX - location[0];
        float y = rawY - location[1];
        for (int i = iconHolders.size() - 1; i >= 0; i--) {
            IconHolder holder = iconHolders.get(i);
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
        stopHoverTimer(previous);
        hoveredHolder = holder;
        hoveredHolderStartTimeMs = holder != null ? SystemClock.uptimeMillis() : 0L;
        if (previous != null) {
            previous.root.animate()
                    .setStartDelay(0L)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                    .start();
        }
        if (holder != null) {
            if (holder != previous) {
                overlayView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            holder.root.bringToFront();
            holder.root.animate()
                    .setStartDelay(0L)
                    .scaleX(1.32f)
                    .scaleY(1.32f)
                    .setDuration(200L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                    .start();
            if (holder.app != null) {
                startHoverTimer(holder);
            }
            showPreview(holder);
        } else {
            hidePreview();
        }
    }

    private void startHoverTimer(IconHolder holder) {
        if (holder == null || holder.timerView == null) {
            return;
        }
        smallWindowReadyHapticFired = false;
        holder.timerView.setProgress(0f);
        holder.timerView.setVisibility(View.VISIBLE);
        hoverTimerAnimator = ValueAnimator.ofFloat(0f, 1f);
        hoverTimerAnimator.setDuration(SMALL_WINDOW_HOVER_TIMEOUT_MS);
        hoverTimerAnimator.setInterpolator(new LinearInterpolator());
        hoverTimerAnimator.addUpdateListener(animation -> {
            if (holder != hoveredHolder || launchAnimationRunning) {
                return;
            }
            float progress = (Float) animation.getAnimatedValue();
            holder.timerView.setProgress(progress);
            if (!smallWindowReadyHapticFired && progress >= 1f) {
                fireSmallWindowReadyHaptic();
            }
        });
        hoverTimerAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled
                        && holder == hoveredHolder
                        && !launchAnimationRunning
                        && !smallWindowReadyHapticFired) {
                    holder.timerView.setProgress(1f);
                    fireSmallWindowReadyHaptic();
                }
            }
        });
        hoverTimerAnimator.start();
    }

    private void fireSmallWindowReadyHaptic() {
        if (smallWindowReadyHapticFired) {
            return;
        }
        smallWindowReadyHapticFired = true;
        overlayView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void stopHoverTimer(IconHolder holder) {
        if (hoverTimerAnimator != null) {
            hoverTimerAnimator.cancel();
            hoverTimerAnimator = null;
        }
        smallWindowReadyHapticFired = false;
        if (holder != null && holder.timerView != null) {
            holder.timerView.setProgress(0f);
            holder.timerView.setVisibility(View.GONE);
        }
    }

    private void cancelHoverTimer() {
        stopHoverTimer(hoveredHolder);
    }

    private boolean shouldLaunchSmallWindow(IconHolder holder) {
        return holder != null
                && holder == hoveredHolder
                && hoveredHolderStartTimeMs > 0L
                && SystemClock.uptimeMillis() - hoveredHolderStartTimeMs
                >= SMALL_WINDOW_HOVER_TIMEOUT_MS;
    }

    private void showPreview(IconHolder holder) {
        if (launchAnimationRunning) {
            return;
        }
        if (holder == null) {
            hidePreview();
            return;
        }
        if (holder.isLetter()) {
            previewImageView.setImageDrawable(null);
            previewImageView.setVisibility(View.GONE);
            previewTextView.setVisibility(View.VISIBLE);
            previewTextView.setText(holder.letter);
            previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42);
            previewTextView.setTypeface(Typeface.DEFAULT_BOLD);
            previewTextView.setGravity(Gravity.CENTER);
            previewTextView.setTextColor(isNightMode() ? Color.WHITE : Color.argb(235, 18, 22, 30));
            previewTextView.setPadding(dp(8), dp(8), dp(8), dp(8));
            setCenteredPreviewSize(dp(96), dp(96));
            setPreviewVisible(true);
            return;
        }
        if (holder.app == null) {
            hidePreview();
            return;
        }
        previewImageView.setVisibility(View.VISIBLE);
        previewImageView.setImageDrawable(holder.app.icon);
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setPadding(dp(18), dp(14), dp(18), dp(42));
        previewTextView.setVisibility(View.VISIBLE);
        previewTextView.setText(holder.app.label);
        previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        previewTextView.setTypeface(Typeface.DEFAULT);
        previewTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        previewTextView.setTextColor(isNightMode() ? Color.WHITE : Color.argb(235, 18, 22, 30));
        previewTextView.setPadding(dp(8), dp(8), dp(8), dp(11));
        setCenteredPreviewSize(dp(PREVIEW_WIDTH_DP), dp(132));
        setPreviewVisible(true);
    }

    private void updatePreviewPosition(IconHolder holder) {
        if (holder == null || previewContainer == null || overlayView.getWidth() <= 0) {
            return;
        }
        int width = holder.isLetter() ? dp(96) : dp(PREVIEW_WIDTH_DP);
        int height = holder.isLetter() ? dp(96) : dp(132);
        setCenteredPreviewSize(width, height);
    }

    private void setCenteredPreviewSize(int width, int height) {
        if (overlayView == null || overlayView.getWidth() <= 0 || overlayView.getHeight() <= 0) {
            return;
        }
        FrameLayout.LayoutParams params =
                previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams
                        ? (FrameLayout.LayoutParams) previewContainer.getLayoutParams()
                        : new FrameLayout.LayoutParams(width, height);
        params.width = Math.min(width, overlayView.getWidth() - dp(24));
        params.height = Math.min(height, overlayView.getHeight() - dp(48));
        params.leftMargin = Math.round((overlayView.getWidth() - params.width) / 2f);
        params.topMargin = Math.round((overlayView.getHeight() - params.height) / 2f);
        previewContainer.setLayoutParams(params);
    }

    private void hidePreview() {
        previewImageView.setImageDrawable(null);
        previewImageView.setVisibility(View.VISIBLE);
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setPadding(0, 0, 0, 0);
        previewTextView.setText("");
        previewTextView.setVisibility(View.GONE);
        resetPreviewBackground();
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

    private void launchApp(IconHolder holder, boolean smallWindow) {
        MBackStarApp app = holder != null ? holder.app : null;
        if (app == null) {
            dismiss();
            return;
        }
        int taskId = app.taskId;
        if (smallWindow && startSmallWindowLaunch(holder)) {
            return;
        }
        dismiss();
        if (taskId >= 0) {
            startTaskFromRecents(taskId);
        } else {
            startLauncherApp(app);
        }
    }

    private boolean startSmallWindowLaunch(IconHolder holder) {
        if (holder == null || holder.app == null) {
            return false;
        }
        fireSmallWindowReadyHaptic();
        stopHoverTimer(holder);
        boolean started = holder.app.taskId >= 0
                ? startTaskFromRecentsInSmallWindow(holder.app)
                : startLauncherAppInSmallWindow(holder.app);
        if (started) {
            dismiss();
        }
        return started;
    }

    private boolean prepareSmallWindowPreview(IconHolder holder) {
        if (holder == null || holder.app == null) {
            return false;
        }
        boolean hasPreviewFrame = previewContainer.getVisibility() == View.VISIBLE
                && previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams
                && ((FrameLayout.LayoutParams) previewContainer.getLayoutParams()).width > 1
                && ((FrameLayout.LayoutParams) previewContainer.getLayoutParams()).height > 1;
        if (!hasPreviewFrame) {
            updatePreviewPosition(holder);
        }
        previewImageView.setImageDrawable(holder.app.icon);
        applySmallWindowIconPadding();
        setPreviewTransitionBackground();
        previewContainer.animate().cancel();
        previewContainer.bringToFront();
        previewContainer.setVisibility(View.VISIBLE);
        previewContainer.setAlpha(1f);
        previewContainer.setScaleX(1f);
        previewContainer.setScaleY(1f);
        previewContainer.setTranslationX(0f);
        previewContainer.setTranslationY(0f);
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return previewContainer.getLayoutParams() instanceof FrameLayout.LayoutParams;
    }

    private Rect resolveSmallWindowTargetRect(int taskId, int startWidth, int startHeight) {
        try {
            Object windowManagerExt = resolveWindowManagerExtInstance(context);
            Method method = resolveGetWindowModeBoundMethod();
            if (windowManagerExt == null || method == null) {
                return null;
            }
            Rect rect = resolveWindowModeBound(
                    windowManagerExt,
                    method,
                    taskId,
                    FLYME_WINDOW_MODE_MINI);
            if (rect.isEmpty()) {
                rect = resolveWindowModeBound(
                        windowManagerExt,
                        method,
                        taskId,
                        FLYME_WINDOW_MODE_FREEFORM);
            }
            if (rect.isEmpty()) {
                return null;
            }
            int[] location = new int[2];
            overlayView.getLocationOnScreen(location);
            rect.offset(-location[0], -location[1]);
            return clampRectToOverlay(rect, startWidth, startHeight);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Rect resolveWindowModeBound(
            Object windowManagerExt,
            Method method,
            int taskId,
            int windowMode) throws Exception {
        Object result = method.invoke(windowManagerExt, resolveDisplayId(), taskId, windowMode);
        return result instanceof Rect ? new Rect((Rect) result) : new Rect();
    }

    private Rect buildFallbackSmallWindowTargetRect(int startWidth, int startHeight) {
        int overlayWidth = overlayView.getWidth();
        int overlayHeight = overlayView.getHeight();
        if (overlayWidth <= 0 || overlayHeight <= 0 || startWidth <= 0 || startHeight <= 0) {
            return null;
        }
        float aspect = startHeight / (float) startWidth;
        int targetWidth = Math.round(overlayWidth * 0.68f);
        int targetHeight = Math.round(targetWidth * aspect);
        int maxHeight = Math.round(overlayHeight * 0.68f);
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = Math.round(targetHeight / Math.max(0.1f, aspect));
        }
        int left = Math.round((overlayWidth - targetWidth) / 2f);
        int top = Math.round(((overlayHeight - targetHeight) / 2f) - dp(28));
        return clampRectToOverlay(
                new Rect(left, top, left + targetWidth, top + targetHeight),
                startWidth,
                startHeight);
    }

    private Rect clampRectToOverlay(Rect rect, int startWidth, int startHeight) {
        if (rect == null || rect.isEmpty()) {
            return null;
        }
        int overlayWidth = overlayView.getWidth();
        int overlayHeight = overlayView.getHeight();
        if (overlayWidth <= 0 || overlayHeight <= 0) {
            return null;
        }
        int minWidth = Math.max(dp(120), Math.round(startWidth * 0.72f));
        int minHeight = Math.max(dp(160), Math.round(startHeight * 0.72f));
        int width = Math.round(clamp(rect.width(), minWidth, overlayWidth - dp(24)));
        int height = Math.round(clamp(rect.height(), minHeight, overlayHeight - dp(48)));
        int margin = dp(12);
        int left = Math.round(clamp(rect.left, margin, Math.max(margin, overlayWidth - width - margin)));
        int top = Math.round(clamp(rect.top, margin, Math.max(margin, overlayHeight - height - margin)));
        return new Rect(left, top, left + width, top + height);
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
                if (!startTaskFromRecents(taskId)) {
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
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setPadding(0, 0, 0, 0);
        resetPreviewBackground();
        setPreviewCornerRadius(dp(PREVIEW_CORNER_RADIUS_DP));
    }

    private boolean startTaskFromRecents(int taskId) {
        return startTaskFromRecents(
                taskId,
                buildNoAnimationTaskOptions(),
                "Failed to start mBack star task from recents: ");
    }

    private boolean startTaskFromRecentsInSmallWindow(MBackStarApp app) {
        if (app == null || app.taskId < 0) {
            return false;
        }
        markFlymeStartWindowMode(app.packageName);
        return startTaskFromRecents(
                app.taskId,
                buildSmallWindowTaskOptions(),
                "Failed to start mBack star task in small window: ");
    }

    private boolean startLauncherApp(MBackStarApp app) {
        return startLauncherApp(
                app,
                buildNoAnimationTaskOptions(),
                "Failed to start mBack star launcher app: ");
    }

    private boolean startLauncherAppInSmallWindow(MBackStarApp app) {
        if (app == null) {
            return false;
        }
        markFlymeStartWindowMode(app.packageName);
        return startLauncherApp(
                app,
                buildSmallWindowTaskOptions(),
                "Failed to start mBack star launcher app in small window: ");
    }

    private boolean startLauncherApp(MBackStarApp app, Bundle options, String warningPrefix) {
        if (app == null || app.packageName.isEmpty() || app.activityName.isEmpty()) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.packageName, app.activityName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (options != null) {
                context.startActivity(intent, options);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    warningPrefix + app.packageName,
                    t);
            return false;
        }
    }

    private boolean startTaskFromRecents(int taskId, Bundle options, String warningPrefix) {
        try {
            Object service = resolveActivityTaskManagerService();
            Method method = resolveStartActivityFromRecentsMethod();
            if (options == null || service == null || method == null) {
                return false;
            }
            Object result = method.invoke(service, taskId, options);
            return !(result instanceof Number) || ((Number) result).intValue() >= 0;
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    warningPrefix + taskId,
                    t);
        }
        return false;
    }

    private Bundle buildNoAnimationTaskOptions() {
        try {
            Method method = resolveMakeCustomTaskAnimationMethod();
            if (method == null) {
                return null;
            }
            Object options = method.invoke(null, context, 0, 0, null, null, null);
            return options instanceof ActivityOptions ? ((ActivityOptions) options).toBundle() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Bundle buildSmallWindowTaskOptions() {
        try {
            Bundle options = buildNoAnimationTaskOptions();
            if (options == null) {
                options = ActivityOptions.makeBasic().toBundle();
            }
            options.putBoolean(START_WINDOW_MODE_BUNDLE_KEY, true);
            return options;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private FrameLayout buildPreviewContainer(
            Context context,
            ImageView imageView,
            TextView textView) {
        FrameLayout container = new FrameLayout(context);
        container.setVisibility(View.GONE);
        container.setClipToOutline(true);
        container.setElevation(dp(10)); // 降低硬高度以优化阴影表现
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 使用淡雅、通透的软阴影参数，取代固有生硬的纯黑硬投影
            container.setOutlineAmbientShadowColor(Color.argb(38, 0, 0, 0));
            container.setOutlineSpotShadowColor(Color.argb(58, 8, 10, 16));
        }
        previewCornerRadius = dp(PREVIEW_CORNER_RADIUS_DP);
        previewBackground = new GradientDrawable();
        previewBackground.setShape(GradientDrawable.RECTANGLE);
        previewBackground.setCornerRadius(previewCornerRadius);
        resetPreviewBackground();
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
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        container.addView(textView, textParams);
        return container;
    }

    private static ImageView buildPreviewImageView(Context context) {
        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(false);
        return imageView;
    }

    private static TextView buildPreviewTextView(Context context) {
        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Color.WHITE);
        textView.setSingleLine(true);
        textView.setShadowLayer(3f, 0f, 1f, Color.argb(120, 0, 0, 0));
        textView.setPadding(8, 8, 8, 8);
        textView.setVisibility(View.GONE);
        return textView;
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
        overlay.setBackgroundColor(getOverlayBackgroundColor(context));
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

    private static Object resolveActivityTaskManagerService() {
        try {
            Method method = resolveActivityTaskManagerGetServiceMethod();
            return method != null ? method.invoke(null) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveActivityTaskManagerGetServiceMethod() {
        if (!activityTaskManagerGetServiceMethodResolved) {
            try {
                Class<?> clazz = Class.forName("android.app.ActivityTaskManager");
                activityTaskManagerGetServiceMethod = clazz.getMethod("getService");
                activityTaskManagerGetServiceMethod.setAccessible(true);
            } catch (Throwable ignored) {
                activityTaskManagerGetServiceMethod = null;
            }
            activityTaskManagerGetServiceMethodResolved = true;
        }
        return activityTaskManagerGetServiceMethod;
    }

    private static Method resolveStartActivityFromRecentsMethod() {
        if (!startActivityFromRecentsMethodResolved) {
            try {
                Class<?> clazz = Class.forName("android.app.IActivityTaskManager");
                startActivityFromRecentsMethod = clazz.getMethod(
                        "startActivityFromRecents",
                        int.class,
                        Bundle.class);
                startActivityFromRecentsMethod.setAccessible(true);
            } catch (Throwable ignored) {
                startActivityFromRecentsMethod = null;
            }
            startActivityFromRecentsMethodResolved = true;
        }
        return startActivityFromRecentsMethod;
    }

    private static Method resolveMakeCustomTaskAnimationMethod() {
        if (!makeCustomTaskAnimationMethodResolved) {
            try {
                Method[] methods = ActivityOptions.class.getDeclaredMethods();
                for (Method method : methods) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if ("makeCustomTaskAnimation".equals(method.getName())
                            && parameterTypes.length == 6
                            && parameterTypes[0] == Context.class
                            && parameterTypes[1] == int.class
                            && parameterTypes[2] == int.class) {
                        method.setAccessible(true);
                        makeCustomTaskAnimationMethod = method;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                makeCustomTaskAnimationMethod = null;
            }
            makeCustomTaskAnimationMethodResolved = true;
        }
        return makeCustomTaskAnimationMethod;
    }

    private static Object resolveWindowManagerExtInstance(Context context) {
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
            if (overlayView.getDisplay() != null) {
                return overlayView.getDisplay().getDisplayId();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void markFlymeStartWindowMode(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return;
        }
        try {
            Object windowManagerExt = resolveWindowManagerExtInstance(context);
            Method method = resolveSetStartWindowModeMethod();
            if (windowManagerExt != null && method != null) {
                method.invoke(windowManagerExt, packageName);
            }
        } catch (Throwable t) {
            FlymeStatusBarSizer.logMBackWarning(
                    "Failed to mark Flyme small window start for mBack star app: "
                            + packageName,
                    t);
        }
    }

    private static Method resolveSetStartWindowModeMethod() {
        if (!setStartWindowModeMethodResolved) {
            try {
                Class<?> clazz = Class.forName("flyme.view.WindowManagerExt");
                setStartWindowModeMethod = clazz.getMethod("setStartWindowMode", String.class);
                setStartWindowModeMethod.setAccessible(true);
            } catch (Throwable ignored) {
                setStartWindowModeMethod = null;
            }
            setStartWindowModeMethodResolved = true;
        }
        return setStartWindowModeMethod;
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

    private void applySmallWindowIconPadding() {
        if (previewContainer == null || previewImageView == null) {
            return;
        }
        int width = previewContainer.getWidth();
        int height = previewContainer.getHeight();
        if (width <= 0 || height <= 0) {
            ViewGroup.LayoutParams params = previewContainer.getLayoutParams();
            if (params != null) {
                width = params.width;
                height = params.height;
            }
        }
        int minSide = Math.max(1, Math.min(width, height));
        int padding = Math.max(0, Math.round(minSide * (1f - SMALL_WINDOW_ICON_SCALE) / 2f));
        previewImageView.setPadding(padding, padding, padding, padding);
    }

    private void setPreviewTransitionBackground() {
        if (previewBackground == null) {
            return;
        }
        previewBackground.setColor(isNightMode() ? Color.BLACK : Color.WHITE);
        previewBackground.setStroke(0, Color.TRANSPARENT);
    }

    private void resetPreviewBackground() {
        if (previewBackground == null) {
            return;
        }
        // 根据日夜模式微调填充色，使之呈半透且带有呼吸感
        int bgColor = isNightMode() 
                ? Color.argb(205, 20, 22, 30) 
                : Color.argb(220, 255, 255, 255);
        previewBackground.setColor(bgColor);
        
        // 引入 1dp 的极其纤细、高雅半透明微光边框，在磨砂玻璃背景下完美浮现
        int strokeColor = isNightMode() 
                ? Color.argb(40, 255, 255, 255) 
                : Color.argb(48, 255, 255, 255);
        previewBackground.setStroke(Math.max(1, dp(1)), strokeColor);
    }

    private boolean isNightMode() {
        try {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String resolveAppBucket(MBackStarApp app) {
        if (app == null) {
            return "#";
        }
        String label = app.label != null ? app.label.toString().trim() : "";
        char directLetter = findFirstAsciiLetter(label);
        if (directLetter != 0) {
            return String.valueOf(directLetter);
        }
        String pinyin = transliterateToPinyin(label);
        char pinyinLetter = findFirstAsciiLetter(pinyin);
        if (pinyinLetter != 0) {
            return String.valueOf(pinyinLetter);
        }
        String packageName = app.packageName != null ? app.packageName.trim() : "";
        char packageLetter = findFirstAsciiLetter(packageName);
        return packageLetter != 0 ? String.valueOf(packageLetter) : "#";
    }

    private static char findFirstAsciiLetter(String value) {
        if (value == null) {
            return 0;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                return ch;
            }
            if (ch >= 'a' && ch <= 'z') {
                return Character.toUpperCase(ch);
            }
            if (Character.isLetterOrDigit(ch)) {
                return 0;
            }
        }
        return 0;
    }

    private static String transliterateToPinyin(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        Transliterator transliterator = resolvePinyinTransliterator();
        if (transliterator == null) {
            return "";
        }
        try {
            return transliterator.transliterate(value).toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Transliterator resolvePinyinTransliterator() {
        if (!pinyinTransliteratorResolved) {
            try {
                pinyinTransliterator = Transliterator.getInstance("Han-Latin/Names; Latin-ASCII");
            } catch (Throwable ignored) {
                pinyinTransliterator = null;
            }
            pinyinTransliteratorResolved = true;
        }
        return pinyinTransliterator;
    }

    private static int getOverlayBackgroundColor(Context context) {
        boolean nightMode = false;
        try {
            nightMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return nightMode
                    ? Color.argb(90, 10, 10, 15)
                    : Color.argb(45, 0, 0, 0);
        } else {
            return nightMode
                    ? Color.argb(190, 8, 8, 12)
                    : Color.argb(135, 0, 0, 0);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    private static final class HoverTimerView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcBounds = new RectF();
        private float progress;
        private Shader gradientShader;
        private final float density;

        HoverTimerView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // 启用软件渲染以完美展示霓虹阴影
            density = context.getResources().getDisplayMetrics().density;
            
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(1.2f * density);
            trackPaint.setColor(Color.argb(38, 255, 255, 255)); // 更内敛高透的白色轨道
            
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(2.6f * density); // 略微加粗使极光色彩更饱和
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            
            // 为圆弧进度设置耀眼的极光蓝外发光光晕
            progressPaint.setShadowLayer(
                    4.2f * density, 
                    0f, 
                    0f, 
                    Color.parseColor("#00F2FE")
            );
        }

        void setProgress(float progress) {
            this.progress = Math.max(0f, Math.min(1f, progress));
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) {
                float cx = w / 2f;
                float cy = h / 2f;
                int[] colors = new int[] {
                    Color.parseColor("#00F2FE"), // 天蓝
                    Color.parseColor("#4FACFE"), // 耀蓝
                    Color.parseColor("#8EC5FC"), // 柔蓝
                    Color.parseColor("#E0C3FC"), // 优雅紫
                    Color.parseColor("#00F2FE")  // 天蓝（闭环）
                };
                Shader sweep = new SweepGradient(cx, cy, colors, null);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(-90f, cx, cy); // 旋转 90 度使渐变起点完美对齐 12 点钟的进度起点
                sweep.setLocalMatrix(matrix);
                gradientShader = sweep;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = progressPaint.getStrokeWidth();
            // 考虑阴影边缘所需的外边距，保证发光不会被容器剪切
            float margin = stroke + 2.0f * density;
            arcBounds.set(
                    margin,
                    margin,
                    getWidth() - margin,
                    getHeight() - margin);
            
            canvas.drawOval(arcBounds, trackPaint);
            
            if (progress > 0.01f) {
                progressPaint.setShader(gradientShader);
                canvas.drawArc(arcBounds, -90f, 360f * progress, false, progressPaint);
            }
        }
    }

    private static final class IconHolder {
        final MBackStarApp app;
        final String letter;
        final boolean enabled;
        final View root;
        final HoverTimerView timerView;
        final float depth;

        IconHolder(
                MBackStarApp app,
                String letter,
                boolean enabled,
                View root,
                HoverTimerView timerView,
                float depth) {
            this.app = app;
            this.letter = letter;
            this.enabled = enabled;
            this.root = root;
            this.timerView = timerView;
            this.depth = depth;
        }

        boolean isLetter() {
            return letter != null;
        }
    }
}
