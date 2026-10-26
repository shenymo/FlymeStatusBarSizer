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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
        private final EditText search;
        private final LinearLayout searchContainer;
        private final TextView searchButton;
        private final TextView closeAllButton;
        private View loadingRow;
        private boolean attached;
        private boolean closing;
        private boolean loading = true;

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
            int panelWidth = Math.min(dp(193), Math.max(dp(147), screenWidth - dp(22)));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT));
            panelParams.leftMargin = dp(8);
            panelParams.rightMargin = dp(8);
            panelParams.topMargin = Math.max(0, Math.round(anchorY - dp(156)));
            root.addView(panel, panelParams);

            searchContainer = new LinearLayout(context);
            searchContainer.setGravity(Gravity.CENTER_VERTICAL);
            searchContainer.setVisibility(View.GONE);
            search = new EditText(context);
            search.setSingleLine(true);
            search.setTextSize(12);
            search.setTextColor(paletteColor(0xff222222, 0xfff1f3f4));
            search.setHintTextColor(paletteColor(0xff888888, 0xffaeb4b9));
            search.setHint("搜索应用");
            search.setPadding(dp(7), 0, dp(7), 0);
            search.setBackground(background(
                    paletteColor(0x11000000, 0x18ffffff), dp(7), Color.TRANSPARENT));
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
            searchButton = new TextView(context);
            searchButton.setText("⌕");
            searchButton.setTextSize(19);
            searchButton.setTextColor(accentColor());
            searchButton.setGravity(Gravity.CENTER);
            searchButton.setContentDescription("搜索任务");
            footer.addView(searchButton, new LinearLayout.LayoutParams(dp(28), dp(27)));
            searchButton.setOnClickListener(view -> toggleSearch());
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
                    divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
                    rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                rows.addView(taskRow(entry), new LinearLayout.LayoutParams(-1, dp(ROW_HEIGHT_DP)));
                count++;
            }
            if (count == 0 && !loading) {
                TextView empty = new TextView(context);
                empty.setText(entries.isEmpty() ? "没有最近任务" : "没有匹配的任务");
                empty.setTextColor(paletteColor(0xff777777, 0xffb8bec3));
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
                    divider.setBackgroundColor(paletteColor(0x18000000, 0x28ffffff));
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
            spinner.setIndeterminateTintList(ColorStateList.valueOf(accentColor()));
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
            remove.setOnClickListener(view -> removeTask(card));

            ImageView icon = new ImageView(context);
            if (entry.icon != null) icon.setImageDrawable(entry.icon);
            else icon.setImageResource(android.R.drawable.sym_def_app_icon);
            int iconSize = dp(16);
            ImageButton miniWindow = new ImageButton(context);
            miniWindow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            miniWindow.setPadding(dp(5), dp(5), dp(5), dp(5));
            miniWindow.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(paletteColor(0x18000000, 0x20ffffff)), null, null));
            miniWindow.setContentDescription("以小窗打开" + label);
            miniWindow.setTooltipText("以小窗打开");
            setModuleIcon(miniWindow, R.drawable.ic_task_window, android.R.drawable.ic_menu_view);
            miniWindow.setOnClickListener(view -> openMiniWindow(card, miniWindow));
            if (fromLeft) {
                row.addView(remove, new LinearLayout.LayoutParams(dp(26), dp(26)));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
            } else {
                row.addView(miniWindow, new LinearLayout.LayoutParams(dp(29), dp(29)));
                row.addView(name, new LinearLayout.LayoutParams(0, textHeight, 1f));
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                row.addView(remove, new LinearLayout.LayoutParams(dp(26), dp(26)));
            }
            row.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                activate(card);
            });
            return row;
        }

        private void setModuleIcon(ImageView view, int resource, int fallback) {
            try {
                Context module = context.createPackageContext(
                        com.example.flymestatusbarsizer.BuildConfig.APPLICATION_ID, 0);
                view.setImageDrawable(module.getDrawable(resource));
            } catch (Exception error) {
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
            search.setTextColor(paletteColor(0xff222222, 0xfff1f3f4));
            search.setHintTextColor(paletteColor(0xff888888, 0xffaeb4b9));
            search.setBackground(background(
                    paletteColor(0x11000000, 0x18ffffff), dp(7), Color.TRANSPARENT));
            searchButton.setTextColor(accentColor());
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
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.animate().scaleX(0.04f).scaleY(0.08f).alpha(0f)
                    .translationX(fromLeft ? -dp(5) : dp(5)).setDuration(230L)
                    .setInterpolator(new AccelerateInterpolator(1.35f))
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
