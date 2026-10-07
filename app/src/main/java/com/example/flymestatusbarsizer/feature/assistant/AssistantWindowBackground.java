package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.example.flymestatusbarsizer.config.ModuleConfig;

/** A compositor background follows the content and belongs only to the global window. */
final class AssistantWindowBackground implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener {
    private final View content;
    private final Drawable original;
    private final ColorDrawable overlay;
    private final int blurRadius;
    private AssistantBlurSurface nativeBlur;
    private AssistantImageSurface imageSurface;
    private Bitmap image;
    private final String imageId;
    private final boolean blurEnabled;
    private final int[] contentLocation = new int[2];
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver observer;
    private boolean active;
    private boolean failed;

    AssistantWindowBackground(View content, float density) {
        this(content, density, new ModuleConfig());
    }

    AssistantWindowBackground(View content, float density, ModuleConfig config) {
        this.content = content;
        blurEnabled = config.assistantBackgroundBlur;
        imageId = config.assistantBackgroundCustom ? config.assistantBackgroundImage : "";
        original = content.getBackground();
        // View.setBackgroundColor mutates an existing ColorDrawable, including the saved original.
        // A fresh drawable also avoids changing any shared ConstantState used by the native window.
        overlay = new ColorDrawable(Color.TRANSPARENT);
        blurRadius = Math.max(1, Math.round(40 * density));
    }

    void apply() {
        if (active) return;
        active = true;
        failed = false;
        content.setBackground(overlay);
        // Keep the window itself free of blur regions: those also blur the SurfaceView
        // cards below its buffer. The separate effect surface is below the cards instead.
        content.addOnAttachStateChangeListener(this);
        if (content.isAttachedToWindow()) onViewAttachedToWindow(content);
        if (AssistantBackgroundImages.validId(imageId)) {
            AssistantBackgroundImages.WORKER.execute(() -> {
                try {
                    Bitmap loaded = AssistantBackgroundImages.load(content.getContext(), imageId, blurEnabled);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!active) return;
                        image = loaded;
                        failed = false;
                        content.invalidate();
                    });
                } catch (Exception | OutOfMemoryError error) {
                    AssistantHooks.warn("Cannot load assistant background; using application background", error);
                }
            });
        }
    }

    void updateAttributes(WindowManager.LayoutParams attrs) {
        attrs.format = PixelFormat.TRANSLUCENT;
        attrs.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Only our separate effect surface owns the blur, never the containing window.
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attrs.setBlurBehindRadius(0);
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (!active) return;
        removePreDrawListener();
        observer = content.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        failed = false;
        onPreDraw();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        removePreDrawListener();
        releaseNativeBlur();
        releaseImageSurface();
        if (active) content.setBackground(overlay);
    }

    @Override public boolean onPreDraw() {
        if (active && !failed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                float alpha = content.getAlpha();
                if (content.getWindowVisibility() != View.VISIBLE
                        || content.getVisibility() != View.VISIBLE || !content.getGlobalVisibleRect(visibleBounds)) {
                    alpha = 0;
                    visibleBounds.setEmpty();
                } else {
                    for (ViewParent parent = content.getParent(); parent instanceof View; parent = parent.getParent()) {
                        if (((View) parent).getVisibility() != View.VISIBLE) { alpha = 0; break; }
                        alpha *= ((View) parent).getAlpha();
                    }
                }
                alpha = Math.max(0, Math.min(1, alpha));
                if (image != null) {
                    if (imageSurface != null && !imageSurface.isValid(content)) releaseImageSurface();
                    if (imageSurface == null) imageSurface = AssistantImageSurface.create(content, image);
                    if (imageSurface != null) {
                        releaseNativeBlur();
                        content.getLocationOnScreen(contentLocation);
                        imageSurface.update(visibleBounds, contentLocation[0], contentLocation[1], alpha,
                                content.getRootSurfaceControl());
                    }
                } else if (blurEnabled) {
                    if (nativeBlur != null && !nativeBlur.isValid()) releaseNativeBlur();
                    if (nativeBlur == null) nativeBlur = AssistantBlurSurface.create(content, blurRadius);
                    if (nativeBlur != null) nativeBlur.update(visibleBounds, alpha, content.getRootSurfaceControl());
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                // An unsupported image surface falls back to the selected application blur mode.
                boolean imageFailed = image != null;
                failed = !imageFailed;
                image = null;
                releaseImageSurface();
                releaseNativeBlur();
                AssistantHooks.warn("Cannot update assistant background surface", error);
            }
        }
        return true;
    }

    private void removePreDrawListener() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    private void releaseNativeBlur() {
        if (nativeBlur == null) return;
        AssistantBlurSurface old = nativeBlur;
        nativeBlur = null;
        try { old.release(); }
        catch (RuntimeException error) { AssistantHooks.warn("Cannot release assistant blur surface", error); }
    }

    private void releaseImageSurface() {
        if (imageSurface == null) return;
        AssistantImageSurface old = imageSurface;
        imageSurface = null;
        try { old.release(); }
        catch (RuntimeException error) { AssistantHooks.warn("Cannot release assistant image surface", error); }
    }

    void restore() {
        active = false;
        content.removeOnAttachStateChangeListener(this);
        removePreDrawListener();
        releaseNativeBlur();
        releaseImageSurface();
        image = null;
        content.setBackground(original);
    }
}
