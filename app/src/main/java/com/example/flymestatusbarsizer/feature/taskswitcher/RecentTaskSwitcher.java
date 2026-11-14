package com.example.flymestatusbarsizer.feature.taskswitcher;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.LruCache;
import android.util.Log;
import android.view.animation.PathInterpolator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.feature.assistant.AssistantPanelBlur;
import com.example.flymestatusbarsizer.feature.assistant.SideGestureActions;
import com.example.flymestatusbarsizer.feature.onehanded.OneStepTaskAccess;
import com.example.flymestatusbarsizer.feature.tasks.RecentTaskCard;
import com.example.flymestatusbarsizer.feature.tasks.TaskProcessStopper;
import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** Compact recent-task switcher opened by a configured side gesture. */
public final class RecentTaskSwitcher {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TAG = "FlymeRecentTaskSwitcher";
    private static final int INITIAL_TASK_COUNT = 0;
    private static final int APPEND_TASK_BATCH_SIZE = 4;
    private static final long DEFERRED_TASK_BATCH_DELAY_MS = 360L;
    private static final long APPEND_TASK_BATCH_DELAY_MS = 72L;
    private static final long APPEND_ROW_ANIMATION_DURATION_MS = 180L;
    private static final long APPEND_ROW_ANIMATION_STAGGER_MS = 24L;
    private static final long INITIAL_ROW_ANIMATION_DELAY_MS = 100L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RecentTaskSwitcher");
        thread.setDaemon(true);
        return thread;
    });
    private static final LruCache<String, AppMetadata> APP_METADATA = new LruCache<>(64);
    private static Popup active;
    private static OneStepTaskAccess taskAccess;

    private RecentTaskSwitcher() { }

    public static void refreshActivePopup(Configuration configuration) {
        Popup popup;
        synchronized (RecentTaskSwitcher.class) {
            popup = active;
        }
        if (popup != null) popup.updateDarkMode(configuration);
    }

    public static void dismissActivePopup() {
        Popup popup;
        synchronized (RecentTaskSwitcher.class) { popup = active; }
        if (popup != null) popup.closeImmediately();
    }

    private static final class AppMetadata {
        final String label;
        final Drawable icon;

        AppMetadata(String label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class TaskEntry {
        final RecentTaskCard card;
        String label;
        Drawable icon;
        boolean metadataLoaded;
        boolean metadataLoading;
        TextView nameView;
        ImageView iconView;
        View row;

        TaskEntry(RecentTaskCard card) {
            this.card = card;
            label = card.description;
        }
    }

    private static final class PendingTaskRemoval {
        final TaskEntry entry;
        final View row;
        final int entryIndex;
        boolean rowDetached;
        boolean resultReady;
        boolean failed;
        boolean reload;

        PendingTaskRemoval(TaskEntry entry, View row, int entryIndex) {
            this.entry = entry;
            this.row = row;
            this.entryIndex = entryIndex;
        }
    }

    public static SideGestureActions.Action action(Context context) {
        return new SideGestureActions.Action() {
            private final Popup popup = new Popup(context);

            @Override public boolean isReady() {
                return context != null && context.getSystemService(Context.WINDOW_SERVICE) != null;
            }

            @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                execute(fromLeft, 0f, onSuccess);
            }

            @Override public void execute(boolean fromLeft, float anchorY, Runnable onSuccess) {
                if (isReady()) {
                    popup.show(fromLeft, anchorY);
                    if (onSuccess != null) onSuccess.run();
                }
            }
        };
    }

    private static final class Popup {
        private static final int ROW_HEIGHT_DP = 34;
        private final Context context;
        private boolean fromLeft;
        private float anchorY;
        private boolean darkMode;
        private final WindowManager windowManager;
        private final List<TaskEntry> entries = new ArrayList<>();
        private final Set<String> lockedApps;
        private final ContentObserver lockObserver;
        private final BroadcastReceiver configurationReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                updateDarkMode(root.getResources().getConfiguration());
            }
        };
        private final FrameLayout root;
        private final LinearLayout panel;
        private final AssistantPanelBlur panelBlur;
        private final LinearLayout rows;
        private final ScrollView scroll;
        private final TextView closeAllButton;
        private View loadingRow;
        private boolean attached;
        private boolean closing;
        private boolean closingAll;
        private PendingTaskRemoval pendingTaskRemoval;
        private boolean loading = true;
        private boolean secondaryLoading;
        private int taskLoadId;
        private long rowAnimationStartUptime;
        private long nextRowAnimationStartUptime;
        private int nextRowAnimationIndex;
        private final Map<Integer, Drawable.ConstantState> moduleIconStates = new HashMap<>();

        Popup(Context context) {
            this.context = context;
            FlymeRecentLockState.migrateLegacy(context);
            lockedApps = FlymeRecentLockState.read(context);
            lockObserver = new ContentObserver(MAIN) {
                @Override public void onChange(boolean selfChange) {
                    lockedApps.clear();
                    lockedApps.addAll(FlymeRecentLockState.read(Popup.this.context));
                    if (isCurrent() && !loading) renderRows();
                }
            };
            darkMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            root = new FrameLayout(context) {
                @Override
                protected void onConfigurationChanged(Configuration configuration) {
                    super.onConfigurationChanged(configuration);
                    updateDarkMode(configuration);
                }

                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        if (event.getAction() == KeyEvent.ACTION_UP) close();
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
            };
            root.setBackgroundColor(Color.TRANSPARENT);
            root.setFocusableInTouchMode(true);
            root.setOnClickListener(view -> close());
            root.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) close();
                return false;
            });

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(4), dp(4), dp(4), dp(2));
            panel.setBackground(panelBackground());
            panel.setElevation(dp(7));
            panel.setOnClickListener(view -> { });
            panelBlur = new AssistantPanelBlur(panel,
                    context.getResources().getDisplayMetrics().density);
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int panelWidth = Math.min(dp(160), Math.max(dp(147), screenWidth - dp(22)));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT));
            panelParams.leftMargin = dp(8);
            panelParams.rightMargin = dp(8);
            panelParams.topMargin = Math.max(0, Math.round(anchorY - dp(156)));
            root.addView(panel, panelParams);

            rows = new LinearLayout(context);
            rows.setOrientation(LinearLayout.VERTICAL);
            LayoutTransition rowTransition = new LayoutTransition();
            rowTransition.disableTransitionType(LayoutTransition.APPEARING);
            rowTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
            rowTransition.setDuration(LayoutTransition.CHANGE_DISAPPEARING,
                    APPEND_ROW_ANIMATION_DURATION_MS);
            rowTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
                    new PathInterpolator(0.2f, 0f, 0f, 1f));
            rows.setLayoutTransition(rowTransition);
            scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                    loadVisibleMetadata());
            scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            LinearLayout footer = new LinearLayout(context);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            View spacer = new View(context);
            footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            closeAllButton = new TextView(context);
            closeAllButton.setText("全部关闭");
            closeAllButton.setTextSize(11);
            closeAllButton.setTextColor(accentColor());
            closeAllButton.setGravity(Gravity.CENTER);
            closeAllButton.setPadding(dp(8), 0, dp(8), 0);
            closeAllButton.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)),
                    background(paletteColor(0x10287a78, 0x2077c6c1), dp(8), Color.TRANSPARENT),
                    null));
            footer.addView(closeAllButton, new LinearLayout.LayoutParams(-2, dp(27)));
            setButtonFeedback(closeAllButton);
            closeAllButton.setOnClickListener(view -> closeAllTasks());
            panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(27)));

            int availableHeight = context.getResources().getDisplayMetrics().heightPixels;
            int rowsHeight = Math.min(dp(ROW_HEIGHT_DP * 9), Math.max(dp(ROW_HEIGHT_DP),
                    (int) (availableHeight / 3f) - dp(33)));
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, rowsHeight));
        }

        void show(boolean fromLeft, float anchorY) {
            this.fromLeft = fromLeft;
            this.anchorY = anchorY;
            updatePanelLayout();
            updateDarkMode(context.getResources().getConfiguration());
            synchronized (RecentTaskSwitcher.class) {
                if (active != null && active != this) active.close();
                active = this;
            }
            if (attached) {
                closing = false;
                panel.animate().cancel();
                alignToGestureAnchor();
                animateIn();
                loadTasksAsync();
                return;
            }
            WindowManager.LayoutParams params = buildLayoutParams();
            Throwable lastError = null;
            for (int type : WINDOW_TYPES) {
                params.type = type;
                try {
                    windowManager.addView(root, params);
                    attached = true;
                    closing = false;
                    context.getContentResolver().registerContentObserver(
                            Settings.System.getUriFor(FlymeRecentLockState.SETTING), false, lockObserver);
                    context.registerReceiver(configurationReceiver,
                            new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
                    root.requestFocus();
                    panel.post(() -> {
                        alignToGestureAnchor();
                        animateIn();
                        panel.postOnAnimation(panelBlur::start);
                    });
                    loadTasksAsync();
                    return;
                } catch (Throwable error) {
                    lastError = error;
                    try { windowManager.removeViewImmediate(root); } catch (Throwable ignoredAgain) { }
                }
            }
            if (lastError != null) Log.w(TAG, "Cannot attach recent task switcher popup", lastError);
            synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
        }

        private void updatePanelLayout() {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int panelWidth = Math.min(dp(160), Math.max(dp(147), screenWidth - dp(22)));
            FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) panel.getLayoutParams();
            panelParams.width = panelWidth;
            panelParams.gravity = Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT);
            panel.setLayoutParams(panelParams);

            int availableHeight = context.getResources().getDisplayMetrics().heightPixels;
            int rowsHeight = Math.min(dp(ROW_HEIGHT_DP * 9), Math.max(dp(ROW_HEIGHT_DP),
                    (int) (availableHeight / 3f) - dp(33)));
            LinearLayout.LayoutParams scrollParams = (LinearLayout.LayoutParams) scroll.getLayoutParams();
            scrollParams.height = rowsHeight;
            scroll.setLayoutParams(scrollParams);
        }

        private void alignToGestureAnchor() {
            if (!attached || root.getHeight() <= 0 || panel.getHeight() <= 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.gravity = Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT);
            int maxTop = Math.max(0, root.getHeight() - panel.getHeight());
            params.topMargin = Math.max(0, Math.min(maxTop,
                    Math.round(anchorY - panel.getHeight() / 2f)));
            panel.setLayoutParams(params);
        }

        private void animateIn() {
            if (!attached) return;
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.setScaleX(0.08f);
            panel.setScaleY(0.28f);
            panel.setAlpha(0.9f);
            panel.setTranslationX(fromLeft ? -dp(14) : dp(14));
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(320L).setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f)).start();
        }

        private WindowManager.LayoutParams buildLayoutParams() {
            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WINDOW_TYPES[0], flags, PixelFormat.TRANSLUCENT);
            params.token = new Binder();
            params.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
            params.setTitle("FlymeStatusBarSizer:RecentTaskSwitcher");
            params.packageName = context.getPackageName();
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            try { WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params); }
            catch (Throwable ignored) { }
            return params;
        }

        private void loadTasksAsync() {
            int loadId = ++taskLoadId;
            loading = true;
            secondaryLoading = false;
            rowAnimationStartUptime = 0L;
            nextRowAnimationStartUptime = 0L;
            nextRowAnimationIndex = 0;
            entries.clear();
            renderRows();
            WORKER.execute(() -> {
                List<RecentTaskCard> cards = new ArrayList<>();
                try { cards.addAll(getTaskAccess(context).currentUserRecentTasks()); }
                catch (Exception ignored) { }
                MAIN.post(() -> showFirstBatch(cards, loadId));
            });
        }

        private static OneStepTaskAccess getTaskAccess(Context context) throws ReflectiveOperationException {
            if (taskAccess == null) taskAccess = new OneStepTaskAccess(context);
            return taskAccess;
        }

        private static AppMetadata appMetadata(Context context, RecentTaskCard card) {
            String key = card.component.flattenToShortString();
            synchronized (APP_METADATA) {
                AppMetadata cached = APP_METADATA.get(key);
                if (cached != null) return cached;
            }
            String label = card.description;
            Drawable icon = null;
            android.content.pm.PackageManager pm = context.getPackageManager();
            try {
                CharSequence activityLabel = pm.getActivityInfo(card.component, 0).loadLabel(pm);
                if (activityLabel != null && activityLabel.length() > 0) label = activityLabel.toString();
            } catch (Exception ignored) { }
            if (label.equals(card.component.getPackageName())) {
                try {
                    CharSequence appLabel = pm.getApplicationLabel(pm.getApplicationInfo(
                            card.component.getPackageName(), 0));
                    if (appLabel != null && appLabel.length() > 0) label = appLabel.toString();
                } catch (Exception ignored) { }
            }
            if (label.equals(card.component.getPackageName())) label = "未命名应用";
            try { icon = pm.getActivityIcon(card.component); }
            catch (Exception ignored) {
                try { icon = pm.getApplicationIcon(card.component.getPackageName()); }
                catch (Exception ignoredAgain) { icon = context.getDrawable(android.R.drawable.sym_def_app_icon); }
            }
            AppMetadata metadata = new AppMetadata(label, icon);
            synchronized (APP_METADATA) { APP_METADATA.put(key, metadata); }
            return metadata;
        }

        private boolean isCurrent() {
            synchronized (RecentTaskSwitcher.class) {
                return active == this && attached && !closing;
            }
        }

        private void renderRows() {
            renderRows(false);
        }

        private void renderRows(boolean animateRows) {
            rows.removeAllViews();
            loadingRow = null;
            int count = 0;
            for (TaskEntry entry : entries) {
                if (count > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                View taskRow = taskRow(entry);
                rows.addView(taskRow, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                if (animateRows) {
                    animateTaskRow(taskRow, nextRowAnimationIndex++);
                }
                count++;
            }
            if (count == 0 && !loading) {
                TextView empty = new TextView(context);
                empty.setText("没有最近任务");
                empty.setTextColor(paletteColor(0xff777777, 0xffb8bec3));
                empty.setTextSize(14);
                empty.setGravity(Gravity.CENTER);
                rows.addView(empty, new LinearLayout.LayoutParams(-1, -1));
            }
            if (loading) addLoadingRow();
        }

        private void loadVisibleMetadata() {
            if (!isCurrent() || loading || entries.isEmpty()) return;
            int height = scroll.getHeight();
            if (height <= 0) {
                scroll.post(this::loadVisibleMetadata);
                return;
            }
            int rowHeight = dp(ROW_HEIGHT_DP) + 1;
            int first = Math.max(0, scroll.getScrollY() / rowHeight - 1);
            int last = Math.min(entries.size(),
                    (scroll.getScrollY() + height) / rowHeight + 2);
            List<TaskEntry> pending = new ArrayList<>();
            for (int i = first; i < last; i++) {
                TaskEntry entry = entries.get(i);
                if (!entry.metadataLoaded && !entry.metadataLoading) {
                    entry.metadataLoading = true;
                    pending.add(entry);
                }
            }
            if (pending.isEmpty()) return;
            WORKER.execute(() -> {
                List<AppMetadata> metadata = new ArrayList<>(pending.size());
                for (TaskEntry entry : pending) {
                    if (!isCurrent()) return;
                    metadata.add(appMetadata(context, entry.card));
                }
                MAIN.post(() -> {
                    if (!isCurrent()) return;
                    for (int i = 0; i < pending.size(); i++) {
                        TaskEntry entry = pending.get(i);
                        AppMetadata loaded = metadata.get(i);
                        entry.metadataLoading = false;
                        entry.metadataLoaded = true;
                        entry.label = loaded.label;
                        entry.icon = loaded.icon;
                        if (entry.nameView != null) entry.nameView.setText(entry.label);
                        if (entry.iconView != null && entry.icon != null) {
                            entry.iconView.setImageDrawable(entry.icon);
                        }
                    }
                });
            });
        }

        private void addLoadingRow() {
            FrameLayout row = new FrameLayout(context);
            ProgressBar spinner = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(accentColor()));
            row.addView(spinner, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            rows.addView(row, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
            loadingRow = row;
        }

        private View taskRow(TaskEntry entry) {
            RecentTaskCard card = entry.card;
            String label = entry.label;
            boolean locked = isLocked(card);
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x22287a78, 0x2877c6c1)), null, null));
            TextView name = new TextView(context);
            name.setText(label);
            name.setTextColor(paletteColor(0xff222222, 0xfff1f3f4));
            name.setTextSize(12);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            if (!fromLeft) name.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            name.setPadding(dp(6), 0, dp(4), 0);
            name.setTooltipText("长按锁定或解锁");
            int textHeight = name.getLineHeight();

            ImageButton actionButton = new ImageButton(context);
            actionButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            actionButton.setPadding(dp(5), dp(5), dp(5), dp(5));
            actionButton.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)), null, null));
            setActionIcon(actionButton, locked);
            setButtonFeedback(actionButton);

            ImageView icon = new ImageView(context);
            if (entry.icon != null) icon.setImageDrawable(entry.icon);
            else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            entry.row = row;
            entry.nameView = name;
            entry.iconView = icon;
            int iconSize = dp(16);
            FrameLayout actionSlot = new FrameLayout(context);
            actionSlot.addView(actionButton, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
            ImageButton miniWindow = new ImageButton(context);
            miniWindow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            miniWindow.setPadding(dp(5), dp(5), dp(5), dp(5));
            miniWindow.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x18000000, 0x20ffffff)), null, null));
            miniWindow.setContentDescription("以小窗打开" + label);
            miniWindow.setTooltipText("以小窗打开");
            setModuleIcon(miniWindow, R.drawable.ic_task_window, android.R.drawable.ic_menu_view);
            setButtonFeedback(miniWindow);
            name.setOnLongClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                toggleLock(entry, actionButton);
                return true;
            });
            actionButton.setOnClickListener(view -> {
                if (!isLocked(card)) removeTask(card, row);
            });
            miniWindow.setOnClickListener(view -> openMiniWindow(card, miniWindow));
            if (fromLeft) {
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(actionSlot, new LinearLayout.LayoutParams(dp(26), dp(26)));
            } else {
                row.addView(actionSlot, new LinearLayout.LayoutParams(dp(26), dp(26)));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
            }
            row.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                activate(card);
            });
            name.setOnClickListener(view -> row.performClick());
            return row;
        }

        private void setButtonFeedback(View button) {
            button.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().cancel();
                        view.setScaleX(0.88f);
                        view.setScaleY(0.88f);
                        HapticFeedbackUtils.perform(context, view, HapticFeedbackConstants.CONTEXT_CLICK);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                        break;
                    default:
                        break;
                }
                return false;
            });
        }

        private void setModuleIcon(ImageView view, int resource, int fallback) {
            Drawable.ConstantState iconState = moduleIconStates.get(resource);
            if (iconState == null) {
                try {
                    Context module = context.createPackageContext(
                            com.example.flymestatusbarsizer.BuildConfig.APPLICATION_ID, 0);
                    Drawable icon = module.getDrawable(resource);
                    iconState = icon != null ? icon.getConstantState() : null;
                    if (iconState != null) moduleIconStates.put(resource, iconState);
                } catch (Exception ignored) { }
            }
            if (iconState != null) {
                view.setImageDrawable(iconState.newDrawable().mutate());
            } else {
                view.setImageResource(fallback);
                view.setColorFilter(accentColor());
            }
        }

        private int paletteColor(int light, int dark) {
            return darkMode ? dark : light;
        }

        private Drawable panelBackground() {
            int alpha = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0x98 : 0xf5;
            return background(
                    paletteColor(0xffffff | (alpha << 24), 0x2a2c2f | (alpha << 24)),
                    dp(12),
                    paletteColor(0x24000000, 0x38ffffff));
        }

        private void updateDarkMode(Configuration configuration) {
            boolean nextDarkMode = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            if (darkMode == nextDarkMode) return;
            darkMode = nextDarkMode;
            panel.setBackground(panelBackground());
            closeAllButton.setTextColor(accentColor());
            closeAllButton.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)),
                    background(paletteColor(0x10287a78, 0x2077c6c1), dp(8), Color.TRANSPARENT),
                    null));
            renderRows();
        }

        private int accentColor() {
            return paletteColor(0xff287a78, 0xff77c6c1);
        }

        private boolean isLocked(RecentTaskCard card) {
            return lockedApps.contains(FlymeRecentLockState.key(card));
        }

        private void toggleLock(TaskEntry entry, ImageButton actionButton) {
            if (closingAll || pendingTaskRemoval != null) return;
            String taskKey = FlymeRecentLockState.key(entry.card);
            boolean locked;
            if (lockedApps.remove(taskKey)) {
                locked = false;
            } else {
                lockedApps.add(taskKey);
                locked = true;
            }
            setActionIcon(actionButton, locked);
            FlymeRecentLockState.write(context, lockedApps);
        }

        private void setActionIcon(ImageButton button, boolean locked) {
            int resource;
            if (locked) {
                resource = R.drawable.ic_task_lock;
                button.setContentDescription("已锁定应用");
                button.setTooltipText("已锁定");
            } else {
                resource = R.drawable.ic_task_close;
                button.setContentDescription("关闭任务");
                button.setTooltipText("关闭任务");
            }
            setModuleIcon(button, resource, android.R.drawable.ic_menu_close_clear_cancel);
        }

        private void openMiniWindow(RecentTaskCard card, ImageButton button) {
            if (pendingTaskRemoval != null) return;
            if (closingAll) return;
            button.setEnabled(false);
            WORKER.execute(() -> {
                try {
                    getTaskAccess(context).openMiniWindow(card);
                    MAIN.post(() -> { if (isCurrent()) close(); });
                } catch (Exception ignored) {
                    MAIN.post(() -> {
                        if (!isCurrent()) return;
                        button.setEnabled(true);
                        Toast.makeText(context, "无法打开小窗", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }

        private void activate(RecentTaskCard card) {
            if (closingAll || pendingTaskRemoval != null) return;
            close();
            WORKER.execute(() -> {
                try { getTaskAccess(context).activate(card); }
                catch (Exception ignored) { }
            });
        }

        private void removeTask(RecentTaskCard card, View row) {
            if (closingAll || pendingTaskRemoval != null || isLocked(card)) return;
            TaskEntry target = null;
            int entryIndex = -1;
            for (int i = 0; i < entries.size(); i++) {
                TaskEntry entry = entries.get(i);
                if (entry.card.sameTask(card) && entry.row == row) {
                    target = entry;
                    entryIndex = i;
                    break;
                }
            }
            if (target == null) {
                loadTasksAsync();
                return;
            }
            PendingTaskRemoval pending = new PendingTaskRemoval(target, row, entryIndex);
            pendingTaskRemoval = pending;
            row.setEnabled(false);
            row.animate().alpha(0f).translationX(fromLeft ? -dp(6) : dp(6))
                    .setDuration(140L)
                    .withEndAction(() -> {
                        if (!isCurrent() || pendingTaskRemoval != pending) return;
                        pending.rowDetached = detachTaskRow(row);
                        if (!pending.rowDetached) pending.reload = true;
                        if (pending.resultReady) finishTaskRemoval(pending);
                    })
                    .start();
            WORKER.execute(() -> {
                boolean failed;
                boolean needsReload = false;
                try {
                    getTaskAccess(context).remove(card);
                    failed = false;
                } catch (Exception error) {
                    Log.w(TAG, "Cannot close task=" + card.taskId + " package=" + card.lockPackageName, error);
                    failed = true;
                }
                if (!failed) {
                    try {
                        needsReload = taskStillPresent(card);
                    } catch (Exception error) {
                        Log.w(TAG, "Cannot verify closed task=" + card.taskId, error);
                        needsReload = true;
                    }
                }
                boolean taskFailed = failed;
                boolean reload = needsReload;
                MAIN.post(() -> {
                    if (!isCurrent() || pendingTaskRemoval != pending) return;
                    pending.failed = taskFailed;
                    pending.reload = reload;
                    pending.resultReady = true;
                    if (pending.failed || pending.reload || pending.rowDetached) {
                        finishTaskRemoval(pending);
                    }
                });
            });
        }

        private void finishTaskRemoval(PendingTaskRemoval pending) {
            if (pendingTaskRemoval != pending) return;
            if (pending.failed) {
                restoreTaskRow(pending);
                Toast.makeText(context, "关闭任务失败", Toast.LENGTH_SHORT).show();
                return;
            }
            entries.remove(pending.entry);
            pendingTaskRemoval = null;
            if (pending.reload) {
                loadTasksAsync();
            } else if (entries.isEmpty()) {
                loading = false;
                renderRows();
            }
        }

        private boolean detachTaskRow(View row) {
            int index = rows.indexOfChild(row);
            if (index < 0) return false;
            rows.removeViewAt(index);
            View next = index < rows.getChildCount() ? rows.getChildAt(index) : null;
            if (next != null && next != loadingRow && !(next instanceof LinearLayout)) {
                rows.removeViewAt(index);
            } else if (index > 0) {
                View previous = rows.getChildAt(index - 1);
                if (previous != loadingRow && !(previous instanceof LinearLayout)) {
                    rows.removeViewAt(index - 1);
                }
            }
            return true;
        }

        private void restoreTaskRow(PendingTaskRemoval pending) {
            View row = pending.row;
            row.animate().cancel();
            if (pending.rowDetached) {
                View nextRow = null;
                View previousRow = null;
                for (int i = pending.entryIndex + 1; i < entries.size(); i++) {
                    View candidate = entries.get(i).row;
                    if (candidate != null && candidate.getParent() == rows) {
                        nextRow = candidate;
                        break;
                    }
                }
                for (int i = pending.entryIndex - 1; i >= 0; i--) {
                    View candidate = entries.get(i).row;
                    if (candidate != null && candidate.getParent() == rows) {
                        previousRow = candidate;
                        break;
                    }
                }
                if (nextRow != null) {
                    int insertAt = rows.indexOfChild(nextRow);
                    rows.addView(row, insertAt++);
                    rows.addView(createDivider(), insertAt);
                } else if (previousRow != null) {
                    int insertAt = rows.indexOfChild(previousRow) + 1;
                    rows.addView(createDivider(), insertAt++);
                    rows.addView(row, insertAt);
                } else {
                    rows.addView(row, loadingRow == null ? rows.getChildCount()
                            : rows.indexOfChild(loadingRow));
                }
                pending.entry.row = row;
            }
            row.setEnabled(true);
            pendingTaskRemoval = null;
            row.setAlpha(0f);
            row.setTranslationX(fromLeft ? -dp(6) : dp(6));
            row.animate().alpha(1f).translationX(0f).setDuration(120L).start();
        }

        private View createDivider() {
            View divider = new View(context);
            divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
            return divider;
        }

        private boolean taskStillPresent(RecentTaskCard removed) throws ReflectiveOperationException {
            OneStepTaskAccess access = getTaskAccess(context);
            List<RecentTaskCard> recent = access.currentUserRecentTasks();
            for (RecentTaskCard card : recent) {
                if (card.sameTask(removed)) return true;
            }
            for (RecentTaskCard card : access.secondaryTasks(recent)) {
                if (card.sameTask(removed)) return true;
            }
            return false;
        }

        private void closeAllTasks() {
            if (closingAll || pendingTaskRemoval != null) return;
            closingAll = true;
            closeAllButton.setEnabled(false);
            WORKER.execute(() -> {
                boolean failed;
                try {
                    TaskProcessStopper.clearAll(context);
                    failed = false;
                } catch (Exception error) {
                    Log.w(TAG, "Cannot clear tasks through Launcher", error);
                    failed = true;
                }
                boolean cleanupFailed = failed;
                MAIN.post(() -> {
                    if (!isCurrent()) return;
                    closingAll = false;
                    closeAllButton.setEnabled(true);
                    loadTasksAsync();
                    if (cleanupFailed) {
                        Toast.makeText(context, "全部任务关闭失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private void showFirstBatch(List<RecentTaskCard> loaded, int loadId) {
            if (!isCurrent() || loadId != taskLoadId) return;
            if (loaded.isEmpty()) {
                loading = false;
                renderRows();
                loadSecondaryTasks(loadId);
                return;
            }
            int initialCount = Math.min(INITIAL_TASK_COUNT, loaded.size());
            if (initialCount == 0) {
                loadRemainingTaskBatches(loaded, 0, loadId, () -> loadSecondaryTasks(loadId));
                return;
            }
            loadTaskBatch(loaded, 0, initialCount, loadId, () -> {
                if (initialCount >= loaded.size()) {
                    loadSecondaryTasks(loadId);
                    return;
                }
                MAIN.postDelayed(() -> loadRemainingTaskBatches(
                        loaded, initialCount, loadId, () -> loadSecondaryTasks(loadId)),
                        DEFERRED_TASK_BATCH_DELAY_MS);
            });
        }

        private void loadRemainingTaskBatches(List<RecentTaskCard> loaded, int start, int loadId,
                Runnable after) {
            if (!isCurrent() || loadId != taskLoadId) return;
            int end = Math.min(loaded.size(), start + APPEND_TASK_BATCH_SIZE);
            loadTaskBatch(loaded, start, end, loadId, () -> {
                if (end >= loaded.size()) {
                    if (after != null) after.run();
                    return;
                }
                MAIN.postDelayed(() -> loadRemainingTaskBatches(loaded, end, loadId, after),
                        APPEND_TASK_BATCH_DELAY_MS);
            });
        }

        private void loadSecondaryTasks(int loadId) {
            if (secondaryLoading || !isCurrent() || loadId != taskLoadId) return;
            secondaryLoading = true;
            List<RecentTaskCard> existing = new ArrayList<>();
            for (TaskEntry entry : entries) existing.add(entry.card);
            WORKER.execute(() -> {
                List<RecentTaskCard> loaded = new ArrayList<>();
                try {
                    loaded.addAll(getTaskAccess(context).secondaryTasks(existing));
                } catch (Exception ignored) { }
                MAIN.post(() -> {
                    if (!isCurrent() || loadId != taskLoadId) return;
                    secondaryLoading = false;
                    if (loaded.isEmpty()) {
                        loadVisibleMetadata();
                        return;
                    }
                    loadRemainingTaskBatches(loaded, 0, loadId, this::loadVisibleMetadata);
                });
            });
        }

        private void loadTaskBatch(List<RecentTaskCard> loaded, int start, int end, int loadId,
                Runnable after) {
            boolean firstBatch = entries.isEmpty();
            WORKER.execute(() -> {
                if (!isCurrent()) return;
                List<AppMetadata> metadata = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    if (!isCurrent()) return;
                    metadata.add(appMetadata(context, loaded.get(i)));
                }
                MAIN.post(() -> {
                    if (!isCurrent() || loadId != taskLoadId) return;
                    List<TaskEntry> addedEntries = new ArrayList<>(end - start);
                    for (int i = start; i < end; i++) {
                        TaskEntry entry = new TaskEntry(loaded.get(i));
                        applyMetadata(entry, metadata.get(i - start));
                        entries.add(entry);
                        addedEntries.add(entry);
                    }
                    loading = false;
                    if (firstBatch) {
                        rowAnimationStartUptime = SystemClock.uptimeMillis()
                                + INITIAL_ROW_ANIMATION_DELAY_MS;
                        renderRows(true);
                    } else {
                        appendTaskRows(addedEntries);
                    }
                    if (after != null) after.run();
                });
            });
        }

        private void appendTaskRows(List<TaskEntry> addedEntries) {
            if (loadingRow != null) {
                rows.removeView(loadingRow);
                loadingRow = null;
            }
            for (int i = 0; i < addedEntries.size(); i++) {
                TaskEntry entry = addedEntries.get(i);
                if (rows.getChildCount() > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                View row = taskRow(entry);
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                animateTaskRow(row, nextRowAnimationIndex++);
            }
        }

        private void animateTaskRow(View row, int index) {
            row.setAlpha(0f);
            row.setTranslationX(fromLeft ? -dp(6) : dp(6));
            long now = SystemClock.uptimeMillis();
            long startAt = Math.max(now, Math.max(rowAnimationStartUptime
                    + index * APPEND_ROW_ANIMATION_STAGGER_MS, nextRowAnimationStartUptime));
            nextRowAnimationStartUptime = startAt + APPEND_ROW_ANIMATION_STAGGER_MS;
            long delay = startAt - now;
            row.animate().alpha(1f).translationX(0f)
                    .setStartDelay(delay)
                    .setDuration(APPEND_ROW_ANIMATION_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        }

        private void applyMetadata(TaskEntry entry, AppMetadata metadata) {
            entry.metadataLoading = false;
            entry.metadataLoaded = true;
            entry.label = metadata.label;
            entry.icon = metadata.icon;
        }

        private void close() {
            if (!attached || closing) return;
            closing = true;
            panel.animate().cancel();
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.animate().scaleX(0.08f).scaleY(0.28f).alpha(0.9f)
                    .translationX(fromLeft ? -dp(14) : dp(14)).setDuration(240L)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f))
                    .withEndAction(this::finishClose).start();
        }

        private void closeImmediately() {
            if (!attached) {
                synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
                return;
            }
            closing = true;
            panel.animate().cancel();
            finishClose();
        }

        private void finishClose() {
            if (!attached) {
                closing = false;
                synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
                return;
            }
            panelBlur.stop();
            context.getContentResolver().unregisterContentObserver(lockObserver);
            context.unregisterReceiver(configurationReceiver);
            if (attached) {
                try { windowManager.removeViewImmediate(root); }
                catch (Throwable ignored) { }
                attached = false;
            }
            closing = false;
            synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
        }

        private GradientDrawable background(int color, float radius, int stroke) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(radius);
            if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
            return drawable;
        }

        private int dp(float value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
