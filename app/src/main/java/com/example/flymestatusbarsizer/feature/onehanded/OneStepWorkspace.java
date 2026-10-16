package com.example.flymestatusbarsizer.feature.onehanded;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;
import com.example.flymestatusbarsizer.util.ReflectUtils;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Workspace UI; task ownership, surfaces and restoration live in OneStepShell. */
final class OneStepWorkspace implements OneStepShell.Listener {
    private static final String TAG = OneStepFlowLog.TAG;
    private static final int COUNT = OneStepLandscapeWindow.PANE_COUNT;
    private static final int TOOLBAR_ICON_SIZE_DP = 48;
    private static final int TOOLBAR_ICON_PADDING_DP = 6;
    private static final int TOOLBAR_EXIT_PADDING_DP = 12;
    private static final int PANE_GAP_DP = 4;
    private static final int WORKSPACE_MARGIN_DP = 4;
    private static final int PANE_RADIUS_DP = 16;
    private static final int PANE_TAP_HIGHLIGHT_MS = 350;
    private static final long IME_HIDE_TIMEOUT_MS = 2500;
    private static final long IME_SETTLE_MS = 100;
    private static final long CLOSE_WATCHDOG_MS = 8000;
    private static final PathInterpolator EASING = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private enum State { CLOSED, OPENING, RUNNING, CLOSING }
    private volatile State state = State.CLOSED;
    private final Context context;
    private final Handler handler;
    private final OneStepTaskAccess tasks;
    private final OneStepShell shell;
    private final OneStepNavigation navigation;
    private final OneStepTouchMode touchMode = new OneStepTouchMode();
    private final OneStepLandscapeWindow landscapeWindow = new OneStepLandscapeWindow();
    private final Pane[] panes = new Pane[COUNT];
    private final ArrayList<Integer> sideOrder = new ArrayList<>();
    private final OneStepRecentAppsBar recentApps;
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
    private final Runnable exitAnimationTimeout = () -> {
        if (state == State.CLOSING && (this.transitionHandoff || this.transitionAnimator != null)) close(false);
    };
    private final Runnable closeWatchdog = this::onCloseWatchdog;
    private WorkspaceRoot backdrop;
    private FrameLayout workspace;
    private View recentStrip;
    private ValueAnimator animator;
    private ValueAnimator layoutAnimator;
    private ValueAnimator transitionAnimator;
    private ValueAnimator imeAnimator;
    private Rect[] frames;
    private Rect contentBounds;
    private final Rect logicalBounds = new Rect();
    private final Rect portraitBounds = new Rect();
    private final Rect desktopBounds = new Rect();
    private final java.util.Map<OneStepShell.Host, Integer> pendingOrientations = new java.util.IdentityHashMap<>();
    private final java.util.Map<OneStepShell.Host, Integer> requestedOrientations = new java.util.IdentityHashMap<>();
    private final java.util.Map<OneStepShell.Host, Integer> manualOrientations = new java.util.IdentityHashMap<>();
    private final Runnable routeOrientations = this::applyPendingOrientations;
    private long orientationMoveAfter;
    private boolean movingTask;
    private int focusedSlot;
    private final Rect transitionBounds = new Rect();
    private final Rect screenBounds = new Rect();
    private OneStepActivitySession activitySession;
    private OneStepLauncherBridge.Session desktopSession;
    private OneStepShell.Host desktopHost;
    private FrameLayout desktopContainer;
    private GradientDrawable desktopBorder;
    private int desktopRequest;
    private boolean desktopBusy;
    private int workspaceUserId;
    private int workspaceOwnerUserId;
    private int width;
    private int height;
    private int layoutToolbarHeightDp = -1;
    private int layoutToolbarPositionDp = -1;
    private boolean layoutKeepMainAspectRatio;
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
    private RectF imeInputRegion;
    private int imePlacement = OneStepImePolicy.UNKNOWN;
    private boolean mainOnLeft;
    private boolean sideWindowsCollapsed;
    private boolean sideWindowsExpanded;
    private boolean sideWindowsHidden;
    private boolean taskNotifications;
    private float transitionProgress;
    private DragSession dragSession;
    private ImageView dragSource;
    private ImageView dragPreview;
    private boolean inputRegionRefreshPending;
    private Runnable onSuccess;
    private RecentTaskCard openingTask;
    private boolean initialTaskStarted;
    private boolean entryAnimationStarted;
    private boolean transitionHandoff;
    private volatile boolean entryStarted;
    private boolean homeRequested;
    private int closeGeneration;
    private boolean rebinding;
    private OneStepPerf perf;
    private volatile boolean oneHandedProfile;

    private void onCloseWatchdog() {
        if (state != State.CLOSING) return;
        OneStepFlowLog.warn("workspace_close_timeout", generation,
                "closeGeneration=" + closeGeneration,
                new IllegalStateException("Workspace close exceeded " + CLOSE_WATCHDOG_MS + "ms"));
        shell.forceClose(this::finishClose);
    }

    OneStepWorkspace(Context source, Handler handler, Object transitions, Object factory, Object displayAreas)
            throws Exception {
        this.handler = handler;
        DisplayManager displays = source.getSystemService(DisplayManager.class);
        context = source.createDisplayContext(displays.getDisplay(0));
        tasks = new OneStepTaskAccess(source);
        navigation = new OneStepNavigation(source);
        shell = new OneStepShell(source, handler, transitions, factory, displayAreas, tasks);
        shell.setListener(this);
        recentApps = new OneStepRecentAppsBar(context, handler, iconLoader,
                new OneStepRecentAppsBar.Actions() {
                    @Override public int iconSizePx() { return toolbarIconSizePx(); }
                    @Override public int iconPaddingPx() { return toolbarIconPaddingPx(); }
                    @Override public int exitPaddingPx() { return toolbarExitPaddingPx(); }
                    @Override public int dp(float value) { return OneStepWorkspace.this.dp(value); }
                    @Override public boolean active() { return OneStepWorkspace.this.active(); }
                    @Override public boolean dragging() { return dragSession != null; }
                    @Override public boolean isHosted(RecentTaskCard card) { return OneStepWorkspace.this.isHosted(card); }
                    @Override public ImageView dragSource() { return OneStepWorkspace.this.dragSource; }
                    @Override public void select(RecentTaskCard card) { showTask(card, mainSlot); }
                    @Override public boolean beginDrag(ImageView icon, RecentTaskCard card) {
                        return OneStepWorkspace.this.beginAppDrag(icon, card);
                    }
                    @Override public void toggleSideWindows() { OneStepWorkspace.this.toggleSideWindows(); }
                    @Override public void close() {
                        if (OneStepWorkspace.this.active() && dragSession == null)
                            OneStepWorkspace.this.close(true);
                    }
                });
        try {
            tasks.registerTaskChanges(() -> {
                if (!active()) return;
                handler.removeCallbacks(taskChanged);
                handler.post(taskChanged);
            });
            taskNotifications = true;
        } catch (Exception error) { OneStepFlowLog.w(TAG, "Task notifications unavailable", error); }
    }

    private boolean active() { return state == State.OPENING || state == State.RUNNING; }
    private boolean running() { return state == State.RUNNING; }

    private int toolbarHeightDp() {
        ModuleConfig config = ModuleConfig.load(context);
        return Math.max(SettingsStore.MIN_WORKSPACE_TOOLBAR_HEIGHT_DP,
                Math.min(SettingsStore.MAX_WORKSPACE_TOOLBAR_HEIGHT_DP,
                        oneHandedProfile ? config.oneHandedWorkspaceToolbarHeightDp
                                : config.workspaceToolbarHeightDp));
    }

    private int toolbarPositionDp() {
        ModuleConfig config = ModuleConfig.load(context);
        return Math.max(SettingsStore.MIN_WORKSPACE_TOOLBAR_POSITION_DP,
                Math.min(SettingsStore.MAX_WORKSPACE_TOOLBAR_POSITION_DP,
                        oneHandedProfile ? config.oneHandedWorkspaceToolbarPositionDp
                                : config.workspaceToolbarPositionDp));
    }

    private int toolbarIconSizeDp() {
        return Math.min(TOOLBAR_ICON_SIZE_DP, toolbarHeightDp());
    }

    private int toolbarIconPaddingDp() {
        return Math.min(TOOLBAR_ICON_PADDING_DP, Math.max(2, toolbarIconSizeDp() / 8));
    }

    private int toolbarExitPaddingDp() {
        return Math.min(TOOLBAR_EXIT_PADDING_DP, Math.max(4, toolbarIconSizeDp() / 4));
    }

    private int toolbarHeightPx() {
        return dp(toolbarHeightDp());
    }

    private int toolbarIconSizePx() {
        return dp(toolbarIconSizeDp());
    }

    private int toolbarIconPaddingPx() {
        return dp(toolbarIconPaddingDp());
    }

    private int toolbarExitPaddingPx() {
        return dp(toolbarExitPaddingDp());
    }

    private int toolbarTopGapPx() {
        return toolbarHeightPx() + dp(PANE_GAP_DP);
    }

    private void updateToolbarControls() {
        int height = toolbarHeightPx();
        int iconSize = toolbarIconSizePx();
        int exitPadding = toolbarExitPaddingPx();
        if (recentStrip != null) {
            FrameLayout.LayoutParams strip = (FrameLayout.LayoutParams) recentStrip.getLayoutParams();
            strip.height = height;
            recentStrip.setLayoutParams(strip);
        }
        recentApps.updateToolbarControls(iconSize, exitPadding, sideWindowsCollapsed);
        int iconPadding = toolbarIconPaddingPx();
        if (dragPreview != null) {
            dragPreview.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            FrameLayout.LayoutParams preview = (FrameLayout.LayoutParams) dragPreview.getLayoutParams();
            preview.width = iconSize;
            preview.height = iconSize;
            dragPreview.setLayoutParams(preview);
        }
    }

    boolean canTrigger() {
        if (state == State.OPENING || state == State.CLOSING || animator != null || layoutAnimator != null || dragSession != null
                || !(running() ? OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId)
                        : OneHandedTaskHooks.environmentAllowed(context))) return false;
        if (running()) return true;
        if (!shell.available()) return false;
        try { return tasks.focusedTask() != null; }
        catch (Exception error) { return false; }
    }

    void useSideGestureProfile() {
        if (!active()) oneHandedProfile = false;
    }

    void useOneHandedProfile() {
        if (!active()) oneHandedProfile = true;
    }

    void toggle(boolean fromLeft, Runnable success) {
        handler.post(() -> {
            if (active()) return;
            if (state != State.CLOSED || !shell.available() || !OneHandedTaskHooks.environmentAllowed(context)) return;
            try { workspaceOwnerUserId = OneHandedTaskHooks.currentUserId(); }
            catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot resolve workspace user", error); return; }
            state = State.OPENING;
            int request = ++generation;
            OneStepFlowLog.info("workspace_open_requested", request,
                    "side=" + (fromLeft ? "left" : "right")
                            + " profile=" + (oneHandedProfile ? "one-handed" : "side-gesture"));
            handler.postDelayed(openingTimeout, 12000);
            taskWorker.execute(() -> {
                try {
                    RecentTaskCard main = tasks.focusedTask();
                    List<RecentTaskCard> recent = tasks.candidates();
                    OneStepFlowLog.info("entry_tasks_resolved", request,
                            "mainTask=" + (main == null ? -1 : main.taskId)
                                    + " recentCount=" + (recent == null ? 0 : recent.size()));
                    handler.post(() -> {
                        if (state != State.OPENING || generation != request) return;
                        if (main == null || !OneHandedTaskHooks.environmentAllowed(context)) { close(false); return; }
                        try {
                            if (OneHandedTaskHooks.imeBusy()) {
                                navigation.hideIme();
                                awaitEntryImeHidden(main, recent, fromLeft, success, request,
                                        SystemClock.uptimeMillis() + IME_HIDE_TIMEOUT_MS, 0);
                            } else startActivity(main, recent, fromLeft, success);
                        }
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

    private void awaitEntryImeHidden(RecentTaskCard main, List<RecentTaskCard> recent,
                                    boolean fromLeft, Runnable success, int request,
                                    long deadline, long hiddenSince) {
        if (state != State.OPENING || generation != request) return;
        if (!OneHandedTaskHooks.environmentAllowed(context)) { close(false); return; }
        long now = SystemClock.uptimeMillis();
        long settledSince = OneHandedTaskHooks.imeBusy() ? 0 : (hiddenSince == 0 ? now : hiddenSince);
        if (settledSince != 0 && now - settledSince >= IME_SETTLE_MS) {
            try { startActivity(main, recent, fromLeft, success); }
            catch (Exception error) { fail("无法打开应用工作台", error); }
            return;
        }
        if (now >= deadline) {
            // No task surfaces or navigation locks have been acquired yet.
            fail("输入法未能收起，请稍后重试", new IllegalStateException("Entry IME hide timed out"));
            return;
        }
        handler.postDelayed(() -> awaitEntryImeHidden(main, recent, fromLeft, success,
                request, deadline, settledSince), 32);
    }

    private void startActivity(RecentTaskCard main, List<RecentTaskCard> recent, boolean fromLeft, Runnable success)
            throws Exception {
        int request = generation;
        workspaceUserId = main.userId;
        // A managed-profile task can belong to a different user than the visible system UI.
        workspaceOwnerUserId = OneHandedTaskHooks.currentUserId();
        suspended = !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
        entryStarted = true;
        navigation.setLocked(!suspended, workspaceOwnerUserId);
        onSuccess = success;
        openingTask = main;
        initialTaskStarted = false;
        entryAnimationStarted = false;
        transitionHandoff = false;
        shell.begin(request, main.userId);
        OneStepFlowLog.info("workspace_entry_started", request,
                "task=" + main.taskId + " user=" + main.userId + " suspended=" + suspended);
        shell.setSuspended(suspended);
        activitySession = new OneStepActivitySession(context, handler, main.userId, workspaceOwnerUserId, shell, new OneStepActivitySession.Listener() {
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
                    focusActivePane();
                updateInput();
                scheduleCheck(300);
            }

            @Override public void onResized(Rect bounds, Rect insets) {
                if (generation == request && state == State.CLOSING
                        && (transitionAnimator != null || transitionHandoff)) { close(false); return; }
                if (generation == request && active()) resizeWorkspace(bounds, insets);
            }

            @Override public void onReattaching(int taskId, Rect bounds, Rect insets, Runnable mount) {
                if (generation == request && state == State.CLOSING
                        && (transitionAnimator != null || transitionHandoff)) { close(false); return; }
                if (generation != request || !active()) return;
                if (state == State.OPENING) { close(false); return; }
                rebinding = true;
                hostVisible = false;
                shell.setHostVisible(false);
                finishAppDrag(-1);
                if (layoutAnimator != null) layoutAnimator.end();
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
                if (generation == request) close(focusMain);
            }

            @Override public void onBarColorsChanged(boolean darkIcons) {
                if (generation == request && active()) OneStepStatusBar.setDarkIcons(darkIcons);
            }

            @Override public void onVisibilityChanged(boolean visible) {
                if (generation == request && !visible && state == State.CLOSING
                        && (transitionAnimator != null || transitionHandoff)) { close(false); return; }
                if (generation != request || !active()) return;
                hostVisible = visible;
                shell.setHostVisible(visible);
                OneStepStatusBar.setVisible(visible && !suspended && !covered);
                if (visible) startInitialTask();
                updateInput();
                scheduleCheck(100);
            }
        });
        handler.removeCallbacks(openingTimeout);
        if (!suspended) handler.postDelayed(openingTimeout, 12000);
        shell.prepareWorkspaceEntry(main, bounds -> {
            if (generation != request || state != State.OPENING) return;
            transitionBounds.set(bounds);
            OneStepStatusBar.setBackgroundOwned(true);
            try { activitySession.start(); }
            catch (Exception error) { fail("无法打开工作台窗口", error); }
        });
        scheduleCheck(300);
    }

    private void startInitialTask() {
        if (state != State.OPENING || suspended || rebinding || !hostVisible || initialTaskStarted || openingTask == null
                || backdrop == null || activitySession == null || !activitySession.isMounted()) return;
        initialTaskStarted = panes[0].load(openingTask);
        if (!initialTaskStarted) return;
        applyTransition(0f);
    }

    private void startEntryAnimation() {
        if (state != State.OPENING || entryAnimationStarted || suspended || rebinding || !hostVisible
                || backdrop == null || panes[mainSlot].host == null || !panes[mainSlot].host.ready) return;
        entryAnimationStarted = true;
        int request = generation;
        backdrop.postOnAnimation(new Runnable() {
            @Override public void run() {
                if (state != State.OPENING || generation != request || backdrop == null) return;
                if (backdrop.isLayoutRequested() || !panes[mainSlot].container.isLaidOut()) {
                    backdrop.postOnAnimation(this);
                    return;
                }
                Runnable start = () -> handler.post(() -> {
                    if (state == State.OPENING && generation == request && !suspended
                            && !rebinding && hostVisible && backdrop != null)
                        animateWorkspace(true, () -> finishEntryAnimation(request));
                });
                if (backdrop.isHardwareAccelerated()) {
                    backdrop.getViewTreeObserver().registerFrameCommitCallback(start);
                    backdrop.invalidate();
                } else backdrop.postOnAnimation(start);
            }
        });
    }

    private void finishEntryAnimation(int request) {
        if (state != State.OPENING || generation != request || backdrop == null) return;
        transitionHandoff = true;
        // The TaskView must have drawn its final transform before the real task leaves
        // the independent animation layer. Posting a single frame is not a draw fence.
        Runnable handoff = () -> handler.post(() -> {
            if (state != State.OPENING || generation != request) return;
            shell.finishWorkspaceEntry(panes[mainSlot].host, () -> {
                if (state != State.OPENING || generation != request) return;
                transitionHandoff = false;
                state = State.RUNNING;
                OneStepFlowLog.info("workspace_ready", request,
                        "task=" + (panes[mainSlot] == null || panes[mainSlot].card == null
                                ? -1 : panes[mainSlot].card.taskId));
                handler.removeCallbacks(openingTimeout);
                openingTask = null;
                Runnable callback = onSuccess;
                onSuccess = null;
                if (callback != null) callback.run();
                if (perf != null) perf.phase("steady");
                updateInput();
                positionForIme();
            });
        });
        if (backdrop.isHardwareAccelerated()) {
            backdrop.getViewTreeObserver().registerFrameCommitCallback(handoff);
            backdrop.invalidate();
        } else backdrop.postOnAnimation(handoff);
    }

    private void resizeWorkspace(Rect screen, Rect insets) {
        if (!active() || workspace == null) return;
        if (state == State.OPENING) {
            if (!screenBounds.equals(screen)
                    || !logicalBounds.equals(new Rect(screen.left + insets.left, screen.top + insets.top,
                            screen.right - insets.right, screen.bottom - insets.bottom))) close(false);
            return;
        }
        finishAppDrag(-1);
        if (animator != null) animator.end();
        if (layoutAnimator != null) layoutAnimator.end();
        if (transitionAnimator != null) transitionAnimator.end();
        cancelImeAnimator();
        Rect previousLogicalBounds = new Rect(logicalBounds);
        screenBounds.set(screen);
        updateLogicalBounds(screen, Insets.of(insets.left, insets.top, insets.right, insets.bottom));
        if (!previousLogicalBounds.equals(logicalBounds)) imeInputRegion = null;
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
        strip.height = toolbarHeightPx();
        strip.leftMargin = strip.rightMargin = dp(WORKSPACE_MARGIN_DP);
        strip.topMargin = Math.max(0, panesTop - toolbarTopGapPx());
        recentStrip.setLayoutParams(strip);
        updateToolbarControls();
        recentApps.setRecentRowPadding(dp(8));
        recentApps.markOrderDirty();
        recentApps.refresh();
        for (Pane pane : panes) if (pane != null) {
            position(pane, frames[pane.slot]);
            pane.container.setTranslationX(0);
            pane.container.setTranslationY(0);
            pane.updateAppearance();
            pane.updateGeometry();
        }
        positionForIme();
        refreshInputRegion();
    }

    private void open(List<RecentTaskCard> recent, boolean fromLeft, Rect screen, Insets insets) throws Exception {
        screenBounds.set(screen);
        updateLogicalBounds(screen, insets);
        contentBounds = new Rect(logicalBounds);
        width = contentBounds.width();
        height = contentBounds.height();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid workspace bounds");
        OneStepFlowLog.info("workspace_view_created", generation,
                "task=" + (openingTask == null ? -1 : openingTask.taskId)
                        + " recentCount=" + (recent == null ? 0 : recent.size())
                        + " side=" + (fromLeft ? "left" : "right"));
        mainSlot = 0;
        focusedSlot = 0;
        pendingOrientations.clear();
        requestedOrientations.clear();
        manualOrientations.clear();
        movingTask = false;
        orientationMoveAfter = 0;
        covered = false;
        hostVisible = true;
        recentApps.clearPins();
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
        imeInputRegion = null;
        imePlacement = OneStepImePolicy.UNKNOWN;
        sideOrder.clear();
        for (int i = 1; i < OneStepLandscapeWindow.SLOT; i++) sideOrder.add(i);
        sideWindowsExpanded = false;
        sideWindowsHidden = false;
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
        recentStrip = recentApps.create(recent, generation);
        int panesTop = frames[mainSlot].top;
        for (int slot : sideOrder) panesTop = Math.min(panesTop, frames[slot].top);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(-1, toolbarHeightPx());
        stripParams.leftMargin = dp(WORKSPACE_MARGIN_DP);
        stripParams.rightMargin = dp(WORKSPACE_MARGIN_DP);
        stripParams.topMargin = Math.max(0, panesTop - toolbarTopGapPx());
        workspace.addView(recentStrip, stripParams);
        for (int i = 0; i < COUNT; i++) {
            Pane pane = new Pane(i);
            panes[i] = pane;
            workspace.addView(pane.container);
            position(pane, frames[i]);
            pane.updateAppearance();
        }
        applyTransition(0f);
        activitySession.setView(backdrop);
        perf = new OneStepPerf(backdrop, handler, generation);
        touchMode.enable();
        OneStepStatusBar.setVisible(!suspended);
    }

    private Rect[] layout() {
        int margin = Math.min(dp(WORKSPACE_MARGIN_DP), Math.min(width, height) / 8);
        int shortSide = Math.min(logicalBounds.width(), logicalBounds.height());
        int longSide = Math.max(logicalBounds.width(), logicalBounds.height());
        boolean showLandscape = landscapeVisible();
        sideWindowsCollapsed = shouldCollapseSideWindows();
        int toolbarHeight = toolbarHeightDp();
        layoutToolbarHeightDp = toolbarHeight;
        int toolbarPosition = toolbarPositionDp();
        layoutToolbarPositionDp = toolbarPosition;
        ModuleConfig config = ModuleConfig.load(context);
        boolean keepMainAspectRatio = oneHandedProfile
                ? config.oneHandedWorkspaceKeepMainAspectRatio : config.workspaceKeepMainAspectRatio;
        layoutKeepMainAspectRatio = keepMainAspectRatio;
        Rect[] result = landscapeWindow.calculate(width - margin * 2,
                height - margin * 2, dp(PANE_GAP_DP),
                dp(toolbarHeight), shortSide, longSide,
                mainSlot, sideOrder, mainOnLeft, showLandscape, !sideWindowsCollapsed,
                dp(toolbarPosition), keepMainAspectRatio);
        // Keep the hosted portrait task's logical canvas tied to the display. The
        // lower main pane can be shorter while the landscape pane is visible, but
        // that is only a presentation scale. Feeding the pane's aspect ratio into
        // WM makes SurfaceGeometry apply different X/Y scales and squashes a newly
        // launched portrait app.
        if (portraitBounds.width() != shortSide || portraitBounds.height() != longSide)
            imeInputRegion = null;
        portraitBounds.set(logicalBounds.left, logicalBounds.top,
                logicalBounds.left + shortSide, logicalBounds.top + longSide);
        for (Rect frame : result) frame.offset(margin, margin);
        return result;
    }

    private void updateLogicalBounds(Rect screen, Insets insets) {
        // Keep the full usable display separate from the app viewports, so relayout
        // never feeds the shortened portrait bounds back into the layout calculation.
        logicalBounds.set(screen);
        logicalBounds.inset(insets.left, insets.top, insets.right, insets.bottom);
        if (logicalBounds.isEmpty()) throw new IllegalStateException("Invalid app viewport");
        int shortSide = Math.min(logicalBounds.width(), logicalBounds.height());
        int longSide = Math.max(logicalBounds.width(), logicalBounds.height());
        desktopBounds.set(logicalBounds.left, logicalBounds.top,
                logicalBounds.left + shortSide, logicalBounds.top + longSide);
        portraitBounds.set(logicalBounds.left, logicalBounds.top,
                logicalBounds.left + shortSide, logicalBounds.top + longSide);
        landscapeWindow.updateViewport(logicalBounds);
    }

    private float scaleForFrame(Rect frame) {
        return scaleForFrame(frame, mainSlot);
    }

    private float scaleForFrame(Rect frame, int slot) {
        Rect viewport = viewport(slot);
        return Math.min(1f, Math.min(frame.width() / (float) Math.max(1, viewport.width()),
                frame.height() / (float) Math.max(1, viewport.height())));
    }

    private Rect viewport(int slot) {
        return landscapeWindow.isSlot(slot) ? landscapeWindow.viewport() : portraitBounds;
    }

    private boolean isSideWindowHidden(int slot) {
        return sideWindowsCollapsed && slot >= 0 && !landscapeWindow.isSlot(slot) && slot != mainSlot;
    }

    private boolean shouldCollapseSideWindows() {
        if (dragSession != null) return false;
        if (sideWindowsHidden) return true;
        if (sideWindowsExpanded) return false;
        for (int slot : sideOrder) {
            Pane pane = panes[slot];
            // Pending loads count as occupied until they succeed or fail.
            if (hasPaneContent(pane)) return false;
        }
        return true;
    }

    private void position(Pane pane, Rect frame) {
        View view = pane.container;
        Rect viewport = viewport(pane.slot);
        // Keep the task surface at its logical aspect ratio. When the display and
        // app orientations differ, the fitted surface can be narrower than the
        // pane; place that surface in the pane center instead of anchoring it left.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(viewport.width(), viewport.height());
        float scale = scaleForFrame(frame, pane.slot);
        int fittedWidth = Math.max(1, Math.round(viewport.width() * scale));
        int fittedHeight = Math.max(1, Math.round(viewport.height() * scale));
        params.leftMargin = frame.left + Math.max(0, (frame.width() - fittedWidth) / 2);
        params.topMargin = frame.top + Math.max(0, (frame.height() - fittedHeight) / 2);
        view.setLayoutParams(params);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setVisibility(isPaneVisible(pane) ? View.VISIBLE : View.INVISIBLE);
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
            if (!shellImeControlled || !shellImePositioning) {
                // Routing may have handed the IME back to an app before Shell's
                // stale end callback arrives. Do not leave imeAnimating latched,
                // because updateInput treats it as a global touch block.
                shellImePositioning = false;
                if (!shellImeControlled) {
                    imeAnimating = false;
                    applyActivityIme();
                }
                workspace.post(this::updateInput);
                return;
            }
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
            imeAnimating = false;
            applyActivityIme();
            logImeEvent("shell-control-lost");
            workspace.post(this::updateInput);
        });
    }

    @Override public void onImeRoutingChanged(IBinder task, int placement, RectF inputRegion) {
        if (!active()) return;
        boolean routingChanged = !java.util.Objects.equals(imeInputTask, task) || imePlacement != placement;
        boolean regionAvailabilityChanged = (imeInputRegion == null) != (inputRegion == null);
        imeInputTask = task;
        imePlacement = placement;
        imeInputRegion = inputRegion == null ? null : new RectF(inputRegion);
        if (placement == OneStepImePolicy.APP) {
            // Insets describe the logical display even when the keyboard is inside a pane.
            // Only a confirmed app parent revokes Shell positioning. Unknown routing during
            // a focus handoff must not cancel a newer Shell animation already in flight.
            shellImeControlled = false;
            shellImePositioning = false;
            imeAnimating = false;
        }
        if (!shellImeControlled) applyActivityIme();
        // The requested input region can move without changing the IME insets.
        positionForIme();
        if (placement == OneStepImePolicy.APP) workspace.post(this::updateInput);
        if (routingChanged) logImeEvent("routing placement=" + placement);
        else if (regionAvailabilityChanged) logImeEvent(inputRegion == null ? "input-region-cleared" : "input-region-ready");
    }

    private Pane imeTargetPane() {
        for (Pane pane : panes) if (directlyInteractive(pane) && pane.host != null && pane.host.card != null
                && imeInputTask != null && imeInputTask.equals(pane.host.card.token)) return pane;
        return null;
    }

    private void logImeEvent(String event) {
        try {
            Pane main = panes[mainSlot];
            RecentTaskCard card = main != null ? main.card : null;
            OneStepFlowLog.i(OneStepImeDiagnostics.TAG, "workspace session=" + generation + " event=" + event
                    + " mainTask=" + (card != null ? card.taskId : -1)
                    + " source=" + (shellImeControlled ? "shell" : "activity")
                    + " activityBottom=" + activityImeBottom + " activityAnimating=" + activityImeAnimating
                    + " imeBottom=" + imeBottom + " imeAnimating=" + imeAnimating
                    + " shellTargetBottom=" + shellImeTargetBottom + " shellFloating=" + shellImeFloating
                    + " avoidOffset=" + imeOffset
                    + " avoidance=" + (imeInputRegion == null ? "fallback" : "input-region")
                    + " inputRegion=" + imeInputRegion
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
        boolean displayIme = imePlacement == OneStepImePolicy.DISPLAY && imeTargetPane() != null;
        int bottom = displayIme ? activityImeBottom : 0;
        boolean animating = displayIme && activityImeAnimating;
        if (imeBottom == bottom && imeAnimating == animating) return;
        imeBottom = bottom;
        imeAnimating = animating;
        positionForIme();
    }

    private void positionForIme() {
        if (!running() || workspace == null || animator != null || layoutAnimator != null || transitionAnimator != null
                || transitionHandoff || dragSession != null) return;
        int nextOffset = offsetForIme();
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

    private int offsetForIme() {
        Pane target = imeTargetPane();
        if (imeBottom <= 0 || imePlacement == OneStepImePolicy.APP || target == null) return 0;
        Rect frame = frames[target.slot];
        float paneTop = contentBounds.top + frame.top;
        float paneHeight = viewport(target.slot).height() * scaleForFrame(frame, target.slot);
        float keyboardTop = screenBounds.bottom - imeBottom;
        float gap = dp(12);
        // The recent-app strip pins to the top when lifted; reserve its height so
        // it cannot cover the caret even with an unusually tall keyboard.
        float visibleTop = contentBounds.top + (landscapeWindow.isSlot(target.slot) ? 0 : toolbarTopGapPx());
        float targetBottom = paneTop + paneHeight;
        float targetTop = paneTop;
        if (imeInputRegion != null) {
            targetTop = paneTop + imeInputRegion.top * paneHeight;
            targetBottom = paneTop + imeInputRegion.bottom * paneHeight + gap;
        }
        float needed = Math.max(0f, targetBottom - keyboardTop);
        float maximum = Math.max(0f, targetTop - visibleTop - gap);
        // Without an input region, keep the main pane's top reachable. Otherwise
        // lift only enough to expose the requested region above the keyboard.
        return Math.max(0, (int) Math.min(Math.ceil(needed), Math.floor(maximum)));
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
        for (Pane pane : panes) if (pane != null) {
            RectF start = transitionFrame(pane.slot, false);
            RectF end = transitionFrame(pane.slot, true);
            setTransitionFrame(pane, start, end, progress);
        }
        updateTransitionAppearance();
    }

    private RectF transitionFrame(int slot, boolean opened) {
        if (!opened && slot == mainSlot) {
            // Task bounds include system bars. The animated pane contains only the
            // stable content viewport, which must return to the same screen region.
            RectF fullscreen = new RectF(OneStepWorkspaceTransition.contentBounds(transitionBounds, logicalBounds));
            fullscreen.offset(-contentBounds.left, -contentBounds.top);
            return fullscreen;
        }
        Rect frame = frames[slot];
        Rect viewport = viewport(slot);
        float scale = scaleForFrame(frame, slot);
        float w = viewport.width() * scale;
        float h = viewport.height() * scale;
        float x = frame.left + Math.max(0f, (frame.width() - w) / 2f);
        float y = frame.top + Math.max(0f, (frame.height() - h) / 2f);
        if (!opened) {
            x += (mainOnLeft ? -dp(24) : dp(24)) + w * 0.03f;
            y += dp(12) + h * 0.03f;
            w *= 0.94f;
            h *= 0.94f;
        }
        return new RectF(x, y, x + w, y + h);
    }

    private RectF currentPaneFrame(Pane pane) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pane.container.getLayoutParams();
        // LayoutParams also describe a frame staged before the next layout traversal.
        float x = params.leftMargin + pane.container.getTranslationX();
        float y = params.topMargin + pane.container.getTranslationY();
        return new RectF(x, y, x + params.width * pane.container.getScaleX(),
                y + params.height * pane.container.getScaleY());
    }

    private RectF mainScreenFrame() {
        RectF frame = currentPaneFrame(panes[mainSlot]);
        frame.offset(contentBounds.left, contentBounds.top + workspace.getTranslationY());
        return frame;
    }

    private void setTransitionFrame(Pane pane, RectF start, RectF end, float progress) {
        float x = start.left + (end.left - start.left) * progress;
        float y = start.top + (end.top - start.top) * progress;
        float w = start.width() + (end.width() - start.width()) * progress;
        float h = start.height() + (end.height() - start.height()) * progress;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pane.container.getLayoutParams();
        pane.container.setTranslationX(x - params.leftMargin);
        pane.container.setTranslationY(y - params.topMargin);
        pane.container.setScaleX(w / viewport(pane.slot).width());
        pane.container.setScaleY(h / viewport(pane.slot).height());
    }

    private static float segment(float progress, float start, float end) {
        return Math.max(0f, Math.min(1f, (progress - start) / (end - start)));
    }

    private void updateTransitionAppearance() {
        float toolbar = segment(transitionProgress, 0.2f, 1f);
        if (recentStrip != null) {
            recentStrip.setAlpha(toolbar);
            int top = ((FrameLayout.LayoutParams) recentStrip.getLayoutParams()).topMargin;
            recentStrip.setTranslationY(Math.max(0f, -workspace.getTranslationY() - top) - dp(8) * (1f - toolbar));
        }
        backdrop.setBackgroundColor(Math.round(20 * transitionProgress) << 24);
        for (Pane pane : panes) if (pane != null) {
            int order = sideOrder.indexOf(pane.slot);
            pane.appearanceProgress = pane.slot == mainSlot ? transitionProgress
                    : segment(transitionProgress, 0.08f + Math.max(0, order) * 0.06f, 1f);
            pane.updateAppearance();
            if (pane.host != null && Build.VERSION.SDK_INT >= 34)
                pane.host.view.setAlpha(pane.slot == mainSlot ? 1f : pane.appearanceProgress);
        }
        Pane main = panes[mainSlot];
        if (main != null && main.host != null)
            shell.workspaceFrame(main.host, mainScreenFrame(), dp(PANE_RADIUS_DP) * transitionProgress, transitionProgress);
    }

    private void animateWorkspace(boolean entering, Runnable done) {
        cancelAnimator(false);
        blockInput(true);
        transitionHandoff = false;
        float target = entering ? 1f : 0f;
        float startProgress = transitionProgress;
        float startOffset = workspace.getTranslationY();
        RectF[] start = new RectF[COUNT], end = new RectF[COUNT];
        for (Pane pane : panes) if (pane != null) {
            start[pane.slot] = currentPaneFrame(pane);
            end[pane.slot] = transitionFrame(pane.slot, entering);
        }
        java.util.function.Consumer<Float> frame = fraction -> {
            transitionProgress = startProgress + (target - startProgress) * fraction;
            workspace.setTranslationY(entering ? startOffset : startOffset * (1f - fraction));
            for (Pane pane : panes) if (pane != null)
                setTransitionFrame(pane, start[pane.slot], end[pane.slot], fraction);
            updateTransitionAppearance();
        };
        if (!ValueAnimator.areAnimatorsEnabled()) {
            frame.accept(1f);
            if (done != null) done.run();
            else { updateInput(); positionForIme(); }
            return;
        }
        ValueAnimator transition = ValueAnimator.ofFloat(0f, 1f);
        transitionAnimator = transition;
        transition.setDuration(Math.max(1L, Math.round((entering ? 320 : 260) * Math.abs(target - startProgress))));
        transition.setInterpolator(EASING);
        transition.addUpdateListener(value -> frame.accept((float) value.getAnimatedValue()));
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
        if (isSideWindowHidden(slot)) return;
        if (landscapeWindow.directlyInteractive(slot, mainSlot)) {
            if (canChangeTask() && panes[slot] != null && panes[slot].host != null
                    && panes[slot].host.ready) focusPane(panes[slot]);
            return;
        }
        if (!running() || movingTask || suspended || rebinding || covered || panesBusy() || animator != null || layoutAnimator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null || desktopBusy) return;
        Pane selected = panes[slot];
        if (selected.host == null && !selected.replacing) { enterDesktop(slot, true); return; }
        if (selected.host == null || !selected.host.ready) return;
        clearPaneTapHighlight();
        haptic(HapticFeedbackConstants.CLOCK_TICK);
        Pane previous = panes[mainSlot];
        boolean leavingDesktop = previous.card != null && previous.card.home;
        OneStepFlowLog.info("app_selected", generation,
                "fromTask=" + (previous.card == null ? -1 : previous.card.taskId)
                        + " toTask=" + (selected.card == null ? -1 : selected.card.taskId)
                        + " slot=" + slot);
        if (leavingDesktop) {
            desktopBusy = true;
            if (desktopSession != null) desktopSession.enable(false);
        }
        swapTo(slot, () -> {
            if (leavingDesktop) {
                hideDesktop(previous);
                desktopBusy = false;
                updateInput();
            }
        });
    }

    private void swapTo(int slot, Runnable after) {
        if (slot == mainSlot) { after.run(); return; }
        Pane selected = panes[slot];
        int previousMain = mainSlot;
        int index = sideOrder.indexOf(slot);
        if (index < 0) return;
        // This is an explicit placement. A queued orientation snapshot from the
        // side pane must not immediately route it back to the landscape role after
        // the swap has committed.
        holdManualPlacement(selected);
        blockInput(true);
        sideOrder.set(index, previousMain);
        mainSlot = slot;
        Rect[] start = frames;
        Rect previousViewport = new Rect(portraitBounds);
        Rect[] end = layout();
        if (perf != null) perf.phase("swap");
        Runnable finish = () -> {
            relayoutPanes();
            focusPane(selected);
            if (perf != null) perf.phase("steady");
            after.run();
        };
        // Let the layout animation handle viewport changes as well as pane movement.
        if (!previousViewport.equals(portraitBounds) || !ValueAnimator.areAnimatorsEnabled()) {
            portraitBounds.set(previousViewport);
            finish.run();
            return;
        }
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
        if (landscapeWindow.isSlot(slot)) return;
        if (!running() || movingTask || desktopBusy || animator != null || layoutAnimator != null || transitionAnimator != null
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
        if (!reusableDesktop()) target.empty.setText("正在打开桌面…");
        swapTo(slot, () -> {
            hideDesktop(previous);
            if (reusableDesktop()) {
                try {
                    target.host = desktopHost;
                    target.card = desktopHost.card;
                    target.mount(desktopHost);
                    target.empty.setVisibility(View.GONE);
                    target.updateAppearance();
                    target.updateGeometry();
                    shell.parkDesktop(desktopHost, false);
                    focusLoadedPane(target);
                    desktopBusy = false;
                    updateInput();
                } catch (Exception error) { fail("无法恢复桌面窗口", error); }
            } else {
                // Only a lost HOME task needs a new connection and TaskView.
                OneStepShell.Host stale = desktopHost;
                disconnectDesktop();
                if (stale == null) connectDesktop(target);
                else {
                    int request = generation;
                    shell.release(stale, () -> {
                        if (!running() || generation != request) return;
                        removeHostView(stale);
                        if (desktopHost == stale) desktopHost = null;
                        connectDesktop(target);
                    });
                }
            }
        });
    }

    private boolean reusableDesktop() {
        return desktopSession != null && desktopHost != null && desktopHost.ready
                && !desktopHost.closing && !desktopHost.released;
    }

    private void connectDesktop(Pane pane) {
        int request = generation;
        int connection = ++desktopRequest;
        taskWorker.execute(() -> {
            try {
                RecentTaskCard home = tasks.homeTask(workspaceUserId);
                handler.post(() -> {
                    if (!running() || generation != request || desktopRequest != connection || pane != panes[mainSlot]) return;
                    try {
                        desktopSession = new OneStepLauncherBridge.Session(context, handler, workspaceUserId,
                                new OneStepLauncherBridge.Listener() {
                            @Override public void onConnected() {
                                if (running() && generation == request && desktopRequest == connection
                                        && pane == panes[mainSlot]) pane.load(home);
                            }
                            @Override public void onLaunch(PendingIntent intent, IBinder animation) {
                                Pane main = panes[mainSlot];
                                if (running() && generation == request && desktopRequest == connection
                                        && main != null && main.host == desktopHost && desktopHost != null)
                                    launchFromDesktop(main, intent, animation);
                                else OneStepLaunchAnimation.cancelRemote(animation);
                            }
                            @Override public void onUnavailable() {
                                if (running() && generation == request && desktopRequest == connection) desktopUnavailable(pane);
                            }
                        });
                    } catch (Exception error) {
                        OneStepFlowLog.w(TAG, "Cannot connect launcher", error);
                        desktopUnavailable(pane);
                    }
                });
            } catch (Exception error) {
                OneStepFlowLog.w(TAG, "Cannot find HOME task", error);
                handler.post(() -> {
                    if (running() && generation == request && desktopRequest == connection) desktopUnavailable(pane);
                });
            }
        });
    }

    private void desktopUnavailable(Pane pane) {
        disconnectDesktop();
        desktopBusy = false;
        pane.showEmpty();
        Toast.makeText(context, "暂时无法打开桌面，请稍后重试", Toast.LENGTH_SHORT).show();
        pane.focusAfterLoad = null;
        focusActivePane();
        updateInput();
    }

    private void disconnectDesktop() {
        desktopRequest++;
        if (desktopSession != null) { desktopSession.close(); desktopSession = null; }
    }

    private void hideDesktop(Pane pane) {
        if (pane == null || pane.host == null || !pane.host.home()) return;
        parkDesktop(pane.host);
        pane.host = null;
        pane.card = null;
        pane.showEmpty();
    }

    private void parkDesktop(OneStepShell.Host host) {
        if (desktopSession != null) { desktopSession.enable(false); desktopSession.retry(); }
        shell.parkDesktop(host, true);
        // Keep the SurfaceView attached, including on Android 13 where INVISIBLE
        // would destroy its surface. Offscreen placement also closes its input hole.
        if (desktopContainer != null) {
            desktopContainer.setAlpha(0f);
            desktopContainer.setTranslationX(width + logicalBounds.width());
            desktopContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        try { host.obscure(new Rect(screenBounds)); }
        catch (Exception error) { fail("无法隐藏桌面触摸区域", error); }
    }

    private void positionDesktop(Pane pane) {
        if (desktopContainer == null || pane.host != desktopHost || desktopHost == null) return;
        updateDesktopTransform(pane);
        float scale = Math.max(0.01f, desktopContainer.getScaleX());
        boolean highlighted = highlightedSlot == pane.slot || tappedSlot == pane.slot;
        desktopBorder.setCornerRadius(pane.cornerRadius);
        desktopBorder.setColor(highlighted ? 0x225aaaff : Color.TRANSPARENT);
        desktopBorder.setStroke(Math.max(1, Math.round(dp(highlighted ? 2 : 0.7f) / scale)),
                highlighted ? 0xff9acbff : 0x40ffffff);
        desktopBorder.setAlpha(Math.round(255 * pane.appearanceProgress));
    }

    private void updateDesktopTransform(Pane pane) {
        if (desktopContainer == null || pane.host != desktopHost || desktopHost == null) return;
        int desktopWidth = Math.max(1, desktopBounds.width());
        int desktopHeight = Math.max(1, desktopBounds.height());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) desktopContainer.getLayoutParams();
        if (params.width != desktopWidth || params.height != desktopHeight) {
            desktopContainer.setLayoutParams(new FrameLayout.LayoutParams(desktopWidth, desktopHeight));
        }
        FrameLayout.LayoutParams paneParams = (FrameLayout.LayoutParams) pane.container.getLayoutParams();
        float baseWidth = pane.container.getWidth() > 0 ? pane.container.getWidth() : paneParams.width;
        float baseHeight = pane.container.getHeight() > 0 ? pane.container.getHeight() : paneParams.height;
        float paneWidth = baseWidth * Math.abs(pane.container.getScaleX());
        float paneHeight = baseHeight * Math.abs(pane.container.getScaleY());
        float scale = Math.min(1f, Math.min(paneWidth / desktopWidth, paneHeight / desktopHeight));
        desktopContainer.setTranslationX(paneParams.leftMargin + pane.container.getTranslationX()
                + (paneWidth - desktopWidth * scale) / 2f);
        desktopContainer.setTranslationY(paneParams.topMargin + pane.container.getTranslationY()
                + (paneHeight - desktopHeight * scale) / 2f);
        desktopContainer.setScaleX(scale);
        desktopContainer.setScaleY(scale);
        desktopContainer.setElevation(pane.container.getElevation());
        desktopContainer.setAlpha(desktopHost.ready ? 1f : 0f);
    }

    private void removeHostView(OneStepShell.Host host) {
        pendingOrientations.remove(host);
        requestedOrientations.remove(host);
        manualOrientations.remove(host);
        if (host.view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) host.view.getParent()).removeView(host.view);
        if (desktopContainer != null && desktopContainer.getChildCount() == 0) desktopContainer.setAlpha(0f);
    }

    private void launchFromDesktop(Pane pane, PendingIntent intent, IBinder animation) {
        if (desktopBusy || animator != null || layoutAnimator != null || transitionAnimator != null || imeAnimator != null || imeAnimating
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
            pane.configureHost(pane.launchHost);
        } catch (Exception error) {
            OneStepFlowLog.w(TAG, "Cannot prepare launcher application", error);
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
        if (running() && !movingTask && !desktopBusy && animator == null && transitionAnimator == null
                && sideWindowsCollapsed != shouldCollapseSideWindows()) {
            // Task removal, failed loads and pane swaps all converge here. Reclaim
            // empty side space only after the pending move has settled.
            relayoutPanes();
        }
        if (!shellImeControlled) applyActivityIme();
        boolean blocked = !running() || movingTask || transitionHandoff || suspended || rebinding || covered || !hostVisible || animator != null || layoutAnimator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null
                || (activitySession != null && !activitySession.isMounted());
        blockInput(blocked);
        boolean controlsBlocked = blocked || panesBusy() || desktopBusy;
        recentApps.setSideWindowsEnabled(!controlsBlocked, sideWindowsCollapsed);
        Pane main = panes[mainSlot];
        if (desktopSession != null) desktopSession.enable(!controlsBlocked && !OneHandedTaskHooks.shadeOpen()
                && main != null && main.host != null && main.host.ready && main.host.home());
    }

    private void refreshInputRegion() {
        inputRegionRefreshPending = true;
        if (workspace == null) return;
        int request = generation;
        workspace.postOnAnimation(() -> {
            if (request != generation || !active()) return;
            updateInput();
            // TaskView is a SurfaceView. Its child surface and internal-insets
            // listener can become ready one traversal after the pane itself has
            // been laid out, especially after a launcher handoff or orientation
            // move. Publish the same region once more after that traversal.
            workspace.postOnAnimation(() -> {
                if (request == generation && active()) {
                    inputRegionRefreshPending = true;
                    updateInput();
                }
            });
        });
    }

    private void blockInput(boolean block) {
        if (backdrop == null) return;
        // TaskView's native insets listener uses its unscaled width/height. Cover everything
        // except the interactive panes. All TaskViews publish into the same host window,
        // so they must receive one identical touch region.
        try {
            boolean changed = inputRegionRefreshPending;
            Region obscured = obscuredTouchRegion(block);
            for (Pane pane : panes) if (pane != null && pane.host != null) {
                java.lang.reflect.Field field = OneStepReflection.field(pane.host.view.getClass(), "mObscuredTouchRegion");
                Object previous = field.get(pane.host.view);
                if (inputRegionRefreshPending || !obscured.equals(previous)) {
                    field.set(pane.host.view, new android.graphics.Region(obscured));
                    changed = true;
                    // The field is private on this ROM and changing it directly does
                    // not invalidate ViewRoot's internal-insets result. A layout pass
                    // is required after a pane move so TaskView publishes the new hole.
                    pane.host.view.requestLayout();
                }
                if (pane.launchHost != null) pane.launchHost.obscure(new Rect(screenBounds));
            }
            if (changed) {
                // The region is consumed by TaskView's internal-insets listener.
                // Invalidate alone can leave the ViewRoot traversal behind a
                // SurfaceView replacement, so explicitly request one traversal.
                backdrop.requestLayout();
                backdrop.invalidate();
            }
            inputRegionRefreshPending = false;
        } catch (Exception error) {
            if (active()) handler.post(() -> fail("无法设置应用触摸区域", error));
            else OneStepFlowLog.w(TAG, "Cannot close task input regions", error);
        }
    }

    private Region obscuredTouchRegion(boolean block) {
        Region obscured = new Region();
        int[] rootWindow = new int[2];
        backdrop.getLocationInWindow(rootWindow);
        obscured.set(rootWindow[0], rootWindow[1],
                rootWindow[0] + backdrop.getWidth(), rootWindow[1] + backdrop.getHeight());
        if (!block) {
            for (Pane pane : panes) if (directlyInteractive(pane)
                    && pane.host != null && pane.host.ready && !pane.replacing
                    && pane.launchHost == null && !pane.host.closing
                    && !pane.host.restoring && !pane.host.released
                    && !(desktopBusy && pane.host.home())) {
                // SurfaceView can report a stale or empty visible rectangle for
                // one frame while its parent is being resized or replaced. The
                // pane frame is the authoritative input geometry here; relying on
                // getGlobalVisibleRect() can leave the whole host window obscured
                // just after a new app is moved to a side/landscape pane.
                if (pane.container.getVisibility() != View.VISIBLE || pane.container.getAlpha() <= 0f) continue;
                Rect paneWindow = paneWindowRect(pane);
                if (paneWindow.isEmpty()) continue;
                RectF bounds = new RectF(toWindowRegion(paneWindow));
                Path shape = new Path();
                shape.addRoundRect(bounds, dp(PANE_RADIUS_DP), dp(PANE_RADIUS_DP), Path.Direction.CW);
                Region touchable = new Region();
                touchable.setPath(shape, new Region(toWindowRegion(paneWindow)));
                obscured.op(touchable, Region.Op.DIFFERENCE);
            }
        }
        addToolbarObscuredRegion(obscured);
        return obscured;
    }

    private Rect paneWindowRect(Pane pane) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pane.container.getLayoutParams();
        float left = params.leftMargin + pane.container.getTranslationX();
        float top = params.topMargin + pane.container.getTranslationY();
        float right = left + params.width * Math.abs(pane.container.getScaleX());
        float bottom = top + params.height * Math.abs(pane.container.getScaleY());
        int[] backdropOnScreen = new int[2];
        backdrop.getLocationOnScreen(backdropOnScreen);
        float workspaceLeft = workspace.getLeft() + workspace.getTranslationX();
        float workspaceTop = workspace.getTop() + workspace.getTranslationY();
        Rect expected = new Rect(Math.round(backdropOnScreen[0] + workspaceLeft + left),
                Math.round(backdropOnScreen[1] + workspaceTop + top),
                Math.round(backdropOnScreen[0] + workspaceLeft + right),
                Math.round(backdropOnScreen[1] + workspaceTop + bottom));
        // Keep the hole inside the rectangle that is actually visible in the host
        // window. This also handles IME translation and display cutout clipping.
        Rect rootVisible = new Rect();
        if (backdrop.getGlobalVisibleRect(rootVisible)) expected.intersect(rootVisible);
        return expected;
    }

    private void addToolbarObscuredRegion(Region region) {
        if (recentStrip == null) return;
        Rect toolbar = new Rect();
        if (recentStrip.getGlobalVisibleRect(toolbar))
            region.op(toWindowRegion(toolbar), Region.Op.UNION);
    }

    private int windowOffsetX() {
        int[] screen = new int[2];
        int[] window = new int[2];
        backdrop.getLocationOnScreen(screen);
        backdrop.getLocationInWindow(window);
        return window[0] - screen[0];
    }

    private int windowOffsetY() {
        int[] screen = new int[2];
        int[] window = new int[2];
        backdrop.getLocationOnScreen(screen);
        backdrop.getLocationInWindow(window);
        return window[1] - screen[1];
    }

    private Rect toWindowRegion(Rect screenRect) {
        Rect result = new Rect(screenRect);
        result.offset(windowOffsetX(), windowOffsetY());
        return result;
    }

    private void toggleSideWindows() {
        if (!active() || !canChangeTask()) return;
        if (sideWindowsCollapsed) {
            sideWindowsHidden = false;
            sideWindowsExpanded = true;
        } else {
            sideWindowsHidden = true;
            sideWindowsExpanded = false;
        }
        relayoutPanes();
        updateInput();
    }

    private void pinRecent(RecentTaskCard card) {
        recentApps.pin(card);
    }

    private void rememberRecent(RecentTaskCard card) {
        recentApps.remember(card);
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
        return running() && !movingTask && !suspended && !rebinding && !covered && hostVisible && !panesBusy() && !desktopBusy && animator == null && layoutAnimator == null
                && transitionAnimator == null && imeAnimator == null && !imeAnimating
                && dragSession == null && !OneHandedTaskHooks.shadeOpen();
    }

    private void updateRecentIcons() { recentApps.refresh(); }

    private boolean landscapeVisible() {
        Pane pane = panes[OneStepLandscapeWindow.SLOT];
        return landscapeWindow.visible(dragSession != null, hasPaneContent(pane));
    }

    private boolean hasPaneContent(Pane pane) {
        return pane != null && (pane.host != null || pane.launchHost != null || pane.replacing);
    }

    private boolean isPaneVisible(Pane pane) {
        return pane != null && !isSideWindowHidden(pane.slot)
                && (!landscapeWindow.isSlot(pane.slot) || landscapeVisible());
    }

    private void relayoutPanes() {
        if (workspace == null || recentStrip == null || !active()) return;
        // A pane swap applies any pending layout when its own animation finishes.
        if (animator != null) return;
        RectF[] start = new RectF[COUNT], end = new RectF[COUNT];
        float[] startAlpha = new float[COUNT], endAlpha = new float[COUNT];
        for (Pane pane : panes) if (pane != null) {
            start[pane.slot] = currentPaneFrame(pane);
            startAlpha[pane.slot] = pane.container.getVisibility() == View.VISIBLE
                    ? pane.container.getAlpha() : 0f;
        }
        Rect previousViewport = new Rect(portraitBounds);
        Rect[] nextFrames = layout();
        // Task callbacks during a drag must not repeatedly restart the same motion.
        if (layoutAnimator != null && java.util.Arrays.equals(frames, nextFrames)
                && previousViewport.equals(portraitBounds)) return;
        cancelLayoutAnimator();
        frames = nextFrames;
        int top = frames[mainSlot].top;
        for (int slot : sideOrder) top = Math.min(top, frames[slot].top);
        FrameLayout.LayoutParams strip = (FrameLayout.LayoutParams) recentStrip.getLayoutParams();
        float startStripY = strip.topMargin + recentStrip.getTranslationY();
        strip.height = toolbarHeightPx();
        strip.topMargin = Math.max(0, top - toolbarTopGapPx());
        updateToolbarControls();
        float endStripY = Math.max(strip.topMargin, -workspace.getTranslationY());
        recentStrip.setLayoutParams(strip);
        boolean changed = Math.abs(startStripY - endStripY) > 0.5f;
        for (Pane pane : panes) if (pane != null) {
            int slot = pane.slot;
            boolean visible = isPaneVisible(pane);
            endAlpha[slot] = visible ? 1f : 0f;
            if (visible) {
                end[slot] = transitionFrame(slot, true);
                if (startAlpha[slot] == 0f) start[slot] = hiddenLayoutFrame(end[slot], slot);
            } else {
                end[slot] = hiddenLayoutFrame(start[slot], slot);
            }
            if (startAlpha[slot] > 0f || visible) {
                RectF from = start[slot], to = end[slot];
                changed |= Math.abs(from.left - to.left) > 0.5f || Math.abs(from.top - to.top) > 0.5f
                        || Math.abs(from.right - to.right) > 0.5f || Math.abs(from.bottom - to.bottom) > 0.5f
                        || startAlpha[slot] != endAlpha[slot];
            }
        }
        boolean animate = changed && running() && hostVisible && !suspended && !covered && !rebinding
                && transitionAnimator == null && !transitionHandoff && ValueAnimator.areAnimatorsEnabled();
        ValueAnimator movement = animate ? ValueAnimator.ofFloat(0f, 1f) : null;
        layoutAnimator = movement;
        if (animate) {
            cancelImeAnimator();
            blockInput(true);
        }
        for (Pane pane : panes) if (pane != null) {
            int slot = pane.slot;
            position(pane, frames[pane.slot]);
            pane.container.setTranslationX(0);
            pane.container.setTranslationY(0);
            pane.container.setAlpha(1f);
            if (animate && (startAlpha[slot] > 0f || endAlpha[slot] > 0f)) {
                pane.container.setVisibility(View.VISIBLE);
                setTransitionFrame(pane, start[slot], end[slot], 0f);
                pane.container.setAlpha(startAlpha[slot]);
            }
            pane.updateAppearance();
            // A change of orientation needs its final logical canvas immediately.
            // The animation only transforms that canvas; ordinary portrait pane
            // resizing can still wait until the motion settles.
            if (!animate || landscapeWindow.isSlot(slot)
                    || (pane.host != null && pane.host.landscapeRequested)) pane.updateGeometry();
        }
        if (!animate) {
            translateForIme(-workspace.getTranslationY());
            positionForIme();
            refreshInputRegion();
            return;
        }
        recentStrip.setTranslationY(startStripY - strip.topMargin);
        movement.setDuration(sideWindowsCollapsed ? 260 : 320);
        movement.setInterpolator(EASING);
        movement.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            recentStrip.setTranslationY(startStripY + (endStripY - startStripY) * progress - strip.topMargin);
            for (Pane pane : panes) if (pane != null) {
                int slot = pane.slot;
                if (startAlpha[slot] == 0f && endAlpha[slot] == 0f) continue;
                setTransitionFrame(pane, start[slot], end[slot], progress);
                pane.container.setAlpha(startAlpha[slot] + (endAlpha[slot] - startAlpha[slot]) * progress);
                // Keep the desktop surface moving with its pane without rebuilding
                // outlines or issuing SurfaceView corner-radius calls on every frame.
                updateDesktopTransform(pane);
            }
            if (dragSession != null && backdrop != null)
                highlightDropTarget(dropSlot(backdrop.touchX, backdrop.touchY));
        });
        movement.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (layoutAnimator != animation) return;
                layoutAnimator = null;
                for (Pane pane : panes) if (pane != null) {
                    position(pane, frames[pane.slot]);
                    pane.container.setTranslationX(0);
                    pane.container.setTranslationY(0);
                    pane.container.setAlpha(1f);
                    pane.updateAppearance();
                    pane.updateGeometry();
                }
                translateForIme(-workspace.getTranslationY());
                positionForIme();
                refreshInputRegion();
            }
        });
        movement.start();
    }

    private RectF hiddenLayoutFrame(RectF frame, int slot) {
        RectF hidden = new RectF(frame);
        hidden.inset(frame.width() * 0.03f, frame.height() * 0.03f);
        hidden.offset(landscapeWindow.isSlot(slot) ? 0 : dp(mainOnLeft ? 16 : -16),
                landscapeWindow.isSlot(slot) ? -dp(12) : 0);
        return hidden;
    }

    private void cancelLayoutAnimator() {
        ValueAnimator current = layoutAnimator;
        layoutAnimator = null;
        if (current == null) return;
        current.removeAllListeners();
        current.removeAllUpdateListeners();
        current.cancel();
    }

    private Pane paneForHost(OneStepShell.Host host) {
        if (host == null) return null;
        for (Pane pane : panes) if (pane != null && (pane.host == host || pane.launchHost == host)) return pane;
        return null;
    }

    private boolean directlyInteractive(Pane pane) {
        return pane != null && landscapeWindow.directlyInteractive(pane.slot, mainSlot);
    }

    private boolean readyForFocus(Pane pane) {
        return directlyInteractive(pane) && pane.host != null && pane.host.ready
                && !pane.host.closing && !pane.host.restoring && !pane.host.released;
    }

    private Pane activePane() {
        Pane pane = panes[focusedSlot];
        if (readyForFocus(pane)) return pane;
        Pane main = panes[mainSlot];
        if (readyForFocus(main)) return main;
        Pane landscape = panes[OneStepLandscapeWindow.SLOT];
        return readyForFocus(landscape) ? landscape : null;
    }

    private void focusPane(Pane pane) {
        if (!directlyInteractive(pane)) return;
        focusedSlot = pane.slot;
        shell.focus(pane.host);
        scheduleOrientationRouting();
    }

    private void focusActivePane() {
        Pane pane = activePane();
        if (pane == null) shell.focus(null);
        else focusPane(pane);
    }

    private void focusLoadedPane(Pane pane) {
        Pane retained = paneForHost(pane.focusAfterLoad);
        pane.focusAfterLoad = null;
        if (readyForFocus(retained)) focusPane(retained);
        else if (directlyInteractive(pane)) focusPane(pane);
        else focusActivePane();
    }

    @Override public void onFocused(OneStepShell.Host host) {
        Pane pane = paneForHost(host);
        if (!active() || !readyForFocus(pane) || pane.host != host) return;
        focusedSlot = pane.slot;
        positionForIme();
        refreshInputRegion();
        scheduleOrientationRouting();
    }

    @Override public void onOrientationRequested(OneStepShell.Host host, int orientation, boolean explicit) {
        if (!active() || paneForHost(host) == null || host.home()) return;
        refreshInputRegion();
        int direction = orientationDirection(orientation);
        requestedOrientations.put(host, direction);
        if (explicit) manualOrientations.remove(host);
        else if (manualOrientations.containsKey(host)) {
            Integer held = manualOrientations.get(host);
            // Repeated configuration snapshots must not undo manual placement. Actual
            // setRequestedOrientation calls above always return to automatic routing.
            if (direction == 0) return;
            if (held == null || held == 0) {
                manualOrientations.put(host, direction);
                return;
            }
            if (held == direction) return;
            manualOrientations.remove(host);
        }
        pendingOrientations.put(host, orientation);
        scheduleOrientationRouting();
    }

    private void scheduleOrientationRouting() {
        handler.removeCallbacks(routeOrientations);
        if (active() && !pendingOrientations.isEmpty()) handler.postDelayed(routeOrientations, 180);
    }

    private static int orientationDirection(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE: return 1;
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT: return -1;
            default: return 0;
        }
    }

    private void applyPendingOrientations() {
        if (!active() || pendingOrientations.isEmpty() || suspended || covered || !hostVisible) return;
        if (!canChangeTask() || movingTask || SystemClock.uptimeMillis() < orientationMoveAfter) {
            scheduleOrientationRouting();
            return;
        }
        for (OneStepShell.Host host : new ArrayList<>(pendingOrientations.keySet())) {
            Pane pane = paneForHost(host);
            if (pane == null || host.closing || host.released || host.restoring) { pendingOrientations.remove(host); continue; }
            if (!host.ready || pane.launchHost != null) { scheduleOrientationRouting(); continue; }
            int direction = orientationDirection(pendingOrientations.get(host));
            if (!directlyInteractive(pane) && direction != 0) continue;
            int destination = landscapeWindow.destinationForOrientation(pane.slot, mainSlot, direction);
            if (destination >= 0) {
                if (!moveTask(pane, panes[destination], true)) scheduleOrientationRouting();
                return;
            }
            pendingOrientations.remove(host);
        }
    }

    private boolean moveTask(Pane source, Pane target, boolean automatic) {
        if (!canChangeTask() || movingTask || source == target || source.host == null
                || !source.host.ready || source.host.closing || source.host.restoring || source.host.released
                || source.replacing || target.replacing || source.host.home()
                || (target.host != null && (!target.host.ready || target.host.closing
                        || target.host.restoring || target.host.released))) return false;
        movingTask = true;
        blockInput(true);
        OneStepShell.Host moving = source.host;
        OneStepShell.Host displaced = target.host;
        int request = generation;
        int sourceSlot = source.slot;
        int targetSlot = target.slot;
        ArrayList<RecentTaskCard> sourceHistory = new ArrayList<>(source.history);
        ArrayList<RecentTaskCard> targetHistory = new ArrayList<>(target.history);
        RecentTaskCard displacedCard = target.card;
        if (displacedCard != null && !displacedCard.home) {
            targetHistory.removeIf(card -> card.sameTask(displacedCard));
            targetHistory.add(0, displacedCard);
        }
        pinRecent(target.card);
        pendingOrientations.remove(moving);
        if (!automatic) holdManualPlacement(source);
        Runnable transfer = () -> {
            if (!active() || generation != request) return;
            if (displaced != null && !displaced.home()) removeHostView(displaced);
            if (target.host == displaced) { target.host = null; target.card = null; }
            target.replacing = false;
            target.history.clear();
            target.history.addAll(targetHistory);
            target.showEmpty();
            if (source.host != moving || moving.closing || moving.restoring || moving.released) {
                movingTask = false;
                relayoutPanes();
                focusActivePane();
                returnToPrevious(target, request);
                scheduleOrientationRouting();
                return;
            }
            // Move the owning Pane, keeping its TaskView attached and its task identity.
            // The displaced task is restored to recents instead of put in the wrong orientation.
            panes[targetSlot] = source;
            panes[sourceSlot] = target;
            source.slot = targetSlot;
            target.slot = sourceSlot;
            target.showEmpty();
            // History belongs to the window being restored, not to the moved TaskView.
            target.history.clear();
            target.history.addAll(sourceHistory);
            source.history.clear();
            source.history.addAll(targetHistory);
            source.history.removeIf(card -> card.sameTask(moving.card));
            source.focusAfterLoad = null;
            source.manualLandscapeLoad = false;
            target.manualLandscapeLoad = false;
            orientationMoveAfter = SystemClock.uptimeMillis() + 700;
            focusedSlot = targetSlot;
            movingTask = false;
            relayoutPanes();
            if (directlyInteractive(source)) focusPane(source); else focusPane(panes[mainSlot]);
            updateRecentIcons();
            if (!automatic) haptic(HapticFeedbackConstants.CONFIRM);
            if (directlyInteractive(target)) {
                target.focusAfterLoad = activePane() == null ? null : activePane().host;
                returnToPrevious(target, request);
            }
            scheduleOrientationRouting();
        };
        if (displaced != null && displaced.home()) {
            hideDesktop(target);
            transfer.run();
        } else if (displaced != null) {
            pendingOrientations.remove(displaced);
            requestedOrientations.remove(displaced);
            manualOrientations.remove(displaced);
            shell.release(displaced, transfer);
        } else transfer.run();
        return true;
    }

    private void showTask(RecentTaskCard card, int slot) {
        if (!canChangeTask() || slot < 0 || slot >= COUNT || panes[slot] == null
                || isSideWindowHidden(slot)) return;
        Pane source = hostedPane(card);
        Pane target = panes[slot];
        if (card.home && landscapeWindow.isSlot(slot)) return;
        if (source != null && source != target
                && (landscapeWindow.isSlot(source.slot) || landscapeWindow.isSlot(slot))) {
            if (source.card.component == null || card.component == null
                    || !source.card.component.getPackageName().equals(card.component.getPackageName())) {
                Toast.makeText(context, "这两个页面属于同一任务，请使用应用内返回", Toast.LENGTH_SHORT).show();
                return;
            }
            moveTask(source, target, false);
            return;
        }
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
            if (source == target) { focusPane(source); return; }
            if (source.host == null || !source.host.ready || (target.host != null && !target.host.ready)) return;
            holdManualPlacement(source);
            int oldSlot = source.slot;
            pinRecent(target.card);
            panes[slot] = source;
            panes[oldSlot] = target;
            source.slot = slot;
            target.slot = oldSlot;
            relayoutPanes();
            if (target.host != null && target.host.home() && target.slot != mainSlot) {
                hideDesktop(target);
            }
            focusPane(panes[mainSlot]);
            updateRecentIcons();
            updateInput();
            haptic(HapticFeedbackConstants.CONFIRM);
            return;
        }
        target.manualLandscapeLoad = landscapeWindow.isSlot(slot);
        target.focusAfterLoad = null;
        target.load(card);
        if (landscapeWindow.isSlot(slot)) relayoutPanes();
    }

    private void restorePage(Pane pane, RecentTaskCard card) {
        pane.replacing = true;
        updateInput();
        int request = generation;
        taskWorker.execute(() -> {
            if (!active() || generation != request) return;
            boolean restored = false;
            try { tasks.returnToPage(card); restored = true; }
            catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot return to source Activity", error); }
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
        relayoutPanes();
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
            dragPreview.setPadding(toolbarIconPaddingPx(), toolbarIconPaddingPx(),
                    toolbarIconPaddingPx(), toolbarIconPaddingPx());
            dragPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            backdrop.addView(dragPreview, new FrameLayout.LayoutParams(toolbarIconSizePx(),
                    toolbarIconSizePx()));
            updateRecentIcons();
            moveAppDrag(backdrop.touchX, backdrop.touchY);
            haptic(HapticFeedbackConstants.LONG_PRESS);
            return true;
        } catch (RuntimeException e) { OneStepFlowLog.w(TAG, "Cannot start app drag", e); }
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
            dragPreview.setTranslationX(x - toolbarIconSizePx() / 2f);
            dragPreview.setTranslationY(y - toolbarIconSizePx() / 2f);
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
        // Commit while the expanded drop targets are still valid, before collapsing
        // any empty panes. Relayout can start IME avoidance, which would otherwise
        // make canChangeTask reject the drop.
        if (targetSlot >= 0 && targetSlot < COUNT && running() && generation == session.generation) {
            // Keep the current visual frame, but allow a drop before expansion finishes.
            cancelLayoutAnimator();
            showTask(session.card, targetSlot);
        }
        relayoutPanes();
        updateRecentIcons();
        updateInput();
    }

    private void holdManualPlacement(Pane pane) {
        if (pane == null || pane.host == null || pane.host.home()) return;
        pendingOrientations.remove(pane.host);
        requestedOrientations.remove(pane.host);
        // A null marker consumes the first post-swap orientation report and keeps
        // the explicit placement until the app changes its request again.
        manualOrientations.put(pane.host, null);
    }

    private int dropSlot(float pointerX, float pointerY) {
        if (!running() || workspace == null || OneHandedTaskHooks.shadeOpen()) return -1;
        int x = (int) (pointerX - workspace.getX());
        int y = (int) (pointerY - workspace.getY());
        for (int slot = 0; slot < COUNT; slot++) {
            if (isSideWindowHidden(slot)) continue;
            Pane pane = panes[slot];
            if (pane != null && !pane.replacing && isPaneVisible(pane)
                    && pane.container.getVisibility() == View.VISIBLE
                    && pane.container.getAlpha() > 0.1f && currentPaneFrame(pane).contains(x, y)) return slot;
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
        handler.post(() -> {
            if (state == State.CLOSING && !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId)) close(false);
            else if (active()) {
                int previousToolbarHeight = layoutToolbarHeightDp;
                int previousToolbarPosition = layoutToolbarPositionDp;
                boolean previousKeepMainAspectRatio = layoutKeepMainAspectRatio;
                ModuleConfig config = ModuleConfig.load(context);
                boolean currentKeepMainAspectRatio = oneHandedProfile
                        ? config.oneHandedWorkspaceKeepMainAspectRatio : config.workspaceKeepMainAspectRatio;
                refreshEnvironment();
                if (previousToolbarHeight != toolbarHeightDp()
                        || previousToolbarPosition != toolbarPositionDp()
                        || previousKeepMainAspectRatio != currentKeepMainAspectRatio) relayoutPanes();
                scheduleCheck(100);
            }
        });
    }

    boolean blocksNavigation() {
        return active() && entryStarted && !suspended
                && OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
    }

    void exitToHome() {
        int request = generation;
        Runnable exit = () -> {
            if (request != generation || state == State.CLOSED
                    || !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId)) return;
            homeRequested = true;
            close(false);
        };
        if (android.os.Looper.myLooper() == handler.getLooper()) exit.run();
        else handler.post(exit);
    }

    private void refreshEnvironment() {
        if (!active()) return;
        boolean next = !OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId);
        try { navigation.setLocked(entryStarted && !next, workspaceOwnerUserId); }
        catch (Exception error) { fail("无法更新工作台导航状态", error); return; }
        if (suspended == next) return;
        suspended = next;
        shell.setSuspended(next);
        if (suspended && state == State.OPENING) { close(false); return; }
        OneStepStatusBar.setVisible(!suspended && !covered && hostVisible);
        if (backdrop != null) backdrop.setVisibility(suspended ? View.INVISIBLE : View.VISIBLE);
        if (suspended) {
            handler.removeCallbacks(openingTimeout);
            finishAppDrag(-1);
            if (animator != null) animator.end();
            if (layoutAnimator != null) layoutAnimator.end();
            if (transitionAnimator != null) transitionAnimator.end();
        } else {
            if (state == State.OPENING) {
                handler.removeCallbacks(openingTimeout);
                handler.postDelayed(openingTimeout, 12000);
                startInitialTask();
            }
            if (hostVisible && !covered && !OneHandedTaskHooks.shadeOpen()) focusActivePane();
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
        scheduleOrientationRouting();
        scheduleCheck(suspended || landscapeVisible() || !taskNotifications ? 700 : 1500);
    }

    @Override public void onCoverageChanged(boolean value) {
        if (!active()) return;
        covered = value;
        OneStepStatusBar.setVisible(!value && !suspended && hostVisible);
        if (value) {
            finishAppDrag(-1);
            if (layoutAnimator != null) layoutAnimator.end();
        } else if (running() && !suspended && !rebinding && hostVisible && !panesBusy() && !OneHandedTaskHooks.shadeOpen()) {
            focusActivePane();
        }
        updateInput();
    }

    @Override public void onTaskChanged(OneStepShell.Host host, RecentTaskCard previous) {
        if (!active()) return;
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.card = host.card;
            if (directlyInteractive(pane)) pinRecent(previous);
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
        if (!running() || movingTask || suspended || rebinding || animator != null || layoutAnimator != null || transitionAnimator != null || panesBusy()
                || dragSession != null || desktopBusy || OneHandedTaskHooks.shadeOpen()) {
            if (attempt < 100) handler.postDelayed(() -> acceptNavigation(navigation, attempt + 1), 50);
            else shell.abandonNavigation(navigation);
            return;
        }
        Pane pane = paneForHost(navigation.source);
        if (!directlyInteractive(pane)) { shell.abandonNavigation(navigation); return; }
        Pane existing = hostedPane(navigation.card);
        if (existing != null) {
            // Focus/task reuse must use the same move path as an explicit icon selection.
            if (canChangeTask()) {
                if (directlyInteractive(existing)) focusPane(existing);
                else showTask(existing.card, pane.slot);
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
            pane.configureHost(pane.launchHost);
        } catch (Exception error) {
            OneStepFlowLog.w(TAG, "Cannot accept application navigation", error);
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
        OneStepFlowLog.info("task_ready", host.session,
                "task=" + (host.card == null ? -1 : host.card.taskId)
                        + " package=" + (host.card == null || host.card.component == null
                                ? "unknown" : host.card.component.getPackageName())
                        + " home=" + host.home());
        for (Pane pane : panes) if (pane != null && pane.launchHost == host) {
            OneStepShell.Host previous = pane.host;
            RecentTaskCard previousCard = pane.card;
            pane.launchHost = null;
            pane.host = host;
            pane.card = host.card;
            pane.empty.setVisibility(View.GONE);
            boolean retainDesktop = previous != null && previous.home();
            if (retainDesktop) parkDesktop(previous);
            if (previousCard != null && !previousCard.home && !previousCard.sameTask(host.card)) {
                pane.history.removeIf(card -> card.sameTask(previousCard));
                pane.history.add(0, previousCard);
            } else if (previousCard != null && previousCard.home) pane.history.clear();
            if (host.navigation != null) shell.completeNavigation(host.navigation);
            if (!host.card.temporary) {
                RecentTaskCard pin = previousCard;
                if (pin != null && pin.temporary) for (RecentTaskCard item : pane.history) {
                    if (!item.temporary) { pin = item; break; }
                }
                pinRecent(pin);
            }
            rememberRecent(host.card);
            focusLoadedPane(pane);
            int request = generation;
            Runnable finished = () -> {
                if (!running() || generation != request) return;
                if (previous != null && !retainDesktop) removeHostView(previous);
                pane.replacing = false;
                desktopBusy = false;
                pane.updateAppearance();
                // Launcher handoff replaces a SurfaceView after the pane has already
                // been laid out. Reapply the decoration once the new view has its
                // final scale; otherwise the first frame can retain the desktop's
                // larger radius until another pane operation triggers a relayout.
                pane.container.postOnAnimation(() -> {
                    if (active() && generation == request && pane.host == host) {
                        pane.updateAppearance();
                        host.refreshCornerRadius(pane.cornerRadius);
                        refreshInputRegion();
                    }
                });
                updateRecentIcons();
                updateInput();
                scheduleOrientationRouting();
            };
            if (previous != null && !retainDesktop) shell.release(previous, finished);
            else finished.run();
            return;
        }
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.card = host.card;
            pane.replacing = false;
            rememberRecent(host.card);
            updateRecentIcons();
            if (host.home()) {
                desktopBusy = false;
                positionDesktop(pane);
            }
            pane.empty.setVisibility(View.GONE);
            if (directlyInteractive(pane)) {
                if (state == State.OPENING) startEntryAnimation();
                if (!OneHandedTaskHooks.shadeOpen()) {
                    focusLoadedPane(pane);
                }
            } else if (!OneHandedTaskHooks.shadeOpen()) focusActivePane();
            pane.focusAfterLoad = null;
            if (perf != null) perf.taskReady(pane.slot, host.card.taskId);
        }
        updateInput();
        scheduleOrientationRouting();
    }

    @Override public void onLaunchFailed(OneStepShell.Host host) {
        OneStepFlowLog.info("app_launch_failure_received", host.session,
                "task=" + (host.card == null ? -1 : host.card.taskId));
        pendingOrientations.remove(host);
        requestedOrientations.remove(host);
        manualOrientations.remove(host);
        if (!active()) return;
        if (host == desktopHost) {
            disconnectDesktop();
            removeHostView(host);
            desktopHost = null;
        }
        for (Pane pane : panes) if (pane != null) {
            if (pane.launchHost == host) {
                removeHostView(host);
                pane.launchHost = null;
                pane.replacing = false;
                desktopBusy = false;
                if (desktopSession != null) desktopSession.retry();
                if (pane.host != null && pane.host.home()) shell.parkDesktop(pane.host, false);
                pane.empty.setVisibility(pane.host == null ? View.VISIBLE : View.GONE);
                focusActivePane();
                Toast.makeText(context, "应用未能打开，请重试", Toast.LENGTH_SHORT).show();
                if (landscapeWindow.isSlot(pane.slot)) relayoutPanes();
                updateInput();
                return;
            }
            if (pane.host == host) {
                removeHostView(host);
                pane.host = null;
                pane.card = null;
                pane.replacing = false;
                pane.showEmpty();
                if (state == State.OPENING) fail("无法打开应用窗口", new IllegalStateException("Initial task unavailable"));
                else if (host.home()) desktopUnavailable(pane);
                else {
                    Toast.makeText(context, "暂时无法打开应用", Toast.LENGTH_SHORT).show();
                    if (directlyInteractive(pane)) returnToPrevious(pane, generation);
                    if (landscapeWindow.isSlot(pane.slot)) relayoutPanes();
                    focusActivePane();
                    updateInput();
                }
                return;
            }
        }
    }

    @Override public void onTaskReused(OneStepShell.Host launching, OneStepShell.Host existing) {
        if (!running()) return;
        OneStepFlowLog.info("task_reused", generation,
                "task=" + (existing.card == null ? -1 : existing.card.taskId));
        for (Pane target : panes) if (target != null && target.host == existing && existing.ready) {
            for (Pane source : panes) if (source != null && source.launchHost == launching) {
                removeHostView(launching);
                source.launchHost = null;
                source.replacing = false;
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
            focusActivePane();
            updateInput();
            return;
        }
        if (animator != null || layoutAnimator != null || transitionAnimator != null || imeAnimator != null || imeAnimating) {
            handler.postDelayed(() -> selectReusedTask(target, request, attempt + 1), 100);
            return;
        }
        desktopBusy = false;
        select(target.slot);
    }

    @Override public void onRemoved(OneStepShell.Host host) {
        OneStepFlowLog.info("task_removed", host.session,
                "task=" + (host.card == null ? -1 : host.card.taskId)
                        + " home=" + host.home());
        pendingOrientations.remove(host);
        requestedOrientations.remove(host);
        manualOrientations.remove(host);
        if (state == State.OPENING || state == State.CLOSING) { close(false); return; }
        if (!active()) return;
        if (host == desktopHost) {
            disconnectDesktop();
            removeHostView(host);
            desktopHost = null;
        }
        for (Pane pane : panes) if (pane != null && pane.launchHost == host) { onLaunchFailed(host); return; }
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            Pane focused = activePane();
            pane.focusAfterLoad = focusedSlot != pane.slot && focused != null ? focused.host : null;
            if (host.home()) disconnectDesktop();
            if (pane.launchHost != null) {
                // The source may finish as part of a successful redirect. Its successor
                // still owns this pane's pending handoff and must be allowed to mount.
                removeHostView(host);
                pane.host = null;
                pane.card = null;
                return;
            }
            removeHostView(host);
            pane.host = null;
            pane.card = null;
            pane.replacing = false;
            pane.showEmpty();
            if (directlyInteractive(pane)) {
                if (pane.slot == mainSlot) desktopBusy = false;
                if (!host.home()) returnToPrevious(pane, generation);
            }
        }
        updateRecentIcons();
        relayoutPanes();
        focusActivePane();
    }

    private void returnToPrevious(Pane pane, int request) {
        if (!running() || generation != request || !directlyInteractive(pane) || panes[pane.slot] != pane
                || hasPaneContent(pane)) return;
        if (!canChangeTask()) {
            handler.postDelayed(() -> returnToPrevious(pane, request), 100);
            return;
        }
        while (!pane.history.isEmpty()) {
            RecentTaskCard previous = pane.history.remove(0);
            Pane existing = hostedPane(previous);
            // Never pull the other directly operated window away to fill this vacancy.
            if (existing != null && directlyInteractive(existing)) continue;
            if (existing != null) {
                OneStepShell.Host retained = pane.focusAfterLoad;
                pane.focusAfterLoad = null;
                showTask(existing.card, pane.slot);
                Pane focused = paneForHost(retained);
                if (directlyInteractive(focused)) focusPane(focused);
                return;
            }
            pane.manualLandscapeLoad = landscapeWindow.isSlot(pane.slot);
            if (pane.load(previous)) return;
        }
        if (pane.slot == mainSlot) returnToDesktop(pane, request);
        else {
            pane.focusAfterLoad = null;
            pane.manualLandscapeLoad = false;
            relayoutPanes();
            focusActivePane();
        }
    }

    private void returnToDesktop(Pane pane, int request) {
        if (!running() || generation != request || pane != panes[mainSlot] || pane.host != null
                || pane.launchHost != null || pane.replacing || desktopBusy) return;
        if (animator != null || layoutAnimator != null || transitionAnimator != null || imeAnimator != null || imeAnimating
                || dragSession != null || OneHandedTaskHooks.shadeOpen()) {
            handler.postDelayed(() -> returnToDesktop(pane, request), 100);
            return;
        }
        enterDesktop(pane.slot, false);
    }

    @Override public void onFailure(String message, Exception error) { if (active()) fail(message, error); }

    @Override public void onWorkspaceTransitionCancelled() {
        if (state == State.OPENING || state == State.CLOSING || transitionHandoff) close(false);
    }

    @Override public void onHomeRequested() { exitToHome(); }

    private void close(boolean focusMain) {
        if (state == State.CLOSED) return;
        OneStepFlowLog.info("workspace_close_requested", generation,
                "focusMain=" + focusMain + " state=" + state.name().toLowerCase(java.util.Locale.ROOT));
        if (state == State.CLOSING) {
            if (!focusMain) {
                closeGeneration++;
                handler.removeCallbacks(exitAnimationTimeout);
                cancelAnimator(false);
                transitionHandoff = false;
                shell.close(-1, this::finishClose);
                handler.removeCallbacks(closeWatchdog);
                handler.postDelayed(closeWatchdog, CLOSE_WATCHDOG_MS);
            }
            return;
        }
        if (focusMain && running()) haptic(HapticFeedbackConstants.CONTEXT_CLICK);
        Pane main = activePane();
        boolean animated = focusMain && backdrop != null && hostVisible && !suspended && !covered
                && main != null && main.slot == mainSlot && main.host != null && main.host.ready && !rebinding && !panesBusy() && !movingTask;
        int closing = ++closeGeneration;
        state = State.CLOSING;
        handler.removeCallbacks(closeWatchdog);
        handler.postDelayed(closeWatchdog, CLOSE_WATCHDOG_MS);
        releaseNavigation();
        recentApps.setExitEnabled(false);
        clearPaneTapHighlight();
        disconnectDesktop();
        handler.removeCallbacks(openingTimeout);
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        handler.removeCallbacks(routeOrientations);
        pendingOrientations.clear();
        requestedOrientations.clear();
        manualOrientations.clear();
        onSuccess = null;
        openingTask = null;
        blockInput(true);
        cancelAnimator(true);
        cancelLayoutAnimator();
        cancelAnimator(false);
        cancelImeAnimator();
        imeAnimating = false;
        shellImeControlled = false;
        shellImePositioning = false;
        finishAppDrag(-1);
        int focusTask = focusMain && main != null && main.card != null ? main.card.taskId : -1;
        Runnable restore = () -> {
            handler.removeCallbacks(exitAnimationTimeout);
            shell.close(focusTask, this::finishClose);
        };
        if (perf != null) perf.phase("closing");
        if (animated) {
            transitionHandoff = true;
            handler.postDelayed(exitAnimationTimeout, 2000);
            shell.prepareWorkspaceExit(main.host, mainScreenFrame(),
                    dp(PANE_RADIUS_DP) * transitionProgress, transitionProgress, bounds -> {
                        if (state != State.CLOSING || closing != closeGeneration) return;
                        transitionBounds.set(bounds);
                        animateWorkspace(false, restore);
                    });
        }
        else restore.run();
    }

    private void finishClose() {
        if (state == State.CLOSED) return;
        int closedSession = generation;
        boolean returnHomeAfterClose = homeRequested;
        handler.removeCallbacks(exitAnimationTimeout);
        handler.removeCallbacks(closeWatchdog);
        cancelAnimator(true);
        cancelLayoutAnimator();
        cancelAnimator(false);
        cancelImeAnimator();
        imeAnimating = false;
        shellImeControlled = false;
        shellImePositioning = false;
        if (perf != null) { perf.stop(); perf = null; }
        OneStepStatusBar.reset();
        if (activitySession != null) { activitySession.close(); activitySession = null; }
        backdrop = null;
        workspace = null;
        desktopHost = null;
        desktopContainer = null;
        desktopBorder = null;
        recentStrip = null;
        recentApps.clear();
        for (int i = 0; i < COUNT; i++) panes[i] = null;
        generation++;
        desktopBusy = false;
        state = State.CLOSED;
        OneStepFlowLog.info("workspace_closed", closedSession,
                "returnHome=" + returnHomeAfterClose);
        suspended = false;
        rebinding = false;
        initialTaskStarted = false;
        entryAnimationStarted = false;
        entryStarted = false;
        transitionHandoff = false;
        releaseNavigation();
        boolean returnHome = homeRequested;
        homeRequested = false;
        if (returnHome && OneHandedTaskHooks.workspaceAllowed(context, workspaceOwnerUserId)) {
            try { tasks.startHome(workspaceOwnerUserId); }
            catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot return to Home after restoring workspace", error); }
        }
    }

    private void releaseNavigation() {
        if (active()) return;
        try { navigation.setLocked(false, workspaceOwnerUserId); }
        catch (Exception error) {
            OneStepFlowLog.w(TAG, "Navigation release will retry", error);
            handler.postDelayed(this::releaseNavigation, 500);
        }
    }

    private void fail(String message, Exception error) {
        OneStepFlowLog.warn("workspace_failed", generation, "reason=" + message, error);
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
                out.setAlpha(appearanceProgress);
            }
        };
        float cornerRadius;
        float appearanceProgress = 1f;
        RecentTaskCard card;
        OneStepShell.Host host;
        OneStepShell.Host launchHost;
        boolean replacing;
        boolean manualLandscapeLoad;
        OneStepShell.Host focusAfterLoad;

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
            empty.setOnClickListener(v -> { if (this.slot == mainSlot) enterDesktop(this.slot, true); else select(this.slot); });
            container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            container.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) select(this.slot);
                return true;
            });
            container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateInput());
        }

        void updateAppearance() {
            float scale = Math.max(0.01f, container.getScaleX());
            // Pane views retain the full app viewport. Compensate their decorations for
            // scaling so all four windows keep the same on-screen corner/stroke/text size.
            cornerRadius = dp(PANE_RADIUS_DP) * (slot == mainSlot ? transitionProgress : 1f) / scale;
            fill.setCornerRadius(cornerRadius);
            fill.setAlpha(Math.round(255 * appearanceProgress));
            border.setCornerRadius(cornerRadius);
            boolean highlighted = highlightedSlot == slot || tappedSlot == slot;
            border.setColor(highlighted ? 0x225aaaff : Color.TRANSPARENT);
            border.setStroke(Math.max(1, Math.round(dp(highlighted ? 2 : 0.7f) / scale)),
                    highlighted ? 0xff9acbff : 0x40ffffff);
            border.setAlpha(Math.round(255 * appearanceProgress));
            float mainScale = 0.01f;
            for (int i = 0; i < frames.length; i++) mainScale = Math.max(mainScale, scaleForFrame(frames[i], i));
            // During swaps the larger pane carries the stronger shadow throughout the motion.
            float prominence = Math.max(0f, Math.min(1f, scale / Math.max(0.01f, mainScale)));
            container.setElevation(dp(8 + 10 * prominence * prominence) * appearanceProgress / scale);
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
            empty.setAlpha(appearanceProgress);
            positionDesktop(this);
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
                } catch (Exception error) { OneStepFlowLog.w(TAG, "Cannot resolve selected task", error); }
                RecentTaskCard cardToLoad = resolved;
                handler.post(() -> {
                    if (!active() || generation != request || panes[slot] != this) return;
                    if (cardToLoad == null) {
                        replacing = false;
                        manualLandscapeLoad = false;
                        recentApps.remove(next);
                        Toast.makeText(context, "应用任务已结束", Toast.LENGTH_SHORT).show();
                        if (host == null && directlyInteractive(this)) returnToPrevious(this, request);
                        if (landscapeWindow.isSlot(slot)) relayoutPanes();
                        updateInput();
                        return;
                    }
                    try {
                        OneStepShell.Host created = shell.create(context, cardToLoad,
                                state == State.OPENING && slot == mainSlot, true);
                        if (manualLandscapeLoad) manualOrientations.put(created, null);
                        manualLandscapeLoad = false;
                        if (previous == null) { host = created; card = cardToLoad; }
                        else launchHost = created;
                        mount(created);
                        created.view.bringToFront();
                        if (Build.VERSION.SDK_INT >= 34) created.view.setAlpha(slot == mainSlot ? 1f : appearanceProgress);
                        configureHost(created);
                        updateAppearance();
                        updateInput();
                    } catch (Exception error) {
                        replacing = false;
                        manualLandscapeLoad = false;
                        if (launchHost != null) {
                            OneStepShell.Host failed = launchHost;
                            shell.release(failed, () -> onLaunchFailed(failed));
                        } else if (host != null && host != previous) {
                            OneStepShell.Host failed = host;
                            shell.release(failed, () -> onLaunchFailed(failed));
                        } else if (state == State.OPENING) fail("无法创建应用窗口", error);
                        else {
                            OneStepFlowLog.w(TAG, "Cannot replace application", error);
                            Toast.makeText(context, "暂时无法打开应用", Toast.LENGTH_SHORT).show();
                            if (host == null && directlyInteractive(this)) returnToPrevious(this, request);
                            if (landscapeWindow.isSlot(slot)) relayoutPanes();
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
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) select(this.slot);
                // TaskView owns the embedded application's input surface. The listener
                // only observes the first touch to update focus; consuming the stream
                // prevents the app from receiving touches when its input hole is rebuilt
                // after an automatic portrait-to-landscape move.
                return false;
            });
            task.obscure(new Rect(screenBounds));
            if (task.home()) {
                desktopHost = task;
                if (desktopContainer == null) {
                    desktopContainer = new FrameLayout(context);
                    desktopContainer.setPivotX(0);
                    desktopContainer.setPivotY(0);
                    desktopBorder = new GradientDrawable();
                    desktopContainer.setForeground(desktopBorder);
                    desktopContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateInput());
                    workspace.addView(desktopContainer,
                            new FrameLayout.LayoutParams(desktopBounds.width(), desktopBounds.height()));
                }
                // Reusing another empty pane changes only geometry and input ownership.
                if (task.view.getParent() != desktopContainer)
                    desktopContainer.addView(task.view, new FrameLayout.LayoutParams(-1, -1));
                positionDesktop(this);
            } else container.addView(task.view, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        void showEmpty() {
            empty.setText(landscapeWindow.isSlot(slot) ? landscapeWindow.emptyText() : "＋");
            empty.setContentDescription(landscapeWindow.isSlot(slot) ? landscapeWindow.emptyDescription()
                    : "空白应用窗口，点击打开桌面，或长按上方应用图标拖入此处");
            empty.setVisibility(View.VISIBLE);
        }

        void updateGeometry() {
            if (!active()) return;
            // Keep the task surface at 1:1 inside TaskView; only the container scales it.
            if (host != null) {
                configureHost(host);
            }
            if (launchHost != null) {
                configureHost(launchHost);
            }
            updateAppearance();
        }

        private void syncTaskBounds(OneStepShell.Host task) {
            // TaskView reads getBoundsOnScreen() from its next laid out frame. A
            // plain post can run before the LayoutParams traversal and leave WM
            // with the previous fullscreen bounds, which then squashes app content.
            task.view.postOnAnimation(() -> {
                if (active() && (host == task || launchHost == task)) shell.syncTaskBounds(task);
            });
        }

        void configureHost(OneStepShell.Host task) {
            Rect bounds = task.home() ? desktopBounds : viewport(slot);
            Rect sourceBounds = landscapeWindow.isSlot(slot) ? bounds : logicalBounds;
            // Home stays fullscreen in WM; retain its complete source viewport so
            // shortening the pane does not crop away the dock or lower app icons.
            Rect surfaceBounds = null;
            if (!task.home()) {
                // New landscape tasks can arrive while their pane animates from
                // a hidden/portrait frame. Configure the final horizontal bounds
                // once; the animation only scales the resulting app surface.
                RectF frame = landscapeWindow.isSlot(slot)
                        ? new RectF(frames[slot]) : currentPaneFrame(this);
                surfaceBounds = new Rect(
                        Math.round(contentBounds.left + workspace.getTranslationX() + frame.left),
                        Math.round(contentBounds.top + workspace.getTranslationY() + frame.top),
                        Math.round(contentBounds.left + workspace.getTranslationX() + frame.right),
                        Math.round(contentBounds.top + workspace.getTranslationY() + frame.bottom));
            }
            shell.geometry(task, task.home() ? logicalBounds : sourceBounds,
                    bounds.width(), bounds.height(), slot == mainSlot, landscapeWindow.isSlot(slot),
                    surfaceBounds);
            syncTaskBounds(task);
        }
    }
}
