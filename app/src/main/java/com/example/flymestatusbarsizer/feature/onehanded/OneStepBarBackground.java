package com.example.flymestatusbarsizer.feature.onehanded;

import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceControl;

import com.example.flymestatusbarsizer.util.ReflectUtils;

/** A small, task-only backdrop for the status bar; owned on the Shell executor. */
final class OneStepBarBackground {
    final Rect bounds;
    private final int color;
    private HardwareBuffer buffer;
    private ColorSpace colorSpace;
    private int references = 1;

    private OneStepBarBackground(Rect bounds, int color) {
        this.bounds = bounds;
        this.color = color;
    }

    static OneStepBarBackground capture(Object info, SurfaceControl source, Rect taskBounds, Rect viewport) {
        Rect strip = new Rect(taskBounds.left, taskBounds.top, taskBounds.right,
                Math.min(taskBounds.bottom, viewport.top));
        if (strip.isEmpty()) return null;
        int color = Color.TRANSPARENT;
        Object description = ReflectUtils.getField(info, "taskDescription");
        if (description != null) {
            color = ReflectUtils.invokeNoArgInt(description, "getStatusBarColor", Color.TRANSPARENT);
            if (Color.alpha(color) == 0)
                color = ReflectUtils.invokeNoArgInt(description, "getBackgroundColor", Color.TRANSPARENT);
        }
        int systemColor = OneStepStatusBar.nativeBackgroundColor();
        if (Color.alpha(systemColor) == 255) color = systemColor;
        OneStepBarBackground result = new OneStepBarBackground(strip, color);
        if (Color.alpha(systemColor) == 255 || source == null || !source.isValid()
                || !ReflectUtils.getBooleanField(info, "isVisible", true)) return result;
        try {
            // Capture only the app's pixels, never SystemUI's clock/icons. Do not
            // request protected or secure content; use the task color if unavailable.
            String api = Build.VERSION.SDK_INT >= 34 ? "android.window.ScreenCapture" : "android.view.SurfaceControl";
            Class<?> capture = Class.forName(api);
            Class<?> args = Class.forName(api + "$LayerCaptureArgs");
            Object builder = Class.forName(api + "$LayerCaptureArgs$Builder")
                    .getConstructor(SurfaceControl.class).newInstance(source);
            Rect crop = new Rect(strip);
            crop.offset(-taskBounds.left, -taskBounds.top);
            OneStepReflection.call(builder, "setSourceCrop", new Class<?>[]{Rect.class}, crop);
            Object screenshot = OneStepReflection.method(capture, "captureLayers", args)
                    .invoke(null, OneStepReflection.call(builder, "build"));
            if (screenshot != null) {
                result.buffer = (HardwareBuffer) OneStepReflection.call(screenshot, "getHardwareBuffer");
                if (Boolean.TRUE.equals(OneStepReflection.call(screenshot, "containsSecureLayers"))) {
                    if (result.buffer != null) result.buffer.close();
                    result.buffer = null;
                } else result.colorSpace = (ColorSpace) OneStepReflection.call(screenshot, "getColorSpace");
            }
        } catch (Exception error) {
            if (result.buffer != null) result.buffer.close();
            result.buffer = null;
            Log.w(OneHandedTaskHooks.TAG, "Using task color for workspace status bar backdrop", error);
        }
        return result;
    }

    OneStepBarBackground retain() { references++; return this; }

    SurfaceControl createLayer(SurfaceControl parent, SurfaceControl.Transaction tx) throws Exception {
        SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setName("OneStep status bar backdrop").setParent(parent).setFormat(PixelFormat.TRANSLUCENT);
        OneStepReflection.call(builder, buffer == null ? "setColorLayer" : "setBLASTLayer");
        SurfaceControl layer = builder.build();
        try {
            if (buffer == null) {
                OneStepReflection.call(tx, "setColor", new Class<?>[]{SurfaceControl.class, float[].class}, layer,
                        new float[]{Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f});
            } else {
                OneStepReflection.call(tx, "setBuffer", new Class<?>[]{SurfaceControl.class, HardwareBuffer.class}, layer, buffer);
                if (colorSpace != null) OneStepReflection.call(tx, "setColorSpace",
                        new Class<?>[]{SurfaceControl.class, ColorSpace.class}, layer, colorSpace);
                tx.setScale(layer, bounds.width() / (float) buffer.getWidth(), bounds.height() / (float) buffer.getHeight());
            }
            OneStepReflection.call(tx, "setWindowCrop", new Class<?>[]{SurfaceControl.class, Rect.class}, layer,
                    new Rect(0, 0, buffer == null ? bounds.width() : buffer.getWidth(),
                            buffer == null ? bounds.height() : buffer.getHeight()));
            tx.setLayer(layer, Integer.MAX_VALUE).setPosition(layer, bounds.left, bounds.top);
            return layer;
        } catch (Exception error) {
            tx.reparent(layer, null);
            layer.release();
            throw error;
        }
    }

    void place(SurfaceControl.Transaction tx, SurfaceControl layer, float progress, boolean visible) {
        float alpha = 1f - Math.max(0f, Math.min(1f, progress));
        if (buffer == null) alpha *= Color.alpha(color) / 255f;
        tx.setAlpha(layer, alpha).setVisibility(layer, visible);
    }

    void release() {
        if (--references == 0 && buffer != null) { buffer.close(); buffer = null; }
    }
}
