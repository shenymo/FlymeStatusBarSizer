package com.example.flymestatusbarsizer.feature.launcher;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Flyme-specific access is kept outside the gesture controller. */
final class LauncherPageIndicatorHooks {
    private static final String LAUNCHER = "com.meizu.flyme.launcher.MzLauncher";
    private static final String INDICATOR = "com.meizu.flyme.launcher.view.MzIconPageIndicator";
    private static final String DOTS =
            "com.meizu.flyme.launcher.view.indicator.FlymeWorkspaceIndicatorView";
    private static final int PRIVACY_SCREEN_ID = 100000000;
    private static final int SNAP_DURATION_MS = 120;

    private final LauncherPageIndicatorTouchController touch =
            new LauncherPageIndicatorTouchController();
    private final Binding binding;
    private Activity owner;
    private IndicatorHost activeHost;
    private boolean loggedFailure;

    private LauncherPageIndicatorHooks(ClassLoader loader) throws ReflectiveOperationException {
        binding = new Binding(loader);
    }

    static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            LauncherPageIndicatorHooks hooks = new LauncherPageIndicatorHooks(loader);
            // Suppress both new timers and a previously queued search transition while dragging.
            module.intercept(hooks.binding.showSearch, chain -> hooks.holds(chain.getThisObject())
                    ? null : chain.proceed());
            module.intercept(hooks.binding.showSearchInternal,
                    chain -> hooks.holds(chain.getThisObject()) ? null : chain.proceed());
            module.intercept(hooks.binding.pause, chain -> {
                if (hooks.owner == chain.getThisObject()) hooks.finish();
                return chain.proceed();
            });
            module.intercept(hooks.binding.dispatch, chain -> {
                Activity activity = (Activity) chain.getThisObject();
                MotionEvent event = (MotionEvent) chain.getArg(0);
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    hooks.finish();
                    if (FlymeStatusBarSizer.loadLauncherAppearanceConfig(activity)
                            .launcherPageIndicatorSwipeEnabled) {
                        hooks.prepare(activity, event);
                    }
                }
                if (hooks.owner != activity) return chain.proceed();
                Object result = hooks.touch.onTouch(event,
                        forwarded -> chain.proceed(new Object[]{forwarded}));
                if (!hooks.touch.hasGesture()) {
                    hooks.activeHost = null;
                    hooks.owner = null;
                }
                return result;
            });
        } catch (Throwable error) {
            FlymeStatusBarSizer.logLauncherWarning("Failed to hook launcher indicator swipe", error);
        }
    }

    private boolean holds(Object indicator) {
        return touch.isDragging() && activeHost != null && activeHost.container == indicator;
    }

    private void prepare(Activity activity, MotionEvent event) {
        try {
            IndicatorHost host = new IndicatorHost(activity);
            if (!host.isAvailable()) return;
            LauncherPageIndicatorTouchController.Targets targets = host.readTargets();
            if (targets == null || !targets.bounds.contains(event.getRawX(), event.getRawY())) return;
            activeHost = host;
            owner = activity;
            touch.prepare(host, targets, ViewConfiguration.get(activity).getScaledTouchSlop(),
                    2f * activity.getResources().getDisplayMetrics().density);
        } catch (Exception error) {
            logFailure(error);
        }
    }

    private void finish() {
        touch.finish();
        activeHost = null;
        owner = null;
    }

    private void logFailure(Exception error) {
        if (!loggedFailure) {
            loggedFailure = true;
            FlymeStatusBarSizer.logLauncherWarning("Launcher indicator swipe unavailable", error);
        }
    }

    private final class IndicatorHost implements LauncherPageIndicatorTouchController.Host {
        final Activity activity;
        final Object workspace;
        final View container;
        final View dots;
        final int[] screenIds;

        IndicatorHost(Activity activity) throws Exception {
            this.activity = activity;
            workspace = binding.workspace.invoke(activity);
            container = (View) binding.indicator.invoke(workspace);
            dots = container == null ? null : (View) binding.dots.invoke(container);
            int count = ((Number) binding.pageCount.invoke(workspace)).intValue();
            screenIds = new int[count];
            for (int i = 0; i < count; i++) {
                screenIds[i] = ((Number) binding.screenId.invoke(workspace, i)).intValue();
            }
        }

        @Override public boolean isAvailable() throws Exception {
            if (container == null || dots == null || !binding.dotsClass.isInstance(dots)
                    || !container.isShown() || container.getAlpha() <= 0f
                    || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }
            Object manager = binding.stateManager.invoke(activity);
            if (binding.state.invoke(manager) != binding.normalState
                    || (Boolean) binding.inTransition.invoke(manager)
                    || (Boolean) binding.editPanelOpen.invoke(activity)
                    || (Boolean) binding.workspaceLocked.invoke(activity)
                    || (Boolean) binding.switchingState.invoke(workspace)
                    || (Boolean) binding.locationAnimation.invoke(workspace)
                    || (Boolean) binding.overlayVisible.invoke(workspace)
                    || binding.topOpenView.invoke(null, activity) != null
                    || (Boolean) binding.dragging.invoke(binding.dragController.invoke(activity))) {
                return false;
            }
            int current = ((Number) binding.currentPage.invoke(workspace)).intValue();
            if (current < 0 || current >= screenIds.length || !isDesktopScreen(screenIds[current])) {
                return false;
            }
            // Page indices can change during loading/removal. Stop this gesture rather than jump
            // using a stale index, and rebuild the mapping on the next DOWN.
            if (((Number) binding.pageCount.invoke(workspace)).intValue() != screenIds.length) return false;
            for (int i = 0; i < screenIds.length; i++) {
                if (((Number) binding.screenId.invoke(workspace, i)).intValue() != screenIds[i]) return false;
            }
            return true;
        }

        LauncherPageIndicatorTouchController.Targets readTargets() throws Exception {
            Object value = binding.screens.get(container);
            if (!(value instanceof List<?>)) return null;
            List<?> dotScreens = (List<?>) value;
            // Removed dots may remain in the animation list briefly after the adapter changes.
            if (binding.paramsAt.invoke(dots, dotScreens.size()) != null) return null;
            View root = container.getRootView();
            int[] origin = new int[2];
            root.getLocationOnScreen(origin);
            List<PageTarget> pages = new ArrayList<>();
            RectF bounds = new RectF();
            RectF dotBounds = new RectF();
            for (int i = 0; i < screenIds.length; i++) {
                if (!isDesktopScreen(screenIds[i])) continue;
                int dotIndex = dotScreens.indexOf(screenIds[i]);
                if (dotIndex < 0) return null;
                Object params = binding.paramsAt.invoke(dots, dotIndex);
                if (params == null || (Boolean) binding.deferRemove.invoke(params)
                        || (Boolean) binding.deferAdd.invoke(params)) return null;
                dotBounds.setEmpty();
                binding.dotCoordinates.invoke(dots, dotIndex, dotBounds, root);
                if (dotBounds.isEmpty()) return null;
                dotBounds.offset(origin[0], origin[1]);
                pages.add(new PageTarget(i, dotBounds.centerX()));
                bounds.union(dotBounds);
            }
            if (pages.size() < 2) return null;
            // Coordinates, rather than page-number ordering, also cover RTL layouts.
            pages.sort(Comparator.comparingDouble(page -> page.x));
            float density = activity.getResources().getDisplayMetrics().density;
            float halfWidth = Math.max(bounds.width() / 2f + 20f * density, 48f * density);
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();
            bounds.set(centerX - halfWidth, centerY - 22f * density,
                    centerX + halfWidth, centerY + 22f * density);
            Rect visible = new Rect();
            View search = (View) binding.searchView.get(container);
            if (search != null && search.isShown() && search.getAlpha() > 0f
                    && search.getGlobalVisibleRect(visible)) {
                visible.offset(origin[0], origin[1]);
                bounds.union(new RectF(visible));
            }
            // Bound the expanded touch area to the indicator container, away from dock icons.
            if (!container.getGlobalVisibleRect(visible)) return null;
            visible.offset(origin[0], origin[1]);
            if (!bounds.intersect(new RectF(visible))) return null;
            int[] indices = new int[pages.size()];
            float[] centers = new float[pages.size()];
            for (int i = 0; i < pages.size(); i++) {
                indices[i] = pages.get(i).index;
                centers[i] = pages.get(i).x;
                if (i > 0 && centers[i] <= centers[i - 1]) return null;
            }
            return new LauncherPageIndicatorTouchController.Targets(indices, centers, bounds);
        }

        @Override public void showDots() throws Exception {
            binding.showIndicator.invoke(container, false);
        }

        @Override public void restoreSearch() throws Exception {
            if (isAvailable()) binding.showSearch.invoke(container, true);
        }

        @Override public void snapToPage(int page) throws Exception {
            if (((Number) binding.nextPage.invoke(workspace)).intValue() != page) {
                binding.snap.invoke(workspace, page, SNAP_DURATION_MS);
            }
        }

        @Override public void onFailure(Exception error) {
            logFailure(error);
        }
    }

    static boolean isDesktopScreen(int screenId) {
        return screenId >= 0 && screenId != PRIVACY_SCREEN_ID;
    }

    private static final class PageTarget {
        final int index;
        final float x;

        PageTarget(int index, float x) {
            this.index = index;
            this.x = x;
        }
    }

    private static final class Binding {
        final Class<?> dotsClass;
        final Object normalState;
        final Method dispatch, pause, workspace, indicator, dots, pageCount, screenId,
                stateManager, state, inTransition, editPanelOpen, workspaceLocked, switchingState,
                locationAnimation, overlayVisible, topOpenView, dragController, dragging, currentPage,
                dotCoordinates, paramsAt, deferRemove, deferAdd,
                showIndicator, showSearch, showSearchInternal, nextPage, snap;
        final Field screens, searchView;

        Binding(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> launcher = Class.forName(LAUNCHER, false, loader);
            Class<?> indicatorClass = Class.forName(INDICATOR, false, loader);
            dotsClass = Class.forName(DOTS, false, loader);
            Class<?> dotParams = Class.forName("com.meizu.flyme.launcher.view.indicator.DotParams", false, loader);
            Class<?> workspaceClass = Class.forName("com.meizu.flyme.launcher.workspace.FlymeWorkspace", false, loader);
            Class<?> manager = Class.forName("com.android.launcher3.statemanager.StateManager", false, loader);
            Class<?> floating = Class.forName("com.android.launcher3.AbstractFloatingView", false, loader);
            Class<?> activityContext = Class.forName("com.android.launcher3.views.ActivityContext", false, loader);
            Class<?> drag = Class.forName("com.android.launcher3.dragndrop.DragController", false, loader);
            normalState = Class.forName("com.android.launcher3.LauncherState", false, loader)
                    .getField("NORMAL").get(null);
            dispatch = launcher.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            pause = launcher.getDeclaredMethod("onPause");
            pause.setAccessible(true);
            workspace = launcher.getMethod("getWorkspace");
            indicator = workspaceClass.getMethod("getMzPageIndicator");
            dots = indicatorClass.getMethod("getMzPageIndicatorView");
            pageCount = workspaceClass.getMethod("getPageCount");
            screenId = workspaceClass.getMethod("getScreenIdForPageIndex", int.class);
            currentPage = workspaceClass.getMethod("getCurrentPage");
            nextPage = workspaceClass.getMethod("getNextPage");
            snap = workspaceClass.getMethod("snapToPage", int.class, int.class);
            stateManager = launcher.getMethod("getStateManager");
            state = manager.getMethod("getState");
            inTransition = manager.getMethod("isInTransition");
            editPanelOpen = launcher.getMethod("isEditPanelOpen");
            workspaceLocked = launcher.getMethod("isWorkspaceLocked");
            switchingState = workspaceClass.getMethod("isSwitchingState");
            locationAnimation = workspaceClass.getMethod("isLocationAppAnimationing");
            overlayVisible = workspaceClass.getMethod("isOverlayVisible");
            topOpenView = floating.getMethod("getTopOpenView", activityContext);
            dragController = launcher.getMethod("getDragController");
            dragging = drag.getMethod("isDragging");
            screens = indicatorClass.getDeclaredField("mWorkspaceScreens");
            screens.setAccessible(true);
            searchView = indicatorClass.getDeclaredField("mIndicatorSearchView");
            searchView.setAccessible(true);
            dotCoordinates = dotsClass.getMethod("getIndicatorCoordRelativeToAncestor",
                    int.class, RectF.class, View.class);
            paramsAt = dotsClass.getMethod("getParamsAt", int.class);
            deferRemove = dotParams.getMethod("isDeferRemove");
            deferAdd = dotParams.getMethod("isDeferAdd");
            showIndicator = indicatorClass.getMethod("showIndicator", boolean.class);
            showSearch = indicatorClass.getMethod("showSearch", boolean.class);
            showSearchInternal = indicatorClass.getDeclaredMethod("showSearchInternal");
            showSearchInternal.setAccessible(true);
        }
    }
}
