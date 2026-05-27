package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ImeHooks {
    private static final String STOCK_CONTROL_BAR_BACK = ImeToolbarSpec.STOCK_CONTROL_BAR_BACK;
    private static final String PACKAGE_ANDROID = "android";
    private static final String PACKAGE_ANDROID_LATIN = "com.android.inputmethod.latin";
    private static final String PACKAGE_FLYME_INPUTMETHOD = "flyme.inputmethod";
    private static final String PACKAGE_GOOGLE_LATIN = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_TENCENT_WETYPE = "com.tencent.wetype";
    private static final int DEFAULT_IME_ICON_SCALE_PERCENT = 100;
    private static final int DEFAULT_IME_ICON_ALPHA_PERCENT = 100;
    private static final int FLYME_QS_ACTION_ITEMS_LAYOUT_ID = 17367445;

    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> TRACKED_INPUT_METHOD_MANAGER_SERVICES =
            new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> LAST_STOCK_CONTROL_BAR_STATES =
            new WeakHashMap<>();
    private static final WeakHashMap<Object, String> LAST_STOCK_CONTROL_BAR_LAYOUT_SPECS =
            new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> PENDING_IME_CONTROL_BAR_REFRESHES =
            new WeakHashMap<>();
    private static final WeakHashMap<Object, ImeWindowAppearanceState> IME_WINDOW_APPEARANCE_STATES =
            new WeakHashMap<>();
    private static final WeakHashMap<View, StockControlBarFrameState> STOCK_CONTROL_BAR_FRAME_STATES =
            new WeakHashMap<>();
    private static final Object IME_CONTROL_BAR_REFRESH_LOCK = new Object();

    private ImeHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader, String packageName) {
        if (module == null || loader == null || packageName == null) {
            return;
        }
        if (isImeClientPackage(packageName)) {
            hookNavigationBarInflaterOnFinishInflate(module, loader);
            hookNavigationBarInflaterCreateView(module, loader);
            hookNavigationBarViewSetNavbarFlags(module, loader);
            hookNavigationBarViewSetDarkIntensity(module, loader);
            hookNavigationBarInsetsVisibility(module, loader);
            hookNavigationBarBackgroundRefresh(module, loader);
            hookInputMethodService(module, loader);
            hookFlymeCaptchaCandidate(module, loader);
        }
        if (PACKAGE_ANDROID.equals(packageName)) {
            hookInputMethodManagerServiceNavFlags(module, loader);
            hookInputMethodManagerServiceWindowStatus(module, loader);
        }
    }

    public static void refreshTrackedInputMethodViews() {
        Runnable refreshRunnable = () -> {
            ArrayList<Object> services = new ArrayList<>(TRACKED_INPUT_METHOD_VIEWS.keySet());
            for (Object inputMethodService : services) {
                if (inputMethodService == null) {
                    continue;
                }
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(inputMethodService);
                if (inputView == null) {
                    continue;
                }
                refreshImeControlBarNow(inputMethodService);
            }
            refreshTrackedInputMethodManagerServices();
        };
        if (FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.postToMainHandler(refreshRunnable);
        } else {
            refreshRunnable.run();
        }
    }

    private static boolean isImeClientPackage(String packageName) {
        return PACKAGE_ANDROID_LATIN.equals(packageName)
                || PACKAGE_GOOGLE_LATIN.equals(packageName)
                || PACKAGE_TENCENT_WETYPE.equals(packageName)
                || PACKAGE_FLYME_INPUTMETHOD.equals(packageName);
    }

    private static boolean shouldReplaceOriginalImeControlBar() {
        return ImeToolbarSpec.shouldReplaceOriginalControlBar(FlymeStatusBarSizer.loadImeConfig(null));
    }

    private static void hookNavigationBarInflaterOnFinishInflate(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.navigationbar.NavigationBarInflaterView",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("onFinishInflate");
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (!shouldReplaceOriginalImeControlBar()) {
                    return result;
                }
                rebuildStockNavigationBarLayout(chain.getThisObject());
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarInflaterView.onFinishInflate",
                    t);
        }
    }

    private static void hookNavigationBarInflaterCreateView(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.navigationbar.NavigationBarInflaterView",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "createView",
                    String.class,
                    ViewGroup.class,
                    LayoutInflater.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object specArg = chain.getArg(0);
                Object parentArg = chain.getArg(1);
                if (!shouldReplaceOriginalImeControlBar()
                        || !(specArg instanceof String)
                        || !(parentArg instanceof ViewGroup)) {
                    return chain.proceed();
                }
                String action = extractButtonName((String) specArg);
                if (ImeToolbarSpec.isValidActionName(action)) {
                    return createStockControlBarActionButton(((ViewGroup) parentArg).getContext(), action);
                }
                if (ImeToolbarSpec.isPlaceholderName(action)) {
                    return createStockControlBarPlaceholderView(((ViewGroup) parentArg).getContext());
                }
                if (!STOCK_CONTROL_BAR_BACK.equals(action)) {
                    return chain.proceed();
                }
                return createStockControlBarBackButton(((ViewGroup) parentArg).getContext());
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarInflaterView.createView",
                    t);
        }
    }

    private static void hookNavigationBarViewSetNavbarFlags(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.navigationbar.NavigationBarView",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("setNavbarFlags", int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object thisObject = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!shouldReplaceOriginalImeControlBar()
                        || thisObject == null
                        || !(arg instanceof Integer)) {
                    return chain.proceed();
                }
                int flags = (Integer) arg;
                if (flags == getIntField(thisObject, "mNavbarFlags", Integer.MIN_VALUE)) {
                    return null;
                }
                setIntField(thisObject, "mNavbarFlags", flags);
                FlymeStatusBarSizer.invokeMethodCompat(
                        thisObject,
                        "updateNavButtonIcons",
                        new Class[0]);
                return null;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarView.setNavbarFlags",
                    t);
        }
    }

    private static void hookNavigationBarViewSetDarkIntensity(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.navigationbar.NavigationBarView",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("setDarkIntensity", float.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object intensityArg = chain.getArg(0);
                if (thisObject instanceof View && intensityArg instanceof Float) {
                    View navigationBarView = (View) thisObject;
                    applyStockControlBarButtonTint(
                            getOrCreateStockControlBarFrameState(navigationBarView),
                            navigationBarView,
                            (Float) intensityArg,
                            FlymeStatusBarSizer.loadImeConfig(null));
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarView.setDarkIntensity",
                    t);
        }
    }

    private static void hookNavigationBarInsetsVisibility(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.NavigationBarController$Impl",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "lambda$installNavigationBarFrameIfNecessary$0",
                    View.class,
                    WindowInsets.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (!shouldReplaceOriginalImeControlBar()) {
                    return chain.proceed();
                }
                Object thisObject = chain.getThisObject();
                Object viewArg = chain.getArg(0);
                Object insetsArg = chain.getArg(1);
                if (!(viewArg instanceof View)
                        || !(insetsArg instanceof WindowInsets)
                        || thisObject == null) {
                    return chain.proceed();
                }
                View view = (View) viewArg;
                WindowInsets insets = (WindowInsets) insetsArg;
                Object navigationBarFrame = getField(thisObject, "mNavigationBarFrame");
                if (navigationBarFrame instanceof View) {
                    boolean visible = insets.isVisible(WindowInsets.Type.captionBar());
                    ((View) navigationBarFrame).setVisibility(visible ? View.VISIBLE : View.GONE);
                    FlymeStatusBarSizer.invokeMethodCompat(
                            thisObject,
                            "checkCustomImeSwitcherButtonRequestedVisible",
                            new Class[]{boolean.class, boolean.class, boolean.class},
                            getBooleanField(thisObject, "mShouldShowImeSwitcherWhenImeIsShown"),
                            getBooleanField(thisObject, "mImeDrawsImeNavBar"),
                            !visible);
                }
                return view.onApplyWindowInsets(insets);
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarController$Impl IME nav visibility",
                    t);
        }
    }

    private static void hookNavigationBarBackgroundRefresh(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "android.inputmethodservice.NavigationBarController$Impl",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "onDrawLegacyNavigationBarBackgroundChanged",
                    boolean.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                if (!shouldReplaceOriginalImeControlBar()) {
                    return chain.proceed();
                }
                Object thisObject = chain.getThisObject();
                if (thisObject == null) {
                    return Boolean.FALSE;
                }
                Object result = chain.proceed();
                Object navigationBarFrame = getField(thisObject, "mNavigationBarFrame");
                Object inputMethodService = getField(thisObject, "mService");
                applyImeWindowNavigationBarAppearance(inputMethodService);
                if (navigationBarFrame instanceof View) {
                    syncStockControlBarButtonsNow(
                            (View) navigationBarFrame,
                            inputMethodService,
                            getFloatField(thisObject, "mDarkIntensity", 0f));
                    syncNavigationBarFrameBackgroundNow((View) navigationBarFrame, inputMethodService);
                    FlymeStatusBarSizer.invokeMethodCompat(
                            thisObject,
                            "scheduleRelayout",
                            new Class[0]);
                }
                FlymeStatusBarSizer.invokeMethodCompat(
                        thisObject,
                        "onSystemBarAppearanceChanged",
                        new Class[]{int.class},
                        getIntField(thisObject, "mAppearance", 0));
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook NavigationBarController$Impl.onDrawLegacyNavigationBarBackgroundChanged",
                    t);
        }
    }

    private static void hookInputMethodService(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("android.inputmethodservice.InputMethodService", false, loader);
            Method setInputView = clazz.getDeclaredMethod("setInputView", View.class);
            setInputView.setAccessible(true);
            module.intercept(setInputView, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                Object arg = chain.getArg(0);
                if (!(thisObject instanceof Context) || !(arg instanceof View)) {
                    return result;
                }
                Context context = (Context) thisObject;
                View inputView = (View) arg;
                FlymeStatusBarSizer.rememberSystemUiContext(context);
                FlymeStatusBarSizer.ensureConfigRefreshObserver(context);
                TRACKED_INPUT_METHOD_VIEWS.put(thisObject, inputView);
                scheduleImeControlBarRefresh(thisObject, inputView);
                return result;
            });

            Class<?> editorInfoClass = Class.forName("android.view.inputmethod.EditorInfo", false, loader);
            Method onStartInputView = clazz.getDeclaredMethod("onStartInputView", editorInfoClass, boolean.class);
            onStartInputView.setAccessible(true);
            module.intercept(onStartInputView, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    scheduleImeControlBarRefresh(thisObject, inputView);
                }
                return result;
            });

            Method showWindow = clazz.getDeclaredMethod("showWindow", boolean.class);
            showWindow.setAccessible(true);
            module.intercept(showWindow, chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                View inputView = TRACKED_INPUT_METHOD_VIEWS.get(thisObject);
                if (inputView != null) {
                    scheduleImeControlBarRefresh(thisObject, inputView);
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to hook InputMethodService.setInputView", t);
        }
    }

    private static void hookFlymeCaptchaCandidate(FlymeStatusBarSizer module, ClassLoader loader) {
        Class<?> itemClass = findClass(loader, "flyme.inputmethod.QsActionItem");
        if (itemClass == null) {
            return;
        }
        hookFlymeCaptchaMessageView(module, loader, itemClass);
        hookFlymeCaptchaActionListener(module, loader, itemClass);
        hookFlymeCaptchaManager(module, loader, itemClass);
    }

    private static void hookFlymeCaptchaMessageView(
            FlymeStatusBarSizer module, ClassLoader loader, Class<?> itemClass) {
        try {
            Class<?> viewClass = Class.forName("flyme.inputmethod.QsActionItemsView", false, loader);
            Method method = viewClass.getDeclaredMethod("updateMessageState", itemClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                updateFlymeCaptchaCandidate(chain.getArg(0), chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookFlymeCaptchaActionListener(
            FlymeStatusBarSizer module, ClassLoader loader, Class<?> itemClass) {
        try {
            Class<?> listenerClass = Class.forName("flyme.inputmethod.QsActionItemsView$1", false, loader);
            Method method = listenerClass.getDeclaredMethod("onItemActionChange", itemClass, int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                if (asInt(chain.getArg(1), -1) == 16) {
                    updateFlymeCaptchaCandidate(chain.getArg(0), chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookFlymeCaptchaManager(
            FlymeStatusBarSizer module, ClassLoader loader, Class<?> itemClass) {
        try {
            Class<?> managerClass = Class.forName("flyme.inputmethod.QsActionManager", false, loader);
            Class<?> callbackClass = Class.forName("flyme.inputmethod.IQsActionCallback", false, loader);
            Method registerActionItem = managerClass.getDeclaredMethod(
                    "registerActionItem",
                    itemClass,
                    callbackClass);
            registerActionItem.setAccessible(true);
            module.intercept(registerActionItem, chain -> {
                Object result = chain.proceed();
                updateFlymeCaptchaCandidate(chain.getArg(0), null);
                return result;
            });

            Method getActionItemList = managerClass.getDeclaredMethod("getActionItemList", int.class);
            getActionItemList.setAccessible(true);
            module.intercept(getActionItemList, chain -> {
                Object result = chain.proceed();
                if (ImeToolbarActions.updateFlymeCaptchaCandidateFromList(result)) {
                    scheduleCaptchaButtonRefresh(null);
                }
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private static void updateFlymeCaptchaCandidate(Object item, Object anchorObject) {
        if (ImeToolbarActions.updateFlymeCaptchaCandidate(item)) {
            scheduleCaptchaButtonRefresh(anchorObject);
        }
    }

    private static void scheduleCaptchaButtonRefresh(Object anchorObject) {
        Runnable refreshRunnable = ImeHooks::refreshTrackedInputMethodViews;
        View anchor = anchorObject instanceof View ? (View) anchorObject : null;
        if (anchor != null && anchor.post(refreshRunnable)) {
            scheduleCaptchaButtonExpiryRefresh(anchor);
            return;
        }
        if (FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.postToMainHandler(refreshRunnable);
            scheduleCaptchaButtonExpiryRefresh(null);
            return;
        }
        refreshRunnable.run();
    }

    private static void scheduleCaptchaButtonExpiryRefresh(View anchor) {
        long delay = ImeToolbarActions.getCaptchaRefreshDelayMs();
        if (delay < 0L) {
            return;
        }
        if (anchor != null && anchor.postDelayed(ImeHooks::refreshTrackedInputMethodViews, delay + 100L)) {
            return;
        }
        if (FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.getMainHandler()
                    .postDelayed(ImeHooks::refreshTrackedInputMethodViews, delay + 100L);
        }
    }

    private static void scheduleImeControlBarRefresh(Object inputMethodService, View anchorView) {
        if (inputMethodService == null) {
            return;
        }
        synchronized (IME_CONTROL_BAR_REFRESH_LOCK) {
            if (Boolean.TRUE.equals(PENDING_IME_CONTROL_BAR_REFRESHES.get(inputMethodService))) {
                return;
            }
            PENDING_IME_CONTROL_BAR_REFRESHES.put(inputMethodService, Boolean.TRUE);
        }
        Runnable refreshRunnable = () -> {
            try {
                refreshImeControlBarNow(inputMethodService);
            } finally {
                synchronized (IME_CONTROL_BAR_REFRESH_LOCK) {
                    PENDING_IME_CONTROL_BAR_REFRESHES.remove(inputMethodService);
                }
            }
        };
        if (anchorView != null && anchorView.post(refreshRunnable)) {
            return;
        }
        if (FlymeStatusBarSizer.getMainHandler() != null) {
            FlymeStatusBarSizer.postToMainHandler(refreshRunnable);
            return;
        }
        refreshRunnable.run();
    }

    private static void hookInputMethodManagerServiceNavFlags(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.server.inputmethod.InputMethodManagerService",
                    false,
                    loader);
            Class<?> userDataClass = Class.forName(
                    "com.android.server.inputmethod.InputMethodManagerService$UserData",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod("getInputMethodNavButtonFlagsLocked", userDataClass);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object service = chain.getThisObject();
                if (service != null) {
                    TRACKED_INPUT_METHOD_MANAGER_SERVICES.put(service, Boolean.TRUE);
                }
                if (!shouldReplaceOriginalImeControlBar()) {
                    return chain.proceed();
                }
                Object userData = chain.getArg(0);
                if (service == null || userData == null) {
                    return chain.proceed();
                }
                return Integer.valueOf(resolveStockImeNavButtonFlags(service, userData));
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook InputMethodManagerService.getInputMethodNavButtonFlagsLocked",
                    t);
        }
    }

    private static void hookInputMethodManagerServiceWindowStatus(
            FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.android.server.inputmethod.InputMethodManagerService",
                    false,
                    loader);
            Method method = clazz.getDeclaredMethod(
                    "updateSystemUiLocked",
                    int.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            module.intercept(method, chain -> {
                Object result = chain.proceed();
                Object service = chain.getThisObject();
                if (service != null) {
                    TRACKED_INPUT_METHOD_MANAGER_SERVICES.put(service, Boolean.TRUE);
                }
                if (!shouldReplaceOriginalImeControlBar() || service == null) {
                    return result;
                }
                applyStockImeWindowStatus(
                        service,
                        asInt(chain.getArg(0), 0),
                        asInt(chain.getArg(1), 0),
                        asInt(chain.getArg(2), 0));
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook InputMethodManagerService.updateSystemUiLocked",
                    t);
        }
    }

    private static void rebuildStockNavigationBarLayout(Object navigationBarInflaterView) {
        if (navigationBarInflaterView == null) {
            return;
        }
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(null);
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarInflaterView,
                "inflateChildren",
                new Class[0]);
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarInflaterView,
                "clearViews",
                new Class[0]);
        Object defaultLayout = FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarInflaterView,
                "getDefaultLayout",
                new Class[0]);
        boolean replaceControlBar = ImeToolbarSpec.shouldReplaceOriginalControlBar(config);
        String layoutSpec = replaceControlBar
                ? ImeToolbarSpec.buildStockControlBarLayout(config)
                : (defaultLayout instanceof String ? (String) defaultLayout : null);
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarInflaterView,
                "inflateLayout",
                new Class[]{String.class},
                layoutSpec);
        if (replaceControlBar) {
            ensureFlymeCaptchaBridgeView(navigationBarInflaterView);
        }
    }

    private static void ensureFlymeCaptchaBridgeView(Object navigationBarInflaterView) {
        if (!(navigationBarInflaterView instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) navigationBarInflaterView;
        View existing = findFlymeQsActionItemsView(parent);
        if (existing != null) {
            existing.setVisibility(View.GONE);
            existing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            return;
        }
        try {
            View bridge = LayoutInflater.from(parent.getContext())
                    .inflate(FLYME_QS_ACTION_ITEMS_LAYOUT_ID, parent, false);
            if (bridge == null || !isFlymeQsActionItemsView(bridge)) {
                return;
            }
            bridge.setVisibility(View.GONE);
            bridge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            parent.addView(bridge);
        } catch (Throwable ignored) {
        }
    }

    private static View findFlymeQsActionItemsView(View root) {
        if (root == null) {
            return null;
        }
        if (isFlymeQsActionItemsView(root)) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findFlymeQsActionItemsView(group.getChildAt(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static boolean isFlymeQsActionItemsView(View view) {
        return view != null && "flyme.inputmethod.QsActionItemsView".equals(view.getClass().getName());
    }

    private static void refreshImeControlBarNow(Object inputMethodService) {
        if (inputMethodService == null) {
            return;
        }
        Object navigationBarController = getField(inputMethodService, "mNavigationBarController");
        Object callbackImpl = getField(navigationBarController, "mImpl");
        if (navigationBarController == null || callbackImpl == null) {
            return;
        }
        boolean replaceControlBar = shouldReplaceOriginalImeControlBar();
        String desiredLayoutSpec = resolveEmbeddedStockControlBarLayoutSpec();
        Boolean lastAppliedState = LAST_STOCK_CONTROL_BAR_STATES.get(inputMethodService);
        String lastLayoutSpec = LAST_STOCK_CONTROL_BAR_LAYOUT_SPECS.get(inputMethodService);
        boolean replaceStateChanged =
                lastAppliedState == null || lastAppliedState.booleanValue() != replaceControlBar;
        boolean layoutSpecChanged = !Objects.equals(lastLayoutSpec, desiredLayoutSpec);
        boolean layoutNeedsRefresh = replaceStateChanged || layoutSpecChanged;
        if (layoutNeedsRefresh) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    callbackImpl,
                    "uninstallNavigationBarFrameIfNecessary",
                    new Class[0]);
        }
        if (replaceControlBar) {
            applyImeWindowNavigationBarAppearance(inputMethodService);
        } else if (replaceStateChanged) {
            restoreImeWindowNavigationBarAppearance(inputMethodService);
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarController,
                "onNavButtonFlagsChanged",
                new Class[]{int.class},
                resolveCurrentImeNavButtonFlags(callbackImpl));
        Object navigationBarFrame = getField(callbackImpl, "mNavigationBarFrame");
        if (navigationBarFrame instanceof View) {
            if (layoutNeedsRefresh) {
                clearStockControlBarFrameStateCache((View) navigationBarFrame);
            }
            syncStockControlBarButtonsNow(
                    (View) navigationBarFrame,
                    inputMethodService,
                    getFloatField(callbackImpl, "mDarkIntensity", 0f));
            if (replaceControlBar) {
                syncNavigationBarFrameBackgroundNow((View) navigationBarFrame, inputMethodService);
            } else if (replaceStateChanged) {
                FlymeStatusBarSizer.invokeMethodCompat(
                        callbackImpl,
                        "onDrawLegacyNavigationBarBackgroundChanged",
                        new Class[]{boolean.class},
                        getBooleanField(callbackImpl, "mDrawLegacyNavigationBarBackground"));
            }
            ((View) navigationBarFrame).requestApplyInsets();
        }
        LAST_STOCK_CONTROL_BAR_STATES.put(inputMethodService, replaceControlBar);
        LAST_STOCK_CONTROL_BAR_LAYOUT_SPECS.put(inputMethodService, desiredLayoutSpec);
    }

    private static int resolveCurrentImeNavButtonFlags(Object callbackImpl) {
        return (getBooleanField(callbackImpl, "mImeDrawsImeNavBar") ? 1 : 0)
                | (getBooleanField(callbackImpl, "mShouldShowImeSwitcherWhenImeIsShown") ? 2 : 0);
    }

    private static void refreshTrackedInputMethodManagerServices() {
        ArrayList<Object> services = new ArrayList<>(TRACKED_INPUT_METHOD_MANAGER_SERVICES.keySet());
        for (Object service : services) {
            if (service == null) {
                continue;
            }
            refreshInputMethodManagerServiceNow(service);
        }
    }

    private static void refreshInputMethodManagerServiceNow(Object service) {
        Class<?> imfLockClass = findClass(service.getClass().getClassLoader(), "com.android.server.inputmethod.ImfLock");
        if (imfLockClass == null) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    service,
                    "sendOnNavButtonFlagsChangedToAllImesLocked",
                    new Class[0]);
            FlymeStatusBarSizer.invokeMethodCompat(
                    service,
                    "updateSystemUiLocked",
                    new Class[]{int.class},
                    getIntField(service, "mCurrentImeUserId", 0));
            return;
        }
        synchronized (imfLockClass) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    service,
                    "sendOnNavButtonFlagsChangedToAllImesLocked",
                    new Class[0]);
            FlymeStatusBarSizer.invokeMethodCompat(
                    service,
                    "updateSystemUiLocked",
                    new Class[]{int.class},
                    getIntField(service, "mCurrentImeUserId", 0));
        }
    }

    private static int resolveStockImeNavButtonFlags(Object service, Object userData) {
        Object bindingController = getField(userData, "mBindingController");
        Object windowManagerInternal = getField(service, "mWindowManagerInternal");
        if (bindingController == null || windowManagerInternal == null) {
            return 0;
        }
        int displayId = invokeIntMethod(bindingController, "getCurTokenDisplayId", -1);
        if (displayId == -1) {
            displayId = 0;
        }
        boolean hasNavigationBar = invokeBooleanMethod(
                windowManagerInternal,
                "hasNavigationBar",
                new Class[]{int.class},
                false,
                displayId);
        Object imeDrawsNavBar = getField(userData, "mImeDrawsNavBar");
        boolean imeDraws = imeDrawsNavBar instanceof AtomicBoolean
                && ((AtomicBoolean) imeDrawsNavBar).get();
        int userId = getIntField(userData, "mUserId", 0);
        boolean shouldShowImeSwitcher = invokeBooleanMethod(
                service,
                "shouldShowImeSwitcherLocked",
                new Class[]{int.class, int.class},
                false,
                3,
                userId);
        return ((imeDraws && hasNavigationBar) ? 1 : 0) | (shouldShowImeSwitcher ? 2 : 0);
    }

    private static void applyStockImeWindowStatus(
            Object service, int imeWindowVis, int backDisposition, int userId) {
        boolean concurrentMultiUser = getBooleanField(service, "mConcurrentMultiUserModeEnabled");
        int currentImeUserId = getIntField(service, "mCurrentImeUserId", userId);
        if (!concurrentMultiUser && userId != currentImeUserId) {
            return;
        }
        Object userData = FlymeStatusBarSizer.invokeMethodCompat(
                service,
                "getUserData",
                new Class[]{int.class},
                userId);
        if (userData == null) {
            return;
        }
        Object bindingController = getField(userData, "mBindingController");
        if (bindingController == null) {
            return;
        }
        if (FlymeStatusBarSizer.invokeMethodCompat(bindingController, "getCurToken", new Class[0]) == null) {
            return;
        }
        int displayId = invokeIntMethod(bindingController, "getCurTokenDisplayId", 0);
        Object imeBindingState = getField(userData, "mImeBindingState");
        Object focusedWindow = getField(imeBindingState, "mFocusedWindow");
        Object focusedWindowPerceptible = getField(service, "mFocusedWindowPerceptible");
        if (focusedWindow != null && focusedWindowPerceptible != null) {
            Object perceptible = FlymeStatusBarSizer.invokeMethodCompat(
                    focusedWindowPerceptible,
                    "get",
                    new Class[]{Object.class},
                    focusedWindow);
            if (Boolean.FALSE.equals(perceptible)) {
                imeWindowVis &= ~2;
            }
        }
        Object menuController = getField(service, "mMenuController");
        Object switchingDialog = FlymeStatusBarSizer.invokeMethodCompat(
                menuController,
                "getSwitchingDialogLocked",
                new Class[0]);
        Object curId = FlymeStatusBarSizer.invokeMethodCompat(
                bindingController,
                "getCurId",
                new Class[0]);
        Object selectedMethodId = FlymeStatusBarSizer.invokeMethodCompat(
                bindingController,
                "getSelectedMethodId",
                new Class[0]);
        if (switchingDialog != null || !Objects.equals(curId, selectedMethodId)) {
            backDisposition = 3;
        }
        boolean shouldShowImeSwitcher = invokeBooleanMethod(
                service,
                "shouldShowImeSwitcherLocked",
                new Class[]{int.class, int.class},
                false,
                imeWindowVis,
                userId);
        Object statusBarManagerInternal = getField(service, "mStatusBarManagerInternal");
        if (statusBarManagerInternal != null) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    statusBarManagerInternal,
                    "setImeWindowStatus",
                    new Class[]{int.class, int.class, int.class, boolean.class},
                    displayId,
                    imeWindowVis,
                    backDisposition,
                    shouldShowImeSwitcher);
        }
    }

    private static Class<?> findClass(ClassLoader loader, String className) {
        if (loader == null || className == null) {
            return null;
        }
        try {
            return Class.forName(className, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getField(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean getBooleanField(Object target, String name) {
        Object value = getField(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static int getIntField(Object target, String name, int fallback) {
        Object value = getField(target, name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static void setIntField(Object target, String name, int value) {
        if (target == null || name == null) {
            return;
        }
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return;
        }
        try {
            field.setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static int invokeIntMethod(Object target, String name, int fallback) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, name, new Class[0]);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static float getFloatField(Object target, String name, float fallback) {
        Object value = getField(target, name);
        return value instanceof Float ? (Float) value : fallback;
    }

    private static boolean invokeBooleanMethod(
            Object target,
            String name,
            Class<?>[] parameterTypes,
            boolean fallback,
            Object... args) {
        Object value = FlymeStatusBarSizer.invokeMethodCompat(target, name, parameterTypes, args);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static void syncNavigationBarFrameBackgroundNow(
            View navigationBarFrame, Object inputMethodService) {
        if (navigationBarFrame == null) {
            return;
        }
        Drawable background = findImeBackgroundDrawable(inputMethodService);
        BackgroundSignature signature = BackgroundSignature.fromDrawable(background);
        StockControlBarFrameState frameState = getOrCreateStockControlBarFrameState(navigationBarFrame);
        if (signature.equals(frameState.backgroundSignature)) {
            return;
        }
        Drawable cloned = cloneDrawable(background, navigationBarFrame.getResources());
        navigationBarFrame.setBackground(cloned);
        frameState.backgroundSignature = signature;
    }

    private static void applyImeWindowNavigationBarAppearance(Object inputMethodService) {
        Object window = getImeWindow(inputMethodService);
        if (window == null) {
            return;
        }
        captureImeWindowAppearanceIfNeeded(inputMethodService, window);
        FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "setNavigationBarContrastEnforced",
                new Class[]{boolean.class},
                false);
        FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "setNavigationBarColor",
                new Class[]{int.class},
                0);
    }

    private static void restoreImeWindowNavigationBarAppearance(Object inputMethodService) {
        Object window = getImeWindow(inputMethodService);
        if (window == null) {
            return;
        }
        ImeWindowAppearanceState state = IME_WINDOW_APPEARANCE_STATES.get(inputMethodService);
        if (state == null || !state.captured) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "setNavigationBarContrastEnforced",
                new Class[]{boolean.class},
                state.navigationBarContrastEnforced);
        FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "setNavigationBarColor",
                new Class[]{int.class},
                state.navigationBarColor);
    }

    private static void captureImeWindowAppearanceIfNeeded(Object inputMethodService, Object window) {
        if (inputMethodService == null || window == null) {
            return;
        }
        ImeWindowAppearanceState existing = IME_WINDOW_APPEARANCE_STATES.get(inputMethodService);
        if (existing != null && existing.captured) {
            return;
        }
        ImeWindowAppearanceState captured = new ImeWindowAppearanceState();
        Object contrast = FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "isNavigationBarContrastEnforced",
                new Class[0]);
        captured.navigationBarContrastEnforced =
                !(contrast instanceof Boolean) || (Boolean) contrast;
        Object color = FlymeStatusBarSizer.invokeMethodCompat(
                window,
                "getNavigationBarColor",
                new Class[0]);
        captured.navigationBarColor = color instanceof Integer ? (Integer) color : 0;
        captured.captured = true;
        IME_WINDOW_APPEARANCE_STATES.put(inputMethodService, captured);
    }

    private static Object getImeWindow(Object inputMethodService) {
        if (inputMethodService == null) {
            return null;
        }
        Object softInputWindow = getField(inputMethodService, "mWindow");
        if (softInputWindow == null) {
            return null;
        }
        return FlymeStatusBarSizer.invokeMethodCompat(
                softInputWindow,
                "getWindow",
                new Class[0]);
    }

    private static Drawable findImeBackgroundDrawable(Object inputMethodService) {
        View trackedInputView = TRACKED_INPUT_METHOD_VIEWS.get(inputMethodService);
        Drawable background = trackedInputView != null ? trackedInputView.getBackground() : null;
        if (background != null) {
            return background;
        }
        ViewGroup inputFrame = asViewGroup(FlymeStatusBarSizer.getFieldCompat(inputMethodService, "mInputFrame"));
        if (inputFrame == null) {
            return null;
        }
        if (inputFrame.getBackground() != null) {
            return inputFrame.getBackground();
        }
        if (inputFrame.getChildCount() == 0) {
            return null;
        }
        return findPrimaryInputBackground(inputFrame.getChildAt(0));
    }

    private static Drawable findPrimaryInputBackground(View view) {
        if (view == null) {
            return null;
        }
        if (view.getBackground() != null) {
            return view.getBackground();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (group.getChildCount() > 0) {
                return findPrimaryInputBackground(group.getChildAt(0));
            }
        }
        return null;
    }

    private static Drawable cloneDrawable(Drawable drawable, Resources resources) {
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            Drawable cloned = resources != null
                    ? constantState.newDrawable(resources)
                    : constantState.newDrawable();
            if (cloned != null) {
                return cloned.mutate();
            }
        }
        if (drawable instanceof ColorDrawable) {
            return new ColorDrawable(((ColorDrawable) drawable).getColor());
        }
        return null;
    }

    private static ViewGroup asViewGroup(Object object) {
        return object instanceof ViewGroup ? (ViewGroup) object : null;
    }

    private static String resolveEmbeddedStockControlBarLayoutSpec() {
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(null);
        if (!ImeToolbarSpec.shouldReplaceOriginalControlBar(config)) {
            return null;
        }
        return ImeToolbarSpec.buildStockControlBarLayout(config);
    }

    private static String extractButtonName(String buttonSpec) {
        if (buttonSpec == null) {
            return "";
        }
        int sizeIndex = buttonSpec.indexOf('[');
        return sizeIndex >= 0 ? buttonSpec.substring(0, sizeIndex) : buttonSpec;
    }

    private static View createStockControlBarActionButton(Context context, String action) {
        if (context == null || !ImeToolbarSpec.isValidActionName(action)) {
            return null;
        }
        if (ImeToolbarSpec.isCaptchaButton(action)) {
            return createStockControlBarCaptchaButton(context);
        }
        return createBaseStockControlBarButton(
                context,
                action,
                ImeToolbarIcons.createIconDrawable(context, action),
                ImeToolbarSpec.getButtonLabel(action));
    }

    private static View createStockControlBarBackButton(Context context) {
        if (context == null) {
            return null;
        }
        return createBaseStockControlBarButton(
                context,
                STOCK_CONTROL_BAR_BACK,
                ImeToolbarIcons.createKeyboardDismissDrawable(context),
                ImeToolbarSpec.getButtonLabel(STOCK_CONTROL_BAR_BACK));
    }

    private static View createStockControlBarCaptchaButton(Context context) {
        TextView button = new TextView(context);
        button.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        button.setTag("captcha");
        button.setText(ImeToolbarSpec.getButtonLabel("captcha"));
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        int horizontalPadding = FlymeStatusBarSizer.dp(context, 6);
        button.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        button.setContentDescription(ImeToolbarSpec.getButtonLabel("captcha"));
        button.setBackground(resolveBorderlessSelectableBackground(context));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static View createStockControlBarPlaceholderView(Context context) {
        View placeholder = new View(context);
        placeholder.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        placeholder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        placeholder.setClickable(false);
        placeholder.setFocusable(false);
        return placeholder;
    }

    private static View createBaseStockControlBarButton(
            Context context, String tag, Drawable drawable, String contentDescription) {
        ImageButton button = new ImageButton(context);
        button.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        button.setTag(tag);
        button.setImageDrawable(drawable);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setContentDescription(contentDescription);
        button.setBackground(resolveBorderlessSelectableBackground(context));
        applyStockControlBarButtonVisualStyle(button, FlymeStatusBarSizer.loadImeConfig(null));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static Drawable resolveBorderlessSelectableBackground(Context context) {
        if (context != null) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    typedValue,
                    true)) {
                try {
                    return context.getDrawable(typedValue.resourceId);
                } catch (Throwable ignored) {
                }
            }
        }
        return new ColorDrawable(Color.TRANSPARENT);
    }

    private static void syncStockControlBarButtonsNow(
            View navigationBarFrame, Object inputMethodService, float darkIntensity) {
        if (navigationBarFrame == null) {
            return;
        }
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(null);
        if (ImeToolbarSpec.shouldReplaceOriginalControlBar(config)) {
            ensureFlymeCaptchaBridgeView(navigationBarFrame);
        }
        ImeToolbarActions.refreshFlymeCaptchaCandidate(navigationBarFrame.getContext());
        scheduleCaptchaButtonExpiryRefresh(navigationBarFrame);
        StockControlBarFrameState frameState = getOrCreateStockControlBarFrameState(navigationBarFrame);
        bindStockControlBarButtonsIfNeeded(frameState, inputMethodService);
        refreshStockControlBarButtonStates(frameState, inputMethodService);
        applyStockControlBarVerticalOffset(navigationBarFrame, config);
        applyStockControlBarButtonTint(frameState, navigationBarFrame, darkIntensity, config);
    }

    private static void applyStockControlBarVerticalOffset(
            View navigationBarFrame, FlymeStatusBarSizer.ImeConfigSnapshot config) {
        if (navigationBarFrame == null) {
            return;
        }
        float targetTranslationY = 0f;
        if (config != null && config.enabled) {
            targetTranslationY = -positionOffsetTenthDpToPx(
                    navigationBarFrame,
                    config.imeControlBarYOffsetTenthDp);
        }
        if (navigationBarFrame.getTranslationY() != targetTranslationY) {
            navigationBarFrame.setTranslationY(targetTranslationY);
        }
    }

    private static float positionOffsetTenthDpToPx(View view, int valueTenthDp) {
        if (view == null) {
            return 0f;
        }
        return (valueTenthDp / 10f) * view.getResources().getDisplayMetrics().density;
    }

    private static void applyStockControlBarButtonTint(
            StockControlBarFrameState frameState,
            View root,
            float darkIntensity,
            FlymeStatusBarSizer.ImeConfigSnapshot config) {
        if (root == null) {
            return;
        }
        int color = ImeToolbarIcons.resolveStockControlBarIconColor(root.getContext());
        int alpha = resolveStockControlBarIconAlpha(config);
        int padding = resolveStockControlBarButtonPaddingPx(root, config);
        ButtonRenderSignature signature = new ButtonRenderSignature(color, alpha, padding);
        if (signature.equals(frameState.buttonRenderSignature)) {
            return;
        }
        for (int i = 0; i < frameState.buttonRefs.size(); i++) {
            View button = frameState.buttonRefs.get(i).view;
            if (!(button instanceof ImageView)) {
                if (button instanceof TextView) {
                    ((TextView) button).setTextColor(applyAlphaToColor(color, alpha));
                }
                continue;
            }
            ImageView imageView = (ImageView) button;
            imageView.setColorFilter(color);
            imageView.setImageAlpha(alpha);
            applyStockControlBarButtonVisualStyle(imageView, config);
        }
        frameState.buttonRenderSignature = signature;
    }

    private static int applyAlphaToColor(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int resolveStockControlBarIconAlpha(FlymeStatusBarSizer.ImeConfigSnapshot config) {
        int percent = config == null
                ? DEFAULT_IME_ICON_ALPHA_PERCENT
                : config.imeControlBarIconAlphaPercent;
        percent = Math.max(10, Math.min(100, percent));
        return Math.round((percent / 100f) * 255f);
    }

    private static void applyStockControlBarButtonVisualStyle(
            ImageView imageView, FlymeStatusBarSizer.ImeConfigSnapshot config) {
        if (imageView == null) {
            return;
        }
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = resolveStockControlBarButtonPaddingPx(imageView, config);
        if (imageView.getPaddingLeft() == padding
                && imageView.getPaddingTop() == padding
                && imageView.getPaddingRight() == padding
                && imageView.getPaddingBottom() == padding) {
            return;
        }
        imageView.setPadding(padding, padding, padding, padding);
    }

    private static int resolveStockControlBarButtonPaddingPx(
            View view, FlymeStatusBarSizer.ImeConfigSnapshot config) {
        if (view == null) {
            return 0;
        }
        int scalePercent = config == null
                ? DEFAULT_IME_ICON_SCALE_PERCENT
                : config.imeControlBarIconScalePercent;
        scalePercent = Math.max(60, Math.min(180, scalePercent));
        float density = view.getResources().getDisplayMetrics().density;
        return Math.max(0, Math.round((8f * 100f / scalePercent) * density));
    }

    private static void bindStockControlBarButtonsIfNeeded(
            StockControlBarFrameState frameState, Object inputMethodService) {
        if (frameState == null || frameState.boundInputMethodService == inputMethodService) {
            return;
        }
        for (int i = 0; i < frameState.buttonRefs.size(); i++) {
            StockControlBarButtonRef buttonRef = frameState.buttonRefs.get(i);
            if (STOCK_CONTROL_BAR_BACK.equals(buttonRef.tag)) {
                buttonRef.view.setOnClickListener(v -> {
                    performStockControlBarButtonHapticFeedback(v);
                    sendBackToHideKeyboard(inputMethodService);
                });
            } else {
                ImeToolbarActions.bindActionButtonView(inputMethodService, buttonRef.view);
            }
        }
        frameState.boundInputMethodService = inputMethodService;
    }

    private static void refreshStockControlBarButtonStates(
            StockControlBarFrameState frameState, Object inputMethodService) {
        if (frameState == null) {
            return;
        }
        for (int i = 0; i < frameState.buttonRefs.size(); i++) {
            StockControlBarButtonRef buttonRef = frameState.buttonRefs.get(i);
            if (STOCK_CONTROL_BAR_BACK.equals(buttonRef.tag)) {
                buttonRef.view.setEnabled(true);
                buttonRef.view.setAlpha(1f);
            } else {
                ImeToolbarActions.refreshActionButtonState(inputMethodService, buttonRef.view);
            }
        }
    }

    private static StockControlBarFrameState getOrCreateStockControlBarFrameState(View root) {
        StockControlBarFrameState state = STOCK_CONTROL_BAR_FRAME_STATES.get(root);
        if (state == null) {
            state = new StockControlBarFrameState();
            STOCK_CONTROL_BAR_FRAME_STATES.put(root, state);
        }
        ensureStockControlBarButtonCache(root, state);
        return state;
    }

    private static void ensureStockControlBarButtonCache(View root, StockControlBarFrameState state) {
        if (root == null || state == null) {
            return;
        }
        if (state.buttonCacheInitialized && areCachedStockControlBarButtonsAlive(root, state)) {
            return;
        }
        state.buttonRefs.clear();
        collectStockControlBarButtons(root, state.buttonRefs);
        state.buttonCacheInitialized = true;
        state.boundInputMethodService = null;
        state.buttonRenderSignature = null;
    }

    private static boolean areCachedStockControlBarButtonsAlive(
            View root, StockControlBarFrameState state) {
        if (!state.buttonCacheInitialized) {
            return false;
        }
        for (int i = 0; i < state.buttonRefs.size(); i++) {
            StockControlBarButtonRef buttonRef = state.buttonRefs.get(i);
            if (buttonRef == null
                    || buttonRef.view == null
                    || !buttonRef.tag.equals(buttonRef.view.getTag())
                    || !isDescendantOrSelf(root, buttonRef.view)) {
                return false;
            }
        }
        return true;
    }

    private static void collectStockControlBarButtons(
            View root, ArrayList<StockControlBarButtonRef> outRefs) {
        if (root == null || outRefs == null) {
            return;
        }
        if (root.getTag() instanceof String) {
            String tag = (String) root.getTag();
            if (ImeToolbarSpec.isValidButtonName(tag)) {
                outRefs.add(new StockControlBarButtonRef(tag, root));
            }
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectStockControlBarButtons(group.getChildAt(i), outRefs);
        }
    }

    private static boolean isDescendantOrSelf(View root, View candidate) {
        View current = candidate;
        while (current != null) {
            if (current == root) {
                return true;
            }
            if (!(current.getParent() instanceof View)) {
                return false;
            }
            current = (View) current.getParent();
        }
        return false;
    }

    private static void clearStockControlBarFrameStateCache(View root) {
        if (root == null) {
            return;
        }
        STOCK_CONTROL_BAR_FRAME_STATES.remove(root);
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            clearStockControlBarFrameStateCache(group.getChildAt(i));
        }
    }

    private static void performStockControlBarButtonHapticFeedback(View button) {
        if (button == null) {
            return;
        }
        try {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }

    private static void sendBackToHideKeyboard(Object inputMethodService) {
        if (inputMethodService instanceof InputMethodService) {
            try {
                ((InputMethodService) inputMethodService).requestHideSelf(0);
                return;
            } catch (Throwable ignored) {
            }
        }
        Object hideResult = FlymeStatusBarSizer.invokeMethodCompat(
                inputMethodService,
                "requestHideSelf",
                new Class[]{int.class},
                0);
        if (hideResult != null || inputMethodService == null) {
            return;
        }
        Object handled = FlymeStatusBarSizer.invokeMethodCompat(
                inputMethodService,
                "handleBack",
                new Class[]{boolean.class},
                true);
        if (Boolean.TRUE.equals(handled)) {
            return;
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                inputMethodService,
                "hideWindow",
                new Class[0]);
    }

    private static final class ImeWindowAppearanceState {
        boolean captured;
        boolean navigationBarContrastEnforced;
        int navigationBarColor;
    }

    private static final class StockControlBarFrameState {
        final ArrayList<StockControlBarButtonRef> buttonRefs =
                new ArrayList<>(ImeToolbarSpec.getButtonSlotCount());
        boolean buttonCacheInitialized;
        Object boundInputMethodService;
        ButtonRenderSignature buttonRenderSignature;
        BackgroundSignature backgroundSignature;
    }

    private static final class StockControlBarButtonRef {
        final String tag;
        final View view;

        StockControlBarButtonRef(String tag, View view) {
            this.tag = tag;
            this.view = view;
        }
    }

    private static final class ButtonRenderSignature {
        final int color;
        final int alpha;
        final int padding;

        ButtonRenderSignature(int color, int alpha, int padding) {
            this.color = color;
            this.alpha = alpha;
            this.padding = padding;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ButtonRenderSignature)) {
                return false;
            }
            ButtonRenderSignature signature = (ButtonRenderSignature) other;
            return color == signature.color
                    && alpha == signature.alpha
                    && padding == signature.padding;
        }

        @Override
        public int hashCode() {
            return Objects.hash(color, alpha, padding);
        }
    }

    private static final class BackgroundSignature {
        private static final BackgroundSignature EMPTY = new BackgroundSignature(null, 0, null, 0);

        final Drawable.ConstantState constantState;
        final int color;
        final String drawableClassName;
        final int identityHash;

        BackgroundSignature(
                Drawable.ConstantState constantState,
                int color,
                String drawableClassName,
                int identityHash) {
            this.constantState = constantState;
            this.color = color;
            this.drawableClassName = drawableClassName;
            this.identityHash = identityHash;
        }

        static BackgroundSignature empty() {
            return EMPTY;
        }

        static BackgroundSignature fromDrawable(Drawable drawable) {
            if (drawable == null) {
                return EMPTY;
            }
            Drawable.ConstantState constantState = drawable.getConstantState();
            int color = drawable instanceof ColorDrawable ? ((ColorDrawable) drawable).getColor() : 0;
            int identityHash = constantState == null ? System.identityHashCode(drawable) : 0;
            return new BackgroundSignature(
                    constantState,
                    color,
                    drawable.getClass().getName(),
                    identityHash);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BackgroundSignature)) {
                return false;
            }
            BackgroundSignature signature = (BackgroundSignature) other;
            return constantState == signature.constantState
                    && color == signature.color
                    && identityHash == signature.identityHash
                    && Objects.equals(drawableClassName, signature.drawableClassName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    System.identityHashCode(constantState),
                    color,
                    drawableClassName,
                    identityHash);
        }
    }
}
