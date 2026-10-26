package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

/** Keeps a compositor blur aligned with a translucent side gesture panel. */
public final class AssistantPanelBlur implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener {
    private final View content;
    private final int radius;
    private final float cornerRadius;
    private final Rect visibleBounds = new Rect();
    private AssistantBlurSurface blur;
    private ViewTreeObserver observer;
    private boolean active;
    private boolean failed;

    public AssistantPanelBlur(View content, float density) {
        this.content = content;
        radius = Math.max(1, Math.round(32f * density));
        cornerRadius = Math.max(1f, 12f * density);
    }

    public void start() {
        if (active) return;
        active = true;
        failed = false;
        content.addOnAttachStateChangeListener(this);
        if (content.isAttachedToWindow()) onViewAttachedToWindow(content);
    }

    public void stop() {
        active = false;
        content.removeOnAttachStateChangeListener(this);
        removePreDrawListener();
        releaseBlur();
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        removePreDrawListener();
        observer = content.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        failed = false;
        onPreDraw();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        removePreDrawListener();
        releaseBlur();
    }

    @Override public boolean onPreDraw() {
        if (!active || failed || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        try {
            float alpha = content.getAlpha();
            if (content.getWindowVisibility() != View.VISIBLE
                    || content.getVisibility() != View.VISIBLE
                    || !content.getGlobalVisibleRect(visibleBounds)) {
                alpha = 0f;
                visibleBounds.setEmpty();
            } else {
                for (ViewParent parent = content.getParent(); parent instanceof View;
                        parent = parent.getParent()) {
                    View parentView = (View) parent;
                    if (parentView.getVisibility() != View.VISIBLE) {
                        alpha = 0f;
                        break;
                    }
                    alpha *= parentView.getAlpha();
                }
            }
            alpha = Math.max(0f, Math.min(1f, alpha));
            if (blur != null && !blur.isValid()) releaseBlur();
            if (blur == null) blur = AssistantBlurSurface.create(content, radius, cornerRadius);
            if (blur != null) {
                blur.update(visibleBounds, alpha, content.getRootSurfaceControl());
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            releaseBlur();
            AssistantHooks.warn("Cannot update side gesture panel blur", error);
        }
        return true;
    }

    private void removePreDrawListener() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    private void releaseBlur() {
        if (blur == null) return;
        AssistantBlurSurface old = blur;
        blur = null;
        try { old.release(); }
        catch (RuntimeException error) { AssistantHooks.warn("Cannot release side gesture panel blur", error); }
    }
}
