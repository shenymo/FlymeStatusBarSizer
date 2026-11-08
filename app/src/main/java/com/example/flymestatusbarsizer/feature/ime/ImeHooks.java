package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.database.ContentObserver;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class ImeHooks {
    private static final String PACKAGE_ANDROID = "android";
    private static final String PACKAGE_SYSTEM = "system";
    private static final int TOOLBAR_HEIGHT_DP = 40;
    private static final int TEXT_ROW_HEIGHT_DP = 20;

    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, OverlayState> OVERLAY_STATES = new WeakHashMap<>();
    private static boolean verificationCodeObserverRegistered;

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
                    ensureVerificationCodeObserver(context);
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

    private static void ensureVerificationCodeObserver(Context context) {
        if (verificationCodeObserverRegistered) {
            return;
        }
        synchronized (ImeHooks.class) {
            if (verificationCodeObserverRegistered) {
                return;
            }
            context.getContentResolver().registerContentObserver(
                    VerificationCodeProvider.URI, false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            refreshTrackedInputMethodViews();
                        }
                    });
            verificationCodeObserverRegistered = true;
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
            ImeToolbarActions.refreshActionButtonStates(service, current.toolbar,
                    config.imeControlBarIconAlphaPercent);
            return;
        }
        detachOverlay(service);
        LinearLayout toolbar = createToolbar(inputView.getContext(), service, config);
        int toolbarHeight = TOOLBAR_HEIGHT_DP
                + (config.imeControlBarExpansionEnabled ? TEXT_ROW_HEIGHT_DP : 0);
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                FlymeStatusBarSizer.dp(inputView.getContext(), toolbarHeight),
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
        toolbar.setOrientation(LinearLayout.VERTICAL);
        if (config.imeControlBarExpansionEnabled) {
            LinearLayout textRow = createToolbarRow(context, service, config,
                    ImeToolbarSpec.resolveTextSlots(config.imeControlBarTextSlots), true);
            toolbar.addView(textRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    FlymeStatusBarSizer.dp(context, TEXT_ROW_HEIGHT_DP)));
        }
        LinearLayout buttonRow = createToolbarRow(context, service, config,
                ImeToolbarSpec.resolveButtonSlots(config), false);
        toolbar.addView(buttonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                FlymeStatusBarSizer.dp(context, TOOLBAR_HEIGHT_DP)));
        ImeToolbarActions.refreshActionButtonStates(service, toolbar,
                config.imeControlBarIconAlphaPercent);
        return toolbar;
    }

    private static LinearLayout createToolbarRow(Context context, Object service,
            FlymeStatusBarSizer.ImeConfigSnapshot config, ArrayList<String> slots,
            boolean textOnly) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                FlymeStatusBarSizer.dp(context, 4),
                textOnly ? 0 : FlymeStatusBarSizer.dp(context, 2),
                FlymeStatusBarSizer.dp(context, 4),
                textOnly ? 0 : FlymeStatusBarSizer.dp(context, 2));
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            String action = slots.get(slotIndex);
            if (TextUtils.isEmpty(action)
                    || (textOnly && !ImeToolbarSpec.isTextContentAction(action))) {
                View spacer = new View(context);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
                continue;
            }
            int span = ImeToolbarSpec.getButtonSpan(action);
            View button;
            if (ImeToolbarSpec.isTextContentAction(action)) {
                TextView codeButton = new TextView(context);
                codeButton.setGravity(Gravity.CENTER);
                codeButton.setTextSize(textOnly ? 12 : 14);
                codeButton.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                codeButton.setTextColor(ImeToolbarIcons.resolveIconColor(context));
                codeButton.setContentDescription("验证码");
                button = codeButton;
            } else {
                ImageButton iconButton = new ImageButton(context);
                iconButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconButton.setImageDrawable(ImeToolbarSpec.isBackButton(action)
                        ? ImeToolbarIcons.createKeyboardDismissDrawable(context)
                        : ImeToolbarIcons.createIconDrawable(context, action));
                iconButton.setContentDescription(ImeToolbarSpec.getButtonLabel(action));
                iconButton.setBackgroundColor(0);
                button = iconButton;
            }
            button.setTag(action);
            int alpha = Math.max(10, Math.min(100, config.imeControlBarIconAlphaPercent));
            if (button instanceof ImageButton) {
                ((ImageButton) button).setImageAlpha(255);
            }
            button.setAlpha(alpha / 100f);
            int scale = Math.max(60, Math.min(180, config.imeControlBarIconScalePercent));
            int padding = textOnly ? 0 : Math.max(0, Math.round((8f * 100f / scale)
                    * context.getResources().getDisplayMetrics().density));
            button.setPadding(padding, padding, padding, padding);
            row.addView(button, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT,
                    span));
            if (ImeToolbarSpec.isBackButton(action)) {
                button.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    hideKeyboard(service);
                });
            } else {
                ImeToolbarActions.bindActionButtonView(service, button);
            }
            slotIndex += span - 1;
        }
        return row;
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
        final boolean expansionEnabled;
        final String textSlots;
        final int iconScale;
        final int iconAlpha;
        final int yOffset;
        final int iconColor;

        OverlayState(View inputView, LinearLayout toolbar,
                FlymeStatusBarSizer.ImeConfigSnapshot config, int iconColor) {
            this.inputView = inputView;
            this.toolbar = toolbar;
            this.buttonSlots = config.imeControlBarButtonSlots;
            this.expansionEnabled = config.imeControlBarExpansionEnabled;
            this.textSlots = config.imeControlBarTextSlots;
            this.iconScale = config.imeControlBarIconScalePercent;
            this.iconAlpha = config.imeControlBarIconAlphaPercent;
            this.yOffset = config.imeControlBarYOffsetTenthDp;
            this.iconColor = iconColor;
        }

        boolean matches(FlymeStatusBarSizer.ImeConfigSnapshot config, int currentIconColor) {
            return iconScale == config.imeControlBarIconScalePercent
                    && iconAlpha == config.imeControlBarIconAlphaPercent
                    && yOffset == config.imeControlBarYOffsetTenthDp
                    && expansionEnabled == config.imeControlBarExpansionEnabled
                    && iconColor == currentIconColor
                    && java.util.Objects.equals(buttonSlots, config.imeControlBarButtonSlots)
                    && java.util.Objects.equals(textSlots, config.imeControlBarTextSlots);
        }
    }
}
