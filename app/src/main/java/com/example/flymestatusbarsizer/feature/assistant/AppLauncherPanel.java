package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.Transliterator;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.flymestatusbarsizer.feature.windowmode.FlymeMiniWindowLauncher;
import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact app search panel opened by a side gesture. */
final class AppLauncherPanel {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TITLE = "FlymeStatusBarSizer:AppLauncher";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long LONG_PRESS_MS = 800L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "FlymeAppLauncher");
        thread.setDaemon(true);
        return thread;
    });
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII");
    private static final Object CACHE_LOCK = new Object();
    private static volatile List<AppEntry> cachedApps;
    private static final List<Popup> pendingPopups = new ArrayList<>();
    private static boolean receiverRegistered;
    private static LauncherApps.Callback shortcutCallback;
    private static boolean indexLoading;
    private static long cacheGeneration;
    private static Popup active;

    private AppLauncherPanel() { }

    static SideGestureActions.Action action(Context context) {
        ensurePackageReceiver(context);
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

    private static void ensurePackageReceiver(Context context) {
        synchronized (CACHE_LOCK) {
            if (receiverRegistered) return;
            Context appContext = context.getApplicationContext();
            if (appContext != null) context = appContext;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    synchronized (CACHE_LOCK) {
                        cachedApps = null;
                        cacheGeneration++;
                    }
                    Popup popup;
                    synchronized (AppLauncherPanel.class) { popup = active; }
                    if (popup != null) popup.refreshAfterPackageChange();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                shortcutCallback = new LauncherApps.Callback() {
                    @Override public void onPackageRemoved(String packageName, UserHandle user) { invalidateCache(); }
                    @Override public void onPackageAdded(String packageName, UserHandle user) { invalidateCache(); }
                    @Override public void onPackageChanged(String packageName, UserHandle user) { invalidateCache(); }
                    @Override public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                        invalidateCache();
                    }
                    @Override public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
                        invalidateCache();
                    }
                    @Override public void onShortcutsChanged(
                            String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
                        invalidateCache();
                    }
                };
                launcherApps.registerCallback(shortcutCallback, MAIN);
            }
            receiverRegistered = true;
        }
    }

    private static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedApps = null;
            cacheGeneration++;
        }
        Popup popup;
        synchronized (AppLauncherPanel.class) { popup = active; }
        if (popup != null) popup.refreshAfterPackageChange();
    }

    private static final class AppEntry {
        final String label;
        final Drawable icon;
        final Intent launchIntent;
        final String packageName;
        final String shortcutId;
        final UserHandle user;
        final String[] keys;

        AppEntry(String label, Drawable icon, Intent launchIntent, String packageName,
                String shortcutId, UserHandle user, String[] keys) {
            this.label = label;
            this.icon = icon;
            this.launchIntent = launchIntent;
            this.packageName = packageName;
            this.shortcutId = shortcutId;
            this.user = user;
            this.keys = keys;
        }

        boolean isShortcut() { return shortcutId != null; }

        int matchScore(List<String> query) {
            int best = Integer.MAX_VALUE;
            for (String key : keys) {
                if (key.length() < query.size()) continue;
                for (int start = 0; start <= key.length() - query.size(); start++) {
                    boolean matched = true;
                    for (int i = 0; i < query.size(); i++) {
                        if (query.get(i).indexOf(key.charAt(start + i)) < 0) {
                            matched = false;
                            break;
                        }
                    }
                    if (matched) {
                        int score = (start == 0 ? 0 : 1_000_000)
                                + start * 1_000 + key.length();
                        best = Math.min(best, score);
                        break;
                    }
                }
            }
            return best;
        }
    }

    private static final class Popup {
        private final Context context;
        private final boolean fromLeft;
        private final float anchorY;
        private final WindowManager windowManager;
        private final FrameLayout root;
        private final LinearLayout panel;
        private final AssistantPanelBlur panelBlur;
        private final EditText search;
        private final LinearLayout rows;
        private final ScrollView scroll;
        private final List<String> query = new ArrayList<>();
        private final List<AppEntry> entries = new ArrayList<>();
        private boolean darkMode;
        private boolean attached;
        private boolean closing;
        private boolean loading;
        private boolean indexRequested;
        private volatile int loadRequestId;
        private View holdView;
        private ImageView holdIcon;
        private Runnable holdRunnable;
        private boolean holdTriggered;
        private boolean holdCanceled;

        Popup(Context context, boolean fromLeft, float anchorY) {
            this.context = context;
            this.fromLeft = fromLeft;
            this.anchorY = anchorY;
            darkMode = isNightMode(context.getResources().getConfiguration());
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            root = new FrameLayout(context) {
                @Override protected void onConfigurationChanged(Configuration configuration) {
                    super.onConfigurationChanged(configuration);
                    updateDarkMode(configuration);
                }
            };
            root.setBackgroundColor(Color.TRANSPARENT);
            root.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE
                        || event.getActionMasked() == MotionEvent.ACTION_DOWN) close();
                return true;
            });

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(5), dp(5), dp(5), dp(5));
            panel.setBackground(panelBackground());
            panel.setElevation(dp(7));
            panel.setOnClickListener(view -> { });
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(dp(193), Math.max(dp(165), screenWidth - dp(22)));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(width,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT));
            panelParams.leftMargin = dp(8);
            panelParams.rightMargin = dp(8);
            root.addView(panel, panelParams);
            panelBlur = new AssistantPanelBlur(panel, context.getResources().getDisplayMetrics().density);

            search = new EditText(context);
            search.setSingleLine(true);
            search.setTextSize(12);
            search.setHint("输入应用首字母");
            search.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
            search.setHintTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            search.setPadding(dp(8), 0, dp(8), 0);
            search.setCursorVisible(false);
            search.setFocusable(false);
            search.setClickable(true);
            search.setShowSoftInputOnFocus(false);
            search.setBackground(background(paletteColor(0x12000000, 0x1cffffff), dp(7), Color.TRANSPARENT));
            search.setOnClickListener(view -> {
                query.clear();
                updateSearch();
                renderRows();
            });
            panel.addView(search, new LinearLayout.LayoutParams(-1, dp(32)));

            rows = new LinearLayout(context);
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(108));
            scrollParams.topMargin = dp(4);
            scrollParams.bottomMargin = dp(4);
            panel.addView(scroll, scrollParams);

            LinearLayout keyboard = new LinearLayout(context);
            keyboard.setOrientation(LinearLayout.VERTICAL);
            addKeyRow(keyboard, new String[]{"QW", "ER", "TY", "UI", "OP"});
            addKeyRow(keyboard, new String[]{"AS", "DF", "GH", "JK", "L"});
            addKeyRow(keyboard, new String[]{"ZX", "CV", "BN", "M"});
            panel.addView(keyboard, new LinearLayout.LayoutParams(-1, dp(108)));
        }

        void show() {
            synchronized (AppLauncherPanel.class) {
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
                    return;
                } catch (Throwable ignored) {
                    try { windowManager.removeViewImmediate(root); } catch (Throwable ignoredAgain) { }
                }
            }
            synchronized (AppLauncherPanel.class) { if (active == this) active = null; }
        }

        private void addKeyRow(LinearLayout keyboard, String[] keys) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (String key : keys) {
                TextView button = new TextView(context);
                button.setText(key);
                button.setTextSize(12);
                button.setGravity(Gravity.CENTER);
                button.setTextColor(paletteColor(0xff273134, 0xffe4e9ea));
                button.setBackground(background(paletteColor(0x16000000, 0x20ffffff), dp(7), Color.TRANSPARENT));
                button.setContentDescription("输入 " + key);
                button.setOnClickListener(view -> {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    query.add(key.toLowerCase(Locale.ROOT));
                    updateSearch();
                    ensureAppsLoaded();
                    renderRows();
                });
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(32), 1f);
                buttonParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(button, buttonParams);
            }
            keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(36)));
        }

        private void loadAppsAsync() {
            if (indexRequested) return;
            indexRequested = true;
            loading = true;
            int requestId = ++loadRequestId;
            renderRows();
            requestApps(context, this, requestId);
        }

        private void setApps(List<AppEntry> loaded, int requestId, long generation) {
            synchronized (CACHE_LOCK) {
                if (generation != cacheGeneration) return;
            }
            if (!isCurrent() || requestId != loadRequestId) return;
            entries.clear();
            entries.addAll(loaded);
            loading = false;
            renderRows();
        }

        private static void requestApps(Context context, Popup popup, int requestId) {
            List<AppEntry> cached;
            long generation;
            synchronized (CACHE_LOCK) {
                cached = cachedApps;
                generation = cacheGeneration;
                if (cached == null) {
                    if (!pendingPopups.contains(popup)) pendingPopups.add(popup);
                    if (indexLoading) return;
                    indexLoading = true;
                }
            }
            if (cached != null) {
                List<AppEntry> result = cached;
                long resultGeneration = generation;
                MAIN.post(() -> popup.setApps(result, requestId, resultGeneration));
                return;
            }
            WORKER.execute(() -> {
                while (true) {
                    long scanGeneration;
                    synchronized (CACHE_LOCK) { scanGeneration = cacheGeneration; }
                    List<AppEntry> loaded = loadApps(context);
                    List<Popup> waiting;
                    synchronized (CACHE_LOCK) {
                        if (scanGeneration != cacheGeneration) continue;
                        cachedApps = loaded;
                        indexLoading = false;
                        waiting = new ArrayList<>(pendingPopups);
                        pendingPopups.clear();
                    }
                    long resultGeneration = scanGeneration;
                    for (Popup target : waiting) {
                        int targetRequest = target.loadRequestId;
                        MAIN.post(() -> target.setApps(loaded, targetRequest, resultGeneration));
                    }
                    return;
                }
            });
        }

        private static List<AppEntry> loadApps(Context context) {
            android.content.pm.PackageManager pm = context.getPackageManager();
            Intent queryIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            Map<String, AppEntry> found = new HashMap<>();
            for (android.content.pm.ResolveInfo info : pm.queryIntentActivities(queryIntent, 0)) {
                android.content.pm.ActivityInfo activity = info.activityInfo;
                if (activity == null || activity.applicationInfo == null || !activity.enabled
                        || !activity.applicationInfo.enabled
                        || context.getPackageName().equals(activity.packageName)
                        || found.containsKey(activity.packageName)) continue;
                CharSequence rawLabel = info.loadLabel(pm);
                String label = rawLabel == null ? activity.packageName : rawLabel.toString();
                Drawable icon = info.loadIcon(pm);
                ComponentName component = new ComponentName(activity.packageName, activity.name);
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                String latin = PINYIN.transliterate(label).toLowerCase(Locale.ROOT);
                found.put(activity.packageName, new AppEntry(label, icon, launch,
                        activity.packageName, null, null, buildKeys(label)));
            }
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                for (UserHandle user : launcherApps.getProfiles()) {
                    for (android.content.pm.ApplicationInfo application : pm.getInstalledApplications(0)) {
                        if (context.getPackageName().equals(application.packageName)) continue;
                        LauncherApps.ShortcutQuery shortcutQuery = new LauncherApps.ShortcutQuery()
                                .setPackage(application.packageName)
                                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                        List<ShortcutInfo> shortcuts;
                        try { shortcuts = launcherApps.getShortcuts(shortcutQuery, user); }
                        catch (RuntimeException ignored) { continue; }
                        if (shortcuts == null) continue;
                        CharSequence appLabel = application.loadLabel(pm);
                        for (ShortcutInfo shortcut : shortcuts) {
                            if (!shortcut.isPinned()) continue;
                            CharSequence rawLabel = shortcut.getShortLabel();
                            if (rawLabel == null || rawLabel.length() == 0) rawLabel = shortcut.getLongLabel();
                            if (rawLabel == null || rawLabel.length() == 0) continue;
                            Drawable shortcutIcon = launcherApps.getShortcutIconDrawable(shortcut, dp(context, 34));
                            if (shortcutIcon == null) shortcutIcon = application.loadIcon(pm);
                            String label = rawLabel.toString();
                            found.put(application.packageName + "#" + user + "#" + shortcut.getId(),
                                    new AppEntry(label, shortcutIcon, null, application.packageName,
                                            shortcut.getId(), user, buildKeys(label, appLabel == null ? null : appLabel.toString())));
                        }
                    }
                }
            }
            List<AppEntry> result = new ArrayList<>(found.values());
            Collections.sort(result, (left, right) -> left.label.compareToIgnoreCase(right.label));
            return result;
        }

        private static int dp(Context context, float value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private static String[] buildKeys(String... labels) {
            List<String> keys = new ArrayList<>();
            for (String label : labels) {
                if (label == null) continue;
                String lower = label.toLowerCase(Locale.ROOT);
                String latin = PINYIN.transliterate(lower);
                keys.add(normalize(latin));
                keys.add(initials(latin));
                keys.add(normalize(lower));
            }
            return keys.toArray(new String[0]);
        }

        private void refreshAfterPackageChange() {
            entries.clear();
            indexRequested = false;
            loading = false;
            loadRequestId++;
            renderRows();
            if (!query.isEmpty()) loadAppsAsync();
        }

        private void renderRows() {
            rows.removeAllViews();
            if (!indexRequested) {
                addMessage("点击键盘开始搜索");
                return;
            }
            if (loading) {
                addMessage("正在加载应用");
                return;
            }
            if (query.isEmpty()) {
                addMessage("请输入应用首字母");
                return;
            }
            List<AppEntry> matches = new ArrayList<>();
            for (AppEntry entry : entries) {
                if (entry.matchScore(query) != Integer.MAX_VALUE) matches.add(entry);
            }
            matches.sort((left, right) -> {
                int score = Integer.compare(left.matchScore(query), right.matchScore(query));
                return score != 0 ? score : left.label.compareToIgnoreCase(right.label);
            });
            if (matches.isEmpty()) {
                addMessage(query.isEmpty() ? "没有可启动的应用" : "未找到匹配应用");
                return;
            }
            for (int start = 0; start < matches.size(); start += 3) {
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                for (int index = start; index < Math.min(start + 3, matches.size()); index++) {
                    addEntry(row, matches.get(index));
                }
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
            }
        }

        private void addMessage(String message) {
            TextView view = new TextView(context);
            view.setText(message);
            view.setTextSize(12);
            view.setTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            view.setGravity(Gravity.CENTER);
            rows.addView(view, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        private void addEntry(LinearLayout row, AppEntry entry) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(2), dp(1), dp(2), dp(1));
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView name = new TextView(context);
            name.setText(entry.label);
            name.setTextSize(10);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
            name.setGravity(Gravity.CENTER);
            cell.addView(name, new LinearLayout.LayoutParams(-1, dp(16)));
            cell.setOnTouchListener((view, event) -> handleEntryTouch(view, icon, entry, event));
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
        }

        private boolean handleEntryTouch(View view, ImageView icon, AppEntry entry, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelHold();
                    holdView = view;
                    holdIcon = icon;
                    holdTriggered = false;
                    holdCanceled = false;
                    float downX = event.getX();
                    float downY = event.getY();
                    int slop = ViewConfiguration.get(context).getScaledTouchSlop();
                    holdRunnable = () -> {
                        if (holdView != view || closing) return;
                        holdTriggered = true;
                        HapticFeedbackUtils.perform(context, view, HapticFeedbackConstants.LONG_PRESS);
                    };
                    icon.animate().scaleX(0.8f).scaleY(0.8f).setDuration(LONG_PRESS_MS).start();
                    view.setTag(new float[]{downX, downY, slop});
                    MAIN.postDelayed(holdRunnable, LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    Object tag = view.getTag();
                    if (tag instanceof float[]) {
                        float[] down = (float[]) tag;
                        if (Math.abs(event.getX() - down[0]) > down[2]
                                || Math.abs(event.getY() - down[1]) > down[2]
                                || event.getX() < 0 || event.getY() < 0
                                || event.getX() > view.getWidth() || event.getY() > view.getHeight()) {
                            holdCanceled = true;
                            cancelHold();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean longPress = holdTriggered && holdView == view;
                    boolean canceled = holdCanceled;
                    cancelHold();
                    if (!canceled) {
                        if (longPress) launchMiniWindow(entry);
                        else launchEntry(entry, view);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    holdCanceled = true;
                    cancelHold();
                    return true;
                default:
                    return true;
            }
        }

        private void cancelHold() {
            if (holdRunnable != null) MAIN.removeCallbacks(holdRunnable);
            if (holdIcon != null) holdIcon.animate().cancel();
            if (holdIcon != null) holdIcon.animate().scaleX(1f).scaleY(1f).setDuration(100L).start();
            holdRunnable = null;
            holdView = null;
            holdIcon = null;
            holdTriggered = false;
        }

        private void launchEntry(AppEntry entry, View target) {
            HapticFeedbackUtils.perform(context, target, HapticFeedbackConstants.CONTEXT_CLICK);
            close();
            try {
                if (entry.isShortcut()) {
                    LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                    launcherApps.startShortcut(entry.packageName, entry.shortcutId, null, null, entry.user);
                } else {
                    context.startActivity(entry.launchIntent);
                }
            } catch (Throwable ignored) { }
        }

        private void launchMiniWindow(AppEntry entry) {
            close();
            try {
                if (entry.isShortcut()) {
                    LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                    FlymeMiniWindowLauncher.startShortcutInMiniWindow(
                            context, launcherApps, entry.packageName, entry.shortcutId, entry.user);
                } else {
                    FlymeMiniWindowLauncher.startActivityInMiniWindow(
                            context, entry.launchIntent, entry.packageName);
                }
            } catch (Throwable error) {
                Toast.makeText(context, "无法打开小窗", Toast.LENGTH_SHORT).show();
            }
        }

        private void updateSearch() {
            StringBuilder text = new StringBuilder();
            for (String key : query) {
                if (text.length() > 0) text.append(' ');
                text.append(key.toUpperCase(Locale.ROOT));
            }
            search.setText(text);
        }

        private void ensureAppsLoaded() {
            if (!indexRequested) loadAppsAsync();
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
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.setScaleX(0.04f);
            panel.setScaleY(0.08f);
            panel.setAlpha(0.15f);
            panel.setTranslationX(fromLeft ? -dp(5) : dp(5));
            panel.animate().scaleX(1f).scaleY(1f).alpha(1f).translationX(0f)
                    .setDuration(360L).setInterpolator(new android.view.animation.OvershootInterpolator(0.55f)).start();
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
            params.setTitle(TITLE);
            params.packageName = context.getPackageName();
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            try { WindowManager.LayoutParams.class.getMethod("setTrustedOverlay").invoke(params); }
            catch (Throwable ignored) { }
            return params;
        }

        private void close() {
            if (!attached || closing) return;
            closing = true;
            cancelHold();
            panel.animate().cancel();
            panel.setPivotX(fromLeft ? 0f : panel.getWidth());
            panel.setPivotY(Math.max(0f, Math.min(panel.getHeight(), anchorY - panel.getTop())));
            panel.animate().scaleX(0.04f).scaleY(0.08f).alpha(0f)
                    .translationX(fromLeft ? -dp(5) : dp(5)).setDuration(230L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.35f))
                    .withEndAction(this::finishClose).start();
        }

        private void finishClose() {
            panelBlur.stop();
            try { windowManager.removeViewImmediate(root); } catch (Throwable ignored) { }
            attached = false;
            synchronized (CACHE_LOCK) { pendingPopups.remove(this); }
            synchronized (AppLauncherPanel.class) { if (active == this) active = null; }
        }

        private boolean isCurrent() {
            synchronized (AppLauncherPanel.class) { return active == this && attached && !closing; }
        }

        private boolean isNightMode(Configuration configuration) {
            return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }

        private void updateDarkMode(Configuration configuration) {
            boolean next = isNightMode(configuration);
            if (darkMode == next) return;
            darkMode = next;
            panel.setBackground(panelBackground());
            search.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
            search.setHintTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            renderRows();
        }

        private Drawable panelBackground() {
            return background(paletteColor(0xf5f7f8f8, 0xf52a2d30), dp(12), paletteColor(0x24000000, 0x38ffffff));
        }

        private Drawable background(int color, float radius, int stroke) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(radius);
            if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
            return drawable;
        }

        private int paletteColor(int light, int dark) { return darkMode ? dark : light; }

        private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    }

    private static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'a' && c <= 'z') result.append(c);
        }
        return result.toString();
    }

    private static String initials(String latin) {
        StringBuilder result = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < latin.length(); i++) {
            char c = latin.charAt(i);
            if (c >= 'a' && c <= 'z') {
                if (wordStart) result.append(c);
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        return result.toString();
    }
}
