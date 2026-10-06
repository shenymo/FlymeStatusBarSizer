package com.example.flymestatusbarsizer.feature.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Suppresses only the cutout fill, preserving display geometry and other screen decorations. */
public final class CameraCutoutHooks {
    private static final String TAG = "CameraCutout";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Boolean> VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final FrontCameraState CAMERAS = new FrontCameraState();
    private static boolean monitoringAttempted;

    private CameraCutoutHooks() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.DisplayCutoutBaseView", false, loader);
            Method draw = type.getDeclaredMethod("drawCutouts", Canvas.class);
            Method attach = type.getDeclaredMethod("onAttachedToWindow");
            draw.setAccessible(true);
            attach.setAccessible(true);
            module.intercept(attach, chain -> {
                Object result = chain.proceed();
                track((View) chain.getThisObject());
                return result;
            });
            // ScreenDecorHwcLayer also calls this method. Skipping onDraw itself would erase
            // rounded corners and break the HWC layer's inverted-alpha background.
            module.intercept(draw, chain -> {
                View view = (View) chain.getThisObject();
                track(view);
                ModuleConfig config = ModuleConfig.load(view.getContext());
                if (isMainDisplay(view) && CAMERAS.shouldHide(config.enabled, config.hideIdleCameraCutout)) {
                    return null;
                }
                return chain.proceed();
            });
            deoptimizeDraw(module, type);
            try {
                deoptimizeDraw(module, Class.forName("com.android.systemui.ScreenDecorHwcLayer", false, loader));
            } catch (ClassNotFoundException ignored) {
                // Older SystemUI builds may only provide the ordinary View path.
            }
        } catch (Throwable error) {
            Log.w(TAG, "Idle camera cutout hiding unavailable", error);
        }
    }

    private static void deoptimizeDraw(FlymeStatusBarSizer module, Class<?> type) {
        try {
            // The small drawCutouts method can be inlined into these callers by ART.
            module.deoptimize(type.getDeclaredMethod("onDraw", Canvas.class));
        } catch (Throwable error) {
            Log.w(TAG, "Cannot deoptimize cutout caller: " + type.getName(), error);
        }
    }

    private static void track(View view) {
        if (isMainDisplay(view) && VIEWS.put(view, Boolean.TRUE) == null) {
            Context context = view.getContext();
            MAIN.post(() -> startMonitoring(context));
        }
    }

    private static boolean isMainDisplay(View view) {
        Display display = view.getDisplay();
        return display != null && display.getDisplayId() == Display.DEFAULT_DISPLAY;
    }

    private static void startMonitoring(Context context) {
        FlymeStatusBarSizer.ensureConfigRefreshObserver(context);
        if (monitoringAttempted) return;
        monitoringAttempted = true;
        try {
            CameraManager manager = context.getSystemService(CameraManager.class);
            if (manager == null) return;
            ArrayList<String> frontIds = new ArrayList<>();
            for (String id : manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing == null) throw new IllegalStateException("Unknown camera facing: " + id);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) frontIds.add(id);
            }
            CAMERAS.reset(frontIds);
            manager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
                @Override public void onCameraAvailable(String cameraId) {
                    updateCamera(cameraId, true);
                }

                @Override public void onCameraUnavailable(String cameraId) {
                    updateCamera(cameraId, false);
                }

                // System APIs absent from the public compile SDK. The extracted SystemUI uses
                // these callbacks and holds CAMERA_OPEN_CLOSE_LISTENER. Do not exclude face unlock.
                public void onCameraOpened(String cameraId, String packageId) {
                    updateCamera(cameraId, false);
                }

                public void onCameraClosed(String cameraId) {
                    updateCamera(cameraId, true);
                }
            }, MAIN);
            // Registration replays current availability, including a camera already in use
            // when SystemUI restarts. Until those callbacks arrive, keep the native fill.
        } catch (Throwable error) {
            CAMERAS.reset(Collections.emptyList());
            Log.w(TAG, "Camera state unavailable; retaining native cutout", error);
        }
    }

    private static void updateCamera(String id, boolean idle) {
        if (CAMERAS.update(id, idle)) refresh();
    }

    public static void refresh() {
        ArrayList<View> views;
        synchronized (VIEWS) {
            views = new ArrayList<>(VIEWS.keySet());
        }
        for (View view : views) {
            // Decorations use their own Looper; View routes invalidation to the owning thread.
            view.postInvalidate();
        }
    }
}
