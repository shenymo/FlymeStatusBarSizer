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
    private static final int PANE_GAP_DP = 4;
    private static final int WORKSPACE_MARGIN_DP = 12;
    private static final int PANE_RADIUS_DP = 16;
    private static final PathInterpolator EASING = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private enum State { CLOSED, OPENING, RUNNING, CLOSING }
    private volatile State state = State.CLOSED;
    private final Context context;
    private final Handler handler;
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
    private int width;
    private int height;
    private int mainSlot;
    private int generation;
    private int highlightedSlot = -1;
    private int imeBottom;
    private int imeOffset;
    private boolean imeAnimating;
    private int activityImeBottom;
    private boolean activityImeAnimating;
    private boolean shellImeControlled;
    private boolean shellImePositioning;
    private boolean shellImeFloating;
    private int shellImeTargetBottom;
    private boolean mainOnLeft;
    private boolean checkInFlight;
    private boolean taskNotifications;
    private float transitionProgress;
    private DragSession dragSession;
    private ImageView dragSource;
    private ImageView dragPreview;
    private Runnable onSuccess;
    private OneStepPerf perf;

    OneStepWorkspace(Context source, Handler handler, Object transitions, Object factory, Object displayAreas)
            throws Exception {
        this.handler = handler;
        DisplayManager displays = source.getSystemService(DisplayManager.class);
        context = source.createDisplayContext(displays.getDisplay(0));
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
                || !(running() ? OneHandedTaskHooks.workspaceAllowed(context)
                        : OneHandedTaskHooks.environmentAllowed(context))) return false;
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
        onSuccess = success;
        shell.begin(request, main.userId);
        activitySession = new OneStepActivitySession(context, handler, main.userId, new OneStepActivitySession.Listener() {
            @Override public void onAttached(int taskId, Rect bounds, Rect insets) {
                if (generation != request || state != State.OPENING) return;
                shell.attachActivity(taskId, () -> {
                    if (generation != request || state != State.OPENING) return;
                    try { open(recent, fromLeft, bounds, Insets.of(insets.left, insets.top, insets.right, insets.bottom)); }
                    catch (Exception error) { fail("无法连接工作台窗口", error); }
                });
            }

            @Override public void onMounted() {
                if (generation != request || state != State.OPENING || backdrop == null) return;
                panes[0].load(main);
                applyTransition(0f);
                backdrop.postOnAnimation(() -> { if (active() && generation == request) animateWorkspace(true, null); });
                scheduleCheck(300);
            }

            @Override public void onImeChanged(int bottom, boolean animating) {
                if (generation != request || !active()) return;
                activityImeBottom = bottom;
                activityImeAnimating = animating;
                // Flyme keeps Activity insets visible until Shell's hide animation ends.
                // They cannot drive movement while Shell owns the actual IME surface.
                if (!shellImeControlled) applyActivityIme();
            }

            @Override public void onClosed(boolean focusMain) {
                if (generation == request) close(focusMain);
            }

            @Override public void onBarColorsChanged(boolean darkIcons) {
                if (generation == request && active()) OneStepStatusBar.setDarkIcons(darkIcons);
            }
        });
        handler.postDelayed(openingTimeout, 12000);
        activitySession.start();
    }

    private void open(List<RecentTaskCard> recent, boolean fromLeft, Rect screen, Insets insets) throws Exception {
        screenBounds.set(screen);
        updateLogicalBounds(screen, insets);
        contentBounds = new Rect(logicalBounds);
        width = contentBounds.width();
        height = contentBounds.height();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid workspace bounds");
        mainSlot = 0;
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
        sideOrder.clear();
        for (int i = 1; i < COUNT; i++) sideOrder.add(i);
        frames = layout();
        backdrop = new WorkspaceRoot();
        backdrop.setClipChildren(false);
        backdrop.setClipToPadding(false);
        backdrop.setOnClickListener(v -> { if (dragSession == null) close(true); });
        workspace = new FrameLayout(context);
        workspace.setClipChildren(false);
        workspace.setClipToPadding(false);
        workspace.setOnClickListener(v -> { if (dragSession == null) close(true); });
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
        OneStepStatusBar.setVisible(true);
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
            workspace.post(this::updateInput);
        });
    }

    void onShellImeControlLost() {
        dispatchShellIme(() -> {
            if (!shellImeControlled) return;
            shellImeControlled = false;
            shellImePositioning = false;
            applyActivityIme();
        });
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
        if (imeBottom == activityImeBottom && imeAnimating == activityImeAnimating) return;
        imeBottom = activityImeBottom;
        imeAnimating = activityImeAnimating;
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
            workspace.setTranslationY(-nextOffset);
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
        movement.addUpdateListener(value -> workspace.setTranslationY(-(float) value.getAnimatedValue()));
        movement.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (imeAnimator != animation) return;
                imeAnimator = null;
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
        if (!running() || slot == mainSlot || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null || desktopBusy) return;
        Pane selected = panes[slot];
        if (selected.host == null && !selected.replacing) { enterDesktop(slot); return; }
        if (selected.host == null || !selected.host.ready) return;
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

    private void enterDesktop(int slot) {
        if (!running() || desktopBusy || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || dragSession != null || OneHandedTaskHooks.shadeOpen()) return;
        Pane target = panes[slot];
        if (target == null || target.host != null || target.replacing) return;
        Pane previous = panes[mainSlot];
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
        pane.empty.setText("点击打开桌面");
        pane.empty.setVisibility(View.VISIBLE);
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
        boolean blocked = !running() || desktopBusy || animator != null || transitionAnimator != null
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
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(true);
        strip.setContentDescription("最近使用的应用，长按图标拖入侧边窗口");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x80171c25);
        background.setCornerRadius(dp(18));
        background.setStroke(Math.max(1, dp(0.7f)), 0x30ffffff);
        strip.setBackground(background);
        strip.setClipToOutline(true);
        strip.setElevation(dp(4));
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
            icon.setAlpha(available && icon != dragSource ? 1f : 0.4f);
            icon.setLongClickable(available);
            icon.setContentDescription(card.description + (available ? "，长按拖入侧边窗口" : "，已在工作台中"));
        }
    }

    private boolean beginAppDrag(ImageView icon, RecentTaskCard card) {
        if (!running() || animator != null || transitionAnimator != null
                || imeAnimator != null || imeAnimating || desktopBusy
                || dragSession != null || isHosted(card) || OneHandedTaskHooks.shadeOpen()
                || backdrop == null || !backdrop.hasDragPointer()) return false;
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
            icon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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
                if (!running() || generation != session.generation || targetSlot == mainSlot
                        || panes[targetSlot] == null || OneHandedTaskHooks.shadeOpen()) return;
                panes[targetSlot].load(session.card);
            });
        }
    }

    private int dropSlot(float pointerX, float pointerY) {
        if (!running() || workspace == null || OneHandedTaskHooks.shadeOpen()) return -1;
        int x = (int) (pointerX - workspace.getX());
        int y = (int) (pointerY - workspace.getY());
        for (int slot : sideOrder) {
            if (panes[slot] != null && !panes[slot].replacing && frames[slot].contains(x, y)) return slot;
        }
        return -1;
    }

    private void highlightDropTarget(int slot) {
        if (highlightedSlot == slot) return;
        highlightedSlot = slot;
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
        // A task snapshot can return after its pane has been replaced/removed. Remember
        // the identities present when the query started so one stale focus cannot exit.
        ArrayList<RecentTaskCard> queriedCards = new ArrayList<>();
        for (Pane pane : panes) if (pane != null) {
            if (pane.card != null) queriedCards.add(pane.card);
            if (pane.launchHost != null && pane.launchHost.card != null) queriedCards.add(pane.launchHost.card);
        }
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
                            if (OneStepShell.taskEnded(root)) continue;
                            boolean hosted = shell.isActivityTask(root);
                            for (RecentTaskCard card : queriedCards) if (card.taskId == OneStepTaskAccess.taskId(root)
                                    && card.token.equals(OneStepTaskAccess.token(root))) hosted = true;
                            for (Pane pane : panes) if (pane != null && pane.card != null
                                    && pane.card.taskId == OneStepTaskAccess.taskId(root)
                                    && pane.card.token.equals(OneStepTaskAccess.token(root))) hosted = true;
                            for (Pane pane : panes) if (pane != null && pane.launchHost != null) {
                                RecentTaskCard launching = pane.launchHost.card;
                                if (OneStepShell.hasCookie(pane.launchHost, root)) hosted = true;
                                if (launching != null && launching.taskId == OneStepTaskAccess.taskId(root)
                                        && launching.token.equals(OneStepTaskAccess.token(root))) hosted = true;
                            }
                            if (!hosted) {
                                Log.i(TAG, "Leaving workspace for focused task=" + OneStepTaskAccess.taskId(root));
                                close(false);
                                return;
                            }
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
        for (Pane pane : panes) if (pane != null && pane.launchHost == host) {
            OneStepShell.Host home = pane.host;
            pane.launchHost = null;
            pane.host = host;
            pane.card = host.card;
            pane.empty.setVisibility(View.GONE);
            disconnectDesktop();
            shell.focus(host);
            int request = generation;
            shell.release(home, () -> {
                if (!running() || generation != request) return;
                pane.container.removeView(home.view);
                desktopBusy = false;
                pane.updateAppearance();
                updateRecentIcons();
                updateInput();
            });
            return;
        }
        for (Pane pane : panes) if (pane != null && pane.host == host) {
            pane.card = host.card;
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
        if (!running()) return;
        for (Pane pane : panes) if (pane != null) {
            if (pane.launchHost == host) {
                pane.container.removeView(host.view);
                pane.launchHost = null;
                desktopBusy = false;
                if (desktopSession != null) desktopSession.retry();
                pane.empty.setVisibility(View.GONE);
                shell.focus(pane.host);
                Toast.makeText(context, "应用未能打开，请重试", Toast.LENGTH_SHORT).show();
                updateInput();
                return;
            }
            if (pane.host == host && host.home()) {
                pane.container.removeView(host.view);
                pane.host = null;
                pane.card = null;
                desktopUnavailable(pane);
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
                OneStepShell.Host launching = pane.launchHost;
                pane.launchHost = null;
                shell.release(launching, () -> pane.container.removeView(launching.view));
            }
            pane.container.removeView(host.view);
            pane.host = null;
            pane.card = null;
            pane.showEmpty();
            if (pane.slot == mainSlot) {
                desktopBusy = false;
                shell.focus(null);
                if (!host.home()) returnToDesktop(pane, generation, 0);
            }
        }
        updateRecentIcons();
        updateInput();
    }

    private void returnToDesktop(Pane pane, int request, int attempt) {
        if (!running() || generation != request || pane.slot != mainSlot || pane.host != null
                || pane.launchHost != null || desktopBusy || attempt >= 30) return;
        if (animator != null || transitionAnimator != null || imeAnimator != null || imeAnimating
                || dragSession != null || OneHandedTaskHooks.shadeOpen()) {
            handler.postDelayed(() -> returnToDesktop(pane, request, attempt + 1), 100);
            return;
        }
        enterDesktop(pane.slot);
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
        disconnectDesktop();
        handler.removeCallbacks(openingTimeout);
        handler.removeCallbacks(check);
        handler.removeCallbacks(taskChanged);
        onSuccess = null;
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
        recentIcons.clear();
        for (int i = 0; i < COUNT; i++) panes[i] = null;
        checkInFlight = false;
        generation++;
        desktopBusy = false;
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
        final GradientDrawable fill = new GradientDrawable();
        final GradientDrawable border = new GradientDrawable();
        final ViewOutlineProvider outline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
                out.setAlpha(transitionProgress);
            }
        };
        float cornerRadius;
        float appliedSurfaceRadius = -1;
        boolean surfaceCornersUnavailable;
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
            empty.setText("＋\n点击打开桌面\n或拖入应用");
            empty.setBackgroundColor(Color.TRANSPARENT);
            empty.setContentDescription("空白应用窗口，点击打开桌面，或长按上方应用图标拖入此处");
            empty.setOnClickListener(v -> { if (slot == mainSlot) enterDesktop(slot); else select(slot); });
            container.addView(empty, new FrameLayout.LayoutParams(-1, -1));
            container.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && slot != mainSlot) select(slot);
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
            boolean highlighted = highlightedSlot == slot;
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
            if (host != null) host.view.invalidateOutline();
            // Counter-scale the centered label without requesting layout on every frame.
            empty.setScaleX(1f / scale);
            empty.setScaleY(1f / scale);
            empty.setAlpha(transitionProgress);
            if (host != null && !surfaceCornersUnavailable && appliedSurfaceRadius != cornerRadius) {
                try {
                    // SurfaceView owns both the compositor crop and its rounded window hole.
                    // Clipping only the FrameLayout would leave the live app square underneath.
                    OneStepReflection.call(host.view, "setCornerRadius", new Class<?>[]{float.class}, cornerRadius);
                    appliedSurfaceRadius = cornerRadius;
                } catch (ReflectiveOperationException | RuntimeException error) {
                    surfaceCornersUnavailable = true;
                    Log.w(TAG, "Task surface corner radius unavailable", error);
                }
            }
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
                    appliedSurfaceRadius = -1;
                    surfaceCornersUnavailable = false;
                    mount(host);
                    updateAppearance();
                    if (Build.VERSION.SDK_INT >= 34) host.view.setAlpha(transitionProgress);
                    updateGeometry();
                    updateRecentIcons();
                    container.post(OneStepWorkspace.this::updateInput);
                } catch (Exception error) { fail("无法创建应用窗口", error); }
                replacing = false;
            };
            if (previous != null) shell.release(previous, attach);
            else attach.run();
        }

        void mount(OneStepShell.Host task) throws ReflectiveOperationException {
            task.view.setOutlineProvider(outline);
            task.view.setClipToOutline(true);
            task.view.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && slot != mainSlot) select(slot);
                return true;
            });
            task.obscure(new Rect(screenBounds));
            container.addView(task.view, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        void showEmpty() {
            empty.setText("＋\n点击打开桌面\n或拖入应用");
            empty.setVisibility(View.VISIBLE);
        }

        void updateGeometry() {
            if (host == null || !active()) return;
            // Keep the task surface at 1:1 inside TaskView; only the container scales it.
            shell.geometry(host, logicalBounds, logicalBounds.width(), logicalBounds.height(), slot == mainSlot);
        }
    }
}
