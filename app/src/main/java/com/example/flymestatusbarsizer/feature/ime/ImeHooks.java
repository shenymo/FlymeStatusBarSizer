package com.example.flymestatusbarsizer.feature.ime;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ImeHooks {
    private static final String PACKAGE_ANDROID = "android";
    private static final String PACKAGE_ANDROID_LATIN = "com.android.inputmethod.latin";
    private static final String PACKAGE_FLYME_INPUTMETHOD = "flyme.inputmethod";
    private static final String PACKAGE_GOOGLE_LATIN = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_TENCENT_WETYPE = "com.tencent.wetype";

    private static final WeakHashMap<Object, View> TRACKED_INPUT_METHOD_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> TRACKED_INPUT_METHOD_MANAGER_SERVICES =
            new WeakHashMap<>();
    private static final WeakHashMap<Object, Boolean> LAST_STOCK_CONTROL_BAR_STATES =
            new WeakHashMap<>();

    private ImeHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader, String packageName) {
        if (module == null || loader == null || packageName == null) {
            return;
        }
        if (isImeClientPackage(packageName)) {
            hookNavigationBarInflaterOnFinishInflate(module, loader);
            hookNavigationBarViewSetNavbarFlags(module, loader);
            hookNavigationBarInsetsVisibility(module, loader);
            hookNavigationBarBackgroundRefresh(module, loader);
            hookInputMethodService(module, loader);
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
                ImeToolbarController.refreshToolbarNow(inputMethodService, inputView);
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

    private static boolean shouldForceStockImeControlBar() {
        FlymeStatusBarSizer.ImeConfigSnapshot config = FlymeStatusBarSizer.loadImeConfig(null);
        return config.enabled && config.imeForceStockControlBar;
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
                if (!shouldForceStockImeControlBar()) {
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
                if (!shouldForceStockImeControlBar()
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
                if (!shouldForceStockImeControlBar()) {
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
                if (!shouldForceStockImeControlBar()) {
                    return chain.proceed();
                }
                Object thisObject = chain.getThisObject();
                if (thisObject == null) {
                    return Boolean.FALSE;
                }
                Object result = chain.proceed();
                Object navigationBarFrame = getField(thisObject, "mNavigationBarFrame");
                Object inputMethodService = getField(thisObject, "mService");
                syncImeWindowNavigationBarAppearance(inputMethodService);
                if (navigationBarFrame instanceof View) {
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
                inputView.post(() -> {
                    ImeToolbarController.attachToolbarIfNeeded(thisObject, inputView);
                    refreshImeControlBarNow(thisObject);
                });
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
                    inputView.post(() -> {
                        ImeToolbarController.attachToolbarIfNeeded(thisObject, inputView);
                        refreshImeControlBarNow(thisObject);
                    });
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
                    inputView.post(() -> {
                        ImeToolbarController.attachToolbarIfNeeded(thisObject, inputView);
                        refreshImeControlBarNow(thisObject);
                    });
                }
                return result;
            });
        } catch (Throwable t) {
            FlymeStatusBarSizer.logImeWarning("Failed to hook InputMethodService.setInputView", t);
        }
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
                if (!shouldForceStockImeControlBar()) {
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
                if (!shouldForceStockImeControlBar() || service == null) {
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
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarInflaterView,
                "inflateLayout",
                new Class[]{String.class},
                defaultLayout instanceof String ? (String) defaultLayout : null);
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
        boolean forceStock = shouldForceStockImeControlBar();
        Boolean lastAppliedState = LAST_STOCK_CONTROL_BAR_STATES.get(inputMethodService);
        if (lastAppliedState == null || lastAppliedState.booleanValue() != forceStock) {
            FlymeStatusBarSizer.invokeMethodCompat(
                    callbackImpl,
                    "uninstallNavigationBarFrameIfNecessary",
                    new Class[0]);
        }
        if (forceStock) {
            syncImeWindowNavigationBarAppearance(inputMethodService);
        }
        FlymeStatusBarSizer.invokeMethodCompat(
                navigationBarController,
                "onNavButtonFlagsChanged",
                new Class[]{int.class},
                resolveCurrentImeNavButtonFlags(callbackImpl));
        Object navigationBarFrame = getField(callbackImpl, "mNavigationBarFrame");
        if (navigationBarFrame instanceof View) {
            if (forceStock) {
                syncNavigationBarFrameBackgroundNow((View) navigationBarFrame, inputMethodService);
            }
            ((View) navigationBarFrame).requestApplyInsets();
        }
        LAST_STOCK_CONTROL_BAR_STATES.put(inputMethodService, forceStock);
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
        Drawable cloned = cloneDrawable(background, navigationBarFrame.getResources());
        navigationBarFrame.setBackground(cloned);
    }

    private static void syncImeWindowNavigationBarAppearance(Object inputMethodService) {
        if (inputMethodService == null) {
            return;
        }
        Object softInputWindow = getField(inputMethodService, "mWindow");
        if (softInputWindow == null) {
            return;
        }
        Object window = FlymeStatusBarSizer.invokeMethodCompat(
                softInputWindow,
                "getWindow",
                new Class[0]);
        if (window == null) {
            return;
        }
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
}
