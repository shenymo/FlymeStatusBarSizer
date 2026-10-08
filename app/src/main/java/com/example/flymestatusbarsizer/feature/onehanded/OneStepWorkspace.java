package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Workspace UI; task ownership, surfaces and restoration live in OneStepShell. */
final class OneStepWorkspace implements OneStepShell.Listener {
    private static final String TAG = "FlymeOneStep";
    private static final int COUNT = 4;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final int REFERENCE_TOP_AREA_DP = 236;
    private static final PathInterpolator EASING = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private enum State { CLOSED, OPENING, RUNNING, CLOSING }
    private volatile State state = State.CLOSED;
    private final Context context;
    private final Handler handler;
    private final WindowManager windows;
    private final OneStepTaskAccess tasks;
    private final OneStepShell shell;
    private final OneStepTouchMode touchMode = new OneStepTouchMode();
    private final Pane[] panes = new Pane[COUNT];
    private final ArrayList<Integer> sideOrder = new ArrayList<>();
    private final ArrayList<ImageView> recentIcons = new ArrayList<>();
    private final Executor taskWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeOneStepTasks");
        thread.setDaemon(true);
        return thread;
    });
    private final Executor iconLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FlymeOneStepIcons");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable check = this::checkTasks;
    private final Runnable taskChanged = () -> scheduleCheck(100);
    private final Runnable openingTimeout = () -> {
        if (state == State.OPENING) fail("应用窗口未能就绪", new IllegalStateException("Task attach timed out"));
    };
    private FrameLayout backdrop;
    private FrameLayout workspace;
    private View recentStrip;
    private ValueAnimator animator;
    private ValueAnimator transitionAnimator;
    private Rect[] frames;
    private Rect contentBounds;
    private final Rect logicalBounds = new Rect();
    private int width;
    private int height;
    private int mainSlot;
    private int generation;
    private int highlightedSlot = -1;
    private int imeBottom;
    private int imeOffset;
    private boolean mainOnLeft;
    private boolean checkInFlight;
    private boolean taskNotifications;
    private float transitionProgress;
    private DragSession dragSession;
    private ImageView dragSource;
    private Runnable onSuccess;
    private OneStepPerf perf;

    OneStepWorkspace(Context source, Handler handler, Object transitions, Object factory, Object displayAreas)
            throws Exception {
        this.handler = handler;
        DisplayManager displays = source.getSystemService(DisplayManager.class);
        context = source.createDisplayContext(displays.getDisplay(0))
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windows = context.getSystemService(WindowManager.class);
        tasks = new OneStepTaskAccess(source);
        shell = new OneStepShell(source, handler, transitions, factory, displayAreas, tasks);
        shell.setListener(this);
        try {
            tasks.registerTaskChanges(() -> {
                if (!active()) return;
                handler.removeCallbacks(taskChanged);
                handler.post(taskChanged);
            });
            taskNotifications = true;
        } catch (Exception error) { Log.w(TAG, "Task notifications unavailable", error); }
    }

    private boolean active() { return state == State.OPENING || state == State.RUNNING; }
    private boolean running() { return state == State.RUNNING; }

    boolean canTrigger() {
        if (state == State.OPENING || state == State.CLOSING || animator != null || dragSession != null
                || !OneHandedTaskHooks.environmentAllowed(context)) return false;
        if (running()) return true;
        if (!shell.available()) return false;
        try { return tasks.focusedTask() != null; }
        catch (Exception error) { return false; }
    }

    void toggle(boolean fromLeft, Runnable success) {
        handler.post(() -> {
            if (running()) { close(true); return; }
            if (state != State.CLOSED || !shell.available() || !OneHandedTaskHooks.environmentAllowed(context)) return;
            state = State.OPENING;
            int request = ++generation;
            taskWorker.execute(() -> {
                try {
                    RecentTaskCard main = tasks.focusedTask();
                    List<RecentTaskCard> recent = tasks.candidates();
                    handler.post(() -> {
                        if (state != State.OPENING || generation != request) return;
                        if (main == null || !OneHandedTaskHooks.environmentAllowed(context)) { close(false); return; }
                        try { open(main, recent, fromLeft, success); }
                        catch (Exception error) { fail("无法打开应用工作台", error); }
                    });
                } catch (Exception error) {
                    handler.post(() -> {
                        if (state == State.OPENING && generation == request) fail("无法读取应用任务", error);
                    });
                }
            });
        });
    }

    private void open(RecentTaskCard main, List<RecentTaskCard> recent, boolean fromLeft, Runnable success)
            throws Exception {
        WindowMetrics metrics = windows.getMaximumWindowMetrics();
        Rect screen = new Rect(metrics.getBounds());
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        updateLogicalBounds(screen, insets);
        contentBounds = new Rect(logicalBounds);
        contentBounds.top += Math.min(dp(REFERENCE_TOP_AREA_DP - TOOLBAR_HEIGHT_DP),
                Math.max(0, contentBounds.height() - dp(TOOLBAR_HEIGHT_DP + 160)));
        width = contentBounds.width();
        height = contentBounds.height();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid workspace bounds");
        mainSlot = 0;
        mainOnLeft = fromLeft;
        onSuccess = success;
        transitionProgress = 0;
        imeBottom = 0;
        imeOffset = 0;
        sideOrder.clear();
        for (int i = 1; i < COUNT; i++) sideOrder.add(i);
        frames = layout();
        shell.begin(generation);
        backdrop = new FrameLayout(context);
        backdrop.setBackgroundColor(Color.rgb(19, 21, 25));
        backdrop.setOnClickListener(v -> { if (dragSession == null) close(true); });
        backdrop.setOnDragListener((v, event) -> onAppDrag(event));
        workspace = new FrameLayout(context);
        workspace.setOnClickListener(v -> { if (dragSession == null) close(true); });
        if (Build.VERSION.SDK_INT >= 35) workspace.setRequestedFrameRate(120f);
        FrameLayout.LayoutParams workspaceParams = new FrameLayout.LayoutParams(width, height);
        workspaceParams.leftMargin = contentBounds.left - screen.left;
        workspaceParams.topMargin = contentBounds.top - screen.top;
        backdrop.addView(workspace, workspaceParams);
        recentStrip = createRecentStrip(recent);
        workspace.addView(recentStrip, new FrameLayout.LayoutParams(-1, dp(TOOLBAR_HEIGHT_DP)));
        for (int i = 0; i < COUNT; i++) {
            Pane pane = new Pane(i);
            panes[i] = pane;
            workspace.addView(pane.container);
            position(pane.container, frames[i]);
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(screen.width(), screen.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        // Like Shell's Bubbles host, this overlay must allow cross-UID input through TaskView's
        // touchable-region hole. Trusting the child SurfaceControl alone leaves the full-screen
        // host subject to InputDispatcher's untrusted-touch blocking.
        OneStepReflection.call(params, "setTrustedOverlay");
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = screen.left;
        params.y = screen.top;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle("FlymeOneStepWorkspace");
        params.windowAnimations = 0;
        params.preferredRefreshRate = 120f;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        // The default-display IME stays above this non-focusable host and owns its normal input.
        backdrop.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int bottom = windowInsets.isVisible(WindowInsets.Type.ime())
                    ? windowInsets.getInsets(WindowInsets.Type.ime()).bottom : 0;
            if (bottom != imeBottom) {
                imeBottom = bottom;
                handler.post(this::positionForIme);
            }
            return windowInsets;
        });
        windows.addView(backdrop, params);
        perf = new OneStepPerf(backdrop, handler, generation);
        touchMode.enable();
        OneStepStatusBar.setVisible(true);
        panes[0].load(main);
        applyTransition(0f);
        handler.postDelayed(openingTimeout, 8000);
        backdrop.postOnAnimation(() -> { if (active()) animateWorkspace(true, null); });
        scheduleCheck(300);
    }

    private Rect[] layout() {
        Rect[] result = OneStepWindowLayout.calculate(COUNT, width, height, dp(8), dp(TOOLBAR_HEIGHT_DP),
                true, false, mainSlot, sideOrder, 3, mainOnLeft, dp(16));
        // Fit the full app viewport inside each slot without stretching or cropping it.
        // Store the fitted frames so swaps and drag targets follow the visible panes.
        for (Rect frame : result) {
            float scale = scaleForFrame(frame);
            int fittedWidth = Math.max(1, Math.round(logicalBounds.width() * scale));
            int fittedHeight = Math.max(1, Math.round(logicalBounds.height() * scale));
            int left = frame.left + (frame.width() - fittedWidth) / 2;
            int top = frame.top + (frame.height() - fittedHeight) / 2;
            frame.set(left, top, left + fittedWidth, top + fittedHeight);
        }
        return result;
    }

    private void updateLogicalBounds(Rect screen, Insets insets) {
        // Capture the full usable display before reserving space for workspace chrome.
        // Keep this viewport fixed for the session, including pane swaps and IME movement.
        logicalBounds.set(screen);
        logicalBounds.inset(insets.left, insets.top, insets.right, insets.bottom);
        if (logicalBounds.isEmpty()) throw new IllegalStateException("Invalid app viewport");
    }

    private float scaleForFrame(Rect frame) {
        return Math.min(1f, Math.min(frame.width() / (float) logicalBounds.width(),
                frame.height() / (float) logicalBounds.height()));
    }

    private void position(View view, Rect frame) {
        // TaskViews keep the logical viewport in every slot. SurfaceView's render-thread
        // transform moves both the task and its window hole together, without a buffer resize.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(logicalBounds.width(), logicalBounds.height());
        params.leftMargin = frame.left;
        params.topMargin = frame.top;
        view.setLayoutParams(params);
        float scale = scaleForFrame(frame);
        view.setScaleX(scale);
        view.setScaleY(scale);
    }

    private void positionForIme() {
        if (!active() || workspace == null || animator != null || transitionAnimator != null) return;
        Rect screen = windows.getMaximumWindowMetrics().getBounds();
        int nextOffset = Math.max(0, contentBounds.bottom - (screen.bottom - imeBottom));
        if (imeOffset == nextOffset) return;
        blockInput(true);
        imeOffset = nextOffset;
        // Move the toolbar and all panes together without changing their size or scale.
        workspace.setTranslationY(-imeOffset);
        // Shell excludes IME layout insets for the hosted tasks for the whole session.
        // Do not send setBounds here: even a position-only change triggers task relayout
        // and a Shell transition. SurfaceView moves the native task input with its surface.
        workspace.post(this::updateInput);
    }

    private void applyTransition(float progress) {
        transitionProgress = progress;
        if (recentStrip != null) recentStrip.setAlpha(progress);
        for (Pane pane : panes) if (pane != null) pane.container.setTranslationY(dp(16) * (1f - progress));
        // Keep task alpha on each SurfaceView, outside parent alpha layers.
        for (Pane pane : panes) if (pane != null && pane.host != null && Build.VERSION.SDK_INT >= 34) {
            pane.host.view.setAlpha(progress);
        }
    }

    private void animateWorkspace(boolean entering, Runnable done) {
        cancelAnimator(false);
        blockInput(true);
        float target = entering ? 1f : 0f;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            applyTransition(target);
            if (done != null) done.run();
            else { updateInput(); positionForIme(); }
            return;
        }
        ValueAnimator transition = ValueAnimator.ofFloat(transitionProgress, target);
        transitionAnimator = transition;
        transition.setDuration(entering ? 250 : 160);
        transition.setInterpolator(EASING);
        transition.addUpdateListener(value -> applyTransition((float) value.getAnimatedValue()));
        transition.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (transitionAnimator != animation) return;
                transitionAnimator = null;
                if (done != null) done.run();
                else { updateInput(); positionForIme(); }
            }
        });
        transition.start();
    }

    private void select(int slot) {
        if (!running() || slot == mainSlot || animator != null || transitionAnimator != null || dragSession != null) return;
        Pane selected = panes[slot];
        if (selected.host == null || !selected.host.ready) return;
        int previousMain = mainSlot;
        int index = sideOrder.indexOf(slot);
        if (index < 0) return;
        blockInput(true);
        sideOrder.set(index, previousMain);
        mainSlot = slot;
        Rect[] start = frames;
        Rect[] end = layout();
        if (perf != null) perf.phase("swap");
        Runnable finish = () -> {
            frames = end;
            for (Pane pane : panes) {
                position(pane.container, frames[pane.slot]);
                pane.container.setTranslationX(0);
                pane.container.setTranslationY(0);
                pane.updateGeometry();
            }
            shell.focus(selected.host);
            workspace.post(this::updateInput);
            positionForIme();
            if (perf != null) perf.phase("steady");
        };
        if (!ValueAnimator.areAnimatorsEnabled()) { finish.run(); return; }
        ValueAnimator swap = ValueAnimator.ofFloat(0, 1);
        animator = swap;
        swap.setDuration(240);
        swap.setInterpolator(EASING);
        swap.addUpdateListener(value -> {
            float f = (float) value.getAnimatedValue();
            transform(panes[previousMain].container, start[previousMain], end[previousMain], f);
            transform(selected.container, start[slot], end[slot], f);
        });
        swap.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                finish.run();
            }
        });
        swap.start();
    }

    private void transform(View view, Rect start, Rect end, float fraction) {
        view.setTranslationX((end.left - start.left) * fraction);
        view.setTranslationY((end.top - start.top) * fraction);
        float startScale = scaleForFrame(start);
        float scale = startScale + (scaleForFrame(end) - startScale) * fraction;
        view.setScaleX(scale);
        view.setScaleY(scale);
    }

    private void cancelAnimator(boolean swap) {
        ValueAnimator current = swap ? animator : transitionAnimator;
        if (swap) animator = null;
        else transitionAnimator = null;
        if (current != null) {
            current.removeAllListeners();
            current.removeAllUpdateListeners();
            current.cancel();
        }
    }

    private void updateInput() {
        blockInput(!running() || animator != null || transitionAnimator != null || dragSession != null);
    }

    private void blockInput(boolean block) {
        if (backdrop == null) return;
        // TaskView's native insets listener uses its unscaled width/height. Cover everything
        // except the visible main pane so scaled side TaskViews cannot open oversized holes.
        android.graphics.Region obscured = new android.graphics.Region();
        obscured.set(0, 0, Math.max(width, backdrop.getWidth()),
                Math.max(height + contentBounds.top, backdrop.getHeight()));
        Pane main = panes[mainSlot];
        if (!block && main != null && main.host != null && main.host.ready) {
            Rect visible = new Rect();
            if (main.host.view.getGlobalVisibleRect(visible)) {
                obscured.op(visible, android.graphics.Region.Op.DIFFERENCE);
            }
        }
        try {
            boolean changed = false;
            for (Pane pane : panes) if (pane != null && pane.host != null) {
                // setObscuredTouchRect cannot represent several disjoint side panes during swaps.
                java.lang.reflect.Field field = OneStepReflection.field(pane.host.view.getClass(), "mObscuredTouchRegion");
                Object previous = field.get(pane.host.view);
                if (!obscured.equals(previous)) {
                    field.set(pane.host.view, new android.graphics.Region(obscured));
                    changed = true;
                }
            }
            if (changed) {
                // Schedule a traversal to publish the input region without requesting
                // a new measure/layout pass for every workspace movement.
                backdrop.invalidate();
            }
        } catch (Exception error) {
            if (active()) handler.post(() -> fail("无法设置应用触摸区域", error));
            else Log.w(TAG, "Cannot close task input regions", error);
        }
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
                    if (active() && generation == sessionGeneration) icon.setImageDrawable(result);
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
        if (!running() || animator != null || transitionAnimator != null
                || dragSession != null || isHosted(card)) return false;
        blockInput(true);
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
        updateInput();
        return false;
    }

    private boolean onAppDrag(DragEvent event) {
        DragSession session = dragSession;
        if (session == null || event.getLocalState() != session || session.generation != generation) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return running();
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
                updateInput();
                if (event.getResult() && session.targetSlot >= 0) {
                    handler.post(() -> {
                        if (!running() || generation != session.generation
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
        if (!running() || workspace == null || OneHandedTaskHooks.shadeOpen()) return -1;
        int x = (int) (event.getX() - workspace.getX());
        int y = (int) (event.getY() - workspace.getY());
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
        handler.post(() -> { if (active() && !OneHandedTaskHooks.workspaceAllowed(context)) close(false); });
    }

    void stop(String reason) {
        Runnable action = () -> {
            if (state != State.CLOSED) {
                Log.i(TAG, "Closing workspace: " + reason);
                close(false);
            }
        };
        if (android.os.Looper.myLooper() == handler.getLooper()) action.run();
        else handler.post(action);
    }

    void beforeRecents() {
        if (state == State.CLOSED) return;
        shell.beforeRecents();
        stop("recents started");
    }

    private void scheduleCheck(long delay) {
        if (!active()) return;
        handler.removeCallbacks(check);
        handler.postDelayed(check, delay);
    }

    private void checkTasks() {
        if (!active() || checkInFlight) return;
        if (!OneHandedTaskHooks.workspaceAllowed(context)) { close(false); return; }
        checkInFlight = true;
        int request = generation;
        taskWorker.execute(() -> {
            try {
                List<?> roots = tasks.roots();
                handler.post(() -> {
                    if (generation != request) return;
                    checkInFlight = false;
                    if (!active()) return;
                    if (running() && !OneHandedTaskHooks.shadeOpen()) {
                        for (Object root : roots) {
                            if (OneStepTaskAccess.display(root) != 0 || !ReflectUtils.getBooleanField(root, "isFocused", false)) continue;
                            boolean hosted = false;
                            for (Pane pane : panes) if (pane != null && pane.card != null
                                    && pane.card.taskId == OneStepTaskAccess.taskId(root)
                                    && pane.card.token.equals(OneStepTaskAccess.token(root))) hosted = true;
                            if (!hosted) { close(false); return; }
                        }
                    }
                    scheduleCheck(taskNotifications ? 3000 : 700);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (generation != request) return;
                    checkInFlight = false;
                    if (active()) fail("无法同步应用任务", error);
                });
            }
        });
    }

    @Override public void onReady(OneStepShell.Host host) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.empty.setVisibility(View.GONE);
            if (pane.slot == mainSlot) {
                if (state == State.OPENING) {
                    state = State.RUNNING;
                    handler.removeCallbacks(openingTimeout);
                    Runnable callback = onSuccess;
                    onSuccess = null;
                    if (callback != null) callback.run();
                    if (perf != null) perf.phase("steady");
                }
                if (!OneHandedTaskHooks.shadeOpen()) shell.focus(host);
            } else {
                Pane main = panes[mainSlot];
                if (main.host != null && !OneHandedTaskHooks.shadeOpen()) shell.focus(main.host);
            }
            if (perf != null) perf.taskReady(pane.slot, host.card.taskId);
        }
        updateInput();
    }

    @Override public void onRemoved(OneStepShell.Host host) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            if (pane.slot == mainSlot) { close(false); return; }
            pane.container.removeView(host.view);
            pane.host = null;
            pane.card = null;
            pane.empty.setText("");
            pane.empty.setVisibility(View.VISIBLE);
        }
        updateRecentIcons();
        updateInput();
    }

    @Override public void onFailure(String message, Exception error) { if (active()) fail(message, error); }
    @Override public void onExternalTransition() { close(false); }

    private void close(boolean focusMain) {
        if (state == State.CLOSED) return;
        if (state == State.CLOSING) {
            if (!focusMain) {
                cancelAnimator(false);
                shell.close(-1, this::finishClose);
            }
            return;
        }
        state = State.CLOSING;
        handler.removeCallbacks(openingTimeout);
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        onSuccess = null;
        blockInput(true);
        cancelAnimator(true);
        cancelAnimator(false);
        if (dragSource != null) {
            try { dragSource.cancelDragAndDrop(); }
            catch (RuntimeException error) { Log.w(TAG, "Cannot cancel drag", error); }
        }
        dragSession = null;
        dragSource = null;
        int focusTask = focusMain && panes[mainSlot] != null && panes[mainSlot].card != null
                ? panes[mainSlot].card.taskId : -1;
        Runnable restore = () -> shell.close(focusTask, this::finishClose);
        if (perf != null) perf.phase("closing");
        if (focusMain && backdrop != null) animateWorkspace(false, restore);
        else restore.run();
    }

    private void finishClose() {
        if (state == State.CLOSED) return;
        cancelAnimator(true);
        cancelAnimator(false);
        if (perf != null) { perf.stop(); perf = null; }
        OneStepStatusBar.setVisible(false);
        if (backdrop != null) {
            try { windows.removeViewImmediate(backdrop); }
            catch (RuntimeException error) { Log.w(TAG, "Cannot remove workspace window", error); }
        }
        backdrop = workspace = null;
        recentStrip = null;
        recentIcons.clear();
        for (int i = 0; i < COUNT; i++) panes[i] = null;
        checkInFlight = false;
        generation++;
        state = State.CLOSED;
    }

    private void fail(String message, Exception error) {
        Log.w(TAG, message, error);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        close(false);
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private final class Pane {
        final int slot;
        final FrameLayout container = new FrameLayout(context);
        final TextView empty = new TextView(context);
        RecentTaskCard card;
        OneStepShell.Host host;
        boolean replacing;

        Pane(int slot) {
            this.slot = slot;
            container.setPivotX(0);
            container.setPivotY(0);
            container.setBackgroundColor(Color.BLACK);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.WHITE);
            empty.setTextSize(12);
            empty.setBackgroundColor(Color.BLACK);
            empty.setContentDescription("空白应用窗口，长按上方应用图标拖入此处");
            empty.setOnClickListener(v -> { });
            container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            container.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && slot != mainSlot) select(slot);
                return true;
            });
            container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateInput());
        }

        void load(RecentTaskCard next) {
            if (!active() || replacing || isHosted(next)) return;
            replacing = true;
            OneStepShell.Host previous = host;
            int request = generation;
            Runnable attach = () -> {
                if (!active() || generation != request) return;
                if (previous != null) container.removeView(previous.view);
                host = null;
                card = next;
                empty.setText("正在打开…");
                empty.setVisibility(View.VISIBLE);
                try {
                    host = shell.create(context, next, state == State.OPENING && slot == mainSlot);
                    host.view.setOnTouchListener((view, event) -> {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && slot != mainSlot) select(slot);
                        return true;
                    });
                    host.obscure(new Rect(0, 0, windows.getMaximumWindowMetrics().getBounds().width(),
                            windows.getMaximumWindowMetrics().getBounds().height()));
                    container.addView(host.view, 0, new FrameLayout.LayoutParams(-1, -1));
                    updateGeometry();
                    updateRecentIcons();
                    container.post(OneStepWorkspace.this::updateInput);
                } catch (Exception error) { fail("无法创建应用窗口", error); }
                replacing = false;
            };
            if (previous != null) shell.release(previous, attach);
            else attach.run();
        }

        void updateGeometry() {
            if (host == null || !active()) return;
            // Keep the task surface at 1:1 inside TaskView; only the container scales it.
            shell.geometry(host, logicalBounds, logicalBounds.width(), logicalBounds.height(), slot == mainSlot);
        }
    }
}
