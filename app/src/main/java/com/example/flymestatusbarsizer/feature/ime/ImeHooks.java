package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class ImeHooks {
    private static final String PACKAGE_ANDROID = "android";
    private static final String PACKAGE_SYSTEM = "system";
    private static final int TOOLBAR_HEIGHT_DP = 40;

    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, OverlayState> OVERLAY_STATES = new WeakHashMap<>();

    private ImeHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader, String packageName) {
        if (module == null || loader == null || packageName == null
                || PACKAGE_ANDROID.equals(packageName) || PACKAGE_SYSTEM.equals(packageName)) {
            return;
        }
        hookInputMethodService(module, loader);
    }

    public static void refreshTrackedInputMethodViews() {
        Runnable refresh = () -> {
            ArrayList<Object> services = new ArrayList<>(TRACKED_INPUT_METHOD_VIEWS.keySet());
            for (Object service : services) {
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(service);
                if (service != null && inputView != null) {
                    refreshImeToolbar(service, inputView);
                }
            }
        };
        if (FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.postToMainHandler(refresh);
        } else {
            refresh.run();
        }
    }

    private static void hookInputMethodService(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.InputMethodService", false, loader);
            Method setInputView = clazz.getDeclaredMethod("setInputView", View.class);
            setInputView.setAccessible(true);
            module.intercept(setInputView, chain -> {
                Object result = chain.proceed();
                Object service = chain.getThisObject();
                Object argument = chain.getArg(0);
                if (service instanceof Context && argument instanceof View) {
                    Context context = (Context) service;
                    View inputView = (View) argument;
                    FlymeStatusBarSizer.rememberSystemUiContext(context);
                    FlymeStatusBarSizer.ensureConfigRefreshObserver(context);
                    TRACKED_INPUT_METHOD_VIEWS.put(service, inputView);
                    postRefresh(service, inputView);
                }
                return result;
            });

            Class<?> editorInfoClass = Class.forName(
                    "android.view.inputmethod.EditorInfo", false, loader);
            Method onStartInputView = clazz.getDeclaredMethod(
                    "onStartInputView", editorInfoClass, boolean.class);
            onStartInputView.setAccessible(true);
            module.intercept(onStartInputView, chain -> {
                Object result = chain.proceed();
                postTrackedRefresh(chain.getThisObject());
                return result;
            });

            Method showWindow = clazz.getDeclaredMethod("showWindow", boolean.class);
            showWindow.setAccessible(true);
            module.intercept(showWindow, chain -> {
                Object result = chain.proceed();
                postTrackedRefresh(chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            FlymeStatusBarSizer.logImeWarning("Failed to hook InputMethodService", error);
        }
    }

    private static void postTrackedRefresh(Object service) {
        View inputView = TRACKED_INPUT_METHOD_VIEWS.get(service);
        if (inputView != null) {
            postRefresh(service, inputView);
        }
    }

    private static void postRefresh(Object service, View inputView) {
        Runnable refresh = () -> refreshImeToolbar(service, inputView);
        if (!inputView.post(refresh) && FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.postToMainHandler(refresh);
        }
    }

    private static void refreshImeToolbar(Object service, View inputView) {
        FlymeStatusBarSizer.ImeConfigSnapshot config =
                FlymeStatusBarSizer.loadImeConfig(inputView.getContext());
        if (config == null || !config.enabled) {
            detachOverlay(service);
            return;
        }
        ViewGroup inputFrame = asViewGroup(
                FlymeStatusBarSizer.getFieldCompat(service, "mInputFrame"));
        if (inputFrame == null) {
            return;
        }
        OverlayState current = OVERLAY_STATES.get(service);
        if (current != null && current.inputView == inputView
                && current.toolbar.getParent() == inputFrame
                && current.matches(config, ImeToolbarIcons.resolveIconColor(inputView.getContext()))) {
            ImeToolbarActions.refreshActionButtonStates(service, current.toolbar);
            return;
        }
        detachOverlay(service);
        LinearLayout toolbar = createToolbar(inputView.getContext(), service, config);
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                FlymeStatusBarSizer.dp(inputView.getContext(), TOOLBAR_HEIGHT_DP),
                Gravity.BOTTOM);
        toolbarParams.bottomMargin = Math.round(
                (config.imeControlBarYOffsetTenthDp / 10f)
                        * inputView.getResources().getDisplayMetrics().density);
        inputFrame.addView(toolbar, toolbarParams);
        OVERLAY_STATES.put(service, new OverlayState(
                inputView, toolbar, config, ImeToolbarIcons.resolveIconColor(inputView.getContext())));
        inputFrame.requestLayout();
        inputFrame.invalidate();
    }

    private static LinearLayout createToolbar(
            Context context, Object service, FlymeStatusBarSizer.ImeConfigSnapshot config) {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(
                FlymeStatusBarSizer.dp(context, 4),
                FlymeStatusBarSizer.dp(context, 2),
                FlymeStatusBarSizer.dp(context, 4),
                FlymeStatusBarSizer.dp(context, 2));
        ArrayList<String> slots = ImeToolbarSpec.resolveButtonSlots(config);
        for (String action : slots) {
            if (TextUtils.isEmpty(action)) {
                View spacer = new View(context);
                toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
                continue;
            }
            ImageButton button = new ImageButton(context);
            button.setTag(action);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setImageDrawable(ImeToolbarSpec.isBackButton(action)
                    ? ImeToolbarIcons.createKeyboardDismissDrawable(context)
                    : ImeToolbarIcons.createIconDrawable(context, action));
            button.setContentDescription(ImeToolbarSpec.getButtonLabel(action));
            button.setBackgroundColor(0);
            int alpha = Math.max(10, Math.min(100, config.imeControlBarIconAlphaPercent));
            button.setImageAlpha(Math.round(alpha * 2.55f));
            int scale = Math.max(60, Math.min(180, config.imeControlBarIconScalePercent));
            int padding = Math.max(0, Math.round((8f * 100f / scale)
                    * context.getResources().getDisplayMetrics().density));
            button.setPadding(padding, padding, padding, padding);
            toolbar.addView(button, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            if (ImeToolbarSpec.isBackButton(action)) {
                button.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    hideKeyboard(service);
                });
            } else {
                ImeToolbarActions.bindActionButtonView(service, button);
            }
        }
        ImeToolbarActions.refreshActionButtonStates(service, toolbar);
        return toolbar;
    }

    private static void detachOverlay(Object service) {
        OverlayState state = OVERLAY_STATES.remove(service);
        if (state == null) {
            return;
        }
        ViewGroup parent = asViewGroup(state.toolbar.getParent());
        if (parent != null) {
            parent.removeView(state.toolbar);
            parent.requestLayout();
            parent.invalidate();
        }
    }

    private static void hideKeyboard(Object service) {
        if (service instanceof InputMethodService) {
            ((InputMethodService) service).requestHideSelf(0);
        } else {
            FlymeStatusBarSizer.invokeMethodCompat(
                    service, "requestHideSelf", new Class[]{int.class}, 0);
        }
    }

    private static ViewGroup asViewGroup(Object value) {
        return value instanceof ViewGroup ? (ViewGroup) value : null;
    }

    private static final class OverlayState {
        final View inputView;
        final LinearLayout toolbar;
        final String buttonSlots;
        final int iconScale;
        final int iconAlpha;
        final int yOffset;
        final int iconColor;

        OverlayState(View inputView, LinearLayout toolbar,
                FlymeStatusBarSizer.ImeConfigSnapshot config, int iconColor) {
            this.inputView = inputView;
            this.toolbar = toolbar;
            this.buttonSlots = config.imeControlBarButtonSlots;
            this.iconScale = config.imeControlBarIconScalePercent;
            this.iconAlpha = config.imeControlBarIconAlphaPercent;
            this.yOffset = config.imeControlBarYOffsetTenthDp;
            this.iconColor = iconColor;
        }

        boolean matches(FlymeStatusBarSizer.ImeConfigSnapshot config, int currentIconColor) {
            return iconScale == config.imeControlBarIconScalePercent
                    && iconAlpha == config.imeControlBarIconAlphaPercent
                    && yOffset == config.imeControlBarYOffsetTenthDp
                    && iconColor == currentIconColor
                    && java.util.Objects.equals(buttonSlots, config.imeControlBarButtonSlots);
        }
    }
}
