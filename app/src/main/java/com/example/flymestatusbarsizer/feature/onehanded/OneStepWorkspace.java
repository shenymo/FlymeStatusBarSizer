package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Live OneStep panes hosted entirely in the LSPosed-injected SystemUI process. */
final class OneStepWorkspace {
    private static final String TAG = "FlymeOneStep";
    private static final int COUNT = 4;
    private static final float MAIN_REFRESH_RATE = 120f;
    private static final float SIDE_REFRESH_RATE = 30f;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final long ENTER_DURATION_MS = 300;
    private static final long EXIT_DURATION_MS = 200;
    private static final long BACKDROP_FADE_DURATION_MS = 140;
    private static final long TASK_CHECK_DEBOUNCE_MS = 100;
    private static final long TASK_CHECK_FALLBACK_MS = 3000;
    private static final PathInterpolator ENTER_INTERPOLATOR = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator EXIT_INTERPOLATOR = new PathInterpolator(0.4f, 0f, 1f, 1f);
    // OneStep4 defaults: media 116dp + navigation (26 + 20)dp + app strip 74dp.
    // Status/cutout insets are already excluded from this overlay's bounds.
    private static final int REFERENCE_TOP_AREA_DP = 116 + 46 + 74;
    // OneStep4's trusted, touch-capable, independently focused display configuration.
    // Do not use DESTROY_CONTENT_ON_REMOVAL: SystemUI death must return apps to the main display.
    private static final int DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | (1 << 6) | (1 << 10) | (1 << 14);
    private final Handler handler;
    private final Context context;
    private final WindowManager windows;
    private final DisplayManager displays;
    private final OneStepTaskAccess tasks;
    private final OneStepTouchMode touchMode = new OneStepTouchMode();
    private final Pane[] panes = new Pane[COUNT];
    private final ArrayList<Integer> sideOrder = new ArrayList<>();
    private final Runnable check = this::check;
    private final Runnable taskChanged = this::onTasksChanged;
    private final Runnable restoreRetry = this::restoreAndDismiss;
    private final Executor taskWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeOneStepTasks");
        thread.setDaemon(true);
        return thread;
    });
    private boolean taskNotifications;
    private boolean checkInFlight;
    private boolean checkAgain;
    private boolean restoring;
    private int restoreRequest;
    private long nextCheckTime;
    private int taskStateVersion;
    private FrameLayout backdrop;
    private FrameLayout workspace;
    private final ArrayList<ImageView> recentIcons = new ArrayList<>();
    private final Executor iconLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeOneStepIcons");
        thread.setDaemon(true);
        return thread;
    });
    private DragSession dragSession;
    private ImageView dragSource;
    private int highlightedSlot = -1;
    private Rect[] frames;
    private ValueAnimator animator;
    private ValueAnimator transitionAnimator;
    private float transitionProgress;
    private boolean animateClose;
    private boolean focusMainOnClose;
    private volatile boolean active;
    private volatile boolean closing;
    private volatile boolean opening;
    private boolean mainOnLeft;
    private int mainSlot;
    private int width;
    private int height;
    private int virtualWidth;
    private int virtualHeight;
    private int density;
    private int generation;
    private long deadline;
    private Runnable onSuccess;
    private int restoreFocusTask = -1;
    private int restoreMainTask = -1;
    private int primaryTask = -1;
    private boolean started;
    private int restoreAttempts;

    OneStepWorkspace(Context source, Handler handler) throws ReflectiveOperationException {
        this.handler = handler;
        displays = source.getSystemService(DisplayManager.class);
        context = source.createDisplayContext(displays.getDisplay(0))
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windows = context.getSystemService(WindowManager.class);
        tasks = new OneStepTaskAccess(source);
        try {
            tasks.registerTaskChanges(() -> {
                if (!active || closing) return;
                handler.removeCallbacks(taskChanged);
                handler.post(taskChanged);
            });
            taskNotifications = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Task notifications unavailable; using background polling", e);
        }
        displays.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) {}
            @Override public void onDisplayRemoved(int id) {
                if (!active || closing) return;
                for (Pane pane : panes) if (pane != null && pane.id() == id) {
                    stop("virtual display removed");
                    return;
                }
            }
            @Override public void onDisplayChanged(int id) {
                if (!active || closing) return;
                for (Pane pane : panes) if (pane != null && pane.id() == id) pane.updateInputSize();
            }
        }, handler);
    }

    boolean canTrigger() {
        if (opening || closing || dragSession != null || !OneHandedTaskHooks.environmentAllowed(context)) return false;
        if (active) return true;
        try { return tasks.focusedTask() != null; }
        catch (Exception e) { return false; }
    }

    void toggle(boolean fromLeft, Runnable success) {
        handler.post(() -> {
            if (opening || closing) return;
            if (active) { close(true); return; }
            if (!OneHandedTaskHooks.environmentAllowed(context)) return;
            opening = true;
            int requestGeneration = ++generation;
            taskWorker.execute(() -> {
                try {
                    RecentTaskCard main = tasks.focusedTask();
                    List<RecentTaskCard> recent = main == null ? null : tasks.candidates();
                    handler.post(() -> {
                        if (!opening || generation != requestGeneration) return;
                        opening = false;
                        if (main == null || !OneHandedTaskHooks.environmentAllowed(context)) return;
                        try { open(main, recent, fromLeft, success); }
                        catch (Exception e) { fail("无法打开多应用工作台", e); }
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        if (!opening || generation != requestGeneration) return;
                        opening = false;
                        fail("无法打开多应用工作台", e);
                    });
                }
            });
        });
    }

    private void open(RecentTaskCard main, List<RecentTaskCard> recent, boolean fromLeft, Runnable success)
            throws Exception {
        WindowMetrics metrics = windows.getMaximumWindowMetrics();
        Rect screen = new Rect(metrics.getBounds());
        Rect bounds = new Rect(screen);
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        bounds.inset(insets.left, insets.top, insets.right, insets.bottom);
        // Keep the compact OneStep application height inside a separate full-screen backdrop.
        // The toolbar sits directly above the panes; the remaining area contains only a solid color.
        int topSpace = Math.min(dp(REFERENCE_TOP_AREA_DP - TOOLBAR_HEIGHT_DP),
                Math.max(0, bounds.height() - dp(TOOLBAR_HEIGHT_DP + 160)));
        bounds.top += topSpace;
        width = bounds.width();
        height = bounds.height();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid workspace bounds");
        Display primaryDisplay = displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (primaryDisplay == null) throw new IllegalStateException("Primary display unavailable");
        DisplayMetrics primaryMetrics = new DisplayMetrics();
        primaryDisplay.getRealMetrics(primaryMetrics);
        if (primaryMetrics.widthPixels <= 0 || primaryMetrics.densityDpi <= 0) {
            throw new IllegalStateException("Invalid primary display metrics");
        }
        active = true;
        generation++;
        closing = false;
        restoreFocusTask = -1;
        restoreMainTask = -1;
        primaryTask = -1;
        started = false;
        restoreAttempts = 0;
        restoring = false;
        checkInFlight = false;
        checkAgain = false;
        nextCheckTime = 0L;
        taskStateVersion++;
        animateClose = false;
        focusMainOnClose = false;
        mainSlot = 0;
        mainOnLeft = fromLeft;
        onSuccess = success;
        deadline = SystemClock.uptimeMillis() + 7000;
        sideOrder.clear();
        for (int i = 1; i < COUNT; i++) sideOrder.add(i);
        frames = layout();
        // Some IMEs retain primary-display pixel sizes even after moving to another display.
        // Match its width and density so cached keys fit, then scale the whole frame in TextureView.
        // All panes share these metrics; swapping panes changes view geometry only.
        virtualWidth = primaryMetrics.widthPixels;
        virtualHeight = Math.max(1, Math.round(virtualWidth * frames[0].height() / (float) frames[0].width()));
        density = primaryMetrics.densityDpi;
        workspace = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= 35) workspace.setRequestedFrameRate(MAIN_REFRESH_RATE);
        workspace.setPivotX(fromLeft ? 0f : width);
        workspace.setPivotY(height * 0.5f);
        applyWorkspaceTransition(0f);
        workspace.setOnClickListener(v -> { if (dragSession == null) close(true); });
        backdrop = new FrameLayout(context);
        // Only transitions need a translucent window; the steady workspace stays opaque so
        // SurfaceFlinger can occlude the underlying primary-display layers.
        // This is compositor occlusion, not an Activity lifecycle or process suspension request.
        backdrop.setBackgroundColor(Color.rgb(19, 21, 25));
        backdrop.setAlpha(0f);
        backdrop.setOnClickListener(v -> { if (dragSession == null) close(true); });
        backdrop.setOnDragListener((v, event) -> onAppDrag(event));
        FrameLayout.LayoutParams workspaceParams = new FrameLayout.LayoutParams(width, height);
        workspaceParams.leftMargin = bounds.left - screen.left;
        workspaceParams.topMargin = bounds.top - screen.top;
        backdrop.addView(workspace, workspaceParams);
        workspace.addView(createRecentStrip(recent), new FrameLayout.LayoutParams(-1, dp(TOOLBAR_HEIGHT_DP)));
        for (int i = 0; i < COUNT; i++) {
            Pane pane = new Pane(i, i == 0 ? main : null);
            panes[i] = pane;
            workspace.addView(pane.container);
            position(pane.container, frames[i]);
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(screen.width(), screen.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = screen.left;
        params.y = screen.top;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle("FlymeOneStepWorkspace");
        params.windowAnimations = 0;
        params.preferredRefreshRate = MAIN_REFRESH_RATE;
        windows.addView(backdrop, params);
        touchMode.enable();
        OneStepStatusBar.setVisible(true);
        updateRecentIcons();
        int sessionGeneration = generation;
        backdrop.postOnAnimation(() -> {
            if (active && !closing && generation == sessionGeneration) animateWorkspace(true);
        });
        scheduleCheck(300);
    }

    private void applyWorkspaceTransition(float progress) {
        transitionProgress = progress;
        workspace.setAlpha(progress);
        float scale = 0.94f + 0.06f * progress;
        workspace.setScaleX(scale);
        workspace.setScaleY(scale);
        workspace.setTranslationX((mainOnLeft ? -1 : 1) * dp(24) * (1f - progress));
        workspace.setTranslationY(dp(12) * (1f - progress));
    }

    private void cancelTransition() {
        if (transitionAnimator == null) return;
        ValueAnimator current = transitionAnimator;
        transitionAnimator = null;
        current.removeAllListeners();
        current.removeAllUpdateListeners();
        current.cancel();
    }

    private void animateWorkspace(boolean entering) {
        cancelTransition();
        float target = entering ? 1f : 0f;
        if (!ValueAnimator.areAnimatorsEnabled() || transitionProgress == target) {
            applyWorkspaceTransition(target);
            finishWorkspaceTransition(entering);
            return;
        }
        ValueAnimator transition = ValueAnimator.ofFloat(transitionProgress, target);
        transitionAnimator = transition;
        transition.setDuration(Math.max(1L, Math.round((entering ? ENTER_DURATION_MS : EXIT_DURATION_MS)
                * Math.abs(target - transitionProgress))));
        transition.setInterpolator(entering ? ENTER_INTERPOLATOR : EXIT_INTERPOLATOR);
        transition.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            // Render properties keep the live textures at a fixed size throughout the animation.
            applyWorkspaceTransition(progress);
            if (entering) backdrop.setAlpha(progress);
        });
        transition.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (transitionAnimator != animation) return;
                transitionAnimator = null;
                finishWorkspaceTransition(entering);
            }
        });
        transition.start();
    }

    private void finishWorkspaceTransition(boolean entering) {
        if (entering) {
            backdrop.setAlpha(1f);
            setWindowFormat(PixelFormat.OPAQUE);
        } else {
            restoreAndDismiss();
        }
    }

    private boolean setWindowFormat(int format) {
        if (backdrop == null || !backdrop.isAttachedToWindow()) return false;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) backdrop.getLayoutParams();
        if (params.format == format) return true;
        int previous = params.format;
        params.format = format;
        try {
            windows.updateViewLayout(backdrop, params);
            return true;
        } catch (RuntimeException e) {
            params.format = previous;
            Log.w(TAG, "Cannot change workspace window format", e);
            return false;
        }
    }

    private void fadeBackdropAndDismiss() {
        if (!animateClose || !ValueAnimator.areAnimatorsEnabled()
                || !setWindowFormat(PixelFormat.TRANSLUCENT)) {
            finishClose();
            return;
        }
        // Tasks are back on the primary display before revealing it. Keep the background
        // covering their migration instead of releasing live textures mid-animation.
        ValueAnimator fade = ValueAnimator.ofFloat(backdrop.getAlpha(), 0f);
        transitionAnimator = fade;
        fade.setDuration(BACKDROP_FADE_DURATION_MS);
        fade.setInterpolator(ENTER_INTERPOLATOR);
        fade.addUpdateListener(value -> backdrop.setAlpha((float) value.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (transitionAnimator != animation) return;
                transitionAnimator = null;
                finishClose();
            }
        });
        fade.start();
    }

    private Rect[] layout() {
        return OneStepWindowLayout.calculate(COUNT, width, height, dp(8), dp(TOOLBAR_HEIGHT_DP),
                true, false, mainSlot, sideOrder, 3, mainOnLeft, dp(16));
    }

    private void position(View view, Rect frame) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(frame.width(), frame.height());
        params.leftMargin = frame.left;
        params.topMargin = frame.top;
        view.setLayoutParams(params);
    }

    private void select(int slot) {
        if (!active || closing || !started || dragSession != null || animator != null
                || transitionAnimator != null || slot == mainSlot) return;
        Pane selected = panes[slot];
        if (!selected.ready) return;
        cancelTouches();
        try { tasks.focus(selected.id()); }
        catch (Exception e) { fail("无法切换应用窗口", e); return; }
        int index = sideOrder.indexOf(slot);
        if (index < 0) return;
        int previousMain = mainSlot;
        sideOrder.set(index, mainSlot);
        mainSlot = slot;
        Rect[] start = frames;
        Rect[] end = layout();
        Pane previous = panes[previousMain];
        if (!ValueAnimator.areAnimatorsEnabled()) {
            finishPaneSwap(previous, selected, end);
            updateRecentIcons();
            return;
        }
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            float f = (float) value.getAnimatedValue();
            transformPane(previous, start[previousMain], end[previousMain], f);
            transformPane(selected, start[slot], end[slot], f);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                finishPaneSwap(previous, selected, end);
            }
        });
        animator.start();
        updateRecentIcons();
    }

    private static void transformPane(Pane pane, Rect start, Rect end, float fraction) {
        View view = pane.container;
        view.setTranslationX((end.left - start.left) * fraction);
        view.setTranslationY((end.top - start.top) * fraction);
        view.setScaleX(1f + (end.width() / (float) start.width() - 1f) * fraction);
        view.setScaleY(1f + (end.height() / (float) start.height() - 1f) * fraction);
    }

    private void finishPaneSwap(Pane previous, Pane selected, Rect[] end) {
        if (active && !closing && Build.VERSION.SDK_INT >= 34) {
            try {
                // Display rates are immutable. Move the task stacks, keeping each display
                // connected to its original TextureView/BufferQueue. Retain the app images
                // until the first new output frame so the old contents do not flash at swap.
                previous.showSwapPreview(selected.texture);
                selected.showSwapPreview(previous.texture);
                taskStateVersion++;
                tasks.swapDisplays(previous.id(), selected.id());
                int previousSlot = previous.slot;
                previous.slot = selected.slot;
                selected.slot = previousSlot;
                panes[previous.slot] = previous;
                panes[selected.slot] = selected;
                RecentTaskCard previousCard = previous.card;
                previous.card = selected.card;
                selected.card = previousCard;
                tasks.focus(previous.id());
                logDisplayRates("swap");
                scheduleCheck(TASK_CHECK_DEBOUNCE_MS);
            } catch (Exception e) {
                fail("无法切换应用窗口", e);
                return;
            }
        }
        // Keep TextureView geometry fixed during the animation and lay out only once at the end.
        settlePane(previous, end[previous.slot]);
        settlePane(selected, end[selected.slot]);
        frames = end;
    }

    private void settlePane(Pane pane, Rect frame) {
        position(pane.container, frame);
        pane.container.setTranslationX(0f);
        pane.container.setTranslationY(0f);
        pane.container.setScaleX(1f);
        pane.container.setScaleY(1f);
    }

    private View createRecentStrip(List<RecentTaskCard> recent) {
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(true);
        strip.setContentDescription("最近使用的应用，长按图标拖入侧边窗口");
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(31, 34, 40));
        background.setCornerRadius(dp(16));
        strip.setBackground(background);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        // Consume taps in the bar's empty area instead of exiting the workspace.
        row.setOnClickListener(v -> { });
        recentIcons.clear();
        HashSet<String> packages = new HashSet<>();
        final int sessionGeneration = generation;
        for (RecentTaskCard card : recent) {
            if (card.component == null || !packages.add(card.userId + ":" + card.component.getPackageName())) continue;
            ImageView icon = new ImageView(context);
            icon.setTag(card);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));
            icon.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
            icon.setContentDescription(card.description + "，长按拖入侧边窗口");
            icon.setOnClickListener(v -> { });
            icon.setOnLongClickListener(v -> beginAppDrag(icon, card));
            row.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
            recentIcons.add(icon);
            iconLoader.execute(() -> {
                Drawable drawable;
                try { drawable = context.getPackageManager().getActivityIcon(card.component); }
                catch (Exception e) { drawable = context.getPackageManager().getDefaultActivityIcon(); }
                Drawable result = drawable;
                handler.post(() -> {
                    if (active && generation == sessionGeneration) icon.setImageDrawable(result);
                });
            });
        }
        strip.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        return strip;
    }

    private boolean isHosted(RecentTaskCard card) {
        for (Pane pane : panes) {
            if (pane == null || pane.card == null) continue;
            if (pane.card.sameTask(card)) return true;
            if (pane.card.userId == card.userId && pane.card.component != null && card.component != null
                    && pane.card.component.getPackageName().equals(card.component.getPackageName())) return true;
        }
        return false;
    }

    private void updateRecentIcons() {
        for (ImageView icon : recentIcons) {
            RecentTaskCard card = (RecentTaskCard) icon.getTag();
            boolean available = !isHosted(card);
            icon.setAlpha(available ? 1f : 0.4f);
            icon.setLongClickable(available);
            icon.setContentDescription(card.description + (available ? "，长按拖入侧边窗口" : "，已在工作台中"));
        }
    }

    private boolean beginAppDrag(ImageView icon, RecentTaskCard card) {
        if (!active || closing || !started || animator != null || transitionAnimator != null
                || dragSession != null || isHosted(card)) return false;
        cancelTouches();
        dragSession = new DragSession(card, generation);
        dragSource = icon;
        try {
            if (icon.startDragAndDrop(ClipData.newPlainText("OneStepApp", card.description),
                    new View.DragShadowBuilder(icon), dragSession, 0)) {
                icon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return true;
            }
        } catch (RuntimeException e) { Log.w(TAG, "Cannot start app drag", e); }
        dragSession = null;
        dragSource = null;
        return false;
    }

    private boolean onAppDrag(DragEvent event) {
        DragSession session = dragSession;
        if (session == null || event.getLocalState() != session || session.generation != generation) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return active && !closing;
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_ENTERED:
                highlightDropTarget(dropSlot(event));
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                highlightDropTarget(-1);
                return true;
            case DragEvent.ACTION_DROP:
                session.targetSlot = dropSlot(event);
                return session.targetSlot >= 0;
            case DragEvent.ACTION_DRAG_ENDED:
                highlightDropTarget(-1);
                dragSession = null;
                dragSource = null;
                if (event.getResult() && session.targetSlot >= 0) {
                    handler.post(() -> {
                        if (!active || closing || generation != session.generation
                                || session.targetSlot == mainSlot || OneHandedTaskHooks.shadeOpen()) return;
                        panes[session.targetSlot].load(session.card);
                    });
                }
                return true;
            default:
                return true;
        }
    }

    private int dropSlot(DragEvent event) {
        if (!active || closing || workspace == null || OneHandedTaskHooks.shadeOpen()) return -1;
        int x = (int) (event.getX() - workspace.getLeft());
        int y = (int) (event.getY() - workspace.getTop());
        for (int slot : sideOrder) {
            if (frames[slot].contains(x, y)) return slot;
        }
        return -1;
    }

    private void highlightDropTarget(int slot) {
        if (highlightedSlot == slot) return;
        highlightedSlot = slot;
        for (Pane pane : panes) {
            if (pane == null) continue;
            GradientDrawable highlight = null;
            if (pane.slot == slot) {
                highlight = new GradientDrawable();
                highlight.setColor(0x225aaaff);
                highlight.setStroke(dp(2), 0xff75baff);
            }
            pane.container.setForeground(highlight);
        }
    }

    private static final class DragSession {
        final RecentTaskCard card;
        final int generation;
        int targetSlot = -1;

        DragSession(RecentTaskCard card, int generation) {
            this.card = card;
            this.generation = generation;
        }
    }

    void refresh() {
        handler.post(() -> {
            if (opening && !OneHandedTaskHooks.environmentAllowed(context)) cancelOpening();
            if (active && !OneHandedTaskHooks.workspaceAllowed(context)) close(false);
        });
    }

    private void cancelOpening() {
        if (!opening) return;
        opening = false;
        generation++;
    }

    void stop(String reason) {
        Runnable action = () -> {
            cancelOpening();
            if (active) {
                Log.i(TAG, "Closing workspace: " + reason);
                close(false);
            }
        };
        if (android.os.Looper.myLooper() == handler.getLooper()) action.run();
        else handler.post(action);
    }

    private void onTasksChanged() {
        if (!active || closing) return;
        taskStateVersion++;
        scheduleCheck(TASK_CHECK_DEBOUNCE_MS);
    }

    private void scheduleCheck(long delayMs) {
        if (!active || closing) return;
        if (checkInFlight) {
            checkAgain = true;
            return;
        }
        long when = SystemClock.uptimeMillis() + delayMs;
        if (nextCheckTime != 0L && nextCheckTime <= when) return;
        handler.removeCallbacks(check);
        nextCheckTime = when;
        handler.postAtTime(check, when);
    }

    private void check() {
        nextCheckTime = 0L;
        if (!active || closing) return;
        if (checkInFlight) {
            checkAgain = true;
            return;
        }
        try {
            if (!OneHandedTaskHooks.workspaceAllowed(context)) { close(false); return; }
            if (!started && SystemClock.uptimeMillis() > deadline) {
                throw new IllegalStateException("Main pane did not draw a frame");
            }
        } catch (Exception e) { fail("多应用窗口已退出", e); return; }
        checkInFlight = true;
        int sessionGeneration = generation;
        int stateVersion = taskStateVersion;
        taskWorker.execute(() -> {
            try {
                List<?> roots = tasks.roots();
                handler.post(() -> finishCheck(sessionGeneration, stateVersion, roots, null));
            } catch (Exception e) {
                handler.post(() -> finishCheck(sessionGeneration, stateVersion, null, e));
            }
        });
    }

    private void finishCheck(int sessionGeneration, int stateVersion, List<?> roots, Exception error) {
        if (generation != sessionGeneration) return;
        checkInFlight = false;
        if (!active || closing) return;
        boolean refreshAgain = checkAgain || stateVersion != taskStateVersion;
        checkAgain = false;
        // A drop, task change or display release may have overtaken this background snapshot.
        if (stateVersion != taskStateVersion) {
            scheduleCheck(TASK_CHECK_DEBOUNCE_MS);
            return;
        }
        if (error != null) { fail("多应用窗口已退出", error); return; }
        try {
            boolean shadeOpen = OneHandedTaskHooks.shadeOpen();
            Pane main = panes[mainSlot];
            if (!started && main.ready && main.frameReceived && hasTask(roots, main.id())) {
                Runnable callback = onSuccess;
                onSuccess = null;
                started = true;
                primaryTask = focusedPrimaryTask(roots);
                if (!shadeOpen) tasks.focus(main.id());
                updateRecentIcons();
                if (callback != null) callback.run();
            }
            if (!main.ready && SystemClock.uptimeMillis() > deadline) {
                throw new IllegalStateException("Main pane did not become ready");
            }
            if (!started && SystemClock.uptimeMillis() > deadline) {
                throw new IllegalStateException("Main pane did not draw a frame");
            }
            int primary = focusedPrimaryTask(roots);
            if (started && !shadeOpen && primary >= 0 && primary != primaryTask) {
                close(false);
                return;
            }
            for (Pane pane : panes) {
                if (pane.ready && SystemClock.uptimeMillis() > pane.started + 2500 && !hasTask(roots, pane.id())) {
                    if (pane.slot == mainSlot) { close(false); return; }
                    pane.release();
                    pane.showEmpty("");
                    updateRecentIcons();
                }
            }
            scheduleCheck(refreshAgain ? TASK_CHECK_DEBOUNCE_MS
                    : !started ? 300 : taskNotifications ? TASK_CHECK_FALLBACK_MS : 700);
        } catch (Exception e) { fail("多应用窗口已退出", e); }
    }

    private static boolean hasTask(List<?> roots, int displayId) {
        for (Object root : roots) {
            if (OneStepTaskAccess.display(root) == displayId && OneStepTaskAccess.application(root)) return true;
        }
        return false;
    }

    private static int focusedPrimaryTask(List<?> roots) {
        int visibleTask = -1;
        for (Object root : roots) {
            if (OneStepTaskAccess.display(root) == 0 && OneStepTaskAccess.application(root)) {
                if (com.example.flymestatusbarsizer.util.ReflectUtils.getBooleanField(root, "isFocused", false)) {
                    return OneStepTaskAccess.taskId(root);
                }
                if (visibleTask < 0 && com.example.flymestatusbarsizer.util.ReflectUtils
                        .getBooleanField(root, "isVisible", false)) visibleTask = OneStepTaskAccess.taskId(root);
            }
        }
        // The notification shade can temporarily take focus from this same underlying task.
        return visibleTask;
    }

    private void cancelTouches() {
        for (Pane pane : panes) if (pane != null) pane.cancelTouch();
    }

    private void close(boolean focusMain) {
        if (!active) return;
        if (closing && focusMain) return;
        // An environment exit supersedes a pending user animation and its focus request.
        if (!focusMain) {
            restoreMainTask = -1;
            try { restoreFocusTask = tasks.defaultFocusedTaskId(); }
            catch (Exception e) { Log.w(TAG, "Cannot retain primary display focus", e); }
        } else {
            Pane pane = panes[mainSlot];
            if (pane != null && pane.display != null) {
                try {
                    for (Object root : tasks.roots()) {
                        if (OneStepTaskAccess.display(root) == pane.id()) {
                            restoreMainTask = OneStepTaskAccess.taskId(root);
                            break;
                        }
                    }
                } catch (Exception e) { Log.w(TAG, "Cannot identify main pane task", e); }
            }
        }
        focusMainOnClose = focusMain;
        animateClose = focusMain && backdrop != null && backdrop.isAttachedToWindow()
                && transitionProgress > 0f && ValueAnimator.areAnimatorsEnabled();
        closing = true;
        onSuccess = null;
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        nextCheckTime = 0L;
        handler.removeCallbacks(restoreRetry);
        cancelTransition();
        if (dragSource != null) {
            try { dragSource.cancelDragAndDrop(); }
            catch (RuntimeException e) { Log.w(TAG, "Cannot cancel app drag", e); }
        }
        dragSession = null;
        dragSource = null;
        highlightDropTarget(-1);
        cancelTouches();
        if (animator != null) { animator.cancel(); animator = null; }
        if (animateClose) animateWorkspace(false);
        else restoreAndDismiss();
    }

    private void restoreAndDismiss() {
        if (!active || !closing || (restoring && focusMainOnClose)) return;
        handler.removeCallbacks(restoreRetry);
        restoreAttempts++;
        restoring = true;
        int sessionGeneration = generation;
        int request = ++restoreRequest;
        // Restore the chosen main last so it is the app shown after leaving the workspace.
        ArrayList<Integer> order = new ArrayList<>(sideOrder);
        order.add(mainSlot);
        ArrayList<Pane> restoringPanes = new ArrayList<>();
        for (int slot : order) {
            Pane pane = panes[slot];
            if (pane != null && pane.display != null) restoringPanes.add(pane);
        }
        if (!focusMainOnClose) {
            // Recents/Home lifecycle hooks must restore before the native transition continues.
            // Supersede any pending read; background work never migrates tasks after this exit.
            List<?> roots = null;
            Exception queryError = null;
            try {
                if (!restoringPanes.isEmpty()) roots = tasks.roots();
            } catch (Exception e) {
                queryError = e;
            }
            finishRestoring(sessionGeneration, request, restoringPanes, roots, queryError);
            return;
        }
        taskWorker.execute(() -> {
            try {
                List<?> roots = restoringPanes.isEmpty() ? null : tasks.roots();
                handler.post(() -> finishRestoring(sessionGeneration, request, restoringPanes, roots, null));
            } catch (Exception e) {
                handler.post(() -> finishRestoring(sessionGeneration, request, restoringPanes, null, e));
            }
        });
    }

    private void finishRestoring(int sessionGeneration, int request, List<Pane> restoringPanes,
                                 List<?> roots, Exception queryError) {
        if (generation != sessionGeneration || restoreRequest != request) return;
        restoring = false;
        if (!active || !closing) return;
        boolean restored = true;
        // Share one task snapshot across panes. Keep each migration and release ordered on the UI
        // thread so a synchronous environment exit cannot race a background task migration.
        for (Pane pane : restoringPanes) {
            try {
                if (queryError != null) throw queryError;
                tasks.restoreDisplay(pane.id(), roots);
                pane.release();
            } catch (Exception e) {
                Log.w(TAG, "Cannot explicitly restore display tasks", e);
                if (restoreAttempts < 3) {
                    restored = false;
                } else {
                    // Display removal without DESTROY_CONTENT_ON_REMOVAL migrates remaining
                    // tasks through WindowManager. Do not leave an unusable overlay above apps.
                    pane.release();
                }
            }
        }
        if (!restored) {
            handler.postDelayed(restoreRetry, 800);
            return;
        }
        if (focusMainOnClose) {
            try {
                if (restoreMainTask >= 0) tasks.focusTask(restoreMainTask);
                else tasks.focus(0);
            }
            catch (Exception e) { Log.w(TAG, "Cannot focus restored task", e); }
        } else if (restoreFocusTask >= 0) {
            try { tasks.focusTask(restoreFocusTask); }
            catch (Exception e) { Log.w(TAG, "Cannot retain primary display task", e); }
        }
        fadeBackdropAndDismiss();
    }

    private void finishClose() {
        cancelTransition();
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        handler.removeCallbacks(restoreRetry);
        nextCheckTime = 0L;
        checkAgain = false;
        active = false;
        OneStepStatusBar.setVisible(false);
        closing = false;
        generation++;
        if (backdrop != null) {
            try { windows.removeViewImmediate(backdrop); }
            catch (RuntimeException e) { Log.w(TAG, "Cannot remove workspace window", e); }
            backdrop = null;
        }
        workspace = null;
        recentIcons.clear();
        for (int i = 0; i < COUNT; i++) panes[i] = null;
    }

    private void fail(String message, Exception e) {
        Log.w(TAG, message, e);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        close(false);
    }

    private void logDisplayRates(String reason) {
        Display primary = displays.getDisplay(Display.DEFAULT_DISPLAY);
        StringBuilder message = new StringBuilder("Display refresh rates (").append(reason)
                .append("): physicalReportedHz=").append(primary != null ? primary.getRefreshRate() : 0f);
        for (Pane pane : panes) {
            if (pane == null || pane.display == null) continue;
            message.append(", slot=").append(pane.slot).append(pane.slot == mainSlot ? " main" : " side")
                    .append(" display=").append(pane.id())
                    .append(" requestedHz=").append(Build.VERSION.SDK_INT >= 34
                            ? (pane.slot == mainSlot ? MAIN_REFRESH_RATE : SIDE_REFRESH_RATE) : 0f)
                    .append(" reportedHz=").append(pane.display.getDisplay().getRefreshRate());
        }
        // Display-reported rates describe scheduling modes, not measured app frame rates.
        Log.i(TAG, message.toString());
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private TextView label(String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setMaxLines(2);
        return view;
    }

    private final class Pane implements TextureView.SurfaceTextureListener {
        int slot;
        final FrameLayout container = new FrameLayout(context);
        final TextureView texture = new TextureView(context);
        final TextView empty = label("正在打开…", 12);
        ImageView swapPreview;
        Runnable pendingFrameUiUpdate;
        RecentTaskCard card;
        VirtualDisplay display;
        Surface surface;
        MotionEvent lastTouch;
        boolean ready;
        boolean frameReceived;
        boolean consumeSelection;
        long started;
        int inputWidth = virtualWidth;
        int inputHeight = virtualHeight;

        Pane(int slot, RecentTaskCard card) {
            this.slot = slot;
            this.card = card;
            container.setPivotX(0f);
            container.setPivotY(0f);
            container.setBackgroundColor(Color.BLACK);
            texture.setSurfaceTextureListener(this);
            texture.setOnTouchListener((v, event) -> touch(event));
            container.addView(texture, new FrameLayout.LayoutParams(-1, -1));
            container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            empty.setBackgroundColor(Color.BLACK);
            empty.setOnClickListener(v -> { });
            empty.setContentDescription("空白应用窗口，长按上方应用图标拖入此处");
            if (card == null) showEmpty("");
        }

        int id() { return display == null ? -1 : display.getDisplay().getDisplayId(); }

        void showSwapPreview(TextureView source) {
            clearSwapPreview();
            Bitmap bitmap = source.getBitmap();
            if (bitmap == null) return;
            swapPreview = new ImageView(context);
            swapPreview.setScaleType(ImageView.ScaleType.FIT_XY);
            swapPreview.setImageBitmap(bitmap);
            swapPreview.setOnTouchListener((view, event) -> true);
            container.addView(swapPreview, new FrameLayout.LayoutParams(-1, -1));
        }

        void clearSwapPreview() {
            if (pendingFrameUiUpdate != null) {
                handler.removeCallbacks(pendingFrameUiUpdate);
                pendingFrameUiUpdate = null;
            }
            if (swapPreview == null) return;
            container.removeView(swapPreview);
            swapPreview.setImageDrawable(null);
            swapPreview = null;
        }

        void showEmpty(String text) {
            ready = false;
            card = null;
            empty.setText(text);
            empty.setVisibility(View.VISIBLE);
        }

        void load(RecentTaskCard next) {
            if (!active || closing || slot == mainSlot || isHosted(next)) return;
            try {
                if (tasks.findCandidate(next) == null) {
                    Toast.makeText(context, "该应用任务已结束，请重新打开工作台", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (display != null) {
                    tasks.restoreDisplay(id());
                    release();
                }
            } catch (Exception e) {
                fail("无法替换侧边应用", e);
                return;
            }
            card = next;
            empty.setText("正在打开…");
            empty.setVisibility(View.VISIBLE);
            if (texture.isAvailable()) create(texture.getSurfaceTexture());
            updateRecentIcons();
        }

        private void create(SurfaceTexture buffer) {
            if (!active || closing || card == null || display != null || buffer == null) return;
            taskStateVersion++;
            try {
                buffer.setDefaultBufferSize(virtualWidth, virtualHeight);
                surface = new Surface(buffer);
                frameReceived = false;
                final int[] callbackDisplayId = {-1};
                VirtualDisplay.Callback callback = new VirtualDisplay.Callback() {
                    @Override public void onStopped() {
                        if (active && !closing && callbackDisplayId[0] > 0
                                && id() == callbackDisplayId[0]) stop("display stopped");
                    }
                };
                String name = "FlymeOneStep-" + generation + "-" + slot;
                if (Build.VERSION.SDK_INT >= 34) {
                    VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                            name, virtualWidth, virtualHeight, density)
                            .setSurface(surface)
                            .setFlags(DISPLAY_FLAGS)
                            .setRequestedRefreshRate(slot == mainSlot ? MAIN_REFRESH_RATE : SIDE_REFRESH_RATE)
                            .build();
                    display = displays.createVirtualDisplay(config, handler, callback);
                } else {
                    display = displays.createVirtualDisplay(name, virtualWidth, virtualHeight,
                            density, surface, DISPLAY_FLAGS, callback, handler);
                }
                if (display == null) throw new IllegalStateException("Virtual display creation returned null");
                callbackDisplayId[0] = id();
                tasks.configureDisplay(id());
                tasks.attach(card, id(), slot == mainSlot && !OneStepWorkspace.this.started);
                ready = true;
                started = SystemClock.uptimeMillis();
                updateInputSize();
                logDisplayRates("pane created");
            } catch (Exception e) {
                Log.w(TAG, "Cannot host pane " + slot, e);
                try {
                    if (display != null) tasks.restoreDisplay(id());
                    release();
                } catch (Exception restoreError) {
                    fail("无法恢复应用窗口", restoreError);
                    return;
                }
                if (slot == mainSlot) { fail("当前应用无法进入多应用窗口", e); return; }
                showEmpty("打开失败，请重新拖入应用");
            }
            if (active && !closing && slot != mainSlot) {
                try {
                    // A deliberate drop can move the task previously underneath the backdrop.
                    // Track the new baseline so the periodic external-launch check does not exit.
                    primaryTask = focusedPrimaryTask(tasks.roots());
                    Pane main = panes[mainSlot];
                    if (main != null && main.ready && !OneHandedTaskHooks.shadeOpen()) tasks.focus(main.id());
                } catch (Exception e) { Log.w(TAG, "Cannot restore main pane focus after drop", e); }
                updateRecentIcons();
            }
            scheduleCheck(TASK_CHECK_DEBOUNCE_MS);
        }

        void updateInputSize() {
            if (display == null) return;
            DisplayMetrics metrics = new DisplayMetrics();
            display.getDisplay().getRealMetrics(metrics);
            inputWidth = metrics.widthPixels;
            inputHeight = metrics.heightPixels;
        }

        boolean touch(MotionEvent event) {
            if (!active || closing || dragSession != null || !ready) return true;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                consumeSelection = !OneStepWorkspace.this.started || transitionAnimator != null
                        || slot != mainSlot || animator != null;
                if (consumeSelection) { select(slot); return true; }
                try { tasks.focus(id()); }
                catch (Exception e) { fail("无法聚焦应用窗口", e); return true; }
            }
            if (consumeSelection || animator != null || transitionAnimator != null
                    || !OneStepWorkspace.this.started) return true;
            try {
                tasks.motion(id(), event, inputWidth, inputHeight, texture.getWidth(), texture.getHeight());
                if (lastTouch != null) lastTouch.recycle();
                lastTouch = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                        ? null : MotionEvent.obtain(event);
            } catch (Exception e) { fail("应用窗口触摸不可用", e); }
            return true;
        }

        void cancelTouch() {
            if (lastTouch == null) return;
            try {
                lastTouch.setAction(MotionEvent.ACTION_CANCEL);
                tasks.motion(id(), lastTouch, inputWidth, inputHeight, texture.getWidth(), texture.getHeight());
            } catch (Exception e) { Log.w(TAG, "Cannot cancel pane touch", e); }
            finally { lastTouch.recycle(); lastTouch = null; }
        }

        void release() {
            taskStateVersion++;
            cancelTouch();
            clearSwapPreview();
            ready = false;
            frameReceived = false;
            card = null;
            if (display != null) {
                VirtualDisplay old = display;
                display = null;
                try { old.release(); }
                catch (RuntimeException e) { Log.w(TAG, "Cannot release virtual display", e); }
            }
            if (surface != null) { surface.release(); surface = null; }
        }

        @Override public void onSurfaceTextureAvailable(SurfaceTexture buffer, int w, int h) { create(buffer); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture buffer, int w, int h) {
            buffer.setDefaultBufferSize(virtualWidth, virtualHeight);
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture buffer) {
            if (active && !closing) stop("pane surface destroyed");
            return true;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture buffer) {
            if (!active || closing || !ready || pendingFrameUiUpdate != null
                    || (frameReceived && swapPreview == null)) return;
            int frameGeneration = generation;
            VirtualDisplay frameDisplay = display;
            ImageView framePreview = swapPreview;
            // TextureView invokes this while its parent's display list may be traversing
            // children. Removing a sibling here invalidates that traversal; even on the main
            // thread, all view changes must wait until the current drawing call has returned.
            pendingFrameUiUpdate = () -> {
                pendingFrameUiUpdate = null;
                if (!active || closing || !ready || generation != frameGeneration
                        || display != frameDisplay) return;
                if (swapPreview == framePreview) clearSwapPreview();
                if (frameReceived) return;
                // Reveal the app only after its first frame, then acknowledge the gesture.
                frameReceived = true;
                empty.setVisibility(View.GONE);
                if (slot == mainSlot && !OneStepWorkspace.this.started) scheduleCheck(0);
            };
            if (!handler.post(pendingFrameUiUpdate)) pendingFrameUiUpdate = null;
        }
    }
}
