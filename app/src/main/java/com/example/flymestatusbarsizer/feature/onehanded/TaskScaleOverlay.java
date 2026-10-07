package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/** Two non-focusable windows cover only the top/left margins once the animation settles. */
final class TaskScaleOverlay implements TaskScaleController.Overlay {
    private final Context context;
    private final WindowManager manager;
    private final Runnable onExit;
    private final Mask[] masks = new Mask[2];
    private boolean animating;

    TaskScaleOverlay(Context source, Runnable onExit) {
        Display display = source.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        context = source.createDisplayContext(display).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        manager = context.getSystemService(WindowManager.class);
        this.onExit = onExit;
    }

    @Override public void show(TaskScaleTarget target, float scale, boolean animating) {
        this.animating = animating;
        WindowMetrics metrics = manager.getMaximumWindowMetrics();
        Rect usable = new Rect(metrics.getBounds());
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        usable.inset(bars.left, bars.top, bars.right, bars.bottom);
        Rect content = target.scaledBounds(scale);
        Rect top = new Rect(target.bounds);
        Rect left = new Rect();
        if (!animating) {
            top.bottom = content.top;
            left.set(target.bounds.left, content.top, content.left, target.bounds.bottom);
        }
        // During the short enter/exit animation input is blocked, but the app remains visible.
        intersect(top, usable);
        intersect(left, usable);
        update(0, top, animating ? content : null);
        update(1, left, null);
    }

    static void intersect(Rect rect, Rect usable) {
        if (!rect.intersect(usable)) rect.setEmpty();
    }

    private void update(int index, Rect bounds, Rect hole) {
        if (bounds.isEmpty()) { remove(index); return; }
        Mask view = masks[index];
        if (view == null) {
            view = new Mask(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    bounds.width(), bounds.height(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setFitInsetsTypes(0);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setTitle("FlymeBarSizer TaskScale margin " + index);
            params.packageName = context.getPackageName();
            params.x = bounds.left;
            params.y = bounds.top;
            view.setMask(bounds, hole);
            manager.addView(view, params);
            masks[index] = view;
        } else {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            if (params.x != bounds.left || params.y != bounds.top
                    || params.width != bounds.width() || params.height != bounds.height()) {
                params.x = bounds.left;
                params.y = bounds.top;
                params.width = bounds.width();
                params.height = bounds.height();
                manager.updateViewLayout(view, params);
            }
            view.setMask(bounds, hole);
        }
    }

    @Override public void hide() { remove(0); remove(1); }

    private void remove(int index) {
        Mask view = masks[index];
        if (view == null) return;
        try { manager.removeViewImmediate(view); }
        catch (RuntimeException e) { android.util.Log.w(OneHandedTaskHooks.TAG, "Remove margin window", e); }
        masks[index] = null;
    }

    private final class Mask extends View {
        final Paint paint = new Paint();
        final Rect hole = new Rect();
        final int slop;
        float downX, downY;
        boolean tap;

        Mask(Context context) {
            super(context);
            paint.setColor(Color.rgb(20, 22, 26));
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setContentDescription("退出应用单手缩放");
            setOnClickListener(v -> { if (!animating) onExit.run(); });
        }

        void setMask(Rect frame, Rect opening) {
            hole.setEmpty();
            if (opening != null) { hole.set(opening); hole.offset(-frame.left, -frame.top); }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            if (!hole.isEmpty()) canvas.clipOutRect(hole);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            canvas.restoreToCount(save);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tap = !animating;
                    downX = event.getX(); downY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) tap = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (tap && !animating) performClick();
                    tap = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                    tap = false;
                    break;
            }
            return true;
        }
    }
}
