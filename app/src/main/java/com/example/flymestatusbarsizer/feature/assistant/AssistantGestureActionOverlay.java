package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class AssistantGestureActionOverlay {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:GestureAction";
    private final Context context;
    private final WindowManager windowManager;
    private final ActionView view;
    private boolean attached;
    private boolean attempted;

    AssistantGestureActionOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new ActionView(context);
    }

    void show(String text, float anchorX, float anchorY) {
        view.setText(text);
        view.setAnchor(anchorX, anchorY);
        if (!attached && !attempted) {
            attempted = true;
            attach();
        }
        if (attached) view.invalidate();
    }

    void hide() {
        if (attached) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (Throwable ignored) {
            }
        }
        attached = false;
        attempted = false;
    }

    private void attach() {
        if (windowManager == null) return;
        Throwable last = null;
        for (int type : WINDOW_TYPES) {
            WindowManager.LayoutParams params = buildLayoutParams(type);
            try {
                windowManager.addView(view, params);
                attached = true;
                return;
            } catch (Throwable error) {
                last = error;
                try { windowManager.removeViewImmediate(view); } catch (Throwable ignored) { }
            }
        }
        AssistantHooks.warn("Cannot attach gesture action overlay", last);
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

    private int statusBarHeight() {
        int resource = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resource == 0 ? dp(24) : context.getResources().getDimensionPixelSize(resource);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class ActionView extends View {
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect textBounds = new Rect();
        private final float density;
        private final float scaledDensity;
        private final int statusBarHeight;
        private String text = "";
        private float anchorX;
        private float anchorY;

        ActionView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            statusBarHeight = AssistantGestureActionOverlay.this.statusBarHeight();
            background.setColor(0xe6222222);
            outline.setColor(0x55ffffff);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(1));
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(15f * scaledDensity);
            textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        }

        void setText(String text) {
            this.text = text == null ? "" : text;
        }

        void setAnchor(float x, float y) {
            anchorX = x;
            anchorY = y;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (text.length() == 0 || getWidth() <= 0) return;
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            float horizontalPadding = dp(18);
            float height = dp(40);
            float width = textBounds.width() + horizontalPadding * 2;
            float left = Math.max(dp(8), Math.min(getWidth() - width - dp(8), anchorX - width / 2f));
            float top = Math.max(statusBarHeight + dp(4),
                    Math.min(getHeight() - height - dp(8), anchorY - height / 2f));
            float right = left + width;
            float bottom = top + height;
            canvas.drawRoundRect(left, top, right, bottom, dp(20), dp(20), background);
            canvas.drawRoundRect(left, top, right, bottom, dp(20), dp(20), outline);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = top + (height - metrics.bottom - metrics.top) / 2f;
            canvas.drawText(text, left + (width - textPaint.measureText(text)) / 2f, baseline, textPaint);
        }

        private float dp(float value) { return value * density; }
    }
}
