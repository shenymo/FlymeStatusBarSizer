package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
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
import android.util.LruCache;
import android.view.animation.PathInterpolator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact recent-task switcher opened by a configured side gesture. */
public final class RecentTaskSwitcher {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
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

        TaskEntry(RecentTaskCard card) {
            this.card = card;
            label = card.description;
        }
    }

    public static SideGestureActions.Action action(Context context) {
        return new SideGestureActions.Action() {
            @Override public boolean isReady() {
                return context != null && context.getSystemService(Context.WINDOW_SERVICE) != null;
            }

            @Override public void execute(boolean fromLeft, Runnable onSuccess) {
                execute(fromLeft, 0f, onSuccess);
            }

            @Override public void execute(boolean fromLeft, float anchorY, Runnable onSuccess) {
                if (isReady()) {
                    new Popup(context, fromLeft, anchorY).show();
                    if (onSuccess != null) onSuccess.run();
                }
            }
        };
    }

    private static final class Popup {
        private static final int ROW_HEIGHT_DP = 34;
        private final Context context;
        private final boolean fromLeft;
        private final float anchorY;
        private boolean darkMode;
        private final WindowManager windowManager;
        private final List<TaskEntry> entries = new ArrayList<>();
        private final FrameLayout root;
        private final LinearLayout panel;
        private final AssistantPanelBlur panelBlur;
        private final LinearLayout rows;
        private final ScrollView scroll;
        private final TextView closeAllButton;
        private View loadingRow;
        private boolean attached;
        private boolean closing;
        private boolean loading = true;
        private boolean moduleIconLoaded;
        private Drawable.ConstantState moduleIconState;

        Popup(Context context, boolean fromLeft, float anchorY) {
            this.context = context;
            this.fromLeft = fromLeft;
            this.anchorY = anchorY;
            darkMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            root = new FrameLayout(context) {
                @Override
                protected void onConfigurationChanged(Configuration configuration) {
                    super.onConfigurationChanged(configuration);
                    updateDarkMode(configuration);
                }
            };
            root.setBackgroundColor(Color.TRANSPARENT);
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
            footer.addView(closeAllButton, new LinearLayout.LayoutParams(-2, dp(27)));
            closeAllButton.setOnClickListener(view -> closeAllTasks());
            panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(27)));

            int availableHeight = context.getResources().getDisplayMetrics().heightPixels;
            int rowsHeight = Math.min(dp(ROW_HEIGHT_DP * 9), Math.max(dp(ROW_HEIGHT_DP),
                    (int) (availableHeight / 3f) - dp(33)));
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, rowsHeight));
        }

        void show() {
            synchronized (RecentTaskSwitcher.class) {
                if (active != null) active.close();
                active = this;
            }
            WindowManager.LayoutParams params = buildLayoutParams();
            for (int type : WINDOW_TYPES) {
                params.type = type;
                try {
                    windowManager.addView(root, params);
                    attached = true;
                    panelBlur.start();
                    panel.post(() -> {
                        alignToGestureAnchor();
                        animateIn();
                    });
                    loadTasksAsync();
                    return;
                } catch (Throwable ignored) {
                    try { windowManager.removeViewImmediate(root); } catch (Throwable ignoredAgain) { }
                }
            }
            synchronized (RecentTaskSwitcher.class) { if (active == this) active = null; }
        }

        private void alignToGestureAnchor() {
            if (!attached || root.getHeight() <= 0 || panel.getHeight() <= 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            int maxTop = Math.max(0, root.getHeight() - panel.getHeight());
            params.topMargin = Math.max(0, Math.min(maxTop,
                    Math.round(anchorY - panel.getHeight() / 2f)));
            panel.setLayoutParams(params);
        }

        private void animateIn() {
            if (!attached) return;
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.setScaleX(0.04f);
            panel.setScaleY(0.08f);
            panel.setAlpha(0.15f);
            panel.setTranslationX(fromLeft ? -dp(5) : dp(5));
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(360L).setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f)).start();
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
            loading = true;
            renderRows();
            WORKER.execute(() -> {
                List<RecentTaskCard> cards = new ArrayList<>();
                try { cards.addAll(getTaskAccess(context).candidates()); }
                catch (Exception ignored) { }
                MAIN.post(() -> replaceCards(cards));
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
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                try {
                    CharSequence activityLabel = pm.getActivityInfo(card.component, 0).loadLabel(pm);
                    if (activityLabel != null && activityLabel.length() > 0) label = activityLabel.toString();
                } catch (Exception ignored) {
                    try {
                        CharSequence appLabel = pm.getApplicationLabel(pm.getApplicationInfo(
                                card.component.getPackageName(), 0));
                        if (appLabel != null && appLabel.length() > 0) label = appLabel.toString();
                    } catch (Exception ignoredAgain) { }
                }
                try { icon = pm.getActivityIcon(card.component); }
                catch (Exception ignored) {
                    try { icon = pm.getApplicationIcon(card.component.getPackageName()); }
                    catch (Exception ignoredAgain) { icon = context.getDrawable(android.R.drawable.sym_def_app_icon); }
                }
            } catch (Exception ignored) { }
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
            rows.removeAllViews();
            loadingRow = null;
            int count = 0;
            for (TaskEntry entry : entries) {
                if (count > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                rows.addView(taskRow(entry), new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                count++;
            }
            if (count == 0 && !loading) {
                TextView empty = new TextView(context);
                empty.setText("没有最近任务");
                empty.setTextColor(paletteColor(0xff777777, 0xffb8bec3));
                empty.setTextSize(14);
                empty.setGravity(Gravity.CENTER);
                rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
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
            int textHeight = name.getLineHeight();

            TextView remove = new TextView(context);
            remove.setText("×");
            remove.setTextSize(13);
            remove.setTextColor(paletteColor(0xff444444, 0xffd4d8dc));
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("关闭" + label);
            remove.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x243a8885, 0x3877c6c1)), null, null));
            setButtonFeedback(remove);

            ImageView icon = new ImageView(context);
            if (entry.icon != null) icon.setImageDrawable(entry.icon);
            else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            entry.nameView = name;
            entry.iconView = icon;
            int iconSize = dp(16);
            ImageButton miniWindow = new ImageButton(context);
            miniWindow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            miniWindow.setPadding(dp(5), dp(5), dp(5), dp(5));
            miniWindow.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x18000000, 0x20ffffff)), null, null));
            miniWindow.setContentDescription("以小窗打开" + label);
            miniWindow.setTooltipText("以小窗打开");
            setModuleIcon(miniWindow, R.drawable.ic_task_window, android.R.drawable.ic_menu_view);
            setButtonFeedback(miniWindow);
            remove.setOnClickListener(view -> removeTask(card, row));
            miniWindow.setOnClickListener(view -> openMiniWindow(card, miniWindow));
            if (fromLeft) {
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(remove, new LinearLayout.LayoutParams(dp(26), dp(26)));
            } else {
                row.addView(remove, new LinearLayout.LayoutParams(dp(26), dp(26)));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
            }
            row.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                activate(card);
            });
            return row;
        }

        private void setButtonFeedback(View button) {
            button.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().cancel();
                        view.setScaleX(0.88f);
                        view.setScaleY(0.88f);
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
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
            if (!moduleIconLoaded) {
                moduleIconLoaded = true;
                try {
                    Context module = context.createPackageContext(
                            com.example.flymestatusbarsizer.BuildConfig.APPLICATION_ID, 0);
                    Drawable icon = module.getDrawable(resource);
                    moduleIconState = icon != null ? icon.getConstantState() : null;
                } catch (Exception ignored) { }
            }
            if (moduleIconState != null) {
                view.setImageDrawable(moduleIconState.newDrawable().mutate());
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
            if (!attached || darkMode == nextDarkMode) return;
            darkMode = nextDarkMode;
            panel.setBackground(panelBackground());
            closeAllButton.setTextColor(accentColor());
            renderRows();
        }

        private int accentColor() {
            return paletteColor(0xff287a78, 0xff77c6c1);
        }

        private void openMiniWindow(RecentTaskCard card, ImageButton button) {
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
            close();
            WORKER.execute(() -> {
                try { getTaskAccess(context).activate(card); }
                catch (Exception ignored) { }
            });
        }

        private void removeTask(RecentTaskCard card, View row) {
            row.setEnabled(false);
            row.animate().alpha(0f).translationX(fromLeft ? -dp(6) : dp(6))
                    .setDuration(140L).start();
            WORKER.execute(() -> {
                boolean failed;
                try {
                    getTaskAccess(context).remove(card);
                    failed = false;
                } catch (Exception error) {
                    failed = true;
                }
                boolean taskFailed = failed;
                MAIN.post(() -> {
                    if (!isCurrent()) return;
                    if (taskFailed) {
                        row.animate().alpha(1f).translationX(0f).setDuration(120L).start();
                        row.setEnabled(true);
                        Toast.makeText(context, "关闭任务失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    entries.removeIf(entry -> entry.card.sameTask(card));
                    int index = rows.indexOfChild(row);
                    if (index >= 0) {
                        rows.removeViewAt(index);
                        if (index > 0) {
                            rows.removeViewAt(index - 1);
                        } else if (rows.getChildCount() > 0) {
                            rows.removeViewAt(0);
                        }
                    }
                    if (entries.isEmpty()) renderRows();
                });
            });
        }

        private void closeAllTasks() {
            List<RecentTaskCard> removing = new ArrayList<>();
            for (TaskEntry entry : entries) removing.add(entry.card);
            closeAllButton.setEnabled(false);
            for (int i = 0; i < rows.getChildCount(); i += 2) {
                rows.getChildAt(i).animate().alpha(0f).setDuration(140L).start();
            }
            WORKER.execute(() -> {
                List<RecentTaskCard> failedCards = new ArrayList<>();
                OneStepTaskAccess tasks = null;
                try { tasks = getTaskAccess(context); }
                catch (Exception ignored) { }
                for (RecentTaskCard card : removing) {
                    if (tasks == null) {
                        failedCards.add(card);
                        continue;
                    }
                    try {
                        tasks.remove(card);
                    } catch (Exception error) {
                        failedCards.add(card);
                    }
                }
                List<RecentTaskCard> remaining = failedCards;
                MAIN.post(() -> {
                    if (!isCurrent()) return;
                    entries.clear();
                    for (RecentTaskCard card : remaining) entries.add(new TaskEntry(card));
                    loading = false;
                    rows.removeAllViews();
                    renderRows();
                    if (!remaining.isEmpty()) {
                        closeAllButton.setEnabled(true);
                        scroll.post(this::loadVisibleMetadata);
                        Toast.makeText(context, "部分任务关闭失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        private void replaceCards(List<RecentTaskCard> loaded) {
            if (!isCurrent()) return;
            entries.clear();
            for (RecentTaskCard card : loaded) entries.add(new TaskEntry(card));
            loading = false;
            renderRows();
            scroll.post(this::loadVisibleMetadata);
        }

        private void close() {
            if (!attached || closing) return;
            closing = true;
            panel.animate().cancel();
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.animate().scaleX(0.04f).scaleY(0.08f).alpha(0f)
                    .translationX(fromLeft ? -dp(5) : dp(5)).setDuration(230L)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f))
                    .withEndAction(this::finishClose).start();
        }

        private void finishClose() {
            panelBlur.stop();
            if (attached) {
                try { windowManager.removeViewImmediate(root); }
                catch (Throwable ignored) { }
                attached = false;
            }
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
