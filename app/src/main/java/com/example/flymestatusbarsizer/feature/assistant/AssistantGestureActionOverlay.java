package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.ComponentName;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.config.SideGestureTargetStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AssistantGestureActionOverlay {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:GestureAction";
    private final Context context;
    private final WindowManager windowManager;
    private final ActionView view;
    private boolean attached;
    private boolean attempted;
    private Context moduleContext;

    public AssistantGestureActionOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new ActionView(context);
    }

    public void show(String text, float anchorX, float anchorY) {
        view.setText(text);
        view.setTextVisible(true);
        view.setAnchor(anchorX, anchorY);
        updateVisibility();
    }

    public void showIcon(int actionId, ModuleConfig config) {
        if (view.iconActionId != actionId) {
            view.iconActionId = actionId;
            view.icon = resolveIcon(actionId, config);
        }
        view.setIconVisible(true);
        updateVisibility();
    }

    public void hide() {
        view.setTextVisible(false);
        updateVisibility();
    }

    public void hideIcon() {
        view.setIconVisible(false);
        updateVisibility();
    }

    public void hideAll() {
        view.setTextVisible(false);
        view.setIconVisible(false);
        updateVisibility();
    }

    public void close() {
        if (attached) windowManager.removeViewImmediate(view);
        attached = false;
        attempted = false;
    }

    private void updateVisibility() {
        boolean visible = view.textVisible || view.iconVisible;
        if (visible && !attached && !attempted) {
            attempted = true;
            attach();
        }
        if (attached) {
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            if (visible) view.postInvalidateOnAnimation();
        } else if (!visible) {
            attempted = false;
        }
    }

    private Drawable resolveIcon(int actionId, ModuleConfig config) {
        Context resources = moduleContext;
        if (resources == null) {
            try {
                resources = context.createPackageContext(BuildConfig.APPLICATION_ID,
                        Context.CONTEXT_IGNORE_SECURITY);
                moduleContext = resources;
            } catch (PackageManager.NameNotFoundException ignored) {
                resources = context;
            }
        }
        String packageName = null;
        ComponentName component = null;
        SideGestureTargetStore.Target target = SideGestureTargetStore.isDynamicAction(actionId)
                ? SideGestureTargetStore.find(config.sideGestureTargetsJson, actionId) : null;
        int actionType = target == null ? actionId : target.type;
        Drawable fallback = resources.getDrawable(actionIconResource(actionType)).mutate();
        fallback.setTint(Color.WHITE);
        String value = target == null ? "" : target.value;
        if (actionType == SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP) {
            packageName = target == null ? config.sideGestureTargetApp : value;
        } else if (actionType == SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY) {
            component = ComponentName.unflattenFromString(
                    target == null ? config.sideGestureTargetActivity : value);
        }
        try {
            if (component != null) return context.getPackageManager().getActivityIcon(component);
            if (packageName != null && !packageName.isEmpty()) {
                return context.getPackageManager().getApplicationIcon(packageName);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return fallback;
    }

    private static int actionIconResource(int actionId) {
        switch (actionId) {
            case SettingsStore.SIDE_GESTURE_ACTION_BACK: return R.drawable.gesture_action_back;
            case SettingsStore.SIDE_GESTURE_ACTION_HOME: return R.drawable.gesture_action_home;
            case SettingsStore.SIDE_GESTURE_ACTION_RECENTS: return R.drawable.gesture_action_recents;
            case SettingsStore.SIDE_GESTURE_ACTION_LOCK_SCREEN: return R.drawable.gesture_action_lock;
            case SettingsStore.SIDE_GESTURE_ACTION_END_CURRENT_TASK: return R.drawable.gesture_action_end_task;
            case SettingsStore.SIDE_GESTURE_ACTION_WINDOWMODE_PANEL: return R.drawable.gesture_action_window_panel;
            case SettingsStore.SIDE_GESTURE_ACTION_WELT_WINDOW: return R.drawable.gesture_action_welt_window;
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SWITCHER: return R.drawable.gesture_action_task_switcher;
            case SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT: return R.drawable.gesture_action_global_assistant;
            case SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE: return R.drawable.gesture_action_task_scale;
            case SettingsStore.SIDE_GESTURE_ACTION_QUICK_TOOLS: return R.drawable.gesture_action_quick_tools;
            case SettingsStore.SIDE_GESTURE_ACTION_APP_LAUNCHER: return R.drawable.gesture_action_app_launcher;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_APP: return R.drawable.gesture_action_open_app;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_ACTIVITY: return R.drawable.gesture_action_open_activity;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_INTENT_URI: return R.drawable.gesture_action_open_intent_uri;
            case SettingsStore.SIDE_GESTURE_ACTION_OPEN_SCHEME_URL: return R.drawable.gesture_action_open_scheme_url;
            default: return R.drawable.gesture_action_none;
        }
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
        private Drawable icon;
        private int iconActionId = Integer.MIN_VALUE;
        private boolean textVisible;
        private boolean iconVisible;
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

        void setTextVisible(boolean visible) { textVisible = visible; }

        void setIconVisible(boolean visible) { iconVisible = visible; }

        void setAnchor(float x, float y) {
            anchorX = x;
            anchorY = y;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0) return;
            if (iconVisible && icon != null) {
                float size = dp(44);
                float top = statusBarHeight + dp(8);
                float left = (getWidth() - size) / 2f;
                float right = left + size;
                float bottom = top + size;
                canvas.drawRoundRect(left, top, right, bottom, dp(12), dp(12), background);
                canvas.drawRoundRect(left, top, right, bottom, dp(12), dp(12), outline);
                int iconSize = Math.round(dp(26));
                int iconLeft = Math.round((getWidth() - iconSize) / 2f);
                int iconTop = Math.round(top + (size - iconSize) / 2f);
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
                icon.draw(canvas);
            }
            if (!textVisible || text.length() == 0) return;
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
