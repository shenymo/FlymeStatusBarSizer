package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Binder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Draws the configured gesture zones from the actual pointer-down position. */
public final class AssistantGestureZoneOverlay {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:GestureZones";
    private final Context context;
    private final WindowManager windowManager;
    private final ZoneView view;
    private boolean attached;
    private boolean attempted;

    public AssistantGestureZoneOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new ZoneView(context);
    }

    void show(float startX, float startY, boolean fromLeft, float shortDistancePx,
            float longDistancePx, float farDistancePx,
            float upAngle, float downAngle, float minVertical, ModuleConfig config) {
        view.setSpec(startX, startY, fromLeft, shortDistancePx, longDistancePx, farDistancePx,
                upAngle, downAngle, minVertical, false);
        show(config);
    }

    public void showCorner(float startX, float startY, boolean fromLeft, ModuleConfig config) {
        float density = context.getResources().getDisplayMetrics().density;
        view.setSpec(startX, startY, fromLeft, config.cornerGestureDistanceDp * density,
                config.cornerGestureSplitEnabled
                        ? config.cornerGestureLongDistanceDp * density : Float.POSITIVE_INFINITY,
                config.cornerGestureFarEnabled
                        ? config.cornerGestureFarDistanceDp * density : Float.POSITIVE_INFINITY,
                0f, 0f, 0f, true);
        show(config);
    }

    private void show(ModuleConfig config) {
        view.setAppearance(config);
        if (!attached && !attempted) {
            attempted = true;
            attach();
        }
        if (attached) {
            view.fadeIn();
        }
    }

    void hide() {
        if (attached) {
            view.fadeOut(null);
        } else {
            attempted = false;
        }
    }

    public void close() {
        if (!attached) return;
        view.fadeOut(() -> {
            windowManager.removeViewImmediate(view);
            attached = false;
            attempted = false;
        });
    }

    public void updatePointer(float x, float y) {
        view.setPointer(x, y);
    }

    private void attach() {
        if (windowManager == null) return;
        Throwable last = null;
        for (int type : WINDOW_TYPES) {
            WindowManager.LayoutParams params = buildLayoutParams(type);
            try {
                view.setVisibility(View.INVISIBLE);
                windowManager.addView(view, params);
                attached = true;
                return;
            } catch (Throwable error) {
                last = error;
                try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) { }
            }
        }
        AssistantHooks.warn("Cannot attach gesture zone overlay", last);
    }

    private WindowManager.LayoutParams buildLayoutParams(int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.token = new Binder();
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle(TITLE);
        params.packageName = context.getPackageName();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        applyTrustedOverlay(params);
        return params;
    }

    private static void applyTrustedOverlay(WindowManager.LayoutParams params) {
        try {
            Method method = WindowManager.LayoutParams.class.getMethod("setTrustedOverlay");
            method.invoke(params);
        } catch (Throwable ignored) {
        }
        try {
            Field field = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            field.setAccessible(true);
            field.setInt(params, field.getInt(params) | 0x01000000);
        } catch (Throwable ignored) {
        }
    }

    private final class ZoneView extends View {
        private float fadeInDurationMs = SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_FADE_IN_MS;
        private float fadeOutDurationMs = SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_FADE_OUT_MS;
        private int revealRadiusDp = SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_RADIUS_DP;
        private int corePercent = SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_CORE_PERCENT;
        private int fillOpacityPercent =
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_FILL_OPACITY_PERCENT;
        private int strokeOpacityPercent =
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_STROKE_OPACITY_PERCENT;
        private final Paint shortFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint farFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint farStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shortStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint longStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint[] revealPaints = {
                shortFill, longFill, farFill, shortStroke, longStroke, farStroke
        };
        private final Matrix revealMatrix = new Matrix();
        private final float density;
        private float startX;
        private float startY;
        private float pointerX;
        private float pointerY;
        private float shortDistance;
        private float longDistance;
        private float farDistance;
        private AssistantGestureGeometry geometry;
        private boolean fromLeft;
        private boolean cornerMode;

        ZoneView(Context context) {
            super(context);
            setAlpha(0f);
            density = getResources().getDisplayMetrics().density;
            shortFill.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_SHORT_COLOR);
            longFill.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_LONG_COLOR);
            farFill.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_FAR_COLOR);
            shortStroke.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_SHORT_STROKE_COLOR);
            longStroke.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_LONG_STROKE_COLOR);
            farStroke.setColor(SettingsStore.DEFAULT_ASSISTANT_GESTURE_ZONE_FAR_STROKE_COLOR);
            shortStroke.setStyle(Paint.Style.STROKE);
            longStroke.setStyle(Paint.Style.STROKE);
            farStroke.setStyle(Paint.Style.STROKE);
            setAppearanceColors();
        }

        void fadeIn() {
            animate().cancel();
            setAlpha(0f);
            setVisibility(View.VISIBLE);
            animate().alpha(1f).setDuration((long) fadeInDurationMs)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        void fadeOut(Runnable onEnd) {
            if (getVisibility() != View.VISIBLE) {
                if (onEnd != null) onEnd.run();
                return;
            }
            animate().cancel();
            animate().alpha(0f).setDuration((long) fadeOutDurationMs)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        setVisibility(View.INVISIBLE);
                        if (onEnd != null) onEnd.run();
                    }).start();
        }

        void setSpec(float startX, float startY, boolean fromLeft, float shortDistance,
                float longDistance, float farDistance,
                float upAngle, float downAngle, float minVertical, boolean cornerMode) {
            this.startX = startX;
            this.startY = startY;
            this.fromLeft = fromLeft;
            this.shortDistance = shortDistance;
            this.longDistance = longDistance;
            this.farDistance = farDistance;
            this.cornerMode = cornerMode;
            geometry = cornerMode ? null
                    : new AssistantGestureGeometry(shortDistance, upAngle, downAngle, minVertical);
            setPointer(startX, startY);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float availableX = fromLeft ? getWidth() - startX : startX;
            if (availableX <= 0f) return;
            int direction = fromLeft ? 1 : -1;
            float upperY = startY;
            float lowerY = getHeight() - startY;
            canvas.save();
            float revealRadius = dp(revealRadiusDp);
            canvas.clipRect(pointerX - revealRadius, pointerY - revealRadius,
                    pointerX + revealRadius, pointerY + revealRadius);
            float[] distances = {shortDistance, longDistance, farDistance};
            Paint[] fills = {shortFill, longFill, farFill};
            Paint[] strokes = {shortStroke, longStroke, farStroke};
            int count = Float.isFinite(farDistance) ? 3 : Float.isFinite(longDistance) ? 2 : 1;
            Matrix transform = new Matrix();
            transform.setScale(direction, 1f);
            transform.postTranslate(startX, startY);
            for (int tier = 0; tier < count; tier++) {
                float outer = tier + 1 < count ? distances[tier + 1] : Float.POSITIVE_INFINITY;
                for (int zone = 0; zone < (cornerMode ? 1 : 3); zone++) {
                    Path path;
                    if (cornerMode) {
                        path = new Path();
                        path.addRect(0f, -upperY, availableX, lowerY, Path.Direction.CW);
                        Path circle = new Path();
                        circle.addCircle(0f, 0f, distances[tier], Path.Direction.CW);
                        path.op(circle, Path.Op.DIFFERENCE);
                        if (Float.isFinite(outer)) {
                            circle.reset();
                            circle.addCircle(0f, 0f, outer, Path.Direction.CW);
                            path.op(circle, Path.Op.INTERSECT);
                        }
                    } else {
                        path = geometry.region(zone, distances[tier], outer,
                                availableX, upperY, lowerY);
                    }
                    path.transform(transform);
                    canvas.drawPath(path, fills[tier]);
                    canvas.drawPath(path, strokes[tier]);
                }
            }
            canvas.restore();
        }

        private float dp(float value) {
            return value * density;
        }

        void setPointer(float x, float y) {
            pointerX = x;
            pointerY = y;
            revealMatrix.setTranslate(x, y);
            for (Paint paint : revealPaints) {
                paint.getShader().setLocalMatrix(revealMatrix);
            }
            postInvalidateOnAnimation();
        }

        void setAppearance(ModuleConfig config) {
            revealRadiusDp = config.assistantGestureZoneRadiusDp;
            corePercent = config.assistantGestureZoneCorePercent;
            fillOpacityPercent = config.assistantGestureZoneFillOpacityPercent;
            strokeOpacityPercent = config.assistantGestureZoneStrokeOpacityPercent;
            fadeInDurationMs = config.assistantGestureZoneFadeInMs;
            fadeOutDurationMs = config.assistantGestureZoneFadeOutMs;
            shortStroke.setStrokeWidth(dp(config.assistantGestureZoneStrokeWidthTenthDp / 10f));
            longStroke.setStrokeWidth(dp(config.assistantGestureZoneStrokeWidthTenthDp / 10f));
            farStroke.setStrokeWidth(dp(config.assistantGestureZoneStrokeWidthTenthDp / 10f));
            shortFill.setColor(config.assistantGestureZoneShortColor);
            longFill.setColor(config.assistantGestureZoneLongColor);
            farFill.setColor(config.assistantGestureZoneFarColor);
            shortStroke.setColor(config.assistantGestureZoneShortStrokeColor);
            longStroke.setColor(config.assistantGestureZoneLongStrokeColor);
            farStroke.setColor(config.assistantGestureZoneFarStrokeColor);
            setAppearanceColors();
            setPointer(pointerX, pointerY);
        }

        private void setAppearanceColors() {
            for (int i = 0; i < revealPaints.length; i++) {
                Paint paint = revealPaints[i];
                int color = paint.getColor();
                int opacity = i < 3 ? fillOpacityPercent : strokeOpacityPercent;
                int solid = (Math.round(opacity * 255f / 100f) << 24) | (color & 0x00ffffff);
                float edgeRadius = dp(revealRadiusDp);
                float coreStop = Math.max(0.001f, corePercent / 100f);
                paint.setShader(new RadialGradient(0f, 0f, edgeRadius,
                        new int[]{solid, solid, color & 0x00ffffff},
                        new float[]{0f, coreStop, 1f}, Shader.TileMode.CLAMP));
            }
        }
    }
}
