package com.example.flymestatusbarsizer.feature.taskswitcher;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.feature.assistant.AssistantPanelBlur;
import com.example.flymestatusbarsizer.feature.assistant.AppLauncherPanel;
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
        RecentTaskCard card;
        String label;
        Drawable icon;
        boolean metadataLoaded;
        boolean metadataLoading;
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
            private Popup popup;

            @Override public void prepare() {
                if (popup == null) popup = new Popup(context);
                popup.prepare();
            }

            @Override public void release() {
                if (popup != null) popup.release();
                popup = null;
            }

            @Override public boolean isReady() {
                return context != null && context.getSystemService(Context.WINDOW_SERVICE) != null;
            }

            @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                execute(fromLeft, 0f, onSuccess);
            }

            @Override public void execute(boolean fromLeft, float anchorY, Runnable onSuccess) {
                if (isReady()) {
                    if (popup == null) popup = new Popup(context);
                    popup.show(fromLeft, anchorY);
                    if (onSuccess != null) onSuccess.run();
                }
            }
        };
    }

    private static final class Popup {
        private static final int ROW_HEIGHT_DP = 34;
        private static final int TASK_SLOT_DP = 26;
        private static final int TASK_CONTENT_HEIGHT_DP = 29;
        private static final int TASK_ICON_DP = 16;
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
        private final RecyclerView rows;
        private final RecyclerView.ItemAnimator rowAnimator;
        private final FrameLayout listContainer;
        private final ProgressBar loadingView;
        private final TextView emptyView;
        private final TextView closeAllButton;
        private boolean attached;
        private boolean visible;
        private WindowManager.LayoutParams windowParams;
        private boolean closing;
        private boolean closingAll;
        private PendingTaskRemoval pendingTaskRemoval;
        private boolean loading;
        private boolean hasSnapshot;
        private boolean rowsDirty;
        private List<RecentTaskCard> pendingCards;
        private int taskLoadId;
        private final Map<Integer, Drawable.ConstantState> moduleIconStates = new HashMap<>();
        private final TaskAdapter adapter = new TaskAdapter();

        Popup(Context context) {
            this.context = context;
            FlymeRecentLockState.migrateLegacy(context);
            lockedApps = FlymeRecentLockState.read(context);
            lockObserver = new ContentObserver(MAIN) {
                @Override public void onChange(boolean selfChange) {
                    lockedApps.clear();
                    lockedApps.addAll(FlymeRecentLockState.read(Popup.this.context));
                    if (isCurrent()) renderRows();
                    else rowsDirty = true;
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
            root.setVisibility(View.GONE);
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
            panel.setVisibility(View.INVISIBLE);
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

            rows = new RecyclerView(context);
            rows.setLayoutManager(new LinearLayoutManager(context));
            rows.setAdapter(adapter);
            rowAnimator = rows.getItemAnimator();
            rowAnimator.setMoveDuration(120L);
            rows.setItemAnimator(null);
            rows.setHasFixedSize(true);
            rows.setItemViewCacheSize(4);
            rows.setVerticalScrollBarEnabled(false);
            rows.setOverScrollMode(View.OVER_SCROLL_NEVER);
            rows.addItemDecoration(new RecyclerView.ItemDecoration() {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
                    paint.setColor(paletteColor(0x18000000, 0x28ffffff));
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        View child = parent.getChildAt(i);
                        if (parent.getChildAdapterPosition(child) > 0)
                            canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(),
                                    child.getTop() + Math.max(1, dp(1)), paint);
                    }
                }
            });
            listContainer = new FrameLayout(context);
            listContainer.addView(rows, new FrameLayout.LayoutParams(-1, -1));
            loadingView = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
            loadingView.setIndeterminateTintList(ColorStateList.valueOf(accentColor()));
            listContainer.addView(loadingView,
                    new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            emptyView = new TextView(context);
            emptyView.setText("没有最近任务");
            emptyView.setTextColor(paletteColor(0xff777777, 0xffb8bec3));
            emptyView.setTextSize(14);
            emptyView.setGravity(Gravity.CENTER);
            listContainer.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
            panel.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

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
            listContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, rowsHeight));
            updateListState();
        }

        void prepare() {
            if (attached) return;
            updatePanelLayout();
            if (attachWindow()) loadTasksAsync();
        }

        private boolean attachWindow() {
            if (attached) return true;
            windowParams = buildLayoutParams();
            windowParams.alpha = 0f;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            Throwable lastError = null;
            for (int type : WINDOW_TYPES) {
                windowParams.type = type;
                try {
                    windowManager.addView(root, windowParams);
                    attached = true;
                    context.getContentResolver().registerContentObserver(
                            Settings.System.getUriFor(FlymeRecentLockState.SETTING), false, lockObserver);
                    context.registerReceiver(configurationReceiver,
                            new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
                    return true;
                } catch (Throwable error) {
                    lastError = error;
                    try { windowManager.removeViewImmediate(root); } catch (Throwable ignoredAgain) { }
                }
            }
            if (lastError != null) Log.w(TAG, "Cannot attach recent task switcher popup", lastError);
            return false;
        }

        void show(boolean fromLeft, float anchorY) {
            AppLauncherPanel.dismissActivePopup();
            boolean sideChanged = this.fromLeft != fromLeft;
            this.fromLeft = fromLeft;
            this.anchorY = anchorY;
            if (!attachWindow()) return;
            synchronized (RecentTaskSwitcher.class) {
                if (active != null && active != this) active.closeImmediately();
                active = this;
            }
            panel.animate().withEndAction(null).cancel();
            closing = false;
            visible = true;
            windowParams.alpha = 1f;
            windowParams.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            windowManager.updateViewLayout(root, windowParams);
            updatePanelLayout();
            updateDarkMode(context.getResources().getConfiguration());
            applyPendingTasks();
            if (sideChanged || rowsDirty) renderRows();
            rows.stopScroll();
            ((LinearLayoutManager) rows.getLayoutManager()).scrollToPositionWithOffset(0, 0);
            rows.setItemAnimator(rowAnimator);
            closeAllButton.setEnabled(!closingAll);
            panel.setVisibility(View.VISIBLE);
            root.setVisibility(View.VISIBLE);
            root.requestFocus();
            panel.removeCallbacks(showAnimation);
            panel.post(showAnimation);
            loadTasksAsync();
            updateListState();
        }

        private final Runnable showAnimation = () -> {
            if (!isCurrent()) return;
            alignToGestureAnchor();
            animateIn();
        };

        private void prepareLayout() {
            if (visible) return;
            int width = context.getResources().getDisplayMetrics().widthPixels;
            int height = context.getResources().getDisplayMetrics().heightPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
            rowAnimator.endAnimations();
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
            LinearLayout.LayoutParams scrollParams = (LinearLayout.LayoutParams) listContainer.getLayoutParams();
            scrollParams.height = rowsHeight;
            listContainer.setLayoutParams(scrollParams);
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
            panelBlur.start();
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(320L).setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f))
                    .start();
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
            if (loading || pendingTaskRemoval != null || closingAll) return;
            int loadId = ++taskLoadId;
            loading = true;
            if (visible) updateListState();
            WORKER.execute(() -> {
                List<RecentTaskCard> cards;
                try {
                    OneStepTaskAccess access = getTaskAccess(context);
                    cards = access.currentUserRecentTasks();
                    cards.addAll(access.secondaryTasks(cards));
                    for (int i = 0; i < Math.min(9, cards.size()); i++)
                        appMetadata(context, cards.get(i));
                } catch (Exception error) {
                    MAIN.post(() -> {
                        if (loadId != taskLoadId) return;
                        loading = false;
                        if (visible) updateListState();
                    });
                    return;
                }
                MAIN.post(() -> {
                    if (!attached || loadId != taskLoadId) return;
                    loading = false;
                    pendingCards = cards;
                    if (pendingTaskRemoval != null || closingAll) return;
                    if (isCurrent() || !hasSnapshot) {
                        applyPendingTasks();
                        if (visible) updateListState();
                        else prepareLayout();
                    }
                });
            });
        }

        private void applyPendingTasks() {
            if (pendingCards == null || pendingTaskRemoval != null || closingAll) return;
            List<TaskEntry> previous = new ArrayList<>(entries);
            List<TaskEntry> next = new ArrayList<>(pendingCards.size());
            for (RecentTaskCard card : pendingCards) {
                TaskEntry entry = null;
                for (TaskEntry old : previous) {
                    if (old.card.sameTask(card) && old.card.component.equals(card.component)
                            && old.card.description.equals(card.description)
                            && old.card.lockPackageName.equals(card.lockPackageName)) {
                        entry = old;
                        entry.card = card;
                        break;
                    }
                }
                if (entry == null) {
                    entry = new TaskEntry(card);
                    AppMetadata metadata;
                    synchronized (APP_METADATA) {
                        metadata = APP_METADATA.get(card.userId + ":"
                                + card.component.flattenToShortString());
                    }
                    if (metadata != null) {
                        entry.label = metadata.label;
                        entry.icon = metadata.icon;
                        entry.metadataLoaded = true;
                    }
                }
                next.add(entry);
            }
            DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldIndex, int newIndex) {
                    RecentTaskCard old = previous.get(oldIndex).card;
                    RecentTaskCard current = next.get(newIndex).card;
                    return old.userId == current.userId && old.taskId == current.taskId;
                }
                @Override public boolean areContentsTheSame(int oldIndex, int newIndex) {
                    return previous.get(oldIndex) == next.get(newIndex);
                }
            });
            entries.clear();
            entries.addAll(next);
            pendingCards = null;
            hasSnapshot = true;
            changes.dispatchUpdatesTo(adapter);
        }

        private static OneStepTaskAccess getTaskAccess(Context context) throws ReflectiveOperationException {
            if (taskAccess == null) taskAccess = new OneStepTaskAccess(context);
            return taskAccess;
        }

        private static AppMetadata appMetadata(Context context, RecentTaskCard card) {
            String key = card.userId + ":" + card.component.flattenToShortString();
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
            if (card.userId == com.example.flymestatusbarsizer.feature.tasks.OneStepRecentTasks.CLONE_USER_ID)
                icon = pm.getUserBadgedIcon(icon,
                        android.os.UserHandle.getUserHandleForUid(card.userId * 100000));
            AppMetadata metadata = new AppMetadata(label, icon);
            synchronized (APP_METADATA) { APP_METADATA.put(key, metadata); }
            return metadata;
        }

        private boolean isCurrent() {
            synchronized (RecentTaskSwitcher.class) {
                return active == this && attached && visible && !closing;
            }
        }

        private void renderRows() {
            rowsDirty = false;
            adapter.notifyDataSetChanged();
            updateListState();
        }

        private void updateListState() {
            loadingView.setVisibility(visible && loading && !hasSnapshot ? View.VISIBLE : View.GONE);
            emptyView.setVisibility((hasSnapshot || !loading) && entries.isEmpty()
                    ? View.VISIBLE : View.GONE);
            rows.setVisibility(entries.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }

        private final class TaskViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout row = new LinearLayout(context);
            final TextView name = new TextView(context);
            final ImageView icon = new ImageView(context);
            final FrameLayout nameSlot = new FrameLayout(context);
            final FrameLayout iconSlot = new FrameLayout(context);
            final ImageButton actionButton = new ImageButton(context);
            final ImageButton miniWindow = new ImageButton(context);
            final FrameLayout actionSlot = new FrameLayout(context);
            final FrameLayout miniWindowSlot = new FrameLayout(context);
            TaskEntry entry;
            boolean arranged;
            boolean arrangedFromLeft;

            TaskViewHolder() {
                super(new LinearLayout(context));
                LinearLayout itemContainer = (LinearLayout) itemView;
                itemContainer.setOrientation(LinearLayout.VERTICAL);
                itemContainer.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));
                row.setMinimumHeight(dp(ROW_HEIGHT_DP));
                row.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x22287a78, 0x2877c6c1)), null, null));
                name.setTextColor(paletteColor(0xff222222, 0xfff1f3f4));
                name.setTextSize(12);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.setMinHeight(0);
                name.setPadding(dp(4), 0, dp(4), 0);
                name.setTooltipText("长按锁定或解锁");
                nameSlot.setMinimumWidth(0);
                nameSlot.setMinimumHeight(0);
                nameSlot.addView(name,
                        new FrameLayout.LayoutParams(-1, dp(TASK_CONTENT_HEIGHT_DP), Gravity.CENTER));
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setMinimumWidth(0);
                icon.setMinimumHeight(0);
                iconSlot.addView(icon,
                        new FrameLayout.LayoutParams(dp(TASK_ICON_DP), dp(TASK_ICON_DP), Gravity.CENTER));
                actionButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                actionButton.setMinimumWidth(0);
                actionButton.setMinimumHeight(0);
                actionButton.setPadding(dp(5), dp(5), dp(5), dp(5));
                actionButton.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)), null, null));
                actionSlot.addView(actionButton,
                        new FrameLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_SLOT_DP), Gravity.CENTER));
                actionSlot.setMinimumWidth(0);
                actionSlot.setMinimumHeight(0);
                iconSlot.setMinimumWidth(0);
                iconSlot.setMinimumHeight(0);
                miniWindow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                miniWindow.setMinimumWidth(0);
                miniWindow.setMinimumHeight(0);
                miniWindow.setPadding(dp(5), dp(5), dp(5), dp(5));
                miniWindow.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x18000000, 0x20ffffff)), null, null));
                miniWindow.setTooltipText("以小窗打开");
                setModuleIcon(miniWindow, R.drawable.ic_task_window, android.R.drawable.ic_menu_view);
                miniWindowSlot.setMinimumWidth(0);
                miniWindowSlot.setMinimumHeight(0);
                miniWindowSlot.addView(miniWindow,
                        new FrameLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_SLOT_DP), Gravity.CENTER));
                setButtonFeedback(actionButton);
                setButtonFeedback(miniWindow);
                name.setOnLongClickListener(view -> {
                    if (entry == null) return true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    toggleLock(entry, actionButton);
                    return true;
                });
                actionButton.setOnClickListener(view -> {
                    if (entry != null && !isLocked(entry.card)) removeTask(entry.card, row);
                });
                miniWindow.setOnClickListener(view -> {
                    if (entry != null) openMiniWindow(entry.card, miniWindow);
                });
                row.setOnClickListener(view -> {
                    if (entry == null) return;
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    activate(entry.card);
                });
                name.setOnClickListener(view -> row.performClick());
                itemContainer.addView(row);
            }
        }

        private final class TaskAdapter extends RecyclerView.Adapter<TaskViewHolder> {
            TaskAdapter() { setHasStableIds(true); }

            @Override public TaskViewHolder onCreateViewHolder(android.view.ViewGroup parent, int type) {
                return new TaskViewHolder();
            }

            @Override public void onBindViewHolder(TaskViewHolder holder, int position) {
                TaskEntry entry = entries.get(position);
                if (!entry.metadataLoaded) {
                    AppMetadata cached;
                    synchronized (APP_METADATA) {
                        cached = APP_METADATA.get(entry.card.userId + ":"
                                + entry.card.component.flattenToShortString());
                    }
                    if (cached != null) {
                        entry.label = cached.label;
                        entry.icon = cached.icon;
                        entry.metadataLoaded = true;
                    }
                }
                holder.entry = entry;
                holder.row.setAlpha(1f);
                holder.row.setTranslationX(0f);
                holder.row.setEnabled(true);
                holder.miniWindow.setEnabled(true);
                holder.name.setText(entry.label);
                holder.row.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x22287a78, 0x2877c6c1)), null, null));
                holder.actionButton.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)), null, null));
                holder.miniWindow.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(paletteColor(0x18000000, 0x20ffffff)), null, null));
                holder.name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                holder.icon.setImageDrawable(entry.icon == null
                        ? context.getDrawable(android.R.drawable.sym_def_app_icon) : entry.icon);
                setActionIcon(holder.actionButton, isLocked(entry.card));
                holder.miniWindow.setContentDescription("以小窗打开" + entry.label);
                if (!holder.arranged || holder.arrangedFromLeft != fromLeft) {
                    holder.arranged = true;
                    holder.arrangedFromLeft = fromLeft;
                    holder.row.removeAllViews();
                    if (fromLeft) {
                        holder.row.addView(holder.miniWindowSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                        holder.row.addView(holder.iconSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                        holder.row.addView(holder.nameSlot,
                                new LinearLayout.LayoutParams(0, dp(TASK_CONTENT_HEIGHT_DP), 1f));
                        holder.row.addView(holder.actionSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                    } else {
                        holder.row.addView(holder.actionSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                        holder.row.addView(holder.iconSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                        holder.row.addView(holder.nameSlot,
                                new LinearLayout.LayoutParams(0, dp(TASK_CONTENT_HEIGHT_DP), 1f));
                        holder.row.addView(holder.miniWindowSlot,
                                new LinearLayout.LayoutParams(dp(TASK_SLOT_DP), dp(TASK_CONTENT_HEIGHT_DP)));
                    }
                }
                requestMetadata(entry);
            }

            @Override public int getItemCount() { return entries.size(); }

            @Override public long getItemId(int position) {
                RecentTaskCard card = entries.get(position).card;
                return (((long) card.userId) << 32) ^ (card.taskId & 0xffffffffL);
            }
        }

        private void requestMetadata(TaskEntry entry) {
            if (!isCurrent() || entry.metadataLoaded || entry.metadataLoading) return;
            entry.metadataLoading = true;
            WORKER.execute(() -> {
                AppMetadata metadata = appMetadata(context, entry.card);
                MAIN.post(() -> {
                    entry.metadataLoading = false;
                    if (!attached || !entries.contains(entry)) return;
                    entry.metadataLoaded = true;
                    entry.label = metadata.label;
                    entry.icon = metadata.icon;
                    int index = entries.indexOf(entry);
                    if (isCurrent() && index >= 0) adapter.notifyItemChanged(index);
                    else rowsDirty = true;
                });
            });
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
            emptyView.setTextColor(paletteColor(0xff777777, 0xffb8bec3));
            loadingView.setIndeterminateTintList(ColorStateList.valueOf(accentColor()));
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
                if (entry.card.sameTask(card)) {
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
            taskLoadId++;
            loading = false;
            pendingCards = null;
            row.setEnabled(false);
            row.animate().alpha(0f).translationX(fromLeft ? -dp(6) : dp(6))
                    .setDuration(140L)
                    .withEndAction(() -> {
                        if (!isCurrent() || pendingTaskRemoval != pending) return;
                        pending.rowDetached = true;
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
                    if (!attached) return;
                    if (!isCurrent() || pendingTaskRemoval != pending) {
                        if (pendingTaskRemoval == pending) pendingTaskRemoval = null;
                        loadTasksAsync();
                        return;
                    }
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
            } else {
                adapter.notifyItemRemoved(pending.entryIndex);
                applyPendingTasks();
                updateListState();
            }
        }

        private void restoreTaskRow(PendingTaskRemoval pending) {
            View row = pending.row;
            row.animate().cancel();
            row.setEnabled(true);
            pendingTaskRemoval = null;
            row.setAlpha(0f);
            row.setTranslationX(fromLeft ? -dp(6) : dp(6));
            row.animate().alpha(1f).translationX(0f).setDuration(120L).start();
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
            taskLoadId++;
            loading = false;
            pendingCards = null;
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
                    closingAll = false;
                    if (!attached) return;
                    if (isCurrent()) closeAllButton.setEnabled(true);
                    loadTasksAsync();
                    if (cleanupFailed && isCurrent()) {
                        Toast.makeText(context, "全部任务关闭失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private void close() {
            if (!visible || closing) return;
            closing = true;
            panel.removeCallbacks(showAnimation);
            panel.animate().withEndAction(null).cancel();
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.animate().scaleX(0.08f).scaleY(0.28f).alpha(0.9f)
                    .translationX(fromLeft ? -dp(14) : dp(14)).setDuration(240L)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 0.6f, 1f))
                    .withEndAction(this::finishClose).start();
        }

        private void closeImmediately() {
            panel.removeCallbacks(showAnimation);
            panel.animate().withEndAction(null).cancel();
            if (attached) finishClose();
        }

        private void finishClose() {
            visible = false;
            closing = false;
            panel.removeCallbacks(showAnimation);
            panel.animate().withEndAction(null).cancel();
            rows.stopScroll();
            root.cancelPendingInputEvents();
            rowAnimator.endAnimations();
            rows.setItemAnimator(null);
            for (int i = 0; i < rows.getChildCount(); i++) {
                TaskViewHolder holder = (TaskViewHolder) rows.getChildViewHolder(rows.getChildAt(i));
                holder.row.animate().withEndAction(null).cancel();
                holder.actionButton.animate().cancel();
                holder.miniWindow.animate().cancel();
                holder.row.setAlpha(1f);
                holder.row.setTranslationX(0f);
                holder.row.setEnabled(true);
                holder.miniWindow.setEnabled(true);
                holder.actionButton.setScaleX(1f);
                holder.actionButton.setScaleY(1f);
                holder.miniWindow.setScaleX(1f);
                holder.miniWindow.setScaleY(1f);
            }
            closeAllButton.animate().cancel();
            closeAllButton.setScaleX(1f);
            closeAllButton.setScaleY(1f);
            pendingTaskRemoval = null;
            loadingView.setVisibility(View.GONE);
            panelBlur.stop();
            panel.setVisibility(View.INVISIBLE);
            root.setVisibility(View.GONE);
            root.clearFocus();
            windowParams.alpha = 0f;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(root, windowParams);
            synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
        }

        private void release() {
            closeImmediately();
            taskLoadId++;
            loading = false;
            panelBlur.stop();
            if (attached) {
                context.getContentResolver().unregisterContentObserver(lockObserver);
                context.unregisterReceiver(configurationReceiver);
                windowManager.removeViewImmediate(root);
                attached = false;
            }
            entries.clear();
            pendingCards = null;
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
