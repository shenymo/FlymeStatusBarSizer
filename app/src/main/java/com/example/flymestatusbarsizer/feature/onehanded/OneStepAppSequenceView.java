package com.example.flymestatusbarsizer.feature.onehanded;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.Transliterator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Search, application discovery and the recycled workspace application list. */
final class OneStepAppSequenceView extends FrameLayout {
    interface Listener { void onAppSelected(AppEntry app); }

    static final class AppEntry {
        final ComponentName component;
        final UserHandle user;
        final String label;
        final String displayLabel;
        final String latinLabel;
        final String lowerLabel;
        final String lowerLatin;
        final String lowerInitials;
        final LauncherActivityInfo launcherInfo;
        volatile Drawable icon;
        volatile Drawable.ConstantState iconState;
        volatile boolean iconLoading;
        final char section;

        AppEntry(ComponentName component, UserHandle user, String label, String displayLabel,
                 String latinLabel, String initials, LauncherActivityInfo launcherInfo, char section) {
            this.component = component;
            this.user = user;
            this.label = label;
            this.displayLabel = displayLabel;
            this.latinLabel = latinLabel;
            this.lowerLabel = label.toLowerCase(Locale.ROOT);
            this.lowerLatin = latinLabel.toLowerCase(Locale.ROOT);
            this.lowerInitials = initials.toLowerCase(Locale.ROOT);
            this.launcherInfo = launcherInfo;
            this.section = section;
        }
    }

    private static final int BACKGROUND = 0xc8202830;
    private static final int SURFACE = 0xff1b1e21;
    private static final int PRIMARY = 0xfff2f3f4;
    private static final int SECONDARY = 0xff9ba1a6;
    private static final int CHIP = 0xff2b3036;
    private static final int ACCENT = 0xff36b7a2;
    private static final int DISABLED = 0x4d9ba1a6;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_APP = 1;
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII; Upper()");
    private static final long APP_CACHE_REFRESH_MS = 30_000L;
    private static final Object APP_CACHE_LOCK = new Object();
    private static final Map<Integer, CachedApps> APP_CACHE = new HashMap<>();
    private static final LruCache<String, Drawable.ConstantState> ICON_CACHE = new LruCache<>(96);

    private final int ownerUserId;
    private final Listener listener;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> daemon(r, "FlymeAppSequence"));
    private final ExecutorService iconLoader = Executors.newFixedThreadPool(2, r -> daemon(r, "FlymeAppIcons"));
    private final ExecutorService filterLoader = Executors.newSingleThreadExecutor(r -> daemon(r, "FlymeAppFilter"));
    private final AtomicInteger filterGeneration = new AtomicInteger();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EditText search;
    private final ImageView clear;
    private final LinearLayout keyboard;
    private final RecyclerView recycler;
    private final TextView sticky;
    private final TextView empty;
    private final LinearLayout rail;
    private final TextView preview;
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private final ArrayList<AppEntry> filtered = new ArrayList<>();
    private final ArrayList<String> queryGroups = new ArrayList<>();
    private final AppAdapter adapter;
    private final Map<Character, Integer> sectionPositions = new HashMap<>();
    private String query = "";
    private int textSizeSp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_TEXT_SIZE_SP;
    private int iconSizeDp = SettingsStore.DEFAULT_WORKSPACE_APP_SEQUENCE_ICON_SIZE_DP;
    private boolean loading = true;
    private boolean loadingPaused;
    private boolean keyboardVisible;
    private boolean settingKeyboardText;
    private boolean scrolling;
    private float cornerRadius;
    private char railLetter;
    private final Runnable filterRunnable = this::applyScheduledFilter;

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class CachedApps {
        final ArrayList<AppEntry> apps;
        final long loadedAt = SystemClock.uptimeMillis();
        CachedApps(ArrayList<AppEntry> apps) { this.apps = apps; }
    }

    private static final class Item {
        final int type;
        final char section;
        final AppEntry app;
        private Item(int type, char section, AppEntry app) { this.type = type; this.section = section; this.app = app; }
        static Item header(char section) { return new Item(TYPE_HEADER, section, null); }
        static Item app(AppEntry app) { return new Item(TYPE_APP, app.section, app); }
        String key() { return type == TYPE_HEADER ? "h:" + section : "a:" + app.user + ":" + app.component.flattenToString(); }
    }

    private static final DiffUtil.ItemCallback<Item> ITEM_CALLBACK = new DiffUtil.ItemCallback<Item>() {
        @Override public boolean areItemsTheSame(@NonNull Item oldItem, @NonNull Item newItem) { return oldItem.key().equals(newItem.key()); }
        @Override public boolean areContentsTheSame(@NonNull Item oldItem, @NonNull Item newItem) {
            return oldItem.type == newItem.type && oldItem.section == newItem.section
                    && (oldItem.app == newItem.app || oldItem.app != null && newItem.app != null
                    && oldItem.app.displayLabel.equals(newItem.app.displayLabel) && oldItem.app.icon == newItem.app.icon);
        }
    };

    OneStepAppSequenceView(Context context, int ownerUserId, Listener listener) {
        super(context);
        this.ownerUserId = ownerUserId;
        this.listener = listener;
        setBackgroundColor(BACKGROUND);
        setFocusable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius); }
        });
        setClipToOutline(true);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(12), dp(6), dp(8));
        addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(roundRect(SURFACE, dp(8)));
        searchBar.setPadding(dp(10), 0, dp(4), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(38));
        searchParams.rightMargin = dp(10);
        column.addView(searchBar, searchParams);
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(android.R.drawable.ic_menu_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(SECONDARY));
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        search.setTextColor(PRIMARY);
        search.setHintTextColor(0xff747a80);
        search.setHint("搜索应用名称、拼音或首字母");
        search.setPadding(dp(8), 0, dp(4), 0);
        search.setBackground(null);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setShowSoftInputOnFocus(false);
        search.setFocusableInTouchMode(true);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
        searchBar.setOnClickListener(v -> showCustomKeyboard());
        search.setOnClickListener(v -> showCustomKeyboard());
        search.setOnTouchListener((v, event) -> { if (event.getAction() == MotionEvent.ACTION_DOWN) showCustomKeyboard(); return false; });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!settingKeyboardText) queryGroups.clear();
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                clear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                main.removeCallbacks(filterRunnable);
                main.postDelayed(filterRunnable, 40L);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (!submit) return false;
            if (!query.isEmpty() && !filtered.isEmpty() && listener != null) listener.onAppSelected(filtered.get(0));
            return true;
        });
        clear = new ImageView(context);
        clear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        clear.setImageTintList(ColorStateList.valueOf(SECONDARY));
        clear.setPadding(dp(8), dp(8), dp(8), dp(8));
        clear.setBackground(selectableBackground());
        clear.setContentDescription("清空搜索");
        clear.setVisibility(GONE);
        clear.setOnClickListener(v -> { queryGroups.clear(); search.setText(""); });
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(34), dp(34)));

        FrameLayout listFrame = new FrameLayout(context);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1);
        listParams.topMargin = dp(10);
        column.addView(listFrame, listParams);
        recycler = new RecyclerView(context);
        recycler.setClipToPadding(false);
        recycler.setVerticalScrollBarEnabled(false);
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        recycler.setHasFixedSize(true);
        adapter = new AppAdapter();
        configureLayoutManager();
        recycler.setAdapter(adapter);
        FrameLayout.LayoutParams recyclerParams = new FrameLayout.LayoutParams(-1, -1);
        recyclerParams.rightMargin = dp(22);
        listFrame.addView(recycler, recyclerParams);
        empty = text("正在读取应用…", 13, SECONDARY, false);
        empty.setGravity(Gravity.CENTER);
        listFrame.addView(empty, new FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP));
        sticky = text("A", 13, PRIMARY, true);
        sticky.setGravity(Gravity.CENTER);
        sticky.setBackground(roundRect(CHIP, dp(11)));
        sticky.setVisibility(GONE);
        FrameLayout.LayoutParams stickyParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.LEFT);
        stickyParams.leftMargin = dp(2);
        stickyParams.topMargin = dp(4);
        listFrame.addView(sticky, stickyParams);
        rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        listFrame.addView(rail, new FrameLayout.LayoutParams(dp(22), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        for (int i = 0; i < ALPHABET.length(); i++) {
            TextView item = text(String.valueOf(ALPHABET.charAt(i)), 10, SECONDARY, false);
            item.setGravity(Gravity.CENTER);
            rail.addView(item, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        preview = text("A", 22, PRIMARY, true);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(roundRect(ACCENT, dp(26)));
        preview.setVisibility(GONE);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.RIGHT);
        previewParams.rightMargin = dp(34);
        listFrame.addView(preview, previewParams);
        rail.setOnTouchListener((v, event) -> {
            if (!query.isEmpty()) return true;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int index = Math.max(0, Math.min(ALPHABET.length() - 1, (int) (event.getY() / Math.max(1f, v.getHeight()) * ALPHABET.length())));
                char letter = ALPHABET.charAt(index);
                if (letter != railLetter) { railLetter = letter; v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); jumpTo(letter); }
                preview.setText(String.valueOf(letter));
                preview.setTranslationY(Math.max(0, Math.min(listFrame.getHeight() - dp(52), event.getY() - dp(26))));
                preview.setVisibility(VISIBLE);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { railLetter = 0; preview.setVisibility(GONE); }
            return true;
        });
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView view, int state) { scrolling = state != RecyclerView.SCROLL_STATE_IDLE; if (!scrolling) adapter.preloadVisible(); updateSticky(); }
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) { updateSticky(); }
        });

        keyboard = new LinearLayout(context);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(4), dp(4), dp(4), dp(4));
        keyboard.setBackground(roundRect(SURFACE, dp(8)));
        keyboard.setVisibility(GONE);
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(-1, dp(174));
        keyboardParams.topMargin = dp(4);
        column.addView(keyboard, keyboardParams);
        addKeyboardRow("QW", "ER", "TY", "UI", "OP");
        addKeyboardRow("AS", "DF", "GH", "JK", "L");
        addKeyboardRow("ZX", "CV", "BN", "M", "DEL");
        loadApps();
    }

    boolean consumeBack() { if (!keyboardVisible) return false; hideCustomKeyboard(); return true; }
    void dismissKeyboard() { if (keyboardVisible) hideCustomKeyboard(); }

    void configure(int textSizeSp, int iconSizeDp) {
        boolean changed = this.textSizeSp != textSizeSp || this.iconSizeDp != iconSizeDp;
        this.textSizeSp = textSizeSp;
        this.iconSizeDp = iconSizeDp;
        if (changed) {
            configureLayoutManager();
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    void setLoadingPaused(boolean paused) { loadingPaused = paused; }

    void setCornerRadius(float radius) {
        cornerRadius = radius;
        invalidateOutline();
        Drawable background = getBackground();
        if (background != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FlymeStatusBarSizer.invokeMethodCompat(background, "setCornerRadius", new Class<?>[]{float.class}, cornerRadius);
    }

    void dispose() { main.removeCallbacks(filterRunnable); loader.shutdownNow(); iconLoader.shutdownNow(); filterLoader.shutdownNow(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(this::applyGlassBackground); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) post(this::configureLayoutManager);
    }

    private void configureLayoutManager() {
        GridLayoutManager manager = new GridLayoutManager(getContext(), gridColumns());
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.itemAt(position).type == TYPE_HEADER ? manager.getSpanCount() : 1; }
        });
        recycler.setLayoutManager(manager);
    }

    private int gridColumns() { return Math.max(1, Math.min(5, Math.max(1, recycler.getWidth()) / dp(88))); }
    private void showCustomKeyboard() { keyboardVisible = true; keyboard.setVisibility(VISIBLE); search.requestFocus(); }
    private void hideCustomKeyboard() { keyboardVisible = false; keyboard.setVisibility(GONE); }

    private void addKeyboardRow(String... keys) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        for (String key : keys) {
            TextView button = text(key, 17, PRIMARY, false);
            button.setGravity(Gravity.CENTER);
            button.setBackground(roundRect(CHIP, dp(8)));
            button.setContentDescription("输入 " + key);
            button.setTag(key);
            button.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onKeyboardKey((String) v.getTag()); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
    }

    private void onKeyboardKey(String key) {
        if ("DEL".equals(key)) { if (!queryGroups.isEmpty()) queryGroups.remove(queryGroups.size() - 1); }
        else queryGroups.add(key.toLowerCase(Locale.ROOT));
        StringBuilder value = new StringBuilder();
        for (String group : queryGroups) value.append(group);
        settingKeyboardText = true;
        try { search.setText(value); } finally { settingKeyboardText = false; }
        search.setSelection(search.length());
    }

    private void applyScheduledFilter() { applyFilter(); recycler.scrollToPosition(0); }

    private void applyGlassBackground() {
        if (!isAttachedToWindow() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Object viewRoot = FlymeStatusBarSizer.invokeNoArgCompat(this, "getViewRootImpl");
        Object blur = FlymeStatusBarSizer.invokeNoArgCompat(viewRoot, "createBackgroundBlurDrawable");
        if (!(blur instanceof Drawable)) return;
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setCornerRadius", new Class<?>[]{float.class}, cornerRadius);
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setBlurRadius", new Class<?>[]{int.class}, dp(32));
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setZAdjustment", new Class<?>[]{int.class}, -1);
        FlymeStatusBarSizer.invokeMethodCompat(blur, "setColor", new Class<?>[]{int.class}, BACKGROUND);
        setBackground((Drawable) blur);
    }

    private void loadApps() {
        CachedApps cached;
        synchronized (APP_CACHE_LOCK) { cached = APP_CACHE.get(ownerUserId); }
        if (cached != null) {
            apps.clear(); apps.addAll(cached.apps); loading = false; applyFilter();
            if (SystemClock.uptimeMillis() - cached.loadedAt < APP_CACHE_REFRESH_MS) return;
        }
        loader.execute(() -> {
            ArrayList<AppEntry> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            LauncherApps launcherApps = getContext().getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                ArrayList<UserHandle> users = new ArrayList<>();
                users.add(UserHandle.getUserHandleForUid(ownerUserId * 100000));
                if (ownerUserId == 0) users.add(UserHandle.getUserHandleForUid(999 * 100000));
                for (int u = 0; u < users.size(); u++) {
                    UserHandle user = users.get(u);
                    try {
                        for (LauncherActivityInfo info : launcherApps.getActivityList(null, user)) {
                            ComponentName component = info.getComponentName();
                            String packageName = component.getPackageName();
                            if (packageName.equals("com.meizu.flyme.launcher") || packageName.equals("com.android.systemui") || packageName.equals(getContext().getPackageName())) continue;
                            if (!seen.add(user + ":" + component.flattenToString())) continue;
                            String label = String.valueOf(info.getLabel());
                            String latin = transliterate(label);
                            char section = latin.isEmpty() ? '#' : latin.charAt(0);
                            if (section < 'A' || section > 'Z') section = '#';
                            String display = u > 0 ? label + "（分身）" : label;
                            found.add(new AppEntry(component, user, label, display, latin, initials(label), info, section));
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            Collections.sort(found, Comparator.comparing((AppEntry app) -> app.section == '#' ? '[' : app.section).thenComparing(app -> app.latinLabel, String.CASE_INSENSITIVE_ORDER).thenComparing(app -> app.displayLabel));
            synchronized (APP_CACHE_LOCK) { APP_CACHE.put(ownerUserId, new CachedApps(found)); }
            main.post(() -> { apps.clear(); apps.addAll(found); loading = false; applyFilter(); });
        });
    }

    private void applyFilter() {
        int generation = filterGeneration.incrementAndGet();
        String querySnapshot = query;
        ArrayList<String> groupsSnapshot = new ArrayList<>(queryGroups);
        ArrayList<AppEntry> appsSnapshot = new ArrayList<>(apps);
        boolean loadingSnapshot = loading;
        filterLoader.execute(() -> {
            ArrayList<AppEntry> result = new ArrayList<>();
            if (querySnapshot.isEmpty()) result.addAll(appsSnapshot);
            else {
                ArrayList<AppEntry> prefix = new ArrayList<>();
                ArrayList<AppEntry> contains = new ArrayList<>();
                for (AppEntry app : appsSnapshot) {
                    if (matchesQuery(app.lowerLabel, querySnapshot, groupsSnapshot, true) || matchesQuery(app.lowerLatin, querySnapshot, groupsSnapshot, true) || matchesQuery(app.lowerInitials, querySnapshot, groupsSnapshot, true)) prefix.add(app);
                    else if (matchesQuery(app.lowerLabel, querySnapshot, groupsSnapshot, false) || matchesQuery(app.lowerLatin, querySnapshot, groupsSnapshot, false) || matchesQuery(app.lowerInitials, querySnapshot, groupsSnapshot, false)) contains.add(app);
                }
                result.addAll(prefix); result.addAll(contains);
            }
            main.post(() -> {
                if (generation != filterGeneration.get()) return;
                filtered.clear(); filtered.addAll(result);
                empty.setText(loadingSnapshot ? "正在读取应用…" : "没有找到应用");
                empty.setVisibility(result.isEmpty() ? VISIBLE : GONE);
                rail.setVisibility(querySnapshot.isEmpty() ? VISIBLE : GONE);
                sticky.setVisibility(querySnapshot.isEmpty() && !result.isEmpty() ? VISIBLE : GONE);
                configureLayoutManager();
                adapter.submitList(buildItems(result, querySnapshot.isEmpty()));
                updateRailColors();
            });
        });
    }

    private ArrayList<Item> buildItems(List<AppEntry> source, boolean sections) {
        ArrayList<Item> items = new ArrayList<>();
        sectionPositions.clear();
        if (!sections) for (AppEntry app : source) items.add(Item.app(app));
        else {
            char current = 0;
            for (AppEntry app : source) {
                if (app.section != current) { current = app.section; sectionPositions.put(current, items.size()); items.add(Item.header(current)); }
                items.add(Item.app(app));
            }
        }
        return items;
    }

    private void jumpTo(char requested) {
        if (sectionPositions.isEmpty()) return;
        int index = ALPHABET.indexOf(requested);
        Integer position = null;
        for (int i = index; i < ALPHABET.length() && position == null; i++) position = sectionPositions.get(ALPHABET.charAt(i));
        for (int i = index - 1; i >= 0 && position == null; i--) position = sectionPositions.get(ALPHABET.charAt(i));
        if (position != null && recycler.getLayoutManager() instanceof LinearLayoutManager)
            ((LinearLayoutManager) recycler.getLayoutManager()).scrollToPositionWithOffset(position, 0);
    }

    private void updateSticky() {
        if (query.length() > 0 || adapter.getItemCount() == 0) return;
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        int position = manager instanceof LinearLayoutManager ? ((LinearLayoutManager) manager).findFirstVisibleItemPosition() : ((GridLayoutManager) manager).findFirstVisibleItemPosition();
        if (position < 0) return;
        sticky.setText(String.valueOf(adapter.itemAt(position).section));
    }

    private void updateRailColors() {
        for (int i = 0; i < rail.getChildCount(); i++) {
            TextView item = (TextView) rail.getChildAt(i);
            item.setTextColor(sectionPositions.containsKey(ALPHABET.charAt(i)) ? SECONDARY : DISABLED);
        }
    }

    private void scheduleIconLoad(AppEntry app) {
        if (loadingPaused || scrolling || app.iconLoading || app.icon != null) return;
        Drawable.ConstantState cached;
        synchronized (ICON_CACHE) { cached = ICON_CACHE.get(iconKey(app)); }
        if (cached != null) { app.iconState = cached; app.icon = cached.newDrawable(getResources()); adapter.notifyAppChanged(app); return; }
        app.iconLoading = true;
        iconLoader.execute(() -> {
            Drawable loaded = null;
            try { loaded = app.launcherInfo.getBadgedIcon(0); } catch (RuntimeException ignored) { }
            Drawable.ConstantState state = loaded == null ? null : loaded.getConstantState();
            if (state != null) synchronized (ICON_CACHE) { ICON_CACHE.put(iconKey(app), state); }
            Drawable icon = loaded;
            main.post(() -> { app.icon = icon; app.iconState = state; app.iconLoading = false; adapter.notifyAppChanged(app); });
        });
    }

    private String iconKey(AppEntry app) { return app.user + ":" + app.component.flattenToString() + ":" + getResources().getDisplayMetrics().densityDpi; }

    private static boolean matchesQuery(String value, String query, List<String> groups, boolean prefix) {
        if (groups.isEmpty()) return prefix ? value.startsWith(query) : value.contains(query);
        int limit = value.length() - groups.size();
        if (limit < 0) return false;
        for (int offset = 0; offset <= limit; offset++) {
            if (prefix && offset > 0) break;
            boolean matched = true;
            for (int i = 0; i < groups.size(); i++) if (groups.get(i).indexOf(value.charAt(offset + i)) < 0) { matched = false; break; }
            if (matched) return true;
        }
        return false;
    }

    private static String transliterate(String value) {
        try {
            String latin;
            synchronized (PINYIN) { latin = PINYIN.transliterate(value); }
            latin = latin.replaceAll("[^A-Z0-9]", "");
            return latin.isEmpty() ? value.toUpperCase(Locale.ROOT) : latin;
        } catch (RuntimeException ignored) { return value.toUpperCase(Locale.ROOT); }
    }

    private static String initials(String label) {
        StringBuilder out = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < label.length();) {
            int cp = label.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                String latin = transliterate(new String(Character.toChars(cp)));
                if (!latin.isEmpty()) out.append(latin.charAt(0));
                wordStart = true;
            } else if (Character.isLetterOrDigit(cp)) {
                if (wordStart || Character.isUpperCase(cp)) out.appendCodePoint(Character.toUpperCase(cp));
                wordStart = false;
            } else wordStart = true;
        }
        return out.toString();
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextColor(color); view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId == 0 ? null : getContext().getDrawable(value.resourceId);
    }

    private Drawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(radius); return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class AppAdapter extends ListAdapter<Item, RecyclerView.ViewHolder> {
        AppAdapter() { super(ITEM_CALLBACK); }
        Item itemAt(int position) { return getItem(position); }
        @Override public int getItemViewType(int position) { return getItem(position).type; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_HEADER) {
                TextView header = text("A", 11, PRIMARY, true);
                header.setGravity(Gravity.CENTER); header.setBackground(roundRect(CHIP, dp(11)));
                header.setLayoutParams(new RecyclerView.LayoutParams(dp(22), dp(26)));
                return new HeaderHolder(header);
            }
            return new AppHolder(new AppItemView(getContext()));
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = getItem(position);
            if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind(item.section); else ((AppHolder) holder).bind(item.app);
        }
        @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) { if (holder instanceof AppHolder) ((AppHolder) holder).clear(); }
        void notifyAppChanged(AppEntry app) {
            main.post(() -> { for (int i = 0; i < getItemCount(); i++) { Item item = getItem(i); if (item.type == TYPE_APP && item.app == app) { notifyItemChanged(i); return; } } });
        }
        void preloadVisible() {
            if (loadingPaused) return;
            RecyclerView.LayoutManager manager = recycler.getLayoutManager();
            int first = manager instanceof LinearLayoutManager ? ((LinearLayoutManager) manager).findFirstVisibleItemPosition() : ((GridLayoutManager) manager).findFirstVisibleItemPosition();
            int last = manager instanceof LinearLayoutManager ? ((LinearLayoutManager) manager).findLastVisibleItemPosition() : ((GridLayoutManager) manager).findLastVisibleItemPosition();
            for (int i = Math.max(0, first); i <= Math.min(getItemCount() - 1, last + 4); i++) if (getItem(i).type == TYPE_APP) scheduleIconLoad(getItem(i).app);
        }
    }

    private final class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(View view) { super(view); }
        void bind(char section) { ((TextView) itemView).setText(String.valueOf(section)); }
    }

    private final class AppHolder extends RecyclerView.ViewHolder {
        private final AppItemView item;
        AppHolder(AppItemView item) { super(item); this.item = item; item.setOnClickListener(v -> { if (item.app != null && listener != null) listener.onAppSelected(item.app); }); }
        void bind(AppEntry app) { item.bind(app); if (app.icon == null) scheduleIconLoad(app); }
        void clear() { item.clear(); }
    }

    private final class AppItemView extends LinearLayout {
        private final ImageView iconView;
        private final TextView labelView;
        private AppEntry app;
        AppItemView(Context context) {
            super(context); setGravity(Gravity.CENTER_VERTICAL); setOrientation(HORIZONTAL); setPadding(dp(4), 0, dp(4), 0);
            iconView = new ImageView(context); iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            labelView = text("", textSizeSp, PRIMARY, false); labelView.setGravity(Gravity.CENTER_VERTICAL);
            addView(iconView); addView(labelView);
        }
        void bind(AppEntry app) {
            this.app = app;
            int size = dp(iconSizeDp);
            setOrientation(VERTICAL);
            setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(dp(76), dp(iconSizeDp + 32))));
            iconView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            labelView.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(24)));
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            labelView.setMaxLines(1); labelView.setEllipsize(android.text.TextUtils.TruncateAt.END); labelView.setText(app.displayLabel);
            iconView.setImageDrawable(app.icon);
        }
        void clear() { app = null; iconView.setImageDrawable(null); labelView.setText(""); }
    }
}
