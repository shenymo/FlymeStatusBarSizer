package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.res.Configuration;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A single existing card component temporarily changes window host. Main thread only. */
final class AssistantWindowSession {
    static final String TITLE = "FlymeStatusBarSizer:Assistant";
    // Hidden TYPE_STATUS_BAR_SUB_PANEL, also used by Flyme's SystemUIDialog.
    // Unlike TYPE_APPLICATION_OVERLAY, this is above the notification/control-center shade.
    // Aicy runs as android.uid.system; WindowManager enforces STATUS_BAR_SERVICE permission.
    static final int WINDOW_TYPE = 2017;
    final Object component;
    final Object panel;
    final Object callback;
    final Window window;
    final View decor;
    final Context windowContext;
    final WindowManager overlayManager;
    final WindowManager originalManager;
    final WindowManager.LayoutParams originalAttrs = new WindowManager.LayoutParams();
    final Object originalWindowManager, originalToken, originalAppName, originalBackgroundState;
    final AssistantWindowBackground background;
    final AssistantTitleColor titleColor;
    final AssistantPanelMotion motion;
    final AssistantSurfaceHost surfaceHost;
    final boolean dark;
    final int originalVisibility, originalSystemUi;
    final Field componentManager, added, windowManager, appToken, appName;
    final Method open, close, start, resume, pause, stop, setWindowManager;
    String restoreLifecycle;
    volatile boolean suppressCallbacks = true;
    boolean restoring, moved, lifecycleChanged;

    AssistantWindowSession(Object component, boolean fromLeft) throws ReflectiveOperationException {
        this.component = component;
        window = (Window) AssistantReflection.get(component, "mWindow");
        decor = (View) AssistantReflection.get(component, "mDecorView");
        panel = AssistantReflection.call(component, "getSlidingPanelLayout");
        callback = AssistantReflection.get(component, "mProxyCallbacks");
        if (window == null || decor == null || panel == null || callback == null)
            throw new IllegalStateException("Assistant content is not ready");
        componentManager = AssistantReflection.field(component.getClass(), "mWindowManager");
        added = AssistantReflection.field(component.getClass(), "isWindowDecorViewAdded");
        windowManager = AssistantReflection.field(Window.class, "mWindowManager");
        appToken = AssistantReflection.field(Window.class, "mAppToken");
        appName = AssistantReflection.field(Window.class, "mAppName");
        originalManager = (WindowManager) componentManager.get(component);
        originalWindowManager = windowManager.get(window);
        originalToken = appToken.get(window);
        originalAppName = appName.get(window);
        originalBackgroundState = AssistantReflection.get(component, "isBackground");
        originalAttrs.copyFrom(window.getAttributes());
        originalVisibility = decor.getVisibility();
        originalSystemUi = decor.getSystemUiVisibility();
        restoreLifecycle = lifecycle(component);
        open = AssistantReflection.method(component.getClass(), "openOverlay", int.class);
        close = AssistantReflection.method(component.getClass(), "closeOverlay", int.class);
        start = AssistantReflection.method(component.getClass(), "onStart");
        resume = AssistantReflection.method(component.getClass(), "onResume");
        pause = AssistantReflection.method(component.getClass(), "onPause");
        stop = AssistantReflection.method(component.getClass(), "onStop");
        setWindowManager = AssistantReflection.method(Window.class, "setWindowManager",
                WindowManager.class, IBinder.class, String.class, boolean.class);
        Context context = (Context) component;
        windowContext = context.getApplicationContext().createDisplayContext(originalManager.getDefaultDisplay())
                .createWindowContext(WINDOW_TYPE, null);
        overlayManager = windowContext.getSystemService(WindowManager.class);
        dark = (windowContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        View slidingContent = (View) AssistantReflection.get(panel, "mContentView");
        if (slidingContent == null) throw new IllegalStateException("Assistant sliding content is not ready");
        motion = new AssistantPanelMotion((View) panel, slidingContent, fromLeft);
        ModuleConfig config = ModuleConfig.load(context);
        background = new AssistantWindowBackground(slidingContent,
                windowContext.getResources().getDisplayMetrics().density,
                config);
        titleColor = new AssistantTitleColor(decor);
        titleColor.setBackgroundColorMode(config.assistantBackgroundCustom, config.assistantBackgroundForegroundMode);
        surfaceHost = AssistantSurfaceHost.create(component.getClass().getClassLoader());
    }

    void attach() throws ReflectiveOperationException {
        moved = true;
        surfaceHost.prepareForHostChange(decor);
        originalManager.removeViewImmediate(decor);
        added.setBoolean(component, false);
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
        attrs.copyFrom(originalAttrs);
        updateAttributes(attrs);
        attrs.alpha = 0;
        attrs.windowAnimations = 0;
        attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
        setWindowManager.invoke(window, overlayManager, null, TITLE, true);
        componentManager.set(component, window.getWindowManager());
        motion.positionChanged();
        background.apply();
        applyIdentityAndAttributes(window, attrs);
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        decor.setVisibility(View.VISIBLE);
        currentManager().addView(decor, window.getAttributes());
        added.setBoolean(component, true);
        titleColor.apply();
        lifecycleChanged = true;
        if ("CREATED".equals(restoreLifecycle)) start.invoke(component);
        if (!"RESUMED".equals(restoreLifecycle)) resume.invoke(component);
    }

    void open() throws ReflectiveOperationException { open.invoke(component, 1); }

    void updateAttributes(WindowManager.LayoutParams attrs) {
        Rect bounds = overlayManager.getCurrentWindowMetrics().getBounds();
        applyHostAttributes(attrs);
        attrs.x = attrs.y = 0;
        attrs.width = bounds.width();
        attrs.height = bounds.height();
        attrs.gravity = Gravity.TOP | Gravity.LEFT;
        background.updateAttributes(attrs);
    }

    static void applyHostAttributes(WindowManager.LayoutParams attrs) {
        attrs.type = WINDOW_TYPE;
        attrs.packageName = AssistantProtocol.PACKAGE;
        attrs.token = null;
        attrs.setTitle(TITLE);
        attrs.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    }

    void attributesChanged(WindowManager.LayoutParams attrs) throws ReflectiveOperationException {
        updateAttributes(attrs);
        if (added.getBoolean(component)) currentManager().updateViewLayout(decor, attrs);
    }

    void restore(boolean reattach) throws ReflectiveOperationException {
        if (restoring) return;
        restoring = true;
        titleColor.restore();
        if (!moved) return;
        try {
            try { motion.dispose(); } catch (Throwable t) { AssistantHooks.warn("Resetting assistant motion", t); }
            // onPanelClosed can be called again here; the owner ignores it while restoring.
            try { close.invoke(component, 0); } catch (Throwable t) { AssistantHooks.warn("Closing assistant panel", t); }
            if (lifecycleChanged) { pause.invoke(component); stop.invoke(component); }
            if (decor.isAttachedToWindow()) {
                surfaceHost.prepareForHostChange(decor);
                currentManager().removeViewImmediate(decor);
            }
            added.setBoolean(component, false);
            windowManager.set(window, originalWindowManager);
            appToken.set(window, originalToken);
            appName.set(window, originalAppName);
            componentManager.set(component, originalManager);
            background.restore();
            applyIdentityAndAttributes(window, originalAttrs);
            decor.setSystemUiVisibility(originalSystemUi);
            decor.setVisibility(originalVisibility);
            suppressCallbacks = false;
            if (reattach) {
                originalManager.addView(decor, window.getAttributes());
                added.setBoolean(component, true);
            }
            AssistantReflection.set(component, "isBackground", originalBackgroundState);
            if (reattach && ("STARTED".equals(restoreLifecycle) || "RESUMED".equals(restoreLifecycle))) start.invoke(component);
            if (reattach && "RESUMED".equals(restoreLifecycle)) resume.invoke(component);
            moved = false;
        } catch (ReflectiveOperationException | RuntimeException error) {
            background.restore();
            // A dead Launcher token must never leave a focusable transparent overlay on screen.
            decor.setVisibility(View.GONE);
            try { currentManager().removeViewImmediate(decor); } catch (Throwable ignored) { }
            try { added.setBoolean(component, false); } catch (Throwable ignored) { }
            throw error;
        }
    }

    WindowManager currentManager() throws IllegalAccessException {
        return (WindowManager) componentManager.get(component);
    }

    static String lifecycle(Object component) throws ReflectiveOperationException {
        return AssistantReflection.call(AssistantReflection.call(component, "getLifecycle"), "getCurrentState").toString();
    }

    static void applyIdentityAndAttributes(Window window, WindowManager.LayoutParams attrs) {
        // copyFrom/setAttributes intentionally preserve non-null identity fields: replace them explicitly.
        WindowManager.LayoutParams live = window.getAttributes();
        live.token = attrs.token;
        live.packageName = attrs.packageName;
        try {
            Field token = AssistantReflection.field(WindowManager.LayoutParams.class, "mWindowContextToken");
            token.set(live, token.get(attrs));
        } catch (ReflectiveOperationException ignored) { }
        window.setAttributes(attrs);
    }
}
