package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;
import com.example.flymestatusbarsizer.util.ReflectUtils;
import com.example.flymestatusbarsizer.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Workspace UI; task ownership, surfaces and restoration live in OneStepShell. */
final class OneStepWorkspace implements OneStepShell.Listener {
    private static final String TAG = "FlymeOneStep";
    private static final int COUNT = 4;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final int PANE_GAP_DP = 4;
    private static final int WORKSPACE_MARGIN_DP = 4;
    private static final int PANE_RADIUS_DP = 16;
    private static final int PANE_TAP_HIGHLIGHT_MS = 350;
    private static final PathInterpolator EASING = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private enum State { CLOSED, OPENING, RUNNING, CLOSING }
    private volatile State state = State.CLOSED;
    private final Context context;
    private final Handler handler;
    private final OneStepTaskAccess tasks;
    private final OneStepShell shell;
    private final OneStepNavigation navigation;
    private final OneStepTouchMode touchMode = new OneStepTouchMode();
    private final Pane[] panes = new Pane[COUNT];
    private final ArrayList<Integer> sideOrder = new ArrayList<>();
    private final ArrayList<ImageView> recentIcons = new ArrayList<>();
    private final ArrayList<RecentTaskCard> recentCards = new ArrayList<>();
    private final ArrayList<RecentTaskCard> pinnedCards = new ArrayList<>();
    private LinearLayout recentRow;
    private HorizontalScrollView recentScroll;
    private ImageView exitButton;
    private boolean recentOrderDirty;
    private boolean covered;
    private boolean hostVisible = true;
    private volatile boolean suspended;
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
    private final Runnable clearTapHighlight = this::clearPaneTapHighlight;
    private final Runnable openingTimeout = () -> {
        if (state == State.OPENING) fail("应用窗口未能就绪", new IllegalStateException("Task attach timed out"));
    };
    private WorkspaceRoot backdrop;
    private FrameLayout workspace;
    private View recentStrip;
    private ValueAnimator animator;
    private ValueAnimator transitionAnimator;
    private ValueAnimator imeAnimator;
    private Rect[] frames;
    private Rect contentBounds;
    private final Rect logicalBounds = new Rect();
    private final Rect screenBounds = new Rect();
    private OneStepActivitySession activitySession;
    private OneStepLauncherBridge.Session desktopSession;
    private boolean desktopBusy;
    private int workspaceUserId;
    private int workspaceOwnerUserId;
    private int width;
    private int height;
    private int mainSlot;
    private volatile int generation;
    private int highlightedSlot = -1;
    private int tappedSlot = -1;
    private int imeBottom;
    private int imeOffset;
    private boolean imeAnimating;
    private int activityImeBottom;
    private boolean activityImeAnimating;
    private boolean shellImeControlled;
    private boolean shellImePositioning;
    private boolean shellImeFloating;
    private int shellImeTargetBottom;
    private IBinder imeInputTask;
    private int imePlacement = OneStepImePolicy.UNKNOWN;
    private boolean mainOnLeft;
    private boolean taskNotifications;
    private float transitionProgress;
    private DragSession dragSession;
    private ImageView dragSource;
    private ImageView dragPreview;
    private Runnable onSuccess;
    private RecentTaskCard openingTask;
    private boolean initialTaskStarted;
    private boolean rebinding;
    private OneStepPerf perf;

    OneStepWorkspace(Context source, Handler handler, Object transitions, Object factory, Object displayAreas)
            throws Exception {
        this.handler = handler;
        DisplayManager displays = source.getSystemService(DisplayManager.class);
        context = source.createDisplayContext(displays.getDisplay(0));
        tasks = new OneStepTaskAccess(source);
        navigation = new OneStepNavigation(source);
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
                || !(running() ? OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId)
                        : OneHandedTaskHooks.environmentAllowed(context))) return false;
        if (running()) return true;
        if (!shell.available()) return false;
        try { return tasks.focusedTask() != null; }
        catch (Exception error) { return false; }
    }

    void toggle(boolean fromLeft, Runnable success) {
        handler.post(() -> {
            if (active()) return;
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
                        try { startActivity(main, recent, fromLeft, success); }
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

    private void startActivity(RecentTaskCard main, List<RecentTaskCard> recent, boolean fromLeft, Runnable success)
            throws Exception {
        int request = generation;
        workspaceUserId = main.userId;
        // A managed-profile task can belong to a different user than the visible system UI.
        workspaceOwnerUserId = OneHandedTaskHooks.currentUserId();
        suspended = !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
        navigation.setLocked(!suspended, workspaceOwnerUserId);
        onSuccess = success;
        openingTask = main;
        initialTaskStarted = false;
        shell.begin(request, main.userId);
        shell.setSuspended(suspended);
        activitySession = new OneStepActivitySession(context, handler, main.userId, workspaceOwnerUserId, new OneStepActivitySession.Listener() {
            @Override public void onAttached(int taskId, Rect bounds, Rect insets) {
                if (generation != request || state != State.OPENING) return;
                shell.attachActivity(taskId, () -> {
                    if (generation != request || state != State.OPENING) return;
                    try {
                        if (backdrop == null) open(recent, fromLeft, bounds, Insets.of(insets.left, insets.top, insets.right, insets.bottom));
                        else resizeWorkspace(bounds, insets);
                    }
                    catch (Exception error) { fail("无法连接工作台窗口", error); }
                });
            }

            @Override public void onMounted(boolean visible) {
                if (generation != request || !active() || backdrop == null) return;
                rebinding = false;
                hostVisible = visible;
                shell.setHostVisible(visible);
                refreshEnvironment();
                startInitialTask();
                if (running() && visible && !suspended && !covered && !OneHandedTaskHooks.shadeOpen())
                    shell.focus(panes[mainSlot].host);
                updateInput();
                scheduleCheck(300);
            }

            @Override public void onResized(Rect bounds, Rect insets) {
                if (generation == request && active()) resizeWorkspace(bounds, insets);
            }

            @Override public void onReattaching(int taskId, Rect bounds, Rect insets, Runnable mount) {
                if (generation != request || !active()) return;
                rebinding = true;
                hostVisible = false;
                shell.setHostVisible(false);
                finishAppDrag(-1);
                updateInput();
                shell.rebindActivity(taskId, () -> {
                    if (generation != request || !active()) return;
                    mount.run();
                });
            }

            @Override public void onImeChanged(int bottom, boolean animating) {
                if (generation != request || !active()) return;
                boolean diagnosticBoundary = (bottom > 0) != (activityImeBottom > 0)
                        || animating != activityImeAnimating || (!animating && bottom != activityImeBottom);
                activityImeBottom = bottom;
                activityImeAnimating = animating;
                // Flyme keeps Activity insets visible until Shell's hide animation ends.
                // They cannot drive movement while Shell owns the actual IME surface.
                if (!shellImeControlled) applyActivityIme();
                if (diagnosticBoundary) logImeEvent("activity-insets");
            }

            @Override public void onClosed(boolean focusMain) {
                if (generation == request) close(false);
            }

            @Override public void onBarColorsChanged(boolean darkIcons) {
                if (generation == request && active()) OneStepStatusBar.setDarkIcons(darkIcons);
            }

            @Override public void onVisibilityChanged(boolean visible) {
                if (generation != request || !active()) return;
                hostVisible = visible;
                shell.setHostVisible(visible);
                OneStepStatusBar.setVisible(visible && !suspended && !covered);
                if (visible) startInitialTask();
                updateInput();
                scheduleCheck(100);
            }
        });
        if (!suspended) handler.postDelayed(openingTimeout, 12000);
        activitySession.start();
        scheduleCheck(300);
    }

    private void startInitialTask() {
        if (state != State.OPENING || suspended || rebinding || !hostVisible || initialTaskStarted || openingTask == null
                || backdrop == null || activitySession == null || !activitySession.isMounted()) return;
        initialTaskStarted = panes[0].load(openingTask);
        if (!initialTaskStarted) return;
        applyTransition(0f);
        int request = generation;
        backdrop.postOnAnimation(() -> { if (active() && generation == request) animateWorkspace(true, null); });
    }

    private void resizeWorkspace(Rect screen, Rect insets) {
        if (!active() || workspace == null) return;
        finishAppDrag(-1);
        if (animator != null) animator.end();
        if (transitionAnimator != null) transitionAnimator.end();
        cancelImeAnimator();
        screenBounds.set(screen);
        updateLogicalBounds(screen, Insets.of(insets.left, insets.top, insets.right, insets.bottom));
        contentBounds.set(logicalBounds);
        width = contentBounds.width();
        height = contentBounds.height();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = contentBounds.left - screen.left;
        params.topMargin = contentBounds.top - screen.top;
        workspace.setLayoutParams(params);
        frames = layout();
        int panesTop = frames[mainSlot].top;
        for (int slot : sideOrder) panesTop = Math.min(panesTop, frames[slot].top);
        FrameLayout.LayoutParams strip = (FrameLayout.LayoutParams) recentStrip.getLayoutParams();
        strip.height = dp(TOOLBAR_HEIGHT_DP);
        strip.leftMargin = strip.rightMargin = dp(WORKSPACE_MARGIN_DP);
        strip.topMargin = Math.max(0, panesTop - dp(TOOLBAR_HEIGHT_DP + PANE_GAP_DP));
        recentStrip.setLayoutParams(strip);
        exitButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        exitButton.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        recentRow.setPadding(dp(8), 0, dp(8), 0);
        recentOrderDirty = true;
        updateRecentIcons();
        for (Pane pane : panes) if (pane != null) {
            position(pane.container, frames[pane.slot]);
            pane.container.setTranslationX(0);
            pane.container.setTranslationY(0);
            pane.updateAppearance();
            pane.updateGeometry();
        }
        positionForIme();
        workspace.post(this::updateInput);
    }

    private void open(List<RecentTaskCard> recent, boolean fromLeft, Rect screen, Insets insets) throws Exception {
        screenBounds.set(screen);
        updateLogicalBounds(screen, insets);
        contentBounds = new Rect(logicalBounds);
        width = contentBounds.width();
        height = contentBounds.height();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid workspace bounds");
        mainSlot = 0;
        covered = false;
        hostVisible = true;
        pinnedCards.clear();
        mainOnLeft = fromLeft;
        transitionProgress = 0;
        imeBottom = 0;
        imeOffset = 0;
        imeAnimating = false;
        activityImeBottom = 0;
        activityImeAnimating = false;
        shellImeControlled = false;
        shellImePositioning = false;
        shellImeFloating = false;
        shellImeTargetBottom = 0;
        imeInputTask = null;
        imePlacement = OneStepImePolicy.UNKNOWN;
        sideOrder.clear();
        for (int i = 1; i < COUNT; i++) sideOrder.add(i);
        frames = layout();
        backdrop = new WorkspaceRoot();
        backdrop.setVisibility(suspended ? View.INVISIBLE : View.VISIBLE);
        backdrop.setClipChildren(false);
        backdrop.setClipToPadding(false);
        backdrop.setOnClickListener(v -> { });
        workspace = new FrameLayout(context);
        workspace.setClipChildren(false);
        workspace.setClipToPadding(false);
        workspace.setOnClickListener(v -> { });
        if (Build.VERSION.SDK_INT >= 35) workspace.setRequestedFrameRate(120f);
        FrameLayout.LayoutParams workspaceParams = new FrameLayout.LayoutParams(width, height);
        workspaceParams.leftMargin = contentBounds.left - screen.left;
        workspaceParams.topMargin = contentBounds.top - screen.top;
        backdrop.addView(workspace, workspaceParams);
        recentStrip = createRecentStrip(recent);
        int panesTop = frames[mainSlot].top;
        for (int slot : sideOrder) panesTop = Math.min(panesTop, frames[slot].top);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(-1, dp(TOOLBAR_HEIGHT_DP));
        stripParams.leftMargin = dp(WORKSPACE_MARGIN_DP);
        stripParams.rightMargin = dp(WORKSPACE_MARGIN_DP);
        stripParams.topMargin = Math.max(0, panesTop - dp(TOOLBAR_HEIGHT_DP + PANE_GAP_DP));
        workspace.addView(recentStrip, stripParams);
        for (int i = 0; i < COUNT; i++) {
            Pane pane = new Pane(i);
            panes[i] = pane;
            workspace.addView(pane.container);
            position(pane.container, frames[i]);
            pane.updateAppearance();
        }
        activitySession.setView(backdrop);
        perf = new OneStepPerf(backdrop, handler, generation);
        touchMode.enable();
        OneStepStatusBar.setVisible(!suspended);
    }

    private Rect[] layout() {
        int margin = Math.min(dp(WORKSPACE_MARGIN_DP), Math.min(width, height) / 8);
        Rect[] result = OneStepWindowLayout.calculateWorkspace(width - margin * 2,
                height - margin * 2, dp(PANE_GAP_DP),
                dp(TOOLBAR_HEIGHT_DP + PANE_GAP_DP), logicalBounds.width(), logicalBounds.height(),
                mainSlot, sideOrder, mainOnLeft);
        for (Rect frame : result) frame.offset(margin, margin);
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

    void onShellImeStart(int shownTop, boolean showing, boolean floating) {
        dispatchShellIme(() -> {
            shellImeControlled = true;
            shellImePositioning = true;
            shellImeFloating = floating || shownTop >= contentBounds.bottom;
            shellImeTargetBottom = showing && !shellImeFloating ? imeBottomForTop(shownTop) : 0;
            cancelImeAnimator();
            imeAnimating = true;
            // Preserve the current position at start, including reversals mid-animation.
            // The following Shell frame supplies the actual surface position.
            blockInput(true);
            logImeEvent("shell-start showing=" + showing + " shownTop=" + shownTop + " floating=" + floating);
        });
    }

    void onShellImePosition(int top) {
        dispatchShellIme(() -> {
            if (!shellImeControlled || !shellImePositioning) return;
            imeBottom = shellImeFloating ? 0 : imeBottomForTop(top);
            imeAnimating = true;
            positionForIme();
        });
    }

    void onShellImeEnd(boolean cancelled) {
        dispatchShellIme(() -> {
            if (!shellImeControlled || !shellImePositioning) return;
            shellImePositioning = false;
            if (!cancelled) {
                imeBottom = shellImeTargetBottom;
                // Apply the exact final position without starting another fallback animation.
                imeAnimating = true;
                positionForIme();
            }
            imeAnimating = false;
            logImeEvent("shell-end cancelled=" + cancelled);
            workspace.post(this::updateInput);
        });
    }

    void onShellImeControlLost() {
        dispatchShellIme(() -> {
            if (!shellImeControlled) return;
            shellImeControlled = false;
            shellImePositioning = false;
            applyActivityIme();
            logImeEvent("shell-control-lost");
        });
    }

    @Override public void onImeRoutingChanged(IBinder task, int placement) {
        if (!active()) return;
        imeInputTask = task;
        imePlacement = placement;
        if (placement == OneStepImePolicy.APP) {
            // Insets describe the logical display even when the keyboard is inside a pane.
            // Only a confirmed app parent revokes Shell positioning. Unknown routing during
            // a focus handoff must not cancel a newer Shell animation already in flight.
            shellImeControlled = false;
            shellImePositioning = false;
        }
        if (!shellImeControlled) applyActivityIme();
        logImeEvent("routing placement=" + placement);
    }

    private boolean imeTargetsMain() {
        Pane main = panes[mainSlot];
        return main != null && main.host != null && main.host.card != null
                && imeInputTask != null && imeInputTask.equals(main.host.card.token);
    }

    private void logImeEvent(String event) {
        try {
            Pane main = panes[mainSlot];
            RecentTaskCard card = main != null ? main.card : null;
            Log.i(OneStepImeDiagnostics.TAG, "workspace session=" + generation + " event=" + event
                    + " mainTask=" + (card != null ? card.taskId : -1)
                    + " source=" + (shellImeControlled ? "shell" : "activity")
                    + " activityBottom=" + activityImeBottom + " activityAnimating=" + activityImeAnimating
                    + " imeBottom=" + imeBottom + " imeAnimating=" + imeAnimating
                    + " shellTargetBottom=" + shellImeTargetBottom + " shellFloating=" + shellImeFloating
                    + " avoidOffset=" + imeOffset
                    + " translationY=" + (workspace != null ? workspace.getTranslationY() : 0)
                    + " screen=" + screenBounds + " content=" + contentBounds);
            shell.logImeTasks(generation, event);
        } catch (Throwable error) { OneStepImeDiagnostics.unavailable(error); }
    }

    private void dispatchShellIme(Runnable update) {
        if (!active()) return;
        int request = generation;
        Runnable action = () -> {
            if (generation == request && active() && workspace != null) update.run();
        };
        // Shell may run on its own executor. Never carry its shared surface transaction
        // to another thread, or let a queued frame affect a later workspace session.
        if (android.os.Looper.myLooper() == handler.getLooper()) action.run();
        else handler.post(action);
    }

    private int imeBottomForTop(int top) {
        return Math.max(0, Math.min(screenBounds.height(), screenBounds.bottom - top));
    }

    private void applyActivityIme() {
        boolean displayIme = imePlacement == OneStepImePolicy.DISPLAY && imeTargetsMain();
        int bottom = displayIme ? activityImeBottom : 0;
        boolean animating = displayIme && activityImeAnimating;
        if (imeBottom == bottom && imeAnimating == animating) return;
        imeBottom = bottom;
        imeAnimating = animating;
        positionForIme();
    }

    private void positionForIme() {
        if (!active() || workspace == null) return;
        int nextOffset = Math.max(0, contentBounds.bottom - (screenBounds.bottom - imeBottom));
        // Parent translation composes with pane swaps and the workspace enter animation.
        // System animation frames already include the IME's easing; do not animate them again.
        if (imeAnimating || !ValueAnimator.areAnimatorsEnabled()) {
            cancelImeAnimator();
            imeOffset = nextOffset;
            blockInput(true);
            translateForIme(nextOffset);
            OneStepStatusBar.setImeVisible(imeBottom > 0);
            if (!imeAnimating) workspace.post(this::updateInput);
            return;
        }
        if (imeAnimator != null && imeOffset == nextOffset) return;
        cancelImeAnimator();
        imeOffset = nextOffset;
        OneStepStatusBar.setImeVisible(imeBottom > 0);
        float startOffset = -workspace.getTranslationY();
        if (startOffset == nextOffset) {
            translateForIme(nextOffset);
            workspace.post(this::updateInput);
            return;
        }
        blockInput(true);
        // Some IMEs/ROMs only report the final inset. Retarget from the current position
        // so rapid show/hide requests do not snap back to an earlier animation endpoint.
        ValueAnimator movement = ValueAnimator.ofFloat(startOffset, nextOffset);
        imeAnimator = movement;
        movement.setDuration(nextOffset > startOffset ? 280 : 220);
        movement.setInterpolator(EASING);
        movement.addUpdateListener(value -> translateForIme((float) value.getAnimatedValue()));
        movement.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (imeAnimator != animation) return;
                imeAnimator = null;
                logImeEvent("fallback-end");
                workspace.post(OneStepWorkspace.this::updateInput);
            }
        });
        // Shell excludes IME layout insets for the hosted tasks for the whole session.
        // Do not send setBounds here: even a position-only change triggers task relayout
        // and a Shell transition. SurfaceView moves the native task input with its surface.
        movement.start();
    }

    private void cancelImeAnimator() {
        ValueAnimator current = imeAnimator;
        imeAnimator = null;
        if (current != null) {
            current.removeAllListeners();
            current.removeAllUpdateListeners();
            current.cancel();
        }
    }

    private void translateForIme(float offset) {
        workspace.setTranslationY(-offset);
        if (recentStrip != null) {
            int top = ((FrameLayout.LayoutParams) recentStrip.getLayoutParams()).topMargin;
            // The only exit control must remain reachable when the keyboard lifts the panes.
            recentStrip.setTranslationY(Math.max(0f, offset - top));
            float elevation = 0f;
            if (offset > top) for (Pane pane : panes) if (pane != null)
                elevation = Math.max(elevation, pane.container.getElevation());
            recentStrip.setTranslationZ(elevation);
            recentStrip.bringToFront();
        }
    }

    private void applyTransition(float progress) {
        transitionProgress = progress;
        if (recentStrip != null) recentStrip.setAlpha(progress);
        for (Pane pane : panes) if (pane != null) {
            Rect frame = frames[pane.slot];
            float entryScale = 0.97f + 0.03f * progress;
            float scale = scaleForFrame(frame) * entryScale;
            pane.container.setScaleX(scale);
            pane.container.setScaleY(scale);
            pane.container.setTranslationX(frame.width() * (1f - entryScale) / 2f);
            pane.container.setTranslationY(dp(16) * (1f - progress)
                    + frame.height() * (1f - entryScale) / 2f);
            pane.updateAppearance();
        }
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
        if (!running() || suspended || rebinding || covered || slot == mainSlot || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null || desktopBusy) return;
        Pane selected = panes[slot];
        if (selected.host == null && !selected.replacing) { enterDesktop(slot, true); return; }
        if (selected.host == null || !selected.host.ready) return;
        clearPaneTapHighlight();
        haptic(HapticFeedbackConstants.CLOCK_TICK);
        Pane previous = panes[mainSlot];
        boolean leavingDesktop = previous.card != null && previous.card.home;
        if (leavingDesktop) { desktopBusy = true; disconnectDesktop(); }
        swapTo(slot, () -> {
            if (leavingDesktop) clearDesktop(previous, () -> { desktopBusy = false; updateInput(); });
        });
    }

    private void swapTo(int slot, Runnable after) {
        if (slot == mainSlot) { after.run(); return; }
        Pane selected = panes[slot];
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
                pane.updateAppearance();
                pane.updateGeometry();
            }
            shell.focus(selected.host);
            workspace.post(this::updateInput);
            positionForIme();
            if (perf != null) perf.phase("steady");
            after.run();
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
            panes[previousMain].updateAppearance();
            selected.updateAppearance();
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

    private void enterDesktop(int slot, boolean fromTap) {
        if (!running() || desktopBusy || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null || OneHandedTaskHooks.shadeOpen()) return;
        Pane target = panes[slot];
        if (target == null || target.host != null || target.replacing) return;
        // A tap still needs acknowledgement when it reuses the visible desktop.
        if (fromTap) showPaneTapFeedback(slot);
        Pane previous = panes[mainSlot];
        // Every empty pane opens the same desktop. Keep its live view and launcher
        // connection in place when the main pane is already showing that desktop.
        if (desktopSession != null && previous != null && previous.host != null
                && previous.host.home() && previous.host.ready && !previous.host.closing) return;
        desktopBusy = true;
        disconnectDesktop();
        target.empty.setText("正在打开桌面…");
        swapTo(slot, () -> clearDesktop(previous, () -> connectDesktop(target)));
    }

    private void connectDesktop(Pane pane) {
        int request = generation;
        taskWorker.execute(() -> {
            try {
                RecentTaskCard home = tasks.homeTask(workspaceUserId);
                handler.post(() -> {
                    if (!running() || generation != request || pane != panes[mainSlot]) return;
                    try {
                        desktopSession = new OneStepLauncherBridge.Session(context, handler, workspaceUserId,
                                new OneStepLauncherBridge.Listener() {
                            @Override public void onConnected() {
                                if (running() && generation == request && pane == panes[mainSlot]) pane.load(home);
                            }
                            @Override public void onLaunch(PendingIntent intent, IBinder animation) {
                                if (running() && generation == request && pane == panes[mainSlot])
                                    launchFromDesktop(pane, intent, animation);
                                else OneStepLaunchAnimation.cancelRemote(animation);
                            }
                            @Override public void onUnavailable() {
                                if (running() && generation == request) desktopUnavailable(pane);
                            }
                        });
                    } catch (Exception error) {
                        Log.w(TAG, "Cannot connect launcher", error);
                        desktopUnavailable(pane);
                    }
                });
            } catch (Exception error) {
                Log.w(TAG, "Cannot find HOME task", error);
                handler.post(() -> { if (running() && generation == request) desktopUnavailable(pane); });
            }
        });
    }

    private void desktopUnavailable(Pane pane) {
        disconnectDesktop();
        desktopBusy = false;
        pane.showEmpty();
        Toast.makeText(context, "暂时无法打开桌面，请稍后重试", Toast.LENGTH_SHORT).show();
        shell.focus(null);
        updateInput();
    }

    private void disconnectDesktop() {
        if (desktopSession != null) { desktopSession.close(); desktopSession = null; }
    }

    private void clearDesktop(Pane pane, Runnable done) {
        if (pane == null || pane.card == null || !pane.card.home || pane.host == null) { done.run(); return; }
        OneStepShell.Host previous = pane.host;
        int request = generation;
        shell.release(previous, () -> {
            if (!running() || generation != request) return;
            pane.container.removeView(previous.view);
            if (pane.host == previous) {
                pane.host = null;
                pane.card = null;
                pane.showEmpty();
            }
            done.run();
        });
    }

    private void launchFromDesktop(Pane pane, PendingIntent intent, IBinder animation) {
        if (desktopBusy || animator != null || transitionAnimator != null || imeAnimator != null || imeAnimating
                || pane.host == null || !pane.host.home() || !pane.host.ready || OneHandedTaskHooks.shadeOpen()) {
            if (desktopSession != null) desktopSession.retry();
            updateInput();
            return;
        }
        desktopBusy = true;
        blockInput(true);
        pane.empty.setText("正在打开应用…");
        pane.empty.setVisibility(animation == null ? View.VISIBLE : View.GONE);
        try {
            pane.launchHost = shell.createLaunch(context, intent, animation, pane.host, screenBounds);
            pane.mount(pane.launchHost);
            shell.geometry(pane.launchHost, logicalBounds, logicalBounds.width(), logicalBounds.height(), true);
        } catch (Exception error) {
            Log.w(TAG, "Cannot prepare launcher application", error);
            if (pane.launchHost != null) {
                OneStepShell.Host failed = pane.launchHost;
                shell.release(failed, () -> onLaunchFailed(failed));
            } else {
                desktopBusy = false;
                if (desktopSession != null) desktopSession.retry();
                pane.empty.setVisibility(View.GONE);
                Toast.makeText(context, "无法打开应用", Toast.LENGTH_SHORT).show();
                updateInput();
            }
        }
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
        if (!shellImeControlled) applyActivityIme();
        boolean blocked = !running() || suspended || rebinding || covered || !hostVisible || panesBusy() || desktopBusy || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null;
        blockInput(blocked);
        Pane main = panes[mainSlot];
        if (desktopSession != null) desktopSession.enable(!blocked && !OneHandedTaskHooks.shadeOpen()
                && main != null && main.host != null && main.host.ready && main.host.home());
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
                // Rounded corners belong to the workspace, not the application's rectangular
                // logical viewport. Use the unclipped view bounds to retain the correct arc
                // when the keyboard moves part of the main pane beyond the screen.
                int[] location = new int[2];
                main.host.view.getLocationOnScreen(location);
                RectF bounds = new RectF(location[0], location[1],
                        location[0] + main.host.view.getWidth() * main.container.getScaleX(),
                        location[1] + main.host.view.getHeight() * main.container.getScaleY());
                Path shape = new Path();
                shape.addRoundRect(bounds, dp(PANE_RADIUS_DP), dp(PANE_RADIUS_DP), Path.Direction.CW);
                Region touchable = new Region();
                touchable.setPath(shape, new Region(visible));
                obscured.op(touchable, Region.Op.DIFFERENCE);
            }
        }
        if (recentStrip != null) {
            Rect toolbar = new Rect();
            if (recentStrip.getGlobalVisibleRect(toolbar)) obscured.op(toolbar, Region.Op.UNION);
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
                if (pane.launchHost != null) pane.launchHost.obscure(new Rect(screenBounds));
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
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOnClickListener(v -> { });
        HorizontalScrollView strip = new HorizontalScrollView(context);
        recentScroll = strip;
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(true);
        strip.setContentDescription("应用选择栏，点击切换主窗口，长按拖入任意窗口");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x80171c25);
        background.setCornerRadius(dp(18));
        background.setStroke(Math.max(1, dp(0.7f)), 0x30ffffff);
        toolbar.setBackground(background);
        toolbar.setClipToOutline(true);
        toolbar.setElevation(dp(4));
        recentRow = new LinearLayout(context);
        recentRow.setGravity(Gravity.CENTER_VERTICAL);
        recentRow.setPadding(dp(8), 0, dp(8), 0);
        recentRow.setOnClickListener(v -> { });
        recentCards.clear();
        recentCards.addAll(recent);
        recentOrderDirty = true;
        strip.addView(recentRow, new HorizontalScrollView.LayoutParams(-2, -1));
        toolbar.addView(strip, new LinearLayout.LayoutParams(0, -1, 1f));
        View divider = new View(context);
        divider.setBackgroundColor(0x30ffffff);
        toolbar.addView(divider, new LinearLayout.LayoutParams(Math.max(1, dp(0.7f)), dp(28)));
        exitButton = new ImageView(context);
        exitButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        exitButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        // The workspace uses SystemUI's Context; resolve module resources explicitly.
        try {
            Context module = context.createPackageContext(com.example.flymestatusbarsizer.BuildConfig.APPLICATION_ID, 0);
            exitButton.setImageDrawable(module.getDrawable(R.drawable.ic_workspace_exit));
        } catch (Exception error) {
            exitButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            exitButton.setColorFilter(Color.WHITE);
        }
        exitButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff), null, null));
        exitButton.setContentDescription("退出工作台");
        exitButton.setTooltipText("退出工作台");
        exitButton.setFocusable(true);
        exitButton.setOnClickListener(v -> { if (active() && dragSession == null) close(true); });
        toolbar.addView(exitButton, new LinearLayout.LayoutParams(dp(56), dp(56)));
        updateRecentIcons();
        return toolbar;
    }

    private static boolean sameEntry(RecentTaskCard a, RecentTaskCard b) {
        return a.sameTask(b) && a.component != null && b.component != null
                && a.component.getPackageName().equals(b.component.getPackageName());
    }

    private void pinRecent(RecentTaskCard card) {
        if (card == null || card.home || card.component == null || card.temporary) return;
        pinnedCards.removeIf(item -> sameEntry(item, card));
        pinnedCards.add(0, card);
        recentOrderDirty = true;
        updateRecentIcons();
    }

    private void rememberRecent(RecentTaskCard card) {
        if (card == null || card.home || card.component == null || card.temporary) return;
        for (RecentTaskCard item : recentCards) if (sameEntry(item, card)) return;
        recentCards.add(0, card);
        recentOrderDirty = true;
    }

    private Pane hostedPane(RecentTaskCard card) {
        for (Pane pane : panes) if (pane != null && pane.card != null && pane.card.sameTask(card)) return pane;
        return null;
    }

    private boolean isHosted(RecentTaskCard card) { return hostedPane(card) != null; }

    private boolean panesBusy() {
        for (Pane pane : panes) if (pane != null && (pane.replacing || pane.launchHost != null)) return true;
        return false;
    }

    private boolean canChangeTask() {
        return running() && !suspended && !rebinding && !covered && hostVisible && !panesBusy() && !desktopBusy && animator == null
                && transitionAnimator == null && imeAnimator == null && !imeAnimating
                && dragSession == null && !OneHandedTaskHooks.shadeOpen();
    }

    private void updateRecentIcons() {
        if (recentOrderDirty && dragSession == null && recentRow != null) {
            recentOrderDirty = false;
            recentRow.removeAllViews();
            recentIcons.clear();
            ArrayList<RecentTaskCard> ordered = new ArrayList<>(pinnedCards);
            for (RecentTaskCard card : recentCards) {
                boolean duplicate = false;
                for (RecentTaskCard item : ordered) if (sameEntry(item, card)) { duplicate = true; break; }
                if (!duplicate && !card.home && !card.temporary) ordered.add(card);
            }
            final int request = generation;
            for (RecentTaskCard card : ordered) {
                if (card.component == null) continue;
                ImageView icon = new ImageView(context);
                icon.setTag(card);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                icon.setImageDrawable(context.getPackageManager().getDefaultActivityIcon());
                icon.setOnClickListener(v -> showTask(card, mainSlot));
                icon.setHapticFeedbackEnabled(false);
                icon.setOnLongClickListener(v -> beginAppDrag(icon, card));
                recentRow.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
                recentIcons.add(icon);
                iconLoader.execute(() -> {
                    Drawable drawable;
                    try { drawable = context.getPackageManager().getActivityIcon(card.component); }
                    catch (Exception error) { drawable = context.getPackageManager().getDefaultActivityIcon(); }
                    Drawable result = drawable;
                    handler.post(() -> {
                        if (active() && generation == request && icon.getParent() == recentRow)
                            icon.setImageDrawable(result);
                    });
                });
            }
            if (!pinnedCards.isEmpty() && recentScroll != null) recentScroll.scrollTo(0, 0);
        }
        for (ImageView icon : recentIcons) {
            RecentTaskCard card = (RecentTaskCard) icon.getTag();
            Pane pane = hostedPane(card);
            icon.setAlpha(icon == dragSource ? 0.4f : pane == null ? 1f : 0.65f);
            icon.setLongClickable(true);
            icon.setContentDescription(card.description + (pane == null ? "" : "，已在工作台中")
                    + "，点击切换主窗口，长按拖入任意窗口");
        }
    }

    private void showTask(RecentTaskCard card, int slot) {
        if (!canChangeTask() || slot < 0 || slot >= COUNT || panes[slot] == null) return;
        Pane source = hostedPane(card);
        Pane target = panes[slot];
        if (source != null) {
            if (!source.card.component.getPackageName().equals(card.component.getPackageName())) {
                // Activities sharing one task cannot be split into two TaskViews.
                if (slot != mainSlot) {
                    Toast.makeText(context, "这两个页面属于同一任务，无法同时分窗，请点击图标返回", Toast.LENGTH_SHORT).show();
                } else if (source.slot != mainSlot) {
                    select(source.slot);
                    int request = generation;
                    handler.postDelayed(() -> {
                        if (running() && generation == request) showTask(card, mainSlot);
                    }, 300);
                } else restorePage(source, card);
                return;
            }
            if (source == target) return;
            if (source.host == null || !source.host.ready || (target.host != null && !target.host.ready)) return;
            int oldSlot = source.slot;
            pinRecent(target.card);
            panes[slot] = source;
            panes[oldSlot] = target;
            source.slot = slot;
            target.slot = oldSlot;
            for (Pane pane : panes) {
                position(pane.container, frames[pane.slot]);
                pane.updateAppearance();
                pane.updateGeometry();
            }
            if (target.host != null && target.host.home() && target.slot != mainSlot) {
                disconnectDesktop();
                clearDesktop(target, this::updateInput);
            }
            shell.focus(panes[mainSlot].host);
            updateRecentIcons();
            updateInput();
            haptic(HapticFeedbackConstants.CONFIRM);
            return;
        }
        target.load(card);
    }

    private void restorePage(Pane pane, RecentTaskCard card) {
        pane.replacing = true;
        updateInput();
        int request = generation;
        taskWorker.execute(() -> {
            if (!active() || generation != request) return;
            boolean restored = false;
            try { tasks.returnToPage(card); restored = true; }
            catch (Exception error) { Log.w(TAG, "Cannot return to source Activity", error); }
            boolean success = restored;
            handler.post(() -> {
                if (!active() || generation != request) return;
                pane.replacing = false;
                if (!success) Toast.makeText(context, "此页面无法直接恢复，请使用应用内返回", Toast.LENGTH_SHORT).show();
                scheduleCheck(0);
                updateInput();
            });
        });
    }

    private boolean beginAppDrag(ImageView icon, RecentTaskCard card) {
        if (!canChangeTask()
                || backdrop == null || !backdrop.hasDragPointer()) return false;
        clearPaneTapHighlight();
        blockInput(true);
        dragSession = new DragSession(card, generation);
        dragSource = icon;
        try {
            // WindowlessWindowManager does not provide a normal window for system drag
            // dispatch. This drag stays inside the embedded workspace's touch stream.
            backdrop.takeDragTouch();
            dragPreview = new ImageView(context);
            Drawable drawable = icon.getDrawable();
            Drawable.ConstantState constant = drawable == null ? null : drawable.getConstantState();
            dragPreview.setImageDrawable(constant == null ? drawable : constant.newDrawable(context.getResources()).mutate());
            dragPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            dragPreview.setPadding(dp(8), dp(8), dp(8), dp(8));
            dragPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            backdrop.addView(dragPreview, new FrameLayout.LayoutParams(dp(56), dp(56)));
            updateRecentIcons();
            moveAppDrag(backdrop.touchX, backdrop.touchY);
            haptic(HapticFeedbackConstants.LONG_PRESS);
            return true;
        } catch (RuntimeException e) { Log.w(TAG, "Cannot start app drag", e); }
        finishAppDrag(-1);
        return false;
    }

    private void moveAppDrag(float x, float y) {
        DragSession session = dragSession;
        if (session == null) return;
        if (!running() || session.generation != generation || OneHandedTaskHooks.shadeOpen()) {
            finishAppDrag(-1);
            return;
        }
        if (dragPreview != null) {
            dragPreview.setTranslationX(x - dp(28));
            dragPreview.setTranslationY(y - dp(28));
        }
        highlightDropTarget(dropSlot(x, y));
    }

    private void finishAppDrag(int targetSlot) {
        DragSession session = dragSession;
        dragSession = null;
        dragSource = null;
        if (dragPreview != null && backdrop != null) backdrop.removeView(dragPreview);
        dragPreview = null;
        highlightDropTarget(-1);
        if (session == null) return;
        updateRecentIcons();
        updateInput();
        if (targetSlot >= 0 && targetSlot < COUNT) {
            handler.post(() -> {
                if (!running() || generation != session.generation) return;
                showTask(session.card, targetSlot);
            });
        }
    }

    private int dropSlot(float pointerX, float pointerY) {
        if (!running() || workspace == null || OneHandedTaskHooks.shadeOpen()) return -1;
        int x = (int) (pointerX - workspace.getX());
        int y = (int) (pointerY - workspace.getY());
        for (int slot = 0; slot < COUNT; slot++) {
            if (panes[slot] != null && !panes[slot].replacing && frames[slot].contains(x, y)) return slot;
        }
        return -1;
    }

    private void showPaneTapFeedback(int slot) {
        clearPaneTapHighlight();
        tappedSlot = slot;
        panes[slot].updateAppearance();
        haptic(HapticFeedbackConstants.CLOCK_TICK);
        handler.postDelayed(clearTapHighlight, PANE_TAP_HIGHLIGHT_MS);
    }

    private void clearPaneTapHighlight() {
        handler.removeCallbacks(clearTapHighlight);
        int previous = tappedSlot;
        tappedSlot = -1;
        if (previous >= 0 && panes[previous] != null) panes[previous].updateAppearance();
    }

    private void highlightDropTarget(int slot) {
        if (highlightedSlot == slot) return;
        highlightedSlot = slot;
        if (slot >= 0 && dragSession != null) haptic(HapticFeedbackConstants.CLOCK_TICK);
        for (Pane pane : panes) {
            if (pane == null) continue;
            pane.updateAppearance();
        }
    }

    private static final class DragSession {
        final RecentTaskCard card;
        final int generation;

        DragSession(RecentTaskCard card, int generation) {
            this.card = card;
            this.generation = generation;
        }
    }

    private final class WorkspaceRoot extends FrameLayout {
        private int pointerId = -1;
        private int pointerCount;
        private long downTime;
        private float touchX;
        private float touchY;
        private boolean ownsTouch;

        WorkspaceRoot() { super(context); }

        boolean hasDragPointer() { return pointerId >= 0 && pointerCount == 1; }

        void takeDragTouch() {
            // Cancel the icon/HorizontalScrollView once, then retain this gesture until
            // UP/CANCEL. In particular, dropping must not click a pane or the backdrop.
            MotionEvent cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, touchX, touchY, 0);
            try { super.dispatchTouchEvent(cancel); }
            finally { cancel.recycle(); }
            ownsTouch = true;
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            boolean terminal = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
            if (action == MotionEvent.ACTION_DOWN) {
                finishAppDrag(-1);
                ownsTouch = false;
                pointerId = event.getPointerId(0);
                downTime = event.getDownTime();
            }
            pointerCount = event.getPointerCount();
            int index = event.findPointerIndex(pointerId);
            if (index >= 0) {
                touchX = event.getX(index);
                touchY = event.getY(index);
            }
            if (dragSession != null) {
                if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                        || action == MotionEvent.ACTION_POINTER_UP || index < 0) {
                    finishAppDrag(-1);
                } else if (action == MotionEvent.ACTION_UP) {
                    finishAppDrag(dropSlot(touchX, touchY));
                } else if (action == MotionEvent.ACTION_MOVE) {
                    moveAppDrag(touchX, touchY);
                }
            }
            // Intercept at the root even when the scrolling toolbar has requested that
            // its parents not intercept. Before long-press, its normal scrolling is intact.
            boolean handled = ownsTouch || super.dispatchTouchEvent(event);
            if (terminal) {
                ownsTouch = false;
                pointerId = -1;
                pointerCount = 0;
            }
            return handled;
        }

        @Override protected void onDetachedFromWindow() {
            if (backdrop == this) finishAppDrag(-1);
            super.onDetachedFromWindow();
        }
    }

    void refresh() {
        handler.post(() -> { if (active()) { refreshEnvironment(); scheduleCheck(100); } });
    }

    boolean blocksNavigation() {
        return active() && !suspended
                && OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
    }

    private void refreshEnvironment() {
        if (!active()) return;
        boolean next = !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
        try { navigation.setLocked(!next, workspaceOwnerUserId); }
        catch (Exception error) { fail("无法更新工作台导航状态", error); return; }
        if (suspended == next) return;
        suspended = next;
        shell.setSuspended(next);
        OneStepStatusBar.setVisible(!suspended && !covered && hostVisible);
        if (backdrop != null) backdrop.setVisibility(suspended ? View.INVISIBLE : View.VISIBLE);
        if (suspended) {
            handler.removeCallbacks(openingTimeout);
            finishAppDrag(-1);
            if (animator != null) animator.end();
            if (transitionAnimator != null) transitionAnimator.end();
        } else {
            if (state == State.OPENING) {
                handler.removeCallbacks(openingTimeout);
                handler.postDelayed(openingTimeout, 12000);
                startInitialTask();
            }
            if (hostVisible && !covered && !OneHandedTaskHooks.shadeOpen()) shell.focus(panes[mainSlot] == null ? null : panes[mainSlot].host);
        }
        updateInput();
    }

    private void scheduleCheck(long delay) {
        if (!active()) return;
        handler.removeCallbacks(check);
        handler.postDelayed(check, delay);
    }

    private void checkTasks() {
        if (!active()) return;
        refreshEnvironment();
        if (!suspended && !rebinding) shell.checkFocus(generation);
        scheduleCheck(suspended ? 700 : taskNotifications ? 3000 : 700);
    }

    @Override public void onCoverageChanged(boolean value) {
        if (!active()) return;
        covered = value;
        OneStepStatusBar.setVisible(!value && !suspended && hostVisible);
        if (value) {
            finishAppDrag(-1);
        } else if (running() && !suspended && !rebinding && hostVisible && !panesBusy() && !OneHandedTaskHooks.shadeOpen()) {
            shell.focus(panes[mainSlot].host);
        }
        updateInput();
    }

    @Override public void onTaskChanged(OneStepShell.Host host, RecentTaskCard previous) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.card = host.card;
            if (pane.slot == mainSlot) pinRecent(previous);
            rememberRecent(host.card);
            updateRecentIcons();
            return;
        }
    }

    @Override public void onNavigation(OneStepShell.Navigation navigation) {
        acceptNavigation(navigation, 0);
    }

    private void acceptNavigation(OneStepShell.Navigation navigation, int attempt) {
        if (!active() || navigation.generation != generation || navigation.cancelled) return;
        if (!running() || suspended || rebinding || animator != null || transitionAnimator != null || panesBusy()
                || dragSession != null || desktopBusy || OneHandedTaskHooks.shadeOpen()) {
            if (attempt < 100) handler.postDelayed(() -> acceptNavigation(navigation, attempt + 1), 50);
            else shell.abandonNavigation(navigation);
            return;
        }
        Pane pane = panes[mainSlot];
        Pane existing = hostedPane(navigation.card);
        if (existing != null) {
            // Focus/task reuse must use the same move path as an explicit icon selection.
            if (canChangeTask()) {
                showTask(existing.card, mainSlot);
                shell.completeNavigation(navigation);
            } else if (attempt < 100) handler.postDelayed(() -> acceptNavigation(navigation, attempt + 1), 50);
            else shell.abandonNavigation(navigation);
            return;
        }
        pane.replacing = true;
        blockInput(true);
        try {
            pane.launchHost = shell.createNavigation(context, navigation);
            pane.mount(pane.launchHost);
            pane.launchHost.view.bringToFront();
            shell.geometry(pane.launchHost, logicalBounds, logicalBounds.width(), logicalBounds.height(), true);
        } catch (Exception error) {
            Log.w(TAG, "Cannot accept application navigation", error);
            if (pane.launchHost != null) {
                OneStepShell.Host failed = pane.launchHost;
                shell.abandonNavigation(navigation);
                shell.release(failed, () -> onLaunchFailed(failed));
            }
            else { pane.replacing = false; shell.abandonNavigation(navigation); updateInput(); }
        }
    }

    @Override public void onReady(OneStepShell.Host host) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.launchHost == host) {
            OneStepShell.Host previous = pane.host;
            RecentTaskCard previousCard = pane.card;
            pane.launchHost = null;
            pane.host = host;
            pane.card = host.card;
            pane.empty.setVisibility(View.GONE);
            if (previous != null && previous.home()) disconnectDesktop();
            if (host.navigation != null && previousCard != null && !previousCard.home) {
                pane.history.removeIf(card -> card.sameTask(previousCard));
                pane.history.add(0, previousCard);
            } else pane.history.clear();
            if (host.navigation != null) shell.completeNavigation(host.navigation);
            if (!host.card.temporary) {
                RecentTaskCard pin = previousCard;
                if (pin != null && pin.temporary) for (RecentTaskCard item : pane.history) {
                    if (!item.temporary) { pin = item; break; }
                }
                pinRecent(pin);
            }
            rememberRecent(host.card);
            shell.focus(panes[mainSlot].host);
            int request = generation;
            Runnable finished = () -> {
                if (!running() || generation != request) return;
                if (previous != null) pane.container.removeView(previous.view);
                pane.replacing = false;
                desktopBusy = false;
                pane.updateAppearance();
                updateRecentIcons();
                updateInput();
            };
            if (previous != null) shell.release(previous, finished);
            else finished.run();
            return;
        }
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.card = host.card;
            pane.replacing = false;
            rememberRecent(host.card);
            updateRecentIcons();
            if (host.home()) desktopBusy = false;
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

    @Override public void onLaunchFailed(OneStepShell.Host host) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null) {
            if (pane.launchHost == host) {
                pane.container.removeView(host.view);
                pane.launchHost = null;
                pane.replacing = false;
                desktopBusy = false;
                if (desktopSession != null) desktopSession.retry();
                pane.empty.setVisibility(pane.host == null ? View.VISIBLE : View.GONE);
                shell.focus(panes[mainSlot].host);
                Toast.makeText(context, "应用未能打开，请重试", Toast.LENGTH_SHORT).show();
                updateInput();
                return;
            }
            if (pane.host == host) {
                pane.container.removeView(host.view);
                pane.host = null;
                pane.card = null;
                pane.replacing = false;
                pane.showEmpty();
                if (state == State.OPENING) fail("无法打开应用窗口", new IllegalStateException("Initial task unavailable"));
                else if (host.home()) desktopUnavailable(pane);
                else {
                    Toast.makeText(context, "暂时无法打开应用", Toast.LENGTH_SHORT).show();
                    if (pane.slot == mainSlot) returnToPrevious(pane, generation);
                    updateInput();
                }
                return;
            }
        }
    }

    @Override public void onTaskReused(OneStepShell.Host launching, OneStepShell.Host existing) {
        if (!running()) return;
        for (Pane target : panes) if (target != null && target.host == existing && existing.ready) {
            for (Pane source : panes) if (source != null && source.launchHost == launching) {
                source.container.removeView(launching.view);
                source.launchHost = null;
                source.empty.setVisibility(View.GONE);
                selectReusedTask(target, generation, 0);
                return;
            }
        }
        onLaunchFailed(launching);
    }

    private void selectReusedTask(Pane target, int request, int attempt) {
        if (!running() || generation != request) return;
        if (target.host == null || !target.host.ready || attempt >= 30 || OneHandedTaskHooks.shadeOpen()) {
            desktopBusy = false;
            if (desktopSession != null) desktopSession.retry();
            shell.focus(panes[mainSlot].host);
            updateInput();
            return;
        }
        if (animator != null || transitionAnimator != null || imeAnimator != null || imeAnimating) {
            handler.postDelayed(() -> selectReusedTask(target, request, attempt + 1), 100);
            return;
        }
        desktopBusy = false;
        select(target.slot);
    }

    @Override public void onRemoved(OneStepShell.Host host) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.launchHost == host) { onLaunchFailed(host); return; }
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            if (host.home()) disconnectDesktop();
            if (pane.launchHost != null) {
                // The source may finish as part of a successful redirect. Its successor
                // still owns this pane's pending handoff and must be allowed to mount.
                pane.container.removeView(host.view);
                pane.host = null;
                pane.card = null;
                return;
            }
            pane.container.removeView(host.view);
            pane.host = null;
            pane.card = null;
            pane.replacing = false;
            pane.showEmpty();
            if (pane.slot == mainSlot) {
                desktopBusy = false;
                shell.focus(null);
                if (!host.home()) returnToPrevious(pane, generation);
            }
        }
        updateRecentIcons();
        updateInput();
    }

    private void returnToPrevious(Pane pane, int request) {
        if (!running() || generation != request || pane != panes[mainSlot] || pane.host != null) return;
        if (!canChangeTask()) {
            handler.postDelayed(() -> returnToPrevious(pane, request), 100);
            return;
        }
        while (!pane.history.isEmpty()) {
            RecentTaskCard previous = pane.history.remove(0);
            Pane existing = hostedPane(previous);
            if (existing != null) { showTask(existing.card, mainSlot); return; }
            if (pane.load(previous)) return;
        }
        returnToDesktop(pane, request);
    }

    private void returnToDesktop(Pane pane, int request) {
        if (!running() || generation != request || pane != panes[mainSlot] || pane.host != null
                || pane.launchHost != null || pane.replacing || desktopBusy) return;
        if (animator != null || transitionAnimator != null || imeAnimator != null || imeAnimating
                || dragSession != null || OneHandedTaskHooks.shadeOpen()) {
            handler.postDelayed(() -> returnToDesktop(pane, request), 100);
            return;
        }
        enterDesktop(pane.slot, false);
    }

    @Override public void onFailure(String message, Exception error) { if (active()) fail(message, error); }

    private void close(boolean focusMain) {
        if (state == State.CLOSED) return;
        if (state == State.CLOSING) {
            if (!focusMain) {
                cancelAnimator(false);
                shell.close(-1, this::finishClose);
            }
            return;
        }
        if (focusMain && running()) haptic(HapticFeedbackConstants.CONTEXT_CLICK);
        state = State.CLOSING;
        if (exitButton != null) exitButton.setEnabled(false);
        releaseNavigation();
        clearPaneTapHighlight();
        disconnectDesktop();
        handler.removeCallbacks(openingTimeout);
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        onSuccess = null;
        openingTask = null;
        blockInput(true);
        cancelAnimator(true);
        cancelAnimator(false);
        cancelImeAnimator();
        imeAnimating = false;
        shellImeControlled = false;
        shellImePositioning = false;
        finishAppDrag(-1);
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
        cancelImeAnimator();
        imeAnimating = false;
        shellImeControlled = false;
        shellImePositioning = false;
        if (perf != null) { perf.stop(); perf = null; }
        OneStepStatusBar.setVisible(false);
        if (activitySession != null) { activitySession.close(); activitySession = null; }
        backdrop = null;
        workspace = null;
        recentStrip = null;
        recentScroll = null;
        exitButton = null;
        recentIcons.clear();
        recentCards.clear();
        pinnedCards.clear();
        recentRow = null;
        recentOrderDirty = false;
        for (int i = 0; i < COUNT; i++) panes[i] = null;
        generation++;
        desktopBusy = false;
        state = State.CLOSED;
        suspended = false;
        rebinding = false;
        initialTaskStarted = false;
        releaseNavigation();
    }

    private void releaseNavigation() {
        if (active()) return;
        try { navigation.setLocked(false, workspaceOwnerUserId); }
        catch (Exception error) {
            Log.w(TAG, "Navigation release will retry", error);
            handler.postDelayed(this::releaseNavigation, 500);
        }
    }

    private void fail(String message, Exception error) {
        Log.w(TAG, message, error);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        close(false);
    }

    private void haptic(int feedback) { HapticFeedbackUtils.perform(context, backdrop, feedback); }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private final class Pane {
        int slot;
        final ArrayList<RecentTaskCard> history = new ArrayList<>();
        final FrameLayout container = new FrameLayout(context);
        final TextView empty = new TextView(context);
        final GradientDrawable fill = new GradientDrawable();
        final GradientDrawable border = new GradientDrawable();
        final ViewOutlineProvider outline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
                out.setAlpha(transitionProgress);
            }
        };
        float cornerRadius;
        RecentTaskCard card;
        OneStepShell.Host host;
        OneStepShell.Host launchHost;
        boolean replacing;

        Pane(int slot) {
            this.slot = slot;
            container.setPivotX(0);
            container.setPivotY(0);
            fill.setColor(0x60171c25);
            container.setBackground(fill);
            container.setForeground(border);
            container.setOutlineProvider(outline);
            container.setClipToOutline(true);
            container.setOutlineAmbientShadowColor(0x50000000);
            container.setOutlineSpotShadowColor(0x70000000);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(0xe6ffffff);
            empty.setTextSize(12);
            empty.setLineSpacing(dp(4), 1f);
            empty.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
            showEmpty();
            empty.setBackgroundColor(Color.TRANSPARENT);
            empty.setContentDescription("空白应用窗口，点击打开桌面，或长按上方应用图标拖入此处");
            empty.setOnClickListener(v -> { if (this.slot == mainSlot) enterDesktop(this.slot, true); else select(this.slot); });
            container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            container.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && this.slot != mainSlot) select(this.slot);
                return true;
            });
            container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateInput());
        }

        void updateAppearance() {
            float scale = Math.max(0.01f, container.getScaleX());
            // Pane views retain the full app viewport. Compensate their decorations for
            // scaling so all four windows keep the same on-screen corner/stroke/text size.
            cornerRadius = dp(PANE_RADIUS_DP) / scale;
            fill.setCornerRadius(cornerRadius);
            fill.setAlpha(Math.round(255 * transitionProgress));
            border.setCornerRadius(cornerRadius);
            boolean highlighted = highlightedSlot == slot || tappedSlot == slot;
            border.setColor(highlighted ? 0x225aaaff : Color.TRANSPARENT);
            border.setStroke(Math.max(1, Math.round(dp(highlighted ? 2 : 0.7f) / scale)),
                    highlighted ? 0xff9acbff : 0x40ffffff);
            border.setAlpha(Math.round(255 * transitionProgress));
            float mainScale = 0.01f;
            for (Rect frame : frames) mainScale = Math.max(mainScale, scaleForFrame(frame));
            // During swaps the larger pane carries the stronger shadow throughout the motion.
            float prominence = Math.max(0f, Math.min(1f, scale / Math.max(0.01f, mainScale)));
            container.setElevation(dp(8 + 10 * prominence * prominence) / scale);
            container.invalidateOutline();
            if (host != null) {
                host.view.invalidateOutline();
                host.setCornerRadius(cornerRadius);
            }
            if (launchHost != null) {
                launchHost.view.invalidateOutline();
                launchHost.setCornerRadius(cornerRadius);
            }
            // Counter-scale the centered label without requesting layout on every frame.
            empty.setScaleX(1f / scale);
            empty.setScaleY(1f / scale);
            empty.setAlpha(transitionProgress);
        }

        boolean load(RecentTaskCard next) {
            if (!active() || replacing || launchHost != null || isHosted(next)) return false;
            replacing = true;
            int request = generation;
            OneStepShell.Host previous = host;
            // Validate dormant entries off the UI thread. A stale icon must leave the
            // current pane intact instead of closing the whole workspace.
            taskWorker.execute(() -> {
                RecentTaskCard resolved = null;
                try {
                    if (next.home) resolved = next;
                    else {
                        for (Object info : tasks.roots()) if (next.taskId == OneStepTaskAccess.taskId(info)
                                && next.token.equals(OneStepTaskAccess.token(info))
                                && next.userId == ReflectUtils.getIntField(info, "userId", -1)
                                && OneStepTaskAccess.externalTaskAllowed(info)) {
                            resolved = OneStepTaskAccess.runningCard(info);
                            break;
                        }
                        if (resolved == null) resolved = tasks.findCandidate(next);
                    }
                } catch (Exception error) { Log.w(TAG, "Cannot resolve selected task", error); }
                RecentTaskCard cardToLoad = resolved;
                handler.post(() -> {
                    if (!active() || generation != request || panes[slot] != this) return;
                    if (cardToLoad == null) {
                        replacing = false;
                        pinnedCards.removeIf(card -> card.sameTask(next));
                        recentCards.removeIf(card -> card.sameTask(next));
                        recentOrderDirty = true;
                        updateRecentIcons();
                        Toast.makeText(context, "应用任务已结束", Toast.LENGTH_SHORT).show();
                        if (host == null && slot == mainSlot) returnToPrevious(this, request);
                        updateInput();
                        return;
                    }
                    try {
                        OneStepShell.Host created = shell.create(context, cardToLoad,
                                state == State.OPENING && slot == mainSlot, true);
                        if (previous == null) { host = created; card = cardToLoad; }
                        else launchHost = created;
                        mount(created);
                        created.view.bringToFront();
                        if (Build.VERSION.SDK_INT >= 34) created.view.setAlpha(transitionProgress);
                        shell.geometry(created, logicalBounds, logicalBounds.width(), logicalBounds.height(), slot == mainSlot);
                        updateAppearance();
                        updateInput();
                    } catch (Exception error) {
                        replacing = false;
                        if (launchHost != null) {
                            OneStepShell.Host failed = launchHost;
                            shell.release(failed, () -> onLaunchFailed(failed));
                        } else if (host != null && host != previous) {
                            OneStepShell.Host failed = host;
                            shell.release(failed, () -> onLaunchFailed(failed));
                        } else if (state == State.OPENING) fail("无法创建应用窗口", error);
                        else {
                            Log.w(TAG, "Cannot replace application", error);
                            Toast.makeText(context, "暂时无法打开应用", Toast.LENGTH_SHORT).show();
                            updateInput();
                        }
                    }
                });
            });
            updateInput();
            return true;
        }

        void mount(OneStepShell.Host task) throws ReflectiveOperationException {
            task.view.setOutlineProvider(outline);
            task.view.setClipToOutline(true);
            // Apply before attachment so the first app frame is rounded at launch handoff.
            task.setCornerRadius(cornerRadius);
            task.view.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && this.slot != mainSlot) select(this.slot);
                return true;
            });
            task.obscure(new Rect(screenBounds));
            container.addView(task.view, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        void showEmpty() {
            empty.setText("＋");
            empty.setVisibility(View.VISIBLE);
        }

        void updateGeometry() {
            if (!active()) return;
            // Keep the task surface at 1:1 inside TaskView; only the container scales it.
            if (host != null) shell.geometry(host, logicalBounds, logicalBounds.width(), logicalBounds.height(), slot == mainSlot);
            if (launchHost != null) shell.geometry(launchHost, logicalBounds, logicalBounds.width(), logicalBounds.height(), slot == mainSlot);
        }
    }
}
