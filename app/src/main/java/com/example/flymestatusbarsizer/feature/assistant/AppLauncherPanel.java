package com.example.flymestatusbarsizer.feature.assistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherUserInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.icu.text.Transliterator;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flymestatusbarsizer.BuildConfig;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.feature.windowmode.FlymeMiniWindowLauncher;
import com.example.flymestatusbarsizer.util.HapticFeedbackUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact app search panel opened by a side gesture. */
public final class AppLauncherPanel {
    private static final int[] WINDOW_TYPES = {2041, 2017, 2040};
    private static final String TAG = "FlymeAppLauncher";
    private static final int FLAG_MATCH_PINNED_BY_ANY_LAUNCHER = 1 << 10;
    private static final String TITLE = "FlymeStatusBarSizer:AppLauncher";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long LONG_PRESS_MS = 800L;
    private static final long CACHE_REFRESH_DELAY_MS = 300L;
    private static final int ICON_CACHE_BYTES = 4 * 1024 * 1024;
    private static ExecutorService worker(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
    private static final ExecutorService INDEX_WORKER = worker("FlymeAppIndex");
    private static final ExecutorService SEARCH_WORKER = worker("FlymeAppSearch");
    private static final ExecutorService ICON_WORKER = worker("FlymeAppIcons");
    private static final ExecutorService LAUNCH_WORKER = worker("FlymeAppLaunch");
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII");
    private static final Object CACHE_LOCK = new Object();
    private static volatile List<AppEntry> cachedApps;
    private static final List<Popup> pendingPopups = new ArrayList<>();
    private static int receiverUsers;
    private static BroadcastReceiver packageReceiver;
    private static Context packageReceiverContext;
    private static LauncherApps.Callback launcherAppsCallback;
    private static final Runnable REFRESH_ACTIVE_POPUP = AppLauncherPanel::refreshActivePopup;
    private static boolean indexLoading;
    private static long cacheGeneration;
    private static Popup active;

    private AppLauncherPanel() { }

    static SideGestureActions.Action action(Context context) {
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

    private static void ensurePackageReceiver(Context context) {
        synchronized (CACHE_LOCK) {
            if (receiverUsers++ > 0) return;
            Context appContext = context.getApplicationContext();
            if (appContext != null) context = appContext;
            packageReceiverContext = context;
            packageReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    invalidateCache();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(packageReceiver, filter);
            }
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                launcherAppsCallback = new LauncherApps.Callback() {
                    @Override public void onPackageRemoved(String packageName, UserHandle user) {
                        invalidateCache();
                    }
                    @Override public void onPackageAdded(String packageName, UserHandle user) {
                        invalidateCache();
                    }
                    @Override public void onPackageChanged(String packageName, UserHandle user) {
                        invalidateCache();
                    }
                    @Override public void onPackagesAvailable(
                            String[] packageNames, UserHandle user, boolean replacing) {
                        invalidateCache();
                    }
                    @Override public void onPackagesUnavailable(
                            String[] packageNames, UserHandle user, boolean replacing) {
                        invalidateCache();
                    }
                    @Override public void onShortcutsChanged(
                            String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
                        invalidateCache();
                    }
                };
                launcherApps.registerCallback(launcherAppsCallback, MAIN);
            }
        }
    }

    private static void releasePackageReceiver() {
        synchronized (CACHE_LOCK) {
            if (--receiverUsers > 0) return;
            packageReceiverContext.unregisterReceiver(packageReceiver);
            LauncherApps launcherApps = packageReceiverContext.getSystemService(LauncherApps.class);
            if (launcherApps != null && launcherAppsCallback != null)
                launcherApps.unregisterCallback(launcherAppsCallback);
            packageReceiver = null;
            packageReceiverContext = null;
            launcherAppsCallback = null;
            cachedApps = null;
            cacheGeneration++;
        }
        MAIN.removeCallbacks(REFRESH_ACTIVE_POPUP);
    }

    private static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedApps = null;
            cacheGeneration++;
        }
        MAIN.removeCallbacks(REFRESH_ACTIVE_POPUP);
        MAIN.postDelayed(REFRESH_ACTIVE_POPUP, CACHE_REFRESH_DELAY_MS);
    }

    private static void refreshActivePopup() {
        Popup popup;
        synchronized (AppLauncherPanel.class) { popup = active; }
        if (popup != null) {
            popup.updateDarkMode(popup.context.getResources().getConfiguration());
            popup.refreshAfterPackageChange();
        }
    }

    static void refreshActivePopup(Configuration configuration) {
        Popup popup;
        synchronized (AppLauncherPanel.class) { popup = active; }
        if (popup != null) popup.updateDarkMode(configuration);
    }

    public static void dismissActivePopup() {
        Popup popup;
        synchronized (AppLauncherPanel.class) { popup = active; }
        if (popup != null) popup.closeImmediately();
    }

    private static final class AppEntry {
        final String label;
        final ComponentName component;
        final LauncherActivityInfo launcherActivity;
        final String packageName;
        final String shortcutId;
        final UserHandle user;
        final String[] keys;

        AppEntry(String label, ComponentName component, LauncherActivityInfo launcherActivity,
                String packageName,
                String shortcutId, UserHandle user, String[] keys) {
            this.label = label;
            this.component = component;
            this.launcherActivity = launcherActivity;
            this.packageName = packageName;
            this.shortcutId = shortcutId;
            this.user = user;
            this.keys = keys;
        }

        boolean isShortcut() { return shortcutId != null; }

        Drawable loadIcon(Context context) {
            if (launcherActivity != null) {
                Drawable icon = launcherActivity.getIcon(context.getResources()
                        .getDisplayMetrics().densityDpi);
                if (user != null && !Process.myUserHandle().equals(user)) {
                    int badgeSize = Math.round(14f * context.getResources()
                            .getDisplayMetrics().density);
                    try {
                        Context module = context.createPackageContext(BuildConfig.APPLICATION_ID,
                                Context.CONTEXT_IGNORE_SECURITY);
                        Drawable badge = module.getDrawable(R.drawable.ic_clone_badge).mutate();
                        LayerDrawable layered = new LayerDrawable(new Drawable[]{icon, badge});
                        int iconSize = Math.round(34f * context.getResources()
                                .getDisplayMetrics().density);
                        layered.setLayerSize(0, iconSize, iconSize);
                        layered.setLayerGravity(1, Gravity.RIGHT | Gravity.BOTTOM);
                        layered.setLayerSize(1, badgeSize, badgeSize);
                        return layered;
                    } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) { }
                }
                return icon;
            }
            if (isShortcut()) {
                LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                if (launcherApps == null) return null;
                LauncherApps.ShortcutQuery shortcutQuery = new LauncherApps.ShortcutQuery()
                        .setPackage(packageName)
                        .setShortcutIds(Collections.singletonList(shortcutId))
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                                | FLAG_MATCH_PINNED_BY_ANY_LAUNCHER);
                try {
                    List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(shortcutQuery, user);
                    if (shortcuts != null && !shortcuts.isEmpty()) {
                        Drawable icon = launcherApps.getShortcutIconDrawable(
                                shortcuts.get(0), Math.round(34f * context.getResources()
                                        .getDisplayMetrics().density));
                        if (user != null && !Process.myUserHandle().equals(user)) {
                            icon = context.getPackageManager().getUserBadgedDrawableForDensity(
                                    icon, user, null, context.getResources().getDisplayMetrics().densityDpi);
                        }
                        return icon;
                    }
                } catch (RuntimeException ignored) { }
                return null;
            }
            return null;
        }

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

    private static final class RankedEntry {
        final AppEntry entry;
        final int score;

        RankedEntry(AppEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private static final class SearchRequest {
        final int generation;
        final List<String> query;
        final List<AppEntry> entries;

        SearchRequest(int generation, List<String> query, List<AppEntry> entries) {
            this.generation = generation;
            this.query = query;
            this.entries = entries;
        }
    }

    private static final class Popup {
        private final Context context;
        private boolean fromLeft;
        private float anchorY;
        private final WindowManager windowManager;
        private final FrameLayout root;
        private final LinearLayout panel;
        private final AssistantPanelBlur panelBlur;
        private final EditText search;
        private final TextView clearSearch;
        private final TextView resultMessage;
        private final RecyclerView resultGrid;
        private final AppAdapter adapter;
        private final List<TextView> keyboardKeys = new ArrayList<>();
        private final List<String> query = new ArrayList<>();
        private final List<AppEntry> entries = new ArrayList<>();
        private final LruCache<AppEntry, Bitmap> iconCache = new LruCache<>(ICON_CACHE_BYTES) {
            @Override protected int sizeOf(AppEntry entry, Bitmap icon) {
                return icon.getAllocationByteCount();
            }
        };
        private final Set<AppEntry> iconsLoading = new HashSet<>();
        private final Object searchLock = new Object();
        private SearchRequest pendingSearch;
        private boolean searchWorkerRunning;
        private int queryGeneration;
        private long iconCacheGeneration = -1;
        private boolean darkMode;
        private boolean attached;
        private boolean visible;
        private WindowManager.LayoutParams windowParams;
        private boolean closing;
        private boolean loading;
        private boolean indexRequested;
        private volatile int loadRequestId;
        private View holdView;
        private ImageView holdIcon;
        private Runnable holdRunnable;
        private boolean holdTriggered;
        private boolean holdCanceled;
        Popup(Context context) {
            this.context = context;
            ensurePackageReceiver(context);
            darkMode = isNightMode(context.getResources().getConfiguration());
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            root = new FrameLayout(context) {
                @Override protected void onConfigurationChanged(Configuration configuration) {
                    super.onConfigurationChanged(configuration);
                    updateDarkMode(configuration);
                }

                @Override public boolean dispatchKeyEvent(KeyEvent event) {
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
            panel.setVisibility(View.INVISIBLE);
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
            search.setHint("搜首字母 · 长按开小窗");
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
                queryGeneration++;
                updateSearch();
                scheduleSearch();
            });
            FrameLayout searchArea = new FrameLayout(context);
            searchArea.addView(search, new FrameLayout.LayoutParams(-1, -1));
            clearSearch = new TextView(context);
            clearSearch.setText("×");
            clearSearch.setTextSize(20);
            clearSearch.setGravity(Gravity.CENTER);
            clearSearch.setTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            clearSearch.setContentDescription("清空搜索");
            clearSearch.setVisibility(View.GONE);
            clearSearch.setOnClickListener(view -> search.performClick());
            searchArea.addView(clearSearch,
                    new FrameLayout.LayoutParams(dp(32), -1, Gravity.RIGHT));
            panel.addView(searchArea, new LinearLayout.LayoutParams(-1, dp(32)));

            FrameLayout resultArea = new FrameLayout(context);
            resultMessage = new TextView(context);
            resultMessage.setTextSize(12);
            resultMessage.setTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            resultMessage.setGravity(Gravity.CENTER);
            resultArea.addView(resultMessage, new FrameLayout.LayoutParams(-1, -1));
            resultGrid = new RecyclerView(context);
            resultGrid.setClipToPadding(false);
            resultGrid.setVerticalScrollBarEnabled(false);
            resultGrid.setItemAnimator(null);
            resultGrid.setLayoutManager(new GridLayoutManager(context, 3));
            adapter = new AppAdapter();
            resultGrid.setAdapter(adapter);
            resultArea.addView(resultGrid, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(108));
            scrollParams.topMargin = dp(4);
            scrollParams.bottomMargin = dp(4);
            panel.addView(resultArea, scrollParams);

            LinearLayout keyboard = new LinearLayout(context);
            keyboard.setOrientation(LinearLayout.VERTICAL);
            addKeyRow(keyboard, new String[]{"QW", "ER", "TY", "UI", "OP"});
            addKeyRow(keyboard, new String[]{"AS", "DF", "GH", "JK", "L"});
            addKeyRow(keyboard, new String[]{"ZX", "CV", "BN", "M"});
            panel.addView(keyboard, new LinearLayout.LayoutParams(-1, dp(108)));
        }

        void prepare() {
            if (attached) return;
            updatePanelLayout();
            if (!attachWindow()) return;
            ensureAppsLoaded();
            int width = context.getResources().getDisplayMetrics().widthPixels;
            int height = context.getResources().getDisplayMetrics().heightPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
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
                    return true;
                } catch (Throwable error) {
                    lastError = error;
                    try { windowManager.removeViewImmediate(root); } catch (Throwable ignoredAgain) { }
                }
            }
            if (lastError != null) Log.w(TAG, "Cannot attach app launcher popup", lastError);
            return false;
        }

        void show(boolean fromLeft, float anchorY) {
            com.example.flymestatusbarsizer.feature.taskswitcher.RecentTaskSwitcher.dismissActivePopup();
            this.fromLeft = fromLeft;
            this.anchorY = anchorY;
            if (!attachWindow()) return;
            synchronized (AppLauncherPanel.class) {
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
            query.clear();
            queryGeneration++;
            updateSearch();
            ensureAppsLoaded();
            scheduleSearch();
            panel.setVisibility(View.VISIBLE);
            root.setVisibility(View.VISIBLE);
            root.requestFocus();
            panel.removeCallbacks(showAnimation);
            panel.post(showAnimation);
        }

        private final Runnable showAnimation = this::showPanel;
        private final Runnable startBlur = this::startPanelBlur;

        private void showPanel() {
            if (!isCurrent()) return;
            alignToGestureAnchor();
            animateIn();
            panel.removeCallbacks(startBlur);
            panel.postOnAnimation(startBlur);
        }

        private void startPanelBlur() {
            if (isCurrent()) panelBlur.start();
        }

        private void updatePanelLayout() {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(dp(193), Math.max(dp(165), screenWidth - dp(22)));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.width = width;
            params.gravity = Gravity.TOP | (fromLeft ? Gravity.LEFT : Gravity.RIGHT);
            panel.setLayoutParams(params);
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
                keyboardKeys.add(button);
                button.setOnClickListener(view -> {
                    view.animate().cancel();
                    view.setScaleX(0.9f);
                    view.setScaleY(0.9f);
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    query.add(key.toLowerCase(Locale.ROOT));
                    queryGeneration++;
                    updateSearch();
                    ensureAppsLoaded();
                    scheduleSearch();
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
            if (visible && !query.isEmpty()) showMessage("正在准备应用");
            requestApps(context, this, requestId);
        }

        private void setApps(List<AppEntry> loaded, int requestId, long generation) {
            if (!attached || requestId != loadRequestId) return;
            synchronized (CACHE_LOCK) {
                if (generation != cacheGeneration) {
                    loading = false;
                    indexRequested = false;
                    if (isCurrent()) ensureAppsLoaded();
                    return;
                }
            }
            if (iconCacheGeneration != generation) {
                iconCache.evictAll();
                iconCacheGeneration = generation;
            }
            entries.clear();
            entries.addAll(loaded);
            loading = false;
            if (isCurrent()) scheduleSearch();
        }

        private static void requestApps(Context context, Popup popup, int requestId) {
            List<AppEntry> cached;
            long generation;
            synchronized (CACHE_LOCK) {
                cached = cachedApps;
                generation = cacheGeneration;
                if (cached == null && popup != null && !pendingPopups.contains(popup)) {
                    pendingPopups.add(popup);
                }
                if (cached == null) {
                    if (indexLoading) return;
                    indexLoading = true;
                }
            }
            if (cached != null) {
                if (popup == null) return;
                List<AppEntry> result = cached;
                long resultGeneration = generation;
                MAIN.post(() -> popup.setApps(result, requestId, resultGeneration));
                return;
            }
            INDEX_WORKER.execute(() -> {
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
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps == null) return Collections.emptyList();

            List<UserHandle> users = new ArrayList<>();
            UserHandle owner = Process.myUserHandle();
            users.add(owner);
            for (UserHandle user : launcherApps.getProfiles()) {
                if (owner.equals(user)) continue;
                if (Build.VERSION.SDK_INT >= 35) {
                    LauncherUserInfo userInfo = launcherApps.getLauncherUserInfo(user);
                    if (userInfo != null
                            && "android.os.usertype.profile.CLONE".equals(userInfo.getUserType())) {
                        users.add(user);
                    }
                }
            }

            Map<String, AppEntry> found = new HashMap<>();
            for (UserHandle user : users) {
                for (LauncherActivityInfo activity : launcherApps.getActivityList(null, user)) {
                    ComponentName component = activity.getComponentName();
                    String packageName = component.getPackageName();
                    if (context.getPackageName().equals(packageName)) continue;
                    CharSequence rawLabel = activity.getLabel();
                    String label = rawLabel == null ? packageName : rawLabel.toString();
                    String key = user + "#" + component.flattenToShortString();
                    found.put(key, new AppEntry(label, component, activity, packageName,
                            null, user, buildKeys(label)));
                }

                LauncherApps.ShortcutQuery shortcutQuery = new LauncherApps.ShortcutQuery()
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                                | FLAG_MATCH_PINNED_BY_ANY_LAUNCHER);
                List<ShortcutInfo> shortcuts;
                try { shortcuts = launcherApps.getShortcuts(shortcutQuery, user); }
                catch (RuntimeException ignored) { continue; }
                if (shortcuts == null) continue;
                for (ShortcutInfo shortcut : shortcuts) {
                    CharSequence rawLabel = shortcut.getShortLabel();
                    if (rawLabel == null || rawLabel.length() == 0) rawLabel = shortcut.getLongLabel();
                    if (rawLabel == null || rawLabel.length() == 0) continue;
                    String packageName = shortcut.getPackage();
                    android.content.pm.ApplicationInfo application;
                    try { application = launcherApps.getApplicationInfo(packageName, 0, user); }
                    catch (android.content.pm.PackageManager.NameNotFoundException ignored) { continue; }
                    CharSequence appLabel = application.loadLabel(pm);
                    String label = rawLabel.toString();
                    String key = packageName + "#" + user + "#" + shortcut.getId();
                    found.put(key, new AppEntry(label, null, null, packageName,
                            shortcut.getId(), user,
                            buildKeys(label, appLabel == null ? null : appLabel.toString())));
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
            iconCache.evictAll();
            iconsLoading.clear();
            indexRequested = false;
            loading = false;
            loadRequestId++;
            queryGeneration++;
            adapter.setEntries(Collections.emptyList());
            showMessage(query.isEmpty() ? "请输入应用首字母" : "正在准备应用");
            if (!query.isEmpty()) ensureAppsLoaded();
        }

        private void scheduleSearch() {
            if (!indexRequested || loading) {
                showMessage(query.isEmpty() ? "请输入应用首字母" : "正在准备应用");
                return;
            }
            if (query.isEmpty()) {
                adapter.setEntries(Collections.emptyList());
                showMessage("请输入应用首字母");
                return;
            }
            List<String> querySnapshot = new ArrayList<>(query);
            List<AppEntry> appSnapshot = new ArrayList<>(entries);
            SearchRequest request = new SearchRequest(queryGeneration, querySnapshot, appSnapshot);
            synchronized (searchLock) {
                pendingSearch = request;
                if (searchWorkerRunning) return;
                searchWorkerRunning = true;
            }
            SEARCH_WORKER.execute(this::runSearches);
        }

        private void runSearches() {
            while (true) {
                SearchRequest request;
                synchronized (searchLock) {
                    request = pendingSearch;
                    pendingSearch = null;
                    if (request == null) {
                        searchWorkerRunning = false;
                        return;
                    }
                }
                List<RankedEntry> ranked = new ArrayList<>();
                for (AppEntry entry : request.entries) {
                    int score = entry.matchScore(request.query);
                    if (score != Integer.MAX_VALUE) ranked.add(new RankedEntry(entry, score));
                }
                ranked.sort((left, right) -> {
                    int score = Integer.compare(left.score, right.score);
                    return score != 0 ? score : left.entry.label.compareToIgnoreCase(right.entry.label);
                });
                List<AppEntry> matches = new ArrayList<>(ranked.size());
                for (RankedEntry result : ranked) matches.add(result.entry);
                MAIN.post(() -> {
                    if (!isCurrent() || request.generation != queryGeneration) return;
                    adapter.setEntries(matches);
                    showMessage(matches.isEmpty() ? "未找到匹配应用" : null);
                });
            }
        }

        private void showMessage(String message) {
            resultMessage.setText(message == null ? "" : message);
            resultMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
            resultGrid.setVisibility(message == null ? View.VISIBLE : View.GONE);
        }

        private void bindIcon(ImageView image, AppEntry entry) {
            Bitmap cached = iconCache.get(entry);
            if (cached != null) {
                image.setImageBitmap(cached);
                return;
            }
            image.setImageDrawable(null);
            if (!iconsLoading.add(entry)) return;
            long requestedGeneration = iconCacheGeneration;
            ICON_WORKER.execute(() -> {
                Drawable icon = entry.loadIcon(context);
                Bitmap bitmap = null;
                if (icon != null) {
                    int size = dp(34);
                    bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
                    icon.setBounds(0, 0, size, size);
                    icon.draw(new Canvas(bitmap));
                }
                Bitmap loaded = bitmap;
                MAIN.post(() -> {
                    iconsLoading.remove(entry);
                    boolean currentGeneration;
                    synchronized (CACHE_LOCK) {
                        currentGeneration = requestedGeneration == iconCacheGeneration
                                && requestedGeneration == cacheGeneration;
                    }
                    if (loaded != null && currentGeneration) {
                        iconCache.put(entry, loaded);
                        int position = adapter.indexOf(entry);
                        if (isCurrent() && position >= 0) adapter.notifyItemChanged(position);
                    }
                });
            });
        }

        private final class AppAdapter extends RecyclerView.Adapter<AppHolder> {
            private final List<AppEntry> values = new ArrayList<>();

            @Override public AppHolder onCreateViewHolder(android.view.ViewGroup parent, int type) {
                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(1), dp(2), dp(1));
                ImageView icon = new ImageView(context);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                TextView name = new TextView(context);
                name.setTextSize(10);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
                name.setGravity(Gravity.CENTER);
                cell.addView(name, new LinearLayout.LayoutParams(-1, dp(16)));
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(54)));
                return new AppHolder(cell, icon, name);
            }

            @Override public void onBindViewHolder(AppHolder holder, int position) {
                AppEntry entry = values.get(position);
                holder.entry = entry;
                holder.name.setText(entry.label);
                holder.name.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
                holder.icon.setTag(entry);
                holder.cell.setOnTouchListener((view, event) ->
                        handleEntryTouch(view, holder.icon, entry, event));
                bindIcon(holder.icon, entry);
            }

            @Override public void onViewRecycled(AppHolder holder) {
                if (holdView == holder.cell) cancelHold();
                holder.cell.setOnTouchListener(null);
                holder.icon.setTag(null);
                holder.icon.setImageDrawable(null);
                holder.entry = null;
            }

            @Override public int getItemCount() { return values.size(); }

            void setEntries(List<AppEntry> next) {
                cancelHold();
                values.clear();
                values.addAll(next);
                showMessage(values.isEmpty() ? "未找到匹配应用" : null);
                notifyDataSetChanged();
                resultGrid.scrollToPosition(0);
            }

            int indexOf(AppEntry entry) { return values.indexOf(entry); }
        }

        private final class AppHolder extends RecyclerView.ViewHolder {
            final LinearLayout cell;
            final ImageView icon;
            final TextView name;
            AppEntry entry;

            AppHolder(LinearLayout cell, ImageView icon, TextView name) {
                super(cell);
                this.cell = cell;
                this.icon = icon;
                this.name = name;
            }
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
                        view.setBackground(background(paletteColor(0x303b82f6, 0x507aaeff),
                                dp(8), paletteColor(0xff3b82f6, 0xffa3c7ff)));
                        updateSearch();
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
            holdCanceled = true;
            if (holdRunnable != null) MAIN.removeCallbacks(holdRunnable);
            if (holdTriggered) {
                holdView.setBackground(null);
                holdTriggered = false;
                updateSearch();
            }
            if (holdIcon != null) holdIcon.animate().cancel();
            if (holdIcon != null) {
                if (isCurrent()) {
                    holdIcon.animate().scaleX(1f).scaleY(1f).setDuration(100L).start();
                } else {
                    holdIcon.setScaleX(1f);
                    holdIcon.setScaleY(1f);
                }
            }
            holdRunnable = null;
            holdView = null;
            holdIcon = null;
            holdTriggered = false;
        }

        private void launchEntry(AppEntry entry, View target) {
            HapticFeedbackUtils.perform(context, target, HapticFeedbackConstants.CONTEXT_CLICK);
            closeImmediately();
            LAUNCH_WORKER.execute(() -> {
                try {
                    LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                    if (entry.isShortcut()) {
                        launcherApps.startShortcut(entry.packageName, entry.shortcutId,
                                null, null, entry.user);
                    } else {
                        launcherApps.startMainActivity(entry.component, entry.user, null, null);
                    }
                } catch (Throwable ignored) {
                }
            });
        }

        private void launchMiniWindow(AppEntry entry) {
            closeImmediately();
            LAUNCH_WORKER.execute(() -> {
                try {
                    LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
                    if (entry.isShortcut()) {
                        FlymeMiniWindowLauncher.startShortcutInMiniWindow(
                                context, launcherApps, entry.packageName, entry.shortcutId, entry.user);
                    } else {
                        FlymeMiniWindowLauncher.startLauncherActivityInMiniWindow(
                                context, launcherApps, entry.component, entry.user, entry.packageName);
                    }
                } catch (Throwable error) {
                    MAIN.post(() -> Toast.makeText(context, "无法打开小窗", Toast.LENGTH_SHORT).show());
                }
            });
        }

        private void updateSearch() {
            boolean showClear = !holdTriggered && !query.isEmpty();
            clearSearch.setVisibility(showClear ? View.VISIBLE : View.GONE);
            search.setPadding(dp(8), 0, dp(showClear ? 32 : 8), 0);
            if (holdTriggered) {
                search.setText("松手以小窗打开");
                return;
            }
            StringBuilder text = new StringBuilder();
            for (String key : query) {
                if (text.length() > 0) text.append(' ');
                text.append(key.toUpperCase(Locale.ROOT));
            }
            search.setText(text);
        }

        private void ensureAppsLoaded() {
            synchronized (CACHE_LOCK) {
                if (!loading && iconCacheGeneration != cacheGeneration) indexRequested = false;
            }
            if (!indexRequested) loadAppsAsync();
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
            if (!visible || closing) return;
            closing = true;
            queryGeneration++;
            synchronized (searchLock) { pendingSearch = null; }
            cancelHold();
            panel.removeCallbacks(showAnimation);
            panel.removeCallbacks(startBlur);
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
            panel.removeCallbacks(startBlur);
            panel.animate().withEndAction(null).cancel();
            if (attached) finishClose();
        }

        private void finishClose() {
            visible = false;
            closing = false;
            queryGeneration++;
            synchronized (searchLock) { pendingSearch = null; }
            cancelHold();
            panel.removeCallbacks(showAnimation);
            panel.removeCallbacks(startBlur);
            panel.animate().withEndAction(null).cancel();
            resultGrid.stopScroll();
            root.cancelPendingInputEvents();
            for (int i = 0; i < resultGrid.getChildCount(); i++) {
                AppHolder holder = (AppHolder) resultGrid.getChildViewHolder(resultGrid.getChildAt(i));
                holder.icon.animate().cancel();
                holder.icon.setScaleX(1f);
                holder.icon.setScaleY(1f);
            }
            for (TextView key : keyboardKeys) {
                key.animate().cancel();
                key.setScaleX(1f);
                key.setScaleY(1f);
            }
            panelBlur.stop();
            panel.setVisibility(View.INVISIBLE);
            root.setVisibility(View.GONE);
            root.clearFocus();
            windowParams.alpha = 0f;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(root, windowParams);
            synchronized (AppLauncherPanel.class) { if (active == this) active = null; }
        }

        private void release() {
            closeImmediately();
            loadRequestId++;
            loading = false;
            panelBlur.stop();
            synchronized (CACHE_LOCK) { pendingPopups.remove(this); }
            if (attached) {
                windowManager.removeViewImmediate(root);
                attached = false;
            }
            iconCache.evictAll();
            entries.clear();
            releasePackageReceiver();
        }

        private boolean isCurrent() {
            synchronized (AppLauncherPanel.class) {
                return active == this && attached && visible && !closing;
            }
        }

        private boolean isNightMode(Configuration configuration) {
            return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }

        private void updateDarkMode(Configuration configuration) {
            boolean next = isNightMode(configuration);
            if (darkMode == next) return;
            cancelHold();
            darkMode = next;
            panel.setBackground(panelBackground());
            search.setTextColor(paletteColor(0xff202426, 0xfff2f4f5));
            search.setHintTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            search.setBackground(background(paletteColor(0x12000000, 0x1cffffff), dp(7), Color.TRANSPARENT));
            clearSearch.setTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            for (TextView key : keyboardKeys) {
                key.setTextColor(paletteColor(0xff273134, 0xffe4e9ea));
                key.setBackground(background(
                        paletteColor(0x16000000, 0x20ffffff), dp(7), Color.TRANSPARENT));
            }
            resultMessage.setTextColor(paletteColor(0xff7d8589, 0xffaeb6ba));
            adapter.notifyDataSetChanged();
        }

        private Drawable panelBackground() {
            int alpha = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0x98 : 0xf5;
            return background(
                    paletteColor(0xffffff | (alpha << 24), 0x2a2c2f | (alpha << 24)),
                    dp(12), paletteColor(0x24000000, 0x38ffffff));
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
