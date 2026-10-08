package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Live OneStep panes hosted entirely in the LSPosed-injected SystemUI process. */
final class OneStepWorkspace {
    private static final String TAG = "FlymeOneStep";
    private static final int COUNT = 4;
    private static final int TOOLBAR_HEIGHT_DP = 56;
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
    private final Pane[] panes = new Pane[COUNT];
    private final ArrayList<Integer> sideOrder = new ArrayList<>();
    private final Runnable check = this::check;
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
    private volatile boolean active;
    private volatile boolean closing;
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

    boolean isActive() { return active; }

    boolean canTrigger() {
        if (closing || dragSession != null || !OneHandedTaskHooks.environmentAllowed(context)) return false;
        if (active) return true;
        try { return tasks.focusedTask() != null; }
        catch (Exception e) { return false; }
    }

    void toggle(boolean fromLeft, Runnable success) {
        handler.post(() -> {
            if (closing) return;
            if (active) { close(true); return; }
            if (!OneHandedTaskHooks.environmentAllowed(context)) return;
            try {
                RecentTaskCard main = tasks.focusedTask();
                if (main == null) return;
                open(main, fromLeft, success);
            } catch (Exception e) { fail("无法打开多应用工作台", e); }
        });
    }

    private void open(RecentTaskCard main, boolean fromLeft, Runnable success) throws Exception {
        List<RecentTaskCard> recent = tasks.candidates();
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
        active = true;
        generation++;
        closing = false;
        restoreFocusTask = -1;
        restoreMainTask = -1;
        primaryTask = -1;
        started = false;
        restoreAttempts = 0;
        mainSlot = 0;
        mainOnLeft = fromLeft;
        onSuccess = success;
        deadline = SystemClock.uptimeMillis() + 7000;
        sideOrder.clear();
        for (int i = 1; i < COUNT; i++) sideOrder.add(i);
        frames = layout();
        // Keep all buffers at the main pane's resolution. Swapping panes changes view geometry only.
        virtualWidth = Math.min(1080, width);
        virtualHeight = Math.max(1, Math.round(virtualWidth * frames[0].height() / (float) frames[0].width()));
        if (virtualHeight > 4096) {
            virtualWidth = Math.max(1, Math.round(virtualWidth * 4096f / virtualHeight));
            virtualHeight = 4096;
        }
        // Same stable 393dp phone width as OneStep4's VirtualDisplayDensityPolicy.
        density = Math.max(120, Math.round(virtualWidth * 160f / 393f));
        workspace = new FrameLayout(context);
        workspace.setOnClickListener(v -> { if (dragSession == null) close(true); });
        backdrop = new FrameLayout(context);
        // One opaque window lets SurfaceFlinger occlude the underlying primary-display layers.
        // This is compositor occlusion, not an Activity lifecycle or process suspension request.
        backdrop.setBackgroundColor(Color.rgb(19, 21, 25));
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
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = screen.left;
        params.y = screen.top;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle("FlymeOneStepWorkspace");
        windows.addView(backdrop, params);
        OneStepStatusBar.setVisible(true);
        updateRecentIcons();
        handler.postDelayed(check, 300);
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
        if (!active || closing || dragSession != null || animator != null || slot == mainSlot) return;
        Pane selected = panes[slot];
        if (!selected.ready) return;
        cancelTouches();
        try { tasks.focus(selected.id()); }
        catch (Exception e) { fail("无法切换应用窗口", e); return; }
        int index = sideOrder.indexOf(slot);
        if (index < 0) return;
        sideOrder.set(index, mainSlot);
        mainSlot = slot;
        Rect[] start = frames;
        Rect[] end = layout();
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            float f = (float) value.getAnimatedValue();
            for (int i = 0; i < COUNT; i++) {
                Rect a = start[i], b = end[i];
                position(panes[i].container, new Rect(lerp(a.left, b.left, f), lerp(a.top, b.top, f),
                        lerp(a.right, b.right, f), lerp(a.bottom, b.bottom, f)));
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                frames = end;
                animator = null;
            }
        });
        animator.start();
        updateRecentIcons();
    }

    private static int lerp(int a, int b, float fraction) { return Math.round(a + (b - a) * fraction); }

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
        if (!active || closing || !started || animator != null || dragSession != null || isHosted(card)) return false;
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
            if (active && !closing && !OneHandedTaskHooks.workspaceAllowed(context)) close(false);
        });
    }

    void stop(String reason) {
        Runnable action = () -> {
            if (active) {
                Log.i(TAG, "Closing workspace: " + reason);
                close(false);
            }
        };
        if (android.os.Looper.myLooper() == handler.getLooper()) action.run();
        else handler.post(action);
    }

    private void check() {
        if (!active || closing) return;
        try {
            if (!OneHandedTaskHooks.workspaceAllowed(context)) { close(false); return; }
            boolean shadeOpen = OneHandedTaskHooks.shadeOpen();
            Pane main = panes[mainSlot];
            List<?> roots = tasks.roots();
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
            handler.postDelayed(check, 700);
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
        if (!closing && focusMain) {
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
        if (!closing && !focusMain) {
            try { restoreFocusTask = tasks.defaultFocusedTaskId(); }
            catch (Exception e) { Log.w(TAG, "Cannot retain primary display focus", e); }
        }
        closing = true;
        restoreAttempts++;
        onSuccess = null;
        handler.removeCallbacks(check);
        if (dragSource != null) {
            try { dragSource.cancelDragAndDrop(); }
            catch (RuntimeException e) { Log.w(TAG, "Cannot cancel app drag", e); }
        }
        dragSession = null;
        dragSource = null;
        highlightDropTarget(-1);
        cancelTouches();
        if (animator != null) { animator.cancel(); animator = null; }
        boolean restored = true;
        // Restore the chosen main last so it is the app shown after leaving the workspace.
        ArrayList<Integer> order = new ArrayList<>(sideOrder);
        order.add(mainSlot);
        for (int slot : order) {
            Pane pane = panes[slot];
            if (pane == null || pane.display == null) continue;
            try {
                tasks.restoreDisplay(pane.id());
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
            int currentGeneration = generation;
            handler.postDelayed(() -> {
                if (active && closing && generation == currentGeneration) close(focusMain);
            }, 800);
            return;
        }
        if (focusMain) {
            try {
                if (restoreMainTask >= 0) tasks.focusTask(restoreMainTask);
                else tasks.focus(0);
            }
            catch (Exception e) { Log.w(TAG, "Cannot focus restored task", e); }
        } else if (restoreFocusTask >= 0) {
            try { tasks.focusTask(restoreFocusTask); }
            catch (Exception e) { Log.w(TAG, "Cannot retain primary display task", e); }
        }
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
        final int slot;
        final FrameLayout container = new FrameLayout(context);
        final TextureView texture = new TextureView(context);
        final TextView empty = label("正在打开…", 12);
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

        void showEmpty(String text) {
            ready = false;
            card = null;
            empty.setText(text);
            empty.setVisibility(View.VISIBLE);
        }

        void load(RecentTaskCard next) {
            if (!active || closing || slot == mainSlot || isHosted(next)) return;
            try {
                boolean valid = false;
                for (RecentTaskCard candidate : tasks.candidates()) {
                    if (candidate.sameTask(next)) { valid = true; break; }
                }
                if (!valid) {
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
            try {
                buffer.setDefaultBufferSize(virtualWidth, virtualHeight);
                surface = new Surface(buffer);
                frameReceived = false;
                final int[] callbackDisplayId = {-1};
                display = displays.createVirtualDisplay("FlymeOneStep-" + generation + "-" + slot,
                        virtualWidth, virtualHeight, density, surface, DISPLAY_FLAGS,
                        new VirtualDisplay.Callback() {
                            @Override public void onStopped() {
                                if (active && !closing && callbackDisplayId[0] > 0
                                        && id() == callbackDisplayId[0]) stop("display stopped");
                            }
                        }, handler);
                if (display == null) throw new IllegalStateException("Virtual display creation returned null");
                callbackDisplayId[0] = id();
                tasks.configureDisplay(id());
                tasks.attach(card, id());
                ready = true;
                started = SystemClock.uptimeMillis();
                updateInputSize();
                // Wait for a real app frame before allowing the selection gesture to complete.
                empty.setVisibility(View.GONE);
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
        }

        void updateInputSize() {
            if (display == null) return;
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            display.getDisplay().getRealMetrics(metrics);
            inputWidth = metrics.widthPixels;
            inputHeight = metrics.heightPixels;
        }

        boolean touch(MotionEvent event) {
            if (!active || closing || dragSession != null || !ready) return true;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                consumeSelection = slot != mainSlot || animator != null;
                if (consumeSelection) { select(slot); return true; }
                try { tasks.focus(id()); }
                catch (Exception e) { fail("无法聚焦应用窗口", e); return true; }
            }
            if (consumeSelection || animator != null) return true;
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
            cancelTouch();
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
        @Override public void onSurfaceTextureUpdated(SurfaceTexture buffer) { frameReceived = true; }
    }
}
