package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.feature.assistant.SideGestureActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        final String label;
        final Drawable icon;

        TaskEntry(RecentTaskCard card, AppMetadata metadata) {
            this.card = card;
            label = metadata.label;
            icon = metadata.icon;
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

            @Override public void execute(boolean fromLeft, float startY, Runnable onSuccess) {
                if (isReady()) {
                    new Popup(context, fromLeft, startY).show();
                    if (onSuccess != null) onSuccess.run();
                }
            }
        };
    }

    private static final class Popup {
        private static final int ROW_HEIGHT_DP = 31;
        private final Context context;
        private final boolean fromLeft;
        private final float startY;
        private final WindowManager windowManager;
        private final List<TaskEntry> entries = new ArrayList<>();
        private final FrameLayout root;
        private final LinearLayout panel;
        private final LinearLayout rows;
        private final ScrollView scroll;
        private final EditText search;
        private final LinearLayout searchContainer;
        private View loadingRow;
        private boolean attached;
        private boolean closing;
        private boolean loading = true;

        Popup(Context context, boolean fromLeft, float startY) {
            this.context = context;
            this.fromLeft = fromLeft;
            this.startY = startY;
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            root = new FrameLayout(context);
            root.setBackgroundColor(Color.TRANSPARENT);
            root.setOnClickListener(view -> close());
            root.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) close();
                return false;
            });

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(4), dp(4), dp(4), dp(2));
            panel.setBackground(background(0xf5ffffff, dp(12), 0x24000000));
            panel.setElevation(dp(7));
            panel.setOnClickListener(view -> { });
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int panelWidth = Math.min(dp(193), Math.max(dp(147), screenWidth - dp(22)));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT));
            panelParams.leftMargin = dp(8);
            panelParams.rightMargin = dp(8);
            panelParams.topMargin = Math.max(0, Math.round(startY - dp(156)));
            root.addView(panel, panelParams);

            searchContainer = new LinearLayout(context);
            searchContainer.setGravity(Gravity.CENTER_VERTICAL);
            searchContainer.setVisibility(View.GONE);
            search = new EditText(context);
            search.setSingleLine(true);
            search.setTextSize(12);
            search.setTextColor(0xff222222);
            search.setHintTextColor(0xff888888);
            search.setHint("搜索应用");
            search.setPadding(dp(7), 0, dp(7), 0);
            search.setBackground(background(0x11000000, dp(7), Color.TRANSPARENT));
            searchContainer.addView(search, new LinearLayout.LayoutParams(-1, dp(24)));
            panel.addView(searchContainer, new LinearLayout.LayoutParams(-1, dp(27)));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    renderRows();
                }
                @Override public void afterTextChanged(Editable s) { }
            });

            rows = new LinearLayout(context);
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            LinearLayout footer = new LinearLayout(context);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            TextView searchButton = new TextView(context);
            searchButton.setText("⌕");
            searchButton.setTextSize(19);
            searchButton.setTextColor(0xff287a78);
            searchButton.setGravity(Gravity.CENTER);
            searchButton.setContentDescription("搜索任务");
            footer.addView(searchButton, new LinearLayout.LayoutParams(dp(28), dp(27)));
            searchButton.setOnClickListener(view -> toggleSearch());
            View spacer = new View(context);
            footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            TextView closeAll = new TextView(context);
            closeAll.setText("全部关闭");
            closeAll.setTextSize(11);
            closeAll.setTextColor(0xff287a78);
            closeAll.setGravity(Gravity.CENTER);
            closeAll.setPadding(dp(8), 0, dp(8), 0);
            footer.addView(closeAll, new LinearLayout.LayoutParams(-2, dp(27)));
            closeAll.setOnClickListener(view -> closeAllTasks());
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
                    panel.post(() -> {
                        alignToGestureStart();
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

        private void alignToGestureStart() {
            if (!attached || root.getHeight() <= 0 || panel.getHeight() <= 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            int maxTop = Math.max(0, root.getHeight() - panel.getHeight());
            params.topMargin = Math.max(0, Math.min(maxTop,
                    Math.round(startY - panel.getHeight() / 2f)));
            panel.setLayoutParams(params);
        }

        private void animateIn() {
            if (!attached) return;
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), startY - panel.getTop())));
            panel.setScaleX(0.04f);
            panel.setScaleY(0.08f);
            panel.setAlpha(0.15f);
            panel.setTranslationX(fromLeft ? -dp(5) : dp(5));
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(360L).setInterpolator(new OvershootInterpolator(0.55f)).start();
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
                List<RecentTaskCard> cards;
                try { cards = getTaskAccess(context).candidates(); }
                catch (Exception ignored) { cards = new ArrayList<>(); }
                for (RecentTaskCard card : cards) {
                    if (!isCurrent()) return;
                    TaskEntry entry = new TaskEntry(card, appMetadata(context, card));
                    MAIN.post(() -> appendLoadedEntry(entry));
                }
                MAIN.post(this::finishTaskLoading);
            });
        }

        private static List<TaskEntry> loadEntries(Context context) {
            List<TaskEntry> result = new ArrayList<>();
            try {
                OneStepTaskAccess tasks = getTaskAccess(context);
                for (RecentTaskCard card : tasks.candidates()) {
                    result.add(new TaskEntry(card, appMetadata(context, card)));
                }
            } catch (Exception ignored) { }
            return result;
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
            String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            int count = 0;
            for (TaskEntry entry : entries) {
                if (!matchesQuery(entry, query)) continue;
                if (count > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(0x18000000);
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                rows.addView(taskRow(entry), new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                count++;
            }
            if (count == 0 && !loading) {
                TextView empty = new TextView(context);
                empty.setText(entries.isEmpty() ? "没有最近任务" : "没有匹配的任务");
                empty.setTextColor(0xff777777);
                empty.setTextSize(14);
                empty.setGravity(Gravity.CENTER);
                rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
            }
            if (loading) addLoadingRow();
        }

        private boolean matchesQuery(TaskEntry entry, String query) {
            return query.isEmpty() || entry.label.toLowerCase(Locale.getDefault()).contains(query)
                    || entry.card.description.toLowerCase(Locale.getDefault()).contains(query);
        }

        private void appendLoadedEntry(TaskEntry entry) {
            if (!isCurrent()) return;
            if (loadingRow != null) rows.removeView(loadingRow);
            loadingRow = null;
            entries.add(entry);

            String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            if (matchesQuery(entry, query)) {
                int visibleBefore = 0;
                for (int i = 0; i < entries.size() - 1; i++) {
                    if (matchesQuery(entries.get(i), query)) visibleBefore++;
                }
                if (visibleBefore > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(0x18000000);
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                View row = taskRow(entry);
                row.setAlpha(0f);
                row.setTranslationY(dp(4));
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                row.animate().alpha(1f).translationY(0f)
                        .setStartDelay(Math.min((entries.size() - 1) * 24L, 120L))
                        .setDuration(180L).setInterpolator(new DecelerateInterpolator()).start();
            }
            if (loading) addLoadingRow();
        }

        private void addLoadingRow() {
            FrameLayout row = new FrameLayout(context);
            ProgressBar spinner = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(0xff287a78));
            row.addView(spinner, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            rows.addView(row, new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
            loadingRow = row;
        }

        private void finishTaskLoading() {
            if (!isCurrent()) return;
            loading = false;
            if (loadingRow != null) rows.removeView(loadingRow);
            loadingRow = null;

            String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            for (TaskEntry entry : entries) if (matchesQuery(entry, query)) return;
            renderRows();
        }

        private View taskRow(TaskEntry entry) {
            RecentTaskCard card = entry.card;
            String label = entry.label;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(context);
            name.setText(label);
            name.setTextColor(0xff222222);
            name.setTextSize(11);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(dp(6), 0, dp(4), 0);
            int textHeight = name.getLineHeight();

            TextView remove = new TextView(context);
            remove.setText("×");
            remove.setTextSize(11);
            remove.setTextColor(0xff444444);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("关闭" + label);
            row.addView(remove, new LinearLayout.LayoutParams(dp(23), textHeight));
            remove.setOnClickListener(view -> removeTask(card));

            ImageView icon = new ImageView(context);
            if (entry.icon != null) icon.setImageDrawable(entry.icon);
            else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            row.addView(icon, new LinearLayout.LayoutParams(textHeight, textHeight));
            row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
            row.setOnClickListener(view -> activate(card));
            return row;
        }

        private void toggleSearch() {
            boolean visible = searchContainer.getVisibility() != View.VISIBLE;
            searchContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                search.requestFocus();
                Object input = context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input instanceof InputMethodManager) ((InputMethodManager) input).showSoftInput(search,
                        InputMethodManager.SHOW_IMPLICIT);
            } else {
                search.setText("");
                Object input = context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input instanceof InputMethodManager) ((InputMethodManager) input).hideSoftInputFromWindow(
                        search.getWindowToken(), 0);
            }
        }

        private void activate(RecentTaskCard card) {
            close();
            WORKER.execute(() -> {
                try { getTaskAccess(context).activate(card); }
                catch (Exception ignored) { }
            });
        }

        private void removeTask(RecentTaskCard card) {
            entries.removeIf(entry -> entry.card.sameTask(card));
            renderRows();
            WORKER.execute(() -> {
                try { getTaskAccess(context).remove(card); }
                catch (Exception ignored) { }
                List<TaskEntry> loaded = loadEntries(context);
                MAIN.post(() -> replaceEntries(loaded));
            });
        }

        private void closeAllTasks() {
            List<RecentTaskCard> removing = new ArrayList<>();
            for (TaskEntry entry : entries) removing.add(entry.card);
            entries.clear();
            loading = true;
            renderRows();
            WORKER.execute(() -> {
                try {
                    OneStepTaskAccess tasks = getTaskAccess(context);
                    for (RecentTaskCard card : removing) {
                        try { tasks.remove(card); }
                        catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
                List<TaskEntry> loaded = loadEntries(context);
                MAIN.post(() -> replaceEntries(loaded));
            });
        }

        private void replaceEntries(List<TaskEntry> loaded) {
            if (!isCurrent()) return;
            entries.clear();
            entries.addAll(loaded);
            loading = false;
            renderRows();
        }

        private void close() {
            if (!attached || closing) return;
            closing = true;
            panel.animate().cancel();
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), startY - panel.getTop())));
            panel.animate().scaleX(0.04f).scaleY(0.08f).alpha(0f)
                    .translationX(fromLeft ? -dp(5) : dp(5)).setDuration(230L)
                    .setInterpolator(new AccelerateInterpolator(1.35f))
                    .withEndAction(this::finishClose).start();
        }

        private void finishClose() {
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
